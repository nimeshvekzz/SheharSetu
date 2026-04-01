<?php
/**
 * GET /api/get_listing.php?listing_id=#
 *
 * Modified to include dynamic attributes.
 */
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/config.php';
$pdo = null;
try { $pdo = pdo(); }
catch (Throwable $e) {
    http_response_code(500);
    echo json_encode(["status"=>"error","message"=>"DB connection failed"]); exit;
}

/* ----------------------- Input ----------------------- */
$listingId = isset($_GET['listing_id']) ? (int)$_GET['listing_id'] : 0;
if ($listingId <= 0) {
    http_response_code(400);
    echo json_encode(["status"=>"error","message"=>"listing_id is required"]); exit;
}

/* ----------------------- Helpers ----------------------- */
function col_exists(PDO $pdo, string $table, string $col): bool {
    try {
        $sql = "SHOW COLUMNS FROM `$table` LIKE :c";
        $st = $pdo->prepare($sql);
        $st->bindValue(':c', $col, PDO::PARAM_STR);
        $st->execute();
        return (bool)$st->fetch();
    } catch (Throwable $e) { return false; }
}
function table_exists(PDO $pdo, string $table): bool {
    try { $pdo->query("SELECT 1 FROM `$table` LIMIT 1"); return true; }
    catch (Throwable $e) { return false; }
}
function money_fmt($v): string {
    if ($v === null || $v === '') return '';
    $s = (string)$v;
    if (strpos($s, '₹') === 0) return $s;
    return '₹ ' . $s;
}
function human_since(string $ts): string {
    if (empty($ts)) return '';
    $t = strtotime($ts);
    if (!$t) return $ts;
    $diff = time() - $t;
    if ($diff < 60)    return $diff . "s ago";
    if ($diff < 3600)  return floor($diff/60) . "m ago";
    if ($diff < 86400) return floor($diff/3600) . "h ago";
    if ($diff < 86400*30) return floor($diff/86400) . "d ago";
    return date('Y-m-d', $t);
}

/* ----------------------- Main fetch ----------------------- */
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

try {
    // 1. Fetch Basic Listing Info
    // Robust column checks
    $hasImageCol   = col_exists($pdo, 'listing', 'image_url');
    $hasCurrency   = col_exists($pdo, 'listing', 'currency');
    $hasLat        = col_exists($pdo, 'listing', 'latitude');
    $hasLng        = col_exists($pdo, 'listing', 'longitude');
    $hasVillage    = col_exists($pdo, 'listing', 'village_name');
    $hasStatus     = col_exists($pdo, 'listing', 'status');
    $hasDeleted    = col_exists($pdo, 'listing', 'deleted_at');
    $hasCreated    = col_exists($pdo, 'listing', 'created_at');
    $hasIsNew      = col_exists($pdo, 'listing', 'is_new');

    $select = "SELECT listing_id, user_id, category_id, subcategory_id,
                      title, description, price,
                      ".($hasCurrency ? "currency," : "")."
                      state, district, ".($hasVillage?"village_name,":"")."
                      pincode, ".($hasLat?"latitude,":"")." ".($hasLng?"longitude,":"")."
                      ".($hasImageCol ? "image_url," : "")."
                      ".($hasCreated ? "created_at," : "")."
                      ".($hasIsNew ? "is_new," : "")."
                      status
               FROM listing
               WHERE listing_id = :id";
    
    // Safety check for deleted or inactive
    // Note: If you want owners to see their own non-active listings, logic needs to be broader.
    // Assuming public API:
    // $select .= " AND status = 'active'"; 

    $st = $pdo->prepare($select);
    $st->bindValue(':id', $listingId, PDO::PARAM_INT);
    $st->execute();
    $row = $st->fetch(PDO::FETCH_ASSOC);

    if (!$row) {
        http_response_code(404);
        echo json_encode(["status"=>"error","message"=>"Listing not found"]); exit;
    }

    // 2. Fetch Images
    $images = [];
    if (table_exists($pdo, 'listing_media')) {
        $imgSql = "SELECT media_url FROM listing_media WHERE listing_id = :id AND media_type='image' ORDER BY sort_order ASC";
        // Attempt to run, fallback if columns missing
        try {
            $is = $pdo->prepare($imgSql);
            $is->bindValue(':id', $listingId, PDO::PARAM_INT);
            $is->execute();
            while ($r = $is->fetch(PDO::FETCH_ASSOC)) {
                if(!empty($r['media_url'])) $images[] = ensure_absolute_url($r['media_url']);
            }
        } catch (Exception $e) {}
    }
    // Fallback to single image
    if (empty($images) && $hasImageCol && !empty($row['image_url'])) {
        $images[] = ensure_absolute_url($row['image_url']);
    }

    // 3. Fetch Seller Info (Fixed for user_id)
    $seller = null;
    $uid = (int)$row['user_id'];
    if ($uid > 0 && table_exists($pdo, 'user')) {
        // Check if PK is 'user_id' or 'id'
        $pk = col_exists($pdo, 'user', 'user_id') ? 'user_id' : 'id';
        
        $sqlUser = "SELECT full_name, phone, created_at FROM user WHERE $pk = :uid LIMIT 1";
        // Check for 'full_name' or 'name' or 'first_name'
        if (!col_exists($pdo, 'user', 'full_name')) {
             if (col_exists($pdo, 'user', 'name')) $sqlUser = "SELECT name as full_name, phone, created_at FROM user WHERE $pk = :uid LIMIT 1";
             else $sqlUser = "SELECT CONCAT(first_name,' ',last_name) as full_name, phone, created_at FROM user WHERE $pk = :uid LIMIT 1";
        }

        $us = $pdo->prepare($sqlUser);
        $us->bindValue(':uid', $uid, PDO::PARAM_INT);
        $us->execute();
        if ($uRow = $us->fetch(PDO::FETCH_ASSOC)) {
            $seller = [
                "name" => $uRow['full_name'] ?? "Seller",
                "phone" => $uRow['phone'] ?? "",
                "member_since" => $uRow['created_at'] ? date('Y', strtotime($uRow['created_at'])) : "",
                "listings_count" => 0 // simplified
            ];
            
            // Count listings
            try {
                $cSql = "SELECT COUNT(*) as c FROM listing WHERE user_id = :uid AND status='active'";
                $cs = $pdo->prepare($cSql);
                $cs->bindValue(':uid', $uid, PDO::PARAM_INT);
                $cs->execute();
                $seller['listings_count'] = (int)$cs->fetchColumn();
            } catch(Exception $e){}
        }
    }

    // 4. Fetch Dynamic Attributes
    $attributes = [];
    if (table_exists($pdo, 'listing_attribute_value') && table_exists($pdo, 'attribute')) {
        $attrSql = "
            SELECT 
                a.label,
                a.code,
                lav.value_string, lav.value_text, lav.value_int, lav.value_decimal, lav.value_bool,
                COALESCE(lav.unit, a.unit) as unit
            FROM listing_attribute_value lav
            JOIN attribute a ON lav.attribute_id = a.attribute_id
            WHERE lav.listing_id = :lid
            ORDER BY a.updated_at ASC
        ";
        $as = $pdo->prepare($attrSql);
        $as->bindValue(':lid', $listingId, PDO::PARAM_INT);
        $as->execute();
        
        while ($rec = $as->fetch(PDO::FETCH_ASSOC)) {
            $val = '';
            if ($rec['value_string'] !== null && $rec['value_string'] !== '') $val = $rec['value_string'];
            elseif ($rec['value_text'] !== null) $val = $rec['value_text'];
            elseif ($rec['value_int'] !== null) $val = (string)$rec['value_int'];
            elseif ($rec['value_decimal'] !== null) $val = (string)(float)$rec['value_decimal']; // float cast removes trailing zeros
            elseif ($rec['value_bool'] !== null) $val = $rec['value_bool'] ? 'Yes' : 'No';

            // Filter out empty or internal attributes
            if ($val !== '' && !in_array($rec['code'], ['description', 'listing_photos', 'price'])) {
                $attributes[] = [
                    "label" => $rec['label'],
                    "value" => $val,
                    "unit"  => $rec['unit'] ?? ""
                ];
            }
        }
    }

    // Prepare Output
    $isNew = 0;
    if ($hasIsNew) $isNew = ($row['is_new'] == 1) ? 1 : 0;

    $city = !empty($row['village_name']) ? $row['village_name'] : 
           (!empty($row['district']) ? $row['district'] : $row['state']);

    $response = [
        "id" => (int)$row['listing_id'],
        "title" => $row['title'],
        "price" => money_fmt($row['price']),
        "description" => $row['description'],
        "city" => $city,
        "posted_when" => human_since($row['created_at'] ?? ''),
        "is_new" => $isNew,
        "status" => $row['status'],
        "seller" => $seller,
        "images" => $images,
        "attributes" => $attributes
    ];

    echo json_encode(["status"=>"success", "data"=>$response]);

} catch (Throwable $e) {
    http_response_code(500);
    echo json_encode(["status"=>"error","message"=>$e->getMessage()]);
}
?>
