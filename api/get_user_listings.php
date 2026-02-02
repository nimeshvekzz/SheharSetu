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
ini_set('display_errors', 0);
ini_set('display_startup_errors', 0);
error_reporting(E_ALL);

require_once __DIR__ . '/config.php';
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-cache, no-store, must-revalidate');
header('Pragma: no-cache');
header('Expires: 0');

// ==================== LOGGING SETUP ====================
$LOG_FILE = __DIR__ . '/logs/get_user_listings.log';

if (!is_dir(__DIR__ . '/logs')) {
    mkdir(__DIR__ . '/logs', 0755, true);
}

// Base URL for images - use /api/ path since image paths are relative to API folder
$BASE_URL = 'https://magenta-owl-444153.hostingersite.com/api/';

function writeLog($message, $level = 'INFO') {
    global $LOG_FILE;
    $timestamp = date('Y-m-d H:i:s');
    $logEntry = "[$timestamp] [$level] $message" . PHP_EOL;
    file_put_contents($LOG_FILE, $logEntry, FILE_APPEND | LOCK_EX);
}

// ==================== API START ====================
writeLog("========== GET USER LISTINGS START ==========");
writeLog("Request Method: " . $_SERVER['REQUEST_METHOD']);

// Get the Authorization header
$headers = getallheaders();
$authHeader = $headers['Authorization'] ?? '';

if (empty($authHeader) || !preg_match('/Bearer\s+(.*)$/i', $authHeader, $matches)) {
    writeLog("No valid Bearer token", "ERROR");
    json_out(['error' => 'Unauthorized - No token provided'], 401);
}

$token = $matches[1];
writeLog("Token received");

try {
    // Decode JWT token
    $parts = explode('.', $token);
    if (count($parts) !== 3) {
        json_out(['error' => 'Invalid token format'], 401);
    }
    
    $payload = json_decode(base64_decode(strtr($parts[1], '-_', '+/')), true);
    if (!$payload) {
        json_out(['error' => 'Invalid token payload'], 401);
    }
    
    // Get user_id from token
    $userId = $payload['user_id'] ?? $payload['uid'] ?? $payload['sub'] ?? null;
    writeLog("User ID from token: " . ($userId ?? "NULL"));
    
    if (!$userId) {
        $pdo = pdo();
        $stmt = $pdo->prepare("SELECT user_id FROM auth_tokens WHERE access_token = :token AND expires_at > NOW()");
        $stmt->execute(['token' => $token]);
        $tokenRow = $stmt->fetch(PDO::FETCH_ASSOC);
        
        if ($tokenRow) {
            $userId = $tokenRow['user_id'];
        } else {
            json_out(['error' => 'Invalid or expired token'], 401);
        }
    }
    
    writeLog("Fetching listings for user_id: $userId");
    
    $pdo = pdo();
    
    // Build main query - only use columns that exist for sure in listing table
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
    
    writeLog("Executing main query");
    
    $stmt = $pdo->prepare($query);
    $stmt->execute(['user_id' => $userId]);
    $listings = $stmt->fetchAll(PDO::FETCH_ASSOC);
    
    writeLog("Found " . count($listings) . " listings");
    
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
            writeLog("listing_media columns: " . implode(', ', $mediaCols));
            
            // Find the URL column
            $possibleUrlCols = ['media_url', 'url', 'file_url', 'image_url', 'path', 'file_path'];
            foreach ($possibleUrlCols as $col) {
                if (in_array($col, $mediaCols)) {
                    $mediaUrlCol = $col;
                    break;
                }
            }
            writeLog("Media URL column: " . ($mediaUrlCol ?? "NOT FOUND"));
        }
    } catch (Exception $e) {
        writeLog("Error checking listing_media: " . $e->getMessage(), "WARN");
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
                    $rawUrl = $mediaRow[$mediaUrlCol];
                    // If it's a relative path, prepend base URL
                    if (!preg_match('/^https?:\/\//i', $rawUrl) && !str_starts_with($rawUrl, 'data:')) {
                        $imageUrl = rtrim($BASE_URL, '/') . '/' . ltrim($rawUrl, '/');
                    } else {
                        $imageUrl = $rawUrl;
                    }
                    writeLog("Got image from listing_media for listing $listingId: $imageUrl");
                }
            } catch (Exception $e) {
                writeLog("Error fetching media: " . $e->getMessage(), "WARN");
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
                        // It's base64, prepend data URI
                        $imageUrl = 'data:image/jpeg;base64,' . $photoData['cover'];
                        writeLog("Got base64 image from attribute for listing $listingId");
                    }
                }
            } catch (Exception $e) {
                writeLog("Error fetching attribute photo: " . $e->getMessage(), "WARN");
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
    
    writeLog("SUCCESS - Returning " . count($formattedListings) . " listings", "SUCCESS");
    json_out($response, 200);
    
} catch (PDOException $e) {
    writeLog("DATABASE ERROR: " . $e->getMessage(), "ERROR");
    json_out(['error' => 'Database error: ' . $e->getMessage()], 500);
} catch (Exception $e) {
    writeLog("EXCEPTION: " . $e->getMessage(), "ERROR");
    json_out(['error' => 'Server error: ' . $e->getMessage()], 500);
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
