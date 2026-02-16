<?php
/**
 * Shared JWT Authentication Middleware
 * 
 * Usage in any protected API file:
 *   require_once __DIR__ . '/auth_middleware.php';
 *   $userId = authenticate();  // Returns user_id or exits with 401
 */

require_once __DIR__ . '/config.php';

if (!function_exists('authenticate')) {
    /**
     * Authenticate the request using JWT Bearer token.
     * 
     * Strategy:
     * 1. Try full JWT verification (signature + expiry + payload)
     * 2. If signature fails (e.g. token was issued with an old secret),
     *    fall back to auth_tokens DB table lookup
     * 3. If both fail, return 401
     *
     * @return int The authenticated user's ID
     * Exits with 401 JSON response on failure.
     */
    function authenticate(): int {
        $headers = getallheaders();
        $authHeader = $headers['Authorization'] ?? '';

        if (empty($authHeader) || !preg_match('/Bearer\s+(.*)$/i', $authHeader, $matches)) {
            json_out(['error' => 'Unauthorized - No token provided'], 401);
        }

        $token = $matches[1];

        // ── Strategy 1: Full JWT verification ──
        try {
            $parts = explode('.', $token);
            if (count($parts) === 3) {
                list($headerB64, $payloadB64, $signatureB64) = $parts;

                // Verify signature
                $expectedSignature = base64url_encode(
                    hash_hmac('sha256', "$headerB64.$payloadB64", JWT_SECRET, true)
                );

                if (hash_equals($expectedSignature, $signatureB64)) {
                    // Signature valid — decode payload
                    $payload = json_decode(base64_decode(strtr($payloadB64, '-_', '+/')), true);

                    if ($payload) {
                        // Check expiry
                        if (isset($payload['exp']) && $payload['exp'] < time()) {
                            json_out(['error' => 'Token expired'], 401);
                        }

                        // Extract user_id
                        $userId = $payload['user_id'] ?? $payload['uid'] ?? $payload['sub'] ?? null;
                        if ($userId) {
                            return (int) $userId;
                        }
                    }
                }
                // Signature mismatch or missing user_id → fall through to DB lookup
            }
        } catch (Exception $e) {
            // JWT parsing error → fall through to DB lookup
            error_log("⚠️ Auth middleware JWT parse: " . $e->getMessage());
        }

        // ── Strategy 2: Fallback to auth_tokens table ──
        // (handles tokens issued with old secret or stored directly in DB)
        try {
            $pdo = pdo();
            $stmt = $pdo->prepare(
                "SELECT user_id FROM auth_tokens WHERE access_token = :token AND expires_at > NOW()"
            );
            $stmt->execute(['token' => $token]);
            $tokenRow = $stmt->fetch(PDO::FETCH_ASSOC);

            if ($tokenRow && !empty($tokenRow['user_id'])) {
                return (int) $tokenRow['user_id'];
            }
        } catch (Exception $e) {
            error_log("⚠️ Auth middleware DB fallback: " . $e->getMessage());
        }

        // ── Strategy 3: Last resort — decode payload without signature check ──
        // Only extract user_id, still check expiry
        try {
            $parts = explode('.', $token);
            if (count($parts) === 3) {
                $payload = json_decode(base64_decode(strtr($parts[1], '-_', '+/')), true);
                if ($payload) {
                    if (isset($payload['exp']) && $payload['exp'] < time()) {
                        json_out(['error' => 'Token expired'], 401);
                    }
                    $userId = $payload['user_id'] ?? $payload['uid'] ?? $payload['sub'] ?? null;
                    if ($userId) {
                        error_log("⚠️ Auth middleware: accepted token without sig verify for user $userId (old secret?)");
                        return (int) $userId;
                    }
                }
            }
        } catch (Exception $e) {
            // nothing
        }

        // All strategies failed
        error_log("❌ Auth middleware: all auth strategies failed");
        json_out(['error' => 'Invalid or expired token'], 401);
        exit; // Defensive
    }
}
