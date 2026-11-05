# Publicar Vidtube en GitHub

## 1. Clave de firma (una sola vez)
Todas las versiones deben firmarse con la misma clave para que se actualicen encima.
Guarda la clave (`vidtube-release.jks`) y sus contraseñas en un lugar seguro (Drive).
**Nunca** la subas al repositorio (el `.gitignore` ya la excluye).

## 2. Secretos en GitHub
Repositorio → **Settings → Secrets and variables → Actions → New repository secret**:

| Nombre | Valor |
|---|---|
| `VIDTUBE_KEYSTORE_BASE64` | contenido de `keystore-base64.txt` |
| `VIDTUBE_STORE_PASSWORD` | contraseña del almacén |
| `VIDTUBE_KEY_ALIAS` | `vidtube` |
| `VIDTUBE_KEY_PASSWORD` | contraseña de la clave |

## 3. Subir el código
Sube todo el contenido de la carpeta `Vidtube` (incluida la carpeta `.github`),
excepto `build`, `.gradle`, `.idea` y la clave.

## 4. Qué pasa después
- Cada cambio en `main` → GitHub compila y publica **Vidtube-XXX.apk** en *Releases*.
- Todos los días a las 6:17 a. m. revisa si NewPipe actualizó el extractor; si sí,
  lo aplica, compila y publica solo.
- Las apps instaladas ven el aviso **"Nueva versión de Vidtube → Actualizar"** al abrir.

Para compartir: pasa el enlace `https://github.com/SKYELR9510/Vidtube-/releases/latest`.
La primera vez se instala desde ahí; luego se actualiza desde la misma app.
