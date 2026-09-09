package com.metrolist.music.utils

import android.util.Log
import com.metrolist.innertube.models.response.PlayerResponse
import com.metrolist.music.utils.InnerTubeXPlayer.PlaybackData
import com.metrolist.music.utils.pipepipe.FaradayJsDecoder
import dev.maxrave.pipepipe.extractor.NewPipe as PipePipeNewPipe
import dev.maxrave.pipepipe.extractor.ServiceList as PipePipeServiceList
import dev.maxrave.pipepipe.extractor.downloader.CancellableCall as PipePipeCancellableCall
import dev.maxrave.pipepipe.extractor.downloader.Downloader as PipePipeDownloader
import dev.maxrave.pipepipe.extractor.downloader.Request as PipePipeRequest
import dev.maxrave.pipepipe.extractor.downloader.Response as PipePipeResponse
import dev.maxrave.pipepipe.extractor.exceptions.ReCaptchaException as PipePipeReCaptchaException
import dev.maxrave.pipepipe.extractor.services.youtube.YoutubeApiDecoder
import dev.maxrave.pipepipe.extractor.stream.StreamInfo as PipePipeStreamInfo
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe as BraveNewPipe
import org.schabi.newpipe.extractor.ServiceList as BraveServiceList
import org.schabi.newpipe.extractor.downloader.Downloader as BraveDownloader
import org.schabi.newpipe.extractor.downloader.Request as BraveRequest
import org.schabi.newpipe.extractor.downloader.Response as BraveResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException as BraveReCaptchaException
import org.schabi.newpipe.extractor.stream.StreamInfo as BraveStreamInfo
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/** SimpMusic's three-level extractor chain, used only after InnerTubeX fails. */
object SimpMusicFallbackPlayer {
    private const val TAG = "SimpMusicFallback"
    private const val DEFAULT_EXPIRY_SECONDS = 300
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36"

    private val initialized = AtomicBoolean(false)
    private val pipePipeDownloader = PipePipeHttpDownloader()
    private val braveDownloader = BraveHttpDownloader()
    private val faradayDecoder = FaradayJsDecoder()

    fun initialize() {
        if (initialized.compareAndSet(false, true)) {
            PipePipeNewPipe.init(pipePipeDownloader)
            BraveNewPipe.init(braveDownloader)
            YoutubeApiDecoder.setLocalDecoder(faradayDecoder)
        }
    }

    suspend fun playerResponseForPlayback(videoId: String): Result<PlaybackData> {
        initialize()

        // Level 1: Faraday on-device decoder. PipePipe falls back to its API when the decoder throws.
        YoutubeApiDecoder.setLocalDecoder(faradayDecoder)
        pipePipeStreams(videoId, "Faraday local")?.let { return Result.success(it) }

        // Level 2: PipePipe with the local decoder disabled, forcing api.pipepipe.dev.
        YoutubeApiDecoder.setLocalDecoder(null)
        pipePipeStreams(videoId, "api.pipepipe.dev")?.let { return Result.success(it) }

        // Level 3: an independent extractor, BravePipe.
        return braveStreams(videoId)?.let { Result.success(it) }
            ?: Result.failure(IllegalStateException("All SimpMusic extraction levels failed"))
    }

    private suspend fun pipePipeStreams(videoId: String, source: String): PlaybackData? =
        runCatching {
            val info =
                PipePipeStreamInfo.getInfo(
                    PipePipeServiceList.YouTube,
                    "https://music.youtube.com/watch?v=$videoId",
                )
            val stream =
                info.audioStreams
                    .asSequence()
                    .filter { !it.content.isNullOrBlank() }
                    .maxByOrNull { it.itagItem?.id ?: 0 }
                    ?: error("PipePipe returned no audio stream")
            val url = requireNotNull(stream.content)
            Timber.tag(TAG).i("%s resolved video=%s itag=%s", source, videoId, stream.itagItem?.id)
            toPlaybackData(url, stream.itagItem?.id ?: 0, source)
        }.onFailure {
            Timber.tag(TAG).w(it, "%s failed for video=%s", source, videoId)
        }.getOrNull()

    private fun braveStreams(videoId: String): PlaybackData? =
        runCatching {
            val info =
                BraveStreamInfo.getInfo(
                    BraveServiceList.YouTube,
                    "https://www.youtube.com/watch?v=$videoId",
                )
            val stream =
                info.audioStreams
                    .asSequence()
                    .filter { !it.content.isNullOrBlank() }
                    .maxByOrNull { it.itagItem?.id ?: 0 }
                    ?: error("BravePipe returned no audio stream")
            val url = requireNotNull(stream.content)
            Timber.tag(TAG).i("BravePipe resolved video=%s itag=%s", videoId, stream.itagItem?.id)
            toPlaybackData(url, stream.itagItem?.id ?: 0, "BravePipe")
        }.onFailure {
            Timber.tag(TAG).w(it, "BravePipe failed for video=%s", videoId)
        }.getOrNull()

    private fun toPlaybackData(url: String, itag: Int, source: String): PlaybackData =
        PlaybackData(
            audioConfig = null,
            videoDetails = null,
            playbackTracking = null,
            format =
                PlayerResponse.StreamingData.Format(
                    itag = itag,
                    url = url,
                    mimeType = "audio/webm",
                    bitrate = 0,
                    width = null,
                    height = null,
                    contentLength = null,
                    quality = "",
                    fps = null,
                    qualityLabel = null,
                    averageBitrate = null,
                    audioQuality = null,
                    approxDurationMs = null,
                    audioSampleRate = null,
                    audioChannels = null,
                    loudnessDb = null,
                    lastModified = null,
                    signatureCipher = null,
                    cipher = null,
                    audioTrack = null,
                ),
            streamUrl = url,
            streamExpiresInSeconds = DEFAULT_EXPIRY_SECONDS,
            streamClient = source,
            streamHeaders = emptyMap(),
            requireBoundedRange = false,
            rangeChunkSizeBytes = 0L,
            useRangeChunks = false,
        )

    private class PipePipeHttpDownloader : PipePipeDownloader() {
        private val client = OkHttpClient()

        @Throws(IOException::class, PipePipeReCaptchaException::class)
        override fun execute(request: PipePipeRequest): PipePipeResponse {
            val builder =
                Request.Builder()
                    .url(request.url())
                    .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
                    .header("User-Agent", USER_AGENT)
            request.headers().forEach { (name, values) ->
                builder.removeHeader(name)
                values.forEach { builder.addHeader(name, it) }
            }
            client.newCall(builder.build()).execute().use { response ->
                if (response.code == 429) throw PipePipeReCaptchaException("YouTube requested a challenge", request.url())
                val bytes = response.body?.bytes() ?: ByteArray(0)
                return PipePipeResponse(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    bytes.toString(Charsets.UTF_8),
                    bytes,
                    response.request.url.toString(),
                )
            }
        }

        override fun executeAsync(
            request: PipePipeRequest,
            callback: PipePipeDownloader.AsyncCallback?,
        ): PipePipeCancellableCall {
            val call = client.newCall(buildRequest(request))
            val cancellable = PipePipeCancellableCall(call)
            call.enqueue(
                object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: IOException) {
                        cancellable.setFinished()
                        callback?.onError(e)
                    }

                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        try {
                            if (response.code == 429) {
                                callback?.onError(PipePipeReCaptchaException("YouTube requested a challenge", request.url()))
                            } else {
                                val bytes = response.body?.bytes() ?: ByteArray(0)
                                callback?.onSuccess(
                                    PipePipeResponse(
                                        response.code,
                                        response.message,
                                        response.headers.toMultimap(),
                                        bytes.toString(Charsets.UTF_8),
                                        bytes,
                                        response.request.url.toString(),
                                    ),
                                )
                            }
                        } catch (error: Exception) {
                            callback?.onError(error)
                        } finally {
                            cancellable.setFinished()
                        }
                    }
                },
            )
            return cancellable
        }

        private fun buildRequest(request: PipePipeRequest): Request {
            val builder =
                Request.Builder()
                    .url(request.url())
                    .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
                    .header("User-Agent", USER_AGENT)
            request.headers().forEach { (name, values) ->
                builder.removeHeader(name)
                values.forEach { builder.addHeader(name, it) }
            }
            return builder.build()
        }
    }

    private class BraveHttpDownloader : BraveDownloader() {
        private val client = OkHttpClient()

        @Throws(IOException::class, BraveReCaptchaException::class)
        override fun execute(request: BraveRequest): BraveResponse {
            val builder =
                Request.Builder()
                    .url(request.url())
                    .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
                    .header("User-Agent", USER_AGENT)
            request.headers().forEach { (name, values) ->
                builder.removeHeader(name)
                values.forEach { builder.addHeader(name, it) }
            }
            client.newCall(builder.build()).execute().use { response ->
                if (response.code == 429) throw BraveReCaptchaException("YouTube requested a challenge", request.url())
                return BraveResponse(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    response.body?.string(),
                    response.request.url.toString(),
                )
            }
        }
    }
}
