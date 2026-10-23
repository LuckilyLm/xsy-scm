package com.xsy.scm.purchase.service;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.purchase.constant.PurchaseErrorCode;
import com.xsy.scm.purchase.constant.ScmPurchaseStatusEnum;
import com.xsy.scm.purchase.dao.PurchaseShareDao;
import com.xsy.scm.purchase.domain.form.PurchaseShareCreateForm;
import com.xsy.scm.purchase.domain.form.PurchaseShareFeedbackForm;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderVO;
import com.xsy.scm.purchase.domain.vo.PurchaseSharePublicVO;
import com.xsy.scm.purchase.domain.vo.PurchaseShareVO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 采购单二维码分享。
 *
 * <p>
 * 分享链接本身就是凭证：令牌不透明、只存哈希、有有效期、可撤销，一张采购单同一时刻只有一条有效链接。 手机端回填只追加记录，不直接改写采购单——现场误填不能悄悄改掉已提交的单据。
 */
@Service
@RequiredArgsConstructor
public class PurchaseShareService {

    /**
     * 默认有效期；现场作业通常当天完成，给一周足够覆盖补录。
     */
    private static final int DEFAULT_VALID_DAYS = 7;

    private static final int TOKEN_BYTES = 32;

    private static final int PREFIX_LENGTH = 6;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PurchaseShareDao purchaseShareDao;

    private final PurchaseQueryService purchaseQueryService;

    /**
     * 生成分享链接：先撤销该采购单上的旧链接，再签一条新的。
     *
     * <p>
     * 旧链接必须当场失效，否则同一张单会同时存在多个入口，回填来源就说不清了。
     */
    @Transactional(rollbackFor = Exception.class)
    public PurchaseShareVO create(PurchaseShareCreateForm form, String operator) {
        PurchaseOrderVO order = purchaseQueryService.orderDetail(form.getPurchaseOrderId());
        ScmPurchaseStatusEnum status = ScmPurchaseStatusEnum.of(order.getStatus());
        if (status == null || !status.committed()) {
            throw new ScmBusinessException(PurchaseErrorCode.PURCHASE_SHARE_STATE_INVALID);
        }

        OffsetDateTime now = OffsetDateTime.now();
        purchaseShareDao.revokeActiveByOrder(order.getId(), now);

        String token = newToken();
        int validDays = form.getValidDays() == null ? DEFAULT_VALID_DAYS : form.getValidDays();
        PurchaseShareVO row = new PurchaseShareVO();
        row.setPurchaseOrderId(order.getId());
        row.setTokenHash(hash(token));
        row.setTokenPrefix(token.substring(0, PREFIX_LENGTH));
        row.setExpiresAt(now.plusDays(validDays));
        row.setCreatedBy(operator);
        purchaseShareDao.insertToken(row);

        // 明文只在这里出现一次，之后任何接口都不再回显
        row.setToken(token);
        return row;
    }

    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long shareId) {
        PurchaseShareVO row = purchaseShareDao.findById(shareId);
        if (row == null) {
            throw new ScmBusinessException(PurchaseErrorCode.PURCHASE_SHARE_NOT_FOUND);
        }
        if (row.getRevokedAt() != null) {
            return;
        }
        purchaseShareDao.revokeActiveByOrder(row.getPurchaseOrderId(), OffsetDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<PurchaseShareVO> list(Long purchaseOrderId) {
        // 详情读取先过一遍采购员范围，越界直接 30005
        purchaseQueryService.orderDetail(purchaseOrderId);
        return purchaseShareDao.listByOrder(purchaseOrderId);
    }

    /**
     * 手机端读取任务；令牌即凭证，不再走登录与数据范围。
     */
    @Transactional(readOnly = true)
    public PurchaseSharePublicVO view(String token) {
        PurchaseShareVO row = requireUsable(token);
        PurchaseSharePublicVO header = purchaseShareDao.header(row.getPurchaseOrderId());
        if (header == null) {
            throw new ScmBusinessException(PurchaseErrorCode.PURCHASE_SHARE_NOT_FOUND);
        }
        header.setExpiresAt(row.getExpiresAt());
        header.setLines(purchaseShareDao.lines(row.getPurchaseOrderId()));
        return header;
    }

    /**
     * 回填单价与重量。
     *
     * <p>
     * 只追加：同一行多次提交都会留下记录，界面展示最近一次。明细必须属于这张采购单， 否则一个令牌就能往别人的单子上写数。
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitFeedback(String token, PurchaseShareFeedbackForm form, String operatorIp) {
        PurchaseShareVO row = requireUsable(token);
        Set<Long> owned = new HashSet<>(purchaseShareDao.itemIdsOfOrder(row.getPurchaseOrderId()));
        for (PurchaseShareFeedbackForm.Line line : form.getItems()) {
            if (!owned.contains(line.getPurchaseOrderItemId())) {
                throw new ScmBusinessException(PurchaseErrorCode.PURCHASE_SHARE_ITEM_NOT_OWNED);
            }
            if (line.getUnitPrice() == null && line.getReceivedQuantity() == null) {
                throw new ScmBusinessException(PurchaseErrorCode.PURCHASE_SHARE_FEEDBACK_INVALID);
            }
            purchaseShareDao.insertFeedback(row.getId(), row.getPurchaseOrderId(), line.getPurchaseOrderItemId(),
                line.getUnitPrice(), line.getReceivedQuantity(), trimToNull(line.getRemark()), operatorIp,
                "share:" + row.getTokenPrefix());
        }
        purchaseShareDao.touchUsed(row.getId(), OffsetDateTime.now());
    }

    /**
     * 令牌可用性：存在、未撤销、未过期。
     */
    private PurchaseShareVO requireUsable(String token) {
        if (token == null || token.isBlank()) {
            throw new ScmBusinessException(PurchaseErrorCode.PURCHASE_SHARE_NOT_FOUND);
        }
        PurchaseShareVO row = purchaseShareDao.findByHash(hash(token.trim()));
        if (row == null) {
            throw new ScmBusinessException(PurchaseErrorCode.PURCHASE_SHARE_NOT_FOUND);
        }
        if (row.getRevokedAt() != null) {
            throw new ScmBusinessException(PurchaseErrorCode.PURCHASE_SHARE_REVOKED);
        }
        if (row.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new ScmBusinessException(PurchaseErrorCode.PURCHASE_SHARE_EXPIRED);
        }
        return row;
    }

    /**
     * 不透明令牌：32 字节随机数的 Base64URL，不含业务含义，也不含时间。
     */
    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] result = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(result.length * 2);
            for (byte value : result) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
