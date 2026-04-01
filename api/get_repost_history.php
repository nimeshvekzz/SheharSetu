<?php
/**
 * Get Repost History API
 * Returns the repost log for a listing owned by the authenticated user.
 * Requires JWT authentication + ownership verification.
 *
 * GET ?listing_id=123
 * Response: { success: true, listing_id: 123, repost_count: 3, history: [...] }
 */

require_once __DIR__ . '/config.php';
require_once __DIR__ . '/auth_middleware.php';

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, no-cache, must-revalidate');

if ($_SERVER['REQUEST_METHOD'] !== 'GET') {
    json_out(['error' => 'Method not allowed'], 405);
}

// ===================== JWT AUTHENTICATION =====================
$userId = authenticate();
// ===================== END JWT AUTHENTICATION =====================

$listingId = $_GET['listing_id'] ?? null;

if (!$listingId) {
    json_out(['error' => 'listing_id is required'], 400);
}

$listingId = (int) $listingId;

try {
    $pdo = pdo();

    // Verify listing exists and belongs to this user
    $stmt = $pdo->prepare("SELECT listing_id, user_id, repost_count FROM listing WHERE listing_id = :listing_id");
    $stmt->execute(['listing_id' => $listingId]);
    $listing = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$listing) {
        json_out(['error' => 'Listing not found'], 404);
    }

    if ((int)$listing['user_id'] !== (int)$userId) {
        json_out(['error' => 'You can only view repost history for your own listings'], 403);
    }

    // Fetch repost history
    $stmt = $pdo->prepare("
        SELECT reposted_at, ip_address
        FROM listing_repost_log
        WHERE listing_id = :listing_id
        ORDER BY reposted_at DESC
        LIMIT 50
    ");
    $stmt->execute(['listing_id' => $listingId]);
    $history = $stmt->fetchAll(PDO::FETCH_ASSOC);

    json_out([
        'success'      => true,
        'listing_id'   => $listingId,
        'repost_count' => (int)$listing['repost_count'],
        'history'      => $history
    ], 200);

} catch (PDOException $e) {
    error_log("❌ get_repost_history DB: " . $e->getMessage());
    json_out(['error' => 'Database error'], 500);
} catch (Exception $e) {
    error_log("❌ get_repost_history: " . $e->getMessage());
    json_out(['error' => 'Server error'], 500);
}
