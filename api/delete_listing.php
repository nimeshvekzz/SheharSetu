<?php
/**
 * Delete Listing API
 * Permanently deletes a listing and its related data
 * DB has ON DELETE CASCADE for listing_media and listing_attribute_value
 * Requires JWT authentication + ownership verification
 */

require_once __DIR__ . '/config.php';
require_once __DIR__ . '/auth_middleware.php';

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

// Only accept POST requests
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    json_out(['error' => 'Method not allowed'], 405);
}

// ===================== JWT AUTHENTICATION =====================
$userId = authenticate();
// ===================== END JWT AUTHENTICATION =====================

// Accept listing_id from POST body OR JSON body
$listingId = $_POST['listing_id'] ?? null;

// Also check for JSON body (Volley may send as form-encoded or JSON)
if (!$listingId) {
    $raw = file_get_contents('php://input');
    if ($raw) {
        $decoded = json_decode($raw, true);
        if ($decoded && isset($decoded['listing_id'])) {
            $listingId = $decoded['listing_id'];
        }
        // Also try parsing as form-encoded
        if (!$listingId) {
            parse_str($raw, $parsed);
            if (isset($parsed['listing_id'])) {
                $listingId = $parsed['listing_id'];
            }
        }
    }
}

if (!$listingId) {
    error_log("delete_listing: listing_id missing. POST=" . json_encode($_POST) . " RAW=" . file_get_contents('php://input'));
    json_out(['error' => 'listing_id is required'], 400);
}

$listingId = (int) $listingId;

try {
    $pdo = pdo();

    // Verify this listing belongs to the user
    $stmt = $pdo->prepare("SELECT listing_id, user_id FROM listing WHERE listing_id = :listing_id");
    $stmt->execute(['listing_id' => $listingId]);
    $listing = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$listing) {
        json_out(['error' => 'Listing not found'], 404);
    }

    if ((int)$listing['user_id'] !== (int)$userId) {
        json_out(['error' => 'You can only delete your own listings'], 403);
    }

    // Just delete the listing row — DB CASCADE handles media & attributes
    $stmt = $pdo->prepare("DELETE FROM listing WHERE listing_id = :lid");
    $stmt->execute(['lid' => $listingId]);

    json_out([
        'success' => true,
        'message' => 'Listing deleted successfully',
        'listing_id' => $listingId
    ], 200);

} catch (PDOException $e) {
    error_log("delete_listing DB error: " . $e->getMessage());
    json_out(['error' => 'Database error: ' . $e->getMessage()], 500);
} catch (Exception $e) {
    error_log("delete_listing error: " . $e->getMessage());
    json_out(['error' => 'Server error: ' . $e->getMessage()], 500);
}
