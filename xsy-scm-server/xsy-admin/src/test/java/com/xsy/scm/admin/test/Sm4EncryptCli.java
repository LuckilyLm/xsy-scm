package com.xsy.scm.admin.test;

import cn.hutool.crypto.symmetric.SM4;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.test.context.DynamicPropertyRegistry;
import static org.assertj.core.api.Assertions.assertThat;


public final class Sm4EncryptCli {
    private static final String SM4_KEY = "1024lab__1024lab";

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private Sm4EncryptCli() {
    }

    /**
     * 与 {@code ApiEncryptServiceSmImpl.stringToHex} 完全一致。
     */
    private static String stringToHex(String input) {
        StringBuilder hex = new StringBuilder();
        for (char c : input.toCharArray()) {
            hex.append(Integer.toHexString((int) c));
        }
        return hex.toString();
    }

    /**
     * 与 {@code ApiEncryptServiceSmImpl.hexToBytes} 完全一致。
     */
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

    /**
     * 与 {@code ApiEncryptServiceSmImpl.encrypt} 完全一致。
     */
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
