# Vidtube

App Android estilo YouTube, sin anuncios, para uso personal.

## Qué trae
- Inicio "Para ti" que aprende de lo que ves (sin cuenta de Google)
- Controles y título en el Bluetooth del carro, audífonos y pantalla de bloqueo (⏮ ⏯ ⏭)
- Inicio con chips (Todo, Música, Videojuegos, Películas, Podcasts, En vivo)
- Búsqueda con sugerencias y búsquedas recientes
- Reproductor con calidad elegible (hasta 1080p), pantalla completa y mini reproductor
- Reproducción en segundo plano (pantalla apagada) e imagen en imagen
- Canales con botón Suscribirse y feed de Suscripciones
- Comentarios, descripción, videos relacionados
- Historial, Ver más tarde y continuar donde te quedaste
- Reproducción automática del siguiente video relacionado
- Pantalla completa con zoom (pellizcar o botón "Llenar pantalla")
- Descargas de video (hasta 1080p) o solo audio, en Descargas/Vidtube
- Abre links de YouTube compartidos desde otras apps (WhatsApp, navegador…)

Todo lo personal (suscripciones, historial) se guarda solo en el celular. No usa cuenta de Google.

## Compilar con Android Studio
1. Abre Android Studio → **File › Open** → elige esta carpeta `Vidtube`.
2. Espera a que termine el "Gradle Sync" (la primera vez descarga dependencias, tarda unos minutos).
3. Conecta tu celular con depuración USB y dale ▶ **Run**, o genera el APK con
   **Build › Build App Bundle(s) / APK(s) › Build APK(s)**.
4. El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Compilar sin instalar nada (GitHub)
1. Sube esta carpeta a un repositorio de GitHub (puede ser privado).
2. Entra a la pestaña **Actions**: se compila solo.
3. Al terminar, descarga el artefacto **Vidtube-apk** e instálalo en el celular.

## Si un día deja de reproducir
YouTube cambia su sistema seguido. La solución casi siempre es actualizar el motor:
en `app/build.gradle.kts` cambia el código después de `NewPipeExtractor:` por el que use
la última versión de NewPipe (archivo `gradle/libs.versions.toml` de su repositorio,
línea `teamnewpipe-newpipe-extractor`) y vuelve a compilar.

## Licencia
Usa NewPipe Extractor y partes de NewPipe (GPLv3). Solo para uso personal.
