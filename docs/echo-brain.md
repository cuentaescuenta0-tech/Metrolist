# Echo Brain en Metrolist

## Resumen

Echo Brain es un planificador local de reproducción continua. No sustituye el catálogo de YouTube ni necesita una cuenta de nube para aprender: observa señales de la sesión, calcula un orden de candidatos y añade bloques de recomendaciones al reproductor. El perfil de aprendizaje se conserva en el almacenamiento local de la aplicación y sólo se modifica mediante eventos explícitos de reproducción.

## Cadena de extracción

La reproducción intenta resolver cada canción en este orden:

1. **Faraday local.** El dispositivo obtiene el identificador del player de YouTube, descarga el JavaScript público correspondiente y lo ejecuta en un runtime QuickJS aislado. La tabla de configuración remota sólo aporta los nombres de las funciones y clases que YouTube cambió; no contiene firmas decodificadas. Se validan los resultados con probes, límites de tamaño, caché y un mutex para evitar runtimes concurrentes.
2. **`api.pipepipe.dev`.** Si el descifrado local o el primer intento de PipePipe no resuelve un stream, se desactiva el decoder local y PipePipe usa su ruta remota como respaldo.
3. **BravePipe.** Si los dos niveles anteriores fallan, se intenta un extractor independiente. Está pensado como último recurso personal y también registra la fuente utilizada.

Cada `PlaybackData` conserva `streamClient`. La caché de URLs lo mantiene junto con la expiración, por lo que los diagnósticos pueden distinguir `InnerTube`, `Faraday local`, `api.pipepipe.dev` y `BravePipe` sin guardar URLs efímeras como datos permanentes.

## Cómo inyecta Echo Brain

Cuando una pista entra en transición, Echo Brain comprueba si el siguiente bloque necesita relleno. Obtiene candidatos de relaciones/radio, elimina la pista actual, duplicados, versiones alternativas y artistas en cooldown, y después aplica un ranking. Los candidatos que pasan los filtros se insertan con `addMediaItems` en el índice siguiente del reproductor; no se reescribe la cola original ni se depende de que la cola inicial sea una playlist completa.

El planificador evita inyectar durante una repetición de una sola pista, conserva diversidad de artistas y permite un modo dominante que mantiene un bloque de recomendaciones delante del usuario. La inyección se repite al alcanzar el borde del bloque anterior, de modo que el reproductor puede continuar aunque la cola original haya terminado.

## Aprendizaje incremental inspirado en FlowNeuro

No se incorpora Flow como dependencia binaria. Se portan ideas compatibles con GPL/FOSS:

- activación por recencia con decaimiento temporal;
- afinidad por artista y género;
- co-ocurrencia de artistas escuchados en la misma sesión;
- contexto horario;
- apetito de descubrimiento y novedad adyacente al gusto conocido;
- penalización temporal de skips tempranos;
- retroalimentación de secuencias: una transición escuchada mejora el orden futuro y una transición rechazada lo reduce;
- separación estricta entre filtros de seguridad y el modelo de ordenación: el modelo nunca puede reintroducir una canción bloqueada.

El perfil compacto se serializa en DataStore con versión de esquema y recuperación frente a corrupción. Para que el conocimiento no desaparezca por un fallo puntual, las escrituras son atómicas, se conserva el último perfil válido y se incluyen exportación/importación y un reinicio explícito como acciones separadas. La aplicación no pretende aprender una vez por hora usando un servidor: aprende cada vez que hay nueva evidencia. El mantenimiento periódico puede compactar el perfil cuando la app vuelve a estar activa, sin inventar eventos ni consumir batería en segundo plano.

## ¿Puede inyectar todas las canciones sin la cola normal?

Puede mantener una reproducción prácticamente continua **sin depender de que la cola original contenga canciones futuras**: se añaden bloques nuevos después de la pista actual y se vuelve a rellenar antes del final. No puede inyectar literalmente todas las canciones existentes en una sola cola: el catálogo es dinámico, las respuestas tienen paginación, los streams caducan, YouTube puede limitar solicitudes y guardar millones de elementos produciría consumo de memoria y una experiencia peor. El diseño recomendado es un buffer deslizante de candidatos, con deduplicación persistente y recuperación bajo demanda.

## Compilación FOSS

El workflow `.github/workflows/echo-brain-neuro-foss-ci.yml` configura JDK 21 y Android API 37, ejecuta las pruebas de `FossDebug`, compila `assembleFossDebug`, verifica la firma y rechaza descriptores de Google Play Services, Firebase, Cast y Sentry en el APK. El artefacto incluye el APK universal y su SHA-256. La rama de integración usa una clave de depuración efímera en CI; para distribución pública debe usarse una clave privada guardada en secretos de GitHub.

## Limitaciones y seguridad

Los extractores de YouTube deben utilizarse conforme a la legislación, los términos aplicables y los derechos del contenido. El código no intenta eludir autenticación ni almacena credenciales. Las tablas de player y los scripts se descargan mediante HTTPS, se validan antes de ejecutarse y se mantienen dentro de límites de tamaño. `BravePipe` queda documentado como fallback personal, no como garantía de disponibilidad.
