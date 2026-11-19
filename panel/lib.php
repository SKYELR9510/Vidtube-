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

// ---------- Importar y exportar canales (Excel, CSV o lista M3U) ----------

/** Columnas de la plantilla, en orden. */
function channel_columns(): array {
    return ['nombre', 'enlace', 'respaldo1', 'respaldo2', 'youtube', 'logo', 'portada', 'categoria', 'nacional', 'orden'];
}

/** Nombre de columna escrito de cualquier forma → columna de la plantilla (o null). */
function channel_column(string $h): ?string {
    $k = strtolower(trim($h));
    $k = strtr($k, ['á' => 'a', 'é' => 'e', 'í' => 'i', 'ó' => 'o', 'ú' => 'u', 'ñ' => 'n']);
    $k = preg_replace('/[^a-z0-9]/', '', $k);
    $map = [
        'nombre' => 'nombre', 'name' => 'nombre', 'canal' => 'nombre', 'titulo' => 'nombre',
        'enlace' => 'enlace', 'url' => 'enlace', 'link' => 'enlace', 'senal' => 'enlace', 'enlaceprincipal' => 'enlace', 'principal' => 'enlace', 'm3u8' => 'enlace',
        'respaldo1' => 'respaldo1', 'respaldo' => 'respaldo1', 'backup1' => 'respaldo1', 'backup' => 'respaldo1', 'url2' => 'respaldo1',
        'respaldo2' => 'respaldo2', 'backup2' => 'respaldo2', 'url3' => 'respaldo2',
        'youtube' => 'youtube', 'canaldeyoutube' => 'youtube', 'canaloficialdeyoutube' => 'youtube', 'yt' => 'youtube',
        'logo' => 'logo', 'tvglogo' => 'logo', 'imagen' => 'logo',
        'portada' => 'portada', 'cover' => 'portada', 'fondo' => 'portada',
        'categoria' => 'categoria', 'category' => 'categoria', 'grupo' => 'categoria', 'grouptitle' => 'categoria', 'genero' => 'categoria',
        'nacional' => 'nacional', 'national' => 'nacional', 'peru' => 'nacional',
        'orden' => 'orden', 'position' => 'orden', 'posicion' => 'orden', 'order' => 'orden',
    ];
    return $map[$k] ?? null;
}

/** Lee la primera hoja de un .xlsx (sin librerías: es un zip con XML). Devuelve filas de textos. */
function read_xlsx(string $path): array {
    if (!class_exists('ZipArchive')) throw new RuntimeException('Tu hosting no tiene la extensión zip de PHP: guarda el Excel como CSV y súbelo así.');
    $zip = new ZipArchive();
    if ($zip->open($path) !== true) throw new RuntimeException('No se pudo abrir el archivo de Excel.');
    $strings = [];
    if (($xml = $zip->getFromName('xl/sharedStrings.xml')) !== false) {
        $sx = simplexml_load_string($xml, 'SimpleXMLElement', LIBXML_NONET);
        foreach ($sx->si as $si) {
            $t = '';
            if (isset($si->t)) $t = (string)$si->t;
            foreach ($si->r as $r) $t .= (string)$r->t;
            $strings[] = $t;
        }
    }
    // Primera hoja según el libro (por si no se llama sheet1.xml)
    $sheet = 'xl/worksheets/sheet1.xml';
    $wb = $zip->getFromName('xl/workbook.xml');
    $rels = $zip->getFromName('xl/_rels/workbook.xml.rels');
    if ($wb !== false && $rels !== false) {
        $w = simplexml_load_string($wb, 'SimpleXMLElement', LIBXML_NONET);
        $first = $w->sheets->sheet[0] ?? null;
        if ($first) {
            $rid = (string)$first->attributes('http://schemas.openxmlformats.org/officeDocument/2006/relationships')['id'];
            $r = simplexml_load_string($rels, 'SimpleXMLElement', LIBXML_NONET);
            foreach ($r->Relationship as $rel) {
                if ((string)$rel['Id'] === $rid) {
                    $target = ltrim((string)$rel['Target'], '/');
                    $sheet = strpos($target, 'xl/') === 0 ? $target : 'xl/' . $target;
                }
            }
        }
    }
    $xml = $zip->getFromName($sheet);
    $zip->close();
    if ($xml === false) throw new RuntimeException('El Excel no tiene hojas con datos.');
    $sx = simplexml_load_string($xml, 'SimpleXMLElement', LIBXML_NONET);
    $rows = [];
    foreach ($sx->sheetData->row as $row) {
        $cells = [];
        foreach ($row->c as $c) {
            $ref = (string)$c['r'];
            $col = 0;
            for ($i = 0; $i < strlen($ref) && ctype_alpha($ref[$i]); $i++) $col = $col * 26 + (ord(strtoupper($ref[$i])) - 64);
            $type = (string)$c['t'];
            if ($type === 's') $v = $strings[(int)$c->v] ?? '';
            elseif ($type === 'inlineStr') $v = (string)$c->is->t;
            elseif ($type === 'b') $v = ((string)$c->v) === '1' ? 'si' : 'no';
            else $v = (string)$c->v;
            $cells[max(0, $col - 1)] = $v;
        }
        if ($cells) {
            $max = max(array_keys($cells));
            $line = [];
            for ($i = 0; $i <= $max; $i++) $line[] = trim((string)($cells[$i] ?? ''));
            $rows[] = $line;
        }
    }
    return $rows;
}

/** Lee un CSV (separado por ; , o tabulación, como lo guarda Excel). */
function read_csv(string $path): array {
    $text = (string)file_get_contents($path);
    if (strncmp($text, "\xEF\xBB\xBF", 3) === 0) $text = substr($text, 3);
    if (!mb_check_encoding($text, 'UTF-8')) $text = mb_convert_encoding($text, 'UTF-8', 'Windows-1252');
    $firstLine = strtok($text, "\n");
    $sep = ';';
    $best = 0;
    foreach ([';', ',', "\t"] as $s) { $n = substr_count((string)$firstLine, $s); if ($n > $best) { $best = $n; $sep = $s; } }
    $fh = fopen('php://memory', 'r+');
    fwrite($fh, $text);
    rewind($fh);
    $rows = [];
    while (($r = fgetcsv($fh, 0, $sep, '"', '\\')) !== false) {
        if ($r === [null]) continue;
        $rows[] = array_map(function ($v) { return trim((string)$v); }, $r);
    }
    fclose($fh);
    return $rows;
}

/** Lee una lista .m3u de IPTV: nombre, logo, grupo (categoría) y cabeceras #EXTVLCOPT. */
function read_m3u(string $path): array {
    $rows = [['nombre', 'enlace', 'logo', 'categoria']];
    $name = $logo = $group = '';
    $opts = [];
    foreach (preg_split('/\r?\n/', (string)file_get_contents($path)) as $line) {
        $line = trim($line);
        if ($line === '' || $line === '#EXTM3U') continue;
        if (stripos($line, '#EXTINF') === 0) {
            $name = trim((string)substr($line, (int)strrpos($line, ',') + 1));
            $logo = preg_match('/tvg-logo="([^"]*)"/i', $line, $m) ? $m[1] : '';
            $group = preg_match('/group-title="([^"]*)"/i', $line, $m) ? $m[1] : '';
            $opts = [];
        } elseif (stripos($line, '#EXTVLCOPT:http-user-agent=') === 0) {
            $opts['User-Agent'] = substr($line, 27);
        } elseif (stripos($line, '#EXTVLCOPT:http-referrer=') === 0) {
            $opts['Referer'] = substr($line, 25);
        } elseif ($line[0] !== '#') {
            $url = $line;
            if ($opts && strpos($url, '|') === false) {
                $url .= '|' . implode('&', array_map(function ($k, $v) { return $k . '=' . $v; }, array_keys($opts), $opts));
            }
            if ($name !== '') $rows[] = [$name, $url, $logo, $group];
            $name = $logo = $group = '';
            $opts = [];
        }
    }
    return $rows;
}

/** Categoría escrita libremente (o el grupo de una lista M3U) → una de las del panel. */
function guess_category(string $raw): string {
    $k = strtolower(strtr(trim($raw), ['á' => 'a', 'é' => 'e', 'í' => 'i', 'ó' => 'o', 'ú' => 'u']));
    foreach (channel_categories() as $c) {
        if ($k === strtolower(strtr($c, ['ú' => 'u', 'í' => 'i']))) return $c;
    }
    if (preg_match('/news|notic/', $k)) return 'Noticias';
    if (preg_match('/sport|deport|futbol|football|soccer/', $k)) return 'Deportes';
    if (preg_match('/music|musica|radio/', $k)) return 'Música';
    if (preg_match('/kid|infantil|nino|cartoon|anima/', $k)) return 'Infantil';
    if (preg_match('/movie|pelic|cine/', $k)) return 'Películas';
    if (preg_match('/entert|entreten|series|show/', $k)) return 'Entretenimiento';
    return 'General';
}

/**
 * Importa canales desde filas (la primera fila = nombres de columna). Si un canal con el mismo
 * nombre ya existe se actualiza; si no, se agrega. Devuelve [agregados, actualizados, errores[]].
 */
function import_channels(array $rows): array {
    if (count($rows) < 2) throw new RuntimeException('El archivo no tiene filas de canales (la primera fila deben ser los nombres de columna).');
    $cols = [];
    foreach ($rows[0] as $i => $h) { $c = channel_column((string)$h); if ($c !== null && !isset($cols[$c])) $cols[$c] = $i; }
    if (!isset($cols['nombre'])) throw new RuntimeException('Falta la columna «nombre». Usa la plantilla.');
    if (!isset($cols['enlace']) && !isset($cols['youtube'])) throw new RuntimeException('Falta la columna «enlace» (o «youtube»). Usa la plantilla.');
    $pdo = db();
    $existing = [];
    foreach ($pdo->query('SELECT id, name, position FROM vt_channels')->fetchAll() as $r) $existing[mb_strtolower(trim($r['name']))] = $r;
    $next = (int)$pdo->query('SELECT COALESCE(MAX(position), 0) FROM vt_channels')->fetchColumn();
    $added = $updated = 0;
    $errors = [];
    $ins = $pdo->prepare('INSERT INTO vt_channels (name, url, url2, url3, youtube, logo, cover, category, national, position, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)');
    $upd = $pdo->prepare('UPDATE vt_channels SET url = ?, url2 = ?, url3 = ?, youtube = ?, logo = ?, cover = ?, category = ?, national = ?, position = ?, updated_at = ? WHERE id = ?');
    $pdo->beginTransaction();
    try {
        foreach (array_slice($rows, 1) as $n => $r) {
            $line = $n + 2;
            $get = function (string $c) use ($cols, $r) { return isset($cols[$c]) ? trim((string)($r[$cols[$c]] ?? '')) : ''; };
            $name = mb_substr($get('nombre'), 0, 80);
            if ($name === '' && implode('', $r) === '') continue; // fila vacía
            if ($name === '') { $errors[] = "Fila $line: falta el nombre."; continue; }
            $u = []; $bad = null;
            foreach (['enlace', 'respaldo1', 'respaldo2'] as $c) {
                $v = $get($c);
                $u[$c] = $v === '' ? '' : clean_url($v);
                if ($u[$c] === null) $bad = "el $c no es un enlace válido";
            }
            $yt = $get('youtube');
            if ($yt !== '') {
                $yt = clean_url($yt);
                $host = $yt ? strtolower((string)parse_url($yt, PHP_URL_HOST)) : '';
                if ($yt === null || !preg_match('~(^|\.)youtube\.com$~', $host)) $bad = 'el canal de YouTube no es válido';
            } else $yt = null;
            $img = [];
            foreach (['logo', 'portada'] as $c) {
                $v = $get($c);
                $img[$c] = $v === '' ? null : clean_url($v);
                if ($v !== '' && ($img[$c] === null || !starts_with($img[$c], 'https://'))) $bad = "el $c debe ser un enlace https://";
            }
            if ($bad) { $errors[] = "Fila $line ($name): $bad."; continue; }
            if ($u['enlace'] === '' && $yt === null) { $errors[] = "Fila $line ($name): falta el enlace o el canal de YouTube."; continue; }
            $cat = guess_category($get('categoria'));
            $nat = preg_match('/^(si|sí|s|x|1|yes|true|verdadero)$/i', $get('nacional')) ? 1 : 0;
            $key = mb_strtolower($name);
            $pos = $get('orden') !== '' && is_numeric($get('orden')) ? (int)$get('orden') : null;
            $vals = [$u['enlace'], $u['respaldo1'] ?: null, $u['respaldo2'] ?: null, $yt, $img['logo'], $img['portada'], $cat, $nat];
            if (isset($existing[$key])) {
                $upd->execute(array_merge($vals, [$pos ?? (int)$existing[$key]['position'], now(), (int)$existing[$key]['id']]));
                $updated++;
            } else {
                $ins->execute(array_merge([$name], $vals, [$pos ?? ++$next, now(), now()]));
                $existing[$key] = ['id' => (int)$pdo->lastInsertId(), 'position' => $pos ?? $next];
                $added++;
            }
        }
        $pdo->commit();
    } catch (Throwable $e) {
        $pdo->rollBack();
        throw $e;
    }
    return [$added, $updated, $errors];
}

/** Envía un CSV que Excel abre bien (UTF-8 con BOM, separado por punto y coma). */
function send_csv(string $filename, array $rows): void {
    header('Content-Type: text/csv; charset=utf-8');
    header('Content-Disposition: attachment; filename="' . $filename . '"');
    header('Cache-Control: no-store');
    $out = fopen('php://output', 'w');
    fwrite($out, "\xEF\xBB\xBF");
    foreach ($rows as $r) fputcsv($out, $r, ';', '"', '\\');
    fclose($out);
    exit;
}
