<?php
declare(strict_types=1);
require __DIR__ . '/lib.php';

// Lectura de enlaces para la app Vidtube.
//   GET api.php?key=CLAVE  → {"version": "...", "titles": [{kind, tmdb_id, title, year, poster, links:[{name,url}]}]}
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

$c = cfg();
$key = (string)($_GET['key'] ?? ($_SERVER['HTTP_X_VIDTUBE_KEY'] ?? ''));
$api = (string)($c['api_key'] ?? '');
if (strlen($api) < 24 || starts_with($api, 'CAMBIA_ESTO') || !hash_equals($api, $key)) {
    usleep(300000);
    http_response_code(401);
    echo json_encode(['error' => 'Clave incorrecta']);
    exit;
}

$rows = db()->query('SELECT kind, tmdb_id, title, year, poster, name, url, updated_at FROM vt_links ORDER BY updated_at DESC, id DESC')->fetchAll();
$titles = [];
$version = '';
foreach ($rows as $r) {
    $k = $r['kind'] . ':' . $r['tmdb_id'];
    if (!isset($titles[$k])) {
        $titles[$k] = [
            'kind' => $r['kind'], 'tmdb_id' => (int)$r['tmdb_id'], 'title' => $r['title'],
            'year' => $r['year'] !== null ? (int)$r['year'] : null, 'poster' => $r['poster'], 'links' => [],
        ];
    }
    $titles[$k]['links'][] = ['name' => $r['name'], 'url' => $r['url']];
    if ($r['updated_at'] > $version) $version = $r['updated_at'];
}
$channels = [];
foreach (db()->query('SELECT name, url, url2, url3, youtube, logo, category, national, cover, updated_at FROM vt_channels ORDER BY position, name')->fetchAll() as $ch) {
    $backups = array_values(array_filter([$ch['url2'] ?? null, $ch['url3'] ?? null]));
    $channels[] = ['name' => $ch['name'], 'url' => $ch['url'], 'backups' => $backups, 'youtube' => $ch['youtube'] ?? null, 'logo' => $ch['logo'],
        'category' => $ch['category'] ?? 'General', 'national' => !empty($ch['national']), 'cover' => $ch['cover'] ?? null];
    if ($ch['updated_at'] > $version) $version = $ch['updated_at'];
}
echo json_encode(['version' => $version, 'count' => count($rows), 'titles' => array_values($titles), 'channels' => $channels],
    JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
