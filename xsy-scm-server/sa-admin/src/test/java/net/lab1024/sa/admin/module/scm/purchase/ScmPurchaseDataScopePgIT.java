package net.lab1024.sa.admin.module.scm.purchase;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;

import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeService;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryLossGainAddForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryLossGainAuditForm;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryLossGainService;
import net.lab1024.sa.admin.module.scm.purchase.constant.ScmReceiptModeEnum;
import net.lab1024.sa.admin.module.scm.purchase.controller.PurchaseOrderController;
import net.lab1024.sa.admin.module.scm.purchase.domain.entity.PurchaseDemandEntity;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseDemandAllocateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseDemandGenerateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseDemandQueryForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseDemandSummaryPreviewForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderAddForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderDeleteForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderQueryForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderReassignForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderUpdateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseOrderVersionForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptCreateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptItemWorkbenchQueryForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptPutawayForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptQueryForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseDemandSummaryVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseDemandVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptItemWorkbenchVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptVO;
import net.lab1024.sa.admin.module.scm.purchase.service.PurchaseDemandService;
import net.lab1024.sa.admin.module.scm.supplier.domain.form.SupplierQueryForm;
import net.lab1024.sa.admin.module.scm.supplier.domain.vo.SupplierVO;
import net.lab1024.sa.admin.module.scm.supplier.service.SupplierQueryService;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.code.UserErrorCode;
import net.lab1024.sa.base.common.domain.PageParam;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.exception.BusinessException;
import net.lab1024.sa.base.common.util.SmartRequestUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

/**
 * P0-F 采购域的行级数据范围、写入侧归属收口，以及报损报溢的「禁止自建自审」。
 *
 * <p>被测口径来自 {@code docs/decisions.md}「P0 基线收口裁决」第 7、8 条：
 * 普通采购员默认只看 {@code purchase_order.purchaser_id} / {@code purchase_demand.purchaser_id}
 * 等于本人的行；{@code purchaser_id IS NULL} 是「未分配」，普通采购员不可见、持全量范围者可见；
 * <b>新建时归属必须由服务端强制为当前员工</b> —— 没有这条写入侧收口，行级范围只是「藏起来」，
 * 谁都能先把单据建到别人名下再去读；供应商主档按采购团队<b>共享读取</b>，
 * 不为数据范围给 {@code supplier} 加归属列。第 8 条的自建自审禁令一并在这里取证：
 * 库存侧只有报损报溢同时存在「录单 + 审批」两个动作。
 *
 * <p><b>身份与权限怎么造</b>：范围解析读两处外部状态 —— 登录员工（{@code SmartRequestUtil}，
 * 覆盖基类的员工 1）与功能权限（{@code StpUtil.hasPermission}）。IT 线程里没有 Sa-Token 上下文，
 * 直接调 {@code StpUtil} 抛出的异常会被 {@code ScmDataScopeService#hasPermission} 吞成 false，
 * 因此按 {@code FileAccessGuardTest} 的既有做法用 {@code mockStatic(StpUtil.class)} 显式给权限；
 * 未点名的权限码取 Mockito 默认值 false，正好等于失败关闭。测试员工一律
 * {@code administratorFlag=false}（裁决第 5 条：超管通过不构成权限证据）。
 * {@link #as} 不可嵌套：同一线程只能注册一次静态 mock。
 *
 * <p><b>越权读的断言口径</b>：只断到异常类型与 {@link UserErrorCode#NO_PERMISSION} 的语义。
 * HTTP 信封里的 30005 由底座统一出码，映射归属底座，不在本域内改。
 *
 * <p><b>两条聚合读（裁决第 8 条收口的最后两个洞）</b>：缺口预览与按商品收货工作台不按采购员收窄，
 * 但 {@code warehouse_id} 必须落在授权仓内。它们返回的是<b>聚合数字</b>而不是明细行，因此断言的
 * 重点是「范围外的量根本不进聚合」：同一 SKU 在两个仓各有一张单时，只授权一个仓的人拿到的计划 /
 * 已收 / 余额只是他自己那个仓的数字，另一仓的量既不出现在行里、也不被并进合计；一个授权仓都没有时
 * 得到空分页，而不是 {@code 0}（0 会被读成「这些仓没收过货」）。
 * {@code demand.generate} 是命令路径，其取数口径刻意继续传 {@code ScmValueScope.all()}，
 * 所以这里同时取证「零仓库授权的调用者仍能生成需求」，防止把读侧收窄误接到写路径上。
 *
 * <p><b>单据夹具</b>：采购单与需求都走真实命令服务（{@code create} / {@code generate}），
 * 因为「归属由服务端强制」这条正是要被测的写入逻辑；直插只能证明读，证不了写。
 */
@DisplayName("P0-F 采购数据范围与归属收口（PG IT）")
class ScmPurchaseDataScopePgIT extends ScmW6PgITBase {

    /** 两个普通采购员：各自拥有采购单与需求，互不可见。 */
    private Long purchaserA;
    private Long purchaserB;

    @Autowired
    private SupplierQueryService supplierQueryService;

    @Autowired
    private InventoryLossGainService lossGainService;

    @BeforeEach
    void setUpPurchasers() {
        purchaserA = newEmployee("PA");
        purchaserB = newEmployee("PB");
        // 库存侧的仓库授权行由并行的 P0-F 子任务收口，这里先把两个测试员工授权给种子仓库，
        // 免得同一用例的结果取决于两条工作流的落地顺序。
        grantWarehouseScope(purchaserA);
        grantWarehouseScope(purchaserB);
    }

    // ==================== 1. 采购单列表与详情 ====================

    @Test
    @DisplayName("采购单列表：无全量范围时只返回 purchaser_id = 本人的行")
    void orderListIsNarrowedToOwnPurchaser() {
        loginAs(purchaserA);
        Purchasable goods = purchasable("OL");
        Long orderOfA = createOrder(goods, "OL-A", purchaserA, purchaserA, Set.of());
        Long orderOfB = createOrder(goods, "OL-B", purchaserB, purchaserB, Set.of());

        assertThat(as(purchaserA, Set.of(), () -> orderIds(orderQuery(null))))
                .contains(orderOfA).doesNotContain(orderOfB);
        assertThat(as(purchaserB, Set.of(), () -> orderIds(orderQuery(null))))
                               .contains(orderOfB).doesNotContain(orderOfA);
        // 收窄是整行的事：全量范围者两单都看得到
        assertThat(as(purchaserA, Set.of(ScmDataScopeService.PURCHASE_ALL_PERM), () -> orderIds(orderQuery(null))))
                .contains(orderOfA, orderOfB);
    }

    @Test
    @DisplayName("未分配采购单：普通采购员连详情都读不到，持全量范围者可读")
    void unassignedOrderVisibleOnlyToAllScope() {
        loginAs(purchaserA);
        Purchasable goods = purchasable("UN");
        Long orderId = createOrder(goods, "UN-A", purchaserA, purchaserA, Set.of());

        // 收回归属只能走改派端点；置空之后连原负责人自己也看不到（他只有「本人」范围）
        as(purchaserA, Set.of(ScmDataScopeService.PURCHASE_ASSIGN_PERM), () -> purchaseOrderService
                .reassign(reassignForm(orderId, null, orderVersion(orderId))));
        assertThat(purchaserIdOf(orderId)).isNull();

        assertThat(as(purchaserA, Set.of(), () -> orderIds(orderQuery(null)))).doesNotContain(orderId);
        assertThat(as(purchaserA, Set.of(ScmDataScopeService.PURCHASE_ALL_PERM), () -> orderIds(orderQuery(null))))
                .contains(orderId);
        assertNoPermission(() -> as(purchaserA, Set.of(), () -> purchaseQueryService.orderDetail(orderId)));
        assertThat(as(purchaserB, Set.of(ScmDataScopeService.PURCHASE_ALL_PERM),
                () -> purchaseQueryService.orderDetail(orderId).getId())).isEqualTo(orderId);
    }

    @Test
    @DisplayName("详情与行/日志子读同一口径：别人的单是数据范围越权，不存在仍是 40481")
    void orderDetailAndSubReadsDenyOutOfScope() {
        loginAs(purchaserA);
        Purchasable goods = purchasable("OD");
        Long orderOfA = createOrder(goods, "OD-A", purchaserA, purchaserA, Set.of());

        assertThat(as(purchaserA, Set.of(), () -> purchaseQueryService.orderDetail(orderOfA).getId()))
                .isEqualTo(orderOfA);
        assertThat(as(purchaserA, Set.of(), () -> purchaseQueryService.orderItems(orderOfA))).hasSize(1);
        assertThat(as(purchaserA, Set.of(), () -> purchaseQueryService.orderLogs(orderOfA))).isNotEmpty();

        assertNoPermission(() -> as(purchaserB, Set.of(), () -> purchaseQueryService.orderDetail(orderOfA)));
        // 明细与日志是同一张单的另一个入口：只挡详情、放行子读等于把价格与数量照样端出去
        assertNoPermission(() -> as(purchaserB, Set.of(), () -> purchaseQueryService.orderItems(orderOfA)));
        assertNoPermission(() -> as(purchaserB, Set.of(), () -> purchaseQueryService.orderLogs(orderOfA)));

        // 两条边界不得混用：上面的 assertNoPermission 走的是 ScmDataScopeException（行存在、只是不在
        // 调用者范围内，出 30005 信封），而这一例走的是「主键取不到行」的 PURCHASE_ORDER_NOT_FOUND。
        // 越权判定不得倒过来把「不存在」也报成无权限：那会让探测 id 与探测权限混成一种结果
        expectCode(() -> as(purchaserB, Set.of(), () -> purchaseQueryService.orderDetail(-1L)), 40481);
        assertThat(as(purchaserB, Set.of(ScmDataScopeService.PURCHASE_ALL_PERM),
                () -> purchaseQueryService.orderDetail(orderOfA).getId())).isEqualTo(orderOfA);
    }

    @Test
    @DisplayName("purchaserId 筛选只能收窄不能扩大")
    void purchaserFilterNarrowsOnly() {
        loginAs(purchaserA);
        Purchasable goods = purchasable("NF");
        Long orderOfA = createOrder(goods, "NF-A", purchaserA, purchaserA, Set.of());
        Long orderOfB = createOrder(goods, "NF-B", purchaserB, purchaserB, Set.of());

        // 普通采购员按别人的采购员筛选：范围谓词仍然 AND 在上面，结果必须为空
        assertThat(as(purchaserA, Set.of(), () -> orderIds(orderQuery(purchaserB)))).isEmpty();
        // 同一筛选交给全量范围者：收窄生效，只剩乙的单
        assertThat(as(purchaserA, Set.of(ScmDataScopeService.PURCHASE_ALL_PERM),
                () -> orderIds(orderQuery(purchaserB)))).containsExactly(orderOfB);
        assertThat(as(purchaserA, Set.of(ScmDataScopeService.PURCHASE_ALL_PERM),
                () -> orderIds(orderQuery(purchaserA)))).containsExactly(orderOfA);
    }

    // ==================== 2. 采购需求 ====================

    @Test
    @DisplayName("需求列表按 purchaser_id 收窄；生成时表单里的「默认采购员」对普通采购员不成立")
    void demandListFollowsPurchaserAndGenerateForcesCaller() {
        loginAs(purchaserA);
        Fixture first = fixture("DM", "4.0000", "4.0000");
        PurchaseDemandEntity demandOfA = as(purchaserA, Set.of(),
                () -> generateDemand(first.supplierId(), first.salesOrderId(), purchaserB));
        // 第二套来源数据改由乙准备：需求归属只认「谁调的 generate」，与上游客户/订单无关
        loginAs(purchaserB);
        Fixture second = fixture("DN", "6.0000", "6.0000");
        PurchaseDemandEntity demandOfB = as(purchaserB, Set.of(),
                () -> generateDemand(second.supplierId(), second.salesOrderId(), null));

        // 表单里填的是别人的 id，落库的仍是调用者本人（伪造归属在写入侧就被挡掉）
        assertThat(demandPurchaserId(demandOfA.getId())).isEqualTo(purchaserA);
        assertThat(demandPurchaserId(demandOfB.getId())).isEqualTo(purchaserB);

        assertThat(as(purchaserA, Set.of(), () -> demandIds(demandQuery(first.skuId()))))
                .containsExactly(demandOfA.getId());
        assertThat(as(purchaserB, Set.of(), () -> demandIds(demandQuery(second.skuId()))))
                .containsExactly(demandOfB.getId());
        assertThat(as(purchaserA, Set.of(), () -> demandIds(demandQuery(second.skuId())))).isEmpty();

        assertNoPermission(() -> as(purchaserA, Set.of(),
                () -> purchaseQueryService.demandDetail(demandOfB.getId())));
        assertThat(as(purchaserA, Set.of(ScmDataScopeService.PURCHASE_ALL_PERM),
                () -> purchaseQueryService.demandDetail(demandOfB.getId()).getId())).isEqualTo(demandOfB.getId());
        // 需求详情按「先取行、再判归属」两段式收口：这一例命中的是前一段（主键无行 →
        // PURCHASE_DEMAND_NOT_FOUND），上一条 assertNoPermission 命中的是后一段（ScmDataScopeException）
        expectCode(() -> as(purchaserA, Set.of(), () -> purchaseQueryService.demandDetail(-1L)), 40480);
    }

    // ==================== 3. 收货单（表上没有采购员列） ====================

    @Test
    @DisplayName("收货单按父采购单的采购员继承：列表与详情、明细同一口径")
    void receiptFollowsParentOrderPurchaser() {
        loginAs(purchaserA);
        Purchasable goods = purchasable("RC");
        Long orderOfA = createSubmittedOrder(goods, "RC-A", purchaserA);
        Long orderOfB = createSubmittedOrder(goods, "RC-B", purchaserB);
        Long receiptOfA = as(purchaserA, Set.of(), () -> createReceipt(orderOfA).getId());
        Long receiptOfB = as(purchaserB, Set.of(), () -> createReceipt(orderOfB).getId());

        // 指名查别人单下的收货行：不是「少几条」，而是一条都不给（EXISTS 半连落在父单上）
        assertThat(as(purchaserA, Set.of(), () -> receiptIds(receiptQuery(orderOfB)))).isEmpty();
        assertThat(as(purchaserA, Set.of(), () -> receiptIds(receiptQuery(orderOfA))))
                .containsExactly(receiptOfA);
        assertThat(as(purchaserB, Set.of(), () -> receiptIds(receiptQuery(orderOfB))))
                .containsExactly(receiptOfB);

        assertThat(as(purchaserA, Set.of(), () -> purchaseQueryService.receiptDetail(receiptOfA).getId()))
                .isEqualTo(receiptOfA);
        assertNoPermission(() -> as(purchaserA, Set.of(), () -> purchaseQueryService.receiptDetail(receiptOfB)));
        assertNoPermission(() -> as(purchaserA, Set.of(), () -> purchaseQueryService.receiptItems(receiptOfB)));
        // 收货单没有「父单越权 → 报不存在」这条回落：越权仍由上面两例的 ScmDataScopeException 承担，
        // 这里只是 id 真取不到行的 PURCHASE_RECEIPT_NOT_FOUND
        expectCode(() -> as(purchaserA, Set.of(), () -> purchaseQueryService.receiptDetail(-1L)), 40483);
        assertThat(as(purchaserB, Set.of(ScmDataScopeService.PURCHASE_ALL_PERM),
                () -> purchaseQueryService.receiptDetail(receiptOfA).getId())).isEqualTo(receiptOfA);
    }

    // ==================== 4. 写入侧归属收口 ====================

    @Test
    @DisplayName("无分配权新建采购单：purchaser_id 强制为本人，伪造的 purchaserId 一律忽略")
    void createForcesPurchaserToCallerAndIgnoresSpoofedValue() {
        loginAs(purchaserA);
        Purchasable goods = purchasable("WF");
        Long orderId = createOrder(goods, "WF-A", purchaserA, purchaserB, Set.of());

        assertThat(purchaserIdOf(orderId)).isEqualTo(purchaserA);
        assertThat(as(purchaserA, Set.of(), () -> orderIds(orderQuery(null)))).contains(orderId);
        assertThat(as(purchaserB, Set.of(), () -> orderIds(orderQuery(null)))).doesNotContain(orderId);
    }

    @Test
    @DisplayName("有分配权可指定别人：单据落在对方名下，且命令返回值仍可用；分配权不隐含可见范围")
    void assignRightAllowsCreatingForAnotherPurchaser() {
        loginAs(purchaserA);
        Purchasable goods = purchasable("WS");
        PurchaseOrderVO created = as(purchaserA, Set.of(ScmDataScopeService.PURCHASE_ASSIGN_PERM), () -> {
            PurchaseOrderAddForm form = orderForm(goods.supplierId(), seedWarehouseId(), goods.skuId(),
                    "4.0000", "6.2000");
            form.setPurchaserId(purchaserB);
            // 替别人建的单，命令侧必须还能把自己刚写的单据投影回来（读取范围只约束查询端点）
            return purchaseOrderService.create(form, prefix + ":WS-A:po");
        });

        assertThat(created.getPurchaserId()).isEqualTo(purchaserB);
        assertThat(purchaserIdOf(created.getId())).isEqualTo(purchaserB);
        assertThat(as(purchaserB, Set.of(), () -> orderIds(orderQuery(null)))).contains(created.getId());
        // V55 刻意把 assign 与 scope:all 分成两点：只有分配权的人看不到别人名下的单
        assertThat(as(purchaserA, Set.of(ScmDataScopeService.PURCHASE_ASSIGN_PERM),
                () -> orderIds(orderQuery(null)))).doesNotContain(created.getId());
    }

    @Test
    @DisplayName("编辑不得改派：表单里的 purchaserId 对任何角色都只是回显值")
    void updateNeverReassignsPurchaser() {
        loginAs(purchaserA);
        Purchasable goods = purchasable("UE");
        Long orderId = createOrder(goods, "UE-A", purchaserA, purchaserA, Set.of());

        as(purchaserA, Set.of(ScmDataScopeService.PURCHASE_ASSIGN_PERM), () -> {
            PurchaseOrderUpdateForm update = editForm(orderId, "5.0000", "6.2000");
            update.setPurchaserId(purchaserB);
            purchaseOrderService.update(update);
            return null;
        });

        assertThat(purchaserIdOf(orderId)).isEqualTo(purchaserA);
        assertThat(as(purchaserB, Set.of(), () -> orderIds(orderQuery(null)))).doesNotContain(orderId);
    }

    @Test
    @DisplayName("改派走独立端点：权限码已种入菜单与角色，带分配权可移动归属，版本过期 40921")
    void reassignEndpointIsTheOnlyMovePath() throws Exception {
        Method endpoint = PurchaseOrderController.class.getDeclaredMethod("reassign",
                PurchaseOrderReassignForm.class);
        SaCheckPermission permission = endpoint.getAnnotation(SaCheckPermission.class);
        assertThat(permission).as("改派端点必须自带权限注解，不能只靠 Service 判断").isNotNull();
        assertThat(permission.value()).containsExactly(ScmDataScopeService.PURCHASE_ASSIGN_PERM);

        // 权限点确实存在且只种一次，并落在「主管有、采购员没有」的角色上（V55 + V56）
        assertThat(jdbc.queryForObject("SELECT count(*) FROM t_menu WHERE api_perms = ?", Integer.class,
                ScmDataScopeService.PURCHASE_ASSIGN_PERM)).isEqualTo(1);
        assertThat(roleHoldsPermission("SCM_PURCHASER", ScmDataScopeService.PURCHASE_ASSIGN_PERM)).isFalse();
        assertThat(roleHoldsPermission("SCM_PURCHASER_LEAD", ScmDataScopeService.PURCHASE_ASSIGN_PERM)).isTrue();
        assertThat(roleHoldsPermission("SCM_PURCHASER", ScmDataScopeService.PURCHASE_ALL_PERM)).isFalse();
        assertThat(roleHoldsPermission("SCM_PURCHASER_LEAD", ScmDataScopeService.PURCHASE_ALL_PERM)).isTrue();

        loginAs(purchaserA);
        Purchasable goods = purchasable("RA");
        Long orderId = createOrder(goods, "RA-A", purchaserA, purchaserA, Set.of());

        // 陈旧改派必须撞 40921，而不是覆盖别人的编辑
        expectCode(() -> as(purchaserA, Set.of(ScmDataScopeService.PURCHASE_ASSIGN_PERM),
                () -> purchaseOrderService.reassign(reassignForm(orderId, purchaserB, 99))), 40921);
        assertThat(purchaserIdOf(orderId)).isEqualTo(purchaserA);

        as(purchaserA, Set.of(ScmDataScopeService.PURCHASE_ASSIGN_PERM),
                () -> purchaseOrderService.reassign(reassignForm(orderId, purchaserB, orderVersion(orderId))));

        assertThat(purchaserIdOf(orderId)).isEqualTo(purchaserB);
        assertThat(as(purchaserB, Set.of(), () -> orderIds(orderQuery(null)))).contains(orderId);
        assertThat(as(purchaserA, Set.of(), () -> orderIds(orderQuery(null)))).doesNotContain(orderId);
        // 改派必须留审计：归属同时是数据范围依据，换了谁要能回答。
        // JSONB 回读的整数标量不保证是 Long，因此按字符串比，不拿 Map.equals 赌类型。
        assertThat(as(purchaserB, Set.of(), () -> purchaseQueryService.orderLogs(orderId)))
                .anySatisfy(row -> assertThat(String.valueOf(row.getAfterData().get("purchaserId")))
                        .isEqualTo(String.valueOf(purchaserB)));
    }

    // ==================== 5. 共享主档与失败关闭 ====================

    @Test
    @DisplayName("供应商主档仍按采购团队共享读取：不为数据范围给 supplier 加归属")
    void supplierReadsStayShared() {
        loginAs(purchaserA);
        Long supplierOfA = purchasable("SH").supplierId();
        loginAs(purchaserB);
        Long supplierOfB = purchasable("SH2").supplierId();

        List<Long> visible = as(purchaserA, Set.of(), () -> supplierIds(supplierQueryForm()));
        assertThat(visible).contains(supplierOfA, supplierOfB);
        // 别人的供应商照常能读：采购团队共享是裁决第 7 条明确保留的例外，不是漏收口的证据
        assertThat(as(purchaserA, Set.of(), () -> supplierQueryService.detail(supplierOfB).getSupplierId()))
                .isEqualTo(supplierOfB);
    }

    @Test
    @DisplayName("没有登录员工上下文：三个列表与两条聚合读都返回空分页而不是全量")
    void missingIdentityFailsClosed() {
        loginAs(purchaserA);
        Purchasable goods = purchasable("FC");
        createOrder(goods, "FC-A", purchaserA, purchaserA, Set.of());

        SmartRequestUtil.remove();
        PageResult<PurchaseOrderVO> orders = purchaseQueryService.orderQuery(newForm(new PurchaseOrderQueryForm()));
        assertThat(orders.getList()).isEmpty();
        assertThat(orders.getTotal()).isZero();
        assertThat(orders.getEmptyFlag()).as("空分页形状与正常分页一致，前端不必另写分支").isTrue();

        assertThat(purchaseQueryService.receiptQuery(newForm(new PurchaseReceiptQueryForm())).getList()).isEmpty();
        assertThat(purchaseQueryService.demandQuery(newForm(new PurchaseDemandQueryForm())).getList()).isEmpty();
        // 两条聚合读走的是同一条失败关闭取向：无身份 → 仓库范围为 none → 空分页，而不是全量
        assertThat(purchaseQueryService.summaryPreview(
                summaryForm(seedWarehouseId(), OffsetDateTime.now().minusHours(1))).getList()).isEmpty();
        assertThat(purchaseQueryService.receiptItemWorkbench(workbenchForm(null)).getList()).isEmpty();
    }

    // ==================== 6. 报损报溢禁止自建自审 ====================

    @Test
    @DisplayName("报损报溢：录单人自己通过与自己驳回都被拒（41065），换一个人审批照常通过并扣库存")
    void lossGainSelfApprovalRejectedButAnotherApproverSucceeds() {
        loginAs(purchaserA);
        Long sku = newSkuOfType("SG", "NON_STANDARD", "ON_SHELF");
        W6Fixture stocked = inboundFixture("SG", sku, "10.0000");
        confirmReceipt(stocked.receipt().getId(), "10.0000");

        Long documentId = lossGainService.create(lossGainForm(sku, "3.0000"));
        // 录单人既是创建者也是当前登录人：两个审批动作都必须被挡住，否则「待审核」形同虚设
        expectCode(() -> lossGainService.approve(documentId, auditForm(documentId, null)), 41065);
        expectCode(() -> lossGainService.reject(documentId, auditForm(documentId, "自己驳回自己")), 41065);
        assertThat(lossGainStatus(documentId)).isEqualTo("PENDING");
        assertThat(balanceRow(seedWarehouseId(), sku).getQuantity()).isEqualByComparingTo("10.0000");

        // 换一个人审批即通过，库存按单据数量扣减；auditor 落的是「端类型:员工号」串
        loginAs(purchaserB);
        lossGainService.approve(documentId, auditForm(documentId, "已核对变质照片"));

        assertThat(lossGainStatus(documentId)).isEqualTo("COMPLETED");
        assertThat(balanceRow(seedWarehouseId(), sku).getQuantity()).isEqualByComparingTo("7.0000");
        assertThat(lossGainAuditor(documentId)).endsWith(":" + purchaserB);
    }

    // ==================== 7. 两条聚合读按仓库收窄 ====================

    @Test
    @DisplayName("缺口预览：只给授权仓的数字；未授权仓整页为空，而不是余额为 0 的 NO_BALANCE 行")
    void summaryPreviewIsNarrowedToAuthorizedWarehouse() {
        Long otherWarehouse = newWarehouse("PSX");
        Fixture goods = fixture("PSI", "5.0000", "3.0000");
        // 同一 SKU、同一批订单量，两个仓的余额刻意不同：种子仓够货（差额 0），另一仓只有 1.0（差额 2.0）
        seedBalance(seedWarehouseId(), goods.skuId(), "10.0000");
        seedBalance(otherWarehouse, goods.skuId(), "1.0000");
        Long seedOnly = newWarehouseScopedEmployee("PSA", seedWarehouseId());
        Long otherOnly = newWarehouseScopedEmployee("PSB", otherWarehouse);

        PurchaseDemandSummaryVO seedRow = as(seedOnly, Set.of(),
                () -> summaryRow(goods.skuId(), seedWarehouseId(), goods.confirmedAt()));
        assertThat(seedRow.getOnHandQuantity()).isEqualByComparingTo("10.0000");
        assertThat(seedRow.getStockComparisonGap()).isEqualByComparingTo("0.0000");

        // 同一窗口换成未授权的那个仓：一行都不给。只把余额左连收窄是错的——那样订单需求量
        // （3.0000）照样成行送出去，只是状态变成 NO_BALANCE，等于把「无权看这个仓」说成「这个仓没货」
        assertThat(as(seedOnly, Set.of(), () -> summaryRows(otherWarehouse, goods.confirmedAt())))
                .as("未授权仓不得返回任何预览行").isEmpty();

        // 反向：只有另一个仓授权的人读到的是那个仓的余额与差额
        PurchaseDemandSummaryVO otherRow = as(otherOnly, Set.of(),
                () -> summaryRow(goods.skuId(), otherWarehouse, goods.confirmedAt()));
        assertThat(otherRow.getOnHandQuantity()).isEqualByComparingTo("1.0000");
        assertThat(otherRow.getStockComparisonGap()).isEqualByComparingTo("2.0000");

        // 持全量仓库范围者两个仓都能读，数字与各自仓一致（收窄没有改变任何口径）
        assertThat(as(seedOnly, Set.of(ScmDataScopeService.WAREHOUSE_ALL_PERM),
                () -> summaryRow(goods.skuId(), otherWarehouse, goods.confirmedAt())
                        .getStockComparisonGap())).isEqualByComparingTo("2.0000");
    }

    @Test
    @DisplayName("收货工作台：计划 / 已收只按授权仓重算，未授权仓的采购单整单不进聚合")
    void workbenchAggregatesOnlyAuthorizedWarehouses() {
        Long otherWarehouse = newWarehouse("WBA");
        Long skuId = newOnShelfSku("WBS");
        Purchasable goods = new Purchasable(skuId, newPurchasableSupplier("WBS", skuId));
        Long seedOnly = newWarehouseScopedEmployee("WB1", seedWarehouseId());
        Long otherOnly = newWarehouseScopedEmployee("WB2", otherWarehouse);
        // 同一 SKU 在两个仓各一张已提交采购单：种子仓 4.0000 未收，另一仓 6.0000 已收 2.0000
        createSubmittedOrderAt(goods, "WBC", seedOnly, seedWarehouseId(), "4.0000");
        Long otherOrder = createSubmittedOrderAt(goods, "WBD", otherOnly, otherWarehouse, "6.0000");
        as(otherOnly, Set.of(), () -> confirmReceipt(createReceipt(otherOrder).getId(), "2.0000"));

        PurchaseReceiptItemWorkbenchVO seedRow = as(seedOnly, Set.of(), () -> workbenchRow(skuId, null));
        assertThat(seedRow.getOrderCount()).as("只数自己那个仓的那一张单").isEqualTo(1L);
        assertThat(seedRow.getLineCount()).isEqualTo(1L);
        assertThat(seedRow.getPlannedQuantity()).isEqualByComparingTo("4.0000");
        assertThat(seedRow.getReceivedQuantity()).as("另一仓的 2.0000 已收量不得并进来").isEqualByComparingTo("0.0000");
        assertThat(seedRow.getPendingQuantity()).isEqualByComparingTo("4.0000");

        // 表单指定未授权仓：用户筛选只能缩小范围，不能借它读另一个仓
        assertThat(as(seedOnly, Set.of(), () -> workbenchRows(otherWarehouse))).isEmpty();
        // 同一筛选交给另一仓的授权者：读到的正是那个仓的量
        PurchaseReceiptItemWorkbenchVO otherRow =
                as(otherOnly, Set.of(), () -> workbenchRow(skuId, otherWarehouse));
        assertThat(otherRow.getPlannedQuantity()).isEqualByComparingTo("6.0000");
        assertThat(otherRow.getReceivedQuantity()).isEqualByComparingTo("2.0000");

        // 全量范围者两仓合并：4 + 6 = 10 计划、2 已收、8 欠收
        PurchaseReceiptItemWorkbenchVO allRow = as(seedOnly, Set.of(ScmDataScopeService.WAREHOUSE_ALL_PERM),
                () -> workbenchRow(skuId, null));
        assertThat(allRow.getOrderCount()).isEqualTo(2L);
        assertThat(allRow.getPlannedQuantity()).isEqualByComparingTo("10.0000");
        assertThat(allRow.getReceivedQuantity()).isEqualByComparingTo("2.0000");
        assertThat(allRow.getPendingQuantity()).isEqualByComparingTo("8.0000");
    }

    @Test
    @DisplayName("两个聚合读：没有任何仓库授权时给空分页，不给 0、也不报错")
    void aggregateReadsWithoutWarehouseGrantAreEmptyNotZeros() {
        Fixture goods = fixture("PSN", "5.0000", "3.0000");
        seedBalance(seedWarehouseId(), goods.skuId(), "1.0000");
        Long noGrant = newEmployee("PSN0");
        Purchasable receivable = purchasable("PSNW");
        loginAs(purchaserA);
        createSubmittedOrderAt(receivable, "PSNW", purchaserA, seedWarehouseId(), "4.0000");

        // 事实都在库里（下面用全量范围读得到），空分页只能是范围的结果，不是「没有数据」
        PageResult<PurchaseDemandSummaryVO> preview = as(noGrant, Set.of(),
                () -> purchaseQueryService.summaryPreview(summaryForm(seedWarehouseId(), goods.confirmedAt())));
        assertThat(preview.getList()).isEmpty();
        assertThat(preview.getTotal()).isZero();
        assertThat(preview.getEmptyFlag()).as("空分页形状与正常分页一致").isTrue();
        assertThat(as(noGrant, Set.of(ScmDataScopeService.WAREHOUSE_ALL_PERM),
                () -> summaryRow(goods.skuId(), seedWarehouseId(), goods.confirmedAt())
                        .getStockComparisonGap())).isEqualByComparingTo("2.0000");

        PageResult<PurchaseReceiptItemWorkbenchVO> workbench = as(noGrant, Set.of(),
                () -> purchaseQueryService.receiptItemWorkbench(workbenchForm(null)));
        assertThat(workbench.getList()).isEmpty();
        assertThat(workbench.getTotal()).isZero();
        assertThat(workbench.getEmptyFlag()).isTrue();
        assertThat(as(noGrant, Set.of(ScmDataScopeService.WAREHOUSE_ALL_PERM),
                () -> workbenchRows(null))).isNotEmpty();
    }

    // ==================== 8. 命令取数不被读侧范围接管 ====================

    @Test
    @DisplayName("generate：命令路径仍取全量来源行，零仓库授权的调用者照样按表单指定仓生成需求")
    void generateIgnoresCallerWarehouseScope() {
        Fixture goods = fixture("GEN", "4.0000", "4.0000");
        Long noGrant = newEmployee("GEN0");

        PurchaseDemandService.GenerateResult result = as(noGrant, Set.of(), () -> {
            PurchaseDemandGenerateForm form = new PurchaseDemandGenerateForm();
            form.setStartAt(goods.confirmedAt());
            form.setEndAt(goods.confirmedAt().plusSeconds(1));
            form.setWarehouseId(seedWarehouseId());
            form.setSupplierId(goods.supplierId());
            return purchaseDemandService.generate(form, prefix + ":gen:" + goods.salesOrderId());
        });

        // 取数口径没有被新参数挡掉：来源行数 = 1、需求真落库
        assertThat(result.getSourceLineCount()).as("listSourceItems 仍按全量来源行取数").isEqualTo(1);
        assertThat(result.getCreatedCount()).isEqualTo(1);
        PurchaseDemandEntity demand = demandOfSourceItem(goods.salesOrderItemId());
        assertThat(demand.getWarehouseId()).isEqualTo(seedWarehouseId());
        assertThat(demandPurchaserId(demand.getId())).as("归属仍由服务端强制为调用者").isEqualTo(noGrant);
        // 写路径不收窄不等于读路径放行：同一个调用者读那个仓的聚合预览仍是空
        assertThat(as(noGrant, Set.of(), () -> summaryRows(seedWarehouseId(), goods.confirmedAt()))).isEmpty();
    }

    // ==================== 9. 收货确认的「采购 ∩ 仓库」交集 ====================

    /**
     * 裁决第 16 条：DIRECT 收货同时是采购动作与库存入账，因此两条边界取<b>交集</b>，
     * 不是二选一。下面三例分别把其中一条打通、另一条打断，再证明「补齐第二条就放行」——
     * 只有 OR 语义才会出现「一条成立即放行」的结果。
     */
    @Test
    @DisplayName("DIRECT 确认：单子归他但目标仓不归他 → 30005，补上仓库授权后同一笔确认照常入账")
    void directConfirmNeedsWarehouseAuthorizationOnTopOfOwnership() {
        Long otherWarehouse = newWarehouse("IXW");
        // 只在种子仓上有授权的人，在自己名下建了一张落在另一个仓的单：
        // 建单只是采购动作（仓是计划落点，此刻还没写库存），确认才要求目标仓的授权。
        Long caller = newWarehouseScopedEmployee("IX1", seedWarehouseId());
        Purchasable goods = purchasable("IXC");
        Long orderId = createSubmittedOrderAt(goods, "IXC", caller, otherWarehouse, "4.0000");
        Long receiptId = as(caller, Set.of(), () -> createReceipt(orderId).getId());

        assertNoPermission(() -> as(caller, Set.of(), () -> confirmReceipt(receiptId, "4.0000")));
        // 失败关闭必须真的什么都没写：状态仍 DRAFT、目标仓既无余额行也无 PURCHASE_IN 流水
        assertThat(receiptStatus(receiptId)).isEqualTo("DRAFT");
        assertThat(balanceRowCount(otherWarehouse, goods.skuId())).isZero();
        assertThat(movementCount(otherWarehouse, goods.skuId())).isZero();

        as(caller, Set.of(ScmDataScopeService.WAREHOUSE_ALL_PERM), () -> confirmReceipt(receiptId, "4.0000"));
        assertThat(receiptStatus(receiptId)).isEqualTo("CONFIRMED");
        assertThat(balanceRow(otherWarehouse, goods.skuId()).getQuantity()).isEqualByComparingTo("4.0000");
    }

    @Test
    @DisplayName("DIRECT 确认：仓库全授权也动不了别人的收货单；采购主管持两条边界才可代录")
    void confirmNeedsOwnershipEvenWithAllWarehouses() {
        Purchasable goods = purchasable("IXO");
        Long owner = newWarehouseScopedEmployee("IX2", seedWarehouseId());
        Long orderId = createSubmittedOrderAt(goods, "IXO", owner, seedWarehouseId(), "4.0000");
        Long receiptId = as(owner, Set.of(), () -> createReceipt(orderId).getId());
        Long lead = newWarehouseScopedEmployee("IX3", seedWarehouseId());

        assertNoPermission(() -> as(lead, Set.of(ScmDataScopeService.WAREHOUSE_ALL_PERM),
                () -> confirmReceipt(receiptId, "4.0000")));
        assertThat(receiptStatus(receiptId)).as("越权确认没有推进状态").isEqualTo("DRAFT");

        // 采购主管的全量采购范围 + 自己管的仓：这才是裁决里「代录」成立的形状
        as(lead, Set.of(ScmDataScopeService.WAREHOUSE_ALL_PERM, ScmDataScopeService.PURCHASE_ALL_PERM),
                () -> confirmReceipt(receiptId, "4.0000"));
        assertThat(receiptStatus(receiptId)).isEqualTo("CONFIRMED");
        assertThat(balanceRow(seedWarehouseId(), goods.skuId()).getQuantity()).isEqualByComparingTo("4.0000");
    }

    @Test
    @DisplayName("WAREHOUSE_CONFIRM：确认不收仓库边界（还没写库存），仓库判定落在上架那一步")
    void warehouseConfirmModeDefersTheWarehouseCheck() {
        Long otherWarehouse = newWarehouse("IXM");
        Long caller = newWarehouseScopedEmployee("IX4", seedWarehouseId());
        Purchasable goods = purchasable("IXM");
        Long orderId = createSubmittedOrderAt(goods, "IXM", caller, otherWarehouse, "4.0000");
        Long receiptId = as(caller, Set.of(), () -> {
            PurchaseReceiptCreateForm form = new PurchaseReceiptCreateForm();
            form.setPurchaseOrderId(orderId);
            form.setReceiptMode(ScmReceiptModeEnum.WAREHOUSE_CONFIRM.name());
            form.setRemark("交集 IT 二次入库单");
            return purchaseReceiptService.create(form, prefix + ":ixm:receipt").getId();
        });

        // 「收货确认 ≠ 库存入账」：这一步没有库存写入，因此不收仓库授权也能确认
        as(caller, Set.of(), () -> confirmReceipt(receiptId, "4.0000"));
        assertThat(receiptStatus(receiptId)).isEqualTo("CONFIRMED");
        assertThat(balanceRowCount(otherWarehouse, goods.skuId())).as("确认阶段不产生余额").isZero();
        assertThat(jdbc.queryForObject("SELECT putaway_status FROM purchase_receipt WHERE id = ?",
                String.class, receiptId)).isEqualTo("PENDING");

        assertNoPermission(() -> as(caller, Set.of(), () -> putaway(receiptId, "denied")));
        assertThat(balanceRowCount(otherWarehouse, goods.skuId())).as("被拒的上架没有写库存").isZero();

        as(caller, Set.of(ScmDataScopeService.WAREHOUSE_ALL_PERM), () -> putaway(receiptId, "granted"));
        assertThat(balanceRow(otherWarehouse, goods.skuId()).getQuantity()).isEqualByComparingTo("4.0000");
    }

    // ==================== 10. 采购写路径的归属边界 ====================

    @Test
    @DisplayName("采购单命令按归属收窄：别人名下的单编辑 / 提交都到不了，本人的照常")
    void orderCommandsFollowPurchaserScope() {
        Purchasable goods = purchasable("WC");
        Long owner = newWarehouseScopedEmployee("WC1", seedWarehouseId());
        Long other = newWarehouseScopedEmployee("WC2", seedWarehouseId());
        Long orderId = createOrder(goods, "WC-A", owner, owner, Set.of());
        // 编辑表单在越权上下文之外装配：editForm 内部要读一次 orderDetail，
        // 放进 as(other, ...) 里会先被那道读侧守卫拒掉，测不到 update 本身。
        PurchaseOrderUpdateForm edit = editForm(orderId, "5.0000", "6.2000");

        assertNoPermission(() -> as(other, Set.of(), () -> purchaseOrderService.update(edit)));
        assertNoPermission(() -> as(other, Set.of(),
                () -> purchaseOrderService.submit(orderVersionForm(orderId), prefix + ":wc:submit")));
        assertThat(orderVersion(orderId)).as("越权命令没有推进版本").isZero();
        assertThat(orderDeleted(orderId)).as("越权删除没有留下软删标记").isFalse();
        assertNoPermission(() -> as(other, Set.of(), () -> {
            purchaseOrderService.delete(deleteForm(orderId));
            return null;
        }));
        assertThat(orderDeleted(orderId)).isFalse();

        assertThat(as(owner, Set.of(),
                () -> purchaseOrderService.submit(orderVersionForm(orderId), prefix + ":wc:own")).getId())
                .isEqualTo(orderId);
    }

    @Test
    @DisplayName("收货单的备注编辑与删除同样继承父单归属；有归属的人照常可动")
    void receiptUpdateAndDeleteFollowParentOrderOwner() {
        Purchasable goods = purchasable("RD");
        Long owner = newWarehouseScopedEmployee("RD1", seedWarehouseId());
        Long other = newWarehouseScopedEmployee("RD2", seedWarehouseId());
        Long orderId = createSubmittedOrderAt(goods, "RD", owner, seedWarehouseId(), "4.0000");
        Long receiptId = as(owner, Set.of(), () -> createReceipt(orderId).getId());

        assertNoPermission(() -> as(other, Set.of(),
                () -> purchaseReceiptService.update(receiptRemarkForm(receiptId, receiptVersion(receiptId), "别人改的"))));
        assertNoPermission(() -> as(other, Set.of(), () -> {
            purchaseReceiptService.delete(receiptDeleteForm(receiptId));
            return null;
        }));
        assertThat(receiptDeleted(receiptId)).isFalse();

        as(owner, Set.of(), () -> purchaseReceiptService
                .update(receiptRemarkForm(receiptId, receiptVersion(receiptId), "本人改的")));
        assertThat(receiptRemark(receiptId)).isEqualTo("本人改的");
    }

    @Test
    @DisplayName("需求分配两头都要在范围内：用别人的采购单接自己的需求、或反之，都被拒且需求量不动")
    void demandAllocationRequiresBothSides() {
        Long owner = newWarehouseScopedEmployee("DA1", seedWarehouseId());
        loginAs(owner);
        Fixture first = fixture("DA", "4.0000", "4.0000");
        PurchaseDemandEntity demandOfOwner = as(owner, Set.of(),
                () -> generateDemand(first.supplierId(), first.salesOrderId(), owner));

        Long other = newWarehouseScopedEmployee("DA2", seedWarehouseId());
        Purchasable goods = new Purchasable(first.skuId(), first.supplierId());
        Long orderOfOther = createOrder(goods, "DA-O", other, other, Set.of());
        as(other, Set.of(), () -> purchaseOrderService.submit(orderVersionForm(orderOfOther),
                prefix + ":da:submit"));

        // 方向一：调用者拥有采购单，但需求在别人名下
        assertNoPermission(() -> as(other, Set.of(),
                () -> purchaseDemandService.allocate(allocateForm(demandOfOwner.getId(), orderOfOther),
                        prefix + ":da:alloc:one")));
        // 方向二：调用者拥有需求，但采购单在别人名下
        assertNoPermission(() -> as(owner, Set.of(),
                () -> purchaseDemandService.allocate(allocateForm(demandOfOwner.getId(), orderOfOther),
                        prefix + ":da:alloc:two")));
        assertThat(allocatedQuantity(demandOfOwner.getId()))
                .as("两次越权分配都没有把需求量并进去").isEqualByComparingTo("0");
    }

    // ==================== 夹具 ====================

    /**
     * 真实员工行：范围判定用的是 {@code purchaser_id} 指向的员工 id，因此按最小必要列插真行，
     * 靠测试事务回滚清理。{@code administrator_flag} 必须是 FALSE。
     */
    private Long newEmployee(String suffix) {
        String loginName = (prefix + "-" + suffix).toUpperCase(Locale.ROOT);
        jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                        + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                UUID.randomUUID().toString().replace("-", ""), loginName, "$argon2id$it-placeholder",
                "采购" + suffix);
        Long employeeId = jdbc.queryForObject(
                "SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
        assertThat(jdbc.queryForObject(
                "SELECT administrator_flag FROM t_employee WHERE employee_id = ?", Boolean.class, employeeId))
                .as("正式业务角色的验收账号禁止超管位（裁决第 5 条）").isFalse();
        return employeeId;
    }

    private void grantWarehouseScope(Long employeeId) {
        grantWarehouseScope(employeeId, seedWarehouseId());
    }

    private void grantWarehouseScope(Long employeeId, Long warehouseId) {
        jdbc.update("INSERT INTO employee_warehouse_scope (employee_id, warehouse_id) VALUES (?, ?)",
                employeeId, warehouseId);
    }

    /**
     * 只在<b>一个</b>仓库上有授权的普通员工：聚合读的用例必须能区分「这个仓没数据」与
     * 「这个仓不归他管」，所以授权行只能一条，不能沿用 {@link #setUpPurchasers()} 的种子仓授权。
     */
    private Long newWarehouseScopedEmployee(String suffix, Long warehouseId) {
        Long employeeId = newEmployee(suffix);
        grantWarehouseScope(employeeId, warehouseId);
        return employeeId;
    }

    // ---- 交集用例的回读与小表单（都按裸 SQL 读，断的是库里的真实行）----

    private PurchaseOrderVersionForm orderVersionForm(Long orderId) {
        PurchaseOrderVersionForm form = new PurchaseOrderVersionForm();
        form.setId(orderId);
        form.setVersion(orderVersion(orderId));
        return form;
    }

    private PurchaseOrderDeleteForm deleteForm(Long orderId) {
        PurchaseOrderDeleteForm form = new PurchaseOrderDeleteForm();
        form.setId(orderId);
        return form;
    }

    private PurchaseReceiptPutawayForm putawayForm(Long receiptId) {
        PurchaseReceiptPutawayForm form = new PurchaseReceiptPutawayForm();
        form.setId(receiptId);
        form.setVersion(jdbc.queryForObject("SELECT version FROM purchase_receipt WHERE id = ?",
                Integer.class, receiptId));
        return form;
    }

    /**
     * 幂等键必须逐次不同：第一次被范围拒绝后 {@code claim} 记录已经落下，
     * 同键重放会让第二次（补齐授权的那次）读到「已提交但缺 result_data」的半成品记录而直接报错，
     * 测不到「授权补齐后放行」这件事本身。
     */
    private PurchaseReceiptVO putaway(Long receiptId, String tag) {
        return purchaseReceiptService.putaway(putawayForm(receiptId), prefix + ":putaway:" + receiptId + ":" + tag);
    }

    private PurchaseDemandAllocateForm allocateForm(Long demandId, Long orderId) {
        PurchaseDemandAllocateForm form = new PurchaseDemandAllocateForm();
        form.setDemandId(demandId);
        form.setPurchaseOrderItemId(jdbc.queryForObject("SELECT id FROM purchase_order_item "
                + "WHERE purchase_order_id = ? AND deleted = FALSE ORDER BY id LIMIT 1", Long.class, orderId));
        form.setQuantity("4.0000");
        form.setSupplierId(
                jdbc.queryForObject("SELECT supplier_id FROM purchase_order WHERE id = ?", Long.class, orderId));
        form.setWarehouseId(seedWarehouseId());
        form.setVersion(jdbc.queryForObject("SELECT version FROM purchase_demand WHERE id = ?",
                Integer.class, demandId));
        return form;
    }

    private String receiptStatus(Long receiptId) {
        evictMybatisCache();
        return jdbc.queryForObject("SELECT status FROM purchase_receipt WHERE id = ?", String.class, receiptId);
    }

    private Integer receiptVersion(Long receiptId) {
        evictMybatisCache();
        return jdbc.queryForObject("SELECT version FROM purchase_receipt WHERE id = ?", Integer.class, receiptId);
    }

    private String receiptRemark(Long receiptId) {
        evictMybatisCache();
        return jdbc.queryForObject("SELECT remark FROM purchase_receipt WHERE id = ?", String.class, receiptId);
    }

    private boolean receiptDeleted(Long receiptId) {
        evictMybatisCache();
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT deleted FROM purchase_receipt WHERE id = ?",
                Boolean.class, receiptId));
    }

    private boolean orderDeleted(Long orderId) {
        evictMybatisCache();
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT deleted FROM purchase_order WHERE id = ?",
                Boolean.class, orderId));
    }

    private BigDecimal allocatedQuantity(Long demandId) {
        evictMybatisCache();
        return jdbc.queryForObject("SELECT allocated_quantity FROM purchase_demand WHERE id = ?",
                BigDecimal.class, demandId);
    }

    private void loginAs(Long employeeId) {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(employeeId);
        employee.setActualName("采购数据范围 IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(false);
        employee.setDepartmentId(1L);
        SmartRequestUtil.setRequestUser(employee);
    }

    /**
     * 以某个员工 + 一组功能权限执行一段逻辑。权限由 {@code mockStatic(StpUtil.class)} 提供：
     * 未点名的权限码取 Mockito 默认值 false，与生产上的失败关闭取向一致。<b>不可嵌套调用</b>。
     */
    private <T> T as(Long employeeId, Set<String> permissions, Supplier<T> body) {
        loginAs(employeeId);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            permissions.forEach(p -> stp.when(() -> StpUtil.hasPermission(p)).thenReturn(true));
            return body.get();
        }
    }

    /**
     * 越权读要落到「没有权限」上，而不是被伪装成「不存在」或空结果。
     * {@code ScmBusinessException} 也继承 {@code BusinessException}，因此必须显式排除。
     */
    private void assertNoPermission(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .isNotInstanceOf(ScmBusinessException.class)
                .hasMessage(UserErrorCode.NO_PERMISSION.getMsg());
    }

    /** 一个「SKU + 已挂该 SKU 的供应商」组合：采购单的合法前置条件。 */
    private record Purchasable(Long skuId, Long supplierId) {
    }

    private Purchasable purchasable(String tag) {
        Long skuId = newOnShelfSku(tag);
        return new Purchasable(skuId, newPurchasableSupplier(tag, skuId));
    }

    /**
     * 以 {@code caller} 身份建一张草稿采购单。
     *
     * @param submittedPurchaserId 表单里的归属；无分配权时必须被服务端覆盖成调用者本人
     */
    private Long createOrder(Purchasable goods, String suffix, Long caller, Long submittedPurchaserId,
                             Set<String> permissions) {
        return as(caller, permissions, () -> {
            PurchaseOrderAddForm form = orderForm(goods.supplierId(), seedWarehouseId(), goods.skuId(),
                    "4.0000", "6.2000");
            form.setPurchaserId(submittedPurchaserId);
            return purchaseOrderService.create(form, prefix + ":" + suffix + ":po").getId();
        });
    }

    /** 建一张已提交的采购单（收货的前置状态）。 */
    private Long createSubmittedOrder(Purchasable goods, String suffix, Long caller) {
        Long orderId = createOrder(goods, suffix, caller, caller, Set.of());
        return as(caller, Set.of(), () -> {
            submitOrder(orderId);
            return orderId;
        });
    }

    /**
     * 在<b>指定仓库</b>建一张已提交采购单，数量由调用方给。
     *
     * <p>聚合工作台按仓库分组，所以「同一 SKU 落在两个仓」这种夹具必须在两个仓各建一张单；
     * 数量分开给是为了让每个仓的计划 / 已收量互不相同，断言才分得清合并与没收窄。
     */
    private Long createSubmittedOrderAt(Purchasable goods, String suffix, Long caller, Long warehouseId,
                                        String quantity) {
        Long orderId = as(caller, Set.of(), () -> {
            PurchaseOrderAddForm form = orderForm(goods.supplierId(), warehouseId, goods.skuId(),
                    quantity, "6.2000");
            form.setPurchaserId(caller);
            return purchaseOrderService.create(form, prefix + ":" + suffix + ":po").getId();
        });
        return as(caller, Set.of(), () -> {
            submitOrder(orderId);
            return orderId;
        });
    }

    /**
     * 直插一行库存余额：缺口预览只<b>读</b>余额，不经收货入库链路，
     * 因此「已知库存事实」这一夹具直接落表就是它要构造的东西（与 {@code PurchaseDemandSummaryPreviewIT} 同）。
     */
    private void seedBalance(Long warehouseId, Long skuId, String quantity) {
        jdbc.update("INSERT INTO inventory_balance (warehouse_id, sku_id, unit, quantity, reserved_quantity, "
                        + "avg_cost, version, deleted, created_at, updated_at, created_by, updated_by) "
                        + "VALUES (?, ?, ?, ?, 0, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'it', 'it')",
                warehouseId, skuId, DEFAULT_PURCHASE_UNIT, new BigDecimal(quantity));
        evictMybatisCache();
    }

    private PurchaseDemandSummaryPreviewForm summaryForm(Long warehouseId, OffsetDateTime confirmedAt) {
        PurchaseDemandSummaryPreviewForm form = newForm(new PurchaseDemandSummaryPreviewForm());
        form.setStartAt(confirmedAt);
        form.setEndAt(confirmedAt.plusSeconds(2));
        form.setWarehouseId(warehouseId);
        return form;
    }

    private List<PurchaseDemandSummaryVO> summaryRows(Long warehouseId, OffsetDateTime confirmedAt) {
        return purchaseQueryService.summaryPreview(summaryForm(warehouseId, confirmedAt)).getList();
    }

    private PurchaseDemandSummaryVO summaryRow(Long skuId, Long warehouseId, OffsetDateTime confirmedAt) {
        return summaryRows(warehouseId, confirmedAt).stream()
                .filter(row -> row.getSkuId().equals(skuId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺口预览缺少 SKU " + skuId + " 的行"));
    }

    private PurchaseReceiptItemWorkbenchQueryForm workbenchForm(Long warehouseId) {
        PurchaseReceiptItemWorkbenchQueryForm form = newForm(new PurchaseReceiptItemWorkbenchQueryForm());
        form.setWarehouseId(warehouseId);
        return form;
    }

    private List<PurchaseReceiptItemWorkbenchVO> workbenchRows(Long warehouseId) {
        return purchaseQueryService.receiptItemWorkbench(workbenchForm(warehouseId)).getList();
    }

    private PurchaseReceiptItemWorkbenchVO workbenchRow(Long skuId, Long warehouseId) {
        return workbenchRows(warehouseId).stream()
                .filter(row -> row.getSkuId().equals(skuId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("收货工作台缺少 SKU " + skuId + " 的聚合行"));
    }

    /** 汇总「恰好这一张」已确认订单 → 需求，并按用例需要填表单里的默认采购员。 */
    private PurchaseDemandEntity generateDemand(Long supplierId, Long salesOrderId, Long submittedPurchaserId) {
        OffsetDateTime confirmedAt = salesOrderConfirmedAt(salesOrderId);
        PurchaseDemandGenerateForm form = new PurchaseDemandGenerateForm();
        form.setStartAt(confirmedAt);
        form.setEndAt(confirmedAt.plusSeconds(1));
        form.setWarehouseId(seedWarehouseId());
        form.setSupplierId(supplierId);
        form.setPurchaserId(submittedPurchaserId);
        purchaseDemandService.generate(form, prefix + ":gen:" + salesOrderId);
        return demandOfSourceItem(confirmedSalesOrderItemId(salesOrderId));
    }

    private <T extends PageParam> T newForm(T form) {
        form.setPageNum(1L);
        form.setPageSize(200L);
        return form;
    }

    /** 列表断言一律用 contains / doesNotContain：同一事务里库里还有别的用例与种子数据，不假设空库。 */
    private PurchaseOrderQueryForm orderQuery(Long purchaserIdFilter) {
        PurchaseOrderQueryForm form = newForm(new PurchaseOrderQueryForm());
        form.setPurchaserId(purchaserIdFilter);
        return form;
    }

    private List<Long> orderIds(PurchaseOrderQueryForm form) {
        return purchaseQueryService.orderQuery(form).getList().stream().map(PurchaseOrderVO::getId).toList();
    }

    private PurchaseDemandQueryForm demandQuery(Long skuId) {
        PurchaseDemandQueryForm form = newForm(new PurchaseDemandQueryForm());
        form.setSkuId(skuId);
        return form;
    }

    private List<Long> demandIds(PurchaseDemandQueryForm form) {
        return purchaseQueryService.demandQuery(form).getList().stream().map(PurchaseDemandVO::getId).toList();
    }

    private PurchaseReceiptQueryForm receiptQuery(Long purchaseOrderId) {
        PurchaseReceiptQueryForm form = newForm(new PurchaseReceiptQueryForm());
        form.setPurchaseOrderId(purchaseOrderId);
        return form;
    }

    private List<Long> receiptIds(PurchaseReceiptQueryForm form) {
        return purchaseQueryService.receiptQuery(form).getList().stream().map(PurchaseReceiptVO::getId).toList();
    }

    private SupplierQueryForm supplierQueryForm() {
        SupplierQueryForm form = new SupplierQueryForm();
        form.setPageNum(1L);
        form.setPageSize(200L);
        form.setKeyword(prefix);
        return form;
    }

    private List<Long> supplierIds(SupplierQueryForm form) {
        return supplierQueryService.query(form).getList().stream().map(SupplierVO::getSupplierId).toList();
    }

    private PurchaseOrderReassignForm reassignForm(Long orderId, Long purchaserId, Integer version) {
        PurchaseOrderReassignForm form = new PurchaseOrderReassignForm();
        form.setId(orderId);
        form.setVersion(version);
        form.setPurchaserId(purchaserId);
        form.setReason("数据范围改派");
        return form;
    }

    private InventoryLossGainAddForm lossGainForm(Long skuId, String quantity) {
        InventoryLossGainAddForm form = new InventoryLossGainAddForm();
        form.setAdjustType("LOSS");
        form.setWarehouseId(seedWarehouseId());
        form.setReason("到货变质");
        InventoryLossGainAddForm.Item item = new InventoryLossGainAddForm.Item();
        item.setSkuId(skuId);
        item.setQuantity(new BigDecimal(quantity));
        form.setItems(new ArrayList<>(List.of(item)));
        return form;
    }

    private InventoryLossGainAuditForm auditForm(Long documentId, String opinion) {
        InventoryLossGainAuditForm form = new InventoryLossGainAuditForm();
        form.setVersion(jdbc.queryForObject(
                "SELECT version FROM inventory_loss_gain WHERE id = ?", Integer.class, documentId));
        form.setAuditOpinion(opinion);
        return form;
    }

    // ---- 回读（清一级缓存后按裸 SQL 读，断言的是库里的真实行）----

    private Long purchaserIdOf(Long orderId) {
        evictMybatisCache();
        return jdbc.queryForObject("SELECT purchaser_id FROM purchase_order WHERE id = ?", Long.class, orderId);
    }

    private Integer orderVersion(Long orderId) {
        evictMybatisCache();
        return jdbc.queryForObject("SELECT version FROM purchase_order WHERE id = ?", Integer.class, orderId);
    }

    private Long demandPurchaserId(Long demandId) {
        evictMybatisCache();
        return jdbc.queryForObject("SELECT purchaser_id FROM purchase_demand WHERE id = ?", Long.class, demandId);
    }

    private String lossGainStatus(Long documentId) {
        evictMybatisCache();
        return jdbc.queryForObject("SELECT status FROM inventory_loss_gain WHERE id = ?", String.class, documentId);
    }

    private String lossGainAuditor(Long documentId) {
        evictMybatisCache();
        return jdbc.queryForObject("SELECT auditor FROM inventory_loss_gain WHERE id = ?", String.class, documentId);
    }

    private boolean roleHoldsPermission(String roleCode, String permission) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM t_role_menu rm
                JOIN t_role r ON r.role_id = rm.role_id
                JOIN t_menu m ON m.menu_id = rm.menu_id
                WHERE r.role_code = ? AND m.api_perms = ?""", Integer.class, roleCode, permission);
        return count != null && count > 0;
    }
}
