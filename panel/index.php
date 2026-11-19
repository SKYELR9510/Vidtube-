<?php
declare(strict_types=1);
require __DIR__ . '/lib.php';

// Panel de Vidtube: películas, series y canales de TV con sus enlaces.
header('X-Frame-Options: DENY');
header('X-Content-Type-Options: nosniff');
header("Content-Security-Policy: default-src 'self'; img-src 'self' https://image.tmdb.org https: data:; style-src 'self' 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'");

start_session();
$c = cfg();
$msg = null;
$err = null;
$action = (string)($_POST['action'] ?? '');
$view = (string)($_GET['view'] ?? 'home');
$IMG = 'https://image.tmdb.org/t/p/';

function flash(string $type, string $text): void { $_SESSION['vt_flash'] = [$type, $text]; }
function go(string $qs): void { header('Location: ./' . ($qs !== '' ? '?' . $qs : '')); exit; }

// ---------- Sin sesión: generar hash e iniciar sesión ----------
if ($action === 'hash' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    check_csrf();
    $p = (string)($_POST['password'] ?? '');
    if (strlen($p) < 10) $err = 'Usa una contraseña de al menos 10 caracteres.';
    else $generated = password_hash($p, PASSWORD_DEFAULT);
}
if ($action === 'login' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    check_csrf();
    if (login_locked()) $err = 'Demasiados intentos. Espera 10 minutos.';
    elseif (!try_login((string)($_POST['user'] ?? ''), (string)($_POST['password'] ?? ''))) $err = 'Usuario o contraseña incorrectos.';
    else go('');
}
if ($action === 'logout' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    check_csrf();
    $_SESSION = [];
    session_destroy();
    go('');
}

// ---------- Acciones con sesión (todas por POST + CSRF; después se redirige) ----------
if (logged_in() && $_SERVER['REQUEST_METHOD'] === 'POST' && $action !== '') {
    check_csrf();
    $back = (string)($_POST['back'] ?? '');
    if (!preg_match('~^[a-z0-9_=&%.-]*$~i', $back)) $back = '';
    try {
        $pdo = db();
        switch ($action) {
            case 'add':
                $url = clean_url((string)($_POST['url'] ?? ''));
                $kind = ($_POST['kind'] ?? '') === 'tv' ? 'tv' : 'movie';
                $id = (int)($_POST['tmdb_id'] ?? 0);
                $title = trim(mb_substr((string)($_POST['title'] ?? ''), 0, 255));
                $year = ($_POST['year'] ?? '') !== '' ? (int)$_POST['year'] : null;
                $poster = preg_match('~^/[A-Za-z0-9_./-]+$~', (string)($_POST['poster'] ?? '')) ? (string)$_POST['poster'] : null;
                $name = trim(mb_substr((string)($_POST['name'] ?? ''), 0, 80));
                if ($url === null) { flash('bad', 'El enlace debe empezar con https:// o http://'); break; }
                if ($id <= 0 || $title === '') { flash('bad', 'Elige primero una película o serie.'); break; }
                if ($name === '') $name = parse_url($url, PHP_URL_HOST) ?: 'Enlace';
                $pdo->prepare('INSERT INTO vt_links (kind, tmdb_id, title, year, poster, name, url, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)')
                    ->execute([$kind, $id, $title, $year, $poster, $name, $url, now(), now()]);
                flash('ok', "Enlace agregado a «{$title}».");
                $back = "view=title&kind=$kind&id=$id";
                break;
            case 'edit':
                $url = clean_url((string)($_POST['url'] ?? ''));
                $name = trim(mb_substr((string)($_POST['name'] ?? ''), 0, 80));
                if ($url === null) { flash('bad', 'El enlace debe empezar con https:// o http://'); break; }
                if ($name === '') $name = parse_url($url, PHP_URL_HOST) ?: 'Enlace';
                $pdo->prepare('UPDATE vt_links SET name = ?, url = ?, updated_at = ? WHERE id = ?')->execute([$name, $url, now(), (int)$_POST['id']]);
                flash('ok', 'Enlace actualizado.');
                break;
            case 'delete':
                $pdo->prepare('DELETE FROM vt_links WHERE id = ?')->execute([(int)$_POST['id']]);
                flash('ok', 'Enlace borrado.');
                break;
            case 'delete_title':
                $pdo->prepare('DELETE FROM vt_links WHERE kind = ? AND tmdb_id = ?')
                    ->execute([($_POST['kind'] ?? '') === 'tv' ? 'tv' : 'movie', (int)$_POST['tmdb_id']]);
                flash('ok', 'Título y sus enlaces borrados.');
                $back = ($_POST['kind'] ?? '') === 'tv' ? 'view=series' : 'view=movies';
                break;
            case 'check':
                $st = $pdo->prepare('SELECT url FROM vt_links WHERE id = ?');
                $st->execute([(int)$_POST['id']]);
                $u = $st->fetchColumn();
                if ($u) { [$ok, $t] = check_url((string)$u); flash($ok ? 'ok' : 'bad', 'Verificación: ' . $t); }
                break;
            case 'ch_add':
            case 'ch_edit':
                $name = trim(mb_substr((string)($_POST['name'] ?? ''), 0, 80));
                $urlRaw = trim((string)($_POST['url'] ?? ''));
                $url = $urlRaw === '' ? '' : clean_url($urlRaw);
                // Canal oficial de YouTube (opcional): si transmite en vivo, la app usa esa transmisión
                $ytRaw = trim((string)($_POST['youtube'] ?? ''));
                $yt = null;
                if ($ytRaw !== '') {
                    $yt = clean_url($ytRaw);
                    $host = $yt ? strtolower((string)parse_url($yt, PHP_URL_HOST)) : '';
                    if ($yt === null || !preg_match('~(^|\.)youtube\.com$~', $host)) {
                        flash('bad', 'El canal de YouTube debe ser un enlace como https://www.youtube.com/@nombre'); break;
                    }
                }
                // Respaldos opcionales: si el principal falla, la app usa estos
                $b = [];
                foreach (['url2', 'url3'] as $f) {
                    $raw = trim((string)($_POST[$f] ?? ''));
                    if ($raw === '') { $b[$f] = null; continue; }
                    $b[$f] = clean_url($raw);
                    if ($b[$f] === null) { flash('bad', 'Los enlaces de respaldo deben empezar con https:// o http://'); break 2; }
                }
                $logoRaw = trim((string)($_POST['logo'] ?? ''));
                $logo = $logoRaw === '' ? null : clean_url($logoRaw);
                $coverRaw = trim((string)($_POST['cover'] ?? ''));
                $cover = $coverRaw === '' ? null : clean_url($coverRaw);
                $cat = in_array($_POST['category'] ?? '', channel_categories(), true) ? (string)$_POST['category'] : 'General';
                $national = !empty($_POST['national']) ? 1 : 0;
                if ($coverRaw !== '' && ($cover === null || !starts_with($cover, 'https://'))) { flash('bad', 'La portada debe ser un enlace https:// a una imagen.'); break; }
                $pos = (int)($_POST['position'] ?? 0);
                if ($name === '') { flash('bad', 'Escribe el nombre del canal.'); break; }
                if ($url === null) { flash('bad', 'El enlace de la señal debe empezar con https:// o http://'); break; }
                if ($url === '' && $yt === null) { flash('bad', 'Pon el enlace de la señal o el canal oficial de YouTube.'); break; }
                if ($logoRaw !== '' && ($logo === null || !starts_with($logo, 'https://'))) { flash('bad', 'El logo debe ser un enlace https:// a una imagen.'); break; }
                if ($action === 'ch_add') {
                    $pdo->prepare('INSERT INTO vt_channels (name, url, url2, url3, youtube, logo, category, national, cover, position, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)')
                        ->execute([$name, $url, $b['url2'], $b['url3'], $yt, $logo, $cat, $national, $cover, $pos, now(), now()]);
                    flash('ok', "Canal «{$name}» agregado.");
                } else {
                    $pdo->prepare('UPDATE vt_channels SET name = ?, url = ?, url2 = ?, url3 = ?, youtube = ?, logo = ?, category = ?, national = ?, cover = ?, position = ?, updated_at = ? WHERE id = ?')
                        ->execute([$name, $url, $b['url2'], $b['url3'], $yt, $logo, $cat, $national, $cover, $pos, now(), (int)$_POST['id']]);
                    flash('ok', 'Canal actualizado.');
                }
                $back = 'view=channels';
                break;
            case 'ch_delete':
                $pdo->prepare('DELETE FROM vt_channels WHERE id = ?')->execute([(int)$_POST['id']]);
                flash('ok', 'Canal borrado.');
                $back = 'view=channels';
                break;
            case 'ch_check':
                $st = $pdo->prepare('SELECT url FROM vt_channels WHERE id = ?');
                $st->execute([(int)$_POST['id']]);
                $u = $st->fetchColumn();
                if ($u) { [$ok, $t] = check_url((string)$u); flash($ok ? 'ok' : 'bad', 'Señal: ' . $t); }
                $back = 'view=channels';
                break;
        }
    } catch (Throwable $e) {
        flash('bad', $e->getMessage());
    }
    go($back);
}

if (!empty($_SESSION['vt_flash'])) {
    [$t, $text] = $_SESSION['vt_flash'];
    unset($_SESSION['vt_flash']);
    if ($t === 'ok') $msg = $text; else $err = $text;
}
$token = csrf_token();

/** Formulario POST con CSRF y página de regreso. */
function form_open(string $action, string $token, string $back = '', string $class = ''): string {
    return '<form method="post"' . ($class ? ' class="' . h($class) . '"' : '') . '>'
        . '<input type="hidden" name="csrf" value="' . h($token) . '">'
        . '<input type="hidden" name="action" value="' . h($action) . '">'
        . '<input type="hidden" name="back" value="' . h($back) . '">';
}
function img(?string $path, string $size): ?string {
    global $IMG;
    return $path ? $IMG . $size . $path : null;
}
function icon(string $n): string {
    $p = [
        'home' => '<path d="M4 10.5 12 4l8 6.5V20h-5v-6H9v6H4z"/>',
        'movie' => '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3 9h18M8 5v4M16 5v4"/>',
        'tv' => '<rect x="3" y="6" width="18" height="12" rx="2"/><path d="m9 2 3 4 3-4"/>',
        'live' => '<circle cx="12" cy="12" r="2"/><path d="M7.8 7.8a6 6 0 0 0 0 8.4M16.2 16.2a6 6 0 0 0 0-8.4M5 5a10 10 0 0 0 0 14M19 19a10 10 0 0 0 0-14"/>',
        'check' => '<path d="M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z"/><path d="m8 12 3 3 5-6"/>',
        'plus' => '<path d="M12 5v14M5 12h14"/>',
        'search' => '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
        'link' => '<path d="M10 14a4 4 0 0 0 5.66 0l3-3a4 4 0 0 0-5.66-5.66l-1 1"/><path d="M14 10a4 4 0 0 0-5.66 0l-3 3a4 4 0 0 0 5.66 5.66l1-1"/>',
        'logout' => '<path d="M15 4h4v16h-4M10 8l-4 4 4 4M6 12h10"/>',
    ][$n] ?? '';
    return '<svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' . $p . '</svg>';
}

// ---------- Datos para la vista ----------
$fatal = null;
$data = [];
if (logged_in()) {
    try {
        $pdo = db();
        $all = $pdo->query('SELECT * FROM vt_links ORDER BY updated_at DESC, id DESC')->fetchAll();
        $titles = [];
        foreach ($all as $l) {
            $k = $l['kind'] . ':' . $l['tmdb_id'];
            if (!isset($titles[$k])) $titles[$k] = $l + ['links' => []];
            $titles[$k]['links'][] = $l;
        }
        $data['titles'] = $titles;
        $data['links'] = count($all);
        $data['movies'] = array_filter($titles, function ($t) { return $t['kind'] === 'movie'; });
        $data['series'] = array_filter($titles, function ($t) { return $t['kind'] === 'tv'; });
        $data['channels'] = $pdo->query('SELECT * FROM vt_channels ORDER BY position, name')->fetchAll();
    } catch (Throwable $e) {
        $fatal = $e->getMessage();
    }
}
$q = trim((string)($_GET['q'] ?? ''));
$filter = trim((string)($_GET['f'] ?? ''));
$tmdbReady = trim((string)($c['tmdb_token'] ?? '')) !== '';
?><!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex, nofollow">
<title>Panel · Vidtube</title>
<link rel="stylesheet" href="panel.css">
</head>
<body>
<?php if (!logged_in()): ?>
<main class="login">
  <div class="brand big">Vid<b>tube</b><span>Panel de enlaces</span></div>
  <?php if ($err): ?><div class="msg bad"><?= h($err) ?></div><?php endif; ?>
  <?php if (($c['admin_password_hash'] ?? '') === ''): ?>
    <div class="card">
      <h3>Primer paso: crea tu contraseña</h3>
      <p class="muted">Escríbela, toca «Generar hash» y copia el resultado en <code>config.php</code> → <code>admin_password_hash</code>. Luego recarga esta página.</p>
      <?= form_open('hash', $token) ?>
        <input type="password" name="password" placeholder="Contraseña nueva (mínimo 10 caracteres)" autocomplete="new-password" required>
        <button class="btn">Generar hash</button>
      </form>
      <?php if (!empty($generated)): ?><p>Copia esto en config.php:</p><code class="block"><?= h($generated) ?></code><?php endif; ?>
    </div>
  <?php else: ?>
    <?= form_open('login', $token, '', 'card') ?>
      <label>Usuario<input type="text" name="user" autocomplete="username" required></label>
      <label>Contraseña<input type="password" name="password" autocomplete="current-password" required></label>
      <button class="btn wide">Entrar</button>
    </form>
  <?php endif; ?>
</main>
<?php else: ?>
<div class="app">
  <aside class="side">
    <div class="brand">Vid<b>tube</b><span>Panel</span></div>
    <nav>
      <?php foreach ([
          ['home', 'home', 'Resumen'], ['movies', 'movie', 'Películas'], ['series', 'tv', 'Series'],
          ['channels', 'live', 'Canales de TV'], ['add', 'plus', 'Agregar'], ['diag', 'check', 'Diagnóstico'],
      ] as [$v, $ic, $label]): ?>
        <a href="?view=<?= $v ?>" class="<?= ($view === $v || ($view === 'title' && $v === ((($_GET['kind'] ?? '') === 'tv') ? 'series' : 'movies'))) ? 'on' : '' ?>"><?= icon($ic) ?><span><?= $label ?></span></a>
      <?php endforeach; ?>
    </nav>
    <?= form_open('logout', $token, '', 'logout') ?><button class="link"><?= icon('logout') ?><span>Salir</span></button></form>
  </aside>

  <main class="content">
    <?php if ($msg): ?><div class="msg ok"><?= h($msg) ?></div><?php endif; ?>
    <?php if ($err): ?><div class="msg bad"><?= h($err) ?></div><?php endif; ?>
    <?php if ($fatal): ?>
      <div class="msg bad"><b>El panel no puede leer la base de datos.</b><br><?= h($fatal) ?><br>
        <a href="?view=diag">Abre Diagnóstico</a> para revisar la instalación.</div>
    <?php endif; ?>

<?php if ($view === 'home' && !$fatal): ?>
    <header class="top"><h1>Resumen</h1><a class="btn" href="?view=add"><?= icon('plus') ?>Agregar enlace</a></header>
    <div class="stats">
      <a class="stat" href="?view=movies"><b><?= count($data['movies']) ?></b><span>Películas</span></a>
      <a class="stat" href="?view=series"><b><?= count($data['series']) ?></b><span>Series</span></a>
      <div class="stat"><b><?= (int)$data['links'] ?></b><span>Enlaces</span></div>
      <a class="stat" href="?view=channels"><b><?= count($data['channels']) ?></b><span>Canales de TV</span></a>
    </div>
    <h2>Agregado recientemente</h2>
    <?php if (!$data['titles']): ?>
      <div class="empty card"><p>Todavía no hay enlaces.</p><a class="btn" href="?view=add"><?= icon('plus') ?>Agregar el primero</a></div>
    <?php else: ?>
      <div class="posters"><?php foreach (array_slice($data['titles'], 0, 12) as $t): ?>
        <a class="poster" href="?view=title&kind=<?= h($t['kind']) ?>&id=<?= (int)$t['tmdb_id'] ?>">
          <?php if ($t['poster']): ?><img src="<?= h(img($t['poster'], 'w342')) ?>" alt="" loading="lazy"><?php else: ?><div class="noimg"><?= h(mb_substr($t['title'], 0, 1)) ?></div><?php endif; ?>
          <span class="badge"><?= count($t['links']) ?> <?= icon('link') ?></span>
          <b><?= h($t['title']) ?></b><small><?= $t['kind'] === 'tv' ? 'Serie' : 'Película' ?><?= $t['year'] ? ' · ' . (int)$t['year'] : '' ?></small>
        </a>
      <?php endforeach; ?></div>
    <?php endif; ?>
    <div class="card apphint">
      <h3>Conectar la app</h3>
      <p class="muted">En Vidtube → Configuración → Panel de enlaces, escribe:</p>
      <p>Dirección: <code><?= h(panel_base_url()) ?></code></p>
      <p>Clave: la <code>api_key</code> de tu config.php.</p>
    </div>

<?php elseif (($view === 'movies' || $view === 'series') && !$fatal):
    $isTv = $view === 'series';
    $list = $isTv ? $data['series'] : $data['movies'];
    if ($filter !== '') $list = array_filter($list, function ($t) use ($filter) { return stripos($t['title'], $filter) !== false; });
    usort($list, function ($a, $b) { return strcasecmp($a['title'], $b['title']); }); ?>
    <header class="top"><h1><?= $isTv ? 'Series' : 'Películas' ?> <span class="count"><?= count($list) ?></span></h1><a class="btn" href="?view=add"><?= icon('plus') ?>Agregar</a></header>
    <form method="get" class="bar"><input type="hidden" name="view" value="<?= h($view) ?>">
      <input type="search" name="f" value="<?= h($filter) ?>" placeholder="Filtrar por título"><button class="btn ghost"><?= icon('search') ?>Filtrar</button></form>
    <?php if (!$list): ?>
      <div class="empty card"><p><?= $filter !== '' ? 'Nada coincide con «' . h($filter) . '».' : 'Todavía no hay ' . ($isTv ? 'series' : 'películas') . ' con enlaces.' ?></p></div>
    <?php else: ?>
      <div class="posters"><?php foreach ($list as $t): ?>
        <a class="poster" href="?view=title&kind=<?= h($t['kind']) ?>&id=<?= (int)$t['tmdb_id'] ?>">
          <?php if ($t['poster']): ?><img src="<?= h(img($t['poster'], 'w342')) ?>" alt="" loading="lazy"><?php else: ?><div class="noimg"><?= h(mb_substr($t['title'], 0, 1)) ?></div><?php endif; ?>
          <span class="badge"><?= count($t['links']) ?> <?= icon('link') ?></span>
          <b><?= h($t['title']) ?></b><small><?= $t['year'] ? (int)$t['year'] : '&nbsp;' ?></small>
        </a>
      <?php endforeach; ?></div>
    <?php endif; ?>

<?php elseif ($view === 'title' && !$fatal):
    $kind = ($_GET['kind'] ?? '') === 'tv' ? 'tv' : 'movie';
    $id = (int)($_GET['id'] ?? 0);
    $t = $data['titles']["$kind:$id"] ?? null;
    $det = $tmdbReady ? tmdb_details($kind, $id) : null;
    $back = "view=title&kind=$kind&id=$id"; ?>
    <?php if (!$t): ?>
      <div class="empty card"><p>Este título ya no tiene enlaces.</p><a class="btn" href="?view=<?= $kind === 'tv' ? 'series' : 'movies' ?>">Volver</a></div>
    <?php else: ?>
      <section class="hero" <?php if ($det && $det['backdrop']): ?>style="background-image:linear-gradient(90deg,#0B0C10 30%,rgba(11,12,16,.6)),url('<?= h(img($det['backdrop'], 'w1280')) ?>')"<?php endif; ?>>
        <?php if ($t['poster']): ?><img class="hp" src="<?= h(img($t['poster'], 'w342')) ?>" alt=""><?php endif; ?>
        <div>
          <a class="crumb" href="?view=<?= $kind === 'tv' ? 'series' : 'movies' ?>">← <?= $kind === 'tv' ? 'Series' : 'Películas' ?></a>
          <h1><?= h($t['title']) ?></h1>
          <p class="meta"><?= $t['year'] ? (int)$t['year'] . ' · ' : '' ?><?= $kind === 'tv' ? 'Serie' : 'Película' ?>
            <?php if ($det): ?><?= $det['genres'] ? ' · ' . h(implode(', ', array_slice($det['genres'], 0, 3))) : '' ?>
              <?= $det['runtime'] ? ' · ' . (int)$det['runtime'] . ' min' : '' ?><?= $det['seasons'] ? ' · ' . (int)$det['seasons'] . ' temporadas' : '' ?>
              <?= $det['rating'] ? ' · ★ ' . h((string)$det['rating']) : '' ?><?php endif; ?></p>
          <?php if ($det && $det['overview']): ?><p class="ov"><?= h($det['overview']) ?></p><?php endif; ?>
        </div>
      </section>

      <h2>Enlaces <span class="count"><?= count($t['links']) ?></span></h2>
      <div class="card list">
        <?php foreach ($t['links'] as $l): ?>
          <div class="row">
            <?= form_open('edit', $token, $back, 'grow') ?>
              <input type="hidden" name="id" value="<?= (int)$l['id'] ?>">
              <input type="text" name="name" value="<?= h($l['name']) ?>" aria-label="Nombre" class="name">
              <input type="text" inputmode="url" autocomplete="off" spellcheck="false" name="url" value="<?= h($l['url']) ?>" aria-label="Enlace" required>
              <button class="btn ghost">Guardar</button>
            </form>
            <?= form_open('check', $token, $back) ?><input type="hidden" name="id" value="<?= (int)$l['id'] ?>"><button class="btn ghost">Verificar</button></form>
            <?= form_open('delete', $token, $back, 'confirm-delete') ?><input type="hidden" name="id" value="<?= (int)$l['id'] ?>"><button class="btn danger">Borrar</button></form>
          </div>
        <?php endforeach; ?>
        <h3>Agregar otro enlace</h3>
        <?= form_open('add', $token, $back, 'row') ?>
          <input type="hidden" name="kind" value="<?= h($kind) ?>"><input type="hidden" name="tmdb_id" value="<?= $id ?>">
          <input type="hidden" name="title" value="<?= h($t['title']) ?>"><input type="hidden" name="year" value="<?= h((string)($t['year'] ?? '')) ?>">
          <input type="hidden" name="poster" value="<?= h((string)($t['poster'] ?? '')) ?>">
          <input type="text" name="name" placeholder="Nombre (ej. Latino 1080p)" maxlength="80" class="name">
          <input type="text" inputmode="url" autocomplete="off" spellcheck="false" name="url" placeholder="https://… (Drive, Dropbox, .mp4, .m3u8, YouTube)" required>
          <button class="btn"><?= icon('plus') ?>Agregar</button>
        </form>
      </div>
      <?= form_open('delete_title', $token, '', 'confirm-delete danger-zone') ?>
        <input type="hidden" name="kind" value="<?= h($kind) ?>"><input type="hidden" name="tmdb_id" value="<?= $id ?>">
        <button class="btn danger">Quitar este título y todos sus enlaces</button>
      </form>
    <?php endif; ?>

<?php elseif ($view === 'add' && !$fatal):
    $results = ($q !== '' && $tmdbReady) ? tmdb_search($q) : []; ?>
    <header class="top"><h1>Agregar enlace</h1></header>
    <form method="get" class="bar big"><input type="hidden" name="view" value="add">
      <input type="search" name="q" value="<?= h($q) ?>" placeholder="Busca la película o serie (ej. Coco, Breaking Bad)" autofocus>
      <button class="btn"><?= icon('search') ?>Buscar</button></form>
    <?php if (!$tmdbReady): ?><div class="msg bad">Falta <code>tmdb_token</code> en config.php para poder buscar.</div>
    <?php elseif ($q !== '' && !$results): ?><div class="empty card"><p>Sin resultados para «<?= h($q) ?>».</p></div>
    <?php elseif ($q === ''): ?><p class="muted">Busca un título, elige el correcto y pega su enlace. Los enlaces aparecen en Vidtube en la ficha de ese título.</p><?php endif; ?>
    <div class="results"><?php foreach ($results as $r): $has = isset($data['titles'][$r['kind'] . ':' . $r['id']]); ?>
      <div class="res">
        <?php if ($r['poster']): ?><img src="<?= h(img($r['poster'], 'w342')) ?>" alt="" loading="lazy"><?php else: ?><div class="noimg"><?= h(mb_substr($r['title'], 0, 1)) ?></div><?php endif; ?>
        <div class="b">
          <b><?= h($r['title']) ?></b><small><?= $r['kind'] === 'tv' ? 'Serie' : 'Película' ?><?= $r['year'] ? ' · ' . (int)$r['year'] : '' ?><?= $has ? ' · ya tiene enlaces' : '' ?></small>
          <?= form_open('add', $token) ?>
            <input type="hidden" name="kind" value="<?= h($r['kind']) ?>"><input type="hidden" name="tmdb_id" value="<?= (int)$r['id'] ?>">
            <input type="hidden" name="title" value="<?= h($r['title']) ?>"><input type="hidden" name="year" value="<?= h((string)($r['year'] ?? '')) ?>">
            <input type="hidden" name="poster" value="<?= h((string)($r['poster'] ?? '')) ?>">
            <input type="text" inputmode="url" autocomplete="off" spellcheck="false" name="url" placeholder="https://…" required>
            <input type="text" name="name" placeholder="Nombre (ej. Latino HD)" maxlength="80">
            <button class="btn"><?= icon('plus') ?>Agregar</button>
          </form>
        </div>
      </div>
    <?php endforeach; ?></div>

<?php elseif ($view === 'channels' && !$fatal): ?>
    <header class="top"><h1>Canales de TV <span class="count"><?= count($data['channels']) ?></span></h1></header>
    <div class="card">
      <h3>Nuevo canal</h3>
      <?= form_open('ch_add', $token, 'view=channels', 'chan') ?>
        <input type="text" name="name" placeholder="Nombre (ej. TV Perú)" maxlength="80" required>
        <input type="text" inputmode="url" autocomplete="off" spellcheck="false" name="url" placeholder="Enlace principal (.m3u8)">
        <input type="text" inputmode="url" autocomplete="off" spellcheck="false" name="url2" placeholder="Respaldo 1 (opcional)">
        <input type="text" inputmode="url" autocomplete="off" spellcheck="false" name="url3" placeholder="Respaldo 2 (opcional)">
        <input type="url" name="youtube" placeholder="Canal oficial de YouTube (opcional) https://www.youtube.com/@…">
        <input type="url" name="logo" placeholder="Logo https://… (opcional)">
        <input type="url" name="cover" placeholder="Portada https://… (opcional, imagen ancha)">
        <select name="category" aria-label="Categoría"><?php foreach (channel_categories() as $cat): ?><option><?= h($cat) ?></option><?php endforeach; ?></select>
        <label class="chk"><input type="checkbox" name="national" value="1" checked> Nacional (Perú)</label>
        <input type="number" name="position" value="<?= count($data['channels']) + 1 ?>" aria-label="Orden" title="Orden">
        <button class="btn"><?= icon('plus') ?>Agregar canal</button>
      </form>
    </div>
    <?php if (!$data['channels']): ?>
      <div class="empty card"><p>Todavía no hay canales. Aparecerán en Vidtube → TV abierta.</p></div>
    <?php else: ?>
      <p class="muted">¿El canal necesita un reproductor específico? Agrega al final del enlace <code>|User-Agent=VLC/3.0.20&amp;Referer=https://sitio.com/</code> (formato de listas IPTV).</p>
      <p class="muted">Si el canal tiene su <b>canal oficial de YouTube</b> y está transmitiendo en vivo, la app usa YouTube. Si no, usa el enlace principal; si ese no funciona, pasa sola al respaldo 1 y luego al respaldo 2. En películas y series puedes agregar varios enlaces a un título: se prueban en orden.</p>
      <div class="channels"><?php foreach ($data['channels'] as $ch): ?>
        <div class="chcard">
          <div class="logo"<?php if (!empty($ch['cover'])): ?> style="background:center/cover url('<?= h($ch['cover']) ?>')"<?php endif; ?>><?php if ($ch['logo'] && empty($ch['cover'])): ?><img src="<?= h($ch['logo']) ?>" alt=""><?php elseif (empty($ch['cover'])): ?><span><?= h($ch['name']) ?></span><?php endif; ?><i><?= h($ch['category'] ?? 'General') ?><?= !empty($ch['national']) ? ' · Nacional' : '' ?></i></div>
          <?= form_open('ch_edit', $token, 'view=channels', 'chform') ?>
            <input type="hidden" name="id" value="<?= (int)$ch['id'] ?>">
            <input type="text" name="name" value="<?= h($ch['name']) ?>" aria-label="Nombre" required>
            <input type="text" inputmode="url" autocomplete="off" spellcheck="false" name="url" value="<?= h($ch['url']) ?>" placeholder="Enlace principal (.m3u8)" aria-label="Enlace principal" title="Enlace principal">
            <input type="text" inputmode="url" autocomplete="off" spellcheck="false" name="url2" value="<?= h((string)($ch['url2'] ?? '')) ?>" placeholder="Respaldo 1 (opcional)" aria-label="Respaldo 1">
            <input type="text" inputmode="url" autocomplete="off" spellcheck="false" name="url3" value="<?= h((string)($ch['url3'] ?? '')) ?>" placeholder="Respaldo 2 (opcional)" aria-label="Respaldo 2">
            <input type="url" name="youtube" value="<?= h((string)($ch['youtube'] ?? '')) ?>" placeholder="Canal oficial de YouTube (opcional)" aria-label="Canal de YouTube" title="Si transmite en vivo, la app usa YouTube">
            <input type="url" name="logo" value="<?= h((string)$ch['logo']) ?>" placeholder="Logo https://…" aria-label="Logo">
            <input type="url" name="cover" value="<?= h((string)($ch['cover'] ?? '')) ?>" placeholder="Portada https://… (opcional)" aria-label="Portada">
            <div class="row2">
              <select name="category" aria-label="Categoría"><?php foreach (channel_categories() as $cat): ?><option<?= ($ch['category'] ?? 'General') === $cat ? ' selected' : '' ?>><?= h($cat) ?></option><?php endforeach; ?></select>
              <label class="chk"><input type="checkbox" name="national" value="1"<?= !empty($ch['national']) ? ' checked' : '' ?>> Nacional</label>
            </div>
            <div class="row2"><input type="number" name="position" value="<?= (int)$ch['position'] ?>" aria-label="Orden" title="Orden"><button class="btn ghost">Guardar</button></div>
          </form>
          <div class="row2">
            <?= form_open('ch_check', $token, 'view=channels') ?><input type="hidden" name="id" value="<?= (int)$ch['id'] ?>"><button class="btn ghost">Verificar señal</button></form>
            <?= form_open('ch_delete', $token, 'view=channels', 'confirm-delete') ?><input type="hidden" name="id" value="<?= (int)$ch['id'] ?>"><button class="btn danger">Borrar</button></form>
          </div>
        </div>
      <?php endforeach; ?></div>
    <?php endif; ?>

<?php elseif ($view === 'diag'): ?>
    <header class="top"><h1>Diagnóstico</h1></header>
    <div class="card list">
      <?php foreach (diagnostics() as [$name, $ok, $text]): ?>
        <div class="diag"><span class="dot <?= $ok ? 'g' : 'r' ?>"></span><b><?= h($name) ?></b><span class="muted"><?= h($text) ?></span></div>
      <?php endforeach; ?>
    </div>
    <div class="card apphint">
      <h3>Datos para la app</h3>
      <p>Dirección del panel: <code><?= h(panel_base_url()) ?></code></p>
      <p>Prueba de la app: <code><?= h(panel_base_url()) ?>api.php?key=TU_CLAVE</code> debe mostrar tus enlaces.</p>
    </div>
<?php endif; ?>
  </main>
</div>
<?php endif; ?>
<script src="panel.js"></script>
</body>
</html>
