<?php
// Enable error reporting for debugging
ini_set('display_errors', 1);
ini_set('display_startup_errors', 1);
error_reporting(E_ALL);

echo "<h1>Testing get_user_profile.php</h1>";
echo "<pre>";

// Simulate the API call
$_SERVER['HTTP_AUTHORIZATION'] = 'Bearer YOUR_TOKEN_HERE'; // Replace with actual token

echo "Loading get_user_profile.php...\n\n";

// Include the API file
ob_start();
include __DIR__ . '/get_user_profile.php';
$output = ob_get_clean();

echo "API Output:\n";
echo $output;

echo "\n\n</pre>";
