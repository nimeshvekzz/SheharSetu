<?php
/**
 * Get User Listings API
 * Returns all listings created by the authenticated user
 * 
 * Database Schema Notes:
 * - listing table: listing_id, user_id, category_id, subcategory_id, title, price, status, created_at, etc.
 * - listing_media table: media_id, listing_id, sort_order, media_url
 * - listing_attribute_value: lav_id, listing_id, attribute_id, value_text (for photos attribute_id=4006)
 */

require_once __DIR__ . '/config.php';
require_once __DIR__ . '/auth_middleware.php';

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-cache, no-store, must-revalidate');
header('Pragma: no-cache');
header('Expires: 0');

// Base URL for images
$BASE_URL = 'https://magenta-owl-444153.hostingersite.com/api/';

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

// ===================== JWT AUTHENTICATION =====================
$userId = authenticate();
// ===================== END JWT AUTHENTICATION =====================

try {
    $pdo = pdo();
    
    // Build main query
    $query = "
        SELECT 
            l.listing_id,
            l.title,
            l.price,
            l.status,
            l.created_at,
            c.name AS category_name,
            s.name AS subcategory_name
        FROM listing l
        LEFT JOIN category c ON l.category_id = c.category_id
        LEFT JOIN subcategory s ON l.subcategory_id = s.subcategory_id
        WHERE l.user_id = :user_id
        ORDER BY l.created_at DESC
    ";
    
    $stmt = $pdo->prepare($query);
    $stmt->execute(['user_id' => $userId]);
    $listings = $stmt->fetchAll(PDO::FETCH_ASSOC);
    
    // Check if listing_media table exists and get its columns
    $hasMediaTable = false;
    $mediaUrlCol = null;
    try {
        $checkTable = $pdo->query("SHOW TABLES LIKE 'listing_media'");
        if ($checkTable->rowCount() > 0) {
            $hasMediaTable = true;
            $mediaColsResult = $pdo->query("SHOW COLUMNS FROM listing_media");
            $mediaCols = [];
            while ($row = $mediaColsResult->fetch(PDO::FETCH_ASSOC)) {
                $mediaCols[] = $row['Field'];
            }
            
            // Find the URL column
            $possibleUrlCols = ['media_url', 'url', 'file_url', 'image_url', 'path', 'file_path'];
            foreach ($possibleUrlCols as $col) {
                if (in_array($col, $mediaCols)) {
                    $mediaUrlCol = $col;
                    break;
                }
            }
        }
    } catch (Exception $e) {
        // Ignore media table check errors
    }
    
    // Format listings with images
    $formattedListings = [];
    foreach ($listings as $listing) {
        $imageUrl = '';
        $listingId = $listing['listing_id'];
        
        // Method 1: Try listing_media table
        if ($hasMediaTable && $mediaUrlCol) {
            try {
                $mediaStmt = $pdo->prepare("SELECT $mediaUrlCol FROM listing_media WHERE listing_id = :listing_id ORDER BY sort_order ASC LIMIT 1");
                $mediaStmt->execute(['listing_id' => $listingId]);
                $mediaRow = $mediaStmt->fetch(PDO::FETCH_ASSOC);
                if ($mediaRow && !empty($mediaRow[$mediaUrlCol])) {
                    $imageUrl = ensure_absolute_url($mediaRow[$mediaUrlCol]);
                }
            } catch (Exception $e) {
                // Ignore
            }
        }
        
        // Method 2: Try listing_attribute_value with attribute_id 4006 (photos)
        if (empty($imageUrl)) {
            try {
                $attrStmt = $pdo->prepare("SELECT value_text FROM listing_attribute_value WHERE listing_id = :listing_id AND attribute_id = 4006 LIMIT 1");
                $attrStmt->execute(['listing_id' => $listingId]);
                $attrRow = $attrStmt->fetch(PDO::FETCH_ASSOC);
                if ($attrRow && !empty($attrRow['value_text'])) {
                    $photoData = json_decode($attrRow['value_text'], true);
                    if ($photoData && isset($photoData['cover'])) {
                        $imageUrl = ensure_absolute_url($photoData['cover']);
                    }
                }
            } catch (Exception $e) {
                // Ignore
            }
        }
        
        // Determine if sold based on status
        $isSold = ($listing['status'] === 'sold');
        
        $formattedListings[] = [
            'listing_id' => (int)$listingId,
            'title' => $listing['title'] ?? 'Untitled',
            'price' => $listing['price'] ?? '0',
            'city' => '', // Not in schema
            'category' => $listing['category_name'] ?? '',
            'subcategory' => $listing['subcategory_name'] ?? '',
            'image_url' => $imageUrl,
            'is_sold' => $isSold,
            'status' => $listing['status'] ?? 'active',
            'created_at' => $listing['created_at'] ?? '',
            'posted_when' => formatTimeAgo($listing['created_at'] ?? '')
        ];
    }
    
    $response = [
        'success' => true,
        'count' => count($formattedListings),
        'listings' => $formattedListings
    ];
    
    json_out($response, 200);
    
} catch (PDOException $e) {
    error_log("❌ get_user_listings DB: " . $e->getMessage());
    json_out(['error' => 'Database error'], 500);
} catch (Exception $e) {
    error_log("❌ get_user_listings: " . $e->getMessage());
    json_out(['error' => 'Server error'], 500);
}

function formatTimeAgo($datetime) {
    if (empty($datetime)) return '';
    
    $timestamp = strtotime($datetime);
    if ($timestamp === false) return '';
    
    $diff = time() - $timestamp;
    
    if ($diff < 60) {
        return 'Just now';
    } elseif ($diff < 3600) {
        $mins = floor($diff / 60);
        return $mins . 'm ago';
    } elseif ($diff < 86400) {
        $hours = floor($diff / 3600);
        return $hours . 'h ago';
    } elseif ($diff < 604800) {
        $days = floor($diff / 86400);
        return $days . 'd ago';
    } elseif ($diff < 2592000) {
        $weeks = floor($diff / 604800);
        return $weeks . 'w ago';
    } else {
        return date('d M Y', $timestamp);
    }
}
