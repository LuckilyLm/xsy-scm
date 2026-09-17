package net.lab1024.sa.admin.test;

import cn.hutool.crypto.symmetric.SM4;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.Base64;

/**
 * e2e 登录密文生成器（命令行工具，供 Playwright / 探针脚本调用）。
 *
 * <p><b>为什么需要这个类：</b>SmartAdmin 的登录口令走「SM4 国密 + Base64」传输加密，
 * 实现见 {@code ApiEncryptServiceSmImpl}。要在 Node / Python 侧复现这段加密，
 * 必须逐字节对齐 Hutool 的填充语义与密钥派生方式——用其它语言的 SM4 库复刻极易出错，
 * 而且一旦上游改动就无法感知。
 *
 * <p>这里选择「不重实现，直接调用项目自己的实现」：单元测试类路径下已有
 * hutool / bouncycastle，直接用同一套代码加密，天然与运行态一致。
 *
 * <p><b>用法：</b>
 * <pre>
 *   java -cp &lt;test-classpath&gt; net.lab1024.sa.admin.test.Sm4EncryptCli &lt;plainText&gt;
 * </pre>
 * 标准输出第一行即 Base64 密文。
 *
 * <p><b>注意：</b>本类只做加密，不含任何凭据；密码由调用方通过命令行传入。
 */
public final class Sm4EncryptCli {

    private static final String SM4_KEY = "1024lab__1024lab";

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private Sm4EncryptCli() {
    }

    /** 与 {@code ApiEncryptServiceSmImpl.stringToHex} 完全一致。 */
    private static String stringToHex(String input) {
        StringBuilder hex = new StringBuilder();
        for (char c : input.toCharArray()) {
            hex.append(Integer.toHexString((int) c));
        }
        return hex.toString();
    }

    /** 与 {@code ApiEncryptServiceSmImpl.hexToBytes} 完全一致。 */
    private static byte[] hexToBytes(String hex) {
        int length = hex.length();
        byte[] result;
        if (length % 2 == 1) {
            length++;
            result = new byte[length / 2];
            hex = "0" + hex;
        } else {
            result = new byte[length / 2];
        }
        int j = 0;
        for (int i = 0; i < length; i += 2) {
            result[j++] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return result;
    }

    /** 与 {@code ApiEncryptServiceSmImpl.encrypt} 完全一致。 */
    public static String encrypt(String data) {
        SM4 sm4 = new SM4(hexToBytes(stringToHex(SM4_KEY)));
        String encryptHex = sm4.encryptHex(data);
        return new String(Base64.getEncoder().encode(encryptHex.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8);
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("用法: Sm4EncryptCli <plainText>");
            System.exit(2);
        }
        System.out.println(encrypt(args[0]));
    }
}
