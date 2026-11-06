package com.xsy.scm.order;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xsy.scm.admin.XsyApplication;
import com.xsy.scm.admin.module.system.employee.dao.EmployeeDao;
import com.xsy.scm.admin.module.system.employee.domain.entity.EmployeeEntity;
import com.xsy.scm.admin.module.system.login.domain.RequestEmployee;
import com.xsy.scm.admin.module.system.login.manager.LoginManager;
import com.xsy.scm.admin.test.PgITPaths;
import com.xsy.scm.base.common.code.UserErrorCode;
import com.xsy.scm.base.common.domain.PageResult;
import com.xsy.scm.base.common.domain.RequestUser;
import com.xsy.scm.base.common.domain.SystemEnvironment;
import com.xsy.scm.base.common.domain.UserPermission;
import com.xsy.scm.base.common.enumeration.UserTypeEnum;
import com.xsy.scm.base.common.exception.BusinessException;
import com.xsy.scm.base.common.util.XsyRequestUtil;
import com.xsy.scm.base.handler.GlobalExceptionHandler;
import com.xsy.scm.common.ScmBaselineMigrationPgITBase;
import com.xsy.scm.common.ScmW3PgITBase;
import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.ScmW6PgITBase;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.domain.entity.ScmIdempotencyRecordEntity;
import com.xsy.scm.common.error.ScmErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.handler.ScmExceptionHandler;
import com.xsy.scm.common.idempotency.ScmIdempotencyRequestHasher;
import com.xsy.scm.common.json.ScmOperatorNameResolver;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.customer.controller.CustomerController;
import com.xsy.scm.customer.dao.CustomerDao;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.domain.form.*;
import com.xsy.scm.customer.domain.form.CustomerAddForm;
import com.xsy.scm.customer.domain.form.CustomerQueryForm;
import com.xsy.scm.customer.domain.form.CustomerSellerReassignForm;
import com.xsy.scm.customer.domain.form.CustomerStatusForm;
import com.xsy.scm.customer.domain.form.CustomerUpdateForm;
import com.xsy.scm.customer.domain.vo.CustomerVO;
import com.xsy.scm.customer.service.CustomerQueryService;
import com.xsy.scm.customer.service.CustomerService;
import com.xsy.scm.finance.constant.ScmFinanceReceivableSourceTypeEnum;
import com.xsy.scm.finance.domain.dto.FinanceCreditExposureDto;
import com.xsy.scm.finance.domain.form.FinanceReceiptAddForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffAutoForm;
import com.xsy.scm.finance.domain.vo.FinanceReceiptVO;
import com.xsy.scm.finance.service.FinanceCreditExposureQueryService;
import com.xsy.scm.finance.service.FinanceOrderPayableQueryService;
import com.xsy.scm.finance.service.FinanceReceiptService;
import com.xsy.scm.finance.service.FinanceReceivableService;
import com.xsy.scm.finance.service.FinanceWriteOffService;
import com.xsy.scm.inventory.constant.ScmInventorySourceDocumentTypeEnum;
import com.xsy.scm.inventory.domain.form.InventoryOutboundAddForm;
import com.xsy.scm.inventory.service.InventoryFulfillmentService;
import com.xsy.scm.inventory.service.InventoryOutboundService;
import com.xsy.scm.inventory.service.InventoryReservationService;
import com.xsy.scm.inventory.service.InventorySalesReturnQueryService;
import com.xsy.scm.mall.account.dao.MallCustomerAccountDao;
import com.xsy.scm.mall.account.domain.form.MallAccountCreateForm;
import com.xsy.scm.mall.account.domain.form.MallLoginForm;
import com.xsy.scm.mall.account.domain.form.MallPasswordChangeForm;
import com.xsy.scm.mall.account.domain.vo.MallAccountVO;
import com.xsy.scm.mall.account.domain.vo.RequestMallCustomer;
import com.xsy.scm.mall.account.service.MallAccountService;
import com.xsy.scm.mall.account.service.MallAuthService;
import com.xsy.scm.mall.address.domain.form.MallAddressSaveForm;
import com.xsy.scm.mall.address.domain.vo.MallMapLocationVO;
import com.xsy.scm.mall.address.service.MallAddressService;
import com.xsy.scm.mall.address.service.MallMapService;
import com.xsy.scm.mall.cart.domain.form.MallCartAddForm;
import com.xsy.scm.mall.cart.service.MallCartService;
import com.xsy.scm.mall.catalog.service.MallStockAvailabilityService;
import com.xsy.scm.mall.order.domain.form.MallCheckoutItemForm;
import com.xsy.scm.mall.order.domain.form.MallCheckoutPreviewForm;
import com.xsy.scm.mall.order.domain.form.MallOrderSubmitForm;
import com.xsy.scm.mall.order.domain.vo.MallCheckoutPreviewVO;
import com.xsy.scm.mall.order.service.MallCheckoutService;
import com.xsy.scm.order.constant.*;
import com.xsy.scm.order.constant.OrderErrorCode;
import com.xsy.scm.order.constant.ScmOrderOperationTypeEnum;
import com.xsy.scm.order.constant.ScmOrderSourceEnum;
import com.xsy.scm.order.constant.ScmOrderStatusEnum;
import com.xsy.scm.order.controller.*;
import com.xsy.scm.order.controller.OrderExchangeController;
import com.xsy.scm.order.controller.OrderReceivedCorrectionController;
import com.xsy.scm.order.controller.OrderTagController;
import com.xsy.scm.order.controller.SalesOrderController;
import com.xsy.scm.order.dao.OrderAddressSnapshotDao;
import com.xsy.scm.order.dao.OrderBundleComponentDao;
import com.xsy.scm.order.dao.OrderOperationLogDao;
import com.xsy.scm.order.dao.OrderRefundDao;
import com.xsy.scm.order.dao.OrderReturnDao;
import com.xsy.scm.order.dao.OrderReturnItemDao;
import com.xsy.scm.order.dao.OrderReturnReceiptItemDao;
import com.xsy.scm.order.dao.OrderTagDao;
import com.xsy.scm.order.dao.SalesOrderDao;
import com.xsy.scm.order.dao.SalesOrderItemDao;
import com.xsy.scm.order.domain.entity.*;
import com.xsy.scm.order.domain.entity.OrderBundleComponentEntity;
import com.xsy.scm.order.domain.entity.OrderOperationLogEntity;
import com.xsy.scm.order.domain.entity.SalesOrderEntity;
import com.xsy.scm.order.domain.entity.SalesOrderItemEntity;
import com.xsy.scm.order.domain.form.*;
import com.xsy.scm.order.domain.form.OrderActualQuantityForm;
import com.xsy.scm.order.domain.form.OrderAddressForm;
import com.xsy.scm.order.domain.form.OrderCancelForm;
import com.xsy.scm.order.domain.form.OrderExchangeCreateForm;
import com.xsy.scm.order.domain.form.OrderLockForm;
import com.xsy.scm.order.domain.form.OrderLogQueryForm;
import com.xsy.scm.order.domain.form.OrderPoolAssignForm;
import com.xsy.scm.order.domain.form.OrderPoolClaimForm;
import com.xsy.scm.order.domain.form.OrderPoolQueryForm;
import com.xsy.scm.order.domain.form.OrderReceivedCorrectionForm;
import com.xsy.scm.order.domain.form.OrderRefundQueryForm;
import com.xsy.scm.order.domain.form.OrderRepriceForm;
import com.xsy.scm.order.domain.form.OrderReturnAddForm;
import com.xsy.scm.order.domain.form.OrderReturnApproveForm;
import com.xsy.scm.order.domain.form.OrderReturnApproveItemForm;
import com.xsy.scm.order.domain.form.OrderReturnDecisionForm;
import com.xsy.scm.order.domain.form.OrderReturnIndependentAddForm;
import com.xsy.scm.order.domain.form.OrderReturnIndependentApproveForm;
import com.xsy.scm.order.domain.form.OrderReturnIndependentItemForm;
import com.xsy.scm.order.domain.form.OrderReturnItemForm;
import com.xsy.scm.order.domain.form.OrderReturnQueryForm;
import com.xsy.scm.order.domain.form.OrderReturnReceiveForm;
import com.xsy.scm.order.domain.form.OrderReturnReceiveItemForm;
import com.xsy.scm.order.domain.form.OrderTagAssignForm;
import com.xsy.scm.order.domain.form.OrderTagCreateForm;
import com.xsy.scm.order.domain.form.OrderVersionForm;
import com.xsy.scm.order.domain.form.SalesOrderAddForm;
import com.xsy.scm.order.domain.form.SalesOrderItemForm;
import com.xsy.scm.order.domain.form.SalesOrderPasteForm;
import com.xsy.scm.order.domain.form.SalesOrderQueryForm;
import com.xsy.scm.order.domain.form.SalesOrderUpdateForm;
import com.xsy.scm.order.domain.vo.*;
import com.xsy.scm.order.domain.vo.OrderAddressSnapshotVO;
import com.xsy.scm.order.domain.vo.OrderOperationLogVO;
import com.xsy.scm.order.domain.vo.OrderPoolAssigneeVO;
import com.xsy.scm.order.domain.vo.OrderRecentPriceVO;
import com.xsy.scm.order.domain.vo.OrderRefundVO;
import com.xsy.scm.order.domain.vo.OrderReturnDetailVO;
import com.xsy.scm.order.domain.vo.OrderReturnItemVO;
import com.xsy.scm.order.domain.vo.OrderReturnReceiptVO;
import com.xsy.scm.order.domain.vo.OrderReturnVO;
import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.domain.vo.SalesOrderImportResultVO;
import com.xsy.scm.order.domain.vo.SalesOrderItemVO;
import com.xsy.scm.order.domain.vo.SalesOrderPastePreviewRowVO;
import com.xsy.scm.order.domain.vo.SalesOrderVO;
import com.xsy.scm.order.manager.*;
import com.xsy.scm.order.manager.OrderBundleReservationPlanner;
import com.xsy.scm.order.manager.OrderOperationLogRecorder;
import com.xsy.scm.order.manager.OrderStateMachine;
import com.xsy.scm.order.permission.OrderPermission;
import com.xsy.scm.order.service.*;
import com.xsy.scm.order.service.OrderBundleSnapshotService;
import com.xsy.scm.order.service.OrderCreditService;
import com.xsy.scm.order.service.OrderLockService;
import com.xsy.scm.order.service.OrderPoolService;
import com.xsy.scm.order.service.OrderRefundService;
import com.xsy.scm.order.service.OrderReturnReceiptService;
import com.xsy.scm.order.service.OrderReturnService;
import com.xsy.scm.order.service.OrderSettlementConfirmationPolicy;
import com.xsy.scm.order.service.OrderTagService;
import com.xsy.scm.order.service.SalesOrderDispatchService;
import com.xsy.scm.order.service.SalesOrderImportService;
import com.xsy.scm.order.service.SalesOrderPasteService;
import com.xsy.scm.order.service.SalesOrderQueryService;
import com.xsy.scm.order.service.SalesOrderService;
import com.xsy.scm.pricing.constant.*;
import com.xsy.scm.pricing.constant.ScmPriceSourceEnum;
import com.xsy.scm.pricing.controller.PriceResolveController;
import com.xsy.scm.pricing.domain.form.PriceResolveForm;
import com.xsy.scm.pricing.domain.vo.ResolvedPriceVO;
import com.xsy.scm.pricing.service.PriceResolver;
import com.xsy.scm.product.dao.ProductSkuOptionDao;
import com.xsy.scm.product.domain.entity.ProductBundleComponentEntity;
import com.xsy.scm.product.domain.form.ProductBundleReplaceForm;
import com.xsy.scm.product.domain.form.ProductSkuForm;
import com.xsy.scm.product.domain.form.ProductSpuAddForm;
import com.xsy.scm.product.domain.vo.ProductSkuOptionVO;
import com.xsy.scm.product.service.ProductBundleComponentQueryService;
import com.xsy.scm.product.service.ProductBundleService;
import com.xsy.scm.product.service.ProductSkuOptionQueryService;
import com.xsy.scm.purchase.constant.ScmPurchaseStatusEnum;
import com.xsy.scm.purchase.dao.PurchaseOrderItemDao;
import com.xsy.scm.purchase.domain.vo.PurchaseLastPriceVO;
import com.xsy.scm.report.constant.ScmFinanceAgingAccountTypeEnum;
import com.xsy.scm.report.domain.form.ScmCustomerStatementForm;
import com.xsy.scm.report.domain.form.ScmFinanceAgingQueryForm;
import com.xsy.scm.report.service.ScmCustomerStatementService;
import com.xsy.scm.report.service.ScmFinanceAgingService;
import com.xsy.scm.sorting.domain.form.SortingTaskCreateForm;
import com.xsy.scm.sorting.service.SortingTaskService;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.SqlSession;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderTest {

    @Nested
    @SpringBootTest(classes = XsyApplication.class, properties = {
            "project.log-directory=" + PgITPaths.DEFAULT_LOG_DIR,
            "file.storage.local.upload-path=" + PgITPaths.DEFAULT_UPLOAD_PATH,
            "file.storage.local.url-prefix=http://127.0.0.1:18082",
            "logging.level.root=WARN"})
    @Transactional
    class OrderLastPurchasePriceIT {
        private static final List<String> COMMITTED = ScmPurchaseStatusEnum.committedNames();

        @Autowired
        private JdbcTemplate jdbc;

        @Autowired
        private PurchaseOrderItemDao purchaseOrderItemDao;

        private static final OffsetDateTime EARLY = OffsetDateTime.parse("2026-10-01T08:00:00+08:00");
        private static final OffsetDateTime LATE = OffsetDateTime.parse("2026-10-05T08:00:00+08:00");

        @Test
        void returnsLatestSubmittedLinePerSku() {
            long skuId = 8_831_000_001L;
            long older = order(ScmPurchaseStatusEnum.SUBMITTED, 101L, EARLY, "早供应商");
            item(older, skuId, "3.2000", false);
            long newer = order(ScmPurchaseStatusEnum.RECEIVED, 101L, LATE, "新供应商");
            item(newer, skuId, "3.8000", false);

            var rows = purchaseOrderItemDao.latestPurchasePrices(List.of(skuId), COMMITTED, ScmValueScope.all());

            assertThat(rows).singleElement().satisfies(row -> {
                assertThat(row.getSkuId()).isEqualTo(skuId);
                assertThat(row.getPurchasePrice()).isEqualByComparingTo("3.8000");
                assertThat(row.getPurchaseUnit()).isEqualTo("斤");
                assertThat(row.getSupplierName()).isEqualTo("新供应商");
                assertThat(row.getSubmittedAt().toInstant()).isEqualTo(LATE.toInstant());
            });
        }

        @Test
        void ignoresCancelledAndDeletedRows() {
            long skuId = 8_831_000_002L;
            long cancelled = order(ScmPurchaseStatusEnum.CANCELLED, 101L, EARLY, "取消供应商");
            item(cancelled, skuId, "88.0000", false);
            long committed = order(ScmPurchaseStatusEnum.SUBMITTED, 101L, EARLY, "已提交供应商");
            item(committed, skuId, "5.5000", false);
            // 已提交单上被软删的行仍不能参与参考价
            long deletedOrder = order(ScmPurchaseStatusEnum.RECEIVED, 101L, LATE, "已删行供应商");
            item(deletedOrder, skuId, "66.0000", true);

            var rows = purchaseOrderItemDao.latestPurchasePrices(List.of(skuId), COMMITTED, ScmValueScope.all());

            assertThat(rows).singleElement().satisfies(row -> {
                assertThat(row.getPurchasePrice()).isEqualByComparingTo("5.5000");
                assertThat(row.getSupplierName()).isEqualTo("已提交供应商");
            });
        }

        @Test
        void respectsPurchaserScopeAndFailsClosedWithoutScope() {
            long skuId = 8_831_000_003L;
            long mine = order(ScmPurchaseStatusEnum.SUBMITTED, 201L, EARLY, "我的供应商");
            item(mine, skuId, "4.1000", false);
            long other = order(ScmPurchaseStatusEnum.RECEIVED, 202L, LATE, "别人的供应商");
            item(other, skuId, "4.9000", false);

            var visible = purchaseOrderItemDao.latestPurchasePrices(List.of(skuId), COMMITTED,
                    ScmValueScope.of(List.of(201L)));

            assertThat(visible).singleElement()
                    .satisfies(row -> assertThat(row.getSupplierName()).isEqualTo("我的供应商"));
            assertThat(purchaseOrderItemDao.latestPurchasePrices(List.of(skuId), COMMITTED, null)).isEmpty();
        }

        private long order(ScmPurchaseStatusEnum status, Long purchaserId, OffsetDateTime submittedAt,
                String supplierName) {
            boolean cancelled = status == ScmPurchaseStatusEnum.CANCELLED;
            return jdbc.queryForObject("""
                    INSERT INTO xsy_purchase_order (order_no, supplier_id, supplier_code_snapshot,
                        supplier_name_snapshot, warehouse_id, warehouse_code_snapshot, warehouse_name_snapshot,
                        purchaser_id, status, total_amount, submitted_at, cancelled_at, cancel_reason)
                    VALUES (?, 1, 'SUP', ?, 1, 'WH', '仓库', ?, ?, 0, ?, ?, ?) RETURNING id
                    """, Long.class, "IT-" + UUID.randomUUID(), supplierName, purchaserId, status.name(), submittedAt,
                    cancelled ? EARLY : null, cancelled ? "取消" : null);
        }

        private void item(long orderId, long skuId, String purchasePrice, boolean deleted) {
            jdbc.update("""
                    INSERT INTO xsy_purchase_order_item (purchase_order_id, spu_id, sku_id, spu_code_snapshot,
                        product_name_snapshot, sku_code_snapshot, sku_name_snapshot, purchase_unit_snapshot,
                        product_type_snapshot, planned_quantity, received_quantity, purchase_price, line_amount, deleted)
                    VALUES (?, 1, ?, 'SPU', '商品', 'SKU', '规格', '斤', 'STANDARD', 1, 0, ?, ?, ?)
                    """, orderId, skuId, new BigDecimal(purchasePrice),
                    new BigDecimal(purchasePrice).multiply(BigDecimal.ONE), deleted);
        }
    }

    @Nested
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("未分配订单池并发抢单的收敛（PG IT，无外层事务）")
    class OrderPoolAssignmentConcurrencyPgIT extends ScmW5PgITBase {
        private static final String TEMPORARY_PASSWORD = "Mall@123456";
        private static final String NEW_PASSWORD = "Mall@654321";

        /** 输家可以拿到的两个业务码：抢输给「已有负责人」或「版本已被改」。 */
        private static final int ALREADY_ASSIGNED = 40973;
        private static final int VERSION_CONFLICT = 40921;

        @Autowired
        private OrderPoolService orderPoolService;

        @Autowired
        private MallAccountService mallAccountService;

        @Autowired
        private MallAuthService mallAuthService;

        @Autowired
        private MallCartService mallCartService;

        @Autowired
        private MallAddressService mallAddressService;

        @Autowired
        private MallCheckoutService mallCheckoutService;

        // 仅隔离高德公网依赖；商城地址校验、订单与库存仍访问真实服务和 PostgreSQL。
        @MockitoBean
        private MallMapService mapService;

        @BeforeEach
        void mockExternalMapProvider() {
            MallMapLocationVO location = new MallMapLocationVO();
            location.setLongitude(new BigDecimal("121.544000"));
            location.setLatitude(new BigDecimal("31.221000"));
            location.setProvinceCode(310000);
            location.setCityCode(310100);
            location.setDistrictCode(310115);
            location.setProvinceCode(310000);
            location.setCityCode(310100);
            location.setDistrictCode(310115);
            location.setProvinceName("上海市");
            location.setCityName("上海市");
            location.setDistrictName("浦东新区");
            when(mapService.reverse(any(BigDecimal.class), any(BigDecimal.class))).thenReturn(location);
            bindMockedDistrictToSeedWarehouse();
        }

        /**
         * 商城按收货地址区县解析配送仓；高德 reverse 已被 mock 成 310115，
         * 这里把该区县指到种子仓，保证地址→仓库这一步有确定的落点。
         */
        private void bindMockedDistrictToSeedWarehouse() {
            jdbc.update("INSERT INTO xsy_mall_district_warehouse (district_code, warehouse_id, priority, deleted,"
                            + " created_by, updated_by) VALUES (?, ?, 0, FALSE, '1:1', '1:1')"
                            + " ON CONFLICT (district_code, warehouse_id) WHERE deleted = FALSE DO NOTHING",
                    310115, seedWarehouseId());
        }


        private int customerSequence;

        @Override
        protected void evictMybatisCache() {
            // 无外层事务 → 每次 DAO 调用都是新 session → 一级缓存天然为空
        }

        // ------------------------------------------------------------------
        // 并发领取：两名主管同时点「领取」
        // ------------------------------------------------------------------

        @Test
        @DisplayName("两名主管同时领取同一张订单：恰好一次指派、一条审计，失败侧是业务码而不是死锁")
        void concurrentClaimsConvergeToASingleAssignment() throws Exception {
            PoolOrder order = unassignedMallOrder("claim");

            var firstFailure = new AtomicReference<Throwable>();
            var secondFailure = new AtomicReference<Throwable>();
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var done = new CountDownLatch(2);

            run("claim-a", () -> orderPoolService.claim(claimForm(order), key("claim-a")),
                    firstFailure, ready, start, done);
            run("claim-b", () -> orderPoolService.claim(claimForm(order), key("claim-b")),
                    secondFailure, ready, start, done);

            awaitConvergence(ready, start, done);

            assertExactlyOneWinner(firstFailure, secondFailure);
            assertSingleAssignment(order, null);
        }

        // ------------------------------------------------------------------
        // 并发指派：两名主管各指给不同的业务员
        // ------------------------------------------------------------------

        @Test
        @DisplayName("两名主管同时把同一张订单指给不同业务员：只有一个人被写进负责人，审计不重复")
        void concurrentAssignmentsToDifferentSellersConvergeToOneOwner() throws Exception {
            PoolOrder order = unassignedMallOrder("assign");
            Long firstTarget = newAssignableEmployee("t1");
            Long secondTarget = newAssignableEmployee("t2");

            var firstFailure = new AtomicReference<Throwable>();
            var secondFailure = new AtomicReference<Throwable>();
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var done = new CountDownLatch(2);

            run("assign-a", () -> orderPoolService.assign(assignForm(order, firstTarget), key("assign-a")),
                    firstFailure, ready, start, done);
            run("assign-b", () -> orderPoolService.assign(assignForm(order, secondTarget), key("assign-b")),
                    secondFailure, ready, start, done);

            awaitConvergence(ready, start, done);

            assertExactlyOneWinner(firstFailure, secondFailure);
            assertSingleAssignment(order, List.of(firstTarget, secondTarget));
        }

        // ------------------------------------------------------------------
        // 同键并发重发：同一个人手抖点了两次
        // ------------------------------------------------------------------

        @Test
        @DisplayName("同一防重复请求编号并发重发：两侧都拿到同一张单，库里只有一次指派和一条审计")
        void concurrentRetryWithTheSameIdempotencyKeyReplaysInsteadOfAssigningTwice() throws Exception {
            PoolOrder order = unassignedMallOrder("retry");
            String sharedKey = key("shared");

            var firstFailure = new AtomicReference<Throwable>();
            var secondFailure = new AtomicReference<Throwable>();
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var done = new CountDownLatch(2);

            run("retry-a", () -> orderPoolService.claim(claimForm(order), sharedKey),
                    firstFailure, ready, start, done);
            run("retry-b", () -> orderPoolService.claim(claimForm(order), sharedKey),
                    secondFailure, ready, start, done);

            awaitConvergence(ready, start, done);

            // 同键重发是「同一个请求」，两侧都不该失败：一侧真正执行，另一侧重放已存结果
            assertThat(firstFailure.get()).as("同键并发重发不得失败").isNull();
            assertThat(secondFailure.get()).as("同键并发重发不得失败").isNull();
            assertSingleAssignment(order, null);
        }

        // ------------------------------------------------------------------
        // 断言辅助
        // ------------------------------------------------------------------

        private void awaitConvergence(CountDownLatch ready, CountDownLatch start, CountDownLatch done) throws Exception {
            assertThat(ready.await(30, TimeUnit.SECONDS)).as("两个线程都要到达起跑线").isTrue();
            start.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS))
                    .as("两个事务必须在 60 秒内结束（否则疑似死锁或长等待）").isTrue();
        }

        private void assertExactlyOneWinner(AtomicReference<Throwable> first, AtomicReference<Throwable> second) {
            boolean firstFailed = first.get() != null;
            boolean secondFailed = second.get() != null;
            assertThat(firstFailed ^ secondFailed)
                    .as("一侧成功、一侧被条件更新拒绝，不得双双成功或双双失败")
                    .isTrue();

            Throwable loser = firstFailed ? first.get() : second.get();
            assertThat(loser)
                    .as("失败必须是可读的业务异常，不能把死锁 / 超时 / 裸约束异常抛到接口")
                    .isInstanceOf(ScmBusinessException.class)
                    .isNotInstanceOf(DataAccessException.class);
            assertThat(((ScmBusinessException) loser).getErrorCode().getCode())
                    .as("输家只能是「已被指派」或「版本冲突」")
                    .isIn(ALREADY_ASSIGNED, VERSION_CONFLICT);
        }

        /**
         * 最终记录：负责人唯一、版本只前进一次、指派审计只有一条。
         *
         * @param expectedOwner 期望的负责人；传 {@code null} 表示只断言「恰好有一个人」
         */
        private void assertSingleAssignment(PoolOrder order, List<Long> expectedOwner) {
            Long owner = jdbc.queryForObject("SELECT seller_id FROM xsy_sales_order WHERE id = ?",
                    Long.class, order.orderId());
            assertThat(owner).as("订单最终必须恰好有一个负责人").isNotNull();
            if (expectedOwner != null) {
                assertThat(owner).as("负责人必须是两个候选之一").isIn(expectedOwner);
            }
            assertThat(jdbc.queryForObject("SELECT version FROM xsy_sales_order WHERE id = ?",
                    Integer.class, order.orderId()))
                    .as("条件更新只成功一次，版本就只前进一次").isEqualTo(order.version() + 1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_order_operation_log WHERE order_id = ? AND operation_type = 'ASSIGN'",
                    Integer.class, order.orderId()))
                    .as("指派审计与业务变更同事务，因此只能有一条").isEqualTo(1);
        }

        // ------------------------------------------------------------------
        // 夹具
        // ------------------------------------------------------------------

        private record PoolOrder(Long orderId, Integer version) {
        }

        /**
         * 走真实商城链路造一张「未分配」的商城订单：客户没有业务员，订单 seller_id 跟着为空，
         * 状态停在 PENDING（非标品要等后台录实重才确认），正是订单池的准入形态。
         */
        private PoolOrder unassignedMallOrder(String tag) {
            Long skuId = newOnShelfSku("pool-" + tag);
            Long customerId = newCustomerWithoutSeller();
            MallAccount account = openMallAccount(customerId);
            RequestMallCustomer customer = signIn(account);
            Long addressId = mallAddressService.create(customer, mallAddress()).getFirst().getAddressId();

            XsyRequestUtil.setRequestUser(customer);
            mallCartService.add(customer, cartAdd(skuId, "1.0000"));
            MallCheckoutPreviewVO preview = mallCheckoutService.preview(customer, previewForm(skuId, addressId));
            var submitted = mallCheckoutService.submit(customer,
                    submitForm(skuId, addressId, preview.getPriceFingerprint()), prefix + ":mall:" + tag);

            Long sellerId = jdbc.queryForObject("SELECT seller_id FROM xsy_sales_order WHERE id = ?",
                    Long.class, submitted.getOrderId());
            assertThat(sellerId).as("夹具前提：客户未分配业务员，订单也必须未分配").isNull();
            assertThat(jdbc.queryForObject("SELECT status FROM xsy_sales_order WHERE id = ?",
                    String.class, submitted.getOrderId()))
                    .as("夹具前提：非标品商城单停在待确认").isEqualTo("PENDING");

            Integer version = jdbc.queryForObject("SELECT version FROM xsy_sales_order WHERE id = ?",
                    Integer.class, submitted.getOrderId());
            return new PoolOrder(submitted.getOrderId(), version);
        }

        /**
         * 造一个未分配业务员的客户。
         *
         * <p>借 {@code CUSTOMER_ASSIGN_PERM}：{@code CustomerService.resolveSellerOnCreate} 在没有分配权时
         * 会把业务员落成「当前登录人」，造不出未分配客户。分配权是真实权限，这里只是以具备该权限的身份造数。
         */
        private Long newCustomerWithoutSeller() {
            CustomerAddForm form = new CustomerAddForm();
            form.setName(prefix + "-P" + (++customerSequence) + "客户");
            form.setSettleMode("INDEPENDENT");
            form.setCodAllowed(true);
            form.setSellerId(null);
            Long customerId;
            try (MockedStatic<StpUtil> ignored = mockStatic(StpUtil.class)) {
                when(StpUtil.hasPermission(ScmDataScopeService.CUSTOMER_ASSIGN_PERM)).thenReturn(true);
                customerId = customerService.add(form);
            }
            CustomerStatusForm status = new CustomerStatusForm();
            status.setCustomerId(customerId);
            status.setVersion(0);
            status.setStatus("COOPERATING");
            customerService.updateStatus(status);
            return customerId;
        }

        /**
         * 造一个可作为指派目标的员工。
         *
         * <p>{@code administratorFlag = TRUE} 让 {@code OrderPoolService.canReceiveOrder} 直接放行：
         * 本用例的被测对象是并发收敛，不是「目标业务员有没有订单查看权」（那条判定由单测覆盖）。
         */
        private Long newAssignableEmployee(String suffix) {
            String login = (prefix + "-" + suffix).toUpperCase(Locale.ROOT);
            jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                            + " administrator_flag, disabled_flag, deleted_flag)"
                            + " VALUES (?, ?, ?, ?, 1, TRUE, FALSE, FALSE)",
                    UUID.randomUUID().toString().replace("-", ""), login, "$argon2id$it-placeholder", "指派目标" + suffix);
            return jdbc.queryForObject("SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, login);
        }

        private MallAccount openMallAccount(Long customerId) {
            String phone = newPhone();
            MallAccountCreateForm form = new MallAccountCreateForm();
            form.setCustomerId(customerId);
            form.setLoginPhone(phone);
            form.setTemporaryPassword(TEMPORARY_PASSWORD);
            MallAccountVO account = mallAccountService.create(form);
            return new MallAccount(account.getAccountId(), phone);
        }

        /**
         * 走真实登录动作并完成首次改密，返回该客户的请求身份。
         *
         * <p>改密不是可省略的仪式：商城接口有「临时密码未改」的前置守卫，跳过它就等于跳过了那道守卫。
         */
        private RequestMallCustomer signIn(MallAccount account) {
            MallLoginForm login = new MallLoginForm();
            login.setPhone(account.phone());
            login.setPassword(TEMPORARY_PASSWORD);
            try (MockedStatic<StpUtil> ignored = mockStatic(StpUtil.class)) {
                mallAuthService.login(login, "127.0.0.1");
                MallPasswordChangeForm change = new MallPasswordChangeForm();
                change.setOldPassword(TEMPORARY_PASSWORD);
                change.setNewPassword(NEW_PASSWORD);
                mallAuthService.changePassword(requestCustomer(account, 0), change);
                login.setPassword(NEW_PASSWORD);
                mallAuthService.login(login, "127.0.0.1");
            }
            return requestCustomer(account, 1);
        }

        private RequestMallCustomer requestCustomer(MallAccount account, int version) {
            RequestMallCustomer customer = mallAuthService.requestCustomer(
                    MallAuthService.loginId(account.accountId(), version), new MockHttpServletRequest());
            assertThat(customer).as("登录后必须能解析出商城客户身份").isNotNull();
            return customer;
        }

        /**
         * 以「全量范围的操作者」身份执行并发动作。
         *
         * <p>线程内必须重新设置请求身份：{@code XsyRequestUtil} 是 ThreadLocal，
         * 而订单池的权限闸门与操作人都从它取。
         */
        private void run(String name, Runnable body, AtomicReference<Throwable> failure,
                         CountDownLatch ready, CountDownLatch start, CountDownLatch done) {
            new Thread(() -> {
                asSupervisor(name);
                try {
                    ready.countDown();
                    start.await();
                    body.run();
                } catch (Throwable t) {
                    failure.set(t);
                } finally {
                    XsyRequestUtil.remove();
                    done.countDown();
                }
            }, name).start();
        }

        private void asSupervisor(String name) {
            RequestEmployee employee = new RequestEmployee();
            employee.setEmployeeId(1L);
            employee.setActualName(name);
            employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            employee.setAdministratorFlag(true);
            XsyRequestUtil.setRequestUser(employee);
        }

        private OrderPoolClaimForm claimForm(PoolOrder order) {
            OrderPoolClaimForm form = new OrderPoolClaimForm();
            form.setOrderId(order.orderId());
            form.setVersion(order.version());
            return form;
        }

        private OrderPoolAssignForm assignForm(PoolOrder order, Long sellerId) {
            OrderPoolAssignForm form = new OrderPoolAssignForm();
            form.setOrderId(order.orderId());
            form.setVersion(order.version());
            form.setSellerId(sellerId);
            return form;
        }

        private String key(String tag) {
            return prefix + ":" + tag + ":" + UUID.randomUUID();
        }

        private static MallCartAddForm cartAdd(Long skuId, String quantity) {
            MallCartAddForm form = new MallCartAddForm();
            form.setSkuId(skuId);
            form.setQuantity(quantity);
            return form;
        }

        private static MallAddressSaveForm mallAddress() {
            MallAddressSaveForm form = new MallAddressSaveForm();
            form.setReceiverName("订单池收货人");
            form.setReceiverPhone("13800000000");
            form.setProvinceCode(310000);
            form.setCityCode(310100);
            form.setDistrictCode(310115);
            form.setProvinceName("上海市");
            form.setCityName("上海市");
            form.setDistrictName("浦东新区");
            form.setDetailAddress("张江路 2 号");
            form.setLongitude(new BigDecimal("121.544000"));
            form.setLatitude(new BigDecimal("31.221000"));
            form.setIsDefault(true);
            return form;
        }

        private static MallCheckoutPreviewForm previewForm(Long skuId, Long addressId) {
            MallCheckoutPreviewForm form = new MallCheckoutPreviewForm();
            form.setItems(List.of(checkoutItem(skuId, "1.0000")));
            form.setAddressId(addressId);
            return form;
        }

        private MallOrderSubmitForm submitForm(Long skuId, Long addressId, String priceFingerprint) {
            MallOrderSubmitForm form = new MallOrderSubmitForm();
            form.setItems(List.of(checkoutItem(skuId, "1.0000")));
            form.setAddressId(addressId);
            form.setPriceFingerprint(priceFingerprint);
            form.setRemark("订单池并发 IT");
            form.setSettlementMethod("COD");
            // 商城下单必须给出本次使用的配送仓；夹具统一用本类入库的那个仓。
            form.setWarehouseId(seedWarehouseId());
            return form;
        }

        private static MallCheckoutItemForm checkoutItem(Long skuId, String quantity) {
            MallCheckoutItemForm item = new MallCheckoutItemForm();
            item.setSkuId(skuId);
            item.setQuantity(quantity);
            return item;
        }

        private static String newPhone() {
            long suffix = Math.abs(UUID.randomUUID().getMostSignificantBits() % 900_000_000L);
            return "13" + (100_000_000L + suffix);
        }

        private record MallAccount(Long accountId, String phone) {
        }
    }

    @Nested
    class OrderUnitTest {
        @Nested
        @DisplayName("订单聚合出口权限交集：订单查看权 AND 定价/客户查看权")
        class OrderAggregatePermissionTest {
            private static final String ORDER_QUERY = "scm:order:query";
            private static final String PRICING_RESOLVE_QUERY = "scm:pricing:resolve:query";
            private static final String CUSTOMER_QUERY = "scm:customer:query";

            private SaCheckPermission annotation(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
                Method method = SalesOrderController.class.getMethod(methodName, parameterTypes);
                SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
                assertThat(permission).as("%s 必须声明 @SaCheckPermission", methodName).isNotNull();
                return permission;
            }

            @Test
            @DisplayName("价格预览要求订单查看权 AND 定价解析查看权，且模式为 AND")
            void pricePreviewRequiresBothPermissions() throws NoSuchMethodException {
                SaCheckPermission permission = annotation("preview", PriceResolveForm.class);
                assertThat(List.of(permission.value())).containsExactlyInAnyOrder(ORDER_QUERY, PRICING_RESOLVE_QUERY);
                assertThat(permission.mode()).isEqualTo(SaMode.AND);
            }

            @Test
            @DisplayName("最近已确认订单价要求订单查看权 AND 客户查看权，与 frequent-skus 同规则")
            void recentPricesRequiresBothPermissions() throws NoSuchMethodException {
                SaCheckPermission permission = annotation("recentPrices", Long.class, Long.class, int.class);
                assertThat(List.of(permission.value())).containsExactlyInAnyOrder(ORDER_QUERY, CUSTOMER_QUERY);
                assertThat(permission.mode()).isEqualTo(SaMode.AND);
            }

            @Test
            @DisplayName("定价解析接口自身的权限未被下调，订单列表权限未被扩宽")
            void neighbouringPermissionsUnchanged() throws NoSuchMethodException {
                Method orderQuery = SalesOrderController.class.getMethod("query",
                        com.xsy.scm.order.domain.form.SalesOrderQueryForm.class);
                assertThat(List.of(orderQuery.getAnnotation(SaCheckPermission.class).value())).containsExactly(ORDER_QUERY);
            }
        }

        @Nested

        class OrderBundleReservationPlannerTest {
            @Test
            void usesFrozenComponentSkuQuantityUnitAndSourceInsteadOfBundleSku() {
                var parent = parent("2.0000");
                var child = child("6.0000", "3.0000", "10.0000");
                var lines = OrderBundleReservationPlanner.lines(List.of(child), List.of(parent));

                assertThat(lines).hasSize(1);
                var line = lines.getFirst();
                assertThat(line.itemId()).isEqualTo(500L);
                assertThat(line.skuId()).isEqualTo(300L);
                assertThat(line.quantity()).isEqualByComparingTo("6.0000");
                assertThat(line.stockUnit()).isEqualTo("kg");
                assertThat(line.factor()).isEqualByComparingTo("10.0000");
                assertThat(line.sourceType())
                        .isEqualTo(ScmInventorySourceDocumentTypeEnum.ORDER_BUNDLE_COMPONENT.name());
            }

            @Test
            void noBundleComponentsAddsNoSeparateReservation() {
                assertThat(OrderBundleReservationPlanner.lines(List.of(), List.of(parent("1")))).isEmpty();
            }

            @Test
            void tamperedFrozenQuantityMustNotReserveOrRecomputeSilently() {
                assertThatThrownBy(() -> OrderBundleReservationPlanner.lines(
                        List.of(child("5.0000", "3.0000", "10.0000")), List.of(parent("2.0000"))))
                        .isInstanceOf(ScmBusinessException.class);
            }

            @Test
            void componentStockConversionMustStayExact() {
                assertThatThrownBy(() -> OrderBundleReservationPlanner.lines(
                        List.of(child("0.3333", "0.3333", "0.3333")), List.of(parent("1.0000"))))
                        .isInstanceOf(ScmBusinessException.class);
            }

            private static SalesOrderItemEntity parent(String quantity) {
                var row = new SalesOrderItemEntity();
                row.setId(200L);
                row.setSkuId(100L);
                row.setActualQuantity(new BigDecimal(quantity));
                return row;
            }

            private static OrderBundleComponentEntity child(String frozenQuantity, String perBundle, String factor) {
                var row = new OrderBundleComponentEntity();
                row.setId(500L);
                row.setOrderItemId(200L);
                row.setBundleSkuId(100L);
                row.setComponentSkuId(300L);
                row.setComponentSaleQuantitySnapshot(new BigDecimal(frozenQuantity));
                row.setQuantityPerBundleSaleUnit(new BigDecimal(perBundle));
                row.setComponentStockUnitSnapshot("kg");
                row.setStockUnitsPerComponentSaleUnit(new BigDecimal(factor));
                return row;
            }
        }

        @Nested

        class OrderBundleSnapshotServiceTest {
            private final ProductBundleComponentQueryService componentDao = mock(ProductBundleComponentQueryService.class);
            private final OrderBundleComponentDao snapshotDao = mock(OrderBundleComponentDao.class);
            private final ProductSkuOptionQueryService skuDao = mock(ProductSkuOptionQueryService.class);
            private final MallStockAvailabilityService stock = mock(MallStockAvailabilityService.class);
            private final OrderBundleSnapshotService service =
                    new OrderBundleSnapshotService(componentDao, snapshotDao, skuDao, stock);

            private final SalesOrderItemEntity orderItem = new SalesOrderItemEntity();

            @BeforeEach
            void prepare() {
                var employee = new RequestEmployee();
                employee.setEmployeeId(1L);
                employee.setActualName("Test");
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                XsyRequestUtil.setRequestUser(employee);

                orderItem.setOrderId(50L);
                orderItem.setId(51L);
                orderItem.setSkuId(200L);
                orderItem.setActualQuantity(new BigDecimal("1.0000"));
                orderItem.setLockedUnitPrice(new BigDecimal("80.0000"));
                when(componentDao.listByBundleSku(200L)).thenReturn(List.of(
                        component(11L, 301L, "2.0000"),
                        component(12L, 302L, "3.0000")));
            }

            @AfterEach
            void clear() {
                XsyRequestUtil.remove();
            }

            @Test
            void freezesMarketPriceShareAndChosenWarehouseForEveryComponent() {
                when(skuDao.byIds(List.of(301L, 302L))).thenReturn(List.of(
                        sku(301L, "10.0000"), sku(302L, "10.0000")));
                when(stock.requireConversion(42L, 301L, "kg"))
                        .thenReturn(new MallStockAvailabilityService.Conversion("kg", BigDecimal.ONE));
                when(stock.requireConversion(42L, 302L, "kg"))
                        .thenReturn(new MallStockAvailabilityService.Conversion("kg", BigDecimal.ONE));

                assertThat(service.freeze(orderItem, 42L)).isTrue();

                ArgumentCaptor<OrderBundleComponentEntity> captured =
                        ArgumentCaptor.forClass(OrderBundleComponentEntity.class);
                verify(snapshotDao, times(2)).insert(captured.capture());
                var rows = captured.getAllValues();
                assertThat(rows.get(0).getWarehouseId()).isEqualTo(42L);
                assertThat(rows.get(0).getComponentSaleQuantitySnapshot()).isEqualByComparingTo("2.0000");
                assertThat(rows.get(0).getMarketUnitPriceSnapshot()).isEqualByComparingTo("10.0000");
                assertThat(rows.get(0).getMarketReferenceAmountSnapshot()).isEqualByComparingTo("20.00000000");
                assertThat(rows.get(0).getAllocatedSaleAmountSnapshot()).isEqualByComparingTo("32.0000");
                assertThat(rows.get(1).getMarketReferenceAmountSnapshot()).isEqualByComparingTo("30.00000000");
                assertThat(rows.get(1).getAllocatedSaleAmountSnapshot()).isEqualByComparingTo("48.0000");
                assertThat(rows.get(0).getAllocatedSaleAmountSnapshot()
                        .add(rows.get(1).getAllocatedSaleAmountSnapshot())).isEqualByComparingTo("80.0000");
            }

            @Test
            void bundleCannotUseImplicitDefaultWarehouse() {
                assertThatThrownBy(() -> service.freeze(orderItem, null))
                        .isInstanceOf(ScmBusinessException.class);
                verifyNoInteractions(skuDao, snapshotDao, stock);
            }

            @Test
            void missingComponentMarketReferenceMustFailWithoutWritingSnapshot() {
                when(skuDao.byIds(List.of(301L, 302L))).thenReturn(List.of(
                        sku(301L, "10.0000"), sku(302L, null)));
                when(stock.requireConversion(42L, 301L, "kg"))
                        .thenReturn(new MallStockAvailabilityService.Conversion("kg", BigDecimal.ONE));

                assertThatThrownBy(() -> service.freeze(orderItem, 42L))
                        .isInstanceOf(ScmBusinessException.class);
                verifyNoInteractions(snapshotDao);
            }

            private static ProductBundleComponentEntity component(Long id, Long skuId, String quantity) {
                var row = new ProductBundleComponentEntity();
                row.setId(id);
                row.setVersion(1);
                row.setBundleSkuId(200L);
                row.setComponentSkuId(skuId);
                row.setQuantityPerBundleSaleUnit(new BigDecimal(quantity));
                return row;
            }

            private static ProductSkuOptionVO sku(Long skuId, String price) {
                var row = new ProductSkuOptionVO();
                row.setSkuId(skuId);
                row.setSkuCode("S-" + skuId);
                row.setProductName("组成商品" + skuId);
                row.setSaleUnit("kg");
                row.setStatus("ON_SHELF");
                row.setSpuStatus("ON_SHELF");
                row.setCategoryStatus("ENABLED");
                row.setMarketPrice(price == null ? null : new BigDecimal(price));
                return row;
            }
        }

        @Nested

        class OrderCreditServiceTest {
            private final CustomerService customers = mock(CustomerService.class);
            private final FinanceCreditExposureQueryService facts = mock(FinanceCreditExposureQueryService.class);
            private final ScmDataScopeService scopes = mock(ScmDataScopeService.class);
            private final CustomerEntity settlement = new CustomerEntity();
            private final FinanceCreditExposureDto exposure = new FinanceCreditExposureDto();
            private final OrderCreditService service = new OrderCreditService(customers, facts, scopes);

            @BeforeEach void setUp() {
                var context = mock(ScmDataScopeContext.class);
                when(scopes.resolve()).thenReturn(context);
                when(context.getOrderSellerScope()).thenReturn(ScmValueScope.all());
                settlement.setId(9L);
                settlement.setCreditLimit(new BigDecimal("100"));
                when(customers.require(9L)).thenReturn(settlement);
                when(customers.lockCreditAccount(9L)).thenReturn(settlement);
                exposure.setOpenReceivableAmount(new BigDecimal("60"));
                exposure.setConfirmedOrderAmount(new BigDecimal("30"));
                exposure.setExposureVisible(true);
                when(facts.summarize(9L, ScmValueScope.all())).thenReturn(exposure);
            }

            @Test void explainsProjectedExposureAndOverdue() {
                exposure.setEarliestOverdueDate(LocalDate.of(2026,1,1));
                var result = service.checkSettlement(9L,new BigDecimal("20"));
                assertThat(result.getProjectedExposure()).isEqualByComparingTo("110");
                assertThat(result.getOverLimit()).isTrue();
                assertThat(result.getOverdue()).isTrue();
                assertThat(result.getAllowed()).isFalse();
            }

            @Test void zeroLimitAllowsAmountButNeverIgnoresOverdue() {
                settlement.setCreditLimit(BigDecimal.ZERO);
                assertThat(service.checkForSettlementConfirmation(9L, new BigDecimal("1000")).getAllowed()).isTrue();
                exposure.setEarliestOverdueDate(LocalDate.of(2026,1,1));
                var blocked = service.checkForSettlementConfirmation(9L,new BigDecimal("1000"));
                assertThat(blocked.getOverLimit()).isFalse();
                assertThat(blocked.getAllowed()).isFalse();
            }

            @Test void hiddenGroupOrdersCannotExposeAccountTotals() {
                exposure.setExposureVisible(false);
                assertThatThrownBy(() -> service.checkSettlement(9L,BigDecimal.ZERO)).isInstanceOf(ScmDataScopeException.class);
            }

            @Test void internalConfirmationStillAccountsForHiddenGroupExposure() {
                exposure.setExposureVisible(false);
                assertThat(service.checkForSettlementConfirmation(9L,new BigDecimal("20")).getAllowed()).isFalse();
            }

            @Test void negativeRequestedAmountCannotReduceExposure() {
                assertThatThrownBy(() -> service.checkSettlement(9L,BigDecimal.ONE.negate())).isInstanceOf(ScmBusinessException.class);
            }

            @Test void confirmationLocksAuthoritativeAccountBeforeRecalculatingExposure() {
                service.checkForSettlementConfirmation(9L, new BigDecimal("10"));
                var ordered = org.mockito.Mockito.inOrder(customers, facts);
                ordered.verify(customers).lockCreditAccount(9L);
                ordered.verify(facts).summarize(9L, ScmValueScope.all());
            }
        }

        @Nested

        class OrderExceptionCompensationPermissionTest {
            @Test
            void replacementOrderMustRespectOriginalReturnVisibilityAndOrderCreationRights() throws Exception {
                var read = OrderExchangeController.class.getMethod("replacement", Long.class)
                        .getAnnotation(SaCheckPermission.class);
                var create = OrderExchangeController.class.getMethod("create", OrderExchangeCreateForm.class, String.class)
                        .getAnnotation(SaCheckPermission.class);
                assertThat(read.value()).containsExactly(OrderPermission.RETURN_QUERY);
                assertThat(create.value()).containsExactly(OrderPermission.ADD, OrderPermission.RETURN_QUERY);
                assertThat(create.mode()).isEqualTo(SaMode.AND);
            }

            @Test
            void customerReceivedCorrectionRequiresQueryAndUpdatePermissions() throws Exception {
                var read = OrderReceivedCorrectionController.class.getMethod("candidate", Long.class, Long.class)
                        .getAnnotation(SaCheckPermission.class);
                var create = OrderReceivedCorrectionController.class
                        .getMethod("record", OrderReceivedCorrectionForm.class, String.class)
                        .getAnnotation(SaCheckPermission.class);
                assertThat(read.value()).containsExactly(OrderPermission.QUERY);
                assertThat(create.value()).containsExactly(OrderPermission.QUERY, OrderPermission.UPDATE);
                assertThat(create.mode()).isEqualTo(SaMode.AND);
            }
        }

        @Nested

        class OrderOnlySettlementPolicyTest {
            private final OrderSettlementConfirmationPolicy policy = new OrderSettlementConfirmationPolicy(
                mock(CustomerService.class), mock(OrderCreditService.class), mock(FinanceOrderPayableQueryService.class));

            @Test
            void newOrderRequiresExplicitSupportedSettlement() {
                SalesOrderEntity order = new SalesOrderEntity();
                order.setMallOrderOnly(true);
                assertThatThrownBy(() -> policy.prepare(order, null))
                    .isInstanceOfSatisfying(ScmBusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(OrderErrorCode.ORDER_SETTLEMENT_METHOD_REQUIRED));
                assertThatThrownBy(() -> policy.prepare(order, "UNKNOWN")).isInstanceOf(ScmBusinessException.class);
                assertThat(order.getSettlementMethod()).isNull();
                policy.prepare(order, "COD");
                assertThat(order.getSettlementMethod()).isEqualTo("COD");
            }

            @Test
            void existingSettlementCannotBeChanged() {
                SalesOrderEntity order = new SalesOrderEntity();
                order.setMallOrderOnly(true);
                order.setSettlementMethod("WALLET");
                assertThatThrownBy(() -> policy.prepare(order, "COD")).isInstanceOf(ScmBusinessException.class);
                assertThat(order.getSettlementMethod()).isEqualTo("WALLET");
                policy.prepare(order, null);
                policy.prepare(order, "WALLET");
                assertThat(order.getSettlementMethod()).isEqualTo("WALLET");
            }

            @Test
            void legacyNullMethodKeepsOriginalConfirmationPath() {
                SalesOrderEntity order = new SalesOrderEntity();
                policy.prepare(order, null);
                assertThat(order.getSettlementMethod()).isNull();
                assertThatThrownBy(() -> policy.prepare(order, "COD")).isInstanceOf(ScmBusinessException.class);
            }
        }

        @Nested

        class OrderRulesTest {
            @ParameterizedTest
            @CsvSource({"PENDING,PENDING,false", "PENDING,CONFIRMED,true", "PENDING,CANCELLED,true", "CONFIRMED,PENDING,false", "CONFIRMED,CONFIRMED,false", "CONFIRMED,CANCELLED,false", "CANCELLED,PENDING,false", "CANCELLED,CONFIRMED,false", "CANCELLED,CANCELLED,false"})
            void stateGraph(String from, String to, boolean allowed) {
                assertThat(OrderStateMachine.canTransition(from, to)).isEqualTo(allowed);
            }

            @Test
            void nullPropagatesButFreePricesAndHalfUpRemainValid() {
                assertThat(OrderAmountCalculator.lineAmount(new BigDecimal("3.0000"), null)).isNull();
                assertThat(OrderAmountCalculator.orderAmount(Arrays.asList(BigDecimal.ONE, null))).isNull();
                assertThat(OrderAmountCalculator.lineAmount(new BigDecimal("1.0005"), new BigDecimal("0.1000"))).isEqualByComparingTo("0.1001");
                assertThat(OrderAmountCalculator.lineAmount(new BigDecimal("3.0000"), BigDecimal.ZERO)).isEqualByComparingTo("0.0000");
                assertThatThrownBy(() -> OrderAmountCalculator.lineAmount(new BigDecimal("99999999999999.9999"), BigDecimal.TEN)).isInstanceOf(ScmBusinessException.class);
            }

            @ParameterizedTest
            @ValueSource(strings = {"1.00000", "-1.0000", "1e4", "100000000000000.0000", "0.0000", ""})
            void rejectsInvalidQuantity(String value) {
                assertThatThrownBy(() -> OrderValidator.decimal(value, true)).isInstanceOf(ScmBusinessException.class);
            }

            @ParameterizedTest
            @ValueSource(strings = {"10", "1.5", "1.5000"})
            void quantityAcceptsUpToFourDecimalsAndNormalizesToScale4(String value) {
                var parsed = OrderValidator.decimal(value, true);
                assertThat(parsed).isEqualByComparingTo(value);
                assertThat(parsed.scale()).isEqualTo(4);
            }

            @Test
            void quantityFormatFailureIsDistinctFromNonPositive() {
                assertThat(errorCode("1e4")).isEqualTo(40076);
                assertThat(errorCode("0.0000")).isEqualTo(40063);
                assertThat(errorCode("-1")).isEqualTo(40076);
            }

            private static int errorCode(String quantity) {
                try {
                    OrderValidator.decimal(quantity, true);
                    throw new AssertionError("Expected a rejection for " + quantity);
                } catch (ScmBusinessException e) {
                    return e.getErrorCode().getCode();
                }
            }

            @Test
            void diffKeepsIdentityAndRejectsStaleOrForeignItems() {
                var old = new SalesOrderItemEntity();
                old.setId(7L);
                old.setVersion(2);
                var retained = new SalesOrderItemEntity();
                retained.setId(7L);
                retained.setVersion(2);
                var added = new SalesOrderItemEntity();
                var diff = SalesOrderItemChangeSet.between(List.of(old), List.of(retained, added));
                assertThat(diff.updated()).containsExactly(retained);
                assertThat(diff.inserted()).containsExactly(added);
                retained.setVersion(1);
                assertThatThrownBy(() -> SalesOrderItemChangeSet.between(List.of(old), List.of(retained))).isInstanceOf(ScmBusinessException.class);
                retained.setId(8L);
                assertThatThrownBy(() -> SalesOrderItemChangeSet.between(List.of(old), List.of(retained))).isInstanceOf(ScmBusinessException.class);
                assertThat(SalesOrderItemChangeSet.between(List.of(old), List.of()).removed()).containsExactly(old);
            }

            @Test
            void changingSkuCreatesANewIdentityAndRetiresTheOriginalLine() {
                var old = new SalesOrderItemEntity();
                old.setId(7L);
                old.setSkuId(11L);
                old.setVersion(2);
                var replacement = new SalesOrderItemEntity();
                replacement.setId(7L);
                replacement.setSkuId(12L);
                replacement.setVersion(2);

                var diff = SalesOrderItemChangeSet.between(List.of(old), List.of(replacement));

                assertThat(diff.removed()).containsExactly(old);
                assertThat(diff.updated()).isEmpty();
                assertThat(diff.inserted()).containsExactly(replacement);
                assertThat(replacement.getId()).isNull();
                assertThat(replacement.getVersion()).isZero();
                assertThat(old.getId()).isEqualTo(7L);

                replacement.setId(7L);
                replacement.setVersion(1);
                assertThatThrownBy(() -> SalesOrderItemChangeSet.between(List.of(old), List.of(replacement)))
                        .isInstanceOf(ScmBusinessException.class);
            }

            @Test
            void canonicalHashHandlesNestedKeysAndDecimals() {
                var h = new ScmIdempotencyRequestHasher(new ObjectMapper());
                assertThat(h.hash(Map.of("b", List.of(Map.of("amount", "1.5000")), "a", 2)))
                        .isEqualTo(h.hash(Map.of("a", 2, "b", List.of(Map.of("amount", "1.5")))));
                assertThat(h.hash(Map.of("p", "0.0000"))).isNotEqualTo(h.hash(Collections.singletonMap("p", null)));
            }

            @Test
            void priceEnumHasNoUnpriced() {
                // 枚举顺序就是取价链顺序：逐客协议价 → 客户分组价 → 时价 → 市场价，人工改价最后兜底。
                assertThat(ScmOrderPriceSourceEnum.values()).extracting(Enum::name).containsExactly("AGREEMENT", "CUSTOMER_GROUP",
                        "TIME_PRICE", "MARKET", "LEGACY", "OVERRIDE");
            }

            @Test
            void draftValidationEnforcesSourceDuplicateAndOverrideRules() {
                var f = new SalesOrderAddForm();
                f.setOrderSource("ADMIN");
                var i = new SalesOrderItemForm();
                i.setSkuId(1L);
                i.setOrderedQuantity("1.0000");
                i.setManualPriceOverride(false);
                f.setItems(List.of(i));
                OrderValidator.draft(f);
                f.setItems(List.of(i, i));
                assertThatThrownBy(() -> OrderValidator.draft(f)).isInstanceOf(ScmBusinessException.class);
                f.setItems(List.of(i));
                i.setManualPriceOverride(true);
                i.setUnitPrice("0.0000");
                assertThatThrownBy(() -> OrderValidator.draft(f)).isInstanceOf(ScmBusinessException.class);
                i.setOverrideReason("赠送");
                OrderValidator.draft(f);
                f.setOriginalOrderId(9L);
                assertThatThrownBy(() -> OrderValidator.draft(f)).isInstanceOf(ScmBusinessException.class);
                f.setOrderSource("SUPPLEMENT");
                f.setSupplementReason("补录");
                OrderValidator.draft(f);
                assertThatThrownBy(() -> OrderValidator.reason(" ", OrderErrorCode.ORDER_CANCEL_REASON_REQUIRED)).isInstanceOf(ScmBusinessException.class);
            }

            @Test
            void newCodesDoNotCollideWithExistingDomains() {
                var seen = new HashSet<Integer>();
                for (var c : com.xsy.scm.pricing.constant.PricingErrorCode.values()) seen.add(c.getCode());
                for (var c : com.xsy.scm.customer.constant.CustomerErrorCode.values())
                    seen.add(c.getCode());
                for (var c : com.xsy.scm.common.error.ScmCommonErrorCode.values()) seen.add(c.getCode());
                for (var c : OrderErrorCode.values()) assertThat(seen.add(c.getCode())).as(c.name()).isTrue();
            }
        }

        @Nested

        class OrderStateLifecycleTest {
            @Test
            void onlyDeclaredTransitionsAreAllowedAcrossAllStatuses() {
                var edges = Set.of("PENDING:CONFIRMED", "PENDING:CANCELLED", "CONFIRMED:DISPATCHED",
                    "DISPATCHED:SIGNED", "DISPATCHED:EXCEPTION");
                for (var from : ScmOrderStatusEnum.values()) {
                    for (var to : ScmOrderStatusEnum.values()) {
                        assertThat(OrderStateMachine.canTransition(from.name(), to.name()))
                            .as("%s -> %s", from, to).isEqualTo(edges.contains(from.name() + ":" + to.name()));
                    }
                }
                assertThat(OrderStateMachine.canTransition(null, "CONFIRMED")).isFalse();
                assertThat(OrderStateMachine.canTransition("CONFIRMED", null)).isFalse();
                assertThat(OrderStateMachine.canTransition("UNKNOWN", "DISPATCHED")).isFalse();
                assertThatThrownBy(() -> OrderStateMachine.transition("SIGNED", "CONFIRMED"))
                    .isInstanceOf(ScmBusinessException.class);
            }

            @Test
            void editingAndQuantityEntryRespectDispatchAndTerminalStates() {
                assertThatCode(() -> OrderStateMachine.editable("DISPATCHED")).doesNotThrowAnyException();
                assertThatThrownBy(() -> OrderStateMachine.actualQuantity("DISPATCHED"))
                    .isInstanceOf(ScmBusinessException.class);
                for (var status : Set.of("SIGNED", "EXCEPTION", "CANCELLED")) {
                    assertThatThrownBy(() -> OrderStateMachine.editable(status)).isInstanceOf(ScmBusinessException.class);
                    assertThatThrownBy(() -> OrderStateMachine.actualQuantity(status)).isInstanceOf(ScmBusinessException.class);
                }
            }

            @Test
            void confirmedTransactionReadsKeepAllSubsequentStages() {
                for (var status : ScmOrderStatusEnum.values()) {
                    assertThat(ScmOrderStatusEnum.isConfirmed(status.name()))
                        .isEqualTo(Set.of("CONFIRMED", "DISPATCHED", "SIGNED", "EXCEPTION").contains(status.name()));
                }
                assertThat(ScmOrderStatusEnum.isConfirmed(null)).isFalse();
            }
        }

        @Nested

        class OrderTagPermissionTest {
            @Test
            void tagListingRequiresOrderQueryPermission() throws Exception {
                var guard = OrderTagController.class.getMethod("list").getAnnotation(SaCheckPermission.class);
                assertThat(guard.value()).containsExactly(OrderPermission.QUERY);
            }

            @Test
            void tagChangesRequireBothOrderQueryAndUpdatePermissions() throws Exception {
                var create = OrderTagController.class.getMethod("create", OrderTagCreateForm.class)
                        .getAnnotation(SaCheckPermission.class);
                var apply = OrderTagController.class.getMethod("apply", OrderTagAssignForm.class)
                        .getAnnotation(SaCheckPermission.class);
                assertThat(create.value()).containsExactly(OrderPermission.QUERY, OrderPermission.UPDATE);
                assertThat(apply.value()).containsExactly(OrderPermission.QUERY, OrderPermission.UPDATE);
                assertThat(create.mode()).isEqualTo(SaMode.AND);
                assertThat(apply.mode()).isEqualTo(SaMode.AND);
            }
        }

        @Nested

        class OrderTagScopeTest {
            @Test
            void bulkClearChecksAllOwnersBeforeWritingAnyTag() {
                var dao = mock(OrderTagDao.class);
                var orders = mock(SalesOrderDao.class);
                var scopes = mock(ScmDataScopeService.class);
                var context = mock(ScmDataScopeContext.class);
                when(scopes.resolve()).thenReturn(context);
                when(context.getOrderSellerScope()).thenReturn(ScmValueScope.of(List.of(10L)));
                var own = new SalesOrderEntity();
                own.setSellerId(10L);
                var other = new SalesOrderEntity();
                other.setSellerId(20L);
                when(orders.lock(1L)).thenReturn(own);
                when(orders.lock(2L)).thenReturn(other);
                var form = new OrderTagAssignForm();
                form.setOrderIds(List.of(2L, 1L));
                form.setAction(OrderTagAssignForm.Action.CLEAR);
                try (var operator = mockStatic(ScmOperator.class)) {
                    operator.when(ScmOperator::current).thenReturn("1:1");
                    assertThatThrownBy(() -> new OrderTagService(dao, orders, scopes).apply(form)).isInstanceOf(ScmDataScopeException.class);
                }
                verifyNoInteractions(dao);
            }
        }

        @Nested

        class SalesOrderImportServiceTest {
            private static final String CUSTOMER_NAME = "西安测试客户";

            private final CustomerDao customers = mock(CustomerDao.class);
            private final ProductSkuOptionDao skus = mock(ProductSkuOptionDao.class);
            private final SalesOrderService orders = mock(SalesOrderService.class);
            private final PriceResolver prices = mock(PriceResolver.class);
            private final SalesOrderImportService service = new SalesOrderImportService(customers, skus, orders, prices);

            @BeforeEach
            void catalog() {
                when(customers.selectActiveByNames(anyList())).thenReturn(List.of(customer(1L, CUSTOMER_NAME)));
                when(skus.selectByProductNames(anyList())).thenReturn(List.of(sku(2L, "商品甲", "500g", "斤")));
                var price = new ResolvedPriceVO();
                price.setSkuId(2L);
                price.setSellable(true);
                price.price(new BigDecimal("1.2000"), ScmPriceSourceEnum.MARKET, null);
                when(prices.resolve(eq(1L), anyList(), any())).thenReturn(List.of(price));
                when(orders.importOrders(anyList(), anyString(), any(), anyInt())).thenReturn(written());
            }

            private SalesOrderImportResultVO written() {
                var result = new SalesOrderImportResultVO();
                result.setTotalRows(1);
                result.setTotalOrders(1);
                return result;
            }

            private CustomerEntity customer(Long id, String name) {
                var customer = new CustomerEntity();
                customer.setId(id);
                customer.setName(name);
                customer.setStatus("COOPERATING");
                customer.setContactName("张三");
                customer.setContactPhone("13800000000");
                customer.setAddress("测试地址");
                return customer;
            }

            private ProductSkuOptionVO sku(Long id, String productName, String specName, String saleUnit) {
                var sku = new ProductSkuOptionVO();
                sku.setSkuId(id);
                sku.setSkuCode("S-" + id);
                sku.setProductName(productName);
                sku.setSpecName(specName);
                sku.setSaleUnit(saleUnit);
                sku.setStatus("ON_SHELF");
                sku.setSpuStatus("ON_SHELF");
                sku.setCategoryStatus("ENABLED");
                sku.setMarketPrice(new BigDecimal("1.2000"));
                return sku;
            }

            /** 生成一张最小可用的送货单：第 3 行收货单位与日期、第 4 行表头、第 5 行一条明细、合计与签字栏。 */
            private MockMultipartFile workbook(Consumer<XSSFWorkbook> edit) throws Exception {
                try (var book = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
                    var sheet = book.createSheet("送货单");
                    sheet.createRow(0).createCell(0).setCellValue("鲜蔬源(西安)供应链服务有限公司");
                    sheet.createRow(1).createCell(0).setCellValue("送货单");
                    var meta = sheet.createRow(2);
                    meta.createCell(0).setCellValue("收货单位：");
                    meta.createCell(1).setCellValue(CUSTOMER_NAME);
                    meta.createCell(6).setCellValue("日期：");
                    meta.createCell(7).setCellValue("2026-10-27");
                    var header = sheet.createRow(3);
                    String[] headers = {"序号", "商品名称", "单位", "规格", "实数量", "销售单价", "销售金额", "备注"};
                    for (int column = 0; column < headers.length; column++)
                        header.createCell(column).setCellValue(headers[column]);
                    var line = sheet.createRow(4);
                    line.createCell(0).setCellValue("1");
                    line.createCell(1).setCellValue("商品甲");
                    line.createCell(2).setCellValue("斤");
                    line.createCell(3).setCellValue("500g");
                    line.createCell(4).setCellValue(2);
                    line.createCell(5).setCellValue(1.2);
                    line.createCell(6).setCellValue(2.4);
                    line.createCell(7).setCellValue("");
                    sheet.createRow(5).createCell(0).setCellValue("合计金额");
                    var footer = sheet.createRow(6);
                    footer.createCell(1).setCellValue("销售方：");
                    footer.createCell(7).setCellValue("收货方：");
                    edit.accept(book);
                    book.write(bytes);
                    return new MockMultipartFile("file", "delivery-note.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes.toByteArray());
                }
            }

            private List<SalesOrderAddForm> capturedForms() {
                @SuppressWarnings("unchecked")
                var captor = ArgumentCaptor.forClass(List.class);
                verify(orders).importOrders(captor.capture(), anyString(), any(), anyInt());
                @SuppressWarnings("unchecked")
                var forms = (List<SalesOrderAddForm>) captor.getValue();
                return forms;
            }

            @Test
            void importsOneOrderWithProfileAddressAndImportPrice() throws Exception {
                var result = service.importFile(workbook(book -> {
                }), "key", true);

                assertThat(result.getTotalErrors()).isZero();
                assertThat(result.getTotalOrders()).isOne();
                verify(orders).importOrders(anyList(), anyString(), eq("key"), eq(1));
                var form = capturedForms().getFirst();
                assertThat(form.getCustomerId()).isEqualTo(1L);
                assertThat(form.getOrderSource()).isEqualTo("IMPORT");
                assertThat(form.getExpectDeliveryTime()).isEqualTo(OffsetDateTime.parse("2026-10-27T00:00:00+08:00"));
                assertThat(form.getAddress().getReceiverName()).isEqualTo("张三");
                assertThat(form.getAddress().getReceiverPhone()).isEqualTo("13800000000");
                assertThat(form.getAddress().getAddress()).isEqualTo("测试地址");
                var item = form.getItems().getFirst();
                assertThat(item.getSkuId()).isEqualTo(2L);
                assertThat(item.getOrderedQuantity()).isEqualTo("2.0000");
                assertThat(item.getManualPriceOverride()).isTrue();
                assertThat(item.getUnitPrice()).isEqualTo("1.2000");
                assertThat(item.getOverrideReason()).isEqualTo("客户订单导入");
            }

            @Test
            void customerNameMustMatchAnActiveCustomer() throws Exception {
                when(customers.selectActiveByNames(anyList())).thenReturn(List.of());
                var result = service.importFile(workbook(book -> {
                }), "key", true);
                assertThat(result.getErrors()).extracting("code").contains("CUSTOMER_NOT_FOUND");
                verifyNoInteractions(orders);
            }

            @Test
            void duplicateCustomerNamesAreRejected() throws Exception {
                when(customers.selectActiveByNames(anyList())).thenReturn(
                    List.of(customer(1L, CUSTOMER_NAME), customer(9L, CUSTOMER_NAME)));
                var result = service.importFile(workbook(book -> {
                }), "key", true);
                assertThat(result.getErrors()).extracting("code").contains("CUSTOMER_AMBIGUOUS");
                verifyNoInteractions(orders);
            }

            @Test
            void specAndUnitMustMatchTheSku() throws Exception {
                var wrongSpec = service.importFile(
                    workbook(book -> book.getSheetAt(0).getRow(4).getCell(3).setCellValue("1kg")), "key", true);
                assertThat(wrongSpec.getErrors()).extracting("code").contains("SKU_NOT_FOUND");

                var wrongUnit = service.importFile(
                    workbook(book -> book.getSheetAt(0).getRow(4).getCell(2).setCellValue("kg")), "key", true);
                assertThat(wrongUnit.getErrors()).extracting("code").contains("UNIT_MISMATCH");
                verifyNoInteractions(orders);
            }

            @Test
            void blankSpecRequiresAUniqueSku() throws Exception {
                when(skus.selectByProductNames(anyList())).thenReturn(
                    List.of(sku(2L, "商品甲", "500g", "斤"), sku(3L, "商品甲", "1kg", "斤")));
                var ambiguous = service.importFile(
                    workbook(book -> book.getSheetAt(0).getRow(4).getCell(3).setCellValue("")), "key", true);
                assertThat(ambiguous.getErrors()).extracting("code").contains("SKU_AMBIGUOUS");

                when(skus.selectByProductNames(anyList())).thenReturn(List.of(sku(2L, "商品甲", "500g", "斤")));
                var unique = service.importFile(
                    workbook(book -> book.getSheetAt(0).getRow(4).getCell(3).setCellValue("")), "key", true);
                assertThat(unique.getTotalErrors()).isZero();
            }

            @Test
            void amountMustEqualQuantityTimesPrice() throws Exception {
                var result = service.importFile(
                    workbook(book -> book.getSheetAt(0).getRow(4).getCell(6).setCellValue("3.00")), "key", true);
                assertThat(result.getErrors()).extracting("code").contains("AMOUNT_NOT_MATCH");
                verifyNoInteractions(orders);
            }

            @Test
            void importPriceRequiresOverridePermission() throws Exception {
                var result = service.importFile(workbook(book -> {
                }), "key", false);
                assertThat(result.getErrors()).extracting("code").contains("PRICE_OVERRIDE_FORBIDDEN");
                verifyNoInteractions(orders);
            }

            @Test
            void blankPriceFallsBackToSystemPrice() throws Exception {
                var result = service.importFile(workbook(book -> {
                    var line = book.getSheetAt(0).getRow(4);
                    line.getCell(5).setCellValue("");
                    line.getCell(6).setCellValue("");
                }), "key", false);
                assertThat(result.getTotalErrors()).isZero();
                var item = capturedForms().getFirst().getItems().getFirst();
                assertThat(item.getManualPriceOverride()).isFalse();
                assertThat(item.getUnitPrice()).isNull();
                assertThat(item.getOverrideReason()).isNull();
            }

            @Test
            void unpricedRowWithoutManualPriceIsRejected() throws Exception {
                var price = new ResolvedPriceVO();
                price.setSkuId(2L);
                price.setSellable(true);
                price.price(null, null, null);
                when(prices.resolve(eq(1L), anyList(), any())).thenReturn(List.of(price));
                var result = service.importFile(workbook(book -> {
                    var line = book.getSheetAt(0).getRow(4);
                    line.getCell(5).setCellValue("");
                    line.getCell(6).setCellValue("");
                }), "key", false);
                assertThat(result.getErrors()).extracting("code").contains("UNPRICED");
                verifyNoInteractions(orders);
            }

            @Test
            void missingCustomerAddressBlocksImport() throws Exception {
                var customer = customer(1L, CUSTOMER_NAME);
                customer.setAddress(null);
                when(customers.selectActiveByNames(anyList())).thenReturn(List.of(customer));
                var result = service.importFile(workbook(book -> {
                }), "key", true);
                assertThat(result.getErrors()).extracting("code").contains("CUSTOMER_PROFILE_INCOMPLETE");
                verifyNoInteractions(orders);
            }

            @Test
            void formulaAmountIsReadFromItsCachedValue() throws Exception {
                var result = service.importFile(workbook(book -> {
                    book.getSheetAt(0).getRow(4).getCell(6).setCellFormula("E5*F5");
                    book.getCreationHelper().createFormulaEvaluator().evaluateAll();
                }), "key", true);
                assertThat(result.getTotalErrors()).isZero();
                verify(orders).importOrders(anyList(), anyString(), eq("key"), eq(1));
            }

            @Test
            void formulaAmountWithTextResultIsRejected() throws Exception {
                var result = service.importFile(workbook(book -> {
                    book.getSheetAt(0).getRow(4).getCell(6).setCellFormula("B5");
                    book.getCreationHelper().createFormulaEvaluator().evaluateAll();
                }), "key", true);
                assertThat(result.getErrors()).extracting("code").contains("CELL_INVALID");
                verifyNoInteractions(orders);
            }

            @Test
            void numericDisplayFormatDoesNotHideInvalidPrecision() throws Exception {
                var result = service.importFile(workbook(book -> {
                    var cell = book.getSheetAt(0).getRow(4).getCell(4);
                    cell.setCellValue(1.23456);
                    var style = book.createCellStyle();
                    style.setDataFormat(book.createDataFormat().getFormat("0.00"));
                    cell.setCellStyle(style);
                }), "key", true);
                assertThat(result.getErrors()).extracting("code").contains("DECIMAL_INVALID");
                verifyNoInteractions(orders);
            }

            @ParameterizedTest
            @ValueSource(strings = {"1e99999999", "0", "-2", "999999999999999"})
            void rejectsInvalidQuantityWithoutWriting(String quantity) throws Exception {
                var result = service.importFile(
                    workbook(book -> book.getSheetAt(0).getRow(4).getCell(4).setCellValue(quantity)), "key", true);
                assertThat(result.getErrors()).extracting("code").contains("DECIMAL_INVALID");
                verifyNoInteractions(orders);
            }

            @Test
            void formulaInQuantityIsRejected() throws Exception {
                var result = service.importFile(
                    workbook(book -> book.getSheetAt(0).getRow(4).getCell(4).setCellFormula("1+1")), "key", true);
                assertThat(result.getErrors()).extracting("code").contains("CELL_INVALID");
                verifyNoInteractions(orders);
            }

            @Test
            void malformedWorkbookReturnsFileError() throws Exception {
                var file = new MockMultipartFile("file", "broken.xlsx", "application/octet-stream", new byte[]{1, 2, 3});
                assertThat(service.importFile(file, "key", false).getErrors()).extracting("code").contains("FILE_INVALID");
                verifyNoInteractions(orders);
            }

            @Test
            void additionalSheetsAreNotSilentlyIgnored() throws Exception {
                var result = service.importFile(workbook(book -> book.createSheet("更多送货单")), "key", true);
                assertThat(result.getErrors()).extracting("code").contains("SHEET_COUNT");
                verifyNoInteractions(orders);
            }

            @Test
            void missingHeaderIsReported() throws Exception {
                var result = service.importFile(
                    workbook(book -> book.getSheetAt(0).getRow(3).getCell(4).setCellValue("数量")), "key", true);
                assertThat(result.getErrors()).extracting("code").contains("HEADER_INVALID");
                verifyNoInteractions(orders);
            }

            @Test
            void transactionFailureIsReturnedWithRowContext() throws Exception {
                when(orders.importOrders(anyList(), anyString(), any(), anyInt())).thenThrow(
                    new SalesOrderService.ImportOrderException(0, new ScmBusinessException(OrderErrorCode.ORDER_ITEM_NOT_FOUND)));
                var result = service.importFile(workbook(book -> {
                }), "key", true);
                assertThat(result.getOrders()).isEmpty();
                assertThat(result.getErrors()).singleElement().satisfies(error -> {
                    assertThat(error.getRowNumber()).isEqualTo(5);
                    assertThat(error.getOrderKey()).isEqualTo(CUSTOMER_NAME);
                    assertThat(error.getMessage()).contains("本批数据未保存");
                });
            }

            @Test
            void dateMustBeReadable() throws Exception {
                var result = service.importFile(
                    workbook(book -> book.getSheetAt(0).getRow(2).getCell(7).setCellValue("不是日期")), "key", true);
                assertThat(result.getErrors()).extracting("code").contains("DATE_INVALID");
                verifyNoInteractions(orders);
            }

            @Test
            void dateCellWithDateFormatIsAccepted() throws Exception {
                var result = service.importFile(workbook(book -> {
                    var cell = book.getSheetAt(0).getRow(2).getCell(7);
                    cell.setCellValue(LocalDate.of(2026, 10, 28));
                    var style = book.createCellStyle();
                    style.setDataFormat(book.createDataFormat().getFormat("yyyy-mm-dd"));
                    cell.setCellStyle(style);
                }), "key", true);
                assertThat(result.getTotalErrors()).isZero();
                assertThat(capturedForms().getFirst().getExpectDeliveryTime())
                    .isEqualTo(OffsetDateTime.parse("2026-10-28T00:00:00+08:00"));
            }

            @Test
            void formulaDateIsRejected() throws Exception {
                var result = service.importFile(
                    workbook(book -> book.getSheetAt(0).getRow(2).getCell(7).setCellFormula("\"2026-10-27\"")), "key", true);
                assertThat(result.getErrors()).extracting("code").contains("CELL_INVALID");
            }

            @Test
            void rowRemarkBelongsToItsOwnItemNotTheOrder() throws Exception {
                var result = service.importFile(
                    workbook(book -> book.getSheetAt(0).getRow(4).getCell(7).setCellValue("去皮")), "key", true);
                assertThat(result.getTotalErrors()).isZero();
                var form = capturedForms().getFirst();
                // 备注跟着商品走：分拣单/送货单按行打印，不能汇总成整单备注
                assertThat(form.getRemark()).isNull();
                assertThat(form.getItems().getFirst().getRemark()).isEqualTo("去皮");
            }

            @Test
            void amountWithoutPriceIsRejected() throws Exception {
                var result = service.importFile(
                    workbook(book -> book.getSheetAt(0).getRow(4).getCell(5).setCellValue("")), "key", true);
                assertThat(result.getErrors()).extracting("code").contains("AMOUNT_WITHOUT_PRICE");
                verifyNoInteractions(orders);
            }

            @Test
            void extraColumnsAreRejected() throws Exception {
                var result = service.importFile(
                    workbook(book -> book.getSheetAt(0).getRow(4).createCell(8).setCellValue("额外")), "key", true);
                assertThat(result.getErrors()).extracting("code").contains("COLUMN_UNEXPECTED");
                verifyNoInteractions(orders);
            }

            @Test
            void zeroPriceIsAcceptedAsManualOverride() throws Exception {
                var result = service.importFile(workbook(book -> {
                    var line = book.getSheetAt(0).getRow(4);
                    line.getCell(5).setCellValue(0);
                    line.getCell(6).setCellValue(0);
                }), "key", true);
                assertThat(result.getTotalErrors()).isZero();
                var item = capturedForms().getFirst().getItems().getFirst();
                assertThat(item.getManualPriceOverride()).isTrue();
                assertThat(item.getUnitPrice()).isEqualTo("0.0000");
            }

            @ParameterizedTest
            @ValueSource(booleans = {true, false})
            void rejectsAnotherOrderAfterTotalOrSignature(boolean keepTotal) throws Exception {
                var result = service.importFile(workbook(book -> {
                    var sheet = book.getSheetAt(0);
                    if (!keepTotal) sheet.removeRow(sheet.getRow(5));
                    sheet.createRow(8).createCell(0).setCellValue("收货单位：第二个客户");
                    sheet.createRow(9).createCell(1).setCellValue("商品甲");
                }), "key", true);
                assertThat(result.getErrors()).extracting("code").contains("DATA_AFTER_END");
                verifyNoInteractions(orders);
            }

            @Test
            void rejectsExtraDataInOtherwiseEmptyRows() throws Exception {
                var result = service.importFile(workbook(book ->
                    book.getSheetAt(0).createRow(7).createCell(8).setCellValue("遗漏的商品")), "key", true);
                assertThat(result.getErrors()).extracting("code").contains("DATA_AFTER_END");
                verifyNoInteractions(orders);
            }

            @Test
            void missingUnitIsReportedOnce() throws Exception {
                var result = service.importFile(
                    workbook(book -> book.getSheetAt(0).getRow(4).getCell(2).setCellValue("")), "key", true);
                assertThat(result.getErrors()).filteredOn(error -> "单位".equals(error.getColumn()))
                    .extracting("code").containsExactly("REQUIRED");
                verifyNoInteractions(orders);
            }
        }

        @Nested

        class SalesOrderQueryRecentPriceTest {
            private SalesOrderQueryService serviceWith(SalesOrderItemDao items) {
                var scopes = mock(com.xsy.scm.common.scope.ScmDataScopeService.class);
                var context = mock(com.xsy.scm.common.scope.ScmDataScopeContext.class);
                when(scopes.resolve()).thenReturn(context);
                when(context.getOrderSellerScope()).thenReturn(com.xsy.scm.common.scope.ScmValueScope.all());
                return new SalesOrderQueryService(mock(SalesOrderDao.class), items, mock(com.xsy.scm.order.dao.OrderTagDao.class), mock(OrderAddressSnapshotDao.class),
                        mock(OrderOperationLogDao.class), mock(ScmOperatorNameResolver.class), mock(MallCustomerAccountDao.class),
                        scopes,
                        mock(CustomerService.class),
                        mock(com.xsy.scm.purchase.dao.PurchaseOrderItemDao.class),
                        mock(com.xsy.scm.promotion.service.PromotionDiscountService.class));
            }

            @Test
            void clampsLimitIntoRangeBeforeHittingDao() {
                var items = mock(SalesOrderItemDao.class);
                when(items.recentPrices(anyLong(), anyLong(), anyInt(), org.mockito.ArgumentMatchers.any())).thenReturn(List.<OrderRecentPriceVO>of());
                var service = serviceWith(items);

                service.recentPrices(7L, 11L, 0);
                verify(items).recentPrices(7L, 11L, 1, com.xsy.scm.common.scope.ScmValueScope.all());

                service.recentPrices(7L, 12L, 999);
                verify(items).recentPrices(7L, 12L, 10, com.xsy.scm.common.scope.ScmValueScope.all());

                service.recentPrices(7L, 13L, 5);
                verify(items).recentPrices(7L, 13L, 5, com.xsy.scm.common.scope.ScmValueScope.all());

                service.recentPrices(7L, 14L, -3);
                verify(items).recentPrices(7L, 14L, 1, com.xsy.scm.common.scope.ScmValueScope.all());
            }
        }
    }

    @Nested
    class OrderUnpricedIT extends ScmW3PgITBase {
        @Autowired
        SalesOrderService orders;
        @Autowired
        SalesOrderQueryService query;
        @Autowired
        CustomerService customers;
        @MockitoSpyBean
        PriceResolver resolver;

        @Test
        void missingPriceRejectsCreationAndLeavesNoOrder() throws Exception {
            var customer = new CustomerAddForm();
            customer.setName(prefix);
            customer.setSettleMode("INDEPENDENT");
            var c = customers.add(customer);
            var status = new CustomerStatusForm();
            status.setCustomerId(c);
            status.setVersion(0);
            status.setStatus("COOPERATING");
            customers.updateStatus(status);
            var sku = newOnShelfSku("NULL");
            var p = new ResolvedPriceVO();
            p.setSkuId(sku);
            p.setSellable(true);
            p.price(null, ScmPriceSourceEnum.MARKET, null);
            // Product pricing requires a market price, so the published UNPRICED resolver contract is injected here.
            // 下单解析按 (客户, SKU, 时点, 仓库) 解析；仓库为 null 表示不按仓取成本。
            doReturn(List.of(p)).when(resolver).resolve(eq(c), anyList(), any(), isNull());
            var f = new SalesOrderAddForm();
            f.setCustomerId(c);
            f.setOrderSource("ADMIN");
            var a = new OrderAddressForm();
            a.setReceiverName("客户");
            a.setReceiverPhone("123");
            a.setAddress("地址");
            f.setAddress(a);
            var i = new SalesOrderItemForm();
            i.setSkuId(sku);
            i.setOrderedQuantity("1.0000");
            i.setManualPriceOverride(false);
            f.setItems(List.of(i));
            // 新建即锁价：解析不出价格就直接失败，不留下任何订单。
            expectCode(() -> orders.create(f, prefix), 40949);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_sales_order WHERE customer_id = ?", Integer.class, c))
                    .isZero();

            // SQL CHECK must reject price/source asymmetry, including PostgreSQL's NULL boolean semantics.
            p.price(new java.math.BigDecimal("5.0000"), ScmPriceSourceEnum.MARKET, null);
            var order = orders.create(f, prefix + "priced");
            assertThat(order.getStatus()).isEqualTo("PENDING");
            assertThat(order.getItems().getFirst().getLockedUnitPrice()).isEqualByComparingTo("5.0000");
            // 改价必须同时带价格和原因，服务层校验替代了原来的数据库 CHECK。
            i.setManualPriceOverride(true);
            i.setOverrideReason("test");
            expectCode(() -> orders.create(f, prefix + "manual"), 40064);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_sales_order WHERE customer_id = ?", Integer.class, c))
                    .isEqualTo(1);
        }
    }

    @Nested
    class OrderW3PgIT extends ScmW3PgITBase {
        @Nested
        @DisplayName("订单锁单与发车后改单（PG IT）")
        class OrderLockPgIT {
            @Autowired
            SalesOrderService orders;
            @Autowired
            OrderLockService locks;
            @Autowired
            CustomerService customers;
            @Autowired
            SalesOrderDao salesOrderDao;
            @Autowired
            SqlSession session;
            @Autowired
            SortingTaskService sorting;

            private Long customer() {
                var f = new CustomerAddForm();
                f.setName(prefix);
                f.setSettleMode("INDEPENDENT");
                f.setSellerId(anyEmployeeId());
                f.setContactName("收货人");
                f.setContactPhone("13800000000");
                f.setAddress("测试地址");
                Long id = customers.add(f);
                var s = new CustomerStatusForm();
                s.setCustomerId(id);
                s.setVersion(0);
                s.setStatus("COOPERATING");
                customers.updateStatus(s);
                return id;
            }

            private SalesOrderAddForm form(Long c, Long sku) {
                var f = new SalesOrderAddForm();
                f.setCustomerId(c);
                f.setOrderSource("ADMIN");
                f.setRemark("测试订单");
                var a = new OrderAddressForm();
                a.setReceiverName("测试客户");
                a.setReceiverPhone("13800000000");
                a.setAddress("测试地址");
                f.setAddress(a);
                var i = new SalesOrderItemForm();
                i.setSkuId(sku);
                i.setOrderedQuantity("2.0000");
                i.setManualPriceOverride(false);
                f.setItems(new ArrayList<>(List.of(i)));
                return f;
            }

            private OrderVersionForm version(SalesOrderDetailVO o) {
                var f = new OrderVersionForm();
                f.setOrderId(o.getOrderId());
                f.setVersion(o.getVersion());
                return f;
            }

            private SalesOrderDetailVO confirmed(Long c, Long sku) {
                var o = orders.create(form(c, sku), prefix + "c-" + UUID.randomUUID());
                var a = new OrderActualQuantityForm();
                a.setOrderId(o.getOrderId());
                a.setItemId(o.getItems().getFirst().getItemId());
                a.setVersion(o.getItems().getFirst().getVersion());
                a.setActualQuantity("1.5000");
                a.setReason("实际称重");
                o = orders.actualQuantity(a, prefix + "a-" + UUID.randomUUID());
                return orders.confirm(version(o), prefix + "f-" + UUID.randomUUID());
            }

            private static SalesOrderUpdateForm updateOf(SalesOrderAddForm base, SalesOrderDetailVO current, String quantity) {
                var u = new SalesOrderUpdateForm();
                org.springframework.beans.BeanUtils.copyProperties(base, u);
                u.setOrderId(current.getOrderId());
                u.setVersion(current.getVersion());
                u.getItems().getFirst().setItemId(current.getItems().getFirst().getItemId());
                u.getItems().getFirst().setVersion(current.getItems().getFirst().getVersion());
                u.getItems().getFirst().setOrderedQuantity(quantity);
                return u;
            }

            private static OrderLockForm lockForm(SalesOrderDetailVO o) {
                var f = new OrderLockForm();
                f.setOrderId(o.getOrderId());
                f.setVersion(o.getVersion());
                f.setReason("对账前锁单");
                return f;
            }

            /** 以显式权限执行一段逻辑：未点名的权限码取 Mockito 默认值 false，与生产的校验失败时拒绝操作一致。 */
            private <T> T as(String permission, Supplier<T> body) {
                try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                    stp.when(() -> StpUtil.hasPermission(permission)).thenReturn(true);
                    return body.get();
                }
            }

            @Test
            @DisplayName("待确认换商品生成新明细并采用新商品价格，不继承旧行报价")
            void pendingSkuReplacementUsesNewIdentityAndPrice() {
                var c = customer();
                var originalSku = newOnShelfSku("REPLACE_OLD");
                var replacementSku = newOnShelfSku("REPLACE_NEW");
                jdbc.update("UPDATE xsy_product_sku SET market_price=7.0000 WHERE id=?", replacementSku);
                session.clearCache();
                var original = orders.create(form(c, originalSku), prefix + "replace-create");
                var originalItemId = original.getItems().getFirst().getItemId();

                var replaced = orders.update(updateOf(form(c, replacementSku), original, "3.0000"));

                assertThat(replaced.getItems()).singleElement().satisfies(item -> {
                    assertThat(item.getItemId()).isNotEqualTo(originalItemId);
                    assertThat(item.getSkuId()).isEqualTo(replacementSku);
                    assertThat(item.getLockedUnitPrice()).isEqualByComparingTo("7.0000");
                    assertThat(item.getActualQuantity()).isNull();
                });
                assertThat(replaced.getOrderedTotalAmount()).isEqualByComparingTo("21.0000");
                assertThat(jdbc.queryForObject("SELECT deleted FROM xsy_sales_order_item WHERE id=?",
                        Boolean.class, originalItemId)).isTrue();
            }

            @Test
            @DisplayName("已有分拣记录时不能用旧明细 ID 替换商品")
            void sortedSkuCannotBeReplacedThroughAnExistingItemId() {
                var c = customer();
                var originalSku = newOnShelfSku("SORTED_OLD");
                var replacementSku = newOnShelfSku("SORTED_NEW");
                var original = confirmed(c, originalSku);
                var create = new SortingTaskCreateForm();
                create.setWarehouseId(jdbc.queryForObject(
                        "SELECT id FROM xsy_warehouse WHERE NOT deleted AND status='ENABLED' ORDER BY id LIMIT 1", Long.class));
                create.setAssigneeEmployeeId(anyEmployeeId());
                create.setSalesOrderItemIds(List.of(original.getItems().getFirst().getItemId()));
                // 分拣还需仓库范围授权；本用例验证商品替换保护，不绕过该独立前置条件。
                as(com.xsy.scm.common.scope.ScmDataScopeService.WAREHOUSE_ALL_PERM,
                    () -> sorting.create(create, prefix + "replace-sort"));

                expectCode(() -> orders.update(updateOf(form(c, replacementSku), original, "3.0000")),
                        OrderErrorCode.ORDER_SORTED_ITEM_CHANGE_BLOCKED.getCode());

                assertThat(jdbc.queryForObject("SELECT sku_id FROM xsy_sales_order_item WHERE id=?", Long.class,
                        original.getItems().getFirst().getItemId())).isEqualTo(originalSku);
            }

            @Test
            @DisplayName("非标未称重确认后编辑仍保留暂计金额，不补造实重")
            void editingUnweighedConfirmedOrderKeepsEstimatedSettlement() {
                var c = customer();
                var sku = newOnShelfSku("UNWEIGHED_EDIT");
                var pending = orders.create(form(c, sku), prefix + "unweighed-create");
                var original = orders.confirm(version(pending), prefix + "unweighed-confirm");
                assertThat(original.getItems().getFirst().getActualQuantity()).isNull();
                assertThat(original.getSettlementTotalAmount()).isEqualByComparingTo("2.4000");

                var update = updateOf(form(c, sku), original, "3.0000");
                update.getItems().getFirst().setRemark("去皮");
                var edited = orders.update(update);

                assertThat(edited.getItems()).singleElement().satisfies(item -> {
                    assertThat(item.getActualQuantity()).isNull();
                    assertThat(item.getSettlementLineAmount()).isEqualByComparingTo("3.6000");
                    assertThat(item.getRemark()).isEqualTo("去皮");
                });
                assertThat(edited.getSettlementTotalAmount()).isEqualByComparingTo("3.6000");
            }

            @Test
            @DisplayName("锁单后普通改单被拒，解锁后可改；两次操作都写日志")
            void lockGatesEditsUntilUnlocked() {
                var c = customer();
                var sku = newOnShelfSku("LOCK");
                var order = confirmed(c, sku);
                assertThat(order.getLockedFlag()).isFalse();
                assertThat(order.getDispatchedAt()).isNull();
                assertThat(order.getEditable()).isTrue();

                var locked = locks.lock(lockForm(order));
                assertThat(locked.getLockedFlag()).isTrue();
                assertThat(locked.getLockReason()).isEqualTo(OrderLockService.MANUAL_REASON);
                assertThat(locked.getStatus()).isEqualTo("CONFIRMED");
                // 当前调用者没有「锁单后改单」权限，后端直接标记为不可改，前端按钮据此禁用。
                assertThat(locked.getEditable()).isFalse();
                assertThat(locked.getEditBlockedReason()).isEqualTo("订单已锁单，请新建加单");

                expectCode(() -> locks.lock(lockForm(locked)), 40912);
                expectCode(() -> orders.update(updateOf(form(c, sku), locked, "3.0000")), 40912);
                assertThat(jdbc.queryForObject("SELECT ordered_quantity FROM xsy_sales_order_item WHERE id=?",
                        java.math.BigDecimal.class, order.getItems().getFirst().getItemId())).isEqualByComparingTo("2.0000");

                var unlocked = locks.unlock(lockForm(locked));
                assertThat(unlocked.getLockedFlag()).isFalse();
                assertThat(unlocked.getLockedAt()).isNull();
                assertThat(unlocked.getEditable()).isTrue();

                var edited = orders.update(updateOf(form(c, sku), unlocked, "3.0000"));
                assertThat(edited.getItems().getFirst().getOrderedQuantity()).isEqualByComparingTo("3.0000");
                assertThat(edited.getOrderedTotalAmount()).isEqualByComparingTo("3.6000");
                // 非标品的实数量是称重结果，改单不覆盖，但结算金额按新单价重算。
                assertThat(edited.getItems().getFirst().getActualQuantity()).isEqualByComparingTo("1.5000");
                assertThat(edited.getSettlementTotalAmount()).isEqualByComparingTo("1.8000");
                assertThat(logCount(order.getOrderId(), "LOCK")).isEqualTo(1);
                assertThat(logCount(order.getOrderId(), "UNLOCK")).isEqualTo(1);
                assertThat(jdbc.queryForObject(
                        "SELECT operator FROM xsy_order_operation_log WHERE order_id=? AND operation_type='LOCK'", String.class,
                        order.getOrderId())).isEqualTo("1:1");
            }

            @Test
            @DisplayName("未持发车后改单权限时改单被拒，订单行保持不变")
            void dispatchedEditIsRefusedWithoutPermission() {
                var c = customer();
                var sku = newOnShelfSku("NOPERM");
                var order = confirmed(c, sku);
                salesOrderDao.markDispatched(order.getOrderId(), OffsetDateTime.now(), "1:1");
                session.clearCache();
                // markDispatched 自己会推进版本号，夹具按服务端的新版本继续。
                order.setVersion(order.getVersion() + 1);

                expectCode(() -> orders.update(updateOf(form(c, sku), order, "2.5000")), 40913);
                assertThat(jdbc.queryForObject("SELECT ordered_quantity FROM xsy_sales_order_item WHERE id=?",
                        java.math.BigDecimal.class, order.getItems().getFirst().getItemId())).isEqualByComparingTo("2.0000");
            }

            @Test
            @DisplayName("签收后不能再直接改单：即使持发车后改单权限也拒绝，金额差异走退货或红字")
            void signedOrderCannotBeEdited() {
                var c = customer();
                var sku = newOnShelfSku("SIGNED");
                var order = confirmed(c, sku);
                salesOrderDao.markDispatched(order.getOrderId(), OffsetDateTime.now(), "1:1");
                jdbc.update("""
                        INSERT INTO xsy_delivery_route_order (route_id, stop_id, order_id, customer_id, order_no_snapshot,
                            assignment_status, fulfillment_status, signed_at, signed_by)
                        VALUES (0, 0, ?, ?, ?, 'ACTIVE', 'SIGNED', CURRENT_TIMESTAMP, '1:1')
                        """, order.getOrderId(), c, order.getOrderNo());
                session.clearCache();
                // markDispatched 自己会推进版本号，夹具按服务端的新版本继续。
                order.setVersion(order.getVersion() + 1);

                assertThat(salesOrderDao.hasSignedFact(order.getOrderId())).isTrue();
                // 发车后改单权限也不能覆盖签收记录：应收已按出库量生成，纸面与账面的差异必须走红字。
                as(OrderPermission.EDIT_AFTER_DISPATCH, () -> {
                    expectCode(() -> orders.update(updateOf(form(c, sku), order, "3.0000")), 41217);
                    return null;
                });
                assertThat(jdbc.queryForObject("SELECT ordered_quantity FROM xsy_sales_order_item WHERE id=?",
                        java.math.BigDecimal.class, order.getItems().getFirst().getItemId())).isEqualByComparingTo("2.0000");
            }

            @Test
            @DisplayName("持发车后改单权限时改单成功，并单独记发车后改单日志")
            void dispatchedEditWithPermissionIsLoggedSeparately() {
                var c = customer();
                var sku = newOnShelfSku("AFTERDISPATCH");
                var order = confirmed(c, sku);
                salesOrderDao.markDispatched(order.getOrderId(), OffsetDateTime.now(), "1:1");
                session.clearCache();
                order.setVersion(order.getVersion() + 1);
                var dispatched = jdbc.queryForObject("SELECT dispatched_at FROM xsy_sales_order WHERE id=?",
                        OffsetDateTime.class, order.getOrderId());
                assertThat(dispatched).isNotNull();

                var edited = as(OrderPermission.EDIT_AFTER_DISPATCH,
                        () -> orders.update(updateOf(form(c, sku), order, "2.5000")));

                assertThat(edited.getItems().getFirst().getOrderedQuantity()).isEqualByComparingTo("2.5000");
                // 发车后改单同样沿用锁定单价，不静默改价。
                assertThat(edited.getItems().getFirst().getLockedUnitPrice())
                        .isEqualByComparingTo(order.getItems().getFirst().getLockedUnitPrice());
                // 结算金额按称重实数量 × 锁定单价，改下单量不改已称出的实数量。
                assertThat(edited.getSettlementTotalAmount()).isEqualByComparingTo("1.8000");
                assertThat(logCount(order.getOrderId(), "EDIT_AFTER_DISPATCH")).isEqualTo(1);
            }

            @Test
            @DisplayName("已确认未发车、未锁的订单按普通改单权限可改")
            void confirmedOrderWithoutDispatchStaysEditable() {
                var c = customer();
                var sku = newOnShelfSku("EDITABLE");
                var order = confirmed(c, sku);

                var edited = orders.update(updateOf(form(c, sku), order, "4.0000"));

                assertThat(edited.getItems().getFirst().getOrderedQuantity()).isEqualByComparingTo("4.0000");
                assertThat(edited.getOrderedTotalAmount()).isEqualByComparingTo("4.8000");
                assertThat(logCount(order.getOrderId(), "UPDATE")).isEqualTo(1);
            }

            @Test
            @DisplayName("发车满 7 天的订单自动锁单，未满 7 天与未发车的不进候选")
            void autoLockOnlyTouchesOrdersDispatchedSevenDaysAgo() {
                var c = customer();
                var sku = newOnShelfSku("AUTO");
                var old = confirmed(c, sku);
                var recent = confirmed(c, sku);
                var notDispatched = confirmed(c, sku);
                jdbc.update("UPDATE xsy_sales_order SET dispatched_at = CURRENT_TIMESTAMP - INTERVAL '8 days' WHERE id=?",
                        old.getOrderId());
                jdbc.update("UPDATE xsy_sales_order SET dispatched_at = CURRENT_TIMESTAMP - INTERVAL '6 days' WHERE id=?",
                        recent.getOrderId());
                session.clearCache();

                assertThat(salesOrderDao.autoLockCandidates(1000)).contains(old.getOrderId()).doesNotContain(
                        recent.getOrderId(), notDispatched.getOrderId());

                assertThat(locks.autoLock(old.getOrderId())).isTrue();
                // 重复调用不会重复锁、不会重复写日志。
                assertThat(locks.autoLock(old.getOrderId())).isFalse();
                assertThat(jdbc.queryForObject(
                        "SELECT locked_flag || ':' || lock_reason || ':' || locked_by FROM xsy_sales_order WHERE id=?",
                        String.class, old.getOrderId())).isEqualTo("true:" + OrderLockService.AUTO_7_DAYS_REASON + ":"
                            + OrderLockService.AUTO_LOCK_OPERATOR);
                assertThat(jdbc.queryForObject("SELECT auto_lock_at IS NOT NULL FROM xsy_sales_order WHERE id=?",
                        Boolean.class, old.getOrderId())).isTrue();
                assertThat(logCount(old.getOrderId(), "AUTO_LOCK")).isEqualTo(1);
                assertThat(logCount(recent.getOrderId(), "AUTO_LOCK")).isZero();
                assertThat(jdbc.queryForObject("SELECT locked_flag FROM xsy_sales_order WHERE id=?", Boolean.class,
                        notDispatched.getOrderId())).isFalse();
            }

            @Test
            @DisplayName("持锁单后改单权限时，锁住的订单仍可改单并留痕")
            void editLockedOrderWithExplicitPermission() {
                var c = customer();
                var sku = newOnShelfSku("EDITLOCKED");
                var order = confirmed(c, sku);
                var locked = locks.lock(lockForm(order));

                var edited = as(OrderPermission.EDIT_LOCKED,
                        () -> orders.update(updateOf(form(c, sku), locked, "3.0000")));

                assertThat(edited.getLockedFlag()).isTrue();
                assertThat(edited.getItems().getFirst().getOrderedQuantity()).isEqualByComparingTo("3.0000");
                assertThat(logCount(order.getOrderId(), "UPDATE")).isEqualTo(1);
            }

            private int logCount(Long orderId, String operation) {
                return jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_order_operation_log WHERE order_id=? AND operation_type=?", Integer.class,
                        orderId, operation);
            }
        }



        @Nested

        class SalesOrderRecentPriceIT {
            @Autowired
            SalesOrderService orders;
            @Autowired
            SalesOrderQueryService query;
            @Autowired
            CustomerService customers;

            private Long customer(String sfx) {
                var f = new CustomerAddForm();
                f.setName(prefix + sfx);
                f.setSettleMode("INDEPENDENT");
                f.setSellerId(anyEmployeeId());
                Long id = customers.add(f);
                var s = new CustomerStatusForm();
                s.setCustomerId(id);
                s.setVersion(0);
                s.setStatus("COOPERATING");
                customers.updateStatus(s);
                return id;
            }

            private SalesOrderAddForm form(Long c, Long sku) {
                var f = new SalesOrderAddForm();
                f.setCustomerId(c);
                f.setOrderSource("ADMIN");
                var a = new OrderAddressForm();
                a.setReceiverName("测试客户");
                a.setReceiverPhone("13800000000");
                a.setAddress("测试地址");
                f.setAddress(a);
                var i = new SalesOrderItemForm();
                i.setSkuId(sku);
                i.setOrderedQuantity("2.0000");
                i.setManualPriceOverride(false);
                f.setItems(new ArrayList<>(List.of(i)));
                return f;
            }

            private OrderVersionForm version(SalesOrderDetailVO o) {
                var f = new OrderVersionForm();
                f.setOrderId(o.getOrderId());
                f.setVersion(o.getVersion());
                return f;
            }

            /** 新建即停在 PENDING（已锁价但未确认），用于验证 PENDING 被排除。 */
            private SalesOrderDetailVO submitted(Long c, Long sku, String tag) {
                return orders.create(form(c, sku), prefix + tag + "c");
            }

            /** 新建 → 录实重 → confirm，得到带锁定单价的 CONFIRMED 单。 */
            private SalesOrderDetailVO confirmed(Long c, Long sku, String tag) {
                var o = submitted(c, sku, tag);
                var a = new OrderActualQuantityForm();
                a.setOrderId(o.getOrderId());
                a.setItemId(o.getItems().getFirst().getItemId());
                a.setVersion(o.getItems().getFirst().getVersion());
                a.setActualQuantity("1.5000");
                a.setReason("实际称重");
                o = orders.actualQuantity(a, prefix + tag + "a");
                return orders.confirm(version(o), prefix + tag + "f");
            }

            @Test
            void recentPricesReturnsOnlyConfirmedForCustomerAndSkuNewestFirst() {
                Long c = customer("A");
                Long sku = newOnShelfSku("RP");
                Long other = customer("B");

                SalesOrderDetailVO first = confirmed(c, sku, "1");
                SalesOrderDetailVO second = confirmed(c, sku, "2");

                // PENDING：已锁价但未确认，参考规则只认 CONFIRMED，必须排除而不是只排除 CANCELLED
                SalesOrderDetailVO pending = submitted(c, sku, "p");
                // CANCELLED：从 PENDING 取消得到（状态机禁止 CONFIRMED 取消），必须排除
                SalesOrderDetailVO pendingToCancel = submitted(c, sku, "x");
                var cancel = new OrderCancelForm();
                cancel.setOrderId(pendingToCancel.getOrderId());
                cancel.setVersion(pendingToCancel.getVersion());
                cancel.setReason("客户取消");
                orders.cancel(cancel, prefix + "cx");
                // 其它客户的 CONFIRMED 不得混入本客户结果
                confirmed(other, sku, "o");

                List<OrderRecentPriceVO> rows = query.recentPrices(c, sku, 10);
                assertThat(rows).extracting(OrderRecentPriceVO::getOrderNo)
                        .containsExactly(second.getOrderNo(), first.getOrderNo());
                assertThat(rows).extracting(OrderRecentPriceVO::getOrderNo)
                        .doesNotContain(pending.getOrderNo(), pendingToCancel.getOrderNo());

                var latest = rows.getFirst();
                assertThat(latest.getUnitPrice()).isEqualByComparingTo("1.2000");
                assertThat(latest.getOrderedQuantity()).isEqualByComparingTo("2.0000");
                assertThat(latest.getPriceSource()).isEqualTo("MARKET");
                assertThat(latest.getOrderSource()).isEqualTo("ADMIN");
                assertThat(latest.getCreatedAt()).isNotNull();
                assertThat(latest.getConfirmedAt()).isNotNull();
                assertThat(latest.getItemId()).isNotNull();
                assertThat(latest.getOrderId()).isNotNull();
                assertThat(latest.getSaleUnit()).isEqualTo("kg");

                // 客户维度隔离：另一客户只有它自己那一条
                assertThat(query.recentPrices(other, sku, 10)).extracting(OrderRecentPriceVO::getOrderNo)
                        .containsExactly(confirmedOrderNoOf(other, sku));
            }

            /** 取该客户该 SKU 的最近已确认订单号（用于跨客户隔离断言，不引入新查询）。 */
            private String confirmedOrderNoOf(Long c, Long sku) {
                return query.recentPrices(c, sku, 1).getFirst().getOrderNo();
            }

            @Test
            void recentPricesLimitTruncatesNewestNOrders() {
                Long c = customer("A");
                Long sku = newOnShelfSku("RL");
                SalesOrderDetailVO a = confirmed(c, sku, "a");
                SalesOrderDetailVO b = confirmed(c, sku, "b");
                SalesOrderDetailVO newest = confirmed(c, sku, "c");

                // limit 约束订单数：取最近 2 张 → 只含 b、newest，最旧的 a 被截断
                assertThat(query.recentPrices(c, sku, 2)).extracting(OrderRecentPriceVO::getOrderNo)
                        .containsExactly(newest.getOrderNo(), b.getOrderNo());
                // limit=0 经服务层裁剪到下限 1，仍返回最近一张
                assertThat(query.recentPrices(c, sku, 0)).hasSize(1);
            }
        }

        @Nested

        class SalesOrderServiceIT {
            @Autowired
            SalesOrderService orders;
            @Autowired
            SalesOrderQueryService query;
            @Autowired
            OrderReturnService returns;
            @Autowired
            OrderRefundService refunds;
            @Autowired
            CustomerService customers;
            @Autowired
            SalesOrderImportService orderImports;
            @Autowired
            org.apache.ibatis.session.SqlSession session;

            Long customer() {
                var f = new CustomerAddForm();
                f.setName(prefix);
                f.setSettleMode("INDEPENDENT");
                f.setSellerId(anyEmployeeId());
                // 送货单导入按客户档案取收货人、电话与地址；其余用例不受影响。
                f.setContactName("收货人");
                f.setContactPhone("13800000000");
                f.setAddress("测试地址");
                Long id = customers.add(f);
                var s = new CustomerStatusForm();
                s.setCustomerId(id);
                s.setVersion(0);
                s.setStatus("COOPERATING");
                customers.updateStatus(s);
                return id;
            }

            SalesOrderAddForm form(Long c, Long sku) {
                var f = new SalesOrderAddForm();
                f.setCustomerId(c);
                f.setOrderSource("ADMIN");
                f.setRemark("测试订单");
                var a = new OrderAddressForm();
                a.setReceiverName("测试客户");
                a.setReceiverPhone("13800000000");
                a.setAddress("测试地址");
                f.setAddress(a);
                var i = new SalesOrderItemForm();
                i.setSkuId(sku);
                i.setOrderedQuantity("2.0000");
                i.setManualPriceOverride(false);
                f.setItems(new ArrayList<>(List.of(i)));
                return f;
            }

            OrderVersionForm version(SalesOrderDetailVO o) {
                var f = new OrderVersionForm();
                f.setOrderId(o.getOrderId());
                f.setVersion(o.getVersion());
                return f;
            }

            SalesOrderDetailVO confirmed(Long c, Long sku) {
                // 键按次唯一：真提交的用例重跑时不能撞上上一次留下的防重复处理记录。
                var o = orders.create(form(c, sku), prefix + "c-" + UUID.randomUUID());
                var a = new OrderActualQuantityForm();
                a.setOrderId(o.getOrderId());
                a.setItemId(o.getItems().getFirst().getItemId());
                a.setVersion(o.getItems().getFirst().getVersion());
                a.setActualQuantity("1.5000");
                a.setReason("实际称重");
                o = orders.actualQuantity(a, prefix + "a");
                return orders.confirm(version(o), prefix + "f");
            }

            @Test
            void lifecycleRetainsSnapshotsAndLockedPricesAndAudit() {
                var c = customer();
                var sku = newOnShelfSku("L");
                var f = form(c, sku);
                var o = orders.create(f, prefix);
                assertThat(o.getVersion()).isZero();
                assertThat(o.getSettleModeSnapshot()).isEqualTo("INDEPENDENT");
                assertThat(o.getSellerId()).isEqualTo(anyEmployeeId());
                var u = new SalesOrderUpdateForm();
                org.springframework.beans.BeanUtils.copyProperties(f, u);
                u.setOrderId(o.getOrderId());
                u.setVersion(o.getVersion());
                u.getItems().getFirst().setItemId(o.getItems().getFirst().getItemId());
                u.getItems().getFirst().setVersion(o.getItems().getFirst().getVersion());
                u.getItems().getFirst().setOrderedQuantity("3.0000");
                var updated = orders.update(u);
                assertThat(updated.getItems().getFirst().getItemId()).isEqualTo(o.getItems().getFirst().getItemId());
                expectCode(() -> orders.update(u), 40921);
                jdbc.update("UPDATE xsy_product_sku SET market_price=2.5000 WHERE id=?", sku);
                session.clearCache();
                // 行情变了不会静默改价：必须显式核价留痕——这正是设计规则。
                var reprice = new OrderRepriceForm();
                reprice.setOrderId(updated.getOrderId());
                reprice.setVersion(updated.getVersion());
                reprice.setReason("行情涨价");
                o = orders.reprice(reprice);
                assertThat(o.getOrderedTotalAmount()).isEqualByComparingTo("7.5000");
                assertThat(o.getItems().getFirst().getActualQuantity()).isNull();
                jdbc.update("UPDATE xsy_product_sku SET market_price=8.0000 WHERE id=?", sku);
                jdbc.update("UPDATE xsy_customer SET name='changed',settle_mode='GROUP',seller_id=NULL WHERE id=?", c);
                session.clearCache();
                // 非标未称重也能确认：不要求销售先填估计实重，金额按订购量暂计，称重后回写实际值
                o = orders.confirm(version(o), prefix + "confirm");
                assertThat(o.getStatus()).isEqualTo("CONFIRMED");
                assertThat(o.getItems().getFirst().getActualQuantity()).isNull();
                assertThat(o.getSettlementTotalAmount()).isEqualByComparingTo("7.5000");
                var a = new OrderActualQuantityForm();
                a.setOrderId(o.getOrderId());
                a.setItemId(o.getItems().getFirst().getItemId());
                a.setVersion(o.getItems().getFirst().getVersion());
                a.setActualQuantity("2.1234");
                a.setReason("人工称重");
                o = orders.actualQuantity(a, prefix + "actual");
                assertThat(o.getSettlementTotalAmount()).isEqualByComparingTo("5.3085");
                assertThat(o.getOrderedTotalAmount()).isEqualByComparingTo("7.5000");
                assertThat(o.getSettleModeSnapshot()).isEqualTo("INDEPENDENT");
                assertThat(o.getCustomerNameSnapshot()).isEqualTo(prefix);
                assertThat(o.getItems().getFirst().getLockedUnitPrice()).isEqualByComparingTo("2.5000");
                var logs = new OrderLogQueryForm();
                logs.setOrderId(o.getOrderId());
                logs.setPageNum(1L);
                logs.setPageSize(20L);
                assertThat(query.logs(logs).getList()).extracting(OrderOperationLogVO::getOperationType).contains("CREATE", "UPDATE", "ACTUAL_QUANTITY", "CONFIRM");
                assertThat(query.logs(logs).getList()).allSatisfy(l -> {
                    assertThat(l.getOperator()).isEqualTo("1:1");
                    assertThat(l.getAfterData()).isNotEmpty();
                });
                var jsonValue = objectMapper.valueToTree(o);
                assertThat(jsonValue.get("settlementTotalAmount").asText()).isEqualTo("5.3085");
            }

            @Test
            void createAndProgressConfirmsStandardButKeepsNonStandardPending() {
                var customer = customer();
                var standardSku = newOnShelfSku("AUTO_STANDARD");
                jdbc.update("UPDATE xsy_product_sku SET product_type='STANDARD' WHERE id=?", standardSku);
                session.clearCache();
                var standard = orders.createAndProgress(form(customer, standardSku), prefix + "standard");
                assertThat(standard.getStatus()).isEqualTo("CONFIRMED");
                assertThat(standard.getItems().getFirst().getActualQuantity()).isEqualByComparingTo("2.0000");
                assertThat(orders.createAndProgress(form(customer, standardSku), prefix + "standard").getOrderId()).isEqualTo(standard.getOrderId());

                var nonStandard = orders.createAndProgress(form(customer, newOnShelfSku("AUTO_WEIGHT")), prefix + "weight");
                assertThat(nonStandard.getStatus()).isEqualTo("PENDING");
                assertThat(nonStandard.getItems().getFirst().getActualQuantity()).isNull();
            }

            @Test
            void pendingOrderEditRelocksQuantityAndKeepsTheSubmittedPrice() {
                var c = customer();
                var sku = newOnShelfSku("PENDING_EDIT");
                jdbc.update("UPDATE xsy_product_sku SET product_type='STANDARD' WHERE id=?", sku);
                session.clearCache();
                var pending = orders.create(form(c, sku), prefix + "pe");
                assertThat(pending.getStatus()).isEqualTo("PENDING");
                var quotedUnitPrice = pending.getItems().getFirst().getLockedUnitPrice();

                var u = new SalesOrderUpdateForm();
                org.springframework.beans.BeanUtils.copyProperties(form(c, sku), u);
                u.setOrderId(pending.getOrderId());
                u.setVersion(pending.getVersion());
                u.getItems().getFirst().setItemId(pending.getItems().getFirst().getItemId());
                u.getItems().getFirst().setVersion(pending.getItems().getFirst().getVersion());
                u.getItems().getFirst().setOrderedQuantity("3.0000");
                var edited = orders.update(u);

                assertThat(edited.getStatus()).isEqualTo("PENDING");
                assertThat(edited.getItems().getFirst().getOrderedQuantity()).isEqualByComparingTo("3.0000");
                // 改单沿用提交时的报价，不静默改价；需要新价时走批量核价。
                assertThat(edited.getItems().getFirst().getLockedUnitPrice()).isEqualByComparingTo(quotedUnitPrice);
                assertThat(edited.getOrderedTotalAmount()).isEqualByComparingTo("3.6000");
                // 标品实数量必须跟着下单量走，否则确认前会被实数量校验拦下。
                assertThat(edited.getItems().getFirst().getActualQuantity()).isEqualByComparingTo("3.0000");
                assertThat(logCount(pending.getOrderId(), "UPDATE")).isEqualTo(1);
            }

            @Test
            void pendingOrderWithPaymentFactRefusesFurtherEdits() {
                var c = customer();
                var sku = newOnShelfSku("PENDING_BLOCKED");
                var pending = orders.create(form(c, sku), prefix + "pb");
                jdbc.queryForObject("INSERT INTO xsy_payment_intent (intent_no, customer_id, customer_name_snapshot,"
                        + " source_type, source_id, source_no_snapshot, amount, method, provider, status,"
                        + " created_by, updated_by) VALUES (?, ?, ?, 'SALES_ORDER', ?, ?, 1.0000, 'ONLINE', 'MOCK',"
                        + " 'PENDING', '1:1', '1:1') RETURNING id",
                        Long.class, "PI-" + prefix + "-" + UUID.randomUUID(), c, prefix, pending.getOrderId(),
                        "SO-" + pending.getOrderId());
                session.clearCache();
                var u = new SalesOrderUpdateForm();
                org.springframework.beans.BeanUtils.copyProperties(form(c, sku), u);
                u.setOrderId(pending.getOrderId());
                u.setVersion(pending.getVersion());
                u.getItems().getFirst().setItemId(pending.getItems().getFirst().getItemId());
                u.getItems().getFirst().setVersion(pending.getItems().getFirst().getVersion());
                u.getItems().getFirst().setOrderedQuantity("5.0000");
                expectCode(() -> orders.update(u), 41204);
                assertThat(jdbc.queryForObject("SELECT ordered_quantity FROM xsy_sales_order_item WHERE id=?",
                        java.math.BigDecimal.class, pending.getItems().getFirst().getItemId()))
                        .isEqualByComparingTo("2.0000");
            }

            @Test
            void confirmedOrderAcceptsActualQuantityUntilDispatch() {
                var c = customer();
                var sku = newOnShelfSku("ACTUAL_CONFIRMED");
                var order = confirmed(c, sku);
                assertThat(order.getStatus()).isEqualTo("CONFIRMED");
                assertThat(order.getSettlementTotalAmount()).isEqualByComparingTo("1.8000");

                var a = new OrderActualQuantityForm();
                a.setOrderId(order.getOrderId());
                a.setItemId(order.getItems().getFirst().getItemId());
                a.setVersion(order.getItems().getFirst().getVersion());
                a.setActualQuantity("2.4000");
                a.setReason("复称");
                var edited = orders.actualQuantity(a, prefix + "ac-" + UUID.randomUUID());

                assertThat(edited.getItems().getFirst().getActualQuantity()).isEqualByComparingTo("2.4000");
                // 已确认订单的实发量就是结算量，订单结算合计必须同步。
                assertThat(edited.getSettlementTotalAmount()).isEqualByComparingTo("2.8800");
                assertThat(logCount(order.getOrderId(), "ACTUAL_QUANTITY")).isEqualTo(2);

                // 发车出库后不再允许改实发量，否则与已出库的分拣记录脱节。
                jdbc.update("UPDATE xsy_sales_order SET dispatched_at=CURRENT_TIMESTAMP WHERE id=?", order.getOrderId());
                session.clearCache();
                var stale = new OrderActualQuantityForm();
                stale.setOrderId(edited.getOrderId());
                stale.setItemId(edited.getItems().getFirst().getItemId());
                stale.setVersion(edited.getItems().getFirst().getVersion());
                stale.setActualQuantity("3.0000");
                stale.setReason("复称");
                expectCode(() -> orders.actualQuantity(stale, prefix + "ac2-" + UUID.randomUUID()), 40917);
            }

            @Test
            void excelImportIsAtomicAndKeepsNonStandardPending() throws Exception {
                customer();
                var standardSku = newOnShelfSku("IMPORT_STANDARD");
                jdbc.update("UPDATE xsy_product_sku SET product_type='STANDARD' WHERE id=?", standardSku);
                var nonStandardSku = newOnShelfSku("IMPORT_WEIGHT");
                session.clearCache();
                var standard = productOf(standardSku);
                var weight = productOf(nonStandardSku);

                // 一张送货单生成一张订单：同时含标品与称重品时整单待称重。
                var mixedFile = deliveryNote(prefix, List.of(noteLine(standard, "2.0000"), noteLine(weight, "3.0000")));
                var mixed = orderImports.importFile(mixedFile, prefix + "import", true);
                assertThat(mixed.getTotalErrors()).isZero();
                assertThat(mixed.getTotalOrders()).isOne();
                assertThat(mixed.getPendingOrders()).isOne();
                assertThat(mixed.getOrders()).singleElement().satisfies(o -> {
                    assertThat(o.getOrderSource()).isEqualTo("IMPORT");
                    assertThat(o.getStatus()).isEqualTo("PENDING");
                    assertThat(o.getItems()).filteredOn(item -> item.getSkuId().equals(standardSku)).singleElement()
                            .satisfies(item -> assertThat(item.getActualQuantity()).isEqualByComparingTo("2.0000"));
                });
                // 同一文件 + 同一防重复请求编号重放，不再产生第二张订单。
                assertThat(orderImports.importFile(mixedFile, prefix + "import", true).getOrders())
                        .extracting(SalesOrderDetailVO::getOrderId)
                        .containsExactlyElementsOf(mixed.getOrders().stream().map(SalesOrderDetailVO::getOrderId).toList());

                // 只含标品的送货单直接确认。
                var single = orderImports.importFile(
                        deliveryNote(prefix, List.<String[]>of(noteLine(standard, "1.0000"))), prefix + "single", true);
                assertThat(single.getConfirmedOrders()).isOne();

                var before = jdbc.queryForObject("SELECT count(*) FROM xsy_sales_order WHERE order_source='IMPORT'", Integer.class);
                var invalid = orderImports.importFile(
                        deliveryNote("不存在的收货单位" + prefix, List.<String[]>of(noteLine(standard, "0"))), prefix + "bad", true);
                assertThat(invalid.getTotalErrors()).isGreaterThanOrEqualTo(2);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_sales_order WHERE order_source='IMPORT'", Integer.class)).isEqualTo(before);
            }

            /** 返回 {@code [商品名称, 规格, 单位]}，送货单只写名称与规格、单位必须与销售单位一致。 */
            private String[] productOf(Long skuId) {
                var row = jdbc.queryForMap(
                        "SELECT p.name, s.spec_name, s.sale_unit FROM xsy_product_sku s JOIN xsy_product_spu p ON p.id = s.spu_id WHERE s.id = ?",
                        skuId);
                return new String[]{(String) row.get("name"), (String) row.get("spec_name"), (String) row.get("sale_unit")};
            }

            private String[] noteLine(String[] product, String quantity) {
                return new String[]{product[0], product[1], product[2], quantity};
            }

            /** 生成客户版式的送货单：第 3 行收货单位/日期，第 4 行表头，明细从第 5 行开始，末行合计与签字栏。 */
            private org.springframework.mock.web.MockMultipartFile deliveryNote(String customerName, List<String[]> lines)
                    throws Exception {
                try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
                        var bytes = new java.io.ByteArrayOutputStream()) {
                    var sheet = book.createSheet("送货单");
                    sheet.createRow(0).createCell(0).setCellValue("鲜蔬源(西安)供应链服务有限公司");
                    sheet.createRow(1).createCell(0).setCellValue("送货单");
                    var meta = sheet.createRow(2);
                    meta.createCell(0).setCellValue("收货单位：");
                    meta.createCell(1).setCellValue(customerName);
                    meta.createCell(6).setCellValue("日期：");
                    meta.createCell(7).setCellValue("2026-10-27");
                    var headers = List.of("序号", "商品名称", "单位", "规格", "实数量", "销售单价", "销售金额", "备注");
                    var header = sheet.createRow(3);
                    for (int column = 0; column < headers.size(); column++)
                        header.createCell(column).setCellValue(headers.get(column));
                    for (int index = 0; index < lines.size(); index++) {
                        var values = lines.get(index);
                        var line = sheet.createRow(4 + index);
                        line.createCell(0).setCellValue(index + 1);
                        line.createCell(1).setCellValue(values[0]);
                        line.createCell(2).setCellValue(values[2]);
                        line.createCell(3).setCellValue(values[1]);
                        line.createCell(4).setCellValue(Double.parseDouble(values[3]));
                    }
                    sheet.createRow(4 + lines.size()).createCell(0).setCellValue("合计金额");
                    var footer = sheet.createRow(5 + lines.size());
                    footer.createCell(1).setCellValue("销售方：");
                    footer.createCell(7).setCellValue("收货方：");
                    book.write(bytes);
                    return new org.springframework.mock.web.MockMultipartFile("file", "delivery-note.xlsx",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes.toByteArray());
                }
            }

            @Test
            @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
            void lateImportFailureRollsBackOrdersItemsAddressesLogsAndIdempotency() {
                var customerId = customer();
                var standardSku = newOnShelfSku("ROLLBACK_OK");
                var unavailableSku = newProductSku("ROLLBACK_BAD", "ON_SHELF", "OFF_SHELF");
                jdbc.update("UPDATE xsy_product_sku SET product_type='STANDARD' WHERE id=?", standardSku);
                var first = form(customerId, standardSku);
                first.setOrderSource("IMPORT");
                var second = form(customerId, unavailableSku);
                second.setOrderSource("IMPORT");
                var logCount = jdbc.queryForObject("SELECT count(*) FROM xsy_order_operation_log", Long.class);
                try {
                    assertThatThrownBy(() -> orders.importOrders(List.of(first, second), "test-hash", prefix + "rollback", 2))
                            .isInstanceOfSatisfying(SalesOrderService.ImportOrderException.class, e -> assertThat(e.getOrderIndex()).isEqualTo(1));
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_sales_order WHERE customer_id=?", Integer.class, customerId)).isZero();
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_sales_order_item WHERE sku_id IN (?,?)", Integer.class, standardSku, unavailableSku)).isZero();
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_order_address_snapshot WHERE customer_id=?", Integer.class, customerId)).isZero();
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_order_operation_log", Long.class)).isEqualTo(logCount);
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_idempotency_record WHERE idempotency_key=?", Integer.class, prefix + "rollback")).isZero();
                } finally {
                    // Only this test's fixture IDs are removed; no shared business data is touched.
                    var spuIds = jdbc.queryForList("SELECT spu_id FROM xsy_product_sku WHERE id IN (?,?)", Long.class, standardSku, unavailableSku);
                    jdbc.update("DELETE FROM xsy_product_sku WHERE id IN (?,?)", standardSku, unavailableSku);
                    for (var spuId : spuIds) jdbc.update("DELETE FROM xsy_product_spu WHERE id=?", spuId);
                    jdbc.update("DELETE FROM xsy_customer WHERE id=?", customerId);
                }
            }

            @Test
            void mixedManualOrderConfirmsWithProvisionalWeightRowAmount() {
                var customerId = customer();
                var standardSku = newOnShelfSku("MIX_STANDARD");
                var weightSku = newOnShelfSku("MIX_WEIGHT");
                jdbc.update("UPDATE xsy_product_sku SET product_type='STANDARD' WHERE id=?", standardSku);
                session.clearCache();
                var request = form(customerId, standardSku);
                request.getItems().add(form(customerId, weightSku).getItems().getFirst());
                var order = orders.createAndProgress(request, prefix + "mixed");
                assertThat(order.getStatus()).isEqualTo("PENDING");
                assertThat(order.getItems()).filteredOn(item -> item.getSkuId().equals(standardSku)).singleElement()
                        .satisfies(item -> assertThat(item.getActualQuantity()).isEqualByComparingTo("2.0000"));
                assertThat(order.getItems()).filteredOn(item -> item.getSkuId().equals(weightSku)).singleElement()
                        .satisfies(item -> assertThat(item.getActualQuantity()).isNull());
                // 称重行未称也能确认：实数量保持空，结算额按订购量暂计，等分拣台称完再回写
                var confirmed = orders.confirm(version(order), prefix + "early-confirm");
                assertThat(confirmed.getStatus()).isEqualTo("CONFIRMED");
                assertThat(confirmed.getItems()).filteredOn(item -> item.getSkuId().equals(weightSku)).singleElement()
                        .satisfies(item -> {
                            assertThat(item.getActualQuantity()).isNull();
                            assertThat(item.getSettlementLineAmount()).isEqualByComparingTo(item.getOrderedLineAmount());
                        });
            }

            @Test
            void zeroIsPricedStandardQuantityAndIdempotentReplay() {
                var c = customer();
                var sku = newOnShelfSku("Z");
                jdbc.update("UPDATE xsy_product_sku SET market_price=0,product_type='STANDARD' WHERE id=?", sku);
                var f = form(c, sku);
                var o = orders.create(f, prefix);
                assertThat(orders.create(f, prefix).getOrderId()).isEqualTo(o.getOrderId());
                f.setRemark("changed");
                expectCode(() -> orders.create(f, prefix), 40966);
                assertThat(o.getStatus()).isEqualTo("PENDING");
                assertThat(o.getItems().getFirst().getActualQuantity()).isEqualByComparingTo("2.0000");
                var actual = new OrderActualQuantityForm();
                actual.setOrderId(o.getOrderId());
                actual.setItemId(o.getItems().getFirst().getItemId());
                actual.setVersion(o.getItems().getFirst().getVersion());
                actual.setActualQuantity("1.0000");
                actual.setReason("不能改标品");
                expectCode(() -> orders.actualQuantity(actual, prefix + "invalidactual"), 40962);
                o = orders.confirm(version(o), prefix + "f");
                assertThat(o.getSettlementTotalAmount()).isEqualByComparingTo("0.0000");
            }

            @Test
            void manualOverrideDoesNotBypassVisibility() {
                var c = customer();
                var sku = newOnShelfSku("M");
                var f = form(c, sku);
                var item = f.getItems().getFirst();
                item.setManualPriceOverride(true);
                item.setUnitPrice("4.5000");
                item.setOverrideReason("协商");
                var o = orders.create(f, prefix);
                assertThat(o.getItems().getFirst().getDraftPriceSource()).isEqualTo("OVERRIDE");
                assertThat(o.getItems().getFirst().getLockedPriceSource()).isEqualTo("OVERRIDE");
                jdbc.update("UPDATE xsy_customer SET visibility_policy='ALLOWLIST' WHERE id=?", c);
                session.clearCache();
                // 人工改价不能绕过可见性：新建时同样按可售价校验。
                expectCode(() -> orders.create(f, prefix + "hidden"), 40949);
            }

            @Test
            void afterSalesQuotaApprovalAndRefundAreAtomic() {
                var o = confirmed(customer(), newOnShelfSku("R"));
                var f = new OrderReturnAddForm();
                f.setOrderId(o.getOrderId());
                f.setReason("品质问题");
                var row = new OrderReturnItemForm();
                row.setOrderItemId(o.getItems().getFirst().getItemId());
                row.setRequestedQuantity("1.0000");
                f.setItems(List.of(row));
                var r = returns.create(f, prefix + "r");
                expectCode(() -> returns.create(f, prefix + "r2"), 40969);
                var approve = new OrderReturnApproveForm();
                approve.setReturnId(r.getReturnId());
                approve.setVersion(r.getVersion());
                var approved = new OrderReturnApproveItemForm();
                approved.setOrderItemId(row.getOrderItemId());
                approved.setApprovedQuantity("0.5000");
                approve.setItems(List.of(approved));
                r = returns.approve(approve, prefix + "approve");
                assertThat(r.getStatus()).isEqualTo("APPROVED");
                var q = new OrderRefundQueryForm();
                q.setOrderId(o.getOrderId());
                q.setPageNum(1L);
                q.setPageSize(20L);
                var refund = refunds.query(q).getList().getFirst();
                assertThat(refund.getRefundAmount()).isEqualByComparingTo("0.6000");
                var complete = new OrderRefundCompleteForm();
                complete.setRefundId(refund.getRefundId());
                complete.setVersion(refund.getVersion());
                complete.setExternalReference(prefix);
                assertThat(refunds.complete(complete, prefix + "complete").getStatus()).isEqualTo("COMPLETED");
                assertThat(refunds.complete(complete, prefix + "complete").getStatus()).isEqualTo("COMPLETED");
                expectCode(() -> refunds.complete(complete, prefix + "staleRefund"), 40921);
                expectCode(() -> returns.approve(approve, prefix + "staleReturn"), 40921);
                assertThat(query.detail(o.getOrderId()).getStatus()).isEqualTo("CONFIRMED");

                // return / refund 与 cancellation 一样要留操作日志。建单 + 审批 = 两条 RETURN，
                // 退款只有一条 REFUND —— 上面那笔 complete 用同一个 Idempotency-Key 调了两次，
                // 第二次是重放，因此日志条数同时钉住「重放不重复记日志」。
                assertThat(logCount(o.getOrderId(), "RETURN")).isEqualTo(2);
                assertThat(logCount(o.getOrderId(), "REFUND")).isEqualTo(1);
            }

            @Test
            @DisplayName("订单已有资金分摊计划时，退款完成必须让位给分摊路径而不是整笔回滚")
            void refundCompletionSurvivesAnExistingAllocationPlan() {
                var o = confirmed(customer(), newOnShelfSku("AP"));
                var f = new OrderReturnAddForm();
                f.setOrderId(o.getOrderId());
                f.setReason("品质问题");
                var row = new OrderReturnItemForm();
                row.setOrderItemId(o.getItems().getFirst().getItemId());
                row.setRequestedQuantity("1.0000");
                f.setItems(List.of(row));
                var r = returns.create(f, prefix + "ap-return");
                var approve = new OrderReturnApproveForm();
                approve.setReturnId(r.getReturnId());
                approve.setVersion(r.getVersion());
                var approved = new OrderReturnApproveItemForm();
                approved.setOrderItemId(row.getOrderItemId());
                approved.setApprovedQuantity("0.5000");
                approve.setItems(List.of(approved));
                returns.approve(approve, prefix + "ap-approve");

                var q = new OrderRefundQueryForm();
                q.setOrderId(o.getOrderId());
                q.setPageNum(1L);
                q.setPageSize(20L);
                var refund = refunds.query(q).getList().getFirst();

                // 模拟「该订单此前已按新分摊路径退过款」：分摊计划只可能来自更早那笔已完成的退款，
                // 而旧钱包返还路径看到计划就必须整体让位（否则同一笔本金会被退两次）。
                Long customerId = jdbc.queryForObject("SELECT customer_id FROM xsy_sales_order WHERE id = ?",
                        Long.class, o.getOrderId());
                Long settlementCustomerId = jdbc.queryForObject(
                        "SELECT settlement_customer_id FROM xsy_sales_order WHERE id = ?", Long.class, o.getOrderId());
                jdbc.update("INSERT INTO xsy_refund_funding_allocation (order_refund_id, order_id, customer_id, "
                        + "settlement_customer_id, source_method, source_id, source_principal, allocated_amount, status, "
                        + "source_succeeded_at, sort_order, created_by, updated_by) "
                        + "VALUES (?, ?, ?, ?, 'BALANCE', 1, 1.0000, 1.0000, 'PENDING', CURRENT_TIMESTAMP, 0, '1:1', '1:1')",
                        refund.getRefundId(), o.getOrderId(), customerId, settlementCustomerId);

                // 完成必须成功：让位要发生在这里的前置分流，不能靠兜异常 —— refundOnCompletion 是
                // MANDATORY 传播，异常一旦逃出就会把本事务标记成 rollback-only，这笔退款完成会整笔回滚。
                var complete = new OrderRefundCompleteForm();
                complete.setRefundId(refund.getRefundId());
                complete.setVersion(refund.getVersion());
                complete.setExternalReference(prefix);
                assertThat(refunds.complete(complete, prefix + "ap-complete").getStatus()).isEqualTo("COMPLETED");
            }

            @Test
            void rejectedReturnIsAuditableOnTheOrder() {
                var o = confirmed(customer(), newOnShelfSku("RJ"));
                var f = new OrderReturnAddForm();
                f.setOrderId(o.getOrderId());
                f.setReason("错发");
                var row = new OrderReturnItemForm();
                row.setOrderItemId(o.getItems().getFirst().getItemId());
                row.setRequestedQuantity("1.0000");
                f.setItems(List.of(row));
                var r = returns.create(f, prefix + "rj");
                var decision = new OrderReturnDecisionForm();
                decision.setReturnId(r.getReturnId());
                decision.setVersion(r.getVersion());
                decision.setDecisionReason("不在受理范围");
                assertThat(returns.reject(decision, prefix + "reject").getStatus()).isEqualTo("REJECTED");
                assertThat(logCount(o.getOrderId(), "RETURN")).isEqualTo(2);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_order_operation_log WHERE order_id = ? AND operation_type = 'RETURN'"
                                + " AND after_data ->> 'status' = 'REJECTED'",
                        Integer.class, o.getOrderId())).as("驳回这条要把状态迁移记进日志").isEqualTo(1);
            }

            private int logCount(Long orderId, String operation) {
                return jdbc.queryForObject("SELECT count(*) FROM xsy_order_operation_log WHERE order_id = ?"
                        + " AND operation_type = ?", Integer.class, orderId, operation);
            }

            @Test
            void cancelAndDraftDeletionRespectStateAndVersions() {
                var c = customer();
                var sku = newOnShelfSku("D");
                var o = orders.create(form(c, sku), prefix);
                var deleteCustomer = new CustomerDeleteForm();
                deleteCustomer.setCustomerId(c);
                deleteCustomer.setVersion(1);
                expectCode(() -> customers.delete(deleteCustomer), 40939);
                orders.delete(version(o));
                assertThat(jdbc.queryForObject("SELECT deleted FROM xsy_sales_order WHERE id=?", Boolean.class, o.getOrderId())).isTrue();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_sales_order_item WHERE order_id=? AND NOT deleted", Integer.class, o.getOrderId())).isZero();
                orders.delete(version(o));
                o = orders.create(form(c, sku), prefix + "other");
                var cancel = new OrderCancelForm();
                cancel.setOrderId(o.getOrderId());
                cancel.setVersion(o.getVersion());
                cancel.setReason("客户取消");
                assertThat(orders.cancel(cancel, prefix + "cancel").getStatus()).isEqualTo("CANCELLED");
            }

            @Test
            @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
            void concurrentDuplicateCreatesAndReturnQuotaHaveOneWinner() throws Exception {
                var c = customer();
                var sku = newOnShelfSku("CONCURRENT");
                var f = form(c, sku);
                // 本用例真提交：键必须每次运行唯一，否则重跑会撞上上一次留下的防重复处理记录（请求指纹必然不同）。
                var sameKey = prefix + "same-" + UUID.randomUUID();
                var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
                try {
                    var barrier = new java.util.concurrent.CyclicBarrier(2);
                    java.util.concurrent.Callable<SalesOrderDetailVO> create = () -> {
                        // 每个线程独立请求体：共用同一个对象时，写入路径对它的规范化会让另一线程算出的指纹漂移。
                        var request = form(c, sku);
                        operator();
                        try {
                            barrier.await();
                            return orders.create(request, sameKey);
                        } finally {
                            com.xsy.scm.base.common.util.XsyRequestUtil.remove();
                        }
                    };
                    var first = pool.submit(create);
                    var second = pool.submit(create);
                    assertThat(first.get().getOrderId()).isEqualTo(second.get().getOrderId());
                    var o = confirmed(c, sku);
                    var r = new OrderReturnAddForm();
                    r.setOrderId(o.getOrderId());
                    r.setReason("并发退货");
                    var i = new OrderReturnItemForm();
                    i.setOrderItemId(o.getItems().getFirst().getItemId());
                    i.setRequestedQuantity("1.0000");
                    r.setItems(List.of(i));
                    java.util.concurrent.Callable<Integer> attempt = () -> {
                        operator();
                        try {
                            barrier.await();
                            returns.create(r, UUID.randomUUID().toString());
                            return 0;
                        } catch (com.xsy.scm.common.exception.ScmBusinessException e) {
                            return e.getErrorCode().getCode();
                        } finally {
                            com.xsy.scm.base.common.util.XsyRequestUtil.remove();
                        }
                    };
                    var one = pool.submit(attempt);
                    var two = pool.submit(attempt);
                    assertThat(List.of(one.get(), two.get())).containsExactlyInAnyOrder(0, 40969);
                } finally {
                    pool.shutdownNow();
                }
            }

            private void operator() {
                var e = new com.xsy.scm.admin.module.system.login.domain.RequestEmployee();
                e.setEmployeeId(1L);
                e.setActualName("W4 IT");
                e.setUserType(com.xsy.scm.base.common.enumeration.UserTypeEnum.ADMIN_EMPLOYEE);
                com.xsy.scm.base.common.util.XsyRequestUtil.setRequestUser(e);
            }

            @Test
            void snapshotsOrderingManualLockAndItemVersionArePreserved() {
                var c = customer();
                var first = newOnShelfSku("S1");
                var second = newOnShelfSku("S2");
                var f = form(c, first);
                var i = f.getItems().getFirst();
                i.setSortOrder(4);
                i.setManualPriceOverride(true);
                i.setUnitPrice("2.1234");
                i.setOverrideReason("客户协商");
                var j = new SalesOrderItemForm();
                j.setSkuId(second);
                j.setOrderedQuantity("1.0000");
                j.setManualPriceOverride(false);
                j.setSortOrder(1);
                f.getItems().add(j);
                var o = orders.create(f, prefix);
                assertThat(o.getItems()).extracting(SalesOrderItemVO::getSkuId).containsExactly(second, first);
                assertThat(o.getItems().getFirst().getSpecValuesSnapshot()).isNotEmpty();
                assertThat(o.getItems().getFirst().getSaleUnitSnapshot()).isEqualTo("kg");
                var manual = o.getItems().getLast();
                assertThat(manual.getLockedUnitPrice()).isEqualByComparingTo("2.1234");
                assertThat(manual.getLockedPriceSource()).isEqualTo("OVERRIDE");
                var a = new OrderActualQuantityForm();
                a.setOrderId(o.getOrderId());
                a.setItemId(manual.getItemId());
                a.setVersion(999);
                a.setActualQuantity("1.0000");
                a.setReason("实测");
                expectCode(() -> orders.actualQuantity(a, prefix + "bad"), 40965);
                assertThat(o.getAddress().getAddress()).isEqualTo("测试地址");
            }

            @Test
            void rejectedAndCancelledReturnsReleaseQuotaAndStaleApprovalFails() {
                var o = confirmed(customer(), newOnShelfSku("DECIDE"));
                var f = new OrderReturnAddForm();
                f.setOrderId(o.getOrderId());
                f.setReason("退货");
                var i = new OrderReturnItemForm();
                i.setOrderItemId(o.getItems().getFirst().getItemId());
                i.setRequestedQuantity("1.0000");
                f.setItems(List.of(i));
                var r = returns.create(f, prefix + "r1");
                var decision = new OrderReturnDecisionForm();
                decision.setReturnId(r.getReturnId());
                decision.setVersion(999);
                decision.setDecisionReason("审核");
                expectCode(() -> returns.reject(decision, prefix + "stale"), 40921);
                decision.setVersion(r.getVersion());
                assertThat(returns.reject(decision, prefix + "reject").getStatus()).isEqualTo("REJECTED");
                r = returns.create(f, prefix + "r2");
                decision.setReturnId(r.getReturnId());
                decision.setVersion(r.getVersion());
                assertThat(returns.cancel(decision, prefix + "cancel").getStatus()).isEqualTo("CANCELLED");
                assertThat(returns.create(f, prefix + "r3").getStatus()).isEqualTo("PENDING");
            }

            @Test
            @DisplayName("商品行备注写入数据库并按行读回：修改能覆盖、清空能生效，不写进整单备注")
            void itemRemarkRoundTripsPerLine() {
                var c = customer();
                var f = form(c, newOnShelfSku("IREMARK"));
                f.getItems().getFirst().setRemark("去皮");
                var o = orders.create(f, prefix + "ir-create");

                // 读的是实体 resultMap：行备注必须显式映射，否则 SELECT * 取回也落不到 VO
                assertThat(o.getItems().getFirst().getRemark()).isEqualTo("去皮");
                assertThat(o.getRemark()).as("整单备注另存").isEqualTo("测试订单");

                var u = new SalesOrderUpdateForm();
                org.springframework.beans.BeanUtils.copyProperties(f, u);
                u.setOrderId(o.getOrderId());
                u.setVersion(o.getVersion());
                u.getItems().getFirst().setItemId(o.getItems().getFirst().getItemId());
                u.getItems().getFirst().setVersion(o.getItems().getFirst().getVersion());
                u.getItems().getFirst().setRemark("切段、分袋");
                var updated = orders.update(u);
                assertThat(updated.getItems().getFirst().getRemark()).isEqualTo("切段、分袋");

                u.setVersion(updated.getVersion());
                u.getItems().getFirst().setVersion(updated.getItems().getFirst().getVersion());
                u.getItems().getFirst().setRemark(null);
                assertThat(orders.update(u).getItems().getFirst().getRemark()).as("清空行备注要真的清掉").isNull();
                assertThat(query.detail(o.getOrderId()).getRemark()).isEqualTo("测试订单");
            }
        }

        @Nested

        class ScmBundleOutboundPgIT {
            @Autowired
            private InventoryReservationService reservationService;

            @Autowired
            private InventoryFulfillmentService fulfillmentService;

            @Autowired
            private InventorySalesReturnQueryService returnQueryService;

            @Test
            void dispatchConsumesTwoFrozenComponentsAndLeavesBundleSkuUntouched() {
                // 出库仓库必须在授权范围内：测试使用正式数据范围允许的管理员身份。
                ((RequestEmployee) XsyRequestUtil.getRequestUser()).setAdministratorFlag(true);
                Long warehouse = jdbc.queryForObject(
                        "SELECT id FROM xsy_warehouse WHERE deleted=FALSE ORDER BY id LIMIT 1", Long.class);
                Long bundleSku = newOnShelfSku("OUT-BUNDLE");
                Long firstSku = newOnShelfSku("OUT-A");
                Long secondSku = newOnShelfSku("OUT-B");
                // 出库行必须能追溯到真实存在的订单行（xsy_lock_trace_outbound_source_item 触发器），
                // 因此订单与订单行走真实插入，而不是只造内存实体。
                long orderId = seedOrder();
                long orderItemId = seedOrderItem(orderId, bundleSku);
                long firstComponentId = 998803L;
                long secondComponentId = 998804L;
                long routeId = 998805L;

                Long firstBalanceId = seedBalance(warehouse, firstSku, "50.0000", "5.0000");
                Long secondBalanceId = seedBalance(warehouse, secondSku, "40.0000", "8.0000");
                SalesOrderItemEntity parent = new SalesOrderItemEntity();
                parent.setId(orderItemId);
                parent.setSkuId(bundleSku);
                parent.setActualQuantity(new BigDecimal("2.0000"));
                var first = component(orderItemId, bundleSku, firstComponentId, firstSku, "2.0000", "5.0000");
                var second = component(orderItemId, bundleSku, secondComponentId, secondSku, "3.0000", "2.0000");
                var components = List.of(first, second);

                reservationService.reserveForSalesOrder(warehouse, orderId,
                        OrderBundleReservationPlanner.lines(components, List.of(parent)), OffsetDateTime.now());
                assertThat(reserved(firstBalanceId)).isEqualByComparingTo("20.0000");
                assertThat(reserved(secondBalanceId)).isEqualByComparingTo("12.0000");

                var dispatched = fulfillmentService.dispatchOutbound(new InventoryFulfillmentService.Command(
                        routeId, warehouse, OffsetDateTime.now(), "bundle-it",
                        List.of(
                                new InventoryFulfillmentService.Line(orderId, orderItemId, firstSku,
                                        new BigDecimal("4.0000"), "kg", new BigDecimal("5.0000"),
                                        firstComponentId, bundleSku, new BigDecimal("2.0000")),
                                new InventoryFulfillmentService.Line(orderId, orderItemId, secondSku,
                                        new BigDecimal("6.0000"), "kg", new BigDecimal("2.0000"),
                                        secondComponentId, bundleSku, new BigDecimal("2.0000")))));

                assertThat(dispatched.outboundId()).isNotNull();
                assertThat(dispatched.shippedLineCount()).isEqualTo(1);
                assertThat(jdbc.queryForObject(
                        "SELECT quantity FROM xsy_inventory_balance WHERE id=?", BigDecimal.class, firstBalanceId))
                        .isEqualByComparingTo("30.0000");
                assertThat(jdbc.queryForObject(
                        "SELECT quantity FROM xsy_inventory_balance WHERE id=?", BigDecimal.class, secondBalanceId))
                        .isEqualByComparingTo("28.0000");
                assertThat(reserved(firstBalanceId)).isEqualByComparingTo("0.0000");
                assertThat(reserved(secondBalanceId)).isEqualByComparingTo("0.0000");
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_inventory_reservation WHERE source_document_id=? "
                        + "AND source_document_type='ORDER_BUNDLE_COMPONENT' AND status='CONSUMED'",
                        Integer.class, orderId)).isEqualTo(2);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_inventory_movement "
                        + "WHERE movement_type='SALES_OUT' AND sku_id=?",
                        Integer.class, bundleSku)).isZero();
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_inventory_outbound_item "
                        + "WHERE outbound_id=? AND bundle_component_id IS NOT NULL",
                        Integer.class, dispatched.outboundId())).isEqualTo(2);

                var movements = returnQueryService.bundleSalesOutAllocations(orderItemId);
                assertThat(movements).hasSize(2);
                assertThat(movements).extracting(item -> item.getBundleComponentId())
                        .containsExactlyInAnyOrder(firstComponentId, secondComponentId);
                assertThat(movements).allSatisfy(item -> assertThat(item.getUnitCost()).isNotNull());
            }

            private long seedOrder() {
                return jdbc.queryForObject("""
                        INSERT INTO xsy_sales_order (order_no, customer_id, customer_code_snapshot, customer_name_snapshot,
                          settle_mode_snapshot, settlement_customer_id, settlement_customer_name_snapshot, status)
                        VALUES (?, 1, 'C-BUNDLE', ?, 'INDEPENDENT', 1, ?, 'CONFIRMED') RETURNING id
                        """, Long.class, prefix + "-BO-" + java.util.UUID.randomUUID(), prefix + "组合客户", prefix + "组合客户");
            }

            private long seedOrderItem(long orderId, Long skuId) {
                return jdbc.queryForObject("""
                        INSERT INTO xsy_sales_order_item (order_id, spu_id, sku_id, spu_code_snapshot,
                          product_name_snapshot, sku_code_snapshot, spec_name_snapshot, sale_unit_snapshot,
                          product_type_snapshot, ordered_quantity, actual_quantity)
                        VALUES (?, (SELECT spu_id FROM xsy_product_sku WHERE id = ?), ?, 'SPU-BUNDLE', '组合商品',
                          'SKU-BUNDLE', '默认规格', '件', 'STANDARD', 2.0000, 2.0000) RETURNING id
                        """, Long.class, orderId, skuId, skuId);
            }

            private Long seedBalance(Long warehouse, Long sku, String quantity, String cost) {
                return jdbc.queryForObject(
                        "INSERT INTO xsy_inventory_balance (warehouse_id, sku_id, unit, quantity, reserved_quantity, avg_cost)"
                        + " VALUES (?,?, 'kg', ?::numeric, 0.0000, ?::numeric) RETURNING id",
                        Long.class, warehouse, sku, quantity, cost);
            }

            private BigDecimal reserved(Long balanceId) {
                return jdbc.queryForObject("SELECT reserved_quantity FROM xsy_inventory_balance WHERE id=?",
                        BigDecimal.class, balanceId);
            }

            private static OrderBundleComponentEntity component(Long orderItemId, Long bundleSku, Long componentId,
                    Long skuId, String unitsPerBundle, String stockFactor) {
                var component = new OrderBundleComponentEntity();
                component.setId(componentId);
                component.setOrderItemId(orderItemId);
                component.setBundleSkuId(bundleSku);
                component.setComponentSkuId(skuId);
                component.setComponentSaleQuantitySnapshot(new BigDecimal("2.0000")
                        .multiply(new BigDecimal(unitsPerBundle)));
                component.setQuantityPerBundleSaleUnit(new BigDecimal(unitsPerBundle));
                component.setComponentStockUnitSnapshot("kg");
                component.setStockUnitsPerComponentSaleUnit(new BigDecimal(stockFactor));
                return component;
            }
        }

        @Nested

        class ScmBundleReservationPgIT {
            @Autowired
            private InventoryReservationService reservationService;

            @Test
            void explicitBundleReservationConsumesComponentStockButNeverBundleSku() {
                Long warehouseId = jdbc.queryForObject(
                        "SELECT id FROM xsy_warehouse WHERE deleted=FALSE ORDER BY id LIMIT 1", Long.class);
                Long componentSku = newOnShelfSku("RESV-COMP");
                Long bundleSku = newOnShelfSku("RESV-BUNDLE");
                Long balanceId = jdbc.queryForObject(
                        "INSERT INTO xsy_inventory_balance (warehouse_id, sku_id, unit, quantity, reserved_quantity,"
                        + " avg_cost) VALUES (?,?, 'kg', 30.0000, 0.0000, 5.0000) RETURNING id",
                        Long.class, warehouseId, componentSku);

                SalesOrderItemEntity parent = new SalesOrderItemEntity();
                parent.setId(99881L);
                parent.setSkuId(bundleSku);
                parent.setActualQuantity(new BigDecimal("2.0000"));
                OrderBundleComponentEntity frozen = new OrderBundleComponentEntity();
                frozen.setId(99882L);
                frozen.setOrderItemId(parent.getId());
                frozen.setBundleSkuId(bundleSku);
                frozen.setComponentSkuId(componentSku);
                frozen.setComponentSaleQuantitySnapshot(new BigDecimal("4.0000"));
                frozen.setQuantityPerBundleSaleUnit(new BigDecimal("2.0000"));
                frozen.setComponentStockUnitSnapshot("kg");
                frozen.setStockUnitsPerComponentSaleUnit(new BigDecimal("5.0000"));

                var lines = OrderBundleReservationPlanner.lines(List.of(frozen), List.of(parent));
                reservationService.reserveForSalesOrder(warehouseId, 99880L, lines, OffsetDateTime.now());

                assertThat(jdbc.queryForObject(
                        "SELECT reserved_quantity FROM xsy_inventory_balance WHERE id=?",
                        BigDecimal.class, balanceId)).isEqualByComparingTo("20.0000");
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_inventory_reservation "
                        + "WHERE source_document_type='ORDER_BUNDLE_COMPONENT' AND source_document_id=99880 "
                        + "AND source_document_item_id=99882 AND sku_id=? AND quantity=20.0000",
                        Integer.class, componentSku)).isEqualTo(1);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_inventory_reservation WHERE sku_id=?",
                        Integer.class, bundleSku)).isZero();
            }
        }

        @Nested
        @DisplayName("P0-F 订单与客户数据范围（PG IT）")
        class ScmOrderCustomerDataScopePgIT {
            @Autowired
            private CustomerService customerService;

            @Autowired
            private CustomerQueryService customerQueryService;

            @Autowired
            private SalesOrderService salesOrderService;

            @Autowired
            private SalesOrderQueryService salesOrderQueryService;

            @Autowired
            private SalesOrderController salesOrderController;

            @Autowired
            private PriceResolveController priceResolveController;

            @Autowired
            private ScmDataScopeService dataScopeService;

            @Autowired
            private OrderReturnService orderReturnService;

            @Autowired
            private OrderRefundService orderRefundService;

            @Autowired
            private org.apache.ibatis.session.SqlSession session;

            /** 两个普通销售：各自拥有客户与订单，互不可见。 */
            private Long sellerA;
            private Long sellerB;

            @BeforeEach
            void setUpEmployees() {
                sellerA = newEmployee("A");
                sellerB = newEmployee("B");
            }

            // ==================== 1. 客户列表 / 详情 ====================

            @Test
            @DisplayName("客户列表：无全量范围时只返回 seller_id = 本人的行")
            void customerListIsNarrowedToOwnSeller() {
                Long customerOfA = createCustomer("CA", sellerA);
                Long customerOfB = createCustomer("CB", sellerB);

                assertThat(as(sellerA, Set.of(), () -> customerIds(customerList())))
                        .contains(customerOfA).doesNotContain(customerOfB);
                assertThat(as(sellerB, Set.of(), () -> customerIds(customerList())))
                        .contains(customerOfB).doesNotContain(customerOfA);
            }

            @Test
            @DisplayName("未分配客户：普通销售不可见，持 scm:customer:scope:all:query 者可见")
            void unassignedCustomerVisibleOnlyToAllScope() {
                Long unassigned = addCustomer(sellerA, Set.of(ScmDataScopeService.CUSTOMER_ASSIGN_PERM), "CU", null);
                assertThat(sellerIdOf(unassigned)).isNull();

                assertThat(as(sellerA, Set.of(), () -> customerIds(customerList()))).doesNotContain(unassigned);
                assertThat(as(sellerB, Set.of(), () -> customerIds(customerList()))).doesNotContain(unassigned);
                assertThat(as(sellerB, Set.of(ScmDataScopeService.CUSTOMER_ALL_PERM), () -> customerIds(customerList())))
                        .contains(unassigned);
            }

            @Test
            @DisplayName("客户详情：别人的客户 30005，自己的照常返回；不存在仍是 40430")
            void customerDetailDeniesOutOfScopeRead() {
                Long customerOfA = createCustomer("DA", sellerA);

                assertThat(as(sellerA, Set.of(), () -> customerQueryService.detail(customerOfA).getCustomerId()))
                        .isEqualTo(customerOfA);
                assertNoPermission(() -> as(sellerB, Set.of(), () -> customerQueryService.detail(customerOfA)));

                // 越权判定不得倒过来把「不存在」也报成无权限：那会让探测 id 与探测权限混成一种结果
                expectCode(() -> as(sellerB, Set.of(), () -> customerQueryService.detail(-1L)), 40430);

                // 全量范围者能读别人的详情（销售主管 / 财务规则）
                assertThat(as(sellerB, Set.of(ScmDataScopeService.CUSTOMER_ALL_PERM),
                        () -> customerQueryService.detail(customerOfA).getCustomerId())).isEqualTo(customerOfA);
            }

            @Test
            @DisplayName("最近成交价与两个后台价格预览入口按客户范围拒绝越权，并允许管理员全量读取")
            void priceReadsRequireCustomerScope() {
                Long customerOfA = createTradableCustomer("PR", sellerA);
                Long sku = newOnShelfSku("PR");
                PriceResolveForm form = priceForm(customerOfA, sku);

                assertNoPermission(() -> as(sellerB, Set.of(),
                        () -> salesOrderQueryService.recentPrices(customerOfA, sku, 5)));
                assertNoPermission(() -> as(sellerB, Set.of(), () -> salesOrderController.preview(form)));
                assertNoPermission(() -> as(sellerB, Set.of(), () -> priceResolveController.resolve(form)));

                assertThat(as(sellerA, Set.of(), () -> salesOrderQueryService.recentPrices(customerOfA, sku, 5))).isEmpty();
                assertThat(as(sellerA, Set.of(), () -> salesOrderController.preview(form).getData().customerId()))
                        .isEqualTo(customerOfA);
                assertThat(as(sellerB, Set.of(ScmDataScopeService.CUSTOMER_ALL_PERM),
                        () -> priceResolveController.resolve(form).getData().customerId())).isEqualTo(customerOfA);

                RequestUser previous = XsyRequestUtil.getRequestUser();
                try {
                    RequestEmployee administrator = new RequestEmployee();
                    administrator.setEmployeeId(sellerB);
                    administrator.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                    administrator.setAdministratorFlag(true);
                    administrator.setDepartmentId(1L);
                    XsyRequestUtil.setRequestUser(administrator);
                    assertThat(dataScopeService.resolve().getCustomerSellerScope().isAll()).isTrue();
                    assertThat(salesOrderQueryService.recentPrices(customerOfA, sku, 5)).isEmpty();
                    assertThat(salesOrderController.preview(form).getData().customerId()).isEqualTo(customerOfA);
                    assertThat(priceResolveController.resolve(form).getData().customerId()).isEqualTo(customerOfA);
                } finally {
                    XsyRequestUtil.remove();
                    XsyRequestUtil.setRequestUser(previous);
                }
            }

            @Test
            @DisplayName("没有登录员工时客户范围为空，三个价格读取入口全部校验失败时拒绝操作")
            void priceReadsFailClosedWithEmptyScope() {
                Long customerOfA = createTradableCustomer("PE", sellerA);
                Long sku = newOnShelfSku("PE");
                PriceResolveForm form = priceForm(customerOfA, sku);
                RequestUser previous = XsyRequestUtil.getRequestUser();
                try {
                    XsyRequestUtil.remove();
                    assertThat(dataScopeService.resolve().getCustomerSellerScope().isEmpty()).isTrue();
                    assertNoPermission(() -> salesOrderQueryService.recentPrices(customerOfA, sku, 5));
                    assertNoPermission(() -> salesOrderController.preview(form));
                    assertNoPermission(() -> priceResolveController.resolve(form));
                } finally {
                    XsyRequestUtil.setRequestUser(previous);
                }
            }

            @Test
            @DisplayName("集团客户与 settle_mode=GROUP 不扩大可见范围")
            void groupRelationDoesNotWidenVisibility() {
                Long groupId = addCustomer(sellerA, Set.of(), "GP", null, form -> {
                    form.setSettleMode("GROUP");
                });
                // 乙名下的子公司挂在甲名下的集团上：归属只按本行 seller_id 判定，不做上溯/下钻展开
                Long childId = addCustomer(sellerB, Set.of(), "GC", null, form -> form.setParentCustomerId(groupId));

                assertThat(as(sellerB, Set.of(), () -> customerIds(customerList())))
                        .contains(childId).doesNotContain(groupId);
                assertNoPermission(() -> as(sellerB, Set.of(), () -> customerQueryService.detail(groupId)));
                // 常购商品取的是成交价与用量，同属客户读边界
                assertNoPermission(() -> as(sellerB, Set.of(),
                        () -> customerQueryService.frequentSkus(groupId, 90, 20)));

                assertThat(as(sellerA, Set.of(), () -> customerIds(customerList())))
                        .contains(groupId).doesNotContain(childId);
                // 列表仍会补全上级名称（展示用），但这不等于能读上级客户的详情
                assertThat(as(sellerB, Set.of(), () -> customerQueryService.detail(childId).getParentCustomerName()))
                        .isNotBlank();
            }

            // ==================== 2. 写入侧归属收口 ====================

            @Test
            @DisplayName("无分配权新建客户：seller_id 强制为本人，伪造的 sellerId 一律忽略")
            void createForcesSellerToCallerAndIgnoresSpoofedValue() {
                Long spoofed = addCustomer(sellerA, Set.of(), "W1", sellerB);

                assertThat(sellerIdOf(spoofed)).isEqualTo(sellerA);
                assertThat(as(sellerA, Set.of(), () -> customerIds(customerList()))).contains(spoofed);
                assertThat(as(sellerB, Set.of(), () -> customerIds(customerList()))).doesNotContain(spoofed);
            }

            @Test
            @DisplayName("编辑客户改派必须有分配权：无权拒绝，有权同步可见范围")
            void updateReassignsSellerOnlyWithAssignmentPermission() {
                Long customerId = createCustomer("W2", sellerA);

                assertThatThrownBy(() -> as(sellerA, Set.of(), () -> {
                    CustomerUpdateForm update = updateForm(customerId, 0);
                    update.setSellerId(sellerB);
                    customerService.update(update);
                    return null;
                })).isInstanceOf(cn.dev33.satoken.exception.NotPermissionException.class);
                assertThat(sellerIdOf(customerId)).isEqualTo(sellerA);

                as(sellerA, Set.of(ScmDataScopeService.CUSTOMER_ASSIGN_PERM), () -> {
                    CustomerUpdateForm update = updateForm(customerId, 0);
                    update.setSellerId(sellerB);
                    customerService.update(update);
                    return null;
                });

                assertThat(sellerIdOf(customerId)).isEqualTo(sellerB);
                assertThat(as(sellerB, Set.of(), () -> customerIds(customerList()))).contains(customerId);
                assertThat(as(sellerA, Set.of(), () -> customerIds(customerList()))).doesNotContain(customerId);
            }

            @Test
            @DisplayName("改派走独立端点：权限码已种入菜单，带分配权可移动归属，版本过期 40921")
            void reassignSellerEndpointIsTheOnlyMovePath() throws Exception {
                Long customerId = createCustomer("W3", sellerA);

                Method endpoint = CustomerController.class.getDeclaredMethod("reassignSeller",
                        CustomerSellerReassignForm.class);
                SaCheckPermission permission = endpoint.getAnnotation(SaCheckPermission.class);
                assertThat(permission).as("改派端点必须自带权限注解，不能只靠 Service 判断").isNotNull();
                assertThat(permission.value()).containsExactly(ScmDataScopeService.CUSTOMER_ASSIGN_PERM);

                // 权限点确实存在且只种了一次，并落在「销售主管有、普通销售没有」的角色上（V55 + V56）
                assertThat(jdbc.queryForObject("SELECT count(*) FROM t_menu WHERE api_perms = ?", Integer.class,
                        ScmDataScopeService.CUSTOMER_ASSIGN_PERM)).isEqualTo(1);
                assertThat(roleHoldsPermission("SCM_SALES", ScmDataScopeService.CUSTOMER_ASSIGN_PERM)).isFalse();
                assertThat(roleHoldsPermission("SCM_SALES_LEAD", ScmDataScopeService.CUSTOMER_ASSIGN_PERM)).isTrue();

                // 版本比对与 update 同一套规则：陈旧改派必须撞 40921，而不是覆盖别人的编辑
                expectCode(() -> as(sellerA, Set.of(ScmDataScopeService.CUSTOMER_ASSIGN_PERM), () -> {
                    customerService.reassignSeller(reassignForm(customerId, sellerB, 99));
                    return null;
                }), 40921);

                as(sellerB, Set.of(ScmDataScopeService.CUSTOMER_ASSIGN_PERM), () -> {
                    customerService.reassignSeller(reassignForm(customerId, sellerB, sellerVersion(customerId)));
                    return null;
                });

                assertThat(sellerIdOf(customerId)).isEqualTo(sellerB);
                assertThat(as(sellerB, Set.of(), () -> customerIds(customerList()))).contains(customerId);
                assertThat(as(sellerA, Set.of(), () -> customerIds(customerList()))).doesNotContain(customerId);
            }

            @Test
            @DisplayName("收回为未分配：只有分配权者可执行，执行后连本人也读不到")
            void unassignClearsOwnership() {
                Long customerId = createCustomer("W4", sellerA);

                as(sellerA, Set.of(ScmDataScopeService.CUSTOMER_ASSIGN_PERM), () -> {
                    customerService.reassignSeller(reassignForm(customerId, null, sellerVersion(customerId)));
                    return null;
                });

                assertThat(sellerIdOf(customerId)).isNull();
                assertThat(as(sellerA, Set.of(), () -> customerIds(customerList()))).doesNotContain(customerId);
            }

            @Test
            @DisplayName("无分配权者不能对读不到的客户开单：建单入口按同一范围拒绝")
            void orderCreateRejectsForeignCustomer() {
                Long customerOfB = createTradableCustomer("W5", sellerB);
                Long sku = newOnShelfSku("W5");

                assertNoPermission(() -> as(sellerA, Set.of(),
                        () -> salesOrderService.create(orderForm(customerOfB, sku), prefix + "-w5")));

                // 负责人自己照常能开单，且订单负责人取自当时的客户资料
                Long orderId = as(sellerB, Set.of(),
                        () -> salesOrderService.create(orderForm(customerOfB, sku), prefix + "-w5b").getOrderId());
                assertThat(sellerIdOfOrder(orderId)).isEqualTo(sellerB);
            }

            @Test
            @DisplayName("分配权隐含开单例外：主管把客户指定给别人后可以替他录单")
            void assignRightAllowsOrderForOtherSellersCustomer() {
                Long customerOfB = createTradableCustomer("W6", sellerB);
                Long sku = newOnShelfSku("W6");

                Long orderId = as(sellerA, Set.of(ScmDataScopeService.CUSTOMER_ASSIGN_PERM),
                        () -> salesOrderService.create(orderForm(customerOfB, sku), prefix + "-w6").getOrderId());
                assertThat(sellerIdOfOrder(orderId)).isEqualTo(sellerB);
            }

            // ==================== 3. 订单族列表与详情 ====================

            @Test
            @DisplayName("订单列表：无全量范围时只返回 seller_id = 本人的行；未分配单只对全量范围可见")
            void orderListIsNarrowedToOwnSeller() {
                Long customer = createCustomer("OA", sellerA);
                Long orderOfA = insertOrder(customer, "A", sellerA);
                Long orderOfB = insertOrder(customer, "B", sellerB);
                Long unassigned = insertOrder(customer, "N", null);

                assertThat(as(sellerA, Set.of(), () -> orderIds(orderList()))).containsExactly(orderOfA);
                assertThat(as(sellerA, Set.of(ScmDataScopeService.ORDER_ALL_PERM), () -> orderIds(orderList())))
                        .contains(orderOfA, orderOfB, unassigned);
            }

            @Test
            @DisplayName("订单列表的 sellerId 筛选只能收窄，不能扩大")
            void sellerIdFilterNarrowsOnly() {
                Long customer = createCustomer("OF", sellerA);
                Long orderOfA = insertOrder(customer, "A", sellerA);
                Long orderOfB = insertOrder(customer, "B", sellerB);

                // 普通销售按别人的业务员筛选：范围谓词仍然 AND 在上面，结果必须为空
                assertThat(as(sellerA, Set.of(), () -> orderIds(salesOrderQueryService.query(sellerFilter(sellerB)))))
                        .isEmpty();
                // 同一筛选交给全量范围者：收窄生效，只剩乙的单
                assertThat(as(sellerA, Set.of(ScmDataScopeService.ORDER_ALL_PERM),
                        () -> orderIds(salesOrderQueryService.query(sellerFilter(sellerB))))).containsExactly(orderOfB);
                assertThat(as(sellerA, Set.of(ScmDataScopeService.ORDER_ALL_PERM),
                        () -> orderIds(salesOrderQueryService.query(sellerFilter(sellerA))))).containsExactly(orderOfA);
            }

            @Test
            @DisplayName("订单详情：别人的单 30005，未分配单对普通销售同样 30005")
            void orderDetailDeniesOutOfScopeRead() {
                Long customer = createCustomer("OD", sellerA);
                Long orderOfA = insertOrder(customer, "A", sellerA);
                Long unassigned = insertOrder(customer, "N", null);

                assertThat(as(sellerA, Set.of(), () -> salesOrderQueryService.detail(orderOfA).getOrderId())).isEqualTo(orderOfA);
                assertNoPermission(() -> as(sellerB, Set.of(), () -> salesOrderQueryService.detail(orderOfA)));
                assertNoPermission(() -> as(sellerA, Set.of(), () -> salesOrderQueryService.detail(unassigned)));
                assertThat(as(sellerB, Set.of(ScmDataScopeService.ORDER_ALL_PERM),
                        () -> salesOrderQueryService.detail(unassigned).getOrderId())).isEqualTo(unassigned);

                expectCode(() -> as(sellerB, Set.of(), () -> salesOrderQueryService.detail(-1L)), 40460);
            }

            @Test
            @DisplayName("退货单与退款单的列表和详情跟随父订单负责人范围")
            void returnAndRefundFollowParentOrderScope() {
                Long customer = createCustomer("OR", sellerA);
                Long orderOfA = insertOrder(customer, "A", sellerA);
                Long orderOfB = insertOrder(customer, "B", sellerB);
                Long returnOfA = insertReturn(orderOfA, customer, "A");
                Long returnOfB = insertReturn(orderOfB, customer, "B");
                Long refundOfA = insertRefund(returnOfA, orderOfA, customer, "A");
                Long refundOfB = insertRefund(returnOfB, orderOfB, customer, "B");

                assertThat(as(sellerB, Set.of(), () -> returnIds(orderReturnService.query(newForm(new OrderReturnQueryForm())))))
                        .containsExactly(returnOfB);
                assertThat(as(sellerB, Set.of(), () -> orderReturnService.detail(returnOfB).getReturnId())).isEqualTo(returnOfB);
                assertNoPermission(() -> as(sellerB, Set.of(), () -> orderReturnService.detail(returnOfA)));

                assertThat(as(sellerA, Set.of(), () -> refundIds(orderRefundService.query(newForm(new OrderRefundQueryForm())))))
                        .containsExactly(refundOfA);
                assertNoPermission(() -> as(sellerA, Set.of(), () -> orderRefundService.detail(refundOfB)));

                // 全量范围两类单都看得到（销售主管规则）
                assertThat(as(sellerB, Set.of(ScmDataScopeService.ORDER_ALL_PERM),
                        () -> returnIds(orderReturnService.query(newForm(new OrderReturnQueryForm())))))
                        .contains(returnOfA, returnOfB);
            }

            @Test
            @DisplayName("操作日志列表按父订单收窄：看不到订单就看不到它的日志")
            void orderLogListFollowsParentOrderScope() {
                Long customer = createCustomer("OL", sellerA);
                Long orderOfA = insertOrder(customer, "A", sellerA);
                Long orderOfB = insertOrder(customer, "B", sellerB);
                insertLog(orderOfA, "CREATE");
                insertLog(orderOfB, "CONFIRM");

                assertThat(as(sellerA, Set.of(), () -> logOrderIds(salesOrderQueryService.logs(newForm(new OrderLogQueryForm())))))
                        .containsExactly(orderOfA);
                assertThat(as(sellerB, Set.of(), () -> logOrderIds(salesOrderQueryService.logs(newForm(new OrderLogQueryForm())))))
                        .containsExactly(orderOfB);

                // 指名查别人的订单日志：不是「少几条」，而是一条都不给
                OrderLogQueryForm targeted = newForm(new OrderLogQueryForm());
                targeted.setOrderId(orderOfB);
                assertThat(as(sellerA, Set.of(), () -> salesOrderQueryService.logs(targeted).getList())).isEmpty();
            }

            // ==================== 4. 校验失败时拒绝操作的边界 ====================

            @Test
            @DisplayName("没有登录员工上下文：列表返回空分页，而不是全量")
            void missingIdentityFailsClosed() {
                createCustomer("F1", sellerA);
                XsyRequestUtil.remove();

                PageResult<CustomerVO> customers = customerQueryService.query(newForm(new CustomerQueryForm()));
                assertThat(customers.getList()).isEmpty();
                assertThat(customers.getTotal()).isZero();
                assertThat(customers.getEmptyFlag()).isTrue();

                PageResult<SalesOrderVO> orders = salesOrderQueryService.query(newForm(new SalesOrderQueryForm()));
                assertThat(orders.getList()).isEmpty();
                assertThat(orders.getTotal()).isZero();
            }

            @Test
            @DisplayName("订单族 VO 不携带成本列：本期无成本可抹，将来加列必须同时接成本权限")
            void orderVosExposeNoCostColumns() {
                for (Class<?> vo : List.of(SalesOrderVO.class, SalesOrderDetailVO.class, SalesOrderItemVO.class,
                        OrderAddressSnapshotVO.class, OrderReturnVO.class, OrderReturnDetailVO.class,
                        OrderReturnItemVO.class, OrderRefundVO.class, OrderOperationLogVO.class)) {
                    for (Field field : vo.getDeclaredFields()) {
                        if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                            continue;
                        }
                        assertThat(field.getName())
                                .as("%s.%s 属于成本/毛利规则，必须按 scm:report:cost:query 抹成 null 才能暴露",
                                        vo.getSimpleName(), field.getName())
                                .doesNotMatch("(?i).*(cost|margin|profit|purchaseprice).*");
                    }
                }
            }

            // ==================== 夹具 ====================

            /**
             * 真实员工行：范围判定用的是 {@code seller_id} 指向的员工 id，因此按最小必要列插真行，
             * 靠测试事务回滚清理。{@code administrator_flag} 必须是 FALSE。
             */
            private Long newEmployee(String suffix) {
                String loginName = (prefix + "-" + suffix).toUpperCase(Locale.ROOT);
                jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                                + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                        UUID.randomUUID().toString().replace("-", ""), loginName, "$argon2id$it-placeholder",
                        "销售" + suffix);
                Long employeeId = jdbc.queryForObject(
                        "SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
                assertThat(jdbc.queryForObject(
                        "SELECT administrator_flag FROM t_employee WHERE employee_id = ?", Boolean.class, employeeId))
                        .as("正式业务角色的验收账号禁止超管位（裁决第 5 条）").isFalse();
                return employeeId;
            }

            private void loginAs(Long employeeId) {
                RequestEmployee employee = new RequestEmployee();
                employee.setEmployeeId(employeeId);
                employee.setActualName("SCM 数据范围 IT");
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                employee.setAdministratorFlag(false);
                employee.setDepartmentId(1L);
                XsyRequestUtil.setRequestUser(employee);
            }

            /**
             * 以某个员工 + 一组功能权限执行一段逻辑。权限由 {@code mockStatic(StpUtil.class)} 提供：
             * 未点名的权限码取 Mockito 默认值 false，与生产上的校验失败时拒绝操作取向一致。
             */
            private <T> T as(Long employeeId, Set<String> permissions, Supplier<T> body) {
                loginAs(employeeId);
                try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                    permissions.forEach(p -> stp.when(() -> StpUtil.hasPermission(p)).thenReturn(true));
                    // 静态mock的void方法默认放行，必须显式模拟改派命令的权限拒绝。
                    stp.when(() -> StpUtil.checkPermission(ScmDataScopeService.CUSTOMER_ASSIGN_PERM)).thenAnswer(invocation -> {
                        if (!permissions.contains(ScmDataScopeService.CUSTOMER_ASSIGN_PERM)) {
                            throw new cn.dev33.satoken.exception.NotPermissionException(ScmDataScopeService.CUSTOMER_ASSIGN_PERM);
                        }
                        return null;
                    });
                    return body.get();
                }
            }

            private void assertNoPermission(Runnable action) {
                assertThatThrownBy(action::run)
                        .isInstanceOf(BusinessException.class)
                        .isNotInstanceOf(ScmBusinessException.class)
                        .hasMessage(UserErrorCode.NO_PERMISSION.getMsg());
            }

            /** 在当前登录员工下建客户；不提交 sellerId，归属由服务端强制为当前员工。 */
            private Long createCustomer(String suffix, Long ownerEmployeeId) {
                return addCustomer(ownerEmployeeId, Set.of(), suffix, null);
            }

            /** 同上，并把客户推进到可交易状态（订单写入侧要求 COOPERATING）。 */
            private Long createTradableCustomer(String suffix, Long ownerEmployeeId) {
                Long customerId = createCustomer(suffix, ownerEmployeeId);
                as(ownerEmployeeId, Set.of(), () -> {
                    CustomerStatusForm status = new CustomerStatusForm();
                    status.setCustomerId(customerId);
                    status.setVersion(0);
                    status.setStatus("COOPERATING");
                    customerService.updateStatus(status);
                    return null;
                });
                return customerId;
            }

            /**
             * 以 {@code callerEmployeeId} 身份 + {@code permissions} 提交建客户表单。
             *
             * @param submittedSellerId 客户端提交的归属；无分配权时必须被服务端覆盖成调用者本人
             */
            private Long addCustomer(Long callerEmployeeId, Set<String> permissions, String suffix, Long submittedSellerId) {
                return addCustomer(callerEmployeeId, permissions, suffix, submittedSellerId, form -> {
                });
            }

            private Long addCustomer(Long callerEmployeeId, Set<String> permissions, String suffix, Long submittedSellerId,
                                     java.util.function.Consumer<CustomerAddForm> customize) {
                return as(callerEmployeeId, permissions, () -> {
                    CustomerAddForm form = customerForm(suffix);
                    form.setSellerId(submittedSellerId);
                    customize.accept(form);
                    return customerService.add(form);
                });
            }

            private CustomerAddForm customerForm(String suffix) {
                CustomerAddForm form = new CustomerAddForm();
                form.setName("范围测试" + suffix);
                form.setSettleMode("INDEPENDENT");
                return form;
            }

            private CustomerUpdateForm updateForm(Long customerId, Integer version) {
                CustomerUpdateForm form = new CustomerUpdateForm();
                form.setCustomerId(customerId);
                form.setVersion(version);
                form.setName("编辑后名称");
                form.setSettleMode("INDEPENDENT");
                return form;
            }

            private CustomerSellerReassignForm reassignForm(Long customerId, Long sellerId, Integer version) {
                CustomerSellerReassignForm form = new CustomerSellerReassignForm();
                form.setCustomerId(customerId);
                form.setSellerId(sellerId);
                form.setVersion(version);
                return form;
            }

            private SalesOrderAddForm orderForm(Long customerId, Long skuId) {
                SalesOrderAddForm form = new SalesOrderAddForm();
                form.setCustomerId(customerId);
                form.setOrderSource("ADMIN");
                form.setRemark("数据范围测试单");
                OrderAddressForm address = new OrderAddressForm();
                address.setReceiverName("收货人");
                address.setReceiverPhone("13800000000");
                address.setAddress("测试地址");
                form.setAddress(address);
                SalesOrderItemForm item = new SalesOrderItemForm();
                item.setSkuId(skuId);
                item.setOrderedQuantity("1.0000");
                item.setManualPriceOverride(false);
                form.setItems(new ArrayList<>(List.of(item)));
                return form;
            }

            private PriceResolveForm priceForm(Long customerId, Long skuId) {
                PriceResolveForm form = new PriceResolveForm();
                form.setCustomerId(customerId);
                form.setSkuIds(List.of(skuId));
                return form;
            }

            private Long sellerIdOf(Long customerId) {
                evicted();
                return jdbc.queryForObject("SELECT seller_id FROM xsy_customer WHERE id = ?", Long.class, customerId);
            }

            private Long sellerIdOfOrder(Long orderId) {
                evicted();
                return jdbc.queryForObject("SELECT seller_id FROM xsy_sales_order WHERE id = ?", Long.class, orderId);
            }

            private Integer sellerVersion(Long customerId) {
                evicted();
                return jdbc.queryForObject("SELECT version FROM xsy_customer WHERE id = ?", Integer.class, customerId);
            }

            /** 列表夹具一律带 {@code prefix} 自限：同一事务里库里还有别的用例与种子数据，不假设空库。 */
            private Long insertOrder(Long customerId, String suffix, Long sellerId) {
                String orderNo = prefix + "-SO" + suffix;
                jdbc.update("INSERT INTO xsy_sales_order (order_no, customer_id, customer_code_snapshot,"
                                + " customer_name_snapshot, settlement_customer_id, settlement_customer_name_snapshot,"
                                + " order_source, status, ordered_total_amount,"
                                + " settlement_total_amount, settle_mode_snapshot, seller_id)"
                                + " VALUES (?, ?, ?, ?, ?, ?, 'ADMIN', 'PENDING', 0, 0, 'INDEPENDENT', ?)",
                        orderNo, customerId, "CODE-" + suffix, "范围测试客户", customerId, "范围测试客户", sellerId);
                Long orderId = jdbc.queryForObject("SELECT id FROM xsy_sales_order WHERE order_no = ?", Long.class, orderNo);
                evicted();
                return orderId;
            }

            private Long insertReturn(Long orderId, Long customerId, String suffix) {
                String returnNo = prefix + "-RT" + suffix;
                // V202 起退货单必须带当时的客户名称：独立退货没有原单可回查，页面与日志都读这一列。
                jdbc.update("INSERT INTO xsy_order_return (return_no, source_type, order_id, customer_id,"
                        + " customer_name_snapshot, status, reason)"
                        + " VALUES (?, 'ORDER', ?, ?, '范围测试客户', 'PENDING', '范围测试退货')",
                    returnNo, orderId, customerId);
                return jdbc.queryForObject("SELECT id FROM xsy_order_return WHERE return_no = ?", Long.class, returnNo);
            }

            private Long insertRefund(Long returnId, Long orderId, Long customerId, String suffix) {
                String refundNo = prefix + "-RF" + suffix;
                jdbc.update("INSERT INTO xsy_order_refund (refund_no, return_id, order_id, customer_id, refund_amount, status)"
                        + " VALUES (?, ?, ?, ?, 1.0000, 'PENDING')", refundNo, returnId, orderId, customerId);
                return jdbc.queryForObject("SELECT id FROM xsy_order_refund WHERE refund_no = ?", Long.class, refundNo);
            }

            /** 管理端夹具使用员工操作者标识；日志查询会按用户类型分别解析员工与商城客户。 */
            private void insertLog(Long orderId, String operationType) {
                jdbc.update("INSERT INTO xsy_order_operation_log (order_id, operation_type, operator, created_by)"
                        + " VALUES (?, ?, ?, ?)", orderId, operationType, "admin:" + sellerA, "admin:" + sellerA);
            }

            private <T extends com.xsy.scm.base.common.domain.PageParam> T newForm(T form) {
                form.setPageNum(1L);
                form.setPageSize(100L);
                return form;
            }

            private SalesOrderQueryForm sellerFilter(Long sellerId) {
                SalesOrderQueryForm form = newForm(new SalesOrderQueryForm());
                form.setSellerId(sellerId);
                return form;
            }

            private PageResult<CustomerVO> customerList() {
                return customerQueryService.query(newForm(new CustomerQueryForm()));
            }

            private PageResult<SalesOrderVO> orderList() {
                return salesOrderQueryService.query(newForm(new SalesOrderQueryForm()));
            }

            private List<Long> customerIds(PageResult<CustomerVO> page) {
                return page.getList().stream().map(CustomerVO::getCustomerId).toList();
            }

            private List<Long> orderIds(PageResult<SalesOrderVO> page) {
                return page.getList().stream().map(SalesOrderVO::getOrderId).toList();
            }

            private List<Long> returnIds(PageResult<OrderReturnVO> page) {
                return page.getList().stream().map(OrderReturnVO::getReturnId).toList();
            }

            private List<Long> refundIds(PageResult<OrderRefundVO> page) {
                return page.getList().stream().map(OrderRefundVO::getRefundId).toList();
            }

            private List<Long> logOrderIds(PageResult<OrderOperationLogVO> page) {
                return page.getList().stream().map(OrderOperationLogVO::getOrderId).toList();
            }

            private boolean roleHoldsPermission(String roleCode, String permission) {
                Integer count = jdbc.queryForObject("""
                        SELECT count(*) FROM t_role_menu rm
                        JOIN t_role r ON r.role_id = rm.role_id
                        JOIN t_menu m ON m.menu_id = rm.menu_id
                        WHERE r.role_code = ? AND m.api_perms = ?""", Integer.class, roleCode, permission);
                return count != null && count > 0;
            }

            /** 同一测试事务共用一个 SqlSession，MyBatis 一级缓存会按「语句 + 参数」命中旧结果。 */
            private void evicted() {
                session.clearCache();
            }
        }

        @Nested
        @DisplayName("SEC-01/SEC-02 订单与退货写入口的归属门禁（PG IT）")
        class ScmOrderWriteScopePgIT {
            @Autowired
            private SalesOrderService salesOrderService;

            @Autowired
            private OrderReturnService orderReturnService;

            @Autowired
            private CustomerService customerService;

            @Autowired
            private org.apache.ibatis.session.SqlSession session;

            /** 两个普通销售：各自负责自己的客户与订单，功能权限相同、数据范围不同。 */
            private Long sellerA;
            private Long sellerB;

            /** 「未分配」订单（{@code seller_id IS NULL}）不对任何普通销售放行。 */
            @BeforeEach
            void setUpEmployees() {
                sellerA = newEmployee("A");
                sellerB = newEmployee("B");
            }

            // ==================== SEC-01：订单写命令 ====================

            @Test
            @DisplayName("订单编辑：改别人的待确认单 30005，改自己的照常成功")
            void updateRejectsForeignOrderButAllowsOwn() {
                Long customerOfB = createTradableCustomer("U1", sellerB);
                Long sku = newOnShelfSku("U1");
                Long orderOfB = pendingOrder(customerOfB, sku, sellerB, "u1");
                Integer before = versionOf(orderOfB);

                assertNoPermission(() -> as(sellerA, Set.of(), () -> salesOrderService.update(
                        updateForm(orderOfB, versionOf(orderOfB), customerOfB, sku))));

                // 越权被拒不是「改了一半」：备注与版本都必须是原样（update 会 bump 版本）
                assertThat(versionOf(orderOfB)).isEqualTo(before);
                assertThat(remarkOf(orderOfB)).isNotEqualTo("写范围测试-编辑");
                assertThat(as(sellerB, Set.of(), () -> salesOrderService.update(
                        updateForm(orderOfB, versionOf(orderOfB), customerOfB, sku)).getOrderId())).isEqualTo(orderOfB);
                assertThat(remarkOf(orderOfB)).isEqualTo("写范围测试-编辑");
            }

            @Test
            @DisplayName("实收数量：改别人单的数量 30005；自己的单照常")
            void actualQuantityRejectsForeignOrderButAllowsOwn() {
                Long customerOfB = createTradableCustomer("Q1", sellerB);
                Long sku = newOnShelfSku("Q1");
                Long orderOfB = pendingOrder(customerOfB, sku, sellerB, "q1");
                Long itemId = firstItemId(orderOfB);
                // 新建即待确认；本用例要验的是那道归属门禁
                assertThat(statusOf(orderOfB)).isEqualTo("PENDING");

                assertNoPermission(() -> as(sellerA, Set.of(), () -> salesOrderService.actualQuantity(
                        actualForm(orderOfB, itemId, versionOf(orderOfB), "1.0000"), prefix + "-q1a")));
                // 越权被拒后实数量仍为空
                assertThat(actualQuantityOf(itemId)).isNull();

                assertThat(as(sellerB, Set.of(), () -> salesOrderService.actualQuantity(
                        actualForm(orderOfB, itemId, versionOf(orderOfB), "1.0000"), prefix + "-q1b").getOrderId()))
                        .isEqualTo(orderOfB);
            }

            @Test
            @DisplayName("订单取消：取消别人的单 30005，且状态不变；自己的单照常取消")
            void cancelRejectsForeignOrderButAllowsOwn() {
                Long customerOfB = createTradableCustomer("C1", sellerB);
                Long sku = newOnShelfSku("C1");
                Long orderOfB = pendingOrder(customerOfB, sku, sellerB, "c1");

                assertNoPermission(() -> as(sellerA, Set.of(), () -> salesOrderService.cancel(
                        cancelForm(orderOfB, versionOf(orderOfB)), prefix + "-c1a")));
                assertThat(as(sellerB, Set.of(), () -> statusOf(orderOfB))).isEqualTo("PENDING");

                assertThat(as(sellerB, Set.of(), () -> salesOrderService.cancel(
                        cancelForm(orderOfB, versionOf(orderOfB)), prefix + "-c1b").getStatus()))
                        .isEqualTo("CANCELLED");
            }

            @Test
            @DisplayName("订单删除：删别人的待确认单 30005，单据仍在；自己的待确认单照常删")
            void deleteRejectsForeignOrderButAllowsOwn() {
                Long customerOfB = createTradableCustomer("D1", sellerB);
                Long sku = newOnShelfSku("D1");
                Long orderOfB = pendingOrder(customerOfB, sku, sellerB, "d1");

                assertNoPermission(() -> as(sellerA, Set.<String>of(), () -> {
                    salesOrderService.delete(versionForm(orderOfB, versionOf(orderOfB)));
                    return null;
                }));
                assertThat(deletedOf(orderOfB)).as("越权删除不得真的写入数据库").isFalse();
                assertThat(statusOf(orderOfB)).isEqualTo("PENDING");

                // 负责人自己删：软删标记必须落地
                as(sellerB, Set.of(), () -> {
                    salesOrderService.delete(versionForm(orderOfB, versionOf(orderOfB)));
                    return null;
                });
                assertThat(deletedOf(orderOfB)).isTrue();
            }

            @Test
            @DisplayName("预留库存：替别人的已确认单预留 30005，不留下任何预留行")
            void reserveStockRejectsForeignOrder() {
                Long customerOfB = createTradableCustomer("R1", sellerB);
                Long sku = newOnShelfSku("R1");
                Long orderOfB = pendingOrder(customerOfB, sku, sellerB, "r1");
                // 用负责人自己的身份把单推到 CONFIRMED：本用例要验的是预留那道门禁，不是状态机
                confirmOwn(orderOfB);
                assertThat(as(sellerB, Set.of(), () -> statusOf(orderOfB))).isEqualTo("CONFIRMED");

                assertNoPermission(() -> as(sellerA, Set.of(), () -> {
                    salesOrderService.reserveStock(orderOfB);
                    return null;
                }));

                // 越权被拒必须在业务数据上留痕为零：不能出现「拒了但已经占了货」
                assertThat(reservationRowsFor(orderOfB)).isZero();
            }

            // ==================== SEC-02：退货写命令 ====================

            @Test
            @DisplayName("退货建单：替别人的订单建退货 30005，退货行不写入数据库")
            void returnCreateRejectsForeignOrder() {
                Long customerOfB = createTradableCustomer("T1", sellerB);
                Long sku = newOnShelfSku("T1");
                Long orderOfB = confirmedOrder(customerOfB, sku, sellerB, "t1");
                Long itemId = firstItemId(orderOfB);

                assertNoPermission(() -> as(sellerA, Set.of(), () -> orderReturnService.create(
                        returnAddForm(orderOfB, itemId, "1.0000"), prefix + "-t1a")));

                assertThat(returnRowsFor(orderOfB)).isZero();

                assertThat(as(sellerB, Set.of(), () -> orderReturnService.create(
                        returnAddForm(orderOfB, firstItemId(orderOfB), "1.0000"), prefix + "-t1b").getOrderId()))
                        .isEqualTo(orderOfB);
            }

            @Test
            @DisplayName("退货建单重复请求返回原结果：A 拿着 B 的订单号重放旧键仍然 30005")
            void returnCreateReplayDoesNotBypassScope() {
                Long customerOfB = createTradableCustomer("T2", sellerB);
                Long sku = newOnShelfSku("T2");
                Long orderOfB = confirmedOrder(customerOfB, sku, sellerB, "t2");
                String key = prefix + "-t2";
                // 重放要命中同一行，请求体必须逐字节一致 → 复用同一个 form
                OrderReturnAddForm form = returnAddForm(orderOfB, firstItemId(orderOfB), "1.0000");

                Long returnId = as(sellerB, Set.of(), () -> orderReturnService.create(form, key).getReturnId());
                assertThat(returnId).isNotNull();

                // A 用自己的键 → 不是重放，走写命令，父订单范围门禁拦下
                assertNoPermission(() -> as(sellerA, Set.of(), () -> orderReturnService.create(form, prefix + "-t2-a")));

                // B 重放自己的键、同样的请求体：replay 分支里 requireParentOrderVisible 仍然成立 → 正常返回
                assertThat(as(sellerB, Set.of(), () -> orderReturnService.create(form, key).getReturnId()))
                        .isEqualTo(returnId);
            }

            @Test
            @DisplayName("退货审批：批别人的退货单 30005，状态仍 PENDING；自己的单照常批")
            void returnApproveRejectsForeignReturn() {
                Long customerOfB = createTradableCustomer("T3", sellerB);
                Long sku = newOnShelfSku("T3");
                Long orderOfB = confirmedOrder(customerOfB, sku, sellerB, "t3");
                Long itemId = firstItemId(orderOfB);
                Long returnId = as(sellerB, Set.of(), () -> orderReturnService.create(
                        returnAddForm(orderOfB, itemId, "1.0000"), prefix + "-t3").getReturnId());

                assertNoPermission(() -> as(sellerA, Set.of(), () -> orderReturnService.approve(
                        approveForm(returnId, returnVersionOf(returnId), itemId, "1.0000"), prefix + "-t3a")));
                assertThat(returnStatusOf(returnId)).isEqualTo("PENDING");

                assertThat(as(sellerB, Set.of(), () -> orderReturnService.approve(
                        approveForm(returnId, returnVersionOf(returnId), itemId, "1.0000"), prefix + "-t3b").getStatus()))
                        .isEqualTo("APPROVED");
            }

            @Test
            @DisplayName("退货驳回：驳别人的退货单 30005；自己的单照常驳回")
            void returnRejectRejectsForeignReturn() {
                Long customerOfB = createTradableCustomer("T4", sellerB);
                Long sku = newOnShelfSku("T4");
                Long orderOfB = confirmedOrder(customerOfB, sku, sellerB, "t4");
                Long returnId = as(sellerB, Set.of(), () -> orderReturnService.create(
                        returnAddForm(orderOfB, firstItemId(orderOfB), "1.0000"), prefix + "-t4").getReturnId());

                assertNoPermission(() -> as(sellerA, Set.of(), () -> orderReturnService.reject(
                        decisionForm(returnId, returnVersionOf(returnId), "越权驳回"), prefix + "-t4a")));
                assertThat(returnStatusOf(returnId)).isEqualTo("PENDING");

                assertThat(as(sellerB, Set.of(), () -> orderReturnService.reject(
                        decisionForm(returnId, returnVersionOf(returnId), "负责人驳回"), prefix + "-t4b").getStatus()))
                        .isEqualTo("REJECTED");
            }

            @Test
            @DisplayName("退货取消：取消别人的退货单 30005；自己的单照常取消")
            void returnCancelRejectsForeignReturn() {
                Long customerOfB = createTradableCustomer("T5", sellerB);
                Long sku = newOnShelfSku("T5");
                Long orderOfB = confirmedOrder(customerOfB, sku, sellerB, "t5");
                Long returnId = as(sellerB, Set.of(), () -> orderReturnService.create(
                        returnAddForm(orderOfB, firstItemId(orderOfB), "1.0000"), prefix + "-t5").getReturnId());

                assertNoPermission(() -> as(sellerA, Set.of(), () -> orderReturnService.cancel(
                        decisionForm(returnId, returnVersionOf(returnId), "越权取消"), prefix + "-t5a")));
                assertThat(returnStatusOf(returnId)).isEqualTo("PENDING");

                assertThat(as(sellerB, Set.of(), () -> orderReturnService.cancel(
                        decisionForm(returnId, returnVersionOf(returnId), "负责人取消"), prefix + "-t5b").getStatus()))
                        .isEqualTo("CANCELLED");
            }

            @Test
            @DisplayName("审批重复请求返回原结果：同一个人换了范围后重放旧键仍 30005（replay 前先 detail）")
            void returnApproveReplayDoesNotBypassScope() {
                Long customerOfB = createTradableCustomer("T6", sellerB);
                Long sku = newOnShelfSku("T6");
                Long orderOfB = confirmedOrder(customerOfB, sku, sellerB, "t6");
                Long itemId = firstItemId(orderOfB);
                Long returnId = as(sellerB, Set.of(), () -> orderReturnService.create(
                        returnAddForm(orderOfB, itemId, "1.0000"), prefix + "-t6").getReturnId());
                String key = prefix + "-t6-approve";
                // 复用同一 form：重放要命中同一行，请求体必须逐字节一致
                OrderReturnApproveForm form = approveForm(returnId, returnVersionOf(returnId), itemId, "1.0000");

                as(sellerB, Set.of(), () -> orderReturnService.approve(form, key));

                // A 用同一个键重放：scope 含 operator，A 的行与 B 的行不同 → 不命中重放，
                // 落到写命令后由 lock() 里的父订单范围门禁拦下（否则猜个 returnId 就能重放别人的审批结果）
                assertNoPermission(() -> as(sellerA, Set.of(), () -> orderReturnService.approve(form, key)));

                // B 自己重放：命中 replay，但 replay 前 detail() 会重新判范围 → 正常返回
                assertThat(as(sellerB, Set.of(), () -> orderReturnService.approve(form, key).getReturnId()))
                        .isEqualTo(returnId);
            }

            @Test
            @DisplayName("未分配订单（seller_id IS NULL）不对普通销售放行，全量范围者可写")
            void unassignedOrderIsNotWritableByPlainSeller() {
                Long customerOfA = createTradableCustomer("N1", sellerA);
                String orderNo = prefix + "-N1";
                // 直接插一张 seller_id 为 null 的待确认单：模拟「归属被收回」的历史单。
                // 用 delete 作为探针：它只需要订单行本身，不依赖下单时保存的地址或订单行，
                // 能把「范围判定」这一件事单独隔离出来。
                jdbc.update("INSERT INTO xsy_sales_order (order_no, customer_id, customer_code_snapshot,"
                                + " customer_name_snapshot, settlement_customer_id, settlement_customer_name_snapshot,"
                                + " order_source, status, ordered_total_amount, settlement_total_amount,"
                                + " settle_mode_snapshot, seller_id)"
                                + " VALUES (?, ?, 'CODE-N1', '范围测试客户', ?, '范围测试客户', 'ADMIN', 'PENDING', 0, 0,"
                                + " 'INDEPENDENT', NULL)",
                        orderNo, customerOfA, customerOfA);
                Long unassigned = jdbc.queryForObject("SELECT id FROM xsy_sales_order WHERE order_no = ?", Long.class, orderNo);

                // A 是「原本可能负责过这户客户」的普通销售：范围谓词只认 seller_id IN (本人)，null 不在其中
                assertNoPermission(() -> as(sellerA, Set.of(), () -> {
                    salesOrderService.delete(versionForm(unassigned, versionOf(unassigned)));
                    return null;
                }));
                assertThat(deletedOf(unassigned)).isFalse();

                // 全量范围：all() 对任何 sellerId 都放行（含 null），删除正常落地
                as(sellerA, Set.of(ScmDataScopeService.ORDER_ALL_PERM), () -> {
                    salesOrderService.delete(versionForm(unassigned, versionOf(unassigned)));
                    return null;
                });
                assertThat(deletedOf(unassigned)).isTrue();
            }

            // ==================== 夹具 ====================

            /** 真实员工行：范围判定用 {@code seller_id} 指向的员工 id，靠测试事务回滚清理。 */
            private Long newEmployee(String suffix) {
                String loginName = (prefix + "-" + suffix).toUpperCase(Locale.ROOT);
                jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                                + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                        UUID.randomUUID().toString().replace("-", ""), loginName, "$argon2id$it-placeholder",
                        "销售" + suffix);
                Long employeeId = jdbc.queryForObject(
                        "SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
                assertThat(jdbc.queryForObject(
                        "SELECT administrator_flag FROM t_employee WHERE employee_id = ?", Boolean.class, employeeId))
                        .as("正式业务角色的验收账号禁止超管位").isFalse();
                return employeeId;
            }

            private void loginAs(Long employeeId) {
                RequestEmployee employee = new RequestEmployee();
                employee.setEmployeeId(employeeId);
                employee.setActualName("SEC 写范围 IT");
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                employee.setAdministratorFlag(false);
                employee.setDepartmentId(1L);
                XsyRequestUtil.setRequestUser(employee);
            }

            private <T> T as(Long employeeId, Set<String> permissions, Supplier<T> body) {
                loginAs(employeeId);
                try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
                    permissions.forEach(p -> stp.when(() -> StpUtil.hasPermission(p)).thenReturn(true));
                    return body.get();
                }
            }

            /** 越权断言只断到异常类型与 {@code NO_PERMISSION} 语义（数字码的映射归属在底座）。 */
            private void assertNoPermission(Runnable action) {
                assertThatThrownBy(action::run)
                        .isInstanceOf(BusinessException.class)
                        .isNotInstanceOf(ScmBusinessException.class)
                        .hasMessage(UserErrorCode.NO_PERMISSION.getMsg());
            }

            private Long createTradableCustomer(String suffix, Long ownerEmployeeId) {
                return as(ownerEmployeeId, Set.of(), () -> {
                    CustomerAddForm form = new CustomerAddForm();
                    form.setName("写范围测试" + suffix);
                    form.setSettleMode("INDEPENDENT");
                    Long customerId = customerService.add(form);
                    CustomerStatusForm status = new CustomerStatusForm();
                    status.setCustomerId(customerId);
                    status.setVersion(0);
                    status.setStatus("COOPERATING");
                    customerService.updateStatus(status);
                    return customerId;
                });
            }

            /** 走真实建单服务造一张草稿，归属由当时的客户资料强制为负责人。 */
            private Long pendingOrder(Long customerId, Long skuId, Long ownerEmployeeId, String suffix) {
                return as(ownerEmployeeId, Set.of(), () -> salesOrderService.create(
                        orderForm(customerId, skuId, suffix), prefix + "-so").getOrderId());
            }

            /** 造一张已确认订单：写路径门禁之前的完整主链，供退货族使用。 */
            private Long confirmedOrder(Long customerId, Long skuId, Long ownerEmployeeId, String suffix) {
                Long orderId = pendingOrder(customerId, skuId, ownerEmployeeId, suffix);
                confirmOwn(orderId);
                return orderId;
            }

            /**
             * 以负责人身份把草稿推到 CONFIRMED。
             *
             * <p>夹具用的 SKU 是 {@code NON_STANDARD}（基类固定），新建时系统不自动补实数量，
             * 而确认前每行都必须有正实数量 —— 所以这里先走一次真实 {@code actualQuantity}，
             * 再确认。这两步都是被本类当作「前置记录」的既有主链，不是待测点。
             */
            private void confirmOwn(Long orderId) {
                Long seller = jdbc.queryForObject("SELECT seller_id FROM xsy_sales_order WHERE id = ?", Long.class, orderId);
                Long itemId = firstItemId(orderId);
                as(seller, Set.of(), () -> {
                    salesOrderService.actualQuantity(
                            actualForm(orderId, itemId, versionOf(orderId), "1.0000"), prefix + "-act");
                    salesOrderService.confirm(versionForm(orderId, versionOf(orderId)), prefix + "-cfm");
                    return null;
                });
            }

            private SalesOrderAddForm orderForm(Long customerId, Long skuId, String suffix) {
                SalesOrderAddForm form = new SalesOrderAddForm();
                form.setCustomerId(customerId);
                form.setOrderSource("ADMIN");
                form.setRemark("写范围测试单" + suffix);
                OrderAddressForm address = new OrderAddressForm();
                address.setReceiverName("收货人");
                address.setReceiverPhone("13800000000");
                address.setAddress("测试地址");
                form.setAddress(address);
                SalesOrderItemForm item = new SalesOrderItemForm();
                item.setSkuId(skuId);
                item.setOrderedQuantity("1.0000");
                item.setManualPriceOverride(false);
                form.setItems(new ArrayList<>(List.of(item)));
                return form;
            }

            private SalesOrderUpdateForm updateForm(Long orderId, Integer version, Long customerId, Long skuId) {
                var form = new SalesOrderUpdateForm();
                form.setOrderId(orderId);
                form.setVersion(version);
                form.setCustomerId(customerId);
                form.setOrderSource("ADMIN");
                form.setRemark("写范围测试-编辑");
                OrderAddressForm address = new OrderAddressForm();
                address.setReceiverName("收货人");
                address.setReceiverPhone("13800000000");
                address.setAddress("测试地址");
                form.setAddress(address);
                SalesOrderItemForm item = new SalesOrderItemForm();
                item.setSkuId(skuId);
                item.setOrderedQuantity("2.0000");
                item.setManualPriceOverride(false);
                form.setItems(new ArrayList<>(List.of(item)));
                return form;
            }

            private OrderVersionForm versionForm(Long orderId, Integer version) {
                OrderVersionForm form = new OrderVersionForm();
                form.setOrderId(orderId);
                form.setVersion(version);
                return form;
            }

            private OrderCancelForm cancelForm(Long orderId, Integer version) {
                OrderCancelForm form = new OrderCancelForm();
                form.setOrderId(orderId);
                form.setVersion(version);
                form.setReason("写范围测试取消");
                return form;
            }

            private OrderActualQuantityForm actualForm(Long orderId, Long itemId, Integer version, String quantity) {
                OrderActualQuantityForm form = new OrderActualQuantityForm();
                form.setOrderId(orderId);
                form.setItemId(itemId);
                form.setVersion(version);
                form.setActualQuantity(quantity);
                form.setReason("写范围测试实收");
                return form;
            }

            private OrderReturnAddForm returnAddForm(Long orderId, Long orderItemId, String quantity) {
                OrderReturnAddForm form = new OrderReturnAddForm();
                form.setOrderId(orderId);
                form.setReason("写范围测试退货");
                OrderReturnItemForm item = new OrderReturnItemForm();
                item.setOrderItemId(orderItemId);
                item.setRequestedQuantity(quantity);
                form.setItems(new ArrayList<>(List.of(item)));
                return form;
            }

            private OrderReturnApproveForm approveForm(Long returnId, Integer version, Long orderItemId, String quantity) {
                OrderReturnApproveForm form = new OrderReturnApproveForm();
                form.setReturnId(returnId);
                form.setVersion(version);
                OrderReturnApproveItemForm item = new OrderReturnApproveItemForm();
                item.setOrderItemId(orderItemId);
                item.setApprovedQuantity(quantity);
                form.setItems(new ArrayList<>(List.of(item)));
                return form;
            }

            private OrderReturnDecisionForm decisionForm(Long returnId, Integer version, String reason) {
                OrderReturnDecisionForm form = new OrderReturnDecisionForm();
                form.setReturnId(returnId);
                form.setVersion(version);
                form.setDecisionReason(reason);
                return form;
            }

            // ---------- 读侧小工具：全部直查库 + 清单缓存，绕开被测的写命令 ----------

            private Integer versionOf(Long orderId) {
                evicted();
                return jdbc.queryForObject("SELECT version FROM xsy_sales_order WHERE id = ?", Integer.class, orderId);
            }

            private String statusOf(Long orderId) {
                evicted();
                return jdbc.queryForObject("SELECT status FROM xsy_sales_order WHERE id = ?", String.class, orderId);
            }

            private String remarkOf(Long orderId) {
                evicted();
                return jdbc.queryForObject("SELECT remark FROM xsy_sales_order WHERE id = ?", String.class, orderId);
            }

            private Boolean deletedOf(Long orderId) {
                evicted();
                return jdbc.queryForObject("SELECT deleted FROM xsy_sales_order WHERE id = ?", Boolean.class, orderId);
            }

            private java.math.BigDecimal actualQuantityOf(Long itemId) {
                evicted();
                return jdbc.queryForObject(
                        "SELECT actual_quantity FROM xsy_sales_order_item WHERE id = ?", java.math.BigDecimal.class, itemId);
            }

            private Long firstItemId(Long orderId) {
                evicted();
                return jdbc.queryForObject(
                        "SELECT id FROM xsy_sales_order_item WHERE order_id = ? AND deleted = FALSE ORDER BY id LIMIT 1",
                        Long.class, orderId);
            }

            private Integer returnVersionOf(Long returnId) {
                evicted();
                return jdbc.queryForObject("SELECT version FROM xsy_order_return WHERE id = ?", Integer.class, returnId);
            }

            private String returnStatusOf(Long returnId) {
                evicted();
                return jdbc.queryForObject("SELECT status FROM xsy_order_return WHERE id = ?", String.class, returnId);
            }

            private Integer reservationRowsFor(Long orderId) {
                evicted();
                return jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_inventory_reservation WHERE source_document_id = ?", Integer.class, orderId);
            }

            private Integer returnRowsFor(Long orderId) {
                evicted();
                return jdbc.queryForObject("SELECT count(*) FROM xsy_order_return WHERE order_id = ?", Integer.class, orderId);
            }

            /** 同一测试事务共用一个 SqlSession，MyBatis 一级缓存会按「语句 + 参数」命中旧结果。 */
            private void evicted() {
                session.clearCache();
            }
        }
    }

    @Nested
    class OrderW5PgIT extends ScmW5PgITBase {
        @Nested

        class OrderReturnIndependentPgIT {
            private static final OffsetDateTime EVENT_AT = OffsetDateTime.of(2026, 3, 17, 9, 0, 0, 0,
                ZoneOffset.ofHours(8));

            @Autowired
            private OrderReturnService orderReturnService;

            @Autowired
            private OrderReturnReceiptService orderReturnReceiptService;

            @Autowired
            private FinanceReceiptService financeReceiptService;

            @Autowired
            private FinanceWriteOffService financeWriteOffService;

            @Autowired
            private ScmFinanceAgingService financeAgingService;

            @Autowired
            private ScmCustomerStatementService customerStatementService;

            @Test
            @DisplayName("#1 独立退货按最近成交价锁价，审批生成客户级红字且不产生退款单")
            void independentReturnLocksRecentTransactionPriceAndGeneratesCustomerLevelRed() {
                Long customerId = newCustomer();
                Long skuId = newOnShelfSku("IR1");
                confirmedSalesOrder(customerId, skuId, "10", "10");

                OrderReturnDetailVO created = orderReturnService.createIndependent(addForm(customerId, skuId, "3"), key("ir"));
                assertThat(created.getOrderId()).isNull();
                assertThat(created.getSourceType()).isEqualTo("INDEPENDENT");
                assertThat(created.getCustomerName()).isNotBlank();
                var item = created.getItems().getFirst();
                assertThat(item.getLockedUnitPrice()).isEqualByComparingTo("1.2000");
                assertThat(item.getPriceSource()).isEqualTo("RECENT_TRANSACTION");

                OrderReturnDetailVO approved = orderReturnService.approveIndependent(approveForm(created), key("ir-approve"));
                assertThat(approved.getApprovedAmount()).isEqualByComparingTo("3.6000");

                assertThat(count("SELECT count(*) FROM xsy_finance_receivable WHERE source_type = 'ORDER_RETURN'"
                    + " AND source_id = ? AND entry_type = 'RED'", created.getReturnId())).isEqualTo(1);
                assertThat(query("SELECT order_id FROM xsy_finance_receivable WHERE source_type = 'ORDER_RETURN'"
                    + " AND source_id = ?", created.getReturnId())).isNull();
                assertThat(query("SELECT original_receivable_id FROM xsy_finance_receivable WHERE source_type ="
                    + " 'ORDER_RETURN' AND source_id = ?", created.getReturnId())).isNull();
                assertThat(decimal("SELECT amount FROM xsy_finance_receivable WHERE source_type = 'ORDER_RETURN'"
                    + " AND source_id = ?", created.getReturnId())).isEqualByComparingTo("3.6000");
                assertThat(count("SELECT count(*) FROM xsy_order_refund WHERE return_id = ?", created.getReturnId())).isZero();
            }

            @Test
            @DisplayName("#2 独立退货回库不绑原出库行，成本取入库前该仓移动加权均价")
            void independentReturnReceiptPostsStockAtWarehouseAverageCost() {
                Long customerId = newCustomer();
                Long skuId = newOnShelfSku("IR2");
                Long warehouseId = newWarehouse("IR");
                confirmedSalesOrder(customerId, skuId, "10", "10");
                OrderReturnDetailVO approved = approve(orderReturnService
                    .createIndependent(addForm(customerId, skuId, "2"), key("ir-receive")), key("ir-receive-approve"));
                jdbc.update("INSERT INTO xsy_inventory_balance (warehouse_id, sku_id, unit, quantity, avg_cost)"
                    + " VALUES (?, ?, 'kg', 10, 5)", warehouseId, skuId);

                OrderReturnReceiptVO receipt = orderReturnReceiptService.receive(receiveForm(approved, warehouseId),
                    key("ir-receipt"));

                var line = receipt.getItems().getFirst();
                assertThat(line.getSourceSalesOutMovementId()).isNull();
                assertThat(line.getSourceOutboundItemId()).isNull();
                assertThat(line.getStockQuantity()).isEqualByComparingTo("2.0000");
                // 成本列对无成本权限的账号会被脱敏，入库成本改从库里核对。
                assertThat(decimal("SELECT unit_cost FROM xsy_order_return_receipt_item WHERE id = ?",
                    line.getReceiptItemId())).isEqualByComparingTo("5.0000");
                assertThat(decimal("SELECT quantity FROM xsy_inventory_balance WHERE warehouse_id = ? AND sku_id = ?",
                    warehouseId, skuId)).isEqualByComparingTo("12.0000");
                assertThat(decimal("SELECT avg_cost FROM xsy_inventory_balance WHERE warehouse_id = ? AND sku_id = ?",
                    warehouseId, skuId)).isEqualByComparingTo("5.0000");
                assertThat(count("SELECT count(*) FROM xsy_inventory_movement WHERE movement_type = 'SALES_RETURN_IN'"
                    + " AND source_document_id = ?", approved.getReturnId())).isEqualTo(1);
            }

            @Test
            @DisplayName("#3 客户级红字按客户先进先出抵减最早未结应收：账龄、核销、对账单规则一致")
            void customerLevelRedReducesAgingAndWriteOffFifo() {
                Long customerId = newCustomer();
                Long oldestOrder = confirmedSalesOrder(customerId, newOnShelfSku("IR3A"), "10", "10");
                Long newestOrder = confirmedSalesOrder(customerId, newOnShelfSku("IR3B"), "10", "10");
                Long oldest = normalReceivable(customerId, oldestOrder, "100.0000", EVENT_AT.minusDays(10));
                Long newest = normalReceivable(customerId, newestOrder, "50.0000", EVENT_AT.minusDays(5));
                customerLevelRed(customerId, "30.0000", EVENT_AT.minusDays(3));

                ScmFinanceAgingQueryForm agingForm = new ScmFinanceAgingQueryForm();
                agingForm.setAccountType(ScmFinanceAgingAccountTypeEnum.RECEIVABLE);
                agingForm.setAsOfDate(LocalDate.of(2026, 3, 18));
                agingForm.setCustomerId(customerId);
                agingForm.setPageNum(1L);
                agingForm.setPageSize(50L);
                // 账龄按「到期日升序、单据 ID 降序」展示：两单都没有到期日，因此新单在前。
                var rows = financeAgingService.query(agingForm).getList();
                assertThat(rows).extracting(row -> row.getDocumentId()).containsExactly(newest, oldest);
                assertThat(rows).extracting(row -> row.getOpenAmount())
                    .containsExactly(new BigDecimal("50.0000"), new BigDecimal("70.0000"));
                assertThat(financeAgingService.summary(agingForm)).extracting(row -> row.getOpenAmount())
                    .containsExactly(new BigDecimal("120.0000"));

                FinanceReceiptVO receipt = addReceipt(customerId, "200.0000");
                FinanceWriteOffAutoForm autoForm = new FinanceWriteOffAutoForm();
                autoForm.setSourceType("RECEIPT");
                autoForm.setSourceId(receipt.getReceiptId());
                var plan = financeWriteOffService.autoPreview(autoForm);
                assertThat(plan).extracting(row -> row.getAllocatedAmount())
                    .containsExactly(new BigDecimal("70.0000"), new BigDecimal("50.0000"));

                ScmCustomerStatementForm statementForm = new ScmCustomerStatementForm();
                statementForm.setSettlementCustomerId(customerId);
                statementForm.setCustomerId(customerId);
                statementForm.setStartDate(LocalDate.of(2026, 3, 1));
                statementForm.setEndDate(LocalDate.of(2026, 3, 18));
                var statement = customerStatementService.freeze(statementForm);
                assertThat(statement.getItems()).filteredOn(line -> "RED".equals(line.getFactType()))
                    .extracting(line -> line.getReceivableDelta()).containsExactly(new BigDecimal("-30.0000"));
            }

            private OrderReturnIndependentAddForm addForm(Long customerId, Long skuId, String quantity) {
                OrderReturnIndependentAddForm form = new OrderReturnIndependentAddForm();
                form.setCustomerId(customerId);
                form.setReason("现场退回，找不到原单");
                OrderReturnIndependentItemForm item = new OrderReturnIndependentItemForm();
                item.setSkuId(skuId);
                item.setRequestedQuantity(quantity);
                form.setItems(new ArrayList<>(List.of(item)));
                return form;
            }

            private OrderReturnIndependentApproveForm approveForm(OrderReturnDetailVO created) {
                OrderReturnIndependentApproveForm form = new OrderReturnIndependentApproveForm();
                form.setReturnId(created.getReturnId());
                form.setVersion(created.getVersion());
                OrderReturnIndependentApproveForm.Item item = new OrderReturnIndependentApproveForm.Item();
                item.setReturnItemId(created.getItems().getFirst().getReturnItemId());
                item.setApprovedQuantity(created.getItems().getFirst().getRequestedQuantity().toPlainString());
                form.setItems(new ArrayList<>(List.of(item)));
                return form;
            }

            private OrderReturnDetailVO approve(OrderReturnDetailVO created, String key) {
                return orderReturnService.approveIndependent(approveForm(created), key);
            }

            private OrderReturnReceiveForm receiveForm(OrderReturnDetailVO approved, Long warehouseId) {
                OrderReturnReceiveForm form = new OrderReturnReceiveForm();
                form.setReturnId(approved.getReturnId());
                form.setWarehouseId(warehouseId);
                form.setVersion(approved.getVersion());
                OrderReturnReceiveItemForm item = new OrderReturnReceiveItemForm();
                item.setReturnItemId(approved.getItems().getFirst().getReturnItemId());
                item.setDisposition("RETURN_TO_STOCK");
                item.setQuantity(approved.getItems().getFirst().getApprovedQuantity().toPlainString());
                form.setItems(new ArrayList<>(List.of(item)));
                return form;
            }

            private FinanceReceiptVO addReceipt(Long customerId, String amount) {
                FinanceReceiptAddForm form = new FinanceReceiptAddForm();
                form.setCustomerId(customerId);
                form.setAmount(amount);
                form.setMethod("BANK_TRANSFER");
                form.setReceivedAt(EVENT_AT);
                return financeReceiptService.add(form, key("receipt"));
            }

            private Long normalReceivable(Long customerId, Long salesOrderId, String amount, OffsetDateTime eventAt) {
                return jdbc.queryForObject("INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id,"
                        + " order_id, customer_id, customer_name_snapshot, settlement_customer_id,"
                        + " settlement_customer_name_snapshot, entry_type, amount, event_at, created_at, updated_at)"
                        + " VALUES (?, 'SALES_ORDER', ?, ?, ?, '独立退货测试客户', ?, '独立退货测试客户', 'NORMAL', ?, ?,"
                        + " now(), now()) RETURNING id",
                    Long.class, "AR-IR-" + UUID.randomUUID(), salesOrderId, salesOrderId, customerId, customerId,
                    new BigDecimal(amount), eventAt);
            }

            private Long customerLevelRed(Long customerId, String amount, OffsetDateTime eventAt) {
                return jdbc.queryForObject("INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id,"
                        + " order_id, customer_id, customer_name_snapshot, settlement_customer_id,"
                        + " settlement_customer_name_snapshot, entry_type, amount, event_at, reason, created_at, updated_at)"
                        + " VALUES (?, 'ORDER_RETURN', ?, NULL, ?, '独立退货测试客户', ?, '独立退货测试客户', 'RED', ?, ?,"
                        + " '独立退货', now(), now()) RETURNING id",
                    Long.class, "AR-IR-RED-" + UUID.randomUUID(),
                    1_000_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 8_000_000_000L), customerId,
                    customerId, new BigDecimal(amount), eventAt);
            }

            private Object query(String sql, Object... args) {
                return jdbc.queryForObject(sql, Object.class, args);
            }

            private BigDecimal decimal(String sql, Object... args) {
                return jdbc.queryForObject(sql, BigDecimal.class, args);
            }

            private int count(String sql, Object... args) {
                Integer value = jdbc.queryForObject(sql, Integer.class, args);
                return value == null ? 0 : value;
            }

            private String key(String tag) {
                return prefix + ":" + tag + ":" + UUID.randomUUID();
            }
        }

        @Nested

        class SalesOrderPastePreviewIT {
            @Autowired
            private SalesOrderPasteService salesOrderPasteService;

            @Test
            @DisplayName("#1 五个商品一次粘贴全部匹配，价格按当前取价链路解析，不创建订单")
            void previewMatchesRowsWithoutCreatingOrder() {
                Long customerId = newCustomer();
                List<Long> skuIds = new ArrayList<>();
                List<SalesOrderPasteForm.Row> rows = new ArrayList<>();
                for (int i = 1; i <= 5; i++) {
                    Long skuId = newOnShelfSku("PASTE" + i);
                    skuIds.add(skuId);
                    rows.add(row(skuId, "2.0000", null));
                }
                int ordersBefore = countOrders(customerId);

                var preview = salesOrderPasteService.preview(form(customerId, rows), false);

                assertThat(preview.getTotalRows()).isEqualTo(5);
                assertThat(preview.getMatchedRows()).isEqualTo(5);
                assertThat(preview.getErrorRows()).isZero();
                assertThat(preview.getRows()).allSatisfy(row -> {
                    assertThat(row.isMatched()).isTrue();
                    assertThat(row.getSkuId()).isNotNull();
                    assertThat(row.getResolvedUnitPrice()).isEqualByComparingTo("1.2000");
                    assertThat(row.getPriceSource()).isEqualTo("MARKET");
                    assertThat(row.isManualPrice()).isFalse();
                });
                assertThat(preview.getRows()).extracting(SalesOrderPastePreviewRowVO::getSkuId)
                    .containsExactlyElementsOf(skuIds);
                assertThat(countOrders(customerId)).isEqualTo(ordersBefore);
            }

            @Test
            @DisplayName("#2 未知商品与单位不符都留在结果里并报错，不静默丢行")
            void unmatchedRowsStayVisibleWithErrors() {
                Long customerId = newCustomer();
                Long skuId = newOnShelfSku("PASTE-M");
                SalesOrderPasteForm.Row unknown = new SalesOrderPasteForm.Row();
                unknown.setProductName(prefix + "不存在的商品");
                unknown.setQuantity("1.0000");

                SalesOrderPasteForm.Row badUnit = row(skuId, "2.0000", null);
                badUnit.setUnit("件");

                var preview = salesOrderPasteService.preview(form(customerId, List.of(unknown, badUnit)), false);

                assertThat(preview.getTotalRows()).isEqualTo(2);
                assertThat(preview.getErrorRows()).isEqualTo(2);
                assertThat(preview.getRows()).hasSize(2);
                assertThat(preview.getRows().get(0).getErrors())
                    .extracting(error -> error.getCode()).contains("SKU_NOT_FOUND");
                assertThat(preview.getRows().get(1).getErrors())
                    .extracting(error -> error.getCode()).contains("UNIT_MISMATCH");
                assertThat(preview.getRows()).allSatisfy(row -> assertThat(row.isMatched()).isFalse());
            }

            @Test
            @DisplayName("#3 同一规格重复、无改价权限、无可用价格都不能被预览放过")
            void duplicateAndPricingBoundariesAreRejected() {
                Long customerId = newCustomer();
                Long skuId = newOnShelfSku("PASTE-D");
                SalesOrderPasteForm.Row first = row(skuId, "1.0000", null);
                SalesOrderPasteForm.Row duplicate = row(skuId, "1.0000", null);

                var preview = salesOrderPasteService.preview(form(customerId, List.of(first, duplicate)), false);
                assertThat(preview.getRows().get(1).getErrors())
                    .extracting(error -> error.getCode()).contains("SKU_DUPLICATE");

                SalesOrderPasteForm.Row manual = row(skuId, "1.0000", "2.5000");
                var forbidden = salesOrderPasteService.preview(form(customerId, List.of(manual)), false);
                assertThat(forbidden.getRows().getFirst().getErrors())
                    .extracting(error -> error.getCode()).contains("PRICE_OVERRIDE_FORBIDDEN");

                var allowed = salesOrderPasteService.preview(form(customerId, List.of(manual)), true);
                assertThat(allowed.getErrorRows()).isZero();
                assertThat(allowed.getRows().getFirst().isManualPrice()).isTrue();
                assertThat(allowed.getRows().getFirst().getRawUnitPrice()).isEqualTo("2.5000");
            }

            @Test
            @DisplayName("#4 其他业务员的客户：预览直接按越权拒绝，不返回协议价")
            void previewRejectsCustomerOutsideDataScope() {
                Long customerId = newCustomer();
                Long skuId = newOnShelfSku("PASTE-S");
                List<SalesOrderPasteForm.Row> rows = List.of(row(skuId, "2.0000", null));

                asEmployee(99001L, () -> assertThatThrownBy(() -> salesOrderPasteService.preview(form(customerId, rows), false))
                    .isExactlyInstanceOf(ScmDataScopeException.class));
            }

            @Test
            @DisplayName("#5 行备注与手工价原文原样返回，供前端追加到订单行")
            void previewKeepsRowRemarkAndRawPrice() {
                Long customerId = newCustomer();
                Long skuId = newOnShelfSku("PASTE-R");
                SalesOrderPasteForm.Row manual = row(skuId, "1.0000", "2.5000");
                manual.setRemark("去皮");

                var preview = salesOrderPasteService.preview(form(customerId, List.of(manual)), true);

                var row = preview.getRows().getFirst();
                assertThat(row.getRemark()).isEqualTo("去皮");
                assertThat(row.getRawUnitPrice()).isEqualTo("2.5000");
                assertThat(row.isManualPrice()).isTrue();
            }

            private SalesOrderPasteForm form(Long customerId, List<SalesOrderPasteForm.Row> rows) {
                SalesOrderPasteForm form = new SalesOrderPasteForm();
                form.setCustomerId(customerId);
                form.setRows(new ArrayList<>(rows));
                return form;
            }

            private SalesOrderPasteForm.Row row(Long skuId, String quantity, String unitPrice) {
                Map<String, Object> product = jdbc.queryForMap(
                    "SELECT sp.name product_name, k.spec_name, k.sale_unit FROM xsy_product_sku k "
                        + "JOIN xsy_product_spu sp ON sp.id = k.spu_id WHERE k.id = ?", skuId);
                SalesOrderPasteForm.Row row = new SalesOrderPasteForm.Row();
                row.setProductName(String.valueOf(product.get("product_name")));
                row.setSpecName(String.valueOf(product.get("spec_name")));
                row.setUnit(String.valueOf(product.get("sale_unit")));
                row.setQuantity(quantity);
                row.setUnitPrice(unitPrice);
                return row;
            }

            private int countOrders(Long customerId) {
                Integer value = jdbc.queryForObject("SELECT count(*) FROM xsy_sales_order WHERE customer_id = ?",
                    Integer.class, customerId);
                return value == null ? 0 : value;
            }

            /** 以非超管员工执行：客户归属别人时，预览必须按数据范围拒绝。 */
            private <T> T asEmployee(Long employeeId, Supplier<T> body) {
                RequestEmployee employee = new RequestEmployee();
                employee.setEmployeeId(employeeId);
                employee.setActualName("整单粘贴数据范围 IT");
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                employee.setAdministratorFlag(false);
                XsyRequestUtil.setRequestUser(employee);
                try {
                    return body.get();
                } finally {
                    RequestEmployee admin = new RequestEmployee();
                    admin.setEmployeeId(1L);
                    admin.setActualName("整单粘贴 IT 操作员");
                    admin.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                    admin.setAdministratorFlag(true);
                    XsyRequestUtil.setRequestUser(admin);
                }
            }
        }

        @Nested

        class ScmAgreementRepricePgIT {
            @Test
            void repriceUnconfirmedDraftRecordsBothPriceChangeAndAuditReason() {
                Long customer = newCustomer();
                Long sku = newOnShelfSku("REPRICE-PENDING");
                SalesOrderAddForm form = new SalesOrderAddForm();
                form.setCustomerId(customer);
                form.setOrderSource("ADMIN");
                var address = new OrderAddressForm();
                address.setReceiverName("重算测试");
                address.setReceiverPhone("13800000000");
                address.setAddress("重算测试地址");
                form.setAddress(address);
                var item = new SalesOrderItemForm();
                item.setSkuId(sku);
                item.setOrderedQuantity("2.0000");
                item.setManualPriceOverride(false);
                form.setItems(List.of(item));
                var draft = salesOrderService.create(form, prefix + ":create");
                Long orderId = draft.getOrderId();
                BigDecimal before = jdbc.queryForObject(
                        "SELECT draft_unit_price FROM xsy_sales_order_item WHERE order_id=?",
                        BigDecimal.class, orderId);
                jdbc.update("UPDATE xsy_product_sku SET market_price=2.5000 WHERE id=?", sku);
                evictMybatisCache();

                var request = new OrderRepriceForm();
                request.setOrderId(orderId);
                request.setVersion(draft.getVersion());
                request.setReason("采购市场参考价变化后人工核价");
                var repriced = salesOrderService.reprice(request);
                assertThat(repriced.getStatus()).isEqualTo("PENDING");
                assertThat(jdbc.queryForObject(
                        "SELECT draft_unit_price FROM xsy_sales_order_item WHERE order_id=?",
                        BigDecimal.class, orderId)).isEqualByComparingTo("2.5000");
                assertThat(jdbc.queryForObject(
                        "SELECT ordered_total_amount FROM xsy_sales_order WHERE id=?",
                        BigDecimal.class, orderId)).isEqualByComparingTo("5.0000");
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_order_operation_log WHERE order_id=? "
                        + "AND operation_type='REPRICE' AND reason LIKE '%人工核价%'",
                        Integer.class, orderId)).isEqualTo(1);
                assertThat(before).isNotEqualByComparingTo("2.5000");

                assertThatThrownBy(() -> salesOrderService.reprice(request))
                        .isInstanceOf(ScmBusinessException.class);
            }

            @Test
            void confirmedOrderCannotRepriceEvenWhenMarketPriceChanged() {
                Long customer = newCustomer();
                Long sku = newOnShelfSku("REPRICE-FROZEN");
                Long orderId = confirmedSalesOrder(customer, sku, "1.0000", "1.0000");
                jdbc.update("UPDATE xsy_product_sku SET market_price=99.0000 WHERE id=?", sku);
                evictMybatisCache();

                var request = new OrderRepriceForm();
                request.setOrderId(orderId);
                request.setVersion(jdbc.queryForObject(
                        "SELECT version FROM xsy_sales_order WHERE id=?", Integer.class, orderId));
                request.setReason("订单已确认后禁止重算");
                assertThatThrownBy(() -> salesOrderService.reprice(request))
                        .isInstanceOf(ScmBusinessException.class);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_order_operation_log WHERE order_id=? "
                        + "AND operation_type='REPRICE'", Integer.class, orderId)).isZero();
            }
        }

        @Nested

        class ScmBundleAdminOrderPgIT {
            @Autowired
            private ProductBundleService bundleService;

            @Test
            void adminBundleOrderFreezesExplicitWarehouseAndComponentAllocations() {
                Long warehouse = seedWarehouseId();
                Long bundle = standardSku("ADMIN-BUNDLE");
                Long tomato = standardSku("ADMIN-TOMATO");
                Long potato = standardSku("ADMIN-POTATO");
                var components = new ProductBundleReplaceForm();
                components.setBundleSkuId(bundle);
                components.setVersion(jdbc.queryForObject(
                        "SELECT version FROM xsy_product_sku WHERE id=?", Integer.class, bundle));
                components.setComponents(List.of(item(tomato, "2.0000"), item(potato, "3.0000")));
                bundleService.replace(components);

                SalesOrderAddForm create = new SalesOrderAddForm();
                create.setCustomerId(newCustomer());
                create.setOrderSource("ADMIN");
                create.setDeliveryWarehouseId(warehouse);
                var address = new OrderAddressForm();
                address.setReceiverName("测试收货人");
                address.setReceiverPhone("13800000000");
                address.setAddress("测试收货地址");
                create.setAddress(address);
                var orderItem = new SalesOrderItemForm();
                orderItem.setSkuId(bundle);
                orderItem.setOrderedQuantity("1.0000");
                orderItem.setManualPriceOverride(false);
                create.setItems(List.of(orderItem));

                var order = salesOrderService.createAndProgress(create, prefix + ":admin-bundle-create");
                assertThat(order.getStatus()).isEqualTo("CONFIRMED");
                assertThat(jdbc.queryForObject(
                        "SELECT mall_delivery_warehouse_id FROM xsy_sales_order WHERE id=?",
                        Long.class, order.getOrderId())).isEqualTo(warehouse);
                var frozen = jdbc.queryForList(
                        "SELECT component_sku_id, market_unit_price_snapshot, allocated_sale_amount_snapshot "
                        + "FROM xsy_order_bundle_component WHERE order_id=? ORDER BY component_sku_id",
                        order.getOrderId());
                assertThat(frozen).hasSize(2);
                BigDecimal sum = jdbc.queryForObject(
                        "SELECT sum(allocated_sale_amount_snapshot) FROM xsy_order_bundle_component WHERE order_id=?",
                        BigDecimal.class, order.getOrderId());
                assertThat(sum).isEqualByComparingTo(
                        jdbc.queryForObject("SELECT settlement_total_amount FROM xsy_sales_order WHERE id=?",
                                BigDecimal.class, order.getOrderId()));
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_inventory_reservation WHERE source_document_id=?",
                        Integer.class, order.getOrderId())).isZero();
            }

            private Long standardSku(String suffix) {
                var spu = new ProductSpuAddForm();
                spu.setSpuCode(prefix + suffix);
                spu.setName(prefix + suffix + "商品");
                spu.setStatus("ON_SHELF");
                spu.setCategoryId(jdbc.queryForObject(
                        "SELECT id FROM xsy_product_category WHERE category_code='FRESH-FRUIT' AND deleted=FALSE",
                        Long.class));
                var sku = new ProductSkuForm();
                sku.setSkuCode(prefix + suffix + "-K");
                sku.setSpecName("规格" + suffix);
                sku.setSpecValues(new LinkedHashMap<>(Map.of("规格", suffix)));
                sku.setSaleUnit("kg");
                sku.setProductType("STANDARD");
                sku.setMarketPrice(new BigDecimal("10.0000"));
                sku.setStatus("ON_SHELF");
                sku.setDefaultFlag(true);
                spu.setSkuList(new ArrayList<>(List.of(sku)));
                Long spuId = productSpuService.add(spu);
                return jdbc.queryForObject("SELECT id FROM xsy_product_sku WHERE spu_id=? AND deleted=FALSE",
                        Long.class, spuId);
            }

            private static ProductBundleReplaceForm.Component item(Long skuId, String qty) {
                var item = new ProductBundleReplaceForm.Component();
                item.setSkuId(skuId);
                item.setQuantityPerBundleSaleUnit(qty);
                return item;
            }
        }

        @Nested

        class ScmBundleReturnFlowPgIT {
            @Autowired
            private ProductBundleService bundleService;

            @Autowired
            private InventoryFulfillmentService fulfillmentService;

            @Autowired
            private OrderReturnService returnService;

            @Autowired
            private OrderReturnReceiptService receiptService;

            @Test
            void fullBundleReturnRestocksTwoComponentsButCountsOneBundleOnce() {
                ((RequestEmployee) XsyRequestUtil.getRequestUser()).setAdministratorFlag(true);
                Long warehouse = seedWarehouseId();
                Long bundle = standardSku("RT-BUNDLE");
                Long first = standardSku("RT-A");
                Long second = standardSku("RT-B");
                var configuration = new ProductBundleReplaceForm();
                configuration.setBundleSkuId(bundle);
                configuration.setVersion(jdbc.queryForObject(
                        "SELECT version FROM xsy_product_sku WHERE id=?", Integer.class, bundle));
                configuration.setComponents(List.of(component(first, "2.0000"), component(second, "3.0000")));
                bundleService.replace(configuration);

                Long firstBalance = stock(warehouse, first, "30.0000", "5.0000");
                Long secondBalance = stock(warehouse, second, "30.0000", "8.0000");
                Long customer = newCustomer();
                var orderForm = new SalesOrderAddForm();
                orderForm.setCustomerId(customer);
                orderForm.setOrderSource("ADMIN");
                orderForm.setDeliveryWarehouseId(warehouse);
                var address = new OrderAddressForm();
                address.setReceiverName("组合退货测试");
                address.setReceiverPhone("13800000000");
                address.setAddress("仓库测试地址");
                orderForm.setAddress(address);
                var item = new SalesOrderItemForm();
                item.setSkuId(bundle);
                item.setOrderedQuantity("2.0000");
                item.setManualPriceOverride(false);
                orderForm.setItems(List.of(item));
                var confirmed = salesOrderService.createAndProgress(orderForm, prefix + ":create-bundle-order");
                assertThat(confirmed.getStatus()).isEqualTo("CONFIRMED");
                Long orderId = confirmed.getOrderId();
                Long itemId = jdbc.queryForObject(
                        "SELECT id FROM xsy_sales_order_item WHERE order_id=?", Long.class, orderId);
                var componentIds = jdbc.queryForList(
                        "SELECT id FROM xsy_order_bundle_component WHERE order_id=? ORDER BY component_sku_id",
                        Long.class, orderId);
                assertThat(componentIds).hasSize(2);
                salesOrderService.reserveStock(orderId);
                assertThat(reserved(firstBalance)).isEqualByComparingTo("4.0000");
                assertThat(reserved(secondBalance)).isEqualByComparingTo("6.0000");

                var outbound = fulfillmentService.dispatchOutbound(new InventoryFulfillmentService.Command(
                        998991L, warehouse, OffsetDateTime.now(), "bundle-return-it", List.of(
                                new InventoryFulfillmentService.Line(orderId, itemId, first, new BigDecimal("4.0000"),
                                        "kg", BigDecimal.ONE, componentIds.get(0), bundle, new BigDecimal("2.0000")),
                                new InventoryFulfillmentService.Line(orderId, itemId, second, new BigDecimal("6.0000"),
                                        "kg", BigDecimal.ONE, componentIds.get(1), bundle, new BigDecimal("2.0000")))));
                assertThat(outbound.shippedLineCount()).isEqualTo(1);
                assertThat(quantity(firstBalance)).isEqualByComparingTo("26.0000");
                assertThat(quantity(secondBalance)).isEqualByComparingTo("24.0000");

                var requested = new OrderReturnItemForm();
                requested.setOrderItemId(itemId);
                requested.setRequestedQuantity("1.0000");
                var createReturn = new OrderReturnAddForm();
                createReturn.setOrderId(orderId);
                createReturn.setReason("整套组合商品退货测试");
                createReturn.setItems(List.of(requested));
                var pending = returnService.create(createReturn, prefix + ":request-return");
                var approvedItem = new OrderReturnApproveItemForm();
                approvedItem.setOrderItemId(itemId);
                approvedItem.setApprovedQuantity("1.0000");
                var approve = new OrderReturnApproveForm();
                approve.setReturnId(pending.getReturnId());
                approve.setVersion(pending.getVersion());
                approve.setItems(List.of(approvedItem));
                var approved = returnService.approve(approve, prefix + ":approve-return");

                var receivedItem = new OrderReturnReceiveItemForm();
                receivedItem.setReturnItemId(approved.getItems().getFirst().getReturnItemId());
                receivedItem.setQuantity("1.0000");
                receivedItem.setDisposition("RETURN_TO_STOCK");
                var receive = new OrderReturnReceiveForm();
                receive.setReturnId(approved.getReturnId());
                receive.setVersion(approved.getVersion());
                receive.setWarehouseId(warehouse);
                receive.setItems(List.of(receivedItem));
                var receipt = receiptService.receive(receive, prefix + ":receive-return");

                assertThat(receipt.getItems()).hasSize(2);
                assertThat(receipt.getItems()).extracting(row -> row.getStockQuantity().toPlainString())
                        .containsExactlyInAnyOrder("2.0000", "3.0000");
                assertThat(receipt.getItems().stream().filter(row ->
                        Boolean.TRUE.equals(row.getBundleReceiptCounted())).count()).isEqualTo(1);
                assertThat(returnService.detail(approved.getReturnId()).getItems().getFirst().getReceivedQuantity())
                        .isEqualByComparingTo("1.0000");
                assertThat(quantity(firstBalance)).isEqualByComparingTo("28.0000");
                assertThat(quantity(secondBalance)).isEqualByComparingTo("27.0000");
                assertThat(reserved(firstBalance)).isEqualByComparingTo("0.0000");
                assertThat(reserved(secondBalance)).isEqualByComparingTo("0.0000");
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_inventory_movement WHERE movement_type='SALES_RETURN_IN' "
                        + "AND sku_id=?", Integer.class, bundle)).isZero();
            }

            private Long standardSku(String suffix) {
                var spu = new ProductSpuAddForm();
                spu.setSpuCode(prefix + suffix);
                spu.setName(prefix + suffix + "商品");
                spu.setStatus("ON_SHELF");
                spu.setCategoryId(jdbc.queryForObject(
                        "SELECT id FROM xsy_product_category WHERE category_code='FRESH-FRUIT' AND deleted=FALSE",
                        Long.class));
                var sku = new ProductSkuForm();
                sku.setSkuCode(prefix + suffix + "-K");
                sku.setSpecName("规格" + suffix);
                sku.setSpecValues(new LinkedHashMap<>(Map.of("规格", suffix)));
                sku.setSaleUnit("kg");
                sku.setProductType("STANDARD");
                sku.setMarketPrice(new BigDecimal("10.0000"));
                sku.setStatus("ON_SHELF");
                sku.setDefaultFlag(true);
                spu.setSkuList(new ArrayList<>(List.of(sku)));
                Long spuId = productSpuService.add(spu);
                return jdbc.queryForObject("SELECT id FROM xsy_product_sku WHERE spu_id=? AND deleted=FALSE",
                        Long.class, spuId);
            }

            private static ProductBundleReplaceForm.Component component(Long sku, String quantity) {
                var item = new ProductBundleReplaceForm.Component();
                item.setSkuId(sku);
                item.setQuantityPerBundleSaleUnit(quantity);
                return item;
            }

            private Long stock(Long warehouse, Long sku, String quantity, String cost) {
                return jdbc.queryForObject(
                        "INSERT INTO xsy_inventory_balance (warehouse_id,sku_id,unit,quantity,reserved_quantity,avg_cost)"
                        + " VALUES (?,?,'kg',?::numeric,0.0000,?::numeric) RETURNING id",
                        Long.class, warehouse, sku, quantity, cost);
            }

            private BigDecimal quantity(Long id) {
                return jdbc.queryForObject("SELECT quantity FROM xsy_inventory_balance WHERE id=?",
                        BigDecimal.class, id);
            }

            private BigDecimal reserved(Long id) {
                return jdbc.queryForObject("SELECT reserved_quantity FROM xsy_inventory_balance WHERE id=?",
                        BigDecimal.class, id);
            }
        }
    }

    @Nested
    class OrderW6PgIT extends ScmW6PgITBase {
        @Nested

        class AdminSettlementReturnCreditPgIT {
            @Autowired private OrderReturnService orderReturnService;
            @Autowired private OrderReturnReceiptService orderReturnReceiptService;
            @Autowired private InventoryOutboundService inventoryOutboundService;
            @Autowired private InventoryFulfillmentService inventoryFulfillmentService;
            @Autowired private SalesOrderQueryService salesOrderQueryService;
            @Autowired private OrderCreditService orderCreditService;

            private com.xsy.scm.order.domain.form.OrderVersionForm salesOrderVersion(SalesOrderDetailVO order) {
                var form = new com.xsy.scm.order.domain.form.OrderVersionForm();
                form.setOrderId(order.getOrderId());
                form.setVersion(order.getVersion());
                return form;
            }

            private String key() { return UUID.randomUUID().toString(); }

            private record Returned(Long skuId, Long warehouseId, OrderReturnDetailVO returned) { }

            private Returned returned() {
                Long skuId = newOnShelfSku("return");
                W6Fixture fixture = inboundFixture("return", skuId, "10.0000");
                confirmReceipt(fixture.receipt().getId(), "10.0000");
                Long customerId = newCustomer();
                Long orderId = confirmedSalesOrder(customerId, skuId, "5.0000", "5.0000");
                Long orderItemId = confirmedSalesOrderItemId(orderId);
                InventoryOutboundAddForm outbound = new InventoryOutboundAddForm();
                outbound.setWarehouseId(seedWarehouseId());
                InventoryOutboundAddForm.Item outboundItem = new InventoryOutboundAddForm.Item();
                outboundItem.setSkuId(skuId);
                outboundItem.setQuantity(new BigDecimal("5.0000"));
                outbound.setItems(List.of(outboundItem));
                Long outboundId = inventoryOutboundService.create(outbound);
                // Link a real inventory command to its sales source, as the delivery adapter does.
                jdbc.update("UPDATE xsy_inventory_outbound_item SET sales_order_id=?, sales_order_item_id=? WHERE outbound_id=?", orderId, orderItemId, outboundId);
                evictMybatisCache();
                inventoryOutboundService.confirm(outboundId);
                OrderReturnItemForm item = new OrderReturnItemForm();
                item.setOrderItemId(orderItemId);
                item.setRequestedQuantity("5.0000");
                OrderReturnAddForm form = new OrderReturnAddForm();
                form.setOrderId(orderId);
                form.setReason("验收退货");
                form.setItems(List.of(item));
                var returned = orderReturnService.create(form, key());
                OrderReturnApproveItemForm approvedItem = new OrderReturnApproveItemForm();
                approvedItem.setOrderItemId(orderItemId);
                approvedItem.setApprovedQuantity("5.0000");
                OrderReturnApproveForm approve = new OrderReturnApproveForm();
                approve.setReturnId(returned.getReturnId());
                approve.setVersion(returned.getVersion());
                approve.setItems(List.of(approvedItem));
                return new Returned(skuId, seedWarehouseId(), orderReturnService.approve(approve, key()));
            }

            private Returned splitCostReturned() {
                Long skuId = newOnShelfSku("return-fifo");
                W6Fixture firstInbound = freeInboundFixture("return-fifo-first", skuId, "2.0000", "kg");
                confirmReceipt(firstInbound.receipt().getId(), "2.0000");

                Long customerId = newCustomer();
                Long orderId = confirmedSalesOrder(customerId, skuId, "5.0000", "5.0000");
                Long orderItemId = confirmedSalesOrderItemId(orderId);
                confirmSalesOutbound(orderId, orderItemId, skuId, "2.0000");

                Long supplierId = newSupplier("return-fifo-second");
                linkSupplierSku(supplierId, skuId, "kg");
                var secondOrder = createDraftOrder("return-fifo-second", supplierId, skuId, "3.0000", "8.4000");
                var secondReceipt = submittedOrderReceipt(secondOrder.getId());
                confirmReceipt(secondReceipt.getId(), "3.0000");
                confirmSalesOutbound(orderId, orderItemId, skuId, "3.0000");

                OrderReturnItemForm item = new OrderReturnItemForm();
                item.setOrderItemId(orderItemId);
                item.setRequestedQuantity("5.0000");
                OrderReturnAddForm form = new OrderReturnAddForm();
                form.setOrderId(orderId);
                form.setReason("FIFO 成本验收退货");
                form.setItems(List.of(item));
                var returned = orderReturnService.create(form, key());
                OrderReturnApproveItemForm approvedItem = new OrderReturnApproveItemForm();
                approvedItem.setOrderItemId(orderItemId);
                approvedItem.setApprovedQuantity("5.0000");
                OrderReturnApproveForm approve = new OrderReturnApproveForm();
                approve.setReturnId(returned.getReturnId());
                approve.setVersion(returned.getVersion());
                approve.setItems(List.of(approvedItem));
                return new Returned(skuId, seedWarehouseId(), orderReturnService.approve(approve, key()));
            }

            private void confirmSalesOutbound(Long orderId, Long orderItemId, Long skuId, String quantity) {
                InventoryOutboundAddForm outbound = new InventoryOutboundAddForm();
                outbound.setWarehouseId(seedWarehouseId());
                InventoryOutboundAddForm.Item outboundItem = new InventoryOutboundAddForm.Item();
                outboundItem.setSkuId(skuId);
                outboundItem.setQuantity(new BigDecimal(quantity));
                outbound.setItems(List.of(outboundItem));
                Long outboundId = inventoryOutboundService.create(outbound);
                jdbc.update("UPDATE xsy_inventory_outbound_item SET sales_order_id=?, sales_order_item_id=? WHERE outbound_id=?",
                        orderId, orderItemId, outboundId);
                evictMybatisCache();
                inventoryOutboundService.confirm(outboundId);
            }

            private void shadowInventoryMovements() {
                jdbc.execute("CREATE TEMP TABLE movement_shadow (LIKE xsy_inventory_movement INCLUDING ALL) ON COMMIT DROP");
                jdbc.execute("INSERT INTO pg_temp.movement_shadow SELECT * FROM xsy_inventory_movement");
                jdbc.execute("ALTER TABLE pg_temp.movement_shadow RENAME TO xsy_inventory_movement");
                jdbc.execute("SELECT setval(pg_get_serial_sequence('pg_temp.xsy_inventory_movement', 'id'), "
                        + "COALESCE((SELECT max(id) FROM pg_temp.xsy_inventory_movement), 1), "
                        + "EXISTS (SELECT 1 FROM pg_temp.xsy_inventory_movement))");
                evictMybatisCache();
            }

            private OrderReturnReceiveForm receiveForm(Returned fixture, String quantity, String disposition) {
                OrderReturnReceiveItemForm item = new OrderReturnReceiveItemForm();
                item.setReturnItemId(fixture.returned().getItems().getFirst().getReturnItemId());
                item.setQuantity(quantity);
                item.setDisposition(disposition);
                OrderReturnReceiveForm form = new OrderReturnReceiveForm();
                form.setReturnId(fixture.returned().getReturnId());
                form.setVersion(fixture.returned().getVersion());
                form.setWarehouseId(fixture.warehouseId());
                form.setItems(List.of(item));
                return form;
            }

            @Test void receiptUsesHistoricalCostAndReplayDoesNotDuplicateStock() {
                var fixture = returned();
                var form = receiveForm(fixture, "2.0000", "RETURN_TO_STOCK");
                String key = key();
                var first = orderReturnReceiptService.receive(form, key);
                assertThat(orderReturnReceiptService.receive(form, key).getReceiptId()).isEqualTo(first.getReceiptId());
                assertThat(balanceRow(fixture.warehouseId(), fixture.skuId()).getQuantity()).isEqualByComparingTo("7.0000");
                assertThat(jdbc.queryForObject("SELECT unit_cost FROM xsy_order_return_receipt_item WHERE receipt_id=?",
                        BigDecimal.class, first.getReceiptId())).isEqualByComparingTo("6.2000");
                orderReturnReceiptService.receive(receiveForm(fixture, "1.0000", "DAMAGE"), key());
                assertThat(balanceRow(fixture.warehouseId(), fixture.skuId()).getQuantity()).isEqualByComparingTo("7.0000");
                assertThat(orderReturnService.detail(fixture.returned().getReturnId()).getItems().getFirst().getReceivedQuantity())
                        .isEqualByComparingTo("3.0000");
            }

            @Test
            void crossUnitReturnUsesFrozenSalesToStockFactorForTwoSeparateReceipts() {
                Long skuId = newOnShelfSku("box-return");
                W6Fixture inbound = freeInboundFixture("box-return", skuId, "100.0000", "kg");
                confirmReceipt(inbound.receipt().getId(), "100.0000");
                jdbc.update("UPDATE xsy_product_sku SET sale_unit='箱' WHERE id=?", skuId);
                evictMybatisCache();

                Long orderId = confirmedSalesOrder(newCustomer(), skuId, "2.0000", "2.0000");
                Long itemId = confirmedSalesOrderItemId(orderId);
                // 先配置经订单确认保存的已存数据记录；被测范围是正式库存出库与退货接收的单位闭环。
                jdbc.update("UPDATE xsy_sales_order_item SET mall_stock_unit_snapshot='kg',"
                        + " mall_stock_units_per_sale_unit=10.0000 WHERE id=?", itemId);
                evictMybatisCache();
                var outbound = inventoryFulfillmentService.dispatchOutbound(
                        new InventoryFulfillmentService.Command(970000L + skuId, seedWarehouseId(),
                                java.time.OffsetDateTime.now(), "W6 IT",
                                List.of(new InventoryFulfillmentService.Line(orderId, itemId, skuId,
                                        new BigDecimal("2.0000"), "kg", new BigDecimal("10.0000")))));
                assertThat(outbound.shippedLineCount()).isEqualTo(1);
                assertThat(balanceRow(seedWarehouseId(), skuId).getQuantity()).isEqualByComparingTo("80.0000");

                OrderReturnItemForm requested = new OrderReturnItemForm();
                requested.setOrderItemId(itemId);
                requested.setRequestedQuantity("2.0000");
                OrderReturnAddForm create = new OrderReturnAddForm();
                create.setOrderId(orderId);
                create.setReason("箱转公斤分批退货");
                create.setItems(List.of(requested));
                var draft = orderReturnService.create(create, key());
                OrderReturnApproveItemForm approvedLine = new OrderReturnApproveItemForm();
                approvedLine.setOrderItemId(itemId);
                approvedLine.setApprovedQuantity("2.0000");
                OrderReturnApproveForm approve = new OrderReturnApproveForm();
                approve.setReturnId(draft.getReturnId());
                approve.setVersion(draft.getVersion());
                approve.setItems(List.of(approvedLine));
                var approved = orderReturnService.approve(approve, key());
                Returned fixture = new Returned(skuId, seedWarehouseId(), approved);

                var first = orderReturnReceiptService.receive(receiveForm(fixture, "1.0000", "RETURN_TO_STOCK"), key());
                assertThat(first.getItems()).hasSize(1);
                assertThat(first.getItems().getFirst().getQuantity()).isEqualByComparingTo("1.0000");
                assertThat(first.getItems().getFirst().getStockQuantity()).isEqualByComparingTo("10.0000");
                assertThat(first.getItems().getFirst().getUnit()).isEqualTo("箱");
                assertThat(first.getItems().getFirst().getStockUnit()).isEqualTo("kg");
                assertThat(balanceRow(seedWarehouseId(), skuId).getQuantity()).isEqualByComparingTo("90.0000");

                var second = orderReturnReceiptService.receive(receiveForm(fixture, "1.0000", "RETURN_TO_STOCK"), key());
                assertThat(second.getItems().getFirst().getStockQuantity()).isEqualByComparingTo("10.0000");
                assertThat(balanceRow(seedWarehouseId(), skuId).getQuantity()).isEqualByComparingTo("100.0000");
                assertThat(orderReturnService.detail(approved.getReturnId()).getItems().getFirst().getReceivedQuantity())
                        .isEqualByComparingTo("2.0000");
                assertThat(jdbc.queryForObject(
                        "SELECT COALESCE(SUM(stock_quantity),0) FROM xsy_order_return_receipt_item "
                                + "WHERE return_item_id = ?", BigDecimal.class,
                        approved.getItems().getFirst().getReturnItemId())).isEqualByComparingTo("20.0000");
            }

            @Test void partialReturnSplitsAcrossHistoricalSalesOutCostsInFifoOrder() {
                var fixture = splitCostReturned();
                Long orderItemId = fixture.returned().getItems().getFirst().getOrderItemId();
                var allocations = inventoryMovementDao.listSalesOutAllocations(orderItemId);
                assertThat(allocations).extracting(m -> m.getQuantity().toPlainString())
                        .containsExactly("2.0000", "3.0000");
                assertThat(allocations).extracting(m -> m.getUnitCost().toPlainString())
                        .containsExactly("6.2000", "8.4000");

                var result = orderReturnReceiptService.receive(receiveForm(fixture, "4.0000", "RETURN_TO_STOCK"), key());

                assertThat(result.getItems()).extracting(i -> i.getQuantity().toPlainString())
                        .containsExactly("2.0000", "2.0000");
                assertThat(jdbc.queryForList("SELECT unit_cost FROM xsy_order_return_receipt_item WHERE receipt_id=? ORDER BY id",
                        BigDecimal.class, result.getReceiptId())).containsExactly(new BigDecimal("6.2000"), new BigDecimal("8.4000"));
                assertThat(result.getItems()).extracting(i -> i.getSourceSalesOutMovementId())
                        .containsExactly(allocations.get(0).getId(), allocations.get(1).getId());
                assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_inventory_movement WHERE movement_type='SALES_RETURN_IN'"
                        + " AND source_document_id=?", Integer.class, fixture.returned().getReturnId())).isEqualTo(2);
                assertThat(balanceRow(fixture.warehouseId(), fixture.skuId()).getQuantity()).isEqualByComparingTo("4.0000");
            }

            @Test
            @Transactional(propagation = Propagation.NOT_SUPPORTED)
            void excessiveReceiptRollsBackHeaderAndStock() {
                var fixture = returned();
                orderReturnReceiptService.receive(receiveForm(fixture, "4.0000", "DAMAGE"), key());
                assertThatThrownBy(() -> orderReturnReceiptService.receive(receiveForm(fixture, "2.0000", "RETURN_TO_STOCK"), key()))
                        .isInstanceOf(ScmBusinessException.class);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_order_return_receipt WHERE return_id=?", Integer.class,
                        fixture.returned().getReturnId())).isEqualTo(1);
                assertThat(balanceRow(fixture.warehouseId(), fixture.skuId()).getQuantity()).isEqualByComparingTo("5.0000");
            }

            @Test void replayRechecksOwnerAndWarehousePermissions() {
                var fixture = returned();
                var form = receiveForm(fixture, "1.0000", "DAMAGE");
                String key = key();
                orderReturnReceiptService.receive(form, key);
                RequestEmployee employee = new RequestEmployee();
                employee.setEmployeeId(987654321L);
                employee.setAdministratorFlag(false);
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                XsyRequestUtil.setRequestUser(employee);
                assertThatThrownBy(() -> orderReturnReceiptService.receive(form, key)).isInstanceOf(ScmDataScopeException.class);
            }

            @Test
            @Transactional(propagation = Propagation.NOT_SUPPORTED)
            void staleVersionRejectsNewReceipt() {
                var fixture = returned();
                var form = receiveForm(fixture, "1.0000", "RETURN_TO_STOCK");
                form.setVersion(form.getVersion() + 1);
                expectCode(() -> orderReturnReceiptService.receive(form, key()), 40921);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_order_return_receipt WHERE return_id=?", Integer.class,
                        fixture.returned().getReturnId())).isZero();
            }

            @Test void receiptRequiresWarehouseScopeEvenForDamageAndReplay() {
                var fixture = returned();
                Long otherWarehouse = newWarehouse("return-target");
                var form = receiveForm(fixture, "1.0000", "DAMAGE");
                form.setWarehouseId(otherWarehouse);
                String key = key();
                orderReturnReceiptService.receive(form, key);
                jdbc.update("UPDATE xsy_sales_order SET seller_id=1 WHERE id=?", fixture.returned().getOrderId());
                evictMybatisCache();
                RequestEmployee employee = new RequestEmployee();
                employee.setEmployeeId(1L);
                employee.setAdministratorFlag(false);
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                XsyRequestUtil.setRequestUser(employee);
                assertThatThrownBy(() -> orderReturnReceiptService.receive(form, key)).isInstanceOf(ScmDataScopeException.class);
                assertThatThrownBy(() -> orderReturnReceiptService.receive(form, key())).isInstanceOf(ScmDataScopeException.class);
            }

            @Test void selectedHistoricalAllocationRequiresFrozenCost() {
                var fixture = returned();
                Long orderItemId = fixture.returned().getItems().getFirst().getOrderItemId();
                Long movementId = inventoryMovementDao.listSalesOutAllocations(orderItemId).getFirst().getId();
                shadowInventoryMovements();
                assertThat(jdbc.update("UPDATE pg_temp.xsy_inventory_movement SET unit_cost=NULL WHERE id=?", movementId)).isEqualTo(1);
                evictMybatisCache();
                assertThat(inventoryMovementDao.listSalesOutAllocations(orderItemId)).anyMatch(m -> m.getUnitCost() == null);
                assertThatThrownBy(() -> orderReturnReceiptService.receive(receiveForm(fixture,"1.0000","RETURN_TO_STOCK"),key()))
                        .isInstanceOf(ScmBusinessException.class);
            }

            private SalesOrderDetailVO pending(Long customerId, Long skuId, String ordered, String actual) {
                SalesOrderAddForm form = orderForm(customerId, skuId, ordered);
                var order = salesOrderService.create(form, key());
                OrderActualQuantityForm quantity = new OrderActualQuantityForm();
                quantity.setOrderId(order.getOrderId());
                quantity.setItemId(order.getItems().getFirst().getItemId());
                quantity.setVersion(order.getItems().getFirst().getVersion());
                quantity.setActualQuantity(actual);
                quantity.setReason("实际验收数量");
                return salesOrderService.actualQuantity(quantity, key());
            }

            private SalesOrderAddForm orderForm(Long customerId, Long skuId, String quantity) {
                SalesOrderAddForm form = new SalesOrderAddForm();
                form.setCustomerId(customerId);
                form.setOrderSource("ADMIN");
                OrderAddressForm address = new OrderAddressForm();
                address.setReceiverName("收货人");
                address.setReceiverPhone("13800000000");
                address.setAddress("验收地址");
                form.setAddress(address);
                SalesOrderItemForm item = new SalesOrderItemForm();
                item.setSkuId(skuId);
                item.setOrderedQuantity(quantity);
                item.setManualPriceOverride(false);
                form.setItems(List.of(item));
                return form;
            }

            private Long pricedSku(String type) {
                Long skuId = newSkuOfType("credit", type, "ON_SHELF");
                jdbc.update("UPDATE xsy_product_sku SET market_price=10 WHERE id=?", skuId);
                evictMybatisCache();
                return skuId;
            }

            @Test void actualWeightAmountControlsCreditAndFailureLeavesOrderPending() {
                Long customerId = newCustomer();
                Long skuId = pricedSku("NON_STANDARD");
                jdbc.update("UPDATE xsy_customer SET credit_limit=15 WHERE id=?", customerId);
                evictMybatisCache();
                var order = pending(customerId, skuId, "1.0000", "2.0000");
                var check = salesOrderService.creditCheck(order.getOrderId());
                assertThat(check.getRequestedOrderAmount()).isEqualByComparingTo("20.0000");
                assertThat(check.getAllowed()).isFalse();
                expectCode(() -> salesOrderService.confirm(salesOrderVersion(order), key()), 41201);
                assertThat(salesOrderQueryService.detail(order.getOrderId()).getStatus()).isEqualTo("PENDING");
            }

            @Test
            @Transactional(propagation = Propagation.NOT_SUPPORTED)
            void automaticStandardConfirmationCannotBypassCredit() {
                Long customerId = newCustomer();
                Long skuId = pricedSku("STANDARD");
                jdbc.update("UPDATE xsy_customer SET credit_limit=5 WHERE id=?", customerId);
                evictMybatisCache();
                expectCode(() -> salesOrderService.createAndProgress(orderForm(customerId, skuId, "1.0000"), key()), 41201);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_sales_order WHERE customer_id=?", Integer.class, customerId)).isZero();
            }

            @Test void frozenSettlementSurvivesCustomerMasterChange() {
                Long customerId = newCustomer();
                Long otherId = newCustomer();
                Long skuId = pricedSku("NON_STANDARD");
                var order = pending(customerId, skuId, "1.0000", "1.0000");
                jdbc.update("UPDATE xsy_customer SET settlement_customer_id=? WHERE id=?", otherId, customerId);
                jdbc.update("UPDATE xsy_customer SET credit_limit=5 WHERE id=?", customerId);
                evictMybatisCache();
                expectCode(() -> salesOrderService.confirm(salesOrderVersion(order), key()), 41201);
                assertThat(jdbc.queryForObject("SELECT settlement_customer_id FROM xsy_sales_order WHERE id=?", Long.class, order.getOrderId()))
                        .isEqualTo(customerId);
            }

            @Test void explicitCreditOverrideChecksPermissionAndFreezesReason() {
                Long customerId = newCustomer();
                Long skuId = pricedSku("NON_STANDARD");
                var order = pending(customerId, skuId, "1.0000", "1.0000");
                jdbc.update("UPDATE xsy_customer SET credit_limit=5 WHERE id=?", customerId);
                evictMybatisCache();
                var form = new com.xsy.scm.order.domain.form.OrderConfirmForm();
                form.setOrderId(order.getOrderId());
                form.setVersion(order.getVersion());
                form.setCreditOverride(true);
                form.setCreditOverrideReason("主管批准临时额度");
                try (var permissions = org.mockito.Mockito.mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
                    salesOrderService.confirm(form,key());
                    permissions.verify(() -> cn.dev33.satoken.stp.StpUtil.checkPermission(
                            com.xsy.scm.order.permission.OrderPermission.CREDIT_OVERRIDE));
                }
                assertThat(jdbc.queryForObject("SELECT credit_override_reason FROM xsy_sales_order WHERE id=?",String.class,order.getOrderId()))
                        .isEqualTo("主管批准临时额度");
                assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_order_operation_log WHERE order_id=? AND operation_type='CREDIT_OVERRIDE'",
                        Integer.class,order.getOrderId())).isEqualTo(1);
            }

            @Test
            @Transactional(propagation = Propagation.NOT_SUPPORTED)
            void concurrentConfirmationsShareOneCreditAccountLock() throws Exception {
                Long customerId = newCustomer();
                Long skuId = pricedSku("NON_STANDARD");
                var first = pending(customerId, skuId, "1.0000", "1.0000");
                var second = pending(customerId, skuId, "1.0000", "1.0000");
                jdbc.update("UPDATE xsy_customer SET credit_limit=15 WHERE id=?", customerId);
                var start = new CountDownLatch(1);
                try (var executor = Executors.newFixedThreadPool(2)) {
                    var results = List.of(first, second).stream().map(order -> executor.submit(() -> {
                        RequestEmployee employee = new RequestEmployee();
                        employee.setEmployeeId(1L);
                        employee.setAdministratorFlag(true);
                        employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                        employee.setActualName("并发授信验收");
                        XsyRequestUtil.setRequestUser(employee);
                        try {
                            start.await(10, TimeUnit.SECONDS);
                            salesOrderService.confirm(salesOrderVersion(order), key());
                            return true;
                        } catch (ScmBusinessException e) {
                            assertThat(e.getErrorCode().getCode()).isEqualTo(41201);
                            return false;
                        } finally { XsyRequestUtil.remove(); }
                    })).toList();
                    start.countDown();
                    int succeeded = 0;
                    for (var result : results) if (result.get(20, TimeUnit.SECONDS)) succeeded++;
                    assertThat(succeeded).isEqualTo(1);
                }
                assertThat(orderCreditService.check(customerId, BigDecimal.ZERO).getConfirmedOrderAmount())
                        .isEqualByComparingTo("10.0000");
            }
        }

        @Nested

        class OrderSingleStateTagPgIT {
            @Autowired
            private SalesOrderDispatchService lifecycle;
            @Autowired
            private SalesOrderQueryService queries;
            @Autowired
            private OrderTagService tags;

            @Test
            void statusFilterAndDetailFollowLifecycleWhileLockDoesNotChangeStatus() {
                Long customer = newCustomer();
                Long first = confirmedSalesOrder(customer, newOnShelfSku("STATUS-A"), "3.0000", "3.0000");
                Long second = confirmedSalesOrder(customer, newOnShelfSku("STATUS-B"), "2.0000", "2.0000");
                assertThat(queries.detail(first).getStatus()).isEqualTo("CONFIRMED");
                lifecycle.markDispatched(first, OffsetDateTime.now(), "1:1");
                jdbc.update("UPDATE xsy_sales_order SET locked_flag = TRUE, locked_at = CURRENT_TIMESTAMP, locked_by = '1:1' WHERE id = ?", first);
                evictMybatisCache();
                var form = query(customer, "DISPATCHED");
                assertThat(queries.query(form).getList()).extracting(row -> row.getOrderId()).containsExactly(first);
                assertThat(queries.detail(first).getStatus()).isEqualTo("DISPATCHED");
                assertThat(queries.query(query(customer, "CONFIRMED")).getList()).extracting(row -> row.getOrderId()).containsExactly(second);
                assertThat(lifecycle.markSigned(first, "SIGNED", "1:1")).isTrue();
                assertThat(queries.query(form).getList()).isEmpty();
                assertThat(queries.query(query(customer, "SIGNED")).getList()).extracting(row -> row.getOrderId()).containsExactly(first);
                assertThat(queries.detail(first).getStatus()).isEqualTo("SIGNED");
                lifecycle.markDispatched(second, OffsetDateTime.now(), "1:1");
                assertThat(lifecycle.markSigned(second, "EXCEPTION", "1:1")).isTrue();
                assertThat(queries.detail(second).getStatus()).isEqualTo("EXCEPTION");
                assertThat(queries.query(query(customer, "EXCEPTION")).getList()).extracting(row -> row.getOrderId()).containsExactly(second);
                assertThat(lifecycle.markDispatched(first, OffsetDateTime.now(), "1:1")).isFalse();
                assertThat(lifecycle.markSigned(first, "EXCEPTION", "1:1")).isFalse();
            }

            @Test
            void pendingAndCancelledStatusesStayExclusive() {
                Long customer = newCustomer();
                Long pending = confirmedSalesOrder(customer, newOnShelfSku("PENDING-STATUS"), "1.0000", "1.0000");
                Long cancelled = confirmedSalesOrder(customer, newOnShelfSku("CANCEL-STATUS"), "1.0000", "1.0000");
                jdbc.update("UPDATE xsy_sales_order SET status = 'PENDING' WHERE id = ?", pending);
                jdbc.update("UPDATE xsy_sales_order SET status = 'CANCELLED', cancel_reason = '测试取消', cancelled_at = CURRENT_TIMESTAMP WHERE id = ?", cancelled);
                evictMybatisCache();
                assertThat(queries.query(query(customer, "PENDING")).getList()).extracting(row -> row.getOrderId()).containsExactly(pending);
                assertThat(queries.query(query(customer, "CANCELLED")).getList()).extracting(row -> row.getOrderId()).containsExactly(cancelled);
            }

            @Test
            void assigningReplacesSingleTagAndClearingRetainsOldLinksWithoutChangingOrderVersionOrAmounts() {
                Long orderId = confirmedSalesOrder(newCustomer(), newOnShelfSku("TAG"), "3.0000", "3.0000");
                var before = queries.detail(orderId);
                Long first = tag("第一个");
                Long second = tag("第二个");
                Long third = tag("第三个");
                apply(orderId, first, OrderTagAssignForm.Action.SET);
                apply(orderId, second, OrderTagAssignForm.Action.ADD);
                apply(orderId, second, OrderTagAssignForm.Action.SET);
                var current = queries.detail(orderId);
                assertThat(current.getTag().getTagId()).isEqualTo(second);
                assertThat(current.getTags()).hasSize(1);
                assertThat(current.getVersion()).isEqualTo(before.getVersion());
                assertThat(current.getSettlementTotalAmount()).isEqualByComparingTo(before.getSettlementTotalAmount());
                expectSqlFailure("INSERT INTO xsy_order_tag_link(order_id, tag_id, created_by) VALUES (?, ?, 'it')", orderId, third);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_order_tag_link WHERE NOT active AND order_id = ?", Integer.class, orderId)).isEqualTo(1);
                apply(orderId, null, OrderTagAssignForm.Action.CLEAR);
                assertThat(queries.detail(orderId).getTag()).isNull();
                assertThat(queries.detail(orderId).getTags()).isEmpty();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM xsy_order_tag_link WHERE NOT active AND order_id = ?", Integer.class, orderId)).isEqualTo(2);
            }

            private SalesOrderQueryForm query(Long customer, String status) {
                var form = new SalesOrderQueryForm();
                form.setCustomerId(customer);
                form.setStatus(status);
                form.setPageNum(1L);
                form.setPageSize(20L);
                return form;
            }
            private Long tag(String name) {
                var form = new OrderTagCreateForm();
                form.setName("单标签-" + java.util.UUID.randomUUID().toString().substring(0, 8) + name);
                form.setColor("BLUE");
                return tags.create(form);
            }
            private void apply(Long orderId, Long tagId, OrderTagAssignForm.Action action) {
                var form = new OrderTagAssignForm();
                form.setOrderIds(List.of(orderId));
                form.setTagId(tagId);
                form.setAction(action);
                tags.apply(form);
            }
        }
    }

    @Nested
    @WebMvcTest({SalesOrderController.class, SalesOrderImportController.class, OrderReturnController.class, OrderRefundController.class})
    @AutoConfigureMockMvc(addFilters = false)
    @ContextConfiguration(classes = {SalesOrderController.class, SalesOrderImportController.class, OrderReturnController.class, OrderRefundController.class, ScmExceptionHandler.class, GlobalExceptionHandler.class})
    class OrderWebTest {
        @Autowired
        MockMvc mvc;
        @MockitoBean
        SalesOrderService service;
        @MockitoBean
        SalesOrderQueryService queries;
        @MockitoBean
        OrderReturnService returns;
        @MockitoBean
        OrderReturnReceiptService returnReceipts;
        @MockitoBean
        OrderRefundService refunds;
        @MockitoBean
        PriceResolver prices;
        @MockitoBean
        CustomerService customers;
        @MockitoBean
        OrderCreditService credits;
        @MockitoBean
        SalesOrderDetailReadService detailReads;
        @MockitoBean
        com.xsy.scm.order.service.OrderLockService orderLockService;
        @MockitoBean
        com.xsy.scm.common.scope.ScmWarehouseScopeGuard warehouseScopeGuard;
        @MockitoBean
        SalesOrderImportService imports;
        @MockitoBean
        SalesOrderPasteService paste;
        @MockitoBean
        com.xsy.scm.base.module.support.securityprotect.service.SecurityFileService securityFiles;
        @MockitoBean(name = "systemEnvironment")
        SystemEnvironment environment;

        @Test
        void allOrderEndpointsHaveNativePermissions() {
            int count = 0;
            for (var type : java.util.List.of(SalesOrderController.class, SalesOrderImportController.class, OrderReturnController.class, OrderRefundController.class))
                for (var m : type.getDeclaredMethods())
                    if (java.lang.reflect.Modifier.isPublic(m.getModifiers())) {
                        assertThat(m.getAnnotation(SaCheckPermission.class)).as(m.getName()).isNotNull();
                        count++;
                    }
            assertThat(count).isEqualTo(36);
        }

        @Test
        void importTemplateIsPackagedAndMatchesParserContract() throws Exception {
            var response = mvc.perform(get("/scm/order/import/template")).andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
                    .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(
                            "%E5%AE%A2%E6%88%B7%E8%AE%A2%E5%8D%95%E5%AF%BC%E5%85%A5%E6%A8%A1%E6%9D%BF.xlsx")))
                    // RFC 6266：中文名以 filename*=UTF-8'' 为准，filename= 仅作 ASCII 回退。
                    .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename*=UTF-8''")))
                    .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")))
                    .andReturn().getResponse();
            assertThat(response.getContentAsByteArray()).isNotEmpty();
            assertThat(response.getHeader("Content-Length")).isEqualTo(String.valueOf(response.getContentAsByteArray().length));
            try (var workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.ByteArrayInputStream(response.getContentAsByteArray()))) {
                var sheet = workbook.getSheetAt(0);
                assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("客户订单");
                assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("收货单位：");
                assertThat(sheet.getRow(2).getCell(6).getStringCellValue()).isEqualTo("日期：");
                var headerRow = sheet.getRow(3);
                assertThat(java.util.stream.IntStream.range(0, 8).mapToObj(i -> headerRow.getCell(i).getStringCellValue()).toList()).containsExactly(
                        "序号", "商品名称", "单位", "规格", "实数量", "销售单价", "销售金额", "备注");
                assertThat(sheet.getRow(66).getCell(0).getStringCellValue()).isEqualTo("合计金额");
                assertThat(sheet.getRow(66).getCell(6).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.FORMULA);
                assertThat(sheet.getRow(67).getCell(1).getStringCellValue()).isEqualTo("销售方：");
            }
        }

        @Test
        void detailUsesFourDecimalStringsAndKeepsNull() throws Exception {
            var o = new SalesOrderDetailVO();
            o.setOrderId(1L);
            o.setOrderedTotalAmount(null);
            o.setSettlementTotalAmount(new java.math.BigDecimal("0"));
            when(detailReads.detail(1L)).thenReturn(o);
            mvc.perform(get("/scm/order/detail/1")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.orderedTotalAmount").doesNotExist()).andExpect(jsonPath("$.data.settlementTotalAmount").value("0.0000"));
        }

        @Test
        void recentPricesEndpointIsReadOnlyAndSerializesFixedScale() throws Exception {
            var p = new OrderRecentPriceVO();
            p.setItemId(100L);
            p.setOrderId(7L);
            p.setOrderNo("SO-1");
            p.setCreatedAt(java.time.OffsetDateTime.parse("2026-09-20T00:00:00Z"));
            p.setConfirmedAt(java.time.OffsetDateTime.parse("2026-09-20T01:00:00Z"));
            p.setOrderSource("ADMIN");
            p.setOrderedQuantity(new java.math.BigDecimal("3"));
            p.setUnitPrice(new java.math.BigDecimal("8.5"));
            p.setPriceSource("MARKET");
            p.setSaleUnit("kg");
            when(queries.recentPrices(1L, 2L, 5)).thenReturn(java.util.List.of(p));
            mvc.perform(get("/scm/order/reference/recent-prices").param("customerId", "1").param("skuId", "2").param("limit", "5"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data[0].itemId").value(100))
                    .andExpect(jsonPath("$.data[0].saleUnit").value("kg"))
                    // confirmedAt 是排序键字段；其序列化随 ObjectMapper 时区配置变化，故只断言序列化为字符串，不比字面偏移量
                    .andExpect(jsonPath("$.data[0].confirmedAt").value(org.hamcrest.Matchers.instanceOf(String.class)))
                    .andExpect(jsonPath("$.data[0].unitPrice").value("8.5000"))
                    .andExpect(jsonPath("$.data[0].orderedQuantity").value("3.0000"))
                    .andExpect(jsonPath("$.data[0].priceSource").value("MARKET"));
            // 只读旁证：不得触碰写命令服务，也不得触发任何定价解析
            verifyNoInteractions(service, prices);
        }

        @Test
        void pricePreviewRejectsCustomerOutsideCurrentScope() throws Exception {
            when(customers.requireInCurrentScope(1L)).thenThrow(new ScmDataScopeException());
            mvc.perform(post("/scm/order/price/preview").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"customerId\":1,\"skuIds\":[2]}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30005));
            verifyNoInteractions(prices);
        }

        @Test
        void recentPricesReturnsNoPermissionEnvelopeForOutOfScopeCustomer() throws Exception {
            when(queries.recentPrices(1L, 2L, 5)).thenThrow(new ScmDataScopeException());
            mvc.perform(get("/scm/order/reference/recent-prices").param("customerId", "1")
                            .param("skuId", "2").param("limit", "5"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(30005));
        }

        @Test
        void missingIdempotencyReturnsStableBusinessCode() throws Exception {
            when(service.create(any(), isNull()))
                    .thenThrow(new ScmBusinessException(OrderErrorCode.ORDER_IDEMPOTENCY_KEY_REQUIRED));
            mvc.perform(post("/scm/order/create").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"customerId\":1,\"orderSource\":\"ADMIN\",\"address\":{\"receiverName\":\"张三\","
                            + "\"receiverPhone\":\"13800000000\",\"address\":\"地址\"},"
                            + "\"items\":[{\"skuId\":2,\"orderedQuantity\":\"1.0000\",\"manualPriceOverride\":false}]}"))
                    .andExpect(jsonPath("$.code").value(40069));
        }

        @Test
        void jsonNumbersCannotEnterFixedPointQuantity() throws Exception {
            mvc.perform(post("/scm/order/item/actual-quantity").contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":1,\"itemId\":2,\"version\":0,\"actualQuantity\":1.2,\"reason\":\"称重\"}")).andExpect(jsonPath("$.code").value(30001));
            verifyNoInteractions(service);
        }

        @Test
        void afterSalesRejectsMissingVersionAndEmptyItems() throws Exception {
            mvc.perform(post("/scm/order/return/approve").contentType(MediaType.APPLICATION_JSON).content("{\"returnId\":1,\"version\":0,\"items\":[]}")).andExpect(jsonPath("$.code").value(30001));
            mvc.perform(post("/scm/order/refund/complete").contentType(MediaType.APPLICATION_JSON).content("{\"refundId\":1}")).andExpect(jsonPath("$.code").value(30001));
            verifyNoInteractions(returns, refunds);
        }
    }

    @Nested
    @SpringBootTest(classes = XsyApplication.class, properties = {
            "project.log-directory=" + PgITPaths.DEFAULT_LOG_DIR,
            "file.storage.local.upload-path=" + PgITPaths.DEFAULT_UPLOAD_PATH,
            "file.storage.local.url-prefix=http://127.0.0.1:18082",
            "logging.level.root=WARN"})
    @Transactional
    class ScmBundleMigrationPgIT extends ScmBaselineMigrationPgITBase {
        @Autowired
        private JdbcTemplate jdbc;

        @Test
        void v140SnapshotContainsFrozenMarketAndAllocationColumns() {
            Integer count = jdbc.queryForObject(
                    "SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema()"
                    + " AND table_name='xsy_order_bundle_component' AND column_name IN "
                    + "('component_sale_quantity_snapshot','market_unit_price_snapshot',"
                    + "'market_reference_amount_snapshot','allocated_sale_amount_snapshot')"
                    + " AND is_nullable='NO'", Integer.class);
            assertThat(count).isEqualTo(4);
            Integer fks = jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE contype='f'"
                    + " AND connamespace=current_schema()::regnamespace", Integer.class);
            assertThat(fks).isZero();
        }

        @Test
        void snapshotCannotBeChangedAfterFreeze() {
            Long id = insertSnapshot(11L, 101L, "32.0000", "20.00000000");
            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE xsy_order_bundle_component SET allocated_sale_amount_snapshot=0 WHERE id=?", id))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("order bundle snapshot is immutable");
        }

        @Test
        void sameOrderItemCannotFreezeSameComponentTwice() {
            insertSnapshot(12L, 101L, "32.0000", "20.00000000");
            assertThatThrownBy(() -> insertSnapshot(12L, 101L, "32.0000", "20.00000000"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void databaseRejectsNegativeAllocation() {
            assertThatThrownBy(() -> insertSnapshot(13L, 101L, "-0.0001", "20.00000000"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void databaseRejectsZeroMarketReferenceEvenIfAllocationIsPositive() {
            assertThatThrownBy(() -> insertSnapshot(14L, 101L, "10.0000", "0.00000000"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        private Long insertSnapshot(Long orderItemId, Long componentSkuId,
                String allocatedAmount, String marketAmount) {
            return jdbc.queryForObject(
                    "INSERT INTO xsy_order_bundle_component "
                    + "(order_id,order_item_id,warehouse_id,bundle_sku_id,component_sku_id,"
                    + "component_sku_code_snapshot,component_product_name_snapshot,"
                    + "component_sale_unit_snapshot,component_stock_unit_snapshot,"
                    + "quantity_per_bundle_sale_unit,stock_units_per_component_sale_unit,"
                    + "component_sale_quantity_snapshot,market_unit_price_snapshot,"
                    + "market_reference_amount_snapshot,allocated_sale_amount_snapshot,"
                    + "product_bundle_component_id,product_bundle_component_version) "
                    + "VALUES (1,?,42,200,?,'COMP','组成商品','kg','kg',"
                    + "2.0000,1.0000,2.0000,10.0000,?::numeric,?::numeric,7,1) RETURNING id",
                    Long.class, orderItemId, componentSkuId, marketAmount, allocatedAmount);
        }
    }

    @Nested
    class ScmOrderMigrationIT extends ScmBaselineMigrationPgITBase {
        @Autowired
        Flyway flyway;
        static final String TABLES = "'xsy_sales_order','xsy_sales_order_item','xsy_order_address_snapshot','xsy_order_operation_log','xsy_idempotency_record','xsy_order_return','xsy_order_return_item','xsy_order_refund'";

        @Test
        void approvedSchemaHasEightTablesNoDeadFieldsAndNullableDraftPrices() {
            flyway.validate();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema=current_schema() AND table_name IN (" + TABLES + ")", Integer.class)).isEqualTo(8);
            // 25 条是订单域批准形态；V51 为报表日期轴追加 2 条部分索引（sales_order.confirmed_at、
            // order_refund.completed_at），二者都在本用例的表清单内；
            // V87（ADM-08 异常订单分析）再追加 order_return.rejected_at 的售后拒绝事件轴索引；
            // V126 为待分配订单池在 sales_order 上追加部分索引 idx_sales_order_unassigned_mall_pool；
            // 商城配送仓映射追加 idx_sales_order_mall_delivery_warehouse；
            // V184 为发车满 7 天自动锁单追加部分索引 idx_sales_order_auto_lock；
            // V202/V204 为独立退货追加 idx_order_return_customer_created、uk_order_return_item_sku_active，
            // 以及订单日志按客户检索的 idx_order_operation_log_customer_created；
            // V220 又为换货关联追加 uk_order_return_replacement = 35。
            assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname=current_schema() AND tablename IN (" + TABLES + ") AND indexname NOT LIKE '%_pkey'", Integer.class)).isEqualTo(35);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.sequences WHERE sequence_schema=current_schema() AND sequence_name IN ('sales_order_no_seq','order_return_no_seq','order_refund_no_seq')", Integer.class)).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name IN (" + TABLES + ") AND column_name IN ('fulfillment_status','pay_status','actual_weight')", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name IN ('xsy_sales_order','xsy_sales_order_item') AND column_name IN ('draft_unit_price','draft_price_source','ordered_line_amount','ordered_total_amount') AND is_nullable='YES'", Integer.class)).isEqualTo(4);
            // 精度规则按 (表,列) 排除坐标列：按列名全局排除会让同名列在任何订单表上逃过金额精度校验
            assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name IN (" + TABLES + ") AND data_type='numeric' AND (table_name,column_name) NOT IN (('xsy_order_address_snapshot','longitude'),('xsy_order_address_snapshot','latitude')) AND (numeric_precision<>18 OR numeric_scale<>4)", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='xsy_order_address_snapshot' AND data_type='numeric' AND (column_name,numeric_precision,numeric_scale) IN (('longitude',11,8),('latitude',10,8))", Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE connamespace=current_schema()::regnamespace AND contype='f' AND conrelid IN (SELECT oid FROM pg_class WHERE relname IN (" + TABLES + "))", Integer.class)).isZero();
        }

        @Test
        void permissionMenusGrantedAndBusinessLogsHaveNoMutationMapper() {
            assertThat(jdbc.queryForList("SELECT menu_id FROM t_menu WHERE menu_id IN (601,602,603,604,605,611,612,613,614,615,616,617,618,619,621,622,623,624,625,631,632,641,642) ORDER BY menu_id", Long.class))
                .containsExactly(601L,602L,603L,604L,605L,611L,612L,613L,615L,616L,617L,618L,619L,621L,622L,623L,624L,625L,631L,632L,641L,642L);
            // 模块基线不再初始化已退役的614「提交」菜单及授权。
            assertThat(jdbc.queryForObject("SELECT count(*) FROM t_role_menu WHERE role_id=1 AND menu_id IN (601,602,603,604,605,611,612,613,614,615,616,617,618,619,621,622,623,624,625,631,632,641,642)", Integer.class)).isEqualTo(22);
            assertThat(jdbc.queryForObject("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname='ck_sales_order_source'", String.class)).contains("IMPORT");
            // 订单日志 DAO 只允许「追加 + 读」：listByOrderId 是商城订单时间线的只读入口，
            // 一旦出现 update / delete 方法，日志就不再是不可改的记录，这里会立刻变红。
            assertThat(com.xsy.scm.order.dao.OrderOperationLogDao.class.getMethods()).extracting(java.lang.reflect.Method::getName).containsExactlyInAnyOrder("insert", "query", "listByOrderId");
        }

        /**
         * V126 待分配订单池：菜单、权限发布、角色授权与候选查询的部分索引。
         *
         * <p>这一版只新增对象、不回填历史订单，所以断言的是「新对象存在且规则正确」。
         * 权限码必须与 {@code OrderPermission.ASSIGN} 一致：它硬编码在菜单迁移与 Java 常量两处，
         * 漏改任何一处都会让页面上的指派按钮点了 403。
         */
        @Test
        void unassignedOrderPoolMenuPermissionAndIndexArePublished() {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM t_menu WHERE menu_id=1261 AND menu_name='待分配订单池' AND menu_type=2 AND parent_id=601 AND path='/order/unassigned-pool' AND visible_flag=TRUE", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT api_perms FROM t_menu WHERE menu_id=1262", String.class)).isEqualTo(com.xsy.scm.order.permission.OrderPermission.ASSIGN);
            assertThat(jdbc.queryForObject("SELECT web_perms FROM t_menu WHERE menu_id=1262", String.class))
                    .as("api_perms 与 web_perms 必须一致，否则按钮显隐与后端拦截会分叉")
                    .isEqualTo(com.xsy.scm.order.permission.OrderPermission.ASSIGN);
            assertThat(jdbc.queryForObject("SELECT context_menu_id FROM t_menu WHERE menu_id=1262", Long.class)).isEqualTo(1261L);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM t_role_menu WHERE role_id=1 AND menu_id IN (1261,1262)", Integer.class)).as("超管必须看得到订单池").isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM t_role_menu rm JOIN t_role r ON r.role_id=rm.role_id WHERE r.role_code='SCM_SALES_LEAD' AND rm.menu_id IN (1261,1262)", Integer.class)).as("销售主管按 role_code 授权，两个菜单都要有").isEqualTo(2);

            // 候选查询是「MALL + 未分配 + 非终态」，索引必须把这个谓词下推成部分索引，
            // 否则待分配列表会退化成对整张订单表的顺序扫描。
            String definition = jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE schemaname=current_schema() AND indexname='idx_sales_order_unassigned_mall_pool'", String.class);
            assertThat(definition).contains("order_source").contains("'MALL'").contains("seller_id IS NULL").contains("deleted = false").contains("DRAFT").contains("PENDING").contains("CONFIRMED");
        }

        /**
         * 操作日志的 operation_type：Java 枚举与库里的 CHECK 白名单必须逐项相等。
         *
         * <p>同一份取值硬编码在五处（业务枚举、DB CHECK、前端常量、查询表单 @Pattern、
         * 日志 operation_type 白名单），漏掉任何一处都要等第一次写日志才暴露。
         * 这里把最容易漏的两处钉成可执行的：加枚举不写迁移、或迁移加了取值而枚举没有，都会在这里变红。
         */
        @Test
        void operationLogTypeWhitelistMatchesJavaEnum() {
            String def = jdbc.queryForObject(
                    "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname='ck_order_operation_log_type'",
                    String.class);
            var allowed = new java.util.HashSet<String>();
            var matcher = java.util.regex.Pattern.compile("'([A-Z_]+)'").matcher(def);
            while (matcher.find()) allowed.add(matcher.group(1));
            assertThat(allowed).containsExactlyInAnyOrderElementsOf(
                    java.util.Arrays.stream(com.xsy.scm.order.constant.ScmOrderOperationTypeEnum.values())
                            .map(Enum::name).toList());
        }
    }

    @Nested
    class OrderUnitTest2 {
        @Nested
        @DisplayName("未分配订单池：领取与指派的服务层规则")
        class OrderPoolServiceTest {
            private static final long SUPERVISOR = 1L;
            private static final long ORDER_ID = 10L;
            private static final long TARGET_SELLER = 7L;
            /** ScmOperator 渲染成「用户类型值:用户 id」，员工类型值是 1。 */
            private static final String OPERATOR = "1:1";
            private static final String IDEMPOTENCY_KEY = "pool-key-1";

            /**
             * 候选业务员查询在服务里直接拼 LambdaQueryWrapper，列名解析依赖 MyBatis-Plus 的实体元数据缓存；
             * 纯单测没有 Spring 上下文去注册它，这里显式初始化一次。
             */
            @BeforeAll
            static void initMybatisPlusMetadata() {
                TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EmployeeEntity.class);
            }

            private final SalesOrderDao salesOrderDao = mock(SalesOrderDao.class);
            private final SalesOrderQueryService queryService = mock(SalesOrderQueryService.class);
            private final EmployeeDao employeeDao = mock(EmployeeDao.class);
            private final LoginManager loginManager = mock(LoginManager.class);
            private final OrderIdempotencyService idempotency = mock(OrderIdempotencyService.class);
            private final OrderOperationLogRecorder operationLogs = mock(OrderOperationLogRecorder.class);
            private final ScmDataScopeService dataScopeService = mock(ScmDataScopeService.class);

            private final OrderPoolService service = new OrderPoolService(salesOrderDao, queryService, employeeDao,
                    loginManager, idempotency, operationLogs, dataScopeService);

            private final ScmIdempotencyRequestHasher hasher = new ScmIdempotencyRequestHasher(new ObjectMapper());

            @BeforeEach
            void setUp() {
                signIn(SUPERVISOR, true);
                var record = new ScmIdempotencyRecordEntity();
                record.setResultData(new LinkedHashMap<>());
                when(idempotency.claim(anyString(), anyString(), any()))
                        .thenReturn(new OrderIdempotencyService.Claim(record, false));
            }

            @AfterEach
            void tearDown() {
                XsyRequestUtil.remove();
            }

            // ---------------------------------------------------------------- 权限闸门

            @Test
            @DisplayName("非超管且没有指派权限时拒绝访问，且在读库之前就失败")
            void nonSupervisorWithoutAssignPermissionIsRejectedBeforeAnyRead() {
                signIn(SUPERVISOR, false);
                try (MockedStatic<StpUtil> permissions = mockStatic(StpUtil.class)) {
                    assertThatThrownBy(() -> service.unassigned(queryForm()))
                            .isInstanceOf(ScmDataScopeException.class);
                    assertThatThrownBy(() -> service.assignees()).isInstanceOf(ScmDataScopeException.class);
                    assertThatThrownBy(() -> service.assign(assignForm(ORDER_ID, 3, TARGET_SELLER), IDEMPOTENCY_KEY))
                            .isInstanceOf(ScmDataScopeException.class);
                }
                verifyNoInteractions(salesOrderDao, employeeDao, idempotency, operationLogs);
            }

            @Test
            @DisplayName("持有指派权限的业务员可以读池，超管不必再持有该权限")
            void assignPermissionOrSupervisorGrantsAccess() {
                signIn(SUPERVISOR, false);
                var form = queryForm();
                SalesOrderEntity row = mallOrder(ORDER_ID, 3, null);
                SalesOrderVO rowView = new SalesOrderVO();
                rowView.setOrderId(ORDER_ID);
                when(salesOrderDao.unassignedMallOrders(any(), eq(form))).thenReturn(List.of(row));
                when(queryService.order(row)).thenReturn(rowView);

                try (MockedStatic<StpUtil> permissions = mockStatic(StpUtil.class)) {
                    permissions.when(() -> StpUtil.hasPermission(OrderPermission.ASSIGN)).thenReturn(true);
                    assertThat(service.unassigned(form).getList()).extracting(SalesOrderVO::getOrderId)
                            .containsExactly(ORDER_ID);
                }

                signIn(SUPERVISOR, true);
                when(salesOrderDao.unassignedMallOrders(any(), eq(form))).thenReturn(List.of(row));
                assertThat(service.unassigned(form).getList()).extracting(SalesOrderVO::getOrderId)
                        .containsExactly(ORDER_ID);
            }

            @Test
            @DisplayName("解析不到登录员工时校验失败时拒绝操作")
            void missingEmployeeIdentityFailsClosed() {
                var denied = mock(ScmDataScopeContext.class);
                when(denied.getEmployeeId()).thenReturn(null);
                when(dataScopeService.resolve()).thenReturn(denied);
                assertThatThrownBy(() -> service.assignees()).isInstanceOf(ScmDataScopeException.class);
            }

            // ---------------------------------------------------------------- 领取

            @Test
            @DisplayName("领取把订单指派给当前登录的业务员本人")
            void claimAssignsToTheSignedInOperator() {
                SalesOrderEntity order = mallOrder(ORDER_ID, 3, null);
                when(salesOrderDao.lock(ORDER_ID)).thenReturn(order);
                when(employeeDao.selectById(SUPERVISOR)).thenReturn(employee(SUPERVISOR, "主管", false, false, false));
                when(loginManager.getUserPermission(SUPERVISOR)).thenReturn(permission(OrderPermission.QUERY));
                when(salesOrderDao.assignUnassignedMallOrder(ORDER_ID, 3, SUPERVISOR, OPERATOR)).thenReturn(1);
                stubSnapshot(order);

                var form = new OrderPoolClaimForm();
                form.setOrderId(ORDER_ID);
                form.setVersion(3);
                service.claim(form, IDEMPOTENCY_KEY);

                verify(salesOrderDao).assignUnassignedMallOrder(ORDER_ID, 3, SUPERVISOR, OPERATOR);
            }

            // ---------------------------------------------------------------- 准入条件

            @Test
            @DisplayName("订单不存在、非商城单或已进入终态都按「订单不存在」拒绝")
            void onlyUnassignedLiveMallOrdersAreEligible() {
                when(salesOrderDao.lock(ORDER_ID)).thenReturn(null);
                assertRejected(assignForm(ORDER_ID, 3, TARGET_SELLER), OrderErrorCode.ORDER_NOT_FOUND);

                when(salesOrderDao.lock(ORDER_ID))
                        .thenReturn(orderOf(ScmOrderSourceEnum.ADMIN, ScmOrderStatusEnum.PENDING, null));
                assertRejected(assignForm(ORDER_ID, 3, TARGET_SELLER), OrderErrorCode.ORDER_NOT_FOUND);

                when(salesOrderDao.lock(ORDER_ID))
                        .thenReturn(orderOf(ScmOrderSourceEnum.MALL, ScmOrderStatusEnum.CANCELLED, null));
                assertRejected(assignForm(ORDER_ID, 3, TARGET_SELLER), OrderErrorCode.ORDER_NOT_FOUND);

                verifyNoInteractions(operationLogs);
            }

            @Test
            @DisplayName("待确认与已确认的商城单都可以被指派")
            void pendingAndConfirmedMallOrdersAreAllEligible() {
                for (ScmOrderStatusEnum status : List.of(ScmOrderStatusEnum.PENDING, ScmOrderStatusEnum.CONFIRMED)) {
                    SalesOrderEntity order = orderOf(ScmOrderSourceEnum.MALL, status, null);
                    when(salesOrderDao.lock(ORDER_ID)).thenReturn(order);
                    stubAssignableTarget();
                    when(salesOrderDao.assignUnassignedMallOrder(ORDER_ID, 3, TARGET_SELLER, OPERATOR)).thenReturn(1);
                    stubSnapshot(order);

                    assertThat(service.assign(assignForm(ORDER_ID, 3, TARGET_SELLER), IDEMPOTENCY_KEY)).isNotNull();
                }
            }

            @Test
            @DisplayName("已有负责人时拒绝重复指派")
            void alreadyOwnedOrderIsRejected() {
                when(salesOrderDao.lock(ORDER_ID))
                        .thenReturn(orderOf(ScmOrderSourceEnum.MALL, ScmOrderStatusEnum.PENDING, 9L));
                assertRejected(assignForm(ORDER_ID, 3, TARGET_SELLER), OrderErrorCode.ORDER_ALREADY_ASSIGNED);
                verify(salesOrderDao, never()).assignUnassignedMallOrder(anyLong(), any(), anyLong(), anyString());
            }

            @Test
            @DisplayName("请求版本落后于当前版本时拒绝，不触碰数据库写入")
            void staleVersionIsRejectedBeforeUpdate() {
                when(salesOrderDao.lock(ORDER_ID)).thenReturn(mallOrder(ORDER_ID, 5, null));
                assertRejected(assignForm(ORDER_ID, 3, TARGET_SELLER), VERSION_CONFLICT);
                verify(salesOrderDao, never()).assignUnassignedMallOrder(anyLong(), any(), anyLong(), anyString());
            }

            @Test
            @DisplayName("条件更新影响行数为零说明已被并发抢占，按版本冲突处理")
            void lostConditionalUpdateIsReportedAsVersionConflict() {
                SalesOrderEntity order = mallOrder(ORDER_ID, 3, null);
                when(salesOrderDao.lock(ORDER_ID)).thenReturn(order);
                stubAssignableTarget();
                when(salesOrderDao.assignUnassignedMallOrder(ORDER_ID, 3, TARGET_SELLER, OPERATOR)).thenReturn(0);

                assertRejected(assignForm(ORDER_ID, 3, TARGET_SELLER), VERSION_CONFLICT);
                verifyNoInteractions(operationLogs);
                verify(idempotency, never()).complete(any(), anyString(), anyLong(), any());
            }

            @Test
            @DisplayName("目标业务员不存在、已停用、已删除或没有订单查看权都拒绝")
            void invalidTargetSellerIsRejected() {
                when(salesOrderDao.lock(ORDER_ID)).thenReturn(mallOrder(ORDER_ID, 3, null));

                when(employeeDao.selectById(TARGET_SELLER)).thenReturn(null);
                assertRejected(assignForm(ORDER_ID, 3, TARGET_SELLER), OrderErrorCode.ORDER_ASSIGN_TARGET_INVALID);

                when(employeeDao.selectById(TARGET_SELLER)).thenReturn(employee(TARGET_SELLER, "张三", false, true, false));
                assertRejected(assignForm(ORDER_ID, 3, TARGET_SELLER), OrderErrorCode.ORDER_ASSIGN_TARGET_INVALID);

                when(employeeDao.selectById(TARGET_SELLER)).thenReturn(employee(TARGET_SELLER, "张三", false, false, true));
                assertRejected(assignForm(ORDER_ID, 3, TARGET_SELLER), OrderErrorCode.ORDER_ASSIGN_TARGET_INVALID);

                when(employeeDao.selectById(TARGET_SELLER)).thenReturn(employee(TARGET_SELLER, "张三", false, false, false));
                when(loginManager.getUserPermission(TARGET_SELLER)).thenReturn(null);
                assertRejected(assignForm(ORDER_ID, 3, TARGET_SELLER), OrderErrorCode.ORDER_ASSIGN_TARGET_INVALID);

                when(loginManager.getUserPermission(TARGET_SELLER)).thenReturn(permission());
                assertRejected(assignForm(ORDER_ID, 3, TARGET_SELLER), OrderErrorCode.ORDER_ASSIGN_TARGET_INVALID);

                verify(salesOrderDao, never()).assignUnassignedMallOrder(anyLong(), any(), anyLong(), anyString());
                verifyNoInteractions(operationLogs);
            }

            @Test
            @DisplayName("超管身份的业务员无需订单查看权即可作为指派目标")
            void administratorTargetNeedsNoExtraPermission() {
                SalesOrderEntity order = mallOrder(ORDER_ID, 3, null);
                when(salesOrderDao.lock(ORDER_ID)).thenReturn(order);
                when(employeeDao.selectById(TARGET_SELLER)).thenReturn(employee(TARGET_SELLER, "张三", true, false, false));
                when(salesOrderDao.assignUnassignedMallOrder(ORDER_ID, 3, TARGET_SELLER, OPERATOR)).thenReturn(1);
                stubSnapshot(order);

                service.assign(assignForm(ORDER_ID, 3, TARGET_SELLER), IDEMPOTENCY_KEY);

                verify(loginManager, never()).getUserPermission(anyLong());
            }

            // ---------------------------------------------------------------- 成功路径

            @Test
            @DisplayName("指派成功后记录指派前后保存的数据并回填防重复处理结果")
            void successfulAssignmentRecordsSnapshotAndCompletesIdempotency() {
                SalesOrderEntity order = mallOrder(ORDER_ID, 3, null);
                SalesOrderEntity reloaded = mallOrder(ORDER_ID, 4, TARGET_SELLER);
                SalesOrderVO before = new SalesOrderVO();
                before.setOrderId(ORDER_ID);
                SalesOrderVO after = new SalesOrderVO();
                after.setOrderId(ORDER_ID);
                when(salesOrderDao.lock(ORDER_ID)).thenReturn(order);
                stubAssignableTarget();
                when(salesOrderDao.assignUnassignedMallOrder(ORDER_ID, 3, TARGET_SELLER, OPERATOR)).thenReturn(1);
                when(salesOrderDao.selectById(ORDER_ID)).thenReturn(reloaded);
                when(queryService.order(order)).thenReturn(before);
                when(queryService.order(reloaded)).thenReturn(after);

                assertThat(service.assign(assignForm(ORDER_ID, 3, TARGET_SELLER), IDEMPOTENCY_KEY)).isSameAs(after);

                verify(operationLogs).record(ORDER_ID, ScmOrderOperationTypeEnum.ASSIGN, "指派业务员：张三", before, after);
                verify(idempotency).complete(any(), eq(ScmFinanceReceivableSourceTypeEnum.SALES_ORDER.name()),
                        eq(ORDER_ID), eq(after));
            }

            // ---------------------------------------------------------------- 防重复处理

            @Test
            @DisplayName("重放命中防重复处理记录时直接返回原结果，不再锁定订单")
            void replayedAssignmentReturnsStoredResultWithoutLockingTheOrder() {
                SalesOrderVO stored = new SalesOrderVO();
                stored.setOrderId(ORDER_ID);
                var record = new ScmIdempotencyRecordEntity();
                record.setResultData(new LinkedHashMap<>(Map.of("value", Map.of())));
                var claim = new OrderIdempotencyService.Claim(record, true);
                when(idempotency.claim(anyString(), anyString(), any())).thenReturn(claim);
                when(idempotency.replay(claim, SalesOrderVO.class)).thenReturn(stored);

                assertThat(service.assign(assignForm(ORDER_ID, 3, TARGET_SELLER), IDEMPOTENCY_KEY)).isSameAs(stored);

                verifyNoInteractions(salesOrderDao, operationLogs);
                verify(idempotency, never()).complete(any(), anyString(), anyLong(), any());
            }

            @Test
            @DisplayName("防重复处理指纹只认订单与目标业务员，版本前进后重试仍然命中")
            void fingerprintIgnoresVersionSoRetriesStillReplay() {
                when(salesOrderDao.lock(ORDER_ID)).thenReturn(null);
                var captor = ArgumentCaptor.forClass(Object.class);

                assertRejected(assignForm(ORDER_ID, 3, TARGET_SELLER), OrderErrorCode.ORDER_NOT_FOUND);
                assertRejected(assignForm(ORDER_ID, 4, TARGET_SELLER), OrderErrorCode.ORDER_NOT_FOUND);
                verify(idempotency, times(2)).claim(anyString(), anyString(), captor.capture());

                List<Object> fingerprints = captor.getAllValues();
                assertThat(fingerprints).allSatisfy(fingerprint -> assertThat(fingerprintOf(fingerprint))
                        .containsOnlyKeys("orderId", "sellerId"));
                assertThat(hasher.hash(fingerprintOf(fingerprints.get(1))))
                        .isEqualTo(hasher.hash(fingerprintOf(fingerprints.get(0))));
            }

            @Test
            @DisplayName("换个订单或换个目标业务员都不能复用同一条防重复处理记录")
            void fingerprintSeparatesOrdersAndTargets() {
                when(salesOrderDao.lock(anyLong())).thenReturn(null);
                var captor = ArgumentCaptor.forClass(Object.class);

                assertRejected(assignForm(ORDER_ID, 3, TARGET_SELLER), OrderErrorCode.ORDER_NOT_FOUND);
                assertRejected(assignForm(ORDER_ID, 3, 8L), OrderErrorCode.ORDER_NOT_FOUND);
                assertRejected(assignForm(11L, 3, TARGET_SELLER), OrderErrorCode.ORDER_NOT_FOUND);
                verify(idempotency, times(3)).claim(anyString(), anyString(), captor.capture());

                List<Object> fingerprints = captor.getAllValues();
                assertThat(hasher.hash(fingerprintOf(fingerprints.get(1))))
                        .isNotEqualTo(hasher.hash(fingerprintOf(fingerprints.get(0))));
                assertThat(hasher.hash(fingerprintOf(fingerprints.get(2))))
                        .isNotEqualTo(hasher.hash(fingerprintOf(fingerprints.get(0))));
            }

            // ---------------------------------------------------------------- 候选业务员

            @Test
            @DisplayName("候选业务员只保留有订单查看权或超管身份的人，按姓名升序、同名按工号升序、无姓名排最后")
            void assigneesKeepOnlyEmployeesAbleToOwnOrders() {
                when(employeeDao.selectList(any())).thenReturn(List.of(
                        employee(7L, "陈一", false, false, false),
                        employee(8L, "陈一", false, false, false),
                        employee(5L, "阿一", true, false, false),
                        employee(6L, "李四", false, false, false),
                        employee(9L, null, true, false, false)));
                when(loginManager.getUserPermission(7L)).thenReturn(permission(OrderPermission.QUERY));
                when(loginManager.getUserPermission(8L)).thenReturn(permission(OrderPermission.QUERY));
                when(loginManager.getUserPermission(6L)).thenReturn(permission(OrderPermission.LOG_QUERY));

                List<OrderPoolAssigneeVO> result = service.assignees();

                assertThat(result).extracting(OrderPoolAssigneeVO::employeeId).containsExactly(5L, 7L, 8L, 9L);
                assertThat(result).extracting(OrderPoolAssigneeVO::actualName).containsExactly("阿一", "陈一", "陈一", null);
            }

            // ---------------------------------------------------------------- 夹具

            private void signIn(long employeeId, boolean administrator) {
                var user = new RequestEmployee();
                user.setEmployeeId(employeeId);
                user.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                user.setAdministratorFlag(administrator);
                XsyRequestUtil.setRequestUser(user);

                var context = mock(ScmDataScopeContext.class);
                when(context.getEmployeeId()).thenReturn(employeeId);
                when(dataScopeService.resolve()).thenReturn(context);
            }

            private void stubAssignableTarget() {
                when(employeeDao.selectById(TARGET_SELLER)).thenReturn(employee(TARGET_SELLER, "张三", false, false, false));
                when(loginManager.getUserPermission(TARGET_SELLER)).thenReturn(permission(OrderPermission.QUERY));
            }

            private void stubSnapshot(SalesOrderEntity order) {
                SalesOrderEntity reloaded = mallOrder(order.getId(), order.getVersion() + 1, TARGET_SELLER);
                SalesOrderVO before = new SalesOrderVO();
                before.setOrderId(order.getId());
                SalesOrderVO after = new SalesOrderVO();
                after.setOrderId(order.getId());
                when(salesOrderDao.selectById(order.getId())).thenReturn(reloaded);
                when(queryService.order(order)).thenReturn(before);
                when(queryService.order(reloaded)).thenReturn(after);
            }

            /** 防重复处理指纹是服务内部构造的映射，断言前统一规整成字符串键的映射。 */
            private static Map<String, Object> fingerprintOf(Object captured) {
                var normalized = new LinkedHashMap<String, Object>();
                ((Map<?, ?>) captured).forEach((key, value) -> normalized.put(String.valueOf(key), value));
                return normalized;
            }

            private static OrderPoolQueryForm queryForm() {
                var form = new OrderPoolQueryForm();
                form.setPageNum(1L);
                form.setPageSize(20L);
                return form;
            }

            private static OrderPoolAssignForm assignForm(long orderId, int version, long sellerId) {
                var form = new OrderPoolAssignForm();
                form.setOrderId(orderId);
                form.setVersion(version);
                form.setSellerId(sellerId);
                return form;
            }

            private static SalesOrderEntity mallOrder(long id, int version, Long sellerId) {
                return orderOf(ScmOrderSourceEnum.MALL, ScmOrderStatusEnum.PENDING, sellerId, id, version);
            }

            private static SalesOrderEntity orderOf(ScmOrderSourceEnum source, ScmOrderStatusEnum status, Long sellerId) {
                return orderOf(source, status, sellerId, ORDER_ID, 3);
            }

            private static SalesOrderEntity orderOf(ScmOrderSourceEnum source, ScmOrderStatusEnum status, Long sellerId,
                    long id, int version) {
                var order = new SalesOrderEntity();
                order.setId(id);
                order.setOrderSource(source.name());
                order.setStatus(status.name());
                order.setSellerId(sellerId);
                order.setVersion(version);
                return order;
            }

            private static EmployeeEntity employee(long id, String actualName, boolean administrator, boolean disabled,
                    boolean deleted) {
                var employee = new EmployeeEntity();
                employee.setEmployeeId(id);
                employee.setActualName(actualName);
                employee.setAdministratorFlag(administrator);
                employee.setDisabledFlag(disabled);
                employee.setDeletedFlag(deleted);
                return employee;
            }

            private static UserPermission permission(String... permissionCodes) {
                var permission = new UserPermission();
                permission.setPermissionList(List.of(permissionCodes));
                return permission;
            }

            private void assertRejected(OrderPoolAssignForm form, ScmErrorCode expected) {
                assertThatThrownBy(() -> service.assign(form, IDEMPOTENCY_KEY))
                        .isInstanceOfSatisfying(ScmBusinessException.class,
                                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
            }
        }

        @Nested

        class OrderReturnMallCancelFingerprintTest {
            private final ScmIdempotencyRequestHasher hasher = new ScmIdempotencyRequestHasher(new ObjectMapper());

            @Test
            void retryAfterVersionChangeKeepsTheSameFingerprint() {
                String first = hasher.hash(OrderReturnService.mallCancelFingerprint(form(33L, 4, "客户自行撤销")));
                String retry = hasher.hash(OrderReturnService.mallCancelFingerprint(form(33L, 5, "客户自行撤销")));
                assertThat(retry).isEqualTo(first);
            }

            @Test
            void anotherReturnOrReasonCannotReplayTheFirstCancellation() {
                String original = hasher.hash(OrderReturnService.mallCancelFingerprint(form(33L, 4, "客户自行撤销")));
                assertThat(hasher.hash(OrderReturnService.mallCancelFingerprint(form(34L, 4, "客户自行撤销"))))
                        .isNotEqualTo(original);
                assertThat(hasher.hash(OrderReturnService.mallCancelFingerprint(form(33L, 4, "不同原因"))))
                        .isNotEqualTo(original);
            }

            private static OrderReturnDecisionForm form(Long returnId, int version, String reason) {
                OrderReturnDecisionForm form = new OrderReturnDecisionForm();
                form.setReturnId(returnId);
                form.setVersion(version);
                form.setDecisionReason(reason);
                return form;
            }
        }

        @Nested

        class OrderReturnMallCreateReplayTest {
            @Test
            void replayReturnsTheOriginalReturnBeforeAnotherOrderOrReturnWrite() {
                OrderIdempotencyService idempotency = mock(OrderIdempotencyService.class);
                SalesOrderService salesOrders = mock(SalesOrderService.class);
                OrderReturnDao returns = mock(OrderReturnDao.class);
                OrderReturnService service = new OrderReturnService(salesOrders, mock(SalesOrderDao.class),
                        mock(SalesOrderItemDao.class), returns, mock(OrderReturnItemDao.class),
                        mock(OrderReturnReceiptItemDao.class), mock(OrderRefundDao.class),
                        idempotency, mock(OrderOperationLogRecorder.class), mock(ScmDataScopeService.class),
                        mock(com.xsy.scm.customer.service.CustomerService.class), mock(OrderReturnPriceService.class),
                        mock(FinanceReceivableService.class));
                OrderReturnAddForm request = new OrderReturnAddForm();
                request.setOrderId(21L);
                OrderIdempotencyService.Claim claim = new OrderIdempotencyService.Claim(null, true);
                OrderReturnDetailVO original = new OrderReturnDetailVO();
                original.setReturnId(33L);
                when(idempotency.claim("ORDER_RETURN_CREATE", "same-key", request)).thenReturn(claim);
                when(idempotency.replay(claim, OrderReturnDetailVO.class)).thenReturn(original);

                assertThat(service.createFromMall(request, "same-key")).isSameAs(original);
                verify(idempotency).claim("ORDER_RETURN_CREATE", "same-key", request);
                verifyNoInteractions(salesOrders, returns);
            }
        }

        @Nested

        class SalesOrderCancelFingerprintTest {
            private final ScmIdempotencyRequestHasher hasher = new ScmIdempotencyRequestHasher(new ObjectMapper());

            @Test
            void mallCancelFingerprintIgnoresTheOrderVersionSoRetriesStillReplay() {
                OrderCancelForm firstAttempt = form(21L, 4, "下错单了");
                OrderCancelForm retry = form(21L, 5, "下错单了");

                // 原始表单把 version 算进哈希，版本前进后重试就对不上了
                assertThat(hasher.hash(firstAttempt)).isNotEqualTo(hasher.hash(retry));
                // 商城指纹只认订单与原因，重试仍命中同一条防重复处理记录
                assertThat(hasher.hash(SalesOrderService.cancelFingerprint(firstAttempt)))
                        .isEqualTo(hasher.hash(SalesOrderService.cancelFingerprint(retry)));
            }

            @Test
            void mallCancelFingerprintStillSeparatesDifferentOrdersAndReasons() {
                String baseline = hasher.hash(SalesOrderService.cancelFingerprint(form(21L, 4, "下错单了")));

                assertThat(hasher.hash(SalesOrderService.cancelFingerprint(form(22L, 4, "下错单了"))))
                        .isNotEqualTo(baseline);
                assertThat(hasher.hash(SalesOrderService.cancelFingerprint(form(21L, 4, "不需要了"))))
                        .isNotEqualTo(baseline);
            }

            private static OrderCancelForm form(Long orderId, int version, String reason) {
                OrderCancelForm form = new OrderCancelForm();
                form.setOrderId(orderId);
                form.setVersion(version);
                form.setReason(reason);
                return form;
            }
        }

        @Nested

        class SalesOrderOperatorNameTest {
            @Test
            void mallAccountIdIsNeverResolvedAsAnEmployee() {
                String name = SalesOrderQueryService.operatorName("2:44",
                        Map.of(44L, "海岸城门店（商城客户）"), ignored -> "员工甲");

                assertThat(name).isEqualTo("海岸城门店（商城客户）");
            }

            @Test
            void groupsIdsByOperatorTypeAndKeepsUnknownValuesReadable() {
                OrderOperationLogEntity employee = log("1:7");
                OrderOperationLogEntity mall = log("2:7");
                OrderOperationLogEntity system = log("SYSTEM");

                assertThat(SalesOrderQueryService.operatorIds(List.of(employee, mall, system), "1:")).containsExactly(7L);
                assertThat(SalesOrderQueryService.operatorIds(List.of(employee, mall, system), "2:")).containsExactly(7L);
                // 账号查不到名字时只显示身份，不把账号编号露到页面上
                assertThat(SalesOrderQueryService.operatorName("2:8", Map.of(), operator -> operator))
                        .isEqualTo("商城客户");
                assertThat(SalesOrderQueryService.operatorName("SYSTEM", Map.of(), operator -> operator)).isEqualTo("SYSTEM");
            }

            private static OrderOperationLogEntity log(String operator) {
                OrderOperationLogEntity row = new OrderOperationLogEntity();
                row.setOperator(operator);
                return row;
            }
        }
    }
}
