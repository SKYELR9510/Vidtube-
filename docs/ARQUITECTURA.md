# Vidtube — arquitectura multiplataforma

Una sola marca y tres apps, cada una con su propia interfaz para su dispositivo.

## Qué hay en el repositorio

| Carpeta | Plataforma | Tecnología | Cómo se compila |
|---|---|---|---|
| `app/` | Android (teléfono y tablet) | Kotlin + Jetpack Compose (Material 3), ExoPlayer, NewPipe Extractor | `.github/workflows/build.yml` → APK firmado en la release `build-N` |
| `pc/` | Windows 10/11 | Electron 33 + JavaScript (sin framework), youtubei.js, Shaka Player | `.github/workflows/pc.yml` → instalador NSIS en la release `pc-1.0.N` |
| `tv/` | Samsung Smart TV (Tizen) | App web Tizen (`.wgt`), mismo JavaScript que la PC, Shaka Player | `.github/workflows/tv.yml` → `.wgt` en la release `tv-1.0.N` |

## Qué se comparte

- **PC y TV** comparten el código de servicios en `pc/src/`:
  - `yt.js`: búsqueda, canales, detalles, comentarios y obtención del video.
  - `store.js`: historial, Mi lista (Ver más tarde), Me gusta, suscripciones, posiciones y ajustes.
  - `icons.js`: íconos.
- **Android** tiene su propia capa de datos en Kotlin (`app/.../data/`: `Repository`, `Library`, `Models`). No se puede compartir con JavaScript sin reescribir una de las dos; no se hizo, porque las dos ya funcionan.
- **Lo común a las tres es la identidad visual (tokens):**

| Token | Valor | Uso |
|---|---|---|
| Negro profundo | `#08090B` | Fondo |
| Negro carbón | `#101114` | Superficies, tarjetas, menú lateral |
| Rojo cinematográfico | `#F20D25` | Acento: botón principal, elemento activo, progreso, EN VIVO |
| Plata | `#BFC3CA` | Texto secundario |
| Blanco | `#FFFFFF` | Texto principal |

  Tema oscuro por defecto y claro opcional, en las tres apps.

  Archivos de tokens:
  - PC: `pc/src/style.css` (`:root`).
  - Android: `ui/Theme.kt` (`VColors`).
  - TV: `tv/style.css` (`:root`).

## Específico de cada plataforma

| | Windows | Android | Samsung TV |
|---|---|---|---|
| **Navegación** | Menú lateral contraíble | Barra inferior de 5 pestañas | Menú lateral con foco |
| **Entrada** | Mouse y teclado (atajos) | Toque y gestos | Control remoto (flechas, OK, Back, teclas multimedia) |
| **Mini reproductor** | Ventana flotante + imagen en imagen | Deslizar el video hacia abajo | No aplica (siempre pantalla completa) |
| **Perfil** | Menú del avatar | Menú del avatar | Configuración |

## Cuentas y datos

- No hay cuentas ni inicio de sesión: los datos se guardan en cada dispositivo.
  - PC y TV: `localStorage`.
  - Android: `SharedPreferences`.
- No hay sincronización entre dispositivos.
- No hay notificaciones en ninguna de las tres apps, por eso no se muestra la campana.
- Al actualizar la app se conservan los datos:
  - El instalador de PC no borra la carpeta de datos de Electron.
  - En Android, la actualización mantiene la misma firma.
