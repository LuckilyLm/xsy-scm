package com.xsy.scm.purchase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.purchase.domain.form.PurchaseShareCreateForm;
import com.xsy.scm.purchase.domain.form.PurchaseShareFeedbackForm;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderVO;
import com.xsy.scm.purchase.domain.vo.PurchaseSharePublicVO;
import com.xsy.scm.purchase.domain.vo.PurchaseShareVO;
import com.xsy.scm.purchase.service.PurchaseShareService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 采购单二维码分享：令牌签发、失效、手机端回填。
 *
 * <p>
 * 令牌是唯一凭证，因此这里重点验证「什么时候不该再能用」：重新分享、撤销、过期都必须让旧令牌失效。
 */
class PurchaseSharePgIT extends ScmW5PgITBase {

    @Autowired
    private PurchaseShareService purchaseShareService;

    @Test
    @DisplayName("#1 签发令牌后可读任务，回填后能看到最近一次单价与重量")
    void issueTokenThenSubmitFeedback() {
        PurchaseOrderVO order = submittedOrder("SHARE1");
        PurchaseShareVO share = purchaseShareService.create(createForm(order.getId(), null), "W9 IT");

        assertThat(share.getToken()).hasSize(43);
        assertThat(share.getExpiresAt()).isAfter(OffsetDateTime.now());

        PurchaseSharePublicVO view = purchaseShareService.view(share.getToken());
        assertThat(view.getOrderNo()).isEqualTo(order.getOrderNo());
        assertThat(view.getLines()).isNotEmpty();

        Long itemId = view.getLines().getFirst().getItemId();
        PurchaseShareFeedbackForm form = feedback(itemId, "3.5000", "12.0000", "现场称重");
        purchaseShareService.submitFeedback(share.getToken(), form, "127.0.0.1");

        PurchaseSharePublicVO after = purchaseShareService.view(share.getToken());
        PurchaseSharePublicVO.Line line = after.getLines().stream()
                .filter(row -> row.getItemId().equals(itemId))
                .findFirst()
                .orElseThrow();
        assertThat(line.getLastUnitPrice()).isEqualByComparingTo("3.5000");
        assertThat(line.getLastReceivedQuantity()).isEqualByComparingTo("12.0000");
        assertThat(line.getLastRemark()).isEqualTo("现场称重");
    }

    @Test
    @DisplayName("#2 重新分享让旧令牌立即失效（41092）")
    void reissueRevokesPreviousToken() {
        PurchaseOrderVO order = submittedOrder("SHARE2");
        PurchaseShareVO first = purchaseShareService.create(createForm(order.getId(), 7), "W9 IT");
        PurchaseShareVO second = purchaseShareService.create(createForm(order.getId(), 7), "W9 IT");

        assertThatThrownBy(() -> purchaseShareService.view(first.getToken()))
                .isInstanceOf(ScmBusinessException.class)
                .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode())
                        .isEqualTo(41092));
        assertThat(purchaseShareService.view(second.getToken()).getOrderNo()).isEqualTo(order.getOrderNo());
    }

    @Test
    @DisplayName("#3 过期令牌被拒（41091），回填非本单明细被拒（41095）")
    void rejectsExpiredTokenAndForeignItem() {
        PurchaseOrderVO order = submittedOrder("SHARE3");
        PurchaseShareVO share = purchaseShareService.create(createForm(order.getId(), 1), "W9 IT");

        jdbc.update("UPDATE xsy_purchase_share_token SET expires_at = ? WHERE id = ?",
                OffsetDateTime.now().minusMinutes(1), share.getId());
        assertThatThrownBy(() -> purchaseShareService.view(share.getToken()))
                .isInstanceOf(ScmBusinessException.class)
                .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode())
                        .isEqualTo(41091));

        // 换一条有效令牌，用别的采购单的明细回填
        PurchaseShareVO fresh = purchaseShareService.create(createForm(order.getId(), 7), "W9 IT");
        PurchaseOrderVO other = submittedOrder("SHARE4");
        Long foreignItem = purchaseShareService.view(fresh.getToken()).getLines().isEmpty()
                ? -1L
                : otherItemId(other.getId());

        assertThatThrownBy(() -> purchaseShareService.submitFeedback(fresh.getToken(),
                feedback(foreignItem, "1.0000", null, null), "127.0.0.1"))
                .isInstanceOf(ScmBusinessException.class)
                .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode())
                        .isEqualTo(41095));
    }

    private PurchaseOrderVO submittedOrder(String suffix) {
        Fixture fixture = fixture(suffix, "10.0000", "10.0000");
        Long orderId = createDraftOrder(suffix, fixture.supplierId(), fixture.skuId(), "10.0000", "2.0000").getId();
        return submitOrder(orderId);
    }

    private Long otherItemId(Long orderId) {
        return jdbc.queryForObject(
                "SELECT id FROM xsy_purchase_order_item WHERE purchase_order_id = ? AND deleted = FALSE LIMIT 1",
                Long.class, orderId);
    }

    private PurchaseShareCreateForm createForm(Long orderId, Integer validDays) {
        PurchaseShareCreateForm form = new PurchaseShareCreateForm();
        form.setPurchaseOrderId(orderId);
        form.setValidDays(validDays);
        return form;
    }

    private PurchaseShareFeedbackForm feedback(Long itemId, String price, String quantity, String remark) {
        PurchaseShareFeedbackForm.Line line = new PurchaseShareFeedbackForm.Line();
        line.setPurchaseOrderItemId(itemId);
        line.setUnitPrice(price == null ? null : new BigDecimal(price));
        line.setReceivedQuantity(quantity == null ? null : new BigDecimal(quantity));
        line.setRemark(remark);
        PurchaseShareFeedbackForm form = new PurchaseShareFeedbackForm();
        form.setItems(List.of(line));
        return form;
    }
}
