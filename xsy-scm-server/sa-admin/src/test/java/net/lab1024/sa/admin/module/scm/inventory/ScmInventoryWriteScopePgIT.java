package net.lab1024.sa.admin.module.scm.inventory;

import cn.dev33.satoken.stp.StpUtil;
import net.lab1024.sa.admin.module.scm.common.ScmW6PgITBase;
import net.lab1024.sa.admin.module.scm.common.handler.ScmExceptionHandler;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeException;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventorySourceDocumentTypeEnum;
import net.lab1024.sa.admin.module.scm.inventory.domain.ReserveInventoryFact;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryConversionAddForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryConversionAuditForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryLossGainAddForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryLossGainAuditForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryOutboundAddForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryStocktakeAddForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryTransferAddForm;
import net.lab1024.sa.admin.module.scm.inventory.domain.form.InventoryWarningThresholdAddForm;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryConversionService;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryLossGainService;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryOutboundService;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryReservationService;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryStocktakeService;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryTransferService;
import net.lab1024.sa.admin.module.scm.inventory.service.InventoryWarningThresholdService;
import net.lab1024.sa.admin.module.scm.purchase.constant.ScmReceiptModeEnum;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptCreateForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.form.PurchaseReceiptPutawayForm;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseOrderVO;
import net.lab1024.sa.admin.module.scm.purchase.domain.vo.PurchaseReceiptVO;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.code.UserErrorCode;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

/**
 * 库存族**写侧**的仓库数据范围取证（{@code docs/decisions.md}「P0 基线收口裁决」第 8 条）。
 *
 * <p>读侧的收口（{@code ScmInventoryDataScopePgIT}）只解决「看不见」；本类钉的是另一件事：
 * 只被授权甲仓的仓管，即使手里有 {@code scm:inventory:*:confirm} 这类功能权限，
 * 也不能对乙仓<b>写下</b>任何库存事实。覆盖的命令：出库（建 / 改 / 确认 / 取消 / 删）、
 * 盘点（建 / 改 / 确认 / 取消 / 删 / 导入）、报损报溢（建 / 审 / 驳 / 删）、
 * 规格转换（建 / 审 / 驳 / 删）、调拨（建 / 改 / 发出 / 收货 / 取消 / 删）、
 * 预留释放、预警阈值配置（建 / 改 / 删）、收货单上架。
 *
 * <p><b>调拨的三个动作判据互不相同</b>，每个方向都单独钉：
 * <b>查询</b>任一端命中即可见（读侧用例）、<b>发出</b>只看 {@code from_warehouse}、
 * <b>收货</b>只看 {@code to_warehouse}、<b>建单 / 改单</b>两端都要授权
 * （能建一张通往未授权仓的调拨单，等于往自己读不到的仓库里塞单据）、
 * <b>取消 / 删除草稿</b>不碰余额，沿用查询那条 OR 判据。
 *
 * <p><b>「拒绝之后一行都没写」是本类的核心断言</b>：守卫必须落在读到持久化行之后、
 * 任何 UPDATE / INSERT 之前。用例包在测试事务里，Service 上的 {@code @Transactional} 只是
 * <em>加入</em>它，异常仅把事务标记成 rollback-only，<b>不会</b>撤掉已经写下的行
 * （{@code ScmInventoryLossGainRollbackIT} 为验证原子性刻意关掉外层事务，正是同一件事的另一面）。
 * 所以这里看到的「流水没多、余额没动、状态没变」不是回滚的结果，而是<b>根本没写</b>——
 * 判定一旦被挪到写库之后，同一句断言会立刻失败。为了让拒绝只可能由范围引起，
 * 反例用的单据一律状态合法、乐观锁版本从库里回读；而「行确实不存在」的路径单独断言
 * 仍返回自己的业务码（41013 等），没有被范围判定吞成 30005。
 *
 * <p><b>身份与权限</b>：与读侧同一套做法——范围解析读登录员工（{@code SmartRequestUtil}）
 * 与功能权限（{@code StpUtil.hasPermission}），IT 线程没有 Sa-Token 上下文，功能点一律由
 * {@code mockStatic} 显式给出。被测员工一律 {@code administratorFlag=false}（裁决第 5 条：
 * 超管通过不构成权限证据）；超管位只用于造夹具、建反例单据，以及第 10 组「超管不受影响」的取证。
 *
 * <p><b>夹具</b>：每个用例新建独立仓库与独立 SKU，经真实的
 * 「采购单 → 提交 → 收货 → 确认」链路入库 10 kg，因此余额行数与流水条数都是可精确断言的数字；
 * 流水一律由命令服务写入（Q7 / V21 append-only），测试不 UPDATE、不 DELETE 账本。
 */
@DisplayName("库存写侧仓库数据范围（PG IT）")
class ScmInventoryWriteScopePgIT extends ScmW6PgITBase {

    private static final String QUANTITY = "10.0000";
    private static final String PRICE = "6.2000";

    /** 一次库存动作的量：出库 / 调拨 / 转换转出都用它。 */
    private static final String MOVE = "4.0000";

    private static final BigDecimal STOCKED = new BigDecimal("10.0000");
    private static final BigDecimal MOVED = new BigDecimal("4.0000");
    private static final BigDecimal ZERO = new BigDecimal("0.0000");

    /** 反例单据的初始状态：拒绝后必须仍是它。 */
    private static final String DRAFT = "DRAFT";
    private static final String PENDING = "PENDING";

    @Autowired
    private InventoryOutboundService outboundService;

    @Autowired
    private InventoryStocktakeService stocktakeService;

    @Autowired
    private InventoryLossGainService lossGainService;

    @Autowired
    private InventoryConversionService conversionService;

    @Autowired
    private InventoryTransferService transferService;

    @Autowired
    private InventoryReservationService reservationService;

    @Autowired
    private InventoryWarningThresholdService thresholdService;

    // ------------------------------------------------------------------
    // 1. 出库：授权仓可确认；未授权仓被拒且账本一行未动
    // ------------------------------------------------------------------

    @Test
    @DisplayName("出库单：授权仓确认出库正常落账；未授权仓确认后流水、余额、状态全部原样")
    void outboundConfirmIsAllowedInOwnWarehouseAndWritesNothingOtherwise() {
        Stocked mine = stockNew("ob1a");
        Stocked foreign = stockNew("ob1b");
        Long keeper = keeper("ob1", mine.warehouseId());

        Long mineId = as(keeper, Set.of(), () -> outboundService.create(outboundForm(mine, MOVE)));
        asRun(keeper, () -> outboundService.confirm(mineId));
        assertThat(statusOf("inventory_outbound", mineId)).isEqualTo("CONFIRMED");
        assertThat(quantityOf(mine)).as("授权仓的出库照常扣账").isEqualByComparingTo("6.0000");
        assertThat(movementTypes(mine)).containsExactly("PURCHASE_IN", "SALES_OUT");

        // 反例：乙仓一张状态合法、版本正确的草稿，只因为仓库不在范围内被挡
        Long foreignId = asAdmin(() -> outboundService.create(outboundForm(foreign, MOVE)));
        assertDataScopeDenied(() -> asRun(keeper, () -> outboundService.confirm(foreignId)));
        assertThat(statusOf("inventory_outbound", foreignId)).as("确认没落进状态机").isEqualTo(DRAFT);
        assertThat(movementTypes(foreign)).as("一条 SALES_OUT 都没写下").containsExactly("PURCHASE_IN");
        assertThat(quantityOf(foreign)).as("余额一分未动").isEqualByComparingTo(STOCKED);
        assertDataScopeDenied(() -> asRun(keeper, () -> outboundService.cancel(foreignId)));
        assertDataScopeDenied(() -> asRun(keeper, () -> outboundService.delete(foreignId)));
        assertThat(activeRowCount("inventory_outbound", foreignId)).as("取消与删除也没碰到这张单").isEqualTo(1);

        // 未授权仓连草稿都建不出来：建单不是「读不到」，而是往别人仓里塞单据
        assertDataScopeDenied(() -> as(keeper, Set.of(), () -> outboundService.create(outboundForm(foreign, MOVE))));
        assertThat(rowCount("inventory_outbound", "warehouse_id = ?", foreign.warehouseId()))
                .as("仍然只有夹具那一张").isEqualTo(1);

        // 改单会把仓库换掉：行上的旧仓与表单的新仓两端都要授权
        Long draftInMine = as(keeper, Set.of(), () -> outboundService.create(outboundForm(mine, MOVE)));
        assertDataScopeDenied(() -> asRun(keeper,
                () -> outboundService.update(draftInMine, outboundForm(foreign, MOVE))));
        assertThat(warehouseOf("inventory_outbound", draftInMine)).isEqualTo(mine.warehouseId());

        // 「行不存在」不能被范围判定吞成 30005，否则探测主键与探测权限混成一种结果
        expectCode(() -> asRun(keeper, () -> outboundService.confirm(foreignId + 9999999L)), 41013);
    }

    // ------------------------------------------------------------------
    // 2. 调拨：发出只看 from 端、收货只看 to 端
    // ------------------------------------------------------------------

    @Test
    @DisplayName("调拨：只授权目标仓者发不出，只授权源仓者收不进；两端各自放行时链路正常走完")
    void transferShipChecksSourceEndAndReceiveChecksTargetEnd() {
        Stocked source = stockNew("tf2a");
        Stocked target = stockNew("tf2b");
        Long sourceKeeper = keeper("tf2s", source.warehouseId());
        Long targetKeeper = keeper("tf2t", target.warehouseId());

        // 单据由夹具（超管）建出来：两端都要授权的人建不成跨仓单，那是第 3 组的用例
        Long transferId = asAdmin(() -> transferService.create(
                transferForm(source.warehouseId(), target.warehouseId(), source.skuId(), MOVE)));

        // 发出要求 from 仓权限：只授权 to 端的人发不出去，源仓余额与流水原样
        assertDataScopeDenied(() -> asRun(targetKeeper, () -> transferService.ship(transferId)));
        assertThat(statusOf("inventory_transfer", transferId)).isEqualTo(DRAFT);
        assertThat(movementTypes(source)).containsExactly("PURCHASE_IN");
        assertThat(quantityOf(source)).isEqualByComparingTo(STOCKED);

        asRun(sourceKeeper, () -> transferService.ship(transferId));
        assertThat(statusOf("inventory_transfer", transferId)).isEqualTo("SHIPPED");
        assertThat(movementTypes(source)).containsExactly("PURCHASE_IN", "TRANSFER_OUT");
        assertThat(quantityOf(source)).isEqualByComparingTo("6.0000");
        assertThat(movementCount(target.warehouseId(), source.skuId()))
                .as("在途：货不属于任何仓的余额").isZero();

        // 收货要求 to 仓权限：发出去的一方不能替目标仓入账
        assertDataScopeDenied(() -> asRun(sourceKeeper, () -> transferService.receive(transferId)));
        assertThat(statusOf("inventory_transfer", transferId)).isEqualTo("SHIPPED");
        assertThat(movementCount(target.warehouseId(), source.skuId())).isZero();
        assertThat(quantityOf(target.warehouseId(), source.skuId()))
                .as("目标仓的余额行还没被创建").isNull();

        asRun(targetKeeper, () -> transferService.receive(transferId));
        assertThat(statusOf("inventory_transfer", transferId)).isEqualTo("RECEIVED");
        assertThat(movementTypes(target.warehouseId(), source.skuId())).containsExactly("TRANSFER_IN");
        assertThat(quantityOf(target.warehouseId(), source.skuId)).isEqualByComparingTo(MOVED);
        assertLedgerBalanced(source.warehouseId(), source.skuId());
        assertLedgerBalanced(target.warehouseId(), source.skuId());

        // 已经走完的两端授权人再撤销：在途/终态不可撤销是状态机的事，与本类无关，但状态不能变
        assertThat(statusOf("inventory_transfer", transferId)).isEqualTo("RECEIVED");
    }

    // ------------------------------------------------------------------
    // 3. 调拨：建单与改单两端都要授权；撤销草稿沿用 OR 判据
    // ------------------------------------------------------------------

    @Test
    @DisplayName("调拨建单/改单要求两端同时授权；只授权一端的第三方既建不成也改不成也撤不掉别人的草稿")
    void transferCannotBeCreatedOrMovedTowardAnUnauthorizedWarehouse() {
        Stocked mine = stockNew("tf3a");
        Stocked alsoMine = stockNew("tf3c");
        Stocked foreign = stockNew("tf3b");
        Long onlyMine = keeper("tf3", mine.warehouseId());
        Long bothEnds = keeper("tf3both", mine.warehouseId(), alsoMine.warehouseId());
        Long stranger = keeper("tf3out", foreign.warehouseId());

        // 甲→乙 与 乙→甲 都建不出来：「看得见这张单」不等于「有资格让这张单存在」
        assertDataScopeDenied(() -> as(onlyMine, Set.of(), () -> transferService.create(
                transferForm(mine.warehouseId(), foreign.warehouseId(), mine.skuId(), MOVE))));
        assertDataScopeDenied(() -> as(onlyMine, Set.of(), () -> transferService.create(
                transferForm(foreign.warehouseId(), mine.warehouseId(), foreign.skuId(), MOVE))));
        assertThat(rowCount("inventory_transfer",
                "(from_warehouse_id IN (?, ?) AND to_warehouse_id IN (?, ?))",
                mine.warehouseId(), foreign.warehouseId(), mine.warehouseId(), foreign.warehouseId()))
                .as("两个方向都没落下任何一张草稿").isZero();

        // 两端都在范围内：建单与改单照常
        Long allowed = as(bothEnds, Set.of(), () -> transferService.create(
                transferForm(mine.warehouseId(), alsoMine.warehouseId(), mine.skuId(), MOVE)));
        asRun(bothEnds, () -> transferService.update(allowed,
                transferForm(mine.warehouseId(), alsoMine.warehouseId(), mine.skuId(), "2.0000")));
        assertThat(quantityOfFirstItem("inventory_transfer_item", "transfer_id", allowed))
                .as("两端都授权时改单照常写入").isEqualByComparingTo("2.0000");

        // 只授权 from 端的人想把 to 换成未授权仓：判掉，且旧值与明细都不留痕
        assertDataScopeDenied(() -> asRun(onlyMine, () -> transferService.update(allowed,
                transferForm(mine.warehouseId(), foreign.warehouseId(), mine.skuId(), MOVE))));
        assertThat(warehouseOf("inventory_transfer", "to_warehouse_id", allowed)).isEqualTo(alsoMine.warehouseId());
        assertThat(quantityOfFirstItem("inventory_transfer_item", "transfer_id", allowed))
                .as("失败的改单没换掉明细数量").isEqualByComparingTo("2.0000");

        // 撤销草稿不碰余额，判据与查询同为 OR：两端都不授权的人撤不掉
        assertDataScopeDenied(() -> asRun(stranger, () -> transferService.cancel(allowed)));
        assertDataScopeDenied(() -> asRun(stranger, () -> transferService.delete(allowed)));
        assertThat(statusOf("inventory_transfer", allowed)).isEqualTo(DRAFT);
        assertThat(activeRowCount("inventory_transfer", allowed)).isEqualTo(1);
        // 命中任一端（这里是 from 端）就可以撤自己的草稿
        asRun(onlyMine, () -> transferService.cancel(allowed));
        assertThat(statusOf("inventory_transfer", allowed)).isEqualTo("CANCELLED");
    }

    // ------------------------------------------------------------------
    // 4. 报损报溢：范围判定与「禁止自建自审」各自独立生效
    // ------------------------------------------------------------------

    @Test
    @DisplayName("报损报溢：未授权仓审批/驳回被拒且不留流水；自建自审禁令 41065 仍独立生效")
    void lossGainApprovalIsGatedByWarehouseAndSelfApprovalBanStaysIndependent() {
        Stocked mine = stockNew("lg4a");
        Stocked foreign = stockNew("lg4b");
        Long keeper = keeper("lg4", mine.warehouseId());

        // 别人在乙仓录的单：keeper 与它既无仓库关系也无录单关系，两个禁令互不遮蔽
        Long foreignDoc = asAdmin(() -> lossGainService.create(lossGainForm(foreign, "1.0000")));
        assertDataScopeDenied(() -> asRun(keeper, () -> lossGainService.approve(
                foreignDoc, lossGainAuditForm(versionOf("inventory_loss_gain", foreignDoc)))));
        assertDataScopeDenied(() -> asRun(keeper, () -> lossGainService.reject(
                foreignDoc, lossGainAuditForm(versionOf("inventory_loss_gain", foreignDoc)))));
        assertThat(statusOf("inventory_loss_gain", foreignDoc)).as("既没通过也没驳回").isEqualTo(PENDING);
        assertThat(movementTypes(foreign)).containsExactly("PURCHASE_IN");
        assertThat(quantityOf(foreign)).isEqualByComparingTo(STOCKED);
        assertDataScopeDenied(() -> asRun(keeper, () -> lossGainService.delete(foreignDoc)));
        assertThat(activeRowCount("inventory_loss_gain", foreignDoc)).isEqualTo(1);

        // 未授权仓连录单都不行
        assertDataScopeDenied(() -> as(keeper, Set.of(), () -> lossGainService.create(lossGainForm(foreign, "1.0000"))));
        assertThat(rowCount("inventory_loss_gain", "warehouse_id = ?", foreign.warehouseId()))
                .as("只有夹具那一张").isEqualTo(1);

        // 自己在授权仓录的单：范围放行，41065 仍按身份拦下 —— 两条禁令各管各的
        Long ownDoc = as(keeper, Set.of(), () -> lossGainService.create(lossGainForm(mine, "1.0000")));
        expectCode(() -> asRun(keeper, () -> lossGainService.approve(
                ownDoc, lossGainAuditForm(versionOf("inventory_loss_gain", ownDoc)))), 41065);
        assertThat(statusOf("inventory_loss_gain", ownDoc)).isEqualTo(PENDING);
        assertThat(movementTypes(mine)).as("自审被拦下时同样不该留下流水").containsExactly("PURCHASE_IN");

        // 换一位同仓仓管审批：正常落账，说明范围收口没有把有效单据卡死
        Long mate = keeper("lg4mate", mine.warehouseId());
        asRun(mate, () -> lossGainService.approve(ownDoc,
                lossGainAuditForm(versionOf("inventory_loss_gain", ownDoc))));
        assertThat(statusOf("inventory_loss_gain", ownDoc)).isEqualTo("COMPLETED");
        assertThat(movementTypes(mine)).containsExactly("PURCHASE_IN", "LOSS_REPORT");
        assertThat(quantityOf(mine)).isEqualByComparingTo("9.0000");
        assertThat(jdbc.queryForObject("SELECT auditor FROM inventory_loss_gain WHERE id = ?", String.class, ownDoc))
                .isEqualTo(UserTypeEnum.ADMIN_EMPLOYEE.getValue() + ":" + mate);
    }

    // ------------------------------------------------------------------
    // 5. 盘点：建 / 改 / 确认 / 取消 / 删 / 导入
    // ------------------------------------------------------------------

    @Test
    @DisplayName("盘点单：未授权仓建不出、确认不掉；导入路径先判范围；授权仓盘盈照常落地")
    void stocktakeCommandsOutsideAuthorizedWarehouseLeaveNoTrace() {
        Stocked mine = stockNew("st5a");
        Stocked foreign = stockNew("st5b");
        Long keeper = keeper("st5", mine.warehouseId());

        assertDataScopeDenied(() -> as(keeper, Set.of(),
                () -> stocktakeService.create(stocktakeForm(foreign, "12.0000"))));
        assertThat(rowCount("inventory_stocktake", "warehouse_id = ?", foreign.warehouseId())).isZero();
        // 导入：仓库取自签名凭证，但凭证不是授权依据，判定落在锁定余额之前
        assertDataScopeDenied(() -> as(keeper, Set.of(), () -> stocktakeService.createFromSnapshot(
                foreign.warehouseId(), List.of())));

        Long foreignDoc = asAdmin(() -> stocktakeService.create(stocktakeForm(foreign, "12.0000")));
        assertDataScopeDenied(() -> asRun(keeper, () -> stocktakeService.confirm(foreignDoc)));
        assertDataScopeDenied(() -> asRun(keeper, () -> stocktakeService.cancel(foreignDoc)));
        assertDataScopeDenied(() -> asRun(keeper, () -> stocktakeService.delete(foreignDoc)));
        assertDataScopeDenied(() -> asRun(keeper,
                () -> stocktakeService.update(foreignDoc, stocktakeForm(foreign, "8.0000"))));
        assertThat(statusOf("inventory_stocktake", foreignDoc)).isEqualTo(DRAFT);
        assertThat(movementTypes(foreign)).as("盘盈/盘亏都没写进乙仓").containsExactly("PURCHASE_IN");
        assertThat(quantityOf(foreign)).isEqualByComparingTo(STOCKED);
        assertThat(bookQuantityOfFirstItem(foreignDoc)).as("失败的改单没换掉账面量快照")
                .isEqualByComparingTo(STOCKED);

        Long ownDoc = as(keeper, Set.of(), () -> stocktakeService.create(stocktakeForm(mine, "12.0000")));
        assertThat(bookQuantityOfFirstItem(ownDoc)).as("授权仓的快照与改单照常").isEqualByComparingTo(STOCKED);
        asRun(keeper, () -> stocktakeService.confirm(ownDoc));
        assertThat(statusOf("inventory_stocktake", ownDoc)).isEqualTo("CONFIRMED");
        assertThat(movementTypes(mine)).containsExactly("PURCHASE_IN", "STOCKTAKE_GAIN");
        assertThat(quantityOf(mine)).isEqualByComparingTo("12.0000");
    }

    // ------------------------------------------------------------------
    // 6. 规格转换：一次改两行余额，先判仓再写任何一条腿
    // ------------------------------------------------------------------

    @Test
    @DisplayName("规格转换单：未授权仓建不出也审不掉，两条腿都不写；授权仓转出转入同步落账")
    void conversionApprovalIsGatedBeforeEitherLegWrites() {
        Stocked mine = stockNew("cv6a");
        Stocked foreign = stockNew("cv6b");
        Long keeper = keeper("cv6", mine.warehouseId());
        Long extraSku = asAdmin(() -> newSkuOfType("cv6x", "NON_STANDARD", "ON_SHELF"));

        assertDataScopeDenied(() -> as(keeper, Set.of(), () -> conversionService.create(
                conversionForm(foreign.warehouseId(), foreign.skuId(), extraSku))));
        assertThat(rowCount("inventory_conversion", "warehouse_id = ?", foreign.warehouseId())).isZero();

        Long foreignDoc = asAdmin(() -> conversionService.create(
                conversionForm(foreign.warehouseId(), foreign.skuId(), extraSku)));
        assertDataScopeDenied(() -> asRun(keeper, () -> conversionService.approve(
                foreignDoc, conversionAuditForm(versionOf("inventory_conversion", foreignDoc)))));
        assertDataScopeDenied(() -> asRun(keeper, () -> conversionService.reject(
                foreignDoc, conversionAuditForm(versionOf("inventory_conversion", foreignDoc)))));
        assertDataScopeDenied(() -> asRun(keeper, () -> conversionService.delete(foreignDoc)));
        assertThat(statusOf("inventory_conversion", foreignDoc)).isEqualTo(PENDING);
        assertThat(movementTypes(foreign)).as("转出腿没写").containsExactly("PURCHASE_IN");
        assertThat(movementCount(foreign.warehouseId(), extraSku)).as("转入腿也没写").isZero();
        assertThat(quantityOf(foreign)).isEqualByComparingTo(STOCKED);
        assertThat(movementTypes(mine)).as("别人的转换一条流水都没沾到自己仓").containsExactly("PURCHASE_IN");

        Long ownDoc = asAdmin(() -> conversionService.create(
                conversionForm(mine.warehouseId(), mine.skuId(), extraSku)));
        asRun(keeper, () -> conversionService.approve(ownDoc,
                conversionAuditForm(versionOf("inventory_conversion", ownDoc))));
        assertThat(statusOf("inventory_conversion", ownDoc)).isEqualTo("COMPLETED");
        assertThat(movementTypes(mine)).containsExactly("PURCHASE_IN", "CONVERT_OUT");
        assertThat(movementTypes(mine.warehouseId(), extraSku)).containsExactly("CONVERT_IN");
        assertThat(quantityOf(mine)).isEqualByComparingTo("6.0000");
        assertThat(quantityOf(mine.warehouseId(), extraSku)).isEqualByComparingTo(MOVED);
        assertLedgerBalanced(mine.warehouseId(), mine.skuId());
    }

    // ------------------------------------------------------------------
    // 7. 预留释放
    // ------------------------------------------------------------------

    @Test
    @DisplayName("预留释放：未授权仓归还不了别人的可用量；订单取消的级联释放不受仓库判定")
    void reservationReleaseIsScopedButOrderCascadeIsNot() {
        Stocked mine = stockNew("rs7a");
        Stocked foreign = stockNew("rs7b");
        Long keeper = keeper("rs7", mine.warehouseId());
        Long mineReservation = asAdmin(() -> reservationService.reserve(reservationFact(mine, "1.0000", 90001L)));
        Long foreignReservation = asAdmin(() -> reservationService.reserve(reservationFact(foreign, "2.0000", 90002L)));
        assertThat(reservedQuantityOf(foreign)).as("前置：乙仓确有 2 kg 占用").isEqualByComparingTo("2.0000");

        assertDataScopeDenied(() -> asRun(keeper, () -> reservationService.release(foreignReservation)));
        assertThat(reservedQuantityOf(foreign)).as("乙仓的占用一分未归还").isEqualByComparingTo("2.0000");
        assertThat(statusOf("inventory_reservation", foreignReservation)).isEqualTo("ACTIVE");

        asRun(keeper, () -> reservationService.release(mineReservation));
        assertThat(reservedQuantityOf(mine)).isEqualByComparingTo(ZERO);
        assertThat(statusOf("inventory_reservation", mineReservation)).isEqualTo("RELEASED");

        // 级联释放：取消订单的授权依据是订单归属（seller），预留只是副作用，不套仓库判据
        asRun(keeper, () -> reservationService.releaseBySource(
                ScmInventorySourceDocumentTypeEnum.SALES_ORDER_ITEM.name(), 90002L));
        assertThat(reservedQuantityOf(foreign)).as("订单域级联照常归还").isEqualByComparingTo(ZERO);
    }

    // ------------------------------------------------------------------
    // 8. 预警阈值配置（按仓生效的配置）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("预警阈值：未授权仓建不出也改不掉删不掉；把配置搬进别人仓同样被拒；授权仓建改删全通")
    void warningThresholdConfigurationIsScopedPerWarehouse() {
        Stocked mine = stockNew("th8a");
        Stocked foreign = stockNew("th8b");
        Long keeper = keeper("th8", mine.warehouseId());

        assertDataScopeDenied(() -> as(keeper, Set.of(), () -> thresholdService.create(
                thresholdForm(foreign.warehouseId(), foreign.skuId(), "1"))));
        assertThat(rowCount("inventory_warning_threshold", "warehouse_id = ?", foreign.warehouseId())).isZero();

        Long foreignRow = asAdmin(() -> thresholdService.create(
                thresholdForm(foreign.warehouseId(), foreign.skuId(), "1")));
        assertDataScopeDenied(() -> asRun(keeper, () -> thresholdService.update(foreignRow,
                thresholdForm(foreign.warehouseId(), foreign.skuId(), "5"))));
        assertDataScopeDenied(() -> asRun(keeper, () -> thresholdService.delete(foreignRow)));
        assertThat(warnMinOf(foreignRow)).as("阈值数值没被改动").isEqualByComparingTo("1");
        assertThat(activeRowCount("inventory_warning_threshold", foreignRow)).isEqualTo(1);

        Long ownRow = as(keeper, Set.of(), () -> thresholdService.create(
                thresholdForm(mine.warehouseId(), mine.skuId(), "1")));
        // 把自己仓的配置搬进别人仓：新仓也要判
        assertDataScopeDenied(() -> asRun(keeper, () -> thresholdService.update(ownRow,
                thresholdForm(foreign.warehouseId(), mine.skuId(), "5"))));
        assertThat(warehouseOf("inventory_warning_threshold", ownRow)).isEqualTo(mine.warehouseId());

        asRun(keeper, () -> thresholdService.update(ownRow,
                thresholdForm(mine.warehouseId(), mine.skuId(), "5")));
        assertThat(warnMinOf(ownRow)).isEqualByComparingTo("5");
        asRun(keeper, () -> thresholdService.delete(ownRow));
        assertThat(activeRowCount("inventory_warning_threshold", ownRow)).isZero();
    }

    // ------------------------------------------------------------------
    // 9. 收货单上架：写 PURCHASE_IN 的那一步要判仓
    // ------------------------------------------------------------------

    @Test
    @DisplayName("收货上架：未授权仓上不了架，收货单仍是 PENDING 且不写 PURCHASE_IN；授权后正常入账")
    void receiptPutawayOutsideScopeKeepsReceiptPendingAndUnbooked() {
        Stocked mine = stockNew("pa9a");
        Long keeper = keeper("pa9", mine.warehouseId());
        PendingPutaway pending = stockPendingPutaway("pa9c");

        // 表单（含乐观锁版本）在超管上下文里备好：读侧的采购员范围与本案要证的仓库范围无关，
        // 混进来会让断言因「读不到」而通过，证不到「写之前就被挡」。
        PurchaseReceiptPutawayForm form = asAdmin(() -> putawayForm(pending.receiptId()));
        assertDataScopeDenied(() -> asRun(keeper, () -> purchaseReceiptService.putaway(form, putawayKey())));
        assertThat(jdbc.queryForObject("SELECT putaway_status FROM purchase_receipt WHERE id = ?",
                String.class, pending.receiptId())).as("仍等着仓库二次确认").isEqualTo(PENDING);
        assertThat(jdbc.queryForObject("SELECT putaway_at IS NULL FROM purchase_receipt WHERE id = ?",
                Boolean.class, pending.receiptId())).isTrue();
        assertThat(rowCount("inventory_movement", "warehouse_id = ?", pending.warehouseId()))
                .as("一条 PURCHASE_IN 都没写").isZero();
        assertThat(quantityOf(pending)).as("余额行还没被创建").isNull();

        PurchaseReceiptVO putaway = asAdmin(() -> purchaseReceiptService.putaway(
                putawayForm(pending.receiptId()), putawayKey()));
        assertThat(putaway.getPutawayStatus()).isEqualTo("COMPLETED");
        assertThat(movementTypes(pending)).containsExactly("PURCHASE_IN");
        assertThat(quantityOf(pending)).isEqualByComparingTo(STOCKED);
    }

    // ------------------------------------------------------------------
    // 10. 超管：administratorFlag=true 的行为一字未变
    // ------------------------------------------------------------------

    @Test
    @DisplayName("administratorFlag=true 绕过数据范围：跨仓的确认、调拨、盘点、转换、建配置全部照常")
    void administratorKeepsWorkingAcrossEveryWarehouseUnchanged() {
        Stocked source = stockNew("ad10a");
        Stocked target = stockNew("ad10b");
        // 没有任何授权行：只有超管位能这样跨仓写（裁决第 5 条的反证）
        Long nobodyElse = newEmployee("ad10emp");
        assertThat(authorizedWarehouseRowCount(nobodyElse)).isZero();

        Long conversion = asAdmin(() -> {
            Long outbound = outboundService.create(outboundForm(target, MOVE));
            outboundService.confirm(outbound);

            Long transfer = transferService.create(
                    transferForm(source.warehouseId(), target.warehouseId(), source.skuId(), MOVE));
            transferService.ship(transfer);
            transferService.receive(transfer);

            Long stocktake = stocktakeService.create(stocktakeForm(source, "9.0000"));
            stocktakeService.confirm(stocktake);

            Long created = conversionService.create(
                    conversionForm(target.warehouseId(), target.skuId(), source.skuId()));
            conversionService.approve(created, conversionAuditForm(versionOf("inventory_conversion", created)));
            return created;
        });
        Long threshold = asAdmin(() -> {
            Long id = thresholdService.create(thresholdForm(source.warehouseId(), source.skuId(), "2"));
            thresholdService.delete(id);
            return id;
        });

        assertThat(statusOf("inventory_conversion", conversion)).isEqualTo("COMPLETED");
        assertThat(activeRowCount("inventory_warning_threshold", threshold))
                .as("超管建了又删了那条配置").isZero();
        // 出库与转换都动「乙仓的乙 SKU」：10 - 4（SALES_OUT）- 4（CONVERT_OUT）= 2；
        // 调拨入与转换入落在「乙仓的甲 SKU」上，那是另一条余额记录，不参与上一行的算式
        assertThat(movementTypes(target)).containsExactly("PURCHASE_IN", "SALES_OUT", "CONVERT_OUT");
        assertThat(quantityOf(target)).isEqualByComparingTo("2.0000");
        assertThat(movementTypes(source)).containsExactly("PURCHASE_IN", "TRANSFER_OUT", "STOCKTAKE_GAIN");
        assertThat(quantityOf(source)).isEqualByComparingTo("9.0000");
        assertThat(movementTypes(target.warehouseId(), source.skuId()))
                .containsExactly("TRANSFER_IN", "CONVERT_IN");
        assertThat(quantityOf(target.warehouseId(), source.skuId)).isEqualByComparingTo("8.0000");
        assertLedgerBalanced(source.warehouseId(), source.skuId());
        assertLedgerBalanced(target.warehouseId(), source.skuId());
        assertLedgerBalanced(target.warehouseId(), target.skuId());
    }

    // ==================================================================
    // 夹具
    // ==================================================================

    /** 夹具用到的仓与 SKU；实现 {@link StockedLike} 让断言可以按 (仓, SKU) 读账本。 */
    private record Stocked(Long warehouseId, Long skuId) implements StockedLike {
    }

    /** {@code WAREHOUSE_CONFIRM} 模式下「已确认收货但尚未上架」的组合：此刻既无余额也无流水。 */
    private record PendingPutaway(Long warehouseId, Long skuId, Long receiptId) implements StockedLike {
    }

    private interface StockedLike {
        Long warehouseId();

        Long skuId();
    }

    private Stocked stockNew(String tag) {
        loginAsAdmin();
        Long warehouseId = newWarehouse(tag);
        Long skuId = newSkuOfType(tag, "NON_STANDARD", "ON_SHELF");
        stockIn(warehouseId, skuId, tag);
        return new Stocked(warehouseId, skuId);
    }

    /**
     * 真实「采购单 → 提交 → {@code WAREHOUSE_CONFIRM} 收货 → 确认」，停在等待上架的状态。
     *
     * <p>刻意选这条链路：此刻该仓<b>既没有余额行也没有流水</b>，
     * 「拒绝后什么都没发生」可以断成「仍是空集」，比在已有账本上比数字更难误判。
     */
    private PendingPutaway stockPendingPutaway(String tag) {
        loginAsAdmin();
        Long warehouseId = newWarehouse(tag);
        Long skuId = newSkuOfType(tag, "NON_STANDARD", "ON_SHELF");
        Long supplierId = newPurchasableSupplier(tag, skuId);
        PurchaseOrderVO order = purchaseOrderService.create(
                orderForm(supplierId, warehouseId, skuId, QUANTITY, PRICE), prefix + ":" + tag + ":po");
        submitOrder(order.getId());
        PurchaseReceiptCreateForm form = new PurchaseReceiptCreateForm();
        form.setPurchaseOrderId(order.getId());
        form.setReceiptMode(ScmReceiptModeEnum.WAREHOUSE_CONFIRM.name());
        form.setRemark("写侧范围 IT 待上架");
        PurchaseReceiptVO receipt = purchaseReceiptService.create(form, prefix + ":" + tag + ":wc");
        confirmReceipt(receipt.getId(), QUANTITY);
        return new PendingPutaway(warehouseId, skuId, receipt.getId());
    }

    /** 真实「采购单 → 提交 → 收货 → 确认」（DIRECT：确认即物理入账）。 */
    private void stockIn(Long warehouseId, Long skuId, String tag) {
        Long supplierId = newPurchasableSupplier(tag, skuId);
        PurchaseOrderVO order = purchaseOrderService.create(
                orderForm(supplierId, warehouseId, skuId, QUANTITY, PRICE), prefix + ":" + tag + ":po");
        submitOrder(order.getId());
        PurchaseReceiptVO receipt = createReceipt(order.getId());
        confirmReceipt(receipt.getId(), QUANTITY);
    }

    /** 一名 {@code administratorFlag=false} 的仓管，只授权列出的那几个仓。 */
    private Long keeper(String tag, Long... warehouseIds) {
        Long employeeId = newEmployee(tag);
        for (Long warehouseId : warehouseIds) {
            authorize(employeeId, warehouseId);
        }
        return employeeId;
    }

    /** 直接插授权行：范围维护本身由读侧用例覆盖，本类只需要一个「只有某几个仓」的身份。 */
    private void authorize(Long employeeId, Long warehouseId) {
        jdbc.update("INSERT INTO employee_warehouse_scope (employee_id, warehouse_id) VALUES (?, ?)",
                employeeId, warehouseId);
        evictMybatisCache();
    }

    private Long newEmployee(String tag) {
        loginAsAdmin();
        String loginName = (prefix + "-" + tag).toUpperCase(Locale.ROOT);
        jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                        + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                UUID.randomUUID().toString().replace("-", ""), loginName, "$argon2id$it-placeholder", "仓管" + tag);
        Long employeeId = jdbc.queryForObject(
                "SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
        assertThat(jdbc.queryForObject(
                "SELECT administrator_flag FROM t_employee WHERE employee_id = ?", Boolean.class, employeeId))
                .as("正式业务角色的验收账号禁止超管位（裁决第 5 条）").isFalse();
        evictMybatisCache();
        return employeeId;
    }

    // ------------------------------------------------------------------
    // 请求表单
    // ------------------------------------------------------------------

    private InventoryOutboundAddForm outboundForm(Stocked stocked, String quantity) {
        InventoryOutboundAddForm form = new InventoryOutboundAddForm();
        form.setWarehouseId(stocked.warehouseId());
        form.setRemark("写侧范围 IT 出库");
        InventoryOutboundAddForm.Item item = new InventoryOutboundAddForm.Item();
        item.setSkuId(stocked.skuId());
        item.setQuantity(new BigDecimal(quantity));
        form.setItems(new ArrayList<>(List.of(item)));
        return form;
    }

    private InventoryStocktakeAddForm stocktakeForm(Stocked stocked, String actualQuantity) {
        InventoryStocktakeAddForm form = new InventoryStocktakeAddForm();
        form.setWarehouseId(stocked.warehouseId());
        form.setRemark("写侧范围 IT 盘点");
        InventoryStocktakeAddForm.Item item = new InventoryStocktakeAddForm.Item();
        item.setSkuId(stocked.skuId());
        item.setActualQuantity(new BigDecimal(actualQuantity));
        form.setItems(new ArrayList<>(List.of(item)));
        return form;
    }

    private InventoryLossGainAddForm lossGainForm(Stocked stocked, String quantity) {
        InventoryLossGainAddForm form = new InventoryLossGainAddForm();
        form.setAdjustType("LOSS");
        form.setWarehouseId(stocked.warehouseId());
        form.setReason("写侧范围 IT 报损");
        form.setRemark("写侧范围 IT 报损");
        InventoryLossGainAddForm.Item item = new InventoryLossGainAddForm.Item();
        item.setSkuId(stocked.skuId());
        item.setQuantity(new BigDecimal(quantity));
        form.setItems(new ArrayList<>(List.of(item)));
        return form;
    }

    /** 审批与驳回共用一个表单形状；意见对驳回必填，对审批可选。 */
    private InventoryLossGainAuditForm lossGainAuditForm(int version) {
        InventoryLossGainAuditForm form = new InventoryLossGainAuditForm();
        form.setVersion(version);
        form.setAuditOpinion("写侧范围 IT 意见");
        return form;
    }

    private InventoryConversionAuditForm conversionAuditForm(int version) {
        InventoryConversionAuditForm form = new InventoryConversionAuditForm();
        form.setVersion(version);
        form.setAuditOpinion("写侧范围 IT 意见");
        return form;
    }

    private InventoryConversionAddForm conversionForm(Long warehouseId, Long sourceSkuId, Long targetSkuId) {
        InventoryConversionAddForm form = new InventoryConversionAddForm();
        form.setWarehouseId(warehouseId);
        form.setConvertType("SPLIT");
        form.setReason("整件拆零");
        form.setRemark("写侧范围 IT 转换");
        InventoryConversionAddForm.Item item = new InventoryConversionAddForm.Item();
        item.setSourceSkuId(sourceSkuId);
        item.setSourceQuantity(MOVED);
        item.setSourceUnit("kg");
        item.setTargetSkuId(targetSkuId);
        item.setTargetQuantity(MOVED);
        item.setTargetUnit("kg");
        form.setItems(new ArrayList<>(List.of(item)));
        return form;
    }

    private InventoryTransferAddForm transferForm(Long from, Long to, Long skuId, String quantity) {
        InventoryTransferAddForm form = new InventoryTransferAddForm();
        form.setFromWarehouseId(from);
        form.setToWarehouseId(to);
        form.setRemark("写侧范围 IT 调拨");
        InventoryTransferAddForm.Item item = new InventoryTransferAddForm.Item();
        item.setSkuId(skuId);
        item.setQuantity(new BigDecimal(quantity));
        form.setItems(new ArrayList<>(List.of(item)));
        return form;
    }

    private InventoryWarningThresholdAddForm thresholdForm(Long warehouseId, Long skuId, String warnMin) {
        InventoryWarningThresholdAddForm form = new InventoryWarningThresholdAddForm();
        form.setWarehouseId(warehouseId);
        form.setSkuId(skuId);
        form.setWarnMin(new BigDecimal(warnMin));
        form.setWarnMax(new BigDecimal("9999"));
        form.setRemark("写侧范围 IT 阈值");
        return form;
    }

    private ReserveInventoryFact reservationFact(Stocked stocked, String quantity, long sourceItemId) {
        return new ReserveInventoryFact(stocked.warehouseId(), stocked.skuId(),
                ScmInventorySourceDocumentTypeEnum.SALES_ORDER_ITEM.name(), 90000L, sourceItemId,
                new BigDecimal(quantity), OffsetDateTime.now(), null);
    }

    /**
     * 上架表单：乐观锁版本用 jdbc 直读，不走 {@code reloadReceipt}。
     *
     * <p>后者经采购查询服务，会按<b>采购员</b>范围判可见性 —— 那与本案要证的仓库范围是两件事，
     * 混进来会让「拒绝」有可能是读侧给的，而不是写侧守卫给的。
     */
    private PurchaseReceiptPutawayForm putawayForm(Long receiptId) {
        PurchaseReceiptPutawayForm form = new PurchaseReceiptPutawayForm();
        form.setId(receiptId);
        form.setVersion(versionOf("purchase_receipt", receiptId));
        return form;
    }

    /** 幂等键逐次唯一：本用例要证的是「这一次调用被拒」，不是命中上一次的幂等重放。 */
    private String putawayKey() {
        return prefix + ":wscope:putaway:" + UUID.randomUUID();
    }

    // ------------------------------------------------------------------
    // 库内事实读取（一律走 jdbc：拒绝之后还要读，不能让一级缓存给出旧值）
    // ------------------------------------------------------------------

    private BigDecimal quantityOf(StockedLike stocked) {
        return quantityOf(stocked.warehouseId(), stocked.skuId());
    }

    private BigDecimal quantityOf(Long warehouseId, Long skuId) {
        List<BigDecimal> rows = jdbc.queryForList("SELECT quantity FROM inventory_balance"
                        + " WHERE deleted = FALSE AND warehouse_id = ? AND sku_id = ?",
                BigDecimal.class, warehouseId, skuId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private BigDecimal reservedQuantityOf(StockedLike stocked) {
        return jdbc.queryForObject("SELECT reserved_quantity FROM inventory_balance"
                        + " WHERE deleted = FALSE AND warehouse_id = ? AND sku_id = ?",
                BigDecimal.class, stocked.warehouseId(), stocked.skuId());
    }

    /** 该 (仓库, SKU) 的活动流水类型序列，按业务时刻排序 —— 多一条、少一条、换类型都会失败。 */
    private List<String> movementTypes(StockedLike stocked) {
        return movementTypes(stocked.warehouseId(), stocked.skuId());
    }

    private List<String> movementTypes(Long warehouseId, Long skuId) {
        List<Map<String, Object>> rows = movementsOf(warehouseId, skuId);
        return rows.stream().map(row -> (String) row.get("movement_type")).toList();
    }

    private String statusOf(String table, Long id) {
        return jdbc.queryForObject("SELECT status FROM " + table + " WHERE id = ?", String.class, id);
    }

    /** 乐观锁版本从库里回读：拒绝只能由仓库范围引起，不能被 40921 顶在前面。 */
    private int versionOf(String table, Long id) {
        return jdbc.queryForObject("SELECT version FROM " + table + " WHERE id = ?", Integer.class, id);
    }

    private Long warehouseOf(String table, Long id) {
        return warehouseOf(table, "warehouse_id", id);
    }

    private Long warehouseOf(String table, String column, Long id) {
        return jdbc.queryForObject("SELECT " + column + " FROM " + table + " WHERE id = ?", Long.class, id);
    }

    private BigDecimal warnMinOf(Long id) {
        return jdbc.queryForObject("SELECT warn_min FROM inventory_warning_threshold WHERE id = ?",
                BigDecimal.class, id);
    }

    /** 单据首行的数量：证「失败的改单连明细都没换」。 */
    private BigDecimal quantityOfFirstItem(String itemTable, String documentColumn, Long documentId) {
        return jdbc.queryForObject("SELECT quantity FROM " + itemTable + " WHERE deleted = FALSE AND "
                + documentColumn + " = ? ORDER BY id LIMIT 1", BigDecimal.class, documentId);
    }

    /** 盘点单首行的账面量快照：改草稿会重新快照，失败的改单不该换掉它。 */
    private BigDecimal bookQuantityOfFirstItem(Long stocktakeId) {
        return jdbc.queryForObject("SELECT book_quantity FROM inventory_stocktake_item"
                        + " WHERE deleted = FALSE AND stocktake_id = ? ORDER BY id LIMIT 1",
                BigDecimal.class, stocktakeId);
    }

    /** {@code id} 传 {@code null} 即全表活动行数（用于「这张表没留下任何行」）。 */
    private int activeRowCount(String table, Long id) {
        return id == null
                ? jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE deleted = FALSE", Integer.class)
                : jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE deleted = FALSE AND id = ?",
                        Integer.class, id);
    }

    private int rowCount(String table, String predicate, Object... args) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE deleted = FALSE AND " + predicate,
                Integer.class, args);
    }

    private int authorizedWarehouseRowCount(Long employeeId) {
        return jdbc.queryForObject("SELECT count(*) FROM employee_warehouse_scope"
                        + " WHERE employee_id = ? AND deleted_flag = FALSE", Integer.class, employeeId);
    }

    // ------------------------------------------------------------------
    // 身份与权限
    // ------------------------------------------------------------------

    /** 夹具与反证用的不受范围约束身份，与基类 {@code setUpOperator} 同一员工。 */
    private void loginAsAdmin() {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(1L);
        employee.setActualName("Write scope IT admin");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(true);
        SmartRequestUtil.setRequestUser(employee);
    }

    private void loginAs(Long employeeId) {
        RequestEmployee employee = new RequestEmployee();
        employee.setEmployeeId(employeeId);
        employee.setActualName("库存写侧范围 IT");
        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        employee.setAdministratorFlag(false);
        employee.setDepartmentId(1L);
        SmartRequestUtil.setRequestUser(employee);
    }

    /**
     * 以某个非超管员工 + 一组功能权限执行一段逻辑。未点名的权限码取 Mockito 默认值 false。
     * <b>不可嵌套调用</b>：静态桩按线程生效，嵌套会互相覆盖。
     */
    private <T> T as(Long employeeId, Set<String> permissions, Supplier<T> body) {
        loginAs(employeeId);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            permissions.forEach(p -> stp.when(() -> StpUtil.hasPermission(p)).thenReturn(true));
            return body.get();
        }
    }

    /** {@link #as} 的无返回值形态：库存命令大多是 {@code void}。 */
    private void asRun(Long employeeId, Runnable body) {
        as(employeeId, Set.of(), () -> {
            body.run();
            return null;
        });
    }

    /** 超管位只用于夹具与反证，不作为任何权限结论的依据（裁决第 5 条）。 */
    private <T> T asAdmin(Supplier<T> body) {
        loginAsAdmin();
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            return body.get();
        }
    }

    /**
     * 越权写必须抛出与读侧同一个 {@link ScmDataScopeException}，并由 {@code ScmExceptionHandler}
     * 出与功能权限不足同一个 30005 信封 —— 调用方不能据此分辨「行不存在」与「行不归他管」。
     */
    private void assertDataScopeDenied(Runnable action) {
        assertThatThrownBy(action::run)
                .as("范围内没有这个仓就必须拒绝，且不能被伪装成「不存在」或别的业务码")
                .isExactlyInstanceOf(ScmDataScopeException.class)
                .hasMessage(UserErrorCode.NO_PERMISSION.getMsg());
        var envelope = new ScmExceptionHandler().handleDataScope(new ScmDataScopeException());
        assertThat(envelope.getCode()).isEqualTo(UserErrorCode.NO_PERMISSION.getCode());
        assertThat(envelope.getMsg()).isEqualTo(UserErrorCode.NO_PERMISSION.getMsg());
        assertThat(envelope.getOk()).isFalse();
    }
}
