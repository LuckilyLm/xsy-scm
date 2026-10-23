package com.xsy.scm.device.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 设备凭证、配对码与员工会话令牌的生成与摘要。
 *
 * <p>
 * 三类令牌都只在签发时返回明文一次，库里只存摘要：拿到数据库不足以冒充设备或员工。 摘要用 SHA-256 而不是加密，因为这些值本身就是高熵随机串，不需要可逆。
 */
public final class ScaleCredentialCodec {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final int TOKEN_BYTES = 32;

    private ScaleCredentialCodec() {
    }

    /**
     * URL 安全且无填充，便于放进请求头与二维码。
     */
    public static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 配对码给人手工输入，用短数字串；熵低于令牌，因此必须短有效期且一次性。
     */
    public static String newPairingCode() {
        int value = RANDOM.nextInt(100_000_000);
        return String.format("%08d", value);
    }

    public static String digest(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] result = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(result.length * 2);
            for (byte value : result) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
