<?php
require_once __DIR__ . '/config.php';
require_once __DIR__ . '/auth_middleware.php';

header('Content-Type: application/json; charset=utf-8');

// Prevent caching - user data should always be fresh
header('Cache-Control: no-cache, no-store, must-revalidate');
header('Pragma: no-cache');
header('Expires: 0');

// ===================== JWT AUTHENTICATION =====================
$userId = authenticate();
// ===================== END JWT AUTHENTICATION =====================

try {
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
    
    if (!$user) {
        json_out(['error' => 'User not found or inactive'], 404);
    }
    
    // Format phone number for display
    $phone = $user['phone'];
    if (strlen($phone) === 10) {
        $formattedPhone = '+91 ' . substr($phone, 0, 5) . ' ' . substr($phone, 5);
    } else {
        $formattedPhone = $phone;
    }
    
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
    
    json_out($response, 200);
    
} catch (Exception $e) {
    error_log("❌ get_user_profile: " . $e->getMessage());
    json_out(['error' => 'Server error occurred'], 500);
}
