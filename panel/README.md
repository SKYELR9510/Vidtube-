# Panel de enlaces de Vidtube

Página web con contraseña para agregar enlaces a películas y series. La app Vidtube los lee
y los muestra en cada ficha («Tus enlaces → Del panel») y en la fila «En tu panel».

## Instalar (SiteGround u otro hosting con PHP 8 y MySQL)

1. **Base de datos:** Site Tools → MySQL → crea una base de datos y un usuario con todos los permisos.
2. **Subir archivos:** Site Tools → File Manager → `public_html` → crea la carpeta `vidtube-panel`
   y sube: `index.php`, `api.php`, `lib.php`, `panel.js`, `.htaccess`, `config.example.php`.
3. **Configurar:** copia `config.example.php` como `config.php` y completa:
   - `db_dsn`, `db_user`, `db_pass` (de la base del paso 1).
   - `api_key`: una clave larga y secreta (mínimo 24 caracteres).
   - `tmdb_token`: el token de TMDB.
4. **Contraseña:** abre `https://TU-DOMINIO/vidtube-panel/`, escribe tu contraseña en
   «Generar hash», copia el resultado en `admin_password_hash` de `config.php` y recarga.
5. **App:** Vidtube → Configuración → Películas → *Panel de enlaces*: dirección
   `https://TU-DOMINIO/vidtube-panel/` y la misma `api_key`. Toca «Guardar y probar».
   (O guárdalas como secretos `VIDTUBE_PANEL_URL` y `VIDTUBE_PANEL_KEY` en GitHub para que vengan incluidas.)

La tabla `vt_links` se crea sola la primera vez.

## Seguridad
- Solo HTTPS; `config.php` y `lib.php` no se pueden abrir desde el navegador (`.htaccess`).
- Contraseña con hash, 5 intentos fallidos = 10 minutos de espera, protección CSRF en todos los formularios.
- Solo se aceptan enlaces `http/https`. La app solo lee (no puede modificar nada del panel).
