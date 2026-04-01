<?php
// send_otp.php
declare(strict_types=1);
require __DIR__ . '/config.php';
header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');

if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
  header('Access-Control-Allow-Headers: Content-Type, Authorization');
  header('Access-Control-Allow-Methods: POST, OPTIONS');
  http_response_code(204); exit;
}

$input = json_decode(file_get_contents('php://input'), true);
$phone = clean_phone($input['phone'] ?? '');
$flow  = strtolower(trim((string)($input['flow'] ?? 'register'))); // "register" | "login"
$via   = strtolower(trim((string)($input['via']  ?? '')));         // optional "waninza"

if (!preg_match('/^[6-9]\d{9}$/', $phone)) {
  json_out(['ok'=>false,'error'=>'Invalid phone','error_code'=>'INVALID_PHONE'], 422);
}

// ===================== RATE LIMITING =====================
$clientIp = $_SERVER['REMOTE_ADDR'] ?? 'unknown';
$rateLimitWindow = 10; // minutes
$maxPerPhone = 3;      // max OTPs per phone per window
$maxPerIp    = 10;     // max OTPs per IP per window

try {
  $pdo_rl = pdo();

  // Ensure rate limit table exists
  $pdo_rl->exec("CREATE TABLE IF NOT EXISTS otp_rate_limit (
    id INT AUTO_INCREMENT PRIMARY KEY,
    identifier VARCHAR(64) NOT NULL,
    type ENUM('phone','ip') NOT NULL DEFAULT 'phone',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_lookup (identifier, type, created_at)
  )");

  // Clean old entries
  $pdo_rl->exec("DELETE FROM otp_rate_limit WHERE created_at < DATE_SUB(NOW(), INTERVAL $rateLimitWindow MINUTE)");

  // Check phone rate
  $stmt = $pdo_rl->prepare("SELECT COUNT(*) FROM otp_rate_limit WHERE identifier=? AND type='phone' AND created_at > DATE_SUB(NOW(), INTERVAL $rateLimitWindow MINUTE)");
  $stmt->execute([$phone]);
  if ((int)$stmt->fetchColumn() >= $maxPerPhone) {
    json_out(['ok'=>false, 'error'=>"Too many OTP requests. Please wait $rateLimitWindow minutes.", 'error_code'=>'RATE_LIMITED'], 429);
  }

  // Check IP rate
  $stmt = $pdo_rl->prepare("SELECT COUNT(*) FROM otp_rate_limit WHERE identifier=? AND type='ip' AND created_at > DATE_SUB(NOW(), INTERVAL $rateLimitWindow MINUTE)");
  $stmt->execute([$clientIp]);
  if ((int)$stmt->fetchColumn() >= $maxPerIp) {
    json_out(['ok'=>false, 'error'=>'Too many requests from this device.', 'error_code'=>'RATE_LIMITED'], 429);
  }

  // Record this attempt
  $stmt = $pdo_rl->prepare("INSERT INTO otp_rate_limit (identifier, type) VALUES (?, 'phone'), (?, 'ip')");
  $stmt->execute([$phone, $clientIp]);

} catch (Throwable $e) {
  error_log("⚠️ OTP rate limit check failed: " . $e->getMessage());
  // Don't block OTP if rate limit table fails — degrade gracefully
}
// ===================== END RATE LIMITING =====================

try {
  $pdo = pdo();
  $pdo->beginTransaction();

  // NOTE: use your real table name (you showed it as `user`)
  $stmt = $pdo->prepare("SELECT user_id FROM `user` WHERE phone=? LIMIT 1");
  $stmt->execute([$phone]);
  $row = $stmt->fetch(PDO::FETCH_ASSOC);

  $now   = time();
  $otp   = random_int(100000, 999999);
  $hash  = hash('sha256', (string)$otp);
  $expAt = (new DateTime('+'.OTP_EXP_MIN.' minutes'))->format('Y-m-d H:i:s');

  if ($row) {
    // EXISTING ACCOUNT
    if ($flow === 'register') {
      // From Register screen → do NOT send OTP, force login
      $pdo->commit();
      json_out([
        'ok'           => true,
        'otp_sent'     => false,
        'user_exists'  => true,
        'next'         => 'login_required',
        'error_code'   => 'ALREADY_REGISTERED',
        'message'      => 'You are already registered. Please login.'
      ], 200);
    }

    // LOGIN flow → refresh OTP and send
    $up = $pdo->prepare("UPDATE `user`
                         SET otp_code=?, otp_expiry=?, updated_at=CURRENT_TIMESTAMP
                         WHERE user_id=?");
    $up->execute([$hash, $expAt, (int)$row['user_id']]);

  } else {
    // NO ACCOUNT
    if ($flow === 'login') {
      // From Login screen we DO NOT create users
      $pdo->commit();
      json_out([
        'ok'          => false,
        'otp_sent'    => false,
        'user_exists' => false,
        'next'        => 'register_required',
        'error_code'  => 'NOT_REGISTERED',
        'message'     => 'This number is not registered. Please register.'
      ], 404);
    }

    // REGISTER flow → create minimal row and send OTP
    $ins = $pdo->prepare("INSERT INTO `user`
      (full_name, phone, status, otp_code, otp_expiry, created_at, updated_at)
      VALUES ('', ?, 1, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
    $ins->execute([$phone, $hash, $expAt]);
  }

  $pdo->commit();

  // Send OTP only when we actually set it above
  $shouldSend = !($row && $flow === 'register'); // block send when register+existing
  if ($shouldSend) {
    $fields = [
      "sender_id"        => NINZA_SENDER_ID,
      "variables_values" => (string)$otp,
      "numbers"          => "91".$phone
    ];
    if ($via === 'waninza') $fields['rout'] = 'waninza';

    $ch = curl_init();
    curl_setopt_array($ch, [
      CURLOPT_URL            => NINZA_API_URL,
      CURLOPT_RETURNTRANSFER => true,
      CURLOPT_SSL_VERIFYHOST => 0,
      CURLOPT_SSL_VERIFYPEER => 0,
      CURLOPT_POST           => true,
      CURLOPT_POSTFIELDS     => json_encode($fields, JSON_UNESCAPED_UNICODE),
      CURLOPT_HTTPHEADER     => [
        "authorization: ".NINZA_API_KEY,
        "accept: */*",
        "content-type: application/json"
      ],
      CURLOPT_TIMEOUT        => 30
    ]);
    $resp = curl_exec($ch);
    $err  = curl_error($ch);
    curl_close($ch);
    if ($err) {
      json_out(['ok'=>false,'error'=>'SMS API error','error_code'=>'SMS_FAILED'], 500);
    }
  }

  // Success responses
  if ($row) {
    // existing user → login flow
    json_out([
      'ok'          => true,
      'otp_sent'    => ($flow !== 'register'),
      'user_exists' => true,
      'next'        => 'login_flow',
      'message'     => 'OTP sent'
      // no DEV OTP toast
    ], 200);
  } else {
    // new user → register flow
    json_out([
      'ok'          => true,
      'otp_sent'    => true,
      'user_exists' => false,
      'next'        => 'complete_profile',
      'message'     => 'OTP sent'
    ], 200);
  }

} catch (Throwable $e) {
  if (isset($pdo) && $pdo->inTransaction()) $pdo->rollBack();
  json_out(['ok'=>false,'error'=>'Server error'], 500);
}
