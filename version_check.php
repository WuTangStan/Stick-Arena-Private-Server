<?php
$counterFile = 'visit_count.txt';
if (!file_exists($counterFile)) {
    file_put_contents($counterFile, '0');
}
$visits = (int)file_get_contents($counterFile);
$visits++;
file_put_contents($counterFile, (string)$visits);

$origin = $_SERVER['HTTP_ORIGIN'] ?? '';
$allowedOrigins = ['https://playstickarena.com'];

if (in_array($origin, $allowedOrigins)) {
    header("Access-Control-Allow-Origin: $origin");
} else {
    header("Access-Control-Allow-Origin: https://playstickarena.com");
}
header("Access-Control-Allow-Methods: POST, GET, OPTIONS");
header("Access-Control-Allow-Headers: Content-Type");

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(200);
    exit;
}

$version = $_POST['ver'] ?? null;

function checkVersion()
{
    global $version;
    if (!isset($version)) {
        return "result=error";
    }
    if ($version == 598 || $version == 588 || $version == 558) {
        return "result=success";
    } else {
        return "result=error";
    }
}

echo checkVersion();
?>
