package net.lab1024.sa.admin.module.scm.purchase;

import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderUpdateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderVersionForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 采购单乐观锁（W5 Target Design §11.2，3 例）。
 *
 * <p>两个层级各自独立：**采购单版本**（`purchase_order.version`）挡「同一张单被两个人同时编辑」，
 * **采购行版本**（`purchase_order_item.version`）挡「同一行被两个人同时改数量」。
 *
 * <p><b>为什么必须两层都有</b>：只校验单头版本时，一个客户端可以拿着「刚读到的单头版本 +
 * 过期的行版本」提交，把别人刚改过的行覆盖掉 —— 单头版本看起来完全合法。
 * 因此这里的第 3 例专门验证行级版本过期必须被拦。
 *
 * <p><b>断言的重点是「拒绝之后库中一个字节都没变」</b>：乐观锁失败必须整体回滚，
 * 不能出现「单头没改、行改了」或「需求侧被扣了但单没保存」这类半成品状态。
 */
@DisplayName("采购单乐观锁：单头版本 / 行版本（PG IT）")
class PurchaseOrderOptimisticLockIT extends ScmW5PgITBase {

    /** 一张草稿单 + 它引用的需求（编辑请求需要需求的当前版本）。 */
    private record Draft(Long skuId, Long supplierId, PurchaseDemandEntity demand, PurchaseOrderVO order) {
    }

    private Draft draft(String suffix) {
        Long skuId = newOnShelfSku(suffix);
        Long supplierId = newPurchasableSupplier(suffix, skuId);
        Long customerId = newCustomer();
        Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
        PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);
        PurchaseOrderVO order = createDraftOrder(suffix, supplierId, skuId, "3.0000", "6.2000",
                allocation(demand, "3.0000"));
        return new Draft(skuId, supplierId, demand, order);
    }

    // ------------------------------------------------------------------
    // 1. 单头版本过期
    // ------------------------------------------------------------------

    @Test
    @DisplayName("update：采购单版本过期 → 40921，且单头 / 采购行 / 需求侧全部不变")
    void updateRejectsStaleOrderVersion() {
        Draft draft = draft("OL1");
        PurchaseDemandEntity fresh = reloadDemand(draft.demand().getId());

        PurchaseOrderUpdateForm form = editForm(draft.order().getId(), "2.0000", "5.0000",
                allocation(fresh, "2.0000"));
        form.setVersion(draft.order().getVersion() + 1);   // 过期版本

        expectCode(() -> purchaseOrderService.update(form), 40921);

        // 整体回滚：没有任何一处被写坏
        PurchaseOrderVO after = reloadOrder(draft.order().getId());
        assertThat(after.getTotalAmount()).isEqualByComparingTo("18.6000");
        assertThat(after.getItems().getFirst().getPlannedQuantity()).isEqualByComparingTo("3.0000");
        assertThat(after.getItems().getFirst().getPurchasePrice()).isEqualByComparingTo("6.2000");
        assertThat(reloadDemand(draft.demand().getId()).getAllocatedQuantity())
                .isEqualByComparingTo("3.0000");
    }

    // ------------------------------------------------------------------
    // 2. submit 的单头版本
    // ------------------------------------------------------------------

    @Test
    @DisplayName("submit：采购单版本过期 → 40921，状态仍为 DRAFT")
    void submitRejectsStaleOrderVersion() {
        Draft draft = draft("OL2");

        PurchaseOrderVersionForm stale = new PurchaseOrderVersionForm();
        stale.setId(draft.order().getId());
        stale.setVersion(draft.order().getVersion() + 7);
        expectCode(() -> purchaseOrderService.submit(stale, prefix + ":OL2:submit"), 40921);

        assertThat(reloadOrder(draft.order().getId()).getStatus()).isEqualTo("DRAFT");
        // 失败不写 SUBMIT 日志
        assertThat(purchaseQueryService.orderLogs(draft.order().getId()))
                .extracting(log -> log.getOperationType())
                .containsExactly("CREATE");

        // 正确版本仍可提交
        PurchaseOrderVersionForm ok = new PurchaseOrderVersionForm();
        ok.setId(draft.order().getId());
        ok.setVersion(reloadOrder(draft.order().getId()).getVersion());
        assertThat(purchaseOrderService.submit(ok, prefix + ":OL2:submit2").getStatus())
                .isEqualTo("SUBMITTED");
    }

    // ------------------------------------------------------------------
    // 3. 行版本过期
    // ------------------------------------------------------------------

    @Test
    @DisplayName("update：保留行版本过期 → 40984（行级乐观锁，单头版本合法也拦得住）")
    void updateRejectsStaleItemVersion() {
        Draft draft = draft("OL3");
        PurchaseDemandEntity fresh = reloadDemand(draft.demand().getId());

        PurchaseOrderUpdateForm form = editForm(draft.order().getId(), "2.0000", "5.0000",
                allocation(fresh, "2.0000"));
        // 单头版本是**当前值**（合法），只有行版本过期 —— 这正是「只校验单头」会漏掉的场景
        form.getItems().getFirst().setVersion(form.getItems().getFirst().getVersion() + 3);

        expectCode(() -> purchaseOrderService.update(form), 40984);

        PurchaseOrderVO after = reloadOrder(draft.order().getId());
        assertThat(after.getItems().getFirst().getPlannedQuantity()).isEqualByComparingTo("3.0000");
        assertThat(after.getTotalAmount()).isEqualByComparingTo("18.6000");
        assertThat(reloadDemand(draft.demand().getId()).getAllocatedQuantity())
                .isEqualByComparingTo("3.0000");
    }
}
