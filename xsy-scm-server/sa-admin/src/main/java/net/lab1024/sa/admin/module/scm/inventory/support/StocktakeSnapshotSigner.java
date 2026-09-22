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
 * <p><b>密钥来源</b>：{@code scm.inventory.stocktake.snapshot.secret}，生产必须用环境变量覆盖默认值
 * （AGENTS §24：不把真实密钥写进源码 / 配置）。默认值只保证本地开发与 IT 可运行，
 * 它保护的是「导入完整性」而非外部系统凭据，泄露不构成对外部资源的越权。
 */
@Component
public class StocktakeSnapshotSigner {

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
    }

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final ObjectMapper json;
    private final byte[] secret;

    public StocktakeSnapshotSigner(ObjectMapper json,
                                   @Value("${scm.inventory.stocktake.snapshot.secret:"
                                           + "xsy-scm-stocktake-snapshot-dev-secret-change-in-production}") String secret) {
        this.json = json;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** 序列化为 {@code base64url(json).base64url(hmac)}。 */
    public String sign(Payload payload) {
        try {
            byte[] body = json.writeValueAsBytes(payload);
            byte[] mac = hmac(body);
            return ENCODER.encodeToString(body) + "." + ENCODER.encodeToString(mac);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成盘点快照凭证", exception);
        }
    }

    /**
     * 验签并反序列化；签名不符、结构损坏或 {@code expiresAtEpochSec < nowEpochSec} 均抛
     * {@link SnapshotCredentialException}。模板版本 / 仓库 / 操作者的匹配由调用方负责。
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
            payload = json.readValue(body, Payload.class);
        } catch (Exception exception) {
            throw new SnapshotCredentialException("快照凭证内容损坏");
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
