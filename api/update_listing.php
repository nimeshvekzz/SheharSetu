<?php
declare(strict_types=1);

require_once __DIR__ . '/config.php';
require_once __DIR__ . '/auth_middleware.php';

// Custom file-based logging (creates log file if not present)
function debug_log($msg) {
    $logDir = __DIR__ . '/logs';
    if (!is_dir($logDir)) {
        @mkdir($logDir, 0775, true);
    }
    $logFile = $logDir . '/update_listing.log';
    $timestamp = date('Y-m-d H:i:s');
    @file_put_contents($logFile, "[{$timestamp}] {$msg}\n", FILE_APPEND | LOCK_EX);
}

header('Content-Type: application/json; charset=utf-8');

// ===================== JWT AUTHENTICATION =====================
$tokenUserId = authenticate();
// ===================== END JWT AUTHENTICATION =====================

try {
    $raw   = file_get_contents('php://input');
    $input = json_decode($raw, true);

    debug_log("=== UPDATE LISTING START === raw_length=" . strlen($raw));

    if (!is_array($input)) {
        throw new RuntimeException('Invalid JSON input');
    }

    // ----------------------------------------------------
    // 1) Required fields
    // ----------------------------------------------------
    $listingId     = isset($input['listing_id']) ? (int)$input['listing_id'] : 0;
    $userId        = isset($input['user_id']) ? (int)$input['user_id'] : 0;
    $categoryId    = isset($input['category_id']) ? (int)$input['category_id'] : 0;
    $subcategoryId = isset($input['subcategory_id']) && $input['subcategory_id'] !== ''
        ? (int)$input['subcategory_id']
        : null;

    $title    = trim($input['title'] ?? '');
    $formData = $input['form_data'] ?? null;

    // SECURITY: Verify user_id matches JWT token
    if ($userId !== (int)$tokenUserId) {
        error_log("❌ update_listing: User ID mismatch. Token: $tokenUserId, Request: $userId");
        json_out(['success' => false, 'message' => 'Unauthorized - User ID mismatch'], 403);
    }

    if ($listingId <= 0) {
        json_out(['success' => false, 'message' => 'listing_id is required'], 400);
    }
    if ($userId <= 0) {
        json_out(['success' => false, 'message' => 'user_id is required'], 400);
    }
    if ($categoryId <= 0) {
        json_out(['success' => false, 'message' => 'category_id is required'], 400);
    }
    if (!is_array($formData)) {
        json_out(['success' => false, 'message' => 'form_data must be an object'], 400);
    }

    $pdo = pdo();

    // --- Verify ownership ---
    $stmt = $pdo->prepare("SELECT user_id FROM listing WHERE listing_id = :lid");
    $stmt->execute(['lid' => $listingId]);
    $row = $stmt->fetch(PDO::FETCH_ASSOC);

    if (!$row) {
        json_out(['success' => false, 'message' => 'Listing not found'], 404);
    }
    if ((int)$row['user_id'] !== $userId) {
        json_out(['success' => false, 'message' => 'You can only edit your own listings'], 403);
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
    // 2.b) is_new toggle
    // ----------------------------------------------------
    $isNew = 1;
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
    // 4) Location fields
    // ----------------------------------------------------
    $state     = isset($formData['state'])        ? trim((string)$formData['state'])        : null;
    $district  = isset($formData['district'])     ? trim((string)$formData['district'])     : null;
    $village   = isset($formData['village_name']) ? trim((string)$formData['village_name']) : null;
    $pincode   = isset($formData['pincode'])      ? trim((string)$formData['pincode'])      : null;
    $latitude  = (isset($formData['latitude'])  && $formData['latitude']  !== '') ? (float)$formData['latitude']  : null;
    $longitude = (isset($formData['longitude']) && $formData['longitude'] !== '') ? (float)$formData['longitude'] : null;

    if ($state === '')    $state = null;
    if ($district === '') $district = null;
    if ($village === '')  $village = null;

    // Backend Geocoding fallback
    if (($latitude === null || $longitude === null) && !empty($village)) {
        $parts = [$village];
        if (!empty($district)) $parts[] = $district;
        $parts[] = !empty($state) ? $state : "Gujarat";
        $parts[] = "India";

        $geoAddr = implode(', ', $parts);
        $geo = geocode($geoAddr);
        if ($geo) {
            $latitude  = $geo['lat'];
            $longitude = $geo['lng'];
        } else {
            $latitude = 0.0;
            $longitude = 0.0;
        }
    } else {
        if ($latitude === null) $latitude = 0.0;
        if ($longitude === null) $longitude = 0.0;
    }

    // ----------------------------------------------------
    // 5) DB transaction start
    // ----------------------------------------------------
    $pdo->beginTransaction();

    // ----------------------------------------------------
    // 6) Update listing row
    // ----------------------------------------------------
    $sqlUpdate = "
        UPDATE listing SET
            category_id    = :category_id,
            subcategory_id = :subcategory_id,
            title          = :title,
            description    = :description,
            price          = :price,
            currency       = :currency,
            state          = :state,
            district       = :district,
            village_name   = :village_name,
            pincode        = :pincode,
            latitude       = :latitude,
            longitude      = :longitude,
            is_new         = :is_new,
            updated_at     = NOW()
        WHERE listing_id = :listing_id
    ";

    $stmtUpdate = $pdo->prepare($sqlUpdate);
    $stmtUpdate->execute([
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
        ':listing_id'     => $listingId,
    ]);

    // ----------------------------------------------------
    // 7) Delete old attributes & re-insert
    // ----------------------------------------------------
    $pdo->prepare("DELETE FROM listing_attribute_value WHERE listing_id = :lid")->execute(['lid' => $listingId]);

    // Re-insert attributes from form_data (skip known non-attribute keys)
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
    // 8) Photos — Delete old media & save new
    // ----------------------------------------------------
    // Log all form_data keys to see what's being sent
    debug_log("📸 UpdateListing #{$listingId}: form_data keys = " . implode(', ', array_keys($formData)));
    
    // Diagnostic tracking for photo processing
    $photoDebug = ['has_listing_photos_key' => isset($formData['listing_photos'])];
    
    if (isset($formData['listing_photos']) && $formData['listing_photos'] !== null) {

        $photosValue = $formData['listing_photos'];
        $photoDebug['photos_type'] = gettype($photosValue);
        debug_log("📸 UpdateListing #{$listingId}: listing_photos type=" . gettype($photosValue));
        
        if (is_string($photosValue)) {
            debug_log("📸 UpdateListing #{$listingId}: listing_photos is string, length=" . strlen($photosValue));
            $photoDebug['photos_string_len'] = strlen($photosValue);
            $decoded = json_decode($photosValue, true);
            if (json_last_error() === JSON_ERROR_NONE && is_array($decoded)) {
                $photosValue = $decoded;
            } else {
                debug_log("❌ UpdateListing #{$listingId}: JSON decode failed: " . json_last_error_msg());
                $photoDebug['json_decode_error'] = json_last_error_msg();
                $photosValue = [];
            }
        }

        if (is_array($photosValue)) {
            // Check if photos contain new base64 data (not just URLs)
            $hasNewPhotos = false;
            $coverRaw = $photosValue['cover'] ?? '';
            $coverLen = strlen((string)$coverRaw);
            $coverIsHttp = preg_match('~^https?://~i', (string)$coverRaw) ? true : false;
            
            $photoDebug['cover_length'] = $coverLen;
            $photoDebug['cover_is_http'] = $coverIsHttp;
            $photoDebug['cover_first_50'] = substr((string)$coverRaw, 0, 50);
            
            debug_log("📸 UpdateListing #{$listingId}: coverRaw type=" . gettype($coverRaw) . " length=" . $coverLen . " starts_with_http=" . ($coverIsHttp ? 'YES' : 'NO'));
            
            if (!empty($coverRaw) && !$coverIsHttp) {
                $hasNewPhotos = true;
            }
            $moreList = $photosValue['more'] ?? [];
            $photoDebug['more_count'] = is_array($moreList) ? count($moreList) : 0;
            
            if (is_array($moreList)) {
                foreach ($moreList as $m) {
                    if (!empty($m) && !preg_match('~^https?://~i', (string)$m)) {
                        $hasNewPhotos = true;
                        break;
                    }
                }
            }

            $photoDebug['has_new_photos'] = $hasNewPhotos;
            debug_log("📸 UpdateListing #{$listingId}: hasNewPhotos=" . ($hasNewPhotos ? 'TRUE' : 'FALSE') . ", moreList count=" . count($moreList));
            
            // Only replace media if new photos were uploaded
            if ($hasNewPhotos) {
                debug_log("📸 UpdateListing #{$listingId}: Deleting old media and saving new photos...");
                // Delete old media records
                $pdo->prepare("DELETE FROM listing_media WHERE listing_id = :lid")->execute(['lid' => $listingId]);

                $baseUploadDir = __DIR__ . '/uploads/listings';
                $baseUploadUrl = 'uploads/listings';

                if (!is_dir($baseUploadDir)) {
                    mkdir($baseUploadDir, 0775, true);
                }

                $datePath   = date('Y') . '/' . date('m') . '/' . date('d');
                $targetDir  = $baseUploadDir . '/' . $datePath;
                $urlPrefix  = $baseUploadUrl . '/' . $datePath;

                if (!is_dir($targetDir)) {
                    mkdir($targetDir, 0775, true);
                }

                $sqlMedia = "INSERT INTO listing_media (listing_id, media_type, file_url, is_cover, sort_order) VALUES (:lid, 'image', :url, :cover, :sort)";
                $stmtMedia = $pdo->prepare($sqlMedia);

                $sortOrder = 1;

                // Cover Image
                if (!empty($coverRaw)) {
                    try {
                        $val = trim($coverRaw);
                        $storedUrl = null;

                        if (preg_match('~^https?://~i', $val)) {
                            $storedUrl = $val;
                            debug_log("📸 UpdateListing #{$listingId}: Cover is URL, reusing");
                        } else {
                            $ext = 'jpg';
                            $data = $val;
                            if (preg_match('~^data:image/(\w+);base64,~i', $val, $m)) {
                                $ext = strtolower($m[1]);
                                $data = substr($val, strpos($val, ',') + 1);
                                debug_log("📸 UpdateListing #{$listingId}: Cover has data: prefix, ext={$ext}");
                            }
                            $binary = base64_decode($data, true);
                            if ($binary !== false) {
                                debug_log("📸 UpdateListing #{$listingId}: base64_decode OK, binary=" . strlen($binary) . " bytes");
                                $filename = sprintf('listing_%d_cover_%03d.%s', $listingId, $sortOrder, $ext);
                                $fullPath = $targetDir . '/' . $filename;
                                $writeResult = file_put_contents($fullPath, $binary);
                                if ($writeResult !== false) {
                                    $storedUrl = $urlPrefix . '/' . $filename;
                                    debug_log("📸 UpdateListing #{$listingId}: Saved {$writeResult} bytes → {$fullPath}, exists=" . (file_exists($fullPath) ? 'YES' : 'NO'));
                                } else {
                                    debug_log("❌ UpdateListing #{$listingId}: file_put_contents FAILED for {$fullPath}");
                                }
                            } else {
                                debug_log("❌ UpdateListing #{$listingId}: base64_decode FAILED, dataLen=" . strlen($data));
                            }
                        }

                        if ($storedUrl) {
                            $stmtMedia->execute([
                                ':lid' => $listingId,
                                ':url' => $storedUrl,
                                ':cover' => 1,
                                ':sort' => $sortOrder
                            ]);
                            $photoDebug['cover_saved'] = $storedUrl;
                            debug_log("📸 UpdateListing #{$listingId}: Cover DB inserted → {$storedUrl}");
                            $sortOrder++;
                        } else {
                            $photoDebug['cover_saved'] = false;
                            debug_log("❌ UpdateListing #{$listingId}: Cover FAILED (storedUrl is null)");
                        }
                    } catch (Exception $e) {
                        debug_log("❌ UpdateListing: Cover error: " . $e->getMessage());
                    }
                }

                // Gallery Images
                if (is_array($moreList)) {
                    foreach ($moreList as $m) {
                        if (empty($m)) continue;
                        try {
                            $val = trim((string)$m);
                            $storedUrl = null;

                            if (preg_match('~^https?://~i', $val)) {
                                $storedUrl = $val;
                            } else {
                                $ext = 'jpg';
                                $data = $val;
                                if (preg_match('~^data:image/(\w+);base64,~i', $val, $mMatch)) {
                                    $ext = strtolower($mMatch[1]);
                                    $data = substr($val, strpos($val, ',') + 1);
                                }
                                $binary = base64_decode($data, true);
                                if ($binary !== false) {
                                    $filename = sprintf('listing_%d_img_%03d.%s', $listingId, $sortOrder, $ext);
                                    $fullPath = $targetDir . '/' . $filename;
                                    if (file_put_contents($fullPath, $binary) !== false) {
                                        $storedUrl = $urlPrefix . '/' . $filename;
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
                            debug_log("❌ UpdateListing: Gallery image error: " . $e->getMessage());
                        }
                    }
                }
            } else {
                $photoDebug['skipped'] = 'no_new_photos';
                debug_log("📸 UpdateListing #{$listingId}: hasNewPhotos=false, keeping existing media");
            }
        }
    }

    // ----------------------------------------------------
    // 9) Commit & Response
    // ----------------------------------------------------
    $pdo->commit();

    // Post-commit: verify listing_media state
    $verifyStmt = $pdo->prepare("SELECT media_id, file_url, is_cover, sort_order FROM listing_media WHERE listing_id = :lid ORDER BY sort_order ASC");
    $verifyStmt->execute(['lid' => $listingId]);
    $mediaRows = $verifyStmt->fetchAll(PDO::FETCH_ASSOC);
    debug_log("📸 POST-COMMIT listing_media for #{$listingId}: " . json_encode($mediaRows));

    echo json_encode([
        'success'    => true,
        'message'    => 'Listing updated successfully',
        'listing_id' => $listingId,
        'title'      => $title,
        'is_new'     => (int)$isNew,
        'photo_debug' => $photoDebug ?? null,
        'media_after_update' => $mediaRows,
    ], JSON_UNESCAPED_UNICODE);

} catch (Throwable $e) {
    if (isset($pdo) && $pdo instanceof PDO && $pdo->inTransaction()) {
        $pdo->rollBack();
    }

    http_response_code(500);
    debug_log("❌ update_listing: " . $e->getMessage());

    echo json_encode([
        'success' => false,
        'message' => 'Error updating listing',
        'error'   => $e->getMessage(),
    ], JSON_UNESCAPED_UNICODE);
}
