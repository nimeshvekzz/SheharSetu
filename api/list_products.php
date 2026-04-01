<?php
/**
 * GET /api/list_products.php?page=1&limit=50&sort=newest&category_id=X&subcategory_id=Y&q=query
 *
 * Fetches listings with pagination, filtering, and MULTI-IMAGE support.
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

// Robust URL handler (same as in get_listing_details.php)
function ensure_absolute_url($path) {
    if (empty($path)) return "";
    if (strpos($path, 'http') === 0) return $path;
    if (strpos($path, 'data:') === 0) return $path;
    
    // Detect raw Base64 (common headers: /9j for jpg, iVB for png, R0l for gif)
    if (preg_match('/^(\/9j|iVB|R0l)/', $path)) {
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

function money_fmt($v): string {
    if ($v === null || $v === '') return '';
    $s = (string)$v;
    if (strpos($s, '₹') === 0) return $s;
    return '₹ ' . $s;
}

function table_exists(PDO $pdo, string $table): bool {
    try {
        $pdo->query("SELECT 1 FROM `$table` LIMIT 1");
        return true;
    } catch (Throwable $e) {
        return false;
    }
}

/* ----------------------- Input ----------------------- */
$page = isset($_GET['page']) ? (int)$_GET['page'] : 1;
if ($page < 1) $page = 1;
$limit = isset($_GET['limit']) ? (int)$_GET['limit'] : 20;
if ($limit < 1) $limit = 20;
if ($limit > 100) $limit = 100;
$offset = ($page - 1) * $limit;

$sort = $_GET['sort'] ?? 'newest'; // newest, oldest, price_asc, price_desc
$catId = isset($_GET['category_id']) ? (int)$_GET['category_id'] : 0;
$subCatId = isset($_GET['subcategory_id']) ? (int)$_GET['subcategory_id'] : 0;
$query = isset($_GET['q']) ? trim($_GET['q']) : '';
$isNew = isset($_GET['is_new']) ? (int)$_GET['is_new'] : -1; // -1: all, 1: new, 0: old

$userLat = isset($_GET['lat']) ? (float)$_GET['lat'] : null;
$userLng = isset($_GET['lng']) ? (float)$_GET['lng'] : null;
$radius  = isset($_GET['radius']) ? (float)$_GET['radius'] : null; // in km

/* ----------------------- Main ----------------------- */
try {
    // Build Query
    $where = ["l.status = 'active'", "l.deleted_at IS NULL"];
    $params = [];
    $distanceSql = "";

    if ($catId > 0) {
        $where[] = "l.category_id = :catId";
        $params['catId'] = $catId;
    }
    if ($subCatId > 0) {
        $where[] = "l.subcategory_id = :subCatId";
        $params['subCatId'] = $subCatId;
    }
    if ($query !== '') {
        $where[] = "(l.title LIKE :q OR l.description LIKE :q)";
        $params['q'] = "%$query%";
    }

    // Radius filtering (Haversine Formula)
    // PDO does NOT allow reusing named params, so use separate names for each occurrence
    if ($userLat !== null && $userLng !== null && $radius !== null) {
        $distanceSql = ", (6371 * acos(cos(radians(:d_lat)) * cos(radians(l.latitude)) * cos(radians(l.longitude) - radians(:d_lng)) + sin(radians(:d_lat2)) * sin(radians(l.latitude)))) AS distance";
        $params['d_lat'] = $userLat;
        $params['d_lng'] = $userLng;
        $params['d_lat2'] = $userLat;

        $where[] = "(6371 * acos(cos(radians(:w_lat)) * cos(radians(l.latitude)) * cos(radians(l.longitude) - radians(:w_lng)) + sin(radians(:w_lat2)) * sin(radians(l.latitude)))) <= :radius";
        $params['w_lat'] = $userLat;
        $params['w_lng'] = $userLng;
        $params['w_lat2'] = $userLat;
        $params['radius'] = $radius;
    }


    $hasIsNew = false;
    try {
        $c = $pdo->query("SHOW COLUMNS FROM listing LIKE 'is_new'");
        if ($c->fetch()) $hasIsNew = true;
    } catch(Exception $e){}

    if ($hasIsNew && $isNew !== -1) {
        $where[] = "l.is_new = :isNew";
        $params['isNew'] = $isNew;
    }

    $whereSql = implode(" AND ", $where);
    
    // Sort
    $orderBy = "l.created_at DESC";
    if ($sort === 'oldest') $orderBy = "l.created_at ASC";
    if ($sort === 'price_asc') $orderBy = "l.price ASC";
    if ($sort === 'price_desc') $orderBy = "l.price DESC";

    if (!empty($distanceSql) && empty($_GET['sort'])) {
        $orderBy = "distance ASC";
    }

    // Column checks for safety
    $listCols = $pdo->query("SHOW COLUMNS FROM `listing`")->fetchAll(PDO::FETCH_COLUMN);
    $hasVillage = in_array('village_name', $listCols);
    $hasImage = in_array('image_url', $listCols);

    // Main Select
    $sql = "SELECT l.listing_id, l.title, l.price, l.created_at, l.district, l.state, l.status,
            " . ($hasVillage ? "l.village_name," : "") . "
            " . ($hasImage ? "l.image_url," : "") . "
            " . ($hasIsNew ? "l.is_new" : "0 as is_new") . "
            $distanceSql
            FROM listing l
            WHERE $whereSql
            ORDER BY $orderBy
            LIMIT " . ($limit + 1) . " OFFSET $offset";

    $stmt = $pdo->prepare($sql);
    $stmt->execute($params);
    $rows = $stmt->fetchAll(PDO::FETCH_ASSOC);

    $listings = [];
    
    // Prepare for multi-image logic
    $hasMedia = table_exists($pdo, 'listing_media');
    $hasAttr = table_exists($pdo, 'listing_attribute_value');

    foreach ($rows as $row) {
        $lid = (int)$row['listing_id'];
        $title = $row['title'] ?? "Untitled";
        $price = money_fmt($row['price'] ?? 0);
        $city = !empty($row['village_name']) ? $row['village_name'] : 
               (!empty($row['district']) ? $row['district'] : ($row['state'] ?? ''));
        $posted = human_since($row['created_at'] ?? '');

        // --- FETCH IMAGES ---
        $images = [];
        $foundInMedia = false;

        // 1. Try listing_media (Best source for multiple images)
        if ($hasMedia) {
             try {
                // Fetch up to 5 images for the slider
                $mStmt = $pdo->prepare("SELECT file_url FROM listing_media WHERE listing_id = :lid AND media_type='image' ORDER BY sort_order ASC LIMIT 5");
                $mStmt->execute(['lid' => $lid]);
                while ($m = $mStmt->fetch(PDO::FETCH_ASSOC)) {
                    if (!empty($m['file_url'])) {
                        $images[] = ensure_absolute_url($m['file_url']);
                        $foundInMedia = true;
                    }
                }
            } catch (Exception $e) {} 
        }

        // 2. Try listing_attribute_value (JSON) if not found in media
        if (!$foundInMedia && $hasAttr) {
            try {
                $aStmt = $pdo->prepare("SELECT value_text FROM listing_attribute_value WHERE listing_id = :lid AND attribute_id=4006 LIMIT 1");
                $aStmt->execute(['lid' => $lid]);
                if ($r = $aStmt->fetch(PDO::FETCH_ASSOC)) {
                    $json = json_decode($r['value_text'] ?? '{}', true);
                    if (is_array($json)) {
                         if (!empty($json['cover'])) $images[] = ensure_absolute_url($json['cover']);
                         if (!empty($json['more']) && is_array($json['more'])) {
                             foreach ($json['more'] as $img) {
                                 // limit logic could be applied here
                                 if (count($images) < 5) $images[] = ensure_absolute_url($img);
                             }
                         }
                    }
                }
            } catch (Exception $e) {} 
        }

        // 3. Fallback to image_url column
        if (empty($images) && $hasImage && !empty($row['image_url'])) {
            $images[] = ensure_absolute_url($row['image_url']);
        }
        
        // 4. Fallback placeholder (optional, valid to return empty array and let client handle)
        // Client side checks images array size.

        $listings[] = [
            "id"          => $lid,
            "title"       => $title,
            "price"       => $price,
            "city"        => $city,
            "posted_time" => $posted,
            "is_new"      => (int)($row['is_new'] ?? 0),
            "status"      => $row['status'] ?? 'active',
            "images"      => $images
        ];
    }

    error_log("✅ list_products: Found " . count($listings) . " items.");

    // Determine if there are more pages
    $hasMore = count($listings) > $limit;
    if ($hasMore) {
        array_pop($listings); // Remove the extra fetched item
    }

    $json = json_encode(["status" => "success", "data" => $listings, "has_more" => $hasMore, "page" => $page], JSON_UNESCAPED_UNICODE);
    if ($json === false) {
        error_log("❌ list_products: JSON Encode Error: " . json_last_error_msg());
        http_response_code(500);
        echo json_encode(["status" => "error", "message" => "JSON Encode Error"]);
    } else {
        echo $json;
    }

} catch (Throwable $e) {
    error_log("❌ list_products: Exception: " . $e->getMessage());
    http_response_code(500);
    echo json_encode(["status" => "error", "message" => "Server error", "debug" => $e->getMessage()]);
}
?>
