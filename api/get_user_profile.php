<?php
// Enable error reporting for debugging
ini_set('display_errors', 1);
ini_set('display_startup_errors', 1);
error_reporting(E_ALL);

require_once __DIR__ . '/config.php';
header('Content-Type: application/json; charset=utf-8');

error_log("========== GET USER PROFILE START ==========");

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
    // Decode JWT token
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
        json_out(['error' => 'Invalid token payload - cannot decode'], 401);
    }
    
    error_log("Payload contents: " . json_encode($payload));
    
    // Try to get user_id from token payload (could be 'user_id', 'uid', 'sub', etc.)
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
    
    error_log("Fetching user data for user_id: " . $userId);
    
    // Fetch user data from database
    $pdo = pdo();
    $stmt = $pdo->prepare("
        SELECT 
            user_id,
            full_name,
            surname,
            phone,
            address,
            village_name,
            district,
            place_type,
            state,
            pincode,
            avatar_url,
            created_at
        FROM user 
        WHERE user_id = :user_id 
        AND status = 1
    ");
    
    $stmt->execute(['user_id' => $userId]);
    $user = $stmt->fetch(PDO::FETCH_ASSOC);
    
    error_log("User query result: " . ($user ? "FOUND" : "NOT FOUND"));
    
    if (!$user) {
        error_log("❌ User not found or inactive for user_id: " . $userId);
        json_out(['error' => 'User not found or inactive'], 404);
    }
    
    error_log("User data: " . json_encode($user));
    
    // Format phone number for display
    $phone = $user['phone'];
    if (strlen($phone) === 10) {
        $formattedPhone = '+91 ' . substr($phone, 0, 5) . ' ' . substr($phone, 5);
    } else {
        $formattedPhone = $phone;
    }
    
    error_log("Phone formatted: " . $formattedPhone);
    
    // Return user profile data
    $response = [
        'success' => true,
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
    
    error_log("✅ SUCCESS - Returning user profile");
    error_log("========== GET USER PROFILE END ==========");
    json_out($response, 200);
    
} catch (Exception $e) {
    error_log("❌ EXCEPTION CAUGHT: " . $e->getMessage());
    error_log("Exception trace: " . $e->getTraceAsString());
    error_log("========== GET USER PROFILE ERROR END ==========");
    json_out(['error' => 'Server error occurred', 'debug' => $e->getMessage()], 500);
}
