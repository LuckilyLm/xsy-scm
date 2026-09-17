package net.lab1024.sa.admin.module.scm.purchase;

import net.lab1024.sa.admin.module.scm.common.ScmW5PgITBase;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderAddForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderVersionForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOperationLogVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 采购写命令幂等（W5 Target Design §7.11 / §11.2，4 例）。
 *
 * <p>复用 W4 的 `idempotency_record` 表（不新建表），但遵守三条纪律：
 * <b>scope 拼操作者</b>、<b>claim 用 INSERT 竞争</b>、<b>complete 与业务写入同一事务</b>。
 *
 * <p><b>为什么「重放」必须返回首次结果而不是重新执行</b>：`Idempotency-Key` 是给
 * 「网络超时后客户端重试」用的。如果重放时重新执行一次，用户点一次「提交」而网络抖动，
 * 就会得到两张采购单 —— 这正是幂等键要防的事，所以断言的重点是
 * <b>单据数量没有增加</b>，而不只是「两次返回同一个单号」。
 *
 * <p><b>为什么重放时必须复用同一个请求对象</b>：hash 是对请求体算的，而 `demandVersion`
 * 会在首次执行时被推进。若第二次重新构造请求（拿着新的需求版本），hash 就变了 ——
 * 那是**另一个请求**，会正确地得到 40990，而不是重放。
 */
@DisplayName("采购写命令幂等：重放 / 冲突 / 键校验 / 操作者隔离（PG IT）")
class PurchaseOrderIdempotencyIT extends ScmW5PgITBase {

    private int countActiveOrders(Long supplierId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM purchase_order WHERE supplier_id = ? AND deleted = FALSE",
                Integer.class, supplierId);
    }

    // ------------------------------------------------------------------
    // 1. 同键同内容 → 重放（不产生第二张单）+ 操作者隔离
    // ------------------------------------------------------------------

    @Test
    @DisplayName("同键同内容重放不产生第二张单；换操作者后同一 key 各自独立执行")
    void sameKeySameRequestReplaysWithoutSecondOrder() {
        Long skuId = newOnShelfSku("ID1");
        Long supplierId = newPurchasableSupplier("ID1", skuId);
        Long customerId = newCustomer();
        Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
        PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);

        String key = prefix + ":ID1:key";
        // **同一个请求实例**用两次：hash 必须一致，否则会被判成「同键异内容」
        PurchaseOrderAddForm form = orderForm(supplierId, seedWarehouseId(), skuId, "3.0000", "6.2000",
                allocation(demand, "3.0000"));

        PurchaseOrderVO first = purchaseOrderService.create(form, key);
        PurchaseOrderVO replayed = purchaseOrderService.create(form, key);

        assertThat(replayed.getId()).isEqualTo(first.getId());
        assertThat(replayed.getOrderNo()).isEqualTo(first.getOrderNo());
        assertThat(countActiveOrders(supplierId)).isEqualTo(1);
        // 重放不写第二条 CREATE 日志
        assertThat(purchaseQueryService.orderLogs(first.getId()))
                .extracting(PurchaseOperationLogVO::getOperationType)
                .containsExactly("CREATE");

        // ---- scope 拼操作者（A-D14）：换操作者、同 key、同内容 → 必须各自独立执行 ----
        Long skuB = newOnShelfSku("ID1b");
        Long supplierB = newPurchasableSupplier("ID1b", skuB);
        Long orderB = confirmedSalesOrder(customerId, skuB, "2.0000", "2.0000");
        PurchaseDemandEntity demandB = generateDemandFor(supplierB, orderB);

        RequestEmployee other = new RequestEmployee();
        other.setEmployeeId(2L);
        other.setActualName("W5 IT B");
        other.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        SmartRequestUtil.setRequestUser(other);

        PurchaseOrderVO byOther = purchaseOrderService.create(
                orderForm(supplierB, seedWarehouseId(), skuB, "2.0000", "6.2000", allocation(demandB, "2.0000")),
                key);

        assertThat(byOther.getId()).isNotEqualTo(first.getId());
        assertThat(countActiveOrders(supplierB)).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // 2. 同键异内容
    // ------------------------------------------------------------------

    @Test
    @DisplayName("同键异内容 → 40990（拒绝，而不是按首次结果返回）")
    void sameKeyDifferentRequestRejected() {
        Long skuId = newOnShelfSku("ID2");
        Long supplierId = newPurchasableSupplier("ID2", skuId);
        Long customerId = newCustomer();
        Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
        PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);

        String key = prefix + ":ID2:key";
        PurchaseOrderVO first = purchaseOrderService.create(
                orderForm(supplierId, seedWarehouseId(), skuId, "3.0000", "6.2000", allocation(demand, "3.0000")),
                key);

        // 同键、不同数量 → 内容哈希不同
        PurchaseOrderAddForm different = orderForm(supplierId, seedWarehouseId(), skuId, "2.0000", "6.2000",
                allocation(reloadDemand(demand.getId()), "2.0000"));
        expectCode(() -> purchaseOrderService.create(different, key), 40990);

        // 首次结果与单据数量都没被这次失败影响
        assertThat(countActiveOrders(supplierId)).isEqualTo(1);
        assertThat(reloadOrder(first.getId()).getTotalAmount()).isEqualByComparingTo("18.6000");
    }

    // ------------------------------------------------------------------
    // 3. 键本身非法
    // ------------------------------------------------------------------

    @Test
    @DisplayName("幂等键：缺失 → 40084；超过 200 字符 → 40085")
    void blankAndOverlongKeyRejected() {
        Long skuId = newOnShelfSku("ID3");
        Long supplierId = newPurchasableSupplier("ID3", skuId);

        PurchaseOrderAddForm blank = orderForm(supplierId, seedWarehouseId(), skuId, "1.0000", "1.0000");
        expectCode(() -> purchaseOrderService.create(blank, null), 40084);
        expectCode(() -> purchaseOrderService.create(blank, "   "), 40084);

        PurchaseOrderAddForm overlong = orderForm(supplierId, seedWarehouseId(), skuId, "1.0000", "1.0000");
        expectCode(() -> purchaseOrderService.create(overlong, "k".repeat(201)), 40085);

        // 校验发生在任何写入之前
        assertThat(countActiveOrders(supplierId)).isZero();
    }

    // ------------------------------------------------------------------
    // 4. 状态迁移的重放
    // ------------------------------------------------------------------

    @Test
    @DisplayName("submit 重放：状态只推进一次，只留一条 SUBMIT 日志")
    void submitReplayDoesNotAdvanceTwice() {
        Long skuId = newOnShelfSku("ID4");
        Long supplierId = newPurchasableSupplier("ID4", skuId);
        Long customerId = newCustomer();
        Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
        PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);

        PurchaseOrderVO order = createDraftOrder("ID4", supplierId, skuId, "3.0000", "6.2000",
                allocation(demand, "3.0000"));

        // submit 的 scope 是 `PURCHASE_ORDER_SUBMIT:<id>`，因此幂等键只在同一张单内生效
        PurchaseOrderVersionForm submit = new PurchaseOrderVersionForm();
        submit.setId(order.getId());
        submit.setVersion(order.getVersion());
        String key = prefix + ":ID4:submit";

        PurchaseOrderVO first = purchaseOrderService.submit(submit, key);
        PurchaseOrderVO replayed = purchaseOrderService.submit(submit, key);

        assertThat(first.getStatus()).isEqualTo("SUBMITTED");
        assertThat(replayed.getStatus()).isEqualTo("SUBMITTED");
        assertThat(replayed.getSubmittedAt()).isEqualTo(first.getSubmittedAt());
        assertThat(replayed.getVersion()).isEqualTo(first.getVersion());

        // 重放不产生第二条 SUBMIT 日志。DESC → 最新在最前
        List<PurchaseOperationLogVO> logs = purchaseQueryService.orderLogs(order.getId());
        assertThat(logs).extracting(PurchaseOperationLogVO::getOperationType)
                .containsExactly("SUBMIT", "CREATE");

        // 另一张单用同一个 key 不受影响（scope 里带单 id）
        PurchaseOrderVO second = createDraftOrder("ID4b", supplierId, skuId, "1.0000", "6.2000");
        PurchaseOrderVersionForm otherSubmit = new PurchaseOrderVersionForm();
        otherSubmit.setId(second.getId());
        otherSubmit.setVersion(second.getVersion());
        assertThat(purchaseOrderService.submit(otherSubmit, key).getStatus()).isEqualTo("SUBMITTED");
    }
}
