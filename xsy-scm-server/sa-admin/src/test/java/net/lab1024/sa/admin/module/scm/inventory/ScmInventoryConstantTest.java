package net.lab1024.sa.admin.module.scm.inventory;

import net.lab1024.sa.admin.module.scm.common.error.ScmErrorCode;
import net.lab1024.sa.admin.module.scm.inventory.constant.InventoryErrorCode;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryConversionStatusEnum;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryConversionTypeEnum;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryLossGainStatusEnum;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryLossGainTypeEnum;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryMovementTypeEnum;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventorySourceDocumentTypeEnum;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryTransferStatusEnum;
import net.lab1024.sa.admin.module.scm.inventory.constant.ScmInventoryWarningStatusEnum;
import net.lab1024.sa.admin.module.scm.purchase.support.PurchaseInventoryContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * W6 库存域常量与错误码（W6 Target Design §12.2）。
 *
 * <p>三件事必须被断言而不是靠人记得：
 * <ol>
 *   <li>{@code source_document_type} 的常量值与 W5 冻结的
 *       {@link PurchaseInventoryContract#SOURCE_DOCUMENT_TYPE} **逐字相等** ——
 *       库存域刻意不 import purchase 的常量（领域原语不该依赖某一个来源域），
 *       所以两个常量的一致性只能靠断言强制；</li>
 *   <li>枚举白名单只放行已支持的值（DB CHECK 与枚举必须同源）；</li>
 *   <li>错误码与**全部** SCM 域零交集 —— 这里用「全库码值集合无重复」的全局判据，
 *       而不是「W6 与 W5 比一次」，这样未来任何新域的撞码都会在这里先失败。</li>
 * </ol>
 */
@DisplayName("W6 库存域常量与错误码（单元）")
class ScmInventoryConstantTest {

    private static final String SCM_PACKAGE = "net.lab1024.sa.admin.module.scm";

    // ------------------------------------------------------------------
    // 常量一致性
    // ------------------------------------------------------------------

    @Test
    @DisplayName("source_document_type 常量与 W5 契约逐字一致（跨域常量靠断言强制）")
    void sourceDocumentTypeMatchesTheFrozenW5Contract() {
        assertThat(ScmInventorySourceDocumentTypeEnum.PURCHASE_RECEIPT_ITEM.name())
                .isEqualTo(PurchaseInventoryContract.SOURCE_DOCUMENT_TYPE)
                .isEqualTo("PURCHASE_RECEIPT_ITEM");
    }

    @Test
    @DisplayName("枚举白名单：只放行已支持的值，null / 空串 / 未支持值一律不放行")
    void enumsOnlyAllowSupportedValues() {
        assertThat(ScmInventoryMovementTypeEnum.isSupported("PURCHASE_IN")).isTrue();
        assertThat(ScmInventoryMovementTypeEnum.isSupported("SALES_OUT")).isTrue();
        assertThat(ScmInventoryMovementTypeEnum.isSupported("STOCKTAKE_GAIN")).isTrue();
        assertThat(ScmInventoryMovementTypeEnum.isSupported("STOCKTAKE_LOSS")).isTrue();
        assertThat(ScmInventoryMovementTypeEnum.isSupported("LOSS_REPORT")).isTrue();
        assertThat(ScmInventoryMovementTypeEnum.isSupported("GAIN_REPORT")).isTrue();
        assertThat(ScmInventoryMovementTypeEnum.isSupported("TRANSFER_OUT")).isTrue();
        assertThat(ScmInventoryMovementTypeEnum.isSupported("TRANSFER_IN")).isTrue();
        assertThat(ScmInventorySourceDocumentTypeEnum.isSupported("PURCHASE_RECEIPT_ITEM")).isTrue();
        assertThat(ScmInventorySourceDocumentTypeEnum.isSupported("SALES_OUTBOUND_ITEM")).isTrue();
        assertThat(ScmInventorySourceDocumentTypeEnum.isSupported("STOCKTAKE_ITEM")).isTrue();
        assertThat(ScmInventorySourceDocumentTypeEnum.isSupported("LOSS_GAIN_ITEM")).isTrue();
        assertThat(ScmInventorySourceDocumentTypeEnum.isSupported("TRANSFER_OUT_ITEM")).isTrue();
        assertThat(ScmInventorySourceDocumentTypeEnum.isSupported("TRANSFER_IN_ITEM")).isTrue();

        // STOCKTAKE_ADJUST 是**刻意不存在**的类型名（盘盈与盘亏必须是两个类型）；
        // UNKNOWN_IN 是一个**明确不存在**的类型名 —— 十个真实类型已全部落地，
        // 因此这里不能再拿「未实现的业务类型」当反例（每落地一个就要改一次），
        // 改用不可能被实现的名字，断言的是「白名单之外一律拒绝」这条性质本身。
        for (String rejected : new String[]{null, "", " ", "UNKNOWN_IN", "STOCKTAKE_ADJUST", "purchase_in"}) {
            assertThat(ScmInventoryMovementTypeEnum.isSupported(rejected))
                    .as("movement_type 不应放行 %s", rejected).isFalse();
            assertThat(ScmInventorySourceDocumentTypeEnum.isSupported(rejected))
                    .as("source_document_type 不应放行 %s", rejected).isFalse();
        }

        // 已落地 10 个流水类型（每组恰好 5 个，与 V33 的十方向分组一致）。
        assertThat(ScmInventoryMovementTypeEnum.values()).hasSize(10);
        // 来源类型：采购收货行 / 出库单行 / 销售订单行（预留）/ 盘点单行 / 报损报溢单行 /
        // 调拨转出行 / 调拨转入行 / 转换转出行 / 转换转入行 ——
        // 调拨与转换各占两个是**被迫的**：它们的同一条明细行会产生两条流水，
        // 共用一个来源类型会撞上 uk_inventory_movement_source_active。
        assertThat(ScmInventorySourceDocumentTypeEnum.values()).hasSize(9);
    }

    @Test
    @DisplayName("方向语义：入库为真、出库为假（与 ck_inventory_movement_snap 的方向分支同源）")
    void movementDirectionMatchesSnapshotConstraint() {
        assertThat(ScmInventoryMovementTypeEnum.PURCHASE_IN.isInbound()).isTrue();
        assertThat(ScmInventoryMovementTypeEnum.SALES_OUT.isInbound()).isFalse();
        // 盘盈是「入」、盘亏是「出」—— 方向由差异正负决定，但类型本身已经把它写死
        assertThat(ScmInventoryMovementTypeEnum.STOCKTAKE_GAIN.isInbound()).isTrue();
        assertThat(ScmInventoryMovementTypeEnum.STOCKTAKE_LOSS.isInbound()).isFalse();
        // 报损是「出」、报溢是「入」
        assertThat(ScmInventoryMovementTypeEnum.LOSS_REPORT.isInbound()).isFalse();
        assertThat(ScmInventoryMovementTypeEnum.GAIN_REPORT.isInbound()).isTrue();
        // 调拨转出是「出」（源仓）、转入是「入」（目标仓）
        assertThat(ScmInventoryMovementTypeEnum.TRANSFER_OUT.isInbound()).isFalse();
        assertThat(ScmInventoryMovementTypeEnum.TRANSFER_IN.isInbound()).isTrue();
        // 规格转换：源 SKU 一律「出」、目标 SKU 一律「入」
        assertThat(ScmInventoryMovementTypeEnum.CONVERT_OUT.isInbound()).isFalse();
        assertThat(ScmInventoryMovementTypeEnum.CONVERT_IN.isInbound()).isTrue();
        assertThat(ScmInventoryMovementTypeEnum.of("SALES_OUT"))
                .isEqualTo(ScmInventoryMovementTypeEnum.SALES_OUT);
        assertThat(ScmInventoryMovementTypeEnum.of("NOT_A_TYPE")).isNull();

        // 十个类型必须**恰好**分成两个方向组、每组五个：
        // 这是 ck_inventory_movement_snap「按方向分组」写法的前提 ——
        // 漏分类的类型会插不进流水（响亮失败），但漏了也没人会发现，所以在这里钉住。
        long inbound = Arrays.stream(ScmInventoryMovementTypeEnum.values())
                .filter(ScmInventoryMovementTypeEnum::isInbound).count();
        assertThat(inbound).as("入库方向的类型数").isEqualTo(5L);
        assertThat(ScmInventoryMovementTypeEnum.values().length - inbound).as("出库方向的类型数")
                .isEqualTo(5L);
    }

    @Test
    @DisplayName("规格转换：类型与状态白名单，且 Q13 不受影响（跨 SKU 而非同一 SKU 多单位）")
    void conversionEnumsMatchTheBackendWhitelist() {
        assertThat(ScmInventoryConversionTypeEnum.values()).hasSize(2);
        assertThat(ScmInventoryConversionTypeEnum.isSupported("SPLIT")).isTrue();
        assertThat(ScmInventoryConversionTypeEnum.isSupported("COMBINE")).isTrue();
        assertThat(ScmInventoryConversionTypeEnum.of("SPLIT"))
                .isEqualTo(ScmInventoryConversionTypeEnum.SPLIT);
        assertThat(ScmInventoryConversionTypeEnum.of("NOT_A_TYPE")).isNull();

        assertThat(ScmInventoryConversionStatusEnum.values()).hasSize(3);
        assertThat(ScmInventoryConversionStatusEnum.PENDING.isEditable()).isTrue();
        assertThat(ScmInventoryConversionStatusEnum.PENDING.isAuditable()).isTrue();
        assertThat(ScmInventoryConversionStatusEnum.PENDING.isDeletable()).isTrue();
        for (ScmInventoryConversionStatusEnum terminal : new ScmInventoryConversionStatusEnum[]{
                ScmInventoryConversionStatusEnum.COMPLETED,
                ScmInventoryConversionStatusEnum.REJECTED}) {
            assertThat(terminal.isEditable()).as("%s 不应可改", terminal).isFalse();
            assertThat(terminal.isAuditable()).as("%s 不应可再审", terminal).isFalse();
            assertThat(terminal.isDeletable()).as("%s 不应可删", terminal).isFalse();
        }

        // **Q13 不受影响**：规格转换是跨 SKU 的（源 SKU → 目标 SKU），
        // 两个 SKU 各自仍只锁一个记账单位。这里用「转换的两个来源类型都存在、
        // 且转换类型枚举里没有单位相关的取值」把「不做同一 SKU 多单位记账」钉住 ——
        // 一旦有人往枚举里加「单位」维度，这个断言会失败，强制走评审。
        assertThat(ScmInventorySourceDocumentTypeEnum.isSupported("CONVERT_OUT_ITEM")).isTrue();
        assertThat(ScmInventorySourceDocumentTypeEnum.isSupported("CONVERT_IN_ITEM")).isTrue();
        assertThat(ScmInventoryConversionTypeEnum.values())
                .extracting(ScmInventoryConversionTypeEnum::name)
                .doesNotContain("UNIT", "UNIT_CONVERT");
    }

    @Test
    @DisplayName("报损报溢：单据类型到流水类型的映射只有一处定义（方向不可能分叉）")
    void lossGainTypeMapsToMovementTypeInOnePlace() {
        assertThat(ScmInventoryLossGainTypeEnum.values()).hasSize(2);
        // LOSS -> LOSS_REPORT（出）、OVERFLOW -> GAIN_REPORT（入）
        assertThat(ScmInventoryLossGainTypeEnum.LOSS.getMovementType())
                .isEqualTo(ScmInventoryMovementTypeEnum.LOSS_REPORT);
        assertThat(ScmInventoryLossGainTypeEnum.OVERFLOW.getMovementType())
                .isEqualTo(ScmInventoryMovementTypeEnum.GAIN_REPORT);
        // isInbound 由流水类型推导，不是另存一个布尔 —— 两处独立存储必然有一天会不一致
        assertThat(ScmInventoryLossGainTypeEnum.LOSS.isInbound()).isFalse();
        assertThat(ScmInventoryLossGainTypeEnum.OVERFLOW.isInbound()).isTrue();

        assertThat(ScmInventoryLossGainTypeEnum.isSupported("LOSS")).isTrue();
        assertThat(ScmInventoryLossGainTypeEnum.isSupported("OVERFLOW")).isTrue();
        assertThat(ScmInventoryLossGainTypeEnum.isSupported("CHECK_ADJUST")).isFalse();
        assertThat(ScmInventoryLossGainTypeEnum.isSupported("CONVERT")).isFalse();
        assertThat(ScmInventoryLossGainTypeEnum.of("NOT_A_TYPE")).isNull();
    }

    @Test
    @DisplayName("报损报溢状态机：只有待审核可改 / 可删 / 可审，两个终态都不可回退")
    void lossGainStatusMachineIsTerminalAfterAudit() {
        assertThat(ScmInventoryLossGainStatusEnum.values()).hasSize(3);
        assertThat(ScmInventoryLossGainStatusEnum.PENDING.isEditable()).isTrue();
        assertThat(ScmInventoryLossGainStatusEnum.PENDING.isAuditable()).isTrue();
        assertThat(ScmInventoryLossGainStatusEnum.PENDING.isDeletable()).isTrue();
        for (ScmInventoryLossGainStatusEnum terminal : new ScmInventoryLossGainStatusEnum[]{
                ScmInventoryLossGainStatusEnum.COMPLETED, ScmInventoryLossGainStatusEnum.REJECTED}) {
            assertThat(terminal.isEditable()).as("%s 不应可改", terminal).isFalse();
            assertThat(terminal.isAuditable()).as("%s 不应可再审", terminal).isFalse();
            assertThat(terminal.isDeletable()).as("%s 不应可删", terminal).isFalse();
        }
        assertThat(ScmInventoryLossGainStatusEnum.isSupported("PENDING")).isTrue();
        assertThat(ScmInventoryLossGainStatusEnum.isSupported("DRAFT")).isFalse();
    }

    @Test
    @DisplayName("调拨状态机：两步式，在途不可取消、两个终态不可回退")
    void transferStatusMachineIsTwoStepAndInTransitIsNotCancellable() {
        assertThat(ScmInventoryTransferStatusEnum.values()).hasSize(4);

        // 草稿：可改、可发、可取消、可删
        assertThat(ScmInventoryTransferStatusEnum.DRAFT.isEditable()).isTrue();
        assertThat(ScmInventoryTransferStatusEnum.DRAFT.isShippable()).isTrue();
        assertThat(ScmInventoryTransferStatusEnum.DRAFT.isCancellable()).isTrue();
        assertThat(ScmInventoryTransferStatusEnum.DRAFT.isDeletable()).isTrue();
        assertThat(ScmInventoryTransferStatusEnum.DRAFT.isReceivable()).isFalse();
        assertThat(ScmInventoryTransferStatusEnum.DRAFT.isInTransit()).isFalse();

        // 在途：只能收货。**不可取消、不可改、不可删** ——
        // 货已经物理离开源仓，账上只能靠一张反向调拨单冲回。
        assertThat(ScmInventoryTransferStatusEnum.SHIPPED.isReceivable()).isTrue();
        assertThat(ScmInventoryTransferStatusEnum.SHIPPED.isInTransit()).isTrue();
        assertThat(ScmInventoryTransferStatusEnum.SHIPPED.isCancellable()).isFalse();
        assertThat(ScmInventoryTransferStatusEnum.SHIPPED.isEditable()).isFalse();
        assertThat(ScmInventoryTransferStatusEnum.SHIPPED.isDeletable()).isFalse();
        assertThat(ScmInventoryTransferStatusEnum.SHIPPED.isShippable()).isFalse();

        // 两个终态都不可回退
        for (ScmInventoryTransferStatusEnum terminal : new ScmInventoryTransferStatusEnum[]{
                ScmInventoryTransferStatusEnum.RECEIVED, ScmInventoryTransferStatusEnum.CANCELLED}) {
            assertThat(terminal.isEditable()).as("%s 不应可改", terminal).isFalse();
            assertThat(terminal.isShippable()).as("%s 不应可发出", terminal).isFalse();
            assertThat(terminal.isReceivable()).as("%s 不应可收货", terminal).isFalse();
            assertThat(terminal.isCancellable()).as("%s 不应可取消", terminal).isFalse();
            assertThat(terminal.isDeletable()).as("%s 不应可删", terminal).isFalse();
            assertThat(terminal.isInTransit()).as("%s 不是在途", terminal).isFalse();
        }

        assertThat(ScmInventoryTransferStatusEnum.isSupported("SHIPPED")).isTrue();
        assertThat(ScmInventoryTransferStatusEnum.isSupported("CONFIRMED")).isFalse();
    }

    @Test
    @DisplayName("预警状态判定：可用量 vs 阈值，边界取等号算正常")
    void warningStatusIsEvaluatedFromAvailableQuantity() {
        assertThat(ScmInventoryWarningStatusEnum.values()).hasSize(3);

        // 边界语义：**取等号算正常**（「不低于下限」= 刚好等于下限是正常的）。
        // 这条最容易写反，所以把等号两侧都钉住。
        assertThat(ScmInventoryWarningStatusEnum.evaluate(new BigDecimal("10"), new BigDecimal("10"), null))
                .as("恰好等于下限 → 正常").isEqualTo(ScmInventoryWarningStatusEnum.NORMAL);
        assertThat(ScmInventoryWarningStatusEnum.evaluate(new BigDecimal("9.9999"), new BigDecimal("10"), null))
                .as("略低于下限 → LOW").isEqualTo(ScmInventoryWarningStatusEnum.LOW);
        assertThat(ScmInventoryWarningStatusEnum.evaluate(new BigDecimal("100"), null, new BigDecimal("100")))
                .as("恰好等于上限 → 正常").isEqualTo(ScmInventoryWarningStatusEnum.NORMAL);
        assertThat(ScmInventoryWarningStatusEnum.evaluate(new BigDecimal("100.0001"), null, new BigDecimal("100")))
                .as("略高于上限 → HIGH").isEqualTo(ScmInventoryWarningStatusEnum.HIGH);

        // 只设一个边界时，另一个方向不预警
        assertThat(ScmInventoryWarningStatusEnum.evaluate(new BigDecimal("9999"), new BigDecimal("10"), null))
                .isEqualTo(ScmInventoryWarningStatusEnum.NORMAL);
        assertThat(ScmInventoryWarningStatusEnum.evaluate(BigDecimal.ZERO, null, new BigDecimal("100")))
                .isEqualTo(ScmInventoryWarningStatusEnum.NORMAL);

        // 没有余额行（available 为 null）按 0 计 → 设了下限就预警
        assertThat(ScmInventoryWarningStatusEnum.evaluate(null, new BigDecimal("1"), null))
                .as("设了下限却一件没有 → LOW").isEqualTo(ScmInventoryWarningStatusEnum.LOW);
        assertThat(ScmInventoryWarningStatusEnum.evaluate(null, null, new BigDecimal("1")))
                .as("只设上限时没有余额不算异常").isEqualTo(ScmInventoryWarningStatusEnum.NORMAL);

        // 异常判定与状态一致
        assertThat(ScmInventoryWarningStatusEnum.NORMAL.isAbnormal()).isFalse();
        assertThat(ScmInventoryWarningStatusEnum.LOW.isAbnormal()).isTrue();
        assertThat(ScmInventoryWarningStatusEnum.HIGH.isAbnormal()).isTrue();

        assertThat(ScmInventoryWarningStatusEnum.isSupported("LOW")).isTrue();
        assertThat(ScmInventoryWarningStatusEnum.isSupported("CRITICAL")).isFalse();
    }

    // ------------------------------------------------------------------
    // 错误码
    // ------------------------------------------------------------------

    @Test
    @DisplayName("错误码：恰好 58 个、码值冻结、段内无重复")
    void errorCodesAreFrozenAndUniqueWithinTheDomain() {
        assertThat(InventoryErrorCode.values()).hasSize(58);

        // W6-1 的 4 个码值冻结不变
        assertThat(InventoryErrorCode.INVENTORY_BALANCE_NOT_FOUND.getCode()).isEqualTo(40486);
        assertThat(InventoryErrorCode.INVENTORY_UNIT_MISMATCH.getCode()).isEqualTo(41001);
        assertThat(InventoryErrorCode.INVENTORY_DUPLICATE_INBOUND.getCode()).isEqualTo(41002);
        assertThat(InventoryErrorCode.INVENTORY_PARAM_INVALID.getCode()).isEqualTo(41003);

        // 出库波次新增：41011–41017
        // （41004–41008 已被 warehouse / purchase 占用，故出库从 41011 起）
        assertThat(InventoryErrorCode.INVENTORY_INSUFFICIENT_AVAILABLE.getCode()).isEqualTo(41011);
        assertThat(InventoryErrorCode.INVENTORY_OUTBOUND_PARAM_INVALID.getCode()).isEqualTo(41012);
        assertThat(InventoryErrorCode.INVENTORY_OUTBOUND_NOT_FOUND.getCode()).isEqualTo(41013);
        assertThat(InventoryErrorCode.INVENTORY_OUTBOUND_STATUS_INVALID.getCode()).isEqualTo(41014);
        assertThat(InventoryErrorCode.INVENTORY_DUPLICATE_OUTBOUND.getCode()).isEqualTo(41015);
        assertThat(InventoryErrorCode.INVENTORY_RESERVATION_INVALID.getCode()).isEqualTo(41016);
        assertThat(InventoryErrorCode.INVENTORY_OUTBOUND_EMPTY_ITEMS.getCode()).isEqualTo(41017);

        // 盘点波次新增：41019–41027（41018 已被 warehouse 占用，故盘点从 41019 起）
        assertThat(InventoryErrorCode.INVENTORY_STOCKTAKE_NOT_FOUND.getCode()).isEqualTo(41019);
        assertThat(InventoryErrorCode.INVENTORY_STOCKTAKE_STATUS_INVALID.getCode()).isEqualTo(41020);
        assertThat(InventoryErrorCode.INVENTORY_STOCKTAKE_EMPTY_ITEMS.getCode()).isEqualTo(41021);
        assertThat(InventoryErrorCode.INVENTORY_STOCKTAKE_PARAM_INVALID.getCode()).isEqualTo(41022);
        assertThat(InventoryErrorCode.INVENTORY_STOCKTAKE_BALANCE_MISSING.getCode()).isEqualTo(41023);
        assertThat(InventoryErrorCode.INVENTORY_STOCKTAKE_NEGATIVE_AFTER.getCode()).isEqualTo(41024);
        assertThat(InventoryErrorCode.INVENTORY_STOCKTAKE_BELOW_RESERVED.getCode()).isEqualTo(41025);
        assertThat(InventoryErrorCode.INVENTORY_DUPLICATE_STOCKTAKE.getCode()).isEqualTo(41026);
        assertThat(InventoryErrorCode.INVENTORY_STOCKTAKE_DUPLICATE_SKU.getCode()).isEqualTo(41027);

        // 报损报溢波次新增：41028–41037
        assertThat(InventoryErrorCode.INVENTORY_LOSS_GAIN_NOT_FOUND.getCode()).isEqualTo(41028);
        assertThat(InventoryErrorCode.INVENTORY_LOSS_GAIN_STATUS_INVALID.getCode()).isEqualTo(41029);
        assertThat(InventoryErrorCode.INVENTORY_LOSS_GAIN_EMPTY_ITEMS.getCode()).isEqualTo(41030);
        assertThat(InventoryErrorCode.INVENTORY_LOSS_GAIN_PARAM_INVALID.getCode()).isEqualTo(41031);
        assertThat(InventoryErrorCode.INVENTORY_LOSS_GAIN_BALANCE_MISSING.getCode()).isEqualTo(41032);
        assertThat(InventoryErrorCode.INVENTORY_LOSS_GAIN_NEGATIVE_AFTER.getCode()).isEqualTo(41033);
        assertThat(InventoryErrorCode.INVENTORY_LOSS_GAIN_BELOW_RESERVED.getCode()).isEqualTo(41034);
        assertThat(InventoryErrorCode.INVENTORY_DUPLICATE_LOSS_GAIN.getCode()).isEqualTo(41035);
        assertThat(InventoryErrorCode.INVENTORY_LOSS_GAIN_DUPLICATE_SKU.getCode()).isEqualTo(41036);
        assertThat(InventoryErrorCode.INVENTORY_LOSS_GAIN_REJECT_OPINION_REQUIRED.getCode()).isEqualTo(41037);

        // 调拨波次新增：41038–41048
        assertThat(InventoryErrorCode.INVENTORY_TRANSFER_NOT_FOUND.getCode()).isEqualTo(41038);
        assertThat(InventoryErrorCode.INVENTORY_TRANSFER_STATUS_INVALID.getCode()).isEqualTo(41039);
        assertThat(InventoryErrorCode.INVENTORY_TRANSFER_EMPTY_ITEMS.getCode()).isEqualTo(41040);
        assertThat(InventoryErrorCode.INVENTORY_TRANSFER_PARAM_INVALID.getCode()).isEqualTo(41041);
        assertThat(InventoryErrorCode.INVENTORY_TRANSFER_SAME_WAREHOUSE.getCode()).isEqualTo(41042);
        assertThat(InventoryErrorCode.INVENTORY_TRANSFER_INSUFFICIENT_AVAILABLE.getCode()).isEqualTo(41043);
        assertThat(InventoryErrorCode.INVENTORY_TRANSFER_UNIT_MISMATCH.getCode()).isEqualTo(41044);
        assertThat(InventoryErrorCode.INVENTORY_TRANSFER_DUPLICATE_SKU.getCode()).isEqualTo(41045);
        assertThat(InventoryErrorCode.INVENTORY_TRANSFER_SOURCE_BALANCE_MISSING.getCode()).isEqualTo(41046);
        assertThat(InventoryErrorCode.INVENTORY_DUPLICATE_TRANSFER.getCode()).isEqualTo(41047);
        assertThat(InventoryErrorCode.INVENTORY_TRANSFER_WAREHOUSE_DISABLED.getCode()).isEqualTo(41048);

        // 阈值预警波次新增：41049–41052
        assertThat(InventoryErrorCode.INVENTORY_WARNING_THRESHOLD_NOT_FOUND.getCode()).isEqualTo(41049);
        assertThat(InventoryErrorCode.INVENTORY_WARNING_THRESHOLD_DUPLICATE.getCode()).isEqualTo(41050);
        assertThat(InventoryErrorCode.INVENTORY_WARNING_THRESHOLD_INVALID.getCode()).isEqualTo(41051);
        assertThat(InventoryErrorCode.INVENTORY_WARNING_THRESHOLD_SKU_NOT_FOUND.getCode()).isEqualTo(41052);

        // 规格转换波次新增：41053–41064
        assertThat(InventoryErrorCode.INVENTORY_CONVERSION_NOT_FOUND.getCode()).isEqualTo(41053);
        assertThat(InventoryErrorCode.INVENTORY_CONVERSION_STATUS_INVALID.getCode()).isEqualTo(41054);
        assertThat(InventoryErrorCode.INVENTORY_CONVERSION_EMPTY_ITEMS.getCode()).isEqualTo(41055);
        assertThat(InventoryErrorCode.INVENTORY_CONVERSION_PARAM_INVALID.getCode()).isEqualTo(41056);
        assertThat(InventoryErrorCode.INVENTORY_CONVERSION_SAME_SKU.getCode()).isEqualTo(41057);
        assertThat(InventoryErrorCode.INVENTORY_CONVERSION_SOURCE_BALANCE_MISSING.getCode()).isEqualTo(41058);
        assertThat(InventoryErrorCode.INVENTORY_CONVERSION_SOURCE_UNIT_MISMATCH.getCode()).isEqualTo(41059);
        assertThat(InventoryErrorCode.INVENTORY_CONVERSION_TARGET_UNIT_MISMATCH.getCode()).isEqualTo(41060);
        assertThat(InventoryErrorCode.INVENTORY_CONVERSION_INSUFFICIENT_AVAILABLE.getCode()).isEqualTo(41061);
        assertThat(InventoryErrorCode.INVENTORY_DUPLICATE_CONVERSION.getCode()).isEqualTo(41062);
        assertThat(InventoryErrorCode.INVENTORY_CONVERSION_REJECT_OPINION_REQUIRED.getCode()).isEqualTo(41063);
        assertThat(InventoryErrorCode.INVENTORY_CONVERSION_WAREHOUSE_DISABLED.getCode()).isEqualTo(41064);

        // P0 裁决第 8 条：报损报溢禁止自建自审（41065 是现查 410xx 占用后紧邻库存块的空位）
        assertThat(InventoryErrorCode.INVENTORY_LOSS_GAIN_SELF_APPROVAL_FORBIDDEN.getCode())
                .isEqualTo(41065);

        Set<Integer> codes = Arrays.stream(InventoryErrorCode.values())
                .map(InventoryErrorCode::getCode).collect(Collectors.toCollection(LinkedHashSet::new));
        assertThat(codes).as("库存域段内出现重复码值").hasSize(58);

        // 段归属：404xx 一个（NOT_FOUND）+ 410xx 五十七个（业务冲突 / 参数非法）
        assertThat(codes.stream().filter(code -> code / 100 == 404).count()).isEqualTo(1L);
        assertThat(codes.stream().filter(code -> code / 100 == 410).count()).isEqualTo(57L);
        // 消息不得为空 —— 错误码没有可读消息等于没有错误码
        Arrays.stream(InventoryErrorCode.values())
                .forEach(code -> assertThat(code.getMsg()).isNotBlank());
    }

    @Test
    @DisplayName("撞码门禁：库存域的 57 个码与其它全部 SCM 域零交集")
    void inventoryErrorCodesDoNotCollideWithAnyOtherDomain() {
        List<Class<?>> domains = discoverErrorCodeEnums();
        Set<String> names = domains.stream()
                .map(Class::getSimpleName).collect(Collectors.toCollection(LinkedHashSet::new));
        assertThat(names)
                .as("扫描/回退清单未覆盖全部 SCM 错误码枚举，门禁会漏放")
                .containsAll(KNOWN_ENUMS);
        assertThat(domains.size())
                .as("扫描到的错误码枚举少于已知的 %d 个：扫描静默失效时请把新域补进 FALLBACK",
                        KNOWN_ENUMS.size())
                .isGreaterThanOrEqualTo(KNOWN_ENUMS.size());

        Set<Integer> others = new LinkedHashSet<>();
        for (Class<?> domain : domains) {
            if (domain == InventoryErrorCode.class) {
                continue;
            }
            for (Object constant : domain.getEnumConstants()) {
                others.add(((ScmErrorCode) constant).getCode());
            }
        }
        assertThat(others).as("其它 SCM 域的错误码集合不应为空").isNotEmpty();

        for (InventoryErrorCode code : InventoryErrorCode.values()) {
            assertThat(others)
                    .as("W6 错误码 %s(%d) 与既有 SCM 域已占用的码冲突", code.name(), code.getCode())
                    .doesNotContain(code.getCode());
        }
    }

    @Test
    @DisplayName("既有观察：40921 VERSION_CONFLICT 在多个域里被重复声明（W6 不复制这种写法）")
    void versionConflictIsDeliberatelyNotReplicatedByW6() {
        // 扫描结果暴露了一个既有事实：ProductErrorCode / PurchaseErrorCode / ScmCommonErrorCode
        // 都各自声明了 40921「数据已被其他操作修改」。按 AGENTS 的稳定码纪律，
        // 「可复用的既有码不复制语义」—— 库存域因此直接复用 ScmCommonErrorCode.VERSION_CONFLICT
        // （见 InventoryCommandService），不新增自己的 40921。
        //
        // 本用例把这个事实钉住：一旦有人给库存域也加一个 40921，上面的撞码门禁会失败，
        // 而这里说明**为什么** W6 选择复用而不是复制。清理既有三处重复属于 W1/W2/W5 的
        // 独立重构，不在 W6-1 范围内。
        assertThat(InventoryErrorCode.values())
                .extracting(InventoryErrorCode::getCode)
                .doesNotContain(40921);
    }

    // ------------------------------------------------------------------
    // classpath 扫描（与 W5 的撞码门禁同一手法：反查目录，未来新域自动纳入）
    // ------------------------------------------------------------------

    /**
     * 回退清单：classpath 不是展开目录时使用（例如从 jar 运行）。
     *
     * <p><b>新增 SCM 域时必须在这里补一行</b> —— 上面的
     * {@code containsAll(KNOWN_ENUMS)} 断言就是为此存在的：漏补会让门禁少比一个域，
     * 而那种漏放不会以任何形式报警。
     */
    private static final List<String> FALLBACK = List.of(
            "net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode",
            "net.lab1024.sa.admin.module.scm.product.constant.ProductErrorCode",
            "net.lab1024.sa.admin.module.scm.customer.constant.CustomerErrorCode",
            "net.lab1024.sa.admin.module.scm.supplier.constant.SupplierErrorCode",
            "net.lab1024.sa.admin.module.scm.pricing.constant.PricingErrorCode",
            "net.lab1024.sa.admin.module.scm.order.constant.OrderErrorCode",
            "net.lab1024.sa.admin.module.scm.purchase.constant.PurchaseErrorCode",
            "net.lab1024.sa.admin.module.scm.warehouse.constant.WarehouseErrorCode",
            InventoryErrorCode.class.getName());

    /**
     * 回退清单对应的简单名（用于断言覆盖完整）。
     */
    private static final Set<String> KNOWN_ENUMS = Set.of(
            "ScmCommonErrorCode", "ProductErrorCode", "CustomerErrorCode", "SupplierErrorCode",
            "PricingErrorCode", "OrderErrorCode", "PurchaseErrorCode", "WarehouseErrorCode",
            InventoryErrorCode.class.getSimpleName());

    private static List<Class<?>> discoverErrorCodeEnums() {
        List<Class<?>> found = new ArrayList<>();
        try {
            URL root = ScmErrorCode.class.getResource("/net/lab1024/sa/admin/module/scm");
            if (root != null && "file".equals(root.getProtocol())) {
                Path base = Paths.get(root.toURI());
                try (Stream<Path> walk = Files.walk(base)) {
                    for (Path path : walk.filter(Files::isRegularFile).toList()) {
                        String fileName = path.getFileName().toString();
                        if (!fileName.endsWith("ErrorCode.class") || fileName.contains("$")) {
                            continue;
                        }
                        String relative = base.relativize(path).toString()
                                .replace(File.separatorChar, '.').replace('/', '.');
                        Class<?> type = load(SCM_PACKAGE + "."
                                + relative.substring(0, relative.length() - ".class".length()));
                        if (type != null && type.isEnum() && ScmErrorCode.class.isAssignableFrom(type)) {
                            found.add(type);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // 目录不可读 → 走回退清单
        }
        if (!found.isEmpty()) {
            return found;
        }
        return FALLBACK.stream().map(ScmInventoryConstantTest::load)
                .filter(java.util.Objects::nonNull).collect(Collectors.toList());
    }

    private static Class<?> load(String fqn) {
        try {
            return Class.forName(fqn, false, ScmErrorCode.class.getClassLoader());
        } catch (Throwable ignored) {
            return null;
        }
    }
}
