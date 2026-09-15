import cn.hutool.crypto.symmetric.SM4;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.Base64;

/**
 * Mirrors net.lab1024.sa.base.module.support.apiencrypt.service.ApiEncryptServiceSmImpl
 * so a real login request can be driven over HTTP.
 *
 * Usage: java Sm4Encrypt.java <plaintext>
 */
public class Sm4Encrypt {

    private static final String CHARSET = "UTF-8";
    private static final String SM4_KEY = "1024lab__1024lab";

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static void main(String[] args) throws Exception {
        String data = args[0];
        SM4 sm4 = new SM4(hexToBytes(stringToHex(SM4_KEY)));
        String encryptHex = sm4.encryptHex(data);
        String encoded = new String(Base64.getEncoder().encode(encryptHex.getBytes(CHARSET)), CHARSET);
        System.out.println(encoded);
    }

    static String stringToHex(String input) {
        StringBuilder hex = new StringBuilder();
        for (char c : input.toCharArray()) {
            hex.append(Integer.toHexString((int) c));
        }
        return hex.toString();
    }

    static byte[] hexToBytes(String hex) {
        int length = hex.length();
        byte[] result;
        if (length % 2 == 1) {
            length++;
            result = new byte[(length / 2)];
            hex = "0" + hex;
        } else {
            result = new byte[(length / 2)];
        }
        int j = 0;
        for (int i = 0; i < length; i += 2) {
            result[j] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
            j++;
        }
        return result;
    }
}
