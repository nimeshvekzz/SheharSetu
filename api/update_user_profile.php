<?php
/**
 * Update User Profile API
 * 
 * Method: POST
 * Auth: JWT Bearer token
 * Body: JSON with fields to update
 */

// Enable error reporting for debugging
ini_set('display_errors', 1);
ini_set('display_startup_errors', 1);
error_reporting(E_ALL);

require_once __DIR__ . '/config.php';
header('Content-Type: application/json; charset=utf-8');

error_log("========== UPDATE USER PROFILE START ==========");

// Only allow POST
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    error_log("❌ Invalid method: " . $_SERVER['REQUEST_METHOD']);
    json_out(['error' => 'Method not allowed'], 405);
}

// Get the Authorization header
$headers = getallheaders();
$authHeader = $headers['Authorization'] ?? '';

error_log("Authorization header: " . ($authHeader ? "EXISTS" : "MISSING"));

if (empty($authHeader) || !preg_match('/Bearer\s+(.*)$/i', $authHeader, $matches)) {
    error_log("❌ No valid Bearer token in Authorization header");
    json_out(['error' => 'Unauthorized - No token provided'], 401);
}

$token = $matches[1];
error_log("Token extracted: " . substr($token, 0, 20) . "...");

try {
    // Decode JWT token to get user_id
    $parts = explode('.', $token);
    error_log("Token parts count: " . count($parts));
    
    if (count($parts) !== 3) {
        error_log("❌ Invalid token format - expected 3 parts, got " . count($parts));
        json_out(['error' => 'Invalid token format'], 401);
    }
    
    // Decode payload
    $payload = json_decode(base64_decode(strtr($parts[1], '-_', '+/')), true);
    error_log("Payload decoded: " . ($payload ? "SUCCESS" : "FAILED"));
    
    if (!$payload) {
        error_log("❌ Cannot decode token payload");
        json_out(['error' => 'Invalid token payload'], 401);
    }
    
    error_log("Payload contents: " . json_encode($payload));
    
    // Try to get user_id from token payload
    $userId = $payload['user_id'] ?? $payload['uid'] ?? $payload['sub'] ?? null;
    error_log("User ID from payload: " . ($userId ? $userId : "NULL"));
    
    // If user_id not in token, try to look it up in auth_tokens table
    if (!$userId) {
        error_log("User ID not in payload, checking auth_tokens table...");
        $pdo = pdo();
        $stmt = $pdo->prepare("SELECT user_id FROM auth_tokens WHERE access_token = :token AND expires_at > NOW()");
        $stmt->execute(['token' => $token]);
        $tokenRow = $stmt->fetch(PDO::FETCH_ASSOC);
        
        if ($tokenRow) {
            $userId = $tokenRow['user_id'];
            error_log("✅ User ID found in auth_tokens: " . $userId);
        } else {
            error_log("❌ Token not found in auth_tokens or expired");
            json_out(['error' => 'Invalid or expired token'], 401);
        }
    }
    
    if (!$userId) {
        error_log("❌ User ID is still NULL after all checks");
        json_out(['error' => 'User ID not found in token'], 401);
    }
    
    error_log("Authenticated user_id: " . $userId);
    
    // Get JSON body
    $input = file_get_contents('php://input');
    error_log("Raw input: " . $input);
    
    $data = json_decode($input, true);
    if (!$data) {
        error_log("❌ Invalid JSON body");
        json_out(['error' => 'Invalid JSON body'], 400);
    }
    
    error_log("Parsed data: " . json_encode($data));
    
    // Allowed fields to update (phone is NOT allowed - it's the login ID)
    $allowedFields = ['full_name', 'surname', 'address', 'village_name', 'district', 'place_type', 'state', 'pincode'];
    
    // Build update query dynamically
    $updates = [];
    $params = ['user_id' => $userId];
    
    foreach ($allowedFields as $field) {
        if (isset($data[$field])) {
            $value = trim($data[$field]);
            
            // Validation
            if ($field === 'full_name' && empty($value)) {
                error_log("❌ Validation failed: full_name is required");
                json_out(['error' => 'Full name is required'], 400);
            }
            
            if ($field === 'pincode' && !empty($value)) {
                if (!preg_match('/^\d{6}$/', $value)) {
                    error_log("❌ Validation failed: pincode must be 6 digits");
                    json_out(['error' => 'Pincode must be 6 digits'], 400);
                }
            }
            
            if ($field === 'place_type' && !empty($value)) {
                if (!in_array($value, ['village', 'city'])) {
                    error_log("❌ Validation failed: place_type must be 'village' or 'city'");
                    json_out(['error' => 'Place type must be village or city'], 400);
                }
            }
            
            $updates[] = "$field = :$field";
            $params[$field] = $value;
            error_log("Field to update: $field = " . $value);
        }
    }
    
    if (empty($updates)) {
        error_log("❌ No fields to update");
        json_out(['error' => 'No valid fields to update'], 400);
    }
    
    // Update the user
    $pdo = pdo();
    $sql = "UPDATE user SET " . implode(', ', $updates) . ", updated_at = NOW() WHERE user_id = :user_id AND status = 1";
    error_log("Update SQL: " . $sql);
    error_log("Update params: " . json_encode($params));
    
    $stmt = $pdo->prepare($sql);
    $result = $stmt->execute($params);
    
    error_log("Update result: " . ($result ? "SUCCESS" : "FAILED"));
    error_log("Rows affected: " . $stmt->rowCount());
    
    if (!$result) {
        error_log("❌ Database update failed");
        json_out(['error' => 'Failed to update profile'], 500);
    }
    
    if ($stmt->rowCount() === 0) {
        error_log("⚠️ No rows updated - user may not exist or is inactive");
        json_out(['error' => 'User not found or no changes made'], 404);
    }
    
    // Fetch updated user data
    $stmt = $pdo->prepare("
        SELECT 
            user_id, full_name, surname, phone, address, 
            village_name, district, place_type, state, pincode, 
            avatar_url, created_at
        FROM user 
        WHERE user_id = :user_id AND status = 1
    ");
    $stmt->execute(['user_id' => $userId]);
    $user = $stmt->fetch(PDO::FETCH_ASSOC);
    
    if (!$user) {
        error_log("❌ User not found after update");
        json_out(['error' => 'User not found'], 404);
    }
    
    error_log("Updated user data: " . json_encode($user));
    
    // Format phone for response
    $phone = $user['phone'];
    $formattedPhone = (strlen($phone) === 10) 
        ? '+91 ' . substr($phone, 0, 5) . ' ' . substr($phone, 5)
        : $phone;
    
    $response = [
        'success' => true,
        'message' => 'Profile updated successfully',
        'user' => [
            'user_id' => $user['user_id'],
            'name' => $user['full_name'] ?? 'User',
            'full_name' => $user['full_name'] ?? '',
            'surname' => $user['surname'] ?? '',
            'phone' => $phone,
            'formatted_phone' => $formattedPhone,
            'address' => $user['address'] ?? '',
            'village_name' => $user['village_name'] ?? '',
            'district' => $user['district'] ?? '',
            'place_type' => $user['place_type'] ?? 'village',
            'state' => $user['state'] ?? '',
            'pincode' => $user['pincode'] ?? '',
            'avatar_url' => $user['avatar_url'] ?? '',
            'member_since' => date('M Y', strtotime($user['created_at']))
        ]
    ];
    
    error_log("✅ SUCCESS - Profile updated");
    error_log("========== UPDATE USER PROFILE END ==========");
    json_out($response, 200);
    
} catch (Exception $e) {
    error_log("❌ EXCEPTION CAUGHT: " . $e->getMessage());
    error_log("Exception trace: " . $e->getTraceAsString());
    error_log("========== UPDATE USER PROFILE ERROR END ==========");
    json_out(['error' => 'Server error occurred', 'debug' => $e->getMessage()], 500);
}
