<?php
/**
 * Mark Listing as Sold/Available API
 * Updates the status of a listing between 'sold' and 'active'
 * 
 * Database Schema Notes:
 * - listing.status: enum('draft','active','paused','sold','expired','rejected')
 */

require_once __DIR__ . '/config.php';
require_once __DIR__ . '/auth_middleware.php';

header('Content-Type: application/json; charset=utf-8');

// Only accept POST requests
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    json_out(['error' => 'Method not allowed'], 405);
}

// ===================== JWT AUTHENTICATION =====================
$userId = authenticate();
// ===================== END JWT AUTHENTICATION =====================

// Get POST data
$listingId = $_POST['listing_id'] ?? null;
$isSold = $_POST['is_sold'] ?? null;

if (!$listingId) {
    json_out(['error' => 'listing_id is required'], 400);
}

if ($isSold === null) {
    json_out(['error' => 'is_sold is required (0 or 1)'], 400);
}

$isSold = (int)$isSold;
$newStatus = $isSold ? 'sold' : 'active';

try {
    $pdo = pdo();
    
    // Verify this listing belongs to the user
    $stmt = $pdo->prepare("SELECT listing_id, user_id, status FROM listing WHERE listing_id = :listing_id");
    $stmt->execute(['listing_id' => $listingId]);
    $listing = $stmt->fetch(PDO::FETCH_ASSOC);
    
    if (!$listing) {
        json_out(['error' => 'Listing not found'], 404);
    }
    
    if ((int)$listing['user_id'] !== (int)$userId) {
        json_out(['error' => 'You can only modify your own listings'], 403);
    }
    
    // Update the status
    $stmt = $pdo->prepare("UPDATE listing SET status = :status, updated_at = NOW() WHERE listing_id = :listing_id");
    $stmt->execute([
        'status' => $newStatus,
        'listing_id' => $listingId
    ]);
    
    $response = [
        'success' => true,
        'message' => "Listing marked as $newStatus",
        'listing_id' => (int)$listingId,
        'is_sold' => (bool)$isSold,
        'status' => $newStatus
    ];
    
    json_out($response, 200);
    
} catch (PDOException $e) {
    error_log("❌ mark_listing_sold DB: " . $e->getMessage());
    json_out(['error' => 'Database error'], 500);
} catch (Exception $e) {
    error_log("❌ mark_listing_sold: " . $e->getMessage());
    json_out(['error' => 'Server error'], 500);
}
