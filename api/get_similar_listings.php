<?php
/**
 * GET /api/get_similar_listings.php?listing_id=123&category_id=1&limit=10
 *
 * Returns similar listings:
 * 1. Same category first (excluding current listing)
 * 2. Fill remaining with other categories
 * Max 10 listings by default
 */
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-cache, no-store, must-revalidate');

require_once __DIR__ . '/config.php';

$pdo = null;
try {
    $pdo = pdo();
} catch (Throwable $e) {
    http_response_code(500);
    echo json_encode(["status" => "error", "message" => "DB connection failed"]);
    exit;
}

/* ----------------------- Helpers ----------------------- */

function ensure_absolute_url($path) {
    if (empty($path)) return "";
    if (strpos($path, 'http') === 0) return $path;
    if (strpos($path, 'data:') === 0) return $path;
    
    // Detect raw Base64
    if (preg_match('/^(\/9j|iVB|R0l)/', $path)) {
        $mime = 'image/jpeg';
        if (strpos($path, 'iVB') === 0) $mime = 'image/png';
        if (strpos($path, 'R0l') === 0) $mime = 'image/gif';
        return "data:$mime;base64," . $path;
    }

    $base = defined('BASE_URL') ? BASE_URL : "https://magenta-owl-444153.hostingersite.com/api";
    return $base . '/' . ltrim($path, '/');
}

function money_fmt($v): string {
    if ($v === null || $v === '') return '';
    $s = (string)$v;
    if (strpos($s, '₹') === 0) return $s;
    return '₹ ' . $s;
}

/* ----------------------- Input ----------------------- */
$listingId = isset($_GET['listing_id']) ? (int)$_GET['listing_id'] : 0;
$categoryId = isset($_GET['category_id']) ? (int)$_GET['category_id'] : 0;
$limit = isset($_GET['limit']) ? (int)$_GET['limit'] : 10;

if ($limit < 1) $limit = 10;
if ($limit > 20) $limit = 20;

if ($listingId <= 0) {
    echo json_encode(["status" => "error", "message" => "listing_id is required"]);
    exit;
}

/* ----------------------- Main ----------------------- */
try {
    $results = [];
    $hasMedia = false;
    
    // Check if listing_media table exists
    try {
        $pdo->query("SELECT 1 FROM listing_media LIMIT 1");
        $hasMedia = true;
    } catch (Exception $e) {}

    // Check column existence
    $listCols = $pdo->query("SHOW COLUMNS FROM `listing`")->fetchAll(PDO::FETCH_COLUMN);
    $hasVillage = in_array('village_name', $listCols);
    $hasImage = in_array('image_url', $listCols);

    // Step 1: Fetch same-category listings first
    if ($categoryId > 0) {
        $sql = "SELECT l.listing_id, l.title, l.price, l.district, l.state, l.category_id,
                " . ($hasVillage ? "l.village_name," : "") . "
                " . ($hasImage ? "l.image_url" : "'' as image_url") . "
                FROM listing l
                WHERE l.category_id = :catId 
                  AND l.listing_id != :listingId
                  AND l.status = 'active'
                  AND l.deleted_at IS NULL
                ORDER BY l.created_at DESC
                LIMIT :limit";
        
        $stmt = $pdo->prepare($sql);
        $stmt->bindValue(':catId', $categoryId, PDO::PARAM_INT);
        $stmt->bindValue(':listingId', $listingId, PDO::PARAM_INT);
        $stmt->bindValue(':limit', $limit, PDO::PARAM_INT);
        $stmt->execute();
        $sameCatRows = $stmt->fetchAll(PDO::FETCH_ASSOC);
        
        foreach ($sameCatRows as $row) {
            $results[] = $row;
        }
    }

    // Step 2: If we need more, fetch from other categories
    $remaining = $limit - count($results);
    if ($remaining > 0) {
        $excludeIds = [$listingId];
        foreach ($results as $r) {
            $excludeIds[] = (int)$r['listing_id'];
        }
        $placeholders = implode(',', array_fill(0, count($excludeIds), '?'));
        
        $sql = "SELECT l.listing_id, l.title, l.price, l.district, l.state, l.category_id,
                " . ($hasVillage ? "l.village_name," : "") . "
                " . ($hasImage ? "l.image_url" : "'' as image_url") . "
                FROM listing l
                WHERE l.listing_id NOT IN ($placeholders)
                  AND l.status = 'active'
                  AND l.deleted_at IS NULL
                ORDER BY l.created_at DESC
                LIMIT ?";
        
        $stmt = $pdo->prepare($sql);
        $paramIndex = 1;
        foreach ($excludeIds as $eid) {
            $stmt->bindValue($paramIndex++, $eid, PDO::PARAM_INT);
        }
        $stmt->bindValue($paramIndex, $remaining, PDO::PARAM_INT);
        $stmt->execute();
        $otherRows = $stmt->fetchAll(PDO::FETCH_ASSOC);
        
        foreach ($otherRows as $row) {
            $results[] = $row;
        }
    }

    // Step 3: Format results with images
    $listings = [];
    foreach ($results as $row) {
        $lid = (int)$row['listing_id'];
        $title = $row['title'] ?? "Untitled";
        $price = money_fmt($row['price'] ?? 0);
        $city = !empty($row['village_name']) ? $row['village_name'] : 
               (!empty($row['district']) ? $row['district'] : ($row['state'] ?? ''));
        
        // Get first image
        $imageUrl = "";
        
        // Try listing_media first
        if ($hasMedia) {
            try {
                $mStmt = $pdo->prepare("SELECT file_url FROM listing_media WHERE listing_id = :lid AND media_type='image' ORDER BY sort_order ASC LIMIT 1");
                $mStmt->execute(['lid' => $lid]);
                if ($m = $mStmt->fetch(PDO::FETCH_ASSOC)) {
                    if (!empty($m['file_url'])) {
                        $imageUrl = ensure_absolute_url($m['file_url']);
                    }
                }
            } catch (Exception $e) {}
        }
        
        // Fallback: Try listing_attribute_value (attribute_id 4006 = listing_photos)
        if (empty($imageUrl)) {
            try {
                $attrStmt = $pdo->prepare("SELECT value_text FROM listing_attribute_value WHERE listing_id = :lid AND attribute_id = 4006 LIMIT 1");
                $attrStmt->execute(['lid' => $lid]);
                if ($attrRow = $attrStmt->fetch(PDO::FETCH_ASSOC)) {
                    $json = json_decode($attrRow['value_text'] ?? '{}', true);
                    if (is_array($json) && !empty($json['cover'])) {
                        $img = stripslashes($json['cover']);
                        if (!preg_match('/^http/i', $img) && !preg_match('/^data:/i', $img)) {
                            $imageUrl = "data:image/jpeg;base64," . $img;
                        } else {
                            $imageUrl = $img;
                        }
                    }
                }
            } catch (Exception $e) {}
        }
        
        // Fallback to image_url column
        if (empty($imageUrl) && $hasImage && !empty($row['image_url'])) {
            $imageUrl = ensure_absolute_url($row['image_url']);
        }

        $listings[] = [
            "id"        => $lid,
            "title"     => $title,
            "price"     => $price,
            "city"      => $city,
            "image_url" => $imageUrl
        ];
    }

    echo json_encode(["status" => "success", "data" => $listings], JSON_UNESCAPED_UNICODE);

} catch (Throwable $e) {
    http_response_code(500);
    echo json_encode(["status" => "error", "message" => "Server error", "debug" => $e->getMessage()]);
}
?>
