<?php
declare(strict_types=1);
require __DIR__ . '/lib.php';

// Panel de enlaces de Vidtube: agregar, editar y borrar enlaces de películas y series.
header('X-Frame-Options: DENY');
header('X-Content-Type-Options: nosniff');
header("Content-Security-Policy: default-src 'self'; img-src 'self' https://image.tmdb.org data:; style-src 'self' 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'");

start_session();
$c = cfg();
$msg = null;
$err = null;
$action = $_POST['action'] ?? $_GET['action'] ?? '';

// ---------- Generar hash de contraseña (sin iniciar sesión) ----------
if ($action === 'hash' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    check_csrf();
    $p = (string)($_POST['password'] ?? '');
    if (strlen($p) < 10) $err = 'Usa una contraseña de al menos 10 caracteres.';
    else $generated = password_hash($p, PASSWORD_DEFAULT);
}

// ---------- Iniciar / cerrar sesión ----------
if ($action === 'login' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    check_csrf();
    if (login_locked()) $err = 'Demasiados intentos. Espera 10 minutos.';
    elseif (!try_login((string)($_POST['user'] ?? ''), (string)($_POST['password'] ?? ''))) $err = 'Usuario o contraseña incorrectos.';
    else { header('Location: ./'); exit; }
}
if ($action === 'logout' && $_SERVER['REQUEST_METHOD'] === 'POST') {
    check_csrf();
    $_SESSION = [];
    session_destroy();
    header('Location: ./');
    exit;
}

// ---------- Acciones del panel (solo con sesión) ----------
if (logged_in() && $_SERVER['REQUEST_METHOD'] === 'POST' && in_array($action, ['add', 'edit', 'delete'], true)) {
    check_csrf();
    $pdo = db();
    if ($action === 'delete') {
        $pdo->prepare('DELETE FROM vt_links WHERE id = ?')->execute([(int)($_POST['id'] ?? 0)]);
        $msg = 'Enlace borrado.';
    } else {
        $url = clean_url((string)($_POST['url'] ?? ''));
        $name = trim(mb_substr((string)($_POST['name'] ?? ''), 0, 80));
        if ($url === null) $err = 'El enlace debe empezar con https:// o http://';
        elseif ($action === 'add') {
            $kind = ($_POST['kind'] ?? '') === 'tv' ? 'tv' : 'movie';
            $id = (int)($_POST['tmdb_id'] ?? 0);
            $title = trim(mb_substr((string)($_POST['title'] ?? ''), 0, 255));
            $year = ($_POST['year'] ?? '') !== '' ? (int)$_POST['year'] : null;
            $poster = preg_match('~^/[A-Za-z0-9_./-]+$~', (string)($_POST['poster'] ?? '')) ? $_POST['poster'] : null;
            if ($id <= 0 || $title === '') $err = 'Elige primero una película o serie.';
            else {
                if ($name === '') $name = parse_url($url, PHP_URL_HOST) ?: 'Enlace';
                $pdo->prepare('INSERT INTO vt_links (kind, tmdb_id, title, year, poster, name, url, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)')
                    ->execute([$kind, $id, $title, $year, $poster, $name, $url, now(), now()]);
                $msg = "Enlace agregado a «{$title}».";
            }
        } else {
            if ($name === '') $name = parse_url($url, PHP_URL_HOST) ?: 'Enlace';
            $pdo->prepare('UPDATE vt_links SET name = ?, url = ?, updated_at = ? WHERE id = ?')
                ->execute([$name, $url, now(), (int)($_POST['id'] ?? 0)]);
            $msg = 'Enlace actualizado.';
        }
    }
}

$q = trim((string)($_GET['q'] ?? ''));
$filter = trim((string)($_GET['f'] ?? ''));
$results = (logged_in() && $q !== '') ? tmdb_search($q) : [];
$img = 'https://image.tmdb.org/t/p/w154';
$token = csrf_token();
?><!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex, nofollow">
<title>Panel de enlaces · Vidtube</title>
<style>
:root{--bg:#0B0C10;--card:#15171D;--raise:#1C1F27;--line:#262A33;--text:#F3F4F7;--muted:#9AA0AC;--coral:#FF405B;--ok:#3DBE8B}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:15px/1.45 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
main{max-width:900px;margin:0 auto;padding:20px 16px 60px}
header{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:18px}
h1{font-size:22px;margin:0}h1 b{color:var(--coral)}h2{font-size:17px;margin:26px 0 10px}
.card{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:16px}
input,button{font:inherit}input[type=text],input[type=password],input[type=url],input[type=search]{width:100%;padding:11px 12px;border-radius:10px;border:1px solid var(--line);background:var(--raise);color:var(--text)}
button,.btn{cursor:pointer;border:0;border-radius:10px;padding:10px 16px;background:var(--coral);color:#fff;font-weight:600;text-decoration:none;display:inline-block}
button.ghost{background:transparent;border:1px solid var(--line);color:var(--text)}button.danger{background:transparent;border:1px solid #5a2630;color:#ff8a9a}
.row{display:flex;gap:10px;align-items:center}.row>*{flex:1}.row>button{flex:none}
.msg{padding:10px 12px;border-radius:10px;margin-bottom:12px}.ok{background:#173a2d;color:#9be7c4}.bad{background:#3a1720;color:#ffb3bf}
.results{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:12px}
.res{background:var(--raise);border:1px solid var(--line);border-radius:12px;overflow:hidden}.res img{width:100%;aspect-ratio:2/3;object-fit:cover;display:block;background:#222}
.res .b{padding:8px}.res form{display:flex;flex-direction:column;gap:6px;margin-top:6px}.res input{padding:8px;font-size:13px}.res button{padding:8px;font-size:13px}
.title{display:flex;gap:12px;padding:12px 0;border-top:1px solid var(--line)}.title img{width:56px;border-radius:8px;flex:none}
.link{display:flex;flex-wrap:wrap;gap:6px;align-items:flex-start;margin:8px 0;padding-bottom:8px;border-bottom:1px dashed var(--line)}
.link form.edit{display:grid;grid-template-columns:1fr;gap:6px;flex:1 1 260px}.link input{padding:8px 10px;font-size:13px}
.link button{padding:8px 12px;font-size:13px}
@media (min-width:640px){.link form.edit{grid-template-columns:170px 1fr auto}}
small,.muted{color:var(--muted)}code{background:var(--raise);padding:2px 6px;border-radius:6px;word-break:break-all}
.tag{font-size:11px;padding:2px 7px;border-radius:20px;border:1px solid var(--line);color:var(--muted);margin-left:6px}
</style>
</head>
<body>
<main>
<?php if (!logged_in()): ?>
  <header><h1>Vid<b>tube</b> · Panel de enlaces</h1></header>
  <?php if ($err): ?><div class="msg bad"><?= h($err) ?></div><?php endif; ?>
  <?php if (($c['admin_password_hash'] ?? '') === ''): ?>
    <div class="card">
      <p><b>Primer paso:</b> crea tu contraseña. Escríbela aquí, toca «Generar hash» y copia el resultado en
        <code>config.php</code> → <code>admin_password_hash</code>. Después recarga esta página.</p>
      <form method="post" class="row">
        <input type="hidden" name="csrf" value="<?= h($token) ?>"><input type="hidden" name="action" value="hash">
        <input type="password" name="password" placeholder="Contraseña nueva (mínimo 10 caracteres)" autocomplete="new-password" required>
        <button>Generar hash</button>
      </form>
      <?php if (!empty($generated)): ?><p>Copia esto en config.php:<br><code><?= h($generated) ?></code></p><?php endif; ?>
    </div>
  <?php else: ?>
    <form method="post" class="card" style="max-width:380px">
      <input type="hidden" name="csrf" value="<?= h($token) ?>"><input type="hidden" name="action" value="login">
      <p><input type="text" name="user" placeholder="Usuario" autocomplete="username" required></p>
      <p><input type="password" name="password" placeholder="Contraseña" autocomplete="current-password" required></p>
      <button style="width:100%">Entrar</button>
    </form>
  <?php endif; ?>
<?php else: ?>
  <header>
    <h1>Vid<b>tube</b> · Panel de enlaces</h1>
    <form method="post"><input type="hidden" name="csrf" value="<?= h($token) ?>"><input type="hidden" name="action" value="logout"><button class="ghost">Salir</button></form>
  </header>
  <?php if ($msg): ?><div class="msg ok"><?= h($msg) ?></div><?php endif; ?>
  <?php if ($err): ?><div class="msg bad"><?= h($err) ?></div><?php endif; ?>

  <section class="card">
    <h2 style="margin-top:0">Agregar enlace</h2>
    <form method="get" class="row">
      <input type="search" name="q" value="<?= h($q) ?>" placeholder="Busca la película o serie (ej. Coco, Breaking Bad)">
      <button>Buscar</button>
    </form>
    <?php if (trim((string)($c['tmdb_token'] ?? '')) === ''): ?>
      <p class="muted">Falta <code>tmdb_token</code> en config.php para poder buscar.</p>
    <?php elseif ($q !== '' && !$results): ?>
      <p class="muted">Sin resultados para «<?= h($q) ?>».</p>
    <?php endif; ?>
    <?php if ($results): ?>
      <div class="results" style="margin-top:14px">
      <?php foreach ($results as $r): ?>
        <div class="res">
          <?php if ($r['poster']): ?><img src="<?= h($img . $r['poster']) ?>" alt="" loading="lazy"><?php endif; ?>
          <div class="b">
            <b><?= h($r['title']) ?></b><br><small><?= $r['kind'] === 'tv' ? 'Serie' : 'Película' ?><?= $r['year'] ? ' · ' . (int)$r['year'] : '' ?></small>
            <form method="post" action="?q=<?= h(urlencode($q)) ?>">
              <input type="hidden" name="csrf" value="<?= h($token) ?>"><input type="hidden" name="action" value="add">
              <input type="hidden" name="kind" value="<?= h($r['kind']) ?>"><input type="hidden" name="tmdb_id" value="<?= (int)$r['id'] ?>">
              <input type="hidden" name="title" value="<?= h($r['title']) ?>"><input type="hidden" name="year" value="<?= h((string)($r['year'] ?? '')) ?>">
              <input type="hidden" name="poster" value="<?= h((string)($r['poster'] ?? '')) ?>">
              <input type="url" name="url" placeholder="https://… (Drive, mp4…)" required>
              <input type="text" name="name" placeholder="Nombre (ej. Latino 1080p)" maxlength="80">
              <button>Agregar</button>
            </form>
          </div>
        </div>
      <?php endforeach; ?>
      </div>
    <?php endif; ?>
  </section>

  <?php
    $all = db()->query('SELECT * FROM vt_links ORDER BY title, id')->fetchAll();
    $grouped = [];
    foreach ($all as $l) {
        if ($filter !== '' && stripos($l['title'], $filter) === false && stripos($l['name'], $filter) === false) continue;
        $grouped[$l['kind'] . ':' . $l['tmdb_id']][] = $l;
    }
  ?>
  <h2>Tus enlaces <span class="muted">(<?= count($all) ?>)</span></h2>
  <form method="get" class="row" style="margin-bottom:8px">
    <input type="search" name="f" value="<?= h($filter) ?>" placeholder="Filtrar por título o nombre">
    <button class="ghost">Filtrar</button>
  </form>
  <div class="card">
    <?php if (!$grouped): ?><p class="muted" style="margin:0">Todavía no hay enlaces. Busca una película arriba para agregar el primero.</p><?php endif; ?>
    <?php foreach ($grouped as $links): $t = $links[0]; ?>
      <div class="title">
        <?php if ($t['poster']): ?><img src="<?= h($img . $t['poster']) ?>" alt=""><?php endif; ?>
        <div style="flex:1;min-width:0">
          <b><?= h($t['title']) ?></b><?= $t['year'] ? ' <small>(' . (int)$t['year'] . ')</small>' : '' ?>
          <span class="tag"><?= $t['kind'] === 'tv' ? 'Serie' : 'Película' ?></span>
          <?php foreach ($links as $l): ?>
            <div class="link">
              <form method="post" class="edit">
                <input type="hidden" name="csrf" value="<?= h($token) ?>"><input type="hidden" name="action" value="edit"><input type="hidden" name="id" value="<?= (int)$l['id'] ?>">
                <input type="text" name="name" value="<?= h($l['name']) ?>" aria-label="Nombre">
                <input type="url" name="url" value="<?= h($l['url']) ?>" aria-label="Enlace" required>
                <button class="ghost">Guardar</button>
              </form>
              <form method="post" class="confirm-delete">
                <input type="hidden" name="csrf" value="<?= h($token) ?>"><input type="hidden" name="action" value="delete"><input type="hidden" name="id" value="<?= (int)$l['id'] ?>">
                <button class="danger">Borrar</button>
              </form>
            </div>
          <?php endforeach; ?>
        </div>
      </div>
    <?php endforeach; ?>
  </div>
  <p class="muted" style="margin-top:20px">La app lee estos enlaces desde <code>api.php</code> con tu clave. Los cambios aparecen en Vidtube al abrir la app o una ficha.</p>
<?php endif; ?>
</main>
<script src="panel.js"></script>
</body>
</html>
