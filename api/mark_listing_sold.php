<?php
/**
 * Mark Listing as Sold/Available API
 * Updates the status of a listing between 'sold' and 'active'
 * 
 * Database Schema Notes:
 * - listing.status: enum('draft','active','paused','sold','expired','rejected')
 */
ini_set('display_errors', 0);
ini_set('display_startup_errors', 0);
error_reporting(E_ALL);

require_once __DIR__ . '/config.php';
header('Content-Type: application/json; charset=utf-8');

// ==================== LOGGING SETUP ====================
$LOG_FILE = __DIR__ . '/logs/mark_listing_sold.log';

if (!is_dir(__DIR__ . '/logs')) {
    mkdir(__DIR__ . '/logs', 0755, true);
}

function writeLog($message, $level = 'INFO') {
    global $LOG_FILE;
    $timestamp = date('Y-m-d H:i:s');
    $logEntry = "[$timestamp] [$level] $message" . PHP_EOL;
    file_put_contents($LOG_FILE, $logEntry, FILE_APPEND | LOCK_EX);
}

// ==================== API START ====================
writeLog("========== MARK LISTING STATUS START ==========");
writeLog("Request Method: " . $_SERVER['REQUEST_METHOD']);

// Only accept POST requests
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    writeLog("Method not allowed", "ERROR");
    json_out(['error' => 'Method not allowed'], 405);
}

// Get the Authorization header
$headers = getallheaders();
$authHeader = $headers['Authorization'] ?? '';

if (empty($authHeader) || !preg_match('/Bearer\s+(.*)$/i', $authHeader, $matches)) {
    writeLog("No valid Bearer token", "ERROR");
    json_out(['error' => 'Unauthorized - No token provided'], 401);
}

$token = $matches[1];

// Get POST data
$listingId = $_POST['listing_id'] ?? null;
$isSold = $_POST['is_sold'] ?? null;

writeLog("POST data - listing_id: " . ($listingId ?? "NULL") . ", is_sold: " . ($isSold ?? "NULL"));

if (!$listingId) {
    json_out(['error' => 'listing_id is required'], 400);
}

if ($isSold === null) {
    json_out(['error' => 'is_sold is required (0 or 1)'], 400);
}

$isSold = (int)$isSold;
$newStatus = $isSold ? 'sold' : 'active';

try {
    // Decode JWT token
    $parts = explode('.', $token);
    if (count($parts) !== 3) {
        json_out(['error' => 'Invalid token format'], 401);
    }
    
    $payload = json_decode(base64_decode(strtr($parts[1], '-_', '+/')), true);
    if (!$payload) {
        json_out(['error' => 'Invalid token payload'], 401);
    }
    
    // Get user_id from token
    $userId = $payload['user_id'] ?? $payload['uid'] ?? $payload['sub'] ?? null;
    
    if (!$userId) {
        $pdo = pdo();
        $stmt = $pdo->prepare("SELECT user_id FROM auth_tokens WHERE access_token = :token AND expires_at > NOW()");
        $stmt->execute(['token' => $token]);
        $tokenRow = $stmt->fetch(PDO::FETCH_ASSOC);
        
        if ($tokenRow) {
            $userId = $tokenRow['user_id'];
        } else {
            json_out(['error' => 'Invalid or expired token'], 401);
        }
    }
    
    writeLog("User ID: $userId, Listing ID: $listingId, New Status: $newStatus");
    
    $pdo = pdo();
    
    // Verify this listing belongs to the user
    $stmt = $pdo->prepare("SELECT listing_id, user_id, status FROM listing WHERE listing_id = :listing_id");
    $stmt->execute(['listing_id' => $listingId]);
    $listing = $stmt->fetch(PDO::FETCH_ASSOC);
    
    if (!$listing) {
        writeLog("Listing not found", "ERROR");
        json_out(['error' => 'Listing not found'], 404);
    }
    
    if ((int)$listing['user_id'] !== (int)$userId) {
        writeLog("Authorization failed - listing belongs to different user", "ERROR");
        json_out(['error' => 'You can only modify your own listings'], 403);
    }
    
    writeLog("Current status: " . $listing['status']);
    
    // Update the status
    $stmt = $pdo->prepare("UPDATE listing SET status = :status, updated_at = NOW() WHERE listing_id = :listing_id");
    $stmt->execute([
        'status' => $newStatus,
        'listing_id' => $listingId
    ]);
    
    writeLog("Listing $listingId marked as $newStatus", "SUCCESS");
    
    $response = [
        'success' => true,
        'message' => "Listing marked as $newStatus",
        'listing_id' => (int)$listingId,
        'is_sold' => (bool)$isSold,
        'status' => $newStatus
    ];
    
    json_out($response, 200);
    
} catch (PDOException $e) {
    writeLog("DATABASE ERROR: " . $e->getMessage(), "ERROR");
    json_out(['error' => 'Database error: ' . $e->getMessage()], 500);
} catch (Exception $e) {
    writeLog("EXCEPTION: " . $e->getMessage(), "ERROR");
    json_out(['error' => 'Server error: ' . $e->getMessage()], 500);
}
