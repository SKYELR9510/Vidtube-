<?php
// Copia este archivo como config.php y completa los datos.
// config.php NO se sube a GitHub (está en .gitignore).

return [
    // Base de datos MySQL (en SiteGround: Site Tools → MySQL → Databases)
    'db_dsn'  => 'mysql:host=localhost;dbname=TU_BASE;charset=utf8mb4',
    'db_user' => 'TU_USUARIO',
    'db_pass' => 'TU_CONTRASEÑA',

    // Usuario del panel y el "hash" de tu contraseña.
    // Para generar el hash: abre el panel en el navegador, toca «Generar hash de contraseña»
    // y pega aquí el texto que te da (empieza con $2y$ o $argon2).
    'admin_user'          => 'admin',
    'admin_password_hash' => '',

    // Clave que usa la app Vidtube para leer los enlaces (texto largo y secreto, mínimo 24 caracteres).
    // La misma clave se pone en Vidtube → Configuración → Panel de enlaces.
    'api_key' => 'CAMBIA_ESTO_POR_UNA_CLAVE_LARGA_Y_SECRETA',

    // Token de lectura de TMDB (el mismo de la app), para buscar películas y series en el panel.
    'tmdb_token' => '',
];
