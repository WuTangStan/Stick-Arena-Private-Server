<?php
require_once('./stick_arena.php');

header("Content-Type: application/json");

global $db;

$result = mysqli_query($db, "SELECT COUNT(*) FROM users WHERE isOnline = 1");
if ($result) {
    $row = mysqli_fetch_row($result);
    $count = (int)$row[0];
    echo json_encode([
        "status" => "ok",
        "count" => $count
    ]);
} else {
    echo json_encode([
        "status" => "fail",
        "error" => "Database error"
    ]);
}
