package net.lab1024.sa.admin.module.scm.inventory.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;

/**
 * 盘点 Excel 模板的「签名快照凭证」编解码器（无状态，不落任何快照表）。
 *
 * <p><b>为什么是无状态签名而不是快照表</b>：盘点计划要求「从导出到导入任一参与行余额版本变化即整批拒绝」，
 * 又不允许新建业务快照表，因此导出时把权威快照（仓库、来源 SKU 集合、每行的余额 id / 版本 / 记账单位 /
 * 账面量、模板版本、导出人、有效期）编成一段令牌随模板带出，导入时用它做「受保护来源集合」并与持锁读取的
 * 当前余额逐项核验。令牌用 HMAC-SHA256 签名，用户改一个字节即校验失败，故 Excel 里的元数据列不可信也无妨。
 *
 * <p><b>密钥来源</b>：{@code scm.inventory.stocktake.snapshot.secret}。默认值 {@code DEV_SECRET}
 * 只保证本地开发与 IT 可运行；pre / prod profile 下缺失或仍等于该公开默认值会让构造直接抛错、
 * 启动失败（见 {@link #requireNonPublicSecret}），生产必须用环境变量
 * {@code SCM_INVENTORY_STOCKTAKE_SNAPSHOT_SECRET} 显式配置（AGENTS §24：不把真实密钥写进源码 / 配置）。
 */
@Component
public class StocktakeSnapshotSigner {

    /** 仓库里公开的占位密钥：只允许在非生产 profile 下兜底。 */
    static final String DEV_SECRET = "xsy-scm-stocktake-snapshot-dev-secret-change-in-production";

    /**
     * 来源集合里的一行：导入持锁核验时逐项比对的权威事实。
     *
     * @param skuCode      用于把用户填写的实盘量映射回来源行（身份键，仍来自受保护凭证）
     * @param balanceId    导出时刻的余额行 id
     * @param version      导出时刻的余额乐观锁版本
     * @param unit         导出时刻的记账单位
     * @param bookQuantity 导出时刻的账面量快照
     */
    public record Entry(String skuCode, Long skuId, Long balanceId, String unit,
                        Integer version, BigDecimal bookQuantity) {
    }

    /**
     * 一份完整快照凭证。
     *
     * @param operator 导出人（{@code ScmOperator#current}），导入时要求同一人，防止跨人复用离线清单
     */
    public record Payload(String templateVersion, Long warehouseId, String operator,
                          long issuedAtEpochSec, long expiresAtEpochSec, List<Entry> entries) {
    }

    /** 凭证校验失败（签名不符 / 结构损坏 / 已过期）。调用方据此整批拒绝。 */
    public static class SnapshotCredentialException extends RuntimeException {
        public SnapshotCredentialException(String message) {
            super(message);
        }

        public SnapshotCredentialException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 验签通过后仍解析不出载荷：body 是本服务自己签出去的字节，所以这只可能是服务端故障，
     * 不是用户凭证坏了。调用方必须与真正的凭证类失败分开处理，否则会把用户推进「重导模板」的死循环。
     */
    public static class SnapshotPayloadUnreadable extends SnapshotCredentialException {
        public SnapshotPayloadUnreadable(Throwable cause) {
            super("快照凭证内容无法解析", cause);
        }
    }

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final ObjectMapper json;
    private final byte[] secret;

    public StocktakeSnapshotSigner(ObjectMapper json,
                                   @Value("${scm.inventory.stocktake.snapshot.secret:"
                                           + DEV_SECRET + "}") String secret,
                                   @Value("${spring.profiles.active:dev}") String activeProfiles) {
        requireNonPublicSecret(secret, activeProfiles);
        this.json = json;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * pre / prod 下密钥必须显式配置且不等于仓库默认值，否则构造失败即启动失败。
     *
     * <p>只看 profile 字符串而不是 {@code SystemEnvironment#isProd()}：后者把 {@code pre} 判为非生产，
     * 而预发布同样是不能用公开密钥的环境。口径与 {@code FileConfig#validateCloudConfig} 保持一致。
     */
    static void requireNonPublicSecret(String secret, String activeProfiles) {
        boolean production = java.util.Arrays.stream(activeProfiles.split(","))
                .map(String::trim)
                .anyMatch(p -> p.equals("pre") || p.equals("prod") || p.equals("production"));
        if (production && (secret == null || secret.isBlank() || DEV_SECRET.equals(secret))) {
            throw new IllegalStateException("scm.inventory.stocktake.snapshot.secret must be overridden with a"
                    + " private value in pre/prod (set SCM_INVENTORY_STOCKTAKE_SNAPSHOT_SECRET);"
                    + " the built-in default is public and would make the snapshot signature unprotected");
        }
    }

    /** 序列化为 {@code base64url(json).base64url(hmac)}。 */
    public String sign(Payload payload) {
        try {
            // 载荷先压缩再 base64：快照要为仓库里每一条活跃余额带上 skuCode / 单位 / 账面量，
            // 而凭证会被写进模板每一行的单元格。POI 的单元格上限是 32767 字符，实测约 200 多个
            // SKU 就会把未压缩的 JSON 顶过这条线，导致大仓库根本导不出盘点模板。
            // 载荷内容不变、校验语义不变，只是编码多一层 DEFLATE。
            byte[] body = deflate(json.writeValueAsBytes(payload));
            byte[] mac = hmac(body);
            return ENCODER.encodeToString(body) + "." + ENCODER.encodeToString(mac);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成盘点快照凭证", exception);
        }
    }

    private static byte[] deflate(byte[] raw) {
        var deflater = new java.util.zip.Deflater(java.util.zip.Deflater.BEST_COMPRESSION, true);
        try {
            deflater.setInput(raw);
            deflater.finish();
            var out = new java.io.ByteArrayOutputStream(Math.max(64, raw.length / 8));
            var buffer = new byte[8192];
            while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer));
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    /** 只在 MAC 校验通过后调用：不解压未经认证的字节。 */
    private static byte[] inflate(byte[] packed) {
        var inflater = new java.util.zip.Inflater(true);
        try {
            inflater.setInput(packed);
            var out = new java.io.ByteArrayOutputStream(Math.max(64, packed.length * 4));
            var buffer = new byte[8192];
            while (!inflater.finished()) {
                int read = inflater.inflate(buffer);
                // 推进不了就停：宁可让上层把这段载荷判成「无法解析」而整批拒绝，也不带着未结束的解压循环转圈。
                if (read == 0) break;
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        } catch (java.util.zip.DataFormatException exception) {
            throw new IllegalStateException("无法解压盘点快照载荷", exception);
        } finally {
            inflater.end();
        }
    }

    /**
     * 验签并反序列化；签名不符、结构损坏或 {@code expiresAtEpochSec < nowEpochSec} 均抛
     * {@link SnapshotCredentialException}。验签<b>通过之后</b>仍解析不出载荷时抛
     * {@link SnapshotPayloadUnreadable}——那是服务端故障，调用方不得按「用户凭证坏了」处理。
     * 模板版本 / 仓库 / 操作者的匹配由调用方负责。
     */
    public Payload verify(String token, long nowEpochSec) {
        if (token == null) {
            throw new SnapshotCredentialException("快照凭证缺失");
        }
        int dot = token.lastIndexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            throw new SnapshotCredentialException("快照凭证格式非法");
        }
        byte[] body;
        byte[] mac;
        try {
            body = DECODER.decode(token.substring(0, dot));
            mac = DECODER.decode(token.substring(dot + 1));
        } catch (IllegalArgumentException exception) {
            throw new SnapshotCredentialException("快照凭证无法解码");
        }
        if (!MessageDigest.isEqual(hmac(body), mac)) {
            throw new SnapshotCredentialException("快照凭证签名校验失败");
        }
        Payload payload;
        try {
            // 解压只在上面 MAC 比对通过之后发生：未经认证的字节不进 Inflater。
            // 解压失败与解析失败同类——载荷是我们自己签出去的，属服务端故障。
            payload = json.readValue(inflate(body), Payload.class);
        } catch (Exception exception) {
            // 走到这里签名已经验过，body 就是我们自己签出去的那段字节 —— 解析不出来不可能是用户造成的，
            // 只可能是服务端（Jackson 版本 / record 结构 / 序列化配置）漂移。报成「凭证损坏，请重导模板」
            // 会让用户空转，且 cause 被丢弃后运维零线索。
            throw new SnapshotPayloadUnreadable(exception);
        }
        if (payload.expiresAtEpochSec() < nowEpochSec) {
            throw new SnapshotCredentialException("快照凭证已过期，请重新导出模板");
        }
        return payload;
    }

    private byte[] hmac(byte[] body) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(body);
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算快照凭证签名", exception);
        }
    }
}
