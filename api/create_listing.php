<?php
declare(strict_types=1);

require_once __DIR__ . '/config.php';
require_once __DIR__ . '/auth_middleware.php';

header('Content-Type: application/json; charset=utf-8');

// ===================== JWT AUTHENTICATION =====================
$tokenUserId = authenticate();
// ===================== END JWT AUTHENTICATION =====================


try {
    $raw   = file_get_contents('php://input');
    $input = json_decode($raw, true);

    if (!is_array($input)) {
        throw new RuntimeException('Invalid JSON input');
    }

    // ----------------------------------------------------
    // 1) Basic required fields (from Android)
    // ----------------------------------------------------
    $userId        = isset($input['user_id']) ? (int)$input['user_id'] : 0;
    $categoryId    = isset($input['category_id']) ? (int)$input['category_id'] : 0;
    $subcategoryId = isset($input['subcategory_id']) && $input['subcategory_id'] !== ''
        ? (int)$input['subcategory_id']
        : null;

    $title    = trim($input['title'] ?? '');
    $formData = $input['form_data'] ?? null;

    // SECURITY: Verify user_id in request matches JWT token user_id
    if ($userId !== (int)$tokenUserId) {
        error_log("❌ create_listing: User ID mismatch. Token: $tokenUserId, Request: $userId");
        http_response_code(403);
        echo json_encode(['success' => false, 'message' => 'Unauthorized - User ID mismatch']);
        exit;
    }

    if ($userId <= 0) {
        throw new RuntimeException('user_id is required');
    }
    if ($categoryId <= 0) {
        throw new RuntimeException('category_id is required');
    }
    if (!is_array($formData)) {
        throw new RuntimeException('form_data must be an object');
    }


    // ----------------------------------------------------
    // 2) Build title from form_data if empty
    // ----------------------------------------------------
    if ($title === '') {
        $parts = [];

        foreach (['brand', 'model', 'product'] as $k) {
            if (!empty($formData[$k])) {
                $parts[] = (string)$formData[$k];
            }
        }
        if (!empty($formData['year'])) {
            $parts[] = (string)$formData['year'];
        }

        $title = $parts ? implode(' ', $parts) : 'Listing';
    }

    // ----------------------------------------------------
    // 2.b) New / Old toggle (is_new column)
    //      Prefer top-level input['is_new'], else fallback to form_data['is_new']
    // ----------------------------------------------------
    $isNew = 1; // default: treat as "New"

    $rawIsNew = null;

    if (array_key_exists('is_new', $input)) {
        $rawIsNew = $input['is_new'];
    } elseif (array_key_exists('is_new', $formData)) {
        $rawIsNew = $formData['is_new'];
    }

    if ($rawIsNew !== null) {
        if (is_bool($rawIsNew)) {
            $isNew = $rawIsNew ? 1 : 0;
        } elseif (is_numeric($rawIsNew)) {
            $isNew = ((int)$rawIsNew) ? 1 : 0;
        } elseif (is_string($rawIsNew)) {
            $v = strtolower(trim($rawIsNew));
            $isNew = in_array($v, ['1', 'true', 'yes', 'on', 'new'], true) ? 1 : 0;
        } else {
            $isNew = (int)(bool)$rawIsNew;
        }
    }

    // ----------------------------------------------------
    // 3) Description & Price
    // ----------------------------------------------------
    $description = isset($formData['description']) ? (string)$formData['description'] : '';

    $price = null;
    foreach (['price', 'price_per_unit', 'price_per_kg'] as $k) {
        if (isset($formData[$k]) && $formData[$k] !== '') {
            $price = (float)$formData[$k];
            break;
        }
    }
    $currency = 'INR';

    // ----------------------------------------------------
    // 4) Optional location fields (future-proof)
    // ----------------------------------------------------
    $state     = isset($formData['state'])        ? trim((string)$formData['state'])        : null;
    $district  = isset($formData['district'])     ? trim((string)$formData['district'])     : null;
    $village   = isset($formData['village_name']) ? trim((string)$formData['village_name']) : null;
    $pincode   = isset($formData['pincode'])      ? trim((string)$formData['pincode'])      : null;
    $latitude  = (isset($formData['latitude'])  && $formData['latitude']  !== '') ? (float)$formData['latitude']  : null;
    $longitude = (isset($formData['longitude']) && $formData['longitude'] !== '') ? (float)$formData['longitude'] : null;

    // Treat empty strings as null
    if ($state === '')    $state = null;
    if ($district === '') $district = null;
    if ($village === '')  $village = null;

    error_log("📍 create_listing: Location data received — village='$village', district='$district', state='$state', lat=$latitude, lng=$longitude");

    // Backend Geocoding: If lat/lng missing but village_name present
    if (($latitude === null || $longitude === null) && !empty($village)) {
        // Construct address: Village, District, State, India
        $parts = [];
        $parts[] = $village;
        if (!empty($district)) $parts[] = $district;
        // Always include Gujarat if state is missing (app is Gujarat-focused)
        if (!empty($state)) {
            $parts[] = $state;
        } else {
            $parts[] = "Gujarat";
        }
        $parts[] = "India";

        $geoAddr = implode(', ', $parts);
        error_log("📍 create_listing: Geocoding address string: '$geoAddr'");

        $geo = geocode($geoAddr);
        if ($geo) {
            $latitude  = $geo['lat'];
            $longitude = $geo['lng'];
            error_log("✅ create_listing: Geocoded '$geoAddr' → {$latitude}, {$longitude}");
        } else {
            error_log("⚠️ create_listing: Failed to geocode '$geoAddr', falling back to 0.0");
            $latitude = 0.0;
            $longitude = 0.0;
        }
    } else {
        // Ensure not null
        if ($latitude === null) $latitude = 0.0;
        if ($longitude === null) $longitude = 0.0;
    }

    // ----------------------------------------------------
    // 5) DB transaction start
    // ----------------------------------------------------
    $pdo = pdo();
    $pdo->beginTransaction();

    // ----------------------------------------------------
    // 6) listing table me basic row insert (with is_new)
    // ----------------------------------------------------
    $sqlListing = "
        INSERT INTO listing (
            user_id,
            category_id,
            subcategory_id,
            title,
            description,
            price,
            currency,
            state,
            district,
            village_name,
            pincode,
            latitude,
            longitude,
            is_new
        ) VALUES (
            :user_id,
            :category_id,
            :subcategory_id,
            :title,
            :description,
            :price,
            :currency,
            :state,
            :district,
            :village_name,
            :pincode,
            :latitude,
            :longitude,
            :is_new
        )
    ";

    $stmtListing = $pdo->prepare($sqlListing);
    $stmtListing->execute([
        ':user_id'        => $userId,
        ':category_id'    => $categoryId,
        ':subcategory_id' => $subcategoryId,
        ':title'          => $title,
        ':description'    => $description,
        ':price'          => $price,
        ':currency'       => $currency,
        ':state'          => $state,
        ':district'       => $district,
        ':village_name'   => $village,
        ':pincode'        => $pincode,
        ':latitude'       => $latitude,
        ':longitude'      => $longitude,
        ':is_new'         => $isNew,
    ]);

    $listingId = (int)$pdo->lastInsertId();
    if ($listingId <= 0) {
        throw new RuntimeException('Failed to create listing in DB');
    }

    // ----------------------------------------------------
    // 7) Save dynamic attributes from form_data
    // ----------------------------------------------------
    $skipKeys = ['listing_photos', 'description', 'price', 'price_per_unit', 'price_per_kg',
                 'address', 'village_name', 'state', 'district', 'pincode',
                 'latitude', 'longitude', 'is_new'];

    $stmtAttrLookup = $pdo->prepare("SELECT attribute_id FROM attribute WHERE code = :code LIMIT 1");
    $stmtAttrInsert = $pdo->prepare("
        INSERT INTO listing_attribute_value (listing_id, attribute_id, value_text)
        VALUES (:lid, :aid, :val)
    ");

    foreach ($formData as $key => $value) {
        if (in_array($key, $skipKeys, true)) continue;
        if ($value === null || $value === '') continue;

        $stmtAttrLookup->execute(['code' => $key]);
        $attrRow = $stmtAttrLookup->fetch(PDO::FETCH_ASSOC);
        if (!$attrRow) continue;

        $attrId = (int)$attrRow['attribute_id'];
        $valText = is_array($value) || is_object($value) ? json_encode($value) : (string)$value;

        $stmtAttrInsert->execute([
            'lid' => $listingId,
            'aid' => $attrId,
            'val' => $valText,
        ]);
    }

    // ----------------------------------------------------
    // 9) Photos – listing_photos ko listing_media me save karna
    //     + Folder create + professional filenames
    // ----------------------------------------------------
    if (isset($formData['listing_photos']) && $formData['listing_photos'] !== null) {

        $photosValue = $formData['listing_photos'];

        if (is_string($photosValue)) {
            $decoded = json_decode($photosValue, true);
            if (json_last_error() === JSON_ERROR_NONE && is_array($decoded)) {
                $photosValue = $decoded;
            } else {
                $photosValue = [];
            }
        }

        if (is_array($photosValue)) {

            // ---------- 9.2 Image Saving Logic (Explicit Loops with Error Handling) ----------

            // Define helper function to keep code clean but we will call it explicitly
            // (Note: In PHP 7+ nested functions are not standard, but closures are.
            //  To satisfy "system back", I will inline the logic or use a standard helper if desired.
            //  The user wants the "system back". Previous system had huge code duplication.
            //  I will use a closure variables but structured as explicit blocks if that helps,
            //  OR honestly, the closure IS the best way. I will modify it to look more "standard"
            //  or just ensure it works PERFECTLY)

            // Actually, I will Unroll it to be super explicit as requested.

            $baseUploadDir = __DIR__ . '/uploads/listings';
            $baseUploadUrl = 'uploads/listings';

            if (!is_dir($baseUploadDir)) {
                 if (!mkdir($baseUploadDir, 0775, true)) {
                     error_log("❌ CreateListing: Failed to create base dir: $baseUploadDir");
                 }
            }

            $datePath   = date('Y') . '/' . date('m') . '/' . date('d');
            $targetDir  = $baseUploadDir . '/' . $datePath;
            $urlPrefix  = $baseUploadUrl . '/' . $datePath;

            if (!is_dir($targetDir)) {
                 if (!mkdir($targetDir, 0775, true)) {
                     error_log("❌ CreateListing: Failed to create target dir: $targetDir");
                 }
            }

            $sqlMedia = "INSERT INTO listing_media (listing_id, media_type, file_url, is_cover, sort_order) VALUES (:lid, 'image', :url, :cover, :sort)";
            $stmtMedia = $pdo->prepare($sqlMedia);

            $sortOrder = 1;

            // --- 9.3 Cover Image ---
            $coverRaw = $photosValue['cover'] ?? '';
            if (!empty($coverRaw)) {
                try {
                    $val = trim($coverRaw);
                    $storedUrl = null;

                    if (preg_match('~^https?://~i', $val)) {
                        $storedUrl = $val;
                    } else {
                         // Base64 decoding
                         $ext = 'jpg';
                         $data = $val;
                         if (preg_match('~^data:image/(\w+);base64,~i', $val, $m)) {
                             $ext = strtolower($m[1]);
                             $data = substr($val, strpos($val, ',') + 1);
                         }
                         $binary = base64_decode($data, true);
                         
                         if ($binary !== false) {
                             $filename = sprintf('listing_%d_cover_%03d.%s', $listingId, $sortOrder, $ext);
                             $fullPath = $targetDir . '/' . $filename;
                             if (file_put_contents($fullPath, $binary) !== false) {
                                 $storedUrl = $urlPrefix . '/' . $filename;
                             } else {
                                 error_log("❌ CreateListing: Failed to write cover image");
                             }
                         }
                    }

                    if ($storedUrl) {
                        $stmtMedia->execute([
                            ':lid' => $listingId,
                            ':url' => $storedUrl,
                            ':cover' => 1,
                            ':sort' => $sortOrder
                        ]);
                        $sortOrder++;
                    }
                } catch (Exception $e) {
                    error_log("❌ CreateListing: Cover image error: " . $e->getMessage());
                }
            }

            // --- 9.4 Gallery Images (Explicit Loop) ---
            $moreList = $photosValue['more'] ?? [];
            if (is_array($moreList)) {
                foreach ($moreList as $m) {
                    if (empty($m)) continue;
                    try {
                        $val = trim((string)$m);
                        $storedUrl = null;

                        if (preg_match('~^https?://~i', $val)) {
                            $storedUrl = $val;
                        } else {
                             // Base64 decoding
                             $ext = 'jpg';
                             $data = $val;
                             if (preg_match('~^data:image/(\w+);base64,~i', $val, $m)) {
                                 $ext = strtolower($m[1]);
                                 $data = substr($val, strpos($val, ',') + 1);
                             }
                             $binary = base64_decode($data, true);
                             
                             if ($binary !== false) {
                                 $filename = sprintf('listing_%d_img_%03d.%s', $listingId, $sortOrder, $ext);
                                 $fullPath = $targetDir . '/' . $filename;
                                 if (file_put_contents($fullPath, $binary) !== false) {
                                     $storedUrl = $urlPrefix . '/' . $filename;
                                 } else {
                                     error_log("❌ CreateListing: Failed to write gallery image");
                                 }
                             }
                        }

                        if ($storedUrl) {
                            $stmtMedia->execute([
                                ':lid' => $listingId,
                                ':url' => $storedUrl,
                                ':cover' => 0,
                                ':sort' => $sortOrder
                            ]);
                            $sortOrder++;
                        }
                    } catch (Exception $e) {
                        error_log("❌ CreateListing: Gallery image error: " . $e->getMessage());
                    }
                }
            }
        }
    }

    // ----------------------------------------------------
    // 10) Commit & Response
    // ----------------------------------------------------
    $pdo->commit();

    echo json_encode([
        'success'    => true,
        'message'    => 'Listing created successfully',
        'listing_id' => $listingId,
        'title'      => $title,
        'is_new'     => (int)$isNew,
    ], JSON_UNESCAPED_UNICODE);

} catch (Throwable $e) {
    if (isset($pdo) && $pdo instanceof PDO && $pdo->inTransaction()) {
        $pdo->rollBack();
    }

    http_response_code(500);

    echo json_encode([
        'success' => false,
        'message' => 'Error creating listing',
        'error'   => $e->getMessage(),   // Android logcat me dekhne ke liye
    ], JSON_UNESCAPED_UNICODE);
}
