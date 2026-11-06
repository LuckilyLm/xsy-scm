package com.xsy.scm.inventory;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xsy.scm.admin.module.system.login.domain.RequestEmployee;
import com.xsy.scm.base.common.code.UserErrorCode;
import com.xsy.scm.base.common.domain.PageParam;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.RequestUser;
import com.xsy.scm.base.common.enumeration.UserTypeEnum;
import com.xsy.scm.base.common.util.XsyRequestUtil;
import com.xsy.scm.common.ScmBaselineMigrationPgITBase;
import com.xsy.scm.common.ScmW6PgITBase;
import com.xsy.scm.common.dao.ScmBusinessNoDao;
import com.xsy.scm.common.error.ScmErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.handler.ScmExceptionHandler;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmWarehouseScopeGuard;
import com.xsy.scm.inventory.constant.InventoryErrorCode;
import com.xsy.scm.inventory.constant.ScmInventoryConversionStatusEnum;
import com.xsy.scm.inventory.constant.ScmInventoryConversionTypeEnum;
import com.xsy.scm.inventory.constant.ScmInventoryLossGainStatusEnum;
import com.xsy.scm.inventory.constant.ScmInventoryLossGainTypeEnum;
import com.xsy.scm.inventory.constant.ScmInventoryMovementTypeEnum;
import com.xsy.scm.inventory.constant.ScmInventorySourceDocumentTypeEnum;
import com.xsy.scm.inventory.constant.ScmInventoryTransferStatusEnum;
import com.xsy.scm.inventory.constant.ScmInventoryWarningStatusEnum;
import com.xsy.scm.inventory.dao.InventoryBalanceDao;
import com.xsy.scm.inventory.dao.InventoryMovementDao;
import com.xsy.scm.inventory.domain.InventoryLossGainFact;
import com.xsy.scm.inventory.domain.InventoryOutboundFact;
import com.xsy.scm.inventory.domain.InventoryStocktakeAdjustment;
import com.xsy.scm.inventory.domain.InventoryStocktakeFact;
import com.xsy.scm.inventory.domain.InventoryTransferFact;
import com.xsy.scm.inventory.domain.ReserveInventoryFact;
import com.xsy.scm.inventory.domain.entity.InventoryBalanceEntity;
import com.xsy.scm.inventory.domain.form.InventoryBalanceQueryForm;
import com.xsy.scm.inventory.domain.form.InventoryConversionAddForm;
import com.xsy.scm.inventory.domain.form.InventoryConversionAuditForm;
import com.xsy.scm.inventory.domain.form.InventoryLossGainAddForm;
import com.xsy.scm.inventory.domain.form.InventoryLossGainAuditForm;
import com.xsy.scm.inventory.domain.form.InventoryMovementQueryForm;
import com.xsy.scm.inventory.domain.form.InventoryOutboundAddForm;
import com.xsy.scm.inventory.domain.form.InventoryStocktakeAddForm;
import com.xsy.scm.inventory.domain.form.InventoryTransferAddForm;
import com.xsy.scm.inventory.domain.form.InventoryTransferQueryForm;
import com.xsy.scm.inventory.domain.form.InventoryWarningQueryForm;
import com.xsy.scm.inventory.domain.form.InventoryWarningThresholdAddForm;
import com.xsy.scm.inventory.domain.form.InventoryWarningThresholdQueryForm;
import com.xsy.scm.inventory.domain.vo.InventoryBalanceVO;
import com.xsy.scm.inventory.domain.vo.InventoryMovementVO;
import com.xsy.scm.inventory.domain.vo.InventoryStocktakeImportResultVO;
import com.xsy.scm.inventory.domain.vo.InventoryWarningVO;
import com.xsy.scm.inventory.service.InventoryCommandService;
import com.xsy.scm.inventory.service.InventoryConversionService;
import com.xsy.scm.inventory.service.InventoryFulfillmentService.Command;
import com.xsy.scm.inventory.service.InventoryFulfillmentService.Line;
import com.xsy.scm.inventory.service.InventoryFulfillmentService.Result;
import com.xsy.scm.inventory.service.InventoryFulfillmentService;
import com.xsy.scm.inventory.service.InventoryLossGainService;
import com.xsy.scm.inventory.service.InventoryOutboundService;
import com.xsy.scm.inventory.service.InventoryReservationService;
import com.xsy.scm.inventory.service.InventoryStocktakeImportService;
import com.xsy.scm.inventory.service.InventoryStocktakeService;
import com.xsy.scm.inventory.service.InventoryTransferQueryService;
import com.xsy.scm.inventory.service.InventoryTransferService;
import com.xsy.scm.inventory.service.InventoryWarningQueryService;
import com.xsy.scm.inventory.service.InventoryWarningThresholdService;
import com.xsy.scm.inventory.support.PurchaseInventoryContractImpl;
import com.xsy.scm.purchase.constant.ScmReceiptModeEnum;
import com.xsy.scm.purchase.domain.form.PurchaseOrderAddForm;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptConfirmForm;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptCreateForm;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptPutawayForm;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderVO;
import com.xsy.scm.purchase.domain.vo.PurchaseReceiptItemVO;
import com.xsy.scm.purchase.domain.vo.PurchaseReceiptVO;
import com.xsy.scm.purchase.support.NoOpPurchaseInventoryContract;
import com.xsy.scm.purchase.support.PurchaseInventoryContract;
import com.xsy.scm.report.domain.form.ScmNegativeStockReportQueryForm;
import com.xsy.scm.report.service.InventoryReportService;
import com.xsy.scm.report.support.ScmReportAccess;
import com.xsy.scm.trace.dao.TraceCorrectionDao;
import com.xsy.scm.trace.dao.TraceUnitDao;
import com.xsy.scm.trace.domain.form.TraceCorrectionDecisionForm;
import com.xsy.scm.trace.domain.form.TraceUnitBindForm;
import com.xsy.scm.trace.domain.form.TraceUnitIssueForm;
import com.xsy.scm.trace.domain.form.TraceUnitQueryForm;
import com.xsy.scm.trace.domain.form.TraceUnitVoidForm;
import com.xsy.scm.trace.domain.vo.TraceCorrectionVO;
import com.xsy.scm.trace.domain.vo.TraceUnitVO;
import com.xsy.scm.trace.service.TraceCorrectionService;
import com.xsy.scm.trace.service.TraceUnitService;
import com.xsy.scm.trace.support.TracePublicLinkBuilder;
import com.xsy.scm.warehouse.constant.WarehouseErrorCode;
import com.xsy.scm.warehouse.domain.form.WarehouseAddForm;
import com.xsy.scm.warehouse.domain.form.WarehouseScopeUpdateForm;
import com.xsy.scm.warehouse.domain.form.WarehouseUpdateForm;
import com.xsy.scm.warehouse.service.WarehouseScopeService;
import com.xsy.scm.warehouse.support.WarehouseDisableGuard;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_INSUFFICIENT_AVAILABLE;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_OUTBOUND_PARAM_INVALID;
import static com.xsy.scm.inventory.constant.InventoryErrorCode.INVENTORY_SOURCE_ALREADY_OUTBOUND;
import static com.xsy.scm.inventory.constant.ScmInventorySourceDocumentTypeEnum.SALES_ORDER_ITEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.xsy.scm.inventory.support.StocktakeSnapshotSigner;
import com.xsy.scm.inventory.service.InventorySaleUnitConverter;

class InventoryTest {

    @Nested
    class InventoryNegativeStockIT extends ScmW6PgITBase {
        @Autowired
        private InventoryReportService inventoryReportService;

        private Long negativeWarehouseId;

        @BeforeEach
        void createNegativeWarehouse() {
            WarehouseAddForm form = new WarehouseAddForm();
            form.setName(prefix + "负库存仓");
            form.setAllowNegativeStock(true);
            negativeWarehouseId = warehouseService.create(form);
        }

        @Override
        protected Long fixtureWarehouseId() {
            return negativeWarehouseId;
        }

        @Test
        @DisplayName("#1 开启开关后可以出到负库存，流水保存的数据与报表同步为负")
        void outboundBelowZeroIsAllowedAndReported() {
            Long skuId = newSkuOfType("NS1", "NON_STANDARD", "ON_SHELF");
            W6Fixture fixture = inboundFixture("NS1", skuId, "2.0000");
            confirmReceipt(fixture.receipt().getId(), "2.0000");

            inventoryCommandService.postSalesOutbound(new InventoryOutboundFact(
                negativeWarehouseId, skuId, 0L, 950101L, new BigDecimal("5.0000"), null,
                OffsetDateTime.now(), "test:1"));

            assertThat(balanceRow(negativeWarehouseId, skuId).getQuantity()).isEqualByComparingTo("-3.0000");
            assertThat(jdbc.queryForObject(
                "SELECT unit_snapshot FROM xsy_inventory_movement WHERE warehouse_id = ? AND sku_id = ? "
                    + "AND movement_type = 'SALES_OUT'",
                String.class, negativeWarehouseId, skuId)).isEqualTo("kg");

            ScmNegativeStockReportQueryForm query = new ScmNegativeStockReportQueryForm();
            query.setPageNum(1L);
            query.setPageSize(20L);
            query.setWarehouseId(negativeWarehouseId);
            var page = inventoryReportService.negativeStockList(query);
            assertThat(page.getList()).hasSize(1);
            var row = page.getList().getFirst();
            assertThat(row.getSkuId()).isEqualTo(skuId);
            assertThat(row.getQuantity()).isEqualByComparingTo("-3.0000");
            assertThat(row.getUnit()).isEqualTo("kg");
            // 夹具操作者没有成本权限，报表把金额列抹掉；成本本身回余额行核对
            assertThat(row.getAvgCost()).isNull();
            assertThat(row.getEstimatedAmount()).isNull();
            assertThat(balanceRow(negativeWarehouseId, skuId).getAvgCost()).isEqualByComparingTo("6.2000");
            assertThat(row.getLastOutboundAt()).isNotNull();
        }

        @Test
        @DisplayName("#2 无余额时必须以出库单位建零行；没有单位仍按 41011 拒绝")
        void balanceLessOutboundNeedsExplicitUnit() {
            Long skuId = newSkuOfType("NS2", "NON_STANDARD", "ON_SHELF");

            expectCode(() -> inventoryCommandService.postSalesOutbound(new InventoryOutboundFact(
                negativeWarehouseId, skuId, 0L, 950102L, new BigDecimal("1.0000"), null,
                OffsetDateTime.now(), "test:1")), 41011);
            assertThat(balanceRowCount(negativeWarehouseId, skuId)).isZero();

            inventoryCommandService.postSalesOutbound(new InventoryOutboundFact(
                negativeWarehouseId, skuId, 0L, 950103L, new BigDecimal("1.5000"), null,
                OffsetDateTime.now(), "test:1", "kg"));
            assertThat(balanceRow(negativeWarehouseId, skuId).getQuantity()).isEqualByComparingTo("-1.5000");
            assertThat(balanceRow(negativeWarehouseId, skuId).getUnit()).isEqualTo("kg");
        }

        @Test
        @DisplayName("#3 关闭负库存开关前必须先补平负余额（41010）")
        void cannotDisableWhileNegativeBalanceExists() {
            Long skuId = newSkuOfType("NS3", "NON_STANDARD", "ON_SHELF");
            inventoryCommandService.postSalesOutbound(new InventoryOutboundFact(
                negativeWarehouseId, skuId, 0L, 950104L, new BigDecimal("2.0000"), null,
                OffsetDateTime.now(), "test:1", "kg"));

            WarehouseUpdateForm update = new WarehouseUpdateForm();
            update.setId(negativeWarehouseId);
            update.setVersion(warehouseService.require(negativeWarehouseId).getVersion());
            update.setName(prefix + "负库存仓");
            update.setAllowNegativeStock(false);
            expectCode(() -> warehouseService.update(update), 41010);
            assertThat(warehouseService.require(negativeWarehouseId).getAllowNegativeStock()).isTrue();
        }

        @Test
        void otherOrderReservationIsPreservedWhileShippingBelowZero() {
            Long skuId = newSkuOfType("RESERVED-NEGATIVE", "NON_STANDARD", "ON_SHELF");
            jdbc.update("INSERT INTO xsy_inventory_balance(warehouse_id, sku_id, unit, quantity, reserved_quantity, avg_cost)"
                + " VALUES (?, ?, 'kg', 3, 2, 1)", negativeWarehouseId, skuId);
            evictMybatisCache();
            inventoryCommandService.postSalesOutbound(new InventoryOutboundFact(negativeWarehouseId, skuId, 0L,
                950105L, new BigDecimal("5.0000"), null, OffsetDateTime.now(), "test:1", "kg"));
            assertThat(balanceRow(negativeWarehouseId, skuId).getQuantity()).isEqualByComparingTo("-2");
            assertThat(balanceRow(negativeWarehouseId, skuId).getReservedQuantity()).isEqualByComparingTo("2");
        }
    }

    @Nested
    class InventoryUnitTest {
        @Nested

        class SalesReturnInventoryContractTest {
            @Test
            void salesReturnIsInboundAndHasDedicatedSource() {
                assertThat(ScmInventoryMovementTypeEnum.SALES_RETURN_IN.isInbound()).isTrue();
                assertThat(ScmInventorySourceDocumentTypeEnum.isSupported("SALES_RETURN_RECEIPT_ITEM")).isTrue();
            }
        }

        @Nested
        @DisplayName("W6 库存域常量与错误码（单元）")
        class ScmInventoryConstantTest {
            /**
             * SCM 根包：SCM 的业务代码全部在 {@code com.xsy.scm}。
             *
             * <p>写成常量而不是从类反推：这个值同时被用来拼 classpath 资源路径
             * （见 {@link #discoverErrorCodeEnums()}），两处必须一致，
             * 而从 {@code ScmErrorCode.class.getPackageName()} 反推会引入「包名对了但基础目录不对」
             * 的隐患 —— 本测试的立场是「包名是一个必须显式声明的约定」。
             */
            private static final String SCM_PACKAGE = "com.xsy.scm";

            // ------------------------------------------------------------------
            // 常量一致性
            // ------------------------------------------------------------------

            @Test
            @DisplayName("source_document_type 常量与 W5 约定逐字一致（跨域常量靠断言强制）")
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
                assertThat(ScmInventoryMovementTypeEnum.isSupported("CONVERT_OUT")).isTrue();
                assertThat(ScmInventoryMovementTypeEnum.isSupported("CONVERT_IN")).isTrue();
                // 促销赠品出库：方向为「出」，但不复用 SALES_OUT —— 赠品不进订单金额与应收，
                // 独立类型才能让毛利报表直接拆出赠品成本（ADM-12）。
                assertThat(ScmInventoryMovementTypeEnum.isSupported("PROMOTION_GIFT_OUT")).isTrue();
                assertThat(ScmInventorySourceDocumentTypeEnum.isSupported("PURCHASE_RECEIPT_ITEM")).isTrue();
                assertThat(ScmInventorySourceDocumentTypeEnum.isSupported("SALES_OUTBOUND_ITEM")).isTrue();
                assertThat(ScmInventorySourceDocumentTypeEnum.isSupported("STOCKTAKE_ITEM")).isTrue();
                assertThat(ScmInventorySourceDocumentTypeEnum.isSupported("LOSS_GAIN_ITEM")).isTrue();
                assertThat(ScmInventorySourceDocumentTypeEnum.isSupported("TRANSFER_OUT_ITEM")).isTrue();
                assertThat(ScmInventorySourceDocumentTypeEnum.isSupported("TRANSFER_IN_ITEM")).isTrue();
                assertThat(ScmInventorySourceDocumentTypeEnum.isSupported("CONVERT_OUT_ITEM")).isTrue();
                assertThat(ScmInventorySourceDocumentTypeEnum.isSupported("CONVERT_IN_ITEM")).isTrue();
                assertThat(ScmInventorySourceDocumentTypeEnum.isSupported("DELIVERY_ROUTE")).isTrue();
                // 赠品出库的源身份是保存的赠品权益而不是出库单行：赠品不挂订单行，
                // 防重锚点用 order_promotion_gift.id。
                assertThat(ScmInventorySourceDocumentTypeEnum.isSupported("ORDER_PROMOTION_GIFT")).isTrue();

                // STOCKTAKE_ADJUST 不存在（盘盈与盘亏必须是两个类型，不能合并成一个）；
                // UNKNOWN_IN 也不存在 —— 真实类型已全部落地，因此这里不能拿
                // 「未实现的业务类型」当反例（每落地一个就要改一次），
                // 改用不可能被实现的名字，断言的是「白名单之外一律拒绝」这条性质本身。
                for (String rejected : new String[]{null, "", " ", "UNKNOWN_IN", "STOCKTAKE_ADJUST", "purchase_in"}) {
                    assertThat(ScmInventoryMovementTypeEnum.isSupported(rejected))
                            .as("movement_type 不应放行 %s", rejected).isFalse();
                    assertThat(ScmInventorySourceDocumentTypeEnum.isSupported(rejected))
                            .as("source_document_type 不应放行 %s", rejected).isFalse();
                }

                // 已落地 12 个流水类型（6 入 6 出；出库组多出的一个是 PROMOTION_GIFT_OUT，
                // 入库组多出的是销售退货入库 SALES_RETURN_IN）。
                assertThat(ScmInventoryMovementTypeEnum.values()).hasSize(12);
                // 来源类型：采购收货行 / 出库单行 / 销售退货接收行 / 销售订单行（预留）/ 盘点单行 /
                // 报损报溢单行 / 调拨转出行 / 调拨转入行 / 转换转出行 / 转换转入行 / 促销赠品权益 /
                // 组合商品组成行 ——
                // 调拨与转换各占两个是必然的：它们的同一条明细行会产生两条流水，
                // 共用一个来源类型会撞上 uk_inventory_movement_source_active。
                // ORDER_BUNDLE_COMPONENT 是组合商品出库时消耗组成行库存的来源；
                // DELIVERY_ROUTE 是唯一不出现在流水里的来源类型：它只标在出库单头上回答
                // 「这张单是哪条线路发车的」，流水仍按 SALES_OUTBOUND_ITEM 记账。
                assertThat(ScmInventorySourceDocumentTypeEnum.values()).hasSize(13);
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
                // 促销赠品出库是「出」：赠品离开仓库，但它的来源是权益而不是订单行
                assertThat(ScmInventoryMovementTypeEnum.PROMOTION_GIFT_OUT.isInbound()).isFalse();
                assertThat(ScmInventoryMovementTypeEnum.of("SALES_OUT"))
                        .isEqualTo(ScmInventoryMovementTypeEnum.SALES_OUT);
                assertThat(ScmInventoryMovementTypeEnum.of("NOT_A_TYPE")).isNull();

                // 十二个类型必须恰好分成两个方向组、6 入 6 出：
                // 这是 ck_inventory_movement_snap「按方向分组」写法的前提 ——
                // 漏分类的类型会插不进流水（响亮失败），但漏了也没人会发现，所以在这里钉住。
                long inbound = Arrays.stream(ScmInventoryMovementTypeEnum.values())
                        .filter(ScmInventoryMovementTypeEnum::isInbound).count();
                assertThat(inbound).as("入库方向的类型数").isEqualTo(6L);
                assertThat(ScmInventoryMovementTypeEnum.values().length - inbound).as("出库方向的类型数")
                        .isEqualTo(6L);
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

                // 规格转换不改变「一个余额行只锁一个记账单位」：它是跨 SKU 的（源 SKU → 目标 SKU），
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

                // 待发货：可改、可发、可取消、可删
                assertThat(ScmInventoryTransferStatusEnum.PENDING.isEditable()).isTrue();
                assertThat(ScmInventoryTransferStatusEnum.PENDING.isShippable()).isTrue();
                assertThat(ScmInventoryTransferStatusEnum.PENDING.isCancellable()).isTrue();
                assertThat(ScmInventoryTransferStatusEnum.PENDING.isDeletable()).isTrue();
                assertThat(ScmInventoryTransferStatusEnum.PENDING.isReceivable()).isFalse();
                assertThat(ScmInventoryTransferStatusEnum.PENDING.isInTransit()).isFalse();

                // 在途：只能收货，不可取消、不可改、不可删 ——
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

                // 边界语义：取等号算正常（「不低于下限」= 刚好等于下限是正常的）。
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
            @DisplayName("错误码：恰好 63 个、码值保存、段内无重复")
            void errorCodesAreFrozenAndUniqueWithinTheDomain() {
                assertThat(InventoryErrorCode.values()).hasSize(63);

                // 以下 4 个码值保存不变
                assertThat(InventoryErrorCode.INVENTORY_BALANCE_NOT_FOUND.getCode()).isEqualTo(40486);
                assertThat(InventoryErrorCode.INVENTORY_UNIT_MISMATCH.getCode()).isEqualTo(41001);
                assertThat(InventoryErrorCode.INVENTORY_DUPLICATE_INBOUND.getCode()).isEqualTo(41002);
                assertThat(InventoryErrorCode.INVENTORY_PARAM_INVALID.getCode()).isEqualTo(41003);

                // 出库：41011–41017
                // （41004–41008 已被 warehouse / purchase 占用，故出库从 41011 起）
                assertThat(InventoryErrorCode.INVENTORY_INSUFFICIENT_AVAILABLE.getCode()).isEqualTo(41011);
                assertThat(InventoryErrorCode.INVENTORY_OUTBOUND_PARAM_INVALID.getCode()).isEqualTo(41012);
                assertThat(InventoryErrorCode.INVENTORY_OUTBOUND_NOT_FOUND.getCode()).isEqualTo(41013);
                assertThat(InventoryErrorCode.INVENTORY_OUTBOUND_STATUS_INVALID.getCode()).isEqualTo(41014);
                assertThat(InventoryErrorCode.INVENTORY_DUPLICATE_OUTBOUND.getCode()).isEqualTo(41015);
                assertThat(InventoryErrorCode.INVENTORY_RESERVATION_INVALID.getCode()).isEqualTo(41016);
                assertThat(InventoryErrorCode.INVENTORY_OUTBOUND_EMPTY_ITEMS.getCode()).isEqualTo(41017);

                // 盘点：41019–41027（41018 已被 warehouse 占用，故盘点从 41019 起）
                assertThat(InventoryErrorCode.INVENTORY_STOCKTAKE_NOT_FOUND.getCode()).isEqualTo(41019);
                assertThat(InventoryErrorCode.INVENTORY_STOCKTAKE_STATUS_INVALID.getCode()).isEqualTo(41020);
                assertThat(InventoryErrorCode.INVENTORY_STOCKTAKE_EMPTY_ITEMS.getCode()).isEqualTo(41021);
                assertThat(InventoryErrorCode.INVENTORY_STOCKTAKE_PARAM_INVALID.getCode()).isEqualTo(41022);
                assertThat(InventoryErrorCode.INVENTORY_STOCKTAKE_BALANCE_MISSING.getCode()).isEqualTo(41023);
                assertThat(InventoryErrorCode.INVENTORY_STOCKTAKE_NEGATIVE_AFTER.getCode()).isEqualTo(41024);
                assertThat(InventoryErrorCode.INVENTORY_STOCKTAKE_BELOW_RESERVED.getCode()).isEqualTo(41025);
                assertThat(InventoryErrorCode.INVENTORY_DUPLICATE_STOCKTAKE.getCode()).isEqualTo(41026);
                assertThat(InventoryErrorCode.INVENTORY_STOCKTAKE_DUPLICATE_SKU.getCode()).isEqualTo(41027);

                // 报损报溢：41028–41037
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

                // 调拨：41038–41048
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

                // 阈值预警：41049–41052
                assertThat(InventoryErrorCode.INVENTORY_WARNING_THRESHOLD_NOT_FOUND.getCode()).isEqualTo(41049);
                assertThat(InventoryErrorCode.INVENTORY_WARNING_THRESHOLD_DUPLICATE.getCode()).isEqualTo(41050);
                assertThat(InventoryErrorCode.INVENTORY_WARNING_THRESHOLD_INVALID.getCode()).isEqualTo(41051);
                assertThat(InventoryErrorCode.INVENTORY_WARNING_THRESHOLD_SKU_NOT_FOUND.getCode()).isEqualTo(41052);

                // 规格转换：41053–41064
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

                // 发车出库：来源单据已有出库单时重复出库必须被拒（兜 uk_inventory_outbound_source_active）。
                assertThat(InventoryErrorCode.INVENTORY_SOURCE_ALREADY_OUTBOUND.getCode()).isEqualTo(41066);

                Set<Integer> codes = Arrays.stream(InventoryErrorCode.values())
                        .map(InventoryErrorCode::getCode).collect(Collectors.toCollection(LinkedHashSet::new));
                assertThat(codes).as("库存域段内出现重复码值").hasSize(63);

                // 段归属：404xx 一个（NOT_FOUND）+ 410xx 六十二个（业务冲突 / 参数非法）
                assertThat(codes.stream().filter(code -> code / 100 == 404).count()).isEqualTo(1L);
                assertThat(codes.stream().filter(code -> code / 100 == 410).count()).isEqualTo(62L);
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
                // 扫描结果暴露了一个既有记录：ProductErrorCode / PurchaseErrorCode / ScmCommonErrorCode
                // 都各自声明了 40921「数据已被其他操作修改」。按 AGENTS 的稳定码纪律，
                // 「可复用的既有码不复制语义」—— 库存域因此直接复用 ScmCommonErrorCode.VERSION_CONFLICT
                // （见 InventoryCommandService），不新增自己的 40921。
                //
                // 本用例把这个记录钉住：一旦有人给库存域也加一个 40921，上面的撞码门禁会失败，
                // 本用例把这个记录钉住：库存域一旦也声明一个 40921，上面的撞码门禁就会失败。
                // 既有三处重复的清理是独立的重构，不属于库存域。
                assertThat(InventoryErrorCode.values())
                        .extracting(InventoryErrorCode::getCode)
                        .doesNotContain(40921);
            }

            // ------------------------------------------------------------------
            // classpath 扫描（撞码门禁的手法：反查目录，新域自动纳入）
            // ------------------------------------------------------------------

            /**
             * 回退清单：classpath 不是展开目录时使用（例如从 jar 运行）。
             *
             * <p><b>新增 SCM 域时必须在这里补一行</b> —— 上面的
             * {@code containsAll(KNOWN_ENUMS)} 断言就是为此存在的：漏补会让门禁少比一个域，
             * 而那种漏放不会以任何形式报警。
             */
            private static final List<String> FALLBACK = List.of(
                    "com.xsy.scm.common.error.ScmCommonErrorCode",
                    "com.xsy.scm.product.constant.ProductErrorCode",
                    "com.xsy.scm.customer.constant.CustomerErrorCode",
                    "com.xsy.scm.supplier.constant.SupplierErrorCode",
                    "com.xsy.scm.pricing.constant.PricingErrorCode",
                    "com.xsy.scm.order.constant.OrderErrorCode",
                    "com.xsy.scm.purchase.constant.PurchaseErrorCode",
                    "com.xsy.scm.warehouse.constant.WarehouseErrorCode",
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
                    URL root = ScmErrorCode.class.getResource("/" + SCM_PACKAGE.replace('.', '/'));
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

        @Nested

        class ScmTraceUnitSafetyPolicyTest {
            private final TraceUnitDao units = mock(TraceUnitDao.class);
            private final ScmWarehouseScopeGuard warehouses = mock(ScmWarehouseScopeGuard.class);
            private final TraceUnitService trace = new TraceUnitService(units, mock(ScmBusinessNoDao.class),
                    warehouses, mock(ScmDataScopeService.class), mock(TracePublicLinkBuilder.class));

            @Test
            void issuedLabelsDoNotRecoverQuotaFromUnattributedLotOutbounds() {
                var balance = new TraceUnitDao.LotBalanceRow();
                balance.setQuantity(new BigDecimal("6.0000"));
                when(units.findLot(51L)).thenReturn(new TraceUnitVO.Lot());
                when(units.lotWarehouseId(51L)).thenReturn(9L);
                when(units.lotUnit(51L)).thenReturn("kg");
                when(units.lockLotBalances(51L)).thenReturn(List.of(balance));
                when(units.sumIssuedByLot(51L)).thenReturn(new BigDecimal("6.0000"));

                var request = new TraceUnitIssueForm();
                request.setLotId(51L);
                request.setPackageType("BAG");
                request.setPackageCount(1);
                request.setQuantityPerPackage("1.0000");
                assertThatThrownBy(() -> trace.issue(request, "tester"))
                        .isInstanceOf(ScmBusinessException.class)
                        .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode())
                                .isEqualTo(41504));
                verify(units, never()).createUnit(any());
            }

            @Test
            void cannotBindNewPackageToShippedSalesOrderItem() {
                var unit = new TraceUnitVO.Unit();
                unit.setId(11L);
                unit.setStatus("ACTIVE");
                // 绑定先只读查码，再按 id 加锁复核；两处都要给出同一个有效包装码。
                when(units.findByCode("UNIT1")).thenReturn(unit);
                when(units.lockById(11L)).thenReturn(unit);
                var sorting = new TraceUnitVO.SortingItem();
                sorting.setItemId(19L);
                sorting.setTaskStatus("COMPLETED");
                sorting.setWarehouseId(9L);
                when(units.lockSortingItem(19L)).thenReturn(sorting);
                when(units.countConfirmedOutboundBySortingItem(19L)).thenReturn(1);
                var request = new TraceUnitBindForm();
                request.setUnitCode("UNIT1");
                request.setSortingTaskItemId(19L);

                assertThatThrownBy(() -> trace.bind(request, "tester"))
                        .isInstanceOf(ScmBusinessException.class)
                        .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode())
                                .isEqualTo(41526));
                verify(units, never()).insertBinding(any(), any(), any(), any(), any(), any(),
                        any(), any(), any(), any(), any());
            }

            @Test
            void correctionApprovedAfterFormalOutboundMustNotAppendUnbind() {
                var corrections = mock(TraceCorrectionDao.class);
                var service = new TraceCorrectionService(corrections, units, warehouses,
                        mock(ScmDataScopeService.class));
                var record = new TraceCorrectionVO();
                record.setId(31L);
                record.setUnitId(11L);
                record.setLotId(51L);
                record.setBindingId(21L);
                record.setStatus("PENDING");
                record.setRequestedBy("applicant");
                when(corrections.lockById(31L)).thenReturn(record);
                var unit = new TraceUnitVO.Unit();
                unit.setId(11L);
                unit.setStatus("ACTIVE");
                when(units.lockById(11L)).thenReturn(unit);
                when(units.findActiveBindingId(11L)).thenReturn(21L);
                when(corrections.countConfirmedOutboundByBinding(21L)).thenReturn(1);
                var decision = new TraceCorrectionDecisionForm();
                decision.setId(31L);
                decision.setVersion(0);

                assertThatThrownBy(() -> service.approve(decision, "reviewer"))
                        .isInstanceOf(ScmBusinessException.class)
                        .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode())
                                .isEqualTo(41525));
                verify(corrections, never()).insertUnbind(any(), any(), any(), any(), any(), any());
            }
        }
    }

    @Nested
    class InventoryW6PgIT extends ScmW6PgITBase {
        @Nested
        @DisplayName("库存域履约出库命令（PG IT）")
        class InventoryFulfillmentPgIT {
            @Autowired
            private InventoryFulfillmentService fulfillment;

            @Autowired
            private InventoryReservationService reservationService;

            @Autowired
            private TransactionTemplate transactionTemplate;

            @Test
            @DisplayName("存量全部被本单预留时也能出库：先归还预留、再按实发量扣实物")
            void consumesReservationInSameWarehouse() {
                Long warehouseId = seedWarehouseId();
                Long skuId = newOnShelfSku("ff1");
                stockIn(warehouseId, skuId, "ff1", "10.0000");
                Long orderId = confirmedOrder(newCustomer(), skuId, "10.0000");
                Long orderItemId = confirmedSalesOrderItemId(orderId);
                reserve(warehouseId, skuId, orderId, orderItemId, "10.0000");
                assertThat(reservedQuantity(warehouseId, skuId)).as("存量全被本单占住，可用量为 0").isEqualByComparingTo("10.0000");

                Result result = fulfill(9001L, warehouseId, line(orderId, orderItemId, skuId, "5.0000"));

                assertThat(result.outboundId()).as("有实物离仓就必须有出库单").isNotNull();
                assertThat(onHand(warehouseId, skuId)).as("只按实发量扣减").isEqualByComparingTo("5.0000");
                assertThat(reservedQuantity(warehouseId, skuId)).as("预留全额归还").isEqualByComparingTo("0.0000");
                assertThat(reservationStatus(orderItemId)).isEqualTo("CONSUMED");
                assertThat(reservationQuantity(orderItemId)).as("预留量不被改写，差额靠现算").isEqualByComparingTo("10.0000");
                assertThat(outboundItemCount(result.outboundId())).isEqualTo(1);
                assertThat(outboundItemOrderLine(result.outboundId())).isEqualTo(orderItemId);
                assertThat(outboundStatus(result.outboundId()))
                        .as("发车出库直接是 CONFIRMED，不经过待出库").isEqualTo("CONFIRMED");
                assertThat(outboundSource(result.outboundId()))
                        .containsEntry("source_document_type", ScmInventorySourceDocumentTypeEnum.DELIVERY_ROUTE.name())
                        .containsEntry("source_document_id", 9001L);
                assertThat(movementQuantity(result.outboundId())).isEqualByComparingTo("5.0000");
            }

            @Test
            @DisplayName("商城销售单位为箱，预留和发车按 kg 记账，且少拣全额释放占用")
            void convertedSalesUnitReservesAndShipsInStockUnit() {
                Long warehouseId = seedWarehouseId();
                Long skuId = newOnShelfSku("unitbox");
                stockIn(warehouseId, skuId, "unitbox", "100.0000");
                jdbc.update("UPDATE xsy_product_sku SET sale_unit='箱' WHERE id=?", skuId);
                evictMybatisCache();
                Long orderId = confirmedOrder(newCustomer(), skuId, "3.0000");
                Long itemId = confirmedSalesOrderItemId(orderId);
                reservationService.reserveForSalesOrder(warehouseId, orderId,
                        List.of(new InventoryReservationService.OrderReserveLine(
                                itemId, skuId, new BigDecimal("3.0000"), "kg", new BigDecimal("10.0000"))),
                        OffsetDateTime.now());
                assertThat(reservedQuantity(warehouseId, skuId)).isEqualByComparingTo("30.0000");

                Result result = fulfill(9020L, warehouseId, new Line(orderId, itemId, skuId,
                        new BigDecimal("2.0000"), "kg", new BigDecimal("10.0000")));

                assertThat(onHand(warehouseId, skuId)).isEqualByComparingTo("80.0000");
                assertThat(reservedQuantity(warehouseId, skuId)).isEqualByComparingTo("0.0000");
                assertThat(reservationStatus(itemId)).isEqualTo("CONSUMED");
                assertThat(movementQuantity(result.outboundId())).isEqualByComparingTo("20.0000");
                Map<String, Object> outboundItem = jdbc.queryForMap(
                        "SELECT quantity, stock_quantity FROM xsy_inventory_outbound_item WHERE outbound_id=?",
                        result.outboundId());
                assertThat((BigDecimal) outboundItem.get("quantity")).isEqualByComparingTo("2.0000");
                assertThat((BigDecimal) outboundItem.get("stock_quantity")).isEqualByComparingTo("20.0000");
            }

            @Test
            @DisplayName("少拣：预留整条收口为 CONSUMED，差额只存在于「预留量 − 出库量」")
            void shortPickKeepsSingleReservationRow() {
                Long warehouseId = seedWarehouseId();
                Long skuId = newOnShelfSku("ff2");
                stockIn(warehouseId, skuId, "ff2", "10.0000");
                Long orderId = confirmedOrder(newCustomer(), skuId, "5.0000");
                Long orderItemId = confirmedSalesOrderItemId(orderId);
                reserve(warehouseId, skuId, orderId, orderItemId, "5.0000");

                Result result = fulfill(9002L, warehouseId, line(orderId, orderItemId, skuId, "3.0000"));

                assertThat(onHand(warehouseId, skuId)).as("实发 3，存量 10 → 7").isEqualByComparingTo("7.0000");
                assertThat(reservedQuantity(warehouseId, skuId)).isEqualByComparingTo("0.0000");
                assertThat(reservationRowCount(orderItemId)).as("一条订单行终身一行预留，拆不出第二行").isEqualTo(1);
                assertThat(reservationStatus(orderItemId)).isEqualTo("CONSUMED");
                assertThat(movementQuantity(result.outboundId())).as("出库流水只记实发量").isEqualByComparingTo("3.0000");
            }

            @Test
            @DisplayName("预留仓与发货仓不同：那条预留被 RELEASED，实发量全部从线路仓库扣")
            void crossWarehouseReservationIsReleasedNotConsumed() {
                Long homeWarehouse = seedWarehouseId();
                Long routeWarehouse = newWarehouse("FF3B");
                Long skuId = newOnShelfSku("ff3");
                stockIn(homeWarehouse, skuId, "ff3", "10.0000");
                stockIn(routeWarehouse, skuId, "ff3b", "10.0000");
                Long orderId = confirmedOrder(newCustomer(), skuId, "4.0000");
                Long orderItemId = confirmedSalesOrderItemId(orderId);
                reserve(homeWarehouse, skuId, orderId, orderItemId, "4.0000");

                Result result = fulfill(9003L, routeWarehouse, line(orderId, orderItemId, skuId, "4.0000"));

                assertThat(reservedQuantity(homeWarehouse, skuId))
                        .as("别仓的占用当场回落，不留悬空").isEqualByComparingTo("0.0000");
                assertThat(onHand(homeWarehouse, skuId)).as("货没从那个仓走，存量不动").isEqualByComparingTo("10.0000");
                assertThat(onHand(routeWarehouse, skuId)).as("实发量全部从线路仓库扣").isEqualByComparingTo("6.0000");
                assertThat(reservationStatus(orderItemId)).as("整条释放而不是被消耗").isEqualTo("RELEASED");
                assertThat(movementWarehouse(result.outboundId())).isEqualTo(routeWarehouse);
            }

            @Test
            @DisplayName("实发为 0 的订单行：归还预留，但不生成出库单也不生成出库行")
            void zeroQuantityLineRetiresReservationWithoutOutbound() {
                Long warehouseId = seedWarehouseId();
                Long skuId = newOnShelfSku("ff4");
                stockIn(warehouseId, skuId, "ff4", "10.0000");
                Long orderId = confirmedOrder(newCustomer(), skuId, "5.0000");
                Long orderItemId = confirmedSalesOrderItemId(orderId);
                reserve(warehouseId, skuId, orderId, orderItemId, "5.0000");
                int outboundBefore = outboundHeaderCount();

                Result result = fulfill(9004L, warehouseId, line(orderId, orderItemId, skuId, "0.0000"));

                assertThat(result.outboundId()).as("整条线路一行都没发货 → 不存在出库单，而不是一张空单").isNull();
                assertThat(result.shippedLineCount()).isZero();
                assertThat(outboundHeaderCount()).as("一张出库单头都不该有").isEqualTo(outboundBefore);
                assertThat(reservedQuantity(warehouseId, skuId)).isEqualByComparingTo("0.0000");
                assertThat(reservationStatus(orderItemId)).as("没出库就是被释放").isEqualTo("RELEASED");
                assertThat(onHand(warehouseId, skuId)).isEqualByComparingTo("10.0000");
            }

            /**
             * 原子性只能在<b>真提交边界</b>上验：本用例关掉外层测试事务，让命令服务自己开事务，
             * 失败时才会真的把第一行已经写下的流水一起撤掉。留在共享事务里时服务上的
             * {@code @Transactional} 只是加入外层事务，第一行的写入照样读得到 ——
             * 那样断言「一行都没扣」就成了假绿。命令服务要求调用方持有事务，因此这里显式套一层
             * {@link TransactionTemplate}，否则「缺事务」的脚手架异常会被误当成业务拒绝。
             */
            @Test
            @DisplayName("库存不足：整条线路回滚，第一行也不留在账上")
            @Transactional(propagation = Propagation.NOT_SUPPORTED)
            void insufficientStockRollsBackWholeRoute() {
                // 种子仓 WH001 自 V186 起允许销售出库出负数，这里另建普通仓才能验「库存不足必须拒绝」。
                Long warehouseId = newWarehouse("缺货");
                Long skuId = newOnShelfSku("ff5");
                stockIn(warehouseId, skuId, "ff5", "6.0000");
                Long firstOrder = confirmedOrder(newCustomer(), skuId, "4.0000");
                Long secondOrder = confirmedOrder(newCustomer(), skuId, "4.0000");
                Long firstItem = confirmedSalesOrderItemId(firstOrder);
                Long secondItem = confirmedSalesOrderItemId(secondOrder);
                int movementsBefore = movementCount(warehouseId, skuId);
                int outboundBefore = outboundHeaderCount();

                expectCode(() -> transactionTemplate.executeWithoutResult(status -> fulfill(nextRouteId(), warehouseId,
                        line(firstOrder, firstItem, skuId, "4.0000"),
                        line(secondOrder, secondItem, skuId, "4.0000"))), INVENTORY_INSUFFICIENT_AVAILABLE.getCode());

                assertThat(onHand(warehouseId, skuId)).as("整条回滚后存量不变").isEqualByComparingTo("6.0000");
                assertThat(movementCount(warehouseId, skuId))
                        .as("第一行先写后回滚，账上不能留下它").isEqualTo(movementsBefore);
                assertThat(outboundHeaderCount()).as("出库单也必须一起消失").isEqualTo(outboundBefore);
            }

            @Test
            @DisplayName("同一条线路重复出库被部分唯一索引拒绝")
            void duplicateRouteIsRejectedByDatabase() {
                Long warehouseId = seedWarehouseId();
                Long skuId = newOnShelfSku("ff6");
                stockIn(warehouseId, skuId, "ff6", "10.0000");
                Long orderId = confirmedOrder(newCustomer(), skuId, "3.0000");
                Long orderItemId = confirmedSalesOrderItemId(orderId);
                Line line = line(orderId, orderItemId, skuId, "3.0000");
                fulfill(9006L, warehouseId, line);

                // 本用例到此为止：撞唯一索引会让 PostgreSQL 把当前事务置为 aborted，之后再查就是 25P02。
                expectCode(() -> fulfill(9006L, warehouseId, line), INVENTORY_SOURCE_ALREADY_OUTBOUND.getCode());
            }

            @Test
            @DisplayName("入参缺订单行、数量为负、同一订单行出现两次一律拒绝，且不落任何库存记录")
            void rejectsMalformedLines() {
                Long warehouseId = seedWarehouseId();
                Long skuId = newOnShelfSku("ff7");
                stockIn(warehouseId, skuId, "ff7", "10.0000");
                Long orderId = confirmedOrder(newCustomer(), skuId, "2.0000");
                Long orderItemId = confirmedSalesOrderItemId(orderId);
                int outboundBefore = outboundHeaderCount();

                expectCode(() -> fulfill(9007L, warehouseId, line(orderId, orderItemId, skuId, "-1.0000")),
                        INVENTORY_OUTBOUND_PARAM_INVALID.getCode());
                expectCode(() -> fulfillment.dispatchOutbound(new Command(9008L, warehouseId, OffsetDateTime.now(),
                        "履约 IT", List.of(new Line(orderId, null, skuId, BigDecimal.ONE)))),
                        INVENTORY_OUTBOUND_PARAM_INVALID.getCode());
                expectCode(() -> fulfill(9009L, warehouseId,
                        line(orderId, orderItemId, skuId, "1.0000"), line(orderId, orderItemId, skuId, "1.0000")),
                        INVENTORY_OUTBOUND_PARAM_INVALID.getCode());

                assertThat(onHand(warehouseId, skuId)).as("三次拒绝都没改账").isEqualByComparingTo("10.0000");
                assertThat(outboundHeaderCount()).as("也没留下出库单").isEqualTo(outboundBefore);
            }

            // ------------------------------------------------------------------
            // 夹具
            // ------------------------------------------------------------------

            private Result fulfill(Long routeId, Long warehouseId, Line... lines) {
                return fulfillment.dispatchOutbound(new Command(routeId, warehouseId, OffsetDateTime.now(), "履约 IT",
                        List.of(lines)));
            }

            /**
             * 真提交的用例要自己换来源单据 id：{@code (来源类型, 来源 id)} 上有出库单部分唯一索引，
             * 用固定的 9005 在同一个库上跑第二次就会被 41066「该来源单据已产生出库单」拒绝，
             * 看起来像业务规则回归，其实只是测试自己撞了上次运行留下的行。与 {@code prefix} 同一套规则。
             */
            private static final java.util.concurrent.atomic.AtomicLong ROUTE_ID_SEQ = new java.util.concurrent.atomic.AtomicLong(
                    System.currentTimeMillis() % 1_000_000_000L);

            private Long nextRouteId() {
                return ROUTE_ID_SEQ.incrementAndGet();
            }

            private Line line(Long orderId, Long orderItemId, Long skuId, String quantity) {
                return new Line(orderId, orderItemId, skuId, new BigDecimal(quantity));
            }

            private void reserve(Long warehouseId, Long skuId, Long orderId, Long orderItemId, String quantity) {
                reservationService.reserve(new ReserveInventoryFact(warehouseId, skuId, SALES_ORDER_ITEM.name(),
                        orderId, orderItemId, new BigDecimal(quantity), OffsetDateTime.now(), "履约 IT"));
            }

            /** 按真实采购链路入库到<b>指定仓库</b>：{@code createDraftOrder} 写死种子仓，跨仓用例必须自己指定。 */
            private void stockIn(Long warehouseId, Long skuId, String tag, String quantity) {
                Long supplierId = newPurchasableSupplier(tag, skuId);
                PurchaseOrderVO order = purchaseOrderService.create(
                        orderForm(supplierId, warehouseId, skuId, quantity, "6.2000"), prefix + ":" + tag + ":po");
                submitOrder(order.getId());
                confirmReceipt(createReceipt(order.getId()).getId(), quantity);
                evictMybatisCache();
            }

            private Long confirmedOrder(Long customerId, Long skuId, String actualQuantity) {
                return confirmedSalesOrder(customerId, skuId, "1.0000", actualQuantity);
            }

            private BigDecimal onHand(Long warehouseId, Long skuId) {
                return inventoryBalanceDao.lockByWarehouseAndSku(warehouseId, skuId).getQuantity();
            }

            private BigDecimal reservedQuantity(Long warehouseId, Long skuId) {
                return inventoryBalanceDao.lockByWarehouseAndSku(warehouseId, skuId).getReservedQuantity();
            }

            private String reservationStatus(Long orderItemId) {
                return jdbc.queryForObject("SELECT status FROM xsy_inventory_reservation WHERE source_document_item_id = ?",
                        String.class, orderItemId);
            }

            private BigDecimal reservationQuantity(Long orderItemId) {
                return jdbc.queryForObject("SELECT quantity FROM xsy_inventory_reservation WHERE source_document_item_id = ?",
                        BigDecimal.class, orderItemId);
            }

            private int reservationRowCount(Long orderItemId) {
                return jdbc.queryForObject("SELECT count(*) FROM xsy_inventory_reservation WHERE source_document_item_id = ?",
                        Integer.class, orderItemId);
            }

            private int outboundItemCount(Long outboundId) {
                return jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_inventory_outbound_item WHERE outbound_id = ? AND deleted = FALSE",
                        Integer.class, outboundId);
            }

            private Long outboundItemOrderLine(Long outboundId) {
                return jdbc.queryForObject(
                        "SELECT sales_order_item_id FROM xsy_inventory_outbound_item WHERE outbound_id = ?", Long.class, outboundId);
            }

            private String outboundStatus(Long outboundId) {
                return jdbc.queryForObject("SELECT status FROM xsy_inventory_outbound WHERE id = ?", String.class, outboundId);
            }

            private Map<String, Object> outboundSource(Long outboundId) {
                return jdbc.queryForMap("SELECT source_document_type, source_document_id FROM xsy_inventory_outbound WHERE id = ?",
                        outboundId);
            }

            private int outboundHeaderCount() {
                return jdbc.queryForObject("SELECT count(*) FROM xsy_inventory_outbound WHERE deleted = FALSE", Integer.class);
            }

            /**
             * 出库流水按 {@code SALES_OUTBOUND_ITEM} 记账，{@code source_document_id} 即出库单头 id；
             * 本类每个用例都只有一行出库明细，所以取单值即可（多行时会抛 NonUniqueResult，正是我们想看到的）。
             */
            private BigDecimal movementQuantity(Long outboundId) {
                return jdbc.queryForObject("SELECT quantity FROM xsy_inventory_movement"
                                + " WHERE source_document_type = ? AND source_document_id = ?",
                        BigDecimal.class, ScmInventorySourceDocumentTypeEnum.SALES_OUTBOUND_ITEM.name(), outboundId);
            }

            private Long movementWarehouse(Long outboundId) {
                return jdbc.queryForObject("SELECT warehouse_id FROM xsy_inventory_movement"
                                + " WHERE source_document_type = ? AND source_document_id = ?",
                        Long.class, ScmInventorySourceDocumentTypeEnum.SALES_OUTBOUND_ITEM.name(), outboundId);
            }
        }

        @Nested
        @DisplayName("W6 backfill 历史记录查询与对账（PG IT）")
        class ScmInventoryBackfillIT {
            /**
             * 本 SKU 的已确认收货行里，没有对应流水的条数（V19 Step 4 第一条判据的范围内重算）。
             *
             * <p>按 SKU 收口而不是全库：V22 之后「已确认收货但未上架」是合法中间态，
             * 那条判据在全库上不再成立，但它对 backfill 负责历史记录查询的那批 DIRECT 收货记录仍然成立。
             */
            private int confirmedLineWithoutMovementCount(Long skuId) {
                return jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_purchase_receipt_item ri "
                                + "JOIN xsy_purchase_receipt r ON r.id = ri.purchase_receipt_id AND r.deleted = FALSE "
                                + "WHERE r.status = 'CONFIRMED' AND ri.deleted = FALSE AND ri.received_quantity > 0 "
                                + "  AND ri.sku_id = ? "
                                + "  AND NOT EXISTS (SELECT 1 FROM xsy_inventory_movement m "
                                + "                  WHERE m.source_document_type = 'PURCHASE_RECEIPT_ITEM' "
                                + "                    AND m.source_document_item_id = ri.id AND m.deleted = FALSE)",
                        Integer.class, skuId);
            }

            // ------------------------------------------------------------------
            // 防重复处理与对账
            // ------------------------------------------------------------------

            @Test
            @DisplayName("#8 backfill 防重复处理与对账：重复执行零增量，Σ流水 = Σ收货、Σ余额 = Σ流水")
            void backfillIsIdempotentAndReconciles() {
                Long warehouseId = seedWarehouseId();
                Long skuId = newOnShelfSku("BF8");
                W6Fixture first = inboundFixture("BF8a", skuId, "6.0000");
                W6Fixture second = inboundFixture("BF8b", skuId, "4.0000");
                confirmReceipt(first.receipt().getId(), "6.0000");
                confirmReceipt(second.receipt().getId(), "4.0000");

                assertThat(movementCount(warehouseId, skuId)).isEqualTo(2);
                assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("10.0000");

                // 重放三段原文：前置检查 / 流水 / 余额（Step 4 的对账判据见 assertLedgerBalanced）
                runBackfillUnitPreCheck();
                runBackfillMovements();
                runBackfillBalances();

                // 零增量：没有第二条余额行、没有重复流水、数量没有被加第二次
                assertThat(balanceRowCount(warehouseId, skuId)).isEqualTo(1);
                assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("10.0000");
                assertThat(movementCount(warehouseId, skuId)).isEqualTo(2);
                assertThat(movementsOfReceiptItem(first.receiptItemId())).isEqualTo(1);
                assertThat(movementsOfReceiptItem(second.receiptItemId())).isEqualTo(1);

                // 对账恒等式（独立于 V19 的 SQL 重算一遍，避免「迁移自己的断言自己说了算」）
                assertLedgerBalanced(warehouseId, skuId);
                assertThat(confirmedLineWithoutMovementCount(skuId)).isZero();

                // 本 (warehouse, sku) 的流水合计 = 收货合计
                assertThat(jdbc.queryForObject(
                        "SELECT sum(quantity) FROM xsy_inventory_movement "
                                + "WHERE warehouse_id = ? AND sku_id = ? AND deleted = FALSE",
                        BigDecimal.class, warehouseId, skuId)).isEqualByComparingTo("10.0000");
            }

            // ------------------------------------------------------------------
            // 与实时路径不交叠
            // ------------------------------------------------------------------

            @Test
            @DisplayName("#9 backfill 与实时路径不交叠：backfill 后新 confirm 正常入库，重跑 backfill 零增量")
            void backfillAndRealtimeDoNotOverlap() {
                Long warehouseId = seedWarehouseId();
                Long skuId = newOnShelfSku("BF9");
                W6Fixture historical = inboundFixture("BF9a", skuId, "3.0000");
                confirmReceipt(historical.receipt().getId(), "3.0000");

                // 迁移跑过一遍之后，历史收货已经在账本里
                runBackfillMovements();
                runBackfillBalances();
                assertThat(movementCount(warehouseId, skuId)).isEqualTo(1);
                assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("3.0000");

                // 新 confirm 走实时路径（应用已启动），与 backfill 不冲突
                W6Fixture live = inboundFixture("BF9b", skuId, "2.0000");
                confirmReceipt(live.receipt().getId(), "2.0000");
                assertThat(movementCount(warehouseId, skuId)).isEqualTo(2);
                assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("5.0000");

                // 再跑一遍 backfill：新 confirm 的源记录已入库 → 零增量（三条路径互不重复入库）
                runBackfillMovements();
                runBackfillBalances();
                assertLedgerBalanced(warehouseId, skuId);
                assertThat(movementCount(warehouseId, skuId)).isEqualTo(2);
                assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("5.0000");
                assertThat(movementsOfReceiptItem(live.receiptItemId())).isEqualTo(1);
            }

            // ------------------------------------------------------------------
            // 历史记录查询顺序：id 顺序 ≠ confirmed_at 顺序时按 confirmed_at
            // ------------------------------------------------------------------

            @Test
            @DisplayName("#10 历史记录查询顺序（Q5 修正）：id 顺序 ≠ confirmed_at 顺序时，按 confirmed_at 历史记录查询")
            void backfillReplaysInConfirmedAtOrder() {
                Long warehouseId = seedWarehouseId();
                Long skuId = newOnShelfSku("BF10");
                // 先建 A 后建 B → item id 一定 A < B
                W6Fixture earlierId = inboundFixture("BF10a", skuId, "6.0000");
                W6Fixture laterId = inboundFixture("BF10b", skuId, "4.0000");
                assertThat(earlierId.receiptItemId()).isLessThan(laterId.receiptItemId());

                // 但先确认 B（item id 大的那张），再确认 A —— 于是 id 顺序与 confirmed_at 顺序相反。
                // 这正是「禁止仅 ORDER BY purchase_receipt_item.id」的原因。
                confirmReceipt(laterId.receipt().getId(), "4.0000");
                confirmReceipt(earlierId.receipt().getId(), "6.0000");

                // 固定成确定的历史时刻：B 在前、A 在后（相差 1 小时，顺序不可能被精度抹平）
                OffsetDateTime base = OffsetDateTime.parse("2026-01-01T00:00:00+08:00");
                overrideReceiptConfirmedAt(laterId.receipt().getId(), base);
                overrideReceiptConfirmedAt(earlierId.receipt().getId(), base.plusHours(1));

                // 把时钟拨回去：删掉实时路径写的流水，让 backfill 重新历史记录查询这段历史
                eraseMovementsFor(warehouseId, skuId);
                runBackfillMovements();

                List<Map<String, Object>> replayed = movementsOf(warehouseId, skuId);
                assertThat(replayed).hasSize(2);

                // 第一条必须是 confirmed_at 最早的那笔（B / 4.0000），before 从 0 起
                Map<String, Object> head = replayed.get(0);
                assertThat(head.get("source_document_item_id")).isEqualTo(laterId.receiptItemId());
                assertThat((BigDecimal) head.get("quantity")).isEqualByComparingTo("4.0000");
                assertThat((BigDecimal) head.get("before_quantity")).isEqualByComparingTo("0.0000");
                assertThat((BigDecimal) head.get("after_quantity")).isEqualByComparingTo("4.0000");

                // 第二条接着第一条（A / 6.0000），before = 4.0000
                Map<String, Object> tail = replayed.get(1);
                assertThat(tail.get("source_document_item_id")).isEqualTo(earlierId.receiptItemId());
                assertThat((BigDecimal) tail.get("quantity")).isEqualByComparingTo("6.0000");
                assertThat((BigDecimal) tail.get("before_quantity")).isEqualByComparingTo("4.0000");
                assertThat((BigDecimal) tail.get("after_quantity")).isEqualByComparingTo("10.0000");

                // 末条 after = 余额；若按 item id 历史记录查询，首条会是 6.0000 而 before 仍是 0 → 上面会失败
                assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("10.0000");
                assertLedgerBalanced(warehouseId, skuId);
            }

            // ------------------------------------------------------------------
            // 前置检查：混单位拒绝汇总
            // ------------------------------------------------------------------

            @Test
            @DisplayName("#14 Q13 backfill 前置检查：同 (wh,sku) 混单位 → 迁移段 RAISE EXCEPTION，不静默汇总")
            void backfillRefusesToAggregateMixedUnits() {
                Long warehouseId = seedWarehouseId();
                Long skuId = newOnShelfSku("BF14");
                // 两笔都按 kg 入库（实时路径允许，因为单位一致）
                W6Fixture a = freeInboundFixture("BF14a", skuId, "5.0000", "kg");
                W6Fixture b = freeInboundFixture("BF14b", skuId, "3.0000", "kg");
                confirmReceipt(a.receipt().getId(), "5.0000");
                confirmReceipt(b.receipt().getId(), "3.0000");
                assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("8.0000");

                // 单位一致时前置检查必须通过（否则它就成了一个永远失败的检查）
                runBackfillUnitPreCheck();

                // 人为制造「实时路径上线前遗留的脏数据」：同一 (wh, sku) 的历史收货行单位不一致。
                // 实时路径产生不出这种数据（异单位会被 41001 挡下），
                // 所以这正是前置检查存在的理由 —— 迁移要处理的是实时路径上线之前就躺在库里的历史。
                overrideReceiptItemUnit(b.receiptItemId(), "box");
                assertThat(jdbc.queryForObject(
                        "SELECT count(DISTINCT ri.purchase_unit_snapshot) FROM xsy_purchase_receipt_item ri "
                                + "JOIN xsy_purchase_receipt r ON r.id = ri.purchase_receipt_id AND r.deleted = FALSE "
                                + "WHERE r.status = 'CONFIRMED' AND ri.deleted = FALSE AND ri.sku_id = ?",
                        Integer.class, skuId)).isEqualTo(2);

                // 前置检查必须让迁移失败，而不是把 5kg + 3箱 汇总成 8
                expectSqlFailure(migrationSection(V19, "-- Step 1", "-- Step 2"));

                // 失败之后什么都没被改动（检查发生在任何写入之前）
                assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("8.0000");
                assertThat(balanceRow(warehouseId, skuId).getUnit()).isEqualTo("kg");
                assertThat(movementCount(warehouseId, skuId)).isEqualTo(2);
                assertThat(balanceRowCount(warehouseId, skuId)).isEqualTo(1);

                // 把脏数据改回一致后，前置检查恢复通过（证明失败的原因就是混单位）
                overrideReceiptItemUnit(b.receiptItemId(), "kg");
                runBackfillUnitPreCheck();
            }

            // ------------------------------------------------------------------
            // occurred_at / operator 取收货确认记录
            // ------------------------------------------------------------------

            @Test
            @DisplayName("#15 occurred_at / operator 取收货确认记录：实时与 backfill 同规则，不用 now() / ambient")
            void movementFactsComeFromTheReceiptConfirmation() {
                Long warehouseId = seedWarehouseId();
                Long skuId = newOnShelfSku("BF15");
                W6Fixture fx = inboundFixture("BF15", skuId, "5.0000");
                confirmReceipt(fx.receipt().getId(), "5.0000");

                OffsetDateTime confirmedAt = receiptConfirmedAt(fx.receipt().getId());
                String operator = receiptOperator(fx.receipt().getId());
                assertThat(confirmedAt).isNotNull();
                // ScmOperator.current() = userType:userId → 基类把请求身份设成 (ADMIN_EMPLOYEE=1, employeeId=1)
                assertThat(operator).isEqualTo("1:1");

                Map<String, Object> live = movementsOf(warehouseId, skuId).getFirst();
                // 精确相等，不是「大约相同」：流水的 occurred_at 就是收货确认时刻本身
                assertThat(timestampOf(live.get("occurred_at")).toInstant())
                        .isEqualTo(confirmedAt.toInstant());
                assertThat(live.get("operator")).isEqualTo(operator);
                assertThat(live.get("created_by")).isEqualTo(operator);

                // --- 判别性验证：如果库存侧用 now() / ambient operator，下面两条一定会失败 ---
                // 把收货确认记录改成一个历史时刻 + 一个哨兵操作者，再让 backfill 历史记录查询
                OffsetDateTime historical = OffsetDateTime.parse("2020-03-15T08:30:00+08:00");
                overrideReceiptConfirmedAt(fx.receipt().getId(), historical);
                jdbc.update("UPDATE xsy_purchase_receipt SET operator = ? WHERE id = ?",
                        "sentinel-operator", fx.receipt().getId());
                eraseMovementsFor(warehouseId, skuId);
                runBackfillMovements();

                Map<String, Object> replayed = movementsOf(warehouseId, skuId).getFirst();
                assertThat(timestampOf(replayed.get("occurred_at")).toInstant())
                        .as("历史记录查询的 occurred_at 必须等于收货确认时刻（2020-03-15），而不是迁移执行时刻")
                        .isEqualTo(historical.toInstant());
                assertThat(replayed.get("operator"))
                        .as("历史记录查询的 operator 必须等于收货单的操作者，而不是环境里的当前操作者")
                        .isEqualTo("sentinel-operator");
            }

            // ------------------------------------------------------------------
            // 对账判据的判别性
            // ------------------------------------------------------------------

            @Test
            @DisplayName("对账判据可判别：绕过流水改账面必须报错，查不存在的 (wh,sku) 不许静默通过")
            void reconciliationJudgeDetectsTamperingAndRefusesVacuousPass() {
                Long warehouseId = seedWarehouseId();
                Long skuId = newOnShelfSku("JUDGE");
                W6Fixture fx = inboundFixture("JUDGE", skuId, "6.0000");
                confirmReceipt(fx.receipt().getId(), "6.0000");
                assertLedgerBalanced(warehouseId, skuId);

                // 产品侧没有任何「改账面却不写流水」的入口，所以这条违规只能手工造
                jdbc.update("UPDATE xsy_inventory_balance SET quantity = quantity + 1 "
                                + "WHERE warehouse_id = ? AND sku_id = ? AND deleted = FALSE",
                        warehouseId, skuId);
                assertThatThrownBy(() -> assertLedgerBalanced(warehouseId, skuId))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContaining("方向净额不一致");

                // 传错 id 时流水与余额两个聚合都是 NULL，差额判据本身无从判别
                assertThatThrownBy(() -> assertLedgerBalanced(warehouseId, -1L))
                        .isInstanceOf(AssertionError.class)
                        .hasMessageContaining("没有活动余额行");
            }
        }

        @Nested
        @DisplayName("规格转换（PG IT）")
        class ScmInventoryConversionIT {
            @Autowired
            private InventoryConversionService conversionService;

            /**
             * 造一个按指定采购单位入库的 SKU，返回 skuId（余额行的记账单位 = 该采购单位）。
             */
            private Long stockedWithUnit(String suffix, String quantity, String purchaseUnit) {
                Long skuId = newSkuOfType(suffix, "NON_STANDARD", "ON_SHELF");
                W6Fixture fixture = freeInboundFixture(suffix, skuId, quantity, purchaseUnit);
                confirmReceipt(fixture.receipt().getId(), quantity);
                return skuId;
            }

            private static InventoryConversionAddForm.Item item(Long sourceSku, String sourceQty, String sourceUnit,
                                                                Long targetSku, String targetQty, String targetUnit) {
                InventoryConversionAddForm.Item row = new InventoryConversionAddForm.Item();
                row.setSourceSkuId(sourceSku);
                row.setSourceQuantity(new BigDecimal(sourceQty));
                row.setSourceUnit(sourceUnit);
                row.setTargetSkuId(targetSku);
                row.setTargetQuantity(new BigDecimal(targetQty));
                row.setTargetUnit(targetUnit);
                return row;
            }

            private InventoryConversionAddForm form(String type, Long warehouseId,
                                                    InventoryConversionAddForm.Item... items) {
                InventoryConversionAddForm form = new InventoryConversionAddForm();
                form.setWarehouseId(warehouseId);
                form.setConvertType(type);
                form.setItems(new ArrayList<>(List.of(items)));
                return form;
            }

            private int versionOf(Long id) {
                return jdbc.queryForObject(
                        "SELECT version FROM xsy_inventory_conversion WHERE id = ?", Integer.class, id);
            }

            private InventoryConversionAuditForm audit(Long id) {
                InventoryConversionAuditForm form = new InventoryConversionAuditForm();
                form.setVersion(versionOf(id));
                return form;
            }

            private String statusOf(Long id) {
                return jdbc.queryForObject(
                        "SELECT status FROM xsy_inventory_conversion WHERE id = ?", String.class, id);
            }

            private List<Map<String, Object>> convertMovements(Long wh, Long sku) {
                return movementsOf(wh, sku).stream()
                        .filter(m -> {
                            String type = String.valueOf(m.get("movement_type"));
                            return "CONVERT_OUT".equals(type) || "CONVERT_IN".equals(type);
                        })
                        .toList();
            }

            // ------------------------------------------------------------------
            // 核心：跨 SKU 两行余额 + 链式转换
            // ------------------------------------------------------------------

            @Test
            @DisplayName("整件拆零：源 SKU 出、目标 SKU 入（目标无余额行时由转换建立）")
            void splitConvertsAcrossTwoSkus() {
                Long wh = seedWarehouseId();
                Long source = stockedWithUnit("cv1", "5.0000", "箱");
                Long target = newSkuOfType("cv1t", "NON_STANDARD", "ON_SHELF");
                assertThat(balanceRow(wh, source).getUnit()).isEqualTo("箱");
                assertThat(balanceRow(wh, target)).as("目标 SKU 尚未入库").isNull();

                // 1 箱 → 10 kg（折算关系由单据声明）
                Long id = conversionService.create(form("SPLIT", wh,
                        item(source, "2.0000", "箱", target, "20.0000", "kg")));
                assertThat(statusOf(id)).isEqualTo("PENDING");
                assertThat(balanceRow(wh, source).getQuantity()).as("待审核不动库存").isEqualByComparingTo("5.0000");

                conversionService.approve(id, audit(id));

                assertThat(statusOf(id)).isEqualTo("COMPLETED");
                assertThat(balanceRow(wh, source).getQuantity()).isEqualByComparingTo("3.0000");
                assertThat(balanceRow(wh, target).getQuantity()).isEqualByComparingTo("20.0000");
                assertThat(balanceRow(wh, target).getUnit()).as("目标行由转换建立，单位取声明值")
                        .isEqualTo("kg");

                Map<String, Object> out = convertMovements(wh, source).getFirst();
                assertThat(out.get("movement_type")).isEqualTo("CONVERT_OUT");
                assertThat(out.get("source_document_type")).isEqualTo("CONVERT_OUT_ITEM");
                assertThat(new BigDecimal(String.valueOf(out.get("quantity")))).isEqualByComparingTo("2.0000");
                assertThat(new BigDecimal(String.valueOf(out.get("after_quantity")))).isEqualByComparingTo("3.0000");

                Map<String, Object> in = convertMovements(wh, target).getFirst();
                assertThat(in.get("movement_type")).isEqualTo("CONVERT_IN");
                assertThat(in.get("source_document_type")).isEqualTo("CONVERT_IN_ITEM");
                assertThat(new BigDecimal(String.valueOf(in.get("quantity")))).isEqualByComparingTo("20.0000");

                // 成本守恒的是总额，不是单价（折算率是人工声明的，跨单位单价必然不同）：
                // 2 箱 × 6.20 = 12.40，摊到 20 kg → 0.6200 / kg。
                assertThat(new BigDecimal(String.valueOf(out.get("unit_cost"))))
                        .as("转出腿 = 源 SKU 当时的均价").isEqualByComparingTo("6.2000");
                assertThat(new BigDecimal(String.valueOf(in.get("unit_cost"))))
                        .as("转入腿 = 转出腿总成本 ÷ 转入数量").isEqualByComparingTo("0.6200");
                assertThat(balanceRow(wh, source).getAvgCost()).as("转出腿不改变源均价")
                        .isEqualByComparingTo("6.2000");
                assertThat(balanceRow(wh, target).getAvgCost())
                        .as("目标行由转换建立，按转入成本入账而不是留在 0")
                        .isEqualByComparingTo("0.6200");
                assertThat(balanceRow(wh, source).getQuantity().multiply(new BigDecimal("6.2000"))
                        .add(balanceRow(wh, target).getQuantity()
                                .multiply(balanceRow(wh, target).getAvgCost())))
                        .as("转换前后总金额不变：3 箱 × 6.20 + 20 kg × 0.62 = 5 箱 × 6.20")
                        .isEqualByComparingTo("31.0000");
            }

            @Test
            @DisplayName("链式转换：同一单里 A→B 且 B→C（B 既是目标又是源）不死锁且结果正确")
            void chainedConversionDoesNotDeadlock() {
                Long wh = seedWarehouseId();
                Long a = stockedWithUnit("cv2a", "10.0000", "kg");
                Long b = newSkuOfType("cv2b", "NON_STANDARD", "ON_SHELF");
                Long c = newSkuOfType("cv2c", "NON_STANDARD", "ON_SHELF");

                // 两行：A→B（5），B→C（3）。B 在第一行是目标、在第二行是源 ——
                // 「逐行先源后目标」的实现会先锁 A 再锁 B、然后先锁 B 再锁 C，
                // 看起来没问题；但只要两行的 SKU 顺序反过来就会以相反顺序拿锁。
                // 正确实现把所有腿按 (skuId, 方向) 全局排序，因此这里能稳定通过。
                Long id = conversionService.create(form("SPLIT", wh,
                        item(a, "5.0000", "kg", b, "5.0000", "kg"),
                        item(b, "3.0000", "kg", c, "3.0000", "kg")));
                conversionService.approve(id, audit(id));

                assertThat(statusOf(id)).isEqualTo("COMPLETED");
                assertThat(balanceRow(wh, a).getQuantity()).isEqualByComparingTo("5.0000");
                // B：先入 5 再出 3 → 净 2。若实现成「先出后入」，B 的「出」会因为还没入而失败
                assertThat(balanceRow(wh, b).getQuantity()).as("链式：先入后出，净 2").isEqualByComparingTo("2.0000");
                assertThat(balanceRow(wh, c).getQuantity()).isEqualByComparingTo("3.0000");

                // 成本沿链条走完：A→B→C 全程 1:1 同单位，所以三行的均价都是 A 的 6.2000。
                // 基准若只取各 SKU 的期初均价，B 没有期初行 → 0，C 于是被记成零成本 ——
                // 与调拨清零是同一个缺陷，因此链式场景必须在测试里钉住。
                assertThat(balanceRow(wh, b).getAvgCost()).as("B 按本单转入腿加权，不是期初的 0")
                        .isEqualByComparingTo("6.2000");
                assertThat(balanceRow(wh, c).getAvgCost()).as("成本穿过中间的 B 到达 C")
                        .isEqualByComparingTo("6.2000");
                assertThat(convertMovements(wh, b).stream()
                        .filter(m -> "CONVERT_OUT".equals(String.valueOf(m.get("movement_type"))))
                        .map(m -> new BigDecimal(String.valueOf(m.get("unit_cost"))))
                        .findFirst().orElseThrow())
                        .as("B 的转出腿 = 它进完之后的均价").isEqualByComparingTo("6.2000");
            }

            // ------------------------------------------------------------------
            // 单位与下限
            // ------------------------------------------------------------------

            @Test
            @DisplayName("源单位与余额记账单位不一致被拒（41059），不做隐式换算")
            void sourceUnitMismatchIsRejected() {
                Long wh = seedWarehouseId();
                Long source = stockedWithUnit("cv3", "5.0000", "箱");
                Long target = newSkuOfType("cv3t", "NON_STANDARD", "ON_SHELF");

                // 源实际按「箱」记账，单据却声明「kg」
                Long id = conversionService.create(form("SPLIT", wh,
                        item(source, "1.0000", "kg", target, "10.0000", "kg")));
                expectCode(() -> conversionService.approve(id, audit(id)), 41059);

                assertThat(balanceRow(wh, source).getQuantity()).isEqualByComparingTo("5.0000");
                assertThat(balanceRow(wh, target)).isNull();
                assertThat(statusOf(id)).isEqualTo("PENDING");
            }

            @Test
            @DisplayName("目标单位与目标 SKU 既有余额单位不一致被拒（41060）")
            void targetUnitMismatchIsRejected() {
                Long wh = seedWarehouseId();
                Long source = stockedWithUnit("cv4", "5.0000", "kg");
                Long target = stockedWithUnit("cv4t", "1.0000", "箱");

                Long id = conversionService.create(form("SPLIT", wh,
                        item(source, "1.0000", "kg", target, "10.0000", "kg")));
                expectCode(() -> conversionService.approve(id, audit(id)), 41060);

                assertThat(balanceRow(wh, target).getQuantity()).isEqualByComparingTo("1.0000");
                assertThat(statusOf(id)).isEqualTo("PENDING");
            }

            @Test
            @DisplayName("源 SKU 可用量不足被拒（41061）")
            void insufficientSourceIsRejected() {
                Long wh = seedWarehouseId();
                Long source = stockedWithUnit("cv5", "5.0000", "kg");
                Long target = newSkuOfType("cv5t", "NON_STANDARD", "ON_SHELF");

                Long id = conversionService.create(form("SPLIT", wh,
                        item(source, "6.0000", "kg", target, "60.0000", "kg")));
                expectCode(() -> conversionService.approve(id, audit(id)), 41061);
                assertThat(balanceRow(wh, source).getQuantity()).isEqualByComparingTo("5.0000");
            }

            @Test
            @DisplayName("源 SKU 无余额行被拒（41058）—— 出方向不建行")
            void missingSourceBalanceIsRejected() {
                Long wh = seedWarehouseId();
                Long source = newSkuOfType("cv6", "NON_STANDARD", "ON_SHELF");
                Long target = newSkuOfType("cv6t", "NON_STANDARD", "ON_SHELF");
                assertThat(balanceRowCount(wh, source)).isZero();

                Long id = conversionService.create(form("SPLIT", wh,
                        item(source, "1.0000", "kg", target, "10.0000", "kg")));
                expectCode(() -> conversionService.approve(id, audit(id)), 41058);
                assertThat(balanceRowCount(wh, source)).isZero();
                assertThat(balanceRowCount(wh, target)).isZero();
            }

            // ------------------------------------------------------------------
            // 入口与状态机
            // ------------------------------------------------------------------

            @Test
            @DisplayName("同一行源 SKU 与目标 SKU 相同被拒（41057）")
            void sameSkuIsRejected() {
                Long wh = seedWarehouseId();
                Long sku = stockedWithUnit("cv7", "5.0000", "kg");
                expectCode(() -> conversionService.create(form("SPLIT", wh,
                        item(sku, "1.0000", "kg", sku, "1.0000", "kg"))), 41057);
            }

            @Test
            @DisplayName("状态机：已完成的单据不可再审批 / 改 / 删；审批乐观锁生效")
            void conversionStatusMachineAndOptimisticLock() {
                Long wh = seedWarehouseId();
                Long source = stockedWithUnit("cv8", "5.0000", "kg");
                Long target = newSkuOfType("cv8t", "NON_STANDARD", "ON_SHELF");

                Long id = conversionService.create(form("SPLIT", wh,
                        item(source, "1.0000", "kg", target, "10.0000", "kg")));

                // 乐观锁：审批人看到的是 version 0，但录单人先改了一次
                int seen = versionOf(id);
                conversionService.update(id, form("SPLIT", wh,
                        item(source, "2.0000", "kg", target, "20.0000", "kg")));
                InventoryConversionAuditForm stale = new InventoryConversionAuditForm();
                stale.setVersion(seen);
                expectCode(() -> conversionService.approve(id, stale), 40921);

                // 用新版本审批通过，按改后的数量执行
                conversionService.approve(id, audit(id));
                assertThat(balanceRow(wh, source).getQuantity()).isEqualByComparingTo("3.0000");
                assertThat(balanceRow(wh, target).getQuantity()).isEqualByComparingTo("20.0000");

                // 终态
                expectCode(() -> conversionService.approve(id, audit(id)), 41054);
                expectCode(() -> conversionService.update(id, form("SPLIT", wh,
                        item(source, "1.0000", "kg", target, "1.0000", "kg"))), 41054);
                expectCode(() -> conversionService.delete(id), 41054);
            }

            @Test
            @DisplayName("驳回必须填审核意见（41063），驳回后不动库存且不可再审")
            void rejectRequiresOpinion() {
                Long wh = seedWarehouseId();
                Long source = stockedWithUnit("cv9", "5.0000", "kg");
                Long target = newSkuOfType("cv9t", "NON_STANDARD", "ON_SHELF");

                Long id = conversionService.create(form("SPLIT", wh,
                        item(source, "1.0000", "kg", target, "10.0000", "kg")));

                expectCode(() -> conversionService.reject(id, audit(id)), 41063);

                InventoryConversionAuditForm withOpinion = audit(id);
                withOpinion.setAuditOpinion("折算率与供应商确认的不一致");
                conversionService.reject(id, withOpinion);

                assertThat(statusOf(id)).isEqualTo("REJECTED");
                assertThat(balanceRow(wh, source).getQuantity()).isEqualByComparingTo("5.0000");
                assertThat(balanceRow(wh, target)).isNull();
                expectCode(() -> conversionService.approve(id, audit(id)), 41054);
            }

            @Test
            @DisplayName("Q7：规格转换的两条流水同样不可改删")
            void conversionMovementsAreStillAppendOnly() {
                Long wh = seedWarehouseId();
                Long source = stockedWithUnit("cv10", "5.0000", "kg");
                Long target = newSkuOfType("cv10t", "NON_STANDARD", "ON_SHELF");

                Long id = conversionService.create(form("SPLIT", wh,
                        item(source, "1.0000", "kg", target, "10.0000", "kg")));
                conversionService.approve(id, audit(id));

                for (Long movementId : List.of(
                        ((Number) convertMovements(wh, source).getFirst().get("id")).longValue(),
                        ((Number) convertMovements(wh, target).getFirst().get("id")).longValue())) {
                    assertMovementImmutable(movementId);
                }
            }
        }

        @Nested
        @DisplayName("移动加权成本（PG IT）")
        class ScmInventoryCostingIT {
            /**
             * 造一笔指定采购单价的入库，返回 skuId（同一 SKU 可多次调用以构造不同价）。
             */
            private void inboundAtPrice(String suffix, Long skuId, String quantity, String price) {
                Long supplierId = newSupplier(suffix);
                linkSupplierSku(supplierId, skuId, DEFAULT_PURCHASE_UNIT);
                PurchaseOrderVO order = createDraftOrder(suffix, supplierId, skuId, quantity, price);
                PurchaseReceiptVO receipt = submittedOrderReceipt(order.getId());
                confirmReceipt(receipt.getId(), quantity);
            }

            private InventoryOutboundFact outboundFact(Long wh, Long sku, String qty, long itemId) {
                return new InventoryOutboundFact(wh, sku, 0L, itemId, new BigDecimal(qty), null,
                        OffsetDateTime.now(), "test:1");
            }

            private List<Map<String, Object>> salesOutMovements(Long wh, Long sku) {
                return movementsOf(wh, sku).stream()
                        .filter(m -> "SALES_OUT".equals(String.valueOf(m.get("movement_type"))))
                        .toList();
            }

            @Test
            @DisplayName("首次入库：均价 = 采购单价（不是 0，也不是别的默认值）")
            void firstInboundSetsAvgToPurchasePrice() {
                Long wh = seedWarehouseId();
                Long sku = newSkuOfType("co1", "NON_STANDARD", "ON_SHELF");

                inboundAtPrice("co1", sku, "10.0000", "6.5000");

                InventoryBalanceEntity balance = balanceRow(wh, sku);
                assertThat(balance.getQuantity()).isEqualByComparingTo("10.0000");
                assertThat(balance.getAvgCost()).as("首次入库的均价就是采购单价")
                        .isEqualByComparingTo("6.5000");
            }

            @Test
            @DisplayName("多笔不同价入库：均价按 (旧量·旧均价 + 入量·入价)/新量 加权")
            void multipleInboundsAverageTheCost() {
                Long wh = seedWarehouseId();
                Long sku = newSkuOfType("co2", "NON_STANDARD", "ON_SHELF");

                // 10 × 6.0000 = 60
                inboundAtPrice("co2a", sku, "10.0000", "6.0000");
                assertThat(balanceRow(wh, sku).getAvgCost()).isEqualByComparingTo("6.0000");

                // 再加 10 × 8.0000 = 80 → 总 140 / 20 = 7.0000
                inboundAtPrice("co2b", sku, "10.0000", "8.0000");
                InventoryBalanceEntity balance = balanceRow(wh, sku);
                assertThat(balance.getQuantity()).isEqualByComparingTo("20.0000");
                assertThat(balance.getAvgCost()).as("(10×6 + 10×8) / 20 = 7").isEqualByComparingTo("7.0000");

                // 再加 5 × 7.0000 = 35 → 总 175 / 25 = 7.0000（均价不变，验证公式而不是巧合）
                inboundAtPrice("co2c", sku, "5.0000", "7.0000");
                assertThat(balanceRow(wh, sku).getAvgCost()).isEqualByComparingTo("7.0000");
            }

            @Test
            @DisplayName("出库不改变均价，但流水写入当时的均价（V34 的核心语义变更）")
            void outboundKeepsAvgAndStampsItOnTheMovement() {
                Long wh = seedWarehouseId();
                Long sku = newSkuOfType("co3", "NON_STANDARD", "ON_SHELF");

                inboundAtPrice("co3a", sku, "10.0000", "6.0000");
                inboundAtPrice("co3b", sku, "10.0000", "8.0000");
                assertThat(balanceRow(wh, sku).getAvgCost()).isEqualByComparingTo("7.0000");

                inventoryCommandService.postSalesOutbound(outboundFact(wh, sku, "4.0000", 950001L));

                InventoryBalanceEntity after = balanceRow(wh, sku);
                assertThat(after.getQuantity()).isEqualByComparingTo("16.0000");
                assertThat(after.getAvgCost()).as("移动加权平均：出库不改变均价")
                        .isEqualByComparingTo("7.0000");

                Map<String, Object> movement = salesOutMovements(wh, sku).getFirst();
                assertThat(new BigDecimal(String.valueOf(movement.get("unit_cost"))))
                        .as("出库流水带的是出库那一刻的均价（此前一律为 NULL）")
                        .isEqualByComparingTo("7.0000");

                // 出库后的再入库，加权基数用出库后的量（16），不是入库总量（20）
                inboundAtPrice("co3c", sku, "4.0000", "11.0000");
                // (16×7 + 4×11) / 20 = (112 + 44) / 20 = 7.8000
                assertThat(balanceRow(wh, sku).getAvgCost()).isEqualByComparingTo("7.8000");
            }

            @Test
            @DisplayName("入库流水的 unit_cost 仍是采购单价（与余额均价区分开）")
            void inboundMovementCarriesThePurchasePrice() {
                Long wh = seedWarehouseId();
                Long sku = newSkuOfType("co4", "NON_STANDARD", "ON_SHELF");

                inboundAtPrice("co4a", sku, "10.0000", "6.0000");
                inboundAtPrice("co4b", sku, "10.0000", "8.0000");

                List<Map<String, Object>> purchaseIn = movementsOf(wh, sku).stream()
                        .filter(m -> "PURCHASE_IN".equals(String.valueOf(m.get("movement_type"))))
                        .toList();
                assertThat(purchaseIn).hasSize(2);
                // 流水的 unit_cost 记的是该笔入库的实际单价，不是余额均价 ——
                // 这是「期初均价取最近一次采购入库单价」这条回填规则能成立的前提。
                assertThat(new BigDecimal(String.valueOf(purchaseIn.get(0).get("unit_cost"))))
                        .isEqualByComparingTo("6.0000");
                assertThat(new BigDecimal(String.valueOf(purchaseIn.get(1).get("unit_cost"))))
                        .isEqualByComparingTo("8.0000");
                assertThat(balanceRow(wh, sku).getAvgCost()).isEqualByComparingTo("7.0000");
            }
        }

        @Nested
        @DisplayName("库存仓库数据范围（PG IT）")
        class ScmInventoryDataScopePgIT {
            /** 「查看全部仓库」与「查看成本字段」两个独立权限点，须与 V55 / V50 的 api_perms 逐字一致。 */
            private static final String WAREHOUSE_ALL = ScmDataScopeService.WAREHOUSE_ALL_PERM;
            private static final String COST = ScmReportAccess.COST_QUERY_PERM;

            /** 入库数量与采购单价：单价即 {@code xsy_inventory_movement.unit_cost} 与余额均价（无四舍五入分支）。 */
            private static final String QUANTITY = "10.0000";
            private static final String PRICE = "6.2000";
            private static final BigDecimal EXPECTED_AVG_COST = new BigDecimal("6.2000");
            private static final BigDecimal EXPECTED_AMOUNT = new BigDecimal("62.00");

            @Autowired
            private InventoryTransferService transferService;

            @Autowired
            private InventoryTransferQueryService transferQuery;

            @Autowired
            private WarehouseScopeService scopeMaintenance;

            // ------------------------------------------------------------------
            // 1. 授权一仓 ⇒ 只见该仓的余额与流水
            // ------------------------------------------------------------------

            @Test
            @DisplayName("只授权一个仓库：余额与流水都只剩该仓的行，分页 total 与实际行数一致")
            void authorizedEmployeeSeesOnlyItsOwnWarehouseRows() {
                Stocked pair = stockTwoWarehouses("ds1");
                Long keeperA = newEmployee("1A");
                Long keeperB = newEmployee("1B");
                authorize(keeperA, pair.warehouseA());
                authorize(keeperB, pair.warehouseB());

                var balancePage = as(keeperA, Set.of(), () -> inventoryBalanceQueryService.query(balanceQuery(null)));
                assertThat(balancePage.getTotal())
                        .as("授权仓是全新仓，只装了本次造的那一行余额").isEqualTo(1L);
                assertThat(balancePage.getTotal()).isEqualTo(balancePage.getList().size());
                assertThat(balancePage.getList()).singleElement().satisfies(row -> {
                    assertThat(row.getWarehouseId()).isEqualTo(pair.warehouseA());
                    assertThat(row.getSkuId()).isEqualTo(pair.skuA());
                });

                var movementPage = as(keeperA, Set.of(), () -> inventoryMovementQueryService.query(movementQuery(null)));
                assertThat(movementPage.getTotal()).isEqualTo(1L);
                assertThat(movementPage.getList()).singleElement().satisfies(row -> {
                    assertThat(row.getWarehouseId()).as("流水按所属仓库判定，与 created_by 无关").isEqualTo(pair.warehouseA());
                    assertThat(row.getMovementType()).isEqualTo("PURCHASE_IN");
                });

                // 换成另一仓的授权人：同一份数据、同一句查询，看到的必须是另一行
                assertThat(warehouseIds(as(keeperB, Set.of(),
                        () -> inventoryBalanceQueryService.query(balanceQuery(null))).getList()))
                        .containsExactly(pair.warehouseB());
                assertThat(skuIds(as(keeperB, Set.of(),
                        () -> inventoryMovementQueryService.query(movementQuery(null))).getList()))
                        .containsExactly(pair.skuB());
            }

            // ------------------------------------------------------------------
            // 2. 无授权行 ⇒ 形状正常的空分页，不是异常，也不是全量
            // ------------------------------------------------------------------

            @Test
            @DisplayName("没有任何仓库授权：得到 total=0 的空分页而不是异常；数据仍在（超管反证）")
            void employeeWithoutAnyAuthorizationGetsWellFormedEmptyPage() {
                Stocked pair = stockTwoWarehouses("ds2");
                Long unscoped = newEmployee("2A");

                var balances = as(unscoped, Set.of(), () -> inventoryBalanceQueryService.query(balanceQuery(null)));
                assertThat(balances.getList()).isEmpty();
                assertThat(balances.getTotal()).isZero();
                assertThat(balances.getEmptyFlag()).as("空分页形状与正常分页一致，前端不必为「无授权」另写分支").isTrue();
                assertThat(balances.getPageNum()).isEqualTo(1L);
                assertThat(balances.getPageSize()).as("分页参数按请求原样回显").isEqualTo(50L);

                var movements = as(unscoped, Set.of(), () -> inventoryMovementQueryService.query(movementQuery(null)));
                assertThat(movements.getTotal()).isZero();
                assertThat(movements.getEmptyFlag()).isTrue();
                assertThat(movements.getList()).isEmpty();

                // 反证：这两行确实躺在库里 —— 上面得到的 0 是「过滤」的结果，不是「没有夹具」
                var adminView = asAdmin(() -> inventoryBalanceQueryService.query(balanceQuery(null)));
                assertThat(adminView.getTotal()).isEqualTo(2L);
                assertThat(warehouseIds(adminView.getList()))
                        .containsExactlyInAnyOrder(pair.warehouseA(), pair.warehouseB());
                assertThat(asAdmin(() -> inventoryMovementQueryService.query(movementQuery(null))).getTotal())
                        .as("两个仓各有一条入库流水").isEqualTo(2L);
            }

            // ------------------------------------------------------------------
            // 3. 全量范围权限 + 调用方筛选：只能收窄，不能放大
            // ------------------------------------------------------------------

            @Test
            @DisplayName("持全部仓库权限者两仓皆可见；仓库筛选放大不了未授权的范围")
            void allScopePermissionSeesEverythingAndFilterOnlyNarrows() {
                Stocked pair = stockTwoWarehouses("ds3");
                // 一条授权行都没有：只有显式的 scope:all 权限才能放宽到全量
                Long headkeeper = newEmployee("3A");

                assertThat(warehouseIds(as(headkeeper, Set.of(WAREHOUSE_ALL),
                        () -> inventoryBalanceQueryService.query(balanceQuery(null)).getList())))
                        .containsExactlyInAnyOrder(pair.warehouseA(), pair.warehouseB());

                // 全量范围内按仓库筛选：结果落在筛选值上，total 同步收窄
                var narrowed = as(headkeeper, Set.of(WAREHOUSE_ALL),
                        () -> inventoryBalanceQueryService.query(balanceQuery(pair.warehouseA())));
                assertThat(narrowed.getTotal()).isEqualTo(1L);
                assertThat(warehouseIds(narrowed.getList())).containsExactly(pair.warehouseA());

                // 同一个筛选交给只授权 A 仓的人去问 B 仓：必须是空，而不是「筛选条件最大」
                Long keeperA = newEmployee("3B");
                authorize(keeperA, pair.warehouseA());
                var widened = as(keeperA, Set.of(),
                        () -> inventoryBalanceQueryService.query(balanceQuery(pair.warehouseB())));
                assertThat(widened.getList()).as("范围谓词与筛选条件是 AND 关系，筛选永远放大不了范围").isEmpty();
                assertThat(widened.getTotal()).isZero();
                assertThat(warehouseIds(as(keeperA, Set.of(),
                        () -> inventoryBalanceQueryService.query(balanceQuery(null)).getList())))
                        .containsExactly(pair.warehouseA());

                // 空清单的授权不等于「全部」：同一个员工拿掉权限点后仍是零行
                assertThat(as(keeperA, Set.of(WAREHOUSE_ALL),
                        () -> inventoryBalanceQueryService.query(balanceQuery(null)).getTotal())).isEqualTo(2L);
            }

            // ------------------------------------------------------------------
            // 4. 调拨：任一端授权即可见
            // ------------------------------------------------------------------

            @Test
            @DisplayName("调拨在途期间源仓与目标仓的授权人都看得到同一张单，第三方看不到")
            void transferIsVisibleFromEitherEndWarehouse() {
                loginAsAdmin();
                Long sku = newSkuOfType("ds4", "NON_STANDARD", "ON_SHELF");
                Long from = newWarehouse("F");
                Long to = newWarehouse("T");
                Long elsewhere = newWarehouse("X");
                stockIn(from, sku, "ds4");

                var draft = new InventoryTransferAddForm();
                draft.setFromWarehouseId(from);
                draft.setToWarehouseId(to);
                InventoryTransferAddForm.Item line = new InventoryTransferAddForm.Item();
                line.setSkuId(sku);
                line.setQuantity(new BigDecimal("4.0000"));
                draft.setItems(new ArrayList<>(List.of(line)));
                Long transferId = transferService.create(draft);
                String transferNo = jdbc.queryForObject(
                        "SELECT transfer_no FROM xsy_inventory_transfer WHERE id = ?", String.class, transferId);
                // 在途：货已离开源仓、尚未进入目标仓，此期间单据对双方都必须可见
                transferService.ship(transferId);

                Long sourceKeeper = newEmployee("4A");
                Long targetKeeper = newEmployee("4B");
                Long stranger = newEmployee("4C");
                authorize(sourceKeeper, from);
                authorize(targetKeeper, to);
                authorize(stranger, elsewhere);

                assertThat(visibleTransferNos(sourceKeeper, transferNo))
                        .as("只授权源仓的人：发出去的货在路上仍归他管").containsExactly(transferNo);
                assertThat(visibleTransferNos(targetKeeper, transferNo))
                        .as("只授权目标仓的人：正送往自己仓的货必须可见").containsExactly(transferNo);
                assertThat(visibleTransferNos(stranger, transferNo)).isEmpty();

                // 在途报表与调拨列表是同一谓词形态的另一处（mapper 里各有一份 <if>）
                assertThat(inTransitTransferNos(sourceKeeper)).contains(transferNo);
                assertThat(inTransitTransferNos(targetKeeper)).contains(transferNo);
                assertThat(inTransitTransferNos(stranger)).doesNotContain(transferNo);

                assertThat(as(sourceKeeper, Set.of(), () -> transferQuery.detail(transferId).getId())).isEqualTo(transferId);
                assertThat(as(targetKeeper, Set.of(), () -> transferQuery.detail(transferId).getId())).isEqualTo(transferId);
                // 两端都不授权：按无权限回答，与「不存在」区分开
                assertDataScopeDenied(() -> as(stranger, Set.of(), () -> transferQuery.detail(transferId)));
            }

            // ------------------------------------------------------------------
            // 5. 详情按 id 越权：独立异常类型 + 30005 信封
            // ------------------------------------------------------------------

            @Test
            @DisplayName("读未授权仓库的余额详情：抛 ScmDataScopeException，由 ScmExceptionHandler 出 30005")
            void detailOutsideAuthorizedWarehouseThrowsDataScopeException() {
                Stocked pair = stockTwoWarehouses("ds5");
                Long keeperA = newEmployee("5A");
                authorize(keeperA, pair.warehouseA());
                Long ownBalanceId = balanceRow(pair.warehouseA(), pair.skuA()).getId();
                Long foreignBalanceId = balanceRow(pair.warehouseB(), pair.skuB()).getId();

                assertThat(as(keeperA, Set.of(), () -> inventoryBalanceQueryService.detail(ownBalanceId).getWarehouseId()))
                        .isEqualTo(pair.warehouseA());
                assertDataScopeDenied(() -> as(keeperA, Set.of(), () -> inventoryBalanceQueryService.detail(foreignBalanceId)));

                // 越权判定不能把「真的不存在」也吞成 30005，否则探测 id 与探测权限混成一种结果
                expectCode(() -> as(keeperA, Set.of(),
                        () -> inventoryBalanceQueryService.detail(foreignBalanceId + 9999999L)), 40486);
            }

            // ------------------------------------------------------------------
            // 6. 成本列：无权时是 null，不是 0
            // ------------------------------------------------------------------

            @Test
            @DisplayName("无成本权限时 avg_cost / 账面金额 / unit_cost 一律为 null；有权限才给出非零数字")
            void costColumnsAreMaskedToNullRatherThanZero() {
                Stocked pair = stockTwoWarehouses("ds6");
                Long keeper = newEmployee("6A");
                authorize(keeper, pair.warehouseA());
                // 库里的真实成本非零：下面得到的 null 只能是「抹掉」的结果，不是数据缺失
                assertThat(balanceRow(pair.warehouseA(), pair.skuA()).getAvgCost())
                        .as("前置：均价记录确实存在").isEqualByComparingTo(EXPECTED_AVG_COST);

                // 每次读之前清一级缓存：无权限的那次读会把返回的 VO 实例就地抹成 null，
                // 而同一事务里同一语句同一参数的第二次读会拿回同一批实例（生产上每请求一个
                // SqlSession，不会串味；测试在一个事务里换权限，就必须自己隔开）。
                var hiddenBalance = costVisibleRows(keeper, false);
                assertThat(hiddenBalance.getAvgCost()).as("无权时必须是 null：0 会被读成「这批货没有成本」").isNull();
                assertThat(hiddenBalance.getAmount()).isNull();
                assertThat(hiddenBalance.getQuantity())
                        .as("数量不是成本记录，权限收口不能顺手把它藏掉").isEqualByComparingTo(QUANTITY);
                assertThat(hiddenBalance.getWarehouseId()).isEqualTo(pair.warehouseA());

                var hiddenMovement = costVisibleMovement(keeper, false);
                assertThat(hiddenMovement.getUnitCost()).isNull();
                assertThat(hiddenMovement.getQuantity()).isEqualByComparingTo(QUANTITY);

                // 详情是同一条记录的另一个入口：只挡列表等于把成本留给详情页
                var hiddenDetail = balanceDetail(keeper, hiddenBalance.getId(), false);
                assertThat(hiddenDetail.getAvgCost()).isNull();
                assertThat(hiddenDetail.getAmount()).isNull();

                var shownBalance = costVisibleRows(keeper, true);
                assertThat(shownBalance.getAvgCost()).isNotNull().isNotZero().isEqualByComparingTo(EXPECTED_AVG_COST);
                assertThat(shownBalance.getAmount()).isNotNull().isNotZero().isEqualByComparingTo(EXPECTED_AMOUNT);
                assertThat(costVisibleMovement(keeper, true).getUnitCost())
                        .isNotNull().isNotZero().isEqualByComparingTo(PRICE);
                assertThat(balanceDetail(keeper, shownBalance.getId(), true).getAvgCost())
                        .isNotNull().isNotZero().isEqualByComparingTo(EXPECTED_AVG_COST);
            }

            /** 余额列表首行；{@code costVisible} 只改功能权限，不改数据范围，两次读的唯一差别就是这一位。 */
            private InventoryBalanceVO costVisibleRows(Long employeeId, boolean costVisible) {
                evictMybatisCache();
                Set<String> permissions = costVisible ? Set.of(COST) : Set.of();
                return as(employeeId, permissions,
                        () -> inventoryBalanceQueryService.query(balanceQuery(null)).getList()).getFirst();
            }

            private InventoryMovementVO costVisibleMovement(Long employeeId, boolean costVisible) {
                evictMybatisCache();
                Set<String> permissions = costVisible ? Set.of(COST) : Set.of();
                return as(employeeId, permissions,
                        () -> inventoryMovementQueryService.query(movementQuery(null)).getList()).getFirst();
            }

            private InventoryBalanceVO balanceDetail(Long employeeId, Long balanceId, boolean costVisible) {
                evictMybatisCache();
                Set<String> permissions = costVisible ? Set.of(COST) : Set.of();
                return as(employeeId, permissions, () -> inventoryBalanceQueryService.detail(balanceId));
            }

            // ------------------------------------------------------------------
            // 7. 授权维护：整体替换、回收即失效，且逐请求生效
            // ------------------------------------------------------------------

            @Test
            @DisplayName("授权整体替换与回收：同一登录上下文里可见范围立即跟着授权行走")
            void replacementAndRecycleChangeVisibilityWithoutRelogin() {
                Stocked pair = stockTwoWarehouses("ds7");
                Long keeper = newEmployee("7A");

                scopeMaintenance.update(scopeForm(keeper, List.of(pair.warehouseA())));
                assertThat(authorizedWarehouseIds(keeper)).containsExactly(pair.warehouseA());
                assertThat(visibleWarehouseIds(keeper)).containsExactly(pair.warehouseA());

                scopeMaintenance.update(scopeForm(keeper, List.of(pair.warehouseA(), pair.warehouseB())));
                assertThat(authorizedWarehouseIds(keeper)).containsExactlyInAnyOrder(pair.warehouseA(), pair.warehouseB());
                assertThat(visibleWarehouseIds(keeper))
                        .containsExactlyInAnyOrder(pair.warehouseA(), pair.warehouseB());

                // 替换不是追加：收掉 B 之后活动行只剩 A，B 仓的余额行随即消失
                scopeMaintenance.update(scopeForm(keeper, List.of(pair.warehouseA())));
                assertThat(authorizedWarehouseIds(keeper)).containsExactly(pair.warehouseA());
                assertThat(visibleWarehouseIds(keeper)).containsExactly(pair.warehouseA());
                assertThat(activeScopeRowCount(keeper)).isEqualTo(1);

                // 全部回收：数据没动，可见性必须归零（证明范围逐请求解析，不缓存在登录保存的数据里）
                scopeMaintenance.update(scopeForm(keeper, List.of()));
                assertThat(activeScopeRowCount(keeper)).isZero();
                var emptied = balancesOf(keeper);
                assertThat(emptied.getTotal()).isZero();
                assertThat(emptied.getEmptyFlag()).isTrue();
                assertThat(asAdmin(() -> inventoryBalanceQueryService.query(balanceQuery(null)).getTotal())).isEqualTo(2L);

                // 回收过的组合可以再授权：部分唯一索引只覆盖活动行
                assertThatCode(() -> scopeMaintenance.update(scopeForm(keeper, List.of(pair.warehouseB()))))
                        .doesNotThrowAnyException();
                assertThat(visibleWarehouseIds(keeper)).containsExactly(pair.warehouseB());
            }

            @Test
            @DisplayName("活动授权唯一由部分唯一索引兜住；维护失败不得清空原有范围")
            void duplicateActivePairRejectedByIndexAndFailedUpdateKeepsScope() {
                Stocked pair = stockTwoWarehouses("ds8");
                Long keeper = newEmployee("8A");
                scopeMaintenance.update(scopeForm(keeper, List.of(pair.warehouseA())));

                // 绕过服务直接写同一组合的第二条活动授权：必须在库内失败，而不是靠 Java 去重
                expectSqlFailure("INSERT INTO xsy_employee_warehouse_scope (employee_id, warehouse_id) VALUES (?, ?)",
                        keeper, pair.warehouseA());
                // 回收之后再写同一条是允许的（历史行不占唯一位）
                jdbc.update("UPDATE xsy_employee_warehouse_scope SET deleted_flag = TRUE WHERE employee_id = ? AND warehouse_id = ?",
                        keeper, pair.warehouseA());
                evictMybatisCache();
                assertThatCode(() -> jdbc.update(
                        "INSERT INTO xsy_employee_warehouse_scope (employee_id, warehouse_id) VALUES (?, ?)",
                        keeper, pair.warehouseA())).doesNotThrowAnyException();

                // 清单里重复的 id 由服务先去重：写成两行会撞上面的唯一索引，而语义上只是同一个仓
                scopeMaintenance.update(scopeForm(keeper, List.of(pair.warehouseA(), pair.warehouseA())));
                assertThat(activeScopeRowCount(keeper)).isEqualTo(1);

                // 指向不存在的仓库：不建外键，完整性只能在写入侧判掉；原有授权不能被这次失败清掉
                expectCode(() -> scopeMaintenance.update(scopeForm(keeper, List.of(999999999L))), 40485);
                assertThat(visibleWarehouseIds(keeper)).containsExactly(pair.warehouseA());

                // 已删除员工不接受授权：垃圾行既命中不到数据也删不掉
                Long retired = newEmployee("8B");
                jdbc.update("UPDATE t_employee SET deleted_flag = TRUE WHERE employee_id = ?", retired);
                expectCode(() -> scopeMaintenance.update(scopeForm(retired, List.of(pair.warehouseA()))), 40000);
            }

            // ==================================================================
            // 夹具
            // ==================================================================

            /** 两个独立新仓库，各有一行余额和一条 {@code PURCHASE_IN} 流水。 */
            private record Stocked(Long warehouseA, Long skuA, Long warehouseB, Long skuB) {
            }

            private Stocked stockTwoWarehouses(String tag) {
                loginAsAdmin();
                Long warehouseA = newWarehouse(tag + "A");
                Long warehouseB = newWarehouse(tag + "B");
                Long skuA = newSkuOfType(tag + "A", "NON_STANDARD", "ON_SHELF");
                Long skuB = newSkuOfType(tag + "B", "NON_STANDARD", "ON_SHELF");
                // 商品规格编码由服务端统一生成（SKU000xxx），而本类要按「本次夹具」收窄查询；
                // 把编码改成带 prefix 的唯一值，查询条件才能只命中本用例造的行。
                renameSkuCode(skuA, tag + "A-K");
                renameSkuCode(skuB, tag + "B-K");
                stockIn(warehouseA, skuA, tag + "SA");
                stockIn(warehouseB, skuB, tag + "SB");
                return new Stocked(warehouseA, skuA, warehouseB, skuB);
            }

            /**
             * 真实「采购单 → 提交 → 收货 → 确认」入库到指定仓库：
             * 余额与流水必须由命令服务写，测试不往账本里直插行。
             */
            private void stockIn(Long warehouseId, Long skuId, String tag) {
                Long supplierId = newPurchasableSupplier(tag, skuId);
                var order = purchaseOrderService.create(
                        orderForm(supplierId, warehouseId, skuId, QUANTITY, PRICE), prefix + ":" + tag + ":po");
                submitOrder(order.getId());
                var receipt = createReceipt(order.getId());
                confirmReceipt(receipt.getId(), QUANTITY);
            }

            /** 直接插授权行：本类要测的是读侧按授权行收窄，写侧另有第 7 组用例。 */
            private void renameSkuCode(Long skuId, String suffix) {
                jdbc.update("UPDATE xsy_product_sku SET sku_code = ? WHERE id = ?", prefix + suffix, skuId);
                evictMybatisCache();
            }

            private void authorize(Long employeeId, Long warehouseId) {
                jdbc.update("INSERT INTO xsy_employee_warehouse_scope (employee_id, warehouse_id) VALUES (?, ?)",
                        employeeId, warehouseId);
                evictMybatisCache();
            }

            /** 一名 {@code administratorFlag=false} 的真实员工：范围判定只看 employee_id 与 deleted_flag。 */
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

            private WarehouseScopeUpdateForm scopeForm(Long employeeId, List<Long> warehouseIds) {
                loginAsAdmin();
                WarehouseScopeUpdateForm form = new WarehouseScopeUpdateForm();
                form.setEmployeeId(employeeId);
                // 表单约定：清空请传空数组，而不是 null
                form.setWarehouseIds(new ArrayList<>(warehouseIds));
                return form;
            }

            // ------------------------------------------------------------------
            // 查询请求与结果查询结果
            // ------------------------------------------------------------------

            private InventoryBalanceQueryForm balanceQuery(Long warehouseId) {
                InventoryBalanceQueryForm form = page(new InventoryBalanceQueryForm());
                // SKU 编码前缀是本用例独有的：把断言限定在自己造的那几行上，不假设库是空的
                form.setSkuCode(prefix);
                form.setWarehouseId(warehouseId);
                return form;
            }

            private InventoryMovementQueryForm movementQuery(Long warehouseId) {
                InventoryMovementQueryForm form = page(new InventoryMovementQueryForm());
                form.setSkuCode(prefix);
                form.setWarehouseId(warehouseId);
                return form;
            }

            private <T extends PageParam> T page(T form) {
                form.setPageNum(1L);
                form.setPageSize(50L);
                return form;
            }

            private PageResult<InventoryBalanceVO> balancesOf(Long employeeId) {
                return as(employeeId, Set.of(), () -> inventoryBalanceQueryService.query(balanceQuery(null)));
            }

            /** 该员工此刻能看到的余额行落在哪些仓库上 —— 授权一变，这个集合就该跟着变。 */
            private List<Long> visibleWarehouseIds(Long employeeId) {
                return warehouseIds(balancesOf(employeeId).getList());
            }

            private List<String> visibleTransferNos(Long employeeId, String transferNo) {
                return as(employeeId, Set.of(), () -> {
                    InventoryTransferQueryForm form = page(new InventoryTransferQueryForm());
                    // 按单号定位：仓库两端都不授权时，这一行必须整体消失，而不是「少几列」
                    form.setTransferNo(transferNo);
                    return transferQuery.queryPage(form).getList().stream()
                            .map(row -> row.getTransferNo()).toList();
                });
            }

            private List<String> inTransitTransferNos(Long employeeId) {
                return as(employeeId, Set.of(), () -> transferQuery.queryInTransit().stream()
                        .map(row -> row.getTransferNo()).toList());
            }

            private static List<Long> warehouseIds(List<InventoryBalanceVO> rows) {
                return rows.stream().map(InventoryBalanceVO::getWarehouseId).toList();
            }

            private static List<Long> skuIds(List<InventoryMovementVO> rows) {
                return rows.stream().map(InventoryMovementVO::getSkuId).toList();
            }

            private List<Long> authorizedWarehouseIds(Long employeeId) {
                var rows = scopeMaintenance.listWarehouses(employeeId);
                return rows.stream().map(row -> row.getWarehouseId()).toList();
            }

            private int activeScopeRowCount(Long employeeId) {
                evictMybatisCache();
                return jdbc.queryForObject("SELECT count(*) FROM xsy_employee_warehouse_scope"
                                + " WHERE employee_id = ? AND deleted_flag = FALSE", Integer.class, employeeId);
            }

            // ------------------------------------------------------------------
            // 身份与权限
            // ------------------------------------------------------------------

            /** 夹具与授权维护用的「不受数据范围约束的操作者」，与基类 {@code setUpOperator} 同一身份。 */
            private void loginAsAdmin() {
                RequestEmployee employee = new RequestEmployee();
                employee.setEmployeeId(1L);
                employee.setActualName("Scope IT admin");
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                employee.setAdministratorFlag(true);
                XsyRequestUtil.setRequestUser(employee);
            }

            private void loginAs(Long employeeId) {
                RequestEmployee employee = new RequestEmployee();
                employee.setEmployeeId(employeeId);
                employee.setActualName("库存数据范围 IT");
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                employee.setAdministratorFlag(false);
                employee.setDepartmentId(1L);
                XsyRequestUtil.setRequestUser(employee);
            }

            /**
             * 以某个非超管员工 + 一组功能权限执行一段逻辑。未点名的权限码取 Mockito 默认值 false，
             * 与 {@code ScmDataScopeService} 的校验失败时拒绝操作取向一致。<b>不可嵌套调用。</b>
             */
            private <T> T as(Long employeeId, Set<String> permissions, Supplier<T> body) {
                loginAs(employeeId);
                try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                    permissions.forEach(p -> stp.when(() -> StpUtil.hasPermission(p)).thenReturn(true));
                    return body.get();
                }
            }

            /** 超管位只用于「数据确实在」的反证，不作为任何权限结论的依据（裁决第 5 条）。 */
            private <T> T asAdmin(Supplier<T> body) {
                loginAsAdmin();
                try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                    return body.get();
                }
            }

            /**
             * 越权读必须是独立的 {@link ScmDataScopeException}：既不能是带出自己业务码的
             * {@code ScmBusinessException}，也不能伪装成「不存在」。信封出码由 {@link ScmExceptionHandler}
             * 负责（30005），本类直接把映射结果也算一遍，避免只断到异常类型而放走映射缺失。
             */
            private void assertDataScopeDenied(Runnable action) {
                assertThatThrownBy(action::run)
                        .isExactlyInstanceOf(ScmDataScopeException.class)
                        .hasMessage(UserErrorCode.NO_PERMISSION.getMsg());
                var envelope = new ScmExceptionHandler().handleDataScope(new ScmDataScopeException());
                assertThat(envelope.getCode()).isEqualTo(UserErrorCode.NO_PERMISSION.getCode());
                assertThat(envelope.getMsg()).isEqualTo(UserErrorCode.NO_PERMISSION.getMsg());
                assertThat(envelope.getOk()).isFalse();
            }
        }

        @Nested
        @DisplayName("W6 采购入库实时路径（PG IT）")
        class ScmInventoryInboundIT {
            private InventoryBalanceQueryForm balanceQuery(Long warehouseId, Long skuId) {
                InventoryBalanceQueryForm form = new InventoryBalanceQueryForm();
                form.setPageNum(1L);
                form.setPageSize(20L);
                form.setWarehouseId(warehouseId);
                form.setSkuId(skuId);
                return form;
            }

            private InventoryMovementQueryForm movementQuery(Long warehouseId, Long skuId) {
                InventoryMovementQueryForm form = new InventoryMovementQueryForm();
                form.setPageNum(1L);
                form.setPageSize(20L);
                form.setWarehouseId(warehouseId);
                form.setSkuId(skuId);
                return form;
            }

            // ------------------------------------------------------------------
            // #1
            // ------------------------------------------------------------------

            @Test
            @DisplayName("#1 confirm → PURCHASE_IN：余额增量、流水恒等式、源身份四元组（同事务）")
            void confirmLandsPurchaseInMovementAndBalance() {
                Long warehouseId = seedWarehouseId();
                Long skuId = newOnShelfSku("IN1");
                W6Fixture fx = inboundFixture("IN1", skuId, "10.0000");

                PurchaseReceiptVO confirmed = confirmReceipt(fx.receipt().getId(), "10.0000");
                assertThat(confirmed.getStatus()).isEqualTo("CONFIRMED");

                // 余额：恰好一行、数量 = 本次有效数量、单位 = 采购时的单位
                assertThat(balanceRowCount(warehouseId, skuId)).isEqualTo(1);
                InventoryBalanceEntity balance = balanceRow(warehouseId, skuId);
                assertThat(balance.getUnit()).isEqualTo(DEFAULT_PURCHASE_UNIT);
                assertThat(balance.getQuantity()).isEqualByComparingTo("10.0000");
                // version 从 0 起，一次自增 = 1（纵深防御列确实在动）
                assertThat(balance.getVersion()).isEqualTo(1);

                // 流水：恰好一条，四元组 + before/after 恒等式 + 当时保存的成本
                List<Map<String, Object>> movements = movementsOf(warehouseId, skuId);
                assertThat(movements).hasSize(1);
                Map<String, Object> movement = movements.getFirst();
                assertThat(movement.get("movement_type")).isEqualTo("PURCHASE_IN");
                assertThat(movement.get("source_document_type"))
                        .isEqualTo(PurchaseInventoryContract.SOURCE_DOCUMENT_TYPE);
                assertThat(movement.get("source_document_id")).isEqualTo(fx.receipt().getId());
                assertThat(movement.get("source_document_item_id")).isEqualTo(fx.receiptItemId());
                assertThat((BigDecimal) movement.get("quantity")).isEqualByComparingTo("10.0000");
                assertThat((BigDecimal) movement.get("before_quantity")).isEqualByComparingTo("0.0000");
                assertThat((BigDecimal) movement.get("after_quantity")).isEqualByComparingTo("10.0000");
                assertThat(movement.get("unit_snapshot")).isEqualTo(DEFAULT_PURCHASE_UNIT);
                assertThat((BigDecimal) movement.get("unit_cost")).isEqualByComparingTo("6.2000");
                // append-only：deleted 恒为 FALSE
                assertThat(movement.get("deleted")).isEqualTo(false);
                assertThat(movementsOfReceiptItem(fx.receiptItemId())).isEqualTo(1);

                // 只读查询面立即可见（余额是活状态，展示字段实时联表）
                PageResult<InventoryBalanceVO> balancePage =
                        inventoryBalanceQueryService.query(balanceQuery(warehouseId, skuId));
                assertThat(balancePage.getList()).hasSize(1);
                assertThat(balancePage.getList().getFirst().getUnit()).isEqualTo(DEFAULT_PURCHASE_UNIT);
                assertThat(balancePage.getList().getFirst().getQuantity()).isEqualByComparingTo("10.0000");

                PageResult<InventoryMovementVO> movementPage =
                        inventoryMovementQueryService.query(movementQuery(warehouseId, skuId));
                assertThat(movementPage.getList()).hasSize(1);
                // 人类可读来源 = 收货单号（没有 movement_no）
                assertThat(movementPage.getList().getFirst().getReceiptNo()).isEqualTo(confirmed.getReceiptNo());
            }

            // ------------------------------------------------------------------
            // #2
            // ------------------------------------------------------------------

            @Test
            @DisplayName("#2 多行收货（标品 + 非标品）：每行一条流水，非标品数量 = 实重")
            void multiLineReceiptWritesOneMovementPerLine() {
                Long warehouseId = seedWarehouseId();
                Long standardSku = newSkuOfType("IN2S", "STANDARD", "ON_SHELF");
                Long nonStandardSku = newSkuOfType("IN2N", "NON_STANDARD", "ON_SHELF");
                Long supplierId = newSupplier("IN2");
                // 一次 replace 挂两个 SKU（不能循环调用：replace 的语义是整体替换）
                linkSupplierSkus(supplierId, standardSku, nonStandardSku);

                PurchaseOrderAddForm form = orderForm(supplierId, warehouseId, standardSku, "4.0000", "3.0000");
                form.getItems().add(item(nonStandardSku, "6.0000", "5.0000"));
                PurchaseOrderVO order = purchaseOrderService.create(form, prefix + ":IN2:po");
                submitOrder(order.getId());
                PurchaseReceiptVO receipt = createReceipt(order.getId());

                List<PurchaseReceiptItemVO> lines = receiptItems(receipt.getId());
                assertThat(lines).hasSize(2);
                PurchaseReceiptItemVO standardLine = lineOf(lines, standardSku);
                PurchaseReceiptItemVO nonStandardLine = lineOf(lines, nonStandardSku);

                // 标品：实重三字段全空；非标品：实重必填（= 有效数量的来源）
                purchaseReceiptService.confirm(confirmForm(receipt.getId(), receipt.getVersion(),
                                receiptLine(standardLine.getId(), standardLine.getVersion(), "4.0000", null),
                                receiptLine(nonStandardLine.getId(), nonStandardLine.getVersion(), "6.0000", "6.0000")),
                        prefix + ":IN2:confirm");

                // 每个 SKU 各一条流水、各一行余额
                assertThat(movementCount(warehouseId, standardSku)).isEqualTo(1);
                assertThat(movementCount(warehouseId, nonStandardSku)).isEqualTo(1);
                assertThat(balanceRow(warehouseId, standardSku).getQuantity()).isEqualByComparingTo("4.0000");
                // 非标品的有效数量取实重（声明数量也是 6，但取值来源是实重）
                assertThat(balanceRow(warehouseId, nonStandardSku).getQuantity()).isEqualByComparingTo("6.0000");
                assertThat(movementsOf(warehouseId, standardSku).getFirst().get("unit_snapshot"))
                        .isEqualTo(DEFAULT_PURCHASE_UNIT);
                assertThat(movementsOf(warehouseId, nonStandardSku).getFirst().get("unit_snapshot"))
                        .isEqualTo(DEFAULT_PURCHASE_UNIT);
            }

            private static PurchaseReceiptItemVO lineOf(List<PurchaseReceiptItemVO> lines, Long skuId) {
                return lines.stream()
                        .filter(line -> line.getSkuId().equals(skuId))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("收货单缺少 SKU " + skuId + " 的行"));
            }

            // ------------------------------------------------------------------
            // #4
            // ------------------------------------------------------------------

            @Test
            @DisplayName("#4 confirm 重复请求返回原结果：同键同内容返回首次结果，流水仍只有 1 条")
            void confirmReplayDoesNotDoubleCountInventory() {
                Long warehouseId = seedWarehouseId();
                Long skuId = newOnShelfSku("IN4");
                W6Fixture fx = inboundFixture("IN4", skuId, "10.0000");

                // 必须复用同一个 form 实例：防重复处理按「键 + 请求内容哈希」判定，
                // 重新读一次库拿到的 version 已经变了，会被判成「同键不同内容」(40990)。
                PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
                PurchaseReceiptItemVO line = current.getItems().getFirst();
                PurchaseReceiptConfirmForm form = confirmForm(current.getId(), current.getVersion(),
                        receiptLine(line.getId(), line.getVersion(), "10.0000"));
                String key = prefix + ":IN4:replay";

                PurchaseReceiptVO first = purchaseReceiptService.confirm(form, key);
                PurchaseReceiptVO replayed = purchaseReceiptService.confirm(form, key);

                assertThat(replayed.getId()).isEqualTo(first.getId());
                assertThat(replayed.getReceiptNo()).isEqualTo(first.getReceiptNo());
                assertThat(replayed.getVersion()).isEqualTo(first.getVersion());
                assertThat(replayed.getStatus()).isEqualTo("CONFIRMED");

                // 库存没有被重放加第二次
                assertThat(movementCount(warehouseId, skuId)).isEqualTo(1);
                assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("10.0000");
                // 采购侧累计也没有被加第二次
                assertThat(receivedQuantityOf(fx.orderItemId())).isEqualByComparingTo("10.0000");
            }

            // ------------------------------------------------------------------
            // #7
            // ------------------------------------------------------------------

            @Test
            @DisplayName("#7 源身份防重：重复 postInbound 同 receiptItemId → 41002；部分唯一索引兜底")
            void duplicateSourceIdentityIsRejected() {
                Long warehouseId = seedWarehouseId();
                Long skuId = newOnShelfSku("IN7");
                W6Fixture fx = inboundFixture("IN7", skuId, "5.0000");
                confirmReceipt(fx.receipt().getId(), "5.0000");
                assertThat(movementCount(warehouseId, skuId)).isEqualTo(1);

                // 命令侧：同一源记录再来一次 → 41002 fail-fast（不静默跳过，否则会出现
                // 「接口返回成功但库存没动」这种最难排查的不一致）
                expectCode(() -> inventoryCommandService.postPurchaseInbound(
                        new PurchaseInventoryContract.InboundFact(
                                fx.order().getId(), fx.receipt().getId(), fx.receiptItemId(),
                                warehouseId, skuId, null, null, null, null,
                                DEFAULT_PURCHASE_UNIT, new BigDecimal("5.0000"), new BigDecimal("6.2000"),
                                "duplicate-probe", OffsetDateTime.now(), "W6 IT", List.of())), 41002);

                // DB 侧兜底：绕过应用层直接插同源流水 → 撞 uk_inventory_movement_source_active
                expectSqlFailure(
                        "INSERT INTO xsy_inventory_movement (warehouse_id, sku_id, movement_type, source_document_type, "
                                + "source_document_id, source_document_item_id, quantity, unit_snapshot, "
                                + "before_quantity, after_quantity, occurred_at, deleted) "
                                + "VALUES (?, ?, 'PURCHASE_IN', 'PURCHASE_RECEIPT_ITEM', ?, ?, 1, 'kg', 0, 1, "
                                + "CURRENT_TIMESTAMP, FALSE)",
                        warehouseId, skuId, fx.receipt().getId(), fx.receiptItemId());

                // 余额没有被第二次入库改变
                assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("5.0000");
                assertThat(balanceRowCount(warehouseId, skuId)).isEqualTo(1);
            }

            // ------------------------------------------------------------------
            // #12
            // ------------------------------------------------------------------

            @Test
            @DisplayName("#12 Q13 单位一致累加：同 (wh,sku) 两次同单位入库 → 余额累加、unit 不变、历史记录查询链正确")
            void sameUnitInboundsAccumulateOnOneBalanceRow() {
                Long warehouseId = seedWarehouseId();
                Long skuId = newOnShelfSku("IN12");
                W6Fixture first = inboundFixture("IN12a", skuId, "6.0000");
                W6Fixture second = inboundFixture("IN12b", skuId, "4.0000");

                confirmReceipt(first.receipt().getId(), "6.0000");
                confirmReceipt(second.receipt().getId(), "4.0000");

                // 一个 (warehouse, sku) 永远只有一行余额
                assertThat(balanceRowCount(warehouseId, skuId)).isEqualTo(1);
                InventoryBalanceEntity balance = balanceRow(warehouseId, skuId);
                assertThat(balance.getUnit()).isEqualTo(DEFAULT_PURCHASE_UNIT);
                assertThat(balance.getQuantity()).isEqualByComparingTo("10.0000");
                assertThat(balance.getVersion()).isEqualTo(2);

                // 历史记录查询链：第二条的 before = 第一条的 after（append-only 账本的核心恒等式）
                List<Map<String, Object>> movements = movementsOf(warehouseId, skuId);
                assertThat(movements).hasSize(2);
                assertThat((BigDecimal) movements.get(0).get("before_quantity")).isEqualByComparingTo("0.0000");
                assertThat((BigDecimal) movements.get(0).get("after_quantity")).isEqualByComparingTo("6.0000");
                assertThat((BigDecimal) movements.get(1).get("before_quantity")).isEqualByComparingTo("6.0000");
                assertThat((BigDecimal) movements.get(1).get("after_quantity")).isEqualByComparingTo("10.0000");
                assertThat(movements.get(0).get("unit_snapshot")).isEqualTo(DEFAULT_PURCHASE_UNIT);
                assertThat(movements.get(1).get("unit_snapshot")).isEqualTo(DEFAULT_PURCHASE_UNIT);
            }

            // ------------------------------------------------------------------
            // #16
            // ------------------------------------------------------------------

            @Test
            @DisplayName("#16 queryAvailability：无行 → 0/0（W6 起不再返回 null）；reserved 恒 0")
            void queryAvailabilityNeverReturnsNull() {
                Long warehouseId = seedWarehouseId();
                Long skuId = newOnShelfSku("IN16");

                // 「确实没有库存」≠「没有库存能力」：返回 0 而不是 null
                PurchaseInventoryContract.Availability empty =
                        inventoryCommandService.queryAvailability(skuId, warehouseId);
                assertThat(empty).isNotNull();
                assertThat(empty.available()).isEqualByComparingTo("0");
                assertThat(empty.reserved()).isEqualByComparingTo("0");

                // null 入参走宽松分支，同样不抛错、不返回 null
                assertThat(inventoryCommandService.queryAvailability(null, warehouseId)).isNotNull();
                assertThat(inventoryCommandService.queryAvailability(skuId, null)).isNotNull();

                W6Fixture fx = inboundFixture("IN16", skuId, "8.0000");
                confirmReceipt(fx.receipt().getId(), "8.0000");

                PurchaseInventoryContract.Availability filled =
                        inventoryCommandService.queryAvailability(skuId, warehouseId);
                assertThat(filled.available()).isEqualByComparingTo("8.0000");
                // 采购入库不占用预留：reserved 恒 0（OrderInventoryContract 保持零实现零调用）
                assertThat(filled.reserved()).isEqualByComparingTo("0");
            }

            // ------------------------------------------------------------------
            // #18
            // ------------------------------------------------------------------

            @Test
            @DisplayName("#18 只读查询面：筛选 / 空态 / 详情 40486 / 拒绝客户端排序 / 流水溯源单号")
            void readOnlyQuerySurface() {
                Long warehouseId = seedWarehouseId();
                Long skuId = newOnShelfSku("IN18");
                W6Fixture fx = inboundFixture("IN18", skuId, "7.0000");
                PurchaseReceiptVO confirmed = confirmReceipt(fx.receipt().getId(), "7.0000");

                // --- 余额：按 (warehouseId, skuId) 命中，展示字段实时联表 ---
                PageResult<InventoryBalanceVO> page =
                        inventoryBalanceQueryService.query(balanceQuery(warehouseId, skuId));
                assertThat(page.getList()).hasSize(1);
                InventoryBalanceVO balance = page.getList().getFirst();
                assertThat(balance.getWarehouseCode()).isEqualTo(SEED_WAREHOUSE_CODE);
                assertThat(balance.getSkuCode()).isEqualTo(skuCodeOf(skuId));
                assertThat(balance.getProductName()).isEqualTo(prefix + "IN18商品");
                assertThat(balance.getSpecValues()).isNotEmpty();
                assertThat(balance.getUnit()).isEqualTo(DEFAULT_PURCHASE_UNIT);
                assertThat(balance.getQuantity()).isEqualByComparingTo("7.0000");
                assertThat(balance.getVersion()).isEqualTo(1);

                // --- 余额：skuCode 模糊命中；不存在的编码 → 空态（「没有库存」不是错误）---
                // V147 起 SKU 编码由后端发号，模糊检索只能用写入数据库编码，不能沿用造数时传入的字符串
                String storedSkuCode = skuCodeOf(skuId);
                InventoryBalanceQueryForm fuzzy = new InventoryBalanceQueryForm();
                fuzzy.setPageNum(1L);
                fuzzy.setPageSize(20L);
                fuzzy.setSkuCode(storedSkuCode);
                List<InventoryBalanceVO> fuzzyRows = inventoryBalanceQueryService.query(fuzzy).getList();
                assertThat(fuzzyRows).hasSize(1);
                assertThat(fuzzyRows.getFirst().getSkuCode()).isEqualTo(storedSkuCode);

                InventoryBalanceQueryForm noMatch = new InventoryBalanceQueryForm();
                noMatch.setPageNum(1L);
                noMatch.setPageSize(20L);
                noMatch.setSkuCode(prefix + "-NO-SUCH-SKU");
                assertThat(inventoryBalanceQueryService.query(noMatch).getList()).isEmpty();

                // --- 余额详情：命中 / 未知 id → 40486 ---
                assertThat(inventoryBalanceQueryService.detail(balance.getId()).getQuantity())
                        .isEqualByComparingTo("7.0000");
                expectCode(() -> inventoryBalanceQueryService.detail(balance.getId() + 999_999_999L), 40486);

                // --- 拒绝客户端排序：join 查询里 {@code updated_at} 在四张表上都存在，
                //     静默忽略会让前端以为排序生效了，因此显式 40000 ---
                InventoryBalanceQueryForm sorted = balanceQuery(warehouseId, skuId);
                PageParam.SortItem sortItem = new PageParam.SortItem();
                sortItem.setColumn("updated_at");
                sortItem.setIsAsc(false);
                sorted.setSortItemList(List.of(sortItem));
                expectCode(() -> inventoryBalanceQueryService.query(sorted), 40000);

                // --- 流水：溯源字段（收货单号）+ 保存当时资料的字段 ---
                PageResult<InventoryMovementVO> movementPage =
                        inventoryMovementQueryService.query(movementQuery(warehouseId, skuId));
                assertThat(movementPage.getList()).hasSize(1);
                InventoryMovementVO movement = movementPage.getList().getFirst();
                assertThat(movement.getReceiptNo()).isEqualTo(confirmed.getReceiptNo());
                assertThat(movement.getSourceDocumentId()).isEqualTo(fx.receipt().getId());
                assertThat(movement.getSourceDocumentItemId()).isEqualTo(fx.receiptItemId());
                assertThat(movement.getMovementType()).isEqualTo("PURCHASE_IN");
                assertThat(movement.getSourceDocumentType())
                        .isEqualTo(PurchaseInventoryContract.SOURCE_DOCUMENT_TYPE);
                assertThat(movement.getUnitSnapshot()).isEqualTo(DEFAULT_PURCHASE_UNIT);
                // unit_cost 是采购价派生的成本记录：无 scm:report:cost:query 时 VO 给 null 而不是 0，
                // 账本本身不受影响，成本仍可从流水行读回（可见性收口在 VO 层，不动账本）。
                assertThat(movement.getUnitCost()).isNull();
                assertThat(inventoryMovementDao
                        .selectBySourceItem(PurchaseInventoryContract.SOURCE_DOCUMENT_TYPE, fx.receiptItemId())
                        .getUnitCost()).isEqualByComparingTo("6.2000");
                assertThat(movement.getBeforeQuantity()).isEqualByComparingTo("0.0000");
                assertThat(movement.getAfterQuantity()).isEqualByComparingTo("7.0000");
                assertThat(movement.getOperator()).isEqualTo(receiptOperator(fx.receipt().getId()));

                // --- 流水时间范围：过滤的是 occurred_at（业务发生时刻），左闭右开 ---
                InventoryMovementQueryForm inWindow = movementQuery(warehouseId, skuId);
                inWindow.setOccurredFrom(movement.getOccurredAt().minusSeconds(1));
                inWindow.setOccurredTo(movement.getOccurredAt().plusSeconds(1));
                assertThat(inventoryMovementQueryService.query(inWindow).getList()).hasSize(1);

                InventoryMovementQueryForm outWindow = movementQuery(warehouseId, skuId);
                // occurredTo 取 occurredAt 本身 → {@code occurred_at < occurredTo} 为假 → 排除（左闭右开）
                outWindow.setOccurredTo(movement.getOccurredAt());
                assertThat(inventoryMovementQueryService.query(outWindow).getList()).isEmpty();

                // --- 类型白名单：枚举与 DB CHECK 同源
                //（PURCHASE_IN 之外，出库 / 盘点 / 报损报溢 / 调拨 / 规格转换各有自己的类型）---
                assertThat(ScmInventoryMovementTypeEnum.isSupported("PURCHASE_IN")).isTrue();
                assertThat(ScmInventoryMovementTypeEnum.isSupported("SALES_OUT")).isTrue();
                assertThat(ScmInventoryMovementTypeEnum.isSupported(null)).isFalse();
                // 白名单之外一律拒绝。用 UNKNOWN_IN 这个明确不存在的名字 ——
                // 十个真实类型已全部落地，再拿「未实现的业务类型」当反例会每落地一个就要改一次。
                assertThat(ScmInventoryMovementTypeEnum.isSupported("UNKNOWN_IN")).isFalse();
                assertThat(ScmInventoryMovementTypeEnum.isSupported("STOCKTAKE_ADJUST")).isFalse();
            }
        }

        @Nested
        @DisplayName("报损报溢（PG IT）")
        class ScmInventoryLossGainIT {
            /**
             * 审批人身份：与基类的录单人（employee 1）刻意不同，用于满足「禁止自建自审」。
             */
            private static final Long AUDITOR_EMPLOYEE_ID = 90011L;

            @Autowired
            private InventoryLossGainService lossGainService;

            @Autowired
            private InventoryReservationService reservations;

            /**
             * 造一个已入库指定数量的 SKU，返回 {@code (warehouseId, skuId)}。
             */
            private Object[] stocked(String suffix, String quantity) {
                Long skuId = newSkuOfType(suffix, "NON_STANDARD", "ON_SHELF");
                W6Fixture fixture = inboundFixture(suffix, skuId, quantity);
                confirmReceipt(fixture.receipt().getId(), quantity);
                return new Object[]{seedWarehouseId(), skuId};
            }

            private static InventoryLossGainAddForm.Item item(Long skuId, String quantity) {
                InventoryLossGainAddForm.Item row = new InventoryLossGainAddForm.Item();
                row.setSkuId(skuId);
                row.setQuantity(new BigDecimal(quantity));
                return row;
            }

            private InventoryLossGainAddForm form(String adjustType, Long wh, Long sku, String qty, String reason) {
                InventoryLossGainAddForm form = new InventoryLossGainAddForm();
                form.setAdjustType(adjustType);
                form.setWarehouseId(wh);
                form.setReason(reason);
                form.setItems(new ArrayList<>(List.of(item(sku, qty))));
                return form;
            }

            /**
             * 单据当前版本号 —— 审批必须带上它（读库而不是读缓存，避免 MyBatis 一级缓存干扰）。
             */
            private int versionOf(Long id) {
                return jdbc.queryForObject(
                        "SELECT version FROM xsy_inventory_loss_gain WHERE id = ?", Integer.class, id);
            }

            private InventoryLossGainAuditForm audit(Long id, String opinion) {
                return audit(id, opinion, versionOf(id));
            }

            private InventoryLossGainAuditForm audit(Long id, String opinion, int version) {
                InventoryLossGainAuditForm form = new InventoryLossGainAuditForm();
                form.setVersion(version);
                form.setAuditOpinion(opinion);
                return form;
            }

            private String statusOf(Long id) {
                return jdbc.queryForObject(
                        "SELECT status FROM xsy_inventory_loss_gain WHERE id = ?", String.class, id);
            }

            /**
             * 审批必须换一个人：报损报溢禁止自建自审（41065，P0 基线收口裁决第 8 条），
             * 而本类通篇是「录单人建单 → 审批」，因此把审批动作切到另一个员工身份、跑完立即恢复，
             * 其余断言仍按录单人视角执行。库里没有指向 {@code t_employee} 的外键，
             * 审批人用合成 id 即可（它只落进审计字段）。
             */
            private void asAuditor(Runnable auditAction) {
                RequestUser maker = XsyRequestUtil.getRequestUser();
                RequestEmployee auditor = new RequestEmployee();
                auditor.setEmployeeId(AUDITOR_EMPLOYEE_ID);
                auditor.setActualName("W6 IT 审核员");
                auditor.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                // 与基类同一取向：夹具操作者不受数据范围约束，本用例只测「审批人 != 录单人」这条身份规则，
                // 不顺带把仓库授权也当被测对象（那属于数据范围用例）。
                auditor.setAdministratorFlag(true);
                XsyRequestUtil.setRequestUser(auditor);
                try {
                    auditAction.run();
                } finally {
                    if (maker == null) {
                        XsyRequestUtil.remove();
                    } else {
                        XsyRequestUtil.setRequestUser(maker);
                    }
                }
            }

            private void approveAsAuditor(Long id, InventoryLossGainAuditForm form) {
                asAuditor(() -> lossGainService.approve(id, form));
            }

            private void rejectAsAuditor(Long id, InventoryLossGainAuditForm form) {
                asAuditor(() -> lossGainService.reject(id, form));
            }

            /**
             * 某个 (仓库, SKU) 的报损报溢流水，按业务时刻升序。
             */
            private List<Map<String, Object>> lossGainMovements(Long wh, Long sku) {
                return movementsOf(wh, sku).stream()
                        .filter(m -> {
                            String type = String.valueOf(m.get("movement_type"));
                            return "LOSS_REPORT".equals(type) || "GAIN_REPORT".equals(type);
                        })
                        .toList();
            }

            private static BigDecimal decimal(Map<String, Object> row, String column) {
                return new BigDecimal(String.valueOf(row.get(column)));
            }

            // ------------------------------------------------------------------
            // 审批才动库存
            // ------------------------------------------------------------------

            @Test
            @DisplayName("报损：创建与改待审核都不动库存，审批通过才写 LOSS_REPORT 并扣减")
            void lossOnlyTouchesStockAtApproval() {
                Object[] s = stocked("lg1", "10.0000");
                Long wh = (Long) s[0];
                Long sku = (Long) s[1];

                Long id = lossGainService.create(form("LOSS", wh, sku, "3.0000", "到货变质"));

                // 创建之后：单据待审核、余额与流水都没动 —— 这正是「审批」这个环节的意义
                assertThat(statusOf(id)).isEqualTo("PENDING");
                assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("10.0000");
                assertThat(lossGainMovements(wh, sku)).isEmpty();

                approveAsAuditor(id, audit(id, null));

                assertThat(statusOf(id)).isEqualTo("COMPLETED");
                assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("7.0000");

                List<Map<String, Object>> rows = lossGainMovements(wh, sku);
                assertThat(rows).hasSize(1);
                Map<String, Object> row = rows.getFirst();
                assertThat(row.get("movement_type")).isEqualTo("LOSS_REPORT");
                assertThat(row.get("source_document_type")).isEqualTo("LOSS_GAIN_ITEM");
                assertThat(decimal(row, "quantity")).isEqualByComparingTo("3.0000");
                // 方向感知保存的数据：报损是减
                assertThat(decimal(row, "before_quantity")).isEqualByComparingTo("10.0000");
                assertThat(decimal(row, "after_quantity")).isEqualByComparingTo("7.0000");
                // 单位以余额记账单位为准，由服务端取
                assertThat(String.valueOf(row.get("unit_snapshot"))).isEqualTo(balanceRow(wh, sku).getUnit());
                // V34 起：出库方向的流水必须带成本，写的是出库那一刻的余额均价。
                // 「报损没有成本依据」不是这里的规则：报损同样是出库，成本随数量一起离开仓库。
                // 移动加权平均的性质是「出库不改变均价」，所以事后再读余额拿到的仍是同一个值。
                assertThat(decimal(row, "unit_cost"))
                        .isEqualByComparingTo(balanceRow(wh, sku).getAvgCost());
            }

            @Test
            @DisplayName("报溢：审批通过写 GAIN_REPORT 并累加余额，保存的数据方向为加")
            void overflowAddsStockAtApproval() {
                Object[] s = stocked("lg2", "10.0000");
                Long wh = (Long) s[0];
                Long sku = (Long) s[1];

                Long id = lossGainService.create(form("OVERFLOW", wh, sku, "5.0000", "盘点外发现多出一批"));
                approveAsAuditor(id, audit(id, null));

                assertThat(statusOf(id)).isEqualTo("COMPLETED");
                assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("15.0000");

                Map<String, Object> row = lossGainMovements(wh, sku).getFirst();
                assertThat(row.get("movement_type")).isEqualTo("GAIN_REPORT");
                assertThat(decimal(row, "quantity")).isEqualByComparingTo("5.0000");
                assertThat(decimal(row, "before_quantity")).isEqualByComparingTo("10.0000");
                assertThat(decimal(row, "after_quantity")).isEqualByComparingTo("15.0000");
            }

            @Test
            @DisplayName("报损后低于预留量被拒（41034），余额与状态都不变 —— 已预留的货不能被报损吃掉")
            void lossBelowReservedIsRejected() {
                Object[] s = stocked("lg3", "10.0000");
                Long wh = (Long) s[0];
                Long sku = (Long) s[1];

                reservations.reserve(new ReserveInventoryFact(
                        wh, sku, "SALES_ORDER_ITEM", 730001L, 830001L,
                        new BigDecimal("8.0000"), OffsetDateTime.now(), null));

                // 10 − 5 = 5 < 已预留 8
                Long id = lossGainService.create(form("LOSS", wh, sku, "5.0000", "破损"));
                expectCode(() -> approveAsAuditor(id, audit(id, null)), 41034);

                assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("10.0000");
                assertThat(balanceRow(wh, sku).getReservedQuantity()).isEqualByComparingTo("8.0000");
                assertThat(lossGainMovements(wh, sku)).isEmpty();
                // 失败后单据仍是待审核，可以改数量后重试
                assertThat(statusOf(id)).isEqualTo("PENDING");
            }

            @Test
            @DisplayName("报损超过现有库存被拒（41033），不引入负库存")
            void lossBeyondOnHandIsRejected() {
                Object[] s = stocked("lg4", "5.0000");
                Long wh = (Long) s[0];
                Long sku = (Long) s[1];

                Long id = lossGainService.create(form("LOSS", wh, sku, "6.0000", "变质"));
                expectCode(() -> approveAsAuditor(id, audit(id, null)), 41033);

                assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("5.0000");
                assertThat(lossGainMovements(wh, sku)).isEmpty();
            }

            @Test
            @DisplayName("从未入库的 (仓库, SKU)：可建单但审批被拒（41032），且不会建出零余额行")
            void lossGainWithoutAnyBalanceIsRejectedAtApprovalWithoutCreatingRow() {
                Long skuId = newSkuOfType("lg5", "NON_STANDARD", "ON_SHELF");
                Long wh = seedWarehouseId();
                assertThat(balanceRowCount(wh, skuId)).isZero();

                // 建单不依赖余额（它只是记录意图），因此这里成功 —— 与出库单同一取向
                Long id = lossGainService.create(form("LOSS", wh, skuId, "1.0000", "丢失"));

                // 审批时才失败：记账单位只能来自余额行，从未入库的 SKU 无账可调
                expectCode(() -> approveAsAuditor(id, audit(id, null)), 41032);
                assertThat(balanceRowCount(wh, skuId)).isZero();
                assertThat(statusOf(id)).isEqualTo("PENDING");
            }

            // ------------------------------------------------------------------
            // 状态守卫与乐观锁
            // ------------------------------------------------------------------

            @Test
            @DisplayName("已完成的单据是终态：不可再审批、不可改、不可删，余额不会被调整两次")
            void completedDocumentIsTerminal() {
                Object[] s = stocked("lg6", "10.0000");
                Long wh = (Long) s[0];
                Long sku = (Long) s[1];

                Long id = lossGainService.create(form("LOSS", wh, sku, "3.0000", "变质"));
                approveAsAuditor(id, audit(id, null));
                assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("7.0000");

                // 再审批：状态已变（不是版本问题）→ 41029
                expectCode(() -> approveAsAuditor(id, audit(id, null)), 41029);
                expectCode(() -> lossGainService.update(id, form("LOSS", wh, sku, "99.0000", "改大一点")), 41029);
                expectCode(() -> lossGainService.delete(id), 41029);

                assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("7.0000");
                assertThat(lossGainMovements(wh, sku)).hasSize(1);
            }

            @Test
            @DisplayName("审批乐观锁：待审核期间单据被改过 → 审批以 40921 失败，不写流水")
            void staleVersionIsRejectedAtApproval() {
                Object[] s = stocked("lg7", "10.0000");
                Long wh = (Long) s[0];
                Long sku = (Long) s[1];

                Long id = lossGainService.create(form("LOSS", wh, sku, "3.0000", "变质"));
                int seenByAuditor = versionOf(id);

                // 审批人打开单据之后，录单人把数量改大了
                lossGainService.update(id, form("LOSS", wh, sku, "9.0000", "变质"));

                // 审批人用他看到的版本提交 → 必须失败，否则他批准的是一个自己没看过的数量
                expectCode(() -> approveAsAuditor(id, audit(id, null, seenByAuditor)), 40921);
                assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("10.0000");
                assertThat(lossGainMovements(wh, sku)).isEmpty();
                assertThat(statusOf(id)).isEqualTo("PENDING");

                // 刷新后重新审批（用新版本）即可通过，且按改后的数量执行
                approveAsAuditor(id, audit(id, null));
                assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("1.0000");
            }

            @Test
            @DisplayName("驳回：必须填审核意见（41037），驳回后不动库存且不可再审")
            void rejectRequiresOpinionAndLeavesStockUntouched() {
                Object[] s = stocked("lg8", "10.0000");
                Long wh = (Long) s[0];
                Long sku = (Long) s[1];

                Long id = lossGainService.create(form("LOSS", wh, sku, "3.0000", "变质"));

                // 空意见的驳回必须被拒 —— 驳回是唯一把「为什么不行」传达给录单人的渠道
                expectCode(() -> rejectAsAuditor(id, audit(id, null)), 41037);
                expectCode(() -> rejectAsAuditor(id, audit(id, "   ")), 41037);

                rejectAsAuditor(id, audit(id, "请附变质照片"));

                assertThat(statusOf(id)).isEqualTo("REJECTED");
                assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("10.0000");
                assertThat(lossGainMovements(wh, sku)).isEmpty();
                // 驳回通知制单人：与状态变更同事务写入原生消息表，恰一条，dataId 指向本单
                assertThat(rejectMessagesFor(id)).isEqualTo(1);

                // 已驳回是终态
                expectCode(() -> approveAsAuditor(id, audit(id, null)), 41029);
                expectCode(() -> rejectAsAuditor(id, audit(id, "再驳一次")), 41029);
                expectCode(() -> lossGainService.delete(id), 41029);
                // 终态后的再次驳回被状态守卫拒绝，事务回滚，不产生第二条通知
                assertThat(rejectMessagesFor(id)).isEqualTo(1);
            }

            private int rejectMessagesFor(Long lossGainId) {
                // t_message 是全站共用表，data_id 只存业务单号字符串；订单确认等其它通知的 data_id
                // 可能与报损单 id 撞号，必须同时按业务类型收敛，否则用例结果取决于谁先占了同一个数字。
                return jdbc.queryForObject(
                        "SELECT count(*) FROM t_message WHERE message_type = ? AND data_id = ? AND receiver_user_id = 1",
                        Integer.class,
com.xsy.scm.base.module.support.message.constant.MessageTypeEnum.SCM_INVENTORY_LOSS_GAIN
                                .getValue(),
                        String.valueOf(lossGainId));
            }

            @Test
            @DisplayName("待审核可改明细，审批按改后的内容执行；改后版本前进")
            void pendingDocumentIsEditable() {
                Object[] s = stocked("lg9", "10.0000");
                Long wh = (Long) s[0];
                Long sku = (Long) s[1];

                Long id = lossGainService.create(form("LOSS", wh, sku, "3.0000", "变质"));
                int v0 = versionOf(id);

                lossGainService.update(id, form("LOSS", wh, sku, "4.0000", "变质（数量核对后修正）"));

                assertThat(versionOf(id)).isGreaterThan(v0);
                approveAsAuditor(id, audit(id, null));
                assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("6.0000");
                assertThat(decimal(lossGainMovements(wh, sku).getFirst(), "quantity")).isEqualByComparingTo("4.0000");
            }

            @Test
            @DisplayName("同一 SKU 在报损报溢单里出现两次被拒（41036）—— 数量不得被调整两次")
            void duplicateSkuInOneDocumentIsRejected() {
                Object[] s = stocked("lg10", "10.0000");
                Long wh = (Long) s[0];
                Long sku = (Long) s[1];

                InventoryLossGainAddForm form = form("LOSS", wh, sku, "3.0000", "变质");
                form.getItems().add(item(sku, "2.0000"));

                expectCode(() -> lossGainService.create(form), 41036);
                assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("10.0000");
            }

            @Test
            @DisplayName("草稿删除不产生任何库存影响")
            void deletingPendingDocumentHasNoStockEffect() {
                Object[] s = stocked("lg11", "10.0000");
                Long wh = (Long) s[0];
                Long sku = (Long) s[1];

                Long id = lossGainService.create(form("LOSS", wh, sku, "3.0000", "变质"));
                lossGainService.delete(id);

                assertThat(jdbc.queryForObject(
                        "SELECT deleted FROM xsy_inventory_loss_gain WHERE id = ?", Boolean.class, id)).isTrue();
                expectCode(() -> approveAsAuditor(id, audit(id, null)), 41028);
                assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("10.0000");
            }

            // ------------------------------------------------------------------
            // 命令侧约定
            // ------------------------------------------------------------------

            @Test
            @DisplayName("同一来源行重复报损报溢被拒（41035），不会调整两次")
            void duplicateLossGainFromSameSourceLineIsRejected() {
                Object[] s = stocked("lg12", "10.0000");
                Long wh = (Long) s[0];
                Long sku = (Long) s[1];

                InventoryLossGainFact fact = new InventoryLossGainFact(
                        wh, sku, 0L, 930001L, "LOSS", new BigDecimal("2.0000"),
                        OffsetDateTime.now(), "test:1");
                assertThat(inventoryCommandService.postLossGainAdjust(fact))
                        .isEqualTo(balanceRow(wh, sku).getUnit());

                expectCode(() -> inventoryCommandService.postLossGainAdjust(fact), 41035);
                assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("8.0000");
                assertThat(lossGainMovements(wh, sku)).hasSize(1);
            }

            @Test
            @DisplayName("未知单据类型被拒（41031）—— 方向无法确定，不能猜")
            void unknownAdjustTypeIsRejected() {
                Object[] s = stocked("lg13", "10.0000");
                Long wh = (Long) s[0];
                Long sku = (Long) s[1];

                expectCode(() -> inventoryCommandService.postLossGainAdjust(new InventoryLossGainFact(
                        wh, sku, 0L, 930002L, "CONVERT", new BigDecimal("1.0000"),
                        OffsetDateTime.now(), "test:1")), 41031);
                assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("10.0000");
            }

            @Test
            @DisplayName("Q7：报损报溢流水同样不可改删（新增类型不是绕过 append-only 的口子）")
            void lossGainMovementsAreStillAppendOnly() {
                Object[] s = stocked("lg14", "10.0000");
                Long wh = (Long) s[0];
                Long sku = (Long) s[1];

                Long id = lossGainService.create(form("LOSS", wh, sku, "3.0000", "变质"));
                approveAsAuditor(id, audit(id, null));

                Long movementId = ((Number) lossGainMovements(wh, sku).getFirst().get("id")).longValue();
                assertMovementImmutable(movementId);
            }

            @Test
            @DisplayName("报溢不改变预留量：报损报溢只动 quantity，不动 reserved_quantity")
            void overflowLeavesReservedUntouched() {
                Object[] s = stocked("lg15", "10.0000");
                Long wh = (Long) s[0];
                Long sku = (Long) s[1];

                reservations.reserve(new ReserveInventoryFact(
                        wh, sku, "SALES_ORDER_ITEM", 730002L, 830002L,
                        new BigDecimal("4.0000"), OffsetDateTime.now(), null));

                Long id = lossGainService.create(form("OVERFLOW", wh, sku, "5.0000", "多出一批"));
                approveAsAuditor(id, audit(id, null));

                InventoryBalanceEntity balance = balanceRow(wh, sku);
                assertThat(balance.getQuantity()).isEqualByComparingTo("15.0000");
                assertThat(balance.getReservedQuantity()).isEqualByComparingTo("4.0000");
            }
        }

        @Nested
        @DisplayName("V37 零成本余额重放修复（PG IT）")
        class ScmInventoryRepriceMigrationIT {
            private static final String V37 = "V37__scm_inventory_reprice_zero_cost_balances.sql";

            @Autowired
            private InventoryTransferService transferService;

            @Autowired
            private InventoryConversionService conversionService;

            // ------------------------------------------------------------------
            // 夹具
            // ------------------------------------------------------------------

            /**
             * 造一个已按默认采购价（6.2000）入库指定数量到默认启用仓库的 SKU。
             */
            private Long stockedInSeed(String suffix, String quantity) {
                Long skuId = newSkuOfType(suffix, "NON_STANDARD", "ON_SHELF");
                W6Fixture fixture = freeInboundFixture(suffix, skuId, quantity, "kg");
                confirmReceipt(fixture.receipt().getId(), quantity);
                return skuId;
            }

            private Long transferOneWay(Long from, Long to, Long skuId, String quantity) {
                InventoryTransferAddForm form = new InventoryTransferAddForm();
                form.setFromWarehouseId(from);
                form.setToWarehouseId(to);
                InventoryTransferAddForm.Item item = new InventoryTransferAddForm.Item();
                item.setSkuId(skuId);
                item.setQuantity(new BigDecimal(quantity));
                form.setItems(new ArrayList<>(List.of(item)));
                Long id = transferService.create(form);
                transferService.ship(id);
                transferService.receive(id);
                return id;
            }

            /**
             * 1 箱 → 10 kg 的整件拆零，审批完成。
             */
            private void convertOneWay(Long warehouseId, Long source, Long target,
                                       String sourceQty, String targetQty) {
                InventoryConversionAddForm.Item item = new InventoryConversionAddForm.Item();
                item.setSourceSkuId(source);
                item.setSourceQuantity(new BigDecimal(sourceQty));
                item.setSourceUnit("箱");
                item.setTargetSkuId(target);
                item.setTargetQuantity(new BigDecimal(targetQty));
                item.setTargetUnit("kg");
                InventoryConversionAddForm form = new InventoryConversionAddForm();
                form.setWarehouseId(warehouseId);
                form.setConvertType("SPLIT");
                form.setItems(new ArrayList<>(List.of(item)));
                Long id = conversionService.create(form);
                InventoryConversionAuditForm audit = new InventoryConversionAuditForm();
                audit.setVersion(jdbc.queryForObject(
                        "SELECT version FROM xsy_inventory_conversion WHERE id = ?", Integer.class, id));
                conversionService.approve(id, audit);
            }

            /**
             * 把库里其它零成本行暂时移出 V37 的候选集合。
             *
             * <p>V37 的候选是全库规则（{@code quantity > 0 AND avg_cost = 0}），而 IT 跑在共享开发库上，
             * 里面可能有与本次用例无关、本身账实就不平的零成本行 —— 那会让「V37 能不能修好我这行」
             * 变成「别人留下的破行能不能让我跑完」。只改均价一个字段，且全部在用例事务内，结束即回滚。
             */
            private void isolateCandidates(Collection<Long> keepSkuIds) {
                assertThat(keepSkuIds).as("候选集合必须留下至少一个本用例自己的行").isNotEmpty();
                String placeholders = keepSkuIds.stream().map(id -> "?").collect(Collectors.joining(","));
                // 拼进去的只有 "?" 占位符本身，SKU id 一律走绑定参数。
                jdbc.update("UPDATE xsy_inventory_balance SET avg_cost = 0.0001 "
                        + "WHERE deleted = FALSE AND quantity > 0 AND avg_cost = 0 "
                        + "AND sku_id NOT IN (" + placeholders + ")", keepSkuIds.toArray());
            }

            private void setAvgCost(Long warehouseId, Long skuId, String avgCost) {
                jdbc.update("UPDATE xsy_inventory_balance SET avg_cost = ? WHERE warehouse_id = ? AND sku_id = ? "
                        + "AND deleted = FALSE", new BigDecimal(avgCost), warehouseId, skuId);
                evictMybatisCache();
            }

            /**
             * V37 Step 1：重放候选行的流水并回写 avg_cost。
             */
            private void runReprice() {
                jdbc.execute(migrationSection(V37, "-- Step 1", "-- Step 2"));
            }

            /**
             * V37 Step 2：收尾断言（候选集合内不得残留「账本有成本、余额零均价」的行）。
             */
            private void runRepriceAssertion() {
                String sql = migrationSql(V37);
                int from = sql.indexOf("-- Step 2");
                assertThat(from).as("%s 中找不到 %s（标记被改名了？）", V37, "-- Step 2")
                        .isGreaterThanOrEqualTo(0);
                jdbc.execute(toCurrentTableNames(sql.substring(from)));
            }

            private void runV37() {
                runReprice();
                runRepriceAssertion();
            }

            private BigDecimal avgCostOf(Long warehouseId, Long skuId) {
                return balanceRow(warehouseId, skuId).getAvgCost();
            }

            /**
             * 直插一条流水。
             *
             * <p>只有直插才能造出「转入腿找不到配对转出腿」这类账本残缺形状 ——
             * 流水是 append-only，业务路径既写不出它也删不掉它。
             */
            private void insertMovement(Long warehouseId, Long skuId, String movementType,
                                        String sourceDocumentType, Long sourceItemId,
                                        String quantity, String unitCost) {
                jdbc.update("INSERT INTO xsy_inventory_movement (warehouse_id, sku_id, movement_type, "
                                + "source_document_type, source_document_id, source_document_item_id, "
                                + "quantity, unit_snapshot, unit_cost, before_quantity, after_quantity, "
                                + "occurred_at, operator, deleted) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, 'kg', ?::numeric, 0, ?::numeric, "
                                + "CURRENT_TIMESTAMP, ?, FALSE)",
                        warehouseId, skuId, movementType, sourceDocumentType, 999000L, sourceItemId,
                        new BigDecimal(quantity), unitCost == null ? null : new BigDecimal(unitCost),
                        new BigDecimal(quantity), "V37 IT");
            }

            private void insertZeroCostBalance(Long warehouseId, Long skuId, String quantity) {
                jdbc.update("INSERT INTO xsy_inventory_balance (warehouse_id, sku_id, unit, quantity, "
                                + "reserved_quantity, avg_cost, version, deleted) "
                                + "VALUES (?, ?, 'kg', ?, 0, 0, 0, FALSE)", warehouseId, skuId,
                        new BigDecimal(quantity));
                evictMybatisCache();
            }

            // ------------------------------------------------------------------
            // 重放修复
            // ------------------------------------------------------------------

            @Test
            @DisplayName("调拨转入被清零的历史余额行：按同一明细行转出腿的成本重放修复，且可重跑")
            void transferZeroedRowIsRepricedFromItsOutboundLeg() {
                Long wh1 = seedWarehouseId();
                Long wh2 = newWarehouse("V37A");
                Long sku = stockedInSeed("v37a", "10.0000");
                transferOneWay(wh1, wh2, sku, "4.0000");

                // 修复后的代码本来就会写对均价；这里把它改回 0，模拟缺陷上线期间留下的数据形状。
                isolateCandidates(List.of(sku));
                setAvgCost(wh2, sku, "0.0000");

                runV37();

                assertThat(avgCostOf(wh2, sku)).as("取转出腿的 6.2000，而不是目标行自己的 0")
                        .isEqualByComparingTo("6.2000");
                assertThat(balanceRow(wh2, sku).getQuantity()).as("数量本来就对，重放不该动它")
                        .isEqualByComparingTo("4.0000");

                // 防重复处理：派生值由账本唯一决定，重跑必须零变化（迁移可能在任何一次启动里被重放评估）。
                runV37();
                assertThat(avgCostOf(wh2, sku)).isEqualByComparingTo("6.2000");

                // 修复过的行不再落回零均价：Step 2 的收尾断言也必须在重跑后依然成立。
                runRepriceAssertion();
            }

            @Test
            @DisplayName("转换转入被清零的历史余额行：按配对转出腿的总成本 ÷ 转入数量折算")
            void conversionZeroedRowIsRepricedFromItsPairedLeg() {
                Long wh = seedWarehouseId();
                Long source = newSkuOfType("v37b", "NON_STANDARD", "ON_SHELF");
                W6Fixture fixture = freeInboundFixture("v37b", source, "5.0000", "箱");
                confirmReceipt(fixture.receipt().getId(), "5.0000");
                Long target = newSkuOfType("v37bt", "NON_STANDARD", "ON_SHELF");
                convertOneWay(wh, source, target, "2.0000", "20.0000");

                isolateCandidates(List.of(source, target));
                setAvgCost(wh, target, "0.0000");

                runV37();

                // 2 箱 × 6.20 = 12.40 的总成本摊到 20 kg → 0.6200 / kg（守恒的是总额，不是单价）。
                assertThat(avgCostOf(wh, target)).isEqualByComparingTo("0.6200");
                assertThat(avgCostOf(wh, source)).as("源行的均价本来就没被转换改过").isEqualByComparingTo("6.2000");
            }

            @Test
            @DisplayName("修复范围只有零均价：非零但规则偏差的行不动（那是 V34 登记的已知近似）")
            void nonZeroRowsAreLeftAlone() {
                Long wh1 = seedWarehouseId();
                Long wh2 = newWarehouse("V37C");
                Long sku = stockedInSeed("v37c", "10.0000");
                // 目标仓已有该 SKU、均价 1.0000（与源仓不同价）：调入后均价是被加权过的非零值。
                jdbc.update("INSERT INTO xsy_inventory_balance (warehouse_id, sku_id, unit, quantity, "
                        + "reserved_quantity, avg_cost, version, deleted) "
                        + "VALUES (?, ?, 'kg', 10, 0, 1.0000, 0, FALSE)", wh2, sku);
                evictMybatisCache();
                transferOneWay(wh1, wh2, sku, "2.0000");

                BigDecimal afterTransfer = avgCostOf(wh2, sku);
                assertThat(afterTransfer).as("(10×1.00 + 2×6.20) / 12 = 1.8667").isEqualByComparingTo("1.8667");

                isolateCandidates(List.of(sku));
                runV37();

                assertThat(avgCostOf(wh2, sku)).as("非零均价不在 V37 的候选集合里，必须一字不动")
                        .isEqualByComparingTo(afterTransfer);
            }

            // ------------------------------------------------------------------
            // 响亮失败
            // ------------------------------------------------------------------

            @Test
            @DisplayName("账实不符的候选行：重放数量对不上存量 → RAISE，不写一个看起来合理的均价")
            void brokenReplayFailsLoudly() {
                Long wh1 = seedWarehouseId();
                Long wh2 = newWarehouse("V37D");
                Long sku = stockedInSeed("v37d", "10.0000");
                transferOneWay(wh1, wh2, sku, "4.0000");

                isolateCandidates(List.of(sku));
                setAvgCost(wh2, sku, "0.0000");
                // 把存量数量改成流水加不出来的值：这行已经坏到不能用账本定价。
                jdbc.update("UPDATE xsy_inventory_balance SET quantity = quantity + 1 "
                        + "WHERE warehouse_id = ? AND sku_id = ? AND deleted = FALSE", wh2, sku);
                evictMybatisCache();

                assertThatThrownBy(this::runReprice)
                        .hasMessageContaining("V37 cannot price")
                        .hasMessageContaining("ledger replays to");
            }

            @Test
            @DisplayName("转入腿找不到配对转出腿 → RAISE：按 0 定价正是本迁移要消除的缺陷")
            void inboundLegWithoutPairedOutboundFailsLoudly() {
                Long wh = seedWarehouseId();
                Long sku = newSkuOfType("v37e", "NON_STANDARD", "ON_SHELF");
                isolateCandidates(List.of(sku));
                insertZeroCostBalance(wh, sku, "5.0000");
                // TRANSFER_IN 有了，但同一明细行的 TRANSFER_OUT 从没写过 —— 账本残缺。
                insertMovement(wh, sku, "TRANSFER_IN", "TRANSFER_IN_ITEM",
                        sentinelItemId(), "5.0000", "0.0000");

                assertThatThrownBy(this::runReprice)
                        .hasMessageContaining("V37 cannot price")
                        .hasMessageContaining("no paired outbound movement");
            }

            @Test
            @DisplayName("盘盈进来的零成本行保持 0：迁移不凭空造价格")
            void stockGainOnlyRowIsNotInventedAPrice() {
                Long wh = seedWarehouseId();
                Long sku = newSkuOfType("v37f", "NON_STANDARD", "ON_SHELF");
                isolateCandidates(List.of(sku));
                insertZeroCostBalance(wh, sku, "5.0000");
                // STOCKTAKE_GAIN 的 unit_cost 为 NULL：盘点补的是数量，不是采购价格记录。
                insertMovement(wh, sku, "STOCKTAKE_GAIN", "STOCKTAKE_ITEM",
                        sentinelItemId(), "5.0000", null);

                runV37();

                assertThat(avgCostOf(wh, sku)).as("没有成本记录就仍是 0，不编一个价")
                        .isEqualByComparingTo("0.0000");
            }

            /**
             * 每次调用给一个没用过的来源行 id，避免撞 {@code uk_inventory_movement_source_active}。
             */
            private static Long sentinelItemId() {
                return ThreadLocalRandom.current().nextLong(100_000_000L, 1_000_000_000L);
            }

            // ------------------------------------------------------------------
            // 约定
            // ------------------------------------------------------------------

            @Test
            @DisplayName("V37 是纯派生状态修复：不碰流水、不碰余额数量，且只有两条 DO")
            void migrationRepairsOnlyDerivedState() {
                String sql = migrationSql(V37);

                assertThat(sql).as("流水是 append-only 账本，迁移一行都不许改")
                        .doesNotContain("UPDATE inventory_movement")
                        .doesNotContain("DELETE FROM inventory_movement");
                assertThat(sql).as("只回填 avg_cost 这一个派生列")
                        .doesNotContain("SET quantity")
                        .doesNotContain("SET reserved_quantity");
                assertThat(sql.split("DO \\$\\$", -1).length - 1).isEqualTo(2);
            }

            @Test
            @DisplayName("候选规则写在 SQL 里：quantity > 0 AND avg_cost = 0 两处都必须在")
            void candidateScopeIsTheZeroCostSymptom() {
                String step1 = migrationSection(V37, "-- Step 1", "-- Step 2");
                String step2 = migrationSql(V37).substring(migrationSql(V37).indexOf("-- Step 2"));

                assertThat(step1).contains("AND b.quantity > 0").contains("AND b.avg_cost = 0");
                assertThat(step2).contains("AND b.quantity > 0").contains("AND b.avg_cost = 0");
            }
        }

        @Nested
        @DisplayName("盘点（PG IT）")
        class ScmInventoryStocktakeIT {
            @Autowired
            private InventoryStocktakeService stocktakeService;

            @Autowired
            private InventoryReservationService reservations;

            /**
             * 造一个已入库指定数量的 SKU，返回 {@code (warehouseId, skuId)}。
             */
            private Object[] stocked(String suffix, String quantity) {
                Long skuId = newSkuOfType(suffix, "NON_STANDARD", "ON_SHELF");
                W6Fixture fixture = inboundFixture(suffix, skuId, quantity);
                confirmReceipt(fixture.receipt().getId(), quantity);
                return new Object[]{seedWarehouseId(), skuId};
            }

            private InventoryStocktakeAddForm form(Long warehouseId, Long skuId, String actual) {
                InventoryStocktakeAddForm form = new InventoryStocktakeAddForm();
                form.setWarehouseId(warehouseId);
                InventoryStocktakeAddForm.Item item = new InventoryStocktakeAddForm.Item();
                item.setSkuId(skuId);
                item.setActualQuantity(new BigDecimal(actual));
                form.setItems(new ArrayList<>(List.of(item)));
                return form;
            }

            private InventoryOutboundFact outboundFact(Long wh, Long sku, String qty, long itemId) {
                return new InventoryOutboundFact(wh, sku, 0L, itemId, new BigDecimal(qty), null,
                        OffsetDateTime.now(), "test:1");
            }

            /**
             * 某个 (仓库, SKU) 的盘点流水（盘盈 + 盘亏），按业务时刻升序。
             */
            private List<Map<String, Object>> stocktakeMovements(Long wh, Long sku) {
                return movementsOf(wh, sku).stream()
                        .filter(m -> String.valueOf(m.get("movement_type")).startsWith("STOCKTAKE_"))
                        .toList();
            }

            private static BigDecimal decimal(Map<String, Object> row, String column) {
                return new BigDecimal(String.valueOf(row.get(column)));
            }

            // ------------------------------------------------------------------
            // 盘盈 / 盘亏 / 账实相符
            // ------------------------------------------------------------------

            @Test
            @DisplayName("盘盈：实盘高于账面 → 余额增加并写 STOCKTAKE_GAIN，保存的数据方向为加")
            void gainIncreasesBalanceAndWritesInboundSnapshot() {
                Object[] s = stocked("st1", "10.0000");
                Long wh = (Long) s[0];
                Long sku = (Long) s[1];

                stocktakeService.confirm(stocktakeService.create(form(wh, sku, "12.0000")));

                assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("12.0000");

                List<Map<String, Object>> rows = stocktakeMovements(wh, sku);
                assertThat(rows).hasSize(1);
                Map<String, Object> row = rows.getFirst();
                assertThat(row.get("movement_type")).isEqualTo("STOCKTAKE_GAIN");
                assertThat(row.get("source_document_type")).isEqualTo("STOCKTAKE_ITEM");
                assertThat(decimal(row, "quantity")).isEqualByComparingTo("2.0000");
                // 方向感知保存的数据：盘盈是加，这正是 V29 必须重建 ck_inventory_movement_snap 的原因
                assertThat(decimal(row, "before_quantity")).isEqualByComparingTo("10.0000");
                assertThat(decimal(row, "after_quantity")).isEqualByComparingTo("12.0000");
                // 单位以余额记账单位为准，由服务端取，不由调用方传
                assertThat(String.valueOf(row.get("unit_snapshot"))).isEqualTo(balanceRow(wh, sku).getUnit());
                // V34 起：盘盈按现有均价入账（它不带来新的采购价格信息，均价因此不变），
                // 流水必须写下这个成本。
                assertThat(decimal(row, "unit_cost"))
                        .isEqualByComparingTo(balanceRow(wh, sku).getAvgCost());
            }

            @Test
            @DisplayName("盘亏：实盘低于账面 → 余额减少并写 STOCKTAKE_LOSS，保存的数据方向为减")
            void lossDecreasesBalanceAndWritesOutboundSnapshot() {
                Object[] s = stocked("st2", "10.0000");
                Long wh = (Long) s[0];
                Long sku = (Long) s[1];

                stocktakeService.confirm(stocktakeService.create(form(wh, sku, "8.0000")));

                assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("8.0000");

                Map<String, Object> row = stocktakeMovements(wh, sku).getFirst();
                assertThat(row.get("movement_type")).isEqualTo("STOCKTAKE_LOSS");
                assertThat(decimal(row, "quantity")).isEqualByComparingTo("2.0000");
                assertThat(decimal(row, "before_quantity")).isEqualByComparingTo("10.0000");
                assertThat(decimal(row, "after_quantity")).isEqualByComparingTo("8.0000");
            }

            @Test
            @DisplayName("账实相符：差异为 0 时不写流水，但当时的单位仍回写")
            void zeroDeltaWritesNoMovementButStillStampsUnit() {
                Object[] s = stocked("st3", "10.0000");
                Long wh = (Long) s[0];
                Long sku = (Long) s[1];

                Long id = stocktakeService.create(form(wh, sku, "10.0000"));
                stocktakeService.confirm(id);

                assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("10.0000");
                // 只有入库那一条流水，没有盘点流水 —— quantity 恒为正，写不出「零差异」流水
                assertThat(stocktakeMovements(wh, sku)).isEmpty();
                assertThat(movementCount(wh, sku)).isEqualTo(1);

                // 差异为 0 的行仍然回写当时的单位（确认动作本身走完了）
                String unit = jdbc.queryForObject(
                        "SELECT unit_snapshot FROM xsy_inventory_stocktake_item WHERE stocktake_id = ?",
                        String.class, id);
                assertThat(unit).isEqualTo(balanceRow(wh, sku).getUnit());
            }

            // ------------------------------------------------------------------
            // 核心规则：差异施加到「确认瞬间的账面量」
            // ------------------------------------------------------------------

            @Test
            @DisplayName("核心规则：差异施加到确认瞬间的账面量，保存草稿后的出库不会被抹掉")
            void deltaIsAppliedToCurrentOnHandNotToTheSnapshot() {
                Object[] s = stocked("st4", "10.0000");
                Long wh = (Long) s[0];
                Long sku = (Long) s[1];

                // 保存草稿：账面量保存的数据 = 10，实盘 12 → 清点发现的差异 delta = +2
                Long id = stocktakeService.create(form(wh, sku, "12.0000"));

                // 确认之前发生一次出库：确认瞬间的账面量变成 7
                inventoryCommandService.postSalesOutbound(outboundFact(wh, sku, "3.0000", 910001L));
                assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("7.0000");

                stocktakeService.confirm(id);

                // after = live + delta = 7 + 2 = 9。
                // 若实现是「把账面直接改写成实盘数」，这里会得到 12，那笔出库就被悄悄抹掉了。
                assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("9.0000");

                Map<String, Object> row = stocktakeMovements(wh, sku).getFirst();
                assertThat(row.get("movement_type")).isEqualTo("STOCKTAKE_GAIN");
                assertThat(decimal(row, "quantity")).as("差异 = 实盘 − 导出时的账面数据").isEqualByComparingTo("2.0000");
                assertThat(decimal(row, "before_quantity")).as("before 是确认瞬间的账面量").isEqualByComparingTo("7.0000");
                assertThat(decimal(row, "after_quantity")).isEqualByComparingTo("9.0000");
            }

            // ------------------------------------------------------------------
            // 下限：可用量与预留
            // ------------------------------------------------------------------

            @Test
            @DisplayName("盘亏把库存推成负数被拒（41024），余额不变 —— 不引入负库存")
            void lossThatWouldGoNegativeIsRejected() {
                Object[] s = stocked("st5", "10.0000");
                Long wh = (Long) s[0];
                Long sku = (Long) s[1];

                // 导出时的账面数据 10、实盘 0 → delta = −10；确认前出库 6，live = 4 → after = −6
                Long id = stocktakeService.create(form(wh, sku, "0.0000"));
                inventoryCommandService.postSalesOutbound(outboundFact(wh, sku, "6.0000", 910002L));

                expectCode(() -> stocktakeService.confirm(id), 41024);
                assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("4.0000");
                assertThat(stocktakeMovements(wh, sku)).isEmpty();
            }

            @Test
            @DisplayName("盘亏到低于已预留量被拒（41025）—— 已预留的货不能被盘点吃掉")
            void lossBelowReservedIsRejected() {
                Object[] s = stocked("st6", "10.0000");
                Long wh = (Long) s[0];
                Long sku = (Long) s[1];

                reservations.reserve(new ReserveInventoryFact(
                        wh, sku, "SALES_ORDER_ITEM", 710001L, 810001L,
                        new BigDecimal("8.0000"), OffsetDateTime.now(), null));

                // 导出时的账面数据 10、实盘 7 → delta = −3 → after = 7 < 已预留 8
                expectCode(() -> stocktakeService.confirm(stocktakeService.create(form(wh, sku, "7.0000"))), 41025);

                InventoryBalanceEntity balance = balanceRow(wh, sku);
                assertThat(balance.getQuantity()).isEqualByComparingTo("10.0000");
                assertThat(balance.getReservedQuantity()).isEqualByComparingTo("8.0000");
                assertThat(stocktakeMovements(wh, sku)).isEmpty();
            }

            @Test
            @DisplayName("盘盈不改预留量：盘点只动 quantity，不动 reserved_quantity")
            void gainLeavesReservedUntouched() {
                Object[] s = stocked("st7", "10.0000");
                Long wh = (Long) s[0];
                Long sku = (Long) s[1];

                reservations.reserve(new ReserveInventoryFact(
                        wh, sku, "SALES_ORDER_ITEM", 710002L, 810002L,
                        new BigDecimal("4.0000"), OffsetDateTime.now(), null));

                stocktakeService.confirm(stocktakeService.create(form(wh, sku, "12.0000")));

                InventoryBalanceEntity balance = balanceRow(wh, sku);
                assertThat(balance.getQuantity()).isEqualByComparingTo("12.0000");
                assertThat(balance.getReservedQuantity()).isEqualByComparingTo("4.0000");
            }

            // ------------------------------------------------------------------
            // 入口校验
            // ------------------------------------------------------------------

            @Test
            @DisplayName("从未入库的 (仓库, SKU) 盘点被拒（41023），且不会建出零余额行")
            void stocktakeWithoutAnyBalanceIsRejectedWithoutCreatingRow() {
                Long skuId = newSkuOfType("st8", "NON_STANDARD", "ON_SHELF");
                Long wh = seedWarehouseId();
                assertThat(balanceRowCount(wh, skuId)).isZero();

                // 记账单位只能来自余额行，因此「凭空盘盈」必须被挡住
                expectCode(() -> stocktakeService.create(form(wh, skuId, "5.0000")), 41023);
                assertThat(balanceRowCount(wh, skuId)).isZero();
            }

            @Test
            @DisplayName("同一 SKU 在盘点单里出现两次被拒（41027）—— 差异不得被施加两次")
            void duplicateSkuInOneStocktakeIsRejected() {
                Object[] s = stocked("st9", "10.0000");
                Long wh = (Long) s[0];
                Long sku = (Long) s[1];

                InventoryStocktakeAddForm form = form(wh, sku, "12.0000");
                InventoryStocktakeAddForm.Item second = new InventoryStocktakeAddForm.Item();
                second.setSkuId(sku);
                second.setActualQuantity(new BigDecimal("13.0000"));
                form.getItems().add(second);

                expectCode(() -> stocktakeService.create(form), 41027);
                assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("10.0000");
            }

            @Test
            @DisplayName("已确认的盘点单不可再确认、不可再改（41020），余额不会被调整两次")
            void confirmedStocktakeIsTerminal() {
                Object[] s = stocked("st10", "10.0000");
                Long wh = (Long) s[0];
                Long sku = (Long) s[1];

                Long id = stocktakeService.create(form(wh, sku, "12.0000"));
                stocktakeService.confirm(id);
                assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("12.0000");

                expectCode(() -> stocktakeService.confirm(id), 41020);
                expectCode(() -> stocktakeService.update(id, form(wh, sku, "20.0000")), 41020);
                expectCode(() -> stocktakeService.delete(id), 41020);

                assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("12.0000");
                assertThat(stocktakeMovements(wh, sku)).hasSize(1);
            }

            @Test
            @DisplayName("草稿取消不产生任何库存影响，且不可再确认")
            void cancelledDraftHasNoStockEffect() {
                Object[] s = stocked("st11", "10.0000");
                Long wh = (Long) s[0];
                Long sku = (Long) s[1];

                Long id = stocktakeService.create(form(wh, sku, "15.0000"));
                stocktakeService.cancel(id);

                expectCode(() -> stocktakeService.confirm(id), 41020);
                assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("10.0000");
                assertThat(stocktakeMovements(wh, sku)).isEmpty();
            }

            @Test
            @DisplayName("改草稿会重新保存的数据账面量：基线跟着账面走，不保存在建单那一刻")
            void updatingDraftRefreshesTheBookSnapshot() {
                Object[] s = stocked("st12", "10.0000");
                Long wh = (Long) s[0];
                Long sku = (Long) s[1];

                Long id = stocktakeService.create(form(wh, sku, "10.0000"));

                // 账面变成 6 之后重存草稿 → 基线刷新为 6，实盘 10 的差异变成 +4
                inventoryCommandService.postSalesOutbound(outboundFact(wh, sku, "4.0000", 910003L));
                stocktakeService.update(id, form(wh, sku, "10.0000"));

                stocktakeService.confirm(id);
                assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("10.0000");
                assertThat(decimal(stocktakeMovements(wh, sku).getFirst(), "quantity")).isEqualByComparingTo("4.0000");
            }

            // ------------------------------------------------------------------
            // 命令侧约定
            // ------------------------------------------------------------------

            @Test
            @DisplayName("同一来源行重复盘点被拒（41026），不会调整两次")
            void duplicateStocktakeFromSameSourceLineIsRejected() {
                Object[] s = stocked("st13", "10.0000");
                Long wh = (Long) s[0];
                Long sku = (Long) s[1];

                InventoryStocktakeFact fact = new InventoryStocktakeFact(
                        wh, sku, 0L, 920001L, new BigDecimal("10.0000"), new BigDecimal("12.0000"),
                        OffsetDateTime.now(), "test:1");
                InventoryStocktakeAdjustment first = inventoryCommandService.postStocktakeAdjust(fact);
                assertThat(first.movementWritten()).isTrue();
                assertThat(first.delta()).isEqualByComparingTo("2.0000");

                expectCode(() -> inventoryCommandService.postStocktakeAdjust(fact), 41026);
                assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("12.0000");
                assertThat(stocktakeMovements(wh, sku)).hasSize(1);
            }

            @Test
            @DisplayName("差异为 0 时命令侧返回 movementWritten = false（不写流水）")
            void zeroDeltaCommandReportsNoMovement() {
                Object[] s = stocked("st14", "10.0000");
                Long wh = (Long) s[0];
                Long sku = (Long) s[1];

                InventoryStocktakeAdjustment result = inventoryCommandService.postStocktakeAdjust(
                        new InventoryStocktakeFact(wh, sku, 0L, 920002L, new BigDecimal("10.0000"),
                                new BigDecimal("10.0000"), OffsetDateTime.now(), "test:1"));

                assertThat(result.movementWritten()).isFalse();
                assertThat(result.delta()).isEqualByComparingTo("0.0000");
                assertThat(result.beforeQuantity()).isEqualByComparingTo("10.0000");
                assertThat(result.afterQuantity()).isEqualByComparingTo("10.0000");
                assertThat(stocktakeMovements(wh, sku)).isEmpty();
            }

            @Test
            @DisplayName("Q7：盘点流水同样不可改删（新增类型不是绕过 append-only 的口子）")
            void stocktakeMovementsAreStillAppendOnly() {
                Object[] s = stocked("st15", "10.0000");
                Long wh = (Long) s[0];
                Long sku = (Long) s[1];

                stocktakeService.confirm(stocktakeService.create(form(wh, sku, "8.0000")));

                Long movementId = ((Number) stocktakeMovements(wh, sku).getFirst().get("id")).longValue();
                assertMovementImmutable(movementId);
            }
        }

        @Nested
        @DisplayName("调拨（PG IT）")
        class ScmInventoryTransferIT {
            @Autowired
            private InventoryTransferService transferService;

            @Autowired
            private com.xsy.scm.inventory.service.InventoryTransferQueryService transferQueryService;

            @Autowired
            private InventoryReservationService reservations;

            @Autowired
            private WarehouseDisableGuard warehouseDisableGuard;

            /**
             * 造一个已入库指定数量到默认启用仓库的 SKU，返回 skuId。
             */
            private Long stockedInSeed(String suffix, String quantity) {
                Long skuId = newSkuOfType(suffix, "NON_STANDARD", "ON_SHELF");
                W6Fixture fixture = inboundFixture(suffix, skuId, quantity);
                confirmReceipt(fixture.receipt().getId(), quantity);
                return skuId;
            }

            private static InventoryTransferAddForm.Item item(Long skuId, String quantity) {
                InventoryTransferAddForm.Item row = new InventoryTransferAddForm.Item();
                row.setSkuId(skuId);
                row.setQuantity(new BigDecimal(quantity));
                return row;
            }

            private InventoryTransferAddForm form(Long from, Long to, Long sku, String qty) {
                InventoryTransferAddForm form = new InventoryTransferAddForm();
                form.setFromWarehouseId(from);
                form.setToWarehouseId(to);
                form.setItems(new ArrayList<>(List.of(item(sku, qty))));
                return form;
            }

            private String statusOf(Long id) {
                return jdbc.queryForObject(
                        "SELECT status FROM xsy_inventory_transfer WHERE id = ?", String.class, id);
            }

            /**
             * 指定类型的流水（同一 (仓库, SKU) 上可能同时有转出与转入）。
             */
            private Map<String, Object> movementOf(Long wh, Long sku, String movementType) {
                return movementsOf(wh, sku).stream()
                        .filter(m -> movementType.equals(String.valueOf(m.get("movement_type"))))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("找不到 " + movementType + " 流水"));
            }

            private static BigDecimal decimal(Map<String, Object> row, String column) {
                return new BigDecimal(String.valueOf(row.get(column)));
            }

            // ------------------------------------------------------------------
            // 两步式核心
            // ------------------------------------------------------------------

            @Test
            @DisplayName("两步式：发出后源仓已扣、目标仓未加；收货后目标仓建立余额行并累加")
            void twoStepShipThenReceiveMovesStockAcrossWarehouses() {
                Long sku = stockedInSeed("tf1", "10.0000");
                Long wh1 = seedWarehouseId();
                Long wh2 = newWarehouse("TF1B");

                Long id = transferService.create(form(wh1, wh2, sku, "4.0000"));

                // 待发货：什么都没动
                assertThat(statusOf(id)).isEqualTo("PENDING");
                assertThat(balanceRow(wh1, sku).getQuantity()).isEqualByComparingTo("10.0000");
                assertThat(balanceRow(wh2, sku)).isNull();

                transferService.ship(id);

                // 发出后：源仓已扣、目标仓还没有 —— 在途期间这批货不在任何余额行里。
                // 这不是缺陷，是两步式的必然结果（inventory_balance 只表达「在仓库里的货」）。
                assertThat(statusOf(id)).isEqualTo("SHIPPED");
                assertThat(balanceRow(wh1, sku).getQuantity()).isEqualByComparingTo("6.0000");
                assertThat(balanceRow(wh2, sku)).isNull();

                Map<String, Object> out = movementOf(wh1, sku, "TRANSFER_OUT");
                assertThat(out.get("source_document_type")).isEqualTo("TRANSFER_OUT_ITEM");
                assertThat(decimal(out, "quantity")).isEqualByComparingTo("4.0000");
                assertThat(decimal(out, "before_quantity")).isEqualByComparingTo("10.0000");
                assertThat(decimal(out, "after_quantity")).isEqualByComparingTo("6.0000");
                // 调拨转出按源仓当时的均价记成本（出库不改变均价），V34 起的移动加权规则。
                // 这条流水同时是收货时的转入成本基准 —— 见 transferInCost 的回读。
                assertThat(decimal(out, "unit_cost"))
                        .isEqualByComparingTo(balanceRow(wh1, sku).getAvgCost());

                transferService.receive(id);

                // 收货后：目标仓由本次调入建立余额行（入方向允许建行），源仓不再变化
                assertThat(statusOf(id)).isEqualTo("RECEIVED");
                assertThat(balanceRow(wh2, sku).getQuantity()).isEqualByComparingTo("4.0000");
                assertThat(balanceRow(wh1, sku).getQuantity()).isEqualByComparingTo("6.0000");

                Map<String, Object> in = movementOf(wh2, sku, "TRANSFER_IN");
                assertThat(in.get("source_document_type")).isEqualTo("TRANSFER_IN_ITEM");
                assertThat(decimal(in, "quantity")).isEqualByComparingTo("4.0000");
                assertThat(decimal(in, "before_quantity")).isEqualByComparingTo("0.0000");
                assertThat(decimal(in, "after_quantity")).isEqualByComparingTo("4.0000");
                // 当时的单位 = 源仓记账单位，目标仓新建行时用的就是它
                assertThat(String.valueOf(in.get("unit_snapshot"))).isEqualTo(balanceRow(wh1, sku).getUnit());

                // 成本守恒：转入腿带的是转出腿的成本，不是目标行当时的 0 均价。
                // 旧实现取目标行的 avg_cost（新建行 = 0），整批货的成本就此清零 —— 数量对、金额账全丢。
                assertThat(decimal(in, "unit_cost"))
                        .as("转入腿回读同一明细行转出腿的成本")
                        .isEqualByComparingTo(decimal(out, "unit_cost"));
                assertThat(balanceRow(wh2, sku).getAvgCost())
                        .as("新建的目标余额行按转入成本入账")
                        .isEqualByComparingTo(decimal(out, "unit_cost"));
            }

            @Test
            @DisplayName("一条明细行产生两条流水，两个方向各占一个来源类型（唯一索引的要求）")
            void oneItemProducesTwoMovementsUnderTwoSourceTypes() {
                Long sku = stockedInSeed("tf2", "10.0000");
                Long wh1 = seedWarehouseId();
                Long wh2 = newWarehouse("TF2B");

                Long id = transferService.create(form(wh1, wh2, sku, "3.0000"));
                transferService.ship(id);
                transferService.receive(id);

                // 同一明细行 id 被两条流水引用 —— 若共用一个来源类型，第二条会撞唯一索引
                Long itemId = jdbc.queryForObject(
                        "SELECT id FROM xsy_inventory_transfer_item WHERE transfer_id = ? AND deleted = FALSE",
                        Long.class, id);
                // 必须带上来源类型：明细行 id 只在其来源表内唯一，共享开发库里其它单据表的明细行
                // 可能取到同一个数值。谓词与 uk_inventory_movement_source_active 的冲突域一致，
                // 断言语义不变 —— 两条腿若共用一个类型，这里只会拿到一行。
                List<Map<String, Object>> both = jdbc.queryForList(
                        "SELECT movement_type, source_document_type FROM xsy_inventory_movement "
                                + "WHERE source_document_item_id = ? "
                                + "AND source_document_type IN ('TRANSFER_OUT_ITEM', 'TRANSFER_IN_ITEM') "
                                + "ORDER BY id", itemId);
                assertThat(both).hasSize(2);
                assertThat(both.get(0).get("movement_type")).isEqualTo("TRANSFER_OUT");
                assertThat(both.get(0).get("source_document_type")).isEqualTo("TRANSFER_OUT_ITEM");
                assertThat(both.get(1).get("movement_type")).isEqualTo("TRANSFER_IN");
                assertThat(both.get(1).get("source_document_type")).isEqualTo("TRANSFER_IN_ITEM");
            }

            // ------------------------------------------------------------------
            // 源仓的下限
            // ------------------------------------------------------------------

            @Test
            @DisplayName("转出超过源仓现有量被拒（41043），单据仍是待发货、目标仓不变")
            void shipBeyondOnHandIsRejected() {
                Long sku = stockedInSeed("tf3", "5.0000");
                Long wh1 = seedWarehouseId();
                Long wh2 = newWarehouse("TF3B");

                Long id = transferService.create(form(wh1, wh2, sku, "6.0000"));
                expectCode(() -> transferService.ship(id), 41043);

                assertThat(statusOf(id)).isEqualTo("PENDING");
                assertThat(balanceRow(wh1, sku).getQuantity()).isEqualByComparingTo("5.0000");
                assertThat(balanceRow(wh2, sku)).isNull();
            }

            @Test
            @DisplayName("转出不得吃掉源仓已预留的货（41043）—— 可用量 = 现有量 − 预留量")
            void shipCannotConsumeReservedStock() {
                Long sku = stockedInSeed("tf4", "10.0000");
                Long wh1 = seedWarehouseId();
                Long wh2 = newWarehouse("TF4B");

                reservations.reserve(new ReserveInventoryFact(
                        wh1, sku, "SALES_ORDER_ITEM", 750001L, 850001L,
                        new BigDecimal("8.0000"), OffsetDateTime.now(), null));

                // 现有量 10，其中 8 已预留 → 可用量只有 2
                Long id = transferService.create(form(wh1, wh2, sku, "3.0000"));
                expectCode(() -> transferService.ship(id), 41043);
                assertThat(balanceRow(wh1, sku).getQuantity()).isEqualByComparingTo("10.0000");
                assertThat(balanceRow(wh1, sku).getReservedQuantity()).isEqualByComparingTo("8.0000");

                // 出满可用量是允许的
                Long ok = transferService.create(form(wh1, wh2, sku, "2.0000"));
                transferService.ship(ok);
                assertThat(balanceRow(wh1, sku).getQuantity()).isEqualByComparingTo("8.0000");
            }

            @Test
            @DisplayName("源仓从未入库该 SKU → 发出被拒（41046），且不会建出零余额行")
            void shipWithoutSourceBalanceIsRejectedWithoutCreatingRow() {
                Long skuId = newSkuOfType("tf5", "NON_STANDARD", "ON_SHELF");
                Long wh1 = seedWarehouseId();
                Long wh2 = newWarehouse("TF5B");
                assertThat(balanceRowCount(wh1, skuId)).isZero();

                Long id = transferService.create(form(wh1, wh2, skuId, "1.0000"));
                expectCode(() -> transferService.ship(id), 41046);
                // 转出是「出」方向：没有余额行 = 无货可调，不建行
                assertThat(balanceRowCount(wh1, skuId)).isZero();
            }

            // ------------------------------------------------------------------
            // 两仓单位
            // ------------------------------------------------------------------

            @Test
            @DisplayName("两仓记账单位不一致 → 收货被拒（41044），不做隐式换算；单据停在在途")
            void unitMismatchBetweenWarehousesIsRejectedAtReceive() {
                Long sku = stockedInSeed("tf6", "10.0000");
                Long wh1 = seedWarehouseId();
                Long wh2 = newWarehouse("TF6B");

                // 目标仓已有该 SKU，但记账单位是「箱」（源仓是 kg）
                jdbc.update("INSERT INTO xsy_inventory_balance "
                        + "(warehouse_id, sku_id, unit, quantity, reserved_quantity, version, deleted) "
                        + "VALUES (?, ?, '箱', 5, 0, 0, FALSE)", wh2, sku);
                evictMybatisCache();

                Long id = transferService.create(form(wh1, wh2, sku, "3.0000"));
                transferService.ship(id);

                expectCode(() -> transferService.receive(id), 41044);

                // 源仓已扣（发出确实发生了），目标仓不变，单据停在在途 —— 需要人工处理：
                // 统一两仓采购单位后重试收货，或反向调拨回源仓。
                assertThat(balanceRow(wh2, sku).getQuantity()).isEqualByComparingTo("5.0000");
                assertThat(balanceRow(wh1, sku).getQuantity()).isEqualByComparingTo("7.0000");
                assertThat(statusOf(id)).isEqualTo("SHIPPED");
            }

            // ------------------------------------------------------------------
            // 入口校验与状态机
            // ------------------------------------------------------------------

            @Test
            @DisplayName("源仓与目标仓相同被拒（41042）—— 那不是调拨，是把货来回加减")
            void sameWarehouseIsRejected() {
                Long sku = stockedInSeed("tf7", "10.0000");
                Long wh1 = seedWarehouseId();
                expectCode(() -> transferService.create(form(wh1, wh1, sku, "1.0000")), 41042);
                assertThat(balanceRow(wh1, sku).getQuantity()).isEqualByComparingTo("10.0000");
            }

            @Test
            @DisplayName("同一 SKU 在调拨单里出现两次被拒（41045）")
            void duplicateSkuIsRejected() {
                Long sku = stockedInSeed("tf8", "10.0000");
                Long wh1 = seedWarehouseId();
                Long wh2 = newWarehouse("TF8B");

                InventoryTransferAddForm form = form(wh1, wh2, sku, "1.0000");
                form.getItems().add(item(sku, "2.0000"));
                expectCode(() -> transferService.create(form), 41045);
            }

            @Test
            @DisplayName("状态机：待发货不可收货；在途不可取消/改/删/再发出；已收货是终态")
            void transferStatusMachineIsTwoStepAndTerminal() {
                Long sku = stockedInSeed("tf9", "10.0000");
                Long wh1 = seedWarehouseId();
                Long wh2 = newWarehouse("TF9B");
                Long id = transferService.create(form(wh1, wh2, sku, "3.0000"));

                // 待发货不可收货
                expectCode(() -> transferService.receive(id), 41039);

                transferService.ship(id);
                // 在途：只能收货。不可取消（货已物理离开源仓）、不可改、不可删、不可再发出
                expectCode(() -> transferService.cancel(id), 41039);
                expectCode(() -> transferService.update(id, form(wh1, wh2, sku, "9.0000")), 41039);
                expectCode(() -> transferService.delete(id), 41039);
                expectCode(() -> transferService.ship(id), 41039);

                transferService.receive(id);
                // 终态
                expectCode(() -> transferService.receive(id), 41039);
                expectCode(() -> transferService.cancel(id), 41039);
                expectCode(() -> transferService.delete(id), 41039);

                assertThat(balanceRow(wh2, sku).getQuantity()).isEqualByComparingTo("3.0000");
                assertThat(balanceRow(wh1, sku).getQuantity()).isEqualByComparingTo("7.0000");
            }

            @Test
            @DisplayName("待发货单取消不产生任何库存影响")
            void cancelledDraftHasNoStockEffect() {
                Long sku = stockedInSeed("tf10", "10.0000");
                Long wh1 = seedWarehouseId();
                Long wh2 = newWarehouse("TF10B");

                Long id = transferService.create(form(wh1, wh2, sku, "3.0000"));
                transferService.cancel(id);

                assertThat(statusOf(id)).isEqualTo("CANCELLED");
                expectCode(() -> transferService.ship(id), 41039);
                assertThat(balanceRow(wh1, sku).getQuantity()).isEqualByComparingTo("10.0000");
                assertThat(balanceRow(wh2, sku)).isNull();
            }

            // ------------------------------------------------------------------
            // 仓库停用守卫
            // ------------------------------------------------------------------

            @Test
            @DisplayName("在途调拨阻塞仓库停用（41009）—— 目标仓侧；待发货不阻塞")
            void inTransitTransferBlocksDestinationWarehouseDisable() {
                Long sku = stockedInSeed("tf11", "10.0000");
                Long wh1 = seedWarehouseId();
                Long wh2 = newWarehouse("TF11B");

                // 目标仓此时既无余额、也无采购单与收货单 → 无阻塞
                assertThat(warehouseDisableGuard.disableBlocker(wh2)).isNull();

                Long id = transferService.create(form(wh1, wh2, sku, "3.0000"));
                // 待发货还没动过任何库存，不阻塞
                assertThat(warehouseDisableGuard.disableBlocker(wh2)).isNull();

                transferService.ship(id);
                // 在途：目标仓还欠着一批要入库的货 → 阻塞
                assertThat(warehouseDisableGuard.disableBlocker(wh2))
                        .isEqualTo(WarehouseErrorCode.WAREHOUSE_DISABLE_HAS_IN_TRANSIT_TRANSFER);
            }

            @Test
            @DisplayName("在途调拨阻塞仓库停用（41009）—— 源仓侧：货已出去但账上还没落地")
            void inTransitTransferBlocksSourceWarehouseDisable() {
                Long sku = stockedInSeed("tf12", "10.0000");
                Long wh1 = seedWarehouseId();
                Long wh2 = newWarehouse("TF12B");

                // 把全部货发出去：源仓余额清零，因此「正库存」这条阻塞条件不再成立
                Long id = transferService.create(form(wh1, wh2, sku, "10.0000"));
                transferService.ship(id);
                assertThat(balanceRow(wh1, sku).getQuantity()).isEqualByComparingTo("0.0000");

                // 还要排掉另外两条，否则先触发的是它们、断言会假红。它们都不是本用例要验证的东西：
                //   * 正库存：本仓还有其它 SKU 的余额 —— 来源是前面 NOT_SUPPORTED 的用例
                //     （它们会把数据提交进库），属测试间的顺序耦合，不是产品行为；
                //   * 在途采购单 / 待入库收货单：夹具自己建的采购单与收货单留下的。
                // 三条语句都在测试事务内，用例结束即回滚，不影响真实数据。
                //
                // 注意必须同时清零 reserved_quantity：ck_inventory_balance_available 要求
                // reserved <= quantity，那些提交进库的行上带着预留量，只清 quantity 会直接违反约束。
                jdbc.update("UPDATE xsy_inventory_balance SET quantity = 0, reserved_quantity = 0 "
                        + "WHERE warehouse_id = ? AND deleted = FALSE", wh1);
                jdbc.update("UPDATE xsy_purchase_order SET status = 'RECEIVED' "
                        + "WHERE warehouse_id = ? AND status IN ('SUBMITTED', 'PARTIALLY_RECEIVED')", wh1);
                jdbc.update("UPDATE xsy_purchase_receipt SET putaway_status = 'COMPLETED' "
                        + "WHERE warehouse_id = ? AND status = 'CONFIRMED' AND putaway_status = 'PENDING'", wh1);
                evictMybatisCache();

                // 另外三条都已排除，此时若仍返回 41009，它就只可能来自新增的第四条
                // —— 断言本身即是证明：若别的条件先命中，这里会拿到 41005 / 41006 / 41007。
                assertThat(warehouseDisableGuard.disableBlocker(wh1))
                        .isEqualTo(WarehouseErrorCode.WAREHOUSE_DISABLE_HAS_IN_TRANSIT_TRANSFER);
            }

            // ------------------------------------------------------------------
            // 在途库存报表（「在途可见」的落地方式：报表增列，不进余额表）
            // ------------------------------------------------------------------

            @Test
            @DisplayName("在途库存报表：只列已发出的调拨量，收货后消失，且不进余额表")
            void inTransitReportListsOnlyShippedTransfers() {
                Long sku = stockedInSeed("tf15", "10.0000");
                Long wh1 = seedWarehouseId();
                Long wh2 = newWarehouse("TF15B");

                Long id = transferService.create(form(wh1, wh2, sku, "4.0000"));

                // 草稿：还没动过库存，不在在途报表里
                assertThat(transferQueryService.queryInTransit())
                        .as("草稿调拨不在在途报表里")
                        .noneMatch(row -> sku.equals(row.getSkuId()));

                transferService.ship(id);

                // 在途：出现，数量是发出量，方向与单位都可读
                var row = transferQueryService.queryInTransit().stream()
                        .filter(r -> sku.equals(r.getSkuId()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("已发出的调拨应出现在在途报表里"));
                assertThat(row.getTransferNo()).isNotBlank();
                assertThat(row.getFromWarehouseId()).isEqualTo(wh1);
                assertThat(row.getToWarehouseId()).isEqualTo(wh2);
                assertThat(row.getQuantity()).isEqualByComparingTo("4.0000");
                assertThat(row.getUnit()).isEqualTo(balanceRow(wh1, sku).getUnit());

                // 核心断言：在途量不在余额表里 —— 目标仓此时还没有余额行。
                // 这正是「不引入虚拟在途仓」的代价，也是这份报表存在的理由：
                // 对账时必须把它算进去，否则「全仓总库存」在在途期间会对不上。
                assertThat(balanceRow(wh2, sku)).as("在途期间目标仓没有余额行").isNull();

                transferService.receive(id);

                // 收货后从在途报表消失，同时目标仓出现余额
                assertThat(transferQueryService.queryInTransit())
                        .as("收货后不再是在途")
                        .noneMatch(r -> sku.equals(r.getSkuId()));
                assertThat(balanceRow(wh2, sku).getQuantity()).isEqualByComparingTo("4.0000");
            }

            // ------------------------------------------------------------------
            // 命令侧约定
            // ------------------------------------------------------------------

            @Test
            @DisplayName("同一来源行重复转出被拒（41047），不会扣两次")
            void duplicateTransferOutFromSameSourceLineIsRejected() {
                Long sku = stockedInSeed("tf13", "10.0000");
                Long wh1 = seedWarehouseId();

                InventoryTransferFact fact = new InventoryTransferFact(
                        wh1, sku, 0L, 940001L, new BigDecimal("2.0000"), null,
                        OffsetDateTime.now(), "test:1");
                assertThat(inventoryCommandService.postTransferOut(fact)).isNotBlank();

                expectCode(() -> inventoryCommandService.postTransferOut(fact), 41047);
                assertThat(balanceRow(wh1, sku).getQuantity()).isEqualByComparingTo("8.0000");
            }

            @Test
            @DisplayName("Q7：调拨流水同样不可改删（新增类型不是绕过 append-only 的口子）")
            void transferMovementsAreStillAppendOnly() {
                Long sku = stockedInSeed("tf14", "10.0000");
                Long wh1 = seedWarehouseId();
                Long wh2 = newWarehouse("TF14B");

                Long id = transferService.create(form(wh1, wh2, sku, "3.0000"));
                transferService.ship(id);
                transferService.receive(id);

                for (Long movementId : List.of(
                        ((Number) movementOf(wh1, sku, "TRANSFER_OUT").get("id")).longValue(),
                        ((Number) movementOf(wh2, sku, "TRANSFER_IN").get("id")).longValue())) {
                    assertMovementImmutable(movementId);
                }
            }
        }

        @Nested
        @DisplayName("库存阈值预警（PG IT）")
        class ScmInventoryWarningIT {
            @Autowired
            private InventoryWarningThresholdService thresholdService;

            @Autowired
            private InventoryWarningQueryService warningQueryService;

            @Autowired
            private InventoryReservationService reservations;

            private Long stocked(String suffix, String quantity) {
                Long skuId = newSkuOfType(suffix, "NON_STANDARD", "ON_SHELF");
                W6Fixture fixture = inboundFixture(suffix, skuId, quantity);
                confirmReceipt(fixture.receipt().getId(), quantity);
                return skuId;
            }

            private static InventoryWarningThresholdAddForm threshold(Long warehouseId, Long skuId,
                                                                      String min, String max) {
                InventoryWarningThresholdAddForm form = new InventoryWarningThresholdAddForm();
                form.setWarehouseId(warehouseId);
                form.setSkuId(skuId);
                form.setWarnMin(min == null ? null : new BigDecimal(min));
                form.setWarnMax(max == null ? null : new BigDecimal(max));
                return form;
            }

            private InventoryWarningQueryForm warningQuery(Long warehouseId) {
                InventoryWarningQueryForm form = new InventoryWarningQueryForm();
                form.setPageNum(1L);
                form.setPageSize(100L);
                form.setWarehouseId(warehouseId);
                return form;
            }

            /**
             * 当前仓库的预警行（默认只看异常）。
             */
            private List<InventoryWarningVO> warnings(Long warehouseId) {
                return warningQueryService.queryWarningPage(warningQuery(warehouseId)).getList();
            }

            private InventoryWarningVO warningOf(Long warehouseId, Long skuId) {
                return warnings(warehouseId).stream()
                        .filter(row -> skuId.equals(row.getSkuId()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("预警列表里没有 SKU " + skuId));
            }

            // ------------------------------------------------------------------
            // 阈值配置
            // ------------------------------------------------------------------

            @Test
            @DisplayName("阈值配置 CRUD：新建 → 查询 → 编辑（含清空下限）→ 删除")
            void thresholdCrud() {
                Long wh = seedWarehouseId();
                Long sku = newSkuOfType("wn1", "NON_STANDARD", "ON_SHELF");

                Long id = thresholdService.create(threshold(wh, sku, "10.0000", "100.0000"));

                InventoryWarningThresholdQueryForm query = new InventoryWarningThresholdQueryForm();
                query.setPageNum(1L);
                query.setPageSize(10L);
                query.setWarehouseId(wh);
                assertThat(warningQueryService.queryThresholdPage(query).getList())
                        .anySatisfy(row -> {
                            assertThat(row.getId()).isEqualTo(id);
                            assertThat(row.getWarnMin()).isEqualByComparingTo("10.0000");
                            assertThat(row.getWarnMax()).isEqualByComparingTo("100.0000");
                            assertThat(row.getSkuId()).isEqualTo(sku);
                        });

                // 编辑：清空下限（只留上限）。实体上的 updateStrategy=ALWAYS 保证 null 会被真正写入 ——
                // 用默认策略会静默保留旧值，那是这一条最容易踩的坑。
                thresholdService.update(id, threshold(wh, sku, null, "50.0000"));
                assertThat(warningQueryService.detail(id).getWarnMin()).isNull();
                assertThat(warningQueryService.detail(id).getWarnMax()).isEqualByComparingTo("50.0000");

                thresholdService.delete(id);
                expectCode(() -> warningQueryService.detail(id), 41049);
            }

            @Test
            @DisplayName("同一 (仓库, SKU) 重复配置被拒（41050）—— 两条配置会让「按哪条判断」没有答案")
            void duplicateThresholdIsRejected() {
                Long wh = seedWarehouseId();
                Long sku = newSkuOfType("wn2", "NON_STANDARD", "ON_SHELF");

                thresholdService.create(threshold(wh, sku, "10.0000", null));
                expectCode(() -> thresholdService.create(threshold(wh, sku, "20.0000", null)), 41050);
            }

            @Test
            @DisplayName("阈值区间非法被拒（41051）：都没有 / 为负 / 下限大于上限")
            void invalidThresholdRangeIsRejected() {
                Long wh = seedWarehouseId();
                Long sku = newSkuOfType("wn3", "NON_STANDARD", "ON_SHELF");

                // 上下限都没有 → 没有任何判断依据
                expectCode(() -> thresholdService.create(threshold(wh, sku, null, null)), 41051);
                // 下限为负
                expectCode(() -> thresholdService.create(threshold(wh, sku, "-1", null)), 40000);
                // 上限为负
                expectCode(() -> thresholdService.create(threshold(wh, sku, null, "-1")), 40000);
                // 下限大于上限 → 所有状态都会异常，预警失去意义
                expectCode(() -> thresholdService.create(threshold(wh, sku, "10", "5")), 41051);
            }

            @Test
            @DisplayName("SKU 不存在被拒（41052）—— 配置表长期驻留，指向不存在 SKU 会成为永久噪声")
            void unknownSkuIsRejected() {
                Long wh = seedWarehouseId();
                expectCode(() -> thresholdService.create(
                        threshold(wh, 9_999_999_999L, "10.0000", null)), 41052);
            }

            // ------------------------------------------------------------------
            // 预警列表
            // ------------------------------------------------------------------

            @Test
            @DisplayName("预警列表由配置驱动：没有配置阈值的 (仓库, SKU) 不出现")
            void warningListIsDrivenByConfiguration() {
                Long wh = seedWarehouseId();
                Long sku = stocked("wn4", "10.0000");

                // 有库存但没有阈值配置 → 不出现在预警列表
                assertThat(warnings(wh)).noneMatch(row -> sku.equals(row.getSkuId()));

                thresholdService.create(threshold(wh, sku, "5.0000", null));
                // 配置后 10 >= 5 → 正常项，默认（只看异常）仍然不出现
                assertThat(warnings(wh)).noneMatch(row -> sku.equals(row.getSkuId()));
                // 显式看正常项才出现
                InventoryWarningQueryForm normal = warningQuery(wh);
                normal.setStatus("NORMAL");
                assertThat(warningQueryService.queryWarningPage(normal).getList())
                        .anyMatch(row -> sku.equals(row.getSkuId()));

                // 把下限抬到 20 → 10 < 20 → 触发 LOW
                InventoryWarningThresholdQueryForm q = new InventoryWarningThresholdQueryForm();
                q.setPageNum(1L);
                q.setPageSize(10L);
                q.setSkuId(sku);
                Long thresholdId = warningQueryService.queryThresholdPage(q).getList().getFirst().getId();
                thresholdService.update(thresholdId, threshold(wh, sku, "20.0000", null));

                InventoryWarningVO row = warningOf(wh, sku);
                assertThat(row.getStatus()).isEqualTo("LOW");
                assertThat(row.getQuantity()).isEqualByComparingTo("10.0000");
                assertThat(row.getAvailableQuantity()).isEqualByComparingTo("10.0000");
                assertThat(row.getWarnMin()).isEqualByComparingTo("20.0000");

                // 删除配置后不再预警
                thresholdService.delete(thresholdId);
                assertThat(warnings(wh)).noneMatch(r -> sku.equals(r.getSkuId()));
            }

            @Test
            @DisplayName("HIGH：可用量高于上限触发积压预警")
            void highWarningIsTriggeredByAvailableAboveMax() {
                Long wh = seedWarehouseId();
                Long sku = stocked("wn5", "100.0000");
                thresholdService.create(threshold(wh, sku, null, "50.0000"));

                InventoryWarningVO row = warningOf(wh, sku);
                assertThat(row.getStatus()).isEqualTo("HIGH");
                assertThat(row.getStatusDesc()).isEqualTo("高于上限");
            }

            @Test
            @DisplayName("判定基准是**可用量**：现有量够但已被预留时仍然预警")
            void warningBasisIsAvailableQuantityNotOnHand() {
                Long wh = seedWarehouseId();
                Long sku = stocked("wn6", "20.0000");

                // 下限 10：现有量 20 明显够
                thresholdService.create(threshold(wh, sku, "10.0000", null));
                assertThat(warnings(wh)).as("现有量 20 >= 下限 10 → 不预警")
                        .noneMatch(row -> sku.equals(row.getSkuId()));

                // 但把 18 预留出去之后，可用量只剩 2 → 必须预警。
                // 这正是「下限的业务含义是还够不够发货」：货已经被订走就不算有货。
                reservations.reserve(new ReserveInventoryFact(
                        wh, sku, "SALES_ORDER_ITEM", 760001L, 860001L,
                        new BigDecimal("18.0000"), OffsetDateTime.now(), null));

                InventoryWarningVO row = warningOf(wh, sku);
                assertThat(row.getStatus()).as("可用量 2 < 下限 10 → LOW").isEqualTo("LOW");
                assertThat(row.getQuantity()).as("现有量仍然够").isEqualByComparingTo("20.0000");
                assertThat(row.getReservedQuantity()).isEqualByComparingTo("18.0000");
                assertThat(row.getAvailableQuantity()).isEqualByComparingTo("2.0000");
            }

            @Test
            @DisplayName("边界取等号算正常；没有余额行时数量按 0 计并触发下限预警")
            void boundaryAndMissingBalance() {
                Long wh = seedWarehouseId();
                Long stockedSku = stocked("wn7", "10.0000");
                Long emptySku = newSkuOfType("wn8", "NON_STANDARD", "ON_SHELF");

                // 恰好等于下限 → 正常（「不低于下限」）
                thresholdService.create(threshold(wh, stockedSku, "10.0000", null));
                assertThat(warnings(wh)).noneMatch(row -> stockedSku.equals(row.getSkuId()));

                // 没有余额行（从未入库）+ 设了下限 1 → 数量按 0 计 → LOW。
                // 这是本能力唯一能表达「还没进过货就要补货」的方式。
                assertThat(balanceRow(wh, emptySku)).isNull();
                thresholdService.create(threshold(wh, emptySku, "1.0000", null));

                InventoryWarningVO row = warningOf(wh, emptySku);
                assertThat(row.getStatus()).isEqualTo("LOW");
                assertThat(row.getQuantity()).isEqualByComparingTo("0.0000");
                assertThat(row.getAvailableQuantity()).isEqualByComparingTo("0.0000");
                assertThat(row.getUnit()).as("没有余额行时没有记账单位").isNull();
            }

            @Test
            @DisplayName("SQL 过滤与 Java 判定等价：按 status 查出来的行，服务层算出的状态必须一致")
            void sqlFilterAgreesWithJavaClassification() {
                Long wh = seedWarehouseId();
                Long lowSku = stocked("wn9", "1.0000");
                Long highSku = stocked("wn10", "500.0000");
                Long normalSku = stocked("wn11", "50.0000");

                thresholdService.create(threshold(wh, lowSku, "10.0000", null));   // 1 < 10 → LOW
                thresholdService.create(threshold(wh, highSku, null, "100.0000")); // 500 > 100 → HIGH
                thresholdService.create(threshold(wh, normalSku, "10.0000", "100.0000")); // 正常

                // 状态判定的规则在 Java 枚举里只实现一次；列表 SQL 里另有一份过滤谓词。
                // 逐个状态查一遍，断言「SQL 筛出来的行」与「Java 算出来的状态」完全一致 ——
                // 两份实现一旦漂移，这里会立刻失败。
                for (String status : List.of("LOW", "HIGH", "NORMAL")) {
                    InventoryWarningQueryForm form = warningQuery(wh);
                    form.setStatus(status);
                    List<InventoryWarningVO> rows = warningQueryService.queryWarningPage(form).getList();
                    assertThat(rows).as("status=%s 至少应有一行", status).isNotEmpty();
                    for (InventoryWarningVO row : rows) {
                        assertThat(row.getStatus()).as("status=%s 的行 %s", status, row.getSkuId())
                                .isEqualTo(status);
                        // 再用枚举独立算一次，确认服务层填的值本身就是对的
                        assertThat(ScmInventoryWarningStatusEnum.evaluate(
                                row.getAvailableQuantity(), row.getWarnMin(), row.getWarnMax()).name())
                                .isEqualTo(status);
                    }
                }

                // 为空 → 只看异常：正常项不得出现
                List<InventoryWarningVO> abnormal = warnings(wh);
                assertThat(abnormal).noneMatch(row -> "NORMAL".equals(row.getStatus()));
                assertThat(abnormal).anyMatch(row -> lowSku.equals(row.getSkuId()));
                assertThat(abnormal).anyMatch(row -> highSku.equals(row.getSkuId()));
                assertThat(abnormal).noneMatch(row -> normalSku.equals(row.getSkuId()));
            }
        }

        @Nested
        @DisplayName("库存写侧仓库数据范围（PG IT）")
        class ScmInventoryWriteScopePgIT {
            private static final String QUANTITY = "10.0000";
            private static final String PRICE = "6.2000";

            /** 一次库存动作的量：出库 / 调拨 / 转换转出都用它。 */
            private static final String MOVE = "4.0000";

            private static final BigDecimal STOCKED = new BigDecimal("10.0000");
            private static final BigDecimal MOVED = new BigDecimal("4.0000");
            private static final BigDecimal ZERO = new BigDecimal("0.0000");

            /** 出库单新建后的初始状态。 */
            private static final String OUTBOUND_INITIAL = "PENDING";
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
                assertThat(statusOf("xsy_inventory_outbound", mineId)).isEqualTo("CONFIRMED");
                assertThat(quantityOf(mine)).as("授权仓的出库照常扣账").isEqualByComparingTo("6.0000");
                assertThat(movementTypes(mine)).containsExactly("PURCHASE_IN", "SALES_OUT");

                // 反例：乙仓一张状态合法、版本正确的草稿，只因为仓库不在范围内被挡
                Long foreignId = asAdmin(() -> outboundService.create(outboundForm(foreign, MOVE)));
                assertDataScopeDenied(() -> asRun(keeper, () -> outboundService.confirm(foreignId)));
                assertThat(statusOf("xsy_inventory_outbound", foreignId)).as("确认没落进状态机").isEqualTo(OUTBOUND_INITIAL);
                assertThat(movementTypes(foreign)).as("一条 SALES_OUT 都没写下").containsExactly("PURCHASE_IN");
                assertThat(quantityOf(foreign)).as("余额一分未动").isEqualByComparingTo(STOCKED);
                assertDataScopeDenied(() -> asRun(keeper, () -> outboundService.cancel(foreignId)));
                assertDataScopeDenied(() -> asRun(keeper, () -> outboundService.delete(foreignId)));
                assertThat(activeRowCount("xsy_inventory_outbound", foreignId)).as("取消与删除也没碰到这张单").isEqualTo(1);

                // 未授权仓连草稿都建不出来：建单不是「读不到」，而是往别人仓里塞单据
                assertDataScopeDenied(() -> as(keeper, Set.of(), () -> outboundService.create(outboundForm(foreign, MOVE))));
                assertThat(rowCount("xsy_inventory_outbound", "warehouse_id = ?", foreign.warehouseId()))
                        .as("仍然只有夹具那一张").isEqualTo(1);

                // 改单会把仓库换掉：行上的旧仓与表单的新仓两端都要授权
                Long draftInMine = as(keeper, Set.of(), () -> outboundService.create(outboundForm(mine, MOVE)));
                assertDataScopeDenied(() -> asRun(keeper,
                        () -> outboundService.update(draftInMine, outboundForm(foreign, MOVE))));
                assertThat(warehouseOf("xsy_inventory_outbound", draftInMine)).isEqualTo(mine.warehouseId());

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
                assertThat(statusOf("xsy_inventory_transfer", transferId)).isEqualTo(PENDING);
                assertThat(movementTypes(source)).containsExactly("PURCHASE_IN");
                assertThat(quantityOf(source)).isEqualByComparingTo(STOCKED);

                asRun(sourceKeeper, () -> transferService.ship(transferId));
                assertThat(statusOf("xsy_inventory_transfer", transferId)).isEqualTo("SHIPPED");
                assertThat(movementTypes(source)).containsExactly("PURCHASE_IN", "TRANSFER_OUT");
                assertThat(quantityOf(source)).isEqualByComparingTo("6.0000");
                assertThat(movementCount(target.warehouseId(), source.skuId()))
                        .as("在途：货不属于任何仓的余额").isZero();

                // 收货要求 to 仓权限：发出去的一方不能替目标仓入账
                assertDataScopeDenied(() -> asRun(sourceKeeper, () -> transferService.receive(transferId)));
                assertThat(statusOf("xsy_inventory_transfer", transferId)).isEqualTo("SHIPPED");
                assertThat(movementCount(target.warehouseId(), source.skuId())).isZero();
                assertThat(quantityOf(target.warehouseId(), source.skuId()))
                        .as("目标仓的余额行还没被创建").isNull();

                asRun(targetKeeper, () -> transferService.receive(transferId));
                assertThat(statusOf("xsy_inventory_transfer", transferId)).isEqualTo("RECEIVED");
                assertThat(movementTypes(target.warehouseId(), source.skuId())).containsExactly("TRANSFER_IN");
                assertThat(quantityOf(target.warehouseId(), source.skuId)).isEqualByComparingTo(MOVED);
                assertLedgerBalanced(source.warehouseId(), source.skuId());
                assertLedgerBalanced(target.warehouseId(), source.skuId());

                // 已经走完的两端授权人再撤销：在途/终态不可撤销是状态机的事，与本类无关，但状态不能变
                assertThat(statusOf("xsy_inventory_transfer", transferId)).isEqualTo("RECEIVED");
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
                assertThat(rowCount("xsy_inventory_transfer",
                        "(from_warehouse_id IN (?, ?) AND to_warehouse_id IN (?, ?))",
                        mine.warehouseId(), foreign.warehouseId(), mine.warehouseId(), foreign.warehouseId()))
                        .as("两个方向都没落下任何一张草稿").isZero();

                // 两端都在范围内：建单与改单照常
                Long allowed = as(bothEnds, Set.of(), () -> transferService.create(
                        transferForm(mine.warehouseId(), alsoMine.warehouseId(), mine.skuId(), MOVE)));
                asRun(bothEnds, () -> transferService.update(allowed,
                        transferForm(mine.warehouseId(), alsoMine.warehouseId(), mine.skuId(), "2.0000")));
                assertThat(quantityOfFirstItem("xsy_inventory_transfer_item", "transfer_id", allowed))
                        .as("两端都授权时改单照常写入").isEqualByComparingTo("2.0000");

                // 只授权 from 端的人想把 to 换成未授权仓：判掉，且旧值与明细都不留痕
                assertDataScopeDenied(() -> asRun(onlyMine, () -> transferService.update(allowed,
                        transferForm(mine.warehouseId(), foreign.warehouseId(), mine.skuId(), MOVE))));
                assertThat(warehouseOf("xsy_inventory_transfer", "to_warehouse_id", allowed)).isEqualTo(alsoMine.warehouseId());
                assertThat(quantityOfFirstItem("xsy_inventory_transfer_item", "transfer_id", allowed))
                        .as("失败的改单没换掉明细数量").isEqualByComparingTo("2.0000");

                // 撤销草稿不碰余额，判据与查询同为 OR：两端都不授权的人撤不掉
                assertDataScopeDenied(() -> asRun(stranger, () -> transferService.cancel(allowed)));
                assertDataScopeDenied(() -> asRun(stranger, () -> transferService.delete(allowed)));
                assertThat(statusOf("xsy_inventory_transfer", allowed)).isEqualTo(PENDING);
                assertThat(activeRowCount("xsy_inventory_transfer", allowed)).isEqualTo(1);
                // 命中任一端（这里是 from 端）就可以撤自己的草稿
                asRun(onlyMine, () -> transferService.cancel(allowed));
                assertThat(statusOf("xsy_inventory_transfer", allowed)).isEqualTo("CANCELLED");
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
                        foreignDoc, lossGainAuditForm(versionOf("xsy_inventory_loss_gain", foreignDoc)))));
                assertDataScopeDenied(() -> asRun(keeper, () -> lossGainService.reject(
                        foreignDoc, lossGainAuditForm(versionOf("xsy_inventory_loss_gain", foreignDoc)))));
                assertThat(statusOf("xsy_inventory_loss_gain", foreignDoc)).as("既没通过也没驳回").isEqualTo(PENDING);
                assertThat(movementTypes(foreign)).containsExactly("PURCHASE_IN");
                assertThat(quantityOf(foreign)).isEqualByComparingTo(STOCKED);
                assertDataScopeDenied(() -> asRun(keeper, () -> lossGainService.delete(foreignDoc)));
                assertThat(activeRowCount("xsy_inventory_loss_gain", foreignDoc)).isEqualTo(1);

                // 未授权仓连录单都不行
                assertDataScopeDenied(() -> as(keeper, Set.of(), () -> lossGainService.create(lossGainForm(foreign, "1.0000"))));
                assertThat(rowCount("xsy_inventory_loss_gain", "warehouse_id = ?", foreign.warehouseId()))
                        .as("只有夹具那一张").isEqualTo(1);

                // 自己在授权仓录的单：范围放行，41065 仍按身份拦下 —— 两条禁令各管各的
                Long ownDoc = as(keeper, Set.of(), () -> lossGainService.create(lossGainForm(mine, "1.0000")));
                expectCode(() -> asRun(keeper, () -> lossGainService.approve(
                        ownDoc, lossGainAuditForm(versionOf("xsy_inventory_loss_gain", ownDoc)))), 41065);
                assertThat(statusOf("xsy_inventory_loss_gain", ownDoc)).isEqualTo(PENDING);
                assertThat(movementTypes(mine)).as("自审被拦下时同样不该留下流水").containsExactly("PURCHASE_IN");

                // 换一位同仓仓管审批：正常落账，说明范围收口没有把有效单据卡死
                Long mate = keeper("lg4mate", mine.warehouseId());
                asRun(mate, () -> lossGainService.approve(ownDoc,
                        lossGainAuditForm(versionOf("xsy_inventory_loss_gain", ownDoc))));
                assertThat(statusOf("xsy_inventory_loss_gain", ownDoc)).isEqualTo("COMPLETED");
                assertThat(movementTypes(mine)).containsExactly("PURCHASE_IN", "LOSS_REPORT");
                assertThat(quantityOf(mine)).isEqualByComparingTo("9.0000");
                assertThat(jdbc.queryForObject("SELECT auditor FROM xsy_inventory_loss_gain WHERE id = ?", String.class, ownDoc))
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
                assertThat(rowCount("xsy_inventory_stocktake", "warehouse_id = ?", foreign.warehouseId())).isZero();
                // 导入：仓库取自签名凭证，但凭证不是授权依据，判定落在锁定余额之前
                assertDataScopeDenied(() -> as(keeper, Set.of(), () -> stocktakeService.createFromSnapshot(
                        foreign.warehouseId(), List.of())));

                Long foreignDoc = asAdmin(() -> stocktakeService.create(stocktakeForm(foreign, "12.0000")));
                assertDataScopeDenied(() -> asRun(keeper, () -> stocktakeService.confirm(foreignDoc)));
                assertDataScopeDenied(() -> asRun(keeper, () -> stocktakeService.cancel(foreignDoc)));
                assertDataScopeDenied(() -> asRun(keeper, () -> stocktakeService.delete(foreignDoc)));
                assertDataScopeDenied(() -> asRun(keeper,
                        () -> stocktakeService.update(foreignDoc, stocktakeForm(foreign, "8.0000"))));
                assertThat(statusOf("xsy_inventory_stocktake", foreignDoc)).isEqualTo(PENDING);
                assertThat(movementTypes(foreign)).as("盘盈/盘亏都没写进乙仓").containsExactly("PURCHASE_IN");
                assertThat(quantityOf(foreign)).isEqualByComparingTo(STOCKED);
                assertThat(bookQuantityOfFirstItem(foreignDoc)).as("失败的改单没换掉账面量保存的数据")
                        .isEqualByComparingTo(STOCKED);

                Long ownDoc = as(keeper, Set.of(), () -> stocktakeService.create(stocktakeForm(mine, "12.0000")));
                assertThat(bookQuantityOfFirstItem(ownDoc)).as("授权仓的已存数据与改单照常").isEqualByComparingTo(STOCKED);
                asRun(keeper, () -> stocktakeService.confirm(ownDoc));
                assertThat(statusOf("xsy_inventory_stocktake", ownDoc)).isEqualTo("CONFIRMED");
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
                assertThat(rowCount("xsy_inventory_conversion", "warehouse_id = ?", foreign.warehouseId())).isZero();

                Long foreignDoc = asAdmin(() -> conversionService.create(
                        conversionForm(foreign.warehouseId(), foreign.skuId(), extraSku)));
                assertDataScopeDenied(() -> asRun(keeper, () -> conversionService.approve(
                        foreignDoc, conversionAuditForm(versionOf("xsy_inventory_conversion", foreignDoc)))));
                assertDataScopeDenied(() -> asRun(keeper, () -> conversionService.reject(
                        foreignDoc, conversionAuditForm(versionOf("xsy_inventory_conversion", foreignDoc)))));
                assertDataScopeDenied(() -> asRun(keeper, () -> conversionService.delete(foreignDoc)));
                assertThat(statusOf("xsy_inventory_conversion", foreignDoc)).isEqualTo(PENDING);
                assertThat(movementTypes(foreign)).as("转出腿没写").containsExactly("PURCHASE_IN");
                assertThat(movementCount(foreign.warehouseId(), extraSku)).as("转入腿也没写").isZero();
                assertThat(quantityOf(foreign)).isEqualByComparingTo(STOCKED);
                assertThat(movementTypes(mine)).as("别人的转换一条流水都没沾到自己仓").containsExactly("PURCHASE_IN");

                Long ownDoc = asAdmin(() -> conversionService.create(
                        conversionForm(mine.warehouseId(), mine.skuId(), extraSku)));
                asRun(keeper, () -> conversionService.approve(ownDoc,
                        conversionAuditForm(versionOf("xsy_inventory_conversion", ownDoc))));
                assertThat(statusOf("xsy_inventory_conversion", ownDoc)).isEqualTo("COMPLETED");
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
                assertThat(statusOf("xsy_inventory_reservation", foreignReservation)).isEqualTo("ACTIVE");

                asRun(keeper, () -> reservationService.release(mineReservation));
                assertThat(reservedQuantityOf(mine)).isEqualByComparingTo(ZERO);
                assertThat(statusOf("xsy_inventory_reservation", mineReservation)).isEqualTo("RELEASED");

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
                assertThat(rowCount("xsy_inventory_warning_threshold", "warehouse_id = ?", foreign.warehouseId())).isZero();

                Long foreignRow = asAdmin(() -> thresholdService.create(
                        thresholdForm(foreign.warehouseId(), foreign.skuId(), "1")));
                assertDataScopeDenied(() -> asRun(keeper, () -> thresholdService.update(foreignRow,
                        thresholdForm(foreign.warehouseId(), foreign.skuId(), "5"))));
                assertDataScopeDenied(() -> asRun(keeper, () -> thresholdService.delete(foreignRow)));
                assertThat(warnMinOf(foreignRow)).as("阈值数值没被改动").isEqualByComparingTo("1");
                assertThat(activeRowCount("xsy_inventory_warning_threshold", foreignRow)).isEqualTo(1);

                Long ownRow = as(keeper, Set.of(), () -> thresholdService.create(
                        thresholdForm(mine.warehouseId(), mine.skuId(), "1")));
                // 把自己仓的配置搬进别人仓：新仓也要判
                assertDataScopeDenied(() -> asRun(keeper, () -> thresholdService.update(ownRow,
                        thresholdForm(foreign.warehouseId(), mine.skuId(), "5"))));
                assertThat(warehouseOf("xsy_inventory_warning_threshold", ownRow)).isEqualTo(mine.warehouseId());

                asRun(keeper, () -> thresholdService.update(ownRow,
                        thresholdForm(mine.warehouseId(), mine.skuId(), "5")));
                assertThat(warnMinOf(ownRow)).isEqualByComparingTo("5");
                asRun(keeper, () -> thresholdService.delete(ownRow));
                assertThat(activeRowCount("xsy_inventory_warning_threshold", ownRow)).isZero();
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
                assertThat(jdbc.queryForObject("SELECT putaway_status FROM xsy_purchase_receipt WHERE id = ?",
                        String.class, pending.receiptId())).as("仍等着仓库二次确认").isEqualTo(PENDING);
                assertThat(jdbc.queryForObject("SELECT putaway_at IS NULL FROM xsy_purchase_receipt WHERE id = ?",
                        Boolean.class, pending.receiptId())).isTrue();
                assertThat(rowCount("xsy_inventory_movement", "warehouse_id = ?", pending.warehouseId()))
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
                    conversionService.approve(created, conversionAuditForm(versionOf("xsy_inventory_conversion", created)));
                    return created;
                });
                Long threshold = asAdmin(() -> {
                    Long id = thresholdService.create(thresholdForm(source.warehouseId(), source.skuId(), "2"));
                    thresholdService.delete(id);
                    return id;
                });

                assertThat(statusOf("xsy_inventory_conversion", conversion)).isEqualTo("COMPLETED");
                assertThat(activeRowCount("xsy_inventory_warning_threshold", threshold))
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
                jdbc.update("INSERT INTO xsy_employee_warehouse_scope (employee_id, warehouse_id) VALUES (?, ?)",
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
                form.setVersion(versionOf("xsy_purchase_receipt", receiptId));
                return form;
            }

            /** 防重复请求编号逐次唯一：本用例要证的是「这一次调用被拒」，不是命中上一次的重复请求返回原结果。 */
            private String putawayKey() {
                return prefix + ":wscope:putaway:" + UUID.randomUUID();
            }

            // ------------------------------------------------------------------
            // 库内记录读取（一律走 jdbc：拒绝之后还要读，不能让一级缓存给出旧值）
            // ------------------------------------------------------------------

            private BigDecimal quantityOf(StockedLike stocked) {
                return quantityOf(stocked.warehouseId(), stocked.skuId());
            }

            private BigDecimal quantityOf(Long warehouseId, Long skuId) {
                List<BigDecimal> rows = jdbc.queryForList("SELECT quantity FROM xsy_inventory_balance"
                                + " WHERE deleted = FALSE AND warehouse_id = ? AND sku_id = ?",
                        BigDecimal.class, warehouseId, skuId);
                return rows.isEmpty() ? null : rows.getFirst();
            }

            private BigDecimal reservedQuantityOf(StockedLike stocked) {
                return jdbc.queryForObject("SELECT reserved_quantity FROM xsy_inventory_balance"
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
                return jdbc.queryForObject("SELECT warn_min FROM xsy_inventory_warning_threshold WHERE id = ?",
                        BigDecimal.class, id);
            }

            /** 单据首行的数量：证「失败的改单连明细都没换」。 */
            private BigDecimal quantityOfFirstItem(String itemTable, String documentColumn, Long documentId) {
                return jdbc.queryForObject("SELECT quantity FROM " + itemTable + " WHERE deleted = FALSE AND "
                        + documentColumn + " = ? ORDER BY id LIMIT 1", BigDecimal.class, documentId);
            }

            /** 盘点单首行的账面量保存的数据：改草稿会重新保存的数据，失败的改单不该换掉它。 */
            private BigDecimal bookQuantityOfFirstItem(Long stocktakeId) {
                return jdbc.queryForObject("SELECT book_quantity FROM xsy_inventory_stocktake_item"
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
                return jdbc.queryForObject("SELECT count(*) FROM xsy_employee_warehouse_scope"
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
                XsyRequestUtil.setRequestUser(employee);
            }

            private void loginAs(Long employeeId) {
                RequestEmployee employee = new RequestEmployee();
                employee.setEmployeeId(employeeId);
                employee.setActualName("库存写侧范围 IT");
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                employee.setAdministratorFlag(false);
                employee.setDepartmentId(1L);
                XsyRequestUtil.setRequestUser(employee);
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
    }

    @Nested
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("W6 并发入库（PG IT，无外层事务）")
    class ScmInventoryConcurrencyIT extends ScmW6PgITBase {
        /**
         * 并发等待上限：锁等待远小于它，超过即为死锁或活锁（比默默挂死好）。
         */
        private static final long TIMEOUT_SECONDS = 60;

        @Override
        protected void evictMybatisCache() {
            // 无外层事务 → 每次 DAO 调用都是新 session → 一级缓存天然为空
        }

        /**
         * 子线程没有请求上下文，必须自己塞一个身份（{@code ScmOperator.current()} 依赖它）。
         *
         * <p>{@code administratorFlag=true} 与本类主线程身份一致：本用例测的是<b>锁序与并发账</b>，
         * 收货确认 / 出库确认带数据范围守卫，若这里给一个非超管且没有授权仓行的身份，
         * 两个线程会双双被 {@code ScmDataScopeException} 挡在业务逻辑之前 —— 测到的只是「都被拒」，
         * 而不是「只有一个抢到余额行」。范围本身由 {@code ScmInventoryWriteScopePgIT} 等专用例取证。
         */
        private static void setThreadOperator() {
            RequestEmployee employee = new RequestEmployee();
            employee.setEmployeeId(1L);
            employee.setActualName("W6 concurrent IT");
            employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            employee.setAdministratorFlag(true);
            XsyRequestUtil.setRequestUser(employee);
        }

        @FunctionalInterface
        private interface ThrowingRunnable {
            void run();
        }

        private record Outcome(boolean success, Throwable error) {
            static Outcome ok() {
                return new Outcome(true, null);
            }

            static Outcome failed(Throwable error) {
                return new Outcome(false, error);
            }
        }

        /**
         * 让两段动作尽可能同时开始，各自在独立事务里执行。
         *
         * <p>用 {@code CountDownLatch} 对齐起跑线而不是「先提交第一个再提交第二个」：
         * 后者只能测到串行路径，测不到竞态。
         */
        private List<Outcome> runConcurrently(ThrowingRunnable first, ThrowingRunnable second)
                throws Exception {
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                List<Future<Object>> futures = List.of(
                        pool.submit(() -> {
                            setThreadOperator();
                            start.await();
                            try {
                                first.run();
                            } finally {
                                XsyRequestUtil.remove();
                            }
                            return null;
                        }),
                        pool.submit(() -> {
                            setThreadOperator();
                            start.await();
                            try {
                                second.run();
                            } finally {
                                XsyRequestUtil.remove();
                            }
                            return null;
                        }));
                start.countDown();

                List<Outcome> outcomes = new ArrayList<>(2);
                for (Future<Object> future : futures) {
                    try {
                        future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                        outcomes.add(Outcome.ok());
                    } catch (ExecutionException e) {
                        outcomes.add(Outcome.failed(e.getCause()));
                    }
                }
                return outcomes;
            } finally {
                pool.shutdownNow();
            }
        }

        /**
         * 按 id（= 插入顺序 = 余额行锁的获取顺序）读流水。
         *
         * <p><b>为什么不按 {@code occurred_at}</b>：{@code receipt.confirmed_at} 是在进入
         * 余额加锁之前取的，因此并发下「确认时刻的先后」与
         * 「余额锁的先后」可以不一致。账本的 before/after 链是按锁顺序串起来的，
         * 所以并发用例必须按 id 排序才能看到正确的链；按 occurred_at 排序会看到
         * 一个顺序与数值不对应的序列 —— 那不是缺陷，是「并发事务的时间戳先于加锁」的必然结果。
         */
        private List<Map<String, Object>> movementsInLockOrder(Long warehouseId, Long skuId) {
            return jdbc.queryForList(
                    "SELECT * FROM xsy_inventory_movement "
                            + "WHERE warehouse_id = ? AND sku_id = ? AND deleted = FALSE ORDER BY id",
                    warehouseId, skuId);
        }

        // ------------------------------------------------------------------
        // #5
        // ------------------------------------------------------------------

        @Test
        @DisplayName("#5 并发首建余额：2 线程同 (wh,sku) 同时入库 → 1 行余额、两次增量都在、无异常逃逸")
        void concurrentFirstInboundCreatesExactlyOneBalanceRow() throws Exception {
            Long warehouseId = seedWarehouseId();
            Long skuId = newOnShelfSku("CC5");
            // 两个互不相干的采购单：采购侧零争用，两个线程才能同时到达「首建余额」
            W6Fixture first = inboundFixture("CC5a", skuId, "6.0000");
            W6Fixture second = inboundFixture("CC5b", skuId, "4.0000");
            assertThat(balanceRow(warehouseId, skuId)).as("前置：余额还不存在").isNull();

            List<Outcome> outcomes = runConcurrently(
                    () -> confirmReceipt(first.receipt().getId(), "6.0000", prefix + ":CC5:a"),
                    () -> confirmReceipt(second.receipt().getId(), "4.0000", prefix + ":CC5:b"));

            // 无异常逃逸：ON CONFLICT 的赢家与输家都必须平安落地
            assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.success())
                    .as("并发入库不应有异常逃逸：%s", outcome.error())
                    .isTrue());

            // 物理行数（含 soft-deleted）：排除「插了两行再删一行」这种伪唯一
            assertThat(balanceRowCount(warehouseId, skuId)).isEqualTo(1);
            assertThat(balanceRow(warehouseId, skuId).getUnit()).isEqualTo(DEFAULT_PURCHASE_UNIT);
            assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("10.0000");

            // 两次增量都在（没有丢失更新），且每行都满足 after = before + quantity
            List<Map<String, Object>> movements = movementsInLockOrder(warehouseId, skuId);
            assertThat(movements).hasSize(2);
            movements.forEach(row -> assertThat((BigDecimal) row.get("after_quantity"))
                    .isEqualByComparingTo(((BigDecimal) row.get("before_quantity"))
                            .add((BigDecimal) row.get("quantity"))));
            // 锁顺序下的链：0 → x → 10
            assertThat((BigDecimal) movements.get(0).get("before_quantity")).isEqualByComparingTo("0.0000");
            assertThat((BigDecimal) movements.get(1).get("after_quantity")).isEqualByComparingTo("10.0000");
            assertThat((BigDecimal) movements.get(1).get("before_quantity"))
                    .isEqualByComparingTo((BigDecimal) movements.get(0).get("after_quantity"));
            // 两笔数量各就各位（谁先拿到锁不影响总量）
            assertThat(movements)
                    .extracting(row -> ((BigDecimal) row.get("quantity")).toPlainString())
                    .containsExactlyInAnyOrder("6.0000", "4.0000");

            // 两张收货单都确认成功
            assertThat(reloadReceipt(first.receipt().getId()).getStatus()).isEqualTo("CONFIRMED");
            assertThat(reloadReceipt(second.receipt().getId()).getStatus()).isEqualTo("CONFIRMED");
            assertThat(movementsOfReceiptItem(first.receiptItemId())).isEqualTo(1);
            assertThat(movementsOfReceiptItem(second.receiptItemId())).isEqualTo(1);
        }

        // ------------------------------------------------------------------
        // #6
        // ------------------------------------------------------------------

        @Test
        @DisplayName("#6 并发 confirm 同一采购单的两张收货单：串行化、累计与库存一致、无死锁")
        void concurrentConfirmsOnSameOrderSerializeWithoutDeadlock() throws Exception {
            Long warehouseId = seedWarehouseId();
            Long skuId = newOnShelfSku("CC6");
            W6Fixture fixture = inboundFixture("CC6", skuId, "10.0000");
            // 分次到货：同一采购单的第二张收货单
            PurchaseReceiptVO second = createAnotherReceipt(fixture.order().getId(), "second");

            List<Outcome> outcomes = runConcurrently(
                    () -> confirmReceipt(fixture.receipt().getId(), "5.0000", prefix + ":CC6:a"),
                    () -> confirmReceipt(second.getId(), "5.0000", prefix + ":CC6:b"));

            assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.success())
                    .as("同单并发收货应当串行化成功，而不是死锁/版本冲突：%s", outcome.error())
                    .isTrue());

            // 采购侧：累计 10（两个事务都先拿采购单行锁 → accumulateReceived 不丢更新）
            assertThat(receivedQuantityOf(fixture.orderItemId())).isEqualByComparingTo("10.0000");
            assertThat(reloadOrder(fixture.order().getId()).getStatus()).isEqualTo("RECEIVED");

            // 库存侧：1 行余额 = 10，2 条流水，每行恒等式成立
            assertThat(balanceRowCount(warehouseId, skuId)).isEqualTo(1);
            assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("10.0000");
            List<Map<String, Object>> movements = movementsInLockOrder(warehouseId, skuId);
            assertThat(movements).hasSize(2);
            movements.forEach(row -> assertThat((BigDecimal) row.get("after_quantity"))
                    .isEqualByComparingTo(((BigDecimal) row.get("before_quantity"))
                            .add((BigDecimal) row.get("quantity"))));
            assertThat((BigDecimal) movements.get(0).get("before_quantity")).isEqualByComparingTo("0.0000");
            assertThat((BigDecimal) movements.get(1).get("after_quantity")).isEqualByComparingTo("10.0000");
            // 两张收货单各自的收货行都恰好入库一次
            assertThat(movementsOfReceiptItem(fixture.receiptItemId())).isEqualTo(1);
            assertThat(movementsOfReceiptItem(second.getItems().getFirst().getId())).isEqualTo(1);
        }
    }

    @Nested
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("报损报溢审批失败的真实回滚（PG IT，无外层事务）")
    class ScmInventoryLossGainRollbackIT extends ScmW6PgITBase {
        @Autowired
        private InventoryLossGainService lossGainService;

        @Autowired
        private InventoryReservationService reservations;

        @Override
        protected void evictMybatisCache() {
            // 无外层事务 → 每次调用都是新 session → 一级缓存天然为空
        }

        private Long stocked(String suffix, String quantity) {
            Long skuId = newSkuOfType(suffix, "NON_STANDARD", "ON_SHELF");
            W6Fixture fixture = inboundFixture(suffix, skuId, quantity);
            confirmReceipt(fixture.receipt().getId(), quantity);
            return skuId;
        }

        private static InventoryLossGainAddForm.Item item(Long skuId, String quantity) {
            InventoryLossGainAddForm.Item row = new InventoryLossGainAddForm.Item();
            row.setSkuId(skuId);
            row.setQuantity(new BigDecimal(quantity));
            return row;
        }

        private InventoryLossGainAddForm form(Long wh, Long firstSku, String firstQty,
                                              Long secondSku, String secondQty) {
            InventoryLossGainAddForm form = new InventoryLossGainAddForm();
            form.setAdjustType("LOSS");
            form.setWarehouseId(wh);
            form.setReason("同一批到货一起变质");
            form.setItems(new ArrayList<>(List.of(item(firstSku, firstQty), item(secondSku, secondQty))));
            return form;
        }

        private InventoryLossGainAuditForm audit(Long id) {
            InventoryLossGainAuditForm form = new InventoryLossGainAuditForm();
            form.setVersion(jdbc.queryForObject(
                    "SELECT version FROM xsy_inventory_loss_gain WHERE id = ?", Integer.class, id));
            return form;
        }

        private List<Map<String, Object>> lossGainMovements(Long wh, Long sku) {
            return movementsOf(wh, sku).stream()
                    .filter(m -> {
                        String type = String.valueOf(m.get("movement_type"));
                        return "LOSS_REPORT".equals(type) || "GAIN_REPORT".equals(type);
                    })
                    .toList();
        }

        /**
         * 审批必须换一个人（41065 禁止自建自审，P0 基线收口裁决第 8 条）：切到另一个员工身份跑完
         * 再恢复。库里没有指向 {@code t_employee} 的外键，审批人用合成 id 即可（只落进审计字段）。
         */
        private void approveAsAuditor(Long id, InventoryLossGainAuditForm form) {
            RequestUser maker = XsyRequestUtil.getRequestUser();
            RequestEmployee auditor = new RequestEmployee();
            auditor.setEmployeeId(90011L);
            auditor.setActualName("W6 IT 审核员");
            auditor.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            // 与基类同一取向：夹具操作者不受数据范围约束，本用例只测「审批人 != 录单人」这条身份规则，
            // 不顺带把仓库授权也当被测对象（那属于数据范围用例）。
            auditor.setAdministratorFlag(true);
            XsyRequestUtil.setRequestUser(auditor);
            try {
                lossGainService.approve(id, form);
            } finally {
                if (maker == null) {
                    XsyRequestUtil.remove();
                } else {
                    XsyRequestUtil.setRequestUser(maker);
                }
            }
        }

        @Test
        @DisplayName("前一行已写流水、后一行失败 → 整单回滚：余额零变化、流水零残留、单据仍是待审核")
        void failingLineRollsBackTheWholeDocument() {
            Long wh = seedWarehouseId();

            // 先建的先处理（服务端按 skuId 升序），因此 first 是「已经成功写入」的那一行
            Long first = stocked("RB3A", "10.0000");
            Long second = stocked("RB3B", "10.0000");

            // second 已全部预留 10 → 报损 1 会让 after = 9 < 已预留 10 → 41034
            // 来源标识取本次新建的 skuId：本类无外层事务，预留会提交到开发库，
            // 固定 id 会让同一个库上的第二次运行撞 uk 而假红（41016）。
            Long reservationId = reservations.reserve(new ReserveInventoryFact(
                    wh, second, "SALES_ORDER_ITEM", second, second,
                    new BigDecimal("10.0000"), OffsetDateTime.now(), null));

            Long id = lossGainService.create(form(wh, first, "2.0000", second, "1.0000"));
            expectCode(() -> approveAsAuditor(id, audit(id)), 41034);

            // first 那一行本已在同一事务内写下了报损流水与余额扣减，必须随整单一起消失
            assertThat(balanceRow(wh, first).getQuantity()).isEqualByComparingTo("10.0000");
            assertThat(lossGainMovements(wh, first)).isEmpty();
            assertThat(balanceRow(wh, second).getQuantity()).isEqualByComparingTo("10.0000");
            assertThat(lossGainMovements(wh, second)).isEmpty();
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM xsy_inventory_loss_gain WHERE id = ?", String.class, id))
                    .isEqualTo("PENDING");

            // 失败原因是那一行，不是别的东西坏了：释放预留后重试即通过
            reservations.release(reservationId);
            approveAsAuditor(id, audit(id));

            assertThat(balanceRow(wh, first).getQuantity()).isEqualByComparingTo("8.0000");
            assertThat(balanceRow(wh, second).getQuantity()).isEqualByComparingTo("9.0000");
            assertThat(lossGainMovements(wh, first)).hasSize(1);
            assertThat(lossGainMovements(wh, second)).hasSize(1);
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM xsy_inventory_loss_gain WHERE id = ?", String.class, id))
                    .isEqualTo("COMPLETED");
        }
    }

    @Nested
    @DisplayName("W9 采购收货批次（PG IT）")
    class ScmInventoryLotIT extends ScmW6PgITBase {
        private Long lotWarehouseId;

        @org.springframework.beans.factory.annotation.Autowired
        private com.xsy.scm.trace.service.TraceUnitService traceUnitService;

        @org.springframework.beans.factory.annotation.Autowired
        private com.xsy.scm.trace.service.TraceCorrectionService traceCorrectionService;

        @BeforeEach
        void createLotWarehouse() {
            WarehouseAddForm form = new WarehouseAddForm();
            form.setName(prefix + "批次仓");
            form.setLotTrackingEnabled(true);
            lotWarehouseId = warehouseService.create(form);
        }

        @Override
        protected Long fixtureWarehouseId() {
            return lotWarehouseId;
        }

        private BigDecimal lotBalance(Long warehouseId, Long skuId) {
            return jdbc.queryForObject(
                    "SELECT COALESCE(SUM(quantity), 0) FROM xsy_inventory_lot_balance "
                            + "WHERE warehouse_id = ? AND sku_id = ?",
                    BigDecimal.class, warehouseId, skuId);
        }

        private BigDecimal totalBalance(Long warehouseId, Long skuId) {
            return jdbc.queryForObject(
                    "SELECT quantity FROM xsy_inventory_balance WHERE warehouse_id = ? AND sku_id = ? AND deleted = FALSE",
                    BigDecimal.class, warehouseId, skuId);
        }

        /** 用给定批次登记确认一张收货单；批次数量之和由调用方保证等于收货量。 */
        private PurchaseReceiptVO confirmWithLots(Long receiptId, String quantity, String... lotQuantities) {
            PurchaseReceiptVO current = reloadReceipt(receiptId);
            PurchaseReceiptItemVO line = current.getItems().getFirst();
            PurchaseReceiptConfirmForm.Item item = receiptLine(line.getId(), line.getVersion(), quantity);
            item.setLots(List.of(lotQuantities).stream().map(text -> {
                PurchaseReceiptConfirmForm.Lot lot = new PurchaseReceiptConfirmForm.Lot();
                lot.setSourceLotNo("SUP-" + text);
                lot.setQuantity(text);
                return lot;
            }).toList());
            return purchaseReceiptService.confirm(confirmForm(current.getId(), current.getVersion(), item),
                    prefix + ":lot:" + UUID.randomUUID());
        }

        @Test
        @DisplayName("#1 一行拆两批：批次余额之和等于总账，批次流水与批次余额一致")
        void splitLotsKeepSubLedgerInSync() {
            Long skuId = newOnShelfSku("LOT1");
            W6Fixture fx = inboundFixture("LOT1", skuId, "10.0000");

            PurchaseReceiptVO confirmed = confirmWithLots(fx.receipt().getId(), "10.0000", "4.0000", "6.0000");
            assertThat(confirmed.getStatus()).isEqualTo("CONFIRMED");

            // 批次档案：两个批次，来源批号原样保存
            Integer lots = jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_trace_lot WHERE sku_id = ? AND source_lot_no LIKE 'SUP-%'",
                    Integer.class, skuId);
            assertThat(lots).isEqualTo(2);

            // 不变量：批次余额之和 = 总账余额
            assertThat(lotBalance(lotWarehouseId, skuId)).isEqualByComparingTo(totalBalance(lotWarehouseId, skuId));
            assertThat(lotBalance(lotWarehouseId, skuId)).isEqualByComparingTo("10.0000");

            // 不变量：批次流水之和 = 批次余额
            BigDecimal movementSum = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(quantity), 0) FROM xsy_inventory_lot_movement "
                            + "WHERE warehouse_id = ? AND sku_id = ?",
                    BigDecimal.class, lotWarehouseId, skuId);
            assertThat(movementSum).isEqualByComparingTo("10.0000");

            // 收货行批次记录的数量之和等于本次收货量
            BigDecimal declared = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(l.quantity), 0) FROM xsy_purchase_receipt_item_lot l "
                            + "JOIN xsy_purchase_receipt_item i ON i.id = l.purchase_receipt_item_id "
                            + "WHERE i.purchase_receipt_id = ?",
                    BigDecimal.class, fx.receipt().getId());
            assertThat(declared).isEqualByComparingTo("10.0000");
        }

        @Test
        @DisplayName("#2 出库按先进先出分摊：先入批次先出，子账之和等于总账")
        void outboundAllocatesFifoLots() {
            Long skuId = newOnShelfSku("LOT2");
            W6Fixture fx = inboundFixture("LOT2", skuId, "10.0000");
            confirmWithLots(fx.receipt().getId(), "10.0000", "4.0000", "6.0000");

            inventoryCommandService.postSalesOutbound(new InventoryOutboundFact(lotWarehouseId, skuId, 9_900_001L,
                    9_900_002L, new BigDecimal("5.0000"), new BigDecimal("6.2000"), OffsetDateTime.now(), "W9 IT"));

            // 先进先出：第一批 4 全部出掉，第二批出 1，剩 5
            List<BigDecimal> remaining = jdbc.queryForList(
                    "SELECT quantity FROM xsy_inventory_lot_balance WHERE warehouse_id = ? AND sku_id = ? "
                            + "ORDER BY lot_id",
                    BigDecimal.class, lotWarehouseId, skuId);
            assertThat(remaining).hasSize(2);
            assertThat(remaining.get(0)).isEqualByComparingTo("0.0000");
            assertThat(remaining.get(1)).isEqualByComparingTo("5.0000");

            assertThat(lotBalance(lotWarehouseId, skuId)).isEqualByComparingTo(totalBalance(lotWarehouseId, skuId));

            // 子账流水有符号：入库 +10、出库 -5
            BigDecimal movementSum = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(quantity), 0) FROM xsy_inventory_lot_movement "
                            + "WHERE warehouse_id = ? AND sku_id = ?",
                    BigDecimal.class, lotWarehouseId, skuId);
            assertThat(movementSum).isEqualByComparingTo("5.0000");
        }

        @Test
        @DisplayName("#3 批次仓拒绝调拨（一期只支持采购入库与销售出库）")
        void lotWarehouseRejectsTransfer() {
            Long skuId = newOnShelfSku("LOT3");
            W6Fixture fx = inboundFixture("LOT3", skuId, "10.0000");
            confirmWithLots(fx.receipt().getId(), "10.0000", "10.0000");

            assertThatThrownBy(() -> inventoryCommandService.postTransferOut(new InventoryTransferFact(lotWarehouseId,
                    skuId, 9_900_101L, 9_900_102L, new BigDecimal("1.0000"), DEFAULT_PURCHASE_UNIT,
                    OffsetDateTime.now(), "W9 IT")))
                    .isInstanceOf(ScmBusinessException.class)
                    .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode()).isEqualTo(41070));
        }

        /**
         * 批次仓不登记批次 → 40095。
         *
         * <p>
         * 与下一条拆成两个用例：异常会把测试事务标记为 rollback-only，
         * 同一个用例里再发第二次命令会得到 40921 而不是被测的行为。
         */
        @Test
        @DisplayName("#4 按批次发码：码不透明且唯一，历史记录查询能回到收货来源")
        void issueUnitsAndReplayToReceipt() {
            Long skuId = newOnShelfSku("UNIT1");
            W6Fixture fx = inboundFixture("UNIT1", skuId, "10.0000");
            confirmWithLots(fx.receipt().getId(), "10.0000", "10.0000");
            Long lotId = jdbc.queryForObject(
                    "SELECT id FROM xsy_trace_lot WHERE sku_id = ? ORDER BY id DESC LIMIT 1", Long.class, skuId);

            com.xsy.scm.trace.domain.form.TraceUnitIssueForm form = new com.xsy.scm.trace.domain.form.TraceUnitIssueForm();
            form.setLotId(lotId);
            form.setPackageType("BAG");
            form.setPackageCount(3);
            form.setQuantityPerPackage("2.0000");
            var units = traceUnitService.issue(form, "W9 IT");
            assertThat(units).hasSize(3);

            // 码不透明：U + 8 位 Base32，不含日期段
            List<String> codes = units.stream().map(com.xsy.scm.trace.domain.vo.TraceUnitVO.Unit::getUnitCode).toList();
            assertThat(codes).allMatch(code -> code.matches("^U[0-9A-HJKMNP-TV-Z]{8}$"));
            assertThat(codes).doesNotHaveDuplicates();
            // 发码即追加事件
            Integer events = jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_trace_event WHERE lot_id = ? AND event_type = 'ISSUE'",
                    Integer.class, lotId);
            assertThat(events).isEqualTo(3);

            // 历史记录查询：单元 → 批次 → 收货来源
            var replay = traceUnitService.replay(codes.getFirst());
            assertThat(replay.getLot().getLotNo()).matches("^XSY\\d{18,19}$");
            assertThat(replay.getReceipt()).isNotNull();
            assertThat(replay.getReceipt().getReceiptNo()).isEqualTo(fx.receipt().getReceiptNo());
        }

        @Test
        @DisplayName("#5 发码总量不得超过批次余额（41504）")
        void issueRejectsBeyondBalance() {
            Long skuId = newOnShelfSku("UNIT2");
            W6Fixture fx = inboundFixture("UNIT2", skuId, "10.0000");
            confirmWithLots(fx.receipt().getId(), "10.0000", "10.0000");
            Long lotId = jdbc.queryForObject(
                    "SELECT id FROM xsy_trace_lot WHERE sku_id = ? ORDER BY id DESC LIMIT 1", Long.class, skuId);

            com.xsy.scm.trace.domain.form.TraceUnitIssueForm form = new com.xsy.scm.trace.domain.form.TraceUnitIssueForm();
            form.setLotId(lotId);
            form.setPackageType("BAG");
            form.setPackageCount(6);
            form.setQuantityPerPackage("1.0000");
            traceUnitService.issue(form, "W9 IT");

            com.xsy.scm.trace.domain.form.TraceUnitIssueForm over = new com.xsy.scm.trace.domain.form.TraceUnitIssueForm();
            over.setLotId(lotId);
            over.setPackageType("BAG");
            over.setPackageCount(6);
            over.setQuantityPerPackage("1.0000");
            assertThatThrownBy(() -> traceUnitService.issue(over, "W9 IT"))
                    .isInstanceOf(ScmBusinessException.class)
                    .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode()).isEqualTo(41504));
        }

        @Test
        @DisplayName("#6 分拣扫码绑定：一码一物，历史记录查询能回到客户与订单")
        void bindUnitToSortingItemAndReplayToCustomer() {
            Long skuId = newOnShelfSku("BIND1");
            W6Fixture fx = inboundFixture("BIND1", skuId, "10.0000");
            confirmWithLots(fx.receipt().getId(), "10.0000", "10.0000");
            Long lotId = jdbc.queryForObject(
                    "SELECT id FROM xsy_trace_lot WHERE sku_id = ? ORDER BY id DESC LIMIT 1", Long.class, skuId);

            com.xsy.scm.trace.domain.form.TraceUnitIssueForm issue = new com.xsy.scm.trace.domain.form.TraceUnitIssueForm();
            issue.setLotId(lotId);
            issue.setPackageType("BAG");
            issue.setPackageCount(2);
            issue.setQuantityPerPackage("2.0000");
            var units = traceUnitService.issue(issue, "W9 IT");

            // 同一 SKU 的确认订单 + 分拣完成，才有可绑的分拣行
            Long customerId = newCustomer();
            Long salesOrderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
            sortingCompletedFor(salesOrderId);
            Long itemId = jdbc.queryForObject(
                    "SELECT id FROM xsy_sorting_task_item WHERE sales_order_id = ? AND sku_id = ? "
                            + "ORDER BY id DESC LIMIT 1", Long.class, salesOrderId, skuId);

            com.xsy.scm.trace.domain.form.TraceUnitBindForm bind = new com.xsy.scm.trace.domain.form.TraceUnitBindForm();
            bind.setUnitCode(units.getFirst().getUnitCode());
            bind.setSortingTaskItemId(itemId);
            var binding = traceUnitService.bind(bind, "W9 IT");
            assertThat(binding.getCustomerName()).isNotBlank();
            assertThat(binding.getOrderNo()).isNotBlank();

            // 绑定只能新增：事件里有一条 BIND
            Integer bindEvents = jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_trace_event WHERE unit_id = ? AND event_type = 'BIND'",
                    Integer.class, units.getFirst().getId());
            assertThat(bindEvents).isEqualTo(1);

            // 历史记录查询：码 → 客户
            var replay = traceUnitService.replay(units.getFirst().getUnitCode());
            assertThat(replay.getBinding()).isNotNull();
            assertThat(replay.getBinding().getCustomerName()).isEqualTo(binding.getCustomerName());
        }

        @Test
        @DisplayName("#7 同一个包装码不能绑两次（41507）")
        void bindRejectsSecondBinding() {
            Long skuId = newOnShelfSku("BIND2");
            W6Fixture fx = inboundFixture("BIND2", skuId, "10.0000");
            confirmWithLots(fx.receipt().getId(), "10.0000", "10.0000");
            Long lotId = jdbc.queryForObject(
                    "SELECT id FROM xsy_trace_lot WHERE sku_id = ? ORDER BY id DESC LIMIT 1", Long.class, skuId);

            com.xsy.scm.trace.domain.form.TraceUnitIssueForm issue = new com.xsy.scm.trace.domain.form.TraceUnitIssueForm();
            issue.setLotId(lotId);
            issue.setPackageType("BAG");
            issue.setPackageCount(1);
            issue.setQuantityPerPackage("1.0000");
            var units = traceUnitService.issue(issue, "W9 IT");

            Long customerId = newCustomer();
            Long salesOrderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
            sortingCompletedFor(salesOrderId);
            Long itemId = jdbc.queryForObject(
                    "SELECT id FROM xsy_sorting_task_item WHERE sales_order_id = ? AND sku_id = ? "
                            + "ORDER BY id DESC LIMIT 1", Long.class, salesOrderId, skuId);

            com.xsy.scm.trace.domain.form.TraceUnitBindForm bind = new com.xsy.scm.trace.domain.form.TraceUnitBindForm();
            bind.setUnitCode(units.getFirst().getUnitCode());
            bind.setSortingTaskItemId(itemId);
            traceUnitService.bind(bind, "W9 IT");

            // 一码一物：第二次绑定必须被拒，且不能留下第二条绑定记录
            assertThatThrownBy(() -> traceUnitService.bind(bind, "W9 IT"))
                    .isInstanceOf(ScmBusinessException.class)
                    .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode()).isEqualTo(41507));
        }

        @Test
        @DisplayName("#8 未绑定的码可以作废，重复作废被拒（41513）")
        void voidUnboundUnitAndRejectRepeat() {
            Long skuId = newOnShelfSku("VOID1");
            W6Fixture fx = inboundFixture("VOID1", skuId, "10.0000");
            confirmWithLots(fx.receipt().getId(), "10.0000", "10.0000");
            Long lotId = jdbc.queryForObject(
                    "SELECT id FROM xsy_trace_lot WHERE sku_id = ? ORDER BY id DESC LIMIT 1", Long.class, skuId);

            com.xsy.scm.trace.domain.form.TraceUnitIssueForm issue = new com.xsy.scm.trace.domain.form.TraceUnitIssueForm();
            issue.setLotId(lotId);
            issue.setPackageType("BAG");
            issue.setPackageCount(1);
            issue.setQuantityPerPackage("1.0000");
            var units = traceUnitService.issue(issue, "W9 IT");

            com.xsy.scm.trace.domain.form.TraceUnitVoidForm voidForm = new com.xsy.scm.trace.domain.form.TraceUnitVoidForm();
            voidForm.setUnitCode(units.getFirst().getUnitCode());
            voidForm.setReason("标签破损");
            traceUnitService.voidUnit(voidForm, "W9 IT");

            assertThat(jdbc.queryForObject("SELECT status FROM xsy_trace_unit WHERE id = ?", String.class,
                    units.getFirst().getId())).isEqualTo("VOID");
            Integer events = jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_trace_event WHERE unit_id = ? AND event_type = 'VOID' AND remark = ?",
                    Integer.class, units.getFirst().getId(), "标签破损");
            assertThat(events).isEqualTo(1);

            // 原码保留：作废不等于删除，也不能复用
            assertThat(traceUnitService.replay(units.getFirst().getUnitCode()).getUnit().getStatus()).isEqualTo("VOID");

            assertThatThrownBy(() -> traceUnitService.voidUnit(voidForm, "W9 IT"))
                    .isInstanceOf(ScmBusinessException.class)
                    .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode()).isEqualTo(41513));
        }

        @Test
        @DisplayName("#9 已绑定分拣行的码不能作废（41512）")
        void voidRejectsBoundUnit() {
            Long skuId = newOnShelfSku("VOID2");
            W6Fixture fx = inboundFixture("VOID2", skuId, "10.0000");
            confirmWithLots(fx.receipt().getId(), "10.0000", "10.0000");
            Long lotId = jdbc.queryForObject(
                    "SELECT id FROM xsy_trace_lot WHERE sku_id = ? ORDER BY id DESC LIMIT 1", Long.class, skuId);

            com.xsy.scm.trace.domain.form.TraceUnitIssueForm issue = new com.xsy.scm.trace.domain.form.TraceUnitIssueForm();
            issue.setLotId(lotId);
            issue.setPackageType("BAG");
            issue.setPackageCount(1);
            issue.setQuantityPerPackage("1.0000");
            var units = traceUnitService.issue(issue, "W9 IT");

            Long customerId = newCustomer();
            Long salesOrderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
            sortingCompletedFor(salesOrderId);
            Long itemId = jdbc.queryForObject(
                    "SELECT id FROM xsy_sorting_task_item WHERE sales_order_id = ? AND sku_id = ? "
                            + "ORDER BY id DESC LIMIT 1", Long.class, salesOrderId, skuId);
            com.xsy.scm.trace.domain.form.TraceUnitBindForm bind = new com.xsy.scm.trace.domain.form.TraceUnitBindForm();
            bind.setUnitCode(units.getFirst().getUnitCode());
            bind.setSortingTaskItemId(itemId);
            traceUnitService.bind(bind, "W9 IT");

            com.xsy.scm.trace.domain.form.TraceUnitVoidForm voidForm = new com.xsy.scm.trace.domain.form.TraceUnitVoidForm();
            voidForm.setUnitCode(units.getFirst().getUnitCode());
            voidForm.setReason("贴错");
            assertThatThrownBy(() -> traceUnitService.voidUnit(voidForm, "W9 IT"))
                    .isInstanceOf(ScmBusinessException.class)
                    .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode()).isEqualTo(41512));
        }

        @Test
        @DisplayName("#10 更正：申请 → 他人批准 → 绑定失效且可重新绑定")
        void correctionUnbindsAndAllowsRebinding() {
            Long skuId = newOnShelfSku("CORR1");
            W6Fixture fx = inboundFixture("CORR1", skuId, "10.0000");
            confirmWithLots(fx.receipt().getId(), "10.0000", "10.0000");
            Long lotId = jdbc.queryForObject(
                    "SELECT id FROM xsy_trace_lot WHERE sku_id = ? ORDER BY id DESC LIMIT 1", Long.class, skuId);

            com.xsy.scm.trace.domain.form.TraceUnitIssueForm issue = new com.xsy.scm.trace.domain.form.TraceUnitIssueForm();
            issue.setLotId(lotId);
            issue.setPackageType("BAG");
            issue.setPackageCount(1);
            issue.setQuantityPerPackage("1.0000");
            String unitCode = traceUnitService.issue(issue, "W9 IT").getFirst().getUnitCode();

            Long customerId = newCustomer();
            Long salesOrderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
            sortingCompletedFor(salesOrderId);
            Long itemId = jdbc.queryForObject(
                    "SELECT id FROM xsy_sorting_task_item WHERE sales_order_id = ? AND sku_id = ? "
                            + "ORDER BY id DESC LIMIT 1", Long.class, salesOrderId, skuId);

            com.xsy.scm.trace.domain.form.TraceUnitBindForm bind = new com.xsy.scm.trace.domain.form.TraceUnitBindForm();
            bind.setUnitCode(unitCode);
            bind.setSortingTaskItemId(itemId);
            traceUnitService.bind(bind, "W9 IT");

            com.xsy.scm.trace.domain.form.TraceCorrectionApplyForm apply =
                    new com.xsy.scm.trace.domain.form.TraceCorrectionApplyForm();
            apply.setUnitCode(unitCode);
            apply.setReason("绑错客户");
            var correction = traceCorrectionService.apply(apply, "W9 IT");
            assertThat(correction.getStatus()).isEqualTo("PENDING");

            com.xsy.scm.trace.domain.form.TraceCorrectionDecisionForm decision =
                    new com.xsy.scm.trace.domain.form.TraceCorrectionDecisionForm();
            decision.setId(correction.getId());
            decision.setVersion(correction.getVersion());
            decision.setReason("核实属实");
            var approved = traceCorrectionService.approve(decision, "W9 审批人");
            assertThat(approved.getStatus()).isEqualTo("APPROVED");

            // 绑定失效：历史记录查询不再显示客户，但解绑记录与事件都留痕
            assertThat(traceUnitService.replay(unitCode).getBinding()).isNull();
            Integer unbindEvents = jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_trace_event WHERE event_type = 'UNBIND' AND remark = ?",
                    Integer.class, "绑错客户");
            assertThat(unbindEvents).isEqualTo(1);
            Integer unbinds = jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_trace_unit_unbind WHERE correction_id = ?",
                    Integer.class, correction.getId());
            assertThat(unbinds).isEqualTo(1);

            // 解绑后可以重新绑定（更正的意义所在）
            traceUnitService.bind(bind, "W9 IT");
            assertThat(traceUnitService.replay(unitCode).getBinding()).isNotNull();
        }

        @Test
        @DisplayName("#11 更正申请不能自己审批（41522）")
        void correctionRejectsSelfApproval() {
            Long skuId = newOnShelfSku("CORR2");
            W6Fixture fx = inboundFixture("CORR2", skuId, "10.0000");
            confirmWithLots(fx.receipt().getId(), "10.0000", "10.0000");
            Long lotId = jdbc.queryForObject(
                    "SELECT id FROM xsy_trace_lot WHERE sku_id = ? ORDER BY id DESC LIMIT 1", Long.class, skuId);

            com.xsy.scm.trace.domain.form.TraceUnitIssueForm issue = new com.xsy.scm.trace.domain.form.TraceUnitIssueForm();
            issue.setLotId(lotId);
            issue.setPackageType("BAG");
            issue.setPackageCount(1);
            issue.setQuantityPerPackage("1.0000");
            String unitCode = traceUnitService.issue(issue, "W9 IT").getFirst().getUnitCode();

            Long customerId = newCustomer();
            Long salesOrderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
            sortingCompletedFor(salesOrderId);
            Long itemId = jdbc.queryForObject(
                    "SELECT id FROM xsy_sorting_task_item WHERE sales_order_id = ? AND sku_id = ? "
                            + "ORDER BY id DESC LIMIT 1", Long.class, salesOrderId, skuId);
            com.xsy.scm.trace.domain.form.TraceUnitBindForm bind = new com.xsy.scm.trace.domain.form.TraceUnitBindForm();
            bind.setUnitCode(unitCode);
            bind.setSortingTaskItemId(itemId);
            traceUnitService.bind(bind, "W9 IT");

            com.xsy.scm.trace.domain.form.TraceCorrectionApplyForm apply =
                    new com.xsy.scm.trace.domain.form.TraceCorrectionApplyForm();
            apply.setUnitCode(unitCode);
            apply.setReason("自己改自己");
            var correction = traceCorrectionService.apply(apply, "W9 IT");

            com.xsy.scm.trace.domain.form.TraceCorrectionDecisionForm decision =
                    new com.xsy.scm.trace.domain.form.TraceCorrectionDecisionForm();
            decision.setId(correction.getId());
            decision.setVersion(correction.getVersion());
            assertThatThrownBy(() -> traceCorrectionService.approve(decision, "W9 IT"))
                    .isInstanceOf(ScmBusinessException.class)
                    .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode()).isEqualTo(41522));
        }

        @Test
        @DisplayName("#4 批次仓不登记批次时拒绝确认（40095）")
        void lotWarehouseRequiresLots() {
            Long skuId = newOnShelfSku("LOT4");
            W6Fixture fx = inboundFixture("LOT4", skuId, "10.0000");

            PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
            PurchaseReceiptItemVO line = current.getItems().getFirst();
            PurchaseReceiptConfirmForm.Item withoutLots = receiptLine(line.getId(), line.getVersion(), "10.0000");
            assertThatThrownBy(() -> purchaseReceiptService.confirm(
                    confirmForm(current.getId(), current.getVersion(), withoutLots),
                    prefix + ":lot:none:" + UUID.randomUUID()))
                    .isInstanceOf(ScmBusinessException.class)
                    .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode()).isEqualTo(40095));
        }

        @Test
        @DisplayName("#5 批次数量之和与收货量不一致时拒绝确认（40096）")
        void lotQuantitiesMustMatchReceiptQuantity() {
            Long skuId = newOnShelfSku("LOT5");
            W6Fixture fx = inboundFixture("LOT5", skuId, "10.0000");

            PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
            PurchaseReceiptItemVO line = current.getItems().getFirst();
            PurchaseReceiptConfirmForm.Item mismatched = receiptLine(line.getId(), line.getVersion(), "10.0000");
            PurchaseReceiptConfirmForm.Lot lot = new PurchaseReceiptConfirmForm.Lot();
            lot.setQuantity("9.0000");
            mismatched.setLots(List.of(lot));
            assertThatThrownBy(() -> purchaseReceiptService.confirm(
                    confirmForm(current.getId(), current.getVersion(), mismatched),
                    prefix + ":lot:mismatch:" + UUID.randomUUID()))
                    .isInstanceOf(ScmBusinessException.class)
                    .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode()).isEqualTo(40096));
        }
    }

    @Nested
    @DisplayName("W6 库存域迁移与约定装配（PG IT）")
    class ScmInventoryMigrationIT extends ScmBaselineMigrationPgITBase {
        private static final List<String> W6_TABLES = List.of("xsy_inventory_balance", "xsy_inventory_movement");

        /**
         * 除余额与流水之外的库存表清单。
         *
         * <p>与 {@link #W6_TABLES} 分开列，是为了让「余额与流水两张核心表」这个记录不被后续新增稀释 ——
         * 一个列表越加越长就失去了「范围锁定」的作用。
         */
        private static final List<String> POST_W6_TABLES = List.of(
                "xsy_inventory_outbound", "xsy_inventory_outbound_item", "xsy_inventory_reservation",
                "xsy_inventory_stocktake", "xsy_inventory_stocktake_item",
                "xsy_inventory_loss_gain", "xsy_inventory_loss_gain_item",
                "xsy_inventory_transfer", "xsy_inventory_transfer_item",
                "xsy_inventory_warning_threshold",
                "xsy_inventory_conversion", "xsy_inventory_conversion_item");

        /**
         * 一个不可能与真实 id 冲突的哨兵（identity 从 1 起）。
         */
        private static final long SENTINEL_SOURCE_ITEM_ID = 9_000_000_000L + (System.nanoTime() % 1_000_000_000L);

        private String constraintDef(String table, String constraint) {
            return jdbc.queryForObject(
                    "SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c "
                            + "JOIN pg_class t ON t.oid = c.conrelid "
                            + "WHERE t.relname = ? AND c.conname = ?",
                    String.class, table, constraint);
        }

        private List<String> columnsOf(String table) {
            return jdbc.queryForList(
                    "SELECT column_name FROM information_schema.columns "
                            + "WHERE table_schema = current_schema() AND table_name = ?",
                    String.class, table);
        }

        /**
         * 插一条合法的 PURCHASE_IN 流水，返回 id（用于验证 append-only 与 CHECK）。
         */
        private Long insertMovement(String unitSnapshot, String movementType, String quantity,
                                    String before, String after) {
            return jdbc.queryForObject(
                    "INSERT INTO xsy_inventory_movement (warehouse_id, sku_id, movement_type, source_document_type, "
                            + "source_document_id, source_document_item_id, quantity, unit_snapshot, unit_cost, "
                            + "before_quantity, after_quantity, occurred_at, operator, deleted, created_by) "
                            + "VALUES (1, 1, ?, 'PURCHASE_RECEIPT_ITEM', 1, ?, ?::NUMERIC, ?, 1, "
                            + "?::NUMERIC, ?::NUMERIC, CURRENT_TIMESTAMP, 'W6 IT', FALSE, 'W6 IT') RETURNING id",
                    Long.class, movementType, SENTINEL_SOURCE_ITEM_ID, quantity, unitSnapshot, before, after);
        }

        // ------------------------------------------------------------------
        // DDL 约定
        // ------------------------------------------------------------------

        @Test
        @DisplayName("#11 DDL 约定：零外键、列形状、CHECK 生效、append-only 锁死、部分唯一索引谓词精确")
        void ddlContract() {
            // --- 零外键：全库既有纪律，库存域不得引入第一个外键 ---
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM pg_constraint WHERE contype = 'f'", Integer.class)).isZero();

            List<String> tables = jdbc.queryForList(
                    "SELECT table_name FROM information_schema.tables "
                            + "WHERE table_schema = current_schema() AND table_type = 'BASE TABLE'",
                    String.class);
            assertThat(tables).containsAll(W6_TABLES);
            assertThat(tables).containsAll(POST_W6_TABLES);

            // --- 列形状：余额带 unit、version 与 reserved_quantity；
            //     流水是纯 append-only（无 version / updated_*）---
            assertThat(columnsOf("xsy_inventory_balance"))
                    .contains("id", "warehouse_id", "sku_id", "unit", "quantity", "reserved_quantity",
                            "version", "deleted",
                            "created_at", "updated_at", "created_by", "updated_by")
                    // V34 起余额表有 avg_cost：移动加权是顺序相关的，
                    // 且出库成本必须在出库那一刻确定，
                    // 事后从流水反推需要重放整条历史（而重放的前提正是出库流水都已带成本，鸡生蛋）。
                    // 加列是有意的范围扩张，成本记录仍由 movement 的 unit_cost 承载。
                    .contains("avg_cost")
                    // 不允许悄悄出现的列：
                    // weight 属分拣域；warn_min / warn_max 不在余额表上（预警配置独立建表）；
                    // batch_id 不启用。total_cost 未采纳 —— 金额由 quantity × avg_cost 派生。
                    .doesNotContain("weight", "total_cost", "warn_min", "warn_max", "batch_id");
            assertThat(columnsOf("xsy_inventory_movement"))
                    .contains("id", "warehouse_id", "sku_id", "movement_type", "source_document_type",
                            "source_document_id", "source_document_item_id", "quantity", "unit_snapshot",
                            "unit_cost", "before_quantity", "after_quantity", "occurred_at", "operator",
                            "deleted", "created_at", "created_by")
                    .doesNotContain("version", "updated_at", "updated_by");

            // --- 部分唯一索引：谓词必须与 ON CONFLICT 的冲突目标完全一致 ---
            String balanceIndex = jdbc.queryForObject(
                    "SELECT indexdef FROM pg_indexes "
                            + "WHERE schemaname = current_schema() AND indexname = 'uk_inventory_balance_wh_sku_active'",
                    String.class);
            assertThat(balanceIndex).containsIgnoringCase("unique");
            assertThat(balanceIndex).contains("warehouse_id, sku_id");
            assertThat(balanceIndex).containsIgnoringCase("deleted = false");

            String movementIndex = jdbc.queryForObject(
                    "SELECT indexdef FROM pg_indexes "
                            + "WHERE schemaname = current_schema() AND indexname = 'uk_inventory_movement_source_active'",
                    String.class);
            assertThat(movementIndex).containsIgnoringCase("unique");
            assertThat(movementIndex).contains("source_document_type, source_document_item_id");
            assertThat(movementIndex).containsIgnoringCase("deleted = false");
            assertThat(movementIndex).containsIgnoringCase("source_document_item_id IS NOT NULL");

            // --- CHECK 定义（读约束定义，确认判据写在 DB 里而不是只写在服务层）---
            // 保存的数据约束必须是方向感知的：入库、出库、盘盈 / 盘亏、报损 / 报溢、转出 / 转入、
            // 转换出 / 转换入各自的分支都在同一条 CHECK 里，
            // 每个类型都必须落在自己的方向组里。
            assertThat(constraintDef("xsy_inventory_movement", "ck_inventory_movement_snap"))
                    .contains("before_quantity").contains("after_quantity").contains("quantity")
                    .contains("PURCHASE_IN").contains("SALES_OUT")
                    .contains("STOCKTAKE_GAIN").contains("STOCKTAKE_LOSS")
                    .contains("LOSS_REPORT").contains("GAIN_REPORT")
                    .contains("TRANSFER_OUT").contains("TRANSFER_IN")
                    .contains("CONVERT_OUT").contains("CONVERT_IN");
            assertThat(constraintDef("xsy_inventory_movement", "ck_inventory_movement_append_only"))
                    .containsIgnoringCase("deleted = false");
            assertThat(constraintDef("xsy_inventory_movement", "ck_inventory_movement_type"))
                    .contains("PURCHASE_IN").contains("SALES_OUT")
                    .contains("STOCKTAKE_GAIN").contains("STOCKTAKE_LOSS")
                    .contains("LOSS_REPORT").contains("GAIN_REPORT")
                    .contains("TRANSFER_OUT").contains("TRANSFER_IN")
                    .contains("CONVERT_OUT").contains("CONVERT_IN");
            // V187：余额不再用 CHECK 约束数量非负（负库存开关仓库允许销售出库出负数），
            // 改为 trg_scm_inventory_balance_negative_guard 按仓库 allow_negative_stock 判定；
            // 负余额不得有预留、非负余额仍要求 reserved_quantity <= quantity 的判断都在触发器里。
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM pg_trigger t JOIN pg_class c ON c.oid = t.tgrelid "
                            + "WHERE c.relname = 'xsy_inventory_balance' AND t.tgname = ? "
                            + "AND NOT t.tgisinternal",
                    Integer.class, "trg_scm_inventory_balance_negative_guard"))
                    .as("负库存守护触发器必须存在，否则负数余额会绕开所有约束").isEqualTo(1);
            assertThat(constraintDef("xsy_inventory_balance", "ck_inventory_balance_reserved"))
                    .contains("reserved_quantity");
            // V34：均价不得为负（服务层加权计算已保证，这里 DB 兜底）。
            // 注意没有 ck 约束禁止 avg_cost = 0 —— 0 是合法值（期初无采购历史时的默认）。
            assertThat(constraintDef("xsy_inventory_balance", "ck_inventory_balance_avg_cost"))
                    .contains("avg_cost");

            // --- 行为验证：约束真的会拒绝坏数据 ---
            Long movementId = insertMovement("kg", "PURCHASE_IN", "5", "0", "5");
            assertThat(movementId).isNotNull();

            // soft delete 历史流水在数据库层失败（服务层纪律之外还有 DB 约束兜底）
            expectSqlFailure("UPDATE xsy_inventory_movement SET deleted = TRUE WHERE id = ?", movementId);
            // V21 closes the gap left by CHECK (deleted = FALSE): valid-looking edits
            // and physical removal must also fail, even when every CHECK would pass.
            expectSqlFailure("UPDATE xsy_inventory_movement SET unit_cost = 2 WHERE id = ?", movementId);
            expectSqlFailure("UPDATE xsy_inventory_movement SET quantity = 6, after_quantity = 6 WHERE id = ?", movementId);
            expectSqlFailure("DELETE FROM xsy_inventory_movement WHERE id = ?", movementId);
            expectSqlFailure("TRUNCATE TABLE xsy_inventory_movement");
            // 行仍然在，且仍是 deleted = FALSE
            assertThat(jdbc.queryForObject(
                    "SELECT deleted FROM xsy_inventory_movement WHERE id = ?", Boolean.class, movementId)).isFalse();

            // after ≠ before + quantity → 违反恒等式
            expectSqlFailure("INSERT INTO xsy_inventory_movement (warehouse_id, sku_id, movement_type, "
                    + "source_document_type, source_document_id, source_document_item_id, quantity, unit_snapshot, "
                    + "before_quantity, after_quantity, occurred_at, deleted) "
                    + "VALUES (1, 1, 'PURCHASE_IN', 'PURCHASE_RECEIPT_ITEM', 1, ?, 5, 'kg', 0, 6, "
                    + "CURRENT_TIMESTAMP, FALSE)", SENTINEL_SOURCE_ITEM_ID + 1);
            // quantity 必须 > 0（方向编码在类型里，数量恒为正）
            expectSqlFailure("INSERT INTO xsy_inventory_movement (warehouse_id, sku_id, movement_type, "
                    + "source_document_type, source_document_id, source_document_item_id, quantity, unit_snapshot, "
                    + "before_quantity, after_quantity, occurred_at, deleted) "
                    + "VALUES (1, 1, 'PURCHASE_IN', 'PURCHASE_RECEIPT_ITEM', 1, ?, 0, 'kg', 0, 0, "
                    + "CURRENT_TIMESTAMP, FALSE)", SENTINEL_SOURCE_ITEM_ID + 2);
            // movement_type 白名单：白名单之外一律拒绝。这里用 UNKNOWN_IN 这个
            // 明确不存在的名字 —— 真实类型已全部落地，再拿「未实现的业务类型」当反例
            // 会每落地一个就要改一次。
            // 也不用已放行的类型当反例：那会因保存的数据方向不符而失败，就不是在验证白名单了。
            expectSqlFailure("INSERT INTO xsy_inventory_movement (warehouse_id, sku_id, movement_type, "
                    + "source_document_type, source_document_id, source_document_item_id, quantity, unit_snapshot, "
                    + "before_quantity, after_quantity, occurred_at, deleted) "
                    + "VALUES (1, 1, 'UNKNOWN_IN', 'PURCHASE_RECEIPT_ITEM', 1, ?, 1, 'kg', 0, 1, "
                    + "CURRENT_TIMESTAMP, FALSE)", SENTINEL_SOURCE_ITEM_ID + 3);
            // 当时的单位不能是空白
            expectSqlFailure("INSERT INTO xsy_inventory_movement (warehouse_id, sku_id, movement_type, "
                    + "source_document_type, source_document_id, source_document_item_id, quantity, unit_snapshot, "
                    + "before_quantity, after_quantity, occurred_at, deleted) "
                    + "VALUES (1, 1, 'PURCHASE_IN', 'PURCHASE_RECEIPT_ITEM', 1, ?, 1, '  ', 0, 1, "
                    + "CURRENT_TIMESTAMP, FALSE)", SENTINEL_SOURCE_ITEM_ID + 4);
            // 余额数量 >= 0：V186 把种子仓 WH001 设为允许负库存，这里另建一个普通仓验证守恒规则。
            Long strictWarehouseId = newWarehouse("守恒");
            expectSqlFailure("INSERT INTO xsy_inventory_balance (warehouse_id, sku_id, unit, quantity, version, deleted) "
                    + "VALUES (?, 1, 'kg', -1, 0, FALSE)", strictWarehouseId);
            // 余额单位不能是空白
            expectSqlFailure("INSERT INTO xsy_inventory_balance (warehouse_id, sku_id, unit, quantity, version, deleted) "
                    + "VALUES (1, 1, '', 1, 0, FALSE)");

            // --- append-only 在代码层的形态：DAO 只声明 insert + select ---
            // 这是「冲销必须新增反向 movement，而不是改历史行」的编译期保障：
            // 往这个 DAO 加 update/delete 方法，本断言会立刻失败并强制走评审。
            // {@code selectBySourceItem} 是一个只读回查（调拨收货回读转出腿成本），
            // 不改变这条纪律。
            // {@code listSalesOutAllocations} 只读查询原销售出库分摊，不增加历史写入口。
            // {@code listBundleSalesOutAllocations} 同理：只读回查组合商品出库时各组成行的分摊。
            List<String> movementDaoMethods = Arrays.stream(InventoryMovementDao.class.getDeclaredMethods())
                    .map(Method::getName).sorted().toList();
            assertThat(movementDaoMethods).containsExactly(
                    "countActiveBySourceItem", "insertOnConflictDoNothing", "listBundleSalesOutAllocations",
                    "listSalesOutAllocations", "queryPage", "selectBySourceItem");

            // 余额 DAO 也没有任何「设置绝对数量」的方法：余额只能是流水的净和。
            // 出库扣减与预留增减都是增量方法，没有赋值型方法。
            // {@code incrementQuantityAndSetAvgCost} —— 数量仍是增量，
            // 只有 avg_cost 是赋值（它是活状态而非流水的净和，见 V34 头注释）。
            // {@code listActiveByWarehouse}：仍是只读回查，不改变上面的纪律。
            List<String> balanceDaoMethods = Arrays.stream(InventoryBalanceDao.class.getDeclaredMethods())
                    .map(Method::getName).sorted().toList();
            assertThat(balanceDaoMethods).containsExactly(
                    "decrementQuantity", "decrementReserved", "detail", "incrementQuantity",
                    "incrementQuantityAndSetAvgCost", "incrementReserved", "insertOnConflictDoNothing",
                    "listActiveByWarehouse", "lockByWarehouseAndSku", "queryPage", "selectByWarehouseAndSku");
            assertThat(balanceDaoMethods)
                    .noneMatch(name -> name.toLowerCase().contains("setquantity"))
                    .noneMatch(name -> name.toLowerCase().contains("updatequantity"));
        }

        // ------------------------------------------------------------------
        // V30 报损报溢：四条 DB 层判据
        // ------------------------------------------------------------------

        /**
         * 报损报溢 schema 的四条判据（类型 / 状态 / 原因 / 审核信息）必须在数据库层生效。
         *
         * <p>这四条值得单独断言：它们不是「格式约束」，而是控制——
         * 类型决定库存往哪个方向变；原因与审核信息是审批留痕的全部内容。
         * 只写在服务层的话，任何绕过服务层的写入（脚本、修数、新增的入口）都会静默破坏它们。
         */
        @Test
        @DisplayName("V30 报损报溢 schema：类型 / 状态 / 原因 / 审核信息四条判据都在 DB 层生效")
        void lossGainSchemaIsEnforcedByTheDatabase() {
            // 白名单与枚举同源（前端枚举、后端枚举、DB CHECK 三处必须一致）
            assertThat(constraintDef("xsy_inventory_loss_gain", "ck_inventory_loss_gain_type"))
                    .contains("LOSS").contains("OVERFLOW");
            assertThat(constraintDef("xsy_inventory_loss_gain", "ck_inventory_loss_gain_status"))
                    .contains("PENDING").contains("COMPLETED").contains("REJECTED");
            assertThat(constraintDef("xsy_inventory_loss_gain", "ck_inventory_loss_gain_reason"))
                    .contains("reason");
            assertThat(constraintDef("xsy_inventory_loss_gain", "ck_inventory_loss_gain_audit"))
                    .contains("audited_at").contains("auditor");

            String insert = "INSERT INTO xsy_inventory_loss_gain "
                    + "(loss_gain_no, warehouse_id, adjust_type, status, reason, audited_at, auditor, version, deleted) "
                    + "VALUES (?, 1, ?, ?, ?, %s, %s, 0, FALSE)";

            // 1) 未知类型：方向无法确定，不能猜
            expectSqlFailure(insert.formatted("NULL", "NULL"), "W30-IT-1", "CONVERT", "PENDING", "规格转换不属于本表");
            // 2) 未知状态
            expectSqlFailure(insert.formatted("NULL", "NULL"), "W30-IT-2", "LOSS", "DRAFT", "草稿态不在本表状态机里");
            // 3) 空白原因：没有原因的单据无法审计，「留痕」也就没有内容
            expectSqlFailure(insert.formatted("NULL", "NULL"), "W30-IT-3", "LOSS", "PENDING", "   ");
            // 4) 已完成却没有审核时刻 / 审核人：审批必须留下是谁、什么时候
            expectSqlFailure(insert.formatted("NULL", "NULL"), "W30-IT-4", "LOSS", "COMPLETED", "到货变质");
            // 5) 待审核却带了审核人：两侧都要挡，否则审核信息可以被提前写脏，
            //    「谁批的」就失去意义（因为可以预先填一个名字）
            expectSqlFailure(insert.formatted("CURRENT_TIMESTAMP", "'张三'"), "W30-IT-5", "LOSS", "PENDING", "到货变质");

            // 合法插入必须成功 —— 证明上面五次失败是判据生效，而不是表根本插不进去
            assertThat(jdbc.update(
                    "INSERT INTO xsy_inventory_loss_gain "
                            + "(loss_gain_no, warehouse_id, adjust_type, status, reason, version, deleted) "
                            + "VALUES (?, 1, 'LOSS', 'PENDING', '到货变质', 0, FALSE)", "W30-IT-OK"))
                    .isEqualTo(1);
        }

        // ------------------------------------------------------------------
        // V31 调拨：源仓≠目标仓 + 状态与发出/收货信息一致
        // ------------------------------------------------------------------

        /**
         * 调拨 schema 的四条判据必须在数据库层生效。
         *
         * <p>值得单独断言：「源仓 ≠ 目标仓」是调拨唯一的结构性前提，
         * 而「状态与发出 / 收货信息一致」保证了两笔流水各自的 {@code occurred_at} 与
         * {@code operator} 一定读得到 —— 少了任何一条，调拨就能写出无法审计的账。
         */
        @Test
        @DisplayName("V31 调拨 schema：源仓≠目标仓、状态与发出/收货信息一致，都在 DB 层生效")
        void transferSchemaIsEnforcedByTheDatabase() {
            assertThat(constraintDef("xsy_inventory_transfer", "ck_inventory_transfer_status"))
                    .contains("PENDING").contains("SHIPPED").contains("RECEIVED").contains("CANCELLED");
            assertThat(constraintDef("xsy_inventory_transfer", "ck_inventory_transfer_distinct"))
                    .contains("from_warehouse_id").contains("to_warehouse_id");
            assertThat(constraintDef("xsy_inventory_transfer", "ck_inventory_transfer_shipped"))
                    .contains("shipped_at").contains("shipped_by");
            assertThat(constraintDef("xsy_inventory_transfer", "ck_inventory_transfer_received"))
                    .contains("received_at").contains("received_by");

            String insert = "INSERT INTO xsy_inventory_transfer "
                    + "(transfer_no, from_warehouse_id, to_warehouse_id, status, "
                    + "shipped_at, shipped_by, received_at, received_by, version, deleted) "
                    + "VALUES (?, ?, ?, ?, %s, %s, %s, %s, 0, FALSE)";

            // 1) 源仓 == 目标仓：那不是调拨，是把货在同一行余额上来回加减
            expectSqlFailure(insert.formatted("NULL", "NULL", "NULL", "NULL"),
                    "W31-IT-1", 1L, 1L, "PENDING");
            // 2) 未知状态
            expectSqlFailure(insert.formatted("NULL", "NULL", "NULL", "NULL"),
                    "W31-IT-2", 1L, 2L, "CONFIRMED");
            // 3) 在途却没有发出信息 → 转出流水的 occurred_at / operator 就无从取值
            expectSqlFailure(insert.formatted("NULL", "NULL", "NULL", "NULL"),
                    "W31-IT-3", 1L, 2L, "SHIPPED");
            // 4) 已收货却没有收货信息 → 转入流水的 occurred_at / operator 无从取值
            expectSqlFailure(insert.formatted("CURRENT_TIMESTAMP", "'甲'", "NULL", "NULL"),
                    "W31-IT-4", 1L, 2L, "RECEIVED");
            // 5) 草稿却带了发出人：两侧都要挡，否则「谁发的」可以被预先写脏
            expectSqlFailure(insert.formatted("CURRENT_TIMESTAMP", "'甲'", "NULL", "NULL"),
                    "W31-IT-5", 1L, 2L, "PENDING");

            // 合法插入必须成功 —— 证明上面五次失败是判据生效，而不是表根本插不进去
            assertThat(jdbc.update("INSERT INTO xsy_inventory_transfer "
                    + "(transfer_no, from_warehouse_id, to_warehouse_id, status, version, deleted) "
                    + "VALUES (?, 1, 2, 'PENDING', 0, FALSE)", "W31-IT-OK")).isEqualTo(1);
        }

        // ------------------------------------------------------------------
        // V32 预警阈值：配置不在余额表上 + 区间判据
        // ------------------------------------------------------------------

        /**
         * V32 的两条结构性记录。
         *
         * <p>第一条是阈值配置不在 {@code xsy_inventory_balance} 上。
         * 上面 {@link #ddlContract()} 里 {@code doesNotContain("warn_min", "warn_max")} 的断言与它互相印证：
         * 余额行只能由流水产生，配置路径不该造出「没有流水支撑的余额行」。
         */
        @Test
        @DisplayName("V32 预警阈值：配置表独立于余额表，且区间判据在 DB 层生效")
        void warningThresholdSchemaIsEnforcedByTheDatabase() {
            // 阈值配置表存在，且余额表上依然没有 warn_min / warn_max
            assertThat(columnsOf("xsy_inventory_warning_threshold"))
                    .contains("warehouse_id", "sku_id", "warn_min", "warn_max", "version", "deleted");
            assertThat(columnsOf("xsy_inventory_balance"))
                    .as("阈值是配置，不是余额状态：不得挪到余额表上")
                    .doesNotContain("warn_min", "warn_max");

            // 一个 (仓库, SKU) 只能有一条有效配置
            String index = jdbc.queryForObject(
                    "SELECT indexdef FROM pg_indexes WHERE schemaname = current_schema() "
                            + "AND indexname = 'uk_inventory_warning_threshold_wh_sku_active'",
                    String.class);
            assertThat(index).containsIgnoringCase("unique");
            assertThat(index).contains("warehouse_id, sku_id");
            assertThat(index).containsIgnoringCase("deleted = false");

            assertThat(constraintDef("xsy_inventory_warning_threshold", "ck_inventory_warning_threshold_range"))
                    .contains("warn_min").contains("warn_max");

            String insert = "INSERT INTO xsy_inventory_warning_threshold "
                    + "(warehouse_id, sku_id, warn_min, warn_max, version, deleted) "
                    + "VALUES (1, 1, %s, %s, 0, FALSE)";

            // 注意：下面每条 SQL 都不带参数占位符（边界值已经 format 进 SQL 文本），
            // 所以不能再给 expectSqlFailure 传参数 —— 传了会让它因为「栏位索引超过许可范围」
            // 而失败，看起来像「约束生效」，实际上根本没测到约束。
            // 1) 上下限都没有：没有任何判断依据
            expectSqlFailure(insert.formatted("NULL", "NULL"));
            // 2) 下限为负
            expectSqlFailure(insert.formatted("-1", "NULL"));
            // 3) 上限为负
            expectSqlFailure(insert.formatted("NULL", "-1"));
            // 4) 下限大于上限：会让所有状态都异常，预警失去意义
            expectSqlFailure(insert.formatted("10", "5"));

            // 合法插入必须成功。两条用不同的 sku_id —— 唯一索引是 (warehouse_id, sku_id)，
            // 同一个 sku 插两次会撞索引，与这里的区间判据无关。
            assertThat(jdbc.update(insert.formatted("5", "100"))).isEqualTo(1);
            assertThat(jdbc.update("INSERT INTO xsy_inventory_warning_threshold "
                    + "(warehouse_id, sku_id, warn_min, warn_max, version, deleted) "
                    + "VALUES (1, 2, 5, 5, 0, FALSE)"))
                    .as("上下限相等是合法的（等价于精确值告警）").isEqualTo(1);
        }

        // ------------------------------------------------------------------
        // 约定装配
        // ------------------------------------------------------------------

        @Test
        @DisplayName("#17 约定装配：PurchaseInventoryContract 恰好 1 个 Bean（真实实现），NoOp 未注册")
        void exactlyOneInventoryContractBean() {
            // 「容器里恰好一个库存约定 Bean」
            // 是约定真正被兑现的充要条件。
            Map<String, PurchaseInventoryContract> beans =
                    applicationContext.getBeansOfType(PurchaseInventoryContract.class);
            assertThat(beans).hasSize(1);
            assertThat(beans.values().iterator().next())
                    .isInstanceOf(PurchaseInventoryContractImpl.class);

            // 占位实现仍然存在但没有被注册：
            // 否则会出现两个候选 Bean，装配期就会歧义报错 —— 那也是一种失败，只是失败得很晚。
            assertThat(applicationContext.getBeansOfType(NoOpPurchaseInventoryContract.class)).isEmpty();

            // 写路径必须加入调用方事务，因此命令服务不得自带事务语义。
            // 加 @Transactional(REQUIRES_NEW) 会让「收货确认与入库同事务」静默失效，
            // 而那种失效在功能测试里看不出来（入库照样成功），只有回滚用例会暴露它。
            assertThat(InventoryCommandService.class.getAnnotation(Transactional.class)).isNull();
            assertThat(Arrays.stream(InventoryCommandService.class.getDeclaredMethods())
                    .filter(method -> method.getName().equals("postPurchaseInbound"))
                    .findFirst()
                    .orElseThrow()
                    .getAnnotation(Transactional.class)).isNull();
        }
    }

    @Nested
    @DisplayName("出库与预留（PG IT）")
    class ScmInventoryOutboundIT extends ScmW6PgITBase {
        @Autowired
        private InventoryReservationService reservations;

        private Long outboundWarehouseId;

        /**
         * 负库存关闭的独立仓：这些用例守的是「没有货不能出」的守恒规则，
         * 而种子仓 WH001 在 V186 起允许销售出库出负数量，借用它会把断言变成对开关的断言。
         */
        @Override
        protected Long fixtureWarehouseId() {
            if (outboundWarehouseId == null) {
                outboundWarehouseId = newWarehouse("出库");
            }
            return outboundWarehouseId;
        }

        private Long warehouseId() {
            return fixtureWarehouseId();
        }

        /**
         * 造一个已入库指定数量的 SKU，返回 (warehouseId, skuId)。
         */
        private Object[] stocked(String suffix, String quantity) {
            Long skuId = newSkuOfType(suffix, "NON_STANDARD", "ON_SHELF");
            W6Fixture fixture = inboundFixture(suffix, skuId, quantity);
            confirmReceipt(fixture.receipt().getId(), quantity);
            return new Object[]{warehouseId(), skuId};
        }

        private InventoryOutboundFact outboundFact(Long wh, Long sku, String qty, long itemId) {
            return new InventoryOutboundFact(wh, sku, 0L, itemId, new BigDecimal(qty), null,
                    OffsetDateTime.now(), "test:1");
        }

        @Test
        @DisplayName("出库扣减余额并写 SALES_OUT 流水，保存的数据方向为减")
        void outboundDecrementsBalanceAndWritesReverseSnapshot() {
            Object[] s = stocked("ob1", "10.0000");
            Long wh = (Long) s[0];
            Long sku = (Long) s[1];

            BigDecimal before = balanceRow(wh, sku).getQuantity();
            assertThat(before).isEqualByComparingTo("10.0000");

            inventoryCommandService.postSalesOutbound(outboundFact(wh, sku, "3.0000", 900001L));

            InventoryBalanceEntity after = balanceRow(wh, sku);
            assertThat(after.getQuantity()).isEqualByComparingTo("7.0000");

            List<java.util.Map<String, Object>> salesOut = movementsOf(wh, sku).stream()
                    .filter(m -> "SALES_OUT".equals(String.valueOf(m.get("movement_type"))))
                    .toList();
            assertThat(salesOut).hasSize(1);
            java.util.Map<String, Object> row = salesOut.getFirst();
            // 方向感知保存的数据：出库是减，这正是 V25 必须重建 ck_inventory_movement_snap 的原因
            assertThat(new BigDecimal(String.valueOf(row.get("before_quantity")))).isEqualByComparingTo("10.0000");
            assertThat(new BigDecimal(String.valueOf(row.get("quantity")))).isEqualByComparingTo("3.0000");
            assertThat(new BigDecimal(String.valueOf(row.get("after_quantity")))).isEqualByComparingTo("7.0000");
            // 单位以余额记账单位为准，由服务端取，不由调用方传
            assertThat(String.valueOf(row.get("unit_snapshot"))).isEqualTo(balanceRow(wh, sku).getUnit());
        }

        @Test
        @DisplayName("出库超过现有量被拒（41011），余额不变 —— 不引入负库存")
        void outboundBeyondOnHandIsRejected() {
            Object[] s = stocked("ob2", "5.0000");
            Long wh = (Long) s[0];
            Long sku = (Long) s[1];

            expectCode(() -> inventoryCommandService.postSalesOutbound(outboundFact(wh, sku, "6.0000", 900002L)), 41011);
            assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("5.0000");
        }

        @Test
        @DisplayName("从未入库的 (仓库, SKU) 出库被拒（41011），且不会建出零余额行")
        void outboundWithoutAnyBalanceIsRejectedWithoutCreatingRow() {
            Long skuId = newSkuOfType("ob3", "NON_STANDARD", "ON_SHELF");
            Long wh = warehouseId();
            assertThat(balanceRowCount(wh, skuId)).isZero();

            expectCode(() -> inventoryCommandService.postSalesOutbound(outboundFact(wh, skuId, "1.0000", 900003L)), 41011);
            // 出库不建行：没有余额行 = 从未入库 = 无货可出
            assertThat(balanceRowCount(wh, skuId)).isZero();
        }

        @Test
        @DisplayName("同一来源行重复出库被拒（41015），不会扣两次")
        void duplicateOutboundFromSameSourceLineIsRejected() {
            Object[] s = stocked("ob4", "10.0000");
            Long wh = (Long) s[0];
            Long sku = (Long) s[1];

            inventoryCommandService.postSalesOutbound(outboundFact(wh, sku, "2.0000", 900004L));
            expectCode(() -> inventoryCommandService.postSalesOutbound(outboundFact(wh, sku, "2.0000", 900004L)), 41015);
            assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("8.0000");
        }

        @Test
        @DisplayName("预留占用可用量但不改物理库存；释放后可用量恢复")
        void reservationHoldsAvailableWithoutTouchingOnHand() {
            Object[] s = stocked("rs1", "10.0000");
            Long wh = (Long) s[0];
            Long sku = (Long) s[1];

            Long reservationId = reservations.reserve(new ReserveInventoryFact(
                    wh, sku, "SALES_ORDER_ITEM", 700001L, 800001L,
                    new BigDecimal("4.0000"), OffsetDateTime.now(), null));

            InventoryBalanceEntity held = balanceRow(wh, sku);
            assertThat(held.getQuantity()).as("预留不改物理库存").isEqualByComparingTo("10.0000");
            assertThat(held.getReservedQuantity()).isEqualByComparingTo("4.0000");

            reservations.release(reservationId);

            InventoryBalanceEntity released = balanceRow(wh, sku);
            assertThat(released.getReservedQuantity()).isEqualByComparingTo("0.0000");
            assertThat(released.getQuantity()).isEqualByComparingTo("10.0000");
        }

        @Test
        @DisplayName("预留超过可用量被拒（41011）")
        void reservationBeyondAvailableIsRejected() {
            Object[] s = stocked("rs2", "5.0000");
            Long wh = (Long) s[0];
            Long sku = (Long) s[1];

            expectCode(() -> reservations.reserve(new ReserveInventoryFact(
                    wh, sku, "SALES_ORDER_ITEM", 700002L, 800002L,
                    new BigDecimal("6.0000"), OffsetDateTime.now(), null)), 41011);
            assertThat(balanceRow(wh, sku).getReservedQuantity()).isEqualByComparingTo("0.0000");
        }

        @Test
        @DisplayName("出库不得吃掉已预留的货（41011）—— 可用量 = 现有量 − 预留量")
        void outboundCannotConsumeReservedStock() {
            Object[] s = stocked("rs3", "10.0000");
            Long wh = (Long) s[0];
            Long sku = (Long) s[1];

            reservations.reserve(new ReserveInventoryFact(
                    wh, sku, "SALES_ORDER_ITEM", 700003L, 800003L,
                    new BigDecimal("8.0000"), OffsetDateTime.now(), null));

            // 现有量 10，其中 8 已预留 → 可用量只有 2
            expectCode(() -> inventoryCommandService.postSalesOutbound(outboundFact(wh, sku, "3.0000", 900005L)), 41011);
            assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("10.0000");
            assertThat(balanceRow(wh, sku).getReservedQuantity()).isEqualByComparingTo("8.0000");

            // 出满可用量是允许的
            inventoryCommandService.postSalesOutbound(outboundFact(wh, sku, "2.0000", 900006L));
            assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("8.0000");
        }

        @Test
        @DisplayName("重复释放被拒（41016），可用量不会被虚增")
        void doubleReleaseIsRejected() {
            Object[] s = stocked("rs4", "10.0000");
            Long wh = (Long) s[0];
            Long sku = (Long) s[1];

            Long id = reservations.reserve(new ReserveInventoryFact(
                    wh, sku, "SALES_ORDER_ITEM", 700004L, 800004L,
                    new BigDecimal("3.0000"), OffsetDateTime.now(), null));
            reservations.release(id);
            expectCode(() -> reservations.release(id), 41016);
            assertThat(balanceRow(wh, sku).getReservedQuantity()).isEqualByComparingTo("0.0000");
        }

        @Test
        @DisplayName("Q7：出库流水同样不可改删（新增类型不是绕过 append-only 的口子）")
        void outboundMovementsAreStillAppendOnly() {
            Object[] s = stocked("ob5", "10.0000");
            Long wh = (Long) s[0];
            Long sku = (Long) s[1];
            inventoryCommandService.postSalesOutbound(outboundFact(wh, sku, "1.0000", 900007L));

            Long movementId = ((Number) movementsOf(wh, sku).stream()
                    .filter(m -> "SALES_OUT".equals(String.valueOf(m.get("movement_type"))))
                    .findFirst().orElseThrow().get("id")).longValue();

            assertMovementImmutable(movementId);
        }
    }

    @Nested
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("库存预留并发（PG IT，无外层事务）")
    class ScmInventoryReservationConcurrencyIT extends ScmW6PgITBase {
        /**
         * 锁等待远小于它；超过即为死锁或活锁 —— 比默默挂死好。
         */
        private static final long TIMEOUT_SECONDS = 60;

        @Autowired
        private InventoryReservationService reservations;

        /**
         * 出库命令与预留一样要求调用方持有事务（{@code InventoryCommandService} 会直接抛错），
         * 本类没有外层事务，因此跨到出库时必须显式开一个，而不是让 IllegalTransactionStateException
         * 冒充「可用量门槛拒绝」——那会让并发断言以错误的理由通过。
         */
        @Autowired
        private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

        private void postSalesOutbound(InventoryOutboundFact fact) {
            transactionTemplate.executeWithoutResult(status -> inventoryCommandService.postSalesOutbound(fact));
        }

        /**
         * 造来源行号用的种子：必须只增不减，且落在库里已用行号之上。
         *
         * <p>原先写 {@code MAX(id) + 100000}，但预留表被别的用例部分清理过，{@code MAX(id)} 会回落：
         * 实测遗留行号已到 100778，而下一颗种子只算到 100740 —— 于是新一轮造出的行号与上一轮遗留的
         * ACTIVE 行重合，{@code uk_inventory_reservation_source_active} 生效，reserve() 报 41016
         * 「预留不合法」，看着像超卖判定写坏了，其实是造数串台（表现为偶发红，且隔离连跑几乎必红）。
         *
         * <p>改用预留表自己的序列取值：序列值不回收，乘 100 拉开间距，保证相邻两次调用至少差 100，
         * 够每个用例取 +1..+3 而互不重叠；再与 {@code MAX(行号)} 取 GREATEST，跨过改动前就已在库里的遗留行。
         *
         * <p>用 {@code COALESCE} 而不是 SQL 的 {@code ?:} 简写：简写里的 {@code ?}
         * 会被 JDBC 驱动当成参数占位符，StatementCallback 直接报语法错误。
         */
        private Long sourceIdSeed() {
            return jdbc.queryForObject(
                    "SELECT GREATEST(nextval('inventory_reservation_id_seq'), "
                            + "(SELECT COALESCE(MAX(source_document_item_id), 0) FROM xsy_inventory_reservation) / 100 + 1) * 100",
                    Long.class);
        }

        @Override
        protected void evictMybatisCache() {
            // 无外层事务 → 每次 DAO 调用都是新 session → 一级缓存天然为空
        }

        /**
         * 子线程没有请求上下文，必须自己塞一个身份（{@code ScmOperator.current()} 依赖它）。
         */
        private static void setThreadOperator() {
            RequestEmployee employee = new RequestEmployee();
            employee.setEmployeeId(1L);
            employee.setActualName("Reservation concurrency IT");
            employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            // 与种子员工 1 的真实行一致：id 1 是 V3 里的 break-glass 超管。本用例测的是竞态，
            // 断言不能对仓库授权范围守卫敏感——缺了这一位，预留与释放会被按「无任何授权」拒掉，
            // 竞态根本发生不了，失败的那笔还会留下未释放的预留行污染后面的用例。
            employee.setAdministratorFlag(true);
            XsyRequestUtil.setRequestUser(employee);
        }

        private record Outcome(boolean success, Long id, Throwable error) {

            static Outcome ok(Long id) {
                return new Outcome(true, id, null);
            }

            static Outcome failed(Throwable error) {
                return new Outcome(false, null, error);
            }
        }

        /**
         * 让 N 段动作在同一起跑线开始，各自跑在独立事务里。
         */
        private List<Outcome> runConcurrently(List<java.util.concurrent.Callable<Long>> tasks) throws Exception {
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
            try {
                List<Future<Long>> futures = new ArrayList<>(tasks.size());
                for (java.util.concurrent.Callable<Long> task : tasks) {
                    futures.add(pool.submit(() -> {
                        setThreadOperator();
                        start.await();
                        try {
                            return task.call();
                        } finally {
                            XsyRequestUtil.remove();
                        }
                    }));
                }
                start.countDown();

                List<Outcome> outcomes = new ArrayList<>(futures.size());
                for (Future<Long> future : futures) {
                    try {
                        outcomes.add(Outcome.ok(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)));
                    } catch (ExecutionException e) {
                        outcomes.add(Outcome.failed(e.getCause()));
                    }
                }
                return outcomes;
            } finally {
                pool.shutdownNow();
            }
        }

        private List<Outcome> reserveTogether(Long warehouseId, Long skuId, Long sourceOrderId,
                                              List<String> quantities, List<Long> itemIds) throws Exception {
            List<java.util.concurrent.Callable<Long>> tasks = new ArrayList<>();
            for (int i = 0; i < quantities.size(); i++) {
                BigDecimal quantity = new BigDecimal(quantities.get(i));
                Long itemId = itemIds.get(i);
                tasks.add(() -> reservations.reserve(new ReserveInventoryFact(
                        warehouseId, skuId, "SALES_ORDER_ITEM", sourceOrderId, itemId,
                        quantity, OffsetDateTime.now(), null)));
            }
            return runConcurrently(tasks);
        }

        /**
         * 本类必须使用「不允许负库存」的仓库：V218 把种子仓 WH001 改成允许负库存后，
         * 出库不再受「现有量 − 预留量」门槛约束，这个竞态命题就无从验证。
         * 独占仓库同时避免本类的余额行污染共享种子仓。
         */
        private Long concurrencyWarehouseId;

        @Override
        protected Long fixtureWarehouseId() {
            if (concurrencyWarehouseId == null) {
                concurrencyWarehouseId = newWarehouse("rc-gate");
            }
            return concurrencyWarehouseId;
        }

        /**
         * 入库 {@code quantity} 后的 (仓库, SKU)，物理量已落账。
         */
        private Object[] stocked(String suffix, String quantity) {
            Long skuId = newSkuOfType(suffix, "NON_STANDARD", "ON_SHELF");
            W6Fixture fixture = inboundFixture(suffix, skuId, quantity);
            confirmReceipt(fixture.receipt().getId(), quantity);
            Long warehouseId = fixtureWarehouseId();
            assertThat(balanceRow(warehouseId, skuId)).as("前置：造数入库未落账").isNotNull();
            return new Object[]{warehouseId, skuId};
        }

        private BigDecimal reserved(Long warehouseId, Long skuId) {
            return balanceRow(warehouseId, skuId).getReservedQuantity();
        }

        private BigDecimal onHand(Long warehouseId, Long skuId) {
            return balanceRow(warehouseId, skuId).getQuantity();
        }

        private int reservationCountByStatus(Long warehouseId, Long skuId, String status) {
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_inventory_reservation "
                            + "WHERE warehouse_id = ? AND sku_id = ? AND status = ? AND deleted = FALSE",
                    Integer.class, warehouseId, skuId, status);
            return count == null ? 0 : count;
        }

        /**
         * 该来源订单行留下的未删除预留行数。故意<b>不按 status 过滤</b>：
         * 「被拒的那笔不该留任何行」要比「不该留 ACTIVE 行」更严——留一行 RELEASED 同样是漏写。
         */
        private int reservationRowsForItem(Long itemId) {
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_inventory_reservation "
                            + "WHERE source_document_type = 'SALES_ORDER_ITEM' "
                            + "AND source_document_item_id = ? AND deleted = FALSE",
                    Integer.class, itemId);
            return count == null ? 0 : count;
        }

        /**
         * 可用量（现有量 − 预留量），直接读余额行两列现算，不取任何缓存视图。
         */
        private BigDecimal available(Long warehouseId, Long skuId) {
            InventoryBalanceEntity balance = balanceRow(warehouseId, skuId);
            return balance.getQuantity().subtract(balance.getReservedQuantity());
        }

        // ------------------------------------------------------------------

        @Test
        @DisplayName("并发预留不超卖：3×4 抢 10 → 恰 2 成功、1 报 41011，占用量恒不超过现有量")
        void concurrentReservesCannotOversell() throws Exception {
            Object[] s = stocked("rc1", "10.0000");
            Long warehouseId = (Long) s[0];
            Long skuId = (Long) s[1];
            Long order = sourceIdSeed();
            List<Long> items = List.of(order + 1, order + 2, order + 3);

            List<Outcome> outcomes = reserveTogether(warehouseId, skuId, order,
                    List.of("4.0000", "4.0000", "4.0000"), items);

            long succeeded = outcomes.stream().filter(Outcome::success).count();
            assertThat(succeeded)
                    .as("总需求 12 > 现有量 10：只允许 2 笔落地，成功数不是 2 说明可用量判断跑在锁之外")
                    .isEqualTo(2);
            outcomes.stream().filter(outcome -> !outcome.success()).forEach(outcome ->
                    assertThat(SCM_ERROR_CODE.apply(outcome.error()))
                            .as("被拒的那笔必须是可用量不足（41011），不能是死锁或版本冲突", outcome.error())
                            .isEqualTo(41011));

            assertThat(reserved(warehouseId, skuId)).isEqualByComparingTo("8.0000");
            assertThat(onHand(warehouseId, skuId)).as("预留不改变物理库存").isEqualByComparingTo("10.0000");
            // 哪一笔被拒取决于哪个线程最后抢到余额行锁，不能假定就是提交顺序里的最后一笔：
            // outcomes 与 itemIds 按下标一一对应，据此定位真正失败的那几笔来查残留。
            for (int i = 0; i < items.size(); i++) {
                if (!outcomes.get(i).success())
                    assertThat(reservationRowsForItem(items.get(i)))
                            .as("被拒的那笔（%s）不得留下预留行", items.get(i))
                            .isZero();
            }
            assertThat(reserved(warehouseId, skuId))
                    .as("ck_inventory_balance_available 的规则：占用不得超过现有量")
                    .isLessThan(onHand(warehouseId, skuId));
        }

        @Test
        @DisplayName("同一订单行并发重复预留：只落 1 行 ACTIVE，占用只加一次")
        void concurrentReserveForSameSourceLineCountsOnce() throws Exception {
            Object[] s = stocked("rc2", "10.0000");
            Long warehouseId = (Long) s[0];
            Long skuId = (Long) s[1];
            Long order = sourceIdSeed();
            Long itemId = order + 10;

            // 同一来源行、两笔都放得下（3+3 ≤ 10）—— 于是唯一能拦住第二笔的就是防重索引。
            List<Outcome> outcomes = reserveTogether(warehouseId, skuId, order,
                    List.of("3.0000", "3.0000"), List.of(itemId, itemId));

            assertThat(outcomes.stream().filter(Outcome::success).count())
                    .as("重复预留必须恰一成功")
                    .isEqualTo(1);
            assertThat(reservationRowsForItem(itemId))
                    .as("软删也算第二行：同一来源行只能有一条活动预留")
                    .isEqualTo(1);
            assertThat(reserved(warehouseId, skuId))
                    .as("占用量必须只加一次，双计会让后续可用量静默偏小")
                    .isEqualByComparingTo("3.0000");
            outcomes.stream().filter(outcome -> !outcome.success()).forEach(outcome ->
                    assertThat(SCM_ERROR_CODE.apply(outcome.error()))
                            .as("重复预留必须报 41016", outcome.error())
                            .isEqualTo(41016));
            assertLedgerBalanced(warehouseId, skuId);
        }

        @Test
        @DisplayName("并发释放同一条预留：恰一次回补，可用量不被虚增也不为负")
        void concurrentReleaseOfSameReservationIsCountedOnce() throws Exception {
            Object[] s = stocked("rc3", "10.0000");
            Long warehouseId = (Long) s[0];
            Long skuId = (Long) s[1];
            Long order = sourceIdSeed();
            List<Outcome> reservedOutcome = reserveTogether(warehouseId, skuId, order,
                    List.of("4.0000"), List.of(order + 20));
            Long reservationId = reservedOutcome.getFirst().id();
            assertThat(reserved(warehouseId, skuId)).isEqualByComparingTo("4.0000");

            List<Outcome> outcomes = runConcurrently(List.of(
                    () -> {
                        reservations.release(reservationId);
                        return reservationId;
                    },
                    () -> {
                        reservations.release(reservationId);
                        return reservationId;
                    }));

            assertThat(outcomes.stream().filter(Outcome::success).count())
                    .as("释放必须恰一次生效")
                    .isEqualTo(1);
            assertThat(reserved(warehouseId, skuId))
                    .as("重复回补会让 reserved_quantity 变负或残留 —— 两者都是错账")
                    .isEqualByComparingTo("0.0000");
            assertThat(available(warehouseId, skuId)).isEqualByComparingTo("10.0000");
            assertThat(reservationCountByStatus(warehouseId, skuId, "RELEASED")).isEqualTo(1);
            outcomes.stream().filter(outcome -> !outcome.success()).forEach(outcome ->
                    assertThat(SCM_ERROR_CODE.apply(outcome.error()))
                            .as("第二次的失败必须来自状态条件（41016），而不是余额行消失", outcome.error())
                            .isEqualTo(41016));
        }

        @Test
        @DisplayName("并发释放同仓同 SKU 的两条预留：两次都回补，可用量恢复后出库能出满")
        void concurrentReleaseOfDifferentReservationsRestoresAvailability() throws Exception {
            Object[] s = stocked("rc4", "10.0000");
            Long warehouseId = (Long) s[0];
            Long skuId = (Long) s[1];
            Long order = sourceIdSeed();
            List<Outcome> held = reserveTogether(warehouseId, skuId, order,
                    List.of("3.0000", "4.0000"), List.of(order + 30, order + 31));
            Long first = held.get(0).id();
            Long second = held.get(1).id();
            assertThat(reserved(warehouseId, skuId)).isEqualByComparingTo("7.0000");

            List<Outcome> outcomes = runConcurrently(List.of(
                    () -> {
                        reservations.release(first);
                        return first;
                    },
                    () -> {
                        reservations.release(second);
                        return second;
                    }));

            assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.success())
                    .as("两条互不相干的预留不应互相失败：%s", outcome.error())
                    .isTrue());
            // 两次 decrement 都必须落地：丢失更新会残留 3 或 4 而不是 0
            assertThat(reserved(warehouseId, skuId)).isEqualByComparingTo("0.0000");
            assertThat(available(warehouseId, skuId)).isEqualByComparingTo("10.0000");
            assertThat(reservationCountByStatus(warehouseId, skuId, "ACTIVE")).isZero();

            // 可用量恢复必须是真实的：能一次性出满 10，才证明不是靠预留行残留撑出来的数字
            postSalesOutbound(new InventoryOutboundFact(
                    warehouseId, skuId, 0L, order + 32, new BigDecimal("10.0000"), null,
                    OffsetDateTime.now(), "test:1"));
            assertThat(onHand(warehouseId, skuId)).isEqualByComparingTo("0.0000");
            assertLedgerBalanced(warehouseId, skuId);
        }

        @Test
        @DisplayName("预留与出库并发抢同一余额行：可用量规则唯一，6+6 抢 10 → 恰一成功、剩 4 可用")
        void reserveAndOutboundShareOneAvailableQuantityGate() throws Exception {
            Object[] s = stocked("rc5", "10.0000");
            Long warehouseId = (Long) s[0];
            Long skuId = (Long) s[1];
            Long order = sourceIdSeed();
            Long outboundItemId = order + 40;

            List<Outcome> outcomes = runConcurrently(List.of(
                    () -> reservations.reserve(new ReserveInventoryFact(
                            warehouseId, skuId, "SALES_ORDER_ITEM", order, order + 41,
                            new BigDecimal("6.0000"), OffsetDateTime.now(), null)),
                    () -> {
                        postSalesOutbound(new InventoryOutboundFact(
                                warehouseId, skuId, 0L, outboundItemId, new BigDecimal("6.0000"), null,
                                OffsetDateTime.now(), "test:1"));
                        return null;
                    }));

            long succeeded = outcomes.stream().filter(Outcome::success).count();
            assertThat(succeeded)
                    .as("占用与出库共用「可用量 = 现有量 − 预留量」一个规则：两者合计 12 > 10，只能成一个")
                    .isEqualTo(1);
            outcomes.stream().filter(outcome -> !outcome.success()).forEach(outcome ->
                    assertThat(SCM_ERROR_CODE.apply(outcome.error()))
                            .as("输的那一方必须是被可用量门槛拒绝（41011），不能是「缺少事务」这类脚手架异常", outcome.error())
                            .isEqualTo(41011));
            assertThat(available(warehouseId, skuId)).isEqualByComparingTo("4.0000");
            assertThat(reserved(warehouseId, skuId))
                    .as("reserved_quantity <= quantity 在竞态后仍成立")
                    .isLessThanOrEqualTo(onHand(warehouseId, skuId));
            assertLedgerBalanced(warehouseId, skuId);

            List<Map<String, Object>> movements = movementsOf(warehouseId, skuId);
            long salesOut = movements.stream()
                    .filter(row -> "SALES_OUT".equals(String.valueOf(row.get("movement_type")))).count();
            assertThat(salesOut)
                    .as("失败的出库不能留下流水（预留不写流水，出库只有一个来源行成功）")
                    .isLessThanOrEqualTo(1);
        }

        /**
         * 从异常里取 SCM 业务错误码；非业务异常返回哨兵值，让断言报出真实原因而不是 NPE。
         */
        private static final java.util.function.Function<Throwable, Integer> SCM_ERROR_CODE = error -> {
            if (error instanceof com.xsy.scm.common.exception.ScmBusinessException e) {
                return e.getErrorCode().getCode();
            }
            return -1;
        };
    }

    @Nested
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("W6 入库失败的真实回滚（PG IT，无外层事务）")
    class ScmInventoryRollbackIT extends ScmW6PgITBase {
        @Override
        protected void evictMybatisCache() {
            // 见类注释：无外层事务 → 每次调用都是新 session → 一级缓存天然为空
        }

        // ------------------------------------------------------------------
        // #3
        // ------------------------------------------------------------------

        @Test
        @DisplayName("#3 超收 40989：真实回滚后余额与流水零残留，采购侧与防重复处理记录也不动")
        void overReceiptLeavesNoResidue() {
            Long warehouseId = seedWarehouseId();
            Long skuId = newOnShelfSku("RB3");
            W6Fixture fx = inboundFixture("RB3", skuId, "10.0000");

            // 可收上限 = planned × (1 + 容差 10%) = 11 → 20 必然超收
            expectCode(() -> confirmReceipt(fx.receipt().getId(), "20.0000"), 40989);

            // 库存侧：余额行根本没被创建，流水一条都没有
            assertThat(balanceRow(warehouseId, skuId)).isNull();
            assertThat(movementCount(warehouseId, skuId)).isZero();

            // 采购侧：收货单仍是草稿、采购行累计为 0、采购单状态没动
            assertThat(reloadReceipt(fx.receipt().getId()).getStatus()).isEqualTo("PENDING");
            assertThat(receivedQuantityOf(fx.orderItemId())).isEqualByComparingTo("0.0000");
            assertThat(reloadOrder(fx.order().getId()).getStatus()).isEqualTo("SUBMITTED");

            // 防重复处理 claim 也随事务回滚：同一个键 + 同样的内容重试，再次执行并再次得到 40989。
            // 若 claim 残留成「已提交但缺 result_data」，这里会得到另一个错误码 —— 断言会立刻失败。
            String key = prefix + ":RB3:retry";
            expectCode(() -> confirmReceipt(fx.receipt().getId(), "20.0000", key), 40989);
            expectCode(() -> confirmReceipt(fx.receipt().getId(), "20.0000", key), 40989);

            // 换成合规数量后仍然能正常入库（失败的原因是超收，不是别的东西坏了）
            assertThat(confirmReceipt(fx.receipt().getId(), "10.0000").getStatus()).isEqualTo("CONFIRMED");
            assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("10.0000");
            assertThat(movementCount(warehouseId, skuId)).isEqualTo(1);
        }

        // ------------------------------------------------------------------
        // #13
        // ------------------------------------------------------------------

        @Test
        @DisplayName("#13 Q13 单位不匹配 41001：confirm 整体回滚（库存侧 + 采购侧零残留）")
        void unitMismatchRollsBackTheWholeConfirm() {
            Long warehouseId = seedWarehouseId();
            Long skuId = newOnShelfSku("RB13");
            // 同一个 SKU 经两个供应商入库、采购单位不同 —— 只有「无需求来源」的采购行能构造出来
            // （有需求来源时要求采购单位 == 销售单位，异单位在分配阶段就被拒了）
            W6Fixture kilogram = freeInboundFixture("RB13a", skuId, "5.0000", "kg");
            W6Fixture box = freeInboundFixture("RB13b", skuId, "3.0000", "box");

            // 第一笔建立记账单位 kg
            confirmReceipt(kilogram.receipt().getId(), "5.0000");
            assertThat(balanceRow(warehouseId, skuId).getUnit()).isEqualTo("kg");
            assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("5.0000");
            assertThat(movementCount(warehouseId, skuId)).isEqualTo(1);

            // 第二笔异单位 → 41001（显式失败，不是把 5kg + 3箱 加成一个没有物理意义的数）
            expectCode(() -> confirmReceipt(box.receipt().getId(), "3.0000"), 41001);

            // 库存侧：既没有第二行余额，也没有「加了一半」的余额，流水也没多
            assertThat(balanceRowCount(warehouseId, skuId)).isEqualTo(1);
            assertThat(balanceRow(warehouseId, skuId).getUnit()).isEqualTo("kg");
            assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("5.0000");
            assertThat(movementCount(warehouseId, skuId)).isEqualTo(1);

            // 采购侧：整笔确认被回滚 —— 这是「收货确认与入库同事务」最直接的证据。
            // 如果库存写入另开了事务（REQUIRES_NEW），这里会看到 CONFIRMED + 累计 3.0000。
            assertThat(reloadReceipt(box.receipt().getId()).getStatus()).isEqualTo("PENDING");
            assertThat(receivedQuantityOf(box.orderItemId())).isEqualByComparingTo("0.0000");
            assertThat(reloadOrder(box.order().getId()).getStatus()).isEqualTo("SUBMITTED");

            // 换成同单位后仍可入库（被拒的原因是单位不一致，不是这张单坏了）
            W6Fixture kilogram2 = freeInboundFixture("RB13c", skuId, "2.0000", "kg");
            confirmReceipt(kilogram2.receipt().getId(), "2.0000");
            assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("7.0000");
            assertThat(balanceRow(warehouseId, skuId).getUnit()).isEqualTo("kg");
            assertThat(movementCount(warehouseId, skuId)).isEqualTo(2);
        }
    }

    @Nested
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("盘点失败的真实回滚（PG IT，无外层事务）")
    class ScmInventoryStocktakeRollbackIT extends ScmW6PgITBase {
        @Autowired
        private InventoryStocktakeService stocktakeService;

        @Autowired
        private InventoryReservationService reservations;

        @Override
        protected void evictMybatisCache() {
            // 无外层事务 → 每次调用都是新 session → 一级缓存天然为空
        }

        /**
         * 造一个已入库指定数量的 SKU。
         */
        private Long stocked(String suffix, String quantity) {
            Long skuId = newSkuOfType(suffix, "NON_STANDARD", "ON_SHELF");
            W6Fixture fixture = inboundFixture(suffix, skuId, quantity);
            confirmReceipt(fixture.receipt().getId(), quantity);
            return skuId;
        }

        /**
         * 两行盘点单（行序由服务端按 skuId 升序决定，因此先建的 SKU 先被处理）。
         */
        private InventoryStocktakeAddForm twoLineForm(Long warehouseId,
                                                      Long firstSku, String firstActual,
                                                      Long secondSku, String secondActual) {
            InventoryStocktakeAddForm form = new InventoryStocktakeAddForm();
            form.setWarehouseId(warehouseId);
            form.setItems(new ArrayList<>(List.of(
                    item(firstSku, firstActual), item(secondSku, secondActual))));
            return form;
        }

        private static InventoryStocktakeAddForm.Item item(Long skuId, String actual) {
            InventoryStocktakeAddForm.Item row = new InventoryStocktakeAddForm.Item();
            row.setSkuId(skuId);
            row.setActualQuantity(new BigDecimal(actual));
            return row;
        }

        private List<Map<String, Object>> stocktakeMovements(Long wh, Long sku) {
            return movementsOf(wh, sku).stream()
                    .filter(m -> String.valueOf(m.get("movement_type")).startsWith("STOCKTAKE_"))
                    .toList();
        }

        @Test
        @DisplayName("前一行已写流水、后一行失败 → 整单回滚：余额零变化、流水零残留、单据仍是草稿")
        void failingLineRollsBackTheWholeStocktake() {
            Long wh = seedWarehouseId();

            // 先建的先处理（服务端按 skuId 升序），因此 first 是「已经成功写入」的那一行
            Long first = stocked("RB1A", "10.0000");
            Long second = stocked("RB1B", "10.0000");

            // second 已全部预留 10 → 实盘 0 会让 after = 0 < 已预留 10 → 41025
            // 来源标识取本次新建的 skuId：本类无外层事务，预留会提交到开发库，
            // 固定 id 会让同一个库上的第二次运行撞 uk 而假红（41016）。
            reservations.reserve(new ReserveInventoryFact(
                    wh, second, "SALES_ORDER_ITEM", second, second,
                    new BigDecimal("10.0000"), OffsetDateTime.now(), null));

            Long id = stocktakeService.create(
                    twoLineForm(wh, first, "12.0000", second, "0.0000"));

            expectCode(() -> stocktakeService.confirm(id), 41025);

            // first 那一行本已在同一事务内写下了盘盈流水与余额调整，必须随整单一起消失
            assertThat(balanceRow(wh, first).getQuantity()).isEqualByComparingTo("10.0000");
            assertThat(stocktakeMovements(wh, first)).isEmpty();
            assertThat(balanceRow(wh, second).getQuantity()).isEqualByComparingTo("10.0000");
            assertThat(stocktakeMovements(wh, second)).isEmpty();

            // 单据仍是草稿、明细仍在 —— 用户修掉冲突行之后可以直接重试
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM xsy_inventory_stocktake WHERE id = ?", String.class, id))
                    .isEqualTo("PENDING");
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_inventory_stocktake_item WHERE stocktake_id = ? AND deleted = FALSE",
                    Integer.class, id)).isEqualTo(2);

            // 失败原因是那一行，不是别的东西坏了：把实盘改成与预留兼容后整单可以正常确认
            stocktakeService.update(id, twoLineForm(wh, first, "12.0000", second, "10.0000"));
            stocktakeService.confirm(id);

            assertThat(balanceRow(wh, first).getQuantity()).isEqualByComparingTo("12.0000");
            assertThat(balanceRow(wh, second).getQuantity()).isEqualByComparingTo("10.0000");
            assertThat(stocktakeMovements(wh, first)).hasSize(1);
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM xsy_inventory_stocktake WHERE id = ?", String.class, id))
                    .isEqualTo("CONFIRMED");
        }

        @Test
        @DisplayName("盘亏推成负数 → 41024 且零残留（余额与流水都不动）")
        void negativeAdjustmentLeavesNoResidue() {
            Long wh = seedWarehouseId();
            Long sku = stocked("RB2A", "10.0000");

            InventoryStocktakeAddForm form = new InventoryStocktakeAddForm();
            form.setWarehouseId(wh);
            // 导出时的账面数据 10、实盘 0 → delta = −10；账面就是 10 → after = 0，正好不越界。
            // 为了构造「推成负数」，先把账面降到 3（出库 7），再盘亏到 0 → after = 3 − 10 = −7。
            form.setItems(new ArrayList<>(List.of(item(sku, "0.0000"))));
            Long id = stocktakeService.create(form);

            // 确认之前出库 7 → 确认瞬间账面 3，delta 仍以保存的数据 10 为基线 = −10 → after = −7
            jdbc.update("UPDATE xsy_inventory_balance SET quantity = 3 WHERE warehouse_id = ? AND sku_id = ?",
                    wh, sku);
            evictMybatisCache();

            expectCode(() -> stocktakeService.confirm(id), 41024);

            assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("3.0000");
            assertThat(stocktakeMovements(wh, sku)).isEmpty();
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM xsy_inventory_stocktake WHERE id = ?", String.class, id))
                    .isEqualTo("PENDING");
        }
    }

    @Nested
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("调拨失败的真实回滚（PG IT，无外层事务）")
    class ScmInventoryTransferRollbackIT extends ScmW6PgITBase {
        @Autowired
        private InventoryTransferService transferService;

        /**
         * 本类建过的仓库 id，用例结束后软删（否则会破坏「唯一启用仓库」的规则）。
         */
        private final List<Long> createdWarehouses = new ArrayList<>();

        @Override
        protected void evictMybatisCache() {
            // 无外层事务 → 每次调用都是新 session → 一级缓存天然为空
        }

        @AfterEach
        void cleanupWarehouses() {
            for (Long warehouseId : createdWarehouses) {
                // 软删即可：warehouse 走 @TableLogic，all() / defaultEnabledWarehouse() 会自动过滤。
                // 硬删会破坏「历史记录保留可查」的规则，也没有必要。
                jdbc.update("UPDATE xsy_warehouse SET deleted = TRUE, status = 'DISABLED' WHERE id = ?", warehouseId);
            }
            createdWarehouses.clear();
        }

        private Long trackedWarehouse(String suffix) {
            Long id = newWarehouse(suffix);
            createdWarehouses.add(id);
            return id;
        }

        private Long stocked(String suffix, String quantity) {
            Long skuId = newSkuOfType(suffix, "NON_STANDARD", "ON_SHELF");
            W6Fixture fixture = inboundFixture(suffix, skuId, quantity);
            confirmReceipt(fixture.receipt().getId(), quantity);
            return skuId;
        }

        private static InventoryTransferAddForm.Item item(Long skuId, String quantity) {
            InventoryTransferAddForm.Item row = new InventoryTransferAddForm.Item();
            row.setSkuId(skuId);
            row.setQuantity(new BigDecimal(quantity));
            return row;
        }

        private InventoryTransferAddForm twoLineForm(Long from, Long to, Long first, String firstQty,
                                                     Long second, String secondQty) {
            InventoryTransferAddForm form = new InventoryTransferAddForm();
            form.setFromWarehouseId(from);
            form.setToWarehouseId(to);
            form.setItems(new ArrayList<>(List.of(item(first, firstQty), item(second, secondQty))));
            return form;
        }

        private String statusOf(Long id) {
            return jdbc.queryForObject(
                    "SELECT status FROM xsy_inventory_transfer WHERE id = ?", String.class, id);
        }

        @Test
        @DisplayName("前一行已写转出流水、后一行可用量不足 → 整单回滚：余额零变化、流水零残留、单据仍是草稿")
        void failingLineRollsBackTheWholeShip() {
            Long wh1 = seedWarehouseId();
            Long wh2 = trackedWarehouse("RB4B");

            // 先建的先处理（服务端按 skuId 升序），因此 first 是「已经成功写入」的那一行
            Long first = stocked("RB4A", "10.0000");
            Long second = stocked("RB4C", "10.0000");

            // first 要 5（够），second 要 11（不够）→ 第二行抛 41043
            Long id = transferService.create(
                    twoLineForm(wh1, wh2, first, "5.0000", second, "11.0000"));

            expectCode(() -> transferService.ship(id), 41043);

            // first 那一行本已在同一事务内写下了转出流水与余额扣减，必须随整单一起消失
            assertThat(balanceRow(wh1, first).getQuantity()).isEqualByComparingTo("10.0000");
            assertThat(movementCount(wh1, first)).as("只剩入库那一条流水").isEqualTo(1);
            assertThat(balanceRow(wh1, second).getQuantity()).isEqualByComparingTo("10.0000");
            assertThat(movementCount(wh1, second)).isEqualTo(1);

            // 单据仍是草稿、明细仍在 —— 用户修掉冲突行之后可以直接重试
            assertThat(statusOf(id)).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_inventory_transfer_item WHERE transfer_id = ? AND deleted = FALSE",
                    Integer.class, id)).isEqualTo(2);

            // 失败原因是那一行，不是别的东西坏了：改成可行数量后整单可以正常发出
            transferService.update(id, twoLineForm(wh1, wh2, first, "5.0000", second, "10.0000"));
            transferService.ship(id);

            assertThat(statusOf(id)).isEqualTo("SHIPPED");
            assertThat(balanceRow(wh1, first).getQuantity()).isEqualByComparingTo("5.0000");
            assertThat(balanceRow(wh1, second).getQuantity()).isEqualByComparingTo("0.0000");
            // 发出只动源仓，目标仓要等收货
            assertThat(balanceRow(wh2, first)).isNull();
        }
    }

    @Nested
    @DisplayName("盘点 Excel 导入（PG IT）")
    class ScmStocktakeImportPgIT extends ScmW6PgITBase {
        @Autowired
        private InventoryStocktakeImportService importService;

        @Autowired
        private InventoryStocktakeService stocktakeService;

        // ------------------------------------------------------------------
        // 夹具：把一个 (仓库, SKU) 备货成指定账面量
        // ------------------------------------------------------------------

        /**
         * 本类独占的仓库：模板校验码按整仓出，规模必须只由本用例自己决定。
         *
         * <p>JUnit 5 默认逐方法新建实例，所以这个字段天然每个用例重新求值；基类 {@code @Transactional}
         * 逐方法回滚，仓库行连同它的余额与流水都不会留到下一个用例。
         */
        private Long exclusiveWarehouseId;

        @Override
        protected Long fixtureWarehouseId() {
            if (exclusiveWarehouseId == null) {
                exclusiveWarehouseId = newWarehouse("stocktake-import");
            }
            return exclusiveWarehouseId;
        }

        private Object[] stocked(String suffix, String quantity) {
            Long skuId = newSkuOfType(suffix, "NON_STANDARD", "ON_SHELF");
            W6Fixture fixture = inboundFixture(suffix, skuId, quantity);
            confirmReceipt(fixture.receipt().getId(), quantity);
            return new Object[]{fixtureWarehouseId(), skuId};
        }

        private String skuCode(Long skuId) {
            return jdbc.queryForObject("SELECT sku_code FROM xsy_product_sku WHERE id = ?", String.class, skuId);
        }

        private interface SheetEditor {
            void edit(XSSFSheet sheet);
        }

        private MultipartFile editedFile(byte[] template, SheetEditor editor) throws IOException {
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template))) {
                XSSFSheet sheet = workbook.getSheetAt(0);
                editor.edit(sheet);
                try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    workbook.write(out);
                    return new MockMultipartFile("file", "stocktake.xlsx",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            out.toByteArray());
                }
            }
        }

        /**
         * 给每个数据行填实盘量：实盘 = {@code actualFor(该行SKU编码, 该行导出时的账面数据)}。
         * 「实盘 = 导出时的账面数据」即差异 0，用来把非目标行填成不影响确认的中性行。
         */
        private void fillActual(XSSFSheet sheet, BiFunction<String, String, String> actualFor) {
            for (int row = 1; row <= sheet.getLastRowNum(); row++) {
                var dataRow = sheet.getRow(row);
                if (dataRow == null) {
                    continue;
                }
                var skuCell = dataRow.getCell(1);
                var bookCell = dataRow.getCell(5);
                String sku = skuCell == null ? null : skuCell.getStringCellValue();
                String book = bookCell == null ? "0" : bookCell.getStringCellValue();
                dataRow.createCell(6).setCellValue(actualFor.apply(sku, book));
            }
        }

        /** 全部行填同一合法实盘：只关心解析之后的凭证 / 来源 / 重复判定，不关心差异落地。 */
        private void fillUniform(XSSFSheet sheet, String actual) {
            fillActual(sheet, (sku, book) -> actual);
        }

        private static Set<String> codes(InventoryStocktakeImportResultVO result) {
            Set<String> codes = new HashSet<>();
            result.getErrors().forEach(error -> codes.add(error.getCode()));
            return codes;
        }

        private List<Map<String, Object>> stocktakeMovements(Long wh, Long sku) {
            return movementsOf(wh, sku).stream()
                    .filter(m -> String.valueOf(m.get("movement_type")).startsWith("STOCKTAKE_"))
                    .toList();
        }

        private String key() {
            return prefix + ":import:" + UUID.randomUUID();
        }

        // ------------------------------------------------------------------
        // 成功路径：导入只建草稿，确认才动库存
        // ------------------------------------------------------------------

        @Test
        @DisplayName("导入填好的模板 → 建草稿且完全不改库存；随后确认才写盘亏流水")
        void importCreatesDraftWithoutTouchingStockAndConfirmIsTheOnlyWriter() throws Exception {
            Object[] s = stocked("si1", "10.0000");
            Long wh = (Long) s[0];
            Long sku = (Long) s[1];
            String targetCode = skuCode(sku);

            var template = importService.buildTemplate(wh);
            // 目标行填实盘 8（差异 −2），其余行填各自导出时的账面数据（差异 0）。
            var file = editedFile(template, sheet ->
                    fillActual(sheet, (code, book) -> targetCode.equals(code) ? "8.0000" : book));

            var result = importService.importFile(file, key());

            assertThat(result.getTotalErrors()).isZero();
            assertThat(result.getStocktakeId()).isNotNull();
            assertThat(result.isReplayed()).isFalse();

            // 导入只建草稿：目标 SKU 账面量没变，也没产生任何盘点流水。
            assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("10.0000");
            assertThat(stocktakeMovements(wh, sku)).isEmpty();

            // 确认才是库存的唯一写入者：差异 = 实盘 8 − 导出时的账面数据 10 = −2，施加到当前账面 10 → 8。
            stocktakeService.confirm(result.getStocktakeId());
            assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("8.0000");
            var movements = stocktakeMovements(wh, sku);
            assertThat(movements).hasSize(1);
            assertThat(movements.getFirst().get("movement_type")).isEqualTo("STOCKTAKE_LOSS");
        }

        @Test
        @DisplayName("同一 Idempotency-Key 重复导入 → 命中重放，返回同一张草稿而非第二张")
        void repeatedImportWithSameKeyReplaysSingleDraft() throws Exception {
            Object[] s = stocked("si2", "10.0000");
            Long wh = (Long) s[0];
            String targetCode = skuCode((Long) s[1]);

            var template = importService.buildTemplate(wh);
            var file = editedFile(template, sheet ->
                    fillActual(sheet, (code, book) -> targetCode.equals(code) ? "8.0000" : book));
            var idempotencyKey = key();

            var first = importService.importFile(file, idempotencyKey);
            var second = importService.importFile(file, idempotencyKey);

            assertThat(first.getTotalErrors()).isZero();
            assertThat(first.isReplayed()).isFalse();
            assertThat(second.isReplayed()).isTrue();
            assertThat(second.getStocktakeId()).isEqualTo(first.getStocktakeId());
        }

        // ------------------------------------------------------------------
        // 整批拒绝：不产生任何草稿
        // ------------------------------------------------------------------

        @Test
        @DisplayName("实盘留空（空白≠0）→ BLANK_ACTUAL，整批拒绝且不建草稿")
        void blankActualRejectsWholeBatch() throws Exception {
            Object[] s = stocked("si3", "10.0000");
            Long wh = (Long) s[0];

            // 原样导回未填实盘的模板（第 6 列保持为空）。
            var template = importService.buildTemplate(wh);
            var file = editedFile(template, sheet -> {
            });

            var result = importService.importFile(file, key());

            assertThat(codes(result)).contains("BLANK_ACTUAL");
            assertThat(result.getStocktakeId()).isNull();
            assertThat(balanceRow(wh, (Long) s[1]).getQuantity()).isEqualByComparingTo("10.0000");
        }

        @Test
        @DisplayName("篡改模板校验码 → CREDENTIAL_INVALID，整批拒绝")
        void tamperedCredentialIsRejected() throws Exception {
            Object[] s = stocked("si4", "10.0000");
            Long wh = (Long) s[0];

            var template = importService.buildTemplate(wh);
            var file = editedFile(template, sheet -> {
                fillUniform(sheet, "8.0000");
                for (int row = 1; row <= sheet.getLastRowNum(); row++) {
                    var dataRow = sheet.getRow(row);
                    if (dataRow != null) {
                        dataRow.getCell(0).setCellValue(dataRow.getCell(0).getStringCellValue() + "x");
                    }
                }
            });

            var result = importService.importFile(file, key());

            assertThat(codes(result)).contains("CREDENTIAL_INVALID");
            assertThat(result.getStocktakeId()).isNull();
        }

        @Test
        @DisplayName("把来源行的 SKU 换成别的编码 → 同时报 SOURCE_UNKNOWN 与 SOURCE_MISSING，整批拒绝")
        void replacingSourceRowIsRejected() throws Exception {
            Object[] s = stocked("si5", "10.0000");
            Long wh = (Long) s[0];

            var template = importService.buildTemplate(wh);
            var file = editedFile(template, sheet -> {
                fillUniform(sheet, "8.0000");
                sheet.getRow(1).getCell(1).setCellValue("REPLACED-9999");
            });

            var result = importService.importFile(file, key());

            // 被换上的编码不在保存的数据来源集合内（SOURCE_UNKNOWN），原来源行随之缺失（SOURCE_MISSING）。
            assertThat(codes(result)).contains("SOURCE_UNKNOWN", "SOURCE_MISSING");
            assertThat(result.getStocktakeId()).isNull();
        }

        @Test
        @DisplayName("同一 SKU 在文件里出现两次 → DUPLICATE_SKU，整批拒绝")
        void duplicateSkuRowIsRejected() throws Exception {
            Object[] s = stocked("si6", "10.0000");
            Long wh = (Long) s[0];

            var template = importService.buildTemplate(wh);
            var file = editedFile(template, sheet -> {
                fillUniform(sheet, "8.0000");
                var first = sheet.getRow(1);
                var duplicate = sheet.createRow(sheet.getLastRowNum() + 1);
                duplicate.createCell(0).setCellValue(first.getCell(0).getStringCellValue());
                duplicate.createCell(1).setCellValue(first.getCell(1).getStringCellValue());
                duplicate.createCell(6).setCellValue("8.0000");
            });

            var result = importService.importFile(file, key());

            assertThat(codes(result)).contains("DUPLICATE_SKU");
            assertThat(result.getStocktakeId()).isNull();
        }

        // ------------------------------------------------------------------
        // 核心裁决：版本漂移必须整批拒绝（不接受「先校验再保存」竞态）
        // ------------------------------------------------------------------

        @Test
        @DisplayName("导出后余额版本自增 → SNAPSHOT_STALE 整批拒绝，即便当前账面恰好等于实盘")
        void versionDriftRejectsWholeBatch() throws Exception {
            Object[] s = stocked("si7", "10.0000");
            Long wh = (Long) s[0];
            Long sku = (Long) s[1];

            // 导出保存的数据：账面 10、版本 v。
            var template = importService.buildTemplate(wh);

            // 保存的数据之后出库 2 → 账面 8、版本 v+1。填的实盘恰好等于「当前账面 8」，
            // 若实现按数量比较就会误放行；按版本判定才能挡住这个竞态。
            inventoryCommandService.postSalesOutbound(new InventoryOutboundFact(
                    wh, sku, 0L, 930001L, new BigDecimal("2.0000"), null,
                    OffsetDateTime.now(), "test:1"));
            assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("8.0000");

            var file = editedFile(template, sheet -> fillUniform(sheet, "8.0000"));
            var result = importService.importFile(file, key());

            assertThat(codes(result)).contains("SNAPSHOT_STALE");
            assertThat(result.getStocktakeId()).isNull();
            // 漂移拒绝后目标库存原样不动（仍是出库后的 8），也没有任何盘点流水。
            assertThat(balanceRow(wh, sku).getQuantity()).isEqualByComparingTo("8.0000");
            assertThat(stocktakeMovements(wh, sku)).isEmpty();
        }
    }

    @Nested
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("W9 包装码并发（PG IT，无外层事务）")
    class ScmTraceUnitConcurrencyIT extends ScmW6PgITBase {
        private static final long TIMEOUT_SECONDS = 60;

        @Autowired
        private TraceUnitService traceUnitService;

        @Autowired
        private TraceCorrectionService traceCorrectionService;

        @Autowired
        private com.xsy.scm.inventory.service.InventoryFulfillmentService fulfillmentService;

        @Autowired
        private org.springframework.transaction.PlatformTransactionManager transactionManager;

        private Long lotWarehouseId;

        @BeforeEach
        void createLotWarehouse() {
            WarehouseAddForm form = new WarehouseAddForm();
            form.setName(prefix + "并发批次仓");
            form.setLotTrackingEnabled(true);
            lotWarehouseId = warehouseService.create(form);
        }

        @Override
        protected Long fixtureWarehouseId() {
            return lotWarehouseId;
        }

        @Override
        protected void evictMybatisCache() {
            // 无外层事务 → 每次 DAO 调用都是新 session → 一级缓存天然为空
        }

        @Test
        void dispatchAndBindingSerializeOnTheOrderItem() throws Exception {
            Long skuId = newOnShelfSku("CLOT-LOCK");
            W6Fixture fixture = inboundFixture("CLOT-LOCK", skuId, "10.0000");
            confirmWithLots(fixture.receipt().getId(), "10.0000", "10.0000");
            var unit = traceUnitService.issue(issueForm(lotIdOf(skuId), 1, "1.0000"), "W9 lock").getFirst();
            Long orderId = confirmedSalesOrder(newCustomer(), skuId, "10.0000", "10.0000");
            sortingCompletedFor(orderId);
            Long itemId = jdbc.queryForObject("SELECT id FROM xsy_sorting_task_item WHERE sales_order_id=?",
                Long.class, orderId);
            Long orderItemId = confirmedSalesOrderItemId(orderId);
            var command = new com.xsy.scm.inventory.service.InventoryFulfillmentService.Command(
                orderId, lotWarehouseId, java.time.OffsetDateTime.now(), "W9 dispatch", List.of(
                    new com.xsy.scm.inventory.service.InventoryFulfillmentService.Line(orderId, orderItemId, skuId,
                        new BigDecimal("1.0000"), "kg", new BigDecimal("1.0000"))));
            var form = new TraceUnitBindForm();
            form.setUnitCode(unit.getUnitCode());
            form.setSortingTaskItemId(itemId);
            CountDownLatch dispatched = new CountDownLatch(1);
            CountDownLatch commit = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<?> outbound = pool.submit(withOperator(() ->
                    new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                        .executeWithoutResult(status -> {
                            fulfillmentService.dispatchOutbound(command);
                            dispatched.countDown();
                            try {
                                if (!commit.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException("等待测试释放发货事务超时");
                                }
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(exception);
                            }
                        })));
                assertThat(dispatched.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
                Future<?> binding = pool.submit(withOperator(() -> traceUnitService.bind(form, "W9 late bind")));
                // 发货尚未提交时绑定必须等待；提交后必须复核出库，不能使用原来保存的资料通过。
                assertThatThrownBy(() -> binding.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(java.util.concurrent.TimeoutException.class);
                commit.countDown();
                outbound.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                assertThatThrownBy(() -> binding.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(ScmBusinessException.class)
                    .satisfies(error -> assertThat(((ScmBusinessException) error.getCause()).getErrorCode().getCode())
                        .isEqualTo(41526));
                assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_trace_unit_binding WHERE unit_id=?",
                    Integer.class, unit.getId())).isZero();
            } finally {
                commit.countDown();
                pool.shutdownNow();
            }
        }

        private PurchaseReceiptVO confirmWithLots(Long receiptId, String quantity, String lotQuantity) {
            PurchaseReceiptVO current = reloadReceipt(receiptId);
            PurchaseReceiptItemVO line = current.getItems().getFirst();
            PurchaseReceiptConfirmForm.Item item = receiptLine(line.getId(), line.getVersion(), quantity);
            PurchaseReceiptConfirmForm.Lot lot = new PurchaseReceiptConfirmForm.Lot();
            lot.setSourceLotNo("CONC-" + quantity);
            lot.setQuantity(lotQuantity);
            item.setLots(List.of(lot));
            return purchaseReceiptService.confirm(confirmForm(current.getId(), current.getVersion(), item),
                    prefix + ":conc:" + UUID.randomUUID());
        }

        private Long lotIdOf(Long skuId) {
            return jdbc.queryForObject("SELECT id FROM xsy_trace_lot WHERE sku_id = ? ORDER BY id DESC LIMIT 1",
                    Long.class, skuId);
        }

        private TraceUnitIssueForm issueForm(Long lotId, int count, String perPackage) {
            TraceUnitIssueForm form = new TraceUnitIssueForm();
            form.setLotId(lotId);
            form.setPackageType("BAG");
            form.setPackageCount(count);
            form.setQuantityPerPackage(perPackage);
            return form;
        }

        /**
         * 工作线程没有父线程的登录上下文（{@code XsyRequestUtil} 是 ThreadLocal），
         * 而数据范围解析会因「取不到登录员工」校验失败时拒绝操作 —— 必须在线程内重新挂身份。
         */
        private Runnable withOperator(Runnable task) {
            return () -> {
                RequestEmployee employee = new RequestEmployee();
                employee.setEmployeeId(1L);
                employee.setActualName("W9 conc");
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                employee.setAdministratorFlag(true);
                XsyRequestUtil.setRequestUser(employee);
                try {
                    task.run();
                } finally {
                    XsyRequestUtil.remove();
                }
            };
        }

        /** 并发跑两个动作，返回各自的结果（成功为 null，失败为异常）。 */
        private List<Throwable> runConcurrently(Runnable first, Runnable second) throws Exception {
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            List<Throwable> failures = new ArrayList<>();
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (Runnable task : List.of(withOperator(first), withOperator(second))) {
                    futures.add(pool.submit(() -> {
                        start.await();
                        try {
                            task.run();
                        } catch (Throwable error) {
                            synchronized (failures) {
                                failures.add(error);
                            }
                        }
                        return null;
                    }));
                }
                start.countDown();
                for (Future<?> future : futures) {
                    future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }
            } finally {
                pool.shutdownNow();
            }
            return failures;
        }

        @Test
        @DisplayName("#1 两事务同时发码：只能成功一个，发码总量不超过批次余额")
        void concurrentIssueCannotExceedBalance() throws Exception {
            Long skuId = newOnShelfSku("CLOT1");
            W6Fixture fx = inboundFixture("CLOT1", skuId, "10.0000");
            confirmWithLots(fx.receipt().getId(), "10.0000", "10.0000");
            Long lotId = lotIdOf(skuId);

            List<Throwable> failures = runConcurrently(
                    () -> traceUnitService.issue(issueForm(lotId, 7, "1.0000"), "W9 conc A"),
                    () -> traceUnitService.issue(issueForm(lotId, 7, "1.0000"), "W9 conc B"));

            // 恰好一个成功：另一个必须被 41504 拒绝
            assertThat(failures).hasSize(1);
            assertThat(failures.getFirst()).isInstanceOf(ScmBusinessException.class);
            assertThat(((ScmBusinessException) failures.getFirst()).getErrorCode().getCode()).isEqualTo(41504);

            BigDecimal issued = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(quantity), 0) FROM xsy_trace_unit WHERE lot_id = ?", BigDecimal.class, lotId);
            assertThat(issued).isLessThanOrEqualTo(new BigDecimal("10.0000"));
            assertThat(issued).isEqualByComparingTo("7.0000");
        }

        @Test
        @DisplayName("#2 绑定与作废并发：只能成功一个，且不会出现「已作废的码挂着绑定」")
        void concurrentBindAndVoidCannotBothSucceed() throws Exception {
            Long skuId = newOnShelfSku("CLOT2");
            W6Fixture fx = inboundFixture("CLOT2", skuId, "10.0000");
            confirmWithLots(fx.receipt().getId(), "10.0000", "10.0000");
            Long lotId = lotIdOf(skuId);
            String unitCode = traceUnitService.issue(issueForm(lotId, 1, "1.0000"), "W9 conc").getFirst().getUnitCode();

            Long customerId = newCustomer();
            Long salesOrderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
            sortingCompletedFor(salesOrderId);
            Long itemId = jdbc.queryForObject(
                    "SELECT id FROM xsy_sorting_task_item WHERE sales_order_id = ? AND sku_id = ? "
                            + "ORDER BY id DESC LIMIT 1", Long.class, salesOrderId, skuId);

            TraceUnitBindForm bind = new TraceUnitBindForm();
            bind.setUnitCode(unitCode);
            bind.setSortingTaskItemId(itemId);
            TraceUnitVoidForm voidForm = new TraceUnitVoidForm();
            voidForm.setUnitCode(unitCode);
            voidForm.setReason("并发作废");

            List<Throwable> failures = runConcurrently(
                    () -> traceUnitService.bind(bind, "W9 conc bind"),
                    () -> traceUnitService.voidUnit(voidForm, "W9 conc void"));

            assertThat(failures).hasSize(1);
            int code = ((ScmBusinessException) failures.getFirst()).getErrorCode().getCode();
            assertThat(code).isIn(41512, 41511);

            // 终态必须自洽：要么「有效 + 已绑定」，要么「已作废 + 无绑定」
            String status = jdbc.queryForObject("SELECT status FROM xsy_trace_unit WHERE unit_code = ?", String.class,
                    unitCode);
            Integer bindings = jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_trace_unit_binding b JOIN xsy_trace_unit u ON u.id = b.unit_id "
                            + "WHERE u.unit_code = ?", Integer.class, unitCode);
            if ("VOID".equals(status)) {
                assertThat(bindings).as("已作废的码不能留下绑定").isZero();
            } else {
                assertThat(status).isEqualTo("ACTIVE");
                assertThat(bindings).as("绑定成功的码必须有效").isEqualTo(1);
            }
        }

        @Test
        @DisplayName("同一码并发绑定：后端共用行锁保证唯一有效绑定")
        void concurrentBindPreservesSingleActiveBinding() throws Exception {
            Long skuId = newOnShelfSku("CLOT4");
            W6Fixture fx = inboundFixture("CLOT4", skuId, "10.0000");
            confirmWithLots(fx.receipt().getId(), "10.0000", "10.0000");
            Long lotId = lotIdOf(skuId);
            var unit = traceUnitService.issue(issueForm(lotId, 1, "1.0000"), "W9 sql guard").getFirst();
            Long customerId = newCustomer();
            Long salesOrderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
            sortingCompletedFor(salesOrderId);
            Long itemId = jdbc.queryForObject(
                    "SELECT id FROM xsy_sorting_task_item WHERE sales_order_id = ? AND sku_id = ? "
                            + "ORDER BY id DESC LIMIT 1", Long.class, salesOrderId, skuId);

            TraceUnitBindForm form = new TraceUnitBindForm();
            form.setUnitCode(unit.getUnitCode());
            form.setSortingTaskItemId(itemId);
            Runnable directInsert = () -> traceUnitService.bind(form, "并发绑定");
            List<Throwable> failures = runConcurrently(directInsert, directInsert);
            assertThat(failures).hasSize(1);
            Integer active = jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_trace_unit_binding b "
                            + "WHERE b.unit_id = ? AND NOT EXISTS "
                            + "(SELECT 1 FROM xsy_trace_unit_unbind ub WHERE ub.binding_id = b.id)",
                    Integer.class, unit.getId());
            assertThat(active).isEqualTo(1);
        }

        @Test
        @DisplayName("已绑定包装码禁止作废；不存在匹配更正申请时禁止解绑")
        void directSqlCannotVoidBoundUnit() {
            Long skuId = newOnShelfSku("CLOT5");
            W6Fixture fx = inboundFixture("CLOT5", skuId, "10.0000");
            confirmWithLots(fx.receipt().getId(), "10.0000", "10.0000");
            Long lotId = lotIdOf(skuId);
            String code = traceUnitService.issue(issueForm(lotId, 1, "1.0000"), "W9 void guard")
                    .getFirst().getUnitCode();
            Long customerId = newCustomer();
            Long salesOrderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
            sortingCompletedFor(salesOrderId);
            Long itemId = jdbc.queryForObject(
                    "SELECT id FROM xsy_sorting_task_item WHERE sales_order_id = ? AND sku_id = ? "
                            + "ORDER BY id DESC LIMIT 1", Long.class, salesOrderId, skuId);
            TraceUnitBindForm bind = new TraceUnitBindForm();
            bind.setUnitCode(code);
            bind.setSortingTaskItemId(itemId);
            traceUnitService.bind(bind, "W9 void guard");

            TraceUnitVoidForm voidForm = new TraceUnitVoidForm();
            voidForm.setUnitCode(code);
            voidForm.setReason("已绑定码不能作废");
            assertThatThrownBy(() -> traceUnitService.voidUnit(voidForm, "独立测试操作人"))
                    .isInstanceOf(ScmBusinessException.class);
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM xsy_trace_unit WHERE unit_code = ?", String.class, code))
                    .isEqualTo("ACTIVE");

            Long unitId = jdbc.queryForObject("SELECT id FROM xsy_trace_unit WHERE unit_code = ?",
                    Long.class, code);
            Long bindingId = jdbc.queryForObject("SELECT id FROM xsy_trace_unit_binding WHERE unit_id = ?",
                    Long.class, unitId);
            var decision = new com.xsy.scm.trace.domain.form.TraceCorrectionDecisionForm();
            decision.setId(Long.MAX_VALUE);
            decision.setVersion(0);
            assertThatThrownBy(() -> traceCorrectionService.approve(decision, "独立审批人"))
                    .isInstanceOf(ScmBusinessException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_trace_unit_unbind WHERE binding_id=?",
                Integer.class, bindingId)).isZero();
        }

        @Test
        @DisplayName("正式出库之后，后端拒绝解绑或补绑，原有效绑定不变")
        void outboundPreventsCorrectionAndLateRebinding() {
            Long skuId = newOnShelfSku("CLOT6");
            W6Fixture fx = inboundFixture("CLOT6", skuId, "10.0000");
            confirmWithLots(fx.receipt().getId(), "10.0000", "10.0000");
            Long lotId = lotIdOf(skuId);
            var units = traceUnitService.issue(issueForm(lotId, 2, "1.0000"), "W9 outbound guard");
            Long customerId = newCustomer();
            Long salesOrderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
            sortingCompletedFor(salesOrderId);
            Long itemId = jdbc.queryForObject(
                    "SELECT id FROM xsy_sorting_task_item WHERE sales_order_id = ? AND sku_id = ? "
                            + "ORDER BY id DESC LIMIT 1", Long.class, salesOrderId, skuId);
            TraceUnitBindForm bind = new TraceUnitBindForm();
            bind.setUnitCode(units.getFirst().getUnitCode());
            bind.setSortingTaskItemId(itemId);
            traceUnitService.bind(bind, "W9 bound");

            var apply = new com.xsy.scm.trace.domain.form.TraceCorrectionApplyForm();
            apply.setUnitCode(units.getFirst().getUnitCode());
            apply.setReason("发车前贴错码");
            var correction = traceCorrectionService.apply(apply, "W9 applicant");

            Long salesOrderItemId = jdbc.queryForObject(
                    "SELECT sales_order_item_id FROM xsy_sorting_task_item WHERE id = ?",
                    Long.class, itemId);
            Long outboundId = jdbc.queryForObject(
                    "INSERT INTO xsy_inventory_outbound"
                            + "(outbound_no, warehouse_id, status, confirmed_at, operator, created_by) "
                            + "VALUES (?, ?, 'CONFIRMED', CURRENT_TIMESTAMP, 'W9 sql', 'W9 sql') RETURNING id",
                    Long.class, "W9SQL-" + UUID.randomUUID(), lotWarehouseId);
            jdbc.update("INSERT INTO xsy_inventory_outbound_item"
                            + "(outbound_id, sku_id, quantity, sales_order_id, sales_order_item_id, created_by) "
                            + "VALUES (?, ?, 1, ?, ?, 'W9 sql')",
                    outboundId, skuId, salesOrderId, salesOrderItemId);

            var decision = new com.xsy.scm.trace.domain.form.TraceCorrectionDecisionForm();
            decision.setId(correction.getId());
            decision.setVersion(correction.getVersion());
            assertThatThrownBy(() -> traceCorrectionService.approve(decision, "W9 approver"))
                    .isInstanceOf(ScmBusinessException.class)
                    .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode())
                            .isEqualTo(41525));

            Long bindingId = jdbc.queryForObject(
                    "SELECT id FROM xsy_trace_unit_binding WHERE unit_id = ?",
                    Long.class, units.getFirst().getId());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_trace_unit_unbind WHERE binding_id=?",
                Integer.class, bindingId)).isZero();

            TraceUnitBindForm lateBind = new TraceUnitBindForm();
            lateBind.setUnitCode(units.get(1).getUnitCode());
            lateBind.setSortingTaskItemId(itemId);
            assertThatThrownBy(() -> traceUnitService.bind(lateBind, "W9 late"))
                    .isInstanceOf(ScmBusinessException.class)
                    .satisfies(error -> assertThat(((ScmBusinessException) error).getErrorCode().getCode())
                            .isEqualTo(41526));

            assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_trace_unit_binding WHERE unit_id=?",
                Integer.class, units.get(1).getId())).isZero();
        }

        @Test
        @DisplayName("#3 两枚码同时绑定同一分拣行：累计不得超过实拣量")
        void concurrentBindCannotExceedSortedQuantity() throws Exception {
            Long skuId = newOnShelfSku("CLOT3");
            W6Fixture fx = inboundFixture("CLOT3", skuId, "20.0000");
            confirmWithLots(fx.receipt().getId(), "20.0000", "20.0000");
            Long lotId = lotIdOf(skuId);
            var units = traceUnitService.issue(issueForm(lotId, 2, "6.0000"), "W9 conc");

            Long customerId = newCustomer();
            Long salesOrderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
            sortingCompletedFor(salesOrderId);
            Long itemId = jdbc.queryForObject(
                    "SELECT id FROM xsy_sorting_task_item WHERE sales_order_id = ? AND sku_id = ? "
                            + "ORDER BY id DESC LIMIT 1", Long.class, salesOrderId, skuId);

            TraceUnitBindForm first = new TraceUnitBindForm();
            first.setUnitCode(units.get(0).getUnitCode());
            first.setSortingTaskItemId(itemId);
            TraceUnitBindForm second = new TraceUnitBindForm();
            second.setUnitCode(units.get(1).getUnitCode());
            second.setSortingTaskItemId(itemId);

            List<Throwable> failures = runConcurrently(
                    () -> traceUnitService.bind(first, "W9 conc bind A"),
                    () -> traceUnitService.bind(second, "W9 conc bind B"));

            // 每枚 6、实拣 10：只能成功一枚
            assertThat(failures).hasSize(1);
            assertThat(((ScmBusinessException) failures.getFirst()).getErrorCode().getCode()).isEqualTo(41509);

            BigDecimal bound = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(u.quantity), 0) FROM xsy_trace_unit_binding b "
                            + "JOIN xsy_trace_unit u ON u.id = b.unit_id WHERE b.sorting_task_item_id = ?",
                    BigDecimal.class, itemId);
            assertThat(bound).isLessThanOrEqualTo(new BigDecimal("10.0000"));
        }
    }

    @Nested
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("W9 包装码仓库范围（PG IT，无外层事务）")
    class ScmTraceUnitScopeIT extends ScmW6PgITBase {
        private static final long SCOPED_EMPLOYEE_ID = 9_900_001L;

        @Autowired
        private TraceUnitService traceUnitService;

        private Long warehouseA;

        private Long warehouseB;

        private Long fixtureWarehouse;

        @Override
        protected Long fixtureWarehouseId() {
            return fixtureWarehouse == null ? super.fixtureWarehouseId() : fixtureWarehouse;
        }

        /** 用给定批次登记确认收货：本类只造单批次行。 */
        private void confirmWithLots(Long receiptId, String quantity, String lotQuantity) {
            com.xsy.scm.purchase.domain.vo.PurchaseReceiptVO current = reloadReceipt(receiptId);
            com.xsy.scm.purchase.domain.vo.PurchaseReceiptItemVO line = current.getItems().getFirst();
            com.xsy.scm.purchase.domain.form.PurchaseReceiptConfirmForm.Item item =
                    receiptLine(line.getId(), line.getVersion(), quantity);
            com.xsy.scm.purchase.domain.form.PurchaseReceiptConfirmForm.Lot lot =
                    new com.xsy.scm.purchase.domain.form.PurchaseReceiptConfirmForm.Lot();
            lot.setSourceLotNo("SCOPE-" + lotQuantity);
            lot.setQuantity(lotQuantity);
            item.setLots(List.of(lot));
            purchaseReceiptService.confirm(confirmForm(current.getId(), current.getVersion(), item),
                    prefix + ":scope:" + java.util.UUID.randomUUID());
        }

        @Override
        protected void evictMybatisCache() {
            // 无外层事务 → 每次 DAO 调用都是新 session
        }

        @BeforeEach
        void createWarehouses() {
            warehouseA = lotWarehouse(prefix + "范围仓A");
            warehouseB = lotWarehouse(prefix + "范围仓B");
            jdbc.update("INSERT INTO xsy_employee_warehouse_scope (employee_id, warehouse_id, deleted_flag, create_time) "
                    + "VALUES (?, ?, FALSE, CURRENT_TIMESTAMP)", SCOPED_EMPLOYEE_ID, warehouseA);
        }

        @AfterEach
        void removeScopedEmployee() {
            XsyRequestUtil.remove();
        }

        private Long lotWarehouse(String name) {
            WarehouseAddForm form = new WarehouseAddForm();
            form.setName(name);
            form.setLotTrackingEnabled(true);
            return warehouseService.create(form);
        }

        /** 切到「只有 A 仓授权」的普通员工（非管理员，也没有全仓权限）。 */
        private void asScopedEmployee() {
            RequestEmployee employee = new RequestEmployee();
            employee.setEmployeeId(SCOPED_EMPLOYEE_ID);
            employee.setActualName("W9 范围员工");
            employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            employee.setAdministratorFlag(false);
            XsyRequestUtil.setRequestUser(employee);
        }

        private void asAdministrator() {
            RequestEmployee employee = new RequestEmployee();
            employee.setEmployeeId(1L);
            employee.setActualName("W9 IT");
            employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            employee.setAdministratorFlag(true);
            XsyRequestUtil.setRequestUser(employee);
        }

        /** 在指定仓库造一个批次并发出一个码，返回码值。 */
        private String unitIn(Long warehouseId, String tag) {
            asAdministrator();
            Long skuId = newOnShelfSku(tag);
            fixtureWarehouse = warehouseId;
            try {
                W6Fixture fx = inboundFixture(tag, skuId, "10.0000");
                confirmWithLots(fx.receipt().getId(), "10.0000", "10.0000");
            } finally {
                fixtureWarehouse = null;
            }
            Long lotId = jdbc.queryForObject("SELECT id FROM xsy_trace_lot WHERE sku_id = ? ORDER BY id DESC LIMIT 1",
                    Long.class, skuId);
            TraceUnitIssueForm form = new TraceUnitIssueForm();
            form.setLotId(lotId);
            form.setPackageType("BAG");
            form.setPackageCount(1);
            form.setQuantityPerPackage("1.0000");
            return traceUnitService.issue(form, "W9 scope").getFirst().getUnitCode();
        }

        @Test
        @DisplayName("#1 只有 A 仓授权的员工读不到 B 仓的码，也发不了码、作废不了")
        void scopedEmployeeCannotTouchOtherWarehouse() {
            String codeA = unitIn(warehouseA, "SCOPEA");
            String codeB = unitIn(warehouseB, "SCOPEB");
            Long lotB = jdbc.queryForObject(
                    "SELECT u.lot_id FROM xsy_trace_unit u WHERE u.unit_code = ?", Long.class, codeB);

            asScopedEmployee();

            // 历史记录查询：按码也读不到
            assertThatThrownBy(() -> traceUnitService.replay(codeB))
                    .isInstanceOf(ScmDataScopeException.class);
            // 列表：B 仓的码不出现在结果里
            TraceUnitQueryForm query = new TraceUnitQueryForm();
            query.setPageNum(1L);
            query.setPageSize(20L);
            List<String> visible = traceUnitService.query(query).getList().stream()
                    .map(TraceUnitVOUnitCode::of).toList();
            assertThat(visible).contains(codeA).doesNotContain(codeB);

            // 发码：跨仓发码必须被拒
            TraceUnitIssueForm issue = new TraceUnitIssueForm();
            issue.setLotId(lotB);
            issue.setPackageType("BAG");
            issue.setPackageCount(1);
            issue.setQuantityPerPackage("1.0000");
            assertThatThrownBy(() -> traceUnitService.issue(issue, "W9 scope"))
                    .isInstanceOf(ScmDataScopeException.class);

            // 作废：跨仓作废必须被拒
            TraceUnitVoidForm voidForm = new TraceUnitVoidForm();
            voidForm.setUnitCode(codeB);
            voidForm.setReason("越权尝试");
            assertThatThrownBy(() -> traceUnitService.voidUnit(voidForm, "W9 scope"))
                    .isInstanceOf(ScmDataScopeException.class);

            // 本仓仍然可读：范围是收窄，不是全关
            assertThat(traceUnitService.replay(codeA).getUnit().getUnitCode()).isEqualTo(codeA);
            assertThat(jdbc.queryForObject("SELECT status FROM xsy_trace_unit WHERE unit_code = ?", String.class, codeB))
                    .isEqualTo("ACTIVE");
        }

        /** 取码值的查询结果，避免在断言里写 lambda 取字段。 */
        private static final class TraceUnitVOUnitCode {
            private static String of(com.xsy.scm.trace.domain.vo.TraceUnitVO.Unit unit) {
                return unit.getUnitCode();
            }
        }

        @Test
        @DisplayName("#2 范围收窄后列表数量与仓库一致：B 仓的码对 A 仓员工不可见")
        void scopedQueryCountsOnlyAuthorizedWarehouse() {
            unitIn(warehouseA, "SCOPEA2");
            unitIn(warehouseB, "SCOPEB2");
            asScopedEmployee();

            TraceUnitQueryForm query = new TraceUnitQueryForm();
            query.setPageNum(1L);
            query.setPageSize(50L);
            var page = traceUnitService.query(query);
            assertThat(page.getList()).allSatisfy(unit -> assertThat(unit.getUnitCode()).isNotBlank());
            assertThat(page.getTotal()).isGreaterThanOrEqualTo(1L);
            assertThat(page.getList()).noneMatch(unit -> unit.getLotId() == null);
        }
    }

    @Nested
    class InventorySaleUnitConverterTest {
        @Test
        void oneBoxIsTenKilograms() {
            BigDecimal actual = InventorySaleUnitConverter.toStockQuantity(
                    new BigDecimal("3.0000"), "kg", new BigDecimal("10.0000"));
            assertThat(actual).isEqualByComparingTo("30.0000");
        }

        @Test
        void legacyWithoutSnapshotRetainsOriginalQuantity() {
            assertThat(InventorySaleUnitConverter.toStockQuantity(new BigDecimal("3.25"), null, null))
                    .isEqualByComparingTo("3.25");
        }

        @Test
        void incompleteOrInvalidSnapshotMustFail() {
            assertThatThrownBy(() -> InventorySaleUnitConverter.toStockQuantity(
                    new BigDecimal("2"), "kg", null)).isInstanceOf(ScmBusinessException.class);
            assertThatThrownBy(() -> InventorySaleUnitConverter.toStockQuantity(
                    new BigDecimal("2"), "kg", BigDecimal.ZERO)).isInstanceOf(ScmBusinessException.class);
        }

        @Test
        void returnAllocationConvertsStockBalanceBackToSaleUnitWithoutOverAllocating() {
            // 发出 30 kg（3 箱），先退回 10 kg（1 箱），剩 20 kg 只能再退 2 箱。
            BigDecimal availableStock = new BigDecimal("30.0000").subtract(new BigDecimal("10.0000"));
            BigDecimal availableSale = InventorySaleUnitConverter.availableSaleQuantity(
                    availableStock, "kg", new BigDecimal("10.0000"));
            assertThat(availableSale).isEqualByComparingTo("2.0000");
            assertThat(InventorySaleUnitConverter.toStockQuantity(
                    availableSale, "kg", new BigDecimal("10.0000"))).isEqualByComparingTo("20.0000");
        }

        @Test
        void returnAllocationDoesNotRoundUpFractionalSaleUnits() {
            assertThat(InventorySaleUnitConverter.availableSaleQuantity(
                    new BigDecimal("1.0000"), "kg", new BigDecimal("3.0000")))
                    .isEqualByComparingTo("0.3333");
            assertThat(InventorySaleUnitConverter.availableSaleQuantity(new BigDecimal("2"), null, null))
                    .isEqualByComparingTo("2");
        }

        @Test
        void neverRoundsInventoryFact() {
            assertThatThrownBy(() -> InventorySaleUnitConverter.toStockQuantity(
                    new BigDecimal("0.0001"), "kg", new BigDecimal("0.1234")))
                    .isInstanceOf(ScmBusinessException.class);
        }
    }

    @Nested
    @DisplayName("盘点时保存的数据密钥：非生产可用默认值，pre/prod 缺失或沿用默认值即启动失败")
    class StocktakeSnapshotSignerTest {
        /** 生产应当显式配置的私有密钥（任意非默认高熵值）。 */
        private static final String PRIVATE_KEY = "3f2b7c9d40a15e8b9c6d3f2a1b0c4d5e";

        private StocktakeSnapshotSigner signer(String secret, String profiles) {
            return new StocktakeSnapshotSigner(new ObjectMapper(), secret, profiles);
        }

        @ParameterizedTest(name = "{0} profile 下缺密钥必须启动失败")
        @ValueSource(strings = {"pre", "prod", "production", "prod,druid"})
        void rejectsBlankSecretInProduction(String profiles) {
            assertThatThrownBy(() -> signer(null, profiles)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> signer("   ", profiles)).isInstanceOf(IllegalStateException.class);
        }

        @ParameterizedTest(name = "{0} profile 下沿用仓库默认密钥必须启动失败")
        @ValueSource(strings = {"pre", "prod"})
        void rejectsRepositoryDefaultSecretInProduction(String profiles) {
            assertThatThrownBy(() -> signer(StocktakeSnapshotSigner.DEV_SECRET, profiles))
                    .isInstanceOf(IllegalStateException.class);
        }

        @ParameterizedTest(name = "{0} profile 下允许使用开发默认密钥")
        @ValueSource(strings = {"dev", "test", "dev,test"})
        void allowsDevSecretOutsideProduction(String profiles) {
            assertThatCode(() -> signer(StocktakeSnapshotSigner.DEV_SECRET, profiles)).doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "{0} profile 下显式私有密钥正常启动")
        @ValueSource(strings = {"pre", "prod"})
        void acceptsExplicitSecretInProduction(String profiles) {
            assertThatCode(() -> signer(PRIVATE_KEY, profiles)).doesNotThrowAnyException();
        }

        /**
         * 凭证要能被写进 Excel 单元格：POI 的硬上限是 32767 字符。
         *
         * <p>载荷为仓库里每条活跃余额带上 skuCode / 单位 / 账面量，且导出会把凭证写进<b>每一行</b>。
         * 实测 240 个 SKU 就把未压缩的 JSON 顶过上限，大仓库因此根本导不出盘点模板
         * （{@code ScmStocktakeImportPgIT} 整类 IllegalArgumentException）。
         * 这里钉住「充分压缩 + 原样往返」，删掉签名链上的 DEFLATE 会立刻变红。
         */
        @ParameterizedTest(name = "{0} 条余额的凭证仍在单元格上限内")
        @ValueSource(ints = {240, 2000})
        void credentialFitsExcelCellLimit(int size) {
            var entries = new java.util.ArrayList<StocktakeSnapshotSigner.Entry>();
            for (int i = 0; i < size; i++) {
                entries.add(new StocktakeSnapshotSigner.Entry("SKU-" + i, (long) i, (long) i + 10_000,
                        "kg", i, new java.math.BigDecimal("12.3456")));
            }
            var payload = new StocktakeSnapshotSigner.Payload("1.0", 7L, "1:1", 1_000L, 9_000L, entries);
            var signer = signer(PRIVATE_KEY, "prod");

            String token = signer.sign(payload);
            assertThat(token).as("凭证长度必须留在 POI 单元格上限内").hasSizeLessThan(32_767);
            assertThat(signer.verify(token, 2_000L).entries())
                    .hasSize(size)
                    .last().isEqualTo(entries.get(size - 1));
        }

        @Test
        @DisplayName("篡改压缩后的载荷同样过不了签名：DEFLATE 不是完整性手段，MAC 才是")
        void tamperedCompressedBodyStillFailsVerification() {
            var entries = new java.util.ArrayList<StocktakeSnapshotSigner.Entry>();
            for (int i = 0; i < 50; i++) {
                entries.add(new StocktakeSnapshotSigner.Entry("SKU-" + i, (long) i, (long) i,
                        "kg", i, new java.math.BigDecimal("1.0000")));
            }
            var signer = signer(PRIVATE_KEY, "prod");
            String token = signer.sign(new StocktakeSnapshotSigner.Payload("1.0", 7L, "1:1", 1_000L, 9_000L, entries));
            byte[] packed = java.util.Base64.getUrlDecoder().decode(token.substring(0, token.lastIndexOf('.')));
            packed[packed.length / 2] ^= 0x55;
            String forged = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(packed)
                    + token.substring(token.lastIndexOf('.'));
            assertThatThrownBy(() -> signer.verify(forged, 2_000L))
                    .isInstanceOf(StocktakeSnapshotSigner.SnapshotCredentialException.class);
        }
    }
}
