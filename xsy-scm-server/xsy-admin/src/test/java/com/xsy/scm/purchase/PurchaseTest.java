package com.xsy.scm.purchase;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xsy.scm.admin.module.system.login.domain.RequestEmployee;
import com.xsy.scm.base.common.code.UserErrorCode;
import com.xsy.scm.base.common.domain.PageParam;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.SystemEnvironment;
import com.xsy.scm.base.common.enumeration.UserTypeEnum;
import com.xsy.scm.base.common.exception.BusinessException;
import com.xsy.scm.base.common.util.XsyRequestUtil;
import com.xsy.scm.base.handler.GlobalExceptionHandler;
import com.xsy.scm.base.module.support.config.ConfigService;
import com.xsy.scm.base.module.support.config.domain.ConfigUpdateForm;
import com.xsy.scm.base.module.support.config.domain.ConfigVO;
import com.xsy.scm.common.ScmBaselineMigrationPgITBase;
import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.ScmW6PgITBase;
import com.xsy.scm.common.constant.ScmEnableStatusEnum;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.error.ScmErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.handler.ScmExceptionHandler;
import com.xsy.scm.common.idempotency.ScmIdempotencyRequestHasher;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.inventory.controller.InventoryBalanceController;
import com.xsy.scm.inventory.domain.ReserveInventoryFact;
import com.xsy.scm.inventory.domain.form.InventoryLossGainAddForm;
import com.xsy.scm.inventory.domain.form.InventoryLossGainAuditForm;
import com.xsy.scm.inventory.service.InventoryLossGainService;
import com.xsy.scm.inventory.service.InventoryReservationService;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;
import com.xsy.scm.purchase.constant.PurchaseConfigKey;
import com.xsy.scm.purchase.constant.PurchaseErrorCode;
import com.xsy.scm.purchase.constant.ScmPurchaseDemandStatusEnum;
import com.xsy.scm.purchase.constant.ScmPurchaseStatusEnum;
import com.xsy.scm.purchase.constant.ScmReceiptModeEnum;
import com.xsy.scm.purchase.constant.ScmReceiptStatusEnum;
import com.xsy.scm.purchase.controller.PurchaseDemandController;
import com.xsy.scm.purchase.controller.PurchaseInquiryController;
import com.xsy.scm.purchase.controller.PurchaseOrderController;
import com.xsy.scm.purchase.controller.PurchaseReceiptController;
import com.xsy.scm.purchase.dao.PurchaseAccountDao;
import com.xsy.scm.purchase.dao.PurchaseDemandAllocationDao;
import com.xsy.scm.purchase.dao.PurchaseDemandDao;
import com.xsy.scm.purchase.dao.PurchaseOrderDao;
import com.xsy.scm.purchase.dao.PurchaseOrderItemDao;
import com.xsy.scm.purchase.domain.entity.PurchaseDemandAllocationEntity;
import com.xsy.scm.purchase.domain.entity.PurchaseDemandEntity;
import com.xsy.scm.purchase.domain.entity.PurchaseOrderEntity;
import com.xsy.scm.purchase.domain.entity.PurchaseOrderItemEntity;
import com.xsy.scm.purchase.domain.form.PurchaseDemandAllocateForm;
import com.xsy.scm.purchase.domain.form.PurchaseDemandGenerateForm;
import com.xsy.scm.purchase.domain.form.PurchaseDemandQueryForm;
import com.xsy.scm.purchase.domain.form.PurchaseDemandSummaryPreviewForm;
import com.xsy.scm.purchase.domain.form.PurchaseInquiryForm;
import com.xsy.scm.purchase.domain.form.PurchaseOrderAddForm;
import com.xsy.scm.purchase.domain.form.PurchaseOrderCancelForm;
import com.xsy.scm.purchase.domain.form.PurchaseOrderDeleteForm;
import com.xsy.scm.purchase.domain.form.PurchaseOrderLockForm;
import com.xsy.scm.purchase.domain.form.PurchaseOrderQueryForm;
import com.xsy.scm.purchase.domain.form.PurchaseOrderReassignForm;
import com.xsy.scm.purchase.domain.form.PurchaseOrderShortCloseForm;
import com.xsy.scm.purchase.domain.form.PurchaseOrderUpdateForm;
import com.xsy.scm.purchase.domain.form.PurchaseOrderVersionForm;
import com.xsy.scm.purchase.domain.form.PurchasePriceForm;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptConfirmForm;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptCreateForm;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptItemWorkbenchQueryForm;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptPutawayForm;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptQueryForm;
import com.xsy.scm.purchase.domain.form.PurchaseShareCreateForm;
import com.xsy.scm.purchase.domain.form.PurchaseShareFeedbackForm;
import com.xsy.scm.purchase.domain.vo.PurchaseDemandSummaryVO;
import com.xsy.scm.purchase.domain.vo.PurchaseDemandVO;
import com.xsy.scm.purchase.domain.vo.PurchaseInquiryVO.Inquiry;
import com.xsy.scm.purchase.domain.vo.PurchaseOperationLogVO;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderAllocationVO;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderItemVO;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderVO;
import com.xsy.scm.purchase.domain.vo.PurchaseReceiptItemVO;
import com.xsy.scm.purchase.domain.vo.PurchaseReceiptItemWorkbenchVO;
import com.xsy.scm.purchase.domain.vo.PurchaseReceiptVO;
import com.xsy.scm.purchase.domain.vo.PurchaseSharePublicVO;
import com.xsy.scm.purchase.domain.vo.PurchaseShareVO;
import com.xsy.scm.purchase.job.PurchaseOrderAutoLockJob;
import com.xsy.scm.purchase.job.PurchasePriceAutoFillJob;
import com.xsy.scm.purchase.manager.PurchaseAmountCalculator;
import com.xsy.scm.purchase.manager.PurchaseDemandAllocator;
import com.xsy.scm.purchase.manager.PurchaseOrderAllocationChangeSet;
import com.xsy.scm.purchase.manager.PurchaseOrderItemChangeSet;
import com.xsy.scm.purchase.manager.PurchaseOrderStateMachine;
import com.xsy.scm.purchase.manager.PurchaseOrderValidator;
import com.xsy.scm.purchase.manager.PurchaseReceiptQuantityCalculator;
import com.xsy.scm.purchase.manager.PurchaseSnapshotFactory;
import com.xsy.scm.purchase.permission.PurchasePermission;
import com.xsy.scm.purchase.service.PurchaseDemandCalculationBatchService;
import com.xsy.scm.purchase.service.PurchaseDemandService;
import com.xsy.scm.purchase.service.PurchaseOrderExportSupport;
import com.xsy.scm.purchase.service.PurchaseOrderLockService;
import com.xsy.scm.purchase.service.PurchaseOrderService;
import com.xsy.scm.purchase.service.PurchasePriceService;
import com.xsy.scm.purchase.service.PurchaseQueryService;
import com.xsy.scm.purchase.service.PurchaseReceiptService;
import com.xsy.scm.purchase.service.PurchaseShareService;
import com.xsy.scm.purchase.support.PurchaseDemandSourceGuard;
import com.xsy.scm.purchase.support.PurchaseInquiryDecisionPolicy;
import com.xsy.scm.purchase.support.PurchaseInventoryContract;
import com.xsy.scm.purchase.support.PurchaseOwnerResolver;
import com.xsy.scm.supplier.constant.SupplierErrorCode;
import com.xsy.scm.supplier.domain.entity.SupplierEntity;
import com.xsy.scm.supplier.domain.entity.SupplierSkuEntity;
import com.xsy.scm.supplier.domain.form.SupplierQueryForm;
import com.xsy.scm.supplier.domain.vo.SupplierVO;
import com.xsy.scm.supplier.service.SupplierQueryService;
import com.xsy.scm.supplier.service.SupplierService;
import com.xsy.scm.supplier.service.SupplierSkuService;
import com.xsy.scm.warehouse.constant.ScmWarehouseStatusEnum;
import com.xsy.scm.warehouse.constant.WarehouseErrorCode;
import com.xsy.scm.warehouse.controller.WarehouseController;
import com.xsy.scm.warehouse.domain.entity.WarehouseEntity;
import com.xsy.scm.warehouse.service.WarehouseQueryService;
import com.xsy.scm.warehouse.service.WarehouseService;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.xsy.scm.purchase.service.PurchaseIdempotencyService;
import com.xsy.scm.purchase.manager.PurchaseReceiptPutawayGuard;
import com.xsy.scm.purchase.constant.ScmPurchaseOperationTypeEnum;

class PurchaseTest {

    @Nested
    class ScmPurchaseStatusEnumTest {
        private static final String MIGRATION = "/db/migration/modules/V1000.12__purchase.sql";

        private static final String WAREHOUSE = "/db/migration/modules/V1000.04__warehouse.sql";

        @Test
        @DisplayName("采购单 6 态与 ck_purchase_order_status 白名单一致")
        void purchaseOrderStatusMatchesDdl() {
            assertThat(names(ScmPurchaseStatusEnum.values()))
                    .isEqualTo(whitelist(MIGRATION, "ck_purchase_order_status"));
            assertThat(ScmPurchaseStatusEnum.values()).hasSize(6);
            assertThat(ScmPurchaseStatusEnum.ACCOUNTED.committed()).isTrue();
            assertThat(com.xsy.scm.purchase.manager.PurchaseOrderStateMachine.canTransition("SUBMITTED", "ACCOUNTED")).isTrue();
            assertThat(com.xsy.scm.purchase.manager.PurchaseOrderStateMachine.terminal("ACCOUNTED")).isTrue();
            assertThat(com.xsy.scm.purchase.manager.PurchaseOrderStateMachine.receivable("ACCOUNTED")).isFalse();
            assertThat(com.xsy.scm.purchase.manager.PurchaseOrderStateMachine.cancellable("ACCOUNTED")).isFalse();
            // 终态判定：RECEIVED / SHORT_CLOSED / CANCELLED 不再有任何后继
            assertThat(ScmPurchaseStatusEnum.RECEIVED).isNotNull();
            assertThat(ScmPurchaseStatusEnum.SHORT_CLOSED).isNotNull();
            assertThat(ScmPurchaseStatusEnum.CANCELLED).isNotNull();
        }

        @Test
        @DisplayName("需求 3 态 / 收货 2 态 / 仓库 2 态与各自白名单一致")
        void demandReceiptWarehouseStatusMatchDdl() {
            assertThat(names(ScmPurchaseDemandStatusEnum.values()))
                    .isEqualTo(whitelist(MIGRATION, "ck_purchase_demand_status"));
            assertThat(names(ScmReceiptStatusEnum.values()))
                    .isEqualTo(whitelist(MIGRATION,
                            "ck_purchase_receipt_status"));
            assertThat(names(ScmWarehouseStatusEnum.values()))
                    .isEqualTo(whitelist(WAREHOUSE, "ck_warehouse_status"));

            assertThat(ScmPurchaseDemandStatusEnum.values()).hasSize(3);
            assertThat(ScmReceiptStatusEnum.values()).hasSize(2);
            assertThat(ScmWarehouseStatusEnum.values()).hasSize(2);
        }

        @Test
        @DisplayName("操作日志 19 型与 ck_purchase_operation_log_type 一致，且归属 CHECK 覆盖全部 19 型")
        void operationTypeMatchesDdlAndOwnerConstraintCoversAll() {
            // 最终白名单与归属必须一并覆盖收货入库、采购价后置和锁单。
            Set<String> fromDdl = whitelist(MIGRATION, "ck_purchase_operation_log_type");
            assertThat(names(ScmPurchaseOperationTypeEnum.values())).isEqualTo(fromDdl);
            assertThat(ScmPurchaseOperationTypeEnum.values()).hasSize(19);

            // 归属由 operation_type 决定，因此每一种类型都必须出现在归属 CHECK 里，
            //      否则该类型的日志会既不被接受也不被拒绝（约束整体求值为 NULL 而放行）。
            String sql = migrationSql(MIGRATION);
            String owner = normalize(constraint(sql, "ck_purchase_operation_log_owner"));
            for (ScmPurchaseOperationTypeEnum type : ScmPurchaseOperationTypeEnum.values()) {
                assertThat(owner)
                        .as("归属 CHECK 未覆盖 operation_type=%s", type.name())
                        .contains("'" + type.name() + "'");
            }
            // 五个分支的分组规则固定：采购单类含采购价后置/锁单，收货分支含 RECEIPT_PUTAWAY
            assertThat(owner).contains("operation_type='DEMAND_GENERATE'");
            assertThat(owner).contains("operation_type='DEMAND_ALLOCATE'");
            assertThat(owner).contains("'CREATE','UPDATE','SUBMIT','CANCEL','SHORT_CLOSE','DELETE'");
            assertThat(owner).contains("'PRICE_FILL','PRICE_ADJUST','PRICE_AUTO_FILL',");
            assertThat(owner).contains("'RECEIPT_CREATE','RECEIPT_UPDATE','RECEIPT_CONFIRM','RECEIPT_DELETE','RECEIPT_PUTAWAY'");
        }

        // ------------------------------------------------------------------

        private static Set<String> names(Enum<?>[] values) {
            return Arrays.stream(values).map(Enum::name)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        /** 读取最终CHECK的完整表达式，兼容IN与PostgreSQL规范化后的ANY/CAST写法。 */
        private static Set<String> whitelist(String resource, String constraintName) {
            String expression = constraint(migrationSql(resource), constraintName);
            Set<String> values = new LinkedHashSet<>();
            var matcher = java.util.regex.Pattern.compile("'([A-Z][A-Z0-9_]*)'").matcher(expression);
            while (matcher.find()) {
                values.add(matcher.group(1));
            }
            assertThat(values).as("约束%s必须有白名单", constraintName).isNotEmpty();
            return values;
        }

        private static String constraint(String sql, String constraintName) {
            int start = sql.indexOf("CONSTRAINT " + constraintName + " CHECK");
            assertThat(start).as("缺少CHECK约束%s", constraintName).isGreaterThanOrEqualTo(0);
            int open = sql.indexOf('(', start);
            int depth = 0;
            boolean quoted = false;
            for (int index = open; index < sql.length(); index++) {
                char value = sql.charAt(index);
                if (value == '\'') {
                    if (quoted && index + 1 < sql.length() && sql.charAt(index + 1) == '\'') {
                        index++;
                    } else {
                        quoted = !quoted;
                    }
                } else if (!quoted && value == '(') {
                    depth++;
                } else if (!quoted && value == ')' && --depth == 0) {
                    return sql.substring(open, index + 1);
                }
            }
            throw new AssertionError("CHECK括号不完整: " + constraintName);
        }

        private static String normalize(String expression) {
            String compact = expression.replaceAll("\\s+", "");
            return compact.replaceAll("CAST\\(([^()]+?)AS(?:text|varchar)\\)", "$1")
                    .replace("::charactervarying", "").replace("::text", "")
                    .replace("(operation_type)", "operation_type");
        }

        private static String migrationSql(String resource) {
            try (InputStream in = ScmPurchaseStatusEnumTest.class.getResourceAsStream(resource)) {
                assertThat(in).as("classpath 上找不到 %s", resource).isNotNull();
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new IllegalStateException("读取 " + resource + " 失败", e);
            }
        }
    }

    @Nested
    @DisplayName("入库确认前置判定（单元）")
    class PurchaseReceiptPutawayGuardTest {
        @Test
        @DisplayName("仅 CONFIRMED + WAREHOUSE_CONFIRM + PENDING 放行")
        void onlyTheExactPreconditionIsAllowed() {
            assertThat(PurchaseReceiptPutawayGuard.putawayAllowed(
                    "CONFIRMED", "WAREHOUSE_CONFIRM", "PENDING")).isTrue();
        }

        @Test
        @DisplayName("其余组合一律拒绝")
        void everyOtherCombinationIsRejected() {
            // 待确认（即使方式与状态正确）
            assertThat(PurchaseReceiptPutawayGuard.putawayAllowed(
                    "PENDING", "WAREHOUSE_CONFIRM", "PENDING")).isFalse();
            // DIRECT 模式（确认即入库，不存在二次确认）
            assertThat(PurchaseReceiptPutawayGuard.putawayAllowed(
                    "CONFIRMED", "DIRECT", "PENDING")).isFalse();
            // 已入库（防重复处理/状态冲突，不能重复入库）
            assertThat(PurchaseReceiptPutawayGuard.putawayAllowed(
                    "CONFIRMED", "WAREHOUSE_CONFIRM", "COMPLETED")).isFalse();
            assertThat(PurchaseReceiptPutawayGuard.putawayAllowed(
                    "CONFIRMED", "DIRECT", "COMPLETED")).isFalse();
            // 空值（字段缺失）
            assertThat(PurchaseReceiptPutawayGuard.putawayAllowed(
                    null, "WAREHOUSE_CONFIRM", "PENDING")).isFalse();
            assertThat(PurchaseReceiptPutawayGuard.putawayAllowed(
                    "CONFIRMED", null, "PENDING")).isFalse();
            assertThat(PurchaseReceiptPutawayGuard.putawayAllowed(
                    "CONFIRMED", "WAREHOUSE_CONFIRM", null)).isFalse();
        }
    }

    @Nested
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("需求分配 vs 采购单取消：状态重读与锁序（PUR-01，PG IT，无外层事务）")
    class PurchaseAllocationCancelRacePgIT extends ScmW5PgITBase {
        @Override
        protected void evictMybatisCache() {
            // 无外层事务 → 每次 DAO 调用都是新 session → 一级缓存天然为空
        }

        /** 一次分配的请求体；{@code quantity} 用字符串，与 API 规则一致。 */
        private PurchaseDemandAllocateForm allocateForm(PurchaseDemandEntity demand, Long orderItemId,
                                                        String quantity) {
            PurchaseDemandAllocateForm form = new PurchaseDemandAllocateForm();
            form.setDemandId(demand.getId());
            form.setPurchaseOrderItemId(orderItemId);
            form.setQuantity(quantity);
            form.setVersion(demand.getVersion());
            // supplier / warehouse 是表单必填项（与需求上固定的那对一致），服务端还会再校验一致性
            form.setSupplierId(demand.getSupplierId());
            form.setWarehouseId(demand.getWarehouseId());
            return form;
        }

        private String key(String tag) {
            return prefix + ":" + tag + ":" + UUID.randomUUID();
        }

        /** 采购单唯一一行的 id（单行夹具，取第一行即可）。 */
        private Long firstItemId(Long orderId) {
            return reloadOrder(orderId).getItems().getFirst().getId();
        }

        private void loginAsAdmin(String name) {
            RequestEmployee employee = new RequestEmployee();
            employee.setEmployeeId(1L);
            employee.setActualName(name);
            employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            employee.setAdministratorFlag(true);
            XsyRequestUtil.setRequestUser(employee);
        }

        // ------------------------------------------------------------------
        // 1. 顺序复现：取消已提交之后，分配必须被拒（窗口 (C) 的确定性版本）
        // ------------------------------------------------------------------

        @Test
        @DisplayName("采购单已取消后再分配 → 40982，且不留下任何活动分配")
        void allocateIsRejectedOnAnAlreadyCancelledOrder() {
            Fixture fx = fixture("PARC1", "10.0000", "10.0000");
            PurchaseDemandEntity demand = generateDemandFor(fx.supplierId(), fx.salesOrderId());
            PurchaseOrderVO order = createDraftOrder("PARCPO1", fx.supplierId(), fx.skuId(), "10.0000", "3.0000");
            PurchaseOrderVO submitted = submitOrder(order.getId());
            Long itemId = firstItemId(order.getId());

            // 取消：释放分配 + 置 CANCELLED，全部提交（无外层事务）
            var cancel = new PurchaseOrderCancelForm();
            cancel.setId(submitted.getId());
            cancel.setVersion(reloadOrder(order.getId()).getVersion());
            cancel.setCancelReason("PUR-01 用例：取消后不允许再分配");
            purchaseOrderService.cancel(cancel, key("cancel"));

            assertThat(reloadOrder(order.getId()).getStatus()).isEqualTo("CANCELLED");

            // 取消后的一切分配尝试都必须被状态门禁拦住
            PurchaseDemandEntity current = reloadDemand(demand.getId());
            expectCode(() -> purchaseDemandService.allocate(allocateForm(current, itemId, "5.0000"), key("alloc")),
                    40982);

            assertThat(allocationsOf(order.getId()))
                    .as("已取消的采购单不得留下任何活动分配").isEmpty();
        }

        // ------------------------------------------------------------------
        // 2. 真并发：取消 vs 分配同时冲进去
        // ------------------------------------------------------------------

        @Test
        @DisplayName("取消与分配真并发：要么取消先赢（分配 40982）、要么分配先赢（取消仍把分配清干净），绝不死锁")
        void cancelAndAllocateRaceWithoutDeadlock() throws Exception {
            Fixture fx = fixture("PARC2", "10.0000", "10.0000");
            PurchaseDemandEntity demand = generateDemandFor(fx.supplierId(), fx.salesOrderId());
            PurchaseOrderVO order = createDraftOrder("PARCPO2", fx.supplierId(), fx.skuId(), "10.0000", "3.0000");
            PurchaseOrderVO submitted = submitOrder(order.getId());
            Long itemId = firstItemId(order.getId());

            var cancelFailure = new AtomicReference<Throwable>();
            var allocateFailure = new AtomicReference<Throwable>();
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var done = new CountDownLatch(2);

            // 取消侧：版本要在自己线程内回读（另一线程可能已改动单据）
            start("cancel-thread", () -> {
                var cancel = new PurchaseOrderCancelForm();
                cancel.setId(submitted.getId());
                cancel.setVersion(reloadOrder(order.getId()).getVersion());
                cancel.setCancelReason("PUR-01 并发用例");
                purchaseOrderService.cancel(cancel, key("race-cancel"));
            }, cancelFailure, ready, start, done);

            start("allocate-thread", () -> {
                PurchaseDemandEntity current = reloadDemand(demand.getId());
                purchaseDemandService.allocate(allocateForm(current, itemId, "5.0000"), key("race-alloc"));
            }, allocateFailure, ready, start, done);

            assertThat(ready.await(30, TimeUnit.SECONDS)).as("两个线程都要到达起跑线").isTrue();
            start.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS))
                    .as("两个事务必须在 60 秒内结束（超时即疑似死锁）").isTrue();

            // 无论谁先赢，最终不变量：单据 CANCELLED，且这条已取消的单上没有任何活动分配。
            assertThat(reloadOrder(order.getId()).getStatus())
                    .as("取消侧不应失败：它只锁自己的单据，与分配侧不构成环").isEqualTo("CANCELLED");
            assertThat(cancelFailure.get()).as("取消一侧不该抛异常：%s", cancelFailure.get()).isNull();

            // 分配侧只有两种合法结局：先赢（成功）或后输（40982）。
            if (allocateFailure.get() != null) {
                assertThat(allocateFailure.get())
                        .as("后到的分配必须是被状态门禁拒绝的业务异常")
                        .isInstanceOf(ScmBusinessException.class);
            }

            assertThat(allocationsOf(order.getId()))
                    .as("无论顺序如何，取消都必须把这一单上的分配清干净 —— "
                            + "这正是修复前「取消读到没有分配 → 早退 → 分配随后插进来」留下的漏网")
                    .isEmpty();

            PurchaseDemandEntity finalDemand = reloadDemand(demand.getId());
            assertThat(finalDemand.getAllocatedQuantity())
                    .as("需求 allocated_quantity 必须回落到 0：分配侧若要写入数据库必然发生在取消之前，取消必然把它释放掉")
                    .isEqualByComparingTo("0.0000");
            assertThat(finalDemand.getStatus())
                    .as("分配全部释放后需求退回 PENDING").isEqualTo("PENDING");
        }

        /**
         * 起一个线程：登录 → 到起跑线 → 等号令 → 跑 → 记异常。
         *
         * <p>与 {@code ScmFinancePaymentRacePgIT} 同一套协调，保持仓库内并发用例一致。
         */
        private void start(String name, Runnable body, AtomicReference<Throwable> failure,
                           CountDownLatch ready, CountDownLatch start, CountDownLatch done) {
            new Thread(() -> {
                loginAsAdmin(name);
                try {
                    ready.countDown();
                    start.await();
                    body.run();
                } catch (Throwable throwable) {
                    failure.set(throwable);
                } finally {
                    XsyRequestUtil.remove();
                    done.countDown();
                }
            }, name).start();
        }
    }

    @Nested
    @WebMvcTest({PurchaseDemandController.class, PurchaseOrderController.class,
            PurchaseReceiptController.class, WarehouseController.class})
    @AutoConfigureMockMvc(addFilters = false)
    @ContextConfiguration(classes = {PurchaseDemandController.class, PurchaseOrderController.class,
            PurchaseReceiptController.class, WarehouseController.class,
            ScmExceptionHandler.class, GlobalExceptionHandler.class})
    @DisplayName("W5 Web 层：42 端点 + 权限注解 + 定点数字符串序列化")
    class PurchaseOrderWebTest {
        @Autowired
        private MockMvc mvc;

        @MockitoBean
        private PurchaseDemandService demandService;

        @MockitoBean
        private PurchaseDemandCalculationBatchService demandBatchService;

        @MockitoBean
        private PurchaseOrderService orderService;

        @MockitoBean
        private PurchaseReceiptService receiptService;

        @MockitoBean
        private PurchaseQueryService queries;

        @MockitoBean
        private WarehouseService warehouseService;

        /** 采购价后置与锁单是独立服务，控制器构造器直接依赖，切片测试必须显式给桩。 */
        @MockitoBean
        private PurchasePriceService purchasePriceService;

        @MockitoBean
        private PurchaseOrderLockService purchaseOrderLockService;

        /**
         * {@code WarehouseController} 的构造器是 {@code (WarehouseService, WarehouseQueryService)}。
         *
         * <p>{@code @WebMvcTest} 只自动注册 Web 层切片 Bean，Service 一律不扫描 —— 漏掉这一个会让
         * 整个上下文加载失败，用例全部报 ApplicationContext failure threshold exceeded，
         * 而真正的错误信息（No qualifying bean of type WarehouseQueryService）藏在 Caused by 里。
         */
        @MockitoBean
        private WarehouseQueryService warehouseQueryService;

        @MockitoBean(name = "systemEnvironment")
        private SystemEnvironment environment;

        // ------------------------------------------------------------------
        // 1. 端点与权限注解
        // ------------------------------------------------------------------

        @Test
        @DisplayName("42 个端点全部声明 @SaCheckPermission，且权限码都在 scm: 命名空间内")
        void allEndpointsHaveNativePermissions() {
            // 计数只是登记端点个数；真正的不变量是下面逐方法的断言 —— 新增公开端点漏声明权限会立刻失败。
            // 改派归属是独立端点 /scm/purchase/reassign（权限码 scm:purchase:assign）：
            // 归属同时是数据范围依据，混在 /update 里就无法单独授权。
            // 采购价补价/改价与锁单/解锁各有独立端点与独立权限，因此比早期多 4 个。
            int count = 0;
            for (Class<?> controller : List.of(PurchaseDemandController.class, PurchaseOrderController.class,
                    PurchaseReceiptController.class, WarehouseController.class)) {
                for (Method method : controller.getDeclaredMethods()) {
                    if (!Modifier.isPublic(method.getModifiers())) {
                        continue;
                    }
                    SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
                    assertThat(permission)
                            .as("%s#%s 必须声明 @SaCheckPermission", controller.getSimpleName(), method.getName())
                            .isNotNull();
                    assertThat(permission.value()).isNotEmpty();
                    assertThat(permission.value()[0]).startsWith("scm:");
                    count++;
                }
            }
            assertThat(count).isEqualTo(41);
        }

        // ------------------------------------------------------------------
        // 2 / 3 / 4. 定点数字符串与 null 语义
        // ------------------------------------------------------------------

        @Test
        @DisplayName("采购单详情：金额输出 4 位定点字符串，null 保持缺席（不写成 0.0000）")
        void orderDetailUsesFourDecimalStringsAndKeepsNull() throws Exception {
            PurchaseOrderVO vo = new PurchaseOrderVO();
            vo.setId(1L);
            vo.setOrderNo("PO20260916000001");
            vo.setStatus("SUBMITTED");
            vo.setTotalAmount(new BigDecimal("18.6"));
            vo.setReceivedProgress(null);
            when(queries.orderDetail(1L)).thenReturn(vo);

            mvc.perform(get("/scm/purchase/detail/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.totalAmount").value("18.6000"))
                    .andExpect(jsonPath("$.data.receivedProgress").doesNotExist());
        }

        @Test
        @DisplayName("收货单详情：对账量输出 4 位定点字符串，实重 null 保持缺席")
        void receiptDetailUsesFourDecimalStringsAndKeepsNull() throws Exception {
            PurchaseReceiptVO vo = new PurchaseReceiptVO();
            vo.setId(7L);
            vo.setReceiptNo("PR20260916000001");
            vo.setStatus("PENDING");
            when(queries.receiptDetail(7L)).thenReturn(vo);

            mvc.perform(get("/scm/purchase/receipt/detail/7"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.receiptNo").value("PR20260916000001"));
        }

        @Test
        @DisplayName("需求返回体（allocate）：required / allocated / unallocated 都是 4 位定点字符串")
        void demandDetailUsesFourDecimalStrings() throws Exception {
            PurchaseDemandVO vo = new PurchaseDemandVO();
            vo.setId(3L);
            vo.setStatus("PARTIALLY_ALLOCATED");
            vo.setRequiredQuantity(new BigDecimal("10"));
            vo.setAllocatedQuantity(new BigDecimal("4"));
            vo.setUnallocatedQuantity(new BigDecimal("6"));
            when(demandService.allocate(any(), any())).thenReturn(vo);

            // 需求没有 /demand/detail/{id} 端点（只定义 query / generate / allocate 三个），
            // 而 PurchaseDemandVO 的两个真实出口是 query 的分页元素与 allocate 的返回体。
            // 这里走 allocate：单对象响应，断言最直接。
            mvc.perform(post("/scm/purchase/demand/allocate")
                            .header("Idempotency-Key", "W5-WEB-demand-allocate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"demandId\":3,\"purchaseOrderItemId\":9,\"quantity\":\"4.0000\","
                                    + "\"supplierId\":1,\"warehouseId\":1,\"version\":0}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.requiredQuantity").value("10.0000"))
                    .andExpect(jsonPath("$.data.allocatedQuantity").value("4.0000"))
                    .andExpect(jsonPath("$.data.unallocatedQuantity").value("6.0000"));
        }

        // ------------------------------------------------------------------
        // 5. 防重复请求编号缺失
        // ------------------------------------------------------------------

        @Test
        @DisplayName("缺少 Idempotency-Key：业务码 40084 原样透出（不是 30001 参数错误）")
        void missingIdempotencyKeyReturnsStableBusinessCode() throws Exception {
            when(orderService.create(any(), isNull()))
                    .thenThrow(new ScmBusinessException(PurchaseErrorCode.PURCHASE_IDEMPOTENCY_KEY_REQUIRED));

            mvc.perform(post("/scm/purchase/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"supplierId\":1,\"warehouseId\":1,\"items\":"
                                    + "[{\"skuId\":1,\"quantity\":\"1.0000\",\"price\":\"1.0000\"}]}"))
                    .andExpect(jsonPath("$.code").value(40084));
        }

        // ------------------------------------------------------------------
        // 6. JSON 数字不得进入定点数字段
        // ------------------------------------------------------------------

        @Test
        @DisplayName("quantity 用 JSON 数字（1.2）→ 30001，且不进 Service（浮点数不得进入数量字段）")
        void jsonNumberCannotEnterFixedPointQuantity() throws Exception {
            mvc.perform(post("/scm/purchase/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"supplierId\":1,\"warehouseId\":1,\"items\":"
                                    + "[{\"skuId\":1,\"quantity\":1.2,\"price\":\"1.0000\"}]}"))
                    .andExpect(jsonPath("$.code").value(30001));
            verifyNoInteractions(orderService);
        }

        // ------------------------------------------------------------------
        // 7 / 8. 空集合校验
        // ------------------------------------------------------------------

        @Test
        @DisplayName("采购单 items 为空 → 30001（Bean Validation 挡住，不进 Service）")
        void createOrderRejectsEmptyItems() throws Exception {
            mvc.perform(post("/scm/purchase/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"supplierId\":1,\"warehouseId\":1,\"items\":[]}"))
                    .andExpect(jsonPath("$.code").value(30001));
            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("确认收货 items 为空 → 30001（必须覆盖全部收货行）")
        void confirmReceiptRejectsEmptyItems() throws Exception {
            mvc.perform(post("/scm/purchase/receipt/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":1,\"version\":0,\"items\":[]}"))
                    .andExpect(jsonPath("$.code").value(30001));
            verifyNoInteractions(receiptService);
        }
    }

    @Nested
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("并发入库确认（PG IT，无外层事务）")
    class PurchaseReceiptPutawayConcurrencyIT extends ScmW6PgITBase {
        private static final long TIMEOUT_SECONDS = 60;

        @Override
        protected void evictMybatisCache() {
            // 无外层事务 → 每次 DAO 调用都是新 session → 一级缓存天然为空
        }

        private static void setThreadOperator() {
            RequestEmployee employee = new RequestEmployee();
            employee.setEmployeeId(1L);
            employee.setActualName("B1 concurrent IT");
            employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            // 与种子员工 1 的真实行一致：本用例测的是并发两次上架的竞态，不是仓库授权。
            // 缺了这一位，上架的仓库范围守卫会按「无任何授权」拒掉两次调用，竞态根本发生不了。
            employee.setAdministratorFlag(true);
            XsyRequestUtil.setRequestUser(employee);
        }

        private record Outcome(boolean success, Throwable error) {
            static Outcome ok() {
                return new Outcome(true, null);
            }

            static Outcome failed(Throwable error) {
                return new Outcome(false, error);
            }
        }

        private List<Outcome> runConcurrently(Runnable first, Runnable second) throws Exception {
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

        @Test
        @DisplayName("并发 putaway 同一收货单：恰好一次成功、一次失败、仅一条流水")
        void concurrentPutawayProducesExactlyOneInbound() throws Exception {
            Long warehouseId = seedWarehouseId();
            Long skuId = newOnShelfSku("PC1");
            Long supplierId = newPurchasableSupplier("PC1", skuId);
            PurchaseOrderVO order = createDraftOrder("PC1", supplierId, skuId, "6.0000", "6.2000");
            submitOrder(order.getId());

            PurchaseReceiptCreateForm create = new PurchaseReceiptCreateForm();
            create.setPurchaseOrderId(order.getId());
            create.setReceiptMode(ScmReceiptModeEnum.WAREHOUSE_CONFIRM.name());
            create.setRemark("B1 并发入库收货单");
            PurchaseReceiptVO receipt = purchaseReceiptService.create(create, prefix + ":PC1:receipt");
            confirmReceipt(receipt.getId(), "6.0000");

            List<Outcome> outcomes = runConcurrently(
                    () -> putaway(receipt.getId()),
                    () -> putaway(receipt.getId()));

            assertThat(outcomes).filteredOn(Outcome::success).hasSize(1);
            assertThat(outcomes).filteredOn(o -> !o.success()).hasSize(1);

            assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("6.0000");
            assertThat(movementCount(warehouseId, skuId)).isEqualTo(1);
            assertThat(movementsOfReceiptItem(receipt.getItems().getFirst().getId())).isEqualTo(1);
        }

        private void putaway(Long receiptId) {
            PurchaseReceiptVO current = reloadReceipt(receiptId);
            PurchaseReceiptPutawayForm form = new PurchaseReceiptPutawayForm();
            form.setId(receiptId);
            form.setVersion(current.getVersion());
            purchaseReceiptService.putaway(form, prefix + ":PC1:putaway:" + UUID.randomUUID());
        }
    }

    @Nested
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("入库确认失败的真实回滚（PG IT，无外层事务）")
    class PurchaseReceiptPutawayRollbackIT extends ScmW6PgITBase {
        @Override
        protected void evictMybatisCache() {
            // 无外层事务 → 每次 DAO 调用都是新 session → 一级缓存天然为空
        }

        private PurchaseReceiptVO warehouseConfirmReceipt(Long orderId) {
            PurchaseReceiptCreateForm form = new PurchaseReceiptCreateForm();
            form.setPurchaseOrderId(orderId);
            form.setReceiptMode(ScmReceiptModeEnum.WAREHOUSE_CONFIRM.name());
            form.setRemark("B1 rollback 收货单");
            return purchaseReceiptService.create(form, prefix + ":rb:receipt:" + orderId);
        }

        private void putaway(Long receiptId) {
            PurchaseReceiptVO current = reloadReceipt(receiptId);
            PurchaseReceiptPutawayForm form = new PurchaseReceiptPutawayForm();
            form.setId(receiptId);
            form.setVersion(current.getVersion());
            purchaseReceiptService.putaway(form, prefix + ":rb:putaway:" + receiptId + ":" + UUID.randomUUID());
        }

        @Test
        @DisplayName("异单位 putaway 41001：putaway 状态、余额、流水、防重复处理记录零残留")
        void unitMismatchRollsBackTheWholePutaway() {
            Long warehouseId = seedWarehouseId();
            Long skuId = newOnShelfSku("RB1");
            // 第一笔：以 kg 建立余额（kg）
            W6Fixture first = freeInboundFixture("RB1a", skuId, "5.0000", "kg");
            confirmReceipt(first.receipt().getId(), "5.0000");
            assertThat(balanceRow(warehouseId, skuId).getUnit()).isEqualTo("kg");
            assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("5.0000");

            // 第二笔：同 SKU 以「箱」为采购单位的 WAREHOUSE_CONFIRM 收货，确认后 putaway → 41001
            Long supplierId = newSupplier("RB1b");
            linkSupplierSku(supplierId, skuId, "box");
            PurchaseOrderVO order = createDraftOrder("RB1b", supplierId, skuId, "3.0000", "6.2000");
            submitOrder(order.getId());
            PurchaseReceiptVO second = warehouseConfirmReceipt(order.getId());
            confirmReceipt(second.getId(), "3.0000");
            assertThat(reloadReceipt(second.getId()).getPutawayStatus()).isEqualTo("PENDING");

            expectCode(() -> putaway(second.getId()), 41001);

            // putaway 状态未变、余额/流水不变
            assertThat(reloadReceipt(second.getId()).getPutawayStatus()).isEqualTo("PENDING");
            assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("5.0000");
            assertThat(balanceRow(warehouseId, skuId).getUnit()).isEqualTo("kg");
            assertThat(movementCount(warehouseId, skuId)).isEqualTo(1);

            // 清理：NOT_SUPPORTED 把数据提交到开发库。WAREHOUSE_CONFIRM + PENDING 的收货单是合法形态
            // 合法形态（CONFIRMED 却没有流水），会打破 ScmInventoryBackfillIT 等对「CONFIRMED ⇒ 有流水」
            // 的 V19 全局对账假设，因此物理删除这张 box 收货单，避免残留污染其它测试。
            jdbc.update("DELETE FROM xsy_receipt_weighing_record WHERE purchase_receipt_item_id IN "
                    + "(SELECT id FROM xsy_purchase_receipt_item WHERE purchase_receipt_id = ?)", second.getId());
            jdbc.update("DELETE FROM xsy_purchase_receipt_item WHERE purchase_receipt_id = ?", second.getId());
            jdbc.update("DELETE FROM xsy_purchase_receipt WHERE id = ?", second.getId());
        }
    }

    @Nested
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("W2B 批量少收关单整批回滚（PG IT，无外层事务）")
    class PurchaseShortCloseRollbackIT extends ScmW5PgITBase {
        @Override
        protected void evictMybatisCache() {
            // 无外层事务 → 每次 Service / DAO 调用都是新 session → 一级缓存天然为空
        }

        @Test
        @DisplayName("合法欠收单先被关单、非法全收单抛 40982 → 两张单与 SHORT_CLOSE 日志全部回滚")
        void stateMachineFailureRollsBackMembersAlreadyWrittenInSameBatch() {
            PurchaseOrderVO legal = partiallyReceivedOrder("RB1");
            PurchaseOrderVO illegal = fullyReceivedOrder("RB1r");
            // 低位 id 先处理：合法那张确实被写过，才谈得上「回滚是否生效」
            assertThat(legal.getId()).isLessThan(illegal.getId());
            Map<String, Object> before = orderRow(legal.getId());

            expectCode(() -> purchaseOrderService.batchShortClose(batchForm("整批关单", legal, illegal)), 40982);

            assertThat(orderRow(legal.getId())).isEqualTo(before);
            assertThat(orderRow(legal.getId())).containsEntry("status", "PARTIALLY_RECEIVED")
                    .containsEntry("short_close_reason", null).containsEntry("short_closed_at", null);
            assertThat(orderRow(illegal.getId())).containsEntry("status", "RECEIVED")
                    .containsEntry("short_closed_at", null);
            assertThat(shortCloseLogs(legal.getId())).isZero();
            assertThat(shortCloseLogs(illegal.getId())).isZero();
        }

        @Test
        @DisplayName("合法欠收单先被关单、兄弟单版本过期抛 40921 → 整批同样不留部分成功")
        void versionConflictRollsBackMembersAlreadyWrittenInSameBatch() {
            PurchaseOrderVO written = partiallyReceivedOrder("RB2");
            PurchaseOrderVO stale = partiallyReceivedOrder("RB3");
            assertThat(written.getId()).isLessThan(stale.getId());
            Map<String, Object> before = orderRow(written.getId());
            // 兄弟单带一个不存在的版本：按 id 升序时先写完 written，再在 stale 上撞乐观锁
            stale.setVersion(stale.getVersion() + 999);

            expectCode(() -> purchaseOrderService.batchShortClose(batchForm("整批关单", written, stale)), 40921);

            assertThat(orderRow(written.getId())).isEqualTo(before);
            assertThat(orderRow(written.getId())).containsEntry("status", "PARTIALLY_RECEIVED");
            assertThat(orderRow(stale.getId())).containsEntry("status", "PARTIALLY_RECEIVED");
            // 断言非空洞：同一张单的其他操作日志确实入了库，只是这次关单没有留下记录
            assertThat(logCount(written.getId(), "CREATE")).isEqualTo(1);
            assertThat(shortCloseLogs(written.getId())).isZero();
            assertThat(shortCloseLogs(stale.getId())).isZero();
        }

        // ---- 独立查询上下文 ----

        /**
         * 关单三要素 + 乐观锁版本：一次读全，回滚是否彻底由整行等值比较判定。
         */
        private Map<String, Object> orderRow(Long orderId) {
            return jdbc.queryForMap("SELECT status, version, short_close_reason, short_closed_at"
                    + " FROM xsy_purchase_order WHERE id=?", orderId);
        }

        private int shortCloseLogs(Long orderId) {
            return logCount(orderId, "SHORT_CLOSE");
        }

        private int logCount(Long orderId, String operationType) {
            return jdbc.queryForObject("SELECT count(*) FROM xsy_purchase_operation_log"
                    + " WHERE purchase_order_id=? AND operation_type=?", Integer.class, orderId, operationType);
        }
    }

    @Nested
    class PurchaseUnitTest {
        @Nested

        class PurchaseAmountCalculatorTest {
            private static int codeOf(Throwable t) {
                return ((ScmBusinessException) t).getErrorCode().getCode();
            }

            @Test
            @DisplayName("line_amount = planned × price，4 位 HALF_UP")
            void lineAmountRoundsHalfUpToScale4() {
                assertThat(PurchaseAmountCalculator.lineAmount(new BigDecimal("3.0000"), new BigDecimal("2.5000")))
                        .isEqualByComparingTo("7.5000");
                // 1.0005 × 0.1000 = 0.10005 → HALF_UP → 0.1001
                assertThat(PurchaseAmountCalculator.lineAmount(new BigDecimal("1.0005"), new BigDecimal("0.1000")))
                        .isEqualByComparingTo("0.1001");
                assertThat(PurchaseAmountCalculator.lineAmount(new BigDecimal("1.0000"), new BigDecimal("0.1000")).scale())
                        .isEqualTo(PurchaseAmountCalculator.SCALE);
            }

            @Test
            @DisplayName("单价为 null → 行金额为 null（不落 0.0000）")
            void nullPriceYieldsNullLineAmount() {
                assertThat(PurchaseAmountCalculator.lineAmount(new BigDecimal("10.0000"), null)).isNull();
                assertThat(PurchaseAmountCalculator.bounded(null)).isNull();
            }

            @Test
            @DisplayName("单价 0.0000 是合法值 → 行金额 0.0000（≠ null）")
            void zeroPriceIsLegalAndDistinctFromNull() {
                BigDecimal zero = PurchaseAmountCalculator.lineAmount(new BigDecimal("3.0000"), BigDecimal.ZERO);
                assertThat(zero).isNotNull();
                assertThat(zero).isEqualByComparingTo("0.0000");
                assertThat(zero).isNotEqualTo(PurchaseAmountCalculator.lineAmount(new BigDecimal("3.0000"), null));
            }

            @Test
            @DisplayName("空行集合 → 单头金额 0.0000（空行集合的金额语义）")
            void emptyLinesYieldZeroTotal() {
                assertThat(PurchaseAmountCalculator.totalAmount(Collections.emptyList()))
                        .isEqualByComparingTo("0.0000");
                assertThat(PurchaseAmountCalculator.totalAmount(null)).isEqualByComparingTo("0.0000");
            }

            @Test
            @DisplayName("任一行金额为 null → 单头金额为 null")
            void nullLinePoisonsTotal() {
                assertThat(PurchaseAmountCalculator.totalAmount(Arrays.asList(BigDecimal.ONE, null))).isNull();
                assertThat(PurchaseAmountCalculator.totalAmount(Collections.singletonList((BigDecimal) null))).isNull();
            }

            @Test
            @DisplayName("total_amount = Σ line_amount，结果同样规范化到 4 位")
            void totalSumsAndNormalizes() {
                List<BigDecimal> lines = List.of(
                        new BigDecimal("7.5000"),
                        new BigDecimal("0.0000"),
                        new BigDecimal("12.3456"));
                BigDecimal total = PurchaseAmountCalculator.totalAmount(lines);
                assertThat(total).isEqualByComparingTo("19.8456");
                assertThat(total.scale()).isEqualTo(PurchaseAmountCalculator.SCALE);
            }

            @Test
            @DisplayName("容量边界：99999999999999.9999 通过，再大 1 分越界 → 40081")
            void capacityBoundary() {
                assertThatCode(() -> PurchaseAmountCalculator.bounded(new BigDecimal("99999999999999.9999")))
                        .doesNotThrowAnyException();
                assertThatThrownBy(() -> PurchaseAmountCalculator.bounded(new BigDecimal("100000000000000.0000")))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40081));
            }

            @Test
            @DisplayName("行金额溢出同样抛 40081（不是 NumberFormatException）")
            void lineAmountOverflowThrowsPriceInvalid() {
                assertThatThrownBy(() -> PurchaseAmountCalculator.lineAmount(
                        new BigDecimal("99999999999999.9999"), BigDecimal.TEN))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40081));
                assertThatThrownBy(() -> PurchaseAmountCalculator.totalAmount(List.of(
                        new BigDecimal("99999999999999.9999"), new BigDecimal("1.0000"))))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40081));
            }
        }

        @Nested

        class PurchaseDemandAllocatorTest {
            private static int codeOf(Throwable t) {
                return ((ScmBusinessException) t).getErrorCode().getCode();
            }

            private static PurchaseDemandEntity demand(Long supplierId, Long warehouseId, String allocated) {
                PurchaseDemandEntity entity = new PurchaseDemandEntity();
                entity.setSupplierId(supplierId);
                entity.setWarehouseId(warehouseId);
                entity.setRequiredQuantity(new BigDecimal("10.0000"));
                entity.setAllocatedQuantity(allocated == null ? null : new BigDecimal(allocated));
                return entity;
            }

            // ------------------------------------------------------------------
            // 单位一致性
            // ------------------------------------------------------------------

            @Test
            @DisplayName("Q17：单位一致（字符串相等）→ 允许自动分配")
            void unitCompatibleWhenEqual() {
                assertThatCode(() -> PurchaseDemandAllocator.unitCompatible("kg", "kg")).doesNotThrowAnyException();
                assertThatCode(() -> PurchaseDemandAllocator.unitCompatible("箱", "箱")).doesNotThrowAnyException();
            }

            @Test
            @DisplayName("Q17：单位不一致 → 40971（拒绝自动分配，不做静默换算）")
            void unitMismatchRejected() {
                assertThatThrownBy(() -> PurchaseDemandAllocator.unitCompatible("kg", "箱"))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40971));
            }

            @Test
            @DisplayName("Q17：单位缺失 → 40971（不做「空即相等」的静默兜底）")
            void missingUnitRejected() {
                assertThatThrownBy(() -> PurchaseDemandAllocator.unitCompatible(null, "kg"))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40971));
                assertThatThrownBy(() -> PurchaseDemandAllocator.unitCompatible("kg", null))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40971));
                assertThatThrownBy(() -> PurchaseDemandAllocator.unitCompatible(null, null))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40971));
            }

            @Test
            @DisplayName("Q17：大小写敏感 —— kg 与 KG 视为不一致（单位不做归一化，避免掩盖真实差异）")
            void unitComparisonIsCaseSensitive() {
                assertThatThrownBy(() -> PurchaseDemandAllocator.unitCompatible("kg", "KG"))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40971));
            }

            // ------------------------------------------------------------------
            // 行 / 需求归属
            // ------------------------------------------------------------------

            @Test
            @DisplayName("采购行必须与需求同 SKU：不同 → 40995；skuId 缺失 → 40995")
            void itemMustMatchDemandSku() {
                assertThatCode(() -> PurchaseDemandAllocator.itemMatchesDemand(100L, 100L)).doesNotThrowAnyException();
                assertThatThrownBy(() -> PurchaseDemandAllocator.itemMatchesDemand(100L, 200L))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40995));
                assertThatThrownBy(() -> PurchaseDemandAllocator.itemMatchesDemand(null, 200L))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40995));
            }

            @Test
            @DisplayName("需求状态白名单：PENDING / PARTIALLY_ALLOCATED / ALLOCATED 都允许（支持补分配）")
            void assignableWhitelist() {
                assertThatCode(() -> PurchaseDemandAllocator.assignable("PENDING")).doesNotThrowAnyException();
                assertThatCode(() -> PurchaseDemandAllocator.assignable("PARTIALLY_ALLOCATED")).doesNotThrowAnyException();
                assertThatCode(() -> PurchaseDemandAllocator.assignable("ALLOCATED")).doesNotThrowAnyException();
            }

            @ParameterizedTest
            @ValueSource(strings = {"CLOSED", "draft", "PENDING "})
            @DisplayName("未知 / 大小写不符的需求状态 → 40981")
            void unknownDemandStatusRejected(String status) {
                assertThatThrownBy(() -> PurchaseDemandAllocator.assignable(status))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40981));
            }

            // ------------------------------------------------------------------
            // 需求版本（乐观锁）
            // ------------------------------------------------------------------

            @Test
            @DisplayName("需求版本必填（40091）且必须相等（40972）")
            void demandVersionRules() {
                assertThatCode(() -> PurchaseDemandAllocator.demandVersion(3, 3)).doesNotThrowAnyException();
                assertThatThrownBy(() -> PurchaseDemandAllocator.demandVersion(null, 3))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40091));
                assertThatThrownBy(() -> PurchaseDemandAllocator.demandVersion(2, 3))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40972));
            }

            // ------------------------------------------------------------------
            // (supplier, warehouse) 一致性
            // ------------------------------------------------------------------

            @Test
            @DisplayName("仓库在 generate 时已固定：与采购单仓库不等 → 40981")
            void warehouseMismatchRejected() {
                assertThatThrownBy(() -> PurchaseDemandAllocator.assignmentCompatible(9L, 2L, demand(null, 1L, "0.0000")))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40981));
                assertThatCode(() -> PurchaseDemandAllocator.assignmentCompatible(9L, 1L, demand(null, 1L, "0.0000")))
                        .doesNotThrowAnyException();
            }

            @Test
            @DisplayName("供应商由首次分配固定：已分配过再换供应商 → 40981；首次分配可自由选择")
            void supplierFixedOnFirstAllocation() {
                // 已分配过 3.0000 且供应商为 7 → 不允许改成 9
                assertThatThrownBy(() -> PurchaseDemandAllocator.assignmentCompatible(9L, 1L, demand(7L, 1L, "3.0000")))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40981));
                // 同一供应商继续分配 → 允许
                assertThatCode(() -> PurchaseDemandAllocator.assignmentCompatible(7L, 1L, demand(7L, 1L, "3.0000")))
                        .doesNotThrowAnyException();
                // 尚未分配（allocated == 0）→ 允许换成 9
                assertThatCode(() -> PurchaseDemandAllocator.assignmentCompatible(9L, 1L, demand(7L, 1L, "0.0000")))
                        .doesNotThrowAnyException();
            }

            @Test
            @DisplayName("首次分配判定与供应商写入数据库判定")
            void firstAllocationDetection() {
                assertThat(PurchaseDemandAllocator.isFirstAllocation(demand(null, 1L, null))).isTrue();
                assertThat(PurchaseDemandAllocator.isFirstAllocation(demand(null, 1L, "0.0000"))).isTrue();
                assertThat(PurchaseDemandAllocator.isFirstAllocation(demand(null, 1L, "0.0001"))).isFalse();

                assertThat(PurchaseDemandAllocator.shouldFixSupplier(demand(null, 1L, "0.0000"))).isTrue();
                assertThat(PurchaseDemandAllocator.shouldFixSupplier(demand(7L, 1L, "0.0000"))).isFalse();
                assertThat(PurchaseDemandAllocator.shouldFixSupplier(demand(null, 1L, "3.0000"))).isFalse();
            }

            // ------------------------------------------------------------------
            // 合计不超需求 + 状态回落
            // ------------------------------------------------------------------

            @Test
            @DisplayName("分配合计必须在 [0, required]：超需求 / 负数 / 缺失 → 40082")
            void allocatedMustStayWithinRequired() {
                assertThatCode(() -> PurchaseDemandAllocator.withinRequired(new BigDecimal("10.0000"), new BigDecimal("10.0000")))
                        .doesNotThrowAnyException();
                assertThatThrownBy(() -> PurchaseDemandAllocator.withinRequired(new BigDecimal("10.0000"), new BigDecimal("10.0001")))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40082));
                assertThatThrownBy(() -> PurchaseDemandAllocator.withinRequired(new BigDecimal("10.0000"), new BigDecimal("-1.0000")))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40082));
                assertThatThrownBy(() -> PurchaseDemandAllocator.withinRequired(new BigDecimal("10.0000"), null))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40082));
            }

            @Test
            @DisplayName("状态推导必须能回落：allocated 归零 → PENDING（否则删分配后状态卡在 ALLOCATED）")
            void statusDerivationFallsBack() {
                BigDecimal required = new BigDecimal("10.0000");
                assertThat(PurchaseDemandAllocator.statusFor(required, null)).isEqualTo("PENDING");
                assertThat(PurchaseDemandAllocator.statusFor(required, new BigDecimal("0.0000"))).isEqualTo("PENDING");
                assertThat(PurchaseDemandAllocator.statusFor(required, new BigDecimal("4.0000"))).isEqualTo("PARTIALLY_ALLOCATED");
                assertThat(PurchaseDemandAllocator.statusFor(required, new BigDecimal("10.0000"))).isEqualTo("ALLOCATED");
            }

            @Test
            @DisplayName("P12 锁序：demandId 去重后升序，避免与 order.create 路径交叉成环")
            void ascendingDemandIdsForLockOrdering() {
                List<Long> ids = PurchaseDemandAllocator.ascendingDemandIds(
                        Arrays.asList(9L, 3L, 9L, 1L, null));
                assertThat(ids).containsExactly(1L, 3L, 9L);
            }
        }

        @Nested
        @DisplayName("缺口预览权限交集：采购需求查看权 AND 库存余额查看权")
        class PurchaseDemandSummaryPreviewPermissionTest {
            private static final String DEMAND_QUERY = "scm:purchase:demand:query";

            private static final String BALANCE_QUERY = "scm:inventory:balance:query";

            private SaCheckPermission previewPermission() throws NoSuchMethodException {
                Method method = PurchaseDemandController.class
                        .getMethod("summaryPreview", PurchaseDemandSummaryPreviewForm.class);
                SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
                assertThat(permission).as("summary-preview 必须声明 @SaCheckPermission").isNotNull();
                return permission;
            }

            @Test
            @DisplayName("预览端点同时声明两条查询权限，缺一即越权路径复活")
            void declaresBothQueryPermissions() throws NoSuchMethodException {
                assertThat(List.of(previewPermission().value()))
                        .containsExactlyInAnyOrder(DEMAND_QUERY, BALANCE_QUERY);
            }

            @Test
            @DisplayName("模式必须是 AND：OR 会让只有采购权限的人照样读到余额")
            void modeIsAndNotOr() throws NoSuchMethodException {
                SaCheckPermission permission = previewPermission();
                assertThat(permission.mode()).isEqualTo(SaMode.AND);
            }

            @Test
            @DisplayName("库存余额接口自身的权限未被下调或改写（修权限不能靠放宽另一侧）")
            void inventoryBalancePermissionUnchanged() throws NoSuchMethodException {
                Method query = InventoryBalanceController.class.getMethod("query",
                        com.xsy.scm.inventory.domain.form.InventoryBalanceQueryForm.class);
                assertThat(List.of(query.getAnnotation(SaCheckPermission.class).value())).containsExactly(BALANCE_QUERY);
            }
        }

        @Nested

        class PurchaseErrorCodeTest {
            /**
             * SCM 根包：全部 SCM 错误码枚举都在 {@code com.xsy.scm} 之下。
             *
             * <p>它必须与 {@link #FALLBACK} 的包名一致：包名对不上时扫描得到 0 个类并静默回退到 FALLBACK 清单，
             * 「扫描失败」就被降级成「清单可用」，属于典型的假绿面。
             */
            private static final String SCM_PACKAGE = "com.xsy.scm";

            /**
             * 回退清单：classpath 不是展开目录时使用。新域的错误码枚举要在这里补一行。
             */
            private static final List<String> FALLBACK = List.of(
                    "com.xsy.scm.common.error.ScmCommonErrorCode",
                    "com.xsy.scm.product.constant.ProductErrorCode",
                    "com.xsy.scm.customer.constant.CustomerErrorCode",
                    "com.xsy.scm.supplier.constant.SupplierErrorCode",
                    "com.xsy.scm.pricing.constant.PricingErrorCode",
                    "com.xsy.scm.order.constant.OrderErrorCode",
                    PurchaseErrorCode.class.getName(),
                    WarehouseErrorCode.class.getName(),
                    // 库存域：本类只断言采购与仓库两枚枚举不撞车，
                    // 库存域与其余域的撞码由 ScmInventoryConstantTest 的全库唯一性判据负责。
                    "com.xsy.scm.inventory.constant.InventoryErrorCode");

            /**
             * 既有域的错误码枚举，必须被扫描到（防止扫描静默失效）。
             */
            private static final Set<String> W1_TO_W4_ENUMS = Set.of(
                    "ScmCommonErrorCode", "ProductErrorCode", "CustomerErrorCode",
                    "SupplierErrorCode", "PricingErrorCode", "OrderErrorCode");

            @Test
            @DisplayName("撞码门禁：W5+B1 合计 70 码、段内无重复、段分布正确、与 W1–W4 零交集")
            void gate() {
                // ---------- 1. 数量 ----------
                // 61 个里含净需求计算的 40092（批量上限）、40487（批次不存在）与采购价后置/锁单的 8 个码。
                assertThat(PurchaseErrorCode.values()).hasSize(61);
                // 9 个里含 WAREHOUSE_DEFAULT_AMBIGUOUS(41018)、阻塞仓库停用的在途调拨 41009，
                // 以及负库存开关的 WAREHOUSE_HAS_NEGATIVE_STOCK(41010)。
                assertThat(WarehouseErrorCode.values()).hasSize(9);

                Map<String, Integer> w5 = new LinkedHashMap<>();
                Arrays.stream(PurchaseErrorCode.values())
                        .forEach(c -> w5.put("PurchaseErrorCode." + c.name(), c.getCode()));
                Arrays.stream(WarehouseErrorCode.values())
                        .forEach(c -> w5.put("WarehouseErrorCode." + c.name(), c.getCode()));
                assertThat(w5).as("W5+B1 错误码合计").hasSize(70);

                // ---------- 2. 段内无重复 ----------
                Set<Integer> w5Codes = new LinkedHashSet<>(w5.values());
                assertThat(w5Codes).as("W5+B1 段内存在重复码值").hasSize(70);

                // ---------- 3. 段分布 ----------
                Set<Integer> purchaseCodes = Arrays.stream(PurchaseErrorCode.values())
                        .map(PurchaseErrorCode::getCode).collect(Collectors.toSet());
                assertThat(purchaseCodes.stream().filter(c -> c / 100 == 400).count())
                        .as("PurchaseErrorCode 400xx 段").isEqualTo(17L);
                assertThat(purchaseCodes.stream().filter(c -> c / 100 == 404).count())
                        .as("PurchaseErrorCode 404xx 段").isEqualTo(7L);
                assertThat(purchaseCodes.stream().filter(c -> c / 100 == 409).count())
                        .as("PurchaseErrorCode 409xx 段").isEqualTo(22L);
                assertThat(purchaseCodes.stream().filter(c -> c / 100 == 410).count())
                        .as("PurchaseErrorCode 410xx 段").isEqualTo(15L);
                assertThat(purchaseCodes).hasSize(61);

                assertThat(WarehouseErrorCode.WAREHOUSE_NOT_FOUND.getCode()).isEqualTo(40485);
                assertThat(WarehouseErrorCode.WAREHOUSE_CODE_DUPLICATE.getCode()).isEqualTo(40996);
                assertThat(WarehouseErrorCode.WAREHOUSE_STATE_INVALID.getCode()).isEqualTo(41004);
                assertThat(WarehouseErrorCode.WAREHOUSE_DISABLE_HAS_BALANCE.getCode()).isEqualTo(41005);
                assertThat(WarehouseErrorCode.WAREHOUSE_DISABLE_HAS_INBOUND.getCode()).isEqualTo(41006);
                assertThat(WarehouseErrorCode.WAREHOUSE_DISABLE_HAS_PENDING_PUTAWAY.getCode()).isEqualTo(41007);
                // 启用仓库不唯一时无法解析「默认仓库」（订单无仓库字段）
                assertThat(WarehouseErrorCode.WAREHOUSE_DEFAULT_AMBIGUOUS.getCode()).isEqualTo(41018);
                // 在途调拨单阻塞仓库停用（第四条停用阻塞条件）
                assertThat(WarehouseErrorCode.WAREHOUSE_DISABLE_HAS_IN_TRANSIT_TRANSFER.getCode()).isEqualTo(41009);
                // 41019 属盘点域，仓库自己用 41010；负余额未补平前不能关闭负库存出库
                assertThat(WarehouseErrorCode.WAREHOUSE_HAS_NEGATIVE_STOCK.getCode()).isEqualTo(41010);
                assertThat(PurchaseErrorCode.PURCHASE_PRICE_PENDING.getCode()).isEqualTo(41067);
                assertThat(PurchaseErrorCode.PURCHASE_PRICE_WINDOW_CLOSED.getCode()).isEqualTo(41068);
                assertThat(PurchaseErrorCode.PURCHASE_ORDER_LOCKED.getCode()).isEqualTo(41069);
                assertThat(PurchaseErrorCode.PURCHASE_ITEM_NOT_PRICE_PENDING.getCode()).isEqualTo(41074);
                assertThat(PurchaseErrorCode.PURCHASE_PRICE_ADJUST_NOT_ALLOWED.getCode()).isEqualTo(41075);
                assertThat(PurchaseErrorCode.PURCHASE_PRICE_NO_REFERENCE.getCode()).isEqualTo(41076);
                assertThat(PurchaseErrorCode.PURCHASE_UNLOCK_REASON_REQUIRED.getCode()).isEqualTo(41077);
                assertThat(PurchaseErrorCode.PURCHASE_PRICE_REASON_REQUIRED.getCode()).isEqualTo(41078);

                // 合并后的段分布：400xx=17 · 404xx=8（含 40485）· 409xx=23（含 40996）· 410xx=22
                assertThat(w5Codes.stream().filter(c -> c / 100 == 400).count()).isEqualTo(17L);
                assertThat(w5Codes.stream().filter(c -> c / 100 == 404).count()).isEqualTo(8L);
                assertThat(w5Codes.stream().filter(c -> c / 100 == 409).count()).isEqualTo(23L);
                assertThat(w5Codes.stream().filter(c -> c / 100 == 410).count()).isEqualTo(22L);

                // ---------- 4. 与既有域错误码零交集 ----------
                List<Class<?>> discovered = discover();
                Set<String> discoveredNames = discovered.stream()
                        .map(Class::getSimpleName).collect(Collectors.toCollection(LinkedHashSet::new));
                assertThat(discoveredNames)
                        .as("扫描未覆盖 W1–W4 全部错误码枚举，门禁会漏放")
                        .containsAll(W1_TO_W4_ENUMS);
                assertThat(discoveredNames)
                        .as("扫描未覆盖 W5 自己的两个枚举")
                        .contains(PurchaseErrorCode.class.getSimpleName(), WarehouseErrorCode.class.getSimpleName());

                Set<Integer> otherCodes = new LinkedHashSet<>();
                for (Class<?> type : discovered) {
                    if (type == PurchaseErrorCode.class || type == WarehouseErrorCode.class) {
                        continue;
                    }
                    for (Object constant : type.getEnumConstants()) {
                        otherCodes.add(((ScmErrorCode) constant).getCode());
                    }
                }
                assertThat(otherCodes).as("W1–W4 错误码集合不应为空").isNotEmpty();

                for (Map.Entry<String, Integer> entry : w5.entrySet()) {
                    assertThat(otherCodes)
                            .as("W5 错误码 %s(%d) 与 W1–W4 已占用码冲突", entry.getKey(), entry.getValue())
                            .doesNotContain(entry.getValue());
                }
            }

            // ------------------------------------------------------------------
            // classpath 扫描
            // ------------------------------------------------------------------

            private static List<Class<?>> discover() {
                try {
                    URL root = ScmErrorCode.class.getResource("/" + SCM_PACKAGE.replace('.', '/'));
                    if (root != null && "file".equals(root.getProtocol())) {
                        Path base = Paths.get(root.toURI());
                        List<Class<?>> found = new ArrayList<>();
                        try (Stream<Path> walk = Files.walk(base)) {
                            for (Path path : walk.filter(Files::isRegularFile).collect(Collectors.toList())) {
                                String fileName = path.getFileName().toString();
                                if (!fileName.endsWith("ErrorCode.class") || fileName.contains("$")) {
                                    continue;
                                }
                                String relative = base.relativize(path).toString()
                                        .replace(File.separatorChar, '.').replace('/', '.');
                                String fqn = SCM_PACKAGE + "." + relative.substring(0, relative.length() - ".class".length());
                                Class<?> type = load(fqn);
                                if (type != null && type.isEnum() && ScmErrorCode.class.isAssignableFrom(type)) {
                                    found.add(type);
                                }
                            }
                        }
                        if (!found.isEmpty()) {
                            return found;
                        }
                    }
                } catch (Exception ignored) {
                    // 落到回退清单
                }
                return FALLBACK.stream().map(PurchaseErrorCodeTest::load)
                        .filter(Objects::nonNull).collect(Collectors.toList());
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

        class PurchaseIdempotencyRequestHasherTest {
            private final ScmIdempotencyRequestHasher hasher =
                    new ScmIdempotencyRequestHasher(new ObjectMapper());

            @Test
            @DisplayName("键排序：对象键顺序不影响哈希；但数组顺序必须影响（分配顺序是请求的一部分）")
            void keyOrderDoesNotMatterButArrayOrderDoes() {
                Map<String, Object> left = new LinkedHashMap<>();
                left.put("supplierId", 1);
                left.put("items", List.of(
                        Map.of("skuId", 100, "quantity", "1.5000", "price", "9.0000"),
                        Map.of("skuId", 101, "weight", "2.0000")));
                left.put("m", Map.of("nested", Map.of("y", "x", "b", "a")));

                Map<String, Object> right = new LinkedHashMap<>();
                right.put("m", Map.of("nested", Map.of("b", "a", "y", "x")));
                right.put("items", List.of(
                        Map.of("price", "9.0000", "quantity", "1.5000", "skuId", 100),
                        Map.of("weight", "2.0000", "skuId", 101)));
                right.put("supplierId", 1);

                assertThat(hasher.hash(left)).isEqualTo(hasher.hash(right));

                // 数组是有序的：allocations 的先后不可归一，否则「换序的同一组分配」会被误判为重放
                assertThat(hasher.hash(Map.of("items", List.of(
                        Map.of("skuId", 100, "quantity", "1.5000"), Map.of("skuId", 101, "quantity", "2.0000")))))
                        .isNotEqualTo(hasher.hash(Map.of("items", List.of(
                                Map.of("skuId", 101, "quantity", "2.0000"),
                                Map.of("skuId", 100, "quantity", "1.5000")))));
            }

            @Test
            @DisplayName("数字归一：JSON 数字 stripTrailingZeros（1.5000 == 1.5，100.0000 == 100）")
            void numericValuesAreStripped() {
                assertThat(hasher.hash(Map.of("amount", new BigDecimal("1.5000"))))
                        .isEqualTo(hasher.hash(Map.of("amount", new BigDecimal("1.5"))));
                assertThat(hasher.hash(Map.of("quantity", new BigDecimal("100.0000"))))
                        .isEqualTo(hasher.hash(Map.of("quantity", 100)));
            }

            @Test
            @DisplayName("数字字符串归一：W5 的定点字符串字段必须可重放（\"1.5000\" == \"1.5\"）")
            void numericStringsAreNormalized() {
                for (String field : List.of("amount", "quantity", "price", "weight", "cost", "rate")) {
                    assertThat(hasher.hash(Map.of(field, "1.5000")))
                            .as("业务数值字段 %s 的 scale 不应改变防重复处理请求身份", field)
                            .isEqualTo(hasher.hash(Map.of(field, "1.5")));
                }
            }

            @Test
            @DisplayName("编码、外部凭据和备注按普通文本保留前导零")
            void identifiersAndRemarksAreNotNormalizedAsNumbers() {
                for (String field : List.of("externalReference", "supplierCode", "skuCode", "remark")) {
                    assertThat(hasher.hash(Map.of(field, "00123")))
                            .as("文本字段 %s 必须保留前导零", field)
                            .isNotEqualTo(hasher.hash(Map.of(field, "123")));
                }

                assertThat(hasher.hash(Map.of("remark", "供应商缺货")))
                        .isNotEqualTo(hasher.hash(Map.of("remark", "供应商缺货 ")));
            }

            @Test
            @DisplayName("三态语义：null 与 \"0.0000\" 必须哈希不同")
            void nullIsNotZero() {
                assertThat(hasher.hash(Map.of("amount", "0.0000")))
                        .isNotEqualTo(hasher.hash(Collections.singletonMap("amount", null)));
                // 嵌套位置同样成立：{allocations:[{demandVersion:null}]} ≠ {allocations:[{demandVersion:0}]}
                assertThat(hasher.hash(Map.of("allocations",
                        List.of(Collections.singletonMap("demandVersion", null)))))
                        .isNotEqualTo(hasher.hash(Map.of("allocations", List.of(Map.of("demandVersion", 0)))));
            }

            @Test
            @DisplayName("确定性：同一请求多次哈希结果稳定，且是 64 位十六进制 SHA-256")
            void hashIsStableAndSha256() {
                Map<String, Object> request = Map.of("supplierId", 1,
                        "items", List.of(Map.of("skuId", 100, "quantity", "3.0000")));
                String first = hasher.hash(request);
                assertThat(first).isEqualTo(hasher.hash(request));
                assertThat(first).matches("[0-9a-f]{64}");

                // 内容变化必须改变哈希
                assertThat(hasher.hash(Map.of("supplierId", 2, "items",
                        List.of(Map.of("skuId", 100, "quantity", "3.0000"))))).isNotEqualTo(first);
            }
        }

        @Nested

        class PurchaseInquiryDecisionPolicyTest {
            private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-17T17:30:00+08:00");

            @Test
            void acceptingExistingQuoteIsAllowedAfterReplyDeadline() {
                Inquiry inquiry = inquiry(3, "OPEN", NOW.minusMinutes(30));
                assertThatCode(() -> PurchaseInquiryDecisionPolicy.requireAwaitingDecision(inquiry, 3))
                        .doesNotThrowAnyException();
                assertThatThrownBy(() -> PurchaseInquiryDecisionPolicy.requireQuoteWritable(inquiry, 3, NOW))
                        .isInstanceOf(ScmBusinessException.class);
                assertThat(PurchaseInquiryDecisionPolicy.isQuoteStillValid(NOW.plusDays(1), NOW)).isTrue();
            }

            @Test
            void staleVersionAndAcceptedInquiryRejectFurtherDecisions() {
                Inquiry inquiry = inquiry(4, "OPEN", NOW.plusMinutes(10));
                assertThatThrownBy(() -> PurchaseInquiryDecisionPolicy.requireAwaitingDecision(inquiry, 3))
                        .isInstanceOf(ScmBusinessException.class);
                inquiry.setStatus("ACCEPTED");
                assertThatThrownBy(() -> PurchaseInquiryDecisionPolicy.requireAwaitingDecision(inquiry, 4))
                        .isInstanceOf(ScmBusinessException.class);
            }

            @Test
            void expiredQuoteCannotBeAccepted() {
                assertThat(PurchaseInquiryDecisionPolicy.isQuoteStillValid(NOW, NOW)).isFalse();
                assertThat(PurchaseInquiryDecisionPolicy.isQuoteStillValid(NOW.minusSeconds(1), NOW)).isFalse();
                assertThat(PurchaseInquiryDecisionPolicy.isQuoteStillValid(null, NOW)).isFalse();
            }

            private static Inquiry inquiry(int version, String status, OffsetDateTime deadline) {
                Inquiry row = new Inquiry();
                row.setVersion(version);
                row.setStatus(status);
                row.setDeadlineAt(deadline);
                return row;
            }
        }

        @Nested

        class PurchaseInquiryPermissionTest {
            @Test
            void eachInquiryOperationHasAnIndependentFunctionPermission() throws Exception {
                assertThat(PurchaseInquiryController.class.getMethod("byBatch", PurchaseInquiryForm.Batch.class)
                        .getAnnotation(SaCheckPermission.class).value()).containsExactly(PurchasePermission.INQUIRY_QUERY);
                assertThat(PurchaseInquiryController.class.getMethod("candidates", PurchaseInquiryForm.Batch.class)
                        .getAnnotation(SaCheckPermission.class).value()).containsExactly(PurchasePermission.INQUIRY_QUERY);
                assertThat(PurchaseInquiryController.class.getMethod("history", PurchaseInquiryForm.History.class)
                        .getAnnotation(SaCheckPermission.class).value()).containsExactly(PurchasePermission.INQUIRY_QUERY);
                assertThat(PurchaseInquiryController.class.getMethod("create", PurchaseInquiryForm.Create.class, String.class)
                        .getAnnotation(SaCheckPermission.class).value()).containsExactly(PurchasePermission.INQUIRY_CREATE);
                assertThat(PurchaseInquiryController.class.getMethod("quote", PurchaseInquiryForm.Quote.class, String.class)
                        .getAnnotation(SaCheckPermission.class).value()).containsExactly(PurchasePermission.INQUIRY_QUOTE);
                assertThat(PurchaseInquiryController.class.getMethod("accept", PurchaseInquiryForm.Accept.class, String.class)
                        .getAnnotation(SaCheckPermission.class).value()).containsExactly(PurchasePermission.INQUIRY_ACCEPT);
            }
        }

        @Nested

        class PurchaseOrderAllocationChangeSetTest {
            private static int codeOf(Throwable t) {
                return ((ScmBusinessException) t).getErrorCode().getCode();
            }

            private static PurchaseDemandAllocationEntity existing(Long id, Long itemId, Long demandId, String quantity) {
                PurchaseDemandAllocationEntity row = new PurchaseDemandAllocationEntity();
                row.setId(id);
                row.setPurchaseOrderItemId(itemId);
                row.setPurchaseDemandId(demandId);
                row.setAllocatedQuantity(new BigDecimal(quantity));
                row.setVersion(3);
                row.setCreatedAt(OffsetDateTime.parse("2026-09-01T10:00:00+08:00"));
                row.setCreatedBy("creator");
                return row;
            }

            private static PurchaseDemandAllocationEntity requested(Long itemId, Long demandId, String quantity) {
                PurchaseDemandAllocationEntity row = new PurchaseDemandAllocationEntity();
                row.setPurchaseOrderItemId(itemId);
                row.setPurchaseDemandId(demandId);
                row.setAllocatedQuantity(new BigDecimal(quantity));
                return row;
            }

            // ------------------------------------------------------------------
            // 主线：一行多需求
            // ------------------------------------------------------------------

            @Test
            @DisplayName("Q13：一行承接两个需求 → 两条 allocation 都进入 inserted（绝不覆盖成一条）")
            void oneItemWithTwoDemandsKeepsBoth() {
                PurchaseOrderAllocationChangeSet diff = PurchaseOrderAllocationChangeSet.between(
                        List.of(),
                        List.of(requested(7L, 101L, "3.0000"), requested(7L, 102L, "5.0000")));

                assertThat(diff.inserted()).hasSize(2);
                assertThat(diff.inserted()).extracting(PurchaseDemandAllocationEntity::getPurchaseDemandId)
                        .containsExactly(101L, 102L);
                assertThat(diff.updated()).isEmpty();
                assertThat(diff.removed()).isEmpty();
            }

            @Test
            @DisplayName("Q13：同一需求出现在两个采购行 → 身份含 itemId，两条都保留")
            void sameDemandOnTwoItemsAreDistinctIdentities() {
                PurchaseOrderAllocationChangeSet diff = PurchaseOrderAllocationChangeSet.between(
                        List.of(),
                        List.of(requested(7L, 101L, "3.0000"), requested(8L, 101L, "4.0000")));
                assertThat(diff.inserted()).hasSize(2);
            }

            @Test
            @DisplayName("Q13：只改一条 allocation → 仅该条进 updated，另一条不出现在任何列表")
            void changingOneAllocationLeavesSiblingsUntouched() {
                PurchaseDemandAllocationEntity a = existing(1L, 7L, 101L, "3.0000");
                PurchaseDemandAllocationEntity b = existing(2L, 7L, 102L, "5.0000");

                PurchaseOrderAllocationChangeSet diff = PurchaseOrderAllocationChangeSet.between(
                        List.of(a, b),
                        List.of(requested(7L, 101L, "3.0000"), requested(7L, 102L, "8.0000")));

                assertThat(diff.updated()).hasSize(1);
                assertThat(diff.updated().get(0).getPurchaseDemandId()).isEqualTo(102L);
                assertThat(diff.updated().get(0).getAllocatedQuantity()).isEqualByComparingTo("8.0000");
                assertThat(diff.inserted()).isEmpty();
                assertThat(diff.removed()).isEmpty();
            }

            @Test
            @DisplayName("Q13：删一条 allocation → 仅该条进 removed，兄弟条保留")
            void deletingOneAllocationKeepsSiblings() {
                PurchaseDemandAllocationEntity a = existing(1L, 7L, 101L, "3.0000");
                PurchaseDemandAllocationEntity b = existing(2L, 7L, 102L, "5.0000");

                PurchaseOrderAllocationChangeSet diff = PurchaseOrderAllocationChangeSet.between(
                        List.of(a, b), List.of(requested(7L, 101L, "3.0000")));

                assertThat(diff.removed()).containsExactly(b);
                assertThat(diff.updated()).isEmpty();
                assertThat(diff.inserted()).isEmpty();
            }

            @Test
            @DisplayName("Q13：数量未变 → 不进任何列表（避免无意义写与版本膨胀）")
            void unchangedQuantityIsNoOp() {
                PurchaseDemandAllocationEntity a = existing(1L, 7L, 101L, "3.0000");
                PurchaseOrderAllocationChangeSet diff = PurchaseOrderAllocationChangeSet.between(
                        List.of(a), List.of(requested(7L, 101L, "3.0000")));
                assertThat(diff.inserted()).isEmpty();
                assertThat(diff.updated()).isEmpty();
                assertThat(diff.removed()).isEmpty();
            }

            @Test
            @DisplayName("Q13：请求内同一 (item, demand) 出现两次 → 40090")
            void duplicateIdentityInRequestRejected() {
                assertThatThrownBy(() -> PurchaseOrderAllocationChangeSet.between(
                        List.of(),
                        List.of(requested(7L, 101L, "3.0000"), requested(7L, 101L, "5.0000"))))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40090));
            }

            @Test
            @DisplayName("Q13：同一 (item, demand) 的两条旧行视为同一身份（不会双删）")
            void duplicateIdentityInExistingCollapses() {
                // 旧数据理论上被唯一索引挡住，但算法层不能因此错乱
                PurchaseOrderAllocationChangeSet diff = PurchaseOrderAllocationChangeSet.between(
                        List.of(existing(1L, 7L, 101L, "3.0000"), existing(2L, 7L, 101L, "3.0000")),
                        List.of(requested(7L, 101L, "3.0000")));
                assertThat(diff.inserted()).isEmpty();
                assertThat(diff.updated()).isEmpty();
                assertThat(diff.removed()).isEmpty();
            }

            @Test
            @DisplayName("Q13：新增第三条 allocation 到已有两行上 → 只 inserted 一条")
            void appendingThirdAllocation() {
                PurchaseOrderAllocationChangeSet diff = PurchaseOrderAllocationChangeSet.between(
                        List.of(existing(1L, 7L, 101L, "3.0000"), existing(2L, 7L, 102L, "5.0000")),
                        List.of(requested(7L, 101L, "3.0000"), requested(7L, 102L, "5.0000"), requested(7L, 103L, "2.0000")));
                assertThat(diff.inserted()).hasSize(1);
                assertThat(diff.inserted().get(0).getPurchaseDemandId()).isEqualTo(103L);
                assertThat(diff.updated()).isEmpty();
                assertThat(diff.removed()).isEmpty();
            }

            // ------------------------------------------------------------------
            // 需求侧重算必须覆盖「旧 ∪ 新」
            // ------------------------------------------------------------------

            @Test
            @DisplayName("Q13：involvedDemandIds 返回旧 ∪ 新（被删空的 demand 也要重算，否则状态不回落）")
            void involvedDemandIdsIsUnionOfOldAndNew() {
                PurchaseDemandAllocationEntity a = existing(1L, 7L, 101L, "3.0000");
                PurchaseDemandAllocationEntity b = existing(2L, 7L, 102L, "5.0000");

                PurchaseOrderAllocationChangeSet diff = PurchaseOrderAllocationChangeSet.between(
                        List.of(a, b), List.of(requested(7L, 103L, "1.0000")));

                // 101 / 102 被删空 → 必须重算（allocated 归零 → status 回落 PENDING）
                assertThat(diff.involvedDemandIds(List.of(a, b)))
                        .containsExactlyInAnyOrder(101L, 102L, 103L);
            }

            @Test
            @DisplayName("Q13：involvedDemandIds 去重")
            void involvedDemandIdsDeduplicates() {
                PurchaseDemandAllocationEntity a = existing(1L, 7L, 101L, "3.0000");
                PurchaseDemandAllocationEntity b = existing(2L, 8L, 101L, "4.0000");

                PurchaseOrderAllocationChangeSet diff = PurchaseOrderAllocationChangeSet.between(
                        List.of(a, b), List.of(requested(7L, 101L, "3.0000"), requested(8L, 101L, "4.0000")));

                assertThat(diff.involvedDemandIds(List.of(a, b))).containsExactly(101L);
            }

            @Test
            @DisplayName("更新行继承旧行的 id / version / 创建审计（分配是修改不是新建）")
            void updatedRowInheritsIdentityAndAudit() {
                PurchaseDemandAllocationEntity a = existing(1L, 7L, 101L, "3.0000");
                PurchaseOrderAllocationChangeSet diff = PurchaseOrderAllocationChangeSet.between(
                        List.of(a), List.of(requested(7L, 101L, "9.0000")));
                assertThat(diff.updated()).hasSize(1);
                assertThat(diff.updated().get(0).getId()).isEqualTo(1L);
                assertThat(diff.updated().get(0).getCreatedAt()).isEqualTo(a.getCreatedAt());
                assertThat(diff.updated().get(0).getCreatedBy()).isEqualTo("creator");
                // version 必须来自库中值：请求只带 demandVersion，不带 allocation 自己的版本。
                // 漏继承会让 updateQuantity 的 WHERE version = ? 拿到 null → 0 行 → 并发写被误判为冲突。
                assertThat(diff.updated().get(0).getVersion()).isEqualTo(3);
            }

            @Test
            @DisplayName("分配集合可被整单清空：空请求 → 全部 removed（allocated 随之回落）")
            void emptyRequestClearsAllAllocations() {
                PurchaseDemandAllocationEntity a = existing(1L, 7L, 101L, "3.0000");
                PurchaseDemandAllocationEntity b = existing(2L, 7L, 102L, "5.0000");

                PurchaseOrderAllocationChangeSet diff =
                        PurchaseOrderAllocationChangeSet.between(List.of(a, b), List.of());

                assertThat(diff.removed()).containsExactlyInAnyOrder(a, b);
                assertThat(diff.inserted()).isEmpty();
                assertThat(diff.updated()).isEmpty();
                assertThat(diff.involvedDemandIds(List.of(a, b))).containsExactlyInAnyOrder(101L, 102L);
            }
        }

        @Nested
        @DisplayName("采购单导出列目录：顺序 / 过滤 / 空值（纯单测）")
        class PurchaseOrderExportSupportTest {
            private static String title(List<String> cell) {
                return cell.get(0);
            }

            private static PurchaseOrderVO order(String orderNo, String status) {
                PurchaseOrderVO vo = new PurchaseOrderVO();
                vo.setOrderNo(orderNo);
                vo.setStatus(status);
                return vo;
            }

            @Test
            @DisplayName("勾选为空 → 导出整目录，表头顺序与目录一致")
            void emptySelectionExportsFullCatalog() {
                List<String> head = PurchaseOrderExportSupport.head(List.of()).stream()
                        .map(PurchaseOrderExportSupportTest::title).toList();
                assertThat(head).containsExactly(
                        "采购单号", "供应商", "供应商编码", "采购员", "收货仓库", "仓库编码",
                        "计划到货日期", "状态", "采购金额", "收货进度", "备注",
                        "取消原因", "少收关单原因", "提交时间", "创建时间");
                assertThat(PurchaseOrderExportSupport.head(null)).hasSize(head.size());
            }

            @Test
            @DisplayName("勾选子集 → 按目录固定顺序保留命中列，而非前端传入顺序")
            void subsetKeepsCatalogOrder() {
                List<String> head = PurchaseOrderExportSupport.head(List.of("status", "orderNo")).stream()
                        .map(PurchaseOrderExportSupportTest::title).toList();
                // 前端先传 status 后传 orderNo，导出仍按目录顺序（采购单号在前）
                assertThat(head).containsExactly("采购单号", "状态");
            }

            @Test
            @DisplayName("未知 key 直接忽略；勾选全部未知时回退整目录，绝不产出空表")
            void unknownKeysIgnoredAndFallbackToFullCatalog() {
                List<String> subset = PurchaseOrderExportSupport.head(List.of("orderNo", "notAColumn")).stream()
                        .map(PurchaseOrderExportSupportTest::title).toList();
                assertThat(subset).containsExactly("采购单号");

                assertThat(PurchaseOrderExportSupport.head(List.of("ghost")))
                        .hasSameSizeAs(PurchaseOrderExportSupport.head(List.of()));
            }

            @Test
            @DisplayName("行数据：null 单元格落空串，且每行单元格数与表头严格相等")
            void rowsAlignWithHeadAndNullBecomesEmpty() {
                // 目录顺序：orderNo → supplierName → status（前端勾选顺序不改变列顺序）
                List<List<Object>> rows = PurchaseOrderExportSupport.rows(
                        List.of("orderNo", "status", "supplierName"),
                        List.of(order("PO-1", "SUBMITTED"), order(null, "RECEIVED")));

                assertThat(rows).hasSize(2);
                // 状态列落中文名：导出是服务端生成的 xlsx，没有前端可翻译
                assertThat(rows.get(0)).containsExactly("PO-1", "", "已提交");
                // orderNo 为 null → 空串，不是字符串 "null"；supplierName 未赋值同样落空串
                assertThat(rows.get(1)).containsExactly("", "", "已收货");
                assertThat(rows.getFirst()).hasSameSizeAs(PurchaseOrderExportSupport.head(
                        List.of("orderNo", "status", "supplierName")));
            }

            @Test
            @DisplayName("状态列导出中文名；认不出的值原样回落，不吞数据")
            void statusExportsChineseLabelAndKeepsUnknownValue() {
                List<List<Object>> rows = PurchaseOrderExportSupport.rows(
                        List.of("status"),
                        List.of(order("PO-1", "PARTIALLY_RECEIVED"), order("PO-2", "LEGACY_VALUE")));

                assertThat(rows.get(0)).containsExactly("部分收货");
                assertThat(rows.get(1)).containsExactly("LEGACY_VALUE");
            }
        }

        @Nested

        class PurchaseOrderItemChangeSetTest {
            private static int codeOf(Throwable t) {
                return ((ScmBusinessException) t).getErrorCode().getCode();
            }

            /**
             * 已存在的行（id 非空、带版本、带创建审计）。
             */
            private static PurchaseOrderItemEntity existing(Long id, Long skuId, int version) {
                PurchaseOrderItemEntity row = new PurchaseOrderItemEntity();
                row.setId(id);
                row.setSkuId(skuId);
                row.setVersion(version);
                row.setReceivedQuantity(new BigDecimal("0.0000"));
                row.setCreatedAt(OffsetDateTime.parse("2026-09-01T10:00:00+08:00"));
                row.setCreatedBy("creator");
                return row;
            }

            /**
             * 请求中的保留行（带 id + version）。
             */
            private static PurchaseOrderItemEntity retained(Long id, Long skuId, Integer version) {
                PurchaseOrderItemEntity row = new PurchaseOrderItemEntity();
                row.setId(id);
                row.setSkuId(skuId);
                row.setVersion(version);
                return row;
            }

            /**
             * 请求中的新增行（无 id）。
             */
            private static PurchaseOrderItemEntity fresh(Long skuId) {
                PurchaseOrderItemEntity row = new PurchaseOrderItemEntity();
                row.setSkuId(skuId);
                return row;
            }

            @Test
            @DisplayName("保留 + 新增：按 id 归入 updated / inserted，互不混淆")
            void classifiesRetainedAndInserted() {
                PurchaseOrderItemEntity old = existing(7L, 100L, 2);
                PurchaseOrderItemEntity kept = retained(7L, 100L, 2);
                PurchaseOrderItemEntity added = fresh(200L);

                PurchaseOrderItemChangeSet diff =
                        PurchaseOrderItemChangeSet.between(List.of(old), List.of(kept, added));

                assertThat(diff.updated()).containsExactly(kept);
                assertThat(diff.inserted()).containsExactly(added);
                assertThat(diff.removed()).isEmpty();
            }

            @Test
            @DisplayName("保留行沿用原有创建审计信息（编辑不是新建）")
            void retainedRowInheritsAudit() {
                PurchaseOrderItemEntity old = existing(7L, 100L, 2);
                PurchaseOrderItemEntity kept = retained(7L, 100L, 2);
                PurchaseOrderItemChangeSet.between(List.of(old), List.of(kept));
                assertThat(kept.getCreatedAt()).isEqualTo(old.getCreatedAt());
                assertThat(kept.getCreatedBy()).isEqualTo("creator");
            }

            @Test
            @DisplayName("旧集合中未被请求引用的行 → removed")
            void unreferencedExistingRowIsRemoved() {
                PurchaseOrderItemEntity a = existing(1L, 100L, 0);
                PurchaseOrderItemEntity b = existing(2L, 200L, 0);
                PurchaseOrderItemChangeSet diff =
                        PurchaseOrderItemChangeSet.between(List.of(a, b), List.of(retained(1L, 100L, 0)));
                assertThat(diff.removed()).containsExactly(b);
                assertThat(diff.inserted()).isEmpty();
            }

            @Test
            @DisplayName("请求内同一 skuId 出现两次 → 40997（uk_purchase_order_item_order_sku_active 的前置拦截）")
            void duplicateSkuInRequestRejected() {
                assertThatThrownBy(() -> PurchaseOrderItemChangeSet.between(
                        List.of(), List.of(fresh(100L), fresh(100L))))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40997));
            }

            @Test
            @DisplayName("保留行与新增行撞同一 skuId → 40997（跨 id 维度也去重）")
            void duplicateSkuAcrossRetainedAndInsertedRejected() {
                assertThatThrownBy(() -> PurchaseOrderItemChangeSet.between(
                        List.of(existing(7L, 100L, 0)), List.of(retained(7L, 100L, 0), fresh(100L))))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40997));
            }

            @Test
            @DisplayName("skuId 为空 → 40997（不允许无商品的行）")
            void nullSkuRejected() {
                assertThatThrownBy(() -> PurchaseOrderItemChangeSet.between(List.of(), List.of(fresh(null))))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40997));
            }

            @Test
            @DisplayName("保留行的 id 不属于本单 → 40983（防止越单改行）")
            void foreignItemIdRejected() {
                assertThatThrownBy(() -> PurchaseOrderItemChangeSet.between(
                        List.of(existing(7L, 100L, 0)), List.of(retained(99L, 100L, 0))))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40983));
            }

            @Test
            @DisplayName("保留行的 id 与 skuId 不自洽 → 40983（不允许悄悄换商品）")
            void retainedIdSkuMismatchRejected() {
                assertThatThrownBy(() -> PurchaseOrderItemChangeSet.between(
                        List.of(existing(7L, 100L, 0)), List.of(retained(7L, 999L, 0))))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40983));
            }

            @Test
            @DisplayName("保留行缺 version → 40088（乐观锁必须显式携带）")
            void missingVersionRejected() {
                assertThatThrownBy(() -> PurchaseOrderItemChangeSet.between(
                        List.of(existing(7L, 100L, 2)), List.of(retained(7L, 100L, null))))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40088));
            }

            @Test
            @DisplayName("保留行 version 过期 → 40984（并发编辑被拒）")
            void staleVersionRejected() {
                assertThatThrownBy(() -> PurchaseOrderItemChangeSet.between(
                        List.of(existing(7L, 100L, 2)), List.of(retained(7L, 100L, 1))))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40984));
            }

            @Test
            @DisplayName("删除已收货的行 → 40982（不能抹掉已发生的收货记录）")
            void removingReceivedRowRejected() {
                PurchaseOrderItemEntity received = existing(7L, 100L, 0);
                received.setReceivedQuantity(new BigDecimal("5.0000"));
                assertThatThrownBy(() -> PurchaseOrderItemChangeSet.between(List.of(received), List.of()))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40982));
            }

            @Test
            @DisplayName("删除未收货的行 → 允许；空请求即整单清空行")
            void removingUnreceivedRowAllowed() {
                PurchaseOrderItemEntity untouched = existing(7L, 100L, 0);
                PurchaseOrderItemChangeSet diff =
                        PurchaseOrderItemChangeSet.between(List.of(untouched), List.of());
                assertThat(diff.removed()).containsExactly(untouched);
                assertThat(diff.inserted()).isEmpty();
                assertThat(diff.updated()).isEmpty();
            }
        }

        @Nested

        class PurchaseOrderValidatorTest {
            private final SupplierService supplierService = mock(SupplierService.class);

            private final SupplierSkuService supplierSkuService = mock(SupplierSkuService.class);

            private final WarehouseService warehouseService = mock(WarehouseService.class);

            private final PurchaseOrderValidator validator =
                    new PurchaseOrderValidator(supplierService, supplierSkuService, warehouseService);

            private static int codeOf(Throwable t) {
                return ((ScmBusinessException) t).getErrorCode().getCode();
            }

            private static PurchaseOrderAddForm baseForm() {
                PurchaseOrderAddForm form = new PurchaseOrderAddForm();
                form.setSupplierId(1L);
                form.setWarehouseId(1L);
                form.setItems(List.of(item(100L, "3.0000", "2.5000", null)));
                return form;
            }

            private static PurchaseOrderAddForm.Item item(Long skuId, String quantity, String price,
                                                          List<PurchaseOrderAddForm.Allocation> allocations) {
                PurchaseOrderAddForm.Item row = new PurchaseOrderAddForm.Item();
                row.setSkuId(skuId);
                row.setQuantity(quantity);
                row.setPrice(price);
                row.setAllocations(allocations);
                return row;
            }

            private static PurchaseOrderAddForm.Allocation allocation(Long demandId, String quantity, Integer version) {
                PurchaseOrderAddForm.Allocation row = new PurchaseOrderAddForm.Allocation();
                row.setDemandId(demandId);
                row.setQuantity(quantity);
                row.setDemandVersion(version);
                return row;
            }

            // ------------------------------------------------------------------
            // 归一化与必填
            // ------------------------------------------------------------------

            @Test
            @DisplayName("trim：空白视作 null（以便真正清空列）")
            void trimNormalizesBlankToNull() {
                assertThat(PurchaseOrderValidator.trim("  采购备注  ")).isEqualTo("采购备注");
                assertThat(PurchaseOrderValidator.trim("")).isNull();
                assertThat(PurchaseOrderValidator.trim("   ")).isNull();
                assertThat(PurchaseOrderValidator.trim(null)).isNull();
            }

            @Test
            @DisplayName("reason：原因类字段空白 → 传入的专用错误码（不是通用 40000）")
            void reasonUsesDedicatedCode() {
                assertThatThrownBy(() -> PurchaseOrderValidator.reason(
                        "  ", PurchaseErrorCode.PURCHASE_CANCEL_REASON_REQUIRED))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40086));
                assertThatThrownBy(() -> PurchaseOrderValidator.reason(
                        null, PurchaseErrorCode.PURCHASE_SHORT_CLOSE_REASON_REQUIRED))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40087));
                assertThatCode(() -> PurchaseOrderValidator.reason(
                        "供应商临时缺货", PurchaseErrorCode.PURCHASE_CANCEL_REASON_REQUIRED))
                        .doesNotThrowAnyException();
            }

            // ------------------------------------------------------------------
            // 4 位定点字符串形态
            // ------------------------------------------------------------------

            @ParameterizedTest
            @ValueSource(strings = {"1", "1.000", "1.00000", "-1.0000", "0.0000", "1e4", "100000000000000.0000", "1.0000 "})
            @DisplayName("数量：非「[0-9]{1,14}.0000」形态或非正 → 40080")
            void invalidQuantityRejected(String value) {
                assertThatThrownBy(() -> PurchaseOrderValidator.decimal(value, true))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40080));
            }

            @Test
            @DisplayName("单价：允许 0.0000（免费赠品），负数与非 4 位小数 → 40081")
            void priceRules() {
                assertThat(PurchaseOrderValidator.decimal("0.0000", false)).isEqualByComparingTo("0.0000");
                assertThat(PurchaseOrderValidator.decimal("99999999999999.9999", false))
                        .isEqualByComparingTo("99999999999999.9999");
                assertThatThrownBy(() -> PurchaseOrderValidator.decimal("-0.0001", false))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40081));
                assertThatThrownBy(() -> PurchaseOrderValidator.decimal("2.50", false))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40081));
            }

            // ------------------------------------------------------------------
            // draft：行与分配的表单规则
            // ------------------------------------------------------------------

            @Test
            @DisplayName("draft：至少一行（40089）；SKU 非空且不重复（40997）")
            void draftRequiresNonEmptyDistinctItems() {
                PurchaseOrderAddForm form = baseForm();
                assertThatCode(() -> PurchaseOrderValidator.draft(form)).doesNotThrowAnyException();

                form.setItems(List.of());
                assertThatThrownBy(() -> PurchaseOrderValidator.draft(form))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40089));

                form.setItems(null);
                assertThatThrownBy(() -> PurchaseOrderValidator.draft(form))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40089));

                form.setItems(List.of(item(100L, "1.0000", "1.0000", null), item(100L, "1.0000", "1.0000", null)));
                assertThatThrownBy(() -> PurchaseOrderValidator.draft(form))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40997));

                form.setItems(List.of(item(null, "1.0000", "1.0000", null)));
                assertThatThrownBy(() -> PurchaseOrderValidator.draft(form))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40997));
            }

            @Test
            @DisplayName("draft：同一行重复关联同一需求 → 40090；缺 demandVersion → 40091；分配数量非法 → 40080")
            void draftAllocationRules() {
                PurchaseOrderAddForm form = baseForm();
                form.setItems(List.of(item(100L, "3.0000", "2.5000",
                        List.of(allocation(101L, "1.0000", 0)))));
                assertThatCode(() -> PurchaseOrderValidator.draft(form)).doesNotThrowAnyException();

                form.setItems(List.of(item(100L, "3.0000", "2.5000",
                        List.of(allocation(101L, "1.0000", 0), allocation(101L, "2.0000", 0)))));
                assertThatThrownBy(() -> PurchaseOrderValidator.draft(form))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40090));

                form.setItems(List.of(item(100L, "3.0000", "2.5000",
                        List.of(allocation(101L, "1.0000", null)))));
                assertThatThrownBy(() -> PurchaseOrderValidator.draft(form))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40091));

                form.setItems(List.of(item(100L, "3.0000", "2.5000",
                        List.of(allocation(101L, "0.0000", 0)))));
                assertThatThrownBy(() -> PurchaseOrderValidator.draft(form))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40080));
            }

            @Test
            @DisplayName("draft：无分配的行合法（允许先建单后分配）")
            void draftAllowsItemWithoutAllocation() {
                PurchaseOrderAddForm form = baseForm();
                form.setItems(List.of(item(100L, "3.0000", "2.5000", null),
                        item(200L, "1.0000", "0.0000", List.of())));
                assertThatCode(() -> PurchaseOrderValidator.draft(form)).doesNotThrowAnyException();
            }

            // ------------------------------------------------------------------
            // 引用校验（需要主数据）
            // ------------------------------------------------------------------

            @Test
            @DisplayName("供应商：不存在走 W2 的 40440；已停用 → 40986（采购侧规则）")
            void supplierMustBeEnabled() {
                SupplierEntity enabled = new SupplierEntity();
                enabled.setStatus(ScmEnableStatusEnum.ENABLED.name());
                when(supplierService.require(1L)).thenReturn(enabled);
                assertThat(validator.requireEnabledSupplier(1L)).isSameAs(enabled);

                SupplierEntity disabled = new SupplierEntity();
                disabled.setStatus(ScmEnableStatusEnum.DISABLED.name());
                when(supplierService.require(2L)).thenReturn(disabled);
                assertThatThrownBy(() -> validator.requireEnabledSupplier(2L))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40986));
            }

            @Test
            @DisplayName("仓库：不存在走 40485；已停用 → 40987")
            void warehouseMustBeEnabled() {
                WarehouseEntity enabled = new WarehouseEntity();
                enabled.setStatus(ScmEnableStatusEnum.ENABLED.name());
                when(warehouseService.require(1L)).thenReturn(enabled);
                assertThat(validator.requireEnabledWarehouse(1L)).isSameAs(enabled);

                WarehouseEntity disabled = new WarehouseEntity();
                disabled.setStatus(ScmEnableStatusEnum.DISABLED.name());
                when(warehouseService.require(2L)).thenReturn(disabled);
                assertThatThrownBy(() -> validator.requireEnabledWarehouse(2L))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40987));
            }

            @Test
            @DisplayName("防腐层：W2 的 requireEnabledForPurchasing 失败码被翻译成 40992（W5 只暴露自己的码）")
            void w2FailureIsTranslatedToPurchaseCode() {
                SupplierSkuEntity sku = new SupplierSkuEntity();
                sku.setPurchaseUnit("箱");
                when(supplierSkuService.requireEnabledForPurchasing(1L, 100L)).thenReturn(sku);
                assertThat(validator.requirePurchasableSku(1L, 100L).getPurchaseUnit()).isEqualTo("箱");

                // 供应侧的真实失败码（40442 / 40940 / 40942）对采购域调用方不可见
                when(supplierSkuService.requireEnabledForPurchasing(1L, 200L))
                        .thenThrow(new ScmBusinessException(SupplierErrorCode.SUPPLIER_DISABLED));
                assertThatThrownBy(() -> validator.requirePurchasableSku(1L, 200L))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40992));
                when(supplierSkuService.requireEnabledForPurchasing(1L, 300L))
                        .thenThrow(new ScmBusinessException(SupplierErrorCode.SUPPLIER_SKU_NOT_FOUND));
                assertThatThrownBy(() -> validator.requirePurchasableSku(1L, 300L))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40992));
            }
        }

        @Nested

        class PurchaseReceiptQuantityCalculatorTest {
            private static int codeOf(Throwable t) {
                return ((ScmBusinessException) t).getErrorCode().getCode();
            }

            private static BigDecimal q(String value) {
                return new BigDecimal(value);
            }

            // ------------------------------------------------------------------
            // 容差配置解析（缺失回退 / 合法 / 非法 / 越界）
            // ------------------------------------------------------------------

            @ParameterizedTest(name = "raw=[{0}] -> {1}")
            @CsvSource({
                    "10,10",
                    "0,0",
                    "100,100",
                    "'  15 ',15",
                    "'007',7"
            })
            @DisplayName("Q3a：合法容差原样解析（含首尾空白与补零）")
            void toleranceParsesLegalValues(String raw, int expected) {
                assertThat(PurchaseReceiptQuantityCalculator.tolerance(raw)).isEqualTo(expected);
            }

            @Test
            @DisplayName("Q3a：配置缺失（null / 空白）回退默认 10 —— 未配置环境必须仍能收货")
            void toleranceFallsBackToDefaultWhenMissing() {
                int fallback = Integer.parseInt(PurchaseConfigKey.OVER_RECEIPT_TOLERANCE_PERCENT_DEFAULT);
                assertThat(fallback).isEqualTo(10);
                assertThat(PurchaseReceiptQuantityCalculator.tolerance(null)).isEqualTo(fallback);
                assertThat(PurchaseReceiptQuantityCalculator.tolerance("")).isEqualTo(fallback);
                assertThat(PurchaseReceiptQuantityCalculator.tolerance("   ")).isEqualTo(fallback);
            }

            @ParameterizedTest(name = "raw=[{0}]")
            @ValueSource(strings = {"-1", "101", "abc", "10.5", "1e2", "10%", "+ 10", "99999999999999999999"})
            @DisplayName("Q3a：非整数 / 越界 / 溢出 → 40999（绝不静默回退）")
            void toleranceRejectsIllegalValues(String raw) {
                assertThatThrownBy(() -> PurchaseReceiptQuantityCalculator.tolerance(raw))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40999));
            }

            @Test
            @DisplayName("Q3a：解析规则是「trim + Integer.parseInt」——前导 + 与首尾空白被接受，这是有意为之的宽松")
            void toleranceParsingIsIntentionallyLenientAboutSignAndWhitespace() {
                // Integer.parseInt 接受前导 '+'，因此 "+10" 视为合法整数 10（不是非法值）
                assertThat(PurchaseReceiptQuantityCalculator.tolerance("+10")).isEqualTo(10);
                assertThat(PurchaseReceiptQuantityCalculator.tolerance("\t10\n")).isEqualTo(10);
            }

            @Test
            @DisplayName("Q3a：范围端点 0 与 100 是闭区间（不越界）")
            void toleranceRangeIsInclusive() {
                assertThat(PurchaseConfigKey.OVER_RECEIPT_TOLERANCE_PERCENT_MIN).isZero();
                assertThat(PurchaseConfigKey.OVER_RECEIPT_TOLERANCE_PERCENT_MAX).isEqualTo(100);
                assertThat(PurchaseReceiptQuantityCalculator.tolerance("0")).isZero();
                assertThat(PurchaseReceiptQuantityCalculator.tolerance("100")).isEqualTo(100);
            }

            // ------------------------------------------------------------------
            // ceiling / available
            // ------------------------------------------------------------------

            @Test
            @DisplayName("ceiling = planned × (1 + tolerance/100)，容差 0 / 10 / 100")
            void ceilingScalesByTolerance() {
                assertThat(PurchaseReceiptQuantityCalculator.ceiling(q("100.0000"), 0)).isEqualByComparingTo("100.0000");
                assertThat(PurchaseReceiptQuantityCalculator.ceiling(q("100.0000"), 10)).isEqualByComparingTo("110.0000");
                assertThat(PurchaseReceiptQuantityCalculator.ceiling(q("100.0000"), 100)).isEqualByComparingTo("200.0000");
            }

            @Test
            @DisplayName("ceiling 不做 4 位取整：小数计划量保持精确（避免边界放行歧义）")
            void ceilingKeepsExactPrecision() {
                BigDecimal ceiling = PurchaseReceiptQuantityCalculator.ceiling(q("3.3333"), 10);
                assertThat(ceiling).isEqualByComparingTo("3.66663");
                assertThat(ceiling.scale()).isGreaterThan(PurchaseAmountCalculator.SCALE);
            }

            @Test
            @DisplayName("available = ceiling − 已累计收货；未收过时已收量按 0 处理")
            void availableSubtractsCumulative() {
                assertThat(PurchaseReceiptQuantityCalculator.available(q("110.0000"), q("10.0000")))
                        .isEqualByComparingTo("100.0000");
                assertThat(PurchaseReceiptQuantityCalculator.available(q("110.0000"), null))
                        .isEqualByComparingTo("110.0000");
                assertThat(PurchaseReceiptQuantityCalculator.available(q("110.0000"), q("110.0000")))
                        .isEqualByComparingTo("0.0000");
            }

            // ------------------------------------------------------------------
            // 对账恒等式
            // ------------------------------------------------------------------

            @Test
            @DisplayName("P24：remaining = GREATEST(planned − cumulative, 0)")
            void remainingIsFlooredAtZero() {
                assertThat(PurchaseReceiptQuantityCalculator.remaining(q("100.0000"), q("40.0000")))
                        .isEqualByComparingTo("60.0000");
                assertThat(PurchaseReceiptQuantityCalculator.remaining(q("100.0000"), q("100.0000")))
                        .isEqualByComparingTo("0.0000");
                // 超收后剩余仍是 0，而不是负数
                assertThat(PurchaseReceiptQuantityCalculator.remaining(q("100.0000"), q("120.0000")))
                        .isEqualByComparingTo("0.0000");
            }

            @Test
            @DisplayName("P24：over_receipt = GREATEST(cumulative − planned, 0)")
            void overReceiptIsFlooredAtZero() {
                assertThat(PurchaseReceiptQuantityCalculator.overReceipt(q("100.0000"), q("80.0000")))
                        .isEqualByComparingTo("0.0000");
                assertThat(PurchaseReceiptQuantityCalculator.overReceipt(q("100.0000"), q("100.0000")))
                        .isEqualByComparingTo("0.0000");
                assertThat(PurchaseReceiptQuantityCalculator.overReceipt(q("100.0000"), q("120.0000")))
                        .isEqualByComparingTo("20.0000");
            }

            @Test
            @DisplayName("P24：receipt_difference = cumulative − planned，**可为负**")
            void differenceIsSigned() {
                assertThat(PurchaseReceiptQuantityCalculator.difference(q("100.0000"), q("80.0000")))
                        .isEqualByComparingTo("-20.0000");
                assertThat(PurchaseReceiptQuantityCalculator.difference(q("100.0000"), q("100.0000")))
                        .isEqualByComparingTo("0.0000");
                assertThat(PurchaseReceiptQuantityCalculator.difference(q("100.0000"), q("120.0000")))
                        .isEqualByComparingTo("20.0000");
            }

            @Test
            @DisplayName("P24：三条恒等式同时成立（over − remaining == difference）")
            void identitiesAreConsistent() {
                BigDecimal planned = q("100.0000");
                for (String cumulative : new String[]{"0.0000", "37.5000", "100.0000", "120.2500"}) {
                    BigDecimal c = q(cumulative);
                    BigDecimal remaining = PurchaseReceiptQuantityCalculator.remaining(planned, c);
                    BigDecimal over = PurchaseReceiptQuantityCalculator.overReceipt(planned, c);
                    BigDecimal difference = PurchaseReceiptQuantityCalculator.difference(planned, c);
                    // remaining = max(planned−cum,0)，over = max(cum−planned,0)，两者最多一个非零，
                    // 且 over − remaining = cum − planned = difference
                    assertThat(over.subtract(remaining)).as("cumulative=%s", cumulative).isEqualByComparingTo(difference);
                    assertThat(remaining.signum() == 0 || over.signum() == 0)
                            .as("cumulative=%s：remaining 与 over 不可同时为正", cumulative).isTrue();
                }
            }

            // ------------------------------------------------------------------
            // 标品 vs 非标品的有效数量
            // ------------------------------------------------------------------

            @Test
            @DisplayName("标品：有效数量 = declared_quantity；实重 / 称重来源 / 修正原因必须全空")
            void standardProductUsesDeclaredQuantity() {
                assertThat(PurchaseReceiptQuantityCalculator.effectiveQuantity(
                        "STANDARD", q("12.5000"), null, null, null)).isEqualByComparingTo("12.5000");
            }

            @Test
            @DisplayName("标品：带实重 / 称重来源 / 修正原因 → 40083（三字段同生同灭）")
            void standardProductRejectsWeightFields() {
                assertThatThrownBy(() -> PurchaseReceiptQuantityCalculator.effectiveQuantity(
                        "STANDARD", q("12.5000"), q("12.5000"), null, null))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40083));
                assertThatThrownBy(() -> PurchaseReceiptQuantityCalculator.effectiveQuantity(
                        "STANDARD", q("12.5000"), null, "MANUAL", null))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40083));
                assertThatThrownBy(() -> PurchaseReceiptQuantityCalculator.effectiveQuantity(
                        "STANDARD", q("12.5000"), null, null, "更正"))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40083));
            }

            @Test
            @DisplayName("标品：declared 缺失或非正 → 40083")
            void standardProductRequiresPositiveDeclared() {
                assertThatThrownBy(() -> PurchaseReceiptQuantityCalculator.effectiveQuantity(
                        "STANDARD", null, null, null, null))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40083));
                assertThatThrownBy(() -> PurchaseReceiptQuantityCalculator.effectiveQuantity(
                        "STANDARD", q("0.0000"), null, null, null))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40083));
            }

            @Test
            @DisplayName("非标品：有效数量 = actual_weight（必填且 > 0），weighing_source 必须 MANUAL")
            void nonStandardProductUsesActualWeight() {
                assertThat(PurchaseReceiptQuantityCalculator.effectiveQuantity(
                        "NON_STANDARD", null, q("48.2000"), "MANUAL", null)).isEqualByComparingTo("48.2000");
            }

            @Test
            @DisplayName("非标品：实重缺失 / 非正 / DEVICE → 40083（DEVICE 是 G-05 扩展点，W5 不开放）")
            void nonStandardProductRejectsBadWeight() {
                assertThatThrownBy(() -> PurchaseReceiptQuantityCalculator.effectiveQuantity(
                        "NON_STANDARD", null, null, "MANUAL", null))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40083));
                assertThatThrownBy(() -> PurchaseReceiptQuantityCalculator.effectiveQuantity(
                        "NON_STANDARD", null, q("0.0000"), "MANUAL", null))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40083));
                assertThatThrownBy(() -> PurchaseReceiptQuantityCalculator.effectiveQuantity(
                        "NON_STANDARD", null, q("48.2000"), "DEVICE", null))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40083));
                assertThatThrownBy(() -> PurchaseReceiptQuantityCalculator.effectiveQuantity(
                        "NON_STANDARD", null, q("48.2000"), null, null))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40083));
            }

            @Test
            @DisplayName("未知商品类型 → 40083（不做「默认按标品」的静默兜底）")
            void unknownProductTypeRejected() {
                assertThatThrownBy(() -> PurchaseReceiptQuantityCalculator.effectiveQuantity(
                        "SEMI_STANDARD", q("1.0000"), null, null, null))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40083));
                assertThatThrownBy(() -> PurchaseReceiptQuantityCalculator.effectiveQuantity(
                        null, q("1.0000"), null, null, null))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40083));
            }

            // ------------------------------------------------------------------
            // 收货数量 / 实重的定点解析（错误码必须是 40083，不是采购侧的 40080）
            // ------------------------------------------------------------------

            @Test
            @DisplayName("收货数量解析：4 位定点字符串原样通过，数值语义不变")
            void declaredAcceptsFourScaleStrings() {
                assertThat(PurchaseReceiptQuantityCalculator.declared("1000.0000")).isEqualByComparingTo("1000.0000");
                assertThat(PurchaseReceiptQuantityCalculator.declared("0.0001")).isEqualByComparingTo("0.0001");
                assertThat(PurchaseReceiptQuantityCalculator.declared("99999999999999.9999"))
                        .isEqualByComparingTo("99999999999999.9999");
            }

            @Test
            @DisplayName("收货数量解析：缺失 / 小数位不符 / 负数 / 零 → 40083（不是采购侧的 40080）")
            void declaredRejectsBadShapeWithReceiptCode() {
                // 与 PurchaseOrderValidator.decimal 的差别只有失败码：40083 vs 40080。
                // 前端要能区分「建单时数量写错」与「收货时数量写错」，所以这里必须逐例锁死。
                for (String bad : new String[]{null, "", "1", "1.000", "1.00000", "-1.0000", "0.0000",
                        "1e2", "1.0000%", " 1.0000"}) {
                    assertThatThrownBy(() -> PurchaseReceiptQuantityCalculator.declared(bad))
                            .as("bad value: [%s]", bad)
                            .isInstanceOfSatisfying(ScmBusinessException.class,
                                    e -> assertThat(codeOf(e)).isEqualTo(40083));
                }
            }
        }

        @Nested

        class PurchaseRulesTest {
            private static int codeOf(Throwable t) {
                return ((ScmBusinessException) t).getErrorCode().getCode();
            }

            // ------------------------------------------------------------------
            // 状态图：6 状态 × 6 状态的全覆盖（合法 + 非法）
            // ------------------------------------------------------------------

            @ParameterizedTest(name = "{0} -> {1} = {2}")
            @CsvSource({
                    // SUBMITTED（新建即提交）
                    "SUBMITTED,SUBMITTED,false",
                    "SUBMITTED,ACCOUNTED,true",
                    "SUBMITTED,PARTIALLY_RECEIVED,true",
                    "SUBMITTED,RECEIVED,true",
                    "SUBMITTED,SHORT_CLOSED,false",
                    "SUBMITTED,CANCELLED,true",
                    // PARTIALLY_RECEIVED（自身到自身合法：第二次收货后仍是部分收货）
                    "PARTIALLY_RECEIVED,SUBMITTED,false",
                    "PARTIALLY_RECEIVED,ACCOUNTED,false",
                    "PARTIALLY_RECEIVED,PARTIALLY_RECEIVED,true",
                    "PARTIALLY_RECEIVED,RECEIVED,true",
                    "PARTIALLY_RECEIVED,SHORT_CLOSED,true",
                    // 已部分收货不允许 cancel，需要终止时用 short-close
                    "PARTIALLY_RECEIVED,CANCELLED,false",
                    // 终态
                    "ACCOUNTED,SUBMITTED,false",
                    "ACCOUNTED,RECEIVED,false",
                    "RECEIVED,RECEIVED,false",
                    "RECEIVED,CANCELLED,false",
                    "SHORT_CLOSED,RECEIVED,false",
                    "CANCELLED,SUBMITTED,false"
            })
            @DisplayName("状态转换表：只有 §4.2 列出的 (from,to) 合法")
            void stateGraph(String from, String to, boolean allowed) {
                assertThat(PurchaseOrderStateMachine.canTransition(from, to)).isEqualTo(allowed);
            }

            @Test
            @DisplayName("未知 / null 状态一律不可转换（白名单防护）")
            void unknownStateNeverTransitions() {
                assertThat(PurchaseOrderStateMachine.canTransition(null, "SUBMITTED")).isFalse();
                assertThat(PurchaseOrderStateMachine.canTransition("", "SUBMITTED")).isFalse();
                assertThat(PurchaseOrderStateMachine.canTransition("SUBMITTED", null)).isFalse();
                assertThat(PurchaseOrderStateMachine.canTransition("ARCHIVED", "SUBMITTED")).isFalse();
            }

            @Test
            @DisplayName("非法转换抛 40982（不是 40000，也不是 DB 异常）")
            void illegalTransitionThrowsStateInvalid() {
                assertThatThrownBy(() -> PurchaseOrderStateMachine.transition("RECEIVED", "CANCELLED"))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40982));
                assertThatThrownBy(() -> PurchaseOrderStateMachine.transition("PARTIALLY_RECEIVED", "CANCELLED"))
                        .isInstanceOfSatisfying(ScmBusinessException.class, e -> assertThat(codeOf(e)).isEqualTo(40982));
                assertThatCode(() -> PurchaseOrderStateMachine.transition("SUBMITTED", "ACCOUNTED"))
                        .doesNotThrowAnyException();
            }

            // ------------------------------------------------------------------
            // 状态谓词
            // ------------------------------------------------------------------

            @Test
            @DisplayName("只有 SUBMITTED 可编辑行与需求分配（T2）")
            void editableOnlySubmitted() {
                assertThat(PurchaseOrderStateMachine.editable("SUBMITTED")).isTrue();
                assertThat(PurchaseOrderStateMachine.editable("ACCOUNTED")).isFalse();
                assertThat(PurchaseOrderStateMachine.editable("PARTIALLY_RECEIVED")).isFalse();
                assertThat(PurchaseOrderStateMachine.editable("RECEIVED")).isFalse();
                assertThat(PurchaseOrderStateMachine.editable("SHORT_CLOSED")).isFalse();
                assertThat(PurchaseOrderStateMachine.editable("CANCELLED")).isFalse();
                assertThat(PurchaseOrderStateMachine.editable(null)).isFalse();
            }

            @Test
            @DisplayName("可建 / 可确认收货：SUBMITTED 或 PARTIALLY_RECEIVED（T7 / T8）")
            void receivableSubmittedOrPartiallyReceived() {
                assertThat(PurchaseOrderStateMachine.receivable("ACCOUNTED")).isFalse();
                assertThat(PurchaseOrderStateMachine.receivable("SUBMITTED")).isTrue();
                assertThat(PurchaseOrderStateMachine.receivable("PARTIALLY_RECEIVED")).isTrue();
                assertThat(PurchaseOrderStateMachine.receivable("RECEIVED")).isFalse();
                assertThat(PurchaseOrderStateMachine.receivable("SHORT_CLOSED")).isFalse();
                assertThat(PurchaseOrderStateMachine.receivable("CANCELLED")).isFalse();
            }

            @Test
            @DisplayName("可取消：仅 SUBMITTED；P14 明确排除 PARTIALLY_RECEIVED（T4）")
            void cancellableExcludesPartiallyReceived() {
                assertThat(PurchaseOrderStateMachine.cancellable("ACCOUNTED")).isFalse();
                assertThat(PurchaseOrderStateMachine.cancellable("SUBMITTED")).isTrue();
                assertThat(PurchaseOrderStateMachine.cancellable("PARTIALLY_RECEIVED")).isFalse();
                assertThat(PurchaseOrderStateMachine.cancellable("RECEIVED")).isFalse();
                assertThat(PurchaseOrderStateMachine.cancellable("CANCELLED")).isFalse();
            }

            @Test
            @DisplayName("可少收关单：仅 PARTIALLY_RECEIVED（T5 / Q2a）")
            void shortClosableOnlyPartiallyReceived() {
                assertThat(PurchaseOrderStateMachine.shortClosable("ACCOUNTED")).isFalse();
                assertThat(PurchaseOrderStateMachine.shortClosable("SUBMITTED")).isFalse();
                assertThat(PurchaseOrderStateMachine.shortClosable("PARTIALLY_RECEIVED")).isTrue();
                assertThat(PurchaseOrderStateMachine.shortClosable("RECEIVED")).isFalse();
                assertThat(PurchaseOrderStateMachine.shortClosable("SHORT_CLOSED")).isFalse();
            }

            @Test
            @DisplayName("终态：RECEIVED / SHORT_CLOSED / CANCELLED")
            void terminalStates() {
                assertThat(PurchaseOrderStateMachine.terminal("ACCOUNTED")).isTrue();
                assertThat(PurchaseOrderStateMachine.terminal("SUBMITTED")).isFalse();
                assertThat(PurchaseOrderStateMachine.terminal("PARTIALLY_RECEIVED")).isFalse();
                assertThat(PurchaseOrderStateMachine.terminal("RECEIVED")).isTrue();
                assertThat(PurchaseOrderStateMachine.terminal("SHORT_CLOSED")).isTrue();
                assertThat(PurchaseOrderStateMachine.terminal("CANCELLED")).isTrue();
            }

            @Test
            @DisplayName("收货确认后状态由「是否全部行收齐」推导（T7）")
            void afterReceiptDerivesStatus() {
                assertThat(PurchaseOrderStateMachine.afterReceipt(true)).isEqualTo("RECEIVED");
                assertThat(PurchaseOrderStateMachine.afterReceipt(false)).isEqualTo("PARTIALLY_RECEIVED");
            }

            // ------------------------------------------------------------------
            // 三张状态表的取值域（与 V15 的 ck_* 白名单由 ScmPurchaseStatusEnumTest 复核）
            // ------------------------------------------------------------------

            @Test
            @DisplayName("状态枚举取值域固定：采购单 6 / 需求 3 / 收货单 2")
            void statusEnumsAreFrozen() {
                assertThat(ScmPurchaseStatusEnum.values())
                        .extracting(Enum::name)
                        .containsExactly("SUBMITTED", "ACCOUNTED", "PARTIALLY_RECEIVED", "RECEIVED", "SHORT_CLOSED", "CANCELLED");
                assertThat(ScmPurchaseDemandStatusEnum.values())
                        .extracting(Enum::name)
                        .containsExactly("PENDING", "PARTIALLY_ALLOCATED", "ALLOCATED");
                assertThat(ScmReceiptStatusEnum.values())
                        .extracting(Enum::name)
                        .containsExactly("PENDING", "CONFIRMED");
            }

            // ------------------------------------------------------------------
            // demand_date 必须按 Asia/Shanghai 派生，而不是 UTC 日期
            // ------------------------------------------------------------------

            @Test
            @DisplayName("Q6a：demand_date = source_confirmed_at 在 Asia/Shanghai 下的 LocalDate")
            void demandDateUsesAsiaShanghai() {
                // UTC 16:00 == 上海次日 00:00 —— 若实现退化成 UTC 日期，这里会得到 9 月 16 日
                assertThat(PurchaseSnapshotFactory.demandDate(OffsetDateTime.parse("2026-09-16T16:00:00Z")))
                        .isEqualTo(LocalDate.of(2026, 9, 17));
                // 上海 23:59:59 仍是当日
                assertThat(PurchaseSnapshotFactory.demandDate(OffsetDateTime.parse("2026-09-16T15:59:59Z")))
                        .isEqualTo(LocalDate.of(2026, 9, 16));
                // 已带偏移的时间点必须做等时刻换算，而不是丢弃偏移
                assertThat(PurchaseSnapshotFactory.demandDate(OffsetDateTime.parse("2026-09-16T20:30:00+08:00")))
                        .isEqualTo(LocalDate.of(2026, 9, 16));
                assertThat(PurchaseSnapshotFactory.demandDate(OffsetDateTime.parse("2026-09-17T04:30:00+08:00")))
                        .isEqualTo(LocalDate.of(2026, 9, 17));
            }

            @Test
            @DisplayName("Q6a：跨日窗口不得把所有需求压平成同一天")
            void demandDateIsNotCollapsedToWindowStart() {
                LocalDate day1 = PurchaseSnapshotFactory.demandDate(OffsetDateTime.parse("2026-09-15T18:00:00Z"));
                LocalDate day2 = PurchaseSnapshotFactory.demandDate(OffsetDateTime.parse("2026-09-16T18:00:00Z"));
                assertThat(day1).isEqualTo(LocalDate.of(2026, 9, 16));
                assertThat(day2).isEqualTo(LocalDate.of(2026, 9, 17));
                assertThat(day1).isNotEqualTo(day2);
            }

            // ------------------------------------------------------------------
            // 来源合法性：40980 的唯一可达入口（PurchaseDemandSourceGuard）
            // ------------------------------------------------------------------

            @Test
            @DisplayName("来源订单必须是 CONFIRMED 且未软删，否则 40980")
            void demandSourceMustBeConfirmed() {
                assertThatCode(() -> PurchaseDemandSourceGuard.requireConfirmed(order("CONFIRMED", false)))
                        .doesNotThrowAnyException();

                for (SalesOrderEntity bad : new SalesOrderEntity[]{
                        order("PENDING", false), order("CANCELLED", false),
                        order("CONFIRMED", true), null}) {
                    assertThatThrownBy(() -> PurchaseDemandSourceGuard.requireConfirmed(bad))
                            .isInstanceOfSatisfying(ScmBusinessException.class,
                                    e -> assertThat(codeOf(e)).isEqualTo(40980));
                }

                assertThat(PurchaseDemandSourceGuard.isConfirmed(order("CONFIRMED", false))).isTrue();
                assertThat(PurchaseDemandSourceGuard.isConfirmed(order("CONFIRMED", true))).isFalse();
                assertThat(PurchaseDemandSourceGuard.isConfirmed(null)).isFalse();
            }

            private static SalesOrderEntity order(String status, boolean deleted) {
                SalesOrderEntity entity = new SalesOrderEntity();
                entity.setStatus(status);
                entity.setDeleted(deleted);
                return entity;
            }

            // ------------------------------------------------------------------
            // 收货单状态谓词
            // ------------------------------------------------------------------

            @ParameterizedTest
            @ValueSource(strings = {"PENDING", "CONFIRMED"})
            @DisplayName("收货单两态都是合法枚举值")
            void receiptStatusWhitelist(String status) {
                assertThat(ScmReceiptStatusEnum.valueOf(status).name()).isEqualTo(status);
            }
        }

        @Nested

        class ScmPurchaseStatusEnumTest {
            @Test
            @DisplayName("已提交清单 = 全部状态减去 CANCELLED")
            void committedNamesCoverEveryStatusExceptCancelled() {
                // 期望值在这里独立算一遍，不复用 committed()：实现若漏掉某个状态，两边会对不上。
                List<String> expected = Arrays.stream(ScmPurchaseStatusEnum.values()).map(Enum::name)
                        .filter(name -> !"CANCELLED".equals(name)).toList();
                assertThat(ScmPurchaseStatusEnum.committedNames()).containsExactlyElementsOf(expected);
                assertThat(ScmPurchaseStatusEnum.committedNames())
                        .containsExactlyInAnyOrder("SUBMITTED", "ACCOUNTED", "PARTIALLY_RECEIVED", "RECEIVED", "SHORT_CLOSED");
            }

            @Test
            @DisplayName("CANCELLED 不计入采购额：取消的已退出履约链路")
            void cancelledIsNotCommitted() {
                assertThat(ScmPurchaseStatusEnum.CANCELLED.committed()).isFalse();
                for (ScmPurchaseStatusEnum status : ScmPurchaseStatusEnum.values()) {
                    if (status == ScmPurchaseStatusEnum.CANCELLED) {
                        continue;
                    }
                    assertThat(status.committed()).as("%s 已进入履约链路", status).isTrue();
                }
            }

            @Test
            @DisplayName("「已提交后又被取消」只发生在一票未收的单上")
            void cancellationOnlyHappensBeforeAnyReceipt() {
                // 这是「当前有效规则」成立的前提：取消会让历史采购额变小，但那些单从未产生收货，
                // 所以抹掉它们不会让「收了多少货」与「采购了多少」对不上。
                assertThat(PurchaseOrderStateMachine.canTransition("SUBMITTED", "CANCELLED")).isTrue();
                assertThat(PurchaseOrderStateMachine.canTransition("PARTIALLY_RECEIVED", "CANCELLED"))
                        .as("部分收货不可取消，只能 shortClose").isFalse();
                assertThat(PurchaseOrderStateMachine.canTransition("RECEIVED", "CANCELLED")).isFalse();
                assertThat(PurchaseOrderStateMachine.canTransition("SHORT_CLOSED", "CANCELLED")).isFalse();
            }
        }
    }

    @Nested
    class PurchaseW5PgIT extends ScmW5PgITBase {
        @Nested
        @DisplayName("采购单分配集合：Q13 一行多需求 + Q17 单位一致（PG IT）")
        class PurchaseDemandAllocationIT {
            @Autowired
            private PurchaseDemandService demandService;

            @Autowired
            private PurchaseOrderService orderService;

            @Autowired
            private PurchaseQueryService queryService;

            @Autowired
            private PurchaseDemandDao purchaseDemandDao;

            @Autowired
            private PurchaseDemandAllocationDao purchaseDemandAllocationDao;

            /**
             * 一个 SKU + 供应商 + 两条需求（两张已确认订单）+ 一张草稿采购单。
             */
            private record TwoDemandFixture(Long skuId, Long supplierId,
                                            Long demandA, Long demandB,
                                            Long salesOrderItemA, Long salesOrderItemB,
                                            Long purchaseOrderId, Long purchaseOrderItemId) {
            }

            private PurchaseDemandEntity demandOf(Long salesOrderItemId) {
                List<PurchaseDemandEntity> rows =
                        purchaseDemandDao.listActiveBySourceItemIds(List.of(salesOrderItemId));
                assertThat(rows).hasSize(1);
                return rows.getFirst();
            }

            /**
             * 造出「一个 SKU 行挂两条需求」的完整前置数据，并按给定数量建单。
             *
             * @param purchaseUnit 供应商侧的采购单位（传 {@code 箱} 即可构造单位不一致）
             * @param quantityA    分配到需求 A 的数量（4 位定点字符串）
             * @param quantityB    分配到需求 B 的数量
             */
            private TwoDemandFixture twoDemands(String suffix, String purchaseUnit,
                                                String quantityA, String quantityB) {
                Long skuId = newOnShelfSku(suffix);
                Long supplierId = newSupplier(suffix);
                linkSupplierSku(supplierId, skuId, purchaseUnit);
                Long customerId = newCustomer();

                // 需求 A：订购 5、实收 3；需求 B：订购 8、实收 4 —— 需求量取实数量
                Long salesOrderA = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
                Long salesOrderB = confirmedSalesOrder(customerId, skuId, "8.0000", "4.0000");
                Long itemA = confirmedSalesOrderItemId(salesOrderA);
                Long itemB = confirmedSalesOrderItemId(salesOrderB);
                OffsetDateTime from = salesOrderConfirmedAt(salesOrderA).minusSeconds(1);
                OffsetDateTime to = salesOrderConfirmedAt(salesOrderB).plusSeconds(1);

                PurchaseDemandGenerateForm generate = new PurchaseDemandGenerateForm();
                generate.setStartAt(from);
                generate.setEndAt(to);
                generate.setWarehouseId(seedWarehouseId());
                generate.setSupplierId(supplierId);
                demandService.generate(generate, prefix + ":" + suffix + ":gen");

                PurchaseDemandEntity demandA = demandOf(itemA);
                PurchaseDemandEntity demandB = demandOf(itemB);

                PurchaseOrderVO order = orderService.create(
                        orderForm(supplierId, skuId, demandA, quantityA, demandB, quantityB),
                        prefix + ":" + suffix + ":po");
                PurchaseOrderItemVO item = order.getItems().getFirst();
                return new TwoDemandFixture(skuId, supplierId, demandA.getId(), demandB.getId(),
                        itemA, itemB, order.getId(), item.getId());
            }

            private PurchaseOrderAddForm orderForm(Long supplierId, Long skuId,
                                                   PurchaseDemandEntity demandA, String quantityA,
                                                   PurchaseDemandEntity demandB, String quantityB) {
                PurchaseOrderAddForm form = new PurchaseOrderAddForm();
                form.setSupplierId(supplierId);
                form.setWarehouseId(seedWarehouseId());
                form.setRemark("W5 分配用例");
                PurchaseOrderAddForm.Item item = new PurchaseOrderAddForm.Item();
                item.setSkuId(skuId);
                item.setQuantity("10.0000");
                item.setPrice("6.2000");
                List<PurchaseOrderAddForm.Allocation> allocations = new ArrayList<>();
                if (demandA != null) {
                    allocations.add(allocation(demandA, quantityA));
                }
                if (demandB != null) {
                    allocations.add(allocation(demandB, quantityB));
                }
                item.setAllocations(allocations);
                form.setItems(new ArrayList<>(List.of(item)));
                return form;
            }

            /**
             * 把既有采购单读成一次编辑请求，目标分配集合完全由参数决定。
             *
             * <p>服务端对分配做的是集合差量同步，请求体就是「这一行最终应该有哪些分配」。
             * 因此「保留另一条」必须在请求里显式带上它 —— 不写就等于要求删除，
             * 这正是 {@link #updateDeletesOneAllocationAndDemandFallsBack} 依赖的语义。
             *
             * <p><b>需求版本取「当前值」而不是建单前的值</b>：{@code create} 里的 {@code recomputeDemands} 会把
             * 需求的 {@code version} 推进一次，因此建单时读到的版本在编辑时已经过期，
             * 直接用它会得到 40972（需求版本冲突）而不是被测的行为。
             */
            private PurchaseOrderUpdateForm updateForm(TwoDemandFixture fx, Map<Long, String> target) {
                PurchaseOrderVO current = queryService.orderDetail(fx.purchaseOrderId());
                PurchaseOrderItemVO item = current.getItems().getFirst();

                PurchaseOrderUpdateForm form = new PurchaseOrderUpdateForm();
                form.setId(current.getId());
                form.setVersion(current.getVersion());
                form.setSupplierId(current.getSupplierId());
                form.setWarehouseId(current.getWarehouseId());
                form.setRemark(current.getRemark());

                PurchaseOrderAddForm.Item row = new PurchaseOrderAddForm.Item();
                row.setId(item.getId());
                row.setVersion(item.getVersion());
                row.setSkuId(item.getSkuId());
                row.setQuantity("10.0000");
                row.setPrice("6.2000");
                List<PurchaseOrderAddForm.Allocation> allocations = new ArrayList<>();
                for (Map.Entry<Long, String> entry : target.entrySet()) {
                    PurchaseOrderAddForm.Allocation one = new PurchaseOrderAddForm.Allocation();
                    one.setDemandId(entry.getKey());
                    one.setQuantity(entry.getValue());
                    one.setDemandVersion(demandById(entry.getKey()).getVersion());
                    allocations.add(one);
                }
                row.setAllocations(allocations);
                form.setItems(new ArrayList<>(List.of(row)));
                return form;
            }

            /**
             * 目标态只含一条分配（同行的其它分配一律被删）。
             */
            private PurchaseOrderUpdateForm updateForm(TwoDemandFixture fx, Long demandId, String quantity) {
                return updateForm(fx, Map.of(demandId, quantity));
            }

            /**
             * 重新读需求。必须先清一级缓存：整个用例跑在一个事务里，MyBatis 的 SqlSession 与事务同生命周期。
             */
            private PurchaseDemandEntity demandById(Long demandId) {
                evictMybatisCache();
                return purchaseDemandDao.selectById(demandId);
            }

            // ------------------------------------------------------------------
            // 1 / 8. 一行两需求 + 单位一致时通过
            // ------------------------------------------------------------------

            @Test
            @DisplayName("Q13 + Q17：单位一致时，一个采购行同时承接两个需求（两条独立 allocation）")
            void oneItemCarriesTwoDemandsWhenUnitsMatch() {
                TwoDemandFixture fx = twoDemands("AL1", DEFAULT_PURCHASE_UNIT, "3.0000", "4.0000");

                PurchaseOrderVO order = queryService.orderDetail(fx.purchaseOrderId());
                List<PurchaseOrderItemVO> items = order.getItems();
                assertThat(items).hasSize(1);   // 行身份 = (order, sku)：两个需求不拆成两行
                assertThat(items.getFirst().getAllocations())
                        .extracting(PurchaseOrderAllocationVO::getDemandId)
                        .containsExactlyInAnyOrder(fx.demandA(), fx.demandB());

                // 两个需求各自补齐 → 都是 ALLOCATED（不是「只保留最后一条」）
                assertThat(demandById(fx.demandA()).getAllocatedQuantity()).isEqualByComparingTo("3.0000");
                assertThat(demandById(fx.demandA()).getStatus()).isEqualTo("ALLOCATED");
                assertThat(demandById(fx.demandB()).getAllocatedQuantity()).isEqualByComparingTo("4.0000");
                assertThat(demandById(fx.demandB()).getStatus()).isEqualTo("ALLOCATED");
                // 首次分配时把 supplier 固定到需求上
                assertThat(demandById(fx.demandA()).getSupplierId()).isEqualTo(fx.supplierId());
            }

            // ------------------------------------------------------------------
            // 2. 只改其中一条
            // ------------------------------------------------------------------

            @Test
            @DisplayName("Q13：编辑只改其中一条 allocation —— 另一条的**行 id 与数量都不变**")
            void updateChangesOnlyOneAllocation() {
                TwoDemandFixture fx = twoDemands("AL2", DEFAULT_PURCHASE_UNIT, "3.0000", "4.0000");
                Long allocationIdOfA = allocationOf(fx.purchaseOrderItemId(), fx.demandA()).getId();

                // 只改需求 B：4.0000 → 3.0000（B 变成部分分配）；A 在请求里原样带上 3.0000。
                // 请求体是目标态，所以「保留 A」必须写出来 —— 见 updateForm 的注释。
                PurchaseOrderUpdateForm form = updateForm(fx, Map.of(
                        fx.demandA(), "3.0000",
                        fx.demandB(), "3.0000"));
                orderService.update(form);

                PurchaseOrderItemVO item = queryService.orderDetail(fx.purchaseOrderId()).getItems().getFirst();
                assertThat(item.getAllocations()).hasSize(2);

                PurchaseDemandAllocationEntity a = allocationOf(fx.purchaseOrderItemId(), fx.demandA());
                // A 是同一行（id 未变）+ 数量未变 → 证明「只改一条」没有重建兄弟行
                assertThat(a.getId()).isEqualTo(allocationIdOfA);
                assertThat(a.getAllocatedQuantity()).isEqualByComparingTo("3.0000");
                assertThat(demandById(fx.demandA()).getAllocatedQuantity()).isEqualByComparingTo("3.0000");
                assertThat(demandById(fx.demandA()).getStatus()).isEqualTo("ALLOCATED");

                assertThat(allocationOf(fx.purchaseOrderItemId(), fx.demandB()).getAllocatedQuantity())
                        .isEqualByComparingTo("3.0000");
                assertThat(demandById(fx.demandB()).getAllocatedQuantity()).isEqualByComparingTo("3.0000");
                assertThat(demandById(fx.demandB()).getStatus()).isEqualTo("PARTIALLY_ALLOCATED");
            }

            // ------------------------------------------------------------------
            // 3. 删其中一条（需求侧必须回落）
            // ------------------------------------------------------------------

            @Test
            @DisplayName("Q13：编辑删掉一条 allocation —— 该需求 allocated 归零、status 从 ALLOCATED 退回 PENDING")
            void updateDeletesOneAllocationAndDemandFallsBack() {
                TwoDemandFixture fx = twoDemands("AL3", DEFAULT_PURCHASE_UNIT, "3.0000", "4.0000");

                // 请求里只留需求 A → 需求 B 的分配被删（且只删这一条）
                orderService.update(updateForm(fx, fx.demandA(), "3.0000"));

                PurchaseOrderItemVO item = queryService.orderDetail(fx.purchaseOrderId()).getItems().getFirst();
                assertThat(item.getAllocations()).extracting(PurchaseOrderAllocationVO::getDemandId)
                        .containsExactly(fx.demandA());
                assertThat(purchaseDemandAllocationDao.listActiveByDemandIds(List.of(fx.demandB()))).isEmpty();

                // B 的 allocated 回落、状态回落 —— 「重算必须遍历旧 ∪ 新」的直接证据
                assertThat(demandById(fx.demandB()).getAllocatedQuantity()).isEqualByComparingTo("0.0000");
                assertThat(demandById(fx.demandB()).getStatus()).isEqualTo("PENDING");
                // A 不受影响
                assertThat(demandById(fx.demandA()).getAllocatedQuantity()).isEqualByComparingTo("3.0000");
                assertThat(demandById(fx.demandA()).getStatus()).isEqualTo("ALLOCATED");
            }

            // ------------------------------------------------------------------
            // 4. 同一行内重复 (item, demand)
            // ------------------------------------------------------------------

            @Test
            @DisplayName("Q13：同一采购行内重复关联同一需求 → 40090")
            void duplicateItemDemandRejected() {
                Long skuId = newOnShelfSku("AL4");
                Long supplierId = newPurchasableSupplier("AL4", skuId);
                Long customerId = newCustomer();
                Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
                Long sourceItem = confirmedSalesOrderItemId(salesOrder);

                PurchaseDemandGenerateForm generate = new PurchaseDemandGenerateForm();
                generate.setStartAt(salesOrderConfirmedAt(salesOrder));
                generate.setEndAt(salesOrderConfirmedAt(salesOrder).plusSeconds(1));
                generate.setWarehouseId(seedWarehouseId());
                generate.setSupplierId(supplierId);
                demandService.generate(generate, prefix + ":AL4:gen");
                PurchaseDemandEntity demand = demandOf(sourceItem);

                PurchaseOrderAddForm form = orderForm(supplierId, skuId, demand, "1.0000", demand, "2.0000");
                expectCode(() -> orderService.create(form, prefix + ":AL4:po"), 40090);
            }

            // ------------------------------------------------------------------
            // 5. 多需求合计超限
            // ------------------------------------------------------------------

            @Test
            @DisplayName("Q13：同一需求被本次请求累计超过 required → 40082（且不产生任何采购单）")
            void allocationExceedingRequiredRejected() {
                TwoDemandFixture fx = twoDemands("AL5", DEFAULT_PURCHASE_UNIT, "3.0000", "4.0000");

                // 需求 A 的 required = 3.0000，本次再要 1.0000 → 3.0000 + 1.0000 > 3.0000
                PurchaseOrderUpdateForm form = updateForm(fx, fx.demandA(), "4.0000");
                expectCode(() -> orderService.update(form), 40082);
                // 回滚：A 的 allocated 保持原值
                assertThat(demandById(fx.demandA()).getAllocatedQuantity()).isEqualByComparingTo("3.0000");
            }

            // ------------------------------------------------------------------
            // 6. 编辑后 status 重算
            // ------------------------------------------------------------------

            @Test
            @DisplayName("编辑后需求侧重算：PARTIALLY_ALLOCATED → ALLOCATED（补齐），再 → PENDING（清空）")
            void demandStatusIsRecalculatedAfterEachEdit() {
                TwoDemandFixture fx = twoDemands("AL6", DEFAULT_PURCHASE_UNIT, "1.5000", "1.0000");
                assertThat(demandById(fx.demandA()).getStatus()).isEqualTo("PARTIALLY_ALLOCATED");
                assertThat(demandById(fx.demandB()).getStatus()).isEqualTo("PARTIALLY_ALLOCATED");

                // 需求 A 补齐到 required = 3.0000；需求 B 在请求里带上原值 1.0000 表示「保持不变」
                orderService.update(updateForm(fx, Map.of(
                        fx.demandA(), "3.0000",
                        fx.demandB(), "1.0000")));
                assertThat(demandById(fx.demandA()).getAllocatedQuantity()).isEqualByComparingTo("3.0000");
                assertThat(demandById(fx.demandA()).getStatus()).isEqualTo("ALLOCATED");
                assertThat(demandById(fx.demandB()).getStatus()).isEqualTo("PARTIALLY_ALLOCATED");

                // 清空全部分配（allocations 缺失 = 空集合 = 清空，与 items 的整体替换语义一致）
                PurchaseOrderVO current = queryService.orderDetail(fx.purchaseOrderId());
                PurchaseOrderItemVO item = current.getItems().getFirst();
                PurchaseOrderUpdateForm clear = new PurchaseOrderUpdateForm();
                clear.setId(current.getId());
                clear.setVersion(current.getVersion());
                clear.setSupplierId(current.getSupplierId());
                clear.setWarehouseId(current.getWarehouseId());
                PurchaseOrderAddForm.Item row = new PurchaseOrderAddForm.Item();
                row.setId(item.getId());
                row.setVersion(item.getVersion());
                row.setSkuId(item.getSkuId());
                row.setQuantity("10.0000");
                row.setPrice("6.2000");
                clear.setItems(new ArrayList<>(List.of(row)));
                orderService.update(clear);

                assertThat(demandById(fx.demandA()).getAllocatedQuantity()).isEqualByComparingTo("0.0000");
                assertThat(demandById(fx.demandA()).getStatus()).isEqualTo("PENDING");
                assertThat(demandById(fx.demandB()).getAllocatedQuantity()).isEqualByComparingTo("0.0000");
                assertThat(demandById(fx.demandB()).getStatus()).isEqualTo("PENDING");
                assertThat(purchaseDemandAllocationDao.listActiveByOrderId(fx.purchaseOrderId())).isEmpty();
            }

            // ------------------------------------------------------------------
            // 7. 单位不一致
            // ------------------------------------------------------------------

            @Test
            @DisplayName("Q17：采购单位（箱）≠ 需求单位（kg）→ 40971，绝不猜换算系数")
            void unitMismatchRejected() {
                Long skuId = newOnShelfSku("AL7");
                Long supplierId = newSupplier("AL7");
                linkSupplierSku(supplierId, skuId, "箱");      // 销售单位是 kg（见基类造数）
                Long customerId = newCustomer();
                Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
                Long sourceItem = confirmedSalesOrderItemId(salesOrder);

                PurchaseDemandGenerateForm generate = new PurchaseDemandGenerateForm();
                generate.setStartAt(salesOrderConfirmedAt(salesOrder));
                generate.setEndAt(salesOrderConfirmedAt(salesOrder).plusSeconds(1));
                generate.setWarehouseId(seedWarehouseId());
                generate.setSupplierId(supplierId);
                demandService.generate(generate, prefix + ":AL7:gen");

                PurchaseDemandEntity demand = demandOf(sourceItem);
                // 需求单位来自销售单位，与 supplier_sku.purchase_unit 相互独立
                assertThat(demand.getDemandUnitSnapshot()).isEqualTo(DEFAULT_PURCHASE_UNIT);

                PurchaseOrderAddForm form = orderForm(supplierId, skuId, demand, "3.0000", null, null);
                expectCode(() -> orderService.create(form, prefix + ":AL7:po"), 40971);
                // 拒绝之后需求侧一个字节都没动
                assertThat(demandById(demand.getId()).getAllocatedQuantity()).isEqualByComparingTo("0.0000");
                assertThat(demandById(demand.getId()).getStatus()).isEqualTo("PENDING");
            }

            // ------------------------------------------------------------------
            // 9. 分配唯一键
            // ------------------------------------------------------------------

            @Test
            @DisplayName("uk_purchase_demand_allocation_source_active：同一 (item, demand) 的第二条活动分配被拒")
            void allocationUniqueIndexBlocksDuplicateIdentity() {
                TwoDemandFixture fx = twoDemands("AL9", DEFAULT_PURCHASE_UNIT, "3.0000", "4.0000");
                PurchaseDemandAllocationEntity existing =
                        allocationOf(fx.purchaseOrderItemId(), fx.demandA());

                expectSqlFailure(
                        "INSERT INTO xsy_purchase_demand_allocation (purchase_demand_id, purchase_order_item_id, "
                                + "sales_order_id, sales_order_item_id, sku_id, allocated_quantity, created_by) "
                                + "VALUES (?, ?, ?, ?, ?, 1.0000, 'dup')",
                        existing.getPurchaseDemandId(), existing.getPurchaseOrderItemId(),
                        existing.getSalesOrderId(), existing.getSalesOrderItemId(), existing.getSkuId());
            }
        }

        @Nested
        @DisplayName("采购需求生成：来源保存的数据 / Q6a / Q17 / 去重收敛（PG IT）")
        class PurchaseDemandServiceIT {
            private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

            @Autowired
            private PurchaseDemandService demandService;

            @Autowired
            private PurchaseQueryService queryService;

            @Autowired
            private PurchaseDemandDao purchaseDemandDao;

            @Autowired
            private PurchaseDemandAllocationDao purchaseDemandAllocationDao;

            /**
             * 紧贴该订单确认时刻的 1 秒窗口。
             *
             * <p>窗口必须收紧到一秒：库里已有历史 CONFIRMED 订单，
             * 宽窗口会把它们的来源行一起汇总进来，让 {@code createdCount} 失去可断言性。
             */
            private PurchaseDemandGenerateForm window(Fixture fixture) {
                PurchaseDemandGenerateForm form = new PurchaseDemandGenerateForm();
                form.setStartAt(fixture.confirmedAt());
                form.setEndAt(fixture.confirmedAt().plusSeconds(1));
                form.setWarehouseId(seedWarehouseId());
                form.setSupplierId(fixture.supplierId());
                return form;
            }

            private PurchaseDemandEntity myDemand(Fixture fixture) {
                List<PurchaseDemandEntity> rows =
                        purchaseDemandDao.listActiveBySourceItemIds(List.of(fixture.salesOrderItemId()));
                assertThat(rows).hasSize(1);
                return rows.getFirst();
            }

            // ------------------------------------------------------------------
            // 1. 保存的数据与来源规则
            // ------------------------------------------------------------------

            @Test
            @DisplayName("generate：来源已确认订单 → 需求保存的数据齐全，需求量取实数量而不是订购量")
            void generateCreatesDemandFromConfirmedOrderWithSnapshots() {
                Fixture fixture = fixture("DG1", "5.0000", "3.2500");
                PurchaseDemandGenerateForm form = window(fixture);
                form.setPurchaserId(anyEmployeeId());

                PurchaseDemandService.GenerateResult result = demandService.generate(form, prefix + ":g1");

                assertThat(result.getSourceLineCount()).isEqualTo(1);
                assertThat(result.getCreatedCount()).isEqualTo(1);
                assertThat(result.getSkippedCount()).isZero();
                assertThat(result.getDemandIds()).hasSize(1);

                PurchaseDemandEntity row = myDemand(fixture);
                assertThat(row.getId()).isEqualTo(result.getDemandIds().getFirst());
                assertThat(row.getSalesOrderId()).isEqualTo(fixture.salesOrderId());
                assertThat(row.getSkuId()).isEqualTo(fixture.skuId());
                assertThat(row.getSalesOrderNoSnapshot()).isNotBlank();
                assertThat(row.getSpuCodeSnapshot()).isNotBlank();
                assertThat(row.getSkuCodeSnapshot()).isNotBlank();
                // 需求量 = sales_order_item.actual_quantity，不是 ordered_quantity = 5.0000
                assertThat(row.getRequiredQuantity()).isEqualByComparingTo("3.2500");
                assertThat(row.getAllocatedQuantity()).isEqualByComparingTo("0.0000");
                assertThat(row.getStatus()).isEqualTo("PENDING");
                // 需求单位 = 销售单位（基类造的 SKU 一律 kg）
                assertThat(row.getDemandUnitSnapshot()).isEqualTo(DEFAULT_PURCHASE_UNIT);
                assertThat(row.getSupplierId()).isEqualTo(fixture.supplierId());
                assertThat(row.getPurchaserId()).isEqualTo(anyEmployeeId());
                assertThat(row.getWarehouseId()).isEqualTo(seedWarehouseId());
                assertThat(row.getSourceConfirmedAt().toInstant()).isEqualTo(fixture.confirmedAt().toInstant());
            }

            @Test
            @DisplayName("还没称重的非标订单按订购量生成需求：采购不能等分拣台称完再下单")
            void unweighedOrderGeneratesDemandOnOrderedQuantity() {
                Fixture fixture = unweighedFixture("DGU", "4.0000");

                PurchaseDemandService.GenerateResult result = demandService.generate(window(fixture), prefix + ":gu");

                assertThat(result.getCreatedCount()).isEqualTo(1);
                PurchaseDemandEntity row = myDemand(fixture);
                assertThat(row.getRequiredQuantity()).isEqualByComparingTo("4.0000");
            }

            // ------------------------------------------------------------------
            // 2. demand_date 派生
            // ------------------------------------------------------------------

            @Test
            @DisplayName("Q6a：demand_date 取 source_confirmed_at 的 Asia/Shanghai 日期，且库层 CHECK 成立")
            void demandDateComesFromSourceConfirmedAtAndDbCheckHolds() {
                Fixture fixture = fixture("DG2", "4.0000", "2.5000");

                // 故意把窗口起点拉到 1 天前：若实现写成 date(startAt)，demand_date 会差一天
                PurchaseDemandGenerateForm form = window(fixture);
                form.setStartAt(fixture.confirmedAt().minusDays(1));
                demandService.generate(form, prefix + ":g2");

                PurchaseDemandEntity row = myDemand(fixture);
                LocalDate expected = fixture.confirmedAt().atZoneSameInstant(SHANGHAI).toLocalDate();
                assertThat(row.getDemandDate()).isEqualTo(expected);
                assertThat(row.getDemandDate())
                        .isNotEqualTo(form.getStartAt().atZoneSameInstant(SHANGHAI).toLocalDate());
                // 原订单确认时间原样保存，不被窗口起点污染
                assertThat(row.getSourceConfirmedAt().toInstant()).isEqualTo(fixture.confirmedAt().toInstant());

                // 库层复核：V15 的 ck_purchase_demand_date 与 Java 侧派生规则必须逐行一致
                Integer mismatched = jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_purchase_demand "
                                + "WHERE demand_date <> (source_confirmed_at AT TIME ZONE 'Asia/Shanghai')::date",
                        Integer.class);
                assertThat(mismatched).isZero();
            }

            // ------------------------------------------------------------------
            // 3. 半开区间
            // ------------------------------------------------------------------

            @Test
            @DisplayName("窗口是半开区间 [startAt, endAt)：endAt 恰好等于确认时刻时取不到该行")
            void windowIsHalfOpen() {
                Fixture fixture = fixture("DG3", "2.0000", "1.0000");

                PurchaseDemandGenerateForm form = new PurchaseDemandGenerateForm();
                form.setStartAt(fixture.confirmedAt().plusSeconds(1));
                form.setEndAt(fixture.confirmedAt().plusSeconds(2));
                form.setWarehouseId(seedWarehouseId());

                PurchaseDemandService.GenerateResult result = demandService.generate(form, prefix + ":g3");

                assertThat(result.getSourceLineCount()).isZero();
                assertThat(result.getCreatedCount()).isZero();
                assertThat(purchaseDemandDao.listActiveBySourceItemIds(List.of(fixture.salesOrderItemId())))
                        .isEmpty();
            }

            // ------------------------------------------------------------------
            // 4. 重复汇总（重复请求返回原结果 + 去重）
            // ------------------------------------------------------------------

            @Test
            @DisplayName("重复 generate：同一来源行收敛到同一需求，第二次计为 skipped 而不是失败")
            void regenerateSkipsExistingSourceLinesAndReturnsSameDemandId() {
                Fixture fixture = fixture("DG4", "6.0000", "4.0000");

                PurchaseDemandService.GenerateResult first =
                        demandService.generate(window(fixture), prefix + ":g4a");
                // 不同防重复请求编号（不同请求身份）：走「已存在 → 跳过」分支，而不是重放
                PurchaseDemandService.GenerateResult second =
                        demandService.generate(window(fixture), prefix + ":g4b");

                assertThat(second.getCreatedCount()).isZero();
                assertThat(second.getSkippedCount()).isEqualTo(1);
                assertThat(second.getDemandIds()).containsExactlyElementsOf(first.getDemandIds());
                assertThat(purchaseDemandDao.listActiveBySourceItemIds(List.of(fixture.salesOrderItemId())))
                        .hasSize(1);
            }

            // ------------------------------------------------------------------
            // 5. C1：INSERT 竞争收敛
            // ------------------------------------------------------------------

            @Test
            @DisplayName("C1：ON CONFLICT DO NOTHING 返回 0，重读收敛到同一条活动需求")
            void insertCompetitionConvergesToSingleActiveDemand() {
                Fixture fixture = fixture("DG5", "3.0000", "2.0000");
                demandService.generate(window(fixture), prefix + ":g5");
                PurchaseDemandEntity winner = myDemand(fixture);

                // 模拟「另一个连接抢先插入了同一来源行」：复制业务字段、不带 id，再插一次
                PurchaseDemandEntity competitor = new PurchaseDemandEntity();
                competitor.setSalesOrderId(winner.getSalesOrderId());
                competitor.setSalesOrderItemId(winner.getSalesOrderItemId());
                competitor.setSpuId(winner.getSpuId());
                competitor.setSkuId(winner.getSkuId());
                competitor.setSalesOrderNoSnapshot(winner.getSalesOrderNoSnapshot());
                competitor.setSpuCodeSnapshot(winner.getSpuCodeSnapshot());
                competitor.setProductNameSnapshot(winner.getProductNameSnapshot());
                competitor.setSkuCodeSnapshot(winner.getSkuCodeSnapshot());
                competitor.setSkuNameSnapshot(winner.getSkuNameSnapshot());
                competitor.setSpecValuesSnapshot(winner.getSpecValuesSnapshot());
                competitor.setDemandUnitSnapshot(winner.getDemandUnitSnapshot());
                competitor.setProductTypeSnapshot(winner.getProductTypeSnapshot());
                competitor.setRequiredQuantity(winner.getRequiredQuantity());
                competitor.setAllocatedQuantity(winner.getAllocatedQuantity());
                competitor.setWarehouseId(winner.getWarehouseId());
                competitor.setStatus(winner.getStatus());
                competitor.setSourceConfirmedAt(winner.getSourceConfirmedAt());
                competitor.setDemandDate(winner.getDemandDate());
                competitor.setCreatedBy("competitor");

                assertThat(purchaseDemandDao.insertIgnore(competitor)).isZero();
                assertThat(competitor.getId()).isNull();   // 冲突时 JDBC 不回传生成键 → 调用方必须走重读分支
                assertThat(purchaseDemandDao.listActiveBySourceItemIds(List.of(fixture.salesOrderItemId())))
                        .singleElement()
                        .extracting(PurchaseDemandEntity::getId)
                        .isEqualTo(winner.getId());
            }

            @Test
            @DisplayName("uk_purchase_demand_source_active：第二条活动需求被唯一索引直接拒绝")
            void sourceUniqueIndexBlocksSecondActiveDemand() {
                Fixture fixture = fixture("DG6", "3.0000", "2.0000");
                demandService.generate(window(fixture), prefix + ":g6");
                PurchaseDemandEntity winner = myDemand(fixture);

                expectSqlFailure(
                        "INSERT INTO xsy_purchase_demand (sales_order_id, sales_order_item_id, spu_id, sku_id, "
                                + "sales_order_no_snapshot, spu_code_snapshot, product_name_snapshot, sku_code_snapshot, "
                                + "sku_name_snapshot, demand_unit_snapshot, product_type_snapshot, required_quantity, "
                                + "allocated_quantity, warehouse_id, status, source_confirmed_at, demand_date, created_by) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, 'PENDING', ?, ?, 'dup')",
                        winner.getSalesOrderId(), winner.getSalesOrderItemId(), winner.getSpuId(), winner.getSkuId(),
                        winner.getSalesOrderNoSnapshot(), winner.getSpuCodeSnapshot(), winner.getProductNameSnapshot(),
                        winner.getSkuCodeSnapshot(), winner.getSkuNameSnapshot(), winner.getDemandUnitSnapshot(),
                        winner.getProductTypeSnapshot(), winner.getRequiredQuantity(), winner.getWarehouseId(),
                        winner.getSourceConfirmedAt(), winner.getDemandDate());
            }

            // ------------------------------------------------------------------
            // 6. 日志归属
            // ------------------------------------------------------------------

            @Test
            @DisplayName("Q14：DEMAND_GENERATE 日志的 purchase_order_id 与 purchase_receipt_id 必须同时为空")
            void generateWritesDemandGenerateLogWithBothIdsNull() {
                Fixture fixture = fixture("DG7", "2.0000", "1.0000");
                demandService.generate(window(fixture), prefix + ":g7");

                Map<String, Object> log = jdbc.queryForMap(
                        "SELECT purchase_order_id, purchase_receipt_id, operation_type, operator, after_data "
                                + "FROM xsy_purchase_operation_log WHERE operation_type = 'DEMAND_GENERATE' "
                                + "ORDER BY id DESC LIMIT 1");
                assertThat(log.get("purchase_order_id")).isNull();
                assertThat(log.get("purchase_receipt_id")).isNull();
                assertThat(log.get("operation_type")).isEqualTo("DEMAND_GENERATE");
                // 操作者落的是 userType:employeeId（ScmOperator 的规则），不是员工姓名
                assertThat(log.get("operator")).isEqualTo(ScmOperator.current());
                assertThat(String.valueOf(log.get("after_data"))).contains("createdCount");
            }

            // ------------------------------------------------------------------
            // 7. 前置校验
            // ------------------------------------------------------------------

            @Test
            @DisplayName("窗口非法 → 40080；仓库已停用 → 40987（且窗口校验先于仓库校验）")
            void generateRejectsBadWindowAndDisabledWarehouse() {
                Fixture fixture = fixture("DG8", "2.0000", "1.0000");

                PurchaseDemandGenerateForm reversed = window(fixture);
                reversed.setEndAt(reversed.getStartAt());     // startAt == endAt：空集是调用方 bug，不是「无数据」
                expectCode(() -> demandService.generate(reversed, prefix + ":g8a"), 40080);

                PurchaseDemandGenerateForm nullWindow = window(fixture);
                nullWindow.setEndAt(null);
                expectCode(() -> demandService.generate(nullWindow, prefix + ":g8b"), 40000);

                Long disabled = newWarehouse("DISABLED");
                disableWarehouse(disabled);
                PurchaseDemandGenerateForm disabledWarehouse = window(fixture);
                disabledWarehouse.setWarehouseId(disabled);
                expectCode(() -> demandService.generate(disabledWarehouse, prefix + ":g8c"), 40987);
            }

            // ------------------------------------------------------------------
            // 8. 只读查询
            // ------------------------------------------------------------------

            @Test
            @DisplayName("需求列表与详情：unallocatedQuantity = required − allocated，规则与列表一致")
            void demandQueryAndDetailExposeUnallocatedQuantity() {
                Fixture fixture = fixture("DG9", "7.0000", "5.0000");
                demandService.generate(window(fixture), prefix + ":g9");
                PurchaseDemandEntity row = myDemand(fixture);

                PurchaseDemandQueryForm query = new PurchaseDemandQueryForm();
                query.setSalesOrderNo(row.getSalesOrderNoSnapshot());
                // 分页参数必须显式给：PageParam 默认 null，convert2PageQuery 会 NPE
                query.setPageNum(1L);
                query.setPageSize(10L);
                var page = queryService.demandQuery(query);
                assertThat(page.getList()).isNotEmpty();
                assertThat(page.getList()).allSatisfy(vo -> assertThat(vo.getUnallocatedQuantity())
                        .isEqualByComparingTo(vo.getRequiredQuantity().subtract(vo.getAllocatedQuantity())));

                var detail = queryService.demandDetail(row.getId());
                assertThat(detail.getUnallocatedQuantity()).isEqualByComparingTo("5.0000");
                assertThat(detail.getDemandUnit()).isEqualTo(DEFAULT_PURCHASE_UNIT);
                assertThat(detail.getProductType()).isEqualTo("NON_STANDARD");
                // 尚未分配：没有任何 allocation 行
                assertThat(purchaseDemandAllocationDao.listActiveByDemandIds(List.of(row.getId()))).isEmpty();
                // 生成器的保存当时资料的字段与详情查询结果必须逐字段同源（demandUnit 就是保存的数据列）
                assertThat(detail.getSpecValues())
                        .isEqualTo(PurchaseSnapshotFactory.copySpecValues(row.getSpecValuesSnapshot()));
            }
        }

        @Nested
        @DisplayName("采购缺口预览：只读聚合 / 预留归属 / Q13 单位门禁 / 仓库隔离（PG IT）")
        class PurchaseDemandSummaryPreviewIT {
            @Autowired
            private PurchaseQueryService queryService;

            @Autowired
            private InventoryReservationService reservationService;

            private PurchaseDemandSummaryPreviewForm window(OffsetDateTime confirmedAt, Long warehouseId) {
                PurchaseDemandSummaryPreviewForm form = new PurchaseDemandSummaryPreviewForm();
                form.setStartAt(confirmedAt);
                form.setEndAt(confirmedAt.plusSeconds(2));
                form.setWarehouseId(warehouseId);
                form.setPageNum(1L);
                form.setPageSize(50L);
                return form;
            }

            /**
             * 直插一行库存余额（本用例的已知库存记录）。
             */
            private void seedBalance(Long warehouseId, Long skuId, String unit,
                                     String quantity, String reserved) {
                jdbc.update("INSERT INTO xsy_inventory_balance (warehouse_id, sku_id, unit, quantity, reserved_quantity, "
                                + "avg_cost, version, deleted, created_at, updated_at, created_by, updated_by) "
                                + "VALUES (?, ?, ?, ?, ?, 0, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'it', 'it')",
                        warehouseId, skuId, unit, new BigDecimal(quantity), new BigDecimal(reserved));
                evictMybatisCache();
            }

            /**
             * 为一已确认订单在其来源行上预留库存（走真实写入口，同步维护余额 {@code reserved_quantity}）。
             *
             * <p>{@code occurredAt} 取该订单的确认时刻，与 {@code reserveBySalesOrder} 的规则一致。
             */
            private void reserveForOrder(Long warehouseId, Long skuId, Long salesOrderId, String quantity) {
                reservationService.reserve(new ReserveInventoryFact(
                        warehouseId, skuId, "SALES_ORDER_ITEM",
                        salesOrderId, confirmedSalesOrderItemId(salesOrderId),
                        new BigDecimal(quantity), salesOrderConfirmedAt(salesOrderId), "2:1"));
                evictMybatisCache();
            }

            private PurchaseDemandSummaryVO rowFor(Long skuId, List<PurchaseDemandSummaryVO> rows) {
                return rows.stream().filter(r -> r.getSkuId().equals(skuId)).findFirst()
                        .orElseThrow(() -> new AssertionError("预览结果缺少 SKU " + skuId));
            }

            private List<PurchaseDemandSummaryVO> preview(Long warehouseId, OffsetDateTime confirmedAt) {
                return queryService.summaryPreview(window(confirmedAt, warehouseId)).getList();
            }

            @Test
            @DisplayName("库存足够且无任何预留：本批可用 = 全仓净可用，差额 0.0000")
            void stockEnough() {
                Fixture fixture = fixture("S1", "5.0000", "3.0000");
                seedBalance(seedWarehouseId(), fixture.skuId(), DEFAULT_PURCHASE_UNIT, "10.0000", "0.0000");

                PurchaseDemandSummaryVO row = rowFor(fixture.skuId(), preview(seedWarehouseId(), fixture.confirmedAt()));

                assertThat(row.getCalculationStatus()).isEqualTo("STOCK_ENOUGH");
                assertThat(row.getOrderDemandQuantity()).isEqualByComparingTo("3.0000");
                assertThat(row.getOnHandQuantity()).isEqualByComparingTo("10.0000");
                assertThat(row.getReservedQuantity()).isEqualByComparingTo("0.0000");
                assertThat(row.getSelectedOrderReservedQuantity()).isEqualByComparingTo("0.0000");
                assertThat(row.getOtherReservedQuantity()).isEqualByComparingTo("0.0000");
                assertThat(row.getAvailableQuantity()).isEqualByComparingTo("10.0000");
                assertThat(row.getStockAvailableForSelectedOrders()).isEqualByComparingTo("10.0000");
                assertThat(row.getStockComparisonGap()).isEqualByComparingTo("0.0000");
                assertThat(row.getSourceOrderCount()).isEqualTo(1L);
                assertThat(row.getSourceLineCount()).isEqualTo(1L);
            }

            @Test
            @DisplayName("账面负库存只按 0 计可用：-100 斤 + 本批 30 斤 → 缺口 30，不是 130")
            void negativeStockDoesNotInflateGap() {
                Fixture fixture = fixture("S2N", "5.0000", "30.0000");
                seedBalance(seedWarehouseId(), fixture.skuId(), DEFAULT_PURCHASE_UNIT, "-100.0000", "0.0000");

                PurchaseDemandSummaryVO row = rowFor(fixture.skuId(), preview(seedWarehouseId(), fixture.confirmedAt()));

                // 负余额本身照实展示（财务要能核对欠账），但不再作为本批额外需求叠加。
                assertThat(row.getOnHandQuantity()).isEqualByComparingTo("-100.0000");
                assertThat(row.getAvailableQuantity()).isEqualByComparingTo("-100.0000");
                assertThat(row.getStockAvailableForSelectedOrders()).isEqualByComparingTo("0.0000");
                assertThat(row.getStockComparisonGap()).isEqualByComparingTo("30.0000");
                assertThat(row.getNetPurchaseGap()).isEqualByComparingTo("30.0000");
                assertThat(row.getCalculationStatus()).isEqualTo("ZERO_STOCK");
            }

            @Test
            @DisplayName("库存不足且占用属于其他订单：其他预留照扣，差额按本批可用算")
            void shortage() {
                Long customerId = newCustomer();
                Long skuId = newOnShelfSku("S2");
                seedBalance(seedWarehouseId(), skuId, DEFAULT_PURCHASE_UNIT, "3.0000", "0.0000");
                // 先建的订单：确认时刻早于本批窗口，它的 1.0 预留属于「其他业务占用」
                Long otherOrder = confirmedSalesOrder(customerId, skuId, "8.0000", "1.0000");
                reserveForOrder(seedWarehouseId(), skuId, otherOrder, "1.0000");
                // 本批订单需求 7.5，自身没有预留
                Long batchOrder = confirmedSalesOrder(customerId, skuId, "8.0000", "7.5000");
                OffsetDateTime confirmedAt = salesOrderConfirmedAt(batchOrder);
                assertThat(salesOrderConfirmedAt(otherOrder)).isBefore(confirmedAt);

                PurchaseDemandSummaryVO row = rowFor(skuId, preview(seedWarehouseId(), confirmedAt));

                assertThat(row.getCalculationStatus()).isEqualTo("SHORTAGE");
                assertThat(row.getOrderDemandQuantity()).isEqualByComparingTo("7.5000");
                assertThat(row.getSourceOrderCount()).isEqualTo(1L);
                assertThat(row.getReservedQuantity()).isEqualByComparingTo("1.0000");
                assertThat(row.getSelectedOrderReservedQuantity()).isEqualByComparingTo("0.0000");
                assertThat(row.getOtherReservedQuantity()).isEqualByComparingTo("1.0000");
                // available = 3 - 1 = 2；本批可用同样是 2（没有自己的预留可还），差额 7.5 - 2 = 5.5
                assertThat(row.getAvailableQuantity()).isEqualByComparingTo("2.0000");
                assertThat(row.getStockAvailableForSelectedOrders()).isEqualByComparingTo("2.0000");
                assertThat(row.getStockComparisonGap()).isEqualByComparingTo("5.5000");
            }

            @Test
            @DisplayName("本批全额预留（固定反例）：需求 100 / 现货 100 / 本批自身预留 100 → 差额 0，绝不是 100")
            void ownReservationDoesNotInflateGap() {
                Long customerId = newCustomer();
                Long skuId = newOnShelfSku("S2A");
                seedBalance(seedWarehouseId(), skuId, DEFAULT_PURCHASE_UNIT, "100.0000", "0.0000");
                Long orderId = confirmedSalesOrder(customerId, skuId, "100.0000", "100.0000");
                OffsetDateTime confirmedAt = salesOrderConfirmedAt(orderId);
                reserveForOrder(seedWarehouseId(), skuId, orderId, "100.0000");

                PurchaseDemandSummaryVO row = rowFor(skuId, preview(seedWarehouseId(), confirmedAt));

                // 全仓净可用被自己的预留打到 0，但那是本批订单已经占好的货，不构成采购缺口
                assertThat(row.getAvailableQuantity()).isEqualByComparingTo("0.0000");
                assertThat(row.getSelectedOrderReservedQuantity()).isEqualByComparingTo("100.0000");
                assertThat(row.getOtherReservedQuantity()).isEqualByComparingTo("0.0000");
                assertThat(row.getStockAvailableForSelectedOrders()).isEqualByComparingTo("100.0000");
                assertThat(row.getStockComparisonGap()).isEqualByComparingTo("0.0000");
                assertThat(row.getCalculationStatus()).isEqualTo("STOCK_ENOUGH");
            }

            @Test
            @DisplayName("本批部分预留：只有未覆盖的那部分进入差额")
            void partialOwnReservation() {
                Long customerId = newCustomer();
                Long skuId = newOnShelfSku("S2B");
                seedBalance(seedWarehouseId(), skuId, DEFAULT_PURCHASE_UNIT, "10.0000", "0.0000");
                Long orderId = confirmedSalesOrder(customerId, skuId, "8.0000", "8.0000");
                OffsetDateTime confirmedAt = salesOrderConfirmedAt(orderId);
                reserveForOrder(seedWarehouseId(), skuId, orderId, "3.0000");

                PurchaseDemandSummaryVO row = rowFor(skuId, preview(seedWarehouseId(), confirmedAt));

                assertThat(row.getReservedQuantity()).isEqualByComparingTo("3.0000");
                assertThat(row.getSelectedOrderReservedQuantity()).isEqualByComparingTo("3.0000");
                assertThat(row.getAvailableQuantity()).isEqualByComparingTo("7.0000");
                assertThat(row.getStockAvailableForSelectedOrders()).isEqualByComparingTo("10.0000");
                // 需求 8 ≤ 本批可用 10 → 无差额
                assertThat(row.getStockComparisonGap()).isEqualByComparingTo("0.0000");
                assertThat(row.getCalculationStatus()).isEqualTo("STOCK_ENOUGH");
            }

            @Test
            @DisplayName("本批 + 其他业务预留同时存在：其他占用照扣，本批预留照还")
            void ownAndOtherReservationTogether() {
                Long customerId = newCustomer();
                Long skuId = newOnShelfSku("S2C");
                seedBalance(seedWarehouseId(), skuId, DEFAULT_PURCHASE_UNIT, "20.0000", "0.0000");
                // 窗口外的旧订单：它的 6.0 预留属于「其他业务占用」
                Long otherOrder = confirmedSalesOrder(customerId, skuId, "6.0000", "6.0000");
                reserveForOrder(seedWarehouseId(), skuId, otherOrder, "6.0000");
                // 窗口内的本批订单：它自己的 4.0 预留不能算成占用
                Long batchOrder = confirmedSalesOrder(customerId, skuId, "9.0000", "9.0000");
                OffsetDateTime confirmedAt = salesOrderConfirmedAt(batchOrder);
                reserveForOrder(seedWarehouseId(), skuId, batchOrder, "4.0000");

                PurchaseDemandSummaryVO row = rowFor(skuId, preview(seedWarehouseId(), confirmedAt));

                assertThat(row.getReservedQuantity()).isEqualByComparingTo("10.0000");
                assertThat(row.getSelectedOrderReservedQuantity()).isEqualByComparingTo("4.0000");
                assertThat(row.getOtherReservedQuantity()).isEqualByComparingTo("6.0000");
                assertThat(row.getAvailableQuantity()).isEqualByComparingTo("10.0000");
                assertThat(row.getStockAvailableForSelectedOrders()).isEqualByComparingTo("14.0000");
                assertThat(row.getStockComparisonGap()).isEqualByComparingTo("0.0000");
                // 只统计窗口内那一张订单的需求
                assertThat(row.getOrderDemandQuantity()).isEqualByComparingTo("9.0000");
                assertThat(row.getSourceOrderCount()).isEqualTo(1L);
            }

            @Test
            @DisplayName("其他业务预留吃掉可用量：本批无预留时仍按全仓净可用判定 ZERO_STOCK")
            void zeroStock() {
                Fixture fixture = fixture("S3", "4.0000", "2.2000");
                // 现有 == 预留 → 本批可用 0
                seedBalance(seedWarehouseId(), fixture.skuId(), DEFAULT_PURCHASE_UNIT, "5.0000", "5.0000");

                PurchaseDemandSummaryVO row = rowFor(fixture.skuId(), preview(seedWarehouseId(), fixture.confirmedAt()));

                assertThat(row.getCalculationStatus()).isEqualTo("ZERO_STOCK");
                assertThat(row.getSelectedOrderReservedQuantity()).isEqualByComparingTo("0.0000");
                assertThat(row.getOtherReservedQuantity()).isEqualByComparingTo("5.0000");
                assertThat(row.getAvailableQuantity()).isEqualByComparingTo("0.0000");
                assertThat(row.getStockAvailableForSelectedOrders()).isEqualByComparingTo("0.0000");
                assertThat(row.getStockComparisonGap()).isEqualByComparingTo("2.2000");
            }

            @Test
            @DisplayName("Q13 单位门禁：余额记账单位 ≠ 需求单位 → UNIT_MISMATCH 且差额为 null，绝不换算")
            void unitMismatchYieldsNullGap() {
                Fixture fixture = fixture("S4", "9.0000", "4.0000");
                // 需求单位是 kg（基类销售单位），余额故意记成 g：单位不可直接相减
                seedBalance(seedWarehouseId(), fixture.skuId(), "g", "1000.0000", "0.0000");

                PurchaseDemandSummaryVO row = rowFor(fixture.skuId(), preview(seedWarehouseId(), fixture.confirmedAt()));

                assertThat(row.getCalculationStatus()).isEqualTo("UNIT_MISMATCH");
                assertThat(row.getDemandUnit()).isEqualTo(DEFAULT_PURCHASE_UNIT);
                assertThat(row.getInventoryUnit()).isEqualTo("g");
                // 关键：不返回伪造差额
                assertThat(row.getStockComparisonGap()).isNull();
            }

            @Test
            @DisplayName("无库存余额行：NO_BALANCE，库存按 0 展示、差额即全部订单量")
            void noBalance() {
                Fixture fixture = fixture("S5", "3.0000", "1.5000");

                PurchaseDemandSummaryVO row = rowFor(fixture.skuId(), preview(seedWarehouseId(), fixture.confirmedAt()));

                assertThat(row.getCalculationStatus()).isEqualTo("NO_BALANCE");
                assertThat(row.getInventoryUnit()).isNull();
                assertThat(row.getOnHandQuantity()).isEqualByComparingTo("0.0000");
                assertThat(row.getSelectedOrderReservedQuantity()).isEqualByComparingTo("0.0000");
                assertThat(row.getStockAvailableForSelectedOrders()).isEqualByComparingTo("0.0000");
                assertThat(row.getStockComparisonGap()).isEqualByComparingTo("1.5000");
            }

            @Test
            @DisplayName("多张已确认订单同 SKU：订单数、需求量与两笔本批预留一起聚合")
            void aggregatesMultipleOrdersOfSameSku() {
                Long customerId = newCustomer();
                Long skuId = newOnShelfSku("S6");
                seedBalance(seedWarehouseId(), skuId, DEFAULT_PURCHASE_UNIT, "1.0000", "0.0000");
                // 同一 SKU 两张订单，实发量各 1.2 + 2.3；两张都属于本批
                Long orderA = confirmedSalesOrder(customerId, skuId, "5.0000", "1.2000");
                Long orderB = confirmedSalesOrder(customerId, skuId, "5.0000", "2.3000");
                OffsetDateTime earliest = salesOrderConfirmedAt(orderA);
                reserveForOrder(seedWarehouseId(), skuId, orderA, "1.0000");

                PurchaseDemandSummaryVO row = rowFor(skuId, preview(seedWarehouseId(), earliest));

                assertThat(row.getSourceOrderCount()).isEqualTo(2L);
                assertThat(row.getSourceLineCount()).isEqualTo(2L);
                assertThat(row.getOrderDemandQuantity()).isEqualByComparingTo("3.5000");
                // 本批可用 = 1.0（现货）- 0（其他占用）= 1.0；A 的 1.0 预留还回来 → 差额 3.5 - 1.0 = 2.5
                assertThat(row.getSelectedOrderReservedQuantity()).isEqualByComparingTo("1.0000");
                assertThat(row.getStockComparisonGap()).isEqualByComparingTo("2.5000");
            }

            @Test
            @DisplayName("仓库隔离：余额与预留都在别的仓库 → 目标仓库按 NO_BALANCE 处理，绝不跨仓混算")
            void doesNotMixWarehouses() {
                Fixture fixture = fixture("S7", "4.0000", "3.0000");
                Long otherWarehouse = newWarehouse("OTHER");
                seedBalance(otherWarehouse, fixture.skuId(), DEFAULT_PURCHASE_UNIT, "100.0000", "0.0000");
                reserveForOrder(otherWarehouse, fixture.skuId(), fixture.salesOrderId(), "3.0000");

                PurchaseDemandSummaryVO row = rowFor(fixture.skuId(), preview(seedWarehouseId(), fixture.confirmedAt()));

                // 目标仓库没有余额行，别的仓库的 100 与其预留完全不可见
                assertThat(row.getCalculationStatus()).isEqualTo("NO_BALANCE");
                assertThat(row.getOnHandQuantity()).isEqualByComparingTo("0.0000");
                assertThat(row.getReservedQuantity()).isEqualByComparingTo("0.0000");
                assertThat(row.getSelectedOrderReservedQuantity()).isEqualByComparingTo("0.0000");
                assertThat(row.getStockComparisonGap()).isEqualByComparingTo("3.0000");
            }

            @Test
            @DisplayName("只读：预览零库存写入、零采购需求写入，且重复调用结果一致")
            void isReadOnlyAndIdempotent() {
                Fixture fixture = fixture("S8", "6.0000", "2.0000");
                seedBalance(seedWarehouseId(), fixture.skuId(), DEFAULT_PURCHASE_UNIT, "1.0000", "0.0000");
                PurchaseDemandSummaryPreviewForm form = window(fixture.confirmedAt(), seedWarehouseId());

                List<PurchaseDemandSummaryVO> first = queryService.summaryPreview(form).getList();
                List<PurchaseDemandSummaryVO> second = queryService.summaryPreview(form).getList();
                assertThat(second).usingRecursiveComparison().isEqualTo(first);

                // 未生成任何采购需求
                assertThat(purchaseDemandDao.listActiveBySourceItemIds(List.of(fixture.salesOrderItemId()))).isEmpty();
                // 未产生任何库存流水
                Integer movements = jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_inventory_movement WHERE sku_id = ?", Integer.class, fixture.skuId());
                assertThat(movements).isZero();
                // 未新增任何预留行
                Integer reservations = jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_inventory_reservation WHERE sku_id = ?", Integer.class, fixture.skuId());
                assertThat(reservations).isZero();
            }
        }

        @Nested
        @DisplayName("批量少收关单 + 按商品收货工作台（PG IT）")
        class PurchaseEfficiencyIT {
            @Autowired
            private PurchaseQueryService queryService;

            // ------------------------------------------------------------------
            // 造数辅助
            // ------------------------------------------------------------------

            private PurchaseReceiptItemWorkbenchQueryForm workbench(Long supplierId) {
                PurchaseReceiptItemWorkbenchQueryForm form = new PurchaseReceiptItemWorkbenchQueryForm();
                form.setSupplierId(supplierId);
                form.setWarehouseId(seedWarehouseId());
                form.setPageNum(1L);
                form.setPageSize(50L);
                return form;
            }

            // ------------------------------------------------------------------
            // 批量少收关单
            // ------------------------------------------------------------------

            @Test
            @DisplayName("两张欠收单 → 批量关单全部转 SHORT_CLOSED，共享同一原因")
            void batchShortCloseClosesAllLegalOrders() {
                PurchaseOrderVO first = partiallyReceivedOrder("BC1");
                PurchaseOrderVO second = partiallyReceivedOrder("BC2");

                purchaseOrderService.batchShortClose(batchForm("供应商产能不足，剩余不再补", first, second));

                for (PurchaseOrderVO order : List.of(first, second)) {
                    PurchaseOrderVO closed = reloadOrder(order.getId());
                    assertThat(closed.getStatus()).isEqualTo("SHORT_CLOSED");
                    assertThat(closed.getShortCloseReason()).isEqualTo("供应商产能不足，剩余不再补");
                    assertThat(closed.getShortClosedAt()).isNotNull();
                }
            }

            @Test
            @DisplayName("批量含一张全收单 → 状态机拒绝，抛 40982")
            void batchShortCloseRejectsIllegalStateMember() {
                PurchaseOrderVO partially = partiallyReceivedOrder("BC3");
                PurchaseOrderVO received = fullyReceivedOrder("BC3r");

                expectCode(() -> purchaseOrderService.batchShortClose(
                        batchForm("整批关单", partially, received)), 40982);
            }

            @Test
            @DisplayName("批量含一张版本过期单 → 乐观锁拒绝，抛 40921")
            void batchShortCloseRejectsStaleVersionMember() {
                PurchaseOrderVO first = partiallyReceivedOrder("BC4");
                PurchaseOrderVO second = partiallyReceivedOrder("BC5");
                // 让低 id 那张带一个不存在的版本：按 id 升序处理时先撞上版本冲突
                PurchaseOrderVO stale = first.getId() < second.getId() ? first : second;
                stale.setVersion(stale.getVersion() + 999);

                expectCode(() -> purchaseOrderService.batchShortClose(
                        batchForm("整批关单", first, second)), 40921);
            }

            // ------------------------------------------------------------------
            // 按商品收货工作台（只读聚合）
            // ------------------------------------------------------------------

            @Test
            @DisplayName("单张欠收单：待收 = 计划 − 已收，超收 0，订单数 / 行数各 1")
            void workbenchShowsPendingPerSku() {
                PurchaseOrderVO order = partiallyReceivedOrder("WB1");
                // 取该单里欠收的那一行（SKU B）—— 它才有非零待收
                PurchaseOrderItemView target = pendingLineOf(order);

                PurchaseReceiptItemWorkbenchVO row = workbenchRow(target.skuId, target.supplierId);

                assertThat(row.getLineCount()).isEqualTo(1L);
                assertThat(row.getOrderCount()).isEqualTo(1L);
                assertThat(row.getPlannedQuantity()).isEqualByComparingTo("6.0000");
                assertThat(row.getReceivedQuantity()).isEqualByComparingTo("2.0000");
                assertThat(row.getPendingQuantity()).isEqualByComparingTo("4.0000");
                assertThat(row.getOverReceiptQuantity()).isEqualByComparingTo("0.0000");
            }

            @Test
            @DisplayName("同一 SKU 跨两张欠收单：工作台按 SKU×单位 汇总计划 / 已收 / 待收")
            void workbenchAggregatesAcrossOrders() {
                Long skuId = newOnShelfSku("WB2");
                Long supplierId = newPurchasableSupplier("WB2", skuId);

                for (int i = 0; i < 2; i++) {
                    String suffix = "WB2-" + i;
                    PurchaseOrderVO order = createDraftOrder(suffix, supplierId, skuId, "10.0000", "6.2000");
                    submitOrder(order.getId());
                    PurchaseReceiptVO created = createReceipt(order.getId());
                    PurchaseReceiptVO receipt = reloadReceipt(created.getId());
                    PurchaseReceiptItemVO line = receipt.getItems().getFirst();
                    // 每张收 3.0000，欠 7.0000
                    purchaseReceiptService.confirm(
                            confirmForm(receipt.getId(), receipt.getVersion(),
                                    receiptLine(line.getId(), line.getVersion(), "3.0000")),
                            prefix + ":" + suffix + ":rc");
                }

                PurchaseReceiptItemWorkbenchVO row = workbenchRow(skuId, supplierId);

                assertThat(row.getOrderCount()).isEqualTo(2L);
                assertThat(row.getLineCount()).isEqualTo(2L);
                assertThat(row.getPlannedQuantity()).isEqualByComparingTo("20.0000");
                assertThat(row.getReceivedQuantity()).isEqualByComparingTo("6.0000");
                assertThat(row.getPendingQuantity()).isEqualByComparingTo("14.0000");
                assertThat(row.getOverReceiptQuantity()).isEqualByComparingTo("0.0000");
            }

            @Test
            @DisplayName("全收齐（RECEIVED）订单不进工作台：状态过滤只覆盖 SUBMITTED / PARTIALLY_RECEIVED")
            void workbenchExcludesReceivedOrders() {
                PurchaseOrderVO received = fullyReceivedOrder("WB3");
                Long skuId = received.getItems().getFirst().getSkuId();
                Long supplierId = received.getSupplierId();

                List<PurchaseReceiptItemWorkbenchVO> rows =
                        queryService.receiptItemWorkbench(workbench(supplierId)).getList();

                assertThat(rows).noneMatch(r -> r.getSkuId().equals(skuId));
            }

            // ------------------------------------------------------------------
            // 工作台读侧小工具
            // ------------------------------------------------------------------

            private record PurchaseOrderItemView(Long skuId, Long supplierId) {
            }

            /**
             * 从一张混合收付单里挑出<b>欠收</b>的那一行（{@code PARTIALLY_RECEIVED} 下必存在）。
             */
            private PurchaseOrderItemView pendingLineOf(PurchaseOrderVO order) {
                return order.getItems().stream()
                        .filter(it -> it.getReceivedQuantity() != null
                                && it.getReceivedQuantity().compareTo(it.getPlannedQuantity()) < 0)
                        .findFirst()
                        .map(it -> new PurchaseOrderItemView(it.getSkuId(), order.getSupplierId()))
                        .orElseThrow(() -> new AssertionError("混合单应存在一行欠收"));
            }

            private PurchaseReceiptItemWorkbenchVO workbenchRow(Long skuId, Long supplierId) {
                return queryService.receiptItemWorkbench(workbench(supplierId)).getList().stream()
                        .filter(r -> r.getSkuId().equals(skuId))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("工作台缺少 SKU " + skuId + " 的汇总行"));
            }
        }

        @Nested
        @DisplayName("采购操作日志：Q14 归属四分支 + 保存的数据约定（PG IT）")
        class PurchaseOperationLogIT {
            private Map<String, Object> latestLog(String operationType) {
                return jdbc.queryForMap(
                        "SELECT purchase_order_id, purchase_receipt_id, operation_type, operator, reason, "
                                + "before_data, after_data FROM xsy_purchase_operation_log "
                                + "WHERE operation_type = ? ORDER BY id DESC LIMIT 1", operationType);
            }

            private PurchaseReceiptVO confirm(ReceiptFixture fx, String quantity) {
                PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
                PurchaseReceiptItemVO line = current.getItems().getFirst();
                return purchaseReceiptService.confirm(
                        confirmForm(current.getId(), current.getVersion(),
                                receiptLine(line.getId(), line.getVersion(), quantity)),
                        prefix + ":log:" + current.getId() + ":" + quantity);
            }

            // ------------------------------------------------------------------
            // 1. DEMAND_GENERATE：两个归属都为空
            // ------------------------------------------------------------------

            @Test
            @DisplayName("Q14 分支一：DEMAND_GENERATE 的两个归属 id 同时为空（无前态保存的数据）")
            void demandGenerateLogHasBothOwnerIdsNull() {
                Long skuId = newOnShelfSku("LG1");
                Long supplierId = newPurchasableSupplier("LG1", skuId);
                Long customerId = newCustomer();
                Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
                generateDemandFor(supplierId, salesOrder);

                Map<String, Object> log = latestLog("DEMAND_GENERATE");
                assertThat(log.get("purchase_order_id")).isNull();
                assertThat(log.get("purchase_receipt_id")).isNull();
                // 操作者落的是 userType:employeeId（ScmOperator 的规则），不是员工姓名
                assertThat(log.get("operator")).isEqualTo(ScmOperator.current());
                assertThat(log.get("before_data")).isNull();
                assertThat(String.valueOf(log.get("after_data"))).contains("createdCount");
            }

            // ------------------------------------------------------------------
            // 2. DEMAND_ALLOCATE：反查得到采购单 id
            // ------------------------------------------------------------------

            @Test
            @DisplayName("Q14 分支二：DEMAND_ALLOCATE 通过 purchaseOrderItemId 反查出 purchase_order_id")
            void demandAllocateLogResolvesPurchaseOrderId() {
                Long skuId = newOnShelfSku("LG2");
                Long supplierId = newPurchasableSupplier("LG2", skuId);
                Long customerId = newCustomer();
                Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
                PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);

                // 采购单行先建出来（不带分配），再由独立分配端点补上 —— 这条路径才会写 DEMAND_ALLOCATE
                PurchaseOrderVO order = createDraftOrder("LG2", supplierId, skuId, "3.0000", "6.2000");
                Long orderItemId = order.getItems().getFirst().getId();
                assertThat(order.getItems().getFirst().getAllocations()).isEmpty();

                // allocate 只接受 SUBMITTED 的采购单：DRAFT 还没定稿，终态已结束（40982）
                submitOrder(order.getId());

                PurchaseDemandAllocateForm form = new PurchaseDemandAllocateForm();
                form.setDemandId(demand.getId());
                form.setPurchaseOrderItemId(orderItemId);
                form.setQuantity("3.0000");
                form.setSupplierId(supplierId);
                form.setWarehouseId(seedWarehouseId());
                form.setVersion(reloadDemand(demand.getId()).getVersion());
                purchaseDemandService.allocate(form, prefix + ":LG2:alloc");

                Map<String, Object> log = latestLog("DEMAND_ALLOCATE");
                assertThat(log.get("purchase_order_id")).isEqualTo(order.getId());
                assertThat(log.get("purchase_receipt_id")).isNull();
                // 分配确实落地了，而不只是写了条日志
                assertThat(allocationOf(orderItemId, demand.getId()).getAllocatedQuantity())
                        .isEqualByComparingTo("3.0000");
            }

            // ------------------------------------------------------------------
            // 3. RECEIPT_*：两个归属都非空 + 只能新增
            // ------------------------------------------------------------------

            @Test
            @DisplayName("Q14 分支四：RECEIPT_CREATE / RECEIPT_CONFIRM 两个归属都非空，且早期日志不被改写")
            void receiptLogsCarryBothOwnerIdsAndAreAppendOnly() {
                ReceiptFixture fx = receiptFixture("LG3", "10.0000");
                // 记下 RECEIPT_CREATE 的原始保存的数据，确认之后回看必须一字不变
                Map<String, Object> createLogBefore = latestLog("RECEIPT_CREATE");

                PurchaseReceiptVO confirmed = confirm(fx, "10.0000");

                Map<String, Object> createLog = latestLog("RECEIPT_CREATE");
                assertThat(createLog.get("purchase_order_id")).isEqualTo(fx.order().getId());
                assertThat(createLog.get("purchase_receipt_id")).isEqualTo(fx.receipt().getId());
                assertThat(createLog.get("after_data")).isEqualTo(createLogBefore.get("after_data"));

                Map<String, Object> confirmLog = latestLog("RECEIPT_CONFIRM");
                assertThat(confirmLog.get("purchase_order_id")).isEqualTo(fx.order().getId());
                assertThat(confirmLog.get("purchase_receipt_id")).isEqualTo(confirmed.getId());
                // 确认日志的 after_data 带上本次单据的新状态
                assertThat(String.valueOf(confirmLog.get("after_data"))).contains("RECEIVED");
            }

            // ------------------------------------------------------------------
            // 4. 单据变更日志的已存数据形状
            // ------------------------------------------------------------------

            @Test
            @DisplayName("CREATE 无前态、UPDATE 有前态与后态，且状态可读")
            void orderMutationLogsExposeStatusSnapshots() {
                Long skuId = newOnShelfSku("LG4");
                Long supplierId = newPurchasableSupplier("LG4", skuId);
                Long customerId = newCustomer();
                Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
                PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);
                PurchaseOrderVO order = createDraftOrder("LG4", supplierId, skuId, "3.0000", "6.2000",
                        allocation(demand, "3.0000"));

                List<PurchaseOperationLogVO> logs = purchaseQueryService.orderLogs(order.getId());
                assertThat(logs).hasSize(1);
                PurchaseOperationLogVO create = logs.getFirst();
                assertThat(create.getOperationType()).isEqualTo("CREATE");
                assertThat(create.getBeforeData()).isNull();
                assertThat(create.getAfterData()).containsEntry("status", "SUBMITTED");

                // UPDATE：前后态都有，且能看出金额变化
                purchaseOrderService.update(editForm(order.getId(), "2.0000", "5.0000",
                        allocation(reloadDemand(demand.getId()), "2.0000")));

                logs = purchaseQueryService.orderLogs(order.getId());
                assertThat(logs).extracting(PurchaseOperationLogVO::getOperationType)
                        .containsExactly("UPDATE", "CREATE");

                PurchaseOperationLogVO update = logs.getFirst();
                assertThat(update.getBeforeData()).containsEntry("totalAmount", "18.6000");
                assertThat(update.getAfterData()).containsEntry("totalAmount", "10.0000");
                assertThat(update.getBeforeData()).containsKeys("items", "status", "version");
                assertThat(update.getAfterData()).containsKeys("items", "status", "version");

                // 只能新增：id 严格递减（DESC 排序下等价于「严格递增」，条数与动作数一致）
                assertThat(logs).extracting(PurchaseOperationLogVO::getId)
                        .isSortedAccordingTo(Comparator.reverseOrder());
            }

            // ------------------------------------------------------------------
            // 5. CANCEL 的 reason 独立于保存的数据
            // ------------------------------------------------------------------

            @Test
            @DisplayName("CANCEL：原因落在日志的 reason 列（不是只塞进 after_data）")
            void cancelLogCarriesReasonOutsideSnapshot() {
                Long skuId = newOnShelfSku("LG5");
                Long supplierId = newPurchasableSupplier("LG5", skuId);
                Long customerId = newCustomer();
                Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
                PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);
                PurchaseOrderVO order = createDraftOrder("LG5", supplierId, skuId, "3.0000", "6.2000",
                        allocation(demand, "3.0000"));

                PurchaseOrderCancelForm cancel = new PurchaseOrderCancelForm();
                cancel.setId(order.getId());
                cancel.setVersion(order.getVersion());
                cancel.setCancelReason("客户取消订单");
                purchaseOrderService.cancel(cancel, prefix + ":LG5:cancel");

                Map<String, Object> log = latestLog("CANCEL");
                assertThat(log.get("purchase_order_id")).isEqualTo(order.getId());
                assertThat(log.get("purchase_receipt_id")).isNull();
                // reason 是独立列：审计查询不需要解析 JSONB
                assertThat(log.get("reason")).isEqualTo("客户取消订单");
                assertThat(String.valueOf(log.get("after_data"))).contains("CANCELLED");
            }
        }

        @Nested
        @DisplayName("采购写命令防重复处理：重放 / 冲突 / 键校验 / 操作者隔离（PG IT）")
        class PurchaseOrderIdempotencyIT {
            private int countActiveOrders(Long supplierId) {
                return jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_purchase_order WHERE supplier_id = ? AND deleted = FALSE",
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
                // 同一个请求实例用两次：hash 必须一致，否则会被判成「同键异内容」
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

                // ---- scope 拼操作者：换操作者、同 key、同内容 → 必须各自独立执行 ----
                Long skuB = newOnShelfSku("ID1b");
                Long supplierB = newPurchasableSupplier("ID1b", skuB);
                Long orderB = confirmedSalesOrder(customerId, skuB, "2.0000", "2.0000");
                PurchaseDemandEntity demandB = generateDemandFor(supplierB, orderB);

                RequestEmployee other = new RequestEmployee();
                other.setEmployeeId(2L);
                other.setActualName("W5 IT B");
                other.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                XsyRequestUtil.setRequestUser(other);

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
            @DisplayName("防重复请求编号：缺失 → 40084；超过 200 字符 → 40085")
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
        }

        @Nested
        @DisplayName("采购单乐观锁：单头版本 / 行版本（PG IT）")
        class PurchaseOrderOptimisticLockIT {
            /**
             * 一张新提交的采购单 + 它引用的需求（编辑请求需要需求的当前版本）。
             */
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
            // 2. 新建即提交，单头版本从 0 开始
            // ------------------------------------------------------------------

            @Test
            @DisplayName("create：新单直接进入 SUBMITTED，版本从 0 开始")
            void createdOrderStartsSubmittedAtVersionZero() {
                Draft draft = draft("OL2");

                PurchaseOrderVO created = reloadOrder(draft.order().getId());
                assertThat(created.getStatus()).isEqualTo("SUBMITTED");
                assertThat(created.getSubmittedAt()).isNotNull();
                assertThat(created.getVersion()).isZero();
                assertThat(purchaseQueryService.orderLogs(draft.order().getId()))
                        .extracting(log -> log.getOperationType())
                        .containsExactly("CREATE");
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
                // 单头版本是当前值（合法），只有行版本过期 —— 这正是「只校验单头」会漏掉的场景
                form.getItems().getFirst().setVersion(form.getItems().getFirst().getVersion() + 3);

                expectCode(() -> purchaseOrderService.update(form), 40984);

                PurchaseOrderVO after = reloadOrder(draft.order().getId());
                assertThat(after.getItems().getFirst().getPlannedQuantity()).isEqualByComparingTo("3.0000");
                assertThat(after.getTotalAmount()).isEqualByComparingTo("18.6000");
                assertThat(reloadDemand(draft.demand().getId()).getAllocatedQuantity())
                        .isEqualByComparingTo("3.0000");
            }
        }

        @Nested
        @DisplayName("采购单生命周期：create / update / submit / cancel / delete（PG IT）")
        class PurchaseOrderServiceIT {
            // ------------------------------------------------------------------
            // 1. create
            // ------------------------------------------------------------------

            @Test
            @DisplayName("create：单号 / 保存的数据 / 金额 / 分配 / 需求侧一次写全")
            void createPersistsOrderWithNumberSnapshotsAndTotalAmount() {
                Long skuId = newOnShelfSku("PO1");
                Long supplierId = newPurchasableSupplier("PO1", skuId);
                Long customerId = newCustomer();
                Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
                PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);

                PurchaseOrderVO order = createDraftOrder("PO1", supplierId, skuId, "3.0000", "6.2000",
                        allocation(demand, "3.0000"));

                assertThat(order.getOrderNo()).matches("^XSY\\d{18,19}$");
                assertThat(order.getStatus()).isEqualTo("SUBMITTED");

                // 供应商 / 仓库保存的数据在创建时保存：历史永不回读主数据
                assertThat(order.getSupplierId()).isEqualTo(supplierId);
                assertThat(order.getSupplierCode()).isNotBlank();
                assertThat(order.getSupplierName()).isNotBlank();
                assertThat(order.getWarehouseId()).isEqualTo(seedWarehouseId());
                assertThat(order.getWarehouseCode()).isEqualTo(SEED_WAREHOUSE_CODE);
                assertThat(order.getWarehouseName()).isNotBlank();
                assertThat(order.getPurchaserId()).isNotNull();
                assertThat(order.getPlannedArrivalDate()).isNotNull();

                // 3.0000 × 6.2000 = 18.6000
                assertThat(order.getTotalAmount()).isEqualByComparingTo("18.6000");

                assertThat(order.getItems()).hasSize(1);
                PurchaseOrderItemVO item = order.getItems().getFirst();
                assertThat(item.getSkuId()).isEqualTo(skuId);
                assertThat(item.getPlannedQuantity()).isEqualByComparingTo("3.0000");
                assertThat(item.getPurchasePrice()).isEqualByComparingTo("6.2000");
                assertThat(item.getLineAmount()).isEqualByComparingTo("18.6000");
                assertThat(item.getPurchaseUnit()).isEqualTo(DEFAULT_PURCHASE_UNIT);
                assertThat(item.getSortOrder()).isZero();
                assertThat(item.getAllocations()).hasSize(1);
                assertThat(item.getAllocations().getFirst().getDemandId()).isEqualTo(demand.getId());

                // 需求侧：分配落账 → ALLOCATED，且供应商被固定到需求上
                PurchaseDemandEntity reloaded = reloadDemand(demand.getId());
                assertThat(reloaded.getAllocatedQuantity()).isEqualByComparingTo("3.0000");
                assertThat(reloaded.getStatus()).isEqualTo("ALLOCATED");
                assertThat(reloaded.getSupplierId()).isEqualTo(supplierId);

                // CREATE 日志：before 为空、after 是全量保存的数据
                List<PurchaseOperationLogVO> logs = purchaseQueryService.orderLogs(order.getId());
                assertThat(logs).hasSize(1);
                assertThat(logs.getFirst().getOperationType()).isEqualTo("CREATE");
                assertThat(logs.getFirst().getPurchaseOrderId()).isEqualTo(order.getId());
                assertThat(logs.getFirst().getBeforeData()).isNull();
                assertThat(logs.getFirst().getAfterData()).isNotNull();
                assertThat(logs.getFirst().getAfterData()).containsEntry("orderNo", order.getOrderNo());
            }

            // ------------------------------------------------------------------
            // 2. create 拒绝既有行 id
            // ------------------------------------------------------------------

            @Test
            @DisplayName("create：请求里带既有采购行 id → 40983（新建不允许「顺手改旧行」）")
            void createRejectsExistingItemId() {
                Long skuId = newOnShelfSku("PO2");
                Long supplierId = newPurchasableSupplier("PO2", skuId);
                Long customerId = newCustomer();
                Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
                PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);
                PurchaseOrderVO existing = createDraftOrder("PO2", supplierId, skuId, "3.0000", "6.2000",
                        allocation(demand, "3.0000"));

                PurchaseOrderAddForm form = orderForm(supplierId, seedWarehouseId(), skuId, "3.0000", "6.2000",
                        allocation(demand, "3.0000"));
                form.getItems().getFirst().setId(existing.getItems().getFirst().getId());

                expectCode(() -> purchaseOrderService.create(form, prefix + ":PO2:again"), 40983);
            }

            // ------------------------------------------------------------------
            // 3. create 未知需求
            // ------------------------------------------------------------------

            @Test
            @DisplayName("create：分配引用不存在的需求 → 40480")
            void createRejectsUnknownDemand() {
                Long skuId = newOnShelfSku("PO3");
                Long supplierId = newPurchasableSupplier("PO3", skuId);

                PurchaseOrderAddForm.Allocation ghost = new PurchaseOrderAddForm.Allocation();
                ghost.setDemandId(999_999_999L);
                ghost.setQuantity("1.0000");
                ghost.setDemandVersion(0);

                PurchaseOrderAddForm form = orderForm(supplierId, seedWarehouseId(), skuId, "1.0000", "1.0000", ghost);
                expectCode(() -> purchaseOrderService.create(form, prefix + ":PO3:po"), 40480);
            }

            // ------------------------------------------------------------------
            // 4. create 主数据停用
            // ------------------------------------------------------------------

            @Test
            @DisplayName("create：停用供应商 → 40986；停用仓库 → 40987")
            void createRejectsDisabledSupplierAndWarehouse() {
                Long skuId = newOnShelfSku("PO4");
                Long supplierId = newPurchasableSupplier("PO4", skuId);

                // 供应商：直改库置 DISABLED（供应商启停不经过采购侧）
                assertThat(jdbc.update("UPDATE xsy_supplier SET status = 'DISABLED', version = version + 1 "
                        + "WHERE id = ? AND deleted = FALSE", supplierId)).isEqualTo(1);
                evictMybatisCache();
                PurchaseOrderAddForm form = orderForm(supplierId, seedWarehouseId(), skuId, "1.0000", "1.0000");
                expectCode(() -> purchaseOrderService.create(form, prefix + ":PO4:po"), 40986);

                // 仓库：用基类的停用辅助，并把供应商改回 ENABLED
                assertThat(jdbc.update("UPDATE xsy_supplier SET status = 'ENABLED', version = version + 1 "
                        + "WHERE id = ? AND deleted = FALSE", supplierId)).isEqualTo(1);
                evictMybatisCache();

                Long warehouseId = newWarehouse("PO4");
                disableWarehouse(warehouseId);
                PurchaseOrderAddForm second = orderForm(supplierId, warehouseId, skuId, "1.0000", "1.0000");
                expectCode(() -> purchaseOrderService.create(second, prefix + ":PO4:po2"), 40987);
            }

            // ------------------------------------------------------------------
            // 5. update 改数量 → 需求侧重算
            // ------------------------------------------------------------------

            @Test
            @DisplayName("update：改计划量 → 金额刷新、需求 allocated 重算、UPDATE 日志带 before/after")
            void updateRecomputesAmountAndDemand() {
                Long skuId = newOnShelfSku("PO5");
                Long supplierId = newPurchasableSupplier("PO5", skuId);
                Long customerId = newCustomer();
                Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
                PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);

                PurchaseOrderVO order = createDraftOrder("PO5", supplierId, skuId, "3.0000", "6.2000",
                        allocation(demand, "1.0000"));
                assertThat(reloadDemand(demand.getId()).getStatus()).isEqualTo("PARTIALLY_ALLOCATED");

                PurchaseDemandEntity fresh = reloadDemand(demand.getId());
                PurchaseOrderUpdateForm form = editForm(order.getId(), "2.0000", "5.0000",
                        allocation(fresh, "2.0000"));
                PurchaseOrderVO updated = purchaseOrderService.update(form);

                // 行：2.0000 × 5.0000 = 10.0000
                assertThat(updated.getTotalAmount()).isEqualByComparingTo("10.0000");
                assertThat(updated.getItems().getFirst().getPlannedQuantity()).isEqualByComparingTo("2.0000");
                assertThat(updated.getItems().getFirst().getPurchasePrice()).isEqualByComparingTo("5.0000");
                assertThat(updated.getItems().getFirst().getLineAmount()).isEqualByComparingTo("10.0000");
                // 版本推进
                assertThat(updated.getVersion()).isGreaterThan(order.getVersion());

                // 需求侧：1.0000 → 2.0000（不是叠加成 3.0000）
                PurchaseDemandEntity afterUpdate = reloadDemand(demand.getId());
                assertThat(afterUpdate.getAllocatedQuantity()).isEqualByComparingTo("2.0000");
                assertThat(afterUpdate.getStatus()).isEqualTo("PARTIALLY_ALLOCATED");

                // UPDATE 日志：before / after 都是全量保存的数据。
                // 日志按 {@code created_at DESC, id DESC} 返回（与 order_operation_log 一致）→ 最新在前
                List<PurchaseOperationLogVO> logs = purchaseQueryService.orderLogs(order.getId());
                assertThat(logs).extracting(PurchaseOperationLogVO::getOperationType)
                        .containsExactly("UPDATE", "CREATE");
                PurchaseOperationLogVO updateLog = logs.getFirst();
                assertThat(updateLog.getBeforeData()).isNotNull();
                assertThat(updateLog.getAfterData()).isNotNull();
                assertThat(updateLog.getBeforeData()).containsEntry("totalAmount", "18.6000");
                assertThat(updateLog.getAfterData()).containsEntry("totalAmount", "10.0000");
            }

            @Test
            @DisplayName("update：替换采购行后，需求分配引用新行且旧分配释放")
            void updateReplacesItemAndLinksAllocationsToNewItem() {
                Long skuId = newOnShelfSku("PO_NEW_ROW");
                Long supplierId = newPurchasableSupplier("PO_NEW_ROW", skuId);
                Long salesOrder = confirmedSalesOrder(newCustomer(), skuId, "5.0000", "3.0000");
                PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);
                PurchaseOrderVO order = createDraftOrder("PO_NEW_ROW", supplierId, skuId, "3.0000", "6.2000",
                        allocation(demand, "1.0000"));
                Long oldItemId = order.getItems().getFirst().getId();
                PurchaseOrderUpdateForm form = editForm(order.getId(), "3.0000", "6.2000",
                        allocation(reloadDemand(demand.getId()), "2.0000"));
                form.getItems().getFirst().setId(null);
                form.getItems().getFirst().setVersion(null);

                PurchaseOrderVO updated = purchaseOrderService.update(form);

                assertThat(updated.getItems()).hasSize(1);
                PurchaseOrderItemVO item = updated.getItems().getFirst();
                assertThat(item.getId()).isNotEqualTo(oldItemId);
                assertThat(item.getAllocations()).hasSize(1);
                assertThat(item.getAllocations().getFirst().getDemandId()).isEqualTo(demand.getId());
                assertThat(allocationOf(item.getId(), demand.getId()).getAllocatedQuantity())
                        .isEqualByComparingTo("2.0000");
                assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_purchase_demand_allocation "
                        + "WHERE purchase_order_item_id = ? AND deleted = FALSE", Long.class, oldItemId)).isZero();
                assertThat(reloadDemand(demand.getId()).getAllocatedQuantity()).isEqualByComparingTo("2.0000");
            }

            // ------------------------------------------------------------------
            // 6. update 保留行的已收数量
            // ------------------------------------------------------------------

            @Test
            @DisplayName("update：保留行必须继承库中 received_quantity —— 请求只表达计划量")
            void updateKeepsReceivedQuantityOfRetainedRow() {
                Long skuId = newOnShelfSku("PO6");
                Long supplierId = newPurchasableSupplier("PO6", skuId);
                Long customerId = newCustomer();
                Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
                PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);

                PurchaseOrderVO order = createDraftOrder("PO6", supplierId, skuId, "3.0000", "6.2000",
                        allocation(demand, "3.0000"));
                Long itemId = order.getItems().getFirst().getId();

                // 尚可编辑的单已收量现实中恒为 0，这里写入一个非零值：
                // 若 update 依赖「已收恒为 0」这个可松动的隐含前提，就会把它静默清 0。
                assertThat(jdbc.update("UPDATE xsy_purchase_order_item SET received_quantity = 1.5000 "
                        + "WHERE id = ? AND deleted = FALSE", itemId)).isEqualTo(1);
                evictMybatisCache();

                PurchaseDemandEntity fresh = reloadDemand(demand.getId());
                purchaseOrderService.update(editForm(order.getId(), "3.0000", "6.2000",
                        allocation(fresh, "3.0000")));

                PurchaseOrderItemVO after = reloadOrder(order.getId()).getItems().getFirst();
                assertThat(after.getId()).isEqualTo(itemId);
                assertThat(after.getReceivedQuantity()).isEqualByComparingTo("1.5000");
                // 对账量随之派生：remaining = planned − received = 1.5000
                assertThat(after.getRemainingQuantity()).isEqualByComparingTo("1.5000");
                assertThat(after.getOverReceiptQuantity()).isEqualByComparingTo("0.0000");
            }

            // ------------------------------------------------------------------
            // 7. create 即提交
            // ------------------------------------------------------------------

            @Test
            @DisplayName("create：新建即 SUBMITTED，写提交时间并启动自动锁单计时")
            void createStartsSubmittedWithLockCountdown() {
                Long skuId = newOnShelfSku("PO7");
                Long supplierId = newPurchasableSupplier("PO7", skuId);
                Long customerId = newCustomer();
                Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
                PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);

                PurchaseOrderVO order = createDraftOrder("PO7", supplierId, skuId, "3.0000", "6.2000",
                        allocation(demand, "3.0000"));

                assertThat(order.getStatus()).isEqualTo("SUBMITTED");
                assertThat(order.getSubmittedAt()).isNotNull();
                assertThat(order.getAutoLockAt()).isNotNull();
                assertThat(purchaseQueryService.orderLogs(order.getId()))
                        .extracting(PurchaseOperationLogVO::getOperationType)
                        .containsExactly("CREATE");
            }

            // ------------------------------------------------------------------
            // 8. cancel 释放分配
            // ------------------------------------------------------------------

            @Test
            @DisplayName("cancel：释放全部分配 + 需求回落 PENDING + 原因落日志")
            void cancelReleasesAllocationsAndDemandFallsBack() {
                Long skuId = newOnShelfSku("PO8");
                Long supplierId = newPurchasableSupplier("PO8", skuId);
                Long customerId = newCustomer();
                Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
                PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);

                PurchaseOrderVO order = createDraftOrder("PO8", supplierId, skuId, "3.0000", "6.2000",
                        allocation(demand, "3.0000"));
                assertThat(reloadDemand(demand.getId()).getStatus()).isEqualTo("ALLOCATED");

                PurchaseOrderCancelForm cancel = new PurchaseOrderCancelForm();
                cancel.setId(order.getId());
                cancel.setVersion(order.getVersion());
                cancel.setCancelReason("供应商临时缺货");
                PurchaseOrderVO cancelled = purchaseOrderService.cancel(cancel, prefix + ":PO8:cancel");

                assertThat(cancelled.getStatus()).isEqualTo("CANCELLED");
                assertThat(cancelled.getCancelledAt()).isNotNull();
                assertThat(cancelled.getCancelReason()).isEqualTo("供应商临时缺货");

                // 分配必须被释放：否则需求永久卡在 ALLOCATED
                assertThat(allocationsOf(order.getId())).isEmpty();
                PurchaseDemandEntity afterCancel = reloadDemand(demand.getId());
                assertThat(afterCancel.getAllocatedQuantity()).isEqualByComparingTo("0.0000");
                assertThat(afterCancel.getStatus()).isEqualTo("PENDING");

                // 原因落进 CANCEL 日志的 reason 字段（不只是塞在 after_data 里）。DESC → 最新在最前
                PurchaseOperationLogVO cancelLog = purchaseQueryService.orderLogs(order.getId()).getFirst();
                assertThat(cancelLog.getOperationType()).isEqualTo("CANCEL");
                assertThat(cancelLog.getReason()).isEqualTo("供应商临时缺货");
            }

            // ------------------------------------------------------------------
            // 9. delete
            // ------------------------------------------------------------------

            @Test
            @DisplayName("delete：仅尚可编辑的单可删（已收货 → 40993），且删除本身防重复处理")
            void deleteOnlyAllowsEditableOrdersAndIsIdempotent() {
                Long skuId = newOnShelfSku("PO9");
                Long supplierId = newPurchasableSupplier("PO9", skuId);
                Long customerId = newCustomer();
                Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
                PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);

                // 新建即提交、尚未收货：可删，并释放分配
                PurchaseOrderVO draft = createDraftOrder("PO9", supplierId, skuId, "3.0000", "6.2000",
                        allocation(demand, "3.0000"));
                PurchaseOrderDeleteForm delete = new PurchaseOrderDeleteForm();
                delete.setId(draft.getId());
                purchaseOrderService.delete(delete);

                assertThat(allocationsOf(draft.getId())).isEmpty();
                assertThat(reloadDemand(demand.getId()).getStatus()).isEqualTo("PENDING");
                // 防重复处理：再删一次不报错
                purchaseOrderService.delete(delete);

                // 已进入收货流程：删除 → 40993
                PurchaseDemandEntity secondDemand = generateDemandFor(supplierId, salesOrder);
                PurchaseOrderVO submitted = createDraftOrder("PO9b", supplierId, skuId, "1.0000", "6.2000",
                        allocation(secondDemand, "1.0000"));
                jdbc.update("UPDATE xsy_purchase_order SET status='PARTIALLY_RECEIVED' WHERE id=?", submitted.getId());

                PurchaseOrderDeleteForm blocked = new PurchaseOrderDeleteForm();
                blocked.setId(submitted.getId());
                expectCode(() -> purchaseOrderService.delete(blocked), 40993);
            }

            // ------------------------------------------------------------------
            // 10. 状态机边界
            // ------------------------------------------------------------------

            @Test
            @DisplayName("状态机边界：未收货不可少收关单（40982），终态不可编辑（40982）")
            void stateMachineBlocksShortCloseAndEditOutsideAllowedStates() {
                Long skuId = newOnShelfSku("PO10");
                Long supplierId = newPurchasableSupplier("PO10", skuId);
                Long customerId = newCustomer();
                Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
                PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);

                PurchaseOrderVO order = createDraftOrder("PO10", supplierId, skuId, "3.0000", "6.2000",
                        allocation(demand, "3.0000"));

                // 少收关单只允许从 PARTIALLY_RECEIVED 出发
                PurchaseOrderShortCloseForm shortClose = new PurchaseOrderShortCloseForm();
                shortClose.setId(order.getId());
                shortClose.setVersion(order.getVersion());
                shortClose.setShortCloseReason("供应商只发一半");
                expectCode(() -> purchaseOrderService.shortClose(shortClose, prefix + ":PO10:sc"), 40982);

                // 采购确认之后不可再编辑行 / 分配
                jdbc.update("UPDATE xsy_purchase_order SET status='ACCOUNTED' WHERE id=?", order.getId());

                PurchaseDemandEntity fresh = reloadDemand(demand.getId());
                PurchaseOrderUpdateForm edit = editForm(order.getId(), "3.0000", "6.2000",
                        allocation(fresh, "3.0000"));
                expectCode(() -> purchaseOrderService.update(edit), 40982);

                // 编辑失败不能改到需求侧
                assertThat(reloadDemand(demand.getId()).getAllocatedQuantity()).isEqualByComparingTo("3.0000");

                // 分配身份的唯一键：同一 (item, demand) 的第二条活动分配被库层拒绝
                PurchaseDemandAllocationEntity existing =
                        allocationOf(order.getItems().getFirst().getId(), demand.getId());
                expectSqlFailure(
                        "INSERT INTO xsy_purchase_demand_allocation (purchase_demand_id, purchase_order_item_id, "
                                + "sales_order_id, sales_order_item_id, sku_id, allocated_quantity, created_by) "
                                + "VALUES (?, ?, ?, ?, ?, 1.0000, 'dup')",
                        existing.getPurchaseDemandId(), existing.getPurchaseOrderItemId(),
                        existing.getSalesOrderId(), existing.getSalesOrderItemId(), existing.getSkuId());

                // 未分配的需求也必须能进采购单：allocations 缺失 = 该行没有需求来源
                Long freeSkuId = newOnShelfSku("PO10b");
                Long freeSupplierId = newPurchasableSupplier("PO10b", freeSkuId);
                PurchaseOrderVO freeOrder = createDraftOrder("PO10b", freeSupplierId, freeSkuId,
                        "2.0000", "3.0000");
                assertThat(freeOrder.getItems().getFirst().getAllocations()).isEmpty();
                assertThat(freeOrder.getTotalAmount()).isEqualByComparingTo("6.0000");
                assertThat(allocationsOf(freeOrder.getId())).isEmpty();
            }
        }

        @Nested
        @DisplayName("收货累计与乐观锁：累加 / 单据版本 / 行版本（PG IT）")
        class PurchaseReceiptConcurrencyIT {
            private PurchaseReceiptVO confirm(PurchaseReceiptVO receipt, String quantity, String suffix) {
                PurchaseReceiptItemVO line = receipt.getItems().getFirst();
                return purchaseReceiptService.confirm(
                        confirmForm(receipt.getId(), receipt.getVersion(),
                                receiptLine(line.getId(), line.getVersion(), quantity)),
                        prefix + ":conc:" + suffix);
            }

            // ------------------------------------------------------------------
            // 1. 分次到货累加
            // ------------------------------------------------------------------

            @Test
            @DisplayName("两次收货在同一采购行累加：6.0000 + 3.0000 = 9.0000")
            void twoReceiptsAccumulateOnTheSameOrderItem() {
                ReceiptFixture fx = receiptFixture("CC1", "10.0000");

                confirm(fx.receipt(), "6.0000", "a");
                assertThat(reloadOrder(fx.order().getId()).getItems().getFirst().getReceivedQuantity())
                        .isEqualByComparingTo("6.0000");

                PurchaseReceiptVO second = createAnotherReceipt(fx.order().getId(), "b");
                confirm(second, "3.0000", "b");

                assertThat(reloadOrder(fx.order().getId()).getItems().getFirst().getReceivedQuantity())
                        .isEqualByComparingTo("9.0000");

                // 第二张单：本次 3.0000，累计 9.0000，还差 1.0000
                PurchaseReceiptItemVO line = reloadReceipt(second.getId()).getItems().getFirst();
                assertThat(line.getReceivedQuantity()).isEqualByComparingTo("3.0000");
                assertThat(line.getCumulativeReceivedQuantity()).isEqualByComparingTo("9.0000");
                assertThat(line.getRemainingQuantity()).isEqualByComparingTo("1.0000");
                assertThat(line.getReceiptDifference()).isEqualByComparingTo("-1.0000");

                // 第一张单的历史值不被第二次确认改写
                PurchaseReceiptItemVO firstLine = reloadReceipt(fx.receipt().getId()).getItems().getFirst();
                assertThat(firstLine.getReceivedQuantity()).isEqualByComparingTo("6.0000");
                assertThat(firstLine.getCumulativeReceivedQuantity()).isEqualByComparingTo("6.0000");
            }

            // ------------------------------------------------------------------
            // 2. 收货单版本
            // ------------------------------------------------------------------

            @Test
            @DisplayName("收货单版本过期 → 40921，单据保持待确认、采购行不动")
            void staleReceiptVersionRejected() {
                ReceiptFixture fx = receiptFixture("CC2", "10.0000");
                PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
                PurchaseReceiptItemVO line = current.getItems().getFirst();

                expectCode(() -> purchaseReceiptService.confirm(
                        confirmForm(current.getId(), current.getVersion() + 5,
                                receiptLine(line.getId(), line.getVersion(), "5.0000")),
                        prefix + ":CC2:stale"), 40921);

                assertThat(reloadReceipt(fx.receipt().getId()).getStatus()).isEqualTo("PENDING");
                assertThat(reloadOrder(fx.order().getId()).getItems().getFirst().getReceivedQuantity())
                        .isEqualByComparingTo("0.0000");
            }

            // ------------------------------------------------------------------
            // 3. 收货行版本
            // ------------------------------------------------------------------

            @Test
            @DisplayName("收货行版本过期 → 40921（单据版本合法也拦得住）")
            void staleReceiptItemVersionRejected() {
                ReceiptFixture fx = receiptFixture("CC3", "10.0000");
                PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
                PurchaseReceiptItemVO line = current.getItems().getFirst();

                // 单据版本是当前值，只有行版本过期
                expectCode(() -> purchaseReceiptService.confirm(
                        confirmForm(current.getId(), current.getVersion(),
                                receiptLine(line.getId(), line.getVersion() + 5, "5.0000")),
                        prefix + ":CC3:stale"), 40921);

                assertThat(reloadReceipt(fx.receipt().getId()).getStatus()).isEqualTo("PENDING");
                assertThat(reloadOrder(fx.order().getId()).getStatus()).isEqualTo("SUBMITTED");

                // 正确版本仍可确认
                PurchaseReceiptVO confirmed = confirm(current, "5.0000", "ok");
                assertThat(confirmed.getStatus()).isEqualTo("CONFIRMED");
            }
        }

        @Nested
        @DisplayName("超收容差：Config 载体 / ceiling 语义 / 40989 整笔回滚（PG IT）")
        class PurchaseReceiptOverReceiptIT {
            @Autowired
            private ConfigService configService;

            /**
             * 通过 ConfigService 改容差（会同步刷新它自己的缓存）。
             */
            private void setTolerance(String value) {
                ConfigVO current = configService.getConfig(PurchaseConfigKey.OVER_RECEIPT_TOLERANCE_PERCENT);
                assertThat(current).as("V15 必须播种采购容差配置").isNotNull();
                ConfigUpdateForm form = new ConfigUpdateForm();
                form.setConfigId(current.getConfigId());
                form.setConfigKey(current.getConfigKey());
                form.setConfigName(current.getConfigName());
                form.setConfigValue(value);
                form.setRemark(current.getRemark());
                configService.updateConfig(form);
            }

            @AfterEach
            void restoreDefaultTolerance() {
                setTolerance(PurchaseConfigKey.OVER_RECEIPT_TOLERANCE_PERCENT_DEFAULT);
            }

            /**
             * 用指定数量确认收货（自动回读当前版本）。
             */
            private PurchaseReceiptVO confirm(ReceiptFixture fx, String quantity) {
                PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
                PurchaseReceiptItemVO line = current.getItems().getFirst();
                return purchaseReceiptService.confirm(
                        confirmForm(current.getId(), current.getVersion(),
                                receiptLine(line.getId(), line.getVersion(), quantity)),
                        prefix + ":over:" + current.getId() + ":" + quantity);
            }

            // ------------------------------------------------------------------
            // 1. 默认容差 = 10%
            // ------------------------------------------------------------------

            @Test
            @DisplayName("默认容差 10%：planned 10 → ceiling 11.0000，恰好收 11.0000 通过")
            void defaultToleranceAllowsExactlyTenPercentOver() {
                ReceiptFixture fx = receiptFixture("OR1", "10.0000");
                PurchaseReceiptVO confirmed = confirm(fx, "11.0000");

                PurchaseReceiptItemVO line = confirmed.getItems().getFirst();
                assertThat(line.getCumulativeReceivedQuantity()).isEqualByComparingTo("11.0000");
                assertThat(line.getOverReceiptQuantity()).isEqualByComparingTo("1.0000");
                assertThat(reloadOrder(fx.order().getId()).getStatus()).isEqualTo("RECEIVED");
            }

            // ------------------------------------------------------------------
            // 2. 超上限 → 整笔回滚
            // ------------------------------------------------------------------

            @Test
            @DisplayName("超出容差：11.0001 → 40989，且收货单 / 采购行 / 过秤记录全部不动")
            void beyondToleranceRejectedAndRolledBack() {
                ReceiptFixture fx = receiptFixture("OR2", "10.0000");
                PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
                PurchaseReceiptItemVO line = current.getItems().getFirst();

                expectCode(() -> purchaseReceiptService.confirm(
                        confirmForm(current.getId(), current.getVersion(),
                                receiptLine(line.getId(), line.getVersion(), "11.0001")),
                        prefix + ":OR2:over"), 40989);

                // 整笔回滚：不是「收 11 然后报错」
                assertThat(reloadReceipt(fx.receipt().getId()).getStatus()).isEqualTo("PENDING");
                assertThat(reloadOrder(fx.order().getId()).getItems().getFirst().getReceivedQuantity())
                        .isEqualByComparingTo("0.0000");
                assertThat(reloadOrder(fx.order().getId()).getStatus()).isEqualTo("SUBMITTED");
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_receipt_weighing_record WHERE purchase_receipt_item_id = ?",
                        Integer.class, line.getId())).isZero();

                // 上限内仍然可以收 —— 证明拒绝的原因是数量而不是别的
                assertThat(confirm(fx, "11.0000").getItems().getFirst().getCumulativeReceivedQuantity())
                        .isEqualByComparingTo("11.0000");
            }

            // ------------------------------------------------------------------
            // 3. 分次到货时 available 逐次递减
            // ------------------------------------------------------------------

            @Test
            @DisplayName("分次到货：先收 6.0000，第二张收货单只剩 5.0000 可收（5.0001 → 40989）")
            void secondReceiptUsesRemainingAvailable() {
                ReceiptFixture fx = receiptFixture("OR3", "10.0000");
                confirm(fx, "6.0000");
                assertThat(reloadOrder(fx.order().getId()).getStatus()).isEqualTo("PARTIALLY_RECEIVED");

                PurchaseReceiptVO second = createAnotherReceipt(fx.order().getId(), "b");
                PurchaseReceiptItemVO line = second.getItems().getFirst();
                // 第二张单的初始累计量是 0，但 available 是 11 − 6 = 5
                assertThat(line.getCumulativeReceivedQuantity()).isEqualByComparingTo("0.0000");

                expectCode(() -> purchaseReceiptService.confirm(
                        confirmForm(second.getId(), second.getVersion(),
                                receiptLine(line.getId(), line.getVersion(), "5.0001")),
                        prefix + ":OR3:b1"), 40989);

                PurchaseReceiptVO ok = purchaseReceiptService.confirm(
                        confirmForm(second.getId(), second.getVersion(),
                                receiptLine(line.getId(), line.getVersion(), "5.0000")),
                        prefix + ":OR3:b2");
                assertThat(ok.getItems().getFirst().getCumulativeReceivedQuantity()).isEqualByComparingTo("11.0000");
                assertThat(reloadOrder(fx.order().getId()).getStatus()).isEqualTo("RECEIVED");
            }

            // ------------------------------------------------------------------
            // 4. 容差 0
            // ------------------------------------------------------------------

            @Test
            @DisplayName("容差 0：多一个最小单位都拒（10.0001 → 40989），恰好 10.0000 通过")
            void zeroToleranceRejectsAnyOverReceipt() {
                setTolerance("0");
                ReceiptFixture fx = receiptFixture("OR4", "10.0000");
                PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
                PurchaseReceiptItemVO line = current.getItems().getFirst();

                expectCode(() -> purchaseReceiptService.confirm(
                        confirmForm(current.getId(), current.getVersion(),
                                receiptLine(line.getId(), line.getVersion(), "10.0001")),
                        prefix + ":OR4:over"), 40989);

                assertThat(confirm(fx, "10.0000").getItems().getFirst().getCumulativeReceivedQuantity())
                        .isEqualByComparingTo("10.0000");
            }

            // ------------------------------------------------------------------
            // 5. 容差真的来自配置
            // ------------------------------------------------------------------

            @Test
            @DisplayName("容差 50%：planned 10 → ceiling 15.0000，收 15.0000 通过、15.0001 拒绝")
            void toleranceComesFromXSYConfig() {
                setTolerance("50");
                ReceiptFixture fx = receiptFixture("OR5", "10.0000");
                PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
                PurchaseReceiptItemVO line = current.getItems().getFirst();

                expectCode(() -> purchaseReceiptService.confirm(
                        confirmForm(current.getId(), current.getVersion(),
                                receiptLine(line.getId(), line.getVersion(), "15.0001")),
                        prefix + ":OR5:over"), 40989);

                PurchaseReceiptVO ok = purchaseReceiptService.confirm(
                        confirmForm(current.getId(), current.getVersion(),
                                receiptLine(line.getId(), line.getVersion(), "15.0000")),
                        prefix + ":OR5:ok");
                assertThat(ok.getItems().getFirst().getOverReceiptQuantity()).isEqualByComparingTo("5.0000");
            }

            // ------------------------------------------------------------------
            // 6. 非法配置
            // ------------------------------------------------------------------

            @Test
            @DisplayName("容差配置非法（abc / 101 / -1）→ 40999，且收货单保持待确认")
            void invalidToleranceConfigRejected() {
                ReceiptFixture fx = receiptFixture("OR6", "10.0000");

                for (String bad : new String[]{"abc", "101", "-1", "10.5", "10%"}) {
                    setTolerance(bad);
                    PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
                    PurchaseReceiptItemVO line = current.getItems().getFirst();
                    expectCode(() -> purchaseReceiptService.confirm(
                            confirmForm(current.getId(), current.getVersion(),
                                    receiptLine(line.getId(), line.getVersion(), "5.0000")),
                            prefix + ":OR6:" + bad), 40999);
                }

                assertThat(reloadReceipt(fx.receipt().getId()).getStatus()).isEqualTo("PENDING");
            }

            // ------------------------------------------------------------------
            // 7. 空值回退
            // ------------------------------------------------------------------

            @Test
            @DisplayName("容差为空 → 回退默认 10（不是「容差 0」，也不是报错）")
            void blankToleranceFallsBackToDefault() {
                setTolerance("");
                ReceiptFixture fx = receiptFixture("OR7", "10.0000");
                PurchaseReceiptVO confirmed = confirm(fx, "11.0000");
                assertThat(confirmed.getItems().getFirst().getCumulativeReceivedQuantity())
                        .isEqualByComparingTo("11.0000");
            }
        }

        @Nested
        @DisplayName("收货对账恒等式：remaining / over / difference（PG IT）")
        class PurchaseReceiptReconciliationIT {
            private PurchaseReceiptVO confirm(ReceiptFixture fx, String quantity) {
                PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
                PurchaseReceiptItemVO line = current.getItems().getFirst();
                return purchaseReceiptService.confirm(
                        confirmForm(current.getId(), current.getVersion(),
                                receiptLine(line.getId(), line.getVersion(), quantity)),
                        prefix + ":rec:" + current.getId() + ":" + quantity);
            }

            /**
             * 库层的三个对账量（与 Java 侧逐字段比对，防止两套规则）。
             */
            private void assertDatabaseMatchesJava(Long receiptItemId, PurchaseReceiptItemVO line) {
                var row = jdbc.queryForMap(
                        "SELECT received_quantity, cumulative_received_quantity, remaining_quantity, "
                                + "over_receipt_quantity, receipt_difference "
                                + "FROM xsy_purchase_receipt_item WHERE id = ?", receiptItemId);
                assertThat((BigDecimal) row.get("received_quantity")).isEqualByComparingTo(line.getReceivedQuantity());
                assertThat((BigDecimal) row.get("cumulative_received_quantity"))
                        .isEqualByComparingTo(line.getCumulativeReceivedQuantity());
                assertThat((BigDecimal) row.get("remaining_quantity")).isEqualByComparingTo(line.getRemainingQuantity());
                assertThat((BigDecimal) row.get("over_receipt_quantity"))
                        .isEqualByComparingTo(line.getOverReceiptQuantity());
                assertThat((BigDecimal) row.get("receipt_difference")).isEqualByComparingTo(line.getReceiptDifference());

                // 恒等式本身（不依赖具体数值）
                BigDecimal planned = line.getPlannedQuantity();
                BigDecimal cumulative = line.getCumulativeReceivedQuantity();
                assertThat(line.getRemainingQuantity()).isEqualByComparingTo(
                        planned.subtract(cumulative).max(BigDecimal.ZERO));
                assertThat(line.getOverReceiptQuantity()).isEqualByComparingTo(
                        cumulative.subtract(planned).max(BigDecimal.ZERO));
                assertThat(line.getReceiptDifference()).isEqualByComparingTo(cumulative.subtract(planned));
                // 两个非负量 + 差异的符号一致性
                assertThat(line.getRemainingQuantity()).isNotNegative();
                assertThat(line.getOverReceiptQuantity()).isNotNegative();
            }

            // ------------------------------------------------------------------
            // 1. 欠收
            // ------------------------------------------------------------------

            @Test
            @DisplayName("欠收：remaining > 0、over = 0、difference < 0（planned 10 收 4）")
            void underReceiptKeepsRemainingAndNegativeDifference() {
                ReceiptFixture fx = receiptFixture("P24A", "10.0000");
                PurchaseReceiptVO confirmed = confirm(fx, "4.0000");
                PurchaseReceiptItemVO line = confirmed.getItems().getFirst();

                assertThat(line.getCumulativeReceivedQuantity()).isEqualByComparingTo("4.0000");
                assertThat(line.getRemainingQuantity()).isEqualByComparingTo("6.0000");
                assertThat(line.getOverReceiptQuantity()).isEqualByComparingTo("0.0000");
                assertThat(line.getReceiptDifference()).isEqualByComparingTo("-6.0000");
                assertDatabaseMatchesJava(line.getId(), line);

                // 采购单进入 PARTIALLY_RECEIVED，且采购行 remaining 同步
                assertThat(reloadOrder(fx.order().getId()).getStatus()).isEqualTo("PARTIALLY_RECEIVED");
                assertThat(reloadOrder(fx.order().getId()).getItems().getFirst().getRemainingQuantity())
                        .isEqualByComparingTo("6.0000");
            }

            // ------------------------------------------------------------------
            // 2. 收齐
            // ------------------------------------------------------------------

            @Test
            @DisplayName("收齐：三个量全部为 0，采购单 → RECEIVED")
            void exactReceiptZeroesEveryReconciliationField() {
                ReceiptFixture fx = receiptFixture("P24B", "10.0000");
                PurchaseReceiptVO confirmed = confirm(fx, "10.0000");
                PurchaseReceiptItemVO line = confirmed.getItems().getFirst();

                assertThat(line.getCumulativeReceivedQuantity()).isEqualByComparingTo("10.0000");
                assertThat(line.getRemainingQuantity()).isEqualByComparingTo("0.0000");
                assertThat(line.getOverReceiptQuantity()).isEqualByComparingTo("0.0000");
                assertThat(line.getReceiptDifference()).isEqualByComparingTo("0.0000");
                assertDatabaseMatchesJava(line.getId(), line);

                assertThat(reloadOrder(fx.order().getId()).getStatus()).isEqualTo("RECEIVED");
            }

            // ------------------------------------------------------------------
            // 3. 超收（容差内）
            // ------------------------------------------------------------------

            @Test
            @DisplayName("超收：remaining = 0、over > 0、difference > 0（planned 10 收 11，容差 10%）")
            void overReceiptWithinToleranceRecordsPositiveOverAndDifference() {
                ReceiptFixture fx = receiptFixture("P24C", "10.0000");
                // ceiling = 10 × 1.1 = 11 → 11.0000 恰好在上限内
                PurchaseReceiptVO confirmed = confirm(fx, "11.0000");
                PurchaseReceiptItemVO line = confirmed.getItems().getFirst();

                assertThat(line.getCumulativeReceivedQuantity()).isEqualByComparingTo("11.0000");
                assertThat(line.getRemainingQuantity()).isEqualByComparingTo("0.0000");
                assertThat(line.getOverReceiptQuantity()).isEqualByComparingTo("1.0000");
                assertThat(line.getReceiptDifference()).isEqualByComparingTo("1.0000");
                assertDatabaseMatchesJava(line.getId(), line);

                // 全部行都 >= planned → RECEIVED（超收也是收齐）
                assertThat(reloadOrder(fx.order().getId()).getStatus()).isEqualTo("RECEIVED");
                assertThat(reloadOrder(fx.order().getId()).getItems().getFirst().getOverReceiptQuantity())
                        .isEqualByComparingTo("1.0000");
            }
        }

        @Nested
        @DisplayName("收货单生命周期：create / update / delete / 防重复处理（PG IT）")
        class PurchaseReceiptServiceIT {
            /**
             * 用指定数量确认收货（自动回读当前版本）。
             */
            private PurchaseReceiptVO confirm(ReceiptFixture fx, String quantity) {
                PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
                PurchaseReceiptItemVO line = current.getItems().getFirst();
                return purchaseReceiptService.confirm(
                        confirmForm(current.getId(), current.getVersion(),
                                receiptLine(line.getId(), line.getVersion(), quantity)),
                        prefix + ":confirm:" + current.getId() + ":" + quantity);
            }

            // ------------------------------------------------------------------
            // 1. create
            // ------------------------------------------------------------------

            @Test
            @DisplayName("create：按采购单活动行生成收货行，单号为 XSY + 雪花 ID，对账量取初始态")
            void createGeneratesReceiptForEveryActiveOrderItem() {
                ReceiptFixture fx = receiptFixture("RC1", "10.0000");
                PurchaseReceiptVO receipt = fx.receipt();

                assertThat(receipt.getReceiptNo()).matches("^XSY\\d{18,19}$");
                assertThat(receipt.getStatus()).isEqualTo("PENDING");
                assertThat(receipt.getPurchaseOrderId()).isEqualTo(fx.order().getId());
                assertThat(receipt.getPurchaseOrderNo()).isEqualTo(fx.order().getOrderNo());
                assertThat(receipt.getSupplierId()).isEqualTo(fx.supplierId());
                assertThat(receipt.getWarehouseId()).isEqualTo(seedWarehouseId());
                // 待确认时没有收货时间与操作者
                assertThat(receipt.getReceivedAt()).isNull();
                assertThat(receipt.getConfirmedAt()).isNull();
                assertThat(receipt.getOperator()).isNull();

                assertThat(receipt.getItems()).hasSize(1);
                PurchaseReceiptItemVO line = receipt.getItems().getFirst();
                assertThat(line.getPurchaseOrderItemId()).isEqualTo(fx.orderItemId());
                assertThat(line.getSkuId()).isEqualTo(fx.skuId());
                assertThat(line.getProductType()).isEqualTo("NON_STANDARD");
                assertThat(line.getPurchaseUnit()).isEqualTo(DEFAULT_PURCHASE_UNIT);
                assertThat(line.getPlannedQuantity()).isEqualByComparingTo("10.0000");
                // 对账恒等式在 cumulative = 0 时的取值
                assertThat(line.getReceivedQuantity()).isEqualByComparingTo("0.0000");
                assertThat(line.getCumulativeReceivedQuantity()).isEqualByComparingTo("0.0000");
                assertThat(line.getRemainingQuantity()).isEqualByComparingTo("10.0000");
                assertThat(line.getOverReceiptQuantity()).isEqualByComparingTo("0.0000");
                assertThat(line.getReceiptDifference()).isEqualByComparingTo("-10.0000");
                assertThat(line.getActualWeight()).isNull();
                assertThat(line.getWeighingSource()).isNull();

                // RECEIPT_CREATE 日志：归属约束的第四分支，两个 id 都非空
                // 日志按 created_at DESC, id DESC 返回（与 order_operation_log 一致）→ 最新在前
                List<PurchaseOperationLogVO> logs = purchaseQueryService.orderLogs(fx.order().getId());
                assertThat(logs).extracting(PurchaseOperationLogVO::getOperationType)
                        .containsExactly("RECEIPT_CREATE", "CREATE");
                PurchaseOperationLogVO log = logs.getFirst();
                assertThat(log.getPurchaseOrderId()).isEqualTo(fx.order().getId());
                assertThat(log.getPurchaseReceiptId()).isEqualTo(receipt.getId());
                assertThat(log.getAfterData()).containsEntry("receiptNo", receipt.getReceiptNo());
            }

            // ------------------------------------------------------------------
            // 2 / 3. create 的前置校验
            // ------------------------------------------------------------------

            @Test
            @DisplayName("create：已取消采购单 → 40991；采购单不存在 → 40481")
            void createRejectsNonReceivableOrderAndUnknownOrder() {
                Long skuId = newOnShelfSku("RC2");
                Long supplierId = newPurchasableSupplier("RC2", skuId);
                Long customerId = newCustomer();
                Long salesOrder = confirmedSalesOrder(customerId, skuId, "5.0000", "3.0000");
                PurchaseDemandEntity demand = generateDemandFor(supplierId, salesOrder);
                PurchaseOrderVO order = createDraftOrder("RC2", supplierId, skuId, "3.0000", "6.2000",
                        allocation(demand, "3.0000"));

                // 新建采购单已是 SUBMITTED，可以立即收货；取消后才不可收货。
                PurchaseOrderCancelForm cancel = new PurchaseOrderCancelForm();
                cancel.setId(order.getId());
                cancel.setVersion(reloadOrder(order.getId()).getVersion());
                cancel.setCancelReason("RC2：取消后不可收货");
                purchaseOrderService.cancel(cancel, prefix + ":cancel:" + order.getId());

                expectCode(() -> createReceipt(order.getId()), 40991);

                expectCode(() -> createReceipt(999_999_999L), 40481);
            }

            // ------------------------------------------------------------------
            // 4. update 只改备注
            // ------------------------------------------------------------------

            @Test
            @DisplayName("update：只允许改备注；确认之后 → 40988")
            void updateOnlyAllowsRemarkOnDraft() {
                ReceiptFixture fx = receiptFixture("RC4", "10.0000");

                PurchaseReceiptVO updated = purchaseReceiptService.update(receiptRemarkForm(
                        fx.receipt().getId(), fx.receipt().getVersion(), "第一次到货"));
                assertThat(updated.getRemark()).isEqualTo("第一次到货");
                assertThat(updated.getVersion()).isGreaterThan(fx.receipt().getVersion());
                assertThat(purchaseQueryService.orderLogs(fx.order().getId()))
                        .extracting(PurchaseOperationLogVO::getOperationType)
                        .contains("RECEIPT_UPDATE");

                // 版本过期同样被拦
                expectCode(() -> purchaseReceiptService.update(receiptRemarkForm(
                        fx.receipt().getId(), fx.receipt().getVersion(), "过期版本")), 40921);

                // 确认之后不可再改
                PurchaseReceiptVO confirmed = confirm(fx, "10.0000");
                assertThat(confirmed.getStatus()).isEqualTo("CONFIRMED");
                expectCode(() -> purchaseReceiptService.update(receiptRemarkForm(
                        confirmed.getId(), confirmed.getVersion(), "改不了")), 40988);
            }

            // ------------------------------------------------------------------
            // 5. delete
            // ------------------------------------------------------------------

            @Test
            @DisplayName("delete：仅待确认可删（确认后 → 40994），删除本身防重复处理")
            void deleteOnlyAllowsDraftAndIsIdempotent() {
                ReceiptFixture draftFixture = receiptFixture("RC5", "10.0000");
                Long draftId = draftFixture.receipt().getId();

                purchaseReceiptService.delete(receiptDeleteForm(draftId));
                expectCode(() -> reloadReceipt(draftId), 40483);
                // 防重复处理：已删视为成功
                purchaseReceiptService.delete(receiptDeleteForm(draftId));

                // 确认后不可删
                ReceiptFixture confirmedFixture = receiptFixture("RC5b", "10.0000");
                PurchaseReceiptVO confirmed = confirm(confirmedFixture, "10.0000");
                expectCode(() -> purchaseReceiptService.delete(receiptDeleteForm(confirmed.getId())), 40994);
                assertThat(reloadReceipt(confirmed.getId()).getStatus()).isEqualTo("CONFIRMED");
            }

            // ------------------------------------------------------------------
            // 6. create 防重复处理
            // ------------------------------------------------------------------

            @Test
            @DisplayName("create 防重复处理：同键同内容重放，不产生第二张收货单")
            void createIsIdempotentWithinOrderScope() {
                ReceiptFixture fx = receiptFixture("RC6", "10.0000");

                // 与 createReceipt 完全相同的请求（同 key、同 remark、同 receiptMode）
                PurchaseReceiptCreateForm form = new PurchaseReceiptCreateForm();
                form.setPurchaseOrderId(fx.order().getId());
                form.setReceiptMode(ScmReceiptModeEnum.DIRECT.name());
                form.setRemark("W5 IT 收货单");
                PurchaseReceiptVO replayed = purchaseReceiptService.create(
                        form, prefix + ":receipt:" + fx.order().getId());

                assertThat(replayed.getId()).isEqualTo(fx.receipt().getId());
                assertThat(replayed.getReceiptNo()).isEqualTo(fx.receipt().getReceiptNo());
                assertThat(receiptOfOrder(fx.order().getId()).getId()).isEqualTo(fx.receipt().getId());
                // 重放不写第二条 RECEIPT_CREATE 日志（同样按 DESC 返回）
                assertThat(purchaseQueryService.orderLogs(fx.order().getId()))
                        .extracting(PurchaseOperationLogVO::getOperationType)
                        .containsExactly("RECEIPT_CREATE", "CREATE");
            }
        }

        @Nested
        @DisplayName("少收关单：混合收付状态 + 分配保留（PG IT）")
        class PurchaseReceiptShortCloseIT {
            /**
             * 两行采购单（两个 SKU）+ 一张草稿收货单。
             */
            private record MixedFixture(Long skuA, Long skuB, Long supplierId,
                                        PurchaseDemandEntity demandA, PurchaseDemandEntity demandB,
                                        PurchaseOrderVO order, PurchaseReceiptVO receipt) {
            }

            private MixedFixture mixed(String suffix) {
                Long skuA = newOnShelfSku(suffix);
                Long skuB = newOnShelfSku(suffix + "b");
                Long supplierId = newSupplier(suffix);
                // 两个 SKU 必须一次挂完：replace 是整体替换
                linkSupplierSkus(supplierId, skuA, skuB);

                Long customerId = newCustomer();
                Long salesOrderA = confirmedSalesOrder(customerId, skuA, "4.0000", "4.0000");
                Long salesOrderB = confirmedSalesOrder(customerId, skuB, "6.0000", "6.0000");
                PurchaseDemandEntity demandA = generateDemandFor(supplierId, salesOrderA);
                PurchaseDemandEntity demandB = generateDemandFor(supplierId, salesOrderB);

                PurchaseOrderAddForm form = new PurchaseOrderAddForm();
                form.setSupplierId(supplierId);
                form.setWarehouseId(seedWarehouseId());
                form.setPurchaserId(anyEmployeeId());
                form.setRemark("W5 少收关单用例");
                form.setItems(new ArrayList<>(List.of(
                        item(skuA, "4.0000", "6.2000", allocation(demandA, "4.0000")),
                        item(skuB, "6.0000", "6.2000", allocation(demandB, "6.0000")))));
                PurchaseOrderVO order = purchaseOrderService.create(form, prefix + ":" + suffix + ":po");

                PurchaseReceiptVO receipt = submittedOrderReceipt(order.getId());
                return new MixedFixture(skuA, skuB, supplierId, demandA, demandB, order, receipt);
            }

            /**
             * 按 SKU 分派两个数量地确认收货。
             */
            private PurchaseReceiptVO confirm(MixedFixture fx, String quantityA, String quantityB) {
                PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
                List<PurchaseReceiptConfirmForm.Item> items = new ArrayList<>(current.getItems().size());
                for (PurchaseReceiptItemVO line : current.getItems()) {
                    String quantity = line.getSkuId().equals(fx.skuA()) ? quantityA : quantityB;
                    items.add(receiptLine(line.getId(), line.getVersion(), quantity));
                }
                return purchaseReceiptService.confirm(
                        confirmForm(current.getId(), current.getVersion(),
                                items.toArray(new PurchaseReceiptConfirmForm.Item[0])),
                        prefix + ":sc:" + quantityA + ":" + quantityB);
            }

            private PurchaseOrderVO shortClose(MixedFixture fx, String reason) {
                PurchaseOrderShortCloseForm form = new PurchaseOrderShortCloseForm();
                form.setId(fx.order().getId());
                form.setVersion(reloadOrder(fx.order().getId()).getVersion());
                form.setShortCloseReason(reason);
                return purchaseOrderService.shortClose(form, prefix + ":sc:close:" + fx.order().getId());
            }

            // ------------------------------------------------------------------
            // 1. 混合状态 → 允许关单
            // ------------------------------------------------------------------

            @Test
            @DisplayName("一行收齐 + 一行欠收 → shortClose 成功，状态 SHORT_CLOSED 且原因写入数据库")
            void shortCloseAfterPartialReceiptOnMixedLines() {
                MixedFixture fx = mixed("SC1");
                assertThat(fx.order().getItems()).hasSize(2);

                // A 收齐 4.0000，B 只收 2.0000（还差 4.0000）
                confirm(fx, "4.0000", "2.0000");
                PurchaseOrderVO partially = reloadOrder(fx.order().getId());
                assertThat(partially.getStatus()).isEqualTo("PARTIALLY_RECEIVED");
                assertThat(partially.getItems()).allSatisfy(item -> assertThat(item.getReceivedQuantity()).isPositive());

                PurchaseOrderVO closed = shortClose(fx, "供应商只发部分，剩余不再补");
                assertThat(closed.getStatus()).isEqualTo("SHORT_CLOSED");
                assertThat(closed.getShortClosedAt()).isNotNull();
                assertThat(closed.getShortCloseReason()).isEqualTo("供应商只发部分，剩余不再补");

                // 关单不抹掉已收数量与欠收差异
                assertThat(closed.getItems()).extracting(item -> item.getRemainingQuantity())
                        .containsExactlyInAnyOrder(
                                new java.math.BigDecimal("0.0000"),
                                new java.math.BigDecimal("4.0000"));

                // SHORT_CLOSE 日志带原因
                assertThat(purchaseQueryService.orderLogs(fx.order().getId()))
                        .filteredOn(log -> "SHORT_CLOSE".equals(log.getOperationType()))
                        .singleElement()
                        .satisfies(log -> assertThat(log.getReason()).isEqualTo("供应商只发部分，剩余不再补"));
            }

            // ------------------------------------------------------------------
            // 2. 全部收齐 → 拒绝
            // ------------------------------------------------------------------

            @Test
            @DisplayName("全部行收齐 → 采购单已是 RECEIVED，shortClose → 40982")
            void shortCloseRejectedWhenAllLinesReceived() {
                MixedFixture fx = mixed("SC2");
                confirm(fx, "4.0000", "6.0000");
                assertThat(reloadOrder(fx.order().getId()).getStatus()).isEqualTo("RECEIVED");

                PurchaseOrderShortCloseForm form = new PurchaseOrderShortCloseForm();
                form.setId(fx.order().getId());
                form.setVersion(reloadOrder(fx.order().getId()).getVersion());
                form.setShortCloseReason("已经全收，没有少收");
                expectCode(() -> purchaseOrderService.shortClose(form, prefix + ":SC2:close"), 40982);

                assertThat(reloadOrder(fx.order().getId()).getStatus()).isEqualTo("RECEIVED");
            }

            // ------------------------------------------------------------------
            // 3. R12 / G1：关单不释放分配
            // ------------------------------------------------------------------

            @Test
            @DisplayName("R12 / G1：shortClose **不**释放需求分配（与 cancel / delete 相反）")
            void shortCloseKeepsAllocations() {
                MixedFixture fx = mixed("SC3");
                confirm(fx, "4.0000", "2.0000");
                assertThat(allocationsOf(fx.order().getId())).hasSize(2);

                PurchaseOrderVO closed = shortClose(fx, "剩余不再到货");
                assertThat(closed.getStatus()).isEqualTo("SHORT_CLOSED");

                // 分配仍在：已收的货仍能追溯到客户需求
                assertThat(allocationsOf(fx.order().getId())).hasSize(2);
                assertThat(reloadDemand(fx.demandA().getId()).getStatus()).isEqualTo("ALLOCATED");
                assertThat(reloadDemand(fx.demandB().getId()).getStatus()).isEqualTo("ALLOCATED");
                assertThat(reloadDemand(fx.demandA().getId()).getAllocatedQuantity()).isEqualByComparingTo("4.0000");
                assertThat(reloadDemand(fx.demandB().getId()).getAllocatedQuantity()).isEqualByComparingTo("6.0000");
            }
        }

        @Nested
        @DisplayName("收货重量：非标品实重规则与过秤留痕（PG IT）")
        class PurchaseReceiptWeightIT {
            /**
             * 用「声明量 + 实重」分开给的方式确认收货。
             */
            private PurchaseReceiptVO confirm(ReceiptFixture fx, String declared, String actualWeight) {
                PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
                PurchaseReceiptItemVO line = current.getItems().getFirst();
                return purchaseReceiptService.confirm(
                        confirmForm(current.getId(), current.getVersion(),
                                receiptLine(line.getId(), line.getVersion(), declared, actualWeight)),
                        prefix + ":weight:" + current.getId() + ":" + declared + ":" + actualWeight);
            }

            private Map<String, Object> latestWeighingRecord(Long receiptItemId) {
                return jdbc.queryForMap(
                        "SELECT purchase_receipt_item_id, raw_reading, confirmed_reading, unit, source, "
                                + "modification_reason, operator FROM xsy_receipt_weighing_record "
                                + "WHERE purchase_receipt_item_id = ? ORDER BY id DESC LIMIT 1", receiptItemId);
            }

            private int weighingRecordCount(Long receiptItemId) {
                return jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_receipt_weighing_record WHERE purchase_receipt_item_id = ?",
                        Integer.class, receiptItemId);
            }

            // ------------------------------------------------------------------
            // 1. 实重优先
            // ------------------------------------------------------------------

            @Test
            @DisplayName("非标品：有效数量取实重（声明 5.0000 / 实重 3.0000 → 累计 3.0000）")
            void nonStandardEffectiveQuantityComesFromActualWeight() {
                ReceiptFixture fx = receiptFixture("WG1", "10.0000");
                PurchaseReceiptVO confirmed = confirm(fx, "5.0000", "3.0000");

                PurchaseReceiptItemVO line = confirmed.getItems().getFirst();
                assertThat(line.getActualWeight()).isEqualByComparingTo("3.0000");
                assertThat(line.getWeighingSource()).isEqualTo("MANUAL");
                assertThat(line.getWeightUnit()).isEqualTo(DEFAULT_PURCHASE_UNIT);
                // 本次有效数量 = 实重，不是声明的 5.0000
                assertThat(line.getReceivedQuantity()).isEqualByComparingTo("3.0000");
                assertThat(line.getCumulativeReceivedQuantity()).isEqualByComparingTo("3.0000");

                // 采购行的已收量同样只累计实重
                assertThat(reloadOrder(fx.order().getId()).getItems().getFirst().getReceivedQuantity())
                        .isEqualByComparingTo("3.0000");
                // 采购单未收齐 → PARTIALLY_RECEIVED
                assertThat(confirmed.getStatus()).isEqualTo("CONFIRMED");
                assertThat(reloadOrder(fx.order().getId()).getStatus()).isEqualTo("PARTIALLY_RECEIVED");
            }

            // ------------------------------------------------------------------
            // 2. 非标品缺实重
            // ------------------------------------------------------------------

            @Test
            @DisplayName("非标品：缺实重 → 40083，且不写任何过秤记录、采购行不动")
            void nonStandardRequiresActualWeight() {
                ReceiptFixture fx = receiptFixture("WG2", "10.0000");

                PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
                PurchaseReceiptItemVO line = current.getItems().getFirst();
                expectCode(() -> purchaseReceiptService.confirm(
                        confirmForm(current.getId(), current.getVersion(),
                                receiptLine(line.getId(), line.getVersion(), "3.0000", null)),
                        prefix + ":WG2:noWeight"), 40083);

                assertThat(weighingRecordCount(line.getId())).isZero();
                assertThat(reloadOrder(fx.order().getId()).getItems().getFirst().getReceivedQuantity())
                        .isEqualByComparingTo("0.0000");
                assertThat(reloadReceipt(fx.receipt().getId()).getStatus()).isEqualTo("PENDING");
            }

            // ------------------------------------------------------------------
            // 3. 过秤留痕
            // ------------------------------------------------------------------

            @Test
            @DisplayName("带实重的确认追加一行过秤记录：原始读数 = 确认读数、来源 MANUAL、单位随采购单位")
            void weighingRecordIsAppendedForWeightedReceipt() {
                ReceiptFixture fx = receiptFixture("WG3", "10.0000");
                PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
                Long receiptItemId = current.getItems().getFirst().getId();
                assertThat(weighingRecordCount(receiptItemId)).isZero();

                confirm(fx, "4.0000", "4.0000");

                assertThat(weighingRecordCount(receiptItemId)).isEqualTo(1);
                Map<String, Object> record = latestWeighingRecord(receiptItemId);
                assertThat(record.get("purchase_receipt_item_id")).isEqualTo(receiptItemId);
                assertThat(record.get("raw_reading")).isEqualTo(new java.math.BigDecimal("4.0000"));
                assertThat(record.get("confirmed_reading")).isEqualTo(new java.math.BigDecimal("4.0000"));
                assertThat(record.get("unit")).isEqualTo(DEFAULT_PURCHASE_UNIT);
                assertThat(record.get("source")).isEqualTo("MANUAL");
                assertThat(record.get("modification_reason")).isNull();
                // 操作者落的是 userType:employeeId（ScmOperator 的规则），不是员工姓名
                assertThat(record.get("operator")).isEqualTo(ScmOperator.current());
            }

            // ------------------------------------------------------------------
            // 4. 声明量形态
            // ------------------------------------------------------------------

            @Test
            @DisplayName("声明数量 / 实重必须是 4 位定点正数：形态不符 → 40083（不是采购侧的 40080）")
            void declaredShapeIsValidatedWithReceiptCode() {
                ReceiptFixture fx = receiptFixture("WG4", "10.0000");
                PurchaseReceiptVO current = reloadReceipt(fx.receipt().getId());
                PurchaseReceiptItemVO line = current.getItems().getFirst();

                // 两位小数、缺小数位、负数、0 一律拒绝
                for (String bad : new String[]{"3.00", "3", "0.0000", "-3.0000"}) {
                    expectCode(() -> purchaseReceiptService.confirm(
                            confirmForm(current.getId(), current.getVersion(),
                                    receiptLine(line.getId(), line.getVersion(), bad, "3.0000")),
                            prefix + ":WG4:" + bad), 40083);
                }

                // 实重形态同样校验
                expectCode(() -> purchaseReceiptService.confirm(
                        confirmForm(current.getId(), current.getVersion(),
                                receiptLine(line.getId(), line.getVersion(), "3.0000", "3.0")),
                        prefix + ":WG4:weight"), 40083);

                assertThat(reloadReceipt(fx.receipt().getId()).getStatus()).isEqualTo("PENDING");
                assertThat(weighingRecordCount(line.getId())).isZero();
            }
        }

        @Nested

        class PurchaseSharePgIT {
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



        @Nested
        @DisplayName("W5 权限种子：V16 菜单 / 授权 / 代码一致性（PG IT）")
        class ScmPurchasePermissionMigrationIT {
            /**
             * V16 里的 25 个菜单 id。
             */
            private static final List<Long> W5_MENU_IDS = List.of(
                    701L, 702L, 703L, 704L, 705L, 706L,
                    711L, 712L, 713L,
                    721L, 722L, 723L, 724L, 725L, 726L, 727L,
                    731L, 732L, 733L, 734L, 735L,
                    741L,
                    751L, 752L, 753L);

            private static String menuIdList() {
                return W5_MENU_IDS.stream().map(String::valueOf).collect(Collectors.joining(","));
            }

            // ------------------------------------------------------------------
            // 1. 菜单与授权
            // ------------------------------------------------------------------

            @Test
            @DisplayName("V16：25 条菜单全部就位、层级正确、全部授权给 role_id=1、序列已推进")
            void menuSeedIsCompleteAndGrantedToRoleOne() {
                // 25 条都在，且都可见
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM t_menu WHERE menu_id = ANY (string_to_array(?, ',')::bigint[])",
                        Integer.class, menuIdList())).isEqualTo(25);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM t_menu WHERE menu_id = ANY (string_to_array(?, ',')::bigint[]) "
                                + "AND visible_flag = TRUE", Integer.class, menuIdList()))
                        .as("724「提交」随采购草稿态下线隐藏（V221），其余 24 条仍可见")
                        .isEqualTo(24);

                // 层级：701 是根目录，702–706 挂在 701 下，权限点挂在各自页面下
                assertThat(jdbc.queryForObject(
                        "SELECT parent_id FROM t_menu WHERE menu_id = 701", Long.class)).isZero();
                assertThat(jdbc.queryForList(
                        "SELECT menu_id FROM t_menu WHERE parent_id = 701 AND menu_id BETWEEN 702 AND 706 "
                                + "ORDER BY menu_id", Long.class)).containsExactly(702L, 703L, 704L, 705L, 706L);
                assertThat(jdbc.queryForObject(
                        "SELECT parent_id FROM t_menu WHERE menu_id = 712", Long.class)).isEqualTo(702L);
                assertThat(jdbc.queryForObject(
                        "SELECT parent_id FROM t_menu WHERE menu_id = 726", Long.class)).isEqualTo(703L);
                assertThat(jdbc.queryForObject(
                        "SELECT parent_id FROM t_menu WHERE menu_id = 735", Long.class)).isEqualTo(704L);
                assertThat(jdbc.queryForObject(
                        "SELECT parent_id FROM t_menu WHERE menu_id = 741", Long.class)).isEqualTo(705L);
                assertThat(jdbc.queryForObject(
                        "SELECT parent_id FROM t_menu WHERE menu_id = 753", Long.class)).isEqualTo(706L);

                // 目录与页面菜单没有权限码；权限点菜单有（perms_type = 1）
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM t_menu WHERE menu_id BETWEEN 701 AND 706 AND api_perms IS NOT NULL",
                        Integer.class)).isZero();
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM t_menu WHERE menu_id = ANY (string_to_array(?, ',')::bigint[]) "
                                + "AND menu_id > 706 AND perms_type = 1 AND api_perms IS NOT NULL",
                        Integer.class, menuIdList())).isEqualTo(19);

                // 724「提交」随采购草稿态下线保留菜单行，但已从超管授权中收回（V221）。
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM t_role_menu WHERE role_id = 1 "
                                + "AND menu_id = ANY (string_to_array(?, ',')::bigint[])",
                        Integer.class, menuIdList())).isEqualTo(24);

                // 序列已推进到 max + 1：下一个 menu_id 不会撞已有行
                assertThat(jdbc.queryForObject(
                        "SELECT nextval(pg_get_serial_sequence('t_menu','menu_id')) > 753", Boolean.class)).isTrue();
            }

            // ------------------------------------------------------------------
            // 2. 代码与菜单一致性
            // ------------------------------------------------------------------

            @Test
            @DisplayName("每个 @SaCheckPermission 权限码都必须在 t_menu.api_perms 中（代码与菜单不脱节）")
            void everyControllerPermissionExistsInMenuSeed() {
                Set<String> declared = new LinkedHashSet<>();
                int endpointCount = 0;
                for (Class<?> controller : List.of(PurchaseDemandController.class, PurchaseOrderController.class,
                        PurchaseReceiptController.class, WarehouseController.class)) {
                    for (Method method : controller.getDeclaredMethods()) {
                        if (!Modifier.isPublic(method.getModifiers())) {
                            continue;
                        }
                        SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
                        assertThat(permission)
                                .as("%s#%s 必须声明 @SaCheckPermission", controller.getSimpleName(), method.getName())
                                .isNotNull();
                        declared.addAll(List.of(permission.value()));
                        endpointCount++;
                    }
                }

                // 7（需求：原 4 条含缺口预览 + ADM-05 计算结果批次的 create / generate / detail）
                // + 18（采购单：14 条含单笔/批量少收关单与 reassign 改派归属，再加补价/改价/锁单/解锁 4 条）
                // + 10（收货，含 putaway）+ 7（仓库，含 enable/disable）= 42 个端点；
                // 采购草稿态下线（V221）移除 submit 端点后为 41。
                assertThat(endpointCount).isEqualTo(41);
                // 权限码去重后 24 个：3 + 9 + 6 + 5 + 1（缺口预览叠加 scm:inventory:balance:query，AND 模式）；
                // 改派只多出一个 scm:purchase:assign —— 它是「能把单据换成谁」的分配权，
                // 与 putaway / enable 一样是新增动作而非新维度，所以端点与权限码同步 +1
                // 端点 35 → 38 带来 3 个新权限码（ADM-05 计算结果批次 batch:create / batch:generate / batch:query），
                // 去重后 24 → 27。下面紧身的 containsAll(declared) 才是关键：
                // 只要有一个码没进 V16 种子，接口对所有人不可用，这条会直接点名。
                // 采购价后置与锁单再补 4 个：price:fill / price:adjust / lock / unlock，去重后 27 → 31；
                // 采购草稿态下线（V221）移除 submit 权限后为 30。
                assertThat(declared).hasSize(30);
                assertThat(declared).contains("scm:inventory:balance:query");

                List<String> seeded = jdbc.queryForList(
                        "SELECT DISTINCT api_perms FROM t_menu WHERE api_perms IS NOT NULL", String.class);
                assertThat(seeded)
                        .as("代码里声明的权限码必须都在 V16 种子里，否则接口对所有人不可用")
                        .containsAll(declared);

                // 反向：采购域的权限码都带 scm: 前缀，不会误用系统菜单的权限
                assertThat(declared).allSatisfy(code -> assertThat(code).startsWith("scm:"));
            }
        }
    }

    @Nested
    class PurchaseW6PgIT extends ScmW6PgITBase {
        @Nested

        class PurchasePriceWindowIT {
            @Autowired
            private PurchasePriceService purchasePriceService;

            @Autowired
            private PurchaseOrderLockService purchaseOrderLockService;

            @Autowired
            private PurchasePriceAutoFillJob purchasePriceAutoFillJob;

            @Autowired
            private PurchaseOrderAutoLockJob purchaseOrderAutoLockJob;

            @Test
            @DisplayName("#1 待定价可补价，补价前收货被拒（41067），确认收货后不能再改价（41075）")
            void fillPendingPriceThenReceive() {
                Long skuId = newOnShelfSku("PW1");
                Long supplierId = newPurchasableSupplier("PW1", skuId);
                PurchaseOrderVO order = createPendingOrder("PW1", supplierId, skuId, "5.0000");
                order = submitOrder(order.getId());

                var item = order.getItems().getFirst();
                assertThat(item.getPricePending()).isTrue();
                assertThat(item.getPurchasePrice()).isNull();
                assertThat(item.getLineAmount()).isEqualByComparingTo("0.0000");

                var receipt = createReceipt(order.getId());
                var current = reloadReceipt(receipt.getId());
                var line = current.getItems().getFirst();
                expectCode(() -> purchaseReceiptService.confirm(
                    confirmForm(current.getId(), current.getVersion(),
                        receiptLine(line.getId(), line.getVersion(), "5.0000")),
                    prefix + ":receive-before-price"), 41067);

                PurchasePriceForm fill = new PurchasePriceForm();
                fill.setPrice("6.0000");
                fill.setReason("供应商当日未报价");
                purchasePriceService.fill(item.getId(), fill, prefix + ":fill:PW1");

                PurchaseOrderVO filled = reloadOrder(order.getId());
                assertThat(filled.getItems().getFirst().getPricePending()).isFalse();
                assertThat(filled.getItems().getFirst().getPurchasePrice()).isEqualByComparingTo("6.0000");
                assertThat(filled.getItems().getFirst().getLineAmount()).isEqualByComparingTo("30.0000");
                assertThat(filled.getTotalAmount()).isEqualByComparingTo("30.0000");
                assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_purchase_price_adjust WHERE purchase_order_item_id = ? "
                        + "AND source = 'MANUAL' AND new_price = 6.0000",
                    Integer.class, item.getId())).isEqualTo(1);
                assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_purchase_operation_log WHERE purchase_order_id = ? "
                        + "AND operation_type = 'PRICE_FILL'",
                    Integer.class, order.getId())).isEqualTo(1);

                confirmReceipt(receipt.getId(), "5.0000");
                assertThat(balanceRow(fixtureWarehouseId(), skuId).getQuantity()).isEqualByComparingTo("5.0000");

                PurchasePriceForm adjust = new PurchasePriceForm();
                adjust.setPrice("6.5000");
                adjust.setReason("发票差异");
                expectCode(() -> purchasePriceService.adjust(item.getId(), adjust, prefix + ":adjust:PW1"), 41075);
            }

            @Test
            @DisplayName("#2 窗口过期后补价被拒（41068），自动回填按最近已提交价并留审计")
            void expiredWindowFallsBackToLastSubmittedPrice() {
                Long skuId = newOnShelfSku("PW2");
                Long supplierId = newPurchasableSupplier("PW2", skuId);

                PurchaseOrderVO history = createDraftOrder("PW2-H", supplierId, skuId, "2.0000", "7.0000");
                submitOrder(history.getId());

                PurchaseOrderVO pending = createPendingOrder("PW2-P", supplierId, skuId, "3.0000");
                pending = submitOrder(pending.getId());
                Long pendingItemId = pending.getItems().getFirst().getId();

                jdbc.update("UPDATE xsy_purchase_order_item SET price_window_started_at = CURRENT_TIMESTAMP - INTERVAL '8 days' "
                    + "WHERE id = ?", pendingItemId);
                jdbc.update("UPDATE xsy_purchase_order SET auto_lock_at = CURRENT_TIMESTAMP - INTERVAL '1 hour' WHERE id = ?",
                    pending.getId());
                evictMybatisCache();

                PurchasePriceForm fill = new PurchasePriceForm();
                fill.setPrice("6.5000");
                expectCode(() -> purchasePriceService.fill(pendingItemId, fill, prefix + ":fill:PW2"), 41068);

                purchasePriceAutoFillJob.autoFillExpiredPrices();

                PurchaseOrderVO filled = reloadOrder(pending.getId());
                assertThat(filled.getItems().getFirst().getPricePending()).isFalse();
                assertThat(filled.getItems().getFirst().getPurchasePrice()).isEqualByComparingTo("7.0000");
                assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_purchase_price_adjust WHERE purchase_order_item_id = ? "
                        + "AND source = 'AUTO_LAST_PRICE'",
                    Integer.class, pendingItemId)).isEqualTo(1);
                assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_purchase_operation_log WHERE purchase_order_id = ? "
                        + "AND operation_type = 'PRICE_AUTO_FILL' AND operator = ?",
                    Integer.class, pending.getId(), PurchasePriceService.AUTO_FILL_OPERATOR)).isEqualTo(1);
            }

            @Test
            @DisplayName("#3 锁单后不能补价（41069），解锁必须写原因（41077）；到期自动锁单")
            void lockBlocksPriceFillAndAutoLockRuns() {
                Long skuId = newOnShelfSku("PW3");
                Long supplierId = newPurchasableSupplier("PW3", skuId);
                PurchaseOrderVO order = submitOrder(createPendingOrder("PW3", supplierId, skuId, "4.0000").getId());
                Long itemId = order.getItems().getFirst().getId();

                PurchaseOrderLockForm lock = new PurchaseOrderLockForm();
                lock.setId(order.getId());
                lock.setVersion(reloadOrder(order.getId()).getVersion());
                lock.setReason("与供应商对账");
                purchaseOrderLockService.lock(lock);

                PurchasePriceForm fill = new PurchasePriceForm();
                fill.setPrice("8.0000");
                expectCode(() -> purchasePriceService.fill(itemId, fill, prefix + ":fill:PW3"), 41069);

                PurchaseOrderLockForm unlock = new PurchaseOrderLockForm();
                unlock.setId(order.getId());
                unlock.setVersion(reloadOrder(order.getId()).getVersion());
                expectCode(() -> purchaseOrderLockService.unlock(unlock), 41077);

                unlock.setReason("价格录错，重新补录");
                purchaseOrderLockService.unlock(unlock);
                purchasePriceService.fill(itemId, fill, prefix + ":fill:PW3-after-unlock");
                assertThat(reloadOrder(order.getId()).getItems().getFirst().getPurchasePrice()).isEqualByComparingTo("8.0000");

                // 另一张到期单由定时任务自动锁上
                PurchaseOrderVO expired = submitOrder(createPendingOrder("PW3B", supplierId, skuId, "1.0000").getId());
                jdbc.update("UPDATE xsy_purchase_order SET auto_lock_at = CURRENT_TIMESTAMP - INTERVAL '1 hour' "
                    + "WHERE id = ?", expired.getId());
                evictMybatisCache();
                purchaseOrderAutoLockJob.lockExpiredOrders();

                assertThat(reloadOrder(expired.getId()).getLockedFlag()).isTrue();
                assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_purchase_operation_log WHERE purchase_order_id = ? "
                        + "AND operation_type = 'PRICE_AUTO_LOCK'",
                    Integer.class, expired.getId())).isEqualTo(1);
            }

            @Test
            @DisplayName("#4 到期但没有历史采购价时保持待定价，不写 0")
            void autoFillWithoutReferenceStaysPending() {
                Long skuId = newOnShelfSku("PW4");
                Long supplierId = newPurchasableSupplier("PW4", skuId);
                PurchaseOrderVO pending = submitOrder(createPendingOrder("PW4", supplierId, skuId, "2.0000").getId());
                Long itemId = pending.getItems().getFirst().getId();
                jdbc.update("UPDATE xsy_purchase_order_item SET price_window_started_at = CURRENT_TIMESTAMP - INTERVAL '8 days' "
                    + "WHERE id = ?", itemId);
                evictMybatisCache();

                purchasePriceAutoFillJob.autoFillExpiredPrices();

                PurchaseOrderVO reloaded = reloadOrder(pending.getId());
                assertThat(reloaded.getItems().getFirst().getPricePending()).isTrue();
                assertThat(reloaded.getItems().getFirst().getPurchasePrice()).isNull();
                assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_purchase_price_adjust WHERE purchase_order_item_id = ?",
                    Integer.class, itemId)).isZero();
            }

            @Test
            @DisplayName("#5 自动回填只认同一采购单位的历史价：公斤价不会被填成斤价")
            void autoFillRequiresSamePurchaseUnit() {
                Long skuId = newOnShelfSku("PW5");
                Long supplierId = newPurchasableSupplier("PW5", skuId);
                PurchaseOrderVO history = submitOrder(createDraftOrder("PW5-H", supplierId, skuId, "2.0000", "6.0000").getId());
                PurchaseOrderVO pending = submitOrder(createPendingOrder("PW5-P", supplierId, skuId, "3.0000").getId());
                Long pendingItemId = pending.getItems().getFirst().getId();
                String pendingUnit = pending.getItems().getFirst().getPurchaseUnit();

                // 历史行改成另一个单位：没有同单位参考价时必须保持待定价，不能直接套用
                jdbc.update("UPDATE xsy_purchase_order_item SET purchase_unit_snapshot = ? WHERE id = ?",
                    pendingUnit + "-OTHER", history.getItems().getFirst().getId());
                expireWindow(pending.getId(), pendingItemId);
                purchasePriceAutoFillJob.autoFillExpiredPrices();
                assertThat(reloadOrder(pending.getId()).getItems().getFirst().getPricePending()).isTrue();
                assertThat(reloadOrder(pending.getId()).getItems().getFirst().getPurchasePrice()).isNull();

                // 单位一致后才回填
                jdbc.update("UPDATE xsy_purchase_order_item SET purchase_unit_snapshot = ? WHERE id = ?", pendingUnit,
                    history.getItems().getFirst().getId());
                purchasePriceAutoFillJob.autoFillExpiredPrices();
                PurchaseOrderVO filled = reloadOrder(pending.getId());
                assertThat(filled.getItems().getFirst().getPricePending()).isFalse();
                assertThat(filled.getItems().getFirst().getPurchasePrice()).isEqualByComparingTo("6.0000");
            }

            @Test
            @DisplayName("#6 自动锁单任务先补价再锁单：锁单不会抢先把可回填的单锁死")
            void autoLockJobFillsPriceBeforeLocking() {
                Long skuId = newOnShelfSku("PW6");
                Long supplierId = newPurchasableSupplier("PW6", skuId);
                submitOrder(createDraftOrder("PW6-H", supplierId, skuId, "2.0000", "5.5000").getId());
                PurchaseOrderVO pending = submitOrder(createPendingOrder("PW6-P", supplierId, skuId, "3.0000").getId());
                Long pendingItemId = pending.getItems().getFirst().getId();
                expireWindow(pending.getId(), pendingItemId);

                // 只跑锁单任务：补价必须在同一轮里先发生，否则这张单会被锁成待定价
                purchaseOrderAutoLockJob.lockExpiredOrders();

                PurchaseOrderVO reloaded = reloadOrder(pending.getId());
                assertThat(reloaded.getLockedFlag()).isTrue();
                assertThat(reloaded.getItems().getFirst().getPricePending()).isFalse();
                assertThat(reloaded.getItems().getFirst().getPurchasePrice()).isEqualByComparingTo("5.5000");
            }

            private void expireWindow(Long orderId, Long itemId) {
                jdbc.update("UPDATE xsy_purchase_order_item SET price_window_started_at = CURRENT_TIMESTAMP - INTERVAL '8 days' "
                    + "WHERE id = ?", itemId);
                jdbc.update("UPDATE xsy_purchase_order SET auto_lock_at = CURRENT_TIMESTAMP - INTERVAL '1 hour' WHERE id = ?",
                    orderId);
                evictMybatisCache();
            }

            private PurchaseOrderVO createPendingOrder(String suffix, Long supplierId, Long skuId, String quantity) {
                PurchaseOrderAddForm form = orderForm(supplierId, fixtureWarehouseId(), skuId, quantity, null);
                form.getItems().getFirst().setPricePending(true);
                return purchaseOrderService.create(form, prefix + ":pending:" + suffix);
            }
        }



        @Nested
        @DisplayName("入库方式与仓库确认入库（PG IT）")
        class PurchaseReceiptPutawayIT {
            /**
             * 造一张「无需求来源 + WAREHOUSE_CONFIRM」的收货单（可自定义采购单位），返回草稿收货单。
             */
            private PurchaseReceiptVO warehouseConfirmReceipt(String suffix, Long skuId, String quantity,
                                                              String purchaseUnit, Long orderId) {
                PurchaseReceiptCreateForm form = new PurchaseReceiptCreateForm();
                form.setPurchaseOrderId(orderId);
                form.setReceiptMode(ScmReceiptModeEnum.WAREHOUSE_CONFIRM.name());
                form.setRemark("B1 IT 收货单");
                return purchaseReceiptService.create(form, prefix + ":wc:" + suffix + ":" + orderId);
            }

            /**
             * 一张「无需求来源 + 可自定义采购单位」的已提交采购单。
             */
            private PurchaseOrderVO freeSubmittedOrder(String suffix, Long skuId, String quantity,
                                                       String purchaseUnit) {
                Long supplierId = newSupplier(suffix);
                linkSupplierSku(supplierId, skuId, purchaseUnit);
                PurchaseOrderVO order = createDraftOrder(suffix, supplierId, skuId, quantity, "6.2000");
                return submitOrder(order.getId());
            }

            private PurchaseReceiptVO putawayReceipt(Long receiptId) {
                PurchaseReceiptVO current = reloadReceipt(receiptId);
                PurchaseReceiptPutawayForm form = new PurchaseReceiptPutawayForm();
                form.setId(receiptId);
                form.setVersion(current.getVersion());
                return purchaseReceiptService.putaway(
                        form, prefix + ":putaway:" + receiptId + ":" + UUID.randomUUID());
            }

            // ------------------------------------------------------------------
            // 1. DIRECT：confirm 同事务完成 putaway + PURCHASE_IN
            // ------------------------------------------------------------------

            @Test
            @DisplayName("DIRECT：confirm 后 receipt=CONFIRMED 且 putaway=COMPLETED，仅一条 PURCHASE_IN")
            void directConfirmCompletesPutawayInTheSameTransaction() {
                Long warehouseId = seedWarehouseId();
                Long skuId = newOnShelfSku("PD1");
                W6Fixture fx = inboundFixture("PD1", skuId, "8.0000");
                PurchaseReceiptVO confirmed = confirmReceipt(fx.receipt().getId(), "8.0000");

                assertThat(confirmed.getStatus()).isEqualTo("CONFIRMED");
                assertThat(confirmed.getReceiptMode()).isEqualTo("DIRECT");
                assertThat(confirmed.getPutawayStatus()).isEqualTo("COMPLETED");
                assertThat(confirmed.getPutawayAt()).isNotNull();
                assertThat(confirmed.getPutawayBy()).isEqualTo(confirmed.getOperator());

                assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("8.0000");
                assertThat(movementCount(warehouseId, skuId)).isEqualTo(1);
                assertThat(movementsOfReceiptItem(fx.receiptItemId())).isEqualTo(1);
            }

            // ------------------------------------------------------------------
            // 2. WAREHOUSE_CONFIRM：confirm 只产生商业记录，不写库存
            // ------------------------------------------------------------------

            @Test
            @DisplayName("WAREHOUSE_CONFIRM：confirm 后 PENDING、无流水、余额不变")
            void warehouseConfirmDoesNotWriteInventory() {
                Long warehouseId = seedWarehouseId();
                Long skuId = newOnShelfSku("PD2");
                PurchaseOrderVO order = freeSubmittedOrder("PD2", skuId, "8.0000", DEFAULT_PURCHASE_UNIT);
                PurchaseReceiptVO receipt = warehouseConfirmReceipt("PD2", skuId, "8.0000", DEFAULT_PURCHASE_UNIT, order.getId());

                PurchaseReceiptVO confirmed = confirmReceipt(receipt.getId(), "8.0000");
                assertThat(confirmed.getStatus()).isEqualTo("CONFIRMED");
                assertThat(confirmed.getReceiptMode()).isEqualTo("WAREHOUSE_CONFIRM");
                assertThat(confirmed.getPutawayStatus()).isEqualTo("PENDING");
                assertThat(confirmed.getPutawayAt()).isNull();
                assertThat(confirmed.getPutawayBy()).isNull();

                assertThat(balanceRow(warehouseId, skuId)).isNull();
                assertThat(movementCount(warehouseId, skuId)).isEqualTo(0);
            }

            // ------------------------------------------------------------------
            // 3. putaway：COMPLETED + PURCHASE_IN，occurred_at = putaway_at
            // ------------------------------------------------------------------

            @Test
            @DisplayName("putaway：COMPLETED、余额入库、流水 occurred_at = putaway_at（非 confirmed_at）")
            void putawayWritesInventoryWithPhysicalTime() {
                Long warehouseId = seedWarehouseId();
                Long skuId = newOnShelfSku("PD3");
                PurchaseOrderVO order = freeSubmittedOrder("PD3", skuId, "8.0000", DEFAULT_PURCHASE_UNIT);
                PurchaseReceiptVO receipt = warehouseConfirmReceipt("PD3", skuId, "8.0000", DEFAULT_PURCHASE_UNIT, order.getId());
                confirmReceipt(receipt.getId(), "8.0000");

                PurchaseReceiptVO putaway = putawayReceipt(receipt.getId());
                assertThat(putaway.getPutawayStatus()).isEqualTo("COMPLETED");
                assertThat(putaway.getPutawayAt()).isNotNull();
                assertThat(putaway.getPutawayBy()).isNotNull();

                assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("8.0000");
                assertThat(movementCount(warehouseId, skuId)).isEqualTo(1);

                Map<String, Object> mv = movementsOf(warehouseId, skuId).getFirst();
                OffsetDateTime occurredAt = timestampOf(mv.get("occurred_at"));
                OffsetDateTime putawayAt = jdbc.queryForObject(
                        "SELECT putaway_at FROM xsy_purchase_receipt WHERE id = ?", OffsetDateTime.class, receipt.getId());
                OffsetDateTime confirmedAt = receiptConfirmedAt(receipt.getId());
                assertThat(occurredAt.toInstant()).isEqualTo(putawayAt.toInstant());
                assertThat(mv.get("operator")).isEqualTo(putaway.getPutawayBy());
                // 与 confirm 是两次独立动作：putaway 时刻不早于 confirm 时刻。
                assertThat(putawayAt.toInstant()).isAfterOrEqualTo(confirmedAt.toInstant());
            }

            // ------------------------------------------------------------------
            // 4. 重复 putaway：41008，不重复入库
            // ------------------------------------------------------------------

            @Test
            @DisplayName("重复 putaway：第二次 41008，余额与流水不重复")
            void secondPutawayIsRejectedAndDoesNotDuplicateInbound() {
                Long warehouseId = seedWarehouseId();
                Long skuId = newOnShelfSku("PD4");
                PurchaseOrderVO order = freeSubmittedOrder("PD4", skuId, "8.0000", DEFAULT_PURCHASE_UNIT);
                PurchaseReceiptVO receipt = warehouseConfirmReceipt("PD4", skuId, "8.0000", DEFAULT_PURCHASE_UNIT, order.getId());
                confirmReceipt(receipt.getId(), "8.0000");
                putawayReceipt(receipt.getId());

                expectCode(() -> putawayReceipt(receipt.getId()), 41008);

                assertThat(balanceRow(warehouseId, skuId).getQuantity()).isEqualByComparingTo("8.0000");
                assertThat(movementCount(warehouseId, skuId)).isEqualTo(1);
                assertThat(movementsOfReceiptItem(receipt.getItems().getFirst().getId())).isEqualTo(1);
            }

            // ------------------------------------------------------------------
            // 5. 历史 CONFIRMED 收货 → DIRECT / COMPLETED（迁移的记录映射）
            // ------------------------------------------------------------------

            @Test
            @DisplayName("历史 DIRECT 收货确认后 = DIRECT / COMPLETED，且不额外产生流水")
            void historicalConfirmedReceiptMapsToDirectCompleted() {
                Long warehouseId = seedWarehouseId();
                Long skuId = newOnShelfSku("PD6");
                W6Fixture fx = inboundFixture("PD6", skuId, "8.0000");
                confirmReceipt(fx.receipt().getId(), "8.0000");

                PurchaseReceiptVO receipt = reloadReceipt(fx.receipt().getId());
                assertThat(receipt.getReceiptMode()).isEqualTo("DIRECT");
                assertThat(receipt.getPutawayStatus()).isEqualTo("COMPLETED");
                assertThat(receipt.getPutawayAt()).isEqualTo(receipt.getConfirmedAt());
                assertThat(receipt.getPutawayBy()).isEqualTo(receipt.getOperator());

                // 迁移与实时路径共享同一条源身份唯一索引 → 不会重复产生 PURCHASE_IN
                assertThat(movementCount(warehouseId, skuId)).isEqualTo(1);
                assertThat(movementsOfReceiptItem(fx.receiptItemId())).isEqualTo(1);
            }
        }



        @Nested
        @DisplayName("P0-F 采购数据范围与归属收口（PG IT）")
        class ScmPurchaseDataScopePgIT {
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
                // 仓库授权属库存域，不在本用例的射程内；这里显式把两个测试员工授权给种子仓库，
                // 免得用例结果取决于别人的种子数据有没有落地。
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
            @DisplayName("详情与行/日志子读同一套规则：别人的单是数据范围越权，不存在仍是 40481")
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

                // 表单里填的是别人的 id，写入数据库的仍是调用者本人（伪造归属在写入侧就被挡掉）
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
            @DisplayName("收货单按父采购单的采购员继承：列表与详情、明细同一套规则")
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

            @Test
            @DisplayName("收货单创建重放仍按采购单当前负责人授权")
            void receiptCreateReplayRechecksCurrentPurchaser() {
                Purchasable goods = purchasable("RPR");
                Long orderId = createSubmittedOrderAt(goods, "RPR", purchaserB, seedWarehouseId(), "4.0000");
                Long receiptId = as(purchaserB, Set.of(), () -> createReceipt(orderId).getId());

                jdbc.update("UPDATE xsy_purchase_order SET purchaser_id = ? WHERE id = ?", purchaserA, orderId);
                evictMybatisCache();
                assertNoPermission(() -> as(purchaserB, Set.of(), () -> createReceipt(orderId)));
                assertThat(receiptDeleted(receiptId)).isFalse();
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
                    // 替别人建的单，命令侧必须还能把自己刚写的单据查询结果回来（读取范围只约束查询端点）
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

            // ==================== 5. 共享主档与校验失败时拒绝操作 ====================

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

                XsyRequestUtil.remove();
                PageResult<PurchaseOrderVO> orders = purchaseQueryService.orderQuery(newForm(new PurchaseOrderQueryForm()));
                assertThat(orders.getList()).isEmpty();
                assertThat(orders.getTotal()).isZero();
                assertThat(orders.getEmptyFlag()).as("空分页形状与正常分页一致，前端不必另写分支").isTrue();

                assertThat(purchaseQueryService.receiptQuery(newForm(new PurchaseReceiptQueryForm())).getList()).isEmpty();
                assertThat(purchaseQueryService.demandQuery(newForm(new PurchaseDemandQueryForm())).getList()).isEmpty();
                // 两条聚合读走的是同一条校验失败时拒绝操作取向：无身份 → 仓库范围为 none → 空分页，而不是全量
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

                // 持全量仓库范围者两个仓都能读，数字与各自仓一致（收窄没有改变任何规则）
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

                // 记录都在库里（下面用全量范围读得到），空分页只能是范围的结果，不是「没有数据」
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

                // 取数规则没有被新参数挡掉：来源行数 = 1、需求真写入数据库
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
                // 校验失败时拒绝操作必须真的什么都没写：状态仍 PENDING、目标仓既无余额行也无 PURCHASE_IN 流水
                assertThat(receiptStatus(receiptId)).isEqualTo("PENDING");
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
                assertThat(receiptStatus(receiptId)).as("越权确认没有推进状态").isEqualTo("PENDING");

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
                assertThat(jdbc.queryForObject("SELECT putaway_status FROM xsy_purchase_receipt WHERE id = ?",
                        String.class, receiptId)).isEqualTo("PENDING");

                assertNoPermission(() -> as(caller, Set.of(), () -> putaway(receiptId, "denied")));
                assertThat(balanceRowCount(otherWarehouse, goods.skuId())).as("被拒的上架没有写库存").isZero();

                as(caller, Set.of(ScmDataScopeService.WAREHOUSE_ALL_PERM), () -> putaway(receiptId, "granted"));
                assertThat(balanceRow(otherWarehouse, goods.skuId()).getQuantity()).isEqualByComparingTo("4.0000");
            }

            // ==================== 10. 采购写路径的归属边界 ====================

            @Test
            @DisplayName("采购单命令按归属收窄：别人名下的单编辑 / 删除都到不了，本人的照常")
            void orderCommandsFollowPurchaserScope() {
                Purchasable goods = purchasable("WC");
                Long owner = newWarehouseScopedEmployee("WC1", seedWarehouseId());
                Long other = newWarehouseScopedEmployee("WC2", seedWarehouseId());
                Long orderId = createOrder(goods, "WC-A", owner, owner, Set.of());
                // 编辑表单在越权上下文之外装配：editForm 内部要读一次 orderDetail，
                // 放进 as(other, ...) 里会先被那道读侧守卫拒掉，测不到 update 本身。
                PurchaseOrderUpdateForm edit = editForm(orderId, "5.0000", "6.2000");

                assertNoPermission(() -> as(other, Set.of(), () -> purchaseOrderService.update(edit)));
                assertThat(orderVersion(orderId)).as("越权命令没有推进版本").isZero();
                assertThat(orderDeleted(orderId)).as("越权删除没有留下软删标记").isFalse();
                assertNoPermission(() -> as(other, Set.of(), () -> {
                    purchaseOrderService.delete(deleteForm(orderId));
                    return null;
                }));
                assertThat(orderDeleted(orderId)).isFalse();

                assertThat(as(owner, Set.of(), () -> purchaseOrderService.update(edit)).getId()).isEqualTo(orderId);
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
                jdbc.update("INSERT INTO xsy_employee_warehouse_scope (employee_id, warehouse_id) VALUES (?, ?)",
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
                form.setVersion(jdbc.queryForObject("SELECT version FROM xsy_purchase_receipt WHERE id = ?",
                        Integer.class, receiptId));
                return form;
            }

            /**
             * 防重复请求编号必须逐次不同：第一次被范围拒绝后 {@code claim} 记录已经落下，
             * 同键重放会让第二次（补齐授权的那次）读到「已提交但缺 result_data」的半成品记录而直接报错，
             * 测不到「授权补齐后放行」这件事本身。
             */
            private PurchaseReceiptVO putaway(Long receiptId, String tag) {
                return purchaseReceiptService.putaway(putawayForm(receiptId), prefix + ":putaway:" + receiptId + ":" + tag);
            }

            private PurchaseDemandAllocateForm allocateForm(Long demandId, Long orderId) {
                PurchaseDemandAllocateForm form = new PurchaseDemandAllocateForm();
                form.setDemandId(demandId);
                form.setPurchaseOrderItemId(jdbc.queryForObject("SELECT id FROM xsy_purchase_order_item "
                        + "WHERE purchase_order_id = ? AND deleted = FALSE ORDER BY id LIMIT 1", Long.class, orderId));
                form.setQuantity("4.0000");
                form.setSupplierId(
                        jdbc.queryForObject("SELECT supplier_id FROM xsy_purchase_order WHERE id = ?", Long.class, orderId));
                form.setWarehouseId(seedWarehouseId());
                form.setVersion(jdbc.queryForObject("SELECT version FROM xsy_purchase_demand WHERE id = ?",
                        Integer.class, demandId));
                return form;
            }

            private String receiptStatus(Long receiptId) {
                evictMybatisCache();
                return jdbc.queryForObject("SELECT status FROM xsy_purchase_receipt WHERE id = ?", String.class, receiptId);
            }

            private Integer receiptVersion(Long receiptId) {
                evictMybatisCache();
                return jdbc.queryForObject("SELECT version FROM xsy_purchase_receipt WHERE id = ?", Integer.class, receiptId);
            }

            private String receiptRemark(Long receiptId) {
                evictMybatisCache();
                return jdbc.queryForObject("SELECT remark FROM xsy_purchase_receipt WHERE id = ?", String.class, receiptId);
            }

            private boolean receiptDeleted(Long receiptId) {
                evictMybatisCache();
                return Boolean.TRUE.equals(jdbc.queryForObject("SELECT deleted FROM xsy_purchase_receipt WHERE id = ?",
                        Boolean.class, receiptId));
            }

            private boolean orderDeleted(Long orderId) {
                evictMybatisCache();
                return Boolean.TRUE.equals(jdbc.queryForObject("SELECT deleted FROM xsy_purchase_order WHERE id = ?",
                        Boolean.class, orderId));
            }

            private BigDecimal allocatedQuantity(Long demandId) {
                evictMybatisCache();
                return jdbc.queryForObject("SELECT allocated_quantity FROM xsy_purchase_demand WHERE id = ?",
                        BigDecimal.class, demandId);
            }

            private void loginAs(Long employeeId) {
                RequestEmployee employee = new RequestEmployee();
                employee.setEmployeeId(employeeId);
                employee.setActualName("采购数据范围 IT");
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                employee.setAdministratorFlag(false);
                employee.setDepartmentId(1L);
                XsyRequestUtil.setRequestUser(employee);
            }

            /**
             * 以某个员工 + 一组功能权限执行一段逻辑。权限由 {@code mockStatic(StpUtil.class)} 提供：
             * 未点名的权限码取 Mockito 默认值 false，与生产上的校验失败时拒绝操作取向一致。<b>不可嵌套调用</b>。
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
             * 因此「已知库存记录」这一夹具直接落表就是它要构造的东西（与 {@code PurchaseDemandSummaryPreviewIT} 同）。
             */
            private void seedBalance(Long warehouseId, Long skuId, String quantity) {
                jdbc.update("INSERT INTO xsy_inventory_balance (warehouse_id, sku_id, unit, quantity, reserved_quantity, "
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
                form.setPageSize(100L);
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
                form.setPageSize(100L);
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
                        "SELECT version FROM xsy_inventory_loss_gain WHERE id = ?", Integer.class, documentId));
                form.setAuditOpinion(opinion);
                return form;
            }

            // ---- 回读（清一级缓存后按裸 SQL 读，断言的是库里的真实行）----

            private Long purchaserIdOf(Long orderId) {
                evictMybatisCache();
                return jdbc.queryForObject("SELECT purchaser_id FROM xsy_purchase_order WHERE id = ?", Long.class, orderId);
            }

            private Integer orderVersion(Long orderId) {
                evictMybatisCache();
                return jdbc.queryForObject("SELECT version FROM xsy_purchase_order WHERE id = ?", Integer.class, orderId);
            }

            private Long demandPurchaserId(Long demandId) {
                evictMybatisCache();
                return jdbc.queryForObject("SELECT purchaser_id FROM xsy_purchase_demand WHERE id = ?", Long.class, demandId);
            }

            private String lossGainStatus(Long documentId) {
                evictMybatisCache();
                return jdbc.queryForObject("SELECT status FROM xsy_inventory_loss_gain WHERE id = ?", String.class, documentId);
            }

            private String lossGainAuditor(Long documentId) {
                evictMybatisCache();
                return jdbc.queryForObject("SELECT auditor FROM xsy_inventory_loss_gain WHERE id = ?", String.class, documentId);
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
    }

    @Nested
    @DisplayName("W5 采购域迁移（PG IT）")
    class ScmPurchaseMigrationIT extends ScmBaselineMigrationPgITBase {
        /**
         * V15 建出的 9 张采购域表。
         */
        private static final List<String> V15_TABLES = List.of(
                "xsy_warehouse",
                "xsy_purchase_demand",
                "xsy_purchase_demand_allocation",
                "xsy_purchase_order",
                "xsy_purchase_order_item",
                "xsy_purchase_receipt",
                "xsy_purchase_receipt_item",
                "xsy_receipt_weighing_record",
                "xsy_purchase_operation_log");

        /**
         * 逗号分隔的表名清单，配合 {@code string_to_array(?, ',')} 使用。
         *
         * <p>不直接传 {@code String[]} 的原因：{@code JdbcTemplate.queryForObject(sql, Class, Object...)}
         * 会把数组当成可变参数展开（9 个表名 → 9 个参数），而 SQL 里只有 1 个 {@code ?}，
         * 于是报「column index out of range」。拼成字符串再在 SQL 里切开，行为与驱动无关。
         */
        private static final String V15_TABLE_LIST = String.join(",", V15_TABLES);

        @Test
        @DisplayName("V15 建 9 表 + 2 序列；后续获批索引 + 2 条种子")
        void schemaShapeMatchesDesign() {
            List<String> tables = jdbc.queryForList(
                    "SELECT table_name FROM information_schema.tables "
                            + "WHERE table_schema = current_schema() AND table_type = 'BASE TABLE'", String.class);
            assertThat(tables).containsAll(V15_TABLES);

            List<String> sequences = jdbc.queryForList(
                    "SELECT sequencename FROM pg_sequences WHERE schemaname = current_schema()", String.class);
            assertThat(sequences).contains("purchase_order_no_seq", "purchase_receipt_no_seq");

            // 非主键索引：主键走 pg_constraint，不计入这里的计数。部分索引分别来自
            // V22（双入库生命周期）、V40（地图归属，warehouse）、V51（报表日期轴）、V74（采购每日清单）、
            // V78（ADM-05 净需求计算结果批次给 purchase_demand 加的「同一批次行只能生成一条需求」唯一索引）
            // 与 V183（录单参考最近采购价：采购单提交时间轴 + 采购行 SKU 有价索引）
            Integer indexes = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_indexes i "
                            + "WHERE i.schemaname = current_schema() "
                            + "  AND i.tablename = ANY (string_to_array(?, ',')) "
                            + "  AND NOT EXISTS (SELECT 1 FROM pg_constraint c "
                            + "                  WHERE c.conname = i.indexname AND c.contype = 'p')",
                    Integer.class, V15_TABLE_LIST);
            assertThat(indexes).isEqualTo(40);

            Integer partial = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_indexes i "
                            + "WHERE i.schemaname = current_schema() "
                            + "  AND i.tablename = ANY (string_to_array(?, ',')) "
                            + "  AND i.indexdef LIKE '%WHERE%'", Integer.class, V15_TABLE_LIST);
            // 计数 = V15 建表时的索引 + 后续获批的部分索引（V78 的 ADM-05 批次行唯一索引、
            // V183 的两条最近采购价索引）
            // V190 待定价窗口索引、V192 自动锁单索引）
            // （ON purchase_demand(calculation_batch_item_id)
            // WHERE deleted=FALSE AND calculation_batch_item_id IS NOT NULL）。
            assertThat(partial).isEqualTo(36);

            // 8 条唯一索引 = V15 里显式 CREATE UNIQUE INDEX 的 7 条 + V78（ADM-05 计算结果批次）
            // 在 purchase_demand 上加的批次行唯一索引。
            // 必须排除「约束支撑的索引」：PG 的 PRIMARY KEY / UNIQUE 约束也会生成 CREATE UNIQUE INDEX，
            // 不排除就会把 9 个主键算进来，从而把断言变成对主键个数的间接断言。
            Integer unique = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_indexes i "
                            + "WHERE i.schemaname = current_schema() "
                            + "  AND i.tablename = ANY (string_to_array(?, ',')) "
                            + "  AND i.indexdef LIKE 'CREATE UNIQUE%' "
                            + "  AND NOT EXISTS (SELECT 1 FROM pg_constraint c "
                            + "                  WHERE c.conname = i.indexname AND c.contype IN ('p','u'))",
                    Integer.class, V15_TABLE_LIST);
            assertThat(unique).isEqualTo(8);

            // 2 条种子：默认仓库（单仓库规则）+ 超收容差配置
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM xsy_warehouse WHERE warehouse_code = ? AND deleted = FALSE",
                    String.class, SEED_WAREHOUSE_CODE)).isEqualTo("ENABLED");
            assertThat(jdbc.queryForObject(
                    "SELECT config_value FROM t_config WHERE config_key = ?",
                    String.class, "scm.purchase.over_receipt_tolerance_percent")).isEqualTo("10");
        }

        @Test
        @DisplayName("全库零外键 + 库存表归属 W6/V19 而非 W5（§3 / §8.1）")
        void noForeignKeysAndInventoryTablesBelongToW6() {
            // 全库 FK=0 是 V2 起定下的既有纪律，任何域都不得引入第一个外键。
            // 库存表之间（余额 → 流水）的引用完整性同样走服务层守卫，不走 DB 外键。
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM pg_constraint WHERE contype = 'f'", Integer.class)).isZero();

            // 库存表不在采购域的建表范围里，因此这里断言的不是「存在性」，而是「归属」：
            //   1) 它们确实存在；
            //   2) 它们不在 V15 的 9 张采购表里；
            //   3) 表注释带 V19 写入的库存域前缀（证明来自 V19 而不是别处）。
            // 形状（列 / 约束 / 索引）由库存域的 ScmInventoryMigrationIT 负责断言，
            // 本类不重复。
            List<String> inventoryTables = List.of("xsy_inventory_balance", "xsy_inventory_movement");
            assertThat(inventoryTables).doesNotContainAnyElementsOf(V15_TABLES);
            assertThat(jdbc.queryForList(
                    "SELECT obj_description(c.oid, 'pg_class') FROM pg_class c "
                            + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                            + "WHERE n.nspname = current_schema() AND c.relname = ANY (string_to_array(?, ','))",
                    String.class, String.join(",", inventoryTables)))
                    .hasSize(2)
                    .allSatisfy(comment -> assertThat(comment).startsWith("W6 库存域"));
        }

        @Test
        @DisplayName("Q14：ck_purchase_operation_log_owner 四分支 —— DEMAND_GENERATE 双 NULL 可写、采购单 id 缺失被拒")
        void operationLogOwnerCheckIsOperationTypeAware() {
            // 约束定义必须按 operation_type 分支，而不是「至少一个 id 非空」这种简单写法
            String definition = jdbc.queryForObject(
                    "SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c "
                            + "JOIN pg_class t ON t.oid = c.conrelid "
                            + "WHERE t.relname = 'xsy_purchase_operation_log' AND c.conname = 'ck_purchase_operation_log_owner'",
                    String.class);
            assertThat(definition)
                    .contains("DEMAND_GENERATE")
                    .contains("DEMAND_ALLOCATE")
                    .contains("RECEIPT_CREATE")
                    // 简单写法的特征：「IS NOT NULL OR ... IS NOT NULL」，这种写法必须被拒
                    .doesNotContain("purchase_order_id IS NOT NULL OR purchase_receipt_id IS NOT NULL");

            // 正例：DEMAND_GENERATE 发生在采购单还不存在之前，两个 id 必须同时为空才写得进去
            int inserted = jdbc.update(
                    "INSERT INTO xsy_purchase_operation_log (purchase_order_id, purchase_receipt_id, operation_type, "
                            + "operator, after_data, created_by) VALUES (NULL, NULL, 'DEMAND_GENERATE', 'W5 IT', "
                            + "'{\"demandIds\":[]}'::JSONB, 'W5 IT')");
            assertThat(inserted).isEqualTo(1);

            // 反例：DEMAND_GENERATE 带采购单 id → 违反归属约束
            expectSqlFailure(
                    "INSERT INTO xsy_purchase_operation_log (purchase_order_id, operation_type, operator) "
                            + "VALUES (1, 'DEMAND_GENERATE', 'W5 IT')");
            // 反例：DEMAND_ALLOCATE 没有采购单 id → 违反归属约束
            expectSqlFailure(
                    "INSERT INTO xsy_purchase_operation_log (operation_type, operator) "
                            + "VALUES ('DEMAND_ALLOCATE', 'W5 IT')");
            // 反例：RECEIPT_CREATE 只有采购单 id、没有收货单 id → 违反归属约束
            expectSqlFailure(
                    "INSERT INTO xsy_purchase_operation_log (purchase_order_id, operation_type, operator) "
                            + "VALUES (1, 'RECEIPT_CREATE', 'W5 IT')");
            // 反例：operation_type 不在白名单
            expectSqlFailure(
                    "INSERT INTO xsy_purchase_operation_log (purchase_order_id, operation_type, operator) "
                            + "VALUES (1, 'UNKNOWN_OP', 'W5 IT')");
        }

        @Test
        @DisplayName("flyway_schema_history：模块记录完整升序或有保留旧历史的切换基线，全部成功")
        void flywayHistoryIsAppendOnly() {
            List<MigrationVersion> versions = jdbc.queryForList(
                    "SELECT version FROM flyway_schema_history "
                            + "WHERE success = TRUE AND version IS NOT NULL ORDER BY installed_rank",
                    String.class).stream().map(MigrationVersion::fromVersion).toList();
            assertThat(versions).as("迁移记录不应为空").isNotEmpty().doesNotHaveDuplicates();
            assertThat(versions).isSorted();
            Integer baselines = jdbc.queryForObject(
                    "SELECT count(*) FROM flyway_schema_history WHERE type='BASELINE'", Integer.class);
            if (baselines == 0) {
                MigrationVersion[] modules = java.util.stream.IntStream.rangeClosed(1, 24)
                        .mapToObj(number -> MigrationVersion.fromVersion("1000." + number))
                        .toArray(MigrationVersion[]::new);
                assertThat(versions).as("新库必须完整记录24份模块初始化").startsWith(modules);
            } else {
                assertThat(baselines).isEqualTo(1);
                assertThat(versions).startsWith(MigrationVersion.fromVersion("1000.24"));
                assertThat(jdbc.queryForObject(
                        "SELECT description FROM flyway_schema_history WHERE type='BASELINE'", String.class))
                        .isEqualTo("SCM module baseline V244");
                List<Integer> legacy = jdbc.queryForList(
                        "SELECT version::integer FROM flyway_schema_history_legacy "
                                + "WHERE version IS NOT NULL ORDER BY installed_rank", Integer.class);
                assertThat(legacy.size()).isIn(243, 244);
                assertThat(legacy).as("切换前的历史记录必须保留且连续")
                        .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, legacy.size()).boxed().toList());
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM flyway_schema_history_legacy WHERE NOT success", Integer.class)).isZero();
            }
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM flyway_schema_history WHERE success = FALSE", Integer.class)).isZero();
            // 除版本化迁移外，最多只有 1 条 << Flyway Schema Creation >> 基线（version 为空）。
            // 从空 schema 直接 migrate 时没有基线行，因此这里断言的是「不存在别的非版本化记录」。
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM flyway_schema_history WHERE version IS NULL", Integer.class))
                    .isLessThanOrEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM flyway_schema_history WHERE version IS NULL AND description <> '<< Flyway Schema Creation >>'",
                    Integer.class)).isZero();
        }
    }

    @Nested
    class PurchaseUnitTest2 {
        @Nested
        @DisplayName("W6 防重复处理结果持久化编解码（单元）")
        class PurchaseIdempotencyResultJsonTest {
            /**
             * 覆盖防重复处理结果里会出现的全部类型形状：带时区时间 / null 时间 / 字符串 / 定点数 / 布尔。
             *
             * <p>用 record 而不是 Map：Jackson 对 record 走「构造器反序列化」，
             * 与 VO 的反序列化路径最接近，能真实暴露「字段名对不上」「类型不可构造」这类问题。
             */
            private record Payload(OffsetDateTime confirmedAt, OffsetDateTime emptyAt, String receiptNo,
                                   BigDecimal quantity, Boolean deleted) {
            }

            private static Map<String, Object> store(Object payload) {
                return (Map<String, Object>) PurchaseIdempotencyService.RESULT_JSON
                        .convertValue(payload, Object.class);
            }

            private static Payload restore(Map<String, Object> stored) {
                return PurchaseIdempotencyService.RESULT_JSON.convertValue(stored, Payload.class);
            }

            @Test
            @DisplayName("往返无损：微秒精度、null、定点数、布尔全部原样还原")
            void roundTripIsLossless() {
                OffsetDateTime exact = OffsetDateTime.parse("2026-09-18T10:51:51.394795+08:00");
                Payload original = new Payload(exact, null, "PR20260918001",
                        new BigDecimal("10.0000"), Boolean.FALSE);

                Payload restored = restore(store(original));

                // 关键：微秒必须保留。展示格式（秒级）会让这里失败 —— 那正是被修正的缺陷。
                assertThat(restored.confirmedAt().toInstant()).isEqualTo(exact.toInstant());
                assertThat(restored.emptyAt()).isNull();
                assertThat(restored.receiptNo()).isEqualTo("PR20260918001");
                assertThat(restored.quantity()).isEqualByComparingTo("10.0000");
                assertThat(restored.deleted()).isFalse();
            }

            @Test
            @DisplayName("兼容回归期间的旧数据：展示格式 yyyy-MM-dd HH:mm:ss 仍可重放")
            void legacyDisplayFormatStillReplays() {
                // 只注册序列化器的那段时间里，首次执行成功的请求写下的就是这种形状
                Map<String, Object> legacy = new LinkedHashMap<>();
                legacy.put("confirmedAt", "2026-09-18 10:51:51");
                legacy.put("emptyAt", null);
                legacy.put("receiptNo", "PR20260918002");
                legacy.put("quantity", "3.0000");
                legacy.put("deleted", Boolean.FALSE);

                Payload restored = restore(legacy);

                // 展示格式不带时区后缀，按展示时区（Asia/Shanghai）解释 → 2026-09-18T10:51:51+08:00
                assertThat(restored.confirmedAt().toInstant())
                        .isEqualTo(OffsetDateTime.parse("2026-09-18T10:51:51+08:00").toInstant());
            }

            @Test
            @DisplayName("ISO-8601 两种写法都接受（+08:00 与 Z），且未知字段被忽略")
            void acceptsIsoFormsAndIgnoresUnknownProperties() {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("confirmedAt", "2026-09-18T02:51:51Z");
                payload.put("receiptNo", "PR20260918003");
                payload.put("quantity", "1.0000");
                payload.put("deleted", Boolean.TRUE);
                // 未知字段：VO 后来删掉某个字段时，库里已有的结果不应因此变得不可重放
                payload.put("fieldRemovedInALaterWave", "whatever");

                Payload restored = restore(payload);

                assertThat(restored.confirmedAt().toInstant())
                        .isEqualTo(OffsetDateTime.parse("2026-09-18T02:51:51Z").toInstant());
                assertThat(restored.deleted()).isTrue();
            }

            @Test
            @DisplayName("非法时间串不静默吞掉：抛异常，而不是悄悄变成 null")
            void invalidTimestampIsRejected() {
                Map<String, Object> bad = new LinkedHashMap<>();
                bad.put("confirmedAt", "2026/09/18 10:51");
                bad.put("receiptNo", "PR20260918004");
                bad.put("quantity", "1.0000");
                bad.put("deleted", Boolean.FALSE);

                // 静默变 null 会让「重放返回首次的真实结果」变成「返回一个悄悄丢了时间的结果」，
                // 那比直接报错危险得多
                assertThatThrownBy(() -> restore(bad)).isInstanceOf(IllegalArgumentException.class);
            }
        }

        @Nested
        @DisplayName("W6 余额加锁顺序（单元）")
        class PurchaseInboundLockOrderTest {
            private static PurchaseInventoryContract.InboundFact fact(Long warehouseId, Long skuId) {
                return new PurchaseInventoryContract.InboundFact(
                        1L, 1L, skuId, warehouseId, skuId, null, null, null, null,
                        "kg", BigDecimal.ONE, BigDecimal.ONE, "lock-order-probe",
                        OffsetDateTime.parse("2026-01-01T00:00:00+08:00"), "W6 unit", List.of());
            }

            private static List<String> keys(List<PurchaseInventoryContract.InboundFact> facts) {
                return facts.stream().map(f -> f.warehouseId() + "/" + f.skuId()).toList();
            }

            @Test
            @DisplayName("按 (warehouseId, skuId) 升序：仓库优先，同仓库内按 SKU 升序")
            void ordersByWarehouseThenSku() {
                List<PurchaseInventoryContract.InboundFact> facts = new ArrayList<>(List.of(
                        fact(2L, 1L),
                        fact(1L, 9L),
                        fact(1L, 3L),
                        fact(2L, 2L),
                        fact(1L, 1L)));

                facts.sort(PurchaseReceiptService.inboundLockOrder());

                assertThat(keys(facts)).containsExactly("1/1", "1/3", "1/9", "2/1", "2/2");
            }

            @Test
            @DisplayName("方向是升序：与 reversed() 的结果恰好相反（防止有人把方向写反）")
            void directionIsAscending() {
                List<PurchaseInventoryContract.InboundFact> ascending = new ArrayList<>(List.of(
                        fact(1L, 1L), fact(1L, 2L), fact(2L, 1L)));
                List<PurchaseInventoryContract.InboundFact> descending = new ArrayList<>(ascending);

                descending.sort(PurchaseReceiptService.inboundLockOrder().reversed());

                assertThat(keys(descending)).containsExactly("2/1", "1/2", "1/1");
                assertThat(keys(descending)).isEqualTo(keys(ascending).reversed());
            }

            @Test
            @DisplayName("已升序的输入保持不变；单元素与空集合不抛错")
            void isIdempotentOnSortedInputAndSafeOnDegenerateInput() {
                List<PurchaseInventoryContract.InboundFact> sorted = new ArrayList<>(List.of(
                        fact(1L, 1L), fact(1L, 2L), fact(3L, 7L)));
                List<String> before = keys(sorted);

                sorted.sort(PurchaseReceiptService.inboundLockOrder());
                assertThat(keys(sorted)).isEqualTo(before);

                List<PurchaseInventoryContract.InboundFact> single = new ArrayList<>(List.of(fact(5L, 5L)));
                single.sort(PurchaseReceiptService.inboundLockOrder());
                assertThat(single).hasSize(1);

                List<PurchaseInventoryContract.InboundFact> empty = new ArrayList<>();
                empty.sort(PurchaseReceiptService.inboundLockOrder());
                assertThat(empty).isEmpty();
            }
        }

        @Nested
        @ExtendWith(MockitoExtension.class)
        class PurchasePriceReplayTest {
            @Mock
            private PurchaseOrderDao orderDao;
            @Mock
            private PurchaseOrderItemDao itemDao;
            @Mock
            private PurchaseAccountDao accountDao;
            @Mock
            private PurchaseOwnerResolver ownerResolver;
            @Mock
            private PurchaseIdempotencyService idempotency;
            @InjectMocks
            private PurchasePriceService prices;

            @BeforeEach
            void prepare() {
                var item = new PurchaseOrderItemEntity();
                item.setId(11L);
                item.setPurchaseOrderId(22L);
                var order = new PurchaseOrderEntity();
                order.setId(22L);
                order.setPurchaserId(33L);
                order.setStatus("ACCOUNTED");
                when(itemDao.selectById(11L)).thenReturn(item);
                when(orderDao.lock(22L)).thenReturn(order);
            }

            @ParameterizedTest
            @ValueSource(strings = {"fill", "adjust"})
            void revokedOwnershipRejectsReplayBeforeReadingTheSnapshot(String command) {
                doThrow(new ScmDataScopeException()).when(ownerResolver).requireVisible(33L);

                assertThatThrownBy(() -> execute(command, form())).isInstanceOf(ScmDataScopeException.class);

                verifyNoInteractions(idempotency, accountDao);
                verify(itemDao, never()).lock(any());
            }

            @ParameterizedTest
            @ValueSource(strings = {"fill", "adjust"})
            void authorizedReplayReturnsOriginalResultBeforeCheckingMutableState(String command) {
                var form = form();
                var claim = new PurchaseIdempotencyService.Claim(null, true);
                var snapshot = new PurchaseOrderVO();
                when(idempotency.claim(eq("PURCHASE_PRICE_" + command.toUpperCase(java.util.Locale.ROOT) + ":11"),
                        eq("original-key"), eq(form))).thenReturn(claim);
                when(idempotency.replay(claim, PurchaseOrderVO.class)).thenReturn(snapshot);

                assertThat(execute(command, form)).isSameAs(snapshot);

                var sequence = inOrder(ownerResolver, idempotency);
                sequence.verify(ownerResolver).requireVisible(33L);
                sequence.verify(idempotency).claim(any(), eq("original-key"), eq(form));
                sequence.verify(idempotency).replay(claim, PurchaseOrderVO.class);
                verifyNoInteractions(accountDao);
                verify(itemDao, never()).lock(any());
            }

            private PurchaseOrderVO execute(String command, PurchasePriceForm form) {
                return "fill".equals(command) ? prices.fill(11L, form, "original-key")
                        : prices.adjust(11L, form, "original-key");
            }

            private PurchasePriceForm form() {
                var form = new PurchasePriceForm();
                form.setPrice("3.0000");
                form.setReason("核对采购价");
                return form;
            }
        }
    }
}
