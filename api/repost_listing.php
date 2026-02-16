<?php
/**
 * Repost Listing API
 * Bumps a listing to the top of the feed by resetting created_at to NOW()
 * Also re-activates the listing if it was sold/paused
 * Requires JWT authentication + ownership verification
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

// Get listing_id from POST
$listingId = $_POST['listing_id'] ?? null;

if (!$listingId) {
    json_out(['error' => 'listing_id is required'], 400);
}

$listingId = (int) $listingId;

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
        json_out(['error' => 'You can only repost your own listings'], 403);
    }

    // Update created_at to NOW() so it appears at top of feed
    // Also set status to 'active' (in case it was sold/paused)
    $stmt = $pdo->prepare("
        UPDATE listing 
        SET created_at = NOW(), 
            updated_at = NOW(), 
            status = 'active' 
        WHERE listing_id = :listing_id
    ");
    $stmt->execute(['listing_id' => $listingId]);

    $newDate = date('Y-m-d H:i:s');

    json_out([
        'success' => true,
        'message' => 'Listing reposted successfully! It will now appear at the top.',
        'listing_id' => $listingId,
        'new_date' => $newDate,
        'status' => 'active'
    ], 200);

} catch (PDOException $e) {
    error_log("❌ repost_listing DB: " . $e->getMessage());
    json_out(['error' => 'Database error'], 500);
} catch (Exception $e) {
    error_log("❌ repost_listing: " . $e->getMessage());
    json_out(['error' => 'Server error'], 500);
}
