<?php
declare(strict_types=1);

// Librería común del panel. No se abre directamente (lo bloquea .htaccess).
if (basename($_SERVER['SCRIPT_FILENAME'] ?? '') === 'lib.php') { http_response_code(404); exit; }

function cfg(): array {
    static $c = null;
    if ($c === null) {
        $file = __DIR__ . '/config.php';
        if (!is_file($file)) {
            http_response_code(500);
            exit('Falta config.php: copia config.example.php como config.php y complétalo.');
        }
        $c = require $file;
    }
    return $c;
}

function db(): PDO {
    static $pdo = null;
    if ($pdo) return $pdo;
    $c = cfg();
    try {
        $pdo = new PDO($c['db_dsn'], $c['db_user'] ?? null, $c['db_pass'] ?? null, [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES => false,
        ]);
    } catch (PDOException $e) {
        throw new RuntimeException('No se pudo conectar con la base de datos. Revisa db_dsn, db_user y db_pass en config.php. Detalle: ' . $e->getMessage());
    }
    $sqlite = starts_with((string)$c['db_dsn'], 'sqlite:');
    $pdo->exec($sqlite
        ? "CREATE TABLE IF NOT EXISTS vt_links (
             id INTEGER PRIMARY KEY AUTOINCREMENT,
             kind TEXT NOT NULL, tmdb_id INTEGER NOT NULL, title TEXT NOT NULL, year INTEGER NULL,
             poster TEXT NULL, name TEXT NOT NULL, url TEXT NOT NULL,
             created_at TEXT NOT NULL, updated_at TEXT NOT NULL)"
        : "CREATE TABLE IF NOT EXISTS vt_links (
             id INT AUTO_INCREMENT PRIMARY KEY,
             kind VARCHAR(5) NOT NULL, tmdb_id BIGINT NOT NULL, title VARCHAR(255) NOT NULL, year INT NULL,
             poster VARCHAR(255) NULL, name VARCHAR(80) NOT NULL, url TEXT NOT NULL,
             created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
             INDEX idx_title (kind, tmdb_id)
           ) DEFAULT CHARSET=utf8mb4");
    $pdo->exec($sqlite
        ? "CREATE TABLE IF NOT EXISTS vt_channels (
             id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, url TEXT NOT NULL, logo TEXT NULL,
             position INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL, updated_at TEXT NOT NULL)"
        : "CREATE TABLE IF NOT EXISTS vt_channels (
             id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(80) NOT NULL, url TEXT NOT NULL, logo TEXT NULL,
             position INT NOT NULL DEFAULT 0, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL
           ) DEFAULT CHARSET=utf8mb4");
    // Respaldo de señal (paneles instalados antes no tienen estas columnas)
    $extra = [
        'url2' => 'TEXT NULL', 'url3' => 'TEXT NULL', 'youtube' => 'TEXT NULL',
        // Diseño de TV abierta: categoría, si es canal nacional y portada
        'category' => "VARCHAR(30) NOT NULL DEFAULT 'General'", 'national' => 'INT NOT NULL DEFAULT 0', 'cover' => 'TEXT NULL',
    ];
    foreach ($extra as $col => $type) {
        try { $pdo->query("SELECT $col FROM vt_channels LIMIT 1"); }
        catch (PDOException $e) { $pdo->exec("ALTER TABLE vt_channels ADD COLUMN $col $type"); }
    }
    return $pdo;
}

/** Categorías de los canales de TV (las mismas que muestra la app). */
function channel_categories(): array {
    return ['General', 'Noticias', 'Deportes', 'Música', 'Infantil', 'Películas', 'Entretenimiento'];
}

/** Igual que str_starts_with (para hostings con PHP 7.4). */
function starts_with(string $s, string $p): bool { return strncmp($s, $p, strlen($p)) === 0; }

function h(?string $s): string { return htmlspecialchars((string)$s, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8'); }

function now(): string { return gmdate('Y-m-d H:i:s'); }

/**
 * Solo enlaces http/https con dominio (mismas reglas que la app). Acepta cabeceras al estilo
 * IPTV después de «|»: https://…/canal.m3u8|User-Agent=VLC/3.0&Referer=https://…
 */
function clean_url(string $raw): ?string {
    $s = trim($raw);
    $opts = '';
    $pipe = strpos($s, '|');
    if ($pipe !== false) {
        $opts = trim(substr($s, $pipe + 1));
        $s = trim(substr($s, 0, $pipe));
        if (strlen($opts) > 600 || preg_match('/[\x00-\x1F]/', $opts)) return null;
    }
    if ($s === '' || strlen($s) > 2000 || preg_match('/\s|[\x00-\x1F]/', $s)) return null;
    if (!preg_match('~^[a-z][a-z0-9+.-]*:~i', $s)) $s = 'https://' . $s;
    $p = parse_url($s);
    if (!$p || !isset($p['scheme'], $p['host'])) return null;
    $scheme = strtolower($p['scheme']);
    if ($scheme !== 'http' && $scheme !== 'https') return null;
    if (strpos($p['host'], '.') === false) return null;
    return $opts !== '' && link_headers('x|' . $opts) ? $s . '|' . $opts : $s;
}

/** Cabeceras IPTV permitidas de un enlace (User-Agent, Referer, Origin, Cookie). */
function link_headers(string $url): array {
    $pipe = strpos($url, '|');
    if ($pipe === false) return [];
    $allowed = ['user-agent' => 'User-Agent', 'referer' => 'Referer', 'referrer' => 'Referer', 'origin' => 'Origin', 'cookie' => 'Cookie'];
    $out = [];
    foreach (explode('&', substr($url, $pipe + 1)) as $part) {
        $eq = strpos($part, '=');
        if ($eq === false || $eq === 0) continue;
        $k = strtolower(trim(substr($part, 0, $eq)));
        if (!isset($allowed[$k])) continue;
        $v = trim(urldecode(substr($part, $eq + 1)));
        if ($v !== '' && strlen($v) <= 400 && !preg_match('/[\x00-\x1F]/', $v)) $out[$allowed[$k]] = $v;
    }
    return $out;
}

// ---------- Sesión, inicio de sesión y CSRF ----------

function start_session(): void {
    if (session_status() === PHP_SESSION_ACTIVE) return;
    session_name('vtpanel');
    session_set_cookie_params([
        'lifetime' => 0, 'path' => '/', 'secure' => !empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off',
        'httponly' => true, 'samesite' => 'Strict',
    ]);
    session_start();
}

function logged_in(): bool { start_session(); return !empty($_SESSION['vt_admin']); }

function csrf_token(): string {
    start_session();
    if (empty($_SESSION['vt_csrf'])) $_SESSION['vt_csrf'] = bin2hex(random_bytes(32));
    return $_SESSION['vt_csrf'];
}

function check_csrf(): void {
    start_session();
    $t = $_POST['csrf'] ?? '';
    if (!is_string($t) || empty($_SESSION['vt_csrf']) || !hash_equals($_SESSION['vt_csrf'], $t)) {
        http_response_code(400);
        exit('La página caducó. Vuelve atrás y recarga.');
    }
}

/** Freno simple contra adivinar la contraseña: 5 intentos fallidos = 10 minutos de espera. */
function login_locked(): bool {
    start_session();
    $f = $_SESSION['vt_fail'] ?? ['n' => 0, 't' => 0];
    return $f['n'] >= 5 && time() - $f['t'] < 600;
}

function try_login(string $user, string $pass): bool {
    start_session();
    $c = cfg();
    $ok = hash_equals((string)$c['admin_user'], $user)
        && ($c['admin_password_hash'] ?? '') !== ''
        && password_verify($pass, $c['admin_password_hash']);
    if ($ok) {
        session_regenerate_id(true);
        $_SESSION['vt_admin'] = true;
        $_SESSION['vt_fail'] = ['n' => 0, 't' => 0];
    } else {
        $f = $_SESSION['vt_fail'] ?? ['n' => 0, 't' => 0];
        $_SESSION['vt_fail'] = ['n' => $f['n'] + 1, 't' => time()];
        usleep(400000);
    }
    return $ok;
}

// ---------- TMDB (solo para buscar títulos en el panel) ----------

function tmdb(string $path, array $q = []): ?array {
    $token = trim((string)(cfg()['tmdb_token'] ?? ''));
    if ($token === '') return null;
    $q += ['language' => 'es-MX'];
    $url = 'https://api.themoviedb.org/3/' . $path . '?' . http_build_query($q);
    $ch = curl_init($url);
    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true, CURLOPT_TIMEOUT => 12,
        CURLOPT_HTTPHEADER => ['Authorization: Bearer ' . $token, 'Accept: application/json'],
    ]);
    $body = curl_exec($ch);
    $code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);
    if ($code !== 200 || !$body) return null;
    $j = json_decode($body, true);
    return is_array($j) ? $j : null;
}

/** Busca películas y series. Devuelve [kind, id, title, year, poster]. */
function tmdb_search(string $query): array {
    $j = tmdb('search/multi', ['query' => $query, 'include_adult' => 'false']);
    $out = [];
    foreach ($j['results'] ?? [] as $r) {
        $type = $r['media_type'] ?? '';
        if ($type !== 'movie' && $type !== 'tv') continue;
        $title = $type === 'tv' ? ($r['name'] ?? '') : ($r['title'] ?? '');
        if ($title === '') continue;
        $date = $type === 'tv' ? ($r['first_air_date'] ?? '') : ($r['release_date'] ?? '');
        $out[] = [
            'kind' => $type, 'id' => (int)$r['id'], 'title' => $title,
            'year' => $date ? (int)substr($date, 0, 4) : null,
            'poster' => !empty($r['poster_path']) ? $r['poster_path'] : null,
        ];
    }
    return array_slice($out, 0, 12);
}


/** Detalles de una película o serie (sinopsis, fondo, géneros, duración/temporadas). */
function tmdb_details(string $kind, int $id): ?array {
    $j = tmdb(($kind === 'tv' ? 'tv' : 'movie') . '/' . $id);
    if (!$j) return null;
    $genres = array_map(function ($g) { return $g['name']; }, $j['genres'] ?? []);
    return [
        'overview' => $j['overview'] ?? '',
        'backdrop' => $j['backdrop_path'] ?? null,
        'poster' => $j['poster_path'] ?? null,
        'genres' => $genres,
        'rating' => isset($j['vote_average']) ? round((float)$j['vote_average'], 1) : null,
        'runtime' => $kind === 'tv' ? null : ($j['runtime'] ?? null),
        'seasons' => $kind === 'tv' ? ($j['number_of_seasons'] ?? null) : null,
    ];
}

/** ¿El enlace responde? Devuelve [ok, texto]. Prueba también como VLC, como hace la app. */
function check_url(string $url): array {
    $u = clean_url($url);
    if ($u === null) return [false, 'Enlace no válido'];
    $headers = link_headers($u);
    $base = explode('|', $u, 2)[0];
    $agents = isset($headers['User-Agent']) ? [$headers['User-Agent']]
        : ['Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36', 'VLC/3.0.20 LibVLC/3.0.20'];
    $last = [false, 'No responde'];
    foreach ($agents as $i => $ua) {
        $h = [];
        foreach ($headers as $k => $v) if ($k !== 'User-Agent') $h[] = "$k: $v";
        $ch = curl_init($base);
        curl_setopt_array($ch, [
            CURLOPT_FOLLOWLOCATION => true, CURLOPT_MAXREDIRS => 5, CURLOPT_TIMEOUT => 12, CURLOPT_RETURNTRANSFER => true,
            CURLOPT_RANGE => '0-2047', // basta con el inicio (la lista .m3u8 o los primeros bytes del video)
            CURLOPT_PROTOCOLS => CURLPROTO_HTTP | CURLPROTO_HTTPS, CURLOPT_REDIR_PROTOCOLS => CURLPROTO_HTTP | CURLPROTO_HTTPS,
            CURLOPT_USERAGENT => $ua, CURLOPT_HTTPHEADER => $h,
        ]);
        $body = (string)curl_exec($ch);
        $code = (int)curl_getinfo($ch, CURLINFO_HTTP_CODE);
        $type = (string)curl_getinfo($ch, CURLINFO_CONTENT_TYPE);
        $err = curl_error($ch);
        curl_close($ch);
        if ($code === 0) { $last = [false, 'No responde' . ($err ? " ($err)" : '')]; continue; }
        if ($code >= 400) {
            $why = $code === 403 ? ' — el enlace venció (token) o el servidor bloquea la conexión'
                : ($code === 404 ? ' — el enlace ya no existe' : '');
            $last = [false, "Error HTTP $code$why"];
            continue;
        }
        $kind = strpos($body, '#EXTM3U') === 0 ? 'lista de señal HLS' : (stripos($type, 'video') !== false ? 'video' : ($type ?: 'contenido'));
        return [true, "Funciona (HTTP $code · $kind" . ($i > 0 ? ' · solo como VLC: la app lo hará sola' : '') . ')'];
    }
    return $last;
}

/** Comprobaciones de la instalación para la sección Diagnóstico. */
function diagnostics(): array {
    $c = cfg();
    $out = [];
    $out[] = ['PHP', version_compare(PHP_VERSION, '7.4', '>='), 'Versión ' . PHP_VERSION . (version_compare(PHP_VERSION, '7.4', '>=') ? '' : ' (se necesita 7.4 o más)')];
    $out[] = ['Extensión cURL', function_exists('curl_init'), function_exists('curl_init') ? 'Disponible' : 'Falta: actívala en tu hosting'];
    try { db()->query('SELECT 1'); $out[] = ['Base de datos', true, 'Conectada y tablas listas']; }
    catch (Throwable $e) { $out[] = ['Base de datos', false, $e->getMessage()]; }
    $tok = trim((string)($c['tmdb_token'] ?? ''));
    if ($tok === '') $out[] = ['TMDB', false, 'Falta tmdb_token en config.php'];
    else { $ok = tmdb('configuration') !== null; $out[] = ['TMDB', $ok, $ok ? 'Token válido' : 'El token no funciona o TMDB no responde']; }
    $api = (string)($c['api_key'] ?? '');
    $okKey = strlen($api) >= 24 && !starts_with($api, 'CAMBIA_ESTO');
    $out[] = ['Clave de la app', $okKey, $okKey ? 'Configurada (' . strlen($api) . ' caracteres)' : 'Cambia api_key en config.php (mínimo 24 caracteres)'];
    $https = !empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off';
    $out[] = ['HTTPS', $https, $https ? 'Activo' : 'Activa SSL en tu hosting: la app solo se conecta por https'];
    return $out;
}

/** Dirección del panel para ponerla en la app. */
function panel_base_url(): string {
    $host = $_SERVER['HTTP_HOST'] ?? 'tu-dominio';
    $dir = rtrim(str_replace('\\', '/', dirname($_SERVER['SCRIPT_NAME'] ?? '/')), '/');
    return 'https://' . $host . $dir . '/';
}
