<?php
/**
 * GET /api/get_listing_details.php?listing_id=#
 *
 * Enhanced version of get_listing.php that includes dynamic attributes.
 *
 * Response:
 * {
 *   "status":"success",
 *   "data":{
 *     "id":1,"title":"...","attributes":[...], ...
 *   }
 * }
 */
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');

// Use existing config.php as base configuration (lowercase per user context)
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
function col_exists(PDO $pdo, string $table, string $col): bool {
    try {
        $sql = "SHOW COLUMNS FROM `$table` LIKE :c";
        $st = $pdo->prepare($sql);
        $st->bindValue(':c', $col, PDO::PARAM_STR);
        $st->execute();
        return (bool)$st->fetch();
    } catch (Throwable $e) {
        return false;
    }
}

function table_exists(PDO $pdo, string $table): bool {
    try {
        $pdo->query("SELECT 1 FROM `$table` LIMIT 1");
        return true;
    } catch (Throwable $e) {
        return false;
    }
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
    if ($diff < 60) return $diff . "s ago";
    if ($diff < 3600) return floor($diff / 60) . "m ago";
    if ($diff < 86400) return floor($diff / 3600) . "h ago";
    if ($diff < 86400 * 30) return floor($diff / 86400) . "d ago";
    return date('Y-m-d', $t);
}

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

    // Check if BASE_URL constant exists from config.php
    $base = defined('BASE_URL') ? BASE_URL : "https://magenta-owl-444153.hostingersite.com/api";
    return $base . '/' . ltrim($path, '/');
}

/* ----------------------- Input ----------------------- */
$listingId = isset($_GET['listing_id']) ? (int)$_GET['listing_id'] : 0;
if ($listingId <= 0) {
    http_response_code(400);
    echo json_encode(["status" => "error", "message" => "listing_id is required"]);
    exit;
}

/* ----------------------- Main fetch ----------------------- */
try {
    // Check available columns
    $hasImageCol   = col_exists($pdo, 'listing', 'image_url');
    $hasCurrency   = col_exists($pdo, 'listing', 'currency');
    $hasLat        = col_exists($pdo, 'listing', 'latitude');
    $hasLng        = col_exists($pdo, 'listing', 'longitude');
    $hasVillage    = col_exists($pdo, 'listing', 'village_name');
    $hasStatus     = col_exists($pdo, 'listing', 'status');
    $hasModeration = col_exists($pdo, 'listing', 'moderation_status');
    $hasDeleted    = col_exists($pdo, 'listing', 'deleted_at');
    $hasCreated    = col_exists($pdo, 'listing', 'created_at');
    $hasUpdated    = col_exists($pdo, 'listing', 'updated_at');
    $hasViews      = col_exists($pdo, 'listing', 'views_count');
    $hasIsNew      = col_exists($pdo, 'listing', 'is_new');

    $select = "SELECT listing_id, user_id, category_id, subcategory_id,
                      title, description, price,
                      " . ($hasCurrency ? "currency," : "") . "
                      state, district, " . ($hasVillage ? "village_name," : "") . "
                      pincode, " . ($hasLat ? "latitude," : "") . " " . ($hasLng ? "longitude," : "") . "
                      " . ($hasImageCol ? "image_url," : "") . "
                      " . ($hasCreated ? "created_at," : "") . "
                      " . ($hasUpdated ? "updated_at," : "") . "
                      " . ($hasStatus ? "status," : "") . "
                      " . ($hasModeration ? "moderation_status," : "") . "
                      " . ($hasViews ? "views_count," : "") . "
                      " . ($hasIsNew ? "is_new," : "") . "
                      1 as _dummy
               FROM listing
               WHERE listing_id = :id";

    // Filter Logic
    $filters = [];
    if ($hasDeleted) $filters[] = "deleted_at IS NULL";
    if ($filters) $select .= " AND " . implode(" AND ", $filters);

    $st = $pdo->prepare($select);
    $st->bindValue(':id', $listingId, PDO::PARAM_INT);
    $st->execute();
    $row = $st->fetch(PDO::FETCH_ASSOC);

    if (!$row) {
        http_response_code(404);
        echo json_encode(["status" => "error", "message" => "Listing not found"]);
        exit;
    }

    // Increment views
    if ($hasViews) {
        try {
            $u = $pdo->prepare("UPDATE listing SET views_count = COALESCE(views_count,0)+1 WHERE listing_id = :id");
            $u->bindValue(':id', $listingId, PDO::PARAM_INT);
            $u->execute();
            $row['views_count'] = (int)($row['views_count'] ?? 0) + 1;
        } catch (Throwable $e) {}
    }

    // --- Images Fetching Strategy ---
    $images = [];
    $foundImages = false;

    // 1. Check listing_media (Newest, multi-image table)
    if (table_exists($pdo, 'listing_media')) {
        $imgSql = "SELECT file_url FROM listing_media WHERE listing_id = :id AND media_type='image' ORDER BY sort_order ASC";
        // Attempt to run, fallback if columns missing or other issues
        try {
            $is = $pdo->prepare($imgSql);
            $is->bindValue(':id', $listingId, PDO::PARAM_INT);
            $is->execute();
            while ($r = $is->fetch(PDO::FETCH_ASSOC)) {
                if(!empty($r['file_url'])) {
                    $images[] = $r['file_url'];
                    $foundImages = true;
                }
            }
        } catch (Exception $e) {}
    }

    // 2. Check listing_attribute_value (If no images found yet)
    // Sometimes images are stored as JSON in an attribute (e.g. code 'listing_photos' or similar)
    if (!$foundImages && table_exists($pdo, 'listing_attribute_value')) {
        // Assuming attribute_id 4006 is 'listing_photos' based on SQL dump
        $stmt = $pdo->prepare("SELECT value_text FROM listing_attribute_value WHERE listing_id = :id AND attribute_id = 4006");
        $stmt->bindValue(':id', $listingId, PDO::PARAM_INT);
        $stmt->execute();
        if ($jsonRow = $stmt->fetch(PDO::FETCH_ASSOC)) {
            $json = json_decode($jsonRow['value_text'] ?? '{}', true);
            $attrImages = [];
            if (is_array($json)) {
                // Cover image
                if (!empty($json['cover'])) {
                    $img = stripslashes($json['cover']);
                    if (!preg_match('/^http/i', $img) && !preg_match('/^data:/i', $img)) {
                        $attrImages[] = "data:image/jpeg;base64," . $img;
                    } else {
                        $attrImages[] = $img;
                    }
                }
                // More images
                if (!empty($json['more']) && is_array($json['more'])) {
                    foreach ($json['more'] as $img) {
                        $img = stripslashes($img);
                        if (!preg_match('/^http/i', $img) && !preg_match('/^data:/i', $img)) {
                            $attrImages[] = "data:image/jpeg;base64," . $img;
                        } else {
                            $attrImages[] = $img;
                        }
                    }
                }
            }
            if (!empty($attrImages)) {
                $images = $attrImages;
                $foundImages = true;
            }
        }
    }

    // 3. Fallback to listing.image_url
    if (!$foundImages && $hasImageCol && !empty($row['image_url'])) {
        $images[] = $row['image_url'];
    }

    // Deduplicate and fix URLs
    if (!empty($images)) {
        $images = array_values(array_unique($images));
        foreach ($images as $k => $v) {
            $images[$k] = ensure_absolute_url($v);
        }
    }

    // --- Seller Info ---
    $seller = null;
    $uid = (int)$row['user_id'];
    if ($uid > 0 && table_exists($pdo, 'user')) {
        $hasFullName = col_exists($pdo, 'user', 'full_name');
        $hasName = col_exists($pdo, 'user', 'name');
        $hasFirst = col_exists($pdo, 'user', 'first_name');
        
        $pk = col_exists($pdo, 'user', 'user_id') ? 'user_id' : 'id';
        $sel = "SELECT * FROM user WHERE $pk = :uid LIMIT 1";
        
        $us = $pdo->prepare($sel);
        $us->bindValue(':uid', $uid, PDO::PARAM_INT);
        $us->execute();
        
        if ($urow = $us->fetch(PDO::FETCH_ASSOC)) {
            $name = 'Seller';
            if ($hasFullName && !empty($urow['full_name'])) {
                $name = $urow['full_name'];
            } elseif ($hasName && !empty($urow['name'])) {
                $name = $urow['name'];
            } elseif ($hasFirst) {
                $fn = trim((string)($urow['first_name'] ?? ''));
                $ln = trim((string)($urow['last_name'] ?? ''));
                if ($fn || $ln) $name = trim("$fn $ln");
            }

            $phone = $urow['phone'] ?? ($urow['mobile'] ?? '');
            
            $seller = [
                "id"             => $uid,
                "name"           => $name,
                "phone"          => $phone,
                "avatar_url"     => ensure_absolute_url($urow['avatar_url'] ?? ""),
                "member_since"   => isset($urow['created_at']) ? date('Y', strtotime($urow['created_at'])) : "",
                "listings_count" => 0 
            ];
            
            // Count listings
            try {
                $cs = $pdo->prepare("SELECT COUNT(*) FROM listing WHERE user_id = :uid AND status='active'");
                $cs->bindValue(':uid', $uid, PDO::PARAM_INT);
                $cs->execute();
                $seller['listings_count'] = (int)$cs->fetchColumn();
            } catch(Exception $e){}
        }
    }

    // --- Dynamic Attributes ---
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
            elseif ($rec['value_decimal'] !== null) $val = (string)(float)$rec['value_decimal'];
            elseif ($rec['value_bool'] !== null) $val = $rec['value_bool'] ? 'Yes' : 'No';

            // Filter out empty or internals like description/photos/price if they are redundant
            if ($val !== '' && !in_array($rec['code'], ['description', 'listing_photos', 'price'])) {
                $attributes[] = [
                    "label" => $rec['label'],
                    "value" => $val,
                    "unit"  => $rec['unit'] ?? ""
                ];
            }
        }
    }

    // Final Data Construction
    $city = !empty($row['village_name']) ? $row['village_name'] : 
           (!empty($row['district']) ? $row['district'] : ($row['state'] ?? ''));
           
    $isNewInt = ($hasIsNew && $row['is_new'] == 1) ? 1 : 0;

    $data = [
        "id"             => (int)$row['listing_id'],
        "category_id"    => (int)($row['category_id'] ?? 0),
        "subcategory_id" => (int)($row['subcategory_id'] ?? 0),
        "title"          => (string)($row['title'] ?? ''),
        "price"          => money_fmt($row['price'] ?? ''),
        "currency"       => $hasCurrency ? (string)($row['currency'] ?? 'INR') : 'INR',
        "state"          => (string)($row['state'] ?? ''),
        "district"       => (string)($row['district'] ?? ''),
        "village_name"   => (string)($row['village_name'] ?? ''),
        "city"           => $city,
        "description"    => (string)($row['description'] ?? ''),
        "posted_when"    => human_since($row['created_at'] ?? ''),
        "is_new"         => $isNewInt,
        "status"         => $hasStatus ? (string)($row['status'] ?? 'active') : 'active',
        "images"         => $images,
        "seller"         => $seller,
        "attributes"     => $attributes
    ];

    echo json_encode(["status" => "success", "data" => $data], JSON_UNESCAPED_UNICODE);

} catch (Throwable $e) {
    http_response_code(500);
    echo json_encode(["status" => "error", "message" => "db error", "error" => $e->getMessage()]);
}
?>
