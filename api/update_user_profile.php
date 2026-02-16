<?php
/**
 * Update User Profile API
 * 
 * Method: POST
 * Auth: JWT Bearer token
 * Body: JSON with fields to update
 */

require_once __DIR__ . '/config.php';
require_once __DIR__ . '/auth_middleware.php';

header('Content-Type: application/json; charset=utf-8');

// Prevent caching
header('Cache-Control: no-cache, no-store, must-revalidate');
header('Pragma: no-cache');
header('Expires: 0');

// Only allow POST
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    json_out(['error' => 'Method not allowed'], 405);
}

// ===================== JWT AUTHENTICATION =====================
$userId = authenticate();
// ===================== END JWT AUTHENTICATION =====================

try {
    // Get JSON body
    $input = file_get_contents('php://input');
    $data = json_decode($input, true);
    if (!$data) {
        json_out(['error' => 'Invalid JSON body'], 400);
    }
    
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
                json_out(['error' => 'Full name is required'], 400);
            }
            
            if ($field === 'pincode' && !empty($value)) {
                if (!preg_match('/^\d{6}$/', $value)) {
                    json_out(['error' => 'Pincode must be 6 digits'], 400);
                }
            }
            
            if ($field === 'place_type' && !empty($value)) {
                if (!in_array($value, ['village', 'city'])) {
                    json_out(['error' => 'Place type must be village or city'], 400);
                }
            }
            
            $updates[] = "$field = :$field";
            $params[$field] = $value;
        }
    }
    
    if (empty($updates)) {
        json_out(['error' => 'No valid fields to update'], 400);
    }
    
    // Update the user
    $pdo = pdo();
    $sql = "UPDATE user SET " . implode(', ', $updates) . ", updated_at = NOW() WHERE user_id = :user_id AND status = 1";
    
    $stmt = $pdo->prepare($sql);
    $result = $stmt->execute($params);
    
    if (!$result) {
        json_out(['error' => 'Failed to update profile'], 500);
    }
    
    if ($stmt->rowCount() === 0) {
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
        json_out(['error' => 'User not found'], 404);
    }
    
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
    
    json_out($response, 200);
    
} catch (Exception $e) {
    error_log("❌ update_user_profile: " . $e->getMessage());
    json_out(['error' => 'Server error occurred'], 500);
}
