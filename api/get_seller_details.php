<?php
/**
 * GET /api/get_seller_details.php?user_id=123
 *
 * Fetches public profile of a seller and their active listings.
 * No Authentication required.
 */
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-cache, no-store, must-revalidate');

require_once __DIR__ . '/config.php';

// --- DEBUG MODE: SHOW ALL ERRORS ---
ini_set('display_errors', 1);
ini_set('display_startup_errors', 1);
error_reporting(E_ALL);
// -----------------------------------

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
    $path = (string)$path; // Force string
    if (strpos($path, 'http') === 0) return $path;
    if (strpos($path, 'data:') === 0) return $path;
    
    // Detect raw Base64 (common headers: /9j for jpg, iVB for png, R0l for gif)
    if (preg_match('/^(\/9j|iVB|R0l)/', $path)) {
        // Assume JPEG if starts with /9j, PNG if iVB. Defaulting to jpeg for generic logic if needed
        $mime = 'image/jpeg';
        if (strpos($path, 'iVB') === 0) $mime = 'image/png';
        if (strpos($path, 'R0l') === 0) $mime = 'image/gif';
        return "data:$mime;base64," . $path;
    }

    $base = defined('BASE_URL') ? BASE_URL : "https://magenta-owl-444153.hostingersite.com/api";
    return $base . '/' . ltrim($path, '/');
}

function human_since(string $ts): string {
    if (empty($ts)) return '';
    $t = strtotime($ts);
    if (!$t) return $ts;
    $diff = time() - $t;
    if ($diff < 60) return "Just now";
    if ($diff < 3600) return floor($diff / 60) . "m ago";
    if ($diff < 86400) return floor($diff / 3600) . "h ago";
    if ($diff < 604800) return floor($diff / 86400) . "d ago";
    return date('d M Y', $t);
}

/* ----------------------- Input ----------------------- */
$userId = isset($_GET['user_id']) ? (int)$_GET['user_id'] : 0;
if ($userId <= 0) {
    http_response_code(400);
    echo json_encode(["status" => "error", "message" => "user_id is required"]);
    exit;
}

/* ----------------------- Main ----------------------- */
try {
    // 1. Fetch User Details
    // Check columns
    $hasFullName = false; $hasName = false; $hasFirst = false;
    $userCols = $pdo->query("SHOW COLUMNS FROM `user`")->fetchAll(PDO::FETCH_COLUMN);
    if (in_array('full_name', $userCols)) $hasFullName = true;
    if (in_array('name', $userCols)) $hasName = true;
    if (in_array('first_name', $userCols)) $hasFirst = true;
    
    $pk = in_array('user_id', $userCols) ? 'user_id' : 'id';

    $sqlUser = "SELECT * FROM `user` WHERE `$pk` = :uid LIMIT 1";
    $stmt = $pdo->prepare($sqlUser);
    $stmt->execute(['uid' => $userId]);
    $user = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$user) {
        http_response_code(404);
        echo json_encode(["status" => "error", "message" => "User not found"]);
        exit;
    }

    // Name Logic
    $name = "Seller";
    if ($hasFullName && !empty($user['full_name'])) {
        $name = $user['full_name'];
    } elseif ($hasName && !empty($user['name'])) {
        $name = $user['name'];
    } elseif ($hasFirst) {
        $fn = trim((string)($user['first_name'] ?? ''));
        $ln = trim((string)($user['last_name'] ?? ''));
        if ($fn || $ln) $name = trim("$fn $ln");
    }

    // Profile Data
    $profile = [
        "id"           => $userId,
        "name"         => $name,
        "phone"        => $user['phone'] ?? ($user['mobile'] ?? ''),
        "avatar_url"   => ensure_absolute_url($user['avatar_url'] ?? ""),
        "member_since" => isset($user['created_at']) ? date('M Y', strtotime($user['created_at'])) : "",
    ];

    // 2. Fetch Active Listings
    // Check listing columns
    $listCols = $pdo->query("SHOW COLUMNS FROM `listing`")->fetchAll(PDO::FETCH_COLUMN);
    $hasImage = in_array('image_url', $listCols);
    $hasStatus = in_array('status', $listCols);
    $hasDeleted = in_array('deleted_at', $listCols);

    $sqlList = "SELECT l.listing_id, l.title, l.price, l.created_at, l.category_id, l.district, l.state, l.village_name ";
    if ($hasStatus) {
        $sqlList .= ", l.status";
    }
    if ($hasImage) {
        $sqlList .= ", l.image_url";
    }
    $sqlList .= " FROM `listing` l WHERE l.user_id = :uid";
    
    if ($hasStatus) $sqlList .= " AND l.status IN ('active', 'sold')";
    if ($hasDeleted) $sqlList .= " AND l.deleted_at IS NULL";
    
    $sqlList .= " ORDER BY l.created_at DESC";

    $stmtL = $pdo->prepare($sqlList);
    $stmtL->execute(['uid' => $userId]);
    $rawListings = $stmtL->fetchAll(PDO::FETCH_ASSOC);

    // Fetch category names for mapping
    $catMap = [];
    try {
        $catStmt = $pdo->query("SELECT category_id, name FROM category");
        while ($c = $catStmt->fetch(PDO::FETCH_ASSOC)) {
            $catMap[$c['category_id']] = $c['name'];
        }
    } catch (Exception $e) {}

    // Check for listing_media table existence
    $hasMediaTable = false;
    try {
        $pdo->query("SELECT 1 FROM listing_media LIMIT 1");
        $hasMediaTable = true;
    } catch (Exception $e) {}

    // Check for listing_attribute_value table existence
    $hasAttrTable = false;
    try {
        $pdo->query("SELECT 1 FROM listing_attribute_value LIMIT 1");
        $hasAttrTable = true;
    } catch (Exception $e) {}

    $listings = [];
    foreach ($rawListings as $row) {
        $lid = (int)$row['listing_id'];
        $images = [];
        
        // 1. Try listing_media
        if ($hasMediaTable) {
            try {
                $mStmt = $pdo->prepare("SELECT file_url FROM listing_media WHERE listing_id = :lid AND media_type='image' ORDER BY sort_order ASC LIMIT 5");
                $mStmt->execute(['lid' => $lid]);
                while ($m = $mStmt->fetch(PDO::FETCH_ASSOC)) {
                    if (!empty($m['file_url'])) {
                        $images[] = ensure_absolute_url($m['file_url']);
                    }
                }
            } catch (Exception $e) {}
        }

        // 2. Try listing_attribute_value (JSON) - Specifically for Photos (attribute_id=4006)
        if (empty($images) && $hasAttrTable) {
             try {
                // Fetch specifically attribute_id 4006 (listing_photos)
                $aStmt = $pdo->prepare("SELECT value_text FROM listing_attribute_value WHERE listing_id = :lid AND attribute_id = 4006 LIMIT 1"); 
                $aStmt->execute(['lid' => $lid]);
                
                if ($r = $aStmt->fetch(PDO::FETCH_ASSOC)) {
                     $val = (string)($r['value_text'] ?? '');
                     if (strpos($val, '{') !== false) {
                        $j = json_decode($val, true);
                        if (is_array($j)) {
                            if (!empty($j['cover'])) $images[] = ensure_absolute_url($j['cover']);
                            if (!empty($j['more']) && is_array($j['more'])) {
                                foreach ($j['more'] as $img) {
                                    if(count($images) < 5) $images[] = ensure_absolute_url($img);
                                }
                            }
                        }
                     }
                }
            } catch (Exception $e) {} 
        }

        // 3. Fallback removed as 'image_url' column does not exist in schema
        
        // --- DEBUG: Print found images for first item ---
        if (count($listings) < 1) {
             error_log("LID=$lid Images found: " . count($images) . " | MediaTable: " . ($hasMediaTable ? 'YES' : 'NO') . " | AttrTable: " . ($hasAttrTable ? 'YES' : 'NO'));
             if (empty($images)) {
                 error_log("  -> Row image_url: " . ($row['image_url'] ?? 'NULL'));
             }
        }
        // -----------------------------------------------
        
        // Legacy single image key for backward compat (first image)
        $img = !empty($images) ? $images[0] : "";

        $priceStr = (string)($row['price'] ?? '');

        $priceStr = (string)($row['price'] ?? '');
        if (is_numeric($priceStr) || (strpos($priceStr, '₹') === false && !empty($priceStr))) {
             $priceStr = '₹ ' . $priceStr;
        }

        $listings[] = [
            "id"          => $lid,
            "title"       => $row['title'] ?? "Untitled",
            "price"       => $priceStr,
            "image_url"   => $img,
            "images"      => $images,
            "category"    => $catMap[$row['category_id']] ?? "Unknown",
            "posted_time" => human_since($row['created_at'] ?? ''),
            "city"        => $row['village_name'] ?: ($row['district'] ?: $row['state'] ?: ""),
            "status"      => $row['status'] ?? 'active'
        ];
    }

    $profile['listings_count'] = count($listings);

    echo json_encode([
        "status" => "success",
        "data" => [
            "profile" => $profile,
            "listings" => $listings
        ]
    ], JSON_UNESCAPED_UNICODE);

} catch (Throwable $e) {
    http_response_code(500);
    echo json_encode(["status" => "error", "message" => "Server error", "debug" => $e->getMessage()]);
}
?>
