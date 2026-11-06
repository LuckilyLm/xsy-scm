package com.xsy.scm.finance;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.xsy.scm.admin.module.system.login.domain.RequestEmployee;
import com.xsy.scm.admin.module.system.login.manager.LoginManager;
import com.xsy.scm.admin.module.system.menu.domain.vo.MenuVO;
import com.xsy.scm.admin.module.system.role.service.RoleMenuService;
import com.xsy.scm.base.common.domain.UserPermission;
import com.xsy.scm.base.common.enumeration.UserTypeEnum;
import com.xsy.scm.base.common.util.XsyRequestUtil;
import com.xsy.scm.common.ScmW5PgITBase;
import com.xsy.scm.common.ScmW6PgITBase;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.domain.entity.ScmIdempotencyRecordEntity;
import com.xsy.scm.common.error.ScmCommonErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.common.util.ScmDocumentNumbers;
import com.xsy.scm.customer.domain.form.CustomerAddForm;
import com.xsy.scm.customer.domain.form.CustomerStatusForm;
import com.xsy.scm.delivery.domain.form.DeliveryOrdersForm;
import com.xsy.scm.delivery.domain.form.DeliveryRouteForm;
import com.xsy.scm.delivery.domain.form.DeliverySignForm;
import com.xsy.scm.delivery.domain.form.DeliveryVersionForm;
import com.xsy.scm.delivery.service.DeliveryRouteService;
import com.xsy.scm.finance.constant.FinanceConstant;
import com.xsy.scm.finance.constant.FinanceErrorCode;
import com.xsy.scm.finance.constant.ScmFinanceBusinessTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceCounterpartyTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceCustomerRefundMethodEnum;
import com.xsy.scm.finance.constant.ScmFinanceEntryTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceOperationTypeEnum;
import com.xsy.scm.finance.constant.ScmFinancePayableItemSourceTypeEnum;
import com.xsy.scm.finance.constant.ScmFinancePayableSourceTypeEnum;
import com.xsy.scm.finance.constant.ScmFinancePaymentMethodEnum;
import com.xsy.scm.finance.constant.ScmFinancePaymentSourceTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceReceiptMethodEnum;
import com.xsy.scm.finance.constant.ScmFinanceReceivableItemSourceTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceReceivableSourceTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceReverseEntryTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceWriteOffSourceTypeEnum;
import com.xsy.scm.finance.constant.ScmFinanceWriteOffTargetTypeEnum;
import com.xsy.scm.finance.controller.FinancePayableController;
import com.xsy.scm.finance.controller.FinancePaymentController;
import com.xsy.scm.finance.controller.FinanceReadController;
import com.xsy.scm.finance.controller.FinanceReceiptController;
import com.xsy.scm.finance.controller.FinanceWaiverController;
import com.xsy.scm.finance.controller.FinanceWriteOffController;
import com.xsy.scm.finance.dao.FinanceCounterpartySourceDao;
import com.xsy.scm.finance.dao.FinanceCustomerExpenseDao;
import com.xsy.scm.finance.dao.FinanceOrderFundingSourceDao;
import com.xsy.scm.finance.dao.FinancePayableDao;
import com.xsy.scm.finance.dao.FinancePaymentDao;
import com.xsy.scm.finance.dao.FinanceReceiptDao;
import com.xsy.scm.finance.dao.FinanceReceivableDao;
import com.xsy.scm.finance.dao.FinanceReceivableItemDao;
import com.xsy.scm.finance.dao.FinanceReceivableSourceDao;
import com.xsy.scm.finance.dao.FinanceSettlementRuleDao;
import com.xsy.scm.finance.dao.FinanceWaiverDao;
import com.xsy.scm.finance.dao.FinanceWriteOffDao;
import com.xsy.scm.finance.domain.dto.FinanceAutoWriteOffTargetDto;
import com.xsy.scm.finance.domain.dto.FinanceBalanceMovementDto;
import com.xsy.scm.finance.domain.dto.FinanceCreditRuleDto;
import com.xsy.scm.finance.domain.dto.FinanceCustomerFactDto;
import com.xsy.scm.finance.domain.dto.FinanceOrderDiscountLineDto;
import com.xsy.scm.finance.domain.dto.FinanceOrderFundingDto;
import com.xsy.scm.finance.domain.dto.FinanceOrderPayableLineDto;
import com.xsy.scm.finance.domain.dto.FinanceReceivableCreditContextDto;
import com.xsy.scm.finance.domain.dto.FinanceReceivableTargetDto;
import com.xsy.scm.finance.domain.entity.FinancePayableEntity;
import com.xsy.scm.finance.domain.entity.FinancePaymentEntity;
import com.xsy.scm.finance.domain.entity.FinanceReceiptEntity;
import com.xsy.scm.finance.domain.entity.FinanceReceivableEntity;
import com.xsy.scm.finance.domain.entity.FinanceWaiverEntity;
import com.xsy.scm.finance.domain.entity.FinanceWriteOffEntity;
import com.xsy.scm.finance.domain.form.FinanceCodCollectionForm;
import com.xsy.scm.finance.domain.form.FinanceCustomerExpenseForm;
import com.xsy.scm.finance.domain.form.FinanceDatePageForm;
import com.xsy.scm.finance.domain.form.FinanceOperationLogQueryForm;
import com.xsy.scm.finance.domain.form.FinancePayableQueryForm;
import com.xsy.scm.finance.domain.form.FinancePayableRedForm;
import com.xsy.scm.finance.domain.form.FinancePayableRedItemForm;
import com.xsy.scm.finance.domain.form.FinancePaymentAddForm;
import com.xsy.scm.finance.domain.form.FinancePaymentQueryForm;
import com.xsy.scm.finance.domain.form.FinancePaymentReverseForm;
import com.xsy.scm.finance.domain.form.FinancePostingAddForm;
import com.xsy.scm.finance.domain.form.FinancePostingQueryForm;
import com.xsy.scm.finance.domain.form.FinancePostingUpdateForm;
import com.xsy.scm.finance.domain.form.FinancePostingVoidForm;
import com.xsy.scm.finance.domain.form.FinanceReceiptAddForm;
import com.xsy.scm.finance.domain.form.FinanceReceiptQueryForm;
import com.xsy.scm.finance.domain.form.FinanceReceiptReverseForm;
import com.xsy.scm.finance.domain.form.FinanceReceivableQueryForm;
import com.xsy.scm.finance.domain.form.FinanceRefundOptionQueryForm;
import com.xsy.scm.finance.domain.form.FinanceWaiverAddForm;
import com.xsy.scm.finance.domain.form.FinanceWaiverReverseForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffAddForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffAddItemForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffAutoForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffQueryForm;
import com.xsy.scm.finance.domain.form.FinanceWriteOffReverseForm;
import com.xsy.scm.finance.domain.vo.FinanceCustomerExpenseVO;
import com.xsy.scm.finance.domain.vo.FinancePayableRedVO;
import com.xsy.scm.finance.domain.vo.FinancePaymentVO;
import com.xsy.scm.finance.domain.vo.FinancePostingVO;
import com.xsy.scm.finance.domain.vo.FinanceReceiptVO;
import com.xsy.scm.finance.domain.vo.FinanceReceivableVO;
import com.xsy.scm.finance.domain.vo.FinanceWaiverVO;
import com.xsy.scm.finance.domain.vo.FinanceWriteOffAddResultVO;
import com.xsy.scm.finance.domain.vo.FinanceWriteOffAutoPlanVO;
import com.xsy.scm.finance.domain.vo.FinanceWriteOffVO;
import com.xsy.scm.finance.permission.FinancePermission;
import com.xsy.scm.finance.service.FinanceCreditExposureQueryService;
import com.xsy.scm.finance.service.FinanceCustomerExpenseService;
import com.xsy.scm.finance.service.FinanceOperationLogQueryService;
import com.xsy.scm.finance.service.FinanceOrderFundingPolicy;
import com.xsy.scm.finance.service.FinanceOrderFundingSettlementService;
import com.xsy.scm.finance.service.FinanceOrderFundingWriteOffService;
import com.xsy.scm.finance.service.FinanceOrderPayableQueryService;
import com.xsy.scm.finance.service.FinancePayableQueryService;
import com.xsy.scm.finance.service.FinancePayableService;
import com.xsy.scm.finance.service.FinancePaymentQueryService;
import com.xsy.scm.finance.service.FinancePaymentService;
import com.xsy.scm.finance.service.FinancePostingQueryService;
import com.xsy.scm.finance.service.FinancePostingService;
import com.xsy.scm.finance.service.FinanceReceiptQueryService;
import com.xsy.scm.finance.service.FinanceReceiptService;
import com.xsy.scm.finance.service.FinanceReceivableQueryService;
import com.xsy.scm.finance.service.FinanceReceivableService;
import com.xsy.scm.finance.service.FinanceRefundOptionQueryService;
import com.xsy.scm.finance.service.FinanceSettlementRuleService;
import com.xsy.scm.finance.service.FinanceWaiverService;
import com.xsy.scm.finance.service.FinanceWriteOffQueryService;
import com.xsy.scm.finance.service.FinanceWriteOffService;
import com.xsy.scm.finance.support.FinanceExportGuard;
import com.xsy.scm.finance.support.FinanceExportLabels;
import com.xsy.scm.finance.support.FinanceFundingAllocationResult.Status;
import com.xsy.scm.finance.support.FinanceFundingAllocationResult;
import com.xsy.scm.finance.support.FinanceOperationLogRecorder;
import com.xsy.scm.order.domain.form.OrderActualQuantityForm;
import com.xsy.scm.order.domain.form.OrderAddressForm;
import com.xsy.scm.order.domain.form.OrderConfirmForm;
import com.xsy.scm.order.domain.form.OrderRefundCompleteForm;
import com.xsy.scm.order.domain.form.OrderReturnAddForm;
import com.xsy.scm.order.domain.form.OrderReturnApproveForm;
import com.xsy.scm.order.domain.form.OrderReturnApproveItemForm;
import com.xsy.scm.order.domain.form.OrderReturnItemForm;
import com.xsy.scm.order.domain.form.OrderVersionForm;
import com.xsy.scm.order.domain.form.SalesOrderAddForm;
import com.xsy.scm.order.domain.form.SalesOrderItemForm;
import com.xsy.scm.order.domain.vo.OrderReturnDetailVO;
import com.xsy.scm.order.domain.vo.SalesOrderDetailVO;
import com.xsy.scm.order.domain.vo.SalesOrderItemVO;
import com.xsy.scm.order.service.OrderRefundService;
import com.xsy.scm.order.service.OrderReturnService;
import com.xsy.scm.promotion.domain.form.PromotionCouponForm;
import com.xsy.scm.promotion.domain.form.PromotionCouponIssueForm;
import com.xsy.scm.promotion.domain.form.PromotionStatusForm;
import com.xsy.scm.promotion.service.PromotionCouponService;
import com.xsy.scm.purchase.constant.ScmReceiptModeEnum;
import com.xsy.scm.purchase.domain.form.PurchaseOrderAddForm;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptConfirmForm;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptCreateForm;
import com.xsy.scm.purchase.domain.form.PurchaseReceiptPutawayForm;
import com.xsy.scm.purchase.domain.vo.PurchaseOrderVO;
import com.xsy.scm.purchase.domain.vo.PurchaseReceiptItemVO;
import com.xsy.scm.purchase.domain.vo.PurchaseReceiptVO;
import com.xsy.scm.sorting.domain.form.SortingActionForm;
import com.xsy.scm.sorting.domain.form.SortingEntryForm;
import com.xsy.scm.sorting.domain.form.SortingEntryItemForm;
import com.xsy.scm.sorting.domain.form.SortingTaskCreateForm;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FinanceTest {

    @Nested
    class FinanceUnitTest {
        @Nested

        class FinanceCustomerExpenseReplayTest {
            @Test
            void replayRechecksBothCustomerScopeAndCostVisibility() {
                var dao = mock(FinanceCustomerExpenseDao.class);
                var scopes = mock(ScmDataScopeService.class);
                var context = mock(ScmDataScopeContext.class);
                var idempotency = mock(ScmIdempotencyService.class);
                var claim = new ScmIdempotencyService.Claim(new ScmIdempotencyRecordEntity(), true);
                when(idempotency.claim(anyString(), anyString(), any())).thenReturn(claim);
                when(idempotency.replay(eq(claim), eq(Long[].class))).thenReturn(new Long[]{10L});
                var stored = new FinanceCustomerExpenseVO();
                stored.setSellerId(99L);
                when(dao.find(10L)).thenReturn(stored);
                when(scopes.resolve()).thenReturn(context);
                when(context.isCostVisible()).thenReturn(true);
                when(context.getCustomerSellerScope()).thenReturn(ScmValueScope.of(List.of(2L)));
                var service = new FinanceCustomerExpenseService(dao, mock(FinanceCounterpartySourceDao.class), scopes, idempotency);
                var form = new FinanceCustomerExpenseForm.BatchAdd();
                form.setExpenseMonth(LocalDate.of(2026, 11, 1));
                assertThatThrownBy(() -> service.addBatch(form, "old-key")).isInstanceOf(ScmDataScopeException.class);
                when(context.getCustomerSellerScope()).thenReturn(ScmValueScope.all());
                when(context.isCostVisible()).thenReturn(false);
                assertThatThrownBy(() -> service.addBatch(form, "old-key")).isInstanceOf(ScmDataScopeException.class);
                verify(dao, never()).insert(any());
            }
        }

        @Nested
        @DisplayName("财务同步导出行数守卫")
        class FinanceExportGuardTest {
            @Test
            @DisplayName("完整结果在上限内返回，查询仅执行一次并探测多一行")
            void returnsRowsFromSingleProbeQuery() {
                AtomicLong requestedPageSize = new AtomicLong();
                List<String> expected = List.of("first", "second");

                List<String> actual = FinanceExportGuard.exportRows(pageSize -> {
                    requestedPageSize.set(pageSize);
                    return expected;
                });

                assertThat(actual).isSameAs(expected);
                assertThat(requestedPageSize).hasValue(FinanceExportGuard.PROBE_PAGE_SIZE);
            }

            @Test
            @DisplayName("超过十万行明确返回 41112，不静默截断")
            void rejectsRowsAboveLimit() {
                List<String> tooMany = Collections.nCopies((int) FinanceExportGuard.PROBE_PAGE_SIZE, "row");

                assertThatThrownBy(() -> FinanceExportGuard.exportRows(ignored -> tooMany))
                        .isInstanceOfSatisfying(ScmBusinessException.class,
                                error -> assertThat(error.getErrorCode().getCode()).isEqualTo(41112));
            }
        }

        @Nested

        class FinanceExportLabelsTest {
            @Test
            void mapsReceiptsPaymentsAndWriteOffReversalsWithoutExposingInternalCodes() {
                assertThat(FinanceExportLabels.entryType("NORMAL")).isEqualTo("原始记录");
                assertThat(FinanceExportLabels.entryType("RED")).isEqualTo("扣减记录");
                assertThat(FinanceExportLabels.entryType("REVERSE")).isEqualTo("撤销记录");
                assertThat(FinanceExportLabels.entryType(null)).isEmpty();
                assertThat(FinanceExportLabels.entryType("")).isEmpty();
                assertThat(FinanceExportLabels.entryType("UNKNOWN")).isEqualTo("UNKNOWN");
            }
        }

        @Nested

        class FinanceOrderFundingPolicyTest {
            private final FinanceOrderFundingSourceDao sources = mock(FinanceOrderFundingSourceDao.class);
            private final FinanceOrderFundingPolicy policy = new FinanceOrderFundingPolicy(sources);

            @Test
            void balanceFundingCannotUseExternalRefundEvenWhenAnOnlineTransactionWasSelected() {
                when(sources.selectOrderFunding(10L)).thenReturn(List.of(balance(), online()));
                assertThatThrownBy(() -> policy.requireCashRefundAllowed(10L, true))
                        .isInstanceOfSatisfying(ScmBusinessException.class,
                                e -> assertThat(e.getErrorCode()).isEqualTo(FinanceErrorCode.REFUND_ALLOCATION_REQUIRED));
            }

            @Test
            void pureBalanceCannotUseManualCashRefund() {
                when(sources.selectOrderFunding(10L)).thenReturn(List.of(balance()));
                assertThatThrownBy(() -> policy.requireCashRefundAllowed(10L, false))
                        .isInstanceOfSatisfying(ScmBusinessException.class,
                                e -> assertThat(e.getErrorCode()).isEqualTo(FinanceErrorCode.BALANCE_REFUND_REQUIRES_WALLET));
            }

            @Test
            void balanceWithManuallyAllocatedReceiptRequiresFundingAllocation() {
                when(sources.selectOrderFunding(10L)).thenReturn(List.of(balance()));
                when(sources.hasReceiptFunding(10L)).thenReturn(true);
                assertThatThrownBy(() -> policy.requireCashRefundAllowed(10L, false))
                        .isInstanceOfSatisfying(ScmBusinessException.class,
                                e -> assertThat(e.getErrorCode()).isEqualTo(FinanceErrorCode.REFUND_ALLOCATION_REQUIRED));
            }

            @Test
            void offlineManualRefundRemainsAvailableWithoutPaymentTransactions() {
                when(sources.selectOrderFunding(10L)).thenReturn(List.of());
                assertThatCode(() -> policy.requireCashRefundAllowed(10L, false)).doesNotThrowAnyException();
                assertThatThrownBy(() -> policy.requireCashRefundAllowed(10L, true)).isInstanceOf(ScmBusinessException.class);
            }

            @ParameterizedTest
            @ValueSource(strings = {"movement", "amount", "settlement", "receipt", "provider", "status", "customer"})
            void rejectsIncompleteOrMismatchedBalanceFacts(String mismatch) {
                var fact = balance();
                switch (mismatch) {
                    case "movement" -> fact.setMovementId(null);
                    case "amount" -> fact.setMovementAmount(new BigDecimal("99"));
                    case "settlement" -> fact.setMovementSettlementCustomerId(999L);
                    case "receipt" -> fact.setReceiptId(99L);
                    case "provider" -> fact.setProvider("MOCK");
                    case "status" -> fact.setTransactionStatus("PENDING");
                    case "customer" -> fact.setMovementCustomerId(999L);
                    default -> throw new IllegalArgumentException(mismatch);
                }
                assertThatThrownBy(() -> policy.requireSuccessful(fact)).isInstanceOf(ScmBusinessException.class);
            }

            @Test
            void onlineFundingUsesActualAmountAndRequiresReceiptBeforeRefund() {
                var fact = online();
                fact.setProviderAmount(new BigDecimal("98")); fact.setReceiptAmount(new BigDecimal("98"));
                when(sources.selectOrderFunding(10L)).thenReturn(List.of(fact));
                assertThatCode(() -> policy.requireCashRefundAllowed(10L, true)).doesNotThrowAnyException();
                fact.setReceiptId(null);
                assertThatThrownBy(() -> policy.requireCashRefundAllowed(10L, true)).isInstanceOf(ScmBusinessException.class);
            }

            static FinanceOrderFundingDto balance() {
                var fact = online();
                fact.setMethod("BALANCE"); fact.setProvider("INTERNAL_BALANCE"); fact.setTransactionProvider("INTERNAL_BALANCE");
                fact.setReceiptId(null); fact.setMovementId(30L); fact.setMovementCustomerId(2L);
                fact.setMovementSettlementCustomerId(3L); fact.setMovementIntentId(1L);
                fact.setMovementType("CONSUME"); fact.setMovementDirection("DEBIT"); fact.setMovementSourceType("PAYMENT_INTENT");
                fact.setMovementAmount(new BigDecimal("100.0000"));
                fact.setMovementOccurredAt(fact.getPaidAt());
                return fact;
            }

            static FinanceOrderFundingDto online() {
                var fact = new FinanceOrderFundingDto();
                fact.setIntentId(1L); fact.setTransactionId(20L); fact.setOrderId(10L);
                fact.setCustomerId(2L); fact.setSettlementCustomerId(3L); fact.setSourceType("SALES_ORDER");
                fact.setMethod("ONLINE"); fact.setProvider("MOCK"); fact.setTransactionProvider("MOCK");
                fact.setIntentStatus("SUCCEEDED"); fact.setTransactionStatus("SUCCEEDED");
                fact.setAmount(new BigDecimal("100.0000")); fact.setProviderAmount(new BigDecimal("100.0000"));
                fact.setPaidAt(OffsetDateTime.parse("2026-10-04T12:00:00+08:00")); fact.setReceiptId(40L);
                fact.setTransactionAmount(fact.getAmount());
                fact.setReceiptCustomerId(2L); fact.setReceiptSettlementCustomerId(3L); fact.setReceiptAmount(fact.getProviderAmount());
                return fact;
            }
        }

        @Nested

        class FinanceOrderFundingSettlementServiceTest {
            private final FinanceReceivableDao receivables = mock(FinanceReceivableDao.class);
            private final FinanceReceiptDao receipts = mock(FinanceReceiptDao.class);
            private final FinanceOrderFundingSourceDao sources = mock(FinanceOrderFundingSourceDao.class);
            private final FinanceOrderFundingWriteOffService writeOffs = mock(FinanceOrderFundingWriteOffService.class);
            private final FinanceOrderFundingSettlementService service = new FinanceOrderFundingSettlementService(
                    receivables, receipts, sources, new FinanceOrderFundingPolicy(sources), writeOffs);

            @BeforeEach
            void funding() {
                var balance = FinanceOrderFundingPolicyTest.balance();
                var online = FinanceOrderFundingPolicyTest.online();
                online.setTransactionId(21L);
                when(sources.selectOrderFunding(10L)).thenReturn(List.of(balance, online));
                var normal = new FinanceReceivableEntity(); normal.setId(50L);
                when(receivables.selectNormalByOrder(10L)).thenReturn(normal);
            }

            @Test
            void everyFundingSourceIsLockedBeforeAnyWriteOffIsRegistered() {
                when(writeOffs.register(20L, 50L))
                        .thenReturn(new FinanceFundingAllocationResult(20L, Status.APPLIED, new BigDecimal("100")));
                when(writeOffs.register(21L, 50L))
                        .thenReturn(new FinanceFundingAllocationResult(21L, Status.APPLIED, new BigDecimal("98")));
                service.settleSalesOrderFunding(10L);
                // 先锁全部来源再锁目标：否则下一笔来源与人工核销会形成目标 → 来源的反向锁链
                var sequence = inOrder(sources, receipts, writeOffs);
                sequence.verify(sources).lockMovement(30L);
                sequence.verify(receipts).selectByIdForUpdate(40L);
                sequence.verify(writeOffs).register(20L, 50L);
                sequence.verify(writeOffs).register(21L, 50L);
            }

            @Test
            void missingNormalReceivableIsReportedPerFundingWithoutTouchingWriteOff() {
                when(receivables.selectNormalByOrder(10L)).thenReturn(null);
                var results = service.settleSalesOrderFunding(10L);
                assertThat(results).hasSize(2);
                assertThat(results).extracting(FinanceFundingAllocationResult::transactionId).containsExactly(20L, 21L);
                assertThat(results).allSatisfy(result -> {
                    assertThat(result.status()).isEqualTo(Status.NO_RECEIVABLE);
                    assertThat(result.appliedAmount()).isEqualByComparingTo("0");
                });
                verifyNoInteractions(writeOffs);
                verify(sources, never()).lockMovement(anyLong());
                verify(receipts, never()).selectByIdForUpdate(anyLong());
            }

            @Test
            void allocationsKeepTheFundingOrderAndPreserveStatusesLeftByEarlierProcessing() {
                when(writeOffs.register(20L, 50L))
                        .thenReturn(new FinanceFundingAllocationResult(20L, Status.REVERSED, BigDecimal.ZERO));
                when(writeOffs.register(21L, 50L))
                        .thenReturn(new FinanceFundingAllocationResult(21L, Status.APPLIED, new BigDecimal("98")));
                var results = service.settleSalesOrderFunding(10L);
                assertThat(results).extracting(FinanceFundingAllocationResult::status)
                        .containsExactly(Status.REVERSED, Status.APPLIED);
                assertThat(results.get(1).appliedAmount()).isEqualByComparingTo("98");
            }
        }

        @Nested

        class FinanceOrderFundingWriteOffServiceTest {
            private final FinanceOrderFundingSourceDao sources = mock(FinanceOrderFundingSourceDao.class);
            private final FinanceReceivableDao receivables = mock(FinanceReceivableDao.class);
            private final FinanceReceiptDao receipts = mock(FinanceReceiptDao.class);
            private final FinanceWriteOffDao writeOffs = mock(FinanceWriteOffDao.class);
            private final FinanceOperationLogRecorder logs = mock(FinanceOperationLogRecorder.class);
            private final FinanceOrderFundingWriteOffService service = new FinanceOrderFundingWriteOffService(sources,
                    new FinanceOrderFundingPolicy(sources), receipts, receivables, writeOffs, logs);

            @BeforeEach
            void target() {
                var target = new FinanceReceivableEntity();
                target.setId(50L); target.setOrderId(10L); target.setCustomerId(2L); target.setSettlementCustomerId(3L);
                target.setAmount(new BigDecimal("70")); target.setReceivableNo("AR50");
                when(receivables.selectById(50L)).thenReturn(target);
                when(receivables.selectNormalTargetForUpdate(50L)).thenReturn(new FinanceReceivableTargetDto());
                when(receivables.selectRedAmount(50L)).thenReturn(new BigDecimal("30"));
                when(writeOffs.selectTargetWrittenOffAmount("RECEIVABLE", 50L)).thenReturn(BigDecimal.ZERO);
            }

            @Test
            void fullBalanceConsumptionRemainsAppliedWhenDeliveryAndRedReduceDebt() {
                balanceSource();
                when(writeOffs.insertBalanceOnConflictDoNothing(any())).thenReturn(1);
                var result = service.register(20L, 50L);
                assertThat(result.status()).isEqualTo(Status.APPLIED);
                assertThat(result.appliedAmount()).isEqualByComparingTo("100");
                var row = ArgumentCaptor.forClass(FinanceWriteOffEntity.class);
                verify(writeOffs).insertBalanceOnConflictDoNothing(row.capture());
                assertThat(row.getValue().getSourceType()).isEqualTo("BALANCE_MOVEMENT");
                assertThat(row.getValue().getAmount()).isEqualByComparingTo("100");
                verifyNoInteractions(receipts);
            }

            @Test
            void reversedBalanceAllocationIsNotRestoredOnReplay() {
                balanceSource();
                var existing = new FinanceWriteOffEntity();
                existing.setId(60L); existing.setTargetType("RECEIVABLE"); existing.setTargetId(50L);
                existing.setAmount(new BigDecimal("100"));
                when(writeOffs.selectNormalAllocation("BALANCE_MOVEMENT", 30L, null)).thenReturn(existing);
                when(writeOffs.hasReversal(60L)).thenReturn(true);
                assertThat(service.register(20L, 50L).status()).isEqualTo(Status.REVERSED);
                verify(writeOffs, never()).insertBalanceOnConflictDoNothing(any());
                verifyNoInteractions(logs);
            }

            @Test
            void onlineAllocationUsesOnlyUnallocatedActualReceiptAmount() {
                onlineSource();
                when(writeOffs.selectSourceUsedAmount("RECEIPT", 40L)).thenReturn(new BigDecimal("30"));
                when(writeOffs.insert(any(FinanceWriteOffEntity.class))).thenReturn(1);
                var result = service.register(20L, 50L);
                assertThat(result.status()).isEqualTo(Status.PARTIALLY_AVAILABLE);
                assertThat(result.appliedAmount()).isEqualByComparingTo("68");
            }

            @Test
            void manualPartialAllocationToSameTargetIsPreserved() {
                onlineSource();
                var existing = new FinanceWriteOffEntity();
                existing.setId(60L); existing.setAmount(new BigDecimal("10"));
                when(writeOffs.selectNormalAllocation("RECEIPT", 40L, 50L)).thenReturn(existing);
                assertThat(service.register(20L, 50L).status()).isEqualTo(Status.ALREADY_ALLOCATED);
                verify(writeOffs, never()).insert(any(FinanceWriteOffEntity.class));
                verifyNoInteractions(logs);
            }

            @Test
            void exhaustedBalanceSourceReportsNoAvailableFunds() {
                balanceSource();
                when(writeOffs.selectSourceUsedAmount("BALANCE_MOVEMENT", 30L)).thenReturn(new BigDecimal("100"));
                var result = service.register(20L, 50L);
                assertThat(result.status()).isEqualTo(Status.NO_AVAILABLE_FUNDS);
                assertThat(result.appliedAmount()).isEqualByComparingTo("0");
                verify(writeOffs, never()).insertBalanceOnConflictDoNothing(any());
                verifyNoInteractions(logs);
            }

            @Test
            void partiallyUsedBalanceSourceIsRejectedBecauseBalanceAllocationIsAllOrNothing() {
                balanceSource();
                when(writeOffs.selectSourceUsedAmount("BALANCE_MOVEMENT", 30L)).thenReturn(new BigDecimal("40"));
                assertInvalid(() -> service.register(20L, 50L));
                verify(writeOffs, never()).insertBalanceOnConflictDoNothing(any());
            }

            @Test
            void balanceMovementMustExistAndBelongToTheFundingIntent() {
                balanceSource();
                when(sources.lockMovement(30L)).thenReturn(null);
                assertInvalid(() -> service.register(20L, 50L));
                var foreign = new FinanceBalanceMovementDto();
                foreign.setSourceId(999L); foreign.setAmount(new BigDecimal("100"));
                when(sources.lockMovement(30L)).thenReturn(foreign);
                assertInvalid(() -> service.register(20L, 50L));
                verify(writeOffs, never()).insertBalanceOnConflictDoNothing(any());
            }

            @Test
            void rechargeReceiptCannotBeAllocatedToAnOrderReceivable() {
                onlineSource();
                when(sources.isRechargeReceipt(40L)).thenReturn(true);
                assertInvalid(() -> service.register(20L, 50L));
                verify(writeOffs, never()).insert(any(FinanceWriteOffEntity.class));
            }

            @Test
            void appliedWriteOffLogsTheReceivableBalanceBeforeAndAfter() {
                balanceSource();
                when(writeOffs.insertBalanceOnConflictDoNothing(any())).thenAnswer(invocation -> {
                    FinanceWriteOffEntity row = invocation.getArgument(0); row.setId(60L); return 1;
                });
                service.register(20L, 50L);
                var before = ArgumentCaptor.forClass(Object.class);
                var after = ArgumentCaptor.forClass(Object.class);
                verify(logs).record(eq(ScmFinanceBusinessTypeEnum.WRITE_OFF), eq(60L),
                        eq(ScmFinanceOperationTypeEnum.WRITE_OFF), isNull(), before.capture(), after.capture(),
                        eq(FinanceConstant.ORDER_FUNDING_OPERATOR));
                // 净额 40 = 应收 70 − 红冲 30；核销 100 因此超额 60 —— 超额只在读侧表达，不改原债权
                assertThat(snapshot(before.getValue())).containsEntry("targetNo", "AR50").containsEntry("netAmount", "40")
                        .containsEntry("writtenOffAmount", "0").containsEntry("openAmount", "40")
                        .containsEntry("overAppliedAmount", "0");
                assertThat(snapshot(after.getValue())).containsEntry("netAmount", "40")
                        .containsEntry("writtenOffAmount", "100").containsEntry("openAmount", "0")
                        .containsEntry("overAppliedAmount", "60");
            }

            @Test
            void concurrentInsertConflictReplaysTheExistingAllocation() {
                balanceSource();
                when(writeOffs.insertBalanceOnConflictDoNothing(any())).thenReturn(0);
                var existing = new FinanceWriteOffEntity();
                existing.setId(60L); existing.setTargetType("RECEIVABLE"); existing.setTargetId(50L);
                existing.setAmount(new BigDecimal("100"));
                // 首次查重为空才会走到插入；冲突后回读才拿到并发写入的那条（两次入参完全相同）
                when(writeOffs.selectNormalAllocation("BALANCE_MOVEMENT", 30L, null)).thenReturn(null, existing);
                when(writeOffs.hasReversal(60L)).thenReturn(false);
                assertThat(service.register(20L, 50L).status()).isEqualTo(Status.ALREADY_ALLOCATED);
                verifyNoInteractions(logs);
            }

            @Test
            void insertConflictAgainstADifferentAllocationFailsInsteadOfPassingSilently() {
                balanceSource();
                when(writeOffs.insertBalanceOnConflictDoNothing(any())).thenReturn(0);
                var other = new FinanceWriteOffEntity();
                other.setId(61L); other.setTargetType("RECEIVABLE"); other.setTargetId(77L);
                other.setAmount(new BigDecimal("100"));
                when(writeOffs.selectNormalAllocation("BALANCE_MOVEMENT", 30L, null)).thenReturn(null, other);
                assertInvalid(() -> service.register(20L, 50L));
                verifyNoInteractions(logs);
            }

            private static void assertInvalid(Executable executable) {
                assertThatThrownBy(executable::execute).isInstanceOfSatisfying(ScmBusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(FinanceErrorCode.ORDER_FUNDING_INVALID));
            }

            @SuppressWarnings("unchecked")
            private static Map<String, Object> snapshot(Object captured) {
                return (Map<String, Object>) captured;
            }

            private void balanceSource() {
                when(sources.selectTransaction(20L)).thenReturn(FinanceOrderFundingPolicyTest.balance());
                var movement = new FinanceBalanceMovementDto();
                movement.setSourceId(1L); movement.setAmount(new BigDecimal("100"));
                when(sources.lockMovement(30L)).thenReturn(movement);
                when(writeOffs.selectSourceUsedAmount("BALANCE_MOVEMENT", 30L)).thenReturn(BigDecimal.ZERO);
            }

            private void onlineSource() {
                var fact = FinanceOrderFundingPolicyTest.online(); fact.setProviderAmount(new BigDecimal("98")); fact.setReceiptAmount(new BigDecimal("98"));
                when(sources.selectTransaction(20L)).thenReturn(fact);
                var receipt = new FinanceReceiptEntity();
                receipt.setCustomerId(2L); receipt.setSettlementCustomerId(3L); receipt.setEntryType("NORMAL");
                receipt.setAmount(new BigDecimal("98"));
                when(receipts.selectByIdForUpdate(40L)).thenReturn(receipt);
                when(receipts.selectReversedAmount(40L)).thenReturn(BigDecimal.ZERO);
            }
        }

        @Nested
        @DisplayName("商城余额支付与签收共用的应付净额规则")
        class FinanceOrderPayableQueryServiceTest {
            private static final Long ORDER_ID = 7L;

            private final FinanceReceivableSourceDao sourceDao = mock(FinanceReceivableSourceDao.class);
            private final FinanceReceivableDao receivableDao = mock(FinanceReceivableDao.class);
            private final FinanceOrderFundingSourceDao fundingDao = mock(FinanceOrderFundingSourceDao.class);
            private final FinanceReceiptDao receiptDao = mock(FinanceReceiptDao.class);
            private final FinanceOrderPayableQueryService service = new FinanceOrderPayableQueryService(sourceDao, receivableDao,
                    fundingDao, receiptDao);

            @Test
            void fullQuantityNetMatchesTheFrozenDiscount() {
                // 10.0000 × 2.0000 = 20.0000，本行已确认优惠 5.0000 全额承担
                when(sourceDao.selectOrderPayableLines(ORDER_ID)).thenReturn(List.of(payableLine(1L, "10.0000", "2.0000",
                        "20.0000"), payableLine(2L, "5.0000", "2.0000", "10.0000")));
                when(sourceDao.selectOrderDiscountLines(ORDER_ID)).thenReturn(List.of(discountLine(1L, "5.0000")));

                assertThat(service.payableOfConfirmedOrder(ORDER_ID)).isEqualByComparingTo("25.0000");
            }

            @Test
            void partialQuantityProRatesTheDiscountInsteadOfChargingItWhole() {
                // 下单量与实重不同才测得到「等比」：实发只有下单量的一半，本行优惠也要减半，
                // 否则少发的货还要背全额优惠，客户付的钱与签收后的应收就对不上。
                when(sourceDao.selectOrderPayableLines(ORDER_ID)).thenReturn(
                        List.of(payableLine(1L, "5.0000", "2.0000", "20.0000")));
                when(sourceDao.selectOrderDiscountLines(ORDER_ID)).thenReturn(List.of(discountLine(1L, "5.0000")));

                assertThat(service.payableOfConfirmedOrder(ORDER_ID)).isEqualByComparingTo("7.5000");
            }

            @Test
            void orderWithoutFrozenDiscountPaysTheGrossAmount() {
                when(sourceDao.selectOrderPayableLines(ORDER_ID)).thenReturn(
                        List.of(payableLine(1L, "3.0000", "4.0000", "12.0000")));
                when(sourceDao.selectOrderDiscountLines(ORDER_ID)).thenReturn(List.of());

                assertThat(service.payableOfConfirmedOrder(ORDER_ID)).isEqualByComparingTo("12.0000");
            }

            @Test
            void standardItemWithoutActualWeightFallsBackToOrderedQuantity() {
                // 标准品确认时实重可以为空，应付必须按下单量估算；否则订单详情会因空值直接 500
                when(sourceDao.selectOrderPayableLines(ORDER_ID)).thenReturn(
                        List.of(payableLineWithoutActual(1L, "4.0000", "5.5000", "22.0000")));
                when(sourceDao.selectOrderDiscountLines(ORDER_ID)).thenReturn(List.of());

                assertThat(service.payableOfConfirmedOrder(ORDER_ID)).isEqualByComparingTo("22.0000");
            }

            @Test
            void signedOrderUsesTheActualReceivableWhenOutboundIsLessThanConfirmedWeight() {
                FinanceReceivableEntity receivable = new FinanceReceivableEntity();
                receivable.setId(31L);
                FinanceReceivableVO summary = new FinanceReceivableVO();
                summary.setNetAmount(new BigDecimal("8.0000"));
                when(receivableDao.selectNormalByOrder(ORDER_ID)).thenReturn(receivable);
                when(receivableDao.selectSummaryById(31L)).thenReturn(summary);

                assertThat(service.payableOfConfirmedOrder(ORDER_ID)).isEqualByComparingTo("8.0000");
            }

            @Test
            void paidAmountIsTheSumOfRecordedOrderFunding() {
                when(fundingDao.selectOrderFunding(ORDER_ID)).thenReturn(List.of(funding("8.0000"), funding("2.0000")));

                assertThat(service.paidAmountOf(ORDER_ID)).isEqualByComparingTo("10.0000");
            }

            @Test
            void unpaidOrderHasZeroPaidAmount() {
                when(fundingDao.selectOrderFunding(ORDER_ID)).thenReturn(List.of());

                assertThat(service.paidAmountOf(ORDER_ID)).isEqualByComparingTo("0.0000");
            }

            @Test
            void receivedAmountAddsCodCollectionsOnTopOfPaymentFacts() {
                when(fundingDao.selectOrderFunding(ORDER_ID)).thenReturn(List.of(funding("8.0000")));
                when(receiptDao.sumCodCollectedByOrder(ORDER_ID)).thenReturn(new BigDecimal("2.4000"));
                when(receiptDao.sumManualCollectedByOrder(ORDER_ID)).thenReturn(new BigDecimal("1.4000"));

                assertThat(service.paidAmountOf(ORDER_ID)).as("支付记录规则不含 COD 收款").isEqualByComparingTo("8.0000");
                assertThat(service.receivedAmountOf(ORDER_ID)).as("历史人工补收也计入该订单实收").isEqualByComparingTo("11.8000");
            }

            @Test
            void receivedAmountIsZeroWhenNothingArrivedAndNoCodCollection() {
                when(fundingDao.selectOrderFunding(ORDER_ID)).thenReturn(List.of());
                when(receiptDao.sumCodCollectedByOrder(ORDER_ID)).thenReturn(null);

                assertThat(service.receivedAmountOf(ORDER_ID)).isEqualByComparingTo("0.0000");
            }

            private static FinanceOrderPayableLineDto payableLine(Long orderItemId, String actualQuantity, String price,
                    String orderedLineAmount) {
                FinanceOrderPayableLineDto line = new FinanceOrderPayableLineDto();
                line.setOrderItemId(orderItemId);
                line.setActualQuantity(new BigDecimal(actualQuantity));
                line.setOrderedQuantity(new BigDecimal(actualQuantity));
                line.setLockedUnitPrice(new BigDecimal(price));
                line.setOrderedLineAmount(new BigDecimal(orderedLineAmount));
                return line;
            }

            private static FinanceOrderPayableLineDto payableLineWithoutActual(Long orderItemId, String orderedQuantity,
                    String price, String orderedLineAmount) {
                FinanceOrderPayableLineDto line = payableLine(orderItemId, orderedQuantity, price, orderedLineAmount);
                line.setActualQuantity(null);
                return line;
            }

            private static FinanceOrderDiscountLineDto discountLine(Long orderItemId, String discountAmount) {
                FinanceOrderDiscountLineDto line = new FinanceOrderDiscountLineDto();
                line.setOrderItemId(orderItemId);
                line.setDiscountAmount(new BigDecimal(discountAmount));
                return line;
            }

            private static FinanceOrderFundingDto funding(String amount) {
                FinanceOrderFundingDto dto = new FinanceOrderFundingDto();
                dto.setAmount(new BigDecimal(amount));
                return dto;
            }
        }

        @Nested
        @DisplayName("财务域只读约定（静态扫描）")
        class FinanceReadOnlyContractTest {
            /**
             * 财务域不得写入的业务表，按前缀匹配：{@code xsy_sales_order*} / {@code xsy_order_return*} /
             * {@code xsy_order_refund} / {@code purchase_*} / {@code inventory_*} / {@code delivery_*}。
             *
             * <p>{@code xsy_sorting_task} 也一并纳入：财务不消费分拣记录（应收数量只取出库量），
             * 因此同样没有任何写它的理由。
             */
            private static final List<String> READ_ONLY_TABLE_PREFIXES = List.of(
                    "xsy_sales_order", "xsy_order_return", "xsy_order_refund",
                    "purchase_", "inventory_", "delivery_", "sorting_");

            /**
             * 写语句关键字，不含 {@code SELECT}：财务域允许读这些表，这正是本约定的前提。
             */
            private static final Pattern WRITE_STATEMENT = Pattern.compile(
                    "\\b(INSERT\\s+INTO|UPDATE|DELETE\\s+FROM|TRUNCATE(?:\\s+TABLE)?)\\s+"
                            + "(" + String.join("|", READ_ONLY_TABLE_PREFIXES) + ")[A-Za-z0-9_]*",
                    Pattern.CASE_INSENSITIVE);

            /**
             * Java 字符串字面量（含文本块）。SQL 只可能出现在这里或 mapper XML 里，
             * 因此只扫描这两处就能覆盖全部越界写法，同时避免把 javadoc 里
             * 「不得 UPDATE {@code xsy_sales_order}」这类说明性文字误判成违规。
             */
            private static final Pattern JAVA_TEXT_BLOCK = Pattern.compile("\"\"\"(.*?)\"\"\"", Pattern.DOTALL);
            private static final Pattern JAVA_STRING_LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

            private static final Pattern SQL_ANNOTATION = Pattern.compile(
                    "@(Insert|Update|Delete|Select)\\s*\\(");

            private static final Pattern TABLE_NAME_ANNOTATION = Pattern.compile("@TableName\\s*\\(\\s*(?:value\\s*=\\s*)?\"([^\"]+)\"");

            private static final Pattern XML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

            @Test
            @DisplayName("finance 包内不存在针对业务表 / 库存表 / 配送表 / 分拣表的写语句")
            void financePackageNeverWritesBusinessTables() throws IOException {
                Path javaRoot = moduleRoot().resolve("src/main/java/com/xsy/scm/finance");
                assertThat(javaRoot).as("财务域源码目录必须存在，否则本断言形同虚设").exists();

                List<String> violations = new ArrayList<>();
                try (Stream<Path> paths = Files.walk(javaRoot)) {
                    for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                        String source = Files.readString(path, StandardCharsets.UTF_8);
                        for (String fragment : stringFragmentsOf(source)) {
                            collectViolations(fragment, javaRoot.relativize(path).toString(), violations);
                        }
                    }
                }

                // mapper XML：财务域自定义 SQL 的唯一合法去处（见 AGENTS.md）。目录存在就纳入扫描，
                // 否则越界 SQL 可以从这里溜过去。
                Path mapperRoot = moduleRoot().resolve("src/main/resources/mapper/scm/finance");
                if (Files.isDirectory(mapperRoot)) {
                    try (Stream<Path> paths = Files.walk(mapperRoot)) {
                        for (Path path : paths.filter(p -> p.toString().endsWith(".xml")).toList()) {
                            String xml = XML_COMMENT.matcher(Files.readString(path, StandardCharsets.UTF_8))
                                    .replaceAll(" ");
                            collectViolations(xml, mapperRoot.relativize(path).toString(), violations);
                        }
                    }
                }

                assertThat(violations)
                        .as("财务域对业务表只读；越界写语句会让库存账 / 订单账出现解释不了的记录")
                        .isEmpty();
            }

            @Test
            @DisplayName("finance 包不在 mapper 注解里写 SQL（AGENTS.md §8）")
            void financePackageHasNoSqlInAnnotations() throws IOException {
                Path javaRoot = moduleRoot().resolve("src/main/java/com/xsy/scm/finance");
                List<String> violations = new ArrayList<>();
                try (Stream<Path> paths = Files.walk(javaRoot)) {
                    for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                        String source = Files.readString(path, StandardCharsets.UTF_8);
                        Matcher matcher = SQL_ANNOTATION.matcher(source);
                        while (matcher.find()) {
                            violations.add(javaRoot.relativize(path) + " -> @" + matcher.group(1));
                        }
                    }
                }
                assertThat(violations)
                        .as("自定义 SQL 必须放 mapper XML，注解里只留方法签名")
                        .isEmpty();
            }

            @Test
            @DisplayName("finance 包的每个实体都映射 finance_* 表，因此 BaseMapper 的写方法够不到业务表")
            void everyFinanceEntityMapsAFinanceTable() throws IOException {
                Path javaRoot = moduleRoot().resolve("src/main/java/com/xsy/scm/finance");
                List<String> mapped = new ArrayList<>();
                try (Stream<Path> paths = Files.walk(javaRoot)) {
                    for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                        Matcher matcher = TABLE_NAME_ANNOTATION.matcher(Files.readString(path, StandardCharsets.UTF_8));
                        while (matcher.find()) {
                            mapped.add(matcher.group(1));
                        }
                    }
                }
                // 每个财务实体都只能映射财务表，新增费用或更正来源不得突破业务表写入边界。
                assertThat(mapped)
                        .as("财务域实体的 @TableName 取值")
                        .hasSize(14)
                        .allSatisfy(table -> assertThat(table).startsWith("xsy_finance_"))
                        .contains("xsy_finance_receivable", "xsy_finance_receivable_item",
                                "xsy_finance_payable", "xsy_finance_payable_item",
                                "xsy_finance_receipt", "xsy_finance_payment",
                                "xsy_finance_write_off", "xsy_finance_operation_log",
                                "xsy_finance_waiver", "xsy_finance_posting", "xsy_finance_posting_void",
                                "xsy_finance_customer_expense", "xsy_finance_customer_expense_void",
                                "xsy_finance_purchase_cost_entry");
            }

            @Test
            @DisplayName("finance 包不依赖 report 包（导出统一走 common 的 ScmExcel，不跨域引用）")
            void financePackageDoesNotDependOnReportPackage() throws IOException {
                Path javaRoot = moduleRoot().resolve("src/main/java/com/xsy/scm/finance");
                List<String> violations = new ArrayList<>();
                try (Stream<Path> paths = Files.walk(javaRoot)) {
                    for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                            if (line.startsWith("import") && line.contains("module.scm.report")) {
                                violations.add(javaRoot.relativize(path) + " -> " + line.trim());
                            }
                        }
                    }
                }
                assertThat(violations)
                        .as("report 域只读 finance 表，finance 域不得反向依赖 report")
                        .isEmpty();
            }

            private void collectViolations(String sql, String where, List<String> sink) {
                Matcher matcher = WRITE_STATEMENT.matcher(sql);
                while (matcher.find()) {
                    sink.add(where + " -> " + matcher.group(0).replaceAll("\\s+", " "));
                }
            }

            private List<String> stringFragmentsOf(String source) {
                List<String> fragments = new ArrayList<>();
                Matcher blocks = JAVA_TEXT_BLOCK.matcher(source);
                while (blocks.find()) {
                    fragments.add(blocks.group(1));
                }
                String withoutTextBlocks = JAVA_TEXT_BLOCK.matcher(source).replaceAll("\"\"");
                Matcher literals = JAVA_STRING_LITERAL.matcher(withoutTextBlocks);
                while (literals.find()) {
                    fragments.add(literals.group(1));
                }
                return fragments;
            }

            /**
             * 定位 {@code xsy-admin} 模块目录。
             *
             * <p>Surefire 的 {@code user.dir} 就是模块目录，但 IDE 里单跑本用例时它可能是仓库根或
             * {@code xsy-scm-server}。逐级向上找，找不到就失败 —— 静默返回一个不存在的目录会让
             * 上面的 {@code assertThat(javaRoot).exists()} 之外的断言全部空转通过。
             */
            private Path moduleRoot() {
                Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
                for (int depth = 0; depth < 4 && current != null; depth++) {
                    if (Files.isDirectory(current.resolve("src/main/java/com/xsy/scm/finance"))) {
                        return current;
                    }
                    current = current.getParent();
                }
                throw new IllegalStateException("找不到 xsy-admin 模块目录（user.dir = " + System.getProperty("user.dir") + "）");
            }
        }

        @Nested

        class FinanceReceivableDetailScopeTest {
            private final FinanceReceivableDao accounts = mock(FinanceReceivableDao.class);
            private final FinanceReceivableItemDao items = mock(FinanceReceivableItemDao.class);
            private final ScmDataScopeService scopes = mock(ScmDataScopeService.class);
            private final FinanceReceivableQueryService service = new FinanceReceivableQueryService(accounts, items,
                mock(FinanceWriteOffQueryService.class), mock(FinanceOperationLogQueryService.class), scopes);

            @Test
            void independentCreditUsesCustomerOwnerAndStillRejectsOtherOwners() {
                var record = new FinanceReceivableEntity();
                record.setId(31L);
                record.setCustomerId(9L);
                record.setEntryType("RED");
                var header = new FinanceReceivableVO();
                header.setReceivableId(31L);
                when(accounts.selectActiveById(31L)).thenReturn(record);
                when(accounts.selectSellerId(31L)).thenReturn(7L);
                when(accounts.selectSummaryById(31L)).thenReturn(header);
                var scope = mock(ScmDataScopeContext.class);
                when(scopes.resolve()).thenReturn(scope);
                when(scope.getOrderSellerScope()).thenReturn(ScmValueScope.of(List.of(7L)));
                assertThat(service.detail(31L).getReceivable()).isSameAs(header);

                when(scope.getOrderSellerScope()).thenReturn(ScmValueScope.of(List.of(8L)));
                assertThatThrownBy(() -> service.detail(31L)).isInstanceOf(ScmDataScopeException.class);
            }

            @Test
            void unassignedOwnerIsNotGrantedToRestrictedAccount() {
                var record = new FinanceReceivableEntity();
                record.setId(32L);
                when(accounts.selectActiveById(32L)).thenReturn(record);
                var scope = mock(ScmDataScopeContext.class);
                when(scopes.resolve()).thenReturn(scope);
                when(scope.getOrderSellerScope()).thenReturn(ScmValueScope.of(List.of(7L)));
                assertThatThrownBy(() -> service.detail(32L)).isInstanceOf(ScmDataScopeException.class);
                verify(accounts, never()).selectSummaryById(32L);
            }
        }

        @Nested

        class FinanceSettlementRuleServiceTest {
            private final FinanceSettlementRuleDao rules = mock(FinanceSettlementRuleDao.class);
            private final FinancePayableDao payables = mock(FinancePayableDao.class);
            private final FinanceSettlementRuleService service = new FinanceSettlementRuleService(rules, payables);

            @Test
            void dailyCreditUsesShanghaiBusinessDateAndFreezesRule() {
                FinanceCreditRuleDto rule = rule("DAY", 10, null);
                when(rules.selectCreditRule(1L)).thenReturn(rule);
                FinanceReceivableEntity row = receivable();
                service.freezeReceivable(row);
                assertThat(row.getDueDate()).isEqualTo(LocalDate.of(2026, 2, 11));
                assertThat(row.getCreditRuleSnapshot()).containsEntry("type", "BY_TIME").containsEntry("value", 10);
                rule.setValue(90);
                assertThat(row.getCreditRuleSnapshot()).containsEntry("value", 10);
            }

            @Test
            void monthlyCreditUsesTargetMonthAndSettlementDay() {
                when(rules.selectCreditRule(1L)).thenReturn(rule("MONTH", 1, 15));
                FinanceReceivableEntity row = receivable();
                service.freezeReceivable(row);
                assertThat(row.getDueDate()).isEqualTo(LocalDate.of(2026, 3, 15));
            }

            @Test
            void creditWithoutSettlementDayUsesFirstDayOfTargetMonth() {
                when(rules.selectCreditRule(1L)).thenReturn(rule("MONTH", 1, null));
                FinanceReceivableEntity row = receivable();
                service.freezeReceivable(row);
                assertThat(row.getDueDate()).isEqualTo(LocalDate.of(2026, 3, 1));
            }

            @Test
            void amountCreditHasSnapshotAndNoDueDate() {
                FinanceCreditRuleDto rule = new FinanceCreditRuleDto();
                rule.setType("BY_AMOUNT");
                when(rules.selectCreditRule(1L)).thenReturn(rule);
                FinanceReceivableEntity row = receivable();
                service.freezeReceivable(row);
                assertThat(row.getDueDate()).isNull();
                assertThat(row.getCreditRuleSnapshot()).containsEntry("type", "BY_AMOUNT");
            }

            @Test
            void payableFreezesSupplierPeriodOnShanghaiBusinessDate() {
                when(rules.selectPaymentDays(2L)).thenReturn(30);
                FinancePayableEntity row = payable();
                service.freezePayable(row);
                assertThat(row.getDueDate()).isEqualTo(LocalDate.of(2026, 3, 3));
                assertThat(row.getPaymentRuleSnapshot()).containsEntry("paymentPeriodDays", 30);
            }

            @Test
            void redPayableInheritsOriginalInsteadOfCurrentSupplierRule() {
                FinancePayableEntity original = payable();
                original.setDueDate(LocalDate.of(2026, 2, 20));
                original.setPaymentRuleSnapshot(Map.of("paymentPeriodDays", 19));
                when(payables.selectById(7L)).thenReturn(original);
                FinancePayableEntity row = payable();
                row.setEntryType("RED");
                row.setOriginalPayableId(7L);
                service.freezePayable(row);
                assertThat(row.getDueDate()).isEqualTo(original.getDueDate());
                assertThat(row.getPaymentRuleSnapshot()).isEqualTo(original.getPaymentRuleSnapshot());
                verifyNoInteractions(rules);
            }

            @Test
            void missingOriginalAndInvalidPeriodFailBeforeWriting() {
                FinancePayableEntity red = payable();
                red.setEntryType("RED");
                red.setOriginalPayableId(99L);
                assertThatThrownBy(() -> service.freezePayable(red)).isInstanceOf(IllegalStateException.class);
                when(rules.selectCreditRule(1L)).thenReturn(rule("DAY", -1, null));
                assertThatThrownBy(() -> service.freezeReceivable(receivable())).isInstanceOf(IllegalStateException.class);
            }

            private FinanceCreditRuleDto rule(String unit, int value, Integer day) {
                FinanceCreditRuleDto rule = new FinanceCreditRuleDto();
                rule.setType("BY_TIME");
                rule.setUnit(unit);
                rule.setValue(value);
                rule.setSettleDay(day);
                return rule;
            }

            private FinanceReceivableEntity receivable() {
                FinanceReceivableEntity row = new FinanceReceivableEntity();
                row.setSettlementCustomerId(1L);
                row.setEventAt(OffsetDateTime.parse("2026-01-31T20:00:00Z"));
                return row;
            }

            private FinancePayableEntity payable() {
                FinancePayableEntity row = new FinancePayableEntity();
                row.setSupplierId(2L);
                row.setEntryType("NORMAL");
                row.setEventAt(OffsetDateTime.parse("2026-01-31T20:00:00Z"));
                return row;
            }
        }

        @Nested
        @DisplayName("抹零：写成 WAIVER 核销行、金额不超未结、反向只能新增")
        class FinanceWaiverServiceTest {
            private final FinanceWaiverDao waivers = mock(FinanceWaiverDao.class);
            private final FinanceReceivableDao receivables = mock(FinanceReceivableDao.class);
            private final FinanceWriteOffDao writeOffs = mock(FinanceWriteOffDao.class);
            private final FinanceOperationLogRecorder logs = mock(FinanceOperationLogRecorder.class);
            private final ScmDataScopeService scopes = mock(ScmDataScopeService.class);
            private final ScmIdempotencyService idempotency = mock(ScmIdempotencyService.class);
            private final FinanceWaiverService service = new FinanceWaiverService(waivers, receivables, writeOffs, logs, scopes,
                idempotency);

            private final ScmDataScopeContext scope = mock(ScmDataScopeContext.class);
            private final ScmIdempotencyService.Claim claim = new ScmIdempotencyService.Claim(null, false);
            private final FinanceWaiverAddForm addForm = new FinanceWaiverAddForm();
            private final FinanceWaiverReverseForm reverseForm = new FinanceWaiverReverseForm();

            @BeforeEach
            void floatingOpenAmount() {
                addForm.setReceivableId(50L);
                addForm.setAmount("0.3000");
                addForm.setReason("对账抹零");
                reverseForm.setWaiverId(5L);
                reverseForm.setReason("客户要求补收");

                when(scopes.resolve()).thenReturn(scope);
                when(scope.getOrderSellerScope()).thenReturn(ScmValueScope.all());
                var target = new FinanceReceivableTargetDto();
                target.setReceivableId(50L);
                target.setReceivableNo("AR50");
                target.setCustomerId(2L);
                target.setSettlementCustomerId(3L);
                target.setCustomerNameSnapshot("客户甲");
                target.setEntryType("NORMAL");
                target.setAmount(new BigDecimal("100.3000"));
                target.setSellerId(7L);
                when(receivables.selectNormalTargetForUpdate(50L)).thenReturn(target);
                when(receivables.selectRedAmount(50L)).thenReturn(BigDecimal.ZERO);
                when(writeOffs.selectTargetWrittenOffAmount("RECEIVABLE", 50L)).thenReturn(new BigDecimal("100"));
                when(idempotency.claim(any(), eq("key"), any())).thenReturn(claim);
            }

            @Test
            @DisplayName("抹零 0.30：写一条 WAIVER 核销行冲减应收，抹零表只存金额与原因")
            void addWritesWaiverAndWriteOff() {
                when(waivers.nextWaiverNo()).thenReturn(1L);
                when(waivers.insert(any(FinanceWaiverEntity.class))).thenAnswer(invocation -> {
                    FinanceWaiverEntity row = invocation.getArgument(0);
                    row.setId(5L);
                    return 1;
                });
                when(writeOffs.nextWriteOffNo()).thenReturn(3L);
                when(writeOffs.insert(any(FinanceWriteOffEntity.class))).thenAnswer(invocation -> {
                    FinanceWriteOffEntity row = invocation.getArgument(0);
                    row.setId(8L);
                    return 1;
                });

                FinanceWaiverVO result;
                try (MockedStatic<ScmOperator> operator = mockStatic(ScmOperator.class)) {
                    operator.when(ScmOperator::current).thenReturn("1:1");
                    result = service.add(addForm, "key");
                }

                assertThat(result.getStatus()).isEqualTo("NORMAL");
                assertThat(result.getAmount()).isEqualByComparingTo("0.30");
                var waiver = ArgumentCaptor.forClass(FinanceWaiverEntity.class);
                verify(waivers).insert(waiver.capture());
                assertThat(waiver.getValue().getReceivableId()).isEqualTo(50L);
                assertThat(waiver.getValue().getReason()).isEqualTo("对账抹零");

                var writeOff = ArgumentCaptor.forClass(FinanceWriteOffEntity.class);
                verify(writeOffs).insert(writeOff.capture());
                assertThat(writeOff.getValue().getSourceType()).isEqualTo("WAIVER");
                assertThat(writeOff.getValue().getSourceId()).isEqualTo(5L);
                assertThat(writeOff.getValue().getTargetType()).isEqualTo("RECEIVABLE");
                assertThat(writeOff.getValue().getAmount()).isEqualByComparingTo("0.30");
                assertThat(writeOff.getValue().getEntryType()).isEqualTo("NORMAL");
            }

            @Test
            @DisplayName("抹零金额超过未结 0.30 时拒绝，不写任何记录")
            void addExceedingOpenAmountRejected() {
                when(writeOffs.selectTargetWrittenOffAmount("RECEIVABLE", 50L)).thenReturn(new BigDecimal("100.2000"));

                assertThatThrownBy(() -> service.add(addForm, "key"))
                    .isInstanceOfSatisfying(ScmBusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(FinanceErrorCode.WAIVER_AMOUNT_EXCEEDED));
                verify(waivers, never()).insert(any(FinanceWaiverEntity.class));
                verifyNoInteractions(logs);
            }

            @Test
            @DisplayName("没有原因的抹零直接判非法，不进入余额计算")
            void addWithoutReasonRejected() {
                addForm.setReason("   ");

                assertThatThrownBy(() -> service.add(addForm, "key"))
                    .isInstanceOfSatisfying(ScmBusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(FinanceErrorCode.WAIVER_RECEIVABLE_INVALID));
                verifyNoInteractions(waivers, writeOffs);
            }

            @Test
            @DisplayName("反向抹零追加 REVERSE 核销行，状态由反向行推导")
            void reverseAppendsReverseWriteOff() {
                var waiver = new FinanceWaiverEntity();
                waiver.setId(5L);
                waiver.setWaiverNo("WV5");
                waiver.setReceivableId(50L);
                waiver.setCustomerId(2L);
                waiver.setSettlementCustomerId(3L);
                waiver.setAmount(new BigDecimal("0.3000"));
                waiver.setReason("对账抹零");
                when(waivers.selectById(5L)).thenReturn(waiver);
                when(waivers.selectByIdForUpdate(5L)).thenReturn(waiver);

                var normal = new FinanceWriteOffEntity();
                normal.setId(8L);
                normal.setWriteOffNo("WO8");
                normal.setSourceType("WAIVER");
                normal.setSourceId(5L);
                normal.setTargetType("RECEIVABLE");
                normal.setTargetId(50L);
                normal.setAmount(new BigDecimal("0.3000"));
                normal.setEntryType("NORMAL");
                when(writeOffs.selectNormalAllocation("WAIVER", 5L, null)).thenReturn(normal);
                when(writeOffs.hasReversal(8L)).thenReturn(false);
                when(writeOffs.nextWriteOffNo()).thenReturn(4L);
                when(writeOffs.insertReverseOnConflictDoNothing(any(FinanceWriteOffEntity.class))).thenAnswer(invocation -> {
                    FinanceWriteOffEntity row = invocation.getArgument(0);
                    row.setId(9L);
                    return 1;
                });
                when(writeOffs.selectTargetWrittenOffAmount("RECEIVABLE", 50L)).thenReturn(new BigDecimal("100.3000"));

                FinanceWaiverVO result;
                try (MockedStatic<ScmOperator> operator = mockStatic(ScmOperator.class)) {
                    operator.when(ScmOperator::current).thenReturn("1:1");
                    result = service.reverse(reverseForm, "key");
                }

                assertThat(result.getStatus()).isEqualTo("REVERSED");
                assertThat(result.getReverseReason()).isEqualTo("客户要求补收");
                var reversal = ArgumentCaptor.forClass(FinanceWriteOffEntity.class);
                verify(writeOffs).insertReverseOnConflictDoNothing(reversal.capture());
                assertThat(reversal.getValue().getEntryType()).isEqualTo("REVERSE");
                assertThat(reversal.getValue().getReverseOfId()).isEqualTo(8L);
                assertThat(reversal.getValue().getReason()).isEqualTo("客户要求补收");
                // 抹零表不留状态：没有更新，只有插入
                verify(waivers, never()).updateById(any(FinanceWaiverEntity.class));
            }

            @Test
            @DisplayName("已反向的抹零不能再次反向")
            void reverseTwiceRejected() {
                var waiver = new FinanceWaiverEntity();
                waiver.setId(5L);
                waiver.setReceivableId(50L);
                when(waivers.selectById(5L)).thenReturn(waiver);
                when(waivers.selectByIdForUpdate(5L)).thenReturn(waiver);
                var normal = new FinanceWriteOffEntity();
                normal.setId(8L);
                normal.setEntryType("NORMAL");
                when(writeOffs.selectNormalAllocation("WAIVER", 5L, null)).thenReturn(normal);
                when(writeOffs.hasReversal(8L)).thenReturn(true);

                assertThatThrownBy(() -> service.reverse(reverseForm, "key"))
                    .isInstanceOfSatisfying(ScmBusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(FinanceErrorCode.WAIVER_ALREADY_REVERSED));
                verify(writeOffs, never()).insertReverseOnConflictDoNothing(any(FinanceWriteOffEntity.class));
            }
        }

        @Nested
        @DisplayName("核销自动 FIFO：按账单日期分配、不超额、剩余留待下次")
        class FinanceWriteOffAutoTest {
            private final FinanceWriteOffDao writeOffs = mock(FinanceWriteOffDao.class);
            private final FinanceOrderFundingSourceDao fundingSources = mock(FinanceOrderFundingSourceDao.class);
            private final FinanceCounterpartySourceDao counterparties = mock(FinanceCounterpartySourceDao.class);
            private final FinanceReceiptDao receipts = mock(FinanceReceiptDao.class);
            private final FinancePaymentDao payments = mock(FinancePaymentDao.class);
            private final FinanceReceivableDao receivables = mock(FinanceReceivableDao.class);
            private final FinancePayableDao payables = mock(FinancePayableDao.class);
            private final FinanceOperationLogRecorder logs = mock(FinanceOperationLogRecorder.class);
            private final ScmDataScopeService scopes = mock(ScmDataScopeService.class);
            private final ScmIdempotencyService idempotency = mock(ScmIdempotencyService.class);
            private final FinanceWriteOffService service = new FinanceWriteOffService(writeOffs, fundingSources,
                counterparties, receipts, payments, receivables, payables, logs, scopes, idempotency);

            private final ScmDataScopeContext scope = mock(ScmDataScopeContext.class);
            private final ScmIdempotencyService.Claim claim = new ScmIdempotencyService.Claim(null, false);
            private final FinanceWriteOffAutoForm form = new FinanceWriteOffAutoForm();

            @BeforeEach
            void receiptOf100() {
                form.setSourceType("RECEIPT");
                form.setSourceId(9L);
                when(scopes.resolve()).thenReturn(scope);
                when(scope.getCustomerSellerScope()).thenReturn(ScmValueScope.all());
                when(scope.getOrderSellerScope()).thenReturn(ScmValueScope.all());
                when(scope.getPurchaserScope()).thenReturn(ScmValueScope.all());

                var receipt = new FinanceReceiptEntity();
                receipt.setId(9L);
                receipt.setReceiptNo("RC9");
                receipt.setCustomerId(2L);
                receipt.setCustomerNameSnapshot("客户甲");
                receipt.setSettlementCustomerId(3L);
                receipt.setAmount(new BigDecimal("100"));
                receipt.setEntryType("NORMAL");
                when(receipts.selectByIdForUpdate(9L)).thenReturn(receipt);
                when(receipts.selectReversedAmount(9L)).thenReturn(BigDecimal.ZERO);
                when(fundingSources.isRechargeReceipt(9L)).thenReturn(false);

                var customer = new FinanceCustomerFactDto();
                customer.setCustomerId(2L);
                customer.setSellerId(7L);
                when(counterparties.selectCustomer(2L)).thenReturn(customer);

                when(writeOffs.selectSourceUsedAmount("RECEIPT", 9L)).thenReturn(BigDecimal.ZERO);
                when(idempotency.claim(any(), eq("key"), any())).thenReturn(claim);
            }

            @Test
            void groupedCustomerPreviewAndCommitUseTheTargetsEffectiveBalance() {
                when(receivables.selectAutoTargets(eq(3L), any(), anyInt()))
                    .thenReturn(List.of(candidate(11L, "80")));
                when(receivables.selectNormalTargetsForUpdate(List.of(11L))).thenReturn(List.of(locked(11L, "100")));
                when(receivables.selectRedAmount(11L)).thenReturn(BigDecimal.ZERO);
                when(writeOffs.selectTargetWrittenOffAmount("RECEIVABLE", 11L)).thenReturn(BigDecimal.ZERO);
                var credit = new FinanceReceivableCreditContextDto();
                credit.setPoolAmount(new BigDecimal("20"));
                credit.setPriorOpenAmount(BigDecimal.ZERO);
                when(receivables.selectCreditContextForTarget(11L)).thenReturn(credit);
                // 集团自身的扣减不能再次减到子客户的已计算余额上。
                when(receivables.selectCustomerLevelRedAmount(3L, 3L)).thenReturn(new BigDecimal("10"));
                when(writeOffs.insert(any(FinanceWriteOffEntity.class))).thenReturn(1);
                var view = new com.xsy.scm.finance.domain.vo.FinanceReceiptQueryVO();
                view.setReceiptId(9L);
                view.setCustomerId(2L);
                view.setSettlementCustomerId(3L);
                view.setEntryType("NORMAL");
                view.setEffectiveAmount(new BigDecimal("100"));
                when(receipts.selectQueryById(9L)).thenReturn(view);
                assertThat(service.autoPreview(form).getFirst().getAllocatedAmount()).isEqualByComparingTo("80");
                try (MockedStatic<ScmOperator> operator = mockStatic(ScmOperator.class)) {
                    operator.when(ScmOperator::current).thenReturn("operator");
                    assertThat(service.autoAdd(form, "key").getItems().getFirst().getAmount()).isEqualByComparingTo("80");
                }
            }

            @Test
            @DisplayName("100 元收款先结清 60 元旧账，再把剩余 40 元结到第二张单")
            void fillsOldestTargetsFirstAndCarriesTheRest() {
                when(receivables.selectAutoTargets(eq(3L), any(), anyInt()))
                    .thenReturn(List.of(candidate(11L, "60"), candidate(12L, "80")));
                when(receivables.selectNormalTargetsForUpdate(List.of(11L, 12L)))
                    .thenReturn(List.of(locked(11L, "60"), locked(12L, "80")));
                when(receivables.selectRedAmount(anyLong())).thenReturn(BigDecimal.ZERO);
                when(writeOffs.selectTargetWrittenOffAmount(eq("RECEIVABLE"), anyLong())).thenReturn(BigDecimal.ZERO);
                when(writeOffs.nextWriteOffNo()).thenReturn(1L, 2L);
                var sequence = new java.util.concurrent.atomic.AtomicLong(1);
                when(writeOffs.insert(any(FinanceWriteOffEntity.class))).thenAnswer(invocation -> {
                    FinanceWriteOffEntity row = invocation.getArgument(0);
                    row.setId(sequence.getAndIncrement());
                    return 1;
                });

                try (MockedStatic<ScmOperator> operator = mockStatic(ScmOperator.class)) {
                    operator.when(ScmOperator::current).thenReturn("1:1");
                    var result = service.autoAdd(form, "key");
                    assertThat(result.getItems()).hasSize(2);
                }

                var captured = ArgumentCaptor.forClass(FinanceWriteOffEntity.class);
                verify(writeOffs, org.mockito.Mockito.times(2)).insert(captured.capture());
                assertThat(captured.getAllValues().stream().map(FinanceWriteOffEntity::getTargetId).toList())
                    .containsExactly(11L, 12L);
                assertThat(captured.getAllValues().stream().map(FinanceWriteOffEntity::getAmount).toList())
                    .containsExactly(new BigDecimal("60.0000"), new BigDecimal("40.0000"));
                // 防重复处理 scope 与手工核销分开，避免同一收款两个入口互相重放
                verify(idempotency).claim(eq("FINANCE_WRITE_OFF_AUTO:RECEIPT:9"), eq("key"), any());
            }

            @Test
            @DisplayName("预览只读：给出顺序与金额，不写核销、不取号")
            void previewNeverWrites() {
                var view = new com.xsy.scm.finance.domain.vo.FinanceReceiptQueryVO();
                view.setReceiptId(9L);
                view.setReceiptNo("RC9");
                view.setCustomerId(2L);
                view.setCustomerName("客户甲");
                view.setSettlementCustomerId(3L);
                view.setEntryType("NORMAL");
                view.setEffectiveAmount(new BigDecimal("100"));
                when(receipts.selectQueryById(9L)).thenReturn(view);
                when(receivables.selectAutoTargets(eq(3L), any(), anyInt()))
                    .thenReturn(List.of(candidate(11L, "60"), candidate(12L, "80")));

                List<FinanceWriteOffAutoPlanVO> plan = service.autoPreview(form);

                assertThat(plan).extracting(FinanceWriteOffAutoPlanVO::getTargetId).containsExactly(11L, 12L);
                assertThat(plan).extracting(FinanceWriteOffAutoPlanVO::getAllocatedAmount)
                    .containsExactly(new BigDecimal("60.0000"), new BigDecimal("40.0000"));
                verify(writeOffs, never()).insert(any(FinanceWriteOffEntity.class));
                verifyNoInteractions(logs, idempotency);
            }

            @Test
            @DisplayName("对方没有未结单据时返回 41164，不写空核销")
            void emptyCandidatesRejected() {
                when(receivables.selectAutoTargets(eq(3L), any(), anyInt())).thenReturn(List.of());

                assertThatThrownBy(() -> service.autoAdd(form, "key"))
                    .isInstanceOfSatisfying(ScmBusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(FinanceErrorCode.AUTO_WRITE_OFF_NO_OPEN_TARGET));
                verify(writeOffs, never()).insert(any(FinanceWriteOffEntity.class));
            }

            @Test
            @DisplayName("客户退款付款没有可冲的应付：不查应付、不写核销")
            void customerPaymentHasNoPayableTargets() {
                form.setSourceType("PAYMENT");
                var payment = new FinancePaymentEntity();
                payment.setId(5L);
                payment.setPaymentNo("PM5");
                payment.setCounterpartyType("CUSTOMER");
                payment.setCounterpartyId(2L);
                payment.setAmount(new BigDecimal("30"));
                payment.setEntryType("NORMAL");
                when(payments.selectByIdForUpdate(5L)).thenReturn(payment);
                when(payments.selectReversedAmount(5L)).thenReturn(BigDecimal.ZERO);
                when(writeOffs.selectSourceUsedAmount("PAYMENT", 5L)).thenReturn(BigDecimal.ZERO);
                form.setSourceId(5L);

                assertThatThrownBy(() -> service.autoAdd(form, "key"))
                    .isInstanceOfSatisfying(ScmBusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(FinanceErrorCode.AUTO_WRITE_OFF_NO_OPEN_TARGET));
                verify(payables, never()).selectAutoTargets(anyLong(), any(), anyInt());
            }

            private static FinanceAutoWriteOffTargetDto candidate(Long id, String openAmount) {
                var row = new FinanceAutoWriteOffTargetDto();
                row.setTargetId(id);
                row.setTargetNo("AR" + id);
                row.setTargetName("客户甲");
                row.setEventAt(OffsetDateTime.parse("2026-10-01T00:00:00+08:00"));
                row.setOpenAmount(new BigDecimal(openAmount));
                row.setOwnerId(7L);
                return row;
            }

            private static FinanceReceivableTargetDto locked(Long id, String openAmount) {
                var row = new FinanceReceivableTargetDto();
                row.setReceivableId(id);
                row.setReceivableNo("AR" + id);
                row.setCustomerId(2L);
                row.setSettlementCustomerId(3L);
                row.setCustomerNameSnapshot("客户甲");
                row.setEntryType("NORMAL");
                row.setAmount(new BigDecimal(openAmount));
                row.setSellerId(7L);
                return row;
            }
        }
    }

    @Nested
    class FinanceW5PgIT extends ScmW5PgITBase {
        @Nested

        class AdminSettlementTermsPgIT {
            @Autowired private FinanceReceiptService financeReceiptService;
            @Autowired private FinanceWriteOffService financeWriteOffService;
            @Autowired private FinanceSettlementRuleService settlementRuleService;
            @Autowired private com.xsy.scm.order.service.OrderCreditService orderCreditService;

            private String key() { return UUID.randomUUID().toString(); }

            private Long groupCustomer(Long parent) {
                CustomerAddForm form = new CustomerAddForm();
                form.setName("集团结算验收");
                form.setSettleMode("GROUP");
                form.setSellerId(1L);
                form.setParentCustomerId(parent);
                form.setSettlementCustomerId(parent);
                Long id = customerService.add(form);
                CustomerStatusForm status = new CustomerStatusForm();
                status.setCustomerId(id);
                status.setVersion(customerService.require(id).getVersion());
                status.setStatus("COOPERATING");
                customerService.updateStatus(status);
                return id;
            }

            private Long receivable(Long customerId, String eventAt) {
                Long skuId = newOnShelfSku(key().substring(0, 4));
                Long orderId = confirmedSalesOrder(customerId, skuId, "1.0000", "1.0000");
                // 账期由服务层按结算主体求值；这里只把结果随源单快照写入，验证保存后不再回算。
                Long settlementCustomerId = jdbc.queryForObject(
                        "SELECT settlement_customer_id FROM xsy_sales_order WHERE id=?", Long.class, orderId);
                var probe = new FinanceReceivableEntity();
                probe.setSettlementCustomerId(settlementCustomerId);
                probe.setEntryType("NORMAL");
                probe.setEventAt(OffsetDateTime.parse(eventAt));
                settlementRuleService.freezeReceivable(probe);
                return jdbc.queryForObject("INSERT INTO xsy_finance_receivable (receivable_no,source_type,source_id,order_id,"
                        + "customer_id,customer_name_snapshot,settlement_customer_id,settlement_customer_name_snapshot,entry_type,amount,event_at,due_date)"
                        + " SELECT ?, 'SALES_ORDER', id,id,customer_id,customer_name_snapshot,settlement_customer_id,"
                        + "settlement_customer_name_snapshot,'NORMAL',10,?::timestamptz,? FROM xsy_sales_order WHERE id=? RETURNING id",
                        Long.class, "AR-" + key(), eventAt, probe.getDueDate(), orderId);
            }

            private Long receipt(Long customerId) {
                FinanceReceiptAddForm form = new FinanceReceiptAddForm();
                form.setCustomerId(customerId);
                form.setAmount("10.0000");
                form.setMethod("BANK_TRANSFER");
                form.setReceivedAt(OffsetDateTime.now());
                return financeReceiptService.add(form, key()).getReceiptId();
            }

            private FinanceWriteOffAddForm allocation(Long receiptId, Long receivableId) {
                FinanceWriteOffAddItemForm item = new FinanceWriteOffAddItemForm();
                item.setTargetId(receivableId);
                item.setAmount("10.0000");
                FinanceWriteOffAddForm form = new FinanceWriteOffAddForm();
                form.setSourceType("RECEIPT");
                form.setSourceId(receiptId);
                form.setItems(List.of(item));
                return form;
            }

            @Test void sameSettlementAllowsSiblingAllocationAndReleasesCredit() {
                Long parent = groupCustomer(null);
                Long payer = groupCustomer(parent);
                Long debtor = groupCustomer(parent);
                Long receivable = receivable(debtor, OffsetDateTime.now().toString());
                Long receipt = receipt(payer);
                var form = allocation(receipt, receivable);
                String key = key();
                var result = financeWriteOffService.add(form, key);
                assertThat(financeWriteOffService.add(form, key).getItems().getFirst().getWriteOffId())
                        .isEqualTo(result.getItems().getFirst().getWriteOffId());
                assertThat(orderCreditService.check(parent, BigDecimal.ZERO).getOpenReceivableAmount()).isEqualByComparingTo("0");
                assertThat(jdbc.queryForObject("SELECT customer_id FROM xsy_finance_receipt WHERE id=?",Long.class,receipt))
                        .as("收款保留实际付款客户").isEqualTo(payer);
                jdbc.update("UPDATE xsy_customer SET settlement_customer_id=id WHERE id=?", payer);
                assertThat(jdbc.queryForObject("SELECT settlement_customer_id FROM xsy_finance_receipt WHERE id=?",Long.class,receipt))
                        .as("统一收款主体保存为集团结算客户").isEqualTo(parent);
            }

            @Test void siblingCannotBeConfiguredAsSettlementCustomer() {
                Long parent = groupCustomer(null);
                Long sibling = groupCustomer(parent);
                CustomerAddForm form = new CustomerAddForm();
                form.setName("非法兄弟结算客户");
                form.setSettleMode("GROUP");
                form.setParentCustomerId(parent);
                form.setSettlementCustomerId(sibling);
                expectCode(() -> customerService.add(form), 40032);
            }

            @Test void parentCustomerActsAsSettlementCustomerWhenModeAndRelationshipAreValid() {
                Long parent = newCustomer();
                Long child = newCustomer();
                jdbc.update("UPDATE xsy_customer SET settle_mode='GROUP',parent_customer_id=?,settlement_customer_id=? WHERE id=?",
                        parent, parent, child);
                jdbc.update("UPDATE xsy_customer SET settle_mode='GROUP' WHERE id=?", parent);
                evictMybatisCache();
                // 客户不再区分类型：只要「直接上级 + 双方 GROUP + 上级自身结算」成立，上级就能作为结算主体。
                assertThatCode(() -> receipt(child)).doesNotThrowAnyException();
            }

            @Test void sourceCustomerScopeCannotBeReplacedByGroupRelationship() {
                Long parent = groupCustomer(null);
                Long payer = groupCustomer(parent);
                Long debtor = groupCustomer(parent);
                Long receivable = receivable(debtor, OffsetDateTime.now().toString());
                Long receipt = receipt(payer);
                jdbc.update("UPDATE xsy_customer SET seller_id=987654321 WHERE id=?", payer);
                evictMybatisCache();
                RequestEmployee employee = new RequestEmployee();
                employee.setEmployeeId(1L);
                employee.setAdministratorFlag(false);
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                XsyRequestUtil.setRequestUser(employee);
                assertThatThrownBy(() -> financeWriteOffService.add(allocation(receipt, receivable), key()))
                        .isInstanceOf(ScmDataScopeException.class);
            }

            @Test void dueDateFreezesAtShanghaiEventDateAndDoesNotFollowMasterEdits() {
                Long customer = newCustomer();
                jdbc.update("UPDATE xsy_customer SET credit_period_type='BY_TIME',credit_period_value=10,credit_period_unit='DAY' WHERE id=?",customer);
                evictMybatisCache();
                Long receivable = receivable(customer, "2026-01-10T20:00:00Z");
                assertThat(jdbc.queryForObject("SELECT due_date FROM xsy_finance_receivable WHERE id=?",LocalDate.class,receivable))
                        .isEqualTo(LocalDate.of(2026,1,21));
                jdbc.update("UPDATE xsy_customer SET credit_period_value=30 WHERE id=?",customer);
                evictMybatisCache();
                assertThat(jdbc.queryForObject("SELECT due_date FROM xsy_finance_receivable WHERE id=?",LocalDate.class,receivable))
                        .isEqualTo(LocalDate.of(2026,1,21));
                assertThat(orderCreditService.check(customer, BigDecimal.ZERO).getOverdue()).isTrue();
            }
        }

        @Nested
        @DisplayName("Customer credit exposure seller-scope SQL (PG IT)")
        class CustomerCreditScopePgIT {
            @Autowired
            private FinanceCreditExposureQueryService exposureQueryService;

            @Test
            @DisplayName("非空 seller 范围绑定 foreach、按订单归属判定可见性；占用金额由服务层按净额补齐")
            void nonEmptySellerScopeBindsIdsAndFiltersConfirmedOrders() {
                Long customerId = newCustomer();
                Long skuId = newOnShelfSku(prefix);
                confirmedSalesOrder(customerId, skuId, "2.0000", "2.0000");
                Long sellerId = customerService.require(customerId).getSellerId();

                // 范围不含该订单的负责人：可见性为 false，调用方据此校验失败时拒绝操作，不会拿残缺敞口做授信裁定
                var outsideScope = exposureQueryService.summarize(customerId, ScmValueScope.of(List.of(Long.MAX_VALUE)));
                assertThat(outsideScope.getExposureVisible()).isFalse();
                assertThat(outsideScope.getConfirmedOrderAmount())
                        .as("占用金额由服务层按正式净额补齐，不再由这条 SQL 汇总")
                        .isGreaterThan(BigDecimal.ZERO);

                var owningScope = exposureQueryService.summarize(customerId, ScmValueScope.of(List.of(sellerId)));
                assertThat(owningScope.getExposureVisible()).isTrue();
                assertThat(owningScope.getConfirmedOrderAmount()).isGreaterThan(BigDecimal.ZERO);
            }
        }

        @Nested
        @DisplayName("货到付款收款确认（PAY-03，PG IT）")
        class FinanceCodCollectionPgIT {
            private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

            /** 下单 2.0000 × 客户价 1.2000 = 应收 2.4000。 */
            private static final BigDecimal RECEIVABLE_AMOUNT = new BigDecimal("2.4000");

            @Autowired
            private FinanceReceiptService financeReceiptService;

            @Autowired
            private FinanceReceiptDao financeReceiptDao;

            @Test
            @DisplayName("已签收的 COD 订单：登记实收并核销应收，分笔来源与订单归属分别记录")
            void collectedCodIsRecordedWithItsOrderAsSourceAndWrittenOff() {
                CodOrder order = signedCodOrder("ok");

                FinanceReceiptVO receipt = collect(order, RECEIVABLE_AMOUNT.toPlainString(), "CASH", "ok");

                assertThat(jdbc.queryForObject("SELECT source_type FROM xsy_finance_receipt WHERE id = ?", String.class,
                        receipt.getReceiptId())).isEqualTo("ORDER_COD_INSTALLMENT");
                assertThat(jdbc.queryForObject("SELECT cod_order_id FROM xsy_finance_receipt WHERE id = ?", Long.class,
                        receipt.getReceiptId())).as("每笔收款都能追到订单").isEqualTo(order.orderId());
                assertThat(receipt.getMethod()).as("方式是真实资金形式，不是 COD 本身").isEqualTo("CASH");
                assertThat(receipt.getAmount()).isEqualByComparingTo(RECEIVABLE_AMOUNT);
                assertThat(receipt.getSettlementCustomerId()).isEqualTo(order.customerId());

                assertThat(writeOffCount(receipt.getReceiptId(), order.receivableId())).isEqualTo(1);
                assertThat(openAmount(order.receivableId())).as("收清后应收无未结余额").isEqualByComparingTo("0.0000");
            }

            @Test
            @DisplayName("少收后可按订单继续分笔补收，合计核销到零")
            void partialCollectionLeavesTheRemainderOpen() {
                CodOrder order = signedCodOrder("partial");

                FinanceReceiptVO receipt = collect(order, "1.0000", "BANK_TRANSFER", "partial");

                assertThat(receipt.getAmount()).isEqualByComparingTo("1.0000");
                assertThat(writeOffCount(receipt.getReceiptId(), order.receivableId())).isEqualTo(1);
                assertThat(openAmount(order.receivableId())).as("剩余 1.4000 仍未结").isEqualByComparingTo("1.4000");
                FinanceReceiptVO supplement = collect(order, "1.4000", "CASH", "partial-supplement");
                assertThat(supplement.getReceiptId()).isNotEqualTo(receipt.getReceiptId());
                assertThat(receiptCount(order.orderId())).isEqualTo(2);
                assertThat(financeReceiptDao.sumCodCollectedByOrder(order.orderId()))
                        .isEqualByComparingTo(RECEIVABLE_AMOUNT);
                assertThat(openAmount(order.receivableId())).isEqualByComparingTo("0.0000");
            }

            @Test
            @DisplayName("客户后来改为集团结算，COD 收款仍沿用订单应收保存的原结算主体")
            void collectionUsesFrozenCounterpartyAfterCustomerRelationshipChanges() {
                CodOrder order = signedCodOrder("frozen");
                Long newSettlementCustomerId = newCustomer();
                jdbc.update("UPDATE xsy_customer SET settlement_customer_id = ?, settle_mode = 'GROUP' WHERE id = ?",
                        newSettlementCustomerId, order.customerId());
                evictMybatisCache();

                FinanceReceiptVO receipt = collect(order, "1.0000", "CASH", "frozen");
                assertThat(receipt.getSettlementCustomerId()).isEqualTo(order.customerId());
                assertThat(writeOffCount(receipt.getReceiptId(), order.receivableId())).isEqualTo(1);
            }

            @Test
            @DisplayName("V131 旧单笔收款与 V132 新分笔收款可在同一订单上接续核销")
            void legacyCollectionCanBeFollowedByANewInstallment() {
                CodOrder order = signedCodOrder("legacy");
                Long legacyReceiptId = jdbc.queryForObject("""
                        INSERT INTO xsy_finance_receipt (receipt_no, customer_id, customer_name_snapshot,
                            settlement_customer_id, settlement_customer_name_snapshot, amount, method, received_at,
                            entry_type, source_type, source_id)
                        SELECT ?, customer_id, customer_name_snapshot, settlement_customer_id,
                            settlement_customer_name_snapshot, 1.0000, 'CASH', now(), 'NORMAL',
                            'ORDER_COD_COLLECTION', order_id
                        FROM xsy_finance_receivable WHERE id = ? RETURNING id
                        """, Long.class, "RC-LEGACY-" + UUID.randomUUID(), order.receivableId());
                jdbc.update("""
                        INSERT INTO xsy_finance_write_off (write_off_no, source_type, source_id, target_type,
                            target_id, amount, entry_type, written_off_at, operator)
                        VALUES (?, 'RECEIPT', ?, 'RECEIVABLE', ?, 1.0000, 'NORMAL', now(), ?)
                        """, "WO-LEGACY-" + UUID.randomUUID(), legacyReceiptId, order.receivableId(), ScmOperator.current());
                evictMybatisCache();

                FinanceReceiptVO installment = collect(order, "1.4000", "BANK_TRANSFER", "legacy-next");
                assertThat(installment.getReceiptId()).isNotEqualTo(legacyReceiptId);
                assertThat(receiptCount(order.orderId())).isEqualTo(2);
                assertThat(financeReceiptDao.sumCodCollectedByOrder(order.orderId()))
                        .isEqualByComparingTo(RECEIVABLE_AMOUNT);
                assertThat(openAmount(order.receivableId())).isEqualByComparingTo("0.0000");
            }

            @Test
            @Transactional(propagation = Propagation.NOT_SUPPORTED)
            @DisplayName("多收被拒，且整笔回滚：不留下一张收不到对应应收的收款单")
            void overCollectionIsRejectedAndRollsBackTheReceipt() {
                // 「整笔回滚」只在真提交时暴露：测试外层事务会把失败行留着等回滚，断言就没有意义。
                CodOrder order = signedCodOrder("over");

                assertThatThrownBy(() -> collect(order, "3.0000", "CASH", "over"))
                        .isInstanceOfSatisfying(ScmBusinessException.class, failure -> assertThat(failure.getErrorCode())
                                .isEqualTo(FinanceErrorCode.WRITE_OFF_AMOUNT_EXCEEDED));

                assertThat(receiptCount(order.orderId())).as("收款与核销同事务，核销不过就不该留下收款").isZero();
                assertThat(openAmount(order.receivableId())).isEqualByComparingTo(RECEIVABLE_AMOUNT);
            }

            @Test
            @Transactional(propagation = Propagation.NOT_SUPPORTED)
            @DisplayName("同键重放首次结果，收清后换键再收仍被核销上限拒绝")
            void replayAndFullySettledOrderDoNotCreateAnotherReceipt() {
                // 失败后检查库中没有第二张收款单，需要真事务观察整笔回滚。
                CodOrder order = signedCodOrder("dup");
                String key = prefix + ":cod-dup";
                // 防重复处理指纹包含请求内容，因此重放必须复用同一个表单对象，否则会被判成「同键异内容」
                FinanceCodCollectionForm request = form(order, "2.4000", "CASH");
                FinanceReceiptVO first = financeReceiptService.collectCod(request, key);

                FinanceReceiptVO replayed = financeReceiptService.collectCod(request, key);
                assertThat(replayed.getReceiptId()).isEqualTo(first.getReceiptId());

                // 换一个防重复请求编号再收一次：分笔来源允许新请求，但已无应收余额，整笔回滚。
                assertThatThrownBy(() -> financeReceiptService.collectCod(request, prefix + ":cod-dup-again"))
                        .isInstanceOfSatisfying(ScmBusinessException.class, failure -> assertThat(failure.getErrorCode())
                                .isEqualTo(FinanceErrorCode.WRITE_OFF_AMOUNT_EXCEEDED));

                assertThat(receiptCount(order.orderId())).isEqualTo(1);
                assertThat(writeOffCount(first.getReceiptId(), order.receivableId())).isEqualTo(1);
            }

            @Test
            @DisplayName("客户改派后原操作员失去范围，同键重放不能读取旧收款结果")
            void replayRechecksCurrentCustomerScope() {
                CodOrder order = signedCodOrder("replay-scope");
                FinanceCodCollectionForm request = form(order, "1.0000", "CASH");
                String key = prefix + ":cod-replay-scope";
                financeReceiptService.collectCod(request, key);

                RequestEmployee original = (RequestEmployee) XsyRequestUtil.getRequestUser();
                jdbc.update("UPDATE xsy_customer SET seller_id = ? WHERE id = ?", Long.MAX_VALUE, order.customerId());
                evictMybatisCache();
                RequestEmployee restricted = new RequestEmployee();
                restricted.setEmployeeId(original.getEmployeeId());
                restricted.setActualName(original.getActualName());
                restricted.setUserType(original.getUserType());
                restricted.setAdministratorFlag(false);
                XsyRequestUtil.setRequestUser(restricted);
                try {
                    assertThatThrownBy(() -> financeReceiptService.collectCod(request, key))
                            .isInstanceOf(ScmDataScopeException.class);
                } finally {
                    XsyRequestUtil.setRequestUser(original);
                }
                assertThat(receiptCount(order.orderId())).isEqualTo(1);
            }

            @Test
            @Transactional(propagation = Propagation.NOT_SUPPORTED)
            @DisplayName("不同防重复请求编号并发补收最后一笔欠款：恰有一笔写入数据库，另一笔完整回滚")
            void concurrentInstallmentsCannotCollectTheLastRemainderTwice() throws Exception {
                CodOrder order = signedCodOrder("race");
                collect(order, "1.0000", "CASH", "race-first");
                RequestEmployee employee = (RequestEmployee) XsyRequestUtil.getRequestUser();
                ExecutorService pool = Executors.newFixedThreadPool(2);
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch go = new CountDownLatch(1);
                List<Future<String>> outcomes = new ArrayList<>();
                try {
                    for (int index = 0; index < 2; index++) {
                        final String key = "race-last-" + index;
                        outcomes.add(pool.submit(() -> {
                            XsyRequestUtil.setRequestUser(employee);
                            try {
                                ready.countDown();
                                go.await();
                                collect(order, "1.4000", "BANK_TRANSFER", key);
                                return "COLLECTED";
                            } catch (ScmBusinessException rejected) {
                                return String.valueOf(rejected.getErrorCode().getCode());
                            } finally {
                                XsyRequestUtil.remove();
                            }
                        }));
                    }
                    assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
                    go.countDown();
                    List<String> results = new ArrayList<>();
                    for (Future<String> outcome : outcomes) {
                        results.add(outcome.get(60, TimeUnit.SECONDS));
                    }
                    assertThat(results).containsExactlyInAnyOrder("COLLECTED",
                            String.valueOf(FinanceErrorCode.WRITE_OFF_AMOUNT_EXCEEDED.getCode()));
                } finally {
                    pool.shutdownNow();
                }
                assertThat(receiptCount(order.orderId())).isEqualTo(2);
                assertThat(openAmount(order.receivableId())).isEqualByComparingTo("0.0000");
            }

            @Test
            @DisplayName("未签收、非 COD 与不存在的订单：都在写任何资金记录之前被拒")
            void unsignedNonCodAndMissingOrdersAreRejectedBeforeAnyFact() {
                // 约定为 COD 但还没有应收（未签收 / 拒收 / 异常签收）：没有可收的钱
                CodOrder unsigned = codOrder("unsigned", false);
                assertThatThrownBy(() -> collect(unsigned, "2.4000", "CASH", "unsigned"))
                        .isInstanceOfSatisfying(ScmBusinessException.class, failure -> assertThat(failure.getErrorCode())
                                .isEqualTo(FinanceErrorCode.COD_COLLECTION_ORDER_NOT_APPLICABLE));
                assertThat(receiptCount(unsigned.orderId())).isZero();

                // 有应收但不是 COD 约定：账期的钱走应收核销，不走 COD 收款
                CodOrder credit = codOrder("credit", true);
                jdbc.update("UPDATE xsy_sales_order SET settlement_method = 'CREDIT' WHERE id = ?", credit.orderId());
                evictMybatisCache();
                assertThatThrownBy(() -> collect(credit, "2.4000", "CASH", "credit"))
                        .isInstanceOfSatisfying(ScmBusinessException.class, failure -> assertThat(failure.getErrorCode())
                                .isEqualTo(FinanceErrorCode.COD_COLLECTION_ORDER_NOT_APPLICABLE));
                assertThat(receiptCount(credit.orderId())).isZero();

                FinanceCodCollectionForm missing = new FinanceCodCollectionForm();
                missing.setOrderId(Long.MAX_VALUE);
                missing.setAmount("2.4000");
                missing.setMethod("CASH");
                missing.setReceivedAt(OffsetDateTime.now());
                assertThatThrownBy(() -> financeReceiptService.collectCod(missing, prefix + ":cod-missing"))
                        .isInstanceOfSatisfying(ScmBusinessException.class, failure -> assertThat(failure.getErrorCode())
                                .isEqualTo(FinanceErrorCode.COD_COLLECTION_ORDER_NOT_FOUND));
            }

            // ------------------------------------------------------------------
            // 夹具
            // ------------------------------------------------------------------

            private record CodOrder(Long orderId, Long customerId, Long receivableId) {
            }

            /** 一张约定为 COD、且已经签收（有正常应收）的订单。 */
            private CodOrder signedCodOrder(String tag) {
                return codOrder(tag, true);
            }

            private CodOrder codOrder(String tag, boolean signed) {
                Long customerId = newCustomer();
                Long skuId = newOnShelfSku("cod-" + tag);
                Long orderId = confirmedSalesOrder(customerId, skuId, "2.0000", "2.0000");
                jdbc.update("UPDATE xsy_sales_order SET settlement_method = 'COD' WHERE id = ?", orderId);
                evictMybatisCache();
                Long receivableId = signed ? normalReceivable(orderId, RECEIVABLE_AMOUNT.toPlainString()) : null;
                return new CodOrder(orderId, customerId, receivableId);
            }

            private FinanceReceiptVO collect(CodOrder order, String amount, String method, String key) {
                return financeReceiptService.collectCod(form(order, amount, method), prefix + ":cod-" + key);
            }

            private FinanceCodCollectionForm form(CodOrder order, String amount, String method) {
                FinanceCodCollectionForm form = new FinanceCodCollectionForm();
                form.setOrderId(order.orderId());
                form.setAmount(amount);
                form.setMethod(method);
                form.setReceivedAt(OffsetDateTime.now());
                form.setExternalReference("COD-" + UUID.randomUUID().toString().substring(0, 8));
                return form;
            }

            private Long normalReceivable(Long orderId, String amount) {
                OffsetDateTime eventAt = LocalDate.now(BUSINESS_ZONE).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
                return jdbc.queryForObject("""
                        INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id, order_id,
                            customer_id, customer_name_snapshot, settlement_customer_id,
                            settlement_customer_name_snapshot, entry_type, amount, event_at)
                        SELECT ?, 'SALES_ORDER', id, id, customer_id, customer_name_snapshot,
                            settlement_customer_id, settlement_customer_name_snapshot, 'NORMAL', CAST(? AS numeric), ?
                        FROM xsy_sales_order WHERE id = ? RETURNING id
                        """, Long.class, "AR-COD-" + UUID.randomUUID(), new BigDecimal(amount), eventAt, orderId);
            }

            private Integer receiptCount(Long orderId) {
                return jdbc.queryForObject("SELECT count(*) FROM xsy_finance_receipt"
                        + " WHERE ((source_type = 'ORDER_COD_COLLECTION' AND source_id = ?)"
                        + " OR (source_type = 'ORDER_COD_INSTALLMENT' AND cod_order_id = ?)) AND deleted = FALSE",
                        Integer.class, orderId, orderId);
            }

            private Integer writeOffCount(Long receiptId, Long receivableId) {
                return jdbc.queryForObject("SELECT count(*) FROM xsy_finance_write_off WHERE source_type = 'RECEIPT'"
                        + " AND source_id = ? AND target_type = 'RECEIVABLE' AND target_id = ? AND entry_type = 'NORMAL'"
                        + " AND deleted = FALSE", Integer.class, receiptId, receivableId);
            }

            /** 应收未结额 = 应收金额 − 正常核销 + 反向核销。 */
            private BigDecimal openAmount(Long receivableId) {
                return jdbc.queryForObject("""
                        SELECT (SELECT amount FROM xsy_finance_receivable WHERE id = ?)
                             - COALESCE((SELECT SUM(CASE WHEN entry_type = 'NORMAL' THEN amount ELSE -amount END)
                                 FROM xsy_finance_write_off
                                 WHERE target_type = 'RECEIVABLE' AND target_id = ? AND deleted = FALSE), 0)
                        """, BigDecimal.class, receivableId, receivableId);
            }
        }

        @Nested

        class FinanceFifoWriteOffIT {
            private static final OffsetDateTime EVENT_AT = OffsetDateTime.of(2026, 3, 17, 9, 0, 0, 0,
                ZoneOffset.ofHours(8));

            @Autowired
            private FinanceReceiptService financeReceiptService;

            @Autowired
            private FinanceWriteOffService financeWriteOffService;

            @Autowired
            private FinanceReceivableDao receivables;

            @Test
            void groupedCreditsStayWithTheirCustomerAndAreAppliedBeforeCandidateLimit() {
                Long parent = newCustomer();
                Long firstCustomer = newCustomer();
                Long secondCustomer = newCustomer();
                normalReceivable(firstCustomer, "100", EVENT_AT.minusDays(10), parent);
                Long remaining = normalReceivable(firstCustomer, "50", EVENT_AT.minusDays(5), parent);
                Long other = normalReceivable(secondCustomer, "100", EVENT_AT.minusDays(3), parent);
                customerCredit(firstCustomer, parent, "120");
                customerCredit(parent, parent, "999");
                evictMybatisCache();
                var one = receivables.selectAutoTargets(parent, ScmValueScope.all(), 1);
                assertThat(one).hasSize(1);
                assertThat(one.getFirst().getTargetId()).isEqualTo(remaining);
                assertThat(one.getFirst().getOpenAmount()).isEqualByComparingTo("30");
                var all = receivables.selectAutoTargets(parent, ScmValueScope.all(), 100);
                assertThat(all).extracting(row -> row.getTargetId()).containsExactly(remaining, other);
                assertThat(all.get(1).getOpenAmount()).isEqualByComparingTo("100");
            }

            @Test
            @DisplayName("#1 自动核销按账单日期从旧到新分配，剩余额不落到更晚的单据")
            void autoWriteOffFollowsBillDateOrder() {
                Long customerId = newCustomer();
                Long oldest = normalReceivable(customerId, "30.0000", EVENT_AT.minusDays(10));
                Long middle = normalReceivable(customerId, "30.0000", EVENT_AT.minusDays(5));
                Long newest = normalReceivable(customerId, "30.0000", EVENT_AT.minusDays(1));
                FinanceReceiptVO receipt = addReceipt(customerId, "50.0000");

                FinanceWriteOffAutoForm previewForm = autoForm(receipt.getReceiptId());
                var plan = financeWriteOffService.autoPreview(previewForm);
                assertThat(plan).extracting(row -> row.getTargetId()).containsExactly(oldest, middle);
                assertThat(plan).extracting(row -> row.getAllocatedAmount())
                    .containsExactly(new BigDecimal("30.0000"), new BigDecimal("20.0000"));

                FinanceWriteOffAddResultVO result = financeWriteOffService.autoAdd(previewForm, key("fifo"));
                assertThat(result.getItems()).extracting(FinanceWriteOffVO::getTargetId)
                    .containsExactly(oldest, middle);
                assertThat(result.getItems()).extracting(FinanceWriteOffVO::getAmount)
                    .containsExactly(new BigDecimal("30.0000"), new BigDecimal("20.0000"));
                assertThat(allocatedTo(newest)).isZero();
            }

            @Test
            @DisplayName("#2 同一防重复请求编号重放不重复核销；金额分完后再次自动核销返回 41164")
            void replayIsIdempotentAndExhaustedReceiptIsRejected() {
                Long customerId = newCustomer();
                normalReceivable(customerId, "40.0000", EVENT_AT.minusDays(3));
                FinanceReceiptVO receipt = addReceipt(customerId, "40.0000");
                FinanceWriteOffAutoForm form = autoForm(receipt.getReceiptId());
                String idempotencyKey = key("fifo-replay");

                FinanceWriteOffAddResultVO first = financeWriteOffService.autoAdd(form, idempotencyKey);
                FinanceWriteOffAddResultVO replay = financeWriteOffService.autoAdd(form, idempotencyKey);

                assertThat(replay.getItems()).extracting(FinanceWriteOffVO::getWriteOffId)
                    .containsExactlyElementsOf(first.getItems().stream().map(FinanceWriteOffVO::getWriteOffId).toList());
                assertThat(count("SELECT count(*) FROM xsy_finance_write_off WHERE source_type = 'RECEIPT' "
                    + "AND source_id = ? AND entry_type = 'NORMAL'", receipt.getReceiptId())).isEqualTo(1);

                expectCode(() -> financeWriteOffService.autoAdd(form, key("fifo-exhausted")), 41164);
            }

            private FinanceWriteOffAutoForm autoForm(Long receiptId) {
                FinanceWriteOffAutoForm form = new FinanceWriteOffAutoForm();
                form.setSourceType("RECEIPT");
                form.setSourceId(receiptId);
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

            private Long normalReceivable(Long customerId, String amount, OffsetDateTime eventAt) {
                return normalReceivable(customerId, amount, eventAt, customerId);
            }

            private void customerCredit(Long customerId, Long settlementId, String amount) {
                jdbc.update("INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id, customer_id, "
                    + "customer_name_snapshot, settlement_customer_id, settlement_customer_name_snapshot, entry_type, amount, event_at, reason) "
                    + "VALUES (?, 'ORDER_RETURN', ?, ?, 'FIFO客户', ?, 'FIFO集团', 'RED', ?, ?, '独立退货')",
                    prefix + "-credit-" + UUID.randomUUID(), uniqueNumber(), customerId, settlementId,
                    new BigDecimal(amount), EVENT_AT);
            }

            private Long normalReceivable(Long customerId, String amount, OffsetDateTime eventAt, Long settlementId) {
                long sourceId = uniqueNumber();
                return jdbc.queryForObject("INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id,"
                        + " order_id, customer_id, customer_name_snapshot, settlement_customer_id,"
                        + " settlement_customer_name_snapshot, entry_type, amount, event_at, created_at, updated_at)"
                        + " VALUES (?, 'SALES_ORDER', ?, 1, ?, 'FIFO 测试客户', ?, 'FIFO 测试客户', 'NORMAL', ?, ?, now(), now())"
                        + " RETURNING id",
                    Long.class, "AR-FIFO-" + prefix + "-" + UUID.randomUUID(), sourceId, customerId, settlementId,
                    new BigDecimal(amount), eventAt);
            }

            private BigDecimal allocatedTo(Long receivableId) {
                return jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM xsy_finance_write_off "
                    + "WHERE target_type = 'RECEIVABLE' AND target_id = ? AND entry_type = 'NORMAL'",
                    BigDecimal.class, receivableId);
            }

            private int count(String sql, Object... args) {
                Integer value = jdbc.queryForObject(sql, Integer.class, args);
                return value == null ? 0 : value;
            }

            private String key(String tag) {
                return prefix + ":" + tag + ":" + UUID.randomUUID();
            }

            private long uniqueNumber() {
                return 1_000_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 8_000_000_000L);
            }
        }

        @Nested

        class FinancePostingIT {
            @Autowired
            private FinancePostingService postingService;

            @Autowired
            private FinancePostingQueryService postingQueryService;

            @Test
            @DisplayName("#1 登记过账只落过账表与日志，不生成应收应付、不写核销")
            void addLeavesNoLedgerEffects() {
                Long customerId = newCustomer();
                int receivablesBefore = count("SELECT count(*) FROM xsy_finance_receivable");
                int payablesBefore = count("SELECT count(*) FROM xsy_finance_payable");
                int writeOffsBefore = count("SELECT count(*) FROM xsy_finance_write_off");

                FinancePostingVO posting = postingService.add(addForm(customerId, "1234.5678"), key("add"));

                assertThat(posting.getPostingNo()).matches("^XSY\\d{18,19}$");
                assertThat(posting.getStatus()).isEqualTo("NORMAL");
                assertThat(posting.getRefundStatus()).isEqualTo("PENDING");
                assertThat(posting.getTaxRate()).isEqualByComparingTo("13.0000");
                assertThat(posting.getCustomerName()).isNotBlank();
                assertThat(posting.getAmount()).isEqualByComparingTo("1234.5678");

                assertThat(count("SELECT count(*) FROM xsy_finance_receivable")).isEqualTo(receivablesBefore);
                assertThat(count("SELECT count(*) FROM xsy_finance_payable")).isEqualTo(payablesBefore);
                assertThat(count("SELECT count(*) FROM xsy_finance_write_off")).isEqualTo(writeOffsBefore);
                assertThat(count("SELECT count(*) FROM xsy_finance_operation_log WHERE business_type = 'POSTING' "
                    + "AND business_id = ? AND operation_type = 'POSTING_ADD'", posting.getPostingId())).isEqualTo(1);
            }

            @Test
            @DisplayName("#2 金额、税点、返款状态与客户各自校验（41186 / 41189 / 41190 / 41191）")
            void addValidatesInputs() {
                Long customerId = newCustomer();

                FinancePostingAddForm zero = addForm(customerId, "0.0000");
                expectCode(() -> postingService.add(zero, key("zero")), 41189);

                FinancePostingAddForm tax = addForm(customerId, "10.0000");
                tax.setTaxRate("100.0001");
                expectCode(() -> postingService.add(tax, key("tax")), 41190);

                FinancePostingAddForm status = addForm(customerId, "10.0000");
                status.setRefundStatus("DONE");
                expectCode(() -> postingService.add(status, key("status")), 41191);

                FinancePostingAddForm missingCustomer = addForm(customerId, "10.0000");
                missingCustomer.setCustomerId(999_999_999L);
                expectCode(() -> postingService.add(missingCustomer, key("customer")), 41186);

                assertThat(count("SELECT count(*) FROM xsy_finance_posting WHERE customer_id = ?", customerId)).isZero();
            }

            @Test
            @DisplayName("#3 同一防重复请求编号重复提交只登记一张过账单")
            void addIsIdempotent() {
                Long customerId = newCustomer();
                String idempotencyKey = key("add-once");

                FinancePostingVO first = postingService.add(addForm(customerId, "88.0000"), idempotencyKey);
                FinancePostingVO replay = postingService.add(addForm(customerId, "88.0000"), idempotencyKey);

                assertThat(replay.getPostingId()).isEqualTo(first.getPostingId());
                assertThat(count("SELECT count(*) FROM xsy_finance_posting WHERE customer_id = ?", customerId)).isEqualTo(1);
            }

            @Test
            @DisplayName("#4 修改写前后保存的数据；版本过期按 40921 拒绝")
            void updateWritesSnapshotAndRejectsStaleVersion() {
                Long customerId = newCustomer();
                FinancePostingVO created = postingService.add(addForm(customerId, "100.0000"), key("update-add"));

                FinancePostingUpdateForm stale = updateForm(created);
                stale.setVersion(created.getVersion() + 1);
                stale.setAmount("200.0000");
                expectCode(() -> postingService.update(stale, key("update-stale")), 40921);
                assertThat(amountOf(created.getPostingId())).isEqualByComparingTo("100.0000");

                FinancePostingUpdateForm update = updateForm(created);
                update.setAmount("200.5000");
                update.setDocumentDate(LocalDate.of(2026, 10, 1));
                update.setRefundStatus("REFUNDED");
                update.setTaxRate("9.0000");
                update.setRemark("按更正后的单据修改");
                FinancePostingVO updated = postingService.update(update, key("update"));

                assertThat(updated.getAmount()).isEqualByComparingTo("200.5000");
                assertThat(updated.getDocumentDate()).isEqualTo(LocalDate.of(2026, 10, 1));
                assertThat(updated.getRefundStatus()).isEqualTo("REFUNDED");
                assertThat(updated.getVersion()).isGreaterThan(created.getVersion());

                assertThat(logValue(created.getPostingId(), "POSTING_UPDATE", "before_data", "amount"))
                    .isEqualTo("100.0000");
                assertThat(logValue(created.getPostingId(), "POSTING_UPDATE", "after_data", "amount"))
                    .isEqualTo("200.5000");
            }

            @Test
            @DisplayName("#5 作废必须写原因（41187），作废后不可修改或重复作废（41185）")
            void voidRequiresReasonAndIsTerminal() {
                Long customerId = newCustomer();
                FinancePostingVO created = postingService.add(addForm(customerId, "66.0000"), key("void-add"));

                FinancePostingVoidForm noReason = voidForm(created, null);
                expectCode(() -> postingService.voidPosting(noReason, key("void-no-reason")), 41187);
                assertThat(statusOf(created.getPostingId())).isEqualTo("NORMAL");

                FinancePostingVO voided = postingService.voidPosting(voidForm(created, "金额录错，重新登记"), key("void"));
                assertThat(voided.getStatus()).isEqualTo("VOIDED");
                assertThat(voided.getVoidReason()).isEqualTo("金额录错，重新登记");
                assertThat(voided.getVoidedAt()).isNotNull();
                assertThat(count("SELECT count(*) FROM xsy_finance_posting_void WHERE posting_id = ?",
                    created.getPostingId())).isEqualTo(1);
                assertThat(count("SELECT count(*) FROM xsy_finance_operation_log WHERE business_type = 'POSTING' "
                    + "AND business_id = ? AND operation_type = 'POSTING_VOID'", created.getPostingId())).isEqualTo(1);

                expectCode(() -> postingService.voidPosting(voidForm(created, "再作废一次"), key("void-again")), 41185);
                FinancePostingUpdateForm update = updateForm(created);
                update.setAmount("70.0000");
                expectCode(() -> postingService.update(update, key("update-voided")), 41185);

                // 作废是记录不是删除：过账行仍在，金额不被改写
                assertThat(amountOf(created.getPostingId())).isEqualByComparingTo("66.0000");
            }

            @Test
            @DisplayName("修改响应丢失后同键重试返回原结果，后续作废也不影响返回原成功结果")
            void updateReplaysBeforeVersionAndVoidChecks() {
                var created = postingService.add(addForm(newCustomer(), "100.0000"), key("retry-create"));
                var form = updateForm(created);
                form.setAmount("150.0000");
                String updateKey = key("retry-update");
                var updated = postingService.update(form, updateKey);

                var replay = postingService.update(form, updateKey);
                assertThat(replay.getVersion()).isEqualTo(updated.getVersion());
                assertThat(replay.getAmount()).isEqualByComparingTo("150.0000");
                postingService.voidPosting(voidForm(updated, "重新登记"), key("retry-later-void"));
                assertThat(postingService.update(form, updateKey).getVersion()).isEqualTo(updated.getVersion());
                assertThat(count("SELECT count(*) FROM xsy_finance_operation_log WHERE business_type='POSTING' "
                        + "AND business_id=? AND operation_type='POSTING_UPDATE'", created.getPostingId())).isEqualTo(1);
                assertThat(statusOf(created.getPostingId())).isEqualTo("VOIDED");
            }

            @Test
            @DisplayName("作废同键重试返回原记录，新键重复作废仍拒绝")
            void voidReplaysWithoutDuplicatingFactsOrLogs() {
                var created = postingService.add(addForm(newCustomer(), "80.0000"), key("void-retry-create"));
                var form = voidForm(created, "金额错误");
                String voidKey = key("void-retry");
                var voided = postingService.voidPosting(form, voidKey);

                var replay = postingService.voidPosting(form, voidKey);
                assertThat(replay.getStatus()).isEqualTo("VOIDED");
                // 对外时间按秒序列化，比较同一业务时刻，不把展示精度当作重复写入。
                assertThat(replay.getVoidedAt().toInstant()).isEqualTo(
                        voided.getVoidedAt().toInstant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
                assertThat(count("SELECT count(*) FROM xsy_finance_posting_void WHERE posting_id=?",
                        created.getPostingId())).isEqualTo(1);
                assertThat(count("SELECT count(*) FROM xsy_finance_operation_log WHERE business_type='POSTING' "
                        + "AND business_id=? AND operation_type='POSTING_VOID'", created.getPostingId())).isEqualTo(1);
                expectCode(() -> postingService.voidPosting(form, key("void-new-command")), 41185);
            }

            @Test
            @DisplayName("#6 数据范围：看不到客户归属的过账，也不能给它登记")
            void scopeHidesOtherSellersPostings() {
                Long customerId = newCustomer();
                FinancePostingVO created = postingService.add(addForm(customerId, "12.0000"), key("scope-add"));

                asEmployee(99001L, () -> {
                    assertThatThrownBy(() -> postingService.add(addForm(customerId, "1.0000"), key("scope-other")))
                        .isExactlyInstanceOf(ScmDataScopeException.class);
                    assertThat(pageRows(null)).isZero();
                    expectCode(() -> postingQueryService.detail(created.getPostingId()), 41184);
                    return null;
                });

                assertThat(pageRows(created.getPostingNo())).isEqualTo(1);
            }

            @Test
            @DisplayName("#7 查询能按返款状态与有效状态筛选，作废行不会悄悄消失")
            void queryFiltersByRefundStatusAndValidity() {
                Long customerId = newCustomer();
                FinancePostingVO normal = postingService.add(addForm(customerId, "30.0000"), key("filter-1"));
                FinancePostingVO second = postingService.add(addForm(customerId, "40.0000"), key("filter-2"));
                FinancePostingVO voided = postingService.update(updateWithRefundStatus(second, "PARTIAL"),
                    key("filter-partial"));
                postingService.voidPosting(voidForm(voided, "重复登记"), key("filter-void"));

                FinancePostingQueryForm all = queryForm();
                all.setCustomerId(customerId);
                assertThat(postingQueryService.query(all).getTotal()).isEqualTo(2);

                FinancePostingQueryForm valid = queryForm();
                valid.setCustomerId(customerId);
                valid.setStatus("NORMAL");
                assertThat(postingQueryService.query(valid).getList()).extracting(FinancePostingVO::getPostingId)
                    .containsExactly(normal.getPostingId());

                FinancePostingQueryForm refunded = queryForm();
                refunded.setCustomerId(customerId);
                refunded.setRefundStatus("PARTIAL");
                assertThat(postingQueryService.query(refunded).getList()).extracting(FinancePostingVO::getPostingId)
                    .containsExactly(voided.getPostingId());
            }

            private FinancePostingAddForm addForm(Long customerId, String amount) {
                FinancePostingAddForm form = new FinancePostingAddForm();
                form.setCustomerId(customerId);
                form.setDocumentDate(LocalDate.of(2026, 10, 28));
                form.setAmount(amount);
                form.setTaxRate("13");
                form.setRemark(prefix + " 过账");
                return form;
            }

            private FinancePostingUpdateForm updateForm(FinancePostingVO posting) {
                FinancePostingUpdateForm form = new FinancePostingUpdateForm();
                form.setPostingId(posting.getPostingId());
                form.setVersion(posting.getVersion());
                form.setDocumentDate(posting.getDocumentDate());
                form.setAmount(posting.getAmount().toPlainString());
                form.setTaxRate(posting.getTaxRate() == null ? null : posting.getTaxRate().toPlainString());
                form.setRefundStatus(posting.getRefundStatus());
                form.setRemark(posting.getRemark());
                return form;
            }

            private FinancePostingUpdateForm updateWithRefundStatus(FinancePostingVO posting, String refundStatus) {
                FinancePostingUpdateForm form = updateForm(posting);
                form.setRefundStatus(refundStatus);
                return form;
            }

            private FinancePostingVoidForm voidForm(FinancePostingVO posting, String reason) {
                FinancePostingVoidForm form = new FinancePostingVoidForm();
                form.setPostingId(posting.getPostingId());
                form.setVersion(posting.getVersion());
                form.setReason(reason);
                return form;
            }

            private FinancePostingQueryForm queryForm() {
                FinancePostingQueryForm form = new FinancePostingQueryForm();
                form.setStartDate(LocalDate.of(2026, 9, 1));
                form.setEndDate(LocalDate.of(2026, 11, 30));
                form.setPageNum(1L);
                form.setPageSize(20L);
                return form;
            }

            private int pageRows(String keyword) {
                FinancePostingQueryForm form = queryForm();
                form.setKeyword(keyword);
                return postingQueryService.query(form).getList().size();
            }

            private java.math.BigDecimal amountOf(Long postingId) {
                return jdbc.queryForObject("SELECT amount FROM xsy_finance_posting WHERE id = ?",
                    java.math.BigDecimal.class, postingId);
            }

            private String statusOf(Long postingId) {
                return postingQueryService.detail(postingId).getStatus();
            }

            private String logValue(Long postingId, String operationType, String column, String key) {
                return jdbc.queryForObject("SELECT " + column + "->>'" + key + "' FROM xsy_finance_operation_log "
                    + "WHERE business_type = 'POSTING' AND business_id = ? AND operation_type = ?",
                    String.class, postingId, operationType);
            }

            private int count(String sql, Object... args) {
                Integer value = jdbc.queryForObject(sql, Integer.class, args);
                return value == null ? 0 : value;
            }

            private String key(String tag) {
                return prefix + ":" + tag + ":" + UUID.randomUUID();
            }

            /**
             * 以非超管员工执行：数据范围按「归属业务员 = 自己」解析，客户归属别人就既读不到也写不了。
             */
            private <T> T asEmployee(Long employeeId, Supplier<T> body) {
                RequestEmployee employee = new RequestEmployee();
                employee.setEmployeeId(employeeId);
                employee.setActualName("过账数据范围 IT");
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                employee.setAdministratorFlag(false);
                XsyRequestUtil.setRequestUser(employee);
                try {
                    return body.get();
                } finally {
                    RequestEmployee admin = new RequestEmployee();
                    admin.setEmployeeId(1L);
                    admin.setActualName("过账 IT 操作员");
                    admin.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                    admin.setAdministratorFlag(true);
                    XsyRequestUtil.setRequestUser(admin);
                }
            }
        }

        @Nested

        class FinanceWaiverIT {
            @Autowired
            private FinanceWaiverService financeWaiverService;

            @Autowired
            private FinanceWriteOffService financeWriteOffService;

            @Test
            @DisplayName("#1 抹零冲减应收并写 WAIVER 核销；超未结额被拒（41157）")
            void waiverReducesOpenAmount() {
                Long receivableId = normalReceivable("100.0000");

                FinanceWaiverVO waiver = financeWaiverService.add(waiverForm(receivableId, "0.3000", "分角抹零"),
                    key("waiver-add"));

                assertThat(waiver.getReceivableId()).isEqualTo(receivableId);
                assertThat(waiver.getAmount()).isEqualByComparingTo("0.3000");
                assertThat(waiver.getWriteOffId()).isNotNull();
                assertThat(netAllocated(receivableId)).isEqualByComparingTo("0.3000");
                assertThat(count("SELECT count(*) FROM xsy_finance_write_off WHERE source_type = 'WAIVER' "
                    + "AND source_id = ? AND target_id = ? AND entry_type = 'NORMAL'",
                    waiver.getWaiverId(), receivableId)).isEqualTo(1);

                expectCode(() -> financeWaiverService.add(waiverForm(receivableId, "200.0000", "超额抹零"),
                    key("waiver-over")), 41157);
                assertThat(netAllocated(receivableId)).isEqualByComparingTo("0.3000");
            }

            @Test
            @DisplayName("#2 反向必须写原因（41138），反向追加 REVERSE 并恢复未结额，重复反向被拒（41159）")
            void reverseRestoresOpenAmount() {
                Long receivableId = normalReceivable("80.0000");
                FinanceWaiverVO waiver = financeWaiverService.add(waiverForm(receivableId, "0.5000", "分角抹零"),
                    key("waiver-reverse-add"));

                FinanceWaiverReverseForm reverse = reverseForm(waiver.getWaiverId(), null);
                expectCode(() -> financeWaiverService.reverse(reverse, key("waiver-reverse-no-reason")), 41138);
                assertThat(netAllocated(receivableId)).isEqualByComparingTo("0.5000");

                reverse.setReason("客户要求恢复应收");
                FinanceWaiverVO reversed = financeWaiverService.reverse(reverse, key("waiver-reverse"));
                assertThat(reversed.getStatus()).isEqualTo("REVERSED");
                assertThat(netAllocated(receivableId)).isZero();
                assertThat(count("SELECT count(*) FROM xsy_finance_write_off WHERE source_type = 'WAIVER' "
                    + "AND source_id = ? AND entry_type = 'REVERSE'", waiver.getWaiverId())).isEqualTo(1);

                expectCode(() -> financeWaiverService.reverse(reverse, key("waiver-reverse-again")), 41159);
            }

            @Test
            @DisplayName("#3 通用核销反向接口不能撤销抹零（41165）")
            void genericWriteOffReverseCannotReverseWaiver() {
                Long receivableId = normalReceivable("50.0000");
                FinanceWaiverVO waiver = financeWaiverService.add(waiverForm(receivableId, "0.2000", "分角抹零"),
                    key("waiver-generic-add"));

                FinanceWriteOffReverseForm form = new FinanceWriteOffReverseForm();
                form.setWriteOffId(waiver.getWriteOffId());
                form.setReason("误操作");
                expectCode(() -> financeWriteOffService.reverse(form, key("waiver-generic-reverse")), 41165);
                assertThat(netAllocated(receivableId)).isEqualByComparingTo("0.2000");
            }

            private FinanceWaiverAddForm waiverForm(Long receivableId, String amount, String reason) {
                FinanceWaiverAddForm form = new FinanceWaiverAddForm();
                form.setReceivableId(receivableId);
                form.setAmount(amount);
                form.setReason(reason);
                return form;
            }

            private FinanceWaiverReverseForm reverseForm(Long waiverId, String reason) {
                FinanceWaiverReverseForm form = new FinanceWaiverReverseForm();
                form.setWaiverId(waiverId);
                form.setReason(reason);
                return form;
            }

            private Long normalReceivable(String amount) {
                Long customerId = newCustomer();
                long sourceId = 1_000_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 8_000_000_000L);
                return jdbc.queryForObject("INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id,"
                        + " order_id, customer_id, customer_name_snapshot, settlement_customer_id,"
                        + " settlement_customer_name_snapshot, entry_type, amount, event_at, created_at, updated_at)"
                        + " VALUES (?, 'SALES_ORDER', ?, 1, ?, '抹零测试客户', ?, '抹零测试客户', 'NORMAL', ?,"
                        + " now(), now(), now()) RETURNING id",
                    Long.class, "AR-WV-" + prefix + "-" + UUID.randomUUID(), sourceId, customerId, customerId,
                    new BigDecimal(amount));
            }

            /** NORMAL 为正、REVERSE 为负，得到当前净核销额。 */
            private BigDecimal netAllocated(Long receivableId) {
                return jdbc.queryForObject("SELECT COALESCE(SUM(CASE WHEN entry_type = 'NORMAL' THEN amount "
                    + "ELSE -amount END), 0) FROM xsy_finance_write_off "
                    + "WHERE target_type = 'RECEIVABLE' AND target_id = ?", BigDecimal.class, receivableId);
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
        @DisplayName("订单资金来源只认已成功的支付意图（PG IT）")
        class ScmFinanceOrderFundingSourcePgIT {
            @Autowired
            private FinanceOrderFundingSourceDao financeOrderFundingSourceDao;

            @Autowired
            private FinanceOrderFundingPolicy financeOrderFundingPolicy;

            @Test
            @DisplayName("渠道实收 ≠ 本地应付的差异交易不参与订单资金")
            void differentialTransactionIsNotOrderFunding() {
                Long orderId = uniqueNumber();
                Long matchedIntentId = intent(orderId, "SUCCEEDED");
                succeededTransaction(matchedIntentId, "100.0000");
                Long differentialIntentId = intent(orderId, "PENDING");
                succeededTransaction(differentialIntentId, "90.0000");

                List<FinanceOrderFundingDto> facts = financeOrderFundingSourceDao.selectOrderFunding(orderId);

                assertThat(facts).singleElement().satisfies(fact -> {
                    assertThat(fact.getIntentId()).isEqualTo(matchedIntentId);
                    assertThat(fact.getIntentStatus()).isEqualTo("SUCCEEDED");
                });
                assertThat(facts).extracting(FinanceOrderFundingDto::getIntentId).doesNotContain(differentialIntentId);
            }

            @Test
            @DisplayName("只剩差异交易时资金校验不再失败，签收因此不被阻断")
            void differentialTransactionDoesNotBreakFundingValidation() {
                Long orderId = uniqueNumber();
                succeededTransaction(intent(orderId, "PENDING"), "90.0000");

                assertThatCode(() -> financeOrderFundingPolicy.requireCompleteOrderFunding(orderId))
                        .as("差异交易留在渠道与对账域，不再触发 ORDER_FUNDING_INVALID")
                        .doesNotThrowAnyException();
            }

            /** 一条支付意图：本地应付 100。差异交易与正常交易的差别只在意图状态。 */
            private Long intent(Long orderId, String status) {
                return jdbc.queryForObject("INSERT INTO xsy_payment_intent (intent_no, customer_id, customer_name_snapshot,"
                                + " source_type, source_id, source_no_snapshot, amount, method, provider, status,"
                                + " succeeded_at, created_by, updated_by)"
                                + " VALUES (?, 1, '资金查询测试客户', 'SALES_ORDER', ?, ?, 100.0000, 'ONLINE', 'MOCK', ?,"
                                + " CASE WHEN ? = 'SUCCEEDED' THEN now() END, '1:1', '1:1') RETURNING id",
                        Long.class, "PI-FUND-" + prefix + "-" + UUID.randomUUID(), orderId, "SO-" + orderId, status, status);
            }

            /** 一条渠道已成功的交易：本地应付保持 100，渠道实收单独记，不覆盖本地金额。 */
            private void succeededTransaction(Long intentId, String providerAmount) {
                jdbc.update("INSERT INTO xsy_payment_transaction (transaction_no, intent_id, provider,"
                                + " provider_transaction_no, amount, provider_amount, status, paid_at, created_by, updated_by)"
                                + " VALUES (?, ?, 'MOCK', ?, 100.0000, ?, 'SUCCEEDED', now(), '1:1', '1:1')",
                        "PT-FUND-" + prefix + "-" + UUID.randomUUID(), intentId, "WX-" + UUID.randomUUID(),
                        new BigDecimal(providerAmount));
            }

            /** 与同目录 IT 相同的规则：订单主键由用例独占，避免共享开发库里别的订单把断言撑破。 */
            private long uniqueNumber() {
                return 1_000_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 8_000_000_000L);
            }
        }

        @Nested
        @DisplayName("付款登记权限发布（F1-3B，PG IT）")
        class ScmFinancePaymentPermissionPgIT {
            @Autowired
            private LoginManager loginManager;

            private Long employeeWithRole(String tag, String roleCode) {
                String loginName = (prefix + "-" + tag).toUpperCase();
                jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                                + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                        UUID.randomUUID().toString().replace("-", ""), loginName, "$argon2id$it-placeholder", "财务岗" + tag);
                Long employeeId = jdbc.queryForObject(
                        "SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
                assertThat(jdbc.queryForObject(
                        "SELECT administrator_flag FROM t_employee WHERE employee_id = ?", Boolean.class, employeeId))
                        .as("权限取证禁止超管位").isFalse();
                jdbc.update("INSERT INTO t_role_employee (role_id, employee_id)"
                                + " SELECT r.role_id, ? FROM t_role r WHERE r.role_code = ?",
                        employeeId, roleCode);
                evictMybatisCache();
                return employeeId;
            }

            private List<String> permissionsOf(Long employeeId) {
                var employee = new RequestEmployee();
                employee.setEmployeeId(employeeId);
                employee.setActualName("F1-3B 权限取证");
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                employee.setAdministratorFlag(false);
                XsyRequestUtil.setRequestUser(employee);
                // 走真实的角色 → 菜单 → api_perms 装配路径（生产登录用的就是它），不 mock StpUtil
                return loginManager.loadUserPermission(employeeId).getPermissionList();
            }

            @Test
            @DisplayName("SCM_FINANCE 持有付款登记权限；SCM_DRIVER 不持有")
            void financeRoleHoldsPaymentAddPermission() {
                Long financeEmployee = employeeWithRole("FIN", "SCM_FINANCE");
                Long driverEmployee = employeeWithRole("DRV", "SCM_DRIVER");

                assertThat(permissionsOf(financeEmployee)).contains(FinanceConstant.PAYMENT_ADD_PERM);
                assertThat(permissionsOf(driverEmployee)).doesNotContain(FinanceConstant.PAYMENT_ADD_PERM);
            }

            @Test
            @DisplayName("库里发布的串与 Controller 注解声明的串逐字一致，且只授财务岗")
            void publishedPermissionStringMatchesTheControllerAnnotation() throws Exception {
                Method endpoint = FinancePaymentController.class
                        .getDeclaredMethod("add", FinancePaymentAddForm.class, String.class);
                SaCheckPermission annotation = endpoint.getAnnotation(SaCheckPermission.class);
                assertThat(annotation).as("付款登记端点必须挂功能权限注解").isNotNull();
                assertThat(annotation.value()).containsExactly(FinanceConstant.PAYMENT_ADD_PERM);

                assertThat(jdbc.queryForList("SELECT api_perms FROM t_menu WHERE api_perms = ?", String.class,
                        FinanceConstant.PAYMENT_ADD_PERM))
                        .containsExactly(FinanceConstant.PAYMENT_ADD_PERM);
                assertThat(jdbc.queryForList(
                        "SELECT r.role_code FROM t_role_menu rm JOIN t_role r ON r.role_id = rm.role_id"
                                + " JOIN t_menu m ON m.menu_id = rm.menu_id WHERE m.api_perms = ?"
                                + " AND r.role_code <> 'SUPER_ADMIN' ORDER BY r.role_code",
                        String.class, FinanceConstant.PAYMENT_ADD_PERM))
                        .containsExactly("SCM_FINANCE");
                // 能力点必须挂在隐藏目录下、自身不带组件：这条串不该让侧栏多出任何入口
                assertThat(jdbc.queryForMap(
                        "SELECT parent_id, menu_type, component FROM t_menu WHERE api_perms = ?",
                        FinanceConstant.PAYMENT_ADD_PERM))
                        .containsEntry("parent_id", 1500L)
                        .containsEntry("component", null);
            }

            @Test
            @DisplayName("付款侧发布登记、查询与反向权限，五个财务页面随组件同步发布")
            void nothingElseIsPublishedYet() {
                assertThat(jdbc.queryForList(
                        "SELECT DISTINCT api_perms FROM t_menu WHERE api_perms LIKE 'scm:finance:payment:%'"
                                + " ORDER BY api_perms", String.class))
                        .as("F1-3B / F1-3C / F1-5 各自发布真实端点使用的权限")
                        .containsExactly(FinanceConstant.PAYMENT_ADD_PERM, FinanceConstant.PAYMENT_QUERY_PERM,
                                FinanceConstant.PAYMENT_REVERSE_PERM);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM t_menu WHERE menu_id BETWEEN 1500 AND 1599 AND menu_type = 2",
                        Integer.class))
                        .as("已发布六个带真实组件的财务页面")
                        .isEqualTo(6);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM t_menu WHERE menu_id BETWEEN 1501 AND 1506 AND component IS NOT NULL",
                        Integer.class)).isEqualTo(6);
            }
        }

        @Nested
        @DisplayName("付款登记（Finance R1 F1-3B，PG IT）")
        class ScmFinancePaymentPgIT {
            private static final OffsetDateTime PAST =
                    OffsetDateTime.of(2026, 4, 12, 15, 45, 0, 0, ZoneOffset.ofHours(8));

            @Autowired
            private FinancePaymentService financePaymentService;

            @Autowired
            private OrderReturnService returns;

            @Autowired
            private OrderRefundService refunds;

            // ------------------------------------------------------------------
            // 夹具
            // ------------------------------------------------------------------

            private FinancePaymentAddForm form(String counterpartyType, Long counterpartyId, String amount,
                                               String method, OffsetDateTime paidAt,
                                               String sourceType, Long sourceId) {
                FinancePaymentAddForm form = new FinancePaymentAddForm();
                form.setCounterpartyType(counterpartyType);
                form.setCounterpartyId(counterpartyId);
                form.setAmount(amount);
                form.setMethod(method);
                form.setPaidAt(paidAt);
                form.setSourceType(sourceType);
                form.setSourceId(sourceId);
                form.setExternalReference("PM-REF-" + UUID.randomUUID().toString().substring(0, 8));
                form.setRemark("F1-3B IT");
                return form;
            }

            private FinancePaymentAddForm supplierForm(Long supplierId, String amount) {
                return form("SUPPLIER", supplierId, amount, "BANK_TRANSFER", PAST, null, null);
            }

            private FinancePaymentAddForm refundForm(Long customerId, String amount, Long refundId) {
                return form("CUSTOMER", customerId, amount, "BANK_TRANSFER", PAST, "ORDER_REFUND", refundId);
            }

            private FinancePaymentVO add(FinancePaymentAddForm form) {
                return financePaymentService.add(form, key("add"));
            }

            private FinancePaymentVO add(FinancePaymentAddForm form, String idempotencyKey) {
                return financePaymentService.add(form, idempotencyKey);
            }

            private String key(String tag) {
                return prefix + ":" + tag + ":" + UUID.randomUUID();
            }

            private Long supplier() {
                return newSupplier("P" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 1000));
            }

            private Long customerOwnedBy(Long sellerId) {
                Long customerId = newCustomer();
                jdbc.update("UPDATE xsy_customer SET seller_id = ? WHERE id = ?", sellerId, customerId);
                evictMybatisCache();
                return customerId;
            }

            /**
             * 走完整订单域链路造一张<b>已 COMPLETED 的退款</b>：确认订单 → 退货批准 → 退款完成。
             *
             * <p>不直插 {@code xsy_order_refund}：退款金额、客户与状态都必须是订单域真实计算的结果，
             * 否则「付款金额必须逐值等于 refund_amount」这条校验就变成两个自造数字互相印证。
             */
            private Refund completedRefund(String tag, String ordered, String approved) {
                Long customerId = customerOwnedBy(null);
                // 商品编码是 prefix + suffix，而 prefix 只在单个用例内唯一，
                // 因此同一用例里造多张退款必须各用自己的 tag，否则第二张撞 SPU 编码唯一。
                Long skuId = newOnShelfSku(tag);
                Long orderId = confirmedSalesOrder(customerId, skuId, ordered, ordered);
                Long orderItemId = confirmedSalesOrderItemId(orderId);

                var create = new OrderReturnAddForm();
                create.setOrderId(orderId);
                create.setReason("品质问题 " + tag);
                var row = new OrderReturnItemForm();
                row.setOrderItemId(orderItemId);
                row.setRequestedQuantity(approved);
                create.setItems(List.of(row));
                var created = returns.create(create, key("return-create:" + tag));

                var approve = new OrderReturnApproveForm();
                approve.setReturnId(created.getReturnId());
                approve.setVersion(created.getVersion());
                var approvedRow = new OrderReturnApproveItemForm();
                approvedRow.setOrderItemId(orderItemId);
                approvedRow.setApprovedQuantity(approved);
                approve.setItems(List.of(approvedRow));
                returns.approve(approve, key("return-approve:" + tag));

                // 按 order_id 直查退款行，不绕 refunds.query(...).getFirst()：
                // 那个分页读接口按 orderSellerScope 收窄（非超管身份下会拿不到行），
                // 而夹具要的只是「这一单的退款」，直查不依赖读侧范围也不依赖行序。
                Long refundId = jdbc.queryForObject(
                        "SELECT id FROM xsy_order_refund WHERE order_id = ? AND deleted = FALSE", Long.class, orderId);
                Integer refundVersion = jdbc.queryForObject(
                        "SELECT version FROM xsy_order_refund WHERE id = ?", Integer.class, refundId);

                var complete = new OrderRefundCompleteForm();
                complete.setRefundId(refundId);
                complete.setVersion(refundVersion);
                // order_refund.external_reference 上有部分唯一索引（uk_order_refund_external_reference_active），
                // 与财务侧「可重复」的同名列正相反，因此这里的值必须每次唯一。
                // 也正因为它唯一，不能把这份凭据号复制进 finance_payment.external_reference：
                // 那会让两域的凭据号互相牵连，本类对此有显式断言。
                complete.setExternalReference("ORDER-SIDE-" + tag + "-" + UUID.randomUUID());
                refunds.complete(complete, key("refund-complete:" + tag));
                evictMybatisCache();

                Map<String, Object> row1 = jdbc.queryForMap(
                        "SELECT * FROM xsy_order_refund WHERE id = ?", refundId);
                return new Refund(refundId, orderId, customerId,
                        (BigDecimal) row1.get("refund_amount"), String.valueOf(row1.get("status")));
            }

            private record Refund(Long refundId, Long orderId, Long customerId,
                                  BigDecimal refundAmount, String status) {
            }

            private int count(String sql, Object... args) {
                Integer value = jdbc.queryForObject(sql, Integer.class, args);
                return value == null ? 0 : value;
            }

            private Map<String, Object> paymentRow(Long paymentId) {
                return jdbc.queryForMap("SELECT * FROM xsy_finance_payment WHERE id = ?", paymentId);
            }

            private List<Map<String, Object>> logsOf(Long paymentId) {
                // business_id 只在同一 business_type 内唯一（各记录表各有 id 序列），必须带类型
                return jdbc.queryForList(
                        "SELECT * FROM xsy_finance_operation_log WHERE business_type = 'PAYMENT' AND business_id = ?",
                        paymentId);
            }

            /**
             * 某供应商名下的付款数。
             *
             * <p>{@code counterparty_id} 是<b>多态</b>列（供应商 id 或客户 id，两条独立的 identity 序列），
             * 因此单按 id 计数会把同号的客户付款一起算进来 —— 必须连 {@code counterparty_type} 一起判。
             */
            private int paymentsOfSupplier(Long supplierId) {
                return count("SELECT count(*) FROM xsy_finance_payment"
                        + " WHERE counterparty_type = 'SUPPLIER' AND counterparty_id = ?", supplierId);
            }

            private int payLogsOfSupplier(Long supplierId) {
                return count("SELECT count(*) FROM xsy_finance_operation_log l"
                        + " JOIN xsy_finance_payment p ON p.id = l.business_id"
                        + " WHERE l.business_type = 'PAYMENT' AND p.counterparty_type = 'SUPPLIER'"
                        + " AND p.counterparty_id = ?", supplierId);
            }

            /** 某退款名下的付款数（来源两列一起判，与 {@code uk_finance_payment_source_active} 同规则）。 */
            private int paymentsOfRefund(Long refundId) {
                return count("SELECT count(*) FROM xsy_finance_payment"
                        + " WHERE source_type = 'ORDER_REFUND' AND source_id = ?", refundId);
            }

            private int payLogsOfRefund(Long refundId) {
                return count("SELECT count(*) FROM xsy_finance_operation_log l"
                        + " JOIN xsy_finance_payment p ON p.id = l.business_id"
                        + " WHERE l.business_type = 'PAYMENT' AND p.source_type = 'ORDER_REFUND'"
                        + " AND p.source_id = ?", refundId);
            }

            /** 切换到非超管身份取证数据范围与功能权限（P0 裁决第 5 条：越权取证禁止用超管位）。 */
            private void asEmployee(Long employeeId) {
                var employee = new RequestEmployee();
                employee.setEmployeeId(employeeId);
                employee.setActualName("F1-3B IT 非超管");
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                employee.setAdministratorFlag(false);
                XsyRequestUtil.setRequestUser(employee);
                evictMybatisCache();
            }

            private void asAdmin() {
                var employee = new RequestEmployee();
                employee.setEmployeeId(1L);
                employee.setActualName("W5 IT");
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                employee.setAdministratorFlag(true);
                XsyRequestUtil.setRequestUser(employee);
                evictMybatisCache();
            }

            private Long newPlainEmployee(String tag) {
                String loginName = (prefix + "-" + tag).toUpperCase();
                jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                        + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                        UUID.randomUUID().toString().replace("-", ""), loginName, "$argon2id$it-placeholder", "财务" + tag);
                Long employeeId = jdbc.queryForObject(
                        "SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
                assertThat(jdbc.queryForObject(
                        "SELECT administrator_flag FROM t_employee WHERE employee_id = ?", Boolean.class, employeeId))
                        .as("范围取证必须用非超管账号").isFalse();
                evictMybatisCache();
                return employeeId;
            }

            private static String currentOperator() {
                return com.xsy.scm.common.constant.ScmOperator.current();
            }

            /**
             * {@code timestamptz} 在 Map 读法里返回 {@link java.sql.Timestamp}，统一成时刻才可比。
             */
            private static OffsetDateTime timestampOf(Object value) {
                if (value instanceof OffsetDateTime offsetDateTime) {
                    return offsetDateTime;
                }
                if (value instanceof java.sql.Timestamp timestamp) {
                    return timestamp.toInstant().atOffset(ZoneOffset.UTC);
                }
                throw new IllegalArgumentException("无法归一为 OffsetDateTime 的列类型: "
                        + (value == null ? "null" : value.getClass().getName()));
            }

            // ------------------------------------------------------------------
            // A / B / C / N. SUPPLIER 模式
            // ------------------------------------------------------------------

            @Test
            @DisplayName("A 供应商预付：没有任何应付也照常登记，且不产生核销行")
            void supplierPrepaymentWithoutAnyPayableIsAllowed() {
                Long supplierId = supplier();
                assertThat(count("SELECT count(*) FROM xsy_finance_payable WHERE supplier_id = ?", supplierId)).isZero();

                FinancePaymentVO result = add(supplierForm(supplierId, "500.0000"));

                Map<String, Object> row = paymentRow(result.getPaymentId());
                assertThat(row.get("counterparty_type")).isEqualTo("SUPPLIER");
                assertThat(row.get("counterparty_id")).isEqualTo(supplierId);
                assertThat(row.get("source_type")).as("预付没有业务来源").isNull();
                assertThat(row.get("source_id")).isNull();
                assertThat(row.get("entry_type")).isEqualTo("NORMAL");
                assertThat(row.get("reverse_of_id")).isNull();
                assertThat(row.get("reason")).isNull();
                assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("500.0000");
                assertThat(String.valueOf(row.get("payment_no"))).matches("^XSY\\d{18,19}$");
                assertThat(count("SELECT count(*) FROM xsy_finance_write_off WHERE source_type = 'PAYMENT' AND source_id = ?",
                        result.getPaymentId())).as("该笔预付款不自动生成核销").isZero();
                assertThat(result.getCounterpartyName()).as("回显登记时保存的对方名").isEqualTo(row.get("counterparty_name_snapshot"));
            }

            @Test
            @DisplayName("B 已有应付的供应商付款：付款成立、应付一字未改、仍然零核销")
            void supplierPaymentWithExistingPayableNeverAutoWritesOff() {
                Long supplierId = supplier();
                // 受控夹具：按 schema 直插一张正常应付，
                // 目的是证明「付款不会自动去冲它」，核销仍只能由核销命令产生。
                Long purchaseOrderId = 900_001L;
                long receiptId = 900_002L;
                jdbc.update("INSERT INTO xsy_finance_payable (payable_no, source_type, source_id, purchase_order_id,"
                                + " supplier_id, supplier_name_snapshot, entry_type, amount, event_at,"
                                + " created_at, updated_at, created_by)"
                                + " VALUES ('AP-FIXTURE-PB', 'PURCHASE_RECEIPT', ?, ?, ?, '夹具供应商', 'NORMAL',"
                                + " 500.0000, now(), now(), now(), ?)",
                        receiptId, purchaseOrderId, supplierId, currentOperator());
                evictMybatisCache();
                Map<String, Object> before = jdbc.queryForMap(
                        "SELECT * FROM xsy_finance_payable WHERE source_id = ?", receiptId);

                FinancePaymentVO payment = add(supplierForm(supplierId, "500.0000"));

                Map<String, Object> after = jdbc.queryForMap(
                        "SELECT * FROM xsy_finance_payable WHERE source_id = ?", receiptId);
                assertThat(after.get("amount")).isEqualTo(before.get("amount"));
                assertThat(after.get("version")).isEqualTo(before.get("version"));
                assertThat(after.get("updated_at")).isEqualTo(before.get("updated_at"));
                assertThat(count("SELECT count(*) FROM xsy_finance_write_off WHERE source_type = 'PAYMENT' AND source_id = ?",
                        payment.getPaymentId())).as("等额付款也不自动核销").isZero();
            }

            @Test
            @DisplayName("C 供应商两笔完全相同的记录：不同命令即两张付款单（没有自然唯一键）")
            void twoIdenticalSupplierPaymentsAreBothAccepted() {
                Long supplierId = supplier();
                FinancePaymentAddForm first = supplierForm(supplierId, "120.0000");
                String sharedReference = "PM-STATEMENT-777";
                first.setExternalReference(sharedReference);
                FinancePaymentAddForm second = supplierForm(supplierId, "120.0000");
                second.setExternalReference(sharedReference);
                second.setPaidAt(first.getPaidAt());

                Long firstId = add(first, key("same-1")).getPaymentId();
                Long secondId = add(second, key("same-2")).getPaymentId();

                assertThat(firstId).isNotEqualTo(secondId);
                assertThat(paymentsOfSupplier(supplierId))
                        .as("对方 / 金额 / 时点 / 凭据号四项全同也不构成重复记录").isEqualTo(2);

                // 库侧取证：finance_payment 的全部唯一索引只有主键、单据号、来源（含 source_id IS NOT NULL 谓词）
                // 与反向唯一 —— 没有任何按业务字段组合的自然键。
                assertThat(jdbc.queryForList(
                        "SELECT indexdef FROM pg_indexes WHERE tablename = 'xsy_finance_payment'"
                                + " AND indexdef ILIKE '%unique%'", String.class))
                        .allSatisfy(def -> assertThat(def).satisfiesAnyOf(
                                sql -> assertThat(sql).contains("payment_no"),
                                sql -> assertThat(sql).contains("source_type, source_id"),
                                sql -> assertThat(sql).contains("reverse_of_id"),
                                sql -> assertThat(sql).contains("finance_payment_pkey")));
                assertThat(count("SELECT count(*) FROM pg_indexes WHERE tablename = 'xsy_finance_payment'"
                        + " AND indexdef ILIKE '%unique%' AND indexdef ILIKE '%external_reference%'")).isZero();
            }

            // ------------------------------------------------------------------
            // D / E / F / G / H / I / J / K. CUSTOMER 退款付款
            // ------------------------------------------------------------------

            @Test
            @DisplayName("D 退款付款：COMPLETED + 金额逐值相等 + 对方等于退款客户 → 成功且带来源")
            void completedRefundPaymentSucceedsAndCarriesSource() {
                Refund refund = completedRefund("D", "10.0000", "2.0000");
                assertThat(refund.status()).isEqualTo("COMPLETED");
                String exact = refund.refundAmount().toPlainString();

                FinancePaymentVO result = add(refundForm(refund.customerId(), exact, refund.refundId()));

                Map<String, Object> row = paymentRow(result.getPaymentId());
                assertThat(row.get("counterparty_type")).isEqualTo("CUSTOMER");
                assertThat(row.get("counterparty_id")).isEqualTo(refund.customerId());
                assertThat(row.get("source_type")).isEqualTo("ORDER_REFUND");
                assertThat(row.get("source_id")).isEqualTo(refund.refundId());
                assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo(exact);
                assertThat(jdbc.queryForObject("SELECT name FROM xsy_customer WHERE id = ?", String.class,
                        refund.customerId())).isEqualTo(row.get("counterparty_name_snapshot"));
                // 订单侧凭据号绝不复制进财务侧（两域各自的资金凭证）
                assertThat(row.get("external_reference"))
                        .isNotEqualTo("ORDER-SIDE-D")
                        .isEqualTo(result.getExternalReference());
            }

            @Test
            @DisplayName("E 退款仍 PENDING：41139 拒绝，不产生付款")
            void pendingRefundCannotBePaid() {
                Refund refund = completedRefund("E", "10.0000", "1.0000");
                // 撤回到 PENDING：夹具用真链路造单，这里只把状态与完成时点成对改回去
                assertThat(jdbc.update("UPDATE xsy_order_refund SET status = 'PENDING', completed_at = NULL WHERE id = ?",
                        refund.refundId())).isEqualTo(1);
                evictMybatisCache();

                expectCode(() -> add(refundForm(refund.customerId(), refund.refundAmount().toPlainString(),
                        refund.refundId())), 41139);
                assertThat(paymentsOfRefund(refund.refundId())).isZero();
            }

            @Test
            @DisplayName("F 金额不等（含差一分钱与 scale 敏感值）：41139 拒绝，不做 2 位舍入")
            void amountMustMatchRefundExactly() {
                Refund refund = completedRefund("F", "10.0000", "3.0000");
                String exact = refund.refundAmount().toPlainString();

                expectCode(() -> add(refundForm(refund.customerId(),
                        refund.refundAmount().add(new BigDecimal("0.0001")).toPlainString(), refund.refundId())), 41139);
                expectCode(() -> add(refundForm(refund.customerId(),
                        refund.refundAmount().subtract(new BigDecimal("0.0001")).toPlainString(), refund.refundId())), 41139);
                // 「四舍五入到 2 位再比」的实现会在这里放行，因此最后一位非零的金额是判别性夹具
                if (refund.refundAmount().scale() < 4) {
                    // order_refund 是 NUMERIC(18,4)，读回来标度即 4；真到这一步说明夹具不再判别，必须让它响
                    throw new AssertionError("退款金额标度不是 4，判别性夹具失效: " + exact);
                }

                assertThat(paymentsOfRefund(refund.refundId())).isZero();
                assertThat(add(refundForm(refund.customerId(), exact, refund.refundId())).getPaymentId()).isNotNull();
            }

            @Test
            @DisplayName("G 客户与退款不一致：41139 拒绝（付款对方以 order_refund.customer_id 为权威）")
            void counterpartyMustEqualRefundCustomer() {
                Refund refund = completedRefund("G", "10.0000", "1.0000");
                Long otherCustomer = customerOwnedBy(null);

                expectCode(() -> add(refundForm(otherCustomer, refund.refundAmount().toPlainString(),
                        refund.refundId())), 41139);
                assertThat(paymentsOfRefund(refund.refundId())).isZero();
            }

            @Test
            @DisplayName("H 客户无来源付款：本期不支持（客户提现 / 余额退款属 P5），41139 拒绝")
            void customerWithoutSourceIsRejected() {
                Refund refund = completedRefund("H", "10.0000", "1.0000");

                expectCode(() -> add(form("CUSTOMER", refund.customerId(), refund.refundAmount().toPlainString(),
                        "CASH", PAST, null, null)), 41139);
                // 只给一半来源也一样不成对（ck_finance_payment_source_pairing 同向，服务层先给可解释码）
                expectCode(() -> add(form("CUSTOMER", refund.customerId(), refund.refundAmount().toPlainString(),
                        "CASH", PAST, "ORDER_REFUND", null)), 41139);
                expectCode(() -> add(form("CUSTOMER", refund.customerId(), refund.refundAmount().toPlainString(),
                        "CASH", PAST, null, refund.refundId())), 41139);
                assertThat(count("SELECT count(*) FROM xsy_finance_payment"
                        + " WHERE counterparty_type = 'CUSTOMER' AND counterparty_id = ?", refund.customerId()))
                        .isZero();
            }

            @Test
            @DisplayName("I 供应商带 ORDER_REFUND 来源：模式配对错误，41139 拒绝")
            void supplierWithRefundSourceIsRejected() {
                Long supplierId = supplier();
                Refund refund = completedRefund("I", "10.0000", "1.0000");

                expectCode(() -> add(form("SUPPLIER", supplierId, "10.0000", "CASH", PAST,
                        "ORDER_REFUND", refund.refundId())), 41139);
                // 供应商侧只给 sourceId 也一样不成对
                expectCode(() -> add(form("SUPPLIER", supplierId, "10.0000", "CASH", PAST, null, refund.refundId())), 41139);
                assertThat(paymentsOfSupplier(supplierId)).isZero();
            }

            @Test
            @DisplayName("J 不支持的来源及对方类型由后端拒绝，均不产生付款")
            void unsupportedSourceTypeIsRejectedTwice() {
                Long supplierId = supplier();
                Refund refund = completedRefund("J", "10.0000", "1.0000");

                expectCode(() -> add(form("CUSTOMER", refund.customerId(), refund.refundAmount().toPlainString(),
                        "CASH", PAST, "PURCHASE_ORDER", refund.refundId())), 41139);
                expectCode(() -> add(form("CUSTOMER", refund.customerId(), refund.refundAmount().toPlainString(),
                        "CASH", PAST, "SALES_ORDER", 12345L)), 41139);
                assertThat(paymentsOfRefund(refund.refundId()) + paymentsOfSupplier(supplierId))
                        .as("两种越界来源都没写入数据库").isZero();

                expectCode(() -> add(form("WAREHOUSE", supplierId, "10.0000", "CASH", PAST, null, null)), 40000);
                assertThat(paymentsOfRefund(refund.refundId()) + paymentsOfSupplier(supplierId)).isZero();
            }

            @Test
            @DisplayName("K 同一退款顺序重复付款（两个不同防重复请求编号）：第二次被来源唯一索引拒绝")
            void secondPaymentForSameRefundIsRejectedBySourceUnique() {
                Refund refund = completedRefund("K", "10.0000", "2.0000");
                String exact = refund.refundAmount().toPlainString();

                Long firstId = add(refundForm(refund.customerId(), exact, refund.refundId()), key("k1")).getPaymentId();
                expectCode(() -> add(refundForm(refund.customerId(), exact, refund.refundId()), key("k2")), 41139);

                assertThat(paymentsOfRefund(refund.refundId())).isEqualTo(1);
                assertThat(payLogsOfRefund(refund.refundId())).as("失败的那次不留日志").isEqualTo(1);
                assertThat(paymentRow(firstId).get("source_id")).isEqualTo(refund.refundId());
                // 异常必须是业务码，不能把 DuplicateKeyException / 约束名泄漏到接口
                assertThatThrownBy(() -> add(refundForm(refund.customerId(), exact, refund.refundId()), key("k3")))
                        .isInstanceOf(com.xsy.scm.common.exception.ScmBusinessException.class)
                        .hasMessageNotContaining("uk_finance_payment_source_active")
                        .hasMessageNotContaining("DuplicateKey");
            }

            // ------------------------------------------------------------------
            // M. 付款不二次冲减应收
            // ------------------------------------------------------------------

            @Test
            @DisplayName("M 退款付款成立后：正常应收与红字应收一字未改、零核销")
            void paymentNeverReducesReceivableTwice() {
                Refund refund = completedRefund("M", "10.0000", "2.0000");
                // 该退货发生在签收之前，因此批准时成功跳过、库里此刻没有红字。
                // 直接按 schema 补两张应收记录作为「已存在」的前置，不依赖签收链路。
                jdbc.update("INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id, order_id,"
                                + " customer_id, customer_name_snapshot, settlement_customer_id,"
                                + " settlement_customer_name_snapshot, entry_type, amount, event_at,"
                                + " created_at, updated_at, created_by)"
                                + " VALUES ('AR-M-NORMAL', 'SALES_ORDER', ?, ?, ?, '夹具客户', ?, '夹具客户', 'NORMAL', 80.0000,"
                                + " now(), now(), now(), ?)",
                        refund.orderId(), refund.orderId(), refund.customerId(), refund.customerId(), currentOperator());
                Long normalId = jdbc.queryForObject(
                        "SELECT id FROM xsy_finance_receivable WHERE receivable_no = 'AR-M-NORMAL'", Long.class);
                jdbc.update("INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id, order_id,"
                                + " customer_id, customer_name_snapshot, settlement_customer_id,"
                                + " settlement_customer_name_snapshot, entry_type, amount, event_at,"
                                + " original_receivable_id, reason, created_at, updated_at, created_by)"
                                + " VALUES ('AR-M-RED', 'ORDER_RETURN', ?, ?, ?, '夹具客户', ?, '夹具客户', 'RED', 20.0000,"
                                + " now(), ?, '品质问题', now(), now(), ?)",
                        refund.refundId(), refund.orderId(), refund.customerId(), refund.customerId(), normalId, currentOperator());
                evictMybatisCache();
                List<Map<String, Object>> before = jdbc.queryForList(
                        "SELECT * FROM xsy_finance_receivable WHERE order_id = ? ORDER BY entry_type", refund.orderId());
                assertThat(before).hasSize(2);

                FinancePaymentVO payment = add(refundForm(
                        refund.customerId(), refund.refundAmount().toPlainString(), refund.refundId()));

                List<Map<String, Object>> after = jdbc.queryForList(
                        "SELECT * FROM xsy_finance_receivable WHERE order_id = ? ORDER BY entry_type", refund.orderId());
                assertThat(after).hasSize(2);
                for (int index = 0; index < after.size(); index++) {
                    assertThat(after.get(index).get("amount")).isEqualTo(before.get(index).get("amount"));
                    assertThat(after.get(index).get("version")).isEqualTo(before.get(index).get("version"));
                    assertThat(after.get(index).get("updated_at")).isEqualTo(before.get(index).get("updated_at"));
                }
                assertThat(count("SELECT count(*) FROM xsy_finance_write_off WHERE source_type = 'PAYMENT' AND source_id = ?",
                        payment.getPaymentId())).as("退款付款不自动生成核销").isZero();
                assertThat(count("SELECT count(*) FROM xsy_finance_receivable WHERE source_id = ? AND entry_type = 'RED'"
                        + " AND receivable_no <> 'AR-M-RED'", refund.refundId())).as("不追加第二张红字").isZero();
            }

            // ------------------------------------------------------------------
            // O / P / Q / R / S. 时点、方式与金额形态
            // ------------------------------------------------------------------

            @Test
            @DisplayName("O paid_at 由调用方给出并逐值写入数据库；P 未来时点不被拒绝")
            void paidAtIsTheCallerSuppliedMoment() {
                Long supplierId = supplier();
                Long receiptId = add(supplierForm(supplierId, "31.0000")).getPaymentId();

                assertThat(timestampOf(paymentRow(receiptId).get("paid_at")).toInstant()).isEqualTo(PAST.toInstant());
                assertThat(jdbc.queryForObject(
                        "SELECT paid_at = ?::timestamptz FROM xsy_finance_payment WHERE id = ?",
                        Boolean.class, PAST.toInstant().toString(), receiptId)).isTrue();

                // 夹具取到微秒：timestamptz 只到微秒且会舍入纳秒，归一放夹具侧才能保持逐值判等
                OffsetDateTime future = OffsetDateTime.now().plusDays(21).truncatedTo(ChronoUnit.MICROS);
                Long futureId = add(form("SUPPLIER", supplierId, "32.0000", "CASH", future, null, null)).getPaymentId();
                assertThat(timestampOf(paymentRow(futureId).get("paid_at")).toInstant()).isEqualTo(future.toInstant());
            }

            @Test
            @DisplayName("Q 方式三值与收款共用同一枚举；R 越界方式由后端41140拒绝")
            void methodSharesTheReceiptEnumAndRejectsEverythingElse() {
                Long supplierId = supplier();
                assertThat(add(form("SUPPLIER", supplierId, "1.0000", "CASH", PAST, null, null)).getMethod())
                        .isEqualTo("CASH");
                assertThat(add(form("SUPPLIER", supplierId, "1.0000", "BANK_TRANSFER", PAST, null, null)).getMethod())
                        .isEqualTo("BANK_TRANSFER");
                assertThat(add(form("SUPPLIER", supplierId, "1.0000", "OTHER", PAST, null, null)).getMethod())
                        .isEqualTo("OTHER");

                expectCode(() -> add(form("SUPPLIER", supplierId, "1.0000", "WECHAT_PAY", PAST, null, null)), 41140);
                expectCode(() -> add(form("SUPPLIER", supplierId, "1.0000", "BALANCE", PAST, null, null)), 41140);
                expectCode(() -> add(form("SUPPLIER", supplierId, "1.0000", "RECHARGE", PAST, null, null)), 41140);
                assertThat(paymentsOfSupplier(supplierId)).isEqualTo(3);

                expectCode(() -> add(form("SUPPLIER", supplierId, "1.0000", "ONLINE_PAYMENT", PAST, null, null)), 41140);
                assertThat(paymentsOfSupplier(supplierId)).isEqualTo(3);
            }

            @Test
            @DisplayName("S 金额形态与收款同一约定：0 / 负 / 超精度 / 科学计数 / NaN 全拒，合法值补齐 scale 4")
            void amountUsesTheSharedStrictDecimalContract() {
                Long supplierId = supplier();
                expectCode(() -> add(supplierForm(supplierId, "0")), 40000);
                expectCode(() -> add(supplierForm(supplierId, "0.0000")), 40000);
                expectCode(() -> add(supplierForm(supplierId, "-1.0000")), 40000);
                expectCode(() -> add(supplierForm(supplierId, "12.34567")), 40000);
                expectCode(() -> add(supplierForm(supplierId, "1e5")), 40000);
                expectCode(() -> add(supplierForm(supplierId, "NaN")), 40000);
                assertThat(paymentsOfSupplier(supplierId)).isZero();

                Long preciseId = add(supplierForm(supplierId, "99.9999")).getPaymentId();
                assertThat((BigDecimal) paymentRow(preciseId).get("amount")).isEqualByComparingTo("99.9999");
                assertThat(((BigDecimal) paymentRow(preciseId).get("amount")).scale()).as("写入数据库标度恒 4").isEqualTo(4);
                Long paddedId = add(supplierForm(supplierId, "10")).getPaymentId();
                assertThat(((BigDecimal) paymentRow(paddedId).get("amount")).toPlainString()).isEqualTo("10.0000");
            }

            // ------------------------------------------------------------------
            // T / U. 数据范围
            // ------------------------------------------------------------------

            @Test
            @DisplayName("T 退款付款范围：客户归属本人可付；他人客户与未分配客户都校验失败时拒绝操作")
            void customerScopeFollowsSellerIdAndFailsClosed() {
                Refund mine = completedRefund("T-MINE", "10.0000", "1.0000");
                Refund theirs = completedRefund("T-THEIRS", "10.0000", "1.0000");
                Long employeeId = newPlainEmployee("T");
                jdbc.update("UPDATE xsy_customer SET seller_id = ? WHERE id = ?", employeeId, mine.customerId());
                Long otherSeller = newPlainEmployee("T-OTHER");
                jdbc.update("UPDATE xsy_customer SET seller_id = ? WHERE id = ?", otherSeller, theirs.customerId());
                evictMybatisCache();

                try {
                    asEmployee(employeeId);
                    // 自己的客户：可付（金额取库上真值）
                    String mineAmount = jdbc.queryForObject("SELECT refund_amount FROM xsy_order_refund WHERE id = ?",
                            BigDecimal.class, mine.refundId()).toPlainString();
                    assertThat(add(refundForm(mine.customerId(), mineAmount, mine.refundId())).getPaymentId())
                            .isNotNull();

                    // 别人的客户与不存在的退款必须给出<b>同一个</b>码，否则「存在但不是你的」就成了探测信号
                    String theirsAmount = jdbc.queryForObject("SELECT refund_amount FROM xsy_order_refund WHERE id = ?",
                            BigDecimal.class, theirs.refundId()).toPlainString();
                    expectCode(() -> add(refundForm(theirs.customerId(), theirsAmount, theirs.refundId())), 41139);
                    expectCode(() -> add(refundForm(theirs.customerId(), theirsAmount, 999999999L)), 41139);
                    assertThat(paymentsOfRefund(theirs.refundId())).isZero();
                } finally {
                    asAdmin();
                }

                // 未分配业务员的客户：非超管不可代其付款（completedRefund 造出来的客户 seller_id 为 NULL）
                Refund unassigned = completedRefund("T-NONE", "10.0000", "1.0000");
                try {
                    asEmployee(newPlainEmployee("T2"));
                    expectCode(() -> add(refundForm(unassigned.customerId(),
                            unassigned.refundAmount().toPlainString(), unassigned.refundId())), 41139);
                } finally {
                    asAdmin();
                    assertThat(add(refundForm(unassigned.customerId(),
                            unassigned.refundAmount().toPlainString(), unassigned.refundId())).getPaymentId())
                            .as("全范围账号可以付，证明拒绝来自范围而不是字段形态").isNotNull();
                }
            }

            @Test
            @DisplayName("U 供应商付款不受 purchaserScope / 仓库范围影响（D-5 的「无行级收窄」来自裁决不是角色 bypass）")
            void supplierPaymentIsNotNarrowedByPurchaserOrWarehouseScope() {
                Long supplierId = supplier();
                Long employeeId = newPlainEmployee("U");
                try {
                    asEmployee(employeeId);
                    // 该账号没有任何角色、没有任何仓库 / 采购员范围授权，连一张应付都不属于它；
                    // 供应商侧不受销售归属范围收窄，因此付款照样成立。
                    FinancePaymentVO result = add(supplierForm(supplierId, "77.0000"));
                    assertThat(result.getPaymentId()).isNotNull();
                    assertThat(paymentRow(result.getPaymentId()).get("created_by"))
                            .as("登记人是这个非超管账号本人，不是超管兜底")
                            .isEqualTo(currentOperatorOf(employeeId));
                } finally {
                    asAdmin();
                }
                assertThat(paymentsOfSupplier(supplierId)).isEqualTo(1);
            }

            /** {@code ScmOperator.current()} 的规则：{@code userType.getValue() + ":" + employeeId}。 */
            private static String currentOperatorOf(Long employeeId) {
                return UserTypeEnum.ADMIN_EMPLOYEE.getValue() + ":" + employeeId;
            }

            // ------------------------------------------------------------------
            // V / W / X. 防重复处理与日志
            // ------------------------------------------------------------------

            @Test
            @DisplayName("V 重复请求返回原结果：同键同内容返回同一张付款单，库里一张、日志一条")
            void sameKeyAndContentReplaysTheFirstResult() {
                Long supplierId = supplier();
                FinancePaymentAddForm form = supplierForm(supplierId, "210.0000");
                String idempotencyKey = key("replay");

                FinancePaymentVO first = add(form, idempotencyKey);
                FinancePaymentVO replay = add(form, idempotencyKey);

                assertThat(replay.getPaymentId()).isEqualTo(first.getPaymentId());
                assertThat(replay.getPaymentNo()).isEqualTo(first.getPaymentNo());
                assertThat(replay.getAmount()).isEqualByComparingTo(first.getAmount());
                assertThat(replay.getSourceType()).isNull();
                assertThat(paymentsOfSupplier(supplierId)).isEqualTo(1);
                assertThat(logsOf(first.getPaymentId())).hasSize(1);
                assertThat(count("SELECT count(*) FROM xsy_idempotency_record WHERE idempotency_key = ?", idempotencyKey))
                        .isEqualTo(1);
            }

            @Test
            @DisplayName("CUSTOMER 付款重放重新校验当前客户归属，拒绝时不追加记录、日志或防重复处理行")
            void customerReplayRechecksCurrentSellerScope() {
                Long employeeA = newPlainEmployee("PRA");
                Long employeeB = newPlainEmployee("PRB");
                Refund refund = completedRefund("CUSTOMER-REPLAY", "10.0000", "2.0000");
                assertThat(jdbc.update("UPDATE xsy_customer SET seller_id = ? WHERE id = ?", employeeA, refund.customerId()))
                        .isEqualTo(1);
                evictMybatisCache();

                FinancePaymentAddForm request = refundForm(
                        refund.customerId(), refund.refundAmount().toPlainString(), refund.refundId());
                String idempotencyKey = key("customer-replay-scope");
                try {
                    asEmployee(employeeA);
                    FinancePaymentVO first = add(request, idempotencyKey);
                    FinancePaymentVO replay = add(request, idempotencyKey);

                    assertThat(replay.getPaymentId()).isEqualTo(first.getPaymentId());
                    assertThat(paymentsOfRefund(refund.refundId())).isEqualTo(1);
                    assertThat(payLogsOfRefund(refund.refundId())).isEqualTo(1);
                    assertThat(count("SELECT count(*) FROM xsy_idempotency_record WHERE idempotency_key = ?", idempotencyKey))
                            .isEqualTo(1);

                    assertThat(jdbc.update("UPDATE xsy_customer SET seller_id = ? WHERE id = ?", employeeB, refund.customerId()))
                            .isEqualTo(1);
                    evictMybatisCache();

                    expectCode(() -> add(request, idempotencyKey), 41139);
                } finally {
                    asAdmin();
                }

                assertThat(paymentsOfRefund(refund.refundId())).isEqualTo(1);
                assertThat(payLogsOfRefund(refund.refundId())).isEqualTo(1);
                assertThat(count("SELECT count(*) FROM xsy_idempotency_record WHERE idempotency_key = ?", idempotencyKey))
                        .isEqualTo(1);
            }

            @Test
            @DisplayName("W 防重复处理冲突 40966 与缺键 40069：两种失败都不产生付款")
            void conflictAndMissingKeyProduceNothing() {
                Long supplierId = supplier();
                String idempotencyKey = key("conflict");
                add(supplierForm(supplierId, "10.0000"), idempotencyKey);

                expectCode(() -> add(supplierForm(supplierId, "90.0000"), idempotencyKey), 40966);
                expectCode(() -> add(supplierForm(supplierId, "10.0000"), "  "), 40069);

                assertThat(paymentsOfSupplier(supplierId)).isEqualTo(1);
                assertThat(payLogsOfSupplier(supplierId)).isEqualTo(1);
            }

            @Test
            @DisplayName("X 操作日志 PAY：改前为空、保存的数据含来源与对方、操作人是登记人；重放不追加")
            void operationLogPaysTheRegistration() throws Exception {
                Refund refund = completedRefund("X", "10.0000", "2.0000");
                // 重放必须用同一把 key 与同一份请求内容：换 key 就是另一条命令（会被来源唯一索引
                // 按重复记录拒掉），换内容则按既有语义报 40966 —— 两者都不是「重放」。
                String idempotencyKey = key("x");
                FinancePaymentAddForm form = refundForm(refund.customerId(),
                        refund.refundAmount().toPlainString(), refund.refundId());
                FinancePaymentVO result = financePaymentService.add(form, idempotencyKey);

                List<Map<String, Object>> logs = logsOf(result.getPaymentId());
                assertThat(logs).hasSize(1);
                Map<String, Object> log = logs.getFirst();
                assertThat(log.get("operation_type")).isEqualTo("PAY");
                assertThat(log.get("before_data")).isNull();
                assertThat(log.get("operator")).isEqualTo(currentOperator());
                assertThat(log.get("reason")).isNull();

                Map<String, Object> after = objectMapper.readValue(String.valueOf(log.get("after_data")),
                        new TypeReference<Map<String, Object>>() {
                        });
                assertThat(after)
                        .containsEntry("paymentNo", result.getPaymentNo())
                        .containsEntry("counterpartyType", "CUSTOMER")
                        .containsEntry("amount", refund.refundAmount().toPlainString())
                        .containsEntry("method", "BANK_TRANSFER")
                        .containsEntry("sourceType", "ORDER_REFUND")
                        .containsEntry("entryType", "NORMAL");
                assertThat(((Number) after.get("counterpartyId")).longValue()).isEqualTo(refund.customerId());
                assertThat(((Number) after.get("sourceId")).longValue()).isEqualTo(refund.refundId());
                // 时点一律以 ISO 字符串入档（JSONB 侧的 typeHandler 未注册 JavaTimeModule），
                // 因此这里比对的是登记人给的那个带偏移的业务时点本身
                assertThat(after.get("paidAt")).isEqualTo(PAST.toString());

                // 同一笔付款不会因为重放再产生第二条日志
                FinancePaymentVO replay = financePaymentService.add(form, idempotencyKey);
                assertThat(replay.getPaymentId()).isEqualTo(result.getPaymentId());
                assertThat(logsOf(result.getPaymentId())).hasSize(1);
                assertThat(paymentsOfRefund(refund.refundId())).isEqualTo(1);
            }

            @Test
            @DisplayName("付款单号 = XSY + 雪花 ID，两次付款不重复")
            void paymentNumberUsesSnowflake() {
                Long supplierId = supplier();
                String firstNo = add(supplierForm(supplierId, "1.0000")).getPaymentNo();
                expectCode(() -> add(supplierForm(supplierId, "0")), 40000);
                String secondNo = add(supplierForm(supplierId, "2.0000")).getPaymentNo();

                assertThat(firstNo).matches("^XSY\\d{18,19}$");
                assertThat(secondNo).matches("^XSY\\d{18,19}$");
                assertThat(secondNo).isNotEqualTo(firstNo);
            }
        }

        @Nested
        @DisplayName("Finance R1 只读查询与 Excel 导出（PG IT）")
        class ScmFinanceReadPgIT {
            private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.ofHours(8));

            @Autowired
            private FinanceReceivableQueryService receivableQueries;

            @Autowired
            private FinancePayableQueryService payableQueries;

            @Autowired
            private FinanceReceiptQueryService receiptQueries;

            @Autowired
            private FinancePaymentQueryService paymentQueries;

            @Autowired
            private FinanceRefundOptionQueryService refundOptionQueries;

            @Autowired
            private FinanceWriteOffQueryService writeOffQueries;

            @Autowired
            private FinanceReceiptService receiptService;

            @Autowired
            private FinancePaymentService paymentService;

            @Autowired
            private FinancePayableService payableService;

            @Autowired
            private FinanceWriteOffService writeOffService;

            @Autowired
            private FinanceOperationLogQueryService operationLogQueries;

            @Autowired
            private FinanceReadController readController;

            @Autowired
            private FinanceWriteOffController writeOffController;

            @Test
            @DisplayName("应收查询派生余额、详情来源链和导出共用同一筛选")
            void receivableQueryDetailAndExport() throws Exception {
                Long customerId = newCustomer();
                Long normalId = normalReceivable(customerId, "100.0000");
                FinanceReceiptAddForm receiptForm = new FinanceReceiptAddForm();
                receiptForm.setCustomerId(customerId);
                receiptForm.setAmount("90.0000");
                receiptForm.setMethod("CASH");
                receiptForm.setReceivedAt(NOW);
                var receipt = receiptService.add(receiptForm, key("ar-receipt"));
                writeOffService.add(writeOffForm("RECEIPT", receipt.getReceiptId(), normalId, "90.0000"), key("ar-apply"));
                insertRedReceivable(normalId, customerId, "20.0000");

                FinanceReceivableQueryForm form = dateRange(new FinanceReceivableQueryForm());
                form.setReceivableId(normalId);
                form.setSettleState("PARTIAL");
                var page = receivableQueries.query(form);

                assertThat(page.getTotal()).isEqualTo(1L);
                assertThat(page.getList().getFirst().getNetAmount()).isEqualByComparingTo("80.0000");
                assertThat(page.getList().getFirst().getOpenAmount()).isEqualByComparingTo("0.0000");
                assertThat(page.getList().getFirst().getOverAppliedAmount()).isEqualByComparingTo("10.0000");
                var detail = receivableQueries.detail(normalId);
                assertThat(detail.getRedEntries()).hasSize(1);
                assertThat(detail.getItems()).hasSize(1);
                assertThat(detail.getWriteOffs()).hasSize(1);
                assertWorkbook("应收单号", page.getList().getFirst().getReceivableNo(),
                        response -> readController.exportReceivables(form, response), 12);
            }

            @Test
            @DisplayName("应付查询按收货负责人范围派生余额并导出红字记录")
            void payableQueryDetailAndExport() throws Exception {
                Long supplierId = newSupplier("READ-AP");
                Long normalId = normalPayable(supplierId, "100.0000");
                FinancePaymentAddForm paymentForm = supplierPayment(supplierId, "90.0000");
                var payment = paymentService.add(paymentForm, key("read-ap-payment"));
                writeOffService.add(writeOffForm("PAYMENT", payment.getPaymentId(), normalId, "90.0000"), key("read-ap-apply"));
                FinancePayableRedForm redForm = payableRedForm(normalId, redItem("2.0000", "10.0000"));
                payableService.red(redForm, key("read-ap-red"));

                FinancePayableQueryForm form = dateRange(new FinancePayableQueryForm());
                form.setPayableId(normalId);
                form.setSupplierName("只读测试供应商");
                form.setSettleState("PARTIAL");
                var page = payableQueries.query(form);

                assertThat(page.getTotal()).isEqualTo(1L);
                assertThat(page.getList().getFirst().getNetAmount()).isEqualByComparingTo("80.0000");
                assertThat(page.getList().getFirst().getOverAppliedAmount()).isEqualByComparingTo("10.0000");
                var detail = payableQueries.detail(normalId);
                assertThat(detail.getRedEntries()).hasSize(1);
                assertThat(detail.getItems()).hasSize(1);
                assertThat(detail.getWriteOffs()).hasSize(1);
                assertWorkbook("应付单号", page.getList().getFirst().getPayableNo(),
                        response -> readController.exportPayables(form, response), 12);
            }

            @Test
            @DisplayName("收款查询和导出显示有效额、已用额与待核销额")
            void receiptQueryDetailAndExport() throws Exception {
                Long customerId = newCustomer();
                FinanceReceiptAddForm addForm = new FinanceReceiptAddForm();
                addForm.setCustomerId(customerId);
                addForm.setAmount("100.0000");
                addForm.setMethod("BANK_TRANSFER");
                addForm.setReceivedAt(NOW);
                var receipt = receiptService.add(addForm, key("read-receipt"));
                Long targetId = normalReceivable(customerId, "100.0000");
                writeOffService.add(writeOffForm("RECEIPT", receipt.getReceiptId(), targetId, "40.0000"), key("read-receipt-apply"));

                FinanceReceiptQueryForm form = dateRange(new FinanceReceiptQueryForm());
                form.setReceiptId(receipt.getReceiptId());
                var page = receiptQueries.query(form);

                assertThat(page.getTotal()).isEqualTo(1L);
                assertThat(page.getList().getFirst().getEffectiveAmount()).isEqualByComparingTo("100.0000");
                assertThat(page.getList().getFirst().getUsedAmount()).isEqualByComparingTo("40.0000");
                assertThat(page.getList().getFirst().getPendingWriteOffAmount()).isEqualByComparingTo("60.0000");
                assertThat(receiptQueries.detail(receipt.getReceiptId()).getWriteOffs()).hasSize(1);
                assertWorkbook("收款单号", page.getList().getFirst().getReceiptNo(),
                        response -> readController.exportReceipts(form, response), 11);

                FinanceOperationLogQueryForm logForm = new FinanceOperationLogQueryForm();
                logForm.setBusinessType("RECEIPT");
                logForm.setBusinessId(receipt.getReceiptId());
                try (MockedStatic<StpUtil> stp = Mockito.mockStatic(StpUtil.class)) {
                    stp.when(() -> StpUtil.checkPermission(FinancePermission.RECEIPT_QUERY)).thenAnswer(invocation -> null);
                    assertThat(operationLogQueries.query(logForm)).extracting(row -> row.getOperationType())
                            .containsExactly("RECEIVE");
                }
            }

            @Test
            @DisplayName("付款查询沿用供应商共享范围，导出退款来源保存的数据")
            void paymentQueryDetailAndExport() throws Exception {
                Long supplierId = newSupplier("READ-PM");
                FinancePaymentAddForm addForm = supplierPayment(supplierId, "80.0000");
                var payment = paymentService.add(addForm, key("read-payment"));
                Long payableId = normalPayable(supplierId, "80.0000");
                writeOffService.add(writeOffForm("PAYMENT", payment.getPaymentId(), payableId, "30.0000"), key("read-payment-apply"));

                FinancePaymentQueryForm form = dateRange(new FinancePaymentQueryForm());
                form.setPaymentId(payment.getPaymentId());
                form.setCounterpartyName(jdbc.queryForObject("SELECT counterparty_name_snapshot FROM xsy_finance_payment WHERE id = ?",
                        String.class, payment.getPaymentId()));
                var page = paymentQueries.query(form);

                assertThat(page.getTotal()).isEqualTo(1L);
                assertThat(page.getList().getFirst().getEffectiveAmount()).isEqualByComparingTo("80.0000");
                assertThat(page.getList().getFirst().getUsedAmount()).isEqualByComparingTo("30.0000");
                assertThat(paymentQueries.detail(payment.getPaymentId()).getWriteOffs()).hasSize(1);
                assertWorkbook("付款单号", page.getList().getFirst().getPaymentNo(),
                        response -> readController.exportPayments(form, response), 12);
            }

            @Test
            @DisplayName("客户付款退款选择器只返回范围内已完成且尚未付款的退款")
            void refundOptionPickerReturnsOnlyEligibleRefunds() {
                Long customerId = newCustomer();
                String refundNo = "RF-READ-" + prefix + "-" + UUID.randomUUID();
                Long refundId = jdbc.queryForObject(
                        "INSERT INTO xsy_order_refund (refund_no, return_id, order_id, customer_id, refund_amount, status, completed_at)"
                                + " VALUES (?, ?, ?, ?, 25.5000, 'COMPLETED', ?) RETURNING id",
                        Long.class, refundNo, uniqueNumber(), uniqueNumber(), customerId, NOW);

                FinanceRefundOptionQueryForm form = new FinanceRefundOptionQueryForm();
                form.setKeyword(refundNo);
                var options = refundOptionQueries.query(form);
                assertThat(options.getTotal()).isEqualTo(1L);
                var option = options.getList().getFirst();
                assertThat(option.getRefundId()).isEqualTo(refundId);
                assertThat(option.getRefundNo()).isEqualTo(refundNo);
                assertThat(option.getCustomerId()).isEqualTo(customerId);
                assertThat(option.getRefundAmount()).isEqualByComparingTo("25.5000");

                FinancePaymentAddForm payment = new FinancePaymentAddForm();
                payment.setCounterpartyType("CUSTOMER");
                payment.setCounterpartyId(customerId);
                payment.setAmount("25.5000");
                payment.setMethod("BANK_TRANSFER");
                payment.setPaidAt(NOW);
                payment.setSourceType("ORDER_REFUND");
                payment.setSourceId(refundId);
                paymentService.add(payment, key("refund-option-paid"));

                var afterPayment = refundOptionQueries.query(form);
                assertThat(afterPayment.getTotal()).isZero();
                assertThat(afterPayment.getList()).isEmpty();
            }

            @Test
            @DisplayName("核销导出复用目标范围与列表筛选")
            void writeOffQueryAndExport() throws Exception {
                Long customerId = newCustomer();
                FinanceReceiptAddForm receiptForm = new FinanceReceiptAddForm();
                receiptForm.setCustomerId(customerId);
                receiptForm.setAmount("30.0000");
                receiptForm.setMethod("CASH");
                receiptForm.setReceivedAt(NOW);
                var receipt = receiptService.add(receiptForm, key("read-writeoff-receipt"));
                Long targetId = normalReceivable(customerId, "30.0000");
                var writeOff = writeOffService.add(writeOffForm("RECEIPT", receipt.getReceiptId(), targetId, "10.0000"),
                        key("read-writeoff-add")).getItems().getFirst();
                FinanceWriteOffQueryForm form = dateRange(new FinanceWriteOffQueryForm());
                form.setWriteOffId(writeOff.getWriteOffId());

                assertThat(writeOffQueries.query(form).getList()).singleElement()
                        .satisfies(row -> assertThat(row.getWriteOffId()).isEqualTo(writeOff.getWriteOffId()));
                assertWorkbook("抵扣单号", writeOff.getWriteOffNo(), response -> writeOffController.export(form, response), 12);
            }

            private void assertWorkbook(String firstHeader, String firstData, WorkbookCall export, int columnCount)
                    throws Exception {
                MockHttpServletResponse response = new MockHttpServletResponse();
                export.run(response);
                byte[] bytes = response.getContentAsByteArray();
                assertThat(bytes.length).isGreaterThan(500);
                try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
                    assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
                    var sheet = workbook.getSheetAt(0);
                    assertThat(sheet.getPhysicalNumberOfRows()).isGreaterThanOrEqualTo(2);
                    assertThat(sheet.getRow(0).getPhysicalNumberOfCells()).isEqualTo(columnCount);
                    assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo(firstHeader);
                    assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo(firstData);
                }
            }

            private <T extends FinanceDatePageForm> T dateRange(T form) {
                LocalDate today = LocalDate.now(ZoneOffset.ofHours(8));
                form.setStartDate(today.minusDays(1));
                form.setEndDate(today.plusDays(1));
                return form;
            }

            private Long normalReceivable(Long customerId, String amount) {
                Long skuId = newOnShelfSku("READ-AR");
                Long orderId = confirmedSalesOrder(customerId, skuId, "1.0000", "1.0000");
                Long receivableId = jdbc.queryForObject("INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id,"
                                + " order_id, customer_id, customer_name_snapshot, settlement_customer_id,"
                                + " settlement_customer_name_snapshot, entry_type, amount, event_at)"
                                + " VALUES (?, 'SALES_ORDER', ?, ?, ?, '只读测试客户', ?, '只读测试客户', 'NORMAL', ?, now()) RETURNING id",
                        Long.class, "AR-READ-" + prefix + "-" + UUID.randomUUID(), orderId, orderId, customerId, customerId,
                        new BigDecimal(amount));
                jdbc.update("INSERT INTO xsy_finance_receivable_item (receivable_id, source_type, source_id, order_item_id,"
                                + " sku_id, sku_name_snapshot, unit_snapshot, quantity, unit_price, amount)"
                                + " VALUES (?, 'INVENTORY_OUTBOUND_ITEM', ?, 1, 1, '只读测试商品', 'kg', 1.0000, ?, ?)",
                        receivableId, uniqueNumber(), new BigDecimal(amount), new BigDecimal(amount));
                return receivableId;
            }

            private void insertRedReceivable(Long originalId, Long customerId, String amount) {
                jdbc.update("INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id, order_id, customer_id,"
                                + " customer_name_snapshot, settlement_customer_id, settlement_customer_name_snapshot,"
                                + " entry_type, original_receivable_id, amount, event_at, reason)"
                                + " VALUES (?, 'ORDER_RETURN', ?, ?, ?, '只读测试客户', ?, '只读测试客户', 'RED', ?, ?, now(), '只读测试红字')",
                        "AR-READ-RED-" + prefix + "-" + UUID.randomUUID(), uniqueNumber(),
                        jdbc.queryForObject("SELECT order_id FROM xsy_finance_receivable WHERE id = ?", Long.class, originalId),
                        customerId, customerId, originalId, new BigDecimal(amount));
            }

            private Long normalPayable(Long supplierId, String amount) {
                long receiptId = uniqueNumber();
                Long payableId = jdbc.queryForObject("INSERT INTO xsy_finance_payable (payable_no, source_type, source_id,"
                                + " purchase_order_id, supplier_id, supplier_name_snapshot, entry_type, amount, event_at)"
                                + " VALUES (?, 'PURCHASE_RECEIPT', ?, 1, ?, '只读测试供应商', 'NORMAL', ?, now()) RETURNING id",
                        Long.class, "AP-READ-" + prefix + "-" + UUID.randomUUID(), receiptId, supplierId,
                        new BigDecimal(amount));
                jdbc.update("INSERT INTO xsy_finance_payable_item (payable_id, source_type, source_id, purchase_order_item_id,"
                                + " sku_id, sku_name_snapshot, unit_snapshot, quantity, unit_price, amount)"
                                + " VALUES (?, 'PURCHASE_RECEIPT_ITEM', ?, 44, 1, '只读测试商品', 'kg', 1.0000, ?, ?)",
                        payableId, uniqueNumber(), new BigDecimal(amount), new BigDecimal(amount));
                return payableId;
            }

            private FinancePayableRedForm payableRedForm(Long originalId, FinancePayableRedItemForm item) {
                FinancePayableRedForm form = new FinancePayableRedForm();
                form.setOriginalPayableId(originalId);
                form.setReason("只读测试红字");
                form.setItems(List.of(item));
                return form;
            }

            private FinancePayableRedItemForm redItem(String quantity, String price) {
                FinancePayableRedItemForm form = new FinancePayableRedItemForm();
                form.setPurchaseOrderItemId(44L);
                form.setQuantity(quantity);
                form.setUnitPrice(price);
                form.setAmount(new BigDecimal(quantity).multiply(new BigDecimal(price))
                        .setScale(4, java.math.RoundingMode.HALF_UP).toPlainString());
                return form;
            }

            private FinanceWriteOffAddForm writeOffForm(String sourceType, Long sourceId, Long targetId, String amount) {
                FinanceWriteOffAddItemForm item = new FinanceWriteOffAddItemForm();
                item.setTargetId(targetId);
                item.setAmount(amount);
                FinanceWriteOffAddForm form = new FinanceWriteOffAddForm();
                form.setSourceType(sourceType);
                form.setSourceId(sourceId);
                form.setItems(List.of(item));
                return form;
            }

            private FinanceReceiptAddForm receiptForm(Long customerId, String amount) {
                FinanceReceiptAddForm form = new FinanceReceiptAddForm();
                form.setCustomerId(customerId);
                form.setAmount(amount);
                form.setMethod("BANK_TRANSFER");
                form.setReceivedAt(NOW);
                return form;
            }

            private FinancePaymentAddForm supplierPayment(Long supplierId, String amount) {
                FinancePaymentAddForm form = new FinancePaymentAddForm();
                form.setCounterpartyType("SUPPLIER");
                form.setCounterpartyId(supplierId);
                form.setAmount(amount);
                form.setMethod("BANK_TRANSFER");
                form.setPaidAt(NOW);
                return form;
            }

            private long uniqueNumber() {
                return 1_000_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 8_000_000_000L);
            }

            private String key(String tag) {
                return prefix + ":" + tag + ":" + UUID.randomUUID();
            }

            @FunctionalInterface
            private interface WorkbookCall {
                void run(MockHttpServletResponse response) throws Exception;
            }
        }

        @Nested
        @DisplayName("收款登记权限发布（F1-3A，PG IT）")
        class ScmFinanceReceiptPermissionPgIT {
            @Autowired
            private LoginManager loginManager;

            private Long employeeWithRole(String tag, String roleCode) {
                String loginName = (prefix + "-" + tag).toUpperCase();
                jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                                + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                        UUID.randomUUID().toString().replace("-", ""), loginName, "$argon2id$it-placeholder", "财务岗" + tag);
                Long employeeId = jdbc.queryForObject(
                        "SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
                assertThat(jdbc.queryForObject(
                        "SELECT administrator_flag FROM t_employee WHERE employee_id = ?", Boolean.class, employeeId))
                        .as("权限取证禁止超管位").isFalse();
                jdbc.update("INSERT INTO t_role_employee (role_id, employee_id)"
                                + " SELECT r.role_id, ? FROM t_role r WHERE r.role_code = ?",
                        employeeId, roleCode);
                evictMybatisCache();
                return employeeId;
            }

            private List<String> permissionsOf(Long employeeId) {
                var employee = new RequestEmployee();
                employee.setEmployeeId(employeeId);
                employee.setActualName("F1-3A 权限取证");
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                employee.setAdministratorFlag(false);
                XsyRequestUtil.setRequestUser(employee);
                // 走真实的角色 → 菜单 → api_perms 装配路径（生产登录用的就是它），不 mock StpUtil
                return loginManager.loadUserPermission(employeeId).getPermissionList();
            }

            @Test
            @DisplayName("SCM_FINANCE 持有收款登记权限；SCM_DRIVER 不持有")
            void financeRoleHoldsReceiptAddPermission() {
                Long financeEmployee = employeeWithRole("FIN", "SCM_FINANCE");
                Long driverEmployee = employeeWithRole("DRV", "SCM_DRIVER");

                assertThat(permissionsOf(financeEmployee)).contains(FinanceConstant.RECEIPT_ADD_PERM);
                assertThat(permissionsOf(driverEmployee)).doesNotContain(FinanceConstant.RECEIPT_ADD_PERM);
            }

            @Test
            @DisplayName("库里发布的串与 Controller 注解声明的串逐字一致")
            void publishedPermissionStringMatchesTheControllerAnnotation() throws Exception {
                Method endpoint = FinanceReceiptController.class
                        .getDeclaredMethod("add",
                                com.xsy.scm.finance.domain.form.FinanceReceiptAddForm.class,
                                String.class);
                SaCheckPermission annotation = endpoint.getAnnotation(SaCheckPermission.class);
                assertThat(annotation).as("收款登记端点必须挂功能权限注解").isNotNull();
                assertThat(annotation.value()).containsExactly(FinanceConstant.RECEIPT_ADD_PERM);

                // 发布链第一段：t_menu 里发布的串必须与注解一致，且只有 SCM_FINANCE 与超管拿到
                assertThat(jdbc.queryForList(
                        "SELECT api_perms FROM t_menu WHERE api_perms = ?", String.class,
                        FinanceConstant.RECEIPT_ADD_PERM))
                        .containsExactly(FinanceConstant.RECEIPT_ADD_PERM);
                assertThat(jdbc.queryForList(
                        "SELECT r.role_code FROM t_role_menu rm JOIN t_role r ON r.role_id = rm.role_id"
                                + " JOIN t_menu m ON m.menu_id = rm.menu_id WHERE m.api_perms = ?"
                                + " AND r.role_code <> 'SUPER_ADMIN' ORDER BY r.role_code",
                        String.class, FinanceConstant.RECEIPT_ADD_PERM))
                        .containsExactly("SCM_FINANCE");
            }

            @Test
            @DisplayName("收款侧发布登记、查询与反向权限，五个财务页面随组件同步发布")
            void nothingElseIsPublishedYet() {
                // 只钉「收款侧」：整个财务段发布了哪几行由 ScmFinanceSchemaPgIT 按 containsExactly 负责，
                // 在这里重复一份，付款侧能力一变就会连带无谓变红。
                assertThat(jdbc.queryForList(
                        "SELECT DISTINCT api_perms FROM t_menu WHERE api_perms LIKE 'scm:finance:receipt:%'"
                                + " ORDER BY api_perms", String.class))
                        .as("F1-3A / F1-3C / F1-5 各自发布真实端点使用的权限")
                        .containsExactly(FinanceConstant.RECEIPT_ADD_PERM, FinanceConstant.RECEIPT_QUERY_PERM,
                                FinanceConstant.RECEIPT_REVERSE_PERM);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM t_menu WHERE menu_id BETWEEN 1500 AND 1599 AND menu_type = 2",
                        Integer.class))
                        .as("已发布六个带真实组件的财务页面")
                        .isEqualTo(6);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM t_menu WHERE menu_id BETWEEN 1501 AND 1506 AND component IS NOT NULL",
                        Integer.class)).isEqualTo(6);
            }
        }

        @Nested
        @DisplayName("收款登记（Finance R1 F1-3A，PG IT）")
        class ScmFinanceReceiptPgIT {
            private static final OffsetDateTime PAST = OffsetDateTime.of(2026, 3, 5, 9, 30, 0, 0, ZoneOffset.ofHours(8));

            @Autowired
            private FinanceReceiptService financeReceiptService;

            // ------------------------------------------------------------------
            // 夹具
            // ------------------------------------------------------------------

            private FinanceReceiptAddForm form(Long customerId, String amount, String method, OffsetDateTime receivedAt) {
                FinanceReceiptAddForm form = new FinanceReceiptAddForm();
                form.setCustomerId(customerId);
                form.setAmount(amount);
                form.setMethod(method);
                form.setReceivedAt(receivedAt);
                form.setExternalReference("BANK-" + UUID.randomUUID().toString().substring(0, 8));
                form.setRemark("F1-3A IT");
                return form;
            }

            private FinanceReceiptVO add(FinanceReceiptAddForm form) {
                return financeReceiptService.add(form, key("add"));
            }

            private String key(String tag) {
                return prefix + ":" + tag + ":" + UUID.randomUUID();
            }

            private Long customerOwnedBy(Long sellerId) {
                Long customerId = newCustomer();
                jdbc.update("UPDATE xsy_customer SET seller_id = ? WHERE id = ?", sellerId, customerId);
                evictMybatisCache();
                return customerId;
            }

            private int count(String sql, Object... args) {
                Integer value = jdbc.queryForObject(sql, Integer.class, args);
                return value == null ? 0 : value;
            }

            private Map<String, Object> receiptRow(Long receiptId) {
                return jdbc.queryForMap("SELECT * FROM xsy_finance_receipt WHERE id = ?", receiptId);
            }

            private List<Map<String, Object>> logsOf(Long receiptId) {
                return jdbc.queryForList(
                        "SELECT * FROM xsy_finance_operation_log WHERE business_type = 'RECEIPT' AND business_id = ?",
                        receiptId);
            }

            /**
             * 本用例客户名下的收款日志条数。
             *
             * <p>必须按客户归属收窄：{@link ScmFinanceReceiptRollbackPgIT} 是 {@code NOT_SUPPORTED}
             * （真提交、不随外层回滚消失），同一个库上先后跑完就会留下既有的 {@code RECEIVE} 日志。
             * 全表计数在那种顺序下不是「本次什么都没产生」的证据。
             */
            private int receiptLogsOfCustomer(Long customerId) {
                return count("SELECT count(*) FROM xsy_finance_operation_log l"
                        + " JOIN xsy_finance_receipt r ON r.id = l.business_id"
                        + " WHERE l.business_type = 'RECEIPT' AND r.customer_id = ?", customerId);
            }

            /** 切换到非超管身份取证数据范围（P0 裁决第 5 条：越权取证禁止用超管位）。 */
            private void asEmployee(Long employeeId) {
                var employee = new RequestEmployee();
                employee.setEmployeeId(employeeId);
                employee.setActualName("F1-3A IT 非超管");
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                employee.setAdministratorFlag(false);
                XsyRequestUtil.setRequestUser(employee);
                evictMybatisCache();
            }

            private void asAdmin() {
                var employee = new RequestEmployee();
                employee.setEmployeeId(1L);
                employee.setActualName("W5 IT");
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                employee.setAdministratorFlag(true);
                XsyRequestUtil.setRequestUser(employee);
                evictMybatisCache();
            }

            private Long newPlainEmployee(String tag) {
                String loginName = (prefix + "-" + tag).toUpperCase();
                jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                        + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                        UUID.randomUUID().toString().replace("-", ""), loginName, "$argon2id$it-placeholder", "财务" + tag);
                Long employeeId = jdbc.queryForObject(
                        "SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
                assertThat(jdbc.queryForObject(
                        "SELECT administrator_flag FROM t_employee WHERE employee_id = ?", Boolean.class, employeeId))
                        .as("范围取证必须用非超管账号").isFalse();
                evictMybatisCache();
                return employeeId;
            }

            // ------------------------------------------------------------------
            // A / B / C. 三种方式正常登记
            // ------------------------------------------------------------------

            @Test
            @DisplayName("CASH 登记：NORMAL 收款写入数据库、单号 RC 前缀、日志 RECEIVE 且保存的数据齐全")
            void cashReceiptWritesFactAndLog() throws Exception {
                Long customerId = customerOwnedBy(null);
                FinanceReceiptVO result = add(form(customerId, "100.0000", "CASH", PAST));

                Map<String, Object> row = receiptRow(result.getReceiptId());
                assertThat(row.get("entry_type")).isEqualTo("NORMAL");
                assertThat(row.get("reverse_of_id")).isNull();
                assertThat(row.get("reason")).isNull();
                assertThat(row.get("method")).isEqualTo("CASH");
                assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("100.0000");
                assertThat(row.get("customer_id")).isEqualTo(customerId);
                assertThat(row.get("deleted")).isEqualTo(false);
                assertThat(String.valueOf(row.get("receipt_no"))).matches("^XSY\\d{18,19}$");
                assertThat(row.get("created_by")).isEqualTo(currentOperator());
                assertThat(timestampOf(row.get("received_at")).toInstant()).isEqualTo(PAST.toInstant());

                List<Map<String, Object>> logs = logsOf(result.getReceiptId());
                assertThat(logs).hasSize(1);
                assertThat(logs.getFirst().get("operation_type")).isEqualTo("RECEIVE");
                assertThat(logs.getFirst().get("before_data")).isNull();
                assertThat(logs.getFirst().get("operator")).isEqualTo(currentOperator());
                // after_data 按 JSON 解析而不是子串匹配：JSONB 的存储形态会重排键序并补空白，
                // 按文本断言等于把测试绑在序列化器的排版细节上。
                Map<String, Object> after = objectMapper.readValue(String.valueOf(logs.getFirst().get("after_data")),
                        new TypeReference<Map<String, Object>>() {
                        });
                assertThat(after)
                        .containsEntry("receiptNo", result.getReceiptNo())
                        .containsEntry("amount", "100.0000")
                        .containsEntry("method", "CASH")
                        .containsEntry("entryType", "NORMAL")
                        .containsEntry("externalReference", row.get("external_reference"))
                        .containsEntry("customerNameSnapshot", row.get("customer_name_snapshot"));
                assertThat(((Number) after.get("customerId")).longValue()).isEqualTo(customerId);
            }

            @Test
            @DisplayName("BANK_TRANSFER 与 OTHER 都在允许集合内，照常写入数据库")
            void bankTransferAndOtherAreAccepted() {
                Long customerId = customerOwnedBy(null);

                assertThat(add(form(customerId, "12.5000", "BANK_TRANSFER", PAST)).getMethod())
                        .isEqualTo("BANK_TRANSFER");
                assertThat(add(form(customerId, "7.0000", "OTHER", PAST)).getMethod()).isEqualTo("OTHER");

                assertThat(count("SELECT count(*) FROM xsy_finance_receipt WHERE customer_id = ?", customerId))
                        .isEqualTo(2);
            }

            // ------------------------------------------------------------------
            // D. 方式三值之外：服务层与库层双层拒绝
            // ------------------------------------------------------------------

            @Test
            @DisplayName("方式越界：渠道名与余额被 41140 拒绝；ONLINE_PAYMENT 人工可登记；直插渠道名被 CHECK 拒绝")
            void methodOutsideReceiptValuesIsRejectedTwice() {
                Long customerId = customerOwnedBy(null);

                // 渠道名不是收款方式：具体渠道写在 external_reference，塞进 method 会让「钱怎么进来的」失去答案
                expectCode(() -> add(form(customerId, "10.0000", "WECHAT_PAY", PAST)), 41140);
                // 余额抵扣必须由余额流水驱动，人工登记会造出「账上有收款、余额没动」的假记录
                expectCode(() -> add(form(customerId, "10.0000", "BALANCE", PAST)), 41140);
                assertThat(count("SELECT count(*) FROM xsy_finance_receipt WHERE customer_id = ?", customerId)).isZero();

                // 在线支付是收款侧的合法方式（ADM-12）：人工登记一笔在线收款是允许的
                assertThat(add(form(customerId, "10.0000", "ONLINE_PAYMENT", PAST)).getMethod())
                        .isEqualTo("ONLINE_PAYMENT");

                // 库层第二道：即使绕过 Java 枚举也进不来
                expectSqlFailure("INSERT INTO xsy_finance_receipt (receipt_no, customer_id, customer_name_snapshot,"
                        + " amount, method, received_at, entry_type) VALUES ('RC-D-1', ?, '客户', 10.0000,"
                        + " 'WECHAT_PAY', now(), 'NORMAL')", customerId);
            }

            // ------------------------------------------------------------------
            // E / F / G / H. 金额形态与 scale
            // ------------------------------------------------------------------

            @Test
            @DisplayName("金额：0 与负数被拒；四位小数原样保存；整数补齐到 scale 4")
            void amountShapeIsNormalizedAndPositive() {
                Long customerId = customerOwnedBy(null);

                expectCode(() -> add(form(customerId, "0", "CASH", PAST)), 40000);
                expectCode(() -> add(form(customerId, "0.0000", "CASH", PAST)), 40000);
                expectCode(() -> add(form(customerId, "-5.0000", "CASH", PAST)), 40000);
                expectCode(() -> add(form(customerId, "12.34567", "CASH", PAST)), 40000);
                expectCode(() -> add(form(customerId, "1e5", "CASH", PAST)), 40000);
                expectCode(() -> add(form(customerId, "NaN", "CASH", PAST)), 40000);
                assertThat(count("SELECT count(*) FROM xsy_finance_receipt WHERE customer_id = ?", customerId)).isZero();

                // 123.4567 原样保存（不四舍五入成 123.4567 → 123.4567，也不截断）
                Long preciseId = add(form(customerId, "123.4567", "CASH", PAST)).getReceiptId();
                assertThat((BigDecimal) receiptRow(preciseId).get("amount")).isEqualByComparingTo("123.4567");
                assertThat(((BigDecimal) receiptRow(preciseId).get("amount")).scale()).as("写入数据库 scale 恒 4").isEqualTo(4);

                // "10" → 10.0000
                Long paddedId = add(form(customerId, "10", "CASH", PAST)).getReceiptId();
                assertThat((BigDecimal) receiptRow(paddedId).get("amount")).isEqualByComparingTo("10.0000");
                assertThat(receiptRow(paddedId).get("amount")).as("NUMERIC(18,4) 的存储标度就是 4")
                        .isInstanceOf(BigDecimal.class);
                assertThat(((BigDecimal) receiptRow(paddedId).get("amount")).scale()).isEqualTo(4);
            }

            // ------------------------------------------------------------------
            // I. external_reference 只是文本，重复必须放行
            // ------------------------------------------------------------------

            @Test
            @DisplayName("external_reference 重复：两次合法登记都成功，各一张收款单（它不是防重复请求编号）")
            void duplicateExternalReferenceIsAllowed() {
                Long customerId = customerOwnedBy(null);
                // 三件全部相同：金额、凭据号、时点。真实业务里「同一账号同日两笔等额同流水号引用」就是
                // 两笔钱，任何按字段组合的自然唯一键都会把第二笔合法收款挡在库外。
                FinanceReceiptAddForm first = form(customerId, "50.0000", "BANK_TRANSFER", PAST);
                first.setExternalReference("BANK-STATEMENT-001");
                FinanceReceiptAddForm second = form(customerId, "50.0000", "BANK_TRANSFER", PAST);
                second.setExternalReference("BANK-STATEMENT-001");

                Long firstId = add(first).getReceiptId();
                Long secondId = add(second).getReceiptId();

                assertThat(firstId).isNotEqualTo(secondId);
                assertThat(count("SELECT count(*) FROM xsy_finance_receipt WHERE customer_id = ? AND external_reference = ?",
                        customerId, "BANK-STATEMENT-001")).isEqualTo(2);
                // external_reference 上有普通索引、没有唯一约束（V65）：银行流水号跨客户重复是真实存在的
                assertThat(count("SELECT count(*) FROM pg_indexes WHERE tablename = 'xsy_finance_receipt'"
                        + " AND indexdef ILIKE '%unique%' AND indexdef ILIKE '%external_reference%'")).isZero();
                // 收款表的来源列 + 第二层防重复处理索引（ADM-12）：
                // 同一笔支付交易被驱动多少次，Finance 只留一条 NORMAL 且带来源的收款记录。
                // 它是「支付交易 → 收款」的防重复请求编号，不是人工收款的自然唯一键，因此谓词必须收窄。
                assertThat(jdbc.queryForList(
                        "SELECT indexdef FROM pg_indexes WHERE tablename = 'xsy_finance_receipt'"
                                + " AND indexdef ILIKE '%unique%'", String.class))
                        .as("finance_receipt 的全部唯一索引")
                        .allSatisfy(indexDef -> assertThat(indexDef)
                                .satisfiesAnyOf(
                                        def -> assertThat(def).contains("receipt_no"),
                                        def -> assertThat(def).contains("reverse_of_id"),
                                        def -> assertThat(def).contains("finance_receipt_pkey"),
                                        def -> assertThat(def).contains("(source_type, source_id)")
                                                .contains("WHERE").contains("source_type IS NOT NULL")));
            }

            // ------------------------------------------------------------------
            // J / K. 预收与「有应收也不自动核销」
            // ------------------------------------------------------------------

            @Test
            @DisplayName("预收：客户没有任何应收也能登记收款")
            void advanceReceiptWithoutAnyReceivableIsAllowed() {
                Long customerId = customerOwnedBy(null);
                assertThat(count("SELECT count(*) FROM xsy_finance_receivable WHERE customer_id = ?", customerId)).isZero();

                FinanceReceiptVO result = add(form(customerId, "30.0000", "CASH", PAST));

                assertThat(result.getReceiptNo()).matches("^XSY\\d{18,19}$");
                assertThat(count("SELECT count(*) FROM xsy_finance_write_off WHERE source_type = 'RECEIPT' AND source_id = ?",
                        result.getReceiptId())).as("这笔预收不自动生成核销").isZero();
                // 按客户收窄而不是全表：同一库里 NOT_SUPPORTED 的 IT 会提交真实的应收记录
                assertThat(count("SELECT count(*) FROM xsy_finance_receivable WHERE customer_id = ?", customerId))
                        .as("登记收款不产生任何应收").isZero();
            }

            @Test
            @DisplayName("已有应收时登记收款：不生成核销行、不改应收单与订单，只多一笔待核销款")
            void registeringReceiptNeverWritesOffOrTouchesReceivable() {
                Long customerId = customerOwnedBy(null);
                Long orderId = confirmedSalesOrder(customerId, newOnShelfSku("RCT"), "10.0000", "10.0000");
                // 受控夹具：按 schema 直插一张正常应收记录，
                // 目的是证明「收款登记不会碰它」，核销仍只能由核销命令产生。
                jdbc.update("INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id, order_id,"
                                + " customer_id, customer_name_snapshot, settlement_customer_id,"
                                + " settlement_customer_name_snapshot, entry_type, amount, event_at,"
                                + " created_at, updated_at, created_by)"
                                + " VALUES ('AR-FIXTURE-1', 'SALES_ORDER', ?, ?, ?, '测试客户', ?, '测试客户', 'NORMAL', 12.0000,"
                                + " now(), now(), now(), ?)",
                        orderId, orderId, customerId, customerId, currentOperator());
                evictMybatisCache();
                Map<String, Object> before = jdbc.queryForMap(
                        "SELECT * FROM xsy_finance_receivable WHERE source_id = ? AND entry_type = 'NORMAL'", orderId);
                BigDecimal orderSettlementBefore = jdbc.queryForObject(
                        "SELECT settlement_total_amount FROM xsy_sales_order WHERE id = ?", BigDecimal.class, orderId);

                FinanceReceiptVO receipt = add(form(customerId, "5.0000", "CASH", PAST));

                Map<String, Object> after = jdbc.queryForMap(
                        "SELECT * FROM xsy_finance_receivable WHERE source_id = ? AND entry_type = 'NORMAL'", orderId);
                assertThat(after.get("amount")).isEqualTo(before.get("amount"));
                assertThat(after.get("version")).isEqualTo(before.get("version"));
                assertThat(after.get("updated_at")).isEqualTo(before.get("updated_at"));
                assertThat(count("SELECT count(*) FROM xsy_finance_write_off WHERE source_type = 'RECEIPT' AND source_id = ?",
                        receipt.getReceiptId())).as("这笔收款不自动生成核销").isZero();
                assertThat(count("SELECT count(*) FROM xsy_finance_receipt WHERE customer_id = ?", customerId)).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT settlement_total_amount FROM xsy_sales_order WHERE id = ?",
                        BigDecimal.class, orderId)).as("订单结算金额一字未改").isEqualByComparingTo(orderSettlementBefore);
            }

            // ------------------------------------------------------------------
            // L / M. 重复请求返回原结果与冲突
            // ------------------------------------------------------------------

            @Test
            @DisplayName("重复请求返回原结果：同键同内容返回同一张收款单，库里一张、日志一条")
            void sameKeyAndContentReplaysTheFirstResult() {
                Long customerId = customerOwnedBy(null);
                FinanceReceiptAddForm form = form(customerId, "88.0000", "CASH", PAST);
                String idempotencyKey = key("replay");

                FinanceReceiptVO first = financeReceiptService.add(form, idempotencyKey);
                FinanceReceiptVO replay = financeReceiptService.add(form, idempotencyKey);

                assertThat(replay.getReceiptId()).isEqualTo(first.getReceiptId());
                assertThat(replay.getReceiptNo()).isEqualTo(first.getReceiptNo());
                assertThat(replay.getAmount()).isEqualByComparingTo(first.getAmount());
                assertThat(count("SELECT count(*) FROM xsy_finance_receipt WHERE customer_id = ?", customerId)).isEqualTo(1);
                assertThat(logsOf(first.getReceiptId())).hasSize(1);
                assertThat(count("SELECT count(*) FROM xsy_idempotency_record WHERE idempotency_key = ?", idempotencyKey))
                        .isEqualTo(1);
            }

            @Test
            @DisplayName("收款重放：客户改派后按当前范围拒绝，保持 30005 且不追加记录")
            void replayIsRejectedAfterCustomerIsReassigned() {
                Long employeeA = newPlainEmployee("RRA");
                Long employeeB = newPlainEmployee("RRB");
                Long customerId = customerOwnedBy(employeeA);
                FinanceReceiptAddForm request = form(customerId, "88.0000", "CASH", PAST);
                String idempotencyKey = key("replay-scope");

                try {
                    asEmployee(employeeA);
                    FinanceReceiptVO first = financeReceiptService.add(request, idempotencyKey);
                    FinanceReceiptVO replay = financeReceiptService.add(request, idempotencyKey);
                    assertThat(replay.getReceiptId()).isEqualTo(first.getReceiptId());

                    assertThat(jdbc.update("UPDATE xsy_customer SET seller_id = ? WHERE id = ?", employeeB, customerId))
                            .isEqualTo(1);
                    evictMybatisCache();

                    assertThatThrownBy(() -> financeReceiptService.add(request, idempotencyKey))
                            .isInstanceOf(ScmDataScopeException.class);
                } finally {
                    asAdmin();
                }

                assertThat(count("SELECT count(*) FROM xsy_finance_receipt WHERE customer_id = ?", customerId)).isEqualTo(1);
                assertThat(receiptLogsOfCustomer(customerId)).isEqualTo(1);
                assertThat(count("SELECT count(*) FROM xsy_idempotency_record WHERE idempotency_key = ?", idempotencyKey))
                        .isEqualTo(1);
            }

            @Test
            @DisplayName("防重复处理冲突：同键不同金额 → 40966，不产生第二张收款单")
            void sameKeyDifferentContentIsRejectedAsConflict() {
                Long customerId = customerOwnedBy(null);
                String idempotencyKey = key("conflict");
                financeReceiptService.add(form(customerId, "10.0000", "CASH", PAST), idempotencyKey);

                FinanceReceiptAddForm different = form(customerId, "99.0000", "CASH", PAST);
                expectCode(() -> financeReceiptService.add(different, idempotencyKey), 40966);

                assertThat(count("SELECT count(*) FROM xsy_finance_receipt WHERE customer_id = ?", customerId)).isEqualTo(1);
                assertThat(receiptLogsOfCustomer(customerId)).as("冲突的那一次没有追加日志").isEqualTo(1);
            }

            @Test
            @DisplayName("缺 Idempotency-Key：40069 拒绝，什么都不产生")
            void missingIdempotencyKeyIsRejected() {
                Long customerId = customerOwnedBy(null);
                FinanceReceiptAddForm form = form(customerId, "10.0000", "CASH", PAST);

                expectCode(() -> financeReceiptService.add(form, "  "), 40069);
                assertThat(count("SELECT count(*) FROM xsy_finance_receipt WHERE customer_id = ?", customerId)).isZero();
            }

            // ------------------------------------------------------------------
            // N. 当时的客户名称保存
            // ------------------------------------------------------------------

            @Test
            @DisplayName("登记后改客户名称：收款单上的当时的名称不变（历史账不回读主档）")
            void customerNameSnapshotIsFrozenAtRegistration() {
                Long customerId = customerOwnedBy(null);
                String original = jdbc.queryForObject("SELECT name FROM xsy_customer WHERE id = ?", String.class, customerId);

                Long receiptId = add(form(customerId, "20.0000", "CASH", PAST)).getReceiptId();
                jdbc.update("UPDATE xsy_customer SET name = ? WHERE id = ?", "改名后的客户" + UUID.randomUUID(), customerId);
                evictMybatisCache();

                assertThat(receiptRow(receiptId).get("customer_name_snapshot")).isEqualTo(original);
                assertThat(jdbc.queryForObject("SELECT name FROM xsy_customer WHERE id = ?", String.class, customerId))
                        .as("主档确实已经改名，保存的数据才有判别性").startsWith("改名后的客户");
            }

            // ------------------------------------------------------------------
            // O / P / Q. 数据范围
            // ------------------------------------------------------------------

            @Test
            @DisplayName("范围正向：客户归属本人（非超管、无 scope:all）即可登记")
            void ownCustomerIsRegistrableForNonAdministrator() {
                Long employeeId = newPlainEmployee("O");
                Long customerId = customerOwnedBy(employeeId);
                try {
                    asEmployee(employeeId);
                    FinanceReceiptVO result = add(form(customerId, "15.0000", "CASH", PAST));
                    assertThat(receiptRow(result.getReceiptId()).get("customer_id")).isEqualTo(customerId);
                } finally {
                    asAdmin();
                }
            }

            @Test
            @DisplayName("范围越界：客户属于别的业务员 → 30005 信封，收款单 0 条")
            void otherSellersCustomerIsRejectedWithoutLeakingExistence() {
                Long employeeId = newPlainEmployee("P");
                Long otherSeller = newPlainEmployee("P-OTHER");
                Long customerId = customerOwnedBy(otherSeller);
                try {
                    asEmployee(employeeId);
                    // 与「客户不存在」共用同一个异常：能分辨存在与否就等于把主键探测变成可用信号
                    assertThatThrownBy(() -> add(form(customerId, "15.0000", "CASH", PAST)))
                            .isInstanceOf(ScmDataScopeException.class);
                    assertThat(count("SELECT count(*) FROM xsy_finance_receipt WHERE customer_id = ?", customerId)).isZero();
                    assertThat(receiptLogsOfCustomer(customerId)).isZero();

                    // 不存在的客户必须是同一个异常
                    assertThatThrownBy(() -> add(form(999999999L, "15.0000", "CASH", PAST)))
                            .isInstanceOf(ScmDataScopeException.class);
                } finally {
                    asAdmin();
                }
            }

            @Test
            @DisplayName("未分配业务员的客户：非超管不可代其登记（校验失败时拒绝操作，不放宽成「都能收」）")
            void unassignedCustomerIsFailClosedForNonAdministrator() {
                Long employeeId = newPlainEmployee("Q");
                Long customerId = customerOwnedBy(null);
                try {
                    asEmployee(employeeId);
                    assertThatThrownBy(() -> add(form(customerId, "15.0000", "CASH", PAST)))
                            .isInstanceOf(ScmDataScopeException.class);
                    assertThat(count("SELECT count(*) FROM xsy_finance_receipt WHERE customer_id = ?", customerId)).isZero();
                } finally {
                    asAdmin();
                    // 全范围账号（超管兜底路径）可以登记，证明拒绝来自范围而不是字段形态
                    assertThat(add(form(customerId, "15.0000", "CASH", PAST)).getReceiptId()).isNotNull();
                }
            }

            // ------------------------------------------------------------------
            // R / S. received_at 由登记人填写
            // ------------------------------------------------------------------

            @Test
            @DisplayName("received_at：显式业务时点逐值写入数据库，服务端不改成 now()")
            void receivedAtIsTheCallerSuppliedBusinessMoment() {
                Long customerId = customerOwnedBy(null);
                Long receiptId = add(form(customerId, "40.0000", "BANK_TRANSFER", PAST)).getReceiptId();

                assertThat(timestampOf(receiptRow(receiptId).get("received_at")).toInstant())
                        .isEqualTo(PAST.toInstant());
                // 库侧逐值判等（不是「接近」）：服务端一旦改成 now()，这里立刻为 false
                assertThat(jdbc.queryForObject(
                        "SELECT received_at = ?::timestamptz FROM xsy_finance_receipt WHERE id = ?",
                        Boolean.class, PAST.toInstant().toString(), receiptId)).isTrue();
            }

            @Test
            @DisplayName("未来时点不被拒绝：本期没有「不得晚于当前时间」这条规则")
            void futureReceivedAtIsNotRejected() {
                Long customerId = customerOwnedBy(null);
                // 夹具取到微秒：timestamptz 只有微秒精度，纳秒会在服务端被舍入，
                // 归一放在夹具侧才能让下面这条逐值判等保持严格。
                OffsetDateTime future = OffsetDateTime.now().plusDays(30).truncatedTo(ChronoUnit.MICROS);

                Long receiptId = add(form(customerId, "10.0000", "CASH", future)).getReceiptId();

                assertThat(timestampOf(receiptRow(receiptId).get("received_at")).toInstant())
                        .isEqualTo(future.toInstant());
            }

            private static String currentOperator() {
                return com.xsy.scm.common.constant.ScmOperator.current();
            }

            /**
             * {@code timestamptz} 在 {@code queryForMap} 的 Map 读法里返回 {@link java.sql.Timestamp}，
             * 类型化读法返回 {@link OffsetDateTime}；统一成 {@code toInstant()} 才比较的是同一个时刻。
             */
            private static OffsetDateTime timestampOf(Object value) {
                if (value instanceof OffsetDateTime offsetDateTime) {
                    return offsetDateTime;
                }
                if (value instanceof java.sql.Timestamp timestamp) {
                    return timestamp.toInstant().atOffset(ZoneOffset.UTC);
                }
                throw new IllegalArgumentException("无法归一为 OffsetDateTime 的列类型: "
                        + (value == null ? "null" : value.getClass().getName()));
            }
        }

        @Nested
        @DisplayName("收款登记的结算主体边界（PG IT）")
        class ScmFinanceReceiptSettlementPgIT {
            @Autowired
            private FinanceReceiptService financeReceiptService;

            @Autowired
            private FinanceReceiptQueryService financeReceiptQueries;

            @Autowired
            private FinanceCounterpartySourceDao counterpartySourceDao;

            private FinanceReceiptAddForm form(Long customerId, String amount) {
                FinanceReceiptAddForm form = new FinanceReceiptAddForm();
                form.setCustomerId(customerId);
                form.setAmount(amount);
                form.setMethod("CASH");
                form.setReceivedAt(OffsetDateTime.now(ZoneOffset.ofHours(8)));
                form.setRemark("结算主体边界 IT");
                return form;
            }

            private String key() {
                return prefix + ":add:" + UUID.randomUUID();
            }

            private Long groupCustomer(Long parentCustomerId) {
                CustomerAddForm form = new CustomerAddForm();
                form.setName("结算主体边界集团客户");
                form.setSettleMode("GROUP");
                form.setSellerId(1L);
                form.setParentCustomerId(parentCustomerId);
                form.setSettlementCustomerId(parentCustomerId);
                Long customerId = customerService.add(form);

                CustomerStatusForm status = new CustomerStatusForm();
                status.setCustomerId(customerId);
                status.setVersion(customerService.require(customerId).getVersion());
                status.setStatus("COOPERATING");
                customerService.updateStatus(status);
                return customerId;
            }

            private void softDelete(Long customerId) {
                jdbc.update("UPDATE xsy_customer SET deleted = TRUE WHERE id = ?", customerId);
                evictMybatisCache();
            }

            private int countReceiptsOf(Long customerId) {
                Integer value = jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_finance_receipt WHERE customer_id = ?", Integer.class, customerId);
                return value == null ? 0 : value;
            }

            /**
             * 非超管业务员，并把客户挂到他名下。
             *
             * <p>
             * 必须用非超管取证：{@link com.xsy.scm.common.scope.ScmValueScope#all()} 对 {@code null}
             * 归属直接放行，超管位下「结算行查不出来」与「查得出来」都同样能通过范围判定，
             * 于是下面两条边界用例全部失去区分能力（这正是本类要防的误改）。
             */
            private Long plainEmployeeOwning(Long customerId) {
                String loginName = (prefix + "-E" + UUID.randomUUID().toString().substring(0, 8)).toUpperCase();
                jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                        + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                        UUID.randomUUID().toString().replace("-", ""), loginName, "$argon2id$it-placeholder", "结算边界业务员");
                Long employeeId = jdbc.queryForObject(
                        "SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
                assertThat(jdbc.queryForObject(
                        "SELECT administrator_flag FROM t_employee WHERE employee_id = ?", Boolean.class, employeeId))
                                .as("越权与可读性取证禁止用超管位").isFalse();
                jdbc.update("UPDATE xsy_customer SET seller_id = ? WHERE id = ?", employeeId, customerId);
                evictMybatisCache();
                return employeeId;
            }

            private void asEmployee(Long employeeId) {
                var employee = new RequestEmployee();
                employee.setEmployeeId(employeeId);
                employee.setActualName("结算主体边界 IT 非超管");
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                employee.setAdministratorFlag(false);
                XsyRequestUtil.setRequestUser(employee);
                evictMybatisCache();
            }

            @Test
            @DisplayName("结算父客户已软删：付款客户整行仍可读、结算主体成对为空、新收款登记被拒且不写入数据库")
            void danglingSettlementSubjectStillReadsThePayerButRejectsANewReceipt() {
                Long parent = groupCustomer(null);
                Long child = groupCustomer(parent);
                Long seller = plainEmployeeOwning(child);
                asEmployee(seller);
                softDelete(parent);

                var fact = counterpartySourceDao.selectCustomer(child);
                assertThat(fact).as("主档悬空不能把付款客户整行变没：只读面靠 seller_id 判范围").isNotNull();
                assertThat(fact.getCustomerId()).isEqualTo(child);
                assertThat(fact.getCustomerName()).isNotBlank();
                assertThat(fact.getSellerId()).isEqualTo(seller);
                // 结算 id 与名称要么成对来自同一行，要么成对为空 —— 不允许「id 是父客户、名称是付款客户」
                assertThat(fact.getSettlementCustomerId()).isNull();
                assertThat(fact.getSettlementCustomerName()).isNull();

                assertThatThrownBy(() -> financeReceiptService.add(form(child, "100.0000"), key()))
                        .isInstanceOfSatisfying(ScmBusinessException.class,
                                e -> assertThat(e.getErrorCode()).isEqualTo(ScmCommonErrorCode.VALIDATION_ERROR));

                assertThat(countReceiptsOf(child)).as("拒绝就不能留下半张收款记录").isZero();
            }

            @Test
            @DisplayName("收款登记之后再软删结算父客户：历史收款在列表与详情里都照常可读，保存的数据不被改写")
            void historicalReceiptStaysReadableAfterTheSettlementParentIsDeletedLater() {
                Long parent = groupCustomer(null);
                Long child = groupCustomer(parent);
                Long seller = plainEmployeeOwning(child);
                asEmployee(seller);

                FinanceReceiptVO receipt = financeReceiptService.add(form(child, "88.0000"), key());
                assertThat(receipt.getSettlementCustomerId()).isEqualTo(parent);
                assertThat(countReceiptsOf(child)).isEqualTo(1);

                softDelete(parent);

                FinanceReceiptQueryForm query = new FinanceReceiptQueryForm();
                LocalDate today = LocalDate.now(ZoneOffset.ofHours(8));
                query.setStartDate(today.minusDays(1));
                query.setEndDate(today.plusDays(1));
                query.setCustomerId(child);
                var page = financeReceiptQueries.query(query);
                assertThat(page.getList()).as("列表不能因为结算行消失而漏掉这张历史收款")
                        .extracting("receiptId").contains(receipt.getReceiptId());

                var detail = financeReceiptQueries.detail(receipt.getReceiptId());
                assertThat(detail.getReceipt().getReceiptNo()).isEqualTo(receipt.getReceiptNo());
                // 保存的是登记当时的结算主体，主档后续变化不回写历史记录
                assertThat(detail.getReceipt().getSettlementCustomerId()).isEqualTo(parent);
            }

            @Test
            @DisplayName("自己结算自己的客户不受影响：结算主体成对解析为付款客户本身")
            void selfSettlingCustomerResolvesToItself() {
                Long customer = newCustomer();

                var fact = counterpartySourceDao.selectCustomer(customer);
                assertThat(fact.getSettlementCustomerId()).isEqualTo(fact.getCustomerId());
                assertThat(fact.getSettlementCustomerName()).isEqualTo(fact.getCustomerName());

                FinanceReceiptVO receipt = financeReceiptService.add(form(customer, "50.0000"), key());
                assertThat(receipt.getSettlementCustomerId()).isEqualTo(customer);
            }
        }

        @Nested
        @DisplayName("收付款反向（Finance R1 F1-3C，PG IT）")
        class ScmFinanceReversePgIT {
            private static final OffsetDateTime PAST =
                    OffsetDateTime.of(2026, 2, 13, 10, 15, 0, 0, ZoneOffset.ofHours(8));

            @Autowired
            private FinanceReceiptService financeReceiptService;

            @Autowired
            private FinancePaymentService financePaymentService;

            @Autowired
            private FinancePaymentDao financePaymentDao;

            @Test
            @DisplayName("收款反向追加记录、记录查询时的余额数据，重复同键请求重放首次结果")
            void receiptReverseIsAppendOnlyAndIdempotent() throws Exception {
                Long customerId = newCustomer();
                FinanceReceiptVO original = addReceipt(customerId, "120.0000");
                Map<String, Object> originalBefore = receiptRow(original.getReceiptId());
                FinanceReceiptReverseForm form = receiptReverseForm(original.getReceiptId(), " 误录客户 ");
                String idempotencyKey = key("receipt-reverse");

                FinanceReceiptVO reversal = financeReceiptService.reverse(form, idempotencyKey);
                FinanceReceiptVO replay = financeReceiptService.reverse(form, idempotencyKey);

                assertThat(replay.getReceiptId()).isEqualTo(reversal.getReceiptId());
                assertThat(reversal.getEntryType()).isEqualTo("REVERSE");
                assertThat(reversal.getReverseOfId()).isEqualTo(original.getReceiptId());
                assertThat(reversal.getReason()).isEqualTo("误录客户");
                assertThat(reversal.getAmount()).isEqualByComparingTo("120.0000");

                Map<String, Object> originalAfter = receiptRow(original.getReceiptId());
                assertThat(originalAfter.get("entry_type")).isEqualTo("NORMAL");
                assertThat(originalAfter.get("reverse_of_id")).isNull();
                assertThat(originalAfter.get("reason")).isNull();
                assertThat(originalAfter.get("amount")).isEqualTo(originalBefore.get("amount"));
                assertThat(originalAfter.get("updated_at")).isEqualTo(originalBefore.get("updated_at"));
                assertThat(count("SELECT count(*) FROM xsy_finance_receipt WHERE reverse_of_id = ? AND entry_type = 'REVERSE'",
                        original.getReceiptId())).isEqualTo(1);

                List<Map<String, Object>> logs = receiptLogsOf(original.getReceiptId());
                assertThat(logs).extracting(row -> row.get("operation_type"))
                        .containsExactlyInAnyOrder("RECEIVE", "RECEIPT_REVERSE");
                Map<String, Object> reverseLog = logOfType(logs, "RECEIPT_REVERSE");
                assertThat(reverseLog.get("reason")).isEqualTo("误录客户");
                Map<String, Object> before = jsonObject(reverseLog.get("before_data"));
                Map<String, Object> after = jsonObject(reverseLog.get("after_data"));
                assertThat(before).containsEntry("effectiveAmount", "120.0000");
                assertThat(after).containsEntry("effectiveAmount", "0.0000")
                        .containsEntry("reverseOfId", original.getReceiptId().intValue());
            }

            @Test
            @DisplayName("已用核销额非零时拒绝反向，且不新增反向记录或日志")
            void receiptReverseIsBlockedUntilWriteOffIsReversed() {
                Long customerId = newCustomer();
                FinanceReceiptVO original = addReceipt(customerId, "50.0000");
                insertWriteOff("RECEIPT", original.getReceiptId(), "10.0000");

                expectCode(() -> financeReceiptService.reverse(
                        receiptReverseForm(original.getReceiptId(), "冲正"), key("blocked-receipt")), 41142);

                assertThat(count("SELECT count(*) FROM xsy_finance_receipt WHERE reverse_of_id = ?", original.getReceiptId()))
                        .isZero();
                assertThat(receiptLogsOf(original.getReceiptId())).extracting(row -> row.get("operation_type"))
                        .containsExactly("RECEIVE");
            }

            @Test
            @DisplayName("不同防重复请求编号重复反向同一收款时由唯一索引语义返回 41143")
            void receiptCannotBeReversedTwice() {
                FinanceReceiptVO original = addReceipt(newCustomer(), "25.0000");
                FinanceReceiptReverseForm form = receiptReverseForm(original.getReceiptId(), "单据重复");

                financeReceiptService.reverse(form, key("first-receipt-reverse"));
                expectCode(() -> financeReceiptService.reverse(form, key("second-receipt-reverse")), 41143);

                assertThat(count("SELECT count(*) FROM xsy_finance_receipt WHERE reverse_of_id = ?", original.getReceiptId()))
                        .isEqualTo(1);
            }

            @Test
            @DisplayName("反向原因不能为空白，返回专用业务码 41138")
            void reverseReasonIsRequired() {
                FinanceReceiptVO original = addReceipt(newCustomer(), "25.0000");

                expectCode(() -> financeReceiptService.reverse(receiptReverseForm(original.getReceiptId(), "  "),
                        key("missing-reason")), 41138);

                assertThat(count("SELECT count(*) FROM xsy_finance_receipt WHERE reverse_of_id = ?", original.getReceiptId()))
                        .isZero();
            }

            @Test
            @DisplayName("供应商付款可反向，原付款不变，反向付款保留对方保存的数据")
            void supplierPaymentCanBeReversedWithoutSupplierScope() {
                FinancePaymentVO original = addSupplierPayment(newSupplier("REVERSE-PAY"), "70.0000");
                Map<String, Object> originalBefore = paymentRow(original.getPaymentId());
                FinancePaymentReverseForm form = paymentReverseForm(original.getPaymentId(), "供应商账号录错");

                FinancePaymentVO reversal = financePaymentService.reverse(form, key("supplier-reverse"));

                assertThat(reversal.getEntryType()).isEqualTo("REVERSE");
                assertThat(reversal.getReverseOfId()).isEqualTo(original.getPaymentId());
                assertThat(reversal.getReason()).isEqualTo("供应商账号录错");
                assertThat(reversal.getCounterpartyType()).isEqualTo("SUPPLIER");
                assertThat(reversal.getCounterpartyId()).isEqualTo(original.getCounterpartyId());
                assertThat(reversal.getAmount()).isEqualByComparingTo("70.0000");

                Map<String, Object> originalAfter = paymentRow(original.getPaymentId());
                assertThat(originalAfter.get("entry_type")).isEqualTo("NORMAL");
                assertThat(originalAfter.get("reverse_of_id")).isNull();
                assertThat(originalAfter.get("reason")).isNull();
                assertThat(originalAfter.get("amount")).isEqualTo(originalBefore.get("amount"));
                assertThat(originalAfter.get("updated_at")).isEqualTo(originalBefore.get("updated_at"));
                assertThat(count("SELECT count(*) FROM xsy_finance_payment WHERE reverse_of_id = ?", original.getPaymentId()))
                        .isEqualTo(1);
            }

            @Test
            @DisplayName("退款付款反向行清空来源列，不与原退款付款争用来源唯一索引")
            void refundPaymentReverseClearsSource() {
                Long customerId = newCustomer();
                Long originalId = insertRefundPayment(customerId);
                Object sourceId = paymentRow(originalId).get("source_id");

                FinancePaymentVO reversal = financePaymentService.reverse(
                        paymentReverseForm(originalId, "退款记录录错"), key("refund-payment-reverse"));

                assertThat(reversal.getEntryType()).isEqualTo("REVERSE");
                assertThat(reversal.getReverseOfId()).isEqualTo(originalId);
                assertThat(reversal.getSourceType()).isNull();
                assertThat(reversal.getSourceId()).isNull();
                Map<String, Object> reversalRow = paymentRow(reversal.getPaymentId());
                assertThat(reversalRow.get("source_type")).isNull();
                assertThat(reversalRow.get("source_id")).isNull();
                assertThat(count("SELECT count(*) FROM xsy_finance_payment"
                        + " WHERE source_type = 'ORDER_REFUND' AND source_id = ?", sourceId)).isEqualTo(1);
                assertThat(paymentLogsOf(originalId)).extracting(row -> row.get("operation_type"))
                        .containsExactly("PAYMENT_REVERSE");
            }

            @Test
            @DisplayName("付款仍有有效核销额时拒绝反向")
            void paymentReverseIsBlockedByWriteOff() {
                FinancePaymentVO original = addSupplierPayment(newSupplier("BLOCK-PAY"), "30.0000");
                insertWriteOff("PAYMENT", original.getPaymentId(), "5.0000");

                expectCode(() -> financePaymentService.reverse(
                        paymentReverseForm(original.getPaymentId(), "先撤核销"), key("blocked-payment")), 41142);

                assertThat(count("SELECT count(*) FROM xsy_finance_payment WHERE reverse_of_id = ?", original.getPaymentId()))
                        .isZero();
                assertThat(paymentLogsOf(original.getPaymentId())).extracting(row -> row.get("operation_type"))
                        .containsExactly("PAY");
            }

            @Test
            @DisplayName("客户退款付款反向沿用 customerSellerScope，范围为空时拒绝访问")
            void customerPaymentReverseFailsClosedWithoutSellerScope() {
                Long customerId = newCustomer();
                Long paymentId = insertRefundPayment(customerId);
                setEmployeeWithoutScope();

                assertThatThrownBy(() -> financePaymentService.reverse(
                        paymentReverseForm(paymentId, "越权反向"), key("out-of-scope")))
                        .isInstanceOf(ScmDataScopeException.class);

                assertThat(count("SELECT count(*) FROM xsy_finance_payment WHERE reverse_of_id = ?", paymentId)).isZero();
                assertThat(paymentLogsOf(paymentId)).isEmpty();
            }

            private FinanceReceiptVO addReceipt(Long customerId, String amount) {
                FinanceReceiptAddForm form = new FinanceReceiptAddForm();
                form.setCustomerId(customerId);
                form.setAmount(amount);
                form.setMethod("BANK_TRANSFER");
                form.setReceivedAt(PAST);
                return financeReceiptService.add(form, key("receipt-add"));
            }

            private FinancePaymentVO addSupplierPayment(Long supplierId, String amount) {
                FinancePaymentAddForm form = new FinancePaymentAddForm();
                form.setCounterpartyType("SUPPLIER");
                form.setCounterpartyId(supplierId);
                form.setAmount(amount);
                form.setMethod("BANK_TRANSFER");
                form.setPaidAt(PAST);
                return financePaymentService.add(form, key("payment-add"));
            }

            private Long insertRefundPayment(Long customerId) {
                OffsetDateTime now = OffsetDateTime.now();
                FinancePaymentEntity payment = new FinancePaymentEntity();
                payment.setPaymentNo("PM-" + prefix + "-REFUND");
                payment.setCounterpartyType("CUSTOMER");
                payment.setCounterpartyId(customerId);
                payment.setCounterpartyNameSnapshot("测试客户");
                payment.setAmount(new BigDecimal("32.0000"));
                payment.setMethod("BANK_TRANSFER");
                payment.setPaidAt(PAST);
                payment.setEntryType("NORMAL");
                payment.setSourceType(ScmFinancePaymentSourceTypeEnum.ORDER_REFUND.name());
                payment.setSourceId(700_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 200_000_000L));
                payment.setCreatedAt(now);
                payment.setUpdatedAt(now);
                payment.setCreatedBy(currentOperator());
                payment.setUpdatedBy(currentOperator());
                assertThat(financePaymentDao.insert(payment)).isEqualTo(1);
                return payment.getId();
            }

            private void insertWriteOff(String sourceType, Long sourceId, String amount) {
                jdbc.update("INSERT INTO xsy_finance_write_off (write_off_no, source_type, source_id, target_type, target_id,"
                                + " amount, entry_type, written_off_at, operator) VALUES (?, ?, ?, ?, 1, ?, 'NORMAL', now(), ?)",
                        "WO-" + prefix + "-" + UUID.randomUUID(), sourceType, sourceId,
                        "RECEIPT".equals(sourceType) ? "RECEIVABLE" : "PAYABLE", new BigDecimal(amount), currentOperator());
            }

            private void setEmployeeWithoutScope() {
                String loginName = (prefix + "-NO-SCOPE").toUpperCase();
                jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                                + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                        UUID.randomUUID().toString().replace("-", ""), loginName,
                        "$argon2id$it-placeholder", "无范围财务员工");
                Long employeeId = jdbc.queryForObject(
                        "SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
                RequestEmployee employee = new RequestEmployee();
                employee.setEmployeeId(employeeId);
                employee.setActualName("无范围财务员工");
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                employee.setAdministratorFlag(false);
                XsyRequestUtil.setRequestUser(employee);
                evictMybatisCache();
            }

            private FinanceReceiptReverseForm receiptReverseForm(Long receiptId, String reason) {
                FinanceReceiptReverseForm form = new FinanceReceiptReverseForm();
                form.setReceiptId(receiptId);
                form.setReason(reason);
                return form;
            }

            private FinancePaymentReverseForm paymentReverseForm(Long paymentId, String reason) {
                FinancePaymentReverseForm form = new FinancePaymentReverseForm();
                form.setPaymentId(paymentId);
                form.setReason(reason);
                return form;
            }

            private String key(String tag) {
                return prefix + ":" + tag + ":" + UUID.randomUUID();
            }

            private static String currentOperator() {
                return com.xsy.scm.common.constant.ScmOperator.current();
            }

            private Map<String, Object> receiptRow(Long receiptId) {
                return jdbc.queryForMap("SELECT * FROM xsy_finance_receipt WHERE id = ?", receiptId);
            }

            private Map<String, Object> paymentRow(Long paymentId) {
                return jdbc.queryForMap("SELECT * FROM xsy_finance_payment WHERE id = ?", paymentId);
            }

            private List<Map<String, Object>> receiptLogsOf(Long receiptId) {
                return jdbc.queryForList("SELECT * FROM xsy_finance_operation_log"
                        + " WHERE business_type = 'RECEIPT' AND business_id = ?", receiptId);
            }

            private List<Map<String, Object>> paymentLogsOf(Long paymentId) {
                return jdbc.queryForList("SELECT * FROM xsy_finance_operation_log"
                        + " WHERE business_type = 'PAYMENT' AND business_id = ?", paymentId);
            }

            private Map<String, Object> logOfType(List<Map<String, Object>> logs, String operationType) {
                return logs.stream()
                        .filter(log -> operationType.equals(log.get("operation_type")))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("未找到财务日志类型 " + operationType));
            }

            private Map<String, Object> jsonObject(Object value) throws Exception {
                return objectMapper.readValue(String.valueOf(value), new TypeReference<Map<String, Object>>() {
                });
            }

            private int count(String sql, Object... args) {
                Integer value = jdbc.queryForObject(sql, Integer.class, args);
                return value == null ? 0 : value;
            }
        }

        @Nested
        @DisplayName("Finance R1 页面与功能权限矩阵（PG IT）")
        class ScmFinanceRoleMatrixPgIT {
            private static final List<Long> PAGE_IDS = List.of(1501L, 1502L, 1503L, 1504L, 1505L);

            private static final List<String> FINANCE_PERMISSIONS = List.of(
                    FinancePermission.RECEIVABLE_QUERY,
                    FinancePermission.PAYABLE_QUERY,
                    FinancePermission.RECEIPT_QUERY,
                    FinancePermission.PAYMENT_QUERY,
                    FinancePermission.WRITE_OFF_QUERY,
                    FinancePermission.RECEIPT_ADD,
                    FinancePermission.PAYMENT_ADD,
                    FinancePermission.WRITE_OFF_ADD,
                    FinancePermission.PAYABLE_RED,
                    FinancePermission.WRITE_OFF_REVERSE,
                    FinancePermission.RECEIPT_REVERSE,
                    FinancePermission.PAYMENT_REVERSE,
                    FinancePermission.WAIVER_ADD,
                    FinancePermission.WAIVER_REVERSE,
                    FinancePermission.EXPORT);

            @Autowired
            private LoginManager loginManager;

            @Autowired
            private RoleMenuService roleMenuService;

            private Long employeeWithRole(String tag, String roleCode) {
                String loginName = (prefix + "-" + tag).toUpperCase();
                jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                                + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                        UUID.randomUUID().toString().replace("-", ""), loginName, "$argon2id$it-placeholder", "财务矩阵" + tag);
                Long employeeId = jdbc.queryForObject(
                        "SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
                assertThat(jdbc.queryForObject(
                        "SELECT administrator_flag FROM t_employee WHERE employee_id = ?", Boolean.class, employeeId))
                        .as("权限取证账号必须是普通账号")
                        .isFalse();
                jdbc.update("INSERT INTO t_role_employee (role_id, employee_id)"
                                + " SELECT r.role_id, ? FROM t_role r WHERE r.role_code = ?",
                        employeeId, roleCode);
                evictMybatisCache();
                return employeeId;
            }

            private UserPermission permissionsOf(Long employeeId) {
                RequestEmployee employee = new RequestEmployee();
                employee.setEmployeeId(employeeId);
                employee.setActualName("F1-6 权限矩阵");
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                employee.setAdministratorFlag(false);
                XsyRequestUtil.setRequestUser(employee);
                return loginManager.loadUserPermission(employeeId);
            }

            private List<Long> menuIdsOfRole(String roleCode) {
                Long roleId = jdbc.queryForObject("SELECT role_id FROM t_role WHERE role_code = ?", Long.class, roleCode);
                return roleMenuService.getMenuList(List.of(roleId), false).stream().map(MenuVO::getMenuId).toList();
            }

            @Test
            @DisplayName("SCM_FINANCE 可访问五个 Finance 页面及全部 15 个受保护能力，司机均不可见")
            void financeRoleHasFinancePagesAndActions() {
                Long financeEmployee = employeeWithRole("FIN", "SCM_FINANCE");
                Long driverEmployee = employeeWithRole("DRV", "SCM_DRIVER");

                assertThat(menuIdsOfRole("SCM_FINANCE")).containsAll(PAGE_IDS);
                assertThat(menuIdsOfRole("SCM_DRIVER")).doesNotContainAnyElementsOf(PAGE_IDS);
                assertThat(permissionsOf(financeEmployee).getPermissionList()).containsAll(FINANCE_PERMISSIONS);
                assertThat(permissionsOf(driverEmployee).getPermissionList()).doesNotContainAnyElementsOf(FINANCE_PERMISSIONS);
            }

            @Test
            @DisplayName("五类查询与导出端点的注解分别对应查询权限及查询 AND 导出权限")
            void readEndpointsUseTheirPublishedPermissions() throws Exception {
                assertPermission(FinanceReadController.class, "receivableQuery", FinanceReceivableQueryForm.class,
                        FinancePermission.RECEIVABLE_QUERY);
                assertPermission(FinanceReadController.class, "payableQuery", FinancePayableQueryForm.class,
                        FinancePermission.PAYABLE_QUERY);
                assertPermission(FinanceReadController.class, "receiptQuery", FinanceReceiptQueryForm.class,
                        FinancePermission.RECEIPT_QUERY);
                assertPermission(FinanceReadController.class, "paymentQuery", FinancePaymentQueryForm.class,
                        FinancePermission.PAYMENT_QUERY);
                assertPermission(FinanceWriteOffController.class, "query", FinanceWriteOffQueryForm.class,
                        FinancePermission.WRITE_OFF_QUERY);

                assertExportPermission(FinanceReadController.class, "exportReceivables", FinanceReceivableQueryForm.class,
                        FinancePermission.RECEIVABLE_QUERY);
                assertExportPermission(FinanceReadController.class, "exportPayables", FinancePayableQueryForm.class,
                        FinancePermission.PAYABLE_QUERY);
                assertExportPermission(FinanceReadController.class, "exportReceipts", FinanceReceiptQueryForm.class,
                        FinancePermission.RECEIPT_QUERY);
                assertExportPermission(FinanceReadController.class, "exportPayments", FinancePaymentQueryForm.class,
                        FinancePermission.PAYMENT_QUERY);
                assertExportPermission(FinanceWriteOffController.class, "export", FinanceWriteOffQueryForm.class,
                        FinancePermission.WRITE_OFF_QUERY);
            }

            @Test
            @DisplayName("手工红字、收付款登记與反向端点仍各自使用独立权限")
            void writeEndpointsKeepIndependentPermissions() throws Exception {
                assertPermission(FinancePayableController.class, "red",
                        com.xsy.scm.finance.domain.form.FinancePayableRedForm.class, FinancePermission.PAYABLE_RED);
                assertPermission(FinanceReceiptController.class, "add",
                        com.xsy.scm.finance.domain.form.FinanceReceiptAddForm.class, FinancePermission.RECEIPT_ADD);
                assertPermission(FinanceReceiptController.class, "reverse",
                        com.xsy.scm.finance.domain.form.FinanceReceiptReverseForm.class, FinancePermission.RECEIPT_REVERSE);
                assertPermission(FinancePaymentController.class, "add",
                        com.xsy.scm.finance.domain.form.FinancePaymentAddForm.class, FinancePermission.PAYMENT_ADD);
                assertPermission(FinancePaymentController.class, "reverse",
                        com.xsy.scm.finance.domain.form.FinancePaymentReverseForm.class, FinancePermission.PAYMENT_REVERSE);
                assertPermission(FinancePaymentController.class, "refundOptions",
                        FinanceRefundOptionQueryForm.class, FinancePermission.PAYMENT_ADD);
                // 抹零与反向抹零分开授权：会抹零不等于能撤销
                assertPermission(FinanceWaiverController.class, "add",
                        com.xsy.scm.finance.domain.form.FinanceWaiverAddForm.class, FinancePermission.WAIVER_ADD);
                assertPermission(FinanceWaiverController.class, "reverse",
                        com.xsy.scm.finance.domain.form.FinanceWaiverReverseForm.class, FinancePermission.WAIVER_REVERSE);
            }

            private static void assertPermission(Class<?> controller, String methodName, Class<?> parameterType,
                    String permission) throws Exception {
                Method method = java.util.Arrays.stream(controller.getDeclaredMethods())
                        .filter(candidate -> candidate.getName().equals(methodName))
                        .filter(candidate -> java.util.Arrays.asList(candidate.getParameterTypes()).contains(parameterType))
                        .findFirst().orElseThrow();
                SaCheckPermission annotation = method.getAnnotation(SaCheckPermission.class);
                assertThat(annotation).as("%s#%s 必须受权限保护", controller.getSimpleName(), methodName).isNotNull();
                assertThat(annotation.value()).containsExactly(permission);
            }

            private static void assertExportPermission(Class<?> controller, String methodName, Class<?> formType,
                    String queryPermission) throws Exception {
                Method method = java.util.Arrays.stream(controller.getDeclaredMethods())
                        .filter(candidate -> candidate.getName().equals(methodName))
                        .filter(candidate -> java.util.Arrays.asList(candidate.getParameterTypes()).contains(formType))
                        .findFirst().orElseThrow();
                SaCheckPermission annotation = method.getAnnotation(SaCheckPermission.class);
                assertThat(annotation).as("%s#%s 必须受双权限保护", controller.getSimpleName(), methodName).isNotNull();
                assertThat(annotation.value()).containsExactly(queryPermission, FinancePermission.EXPORT);
                assertThat(annotation.mode()).isEqualTo(SaMode.AND);
            }
        }

        @Nested
        @DisplayName("结清状态派生：全额红冲 / 部分红冲 / 部分核销 / 超额核销（FIX-01，PG IT）")
        class ScmFinanceSettleStatePgIT {
            private static final OffsetDateTime EVENT_AT =
                    OffsetDateTime.of(2026, 3, 17, 9, 0, 0, 0, ZoneOffset.ofHours(8));

            @Autowired
            private FinanceReceiptService financeReceiptService;

            @Autowired
            private FinancePaymentService financePaymentService;

            @Autowired
            private FinanceWriteOffService financeWriteOffService;

            @Autowired
            private FinanceReceivableQueryService receivableQueryService;

            @Autowired
            private FinancePayableQueryService payableQueryService;

            // ------------------------------------------------------------------
            // 应收
            // ------------------------------------------------------------------

            @Test
            @DisplayName("应收全额红冲：净额归零即已结清，OPEN 筛选不再命中")
            void fullyRedFlushedReceivableIsSettled() {
                Long customerId = newCustomer();
                Long receivableId = normalReceivable(customerId, "100.0000");
                insertRedReceivable(receivableId, customerId, "100.0000");

                var header = receivableQueryService.detail(receivableId).getReceivable();

                assertThat(header.getNetAmount()).isEqualByComparingTo("0.0000");
                assertThat(header.getWrittenOffAmount()).isEqualByComparingTo("0.0000");
                assertThat(header.getOpenAmount()).isEqualByComparingTo("0.0000");
                assertThat(header.getOverAppliedAmount()).isEqualByComparingTo("0.0000");
                assertThat(header.getSettleState()).isEqualTo("SETTLED");
                assertThat(receivableIdsFilteredBy("SETTLED")).contains(receivableId);
                assertThat(receivableIdsFilteredBy("OPEN")).doesNotContain(receivableId);
            }

            @Test
            @DisplayName("应收部分红冲且无核销：仍有净额待收，保持未结清")
            void partiallyRedFlushedReceivableStaysOpen() {
                Long customerId = newCustomer();
                Long receivableId = normalReceivable(customerId, "100.0000");
                insertRedReceivable(receivableId, customerId, "40.0000");

                var header = receivableQueryService.detail(receivableId).getReceivable();

                assertThat(header.getNetAmount()).isEqualByComparingTo("60.0000");
                assertThat(header.getOpenAmount()).isEqualByComparingTo("60.0000");
                assertThat(header.getSettleState()).isEqualTo("OPEN");
                assertThat(receivableIdsFilteredBy("OPEN")).contains(receivableId);
                assertThat(receivableIdsFilteredBy("SETTLED")).doesNotContain(receivableId);
            }

            @Test
            @DisplayName("应收部分核销为 PARTIAL，核销额恰等于净额为 SETTLED")
            void receivablePartialAndExactWriteOff() {
                Long customerId = newCustomer();
                FinanceReceiptVO receipt = addReceipt(customerId, "200.0000");
                Long partialId = normalReceivable(customerId, "100.0000");
                Long exactId = normalReceivable(customerId, "70.0000");
                financeWriteOffService.add(
                        writeOffForm("RECEIPT", receipt.getReceiptId(), writeOffItem(partialId, "30.0000")),
                        key("partial-write-off"));
                financeWriteOffService.add(
                        writeOffForm("RECEIPT", receipt.getReceiptId(), writeOffItem(exactId, "70.0000")),
                        key("exact-write-off"));

                var partial = receivableQueryService.detail(partialId).getReceivable();
                assertThat(partial.getWrittenOffAmount()).isEqualByComparingTo("30.0000");
                assertThat(partial.getOpenAmount()).isEqualByComparingTo("70.0000");
                assertThat(partial.getSettleState()).isEqualTo("PARTIAL");

                var exact = receivableQueryService.detail(exactId).getReceivable();
                assertThat(exact.getOpenAmount()).isEqualByComparingTo("0.0000");
                assertThat(exact.getSettleState()).isEqualTo("SETTLED");

                assertThat(receivableIdsFilteredBy("PARTIAL")).contains(partialId).doesNotContain(exactId);
                assertThat(receivableIdsFilteredBy("SETTLED")).contains(exactId).doesNotContain(partialId);
            }

            @Test
            @DisplayName("应收先核销后红冲造成超额核销：保持 PARTIAL，不得误标记已结清")
            void overAppliedReceivableStaysPartial() {
                Long customerId = newCustomer();
                FinanceReceiptVO receipt = addReceipt(customerId, "90.0000");
                Long receivableId = normalReceivable(customerId, "100.0000");
                financeWriteOffService.add(
                        writeOffForm("RECEIPT", receipt.getReceiptId(), writeOffItem(receivableId, "90.0000")),
                        key("write-off-before-red"));
                insertRedReceivable(receivableId, customerId, "30.0000");

                var header = receivableQueryService.detail(receivableId).getReceivable();

                assertThat(header.getNetAmount()).isEqualByComparingTo("70.0000");
                assertThat(header.getWrittenOffAmount()).isEqualByComparingTo("90.0000");
                assertThat(header.getOpenAmount()).isEqualByComparingTo("0.0000");
                assertThat(header.getOverAppliedAmount()).isEqualByComparingTo("20.0000");
                assertThat(header.getSettleState()).isEqualTo("PARTIAL");
                assertThat(receivableIdsFilteredBy("SETTLED")).doesNotContain(receivableId);
                assertThat(receivableIdsFilteredBy("PARTIAL")).contains(receivableId);
            }

            // ------------------------------------------------------------------
            // 应付
            // ------------------------------------------------------------------

            @Test
            @DisplayName("应付全额红冲：净额归零即已结清，OPEN 筛选不再命中")
            void fullyRedFlushedPayableIsSettled() {
                Long supplierId = newSupplier("SS-FULL-RED");
                Long payableId = normalPayable(supplierId, "100.0000");
                insertRedPayable(payableId, supplierId, "100.0000");

                var header = payableQueryService.detail(payableId).getPayable();

                assertThat(header.getNetAmount()).isEqualByComparingTo("0.0000");
                assertThat(header.getOpenAmount()).isEqualByComparingTo("0.0000");
                assertThat(header.getSettleState()).isEqualTo("SETTLED");
                assertThat(payableIdsFilteredBy("SETTLED")).contains(payableId);
                assertThat(payableIdsFilteredBy("OPEN")).doesNotContain(payableId);
            }

            @Test
            @DisplayName("应付部分核销为 PARTIAL，核销额恰等于净额为 SETTLED")
            void payablePartialAndExactWriteOff() {
                Long supplierId = newSupplier("SS-WO");
                FinancePaymentVO payment = addSupplierPayment(supplierId, "200.0000");
                Long partialId = normalPayable(supplierId, "100.0000");
                Long exactId = normalPayable(supplierId, "70.0000");
                financeWriteOffService.add(
                        writeOffForm("PAYMENT", payment.getPaymentId(), writeOffItem(partialId, "30.0000")),
                        key("payable-partial-write-off"));
                financeWriteOffService.add(
                        writeOffForm("PAYMENT", payment.getPaymentId(), writeOffItem(exactId, "70.0000")),
                        key("payable-exact-write-off"));

                var partial = payableQueryService.detail(partialId).getPayable();
                assertThat(partial.getWrittenOffAmount()).isEqualByComparingTo("30.0000");
                assertThat(partial.getOpenAmount()).isEqualByComparingTo("70.0000");
                assertThat(partial.getSettleState()).isEqualTo("PARTIAL");

                var exact = payableQueryService.detail(exactId).getPayable();
                assertThat(exact.getOpenAmount()).isEqualByComparingTo("0.0000");
                assertThat(exact.getSettleState()).isEqualTo("SETTLED");

                assertThat(payableIdsFilteredBy("PARTIAL")).contains(partialId).doesNotContain(exactId);
                assertThat(payableIdsFilteredBy("SETTLED")).contains(exactId).doesNotContain(partialId);
            }

            @Test
            @DisplayName("应付先核销后红冲造成超额核销：保持 PARTIAL，不得误标记已结清")
            void overAppliedPayableStaysPartial() {
                Long supplierId = newSupplier("SS-OVER");
                FinancePaymentVO payment = addSupplierPayment(supplierId, "90.0000");
                Long payableId = normalPayable(supplierId, "100.0000");
                financeWriteOffService.add(
                        writeOffForm("PAYMENT", payment.getPaymentId(), writeOffItem(payableId, "90.0000")),
                        key("payable-write-off-before-red"));
                insertRedPayable(payableId, supplierId, "30.0000");

                var header = payableQueryService.detail(payableId).getPayable();

                assertThat(header.getNetAmount()).isEqualByComparingTo("70.0000");
                assertThat(header.getWrittenOffAmount()).isEqualByComparingTo("90.0000");
                assertThat(header.getOpenAmount()).isEqualByComparingTo("0.0000");
                assertThat(header.getOverAppliedAmount()).isEqualByComparingTo("20.0000");
                assertThat(header.getSettleState()).isEqualTo("PARTIAL");
                assertThat(payableIdsFilteredBy("SETTLED")).doesNotContain(payableId);
                assertThat(payableIdsFilteredBy("PARTIAL")).contains(payableId);
            }

            // ------------------------------------------------------------------
            // 夹具
            // ------------------------------------------------------------------

            /**
             * 直接插一条正常应收。汇总 SQL 与 {@code xsy_sales_order} 是 INNER JOIN，
             * 因此订单必须真实存在（走订单命令链造单，不用不存在的 id）。
             */
            private Long normalReceivable(Long customerId, String amount) {
                // SKU 编码含用例前缀但不含序号：同一用例造两条应收时必须各占一个后缀，否则撞唯一索引
                Long orderId = confirmedSalesOrder(customerId,
                        newOnShelfSku("SS-AR-" + UUID.randomUUID().toString().substring(0, 6)), "10.0000", "10.0000");
                return jdbc.queryForObject("INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id, order_id,"
                                + " customer_id, customer_name_snapshot, settlement_customer_id,"
                                + " settlement_customer_name_snapshot, entry_type, amount, event_at, created_at, updated_at)"
                                + " VALUES (?, 'SALES_ORDER', ?, ?, ?, '结清测试客户', ?, '结清测试客户', 'NORMAL', ?,"
                                + " now(), now(), now()) RETURNING id",
                        Long.class, "AR-SS-" + prefix + "-" + UUID.randomUUID(), uniqueNumber(), orderId, customerId,
                        customerId, new BigDecimal(amount));
            }

            private void insertRedReceivable(Long originalId, Long customerId, String amount) {
                Long orderId = jdbc.queryForObject("SELECT order_id FROM xsy_finance_receivable WHERE id = ?",
                        Long.class, originalId);
                jdbc.update("INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id, order_id, customer_id,"
                                + " customer_name_snapshot, settlement_customer_id,"
                                + " settlement_customer_name_snapshot, entry_type, original_receivable_id, amount, event_at,"
                                + " reason)"
                                + " VALUES (?, 'ORDER_RETURN', ?, ?, ?, '结清测试客户', ?, '结清测试客户', 'RED', ?, ?, now(),"
                                + " '测试红字')",
                        "AR-SS-RED-" + prefix + "-" + UUID.randomUUID(), uniqueNumber(), orderId, customerId, customerId,
                        originalId, new BigDecimal(amount));
            }

            private Long normalPayable(Long supplierId, String amount) {
                Long payableId = jdbc.queryForObject("INSERT INTO xsy_finance_payable (payable_no, source_type, source_id,"
                                + " purchase_order_id, supplier_id, supplier_name_snapshot, entry_type, amount, event_at)"
                                + " VALUES (?, 'PURCHASE_RECEIPT', ?, 1, ?, '结清测试供应商', 'NORMAL', ?, now()) RETURNING id",
                        Long.class, "AP-SS-" + prefix + "-" + UUID.randomUUID(), uniqueNumber(), supplierId,
                        new BigDecimal(amount));
                jdbc.update("INSERT INTO xsy_finance_payable_item (payable_id, source_type, source_id, purchase_order_item_id,"
                                + " sku_id, sku_name_snapshot, unit_snapshot, quantity, unit_price, amount)"
                                + " VALUES (?, 'PURCHASE_RECEIPT_ITEM', ?, 44, 1, '测试商品', 'kg', 10.0000, 10.0000, 100.0000)",
                        payableId, uniqueNumber());
                return payableId;
            }

            /** 红字应付的库级配对约束：MANUAL 来源、source_id 为空、必须引用原单并写原因。 */
            private void insertRedPayable(Long originalId, Long supplierId, String amount) {
                jdbc.update("INSERT INTO xsy_finance_payable (payable_no, source_type, source_id, purchase_order_id,"
                                + " supplier_id, supplier_name_snapshot, entry_type, original_payable_id, amount, event_at,"
                                + " reason)"
                                + " VALUES (?, 'MANUAL', NULL, 1, ?, '结清测试供应商', 'RED', ?, ?, now(), '测试红字')",
                        "AP-SS-RED-" + prefix + "-" + UUID.randomUUID(), supplierId, originalId, new BigDecimal(amount));
            }

            private FinanceReceiptVO addReceipt(Long customerId, String amount) {
                var form = new com.xsy.scm.finance.domain.form.FinanceReceiptAddForm();
                form.setCustomerId(customerId);
                form.setAmount(amount);
                form.setMethod("BANK_TRANSFER");
                form.setReceivedAt(EVENT_AT);
                return financeReceiptService.add(form, key("receipt-add"));
            }

            private FinancePaymentVO addSupplierPayment(Long supplierId, String amount) {
                var form = new com.xsy.scm.finance.domain.form.FinancePaymentAddForm();
                form.setCounterpartyType("SUPPLIER");
                form.setCounterpartyId(supplierId);
                form.setAmount(amount);
                form.setMethod("BANK_TRANSFER");
                form.setPaidAt(EVENT_AT);
                return financePaymentService.add(form, key("payment-add"));
            }

            private List<Long> receivableIdsFilteredBy(String settleState) {
                var form = new com.xsy.scm.finance.domain.form.FinanceReceivableQueryForm();
                form.setPageNum(1L);
                form.setPageSize(100L);
                form.setSettleState(settleState);
                form.setStartDate(LocalDate.now(ZoneOffset.ofHours(8)).minusDays(1));
                form.setEndDate(LocalDate.now(ZoneOffset.ofHours(8)));
                return receivableQueryService.query(form).getList().stream()
                        .map(com.xsy.scm.finance.domain.vo.FinanceReceivableVO::getReceivableId).toList();
            }

            private List<Long> payableIdsFilteredBy(String settleState) {
                var form = new com.xsy.scm.finance.domain.form.FinancePayableQueryForm();
                form.setPageNum(1L);
                form.setPageSize(100L);
                form.setSettleState(settleState);
                form.setStartDate(LocalDate.now(ZoneOffset.ofHours(8)).minusDays(1));
                form.setEndDate(LocalDate.now(ZoneOffset.ofHours(8)));
                return payableQueryService.query(form).getList().stream()
                        .map(com.xsy.scm.finance.domain.vo.FinancePayableVO::getPayableId).toList();
            }

            private FinanceWriteOffAddForm writeOffForm(String sourceType, Long sourceId,
                    FinanceWriteOffAddItemForm... items) {
                FinanceWriteOffAddForm form = new FinanceWriteOffAddForm();
                form.setSourceType(sourceType);
                form.setSourceId(sourceId);
                form.setItems(List.of(items));
                return form;
            }

            private FinanceWriteOffAddItemForm writeOffItem(Long targetId, String amount) {
                FinanceWriteOffAddItemForm item = new FinanceWriteOffAddItemForm();
                item.setTargetId(targetId);
                item.setAmount(amount);
                return item;
            }

            private String key(String tag) {
                return prefix + ":" + tag + ":" + UUID.randomUUID();
            }

            private long uniqueNumber() {
                return 1_000_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 8_000_000_000L);
            }
        }

        @Nested
        @DisplayName("核销与手工红字应付（Finance R1 F1-4，PG IT）")
        class ScmFinanceWriteOffPgIT {
            private static final OffsetDateTime EVENT_AT =
                    OffsetDateTime.of(2026, 3, 17, 9, 0, 0, 0, ZoneOffset.ofHours(8));

            @Autowired
            private FinanceReceiptService financeReceiptService;

            @Autowired
            private FinancePaymentService financePaymentService;

            @Autowired
            private FinancePayableService financePayableService;

            @Autowired
            private FinanceWriteOffService financeWriteOffService;

            @Autowired
            private FinanceWriteOffQueryService financeWriteOffQueryService;

            @Test
            @DisplayName("一笔收款分配给多张应收，重复请求返回原结果不重复生成记录")
            void oneReceiptCanBeAppliedToManyReceivables() {
                Long customerId = newCustomer();
                FinanceReceiptVO receipt = addReceipt(customerId, "100.0000");
                Long firstReceivableId = normalReceivable(customerId, "80.0000");
                Long secondReceivableId = normalReceivable(customerId, "60.0000");
                FinanceWriteOffAddForm form = writeOffForm("RECEIPT", receipt.getReceiptId(),
                        writeOffItem(firstReceivableId, "30.0000"), writeOffItem(secondReceivableId, "40.0000"));
                String key = key("receipt-many-targets");

                FinanceWriteOffAddResultVO result = financeWriteOffService.add(form, key);
                FinanceWriteOffAddResultVO replay = financeWriteOffService.add(form, key);

                assertThat(result.getItems()).hasSize(2);
                assertThat(replay.getItems()).extracting(FinanceWriteOffVO::getWriteOffId)
                        .containsExactlyElementsOf(result.getItems().stream().map(FinanceWriteOffVO::getWriteOffId).toList());
                assertThat(count("SELECT count(*) FROM xsy_finance_write_off WHERE source_type = 'RECEIPT' AND source_id = ?"
                        + " AND entry_type = 'NORMAL'", receipt.getReceiptId())).isEqualTo(2);
                assertThat(count("SELECT count(*) FROM xsy_finance_operation_log WHERE business_type = 'WRITE_OFF'"
                        + " AND operation_type = 'WRITE_OFF' AND business_id IN"
                        + " (SELECT id FROM xsy_finance_write_off WHERE source_id = ? AND source_type = 'RECEIPT')",
                        receipt.getReceiptId())).isEqualTo(2);
                assertThat(result.getItems()).extracting(FinanceWriteOffVO::getSourceNo)
                        .containsOnly(receipt.getReceiptNo());
            }

            @Test
            @DisplayName("供应商付款只能核同一供应商应付")
            void supplierPaymentCanBeAppliedToItsPayables() {
                Long supplierId = newSupplier("WO-PAYMENT");
                FinancePaymentVO payment = addSupplierPayment(supplierId, "90.0000");
                Long payableId = normalPayable(supplierId, "70.0000");

                FinanceWriteOffAddResultVO result = financeWriteOffService.add(
                        writeOffForm("PAYMENT", payment.getPaymentId(), writeOffItem(payableId, "50.0000")),
                        key("supplier-payment"));

                assertThat(result.getItems()).singleElement().satisfies(row -> {
                    assertThat(row.getSourceType()).isEqualTo("PAYMENT");
                    assertThat(row.getTargetType()).isEqualTo("PAYABLE");
                    assertThat(row.getAmount()).isEqualByComparingTo("50.0000");
                    assertThat(row.getEntryType()).isEqualTo("NORMAL");
                });
            }

            @Test
            @DisplayName("核销分页返回 source/target 单号与反向方向，按目标负责人范围过滤")
            void queryReturnsWriteOffRows() {
                Long customerId = newCustomer();
                FinanceReceiptVO receipt = addReceipt(customerId, "50.0000");
                Long targetId = normalReceivable(customerId, "50.0000");
                FinanceWriteOffVO created = financeWriteOffService.add(
                        writeOffForm("RECEIPT", receipt.getReceiptId(), writeOffItem(targetId, "10.0000")),
                        key("query-add")).getItems().getFirst();
                FinanceWriteOffQueryForm form = writeOffQueryForm(receipt.getReceiptNo());

                var page = financeWriteOffQueryService.query(form);

                assertThat(page.getTotal()).isEqualTo(1L);
                assertThat(page.getList()).singleElement().satisfies(row -> {
                    assertThat(row.getWriteOffId()).isEqualTo(created.getWriteOffId());
                    assertThat(row.getSourceNo()).isEqualTo(receipt.getReceiptNo());
                    assertThat(row.getTargetNo()).isNotBlank();
                    assertThat(row.getEntryType()).isEqualTo("NORMAL");
                });
            }

            @Test
            @DisplayName("跨客户或跨供应商核销被拒绝并保留 41136")
            void counterpartyMismatchIsRejected() {
                FinanceReceiptVO receipt = addReceipt(newCustomer(), "100.0000");
                Long otherCustomerId = newCustomer();
                Long targetId = normalReceivable(otherCustomerId, "100.0000");

                expectCode(() -> financeWriteOffService.add(
                        writeOffForm("RECEIPT", receipt.getReceiptId(), writeOffItem(targetId, "10.0000")),
                        key("cross-customer")), 41136);

                assertThat(count("SELECT count(*) FROM xsy_finance_write_off WHERE source_type = 'RECEIPT' AND source_id = ?",
                        receipt.getReceiptId())).isZero();
            }

            @Test
            @DisplayName("供应商付款不能核到另一家供应商的应付")
            void supplierPaymentCannotBeAppliedAcrossSuppliers() {
                FinancePaymentVO payment = addSupplierPayment(newSupplier("WO-SOURCE-SUPPLIER"), "100.0000");
                Long payableId = normalPayable(newSupplier("WO-TARGET-SUPPLIER"), "100.0000");

                expectCode(() -> financeWriteOffService.add(
                        writeOffForm("PAYMENT", payment.getPaymentId(), writeOffItem(payableId, "10.0000")),
                        key("cross-supplier")), 41136);

                assertThat(count("SELECT count(*) FROM xsy_finance_write_off WHERE source_type = 'PAYMENT' AND source_id = ?",
                        payment.getPaymentId())).isZero();
            }

            @Test
            @DisplayName("客户退款付款不能用于供应商应付核销")
            void customerRefundPaymentCannotBeAppliedToSupplierPayable() {
                Long customerId = newCustomer();
                Long paymentId = insertCustomerRefundPayment(customerId);
                Long payableId = normalPayable(newSupplier("WO-CUSTOMER-PAYMENT"), "100.0000");

                expectCode(() -> financeWriteOffService.add(
                        writeOffForm("PAYMENT", paymentId, writeOffItem(payableId, "10.0000")),
                        key("customer-payment-to-payable")), 41136);

                assertThat(count("SELECT count(*) FROM xsy_finance_write_off WHERE source_type = 'PAYMENT' AND source_id = ?",
                        paymentId)).isZero();
            }

            @Test
            @DisplayName("单次金额超过资金待核销额或目标 openAmount 时返回 41135")
            void sourceAndTargetLimitsAreBothEnforced() {
                Long customerId = newCustomer();
                FinanceReceiptVO receipt = addReceipt(customerId, "50.0000");
                Long targetId = normalReceivable(customerId, "20.0000");

                expectCode(() -> financeWriteOffService.add(
                        writeOffForm("RECEIPT", receipt.getReceiptId(), writeOffItem(targetId, "21.0000")),
                        key("target-limit")), 41135);
                expectCode(() -> financeWriteOffService.add(
                        writeOffForm("RECEIPT", receipt.getReceiptId(), writeOffItem(targetId, "51.0000")),
                        key("source-limit")), 41135);

                assertThat(count("SELECT count(*) FROM xsy_finance_write_off WHERE source_type = 'RECEIPT' AND source_id = ?",
                        receipt.getReceiptId())).isZero();
            }

            @Test
            @DisplayName("负净应收的 openAmount 为零，不能继续核销")
            void negativeNetReceivableCannotBeWrittenOff() {
                Long customerId = newCustomer();
                FinanceReceiptVO receipt = addReceipt(customerId, "50.0000");
                Long targetId = normalReceivable(customerId, "20.0000");
                insertRedReceivable(targetId, customerId, "30.0000");

                expectCode(() -> financeWriteOffService.add(
                        writeOffForm("RECEIPT", receipt.getReceiptId(), writeOffItem(targetId, "1.0000")),
                        key("negative-open")), 41135);

                assertThat(count("SELECT count(*) FROM xsy_finance_write_off WHERE source_type = 'RECEIPT' AND source_id = ?",
                        receipt.getReceiptId())).isZero();
            }

            @Test
            @DisplayName("反向核销追加等额 REVERSE 行，更新派生余额且同一记录只能反向一次")
            void reverseWriteOffAppendsOppositeFact() {
                Long customerId = newCustomer();
                FinanceReceiptVO receipt = addReceipt(customerId, "100.0000");
                Long targetId = normalReceivable(customerId, "80.0000");
                FinanceWriteOffVO normal = financeWriteOffService.add(
                        writeOffForm("RECEIPT", receipt.getReceiptId(), writeOffItem(targetId, "40.0000")),
                        key("reverse-source-add")).getItems().getFirst();
                Map<String, Object> originalBefore = writeOffRow(normal.getWriteOffId());
                FinanceWriteOffReverseForm form = reverseForm(normal.getWriteOffId(), "重复核销");

                FinanceWriteOffVO reversal = financeWriteOffService.reverse(form, key("reverse-write-off"));
                expectCode(() -> financeWriteOffService.reverse(form, key("reverse-write-off-again")), 41143);

                assertThat(reversal.getEntryType()).isEqualTo("REVERSE");
                assertThat(reversal.getReverseOfId()).isEqualTo(normal.getWriteOffId());
                assertThat(reversal.getAmount()).isEqualByComparingTo("40.0000");
                assertThat(reversal.getReason()).isEqualTo("重复核销");
                Map<String, Object> originalAfter = writeOffRow(normal.getWriteOffId());
                assertThat(originalAfter.get("entry_type")).isEqualTo("NORMAL");
                assertThat(originalAfter.get("reverse_of_id")).isNull();
                assertThat(originalAfter.get("reason")).isNull();
                assertThat(originalAfter.get("amount")).isEqualTo(originalBefore.get("amount"));
                assertThat(count("SELECT count(*) FROM xsy_finance_write_off WHERE reverse_of_id = ?",
                        normal.getWriteOffId())).isEqualTo(1);
                assertThat(count("SELECT count(*) FROM xsy_finance_operation_log WHERE business_type = 'WRITE_OFF'"
                        + " AND business_id = ? AND operation_type = 'WRITE_OFF_REVERSE'", normal.getWriteOffId()))
                        .isEqualTo(1);
            }

            @Test
            @DisplayName("核销目标的数据范围校验失败时拒绝操作")
            void targetScopeIsEnforced() {
                Long customerId = newCustomer();
                FinanceReceiptVO receipt = addReceipt(customerId, "50.0000");
                Long targetId = normalReceivable(customerId, "50.0000");
                financeWriteOffService.add(
                        writeOffForm("RECEIPT", receipt.getReceiptId(), writeOffItem(targetId, "10.0000")),
                        key("in-scope-target-write-off"));
                setEmployeeWithoutScope();

                assertThatThrownBy(() -> financeWriteOffService.add(
                        writeOffForm("RECEIPT", receipt.getReceiptId(), writeOffItem(targetId, "10.0000")),
                        key("target-out-of-scope"))).isInstanceOf(ScmDataScopeException.class);

                assertThat(count("SELECT count(*) FROM xsy_finance_write_off WHERE source_id = ? AND source_type = 'RECEIPT'",
                        receipt.getReceiptId())).isEqualTo(1);
                FinanceWriteOffQueryForm queryForm = writeOffQueryForm(receipt.getReceiptNo());
                assertThat(financeWriteOffQueryService.query(queryForm).getTotal()).isZero();
            }

            @Test
            @DisplayName("手工红字应付追加 RED 单据和明细，且累计金额不得超过原应付")
            void manualRedPayableIsCappedAndAppendOnly() {
                Long supplierId = newSupplier("WO-RED");
                Long originalId = normalPayable(supplierId, "100.0000");
                Map<String, Object> originalBefore = payableRow(originalId);
                FinancePayableRedForm firstForm = payableRedForm(originalId, "退货差额", redItem("2.0000", "10.0000"));
                String key = key("payable-red");

                FinancePayableRedVO red = financePayableService.red(firstForm, key);
                FinancePayableRedVO replay = financePayableService.red(firstForm, key);
                expectCode(() -> financePayableService.red(
                        payableRedForm(originalId, "超额红字", redItem("8.1000", "10.0000")), key("payable-red-over")), 41137);

                assertThat(replay.getPayableId()).isEqualTo(red.getPayableId());
                assertThat(red.getEntryType()).isEqualTo("RED");
                assertThat(red.getOriginalPayableId()).isEqualTo(originalId);
                assertThat(red.getAmount()).isEqualByComparingTo("20.0000");
                assertThat(count("SELECT count(*) FROM xsy_finance_payable WHERE original_payable_id = ?"
                        + " AND entry_type = 'RED'", originalId)).isEqualTo(1);
                Map<String, Object> originalAfter = payableRow(originalId);
                assertThat(originalAfter.get("entry_type")).isEqualTo("NORMAL");
                assertThat(originalAfter.get("amount")).isEqualTo(originalBefore.get("amount"));
                assertThat(count("SELECT count(*) FROM xsy_finance_payable_item WHERE payable_id = ?"
                        + " AND source_type = 'MANUAL' AND source_id IS NULL", red.getPayableId())).isEqualTo(1);
                assertThat(count("SELECT count(*) FROM xsy_finance_operation_log WHERE business_type = 'PAYABLE'"
                        + " AND business_id = ? AND operation_type = 'RED_GENERATE'", red.getPayableId())).isEqualTo(1);
            }

            @Test
            @DisplayName("手工红字额度不扣除既有核销额，超额核销继续作为派生记录表达")
            void manualRedCapDoesNotSubtractExistingWriteOffs() {
                Long supplierId = newSupplier("WO-RED-OVER-APPLIED");
                Long originalId = normalPayable(supplierId, "100.0000");
                FinancePaymentVO payment = addSupplierPayment(supplierId, "90.0000");
                financeWriteOffService.add(
                        writeOffForm("PAYMENT", payment.getPaymentId(), writeOffItem(originalId, "90.0000")),
                        key("write-off-before-red"));

                FinancePayableRedVO red = financePayableService.red(
                        payableRedForm(originalId, "退货调整", redItem("2.0000", "10.0000")), key("red-after-write-off"));

                assertThat(red.getAmount()).isEqualByComparingTo("20.0000");
                assertThat(jdbc.queryForObject("SELECT amount FROM xsy_finance_payable WHERE original_payable_id = ?"
                        + " AND entry_type = 'RED'", BigDecimal.class, originalId)).isEqualByComparingTo("20.0000");
                assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM xsy_finance_write_off WHERE target_type = 'PAYABLE'"
                        + " AND target_id = ? AND entry_type = 'NORMAL'", BigDecimal.class, originalId))
                        .isEqualByComparingTo("90.0000");
            }

            @Test
            @DisplayName("手工红字应付按 purchaserScope 校验失败时拒绝操作")
            void manualRedPayableRequiresPurchaserScope() {
                Long originalId = normalPayable(newSupplier("WO-RED-SCOPE"), "100.0000");
                setEmployeeWithoutScope();

                assertThatThrownBy(() -> financePayableService.red(
                        payableRedForm(originalId, "无范围红字", redItem("1.0000", "10.0000")), key("red-out-of-scope")))
                        .isInstanceOf(ScmDataScopeException.class);

                assertThat(count("SELECT count(*) FROM xsy_finance_payable WHERE original_payable_id = ?"
                        + " AND entry_type = 'RED'", originalId)).isZero();
            }

            private FinanceReceiptVO addReceipt(Long customerId, String amount) {
                var form = new com.xsy.scm.finance.domain.form.FinanceReceiptAddForm();
                form.setCustomerId(customerId);
                form.setAmount(amount);
                form.setMethod("BANK_TRANSFER");
                form.setReceivedAt(EVENT_AT);
                return financeReceiptService.add(form, key("receipt-add"));
            }

            private FinancePaymentVO addSupplierPayment(Long supplierId, String amount) {
                var form = new com.xsy.scm.finance.domain.form.FinancePaymentAddForm();
                form.setCounterpartyType("SUPPLIER");
                form.setCounterpartyId(supplierId);
                form.setAmount(amount);
                form.setMethod("BANK_TRANSFER");
                form.setPaidAt(EVENT_AT);
                return financePaymentService.add(form, key("payment-add"));
            }

            private Long normalReceivable(Long customerId, String amount) {
                long sourceId = uniqueNumber();
                return jdbc.queryForObject("INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id, order_id,"
                                + " customer_id, customer_name_snapshot, settlement_customer_id,"
                                + " settlement_customer_name_snapshot, entry_type, amount, event_at, created_at, updated_at)"
                                + " VALUES (?, 'SALES_ORDER', ?, 1, ?, '核销测试客户', ?, '核销测试客户', 'NORMAL', ?,"
                                + " now(), now(), now())"
                                + " RETURNING id",
                        Long.class, "AR-WO-" + prefix + "-" + UUID.randomUUID(), sourceId, customerId, customerId,
                        new BigDecimal(amount));
            }

            private void insertRedReceivable(Long originalId, Long customerId, String amount) {
                long sourceId = uniqueNumber();
                jdbc.update("INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id, order_id, customer_id,"
                                + " customer_name_snapshot, settlement_customer_id,"
                                + " settlement_customer_name_snapshot, entry_type, original_receivable_id, amount, event_at, reason)"
                                + " VALUES (?, 'ORDER_RETURN', ?, 1, ?, '核销测试客户', ?, '核销测试客户', 'RED', ?, ?, now(), '测试红字')",
                        "AR-WO-RED-" + prefix + "-" + UUID.randomUUID(), sourceId, customerId, customerId, originalId,
                        new BigDecimal(amount));
            }

            private Long normalPayable(Long supplierId, String amount) {
                long receiptId = uniqueNumber();
                Long payableId = jdbc.queryForObject("INSERT INTO xsy_finance_payable (payable_no, source_type, source_id,"
                                + " purchase_order_id, supplier_id, supplier_name_snapshot, entry_type, amount, event_at)"
                                + " VALUES (?, 'PURCHASE_RECEIPT', ?, 1, ?, '核销测试供应商', 'NORMAL', ?, now()) RETURNING id",
                        Long.class, "AP-WO-" + prefix + "-" + UUID.randomUUID(), receiptId, supplierId,
                        new BigDecimal(amount));
                jdbc.update("INSERT INTO xsy_finance_payable_item (payable_id, source_type, source_id, purchase_order_item_id,"
                                + " sku_id, sku_name_snapshot, unit_snapshot, quantity, unit_price, amount)"
                                + " VALUES (?, 'PURCHASE_RECEIPT_ITEM', ?, 44, 1, '测试商品', 'kg', 10.0000, 10.0000, 100.0000)",
                        payableId, uniqueNumber());
                return payableId;
            }

            private Long insertCustomerRefundPayment(Long customerId) {
                return jdbc.queryForObject("INSERT INTO xsy_finance_payment (payment_no, counterparty_type, counterparty_id,"
                                + " counterparty_name_snapshot, amount, method, paid_at, entry_type, source_type, source_id)"
                                + " VALUES (?, 'CUSTOMER', ?, '核销测试客户', 100.0000, 'BANK_TRANSFER', now(),"
                                + " 'NORMAL', 'ORDER_REFUND', ?) RETURNING id",
                        Long.class, "PM-WO-" + prefix + "-" + UUID.randomUUID().toString().substring(0, 8),
                        customerId, uniqueNumber());
            }

            private FinanceWriteOffAddForm writeOffForm(String sourceType, Long sourceId,
                    FinanceWriteOffAddItemForm... items) {
                FinanceWriteOffAddForm form = new FinanceWriteOffAddForm();
                form.setSourceType(sourceType);
                form.setSourceId(sourceId);
                form.setItems(List.of(items));
                return form;
            }

            private FinanceWriteOffAddItemForm writeOffItem(Long targetId, String amount) {
                FinanceWriteOffAddItemForm item = new FinanceWriteOffAddItemForm();
                item.setTargetId(targetId);
                item.setAmount(amount);
                return item;
            }

            private FinanceWriteOffReverseForm reverseForm(Long writeOffId, String reason) {
                FinanceWriteOffReverseForm form = new FinanceWriteOffReverseForm();
                form.setWriteOffId(writeOffId);
                form.setReason(reason);
                return form;
            }

            private FinanceWriteOffQueryForm writeOffQueryForm(String sourceNo) {
                FinanceWriteOffQueryForm form = new FinanceWriteOffQueryForm();
                form.setPageNum(1L);
                form.setPageSize(20L);
                form.setSourceNo(sourceNo);
                form.setStartDate(LocalDate.now(ZoneOffset.ofHours(8)).minusDays(1));
                form.setEndDate(LocalDate.now(ZoneOffset.ofHours(8)));
                return form;
            }

            private FinancePayableRedForm payableRedForm(Long originalPayableId, String reason,
                    FinancePayableRedItemForm... items) {
                FinancePayableRedForm form = new FinancePayableRedForm();
                form.setOriginalPayableId(originalPayableId);
                form.setReason(reason);
                form.setItems(List.of(items));
                return form;
            }

            private FinancePayableRedItemForm redItem(String quantity, String unitPrice) {
                FinancePayableRedItemForm item = new FinancePayableRedItemForm();
                item.setPurchaseOrderItemId(44L);
                item.setQuantity(quantity);
                item.setUnitPrice(unitPrice);
                item.setAmount(new BigDecimal(quantity).multiply(new BigDecimal(unitPrice))
                        .setScale(4, java.math.RoundingMode.HALF_UP).toPlainString());
                return item;
            }

            private void setEmployeeWithoutScope() {
                String loginName = (prefix + "-NO-SCOPE").toUpperCase();
                jdbc.update("INSERT INTO t_employee (employee_uid, login_name, login_pwd, actual_name, department_id,"
                                + " administrator_flag, deleted_flag) VALUES (?, ?, ?, ?, 1, FALSE, FALSE)",
                        UUID.randomUUID().toString().replace("-", ""), loginName,
                        "$argon2id$it-placeholder", "无范围财务员工");
                Long employeeId = jdbc.queryForObject(
                        "SELECT employee_id FROM t_employee WHERE login_name = ?", Long.class, loginName);
                RequestEmployee employee = new RequestEmployee();
                employee.setEmployeeId(employeeId);
                employee.setActualName("无范围财务员工");
                employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
                employee.setAdministratorFlag(false);
                XsyRequestUtil.setRequestUser(employee);
                evictMybatisCache();
            }

            private Map<String, Object> writeOffRow(Long id) {
                return jdbc.queryForMap("SELECT * FROM xsy_finance_write_off WHERE id = ?", id);
            }

            private Map<String, Object> payableRow(Long id) {
                return jdbc.queryForMap("SELECT * FROM xsy_finance_payable WHERE id = ?", id);
            }

            private String key(String tag) {
                return prefix + ":" + tag + ":" + UUID.randomUUID();
            }

            private long uniqueNumber() {
                return 1_000_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 8_000_000_000L);
            }

            private int count(String sql, Object... args) {
                Integer value = jdbc.queryForObject(sql, Integer.class, args);
                return value == null ? 0 : value;
            }
        }
    }

    @Nested
    class FinanceW6PgIT extends ScmW6PgITBase {
        @Nested
        @DisplayName("收货确认生成应付（Finance R1 F1-2A，PG IT）")
        class ScmFinancePayablePgIT {
            @Autowired
            private FinancePayableService financePayableService;

            // ------------------------------------------------------------------
            // 夹具
            // ------------------------------------------------------------------

            /**
             * 单行采购单 + 指定入库方式的草稿收货单。
             *
             * <p>不复用 {@link ScmW6PgITBase#inboundFixture}：那条链带需求来源，需求分配又要求
             * 采购单位与销售单位一致，而本类需要的是单价与入库方式两个自由度。
             */
            private PurchaseReceiptVO draftReceipt(String suffix, Long skuId, String planned,
                                                   String price, ScmReceiptModeEnum mode) {
                Long supplierId = newSupplier(suffix);
                linkSupplierSku(supplierId, skuId, DEFAULT_PURCHASE_UNIT);
                PurchaseOrderVO order = createDraftOrder(suffix, supplierId, skuId, planned, price);
                submitOrder(order.getId());
                return draftReceiptOf(suffix, order.getId(), mode);
            }

            private PurchaseReceiptVO draftReceiptOf(String suffix, Long orderId, ScmReceiptModeEnum mode) {
                PurchaseReceiptCreateForm form = new PurchaseReceiptCreateForm();
                form.setPurchaseOrderId(orderId);
                form.setReceiptMode(mode.name());
                form.setRemark("F1-2A IT 收货单 " + suffix);
                // 防重复请求编号必须逐张唯一：同一采购单的多张收货单是分次到货的正常业务，不限张数；
                // 复用同一个键会因为内容不同而得到 40990，那看起来像本类的断言错了。
                return purchaseReceiptService.create(form, prefix + ":receipt:" + orderId + ":" + suffix);
            }

            /**
             * 两行采购单（两个不同 SKU）的 DIRECT 草稿收货单；行顺序 = {@code skuA} 在前。
             */
            private PurchaseReceiptVO draftReceiptTwoLines(String suffix, Long skuA, String priceA,
                                                           Long skuB, String priceB) {
                Long supplierId = newSupplier(suffix);
                linkSupplierSkus(supplierId, skuA, skuB);

                PurchaseOrderAddForm form = orderForm(supplierId, seedWarehouseId(), skuA, "10.0000", priceA);
                form.setItems(new ArrayList<>(List.of(
                        item(skuA, "10.0000", priceA),
                        item(skuB, "10.0000", priceB))));
                PurchaseOrderVO order = purchaseOrderService.create(form, prefix + ":" + suffix + ":po2");
                submitOrder(order.getId());
                return draftReceiptOf(suffix, order.getId(), ScmReceiptModeEnum.DIRECT);
            }

            /**
             * 按收货行录入顺序提交全部活动行（请求行集合必须等于活动行集合，少一行即 40998）。
             */
            private PurchaseReceiptVO confirmAllLines(Long receiptId, String... quantities) {
                PurchaseReceiptVO current = reloadReceipt(receiptId);
                List<PurchaseReceiptItemVO> lines = current.getItems();
                assertThat(lines).as("提交数量数必须与收货行数一致").hasSize(quantities.length);

                List<PurchaseReceiptConfirmForm.Item> items = new ArrayList<>(lines.size());
                for (int index = 0; index < lines.size(); index++) {
                    PurchaseReceiptItemVO line = lines.get(index);
                    items.add(receiptLine(line.getId(), line.getVersion(), quantities[index]));
                }
                return purchaseReceiptService.confirm(
                        confirmForm(current.getId(), current.getVersion(),
                                items.toArray(PurchaseReceiptConfirmForm.Item[]::new)),
                        prefix + ":confirm:" + receiptId + ":" + UUID.randomUUID());
            }

            private PurchaseReceiptVO putaway(Long receiptId) {
                PurchaseReceiptVO current = reloadReceipt(receiptId);
                PurchaseReceiptPutawayForm form = new PurchaseReceiptPutawayForm();
                form.setId(receiptId);
                form.setVersion(current.getVersion());
                return purchaseReceiptService.putaway(
                        form, prefix + ":putaway:" + receiptId + ":" + UUID.randomUUID());
            }

            // ------------------------------------------------------------------
            // 断言辅助
            // ------------------------------------------------------------------

            private Map<String, Object> payableOf(Long receiptId) {
                List<Map<String, Object>> rows = jdbc.queryForList(
                        "SELECT * FROM xsy_finance_payable WHERE source_type = 'PURCHASE_RECEIPT' AND source_id = ?",
                        receiptId);
                assertThat(rows).as("收货单 %s 的应付单（恰好一张）", receiptId).hasSize(1);
                return rows.getFirst();
            }

            private List<Map<String, Object>> payablesOfReceipt(Long receiptId) {
                return jdbc.queryForList(
                        "SELECT * FROM xsy_finance_payable WHERE source_type = 'PURCHASE_RECEIPT' AND source_id = ?",
                        receiptId);
            }

            private int payablesOfOrder(Long purchaseOrderId) {
                return jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_finance_payable WHERE purchase_order_id = ?",
                        Integer.class, purchaseOrderId);
            }

            private List<Map<String, Object>> payableItemsOf(Long payableId) {
                return jdbc.queryForList(
                        "SELECT * FROM xsy_finance_payable_item WHERE payable_id = ? ORDER BY id", payableId);
            }

            /**
             * 这张收货单在财务侧留下的明细行数（作用域到行，不用全表计数）。
             */
            private int payableItemCountOfReceipt(Long receiptId) {
                return jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_finance_payable_item i WHERE i.source_type = 'PURCHASE_RECEIPT_ITEM' "
                                + "AND i.source_id IN (SELECT id FROM xsy_purchase_receipt_item WHERE purchase_receipt_id = ?)",
                        Integer.class, receiptId);
            }

            private List<Map<String, Object>> payableLogsOf(Long payableId) {
                return jdbc.queryForList(
                        "SELECT * FROM xsy_finance_operation_log WHERE business_type = 'PAYABLE' AND business_id = ?",
                        payableId);
            }

            private static Long longOf(Map<String, Object> row, String column) {
                return ((Number) row.get(column)).longValue();
            }

            private static BigDecimal decimalOf(Map<String, Object> row, String column) {
                return (BigDecimal) row.get(column);
            }

            private static OffsetDateTime timeOf(Map<String, Object> row, String column) {
                return timestampOf(row.get(column));
            }

            // ------------------------------------------------------------------
            // A / I. DIRECT 收货确认 → 单头 + 明细 + 操作日志
            // ------------------------------------------------------------------

            @Test
            @DisplayName("DIRECT：确认后一张 NORMAL 应付，量=有效收货量、价=采购行单价、日志同事务")
            void directConfirmGeneratesPayableWithItemsAndLog() throws Exception {
                Long skuId = newOnShelfSku("FPA");
                PurchaseReceiptVO receipt = draftReceipt("FPA", skuId, "8.0000", "6.2000", ScmReceiptModeEnum.DIRECT);
                confirmAllLines(receipt.getId(), "8.0000");

                Map<String, Object> payable = payableOf(receipt.getId());
                Long payableId = longOf(payable, "id");
                assertThat(payable.get("entry_type")).isEqualTo("NORMAL");
                assertThat(longOf(payable, "source_id")).isEqualTo(receipt.getId());
                assertThat(longOf(payable, "purchase_order_id")).isEqualTo(receipt.getPurchaseOrderId());
                assertThat(longOf(payable, "supplier_id")).isEqualTo(receipt.getSupplierId());
                // 供应商名取收货记录上的已存数据，不回读供应商主档（主档改名不会改历史账）
                assertThat(payable.get("supplier_name_snapshot")).isEqualTo(receipt.getSupplierName());
                assertThat(decimalOf(payable, "amount")).isEqualByComparingTo("49.6000");
                assertThat(payable.get("reason")).isNull();
                assertThat(payable.get("original_payable_id")).isNull();
                assertThat(payable.get("deleted")).isEqualTo(false);
                assertThat(String.valueOf(payable.get("payable_no"))).matches("^XSY\\d{18,19}$");
                // 应付时点 = 收货确认时刻（DIRECT 下也等于物理入库时刻，但规则取的是 confirmed_at）
                assertThat(timeOf(payable, "event_at").toInstant())
                        .isEqualTo(receiptConfirmedAt(receipt.getId()).toInstant());

                List<Map<String, Object>> items = payableItemsOf(payableId);
                assertThat(items).hasSize(1);
                Map<String, Object> line = items.getFirst();
                Long receiptItemId = receipt.getItems().getFirst().getId();
                assertThat(line.get("source_type")).isEqualTo("PURCHASE_RECEIPT_ITEM");
                assertThat(longOf(line, "source_id")).isEqualTo(receiptItemId);
                assertThat(longOf(line, "purchase_order_item_id"))
                        .isEqualTo(receipt.getItems().getFirst().getPurchaseOrderItemId());
                assertThat(longOf(line, "sku_id")).isEqualTo(skuId);
                assertThat(line.get("sku_name_snapshot")).isEqualTo(jdbc.queryForObject(
                        "SELECT sku_name_snapshot FROM xsy_purchase_receipt_item WHERE id = ?", String.class, receiptItemId));
                assertThat(line.get("unit_snapshot")).isEqualTo(DEFAULT_PURCHASE_UNIT);
                assertThat(decimalOf(line, "quantity")).isEqualByComparingTo("8.0000");
                assertThat(decimalOf(line, "unit_price")).isEqualByComparingTo("6.2000");
                assertThat(decimalOf(line, "amount")).isEqualByComparingTo("49.6000");
                assertThat(line.get("deleted")).isEqualTo(false);

                // 日志与应付同事务：GENERATE 没有「改前」，改后是单头保存的数据，operator 是本次收货确认的人
                List<Map<String, Object>> logs = payableLogsOf(payableId);
                assertThat(logs).hasSize(1);
                Map<String, Object> log = logs.getFirst();
                assertThat(log.get("operation_type")).isEqualTo("GENERATE");
                assertThat(log.get("reason")).isNull();
                assertThat(log.get("before_data")).isNull();
                assertThat(log.get("operator")).isEqualTo(ScmOperator.current());
                // after_data 走 JSON 解析而不是字符串包含：JSONB 的存储形态会重排键序与空白，
                // 按子串断言等于把测试绑在序列化器的排版细节上。
                Map<String, Object> after = objectMapper.readValue(String.valueOf(log.get("after_data")),
                        new TypeReference<Map<String, Object>>() {
                        });
                assertThat(after)
                        .containsEntry("payableNo", payable.get("payable_no"))
                        .containsEntry("amount", "49.6000")
                        .containsEntry("entryType", "NORMAL")
                        .containsEntry("sourceType", "PURCHASE_RECEIPT")
                        .containsEntry("itemCount", 1);
                // JSON 数字读回来是 Integer 还是 Long 取决于值的大小，因此按数值比较而不是直接比对象
                assertThat(((Number) after.get("sourceId")).longValue()).isEqualTo(receipt.getId());
                assertThat(((Number) after.get("purchaseOrderId")).longValue())
                        .isEqualTo(receipt.getPurchaseOrderId());
            }

            // ------------------------------------------------------------------
            // B / C. WAREHOUSE_CONFIRM 的应付时点是「确认」，不是「上架」
            // ------------------------------------------------------------------

            @Test
            @DisplayName("WAREHOUSE_CONFIRM：确认即生成应付且 event_at=confirmed_at；putaway 不追加也不改时点")
            void warehouseConfirmGeneratesAtConfirmTimeNotPutawayTime() {
                Long skuId = newOnShelfSku("FPB");
                PurchaseReceiptVO receipt = draftReceipt("FPB", skuId, "8.0000", "6.2000",
                        ScmReceiptModeEnum.WAREHOUSE_CONFIRM);
                confirmAllLines(receipt.getId(), "8.0000");

                Map<String, Object> payable = payableOf(receipt.getId());
                Long payableId = longOf(payable, "id");
                OffsetDateTime confirmedAt = receiptConfirmedAt(receipt.getId());
                // 库存还没入账（putaway 仍 PENDING、无流水），应付已经成立 —— 应付只由收货记录派生，不等到货入库
                assertThat(reloadReceipt(receipt.getId()).getPutawayStatus()).isEqualTo("PENDING");
                assertThat(movementCount(seedWarehouseId(), skuId)).isZero();
                assertThat(timeOf(payable, "event_at").toInstant()).isEqualTo(confirmedAt.toInstant());
                assertThat(payablesOfOrder(receipt.getPurchaseOrderId())).isEqualTo(1);

                assertThat(putaway(receipt.getId()).getPutawayStatus()).isEqualTo("COMPLETED");

                assertThat(payablesOfOrder(receipt.getPurchaseOrderId())).as("上架不追加应付").isEqualTo(1);
                Map<String, Object> afterPutaway = payableOf(receipt.getId());
                assertThat(longOf(afterPutaway, "id")).isEqualTo(payableId);
                assertThat(timeOf(afterPutaway, "event_at").toInstant())
                        .as("上架也不改时点").isEqualTo(confirmedAt.toInstant());
                assertThat(payableItemsOf(payableId)).hasSize(1);
                assertThat(payableLogsOf(payableId)).hasSize(1);
            }

            // ------------------------------------------------------------------
            // D. 金额 = 量 × 价，scale 4、HALF_UP
            // ------------------------------------------------------------------

            @Test
            @DisplayName("金额计算规则：3.3333 × 2.2222 = 7.40725926 → 7.4073（HALF_UP 到 4 位，不是截断）")
            void itemAmountRoundsHalfUpAtScaleFour() {
                Long skuId = newOnShelfSku("FPC");
                PurchaseReceiptVO receipt = draftReceipt("FPC", skuId, "4.0000", "2.2222", ScmReceiptModeEnum.DIRECT);
                confirmAllLines(receipt.getId(), "3.3333");

                Map<String, Object> payable = payableOf(receipt.getId());
                Map<String, Object> line = payableItemsOf(longOf(payable, "id")).getFirst();
                assertThat(decimalOf(line, "quantity")).isEqualByComparingTo("3.3333");
                assertThat(decimalOf(line, "unit_price")).isEqualByComparingTo("2.2222");
                assertThat(decimalOf(line, "amount")).isEqualByComparingTo("7.4073");
                assertThat(decimalOf(payable, "amount")).as("单头 = 已舍入的行金额之和").isEqualByComparingTo("7.4073");
            }

            // ------------------------------------------------------------------
            // E. 合法容差内超收：全额进应付
            // ------------------------------------------------------------------

            @Test
            @DisplayName("超收：planned 10 收 11（默认容差 10%）→ 应付按 11.0000 全额计")
            void overReceiptWithinToleranceIsFullyPayable() {
                Long skuId = newOnShelfSku("FPD");
                PurchaseReceiptVO receipt = draftReceipt("FPD", skuId, "10.0000", "6.2000", ScmReceiptModeEnum.DIRECT);
                PurchaseReceiptVO confirmed = confirmAllLines(receipt.getId(), "11.0000");

                assertThat(confirmed.getItems().getFirst().getOverReceiptQuantity())
                        .isEqualByComparingTo("1.0000");

                Map<String, Object> payable = payableOf(receipt.getId());
                Map<String, Object> line = payableItemsOf(longOf(payable, "id")).getFirst();
                assertThat(decimalOf(line, "quantity")).isEqualByComparingTo("11.0000");
                assertThat(decimalOf(payable, "amount")).isEqualByComparingTo("68.2000");
            }

            // ------------------------------------------------------------------
            // F. 少收：只按实际确认量形成，不产生任何差异记录
            // ------------------------------------------------------------------

            @Test
            @DisplayName("少收：planned 10 收 4 → 应付 24.8000；采购侧的差异/剩余量一个都不进财务表")
            void shortReceiptPaysOnlyTheConfirmedQuantity() {
                Long skuId = newOnShelfSku("FPE");
                PurchaseReceiptVO receipt = draftReceipt("FPE", skuId, "10.0000", "6.2000", ScmReceiptModeEnum.DIRECT);
                confirmAllLines(receipt.getId(), "4.0000");

                Map<String, Object> payable = payableOf(receipt.getId());
                List<Map<String, Object>> items = payableItemsOf(longOf(payable, "id"));
                assertThat(items).as("一条收货行只有一行应付明细").hasSize(1);
                assertThat(decimalOf(items.getFirst(), "quantity")).isEqualByComparingTo("4.0000");
                assertThat(decimalOf(payable, "amount")).isEqualByComparingTo("24.8000");

                // 「不复制差异数据」：这张收货单在财务侧只有这一张单、这一行。
                // 尤其不得出现一条表达「还欠 6.0000」的差异行，也不得有 RED 单。
                assertThat(payableItemCountOfReceipt(receipt.getId()))
                        .as("本张收货单只留下一行应付明细").isEqualTo(1);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_finance_payable WHERE purchase_order_id = ? AND entry_type = 'RED'",
                        Integer.class, receipt.getPurchaseOrderId())).isZero();
            }

            // ------------------------------------------------------------------
            // G. 整单 0 元：不产生 0 元财务记录
            // ------------------------------------------------------------------

            @Test
            @DisplayName("单价合法为 0：有效量存在但金额为 0 → 成功跳过，不产生 0 元应付也不留日志")
            void zeroAmountFactsAreNotGenerated() {
                Long skuId = newOnShelfSku("FPF");
                PurchaseReceiptVO receipt = draftReceipt("FPF", skuId, "5.0000", "0.0000", ScmReceiptModeEnum.DIRECT);
                PurchaseReceiptVO confirmed = confirmAllLines(receipt.getId(), "5.0000");

                // 前置记录：收货本身成功了（跳过财务记录不是「整笔失败」的副作用）
                assertThat(confirmed.getStatus()).isEqualTo("CONFIRMED");
                assertThat(movementCount(seedWarehouseId(), skuId)).isEqualTo(1);

                assertThat(payablesOfReceipt(receipt.getId())).as("0 元收货不产生应付单头").isEmpty();
                assertThat(payableItemCountOfReceipt(receipt.getId())).as("也没有任何应付明细").isZero();

                // 重放同样什么都不产生
                financePayableService.generateOnReceiptConfirm(receipt.getId());
                assertThat(payablesOfReceipt(receipt.getId())).isEmpty();
            }

            // ------------------------------------------------------------------
            // H. 0 元明细留在应付单里，只要单头金额为正
            // ------------------------------------------------------------------

            @Test
            @DisplayName("两行（赠品行 0 元 + 正常行）：0 元明细照实入账不丢弃，单头为行之和 14.0000")
            void zeroPriceLineStaysInPayableWhileHeaderStaysPositive() {
                Long freeSku = newOnShelfSku("FPG-A");
                Long paidSku = newOnShelfSku("FPG-B");
                PurchaseReceiptVO receipt = draftReceiptTwoLines("FPG", freeSku, "0.0000", paidSku, "3.5000");
                confirmAllLines(receipt.getId(), "2.0000", "4.0000");

                Map<String, Object> payable = payableOf(receipt.getId());
                List<Map<String, Object>> items = payableItemsOf(longOf(payable, "id"));
                assertThat(items).as("两行收货 → 两行应付明细，金额为 0 的那一行也在").hasSize(2);

                Map<String, Object> free = items.get(0);
                assertThat(longOf(free, "sku_id")).isEqualTo(freeSku);
                assertThat(decimalOf(free, "quantity")).isEqualByComparingTo("2.0000");
                assertThat(decimalOf(free, "unit_price")).isEqualByComparingTo("0.0000");
                assertThat(decimalOf(free, "amount")).isEqualByComparingTo("0.0000");

                Map<String, Object> paid = items.get(1);
                assertThat(longOf(paid, "sku_id")).isEqualTo(paidSku);
                assertThat(decimalOf(paid, "quantity")).isEqualByComparingTo("4.0000");
                assertThat(decimalOf(paid, "amount")).isEqualByComparingTo("14.0000");

                assertThat(decimalOf(payable, "amount")).isEqualByComparingTo("14.0000");
            }

            // ------------------------------------------------------------------
            // I. 重复触发只有一张应付
            // ------------------------------------------------------------------

            @Test
            @DisplayName("重复生成：第二次命中来源唯一索引 → 仍是一张单、一份日志，且不报错")
            void duplicateGenerationKeepsSinglePayableAndSingleLog() {
                Long skuId = newOnShelfSku("FPH");
                PurchaseReceiptVO receipt = draftReceipt("FPH", skuId, "8.0000", "6.2000", ScmReceiptModeEnum.DIRECT);
                confirmAllLines(receipt.getId(), "8.0000");

                Long payableId = longOf(payableOf(receipt.getId()), "id");

                financePayableService.generateOnReceiptConfirm(receipt.getId());
                financePayableService.generateOnReceiptConfirm(receipt.getId());

                Map<String, Object> payable = payableOf(receipt.getId());
                assertThat(longOf(payable, "id")).as("重放不换单也不换号").isEqualTo(payableId);
                assertThat(payableItemsOf(payableId)).hasSize(1);
                assertThat(payableLogsOf(payableId)).as("已生成不是新记录，不重复留痕").hasSize(1);
            }

            // ------------------------------------------------------------------
            // J. 一张采购单多次收货 = 多张应付
            // ------------------------------------------------------------------

            @Test
            @DisplayName("分次到货：同一采购单的两张收货单各生成一张应付，互不合并、单号不重复")
            void secondReceiptOnTheSameOrderGeneratesItsOwnPayable() {
                Long skuId = newOnShelfSku("FPI");
                Long supplierId = newSupplier("FPI");
                linkSupplierSku(supplierId, skuId, DEFAULT_PURCHASE_UNIT);
                PurchaseOrderVO order = createDraftOrder("FPI", supplierId, skuId, "10.0000", "6.2000");
                submitOrder(order.getId());

                PurchaseReceiptVO first = draftReceiptOf("FPI-1", order.getId(), ScmReceiptModeEnum.DIRECT);
                confirmAllLines(first.getId(), "6.0000");
                PurchaseReceiptVO second = draftReceiptOf("FPI-2", order.getId(), ScmReceiptModeEnum.DIRECT);
                confirmAllLines(second.getId(), "4.0000");

                assertThat(payablesOfOrder(order.getId())).isEqualTo(2);
                Map<String, Object> firstPayable = payableOf(first.getId());
                Map<String, Object> secondPayable = payableOf(second.getId());
                assertThat(decimalOf(firstPayable, "amount")).isEqualByComparingTo("37.2000");
                assertThat(decimalOf(secondPayable, "amount")).isEqualByComparingTo("24.8000");
                assertThat(longOf(firstPayable, "id")).isNotEqualTo(longOf(secondPayable, "id"));
                // 两条单号来自同一条全局非重置序列
                assertThat(firstPayable.get("payable_no")).isNotEqualTo(secondPayable.get("payable_no"));
                // 两行明细各归各的单，来源都是自己的收货行
                assertThat(payableItemsOf(longOf(firstPayable, "id"))).hasSize(1);
                assertThat(payableItemsOf(longOf(secondPayable, "id"))).hasSize(1);
            }

            // ------------------------------------------------------------------
            // K. 未确认的收货单不产生应付
            // ------------------------------------------------------------------

            @Test
            @DisplayName("草稿收货单调用生成器 → 抛错且不产生应付（状态守卫在 SQL 谓词里，不靠调用方自觉）")
            void draftReceiptCannotGeneratePayable() {
                Long skuId = newOnShelfSku("FPK");
                PurchaseReceiptVO receipt = draftReceipt("FPK", skuId, "8.0000", "6.2000", ScmReceiptModeEnum.DIRECT);

                assertThatThrownBy(() -> financePayableService.generateOnReceiptConfirm(receipt.getId()))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("不能生成应付");

                assertThat(payablesOfReceipt(receipt.getId())).isEmpty();
                assertThat(reloadReceipt(receipt.getId()).getStatus()).isEqualTo("PENDING");
            }
        }

        @Nested
        @DisplayName("签收生成应收（Finance R1 F1-2B，PG IT）")
        class ScmFinanceReceivablePgIT {
            @Autowired
            private DeliveryRouteService routeService;

            @Autowired
            private FinanceReceivableService financeReceivableService;

            // ------------------------------------------------------------------
            // 夹具：下单 → 分拣 → 已规划线路
            // ------------------------------------------------------------------

            /** 一条链路所需的全部 id；{@code outboundItemId} 在发车之后才可取。 */
            private record Chain(Long routeId, Long warehouseId, Long skuId, Long customerId, Long orderId) {
            }

            /**
             * 建一条「地址齐备 + 已确认 + 已分拣 + 线路已规划」的链路，未发车。
             *
             * @param orderedQuantity 订单行下单量
             * @param marketPrice     SKU 市场价：订单走真实价格解析链路，因此价格只能通过主数据施加
             * @param sortedQuantity  分拣实发量（&lt; 下单量即为少拣）
             */
            private Chain sortedAndPlanned(String tag, String orderedQuantity, String marketPrice,
                                           String sortedQuantity) {
                Long warehouseId = locatedWarehouse(tag);
                Long skuId = newOnShelfSku(tag);
                withMarketPrice(skuId, marketPrice);
                stockIn(warehouseId, skuId, tag, "20.0000");

                Long customerId = newCustomer();
                jdbc.update("UPDATE xsy_customer SET address = ?, longitude = 113.94, latitude = 22.54,"
                        + " geom_crs = 'GCJ02' WHERE id = ?", "F1-2B IT 地址" + tag, customerId);
                evictMybatisCache();

                // 下单量 = 实数量（非标品的 confirm 前置），少拣只体现在分拣实发量上
                Long orderId = confirmedSalesOrder(customerId, skuId, orderedQuantity, orderedQuantity);
                sortOneLine(orderId, sortedQuantity, warehouseId);

                Long routeId = newRoute(warehouseId);
                attach(routeId, orderId);
                locateAllStops(routeId);
                plan(routeId);
                return new Chain(routeId, warehouseId, skuId, customerId, orderId);
            }

            /** 建两条订单挂同一线路（分单 / 补单场景）。 */
            private Chain sortedAndPlannedTwoOrders(String tag, String sortedQuantity) {
                Long warehouseId = locatedWarehouse(tag);
                Long skuId = newOnShelfSku(tag);
                stockIn(warehouseId, skuId, tag, "20.0000");
                Long firstOrder = addressedConfirmedOrder(tag + "1", warehouseId, skuId);
                sortOneLine(firstOrder, sortedQuantity, warehouseId);
                Long secondOrder = addressedConfirmedOrder(tag + "2", warehouseId, skuId);
                sortOneLine(secondOrder, sortedQuantity, warehouseId);

                Long routeId = newRoute(warehouseId);
                attach(routeId, firstOrder);
                attach(routeId, secondOrder);
                locateAllStops(routeId);
                plan(routeId);
                return new Chain(routeId, warehouseId, skuId, null, firstOrder);
            }

            private Long addressedConfirmedOrder(String tag, Long warehouseId, Long skuId) {
                Long customerId = newCustomer();
                jdbc.update("UPDATE xsy_customer SET address = ?, longitude = 113.94, latitude = 22.54,"
                        + " geom_crs = 'GCJ02' WHERE id = ?", "F1-2B IT 地址" + tag, customerId);
                evictMybatisCache();
                Long orderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
                evictMybatisCache();
                return orderId;
            }

            private Long locatedWarehouse(String tag) {
                Long warehouseId = newWarehouse(tag);
                jdbc.update("UPDATE xsy_warehouse SET longitude = 113.90, latitude = 22.50, geom_crs = 'GCJ02' WHERE id = ?",
                        warehouseId);
                evictMybatisCache();
                return warehouseId;
            }

            /**
             * 改 SKU 市场价，让订单在真实价格解析链路上拿到调用方要的单价。
             *
             * <p>不走订单手工改价：那会把 {@code locked_price_source} 变成 OVERRIDE，
             * 而本类要证明的是「财务读的就是订单行那个保存单价列」，与它来自哪个价格源无关。
             */
            private void withMarketPrice(Long skuId, String marketPrice) {
                jdbc.update("UPDATE xsy_product_sku SET market_price = ? WHERE id = ?",
                        new BigDecimal(marketPrice), skuId);
                evictMybatisCache();
            }

            /** 真实采购入库链路（库存的唯一合法来源），否则分拣与发车无货可扣。 */
            private void stockIn(Long warehouseId, Long skuId, String tag, String quantity) {
                Long supplierId = newPurchasableSupplier(tag, skuId);
                var order = purchaseOrderService.create(
                        orderForm(supplierId, warehouseId, skuId, quantity, "6.2000"), prefix + ":" + tag + ":po");
                submitOrder(order.getId());
                confirmReceipt(createReceipt(order.getId()).getId(), quantity);
                evictMybatisCache();
            }

            private void sortOneLine(Long orderId, String sortedQuantity, Long warehouseId) {
                var create = new SortingTaskCreateForm();
                create.setWarehouseId(warehouseId);
                create.setAssigneeEmployeeId(1L);
                create.setRemark("F1-2B 前置夹具");
                create.setSalesOrderItemIds(new ArrayList<>(List.of(confirmedSalesOrderItemId(orderId))));
                var detail = sortingTaskService.create(create, prefix + ":sort:" + orderId);
                Long taskId = detail.getTask().getId();

                var entry = new SortingEntryItemForm();
                entry.setId(detail.getItems().getFirst().getId());
                entry.setVersion(detail.getItems().getFirst().getVersion());
                entry.setSortedQuantity(new BigDecimal(sortedQuantity));
                entry.setResult("NORMAL");
                var entries = new ArrayList<SortingEntryItemForm>();
                entries.add(entry);
                var entryForm = new SortingEntryForm();
                entryForm.setItems(entries);
                sortingTaskService.enter(taskId, entryForm);
                var action = new SortingActionForm();
                action.setVersion(jdbc.queryForObject("SELECT version FROM xsy_sorting_task WHERE id = ?",
                        Integer.class, taskId));
                sortingTaskService.complete(taskId, action);
                evictMybatisCache();
            }

            private Long newRoute(Long warehouseId) {
                var form = new DeliveryRouteForm();
                form.setRouteName("F1-2B IT 线路");
                form.setDeliveryDate(LocalDate.now());
                form.setWarehouseId(warehouseId);
                return routeService.create(form);
            }

            private void attach(Long routeId, Long orderId) {
                var form = new DeliveryOrdersForm();
                form.setVersion(versionOf(routeId));
                form.setOrderIds(List.of(orderId));
                form.setReason("组单");
                routeService.addOrders(routeId, form);
            }

            private void locateAllStops(Long routeId) {
                jdbc.update("UPDATE xsy_delivery_route_stop SET longitude = 113.95, latitude = 22.55, geom_crs = 'GCJ02'"
                        + " WHERE route_id = ? AND deleted = FALSE", routeId);
                evictMybatisCache();
            }

            private void plan(Long routeId) {
                routeService.plan(routeId, versionForm(routeId));
            }

            private void dispatch(Long routeId) {
                routeService.dispatch(routeId, versionForm(routeId), key("dispatch"));
            }

            private void sign(Long routeId, Long orderId, String result, String reason) {
                var form = new DeliverySignForm();
                form.setVersion(orderVersionOf(routeId, orderId));
                form.setResult(result);
                form.setReason(reason);
                routeService.sign(routeId, orderId, form);
                evictMybatisCache();
            }

            private DeliveryVersionForm versionForm(Long routeId) {
                var form = new DeliveryVersionForm();
                form.setVersion(versionOf(routeId));
                return form;
            }

            private Integer versionOf(Long routeId) {
                return jdbc.queryForObject("SELECT version FROM xsy_delivery_route WHERE id = ?", Integer.class, routeId);
            }

            private Integer orderVersionOf(Long routeId, Long orderId) {
                return jdbc.queryForObject(
                        "SELECT version FROM xsy_delivery_route_order WHERE route_id = ? AND order_id = ? AND deleted = FALSE",
                        Integer.class, routeId, orderId);
            }

            private String key(String tag) {
                return prefix + ":" + tag + ":" + UUID.randomUUID();
            }

            /** 签收行 id（应收时点与签收人的唯一来源）。 */
            private Long assignmentId(Long routeId, Long orderId) {
                return jdbc.queryForObject(
                        "SELECT id FROM xsy_delivery_route_order WHERE route_id = ? AND order_id = ? AND deleted = FALSE",
                        Long.class, routeId, orderId);
            }

            private Long outboundItemIdOf(Long orderId) {
                return jdbc.queryForObject(
                        "SELECT id FROM xsy_inventory_outbound_item WHERE sales_order_id = ? AND deleted = FALSE ORDER BY id LIMIT 1",
                        Long.class, orderId);
            }

            // ------------------------------------------------------------------
            // 断言辅助
            // ------------------------------------------------------------------

            private Map<String, Object> receivableOf(Long orderId) {
                List<Map<String, Object>> rows = jdbc.queryForList(
                        "SELECT * FROM xsy_finance_receivable WHERE source_type = 'SALES_ORDER' AND source_id = ?", orderId);
                assertThat(rows).as("订单 %s 的应收单（恰好一张）", orderId).hasSize(1);
                return rows.getFirst();
            }

            private int receivablesOfOrder(Long orderId) {
                return jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_finance_receivable WHERE source_type = 'SALES_ORDER' AND source_id = ?",
                        Integer.class, orderId);
            }

            /**
             * 本订单的出库行所产生的应收明细数。
             *
             * <p>按来源作用域而不是全表计数：本仓库的 NOT_SUPPORTED 用例会留下已提交的记录，
             * 「全库只有 N 行」这类断言会随执行顺序忽绿忽红。
             */
            private int receivableItemsOfOrder(Long orderId) {
                return jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_finance_receivable_item WHERE source_type = 'INVENTORY_OUTBOUND_ITEM'"
                                + " AND source_id IN (SELECT id FROM xsy_inventory_outbound_item WHERE sales_order_id = ?)",
                        Integer.class, orderId);
            }

            private int receivableLogsOfOrder(Long orderId) {
                return jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_finance_operation_log WHERE business_type = 'RECEIVABLE'"
                                + " AND business_id IN (SELECT id FROM xsy_finance_receivable"
                                + "                       WHERE source_type = 'SALES_ORDER' AND source_id = ?)",
                        Integer.class, orderId);
            }

            private List<Map<String, Object>> receivableItemsOf(Long receivableId) {
                return jdbc.queryForList(
                        "SELECT * FROM xsy_finance_receivable_item WHERE receivable_id = ? ORDER BY id", receivableId);
            }

            private List<Map<String, Object>> receivableLogsOf(Long receivableId) {
                return jdbc.queryForList(
                        "SELECT * FROM xsy_finance_operation_log WHERE business_type = 'RECEIVABLE' AND business_id = ?",
                        receivableId);
            }

            /**
             * 让 PostgreSQL 自己比较两列 —— 「event_at 等于 signed_at」这句话用 Java 比对时区偏移会骗人，
             * 用 {@code toInstant()} 又只是「时刻相同」的近似断言。
             */
            private boolean sameAs(String expression, Object... args) {
                Boolean result = jdbc.queryForObject("SELECT " + expression, Boolean.class, args);
                return Boolean.TRUE.equals(result);
            }

            private static Long longOf(Map<String, Object> row, String column) {
                return ((Number) row.get(column)).longValue();
            }

            private static BigDecimal decimalOf(Map<String, Object> row, String column) {
                return (BigDecimal) row.get(column);
            }

            // ------------------------------------------------------------------
            // A. 正常链路：PLANNED → 发车 → SIGNED → NORMAL 应收
            // ------------------------------------------------------------------

            @Test
            @DisplayName("正常链路：签收生成一张 NORMAL 应收，单头与明细字段全部来自既有记录")
            void signGeneratesNormalReceivableWithHeaderAndItems() {
                Chain chain = sortedAndPlanned("FRA", "10.0000", "3.5000", "10.0000");
                dispatch(chain.routeId());
                sign(chain.routeId(), chain.orderId(), "SIGNED", null);

                Map<String, Object> receivable = receivableOf(chain.orderId());
                Long receivableId = longOf(receivable, "id");
                assertThat(receivable.get("entry_type")).isEqualTo("NORMAL");
                assertThat(receivable.get("source_type")).isEqualTo("SALES_ORDER");
                assertThat(longOf(receivable, "source_id")).isEqualTo(chain.orderId());
                assertThat(longOf(receivable, "order_id")).isEqualTo(chain.orderId());
                assertThat(longOf(receivable, "customer_id")).isEqualTo(chain.customerId());
                assertThat(receivable.get("customer_name_snapshot")).isEqualTo(jdbc.queryForObject(
                        "SELECT customer_name_snapshot FROM xsy_sales_order WHERE id = ?", String.class, chain.orderId()));
                assertThat(receivable.get("original_receivable_id")).isNull();
                assertThat(receivable.get("reason")).isNull();
                assertThat(receivable.get("deleted")).isEqualTo(false);
                assertThat(decimalOf(receivable, "amount")).isEqualByComparingTo("35.0000");
                assertThat(String.valueOf(receivable.get("receivable_no"))).matches("^XSY\\d{18,19}$");

                List<Map<String, Object>> items = receivableItemsOf(receivableId);
                assertThat(items).hasSize(1);
                Map<String, Object> line = items.getFirst();
                assertThat(line.get("source_type")).isEqualTo("INVENTORY_OUTBOUND_ITEM");
                assertThat(longOf(line, "source_id")).isEqualTo(outboundItemIdOf(chain.orderId()));
                assertThat(longOf(line, "order_item_id")).isEqualTo(confirmedSalesOrderItemId(chain.orderId()));
                assertThat(longOf(line, "sku_id")).isEqualTo(chain.skuId());
                assertThat(line.get("unit_snapshot")).isEqualTo("kg");
                assertThat(decimalOf(line, "quantity")).isEqualByComparingTo("10.0000");
                assertThat(decimalOf(line, "unit_price")).isEqualByComparingTo("3.5000");
                assertThat(decimalOf(line, "amount")).isEqualByComparingTo("35.0000");

                List<Map<String, Object>> logs = receivableLogsOf(receivableId);
                assertThat(logs).hasSize(1);
                assertThat(logs.getFirst().get("operation_type")).isEqualTo("GENERATE");
                assertThat(logs.getFirst().get("before_data")).isNull();
                assertThat(String.valueOf(logs.getFirst().get("after_data")))
                        .contains("receivableNo").contains("35.0000").contains("NORMAL");
            }

            // ------------------------------------------------------------------
            // B / C. 时点与签收人是同一个数据库记录
            // ------------------------------------------------------------------

            @Test
            @DisplayName("event_at 逐值等于 delivery_route_order.signed_at；created_by 等于 signed_by")
            void eventAtAndOperatorAreTheSignedRowFactsThemselves() {
                Chain chain = sortedAndPlanned("FRB", "10.0000", "3.5000", "10.0000");
                dispatch(chain.routeId());
                sign(chain.routeId(), chain.orderId(), "SIGNED", null);
                Long assignment = assignmentId(chain.routeId(), chain.orderId());
                Long receivableId = longOf(receivableOf(chain.orderId()), "id");

                assertThat(sameAs("r.event_at = ro.signed_at"
                                + " FROM xsy_finance_receivable r JOIN xsy_delivery_route_order ro ON ro.id = ?"
                                + " WHERE r.id = ?",
                        assignment, receivableId))
                        .as("应收时点必须逐值等于签收时刻，不是「服务层现在时间」")
                        .isTrue();
                assertThat(sameAs("r.created_by = ro.signed_by"
                                + " FROM xsy_finance_receivable r JOIN xsy_delivery_route_order ro ON ro.id = ?"
                                + " WHERE r.id = ?",
                        assignment, receivableId))
                        .as("应收创建人必须是签收人本人")
                        .isTrue();
                assertThat(sameAs("l.operator = ro.signed_by"
                                + " FROM xsy_finance_operation_log l"
                                + " JOIN xsy_delivery_route_order ro ON ro.id = ?"
                                + " WHERE l.business_type = 'RECEIVABLE' AND l.business_id = ?",
                        assignment, receivableId))
                        .as("日志操作人必须与 signed_by 一致")
                        .isTrue();
                // 明细的身份列同样取签收人，不另立第二个操作人记录
                assertThat(sameAs("i.created_by = ro.signed_by"
                                + " FROM xsy_finance_receivable_item i JOIN xsy_delivery_route_order ro ON ro.id = ?"
                                + " WHERE i.receivable_id = ?",
                        assignment, receivableId)).isTrue();
                assertThat(jdbc.queryForObject(
                        "SELECT signed_at FROM xsy_delivery_route_order WHERE id = ?", java.time.OffsetDateTime.class,
                        assignment)).isNotNull();
            }

            // ------------------------------------------------------------------
            // D. 少拣：应收量 = 实际出库量
            // ------------------------------------------------------------------

            @Test
            @DisplayName("少拣：下单 10、实发 7 → 应收数量必须 7；订单量与分拣量都不是应收数量记录")
            void receivableQuantityIsTheOutboundQuantityNotOrderOrSortingQuantity() {
                Chain chain = sortedAndPlanned("FRD", "10.0000", "3.5000", "7.0000");
                dispatch(chain.routeId());
                sign(chain.routeId(), chain.orderId(), "SIGNED", null);

                Long orderItemId = confirmedSalesOrderItemId(chain.orderId());
                // 前置记录：下单量仍是 10；分拣称重已把订单实发量回写成 7（称多少、结多少），
                // 分拣行也是 7。应收量与这三者都无关，只取出库行。
                assertThat(jdbc.queryForObject(
                        "SELECT ordered_quantity FROM xsy_sales_order_item WHERE id = ?", BigDecimal.class, orderItemId))
                        .isEqualByComparingTo("10.0000");
                assertThat(jdbc.queryForObject(
                        "SELECT actual_quantity FROM xsy_sales_order_item WHERE id = ?", BigDecimal.class, orderItemId))
                        .isEqualByComparingTo("7.0000");
                assertThat(jdbc.queryForObject(
                        "SELECT sorted_quantity FROM xsy_sorting_task_item WHERE sales_order_item_id = ? AND deleted = FALSE",
                        BigDecimal.class, orderItemId)).isEqualByComparingTo("7.0000");

                Map<String, Object> receivable = receivableOf(chain.orderId());
                Map<String, Object> line = receivableItemsOf(longOf(receivable, "id")).getFirst();
                assertThat(decimalOf(line, "quantity")).as("应收量取出库行").isEqualByComparingTo("7.0000");
                assertThat(decimalOf(receivable, "amount")).as("7 × 3.5").isEqualByComparingTo("24.5000");
                // 行级来源锚点是出库行主键，不是订单行 id
                assertThat(longOf(line, "source_id")).isEqualTo(outboundItemIdOf(chain.orderId()));
                assertThat(longOf(line, "order_item_id")).isEqualTo(orderItemId);
            }

            // ------------------------------------------------------------------
            // E. 售价唯一来源是订单行保存单价
            // ------------------------------------------------------------------

            @Test
            @DisplayName("单价：应收 unit_price 逐值等于 locked_unit_price；库存出库成本 6.2 一分都不用")
            void unitPriceComesFromLockedOrderPriceNotInventoryCost() {
                Chain chain = sortedAndPlanned("FRE", "10.0000", "8.1234", "10.0000");
                dispatch(chain.routeId());
                sign(chain.routeId(), chain.orderId(), "SIGNED", null);
                Long orderItemId = confirmedSalesOrderItemId(chain.orderId());

                Map<String, Object> receivable = receivableOf(chain.orderId());
                Map<String, Object> line = receivableItemsOf(longOf(receivable, "id")).getFirst();
                assertThat(sameAs("i.unit_price = oi.locked_unit_price"
                                + " FROM xsy_finance_receivable_item i"
                                + " JOIN xsy_finance_receivable r ON r.id = i.receivable_id"
                                + " JOIN xsy_sales_order_item oi ON oi.id = i.order_item_id"
                                + " WHERE r.source_id = ? AND i.order_item_id = ?",
                        chain.orderId(), orderItemId))
                        .as("单价必须逐值等于订单行确认时保存的售价")
                        .isTrue();
                assertThat(decimalOf(line, "unit_price")).isEqualByComparingTo("8.1234");
                assertThat(decimalOf(line, "amount")).isEqualByComparingTo("81.2340");
                assertThat(decimalOf(receivable, "amount")).isEqualByComparingTo("81.2340");

                // 反向证明：采购入库成本是 6.2000，若读成本金额只会是 62.0000
                assertThat(jdbc.queryForObject(
                        "SELECT unit_cost FROM xsy_inventory_movement WHERE sku_id = ? AND movement_type = 'PURCHASE_IN'"
                                + " AND deleted = FALSE", BigDecimal.class, chain.skuId()))
                        .isEqualByComparingTo("6.2000");
                assertThat(decimalOf(receivable, "amount")).isNotEqualByComparingTo("62.0000");
                // 也不得读订单结算列：本例里 settlement_line_amount = 10 × 8.1234 = 81.2340 会巧合同值，
                // 因此改判「应收金额来自逐行舍入后的出库行之和」这一结构：明细只有一行且数量是出库量
                assertThat(decimalOf(line, "quantity")).isEqualByComparingTo("10.0000");
            }

            // ------------------------------------------------------------------
            // F. HALF_UP 到 4 位
            // ------------------------------------------------------------------

            @Test
            @DisplayName("金额舍入：3.3333 × 2.2222 = 7.40725926 → 7.4073（HALF_UP，不是截断 7.4072）")
            void itemAmountRoundsHalfUpAtScaleFour() {
                Chain chain = sortedAndPlanned("FRF", "10.0000", "2.2222", "3.3333");
                dispatch(chain.routeId());
                sign(chain.routeId(), chain.orderId(), "SIGNED", null);

                Map<String, Object> receivable = receivableOf(chain.orderId());
                Map<String, Object> line = receivableItemsOf(longOf(receivable, "id")).getFirst();
                assertThat(decimalOf(line, "quantity")).isEqualByComparingTo("3.3333");
                assertThat(decimalOf(line, "amount")).isEqualByComparingTo("7.4073");
                assertThat(decimalOf(receivable, "amount")).as("单头 = 已舍入行之和").isEqualByComparingTo("7.4073");
            }

            // ------------------------------------------------------------------
            // G. 一条订单行多条出库行：逐条成行，不合并
            // ------------------------------------------------------------------

            @Test
            @DisplayName("同一订单行两条出库行 → 两条应收明细，各自锚定自己的出库行，不合并")
            void multipleOutboundLinesOfOneOrderLineStaySeparateItems() {
                Chain chain = sortedAndPlanned("FRG", "10.0000", "3.5000", "6.0000");
                dispatch(chain.routeId());

                // 受控夹具：V63 不给 sales_order_item_id 建唯一索引（一条订单行可以对应多条出库行），
                // 因此「同订单行的第二条出库行」是 schema 合法形状，但现有发车入口一次只生成一条。
                Long first = outboundItemIdOf(chain.orderId());
                Long outboundId = jdbc.queryForObject(
                        "SELECT outbound_id FROM xsy_inventory_outbound_item WHERE id = ?", Long.class, first);
                jdbc.update("INSERT INTO xsy_inventory_outbound_item (outbound_id, sku_id, quantity, sales_order_id,"
                                + " sales_order_item_id, version, deleted, created_at, updated_at)"
                                + " SELECT ?, sku_id, 2.0000, sales_order_id, sales_order_item_id, 0, FALSE,"
                                + "        now(), now() FROM xsy_inventory_outbound_item WHERE id = ?",
                        outboundId, first);
                evictMybatisCache();

                sign(chain.routeId(), chain.orderId(), "SIGNED", null);

                Map<String, Object> receivable = receivableOf(chain.orderId());
                List<Map<String, Object>> items = receivableItemsOf(longOf(receivable, "id"));
                assertThat(items).as("两条出库行 = 两条应收明细").hasSize(2);
                assertThat(items).allSatisfy(line ->
                        assertThat(longOf(line, "order_item_id")).isEqualTo(confirmedSalesOrderItemId(chain.orderId())));
                assertThat(items).extracting(line -> longOf(line, "source_id")).doesNotHaveDuplicates();
                assertThat(decimalOf(receivable, "amount")).as("(6 + 2) × 3.5").isEqualByComparingTo("28.0000");
            }

            // ------------------------------------------------------------------
            // H. EXCEPTION 不形成应收
            // ------------------------------------------------------------------

            @Test
            @DisplayName("EXCEPTION 签收：无应收、无明细、无日志；SALES_OUT 记录一字未改")
            void exceptionSignGeneratesNoReceivable() {
                Chain chain = sortedAndPlanned("FRH", "10.0000", "3.5000", "10.0000");
                dispatch(chain.routeId());
                // 全部流水断言一律按本用例新建的 SKU 作用域：本仓库还有 NOT_SUPPORTED 的用例会留下
                // 已提交的真实流水，「全库只有两条」这种写法会随执行顺序忽绿忽红。
                BigDecimal salesOutBefore = jdbc.queryForObject(
                        "SELECT quantity FROM xsy_inventory_movement WHERE movement_type = 'SALES_OUT'"
                                + " AND sku_id = ? AND deleted = FALSE", BigDecimal.class, chain.skuId());

                sign(chain.routeId(), chain.orderId(), "EXCEPTION", "客户拒收");

                assertThat(fulfillmentOf(assignmentId(chain.routeId(), chain.orderId())))
                        .isEqualTo("EXCEPTION");
                assertThat(receivablesOfOrder(chain.orderId())).as("异常签收不形成应收（第二批 Q6）").isZero();
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_finance_receivable_item WHERE source_type = 'INVENTORY_OUTBOUND_ITEM'"
                                + " AND source_id IN (SELECT id FROM xsy_inventory_outbound_item WHERE sales_order_id = ?)",
                        Integer.class, chain.orderId())).isZero();
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_finance_operation_log l WHERE l.business_type = 'RECEIVABLE'"
                                + " AND l.business_id IN (SELECT id FROM xsy_finance_receivable WHERE source_id = ?)",
                        Integer.class, chain.orderId())).isZero();
                // 财务不反冲库存：SALES_OUT 还在，数量不变，也没有新增反向流水
                assertThat(jdbc.queryForObject(
                        "SELECT quantity FROM xsy_inventory_movement WHERE movement_type = 'SALES_OUT'"
                                + " AND sku_id = ? AND deleted = FALSE", BigDecimal.class, chain.skuId()))
                        .isEqualByComparingTo(salesOutBefore);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_inventory_movement WHERE sku_id = ? AND deleted = FALSE",
                        Integer.class, chain.skuId()))
                        .as("本 SKU 只有采购入库与销售出库两条流水").isEqualTo(2);
            }

            // ------------------------------------------------------------------
            // I. 签收成功但无有效出库行 → 成功跳过
            // ------------------------------------------------------------------

            @Test
            @DisplayName("SIGNED 且无有效出库行：签收成功、财务跳过，不造空单头也不造 0 元记录")
            void signedWithoutOutboundLinesSkipsFinanceSuccessfully() {
                Chain chain = sortedAndPlanned("FRI", "10.0000", "3.5000", "10.0000");
                dispatch(chain.routeId());

                // 受控夹具：真实入口走不到「已发车出库、但没有任何有效出库行」这一形状
                // （零实发的线路在发车前置就被拒），而这条跳过分支必须留下证据，
                // 因此按 schema 允许的方式把出库行软删，不修改任何产品规则。
                // 必须断言恰好软删到一行：软删 0 行时下面所有「财务跳过」的断言都会空转通过，
                // 那等于这条用例根本没被执行。
                assertThat(jdbc.update("UPDATE xsy_inventory_outbound_item SET deleted = TRUE WHERE sales_order_id = ?",
                        chain.orderId())).as("夹具必须真的造出「零有效出库行」").isEqualTo(1);
                evictMybatisCache();

                sign(chain.routeId(), chain.orderId(), "SIGNED", null);

                assertThat(fulfillmentOf(assignmentId(chain.routeId(), chain.orderId())))
                        .as("跳过财务记录不是签收失败的副作用").isEqualTo("SIGNED");
                assertThat(receivablesOfOrder(chain.orderId())).isZero();
                assertThat(receivableItemsOfOrder(chain.orderId()))
                        .as("本订单的出库行没有产生任何应收明细").isZero();
                assertThat(receivableLogsOfOrder(chain.orderId()))
                        .as("跳过不留下任何生成日志").isZero();

                // 重放同样什么都不产生
                financeReceivableService.generateOnSign(assignmentId(chain.routeId(), chain.orderId()));
                assertThat(receivablesOfOrder(chain.orderId())).isZero();
            }

            // ------------------------------------------------------------------
            // J. 合法 0 元订单 → 跳过
            // ------------------------------------------------------------------

            @Test
            @DisplayName("整单单价为 0：签收成功、财务跳过，不生成 0 元应收与空单头")
            void zeroAmountOrderSkipsFinanceFacts() {
                Chain chain = sortedAndPlanned("FRJ", "10.0000", "0.0000", "10.0000");
                dispatch(chain.routeId());

                sign(chain.routeId(), chain.orderId(), "SIGNED", null);

                assertThat(fulfillmentOf(assignmentId(chain.routeId(), chain.orderId()))).isEqualTo("SIGNED");
                assertThat(receivablesOfOrder(chain.orderId())).as("0 元记录不生成（Q8 同纪律）").isZero();
                assertThat(receivableItemsOfOrder(chain.orderId())).isZero();
            }

            // ------------------------------------------------------------------
            // K. 重复生成只有一张应收
            // ------------------------------------------------------------------

            @Test
            @DisplayName("重复生成：第二次命中来源唯一索引 → 仍是一张应收、一份日志，且不报错")
            void duplicateGenerationKeepsSingleReceivableAndSingleLog() {
                Chain chain = sortedAndPlanned("FRK", "10.0000", "3.5000", "10.0000");
                dispatch(chain.routeId());
                sign(chain.routeId(), chain.orderId(), "SIGNED", null);
                Long assignment = assignmentId(chain.routeId(), chain.orderId());
                Long receivableId = longOf(receivableOf(chain.orderId()), "id");

                financeReceivableService.generateOnSign(assignment);
                financeReceivableService.generateOnSign(assignment);

                Map<String, Object> receivable = receivableOf(chain.orderId());
                assertThat(longOf(receivable, "id")).as("重放不换单也不换号").isEqualTo(receivableId);
                assertThat(receivableItemsOf(receivableId)).hasSize(1);
                assertThat(receivableLogsOf(receivableId)).as("已生成不是新记录，不重复留痕").hasSize(1);
            }

            // ------------------------------------------------------------------
            // L. 两条独立订单各自一张应收（补单按独立 source 处理）
            // ------------------------------------------------------------------

            @Test
            @DisplayName("同线路两单：各自生成一张应收，单号不重复，不并成一单")
            void eachSignedOrderGetsItsOwnReceivable() {
                Chain chain = sortedAndPlannedTwoOrders("FRL", "10.0000");
                dispatch(chain.routeId());
                Long firstOrder = chain.orderId();
                Long secondOrder = jdbc.queryForObject(
                        "SELECT order_id FROM xsy_delivery_route_order WHERE route_id = ? AND deleted = FALSE"
                                + " AND order_id <> ? ORDER BY id", Long.class, chain.routeId(), firstOrder);

                sign(chain.routeId(), firstOrder, "SIGNED", null);
                sign(chain.routeId(), secondOrder, "SIGNED", null);

                Map<String, Object> first = receivableOf(firstOrder);
                Map<String, Object> second = receivableOf(secondOrder);
                assertThat(longOf(first, "id")).isNotEqualTo(longOf(second, "id"));
                assertThat(first.get("receivable_no")).isNotEqualTo(second.get("receivable_no"));
                assertThat(receivablesOfOrder(firstOrder)).isEqualTo(1);
                assertThat(receivablesOfOrder(secondOrder)).isEqualTo(1);
            }

            // ------------------------------------------------------------------
            // M. 未签收的分配行不能生成应收
            // ------------------------------------------------------------------

            @Test
            @DisplayName("在途未签收就调用生成器 → 抛错且不产生应收（EXCEPTION 与 SIGNED 共用一条 UPDATE 的防线）")
            void inTransitAssignmentCannotGenerateReceivable() {
                Chain chain = sortedAndPlanned("FRM", "10.0000", "3.5000", "10.0000");
                dispatch(chain.routeId());
                Long assignment = assignmentId(chain.routeId(), chain.orderId());
                assertThat(fulfillmentOf(assignment)).isEqualTo("IN_TRANSIT");

                assertThatThrownBy(() -> financeReceivableService.generateOnSign(assignment))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("不能生成应收");

                assertThat(receivablesOfOrder(chain.orderId())).isZero();
            }

            private String fulfillmentOf(Long assignment) {
                return jdbc.queryForObject("SELECT fulfillment_status FROM xsy_delivery_route_order WHERE id = ?",
                        String.class, assignment);
            }
        }



        @Nested
        @DisplayName("Finance R1 迁移约定与阶段边界（PG IT）")
        class ScmFinanceSchemaPgIT {
            /**
             * V65 建的 8 张财务表 + V194 的抹零表 + V207 的过账与过账作废记录。
             */
            private static final List<String> FINANCE_TABLES = List.of(
                    "xsy_finance_receivable", "xsy_finance_receivable_item",
                    "xsy_finance_payable", "xsy_finance_payable_item",
                    "xsy_finance_receipt", "xsy_finance_payment",
                    "xsy_finance_write_off", "xsy_finance_operation_log",
                    "xsy_finance_waiver", "xsy_finance_posting", "xsy_finance_posting_void",
                    "xsy_finance_customer_expense", "xsy_finance_customer_expense_void", "xsy_finance_purchase_cost_entry");

            /**
             * 带 {@code deleted} 列的记录表；{@code xsy_finance_operation_log} 不在其中 ——
             * 它连 {@code deleted} 列都没有，append-only 是结构性的。
             */
            private static final List<String> APPEND_ONLY_TABLES = List.of(
                    "xsy_finance_receivable", "xsy_finance_receivable_item",
                    "xsy_finance_payable", "xsy_finance_payable_item",
                    "xsy_finance_receipt", "xsy_finance_payment", "xsy_finance_write_off",
                    "xsy_finance_waiver", "xsy_finance_posting", "xsy_finance_posting_void");

            private static final List<String> FINANCE_SEQUENCES = List.of(
                    "finance_receivable_no_seq", "finance_payable_no_seq", "finance_receipt_no_seq",
                    "finance_payment_no_seq", "finance_write_off_no_seq", "finance_waiver_no_seq",
                    "finance_posting_no_seq", "finance_customer_expense_no_seq");

            private String no(String prefix) {
                return prefix + UUID.randomUUID().toString().substring(0, 16).toUpperCase(java.util.Locale.ROOT);
            }

            /**
             * 约束名基于 V118 之前的表名：{@code ALTER TABLE ... RENAME} 不改约束名，
             * 因此拼 {@code ck_…} 时要去掉 {@code xsy_} 前缀。
             */
            private static String constraintBase(String table) {
                return table.startsWith("xsy_") ? table.substring(4) : table;
            }

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

            private String indexDef(String index) {
                return jdbc.queryForObject(
                        "SELECT indexdef FROM pg_indexes WHERE schemaname = current_schema() AND indexname = ?",
                        String.class, index);
            }

            /**
             * 从约束定义里抽出全部单引号字面量，用于比对 Java enum 与 DB CHECK 白名单是否同一份真值。
             *
             * <p>模式允许空串（{@code [^']*}）：配对类约束里有 {@code btrim(COALESCE(reason, '')) <> ''}，
             * 若要求至少一个字符，正则会跨过 {@code ''} 去匹配到 {@code ') <> '} 这种伪字面量。
             * 空串随后被过滤掉，因此只留下真正的枚举取值。
             */
            private Set<String> literalsOf(String constraintDef) {
                Matcher matcher = Pattern.compile("'([^']*)'").matcher(constraintDef);
                return matcher.results()
                        .map(r -> r.group(1))
                        .filter(value -> !value.isBlank())
                        .collect(Collectors.toSet());
            }

            /** 从字面量集合里剔除分组用的对手方名，剩下的就是该支允许的资金渠道。 */
            private Set<String> without(Set<String> literals, String counterpartyType) {
                return literals.stream().filter(v -> !v.equals(counterpartyType)).collect(Collectors.toSet());
            }

            // ------------------------------------------------------------------
            // 表 / 序列 / 列
            // ------------------------------------------------------------------

            @Test
            @DisplayName("V65 建 8 表 + 5 序列，且不含任何外键")
            void tablesSequencesAndNoForeignKeys() {
                List<String> tables = jdbc.queryForList(
                        "SELECT table_name FROM information_schema.tables "
                                + "WHERE table_schema = current_schema() AND table_type = 'BASE TABLE'", String.class);
                assertThat(tables).containsAll(FINANCE_TABLES);

                List<String> sequences = jdbc.queryForList(
                        "SELECT sequencename FROM pg_sequences WHERE schemaname = current_schema()", String.class);
                assertThat(sequences).containsAll(FINANCE_SEQUENCES);

                // 项目级规则：不建数据库外键，关系完整性由服务层 + CHECK + 唯一索引承担。
                Integer foreignKeys = jdbc.queryForObject(
                        "SELECT count(*) FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid "
                                + "WHERE c.contype = 'f' AND t.relname LIKE 'finance_%'", Integer.class);
                assertThat(foreignKeys).isZero();
            }

            @Test
            @DisplayName("财务表不含状态列 / 余额列 / 账期列（Q15 Q17 Q20，全局不变量 6）")
            void noStateMachineOrDerivedColumns() {
                // 写入数据库的派生态一定会漂移；审批相关列不属于财务域。
                // due_date 不在禁用之列：ADM-04 账期把它作为保存记录写入数据库（主档后来改账期不得重算旧应收），
                // 它是账期规则的求值当时的结果，不是可被回写的派生态。
                List<String> forbidden = new ArrayList<>(List.of(
                        "status", "settled_amount", "open_amount", "written_off_amount", "net_amount",
                        "approver", "approval_state", "currency", "tax_rate", "tax_amount"));
                // 过账特殊单据是唯一例外：现场要求按纸质单据原样记下税点（D-1028-16）。
                // 它只存税率这一个输入，不存 tax_amount，也不参与任何金额计算，因此不违背「财务域不做税额」的本意。
                // status 仍然被禁：是否作废由 xsy_finance_posting_void 推导，与本类其余表同一纪律。
                for (String table : FINANCE_TABLES) {
                    List<String> tableForbidden = new ArrayList<>(forbidden);
                    if ("xsy_finance_posting".equals(table)) {
                        tableForbidden.remove("tax_rate");
                    }
                    if ("xsy_finance_customer_expense".equals(table)) {
                        // 记录负责人确认的实际税费金额，不保存或推导计税费率。
                        tableForbidden.remove("tax_amount");
                    }
                    assertThat(columnsOf(table))
                            .as("表 %s 不得出现派生态或越界列", table)
                            .doesNotContainAnyElementsOf(tableForbidden);
                }
                // 方向编码在类型里，因此不存在独立的 direction 列（照 inventory_movement 的纪律）。
                for (String table : FINANCE_TABLES) {
                    assertThat(columnsOf(table)).doesNotContain("direction");
                }
            }

            // ------------------------------------------------------------------
            // CHECK：金额 / 数量 / 精度
            // ------------------------------------------------------------------

            // ------------------------------------------------------------------
            // CHECK：NORMAL / RED 与 NORMAL / REVERSE 的字段配对
            // ------------------------------------------------------------------

            // ------------------------------------------------------------------
            // 唯一索引：来源防重、反向防重，以及两处不唯一的索引
            // ------------------------------------------------------------------

            @Test
            @DisplayName("来源唯一索引存在且谓词逐字正确（Q26：来源唯一索引是最终数据库防线）")
            void sourceUniqueIndexPredicates() {
                assertThat(indexDef("uk_finance_receivable_source_active"))
                        .contains("CREATE UNIQUE").contains("source_type, source_id").contains("WHERE (deleted = false)");
                assertThat(indexDef("uk_finance_receivable_item_source_active"))
                        .contains("CREATE UNIQUE").contains("source_type, source_id").contains("WHERE (deleted = false)");
                // 应付 / 应付明细 / 付款的谓词必须带 source_id IS NOT NULL：手工红字、MANUAL 明细与
                // 无来源预付的 source_id 为 NULL，而 NULL 不等于任何值 —— 少了这个谓词，
                // PostgreSQL 会放行任意多条手工红字，索引形同不存在。
                for (String index : List.of("uk_finance_payable_source_active",
                        "uk_finance_payable_item_source_active", "uk_finance_payment_source_active")) {
                    assertThat(indexDef(index))
                            .as("%s 的谓词", index)
                            .contains("CREATE UNIQUE")
                            .contains("WHERE ((deleted = false) AND (source_id IS NOT NULL))");
                }
            }

            @Test
            @DisplayName("来源唯一索引真的挡住重复财务记录")
            void sourceUniqueIndexRejectsDuplicateFact() {
                long first = insertReceivable("AR", "SALES_ORDER", "NORMAL", null, null, new BigDecimal("100.0000"));
                Long sourceId = jdbc.queryForObject("SELECT source_id FROM xsy_finance_receivable WHERE id = ?",
                        Long.class, first);
                // 同一张订单第二次生成应收 → 拒绝。生成器据此走 insertOnConflictDoNothing，
                // 命中冲突即「已生成」并返回成功（可重放的派生，不是用户命令）。
                expectSqlFailure("INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id, order_id, "
                                + "customer_id, customer_name_snapshot, settlement_customer_id, settlement_customer_name_snapshot, entry_type, amount, event_at) "
                                + "VALUES (?, 'SALES_ORDER', ?, 900001, 900001, '客户', 900001, '客户', 'NORMAL', 100, now())",
                        no("AR"), sourceId);
            }

            @Test
            @DisplayName("三条反向唯一索引：一条 NORMAL 最多被反向一次（Q18 / D-3）")
            void singleReverseUniqueIndexes() {
                for (String index : List.of("uk_finance_write_off_single_reverse",
                        "uk_finance_receipt_single_reverse", "uk_finance_payment_single_reverse")) {
                    // 断言谓词的语义而不是逐字文本：PostgreSQL 会把 varchar 比较渲染成
                    // ((entry_type)::text = 'REVERSE'::text)，逐字比对等于把断言绑在 PG 版本的
                    // 反解析格式上。三个成分齐备才是「只约束反向行」这件事的实质。
                    assertThat(indexDef(index))
                            .as("%s 的定义", index)
                            .contains("CREATE UNIQUE")
                            .contains("reverse_of_id")
                            .contains("deleted = false")
                            .contains("'REVERSE'");
                }

                long receipt = insertReceipt(no("RC"), "NORMAL", null, null, null);
                insertReceipt(no("RC"), "REVERSE", receipt, "登错金额", null);
                // 第二条反向 → 撞库级唯一索引。重复撤销必须在这里失败，而不是靠服务层先查后判。
                expectSqlFailure("INSERT INTO xsy_finance_receipt (receipt_no, customer_id, customer_name_snapshot, settlement_customer_id, settlement_customer_name_snapshot, amount, "
                                + "method, received_at, entry_type, reverse_of_id, reason) "
                                + "VALUES (?, 900001, '客户', 900001, '客户', 100, 'CASH', now(), 'REVERSE', ?, '再冲一次')",
                        no("RC"), receipt);
            }

            @Test
            @DisplayName("external_reference 刻意**不**唯一，且不作防重复请求编号（设计稿 §3.2 修正）")
            void externalReferenceIsNotUnique() {
                // 银行流水号跨客户重复是真实存在的；把它当唯一键会让第二笔合法收款登不进去，
                // 因此这两条索引不能加 UNIQUE。
                for (String index : List.of("idx_finance_receipt_external_ref", "idx_finance_payment_external_ref")) {
                    assertThat(indexDef(index)).doesNotContain("CREATE UNIQUE");
                }
                String shared = "BANK-" + UUID.randomUUID();
                insertReceipt(no("RC"), "NORMAL", null, null, shared);
                // 同一凭据号的第二笔收款必须成功。
                insertReceipt(no("RC"), "NORMAL", null, null, shared);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_finance_receipt WHERE external_reference = ?", Integer.class, shared))
                        .isEqualTo(2);
            }

            @Test
            @DisplayName("手工红字应付不受来源唯一索引约束（source_id 为 NULL 落在谓词之外）")
            void manualRedPayableNotBlockedBySourceUniqueIndex() {
                // 两张手工红字就是两个记录；超额由 41137 拦，请求级防重复请求编号防重复提交。
                insertPayable("AP", "MANUAL", null, "RED", "录错单价");
                insertPayable("AP", "MANUAL", null, "RED", "录错数量");
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_finance_payable WHERE source_type = 'MANUAL' AND source_id IS NULL",
                        Integer.class)).isGreaterThanOrEqualTo(2);
            }

            // ------------------------------------------------------------------
            // Java enum ↔ DB CHECK 白名单同源
            // ------------------------------------------------------------------

            /**
             * 断言枚举取值与约束白名单双向相等。
             */
            private void assertWhitelist(String table, String constraint, Class<? extends Enum<?>> type) {
                Set<String> allowed = literalsOf(constraintDef(table, constraint));
                Set<String> declared = Arrays.stream(type.getEnumConstants())
                        .map(Enum::name)
                        .collect(Collectors.toSet());
                assertThat(allowed)
                        .as("%s 与 %s.%s 必须是同一份白名单", type.getSimpleName(), table, constraint)
                        .containsExactlyInAnyOrderElementsOf(declared);
            }

            // ------------------------------------------------------------------
            // 发布范围：财务菜单/权限只覆盖「已经真实存在的能力」
            // ------------------------------------------------------------------

            /**
             * 财务菜单与权限只发布已经真实存在的能力：页面组件、登记与查询端点先落地，菜单和权限串才进库。
             *
             * <p><b>为什么逐值钉住已发布集合</b>：页面菜单一旦指向不存在的 {@code .vue}，
             * {@code src/router/index.ts} 的 {@code route.component = modules[relativePath]} 在文件缺失时
             * 得到 {@code undefined}，菜单点开是空白页，而构建 / 类型检查 / 后端测试全绿；
             * {@code visible_flag = false} 掩盖不了它：那只影响 {@code meta.hideInMenu}。
             * 反过来，提前种一条没有端点使用的权限串是同一类错误的镜像：menu_id 一旦被真实库应用就不可回收。
             * 所以下面逐值钉的是「已发布集合 == 已有真实端点的能力集合」。
             */
            @Test
            @DisplayName("阶段边界：财务页面组件与授权只发布已实现的能力")
            void financePublishesOnlyImplementedCapabilities() {
                assertThat(jdbc.queryForList(
                        "SELECT menu_id FROM t_menu WHERE menu_id BETWEEN 1500 AND 1599 ORDER BY menu_id", Long.class))
                        .as("财务段只允许目录、六个实际页面与二十五个真实能力权限")
                        .containsExactly(1500L, 1501L, 1502L, 1503L, 1504L, 1505L, 1506L, 1511L, 1512L, 1513L, 1514L,
                                1515L, 1521L, 1522L, 1523L, 1524L, 1525L, 1526L, 1527L, 1528L, 1529L, 1531L, 1532L,
                                1533L, 1534L, 1535L, 1536L, 1537L, 1538L, 1539L, 1540L, 1541L);

                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM t_menu WHERE menu_id BETWEEN 1500 AND 1599 AND menu_type = 2",
                        Integer.class))
                        .as("菜单页面只在对应 .vue 存在后发布")
                        .isEqualTo(6);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM t_menu WHERE menu_id BETWEEN 1500 AND 1599 AND component IS NOT NULL",
                        Integer.class))
                        .as("每个页面都必须声明真实组件路径")
                        .isEqualTo(6);
                assertThat(jdbc.queryForList(
                        "SELECT component FROM t_menu WHERE menu_id BETWEEN 1501 AND 1506 ORDER BY menu_id", String.class))
                        .containsExactly("/business/scm/finance/finance-receivable-list.vue",
                                "/business/scm/finance/finance-payable-list.vue",
                                "/business/scm/finance/finance-receipt-list.vue",
                                "/business/scm/finance/finance-payment-list.vue",
                                "/business/scm/finance/finance-write-off-list.vue",
                                "/business/scm/finance/finance-posting-list.vue");
                assertThat(jdbc.queryForList(
                        "SELECT icon FROM t_menu WHERE menu_id BETWEEN 1500 AND 1506 ORDER BY menu_id", String.class))
                        .containsExactly("DollarOutlined", "AccountBookOutlined", "FileTextOutlined", "ImportOutlined",
                                "ExportOutlined", "RetweetOutlined", "AuditOutlined");
                assertThat(jdbc.queryForObject(
                        "SELECT visible_flag FROM t_menu WHERE menu_id = 1500", Boolean.class))
                        .as("财务页发布时同步显示目录入口")
                        .isTrue();
                // 「隐藏式」靠授权范围实现：过账页只有超管与正式财务岗位能看见，不靠 visible_flag 把入口从已授权的人眼前藏掉。
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM t_role_menu WHERE menu_id = 1506", Integer.class))
                        .as("过账页面恰好两条授权行，多一条就是多授了一个角色")
                        .isEqualTo(2);

                // 权限串按内容再查一遍：换号段种同样会造成「已授权但无任何端点使用它」。
                assertThat(jdbc.queryForList(
                        "SELECT DISTINCT api_perms FROM t_menu WHERE api_perms LIKE 'scm:finance:%' ORDER BY api_perms",
                        String.class))
                        .as("只发布已实现的登记、查询、核销、红字、抹零、过账、客户成本费用、反向与导出权限")
                        .containsExactly("scm:finance:expense:add", "scm:finance:expense:query", "scm:finance:expense:void",
                                "scm:finance:export", "scm:finance:payable:query", "scm:finance:payable:red",
                                "scm:finance:payment:add", "scm:finance:payment:query", "scm:finance:payment:reverse",
                                "scm:finance:posting:add", "scm:finance:posting:query", "scm:finance:posting:update",
                                "scm:finance:posting:void",
                                "scm:finance:receipt:add", "scm:finance:receipt:query", "scm:finance:receipt:reverse",
                                "scm:finance:receivable:query", "scm:finance:waiver:add", "scm:finance:waiver:reverse",
                                "scm:finance:write-off:add",
                                "scm:finance:write-off:query", "scm:finance:write-off:reverse");
                // api_perms == web_perms：前端按钮与服务端鉴权读的是同一个串。
                // 作用域限制在财务段：底座原生菜单行本就允许两者不对称，全库断言会误伤。
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM t_menu "
                                + "WHERE menu_id BETWEEN 1500 AND 1599 AND api_perms IS DISTINCT FROM web_perms",
                        Integer.class))
                        .isZero();

                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM t_role_menu rm JOIN t_menu m ON m.menu_id = rm.menu_id "
                                + "WHERE m.menu_id BETWEEN 1500 AND 1599", Integer.class))
                        .as("财务段三十二行菜单（目录、六页面、二十五权限）各授超管兜底与 SCM_FINANCE，共 32 × 2 行")
                        .isEqualTo(64);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM t_role_menu rm JOIN t_menu m ON m.menu_id = rm.menu_id "
                                + "JOIN t_role r ON r.role_id = rm.role_id "
                                + "WHERE m.menu_id BETWEEN 1501 AND 1506 AND r.role_code = 'SCM_FINANCE'", Integer.class))
                        .as("SCM_FINANCE 获得六个页面菜单")
                        .isEqualTo(6);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM t_role_menu rm JOIN t_menu m ON m.menu_id = rm.menu_id "
                                + "JOIN t_role r ON r.role_id = rm.role_id "
                                + "WHERE m.menu_id BETWEEN 1501 AND 1506 AND r.role_code = 'SCM_DRIVER'", Integer.class))
                        .as("司机角色没有财务页面")
                        .isZero();
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM t_role_menu rm JOIN t_menu m ON m.menu_id = rm.menu_id "
                                + "WHERE m.menu_id = 1521", Integer.class))
                        .as("能力点 1521 恰好两条授权行，多一条就是多授了一个角色")
                        .isEqualTo(2);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM t_role_menu rm JOIN t_menu m ON m.menu_id = rm.menu_id "
                                + "WHERE m.menu_id = 1522", Integer.class))
                        .as("能力点 1522 同样恰好两条授权行")
                        .isEqualTo(2);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM t_role_menu rm JOIN t_menu m ON m.menu_id = rm.menu_id "
                                + "WHERE m.menu_id = 1526", Integer.class))
                        .as("能力点 1526 恰好两条授权行")
                        .isEqualTo(2);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM t_role_menu rm JOIN t_menu m ON m.menu_id = rm.menu_id "
                                + "WHERE m.menu_id = 1527", Integer.class))
                        .as("能力点 1527 恰好两条授权行")
                        .isEqualTo(2);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM t_role_menu rm JOIN t_menu m ON m.menu_id = rm.menu_id "
                                + "WHERE m.menu_id IN (1515, 1523, 1524, 1525)", Integer.class))
                        .as("V69 四个能力点各授给超管与 SCM_FINANCE，共 8 行")
                        .isEqualTo(8);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM t_role_menu rm JOIN t_menu m ON m.menu_id = rm.menu_id "
                                + "WHERE m.menu_id IN (1511, 1512, 1513, 1514, 1531)", Integer.class))
                        .as("V70 五个只读 / 导出能力点各授给超管与 SCM_FINANCE，共 10 行")
                        .isEqualTo(10);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM t_role_menu rm JOIN t_menu m ON m.menu_id = rm.menu_id "
                                + "JOIN t_role r ON r.role_id = rm.role_id "
                                + "WHERE m.menu_id = 1521 AND r.role_code = 'SCM_FINANCE'", Integer.class))
                        .as("SCM_FINANCE 按 role_code 授权（V56 规则），不硬编码 role_id")
                        .isEqualTo(1);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM t_role_menu rm JOIN t_menu m ON m.menu_id = rm.menu_id "
                                + "JOIN t_role r ON r.role_id = rm.role_id "
                                + "WHERE m.menu_id = 1522 AND r.role_code = 'SCM_FINANCE'", Integer.class))
                        .isEqualTo(1);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM t_role_menu rm JOIN t_menu m ON m.menu_id = rm.menu_id "
                                + "JOIN t_role r ON r.role_id = rm.role_id "
                                + "WHERE m.menu_id IN (1515, 1523, 1524, 1525, 1526, 1527)"
                                + " AND r.role_code = 'SCM_FINANCE'", Integer.class))
                        .as("F1-3C 与 F1-4 权限按 role_code 授给 SCM_FINANCE")
                        .isEqualTo(6);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM t_role_menu rm JOIN t_menu m ON m.menu_id = rm.menu_id "
                                + "JOIN t_role r ON r.role_id = rm.role_id "
                                + "WHERE m.menu_id IN (1511, 1512, 1513, 1514, 1531)"
                                + " AND r.role_code = 'SCM_FINANCE'", Integer.class))
                        .as("F1-5 查询 / 导出权限按 role_code 授给 SCM_FINANCE")
                        .isEqualTo(5);
            }

            /**
             * 财务域不新增任何范围放宽点：V65 是纯 DDL，
             * 因此全库的 {@code *:scope:all:query} 仍恰好是 V55 种的五个维度。
             * 财务的全范围继续只能来自既有显式授权。
             */
            @Test
            @DisplayName("D-5：本轮没有新增任何 *:scope:all:query 范围放宽权限点")
            void noNewScopeWideningPermission() {
                assertThat(jdbc.queryForList(
                        "SELECT DISTINCT api_perms FROM t_menu WHERE api_perms LIKE '%:scope:all:query' ORDER BY api_perms",
                        String.class))
                        .containsExactly("scm:customer:scope:all:query", "scm:delivery:scope:all:query",
                                "scm:inventory:scope:all:query", "scm:order:scope:all:query",
                                "scm:purchase:scope:all:query");
            }

            // ------------------------------------------------------------------
            // 造数助手：只写 finance_* 表，全部随用例事务回滚
            // ------------------------------------------------------------------

            private long insertReceivable(String prefix, String sourceType, String entryType,
                                          Long originalId, String reason, BigDecimal amount) {
                long sourceId = 900_000L + (System.nanoTime() % 1_000_000L);
                jdbc.update("INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id, order_id, customer_id, "
                                + "customer_name_snapshot, settlement_customer_id, settlement_customer_name_snapshot, entry_type, original_receivable_id, amount, event_at, reason) "
                                + "VALUES (?, ?, ?, 900001, 900001, '客户', 900001, '客户', ?, ?, ?, now(), ?)",
                        no(prefix), sourceType, sourceId, entryType, originalId, amount, reason);
                return jdbc.queryForObject("SELECT id FROM xsy_finance_receivable WHERE source_type = ? AND source_id = ?",
                        Long.class, sourceType, sourceId);
            }

            private void insertPayable(String prefix, String sourceType, Long sourceId, String entryType, String reason) {
                jdbc.update("INSERT INTO xsy_finance_payable (payable_no, source_type, source_id, purchase_order_id, supplier_id, "
                                + "supplier_name_snapshot, entry_type, original_payable_id, amount, event_at, reason) "
                                + "VALUES (?, ?, ?, 900001, 900001, '供应商', ?, "
                                + "CASE WHEN ? = 'RED' THEN 900001 ELSE NULL END, 100.0000, now(), ?)",
                        no(prefix), sourceType, sourceId, entryType, entryType, reason);
            }

            private long insertReceipt(String receiptNo, String entryType, Long reverseOfId, String reason,
                                       String externalReference) {
                jdbc.update("INSERT INTO xsy_finance_receipt (receipt_no, customer_id, customer_name_snapshot, settlement_customer_id, settlement_customer_name_snapshot, amount, method, "
                                + "received_at, entry_type, reverse_of_id, reason, external_reference) "
                                + "VALUES (?, 900001, '客户', 900001, '客户', 100.0000, 'CASH', now(), ?, ?, ?, ?)",
                        receiptNo, entryType, reverseOfId, reason, externalReference);
                return jdbc.queryForObject("SELECT id FROM xsy_finance_receipt WHERE receipt_no = ?", Long.class, receiptNo);
            }
        }
    }

    @Nested
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("Finance R1 并发锁序（PG IT，无外层事务）")
    class ScmFinanceConcurrencyPgIT extends ScmW5PgITBase {
        private static final OffsetDateTime EVENT_AT =
                OffsetDateTime.of(2026, 5, 20, 14, 0, 0, 0, ZoneOffset.ofHours(8));

        @Autowired
        private FinanceReceiptService financeReceiptService;

        @Autowired
        private FinanceWriteOffService financeWriteOffService;

        @Override
        protected void evictMybatisCache() {
            // No outer transaction means JdbcTemplate and each command use independent sessions.
        }

        @Test
        @DisplayName("同一收款并发反向：恰好一条 REVERSE，失败侧为 41143")
        void concurrentReceiptReversalsConvergeToOneFact() throws Exception {
            FinanceReceiptVO receipt = receipt("100.0000");
            var firstFailure = new AtomicReference<Throwable>();
            var secondFailure = new AtomicReference<Throwable>();
            var firstResult = new AtomicReference<FinanceReceiptVO>();
            var secondResult = new AtomicReference<FinanceReceiptVO>();
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var done = new CountDownLatch(2);

            run("receipt-reverse-a", () -> firstResult.set(
                    financeReceiptService.reverse(receiptReverse(receipt.getReceiptId()), key("receipt-race-a"))),
                    firstFailure, ready, start, done);
            run("receipt-reverse-b", () -> secondResult.set(
                    financeReceiptService.reverse(receiptReverse(receipt.getReceiptId()), key("receipt-race-b"))),
                    secondFailure, ready, start, done);
            start(ready, start, done);

            assertThat((firstResult.get() != null) ^ (secondResult.get() != null))
                    .as("exactly one reversal command succeeds")
                    .isTrue();
            Throwable loser = firstFailure.get() != null ? firstFailure.get() : secondFailure.get();
            assertBusinessCode(loser, 41143);
            assertThat(count("SELECT count(*) FROM xsy_finance_receipt WHERE reverse_of_id = ? AND entry_type = 'REVERSE'",
                    receipt.getReceiptId())).isEqualTo(1);
        }

        @Test
        @DisplayName("两笔并发核销竞争同一余额：一笔成功，另一笔以 41135 失败")
        void concurrentWriteOffsCannotOverspendTarget() throws Exception {
            Long customerId = newCustomer();
            FinanceReceiptVO receipt = receipt(customerId, "100.0000");
            Long targetId = normalReceivable(customerId, "100.0000");
            var firstFailure = new AtomicReference<Throwable>();
            var secondFailure = new AtomicReference<Throwable>();
            var firstResult = new AtomicReference<Object>();
            var secondResult = new AtomicReference<Object>();
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var done = new CountDownLatch(2);

            run("write-off-a", () -> firstResult.set(financeWriteOffService.add(
                    writeOff(receipt.getReceiptId(), targetId, "70.0000"), key("write-off-race-a"))),
                    firstFailure, ready, start, done);
            run("write-off-b", () -> secondResult.set(financeWriteOffService.add(
                    writeOff(receipt.getReceiptId(), targetId, "70.0000"), key("write-off-race-b"))),
                    secondFailure, ready, start, done);
            start(ready, start, done);

            assertThat((firstResult.get() != null) ^ (secondResult.get() != null))
                    .as("one allocation commits and one sees the remaining open amount")
                    .isTrue();
            Throwable loser = firstFailure.get() != null ? firstFailure.get() : secondFailure.get();
            assertBusinessCode(loser, 41135);
            assertThat(count("SELECT count(*) FROM xsy_finance_write_off"
                    + " WHERE source_type = 'RECEIPT' AND source_id = ? AND target_type = 'RECEIVABLE' AND target_id = ?"
                    + " AND entry_type = 'NORMAL'", receipt.getReceiptId(), targetId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM xsy_finance_write_off"
                    + " WHERE target_type = 'RECEIVABLE' AND target_id = ? AND deleted = FALSE",
                    BigDecimal.class, targetId)).isEqualByComparingTo("70.0000");
        }

        @Test
        @DisplayName("同一核销并发反向：唯一反向行获胜，另一笔得到 41143")
        void concurrentWriteOffReversalsConvergeToOneFact() throws Exception {
            Long customerId = newCustomer();
            FinanceReceiptVO receipt = receipt(customerId, "100.0000");
            Long targetId = normalReceivable(customerId, "100.0000");
            FinanceWriteOffVO normal = financeWriteOffService.add(
                    writeOff(receipt.getReceiptId(), targetId, "40.0000"), key("write-off-before-race"))
                    .getItems().getFirst();
            var firstFailure = new AtomicReference<Throwable>();
            var secondFailure = new AtomicReference<Throwable>();
            var firstResult = new AtomicReference<FinanceWriteOffVO>();
            var secondResult = new AtomicReference<FinanceWriteOffVO>();
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var done = new CountDownLatch(2);

            run("write-off-reverse-a", () -> firstResult.set(financeWriteOffService.reverse(
                    writeOffReverse(normal.getWriteOffId()), key("write-off-reverse-race-a"))),
                    firstFailure, ready, start, done);
            run("write-off-reverse-b", () -> secondResult.set(financeWriteOffService.reverse(
                    writeOffReverse(normal.getWriteOffId()), key("write-off-reverse-race-b"))),
                    secondFailure, ready, start, done);
            start(ready, start, done);

            assertThat((firstResult.get() != null) ^ (secondResult.get() != null))
                    .as("one reversal wins the source/target/original lock sequence")
                    .isTrue();
            Throwable loser = firstFailure.get() != null ? firstFailure.get() : secondFailure.get();
            assertBusinessCode(loser, 41143);
            assertThat(count("SELECT count(*) FROM xsy_finance_write_off WHERE reverse_of_id = ?"
                    + " AND entry_type = 'REVERSE'", normal.getWriteOffId())).isEqualTo(1);
        }

        @Test
        @DisplayName("收款反向与核销反向共用来源锁，不会产生负待核销额")
        void receiptAndWriteOffReversalShareSourceLock() throws Exception {
            Long customerId = newCustomer();
            FinanceReceiptVO receipt = receipt(customerId, "100.0000");
            Long targetId = normalReceivable(customerId, "100.0000");
            FinanceWriteOffVO normal = financeWriteOffService.add(
                    writeOff(receipt.getReceiptId(), targetId, "50.0000"), key("shared-lock-write-off"))
                    .getItems().getFirst();
            var writeOffFailure = new AtomicReference<Throwable>();
            var receiptFailure = new AtomicReference<Throwable>();
            var writeOffResult = new AtomicReference<FinanceWriteOffVO>();
            var receiptResult = new AtomicReference<FinanceReceiptVO>();
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var done = new CountDownLatch(2);

            run("write-off-reverse-shared", () -> writeOffResult.set(financeWriteOffService.reverse(
                    writeOffReverse(normal.getWriteOffId()), key("shared-lock-write-off-reverse"))),
                    writeOffFailure, ready, start, done);
            run("receipt-reverse-shared", () -> receiptResult.set(financeReceiptService.reverse(
                    receiptReverse(receipt.getReceiptId()), key("shared-lock-receipt-reverse"))),
                    receiptFailure, ready, start, done);
            start(ready, start, done);

            assertThat(writeOffResult.get()).isNotNull();
            assertThat(writeOffFailure.get()).isNull();
            BigDecimal effectiveReceiptAmount = jdbc.queryForObject("SELECT amount - COALESCE((SELECT SUM(amount)"
                            + " FROM xsy_finance_receipt reverse WHERE reverse.reverse_of_id = original.id"
                            + " AND reverse.entry_type = 'REVERSE' AND reverse.deleted = FALSE), 0)"
                            + " FROM xsy_finance_receipt original WHERE original.id = ?",
                    BigDecimal.class, receipt.getReceiptId());
            BigDecimal usedAmount = jdbc.queryForObject("SELECT COALESCE(SUM(CASE"
                            + " WHEN entry_type = 'NORMAL' THEN amount WHEN entry_type = 'REVERSE' THEN -amount ELSE 0 END), 0)"
                            + " FROM xsy_finance_write_off WHERE source_type = 'RECEIPT' AND source_id = ? AND deleted = FALSE",
                    BigDecimal.class, receipt.getReceiptId());
            assertThat(effectiveReceiptAmount.subtract(usedAmount)).isGreaterThanOrEqualTo(BigDecimal.ZERO);
            if (receiptResult.get() != null) {
                assertThat(receiptFailure.get()).isNull();
                assertThat(effectiveReceiptAmount).isEqualByComparingTo(BigDecimal.ZERO);
                assertThat(usedAmount).isEqualByComparingTo(BigDecimal.ZERO);
            } else {
                assertBusinessCode(receiptFailure.get(), 41142);
            }
        }

        private FinanceReceiptVO receipt(String amount) {
            return receipt(newCustomer(), amount);
        }

        private FinanceReceiptVO receipt(Long customerId, String amount) {
            FinanceReceiptAddForm form = new FinanceReceiptAddForm();
            form.setCustomerId(customerId);
            form.setAmount(amount);
            form.setMethod("BANK_TRANSFER");
            form.setReceivedAt(EVENT_AT);
            return financeReceiptService.add(form, key("receipt-add"));
        }

        private Long normalReceivable(Long customerId, String amount) {
            long sourceId = uniqueNumber();
            return jdbc.queryForObject("INSERT INTO xsy_finance_receivable (receivable_no, source_type, source_id, order_id,"
                            + " customer_id, customer_name_snapshot, settlement_customer_id,"
                            + " settlement_customer_name_snapshot, entry_type, amount, event_at, created_at, updated_at)"
                            + " VALUES (?, 'SALES_ORDER', ?, 1, ?, '并发核销客户', ?, '并发核销客户', 'NORMAL', ?,"
                            + " now(), now(), now())"
                            + " RETURNING id",
                    Long.class, "AR-RACE-" + prefix + "-" + UUID.randomUUID(), sourceId, customerId, customerId,
                    new BigDecimal(amount));
        }

        private FinanceWriteOffAddForm writeOff(Long receiptId, Long targetId, String amount) {
            FinanceWriteOffAddItemForm item = new FinanceWriteOffAddItemForm();
            item.setTargetId(targetId);
            item.setAmount(amount);
            FinanceWriteOffAddForm form = new FinanceWriteOffAddForm();
            form.setSourceType("RECEIPT");
            form.setSourceId(receiptId);
            form.setItems(List.of(item));
            return form;
        }

        private FinanceWriteOffReverseForm writeOffReverse(Long writeOffId) {
            FinanceWriteOffReverseForm form = new FinanceWriteOffReverseForm();
            form.setWriteOffId(writeOffId);
            form.setReason("并发反向核销");
            return form;
        }

        private FinanceReceiptReverseForm receiptReverse(Long receiptId) {
            FinanceReceiptReverseForm form = new FinanceReceiptReverseForm();
            form.setReceiptId(receiptId);
            form.setReason("并发反向收款");
            return form;
        }

        private void run(String name, Runnable body, AtomicReference<Throwable> failure,
                CountDownLatch ready, CountDownLatch start, CountDownLatch done) {
            new Thread(() -> {
                login(name);
                try {
                    ready.countDown();
                    start.await();
                    body.run();
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    XsyRequestUtil.remove();
                    done.countDown();
                }
            }, name).start();
        }

        private void login(String name) {
            RequestEmployee employee = new RequestEmployee();
            employee.setEmployeeId(1L);
            employee.setActualName(name);
            employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            employee.setAdministratorFlag(true);
            XsyRequestUtil.setRequestUser(employee);
        }

        private void start(CountDownLatch ready, CountDownLatch start, CountDownLatch done) throws InterruptedException {
            assertThat(ready.await(30, TimeUnit.SECONDS)).as("threads reach the same start line").isTrue();
            start.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS)).as("lock contenders finish without deadlock").isTrue();
        }

        private static void assertBusinessCode(Throwable error, int expectedCode) {
            assertThat(error).isInstanceOf(ScmBusinessException.class);
            assertThat(((ScmBusinessException) error).getErrorCode().getCode()).isEqualTo(expectedCode);
        }

        private int count(String sql, Object... args) {
            Integer value = jdbc.queryForObject(sql, Integer.class, args);
            return value == null ? 0 : value;
        }

        private String key(String label) {
            return prefix + ":" + label + ":" + UUID.randomUUID();
        }

        private long uniqueNumber() {
            return 1_000_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 8_000_000_000L);
        }
    }

    @Nested
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("应付生成失败与收货确认回滚（F1-2A，PG IT，无外层事务）")
    class ScmFinancePayableRollbackPgIT extends ScmW6PgITBase {
        @Autowired
        private FinancePayableService financePayableService;

        @Override
        protected void evictMybatisCache() {
            // 无外层事务 → 每次 DAO 调用都是新 session → 一级缓存天然为空
        }

        private PurchaseReceiptVO draftDirectReceipt(String suffix, Long skuId, String planned, String price) {
            Long supplierId = newSupplier(suffix);
            linkSupplierSku(supplierId, skuId, DEFAULT_PURCHASE_UNIT);
            PurchaseOrderVO order = createDraftOrder(suffix, supplierId, skuId, planned, price);
            submitOrder(order.getId());

            PurchaseReceiptCreateForm form = new PurchaseReceiptCreateForm();
            form.setPurchaseOrderId(order.getId());
            form.setReceiptMode(ScmReceiptModeEnum.DIRECT.name());
            form.setRemark("F1-2A rollback 收货单");
            return purchaseReceiptService.create(form, prefix + ":receipt:" + order.getId());
        }

        private void confirm(PurchaseReceiptVO receipt, String quantity) {
            PurchaseReceiptVO current = reloadReceipt(receipt.getId());
            PurchaseReceiptItemVO line = current.getItems().getFirst();
            purchaseReceiptService.confirm(
                    confirmForm(current.getId(), current.getVersion(),
                            receiptLine(line.getId(), line.getVersion(), quantity)),
                    prefix + ":confirm:" + current.getId() + ":" + UUID.randomUUID());
        }

        // ------------------------------------------------------------------
        // 1. 财务写入失败 → 收货确认整笔回滚
        // ------------------------------------------------------------------

        @Test
        @DisplayName("来源键被占：confirm 抛错 → 收货单仍 PENDING、无应付无日志、库存与防重复处理零残留")
        void financeFailureRollsBackTheWholeReceiptConfirm() {
            Long skuId = newOnShelfSku("FPR1");
            PurchaseReceiptVO receipt = draftDirectReceipt("FPR1", skuId, "8.0000", "6.2000");
            Long receiptItemId = receipt.getItems().getFirst().getId();

            // 先占掉这一行的应付来源键（模拟「同一收货行已被别的应付单记账」的数据异常）
            jdbc.update("INSERT INTO xsy_finance_payable_item (payable_id, source_type, source_id, "
                            + "purchase_order_item_id, sku_id, sku_name_snapshot, unit_snapshot, "
                            + "quantity, unit_price, amount, created_at, updated_at) "
                            + "VALUES (?, 'PURCHASE_RECEIPT_ITEM', ?, ?, ?, '占位', 'kg', "
                            + "        1.0000, 1.0000, 1.0000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    -1L, receiptItemId, receipt.getItems().getFirst().getPurchaseOrderItemId(), skuId);

            try {
                assertThatThrownBy(() -> confirm(receipt, "8.0000"))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("收货行已挂在别的应付单上");

                // 采购侧什么都没留下：状态、累计量、单号、防重复处理结果全部回到确认前
                PurchaseReceiptVO after = reloadReceipt(receipt.getId());
                assertThat(after.getStatus()).isEqualTo("PENDING");
                assertThat(after.getConfirmedAt()).isNull();
                assertThat(after.getPutawayStatus()).isEqualTo("PENDING");
                assertThat(reloadOrder(receipt.getPurchaseOrderId()).getStatus()).isEqualTo("SUBMITTED");
                assertThat(reloadOrder(receipt.getPurchaseOrderId()).getItems().getFirst().getReceivedQuantity())
                        .isEqualByComparingTo("0.0000");

                // 财务侧既没有单头也没有第二行明细，也没有「改了很多但没记下来」的日志
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_finance_payable WHERE source_type = 'PURCHASE_RECEIPT' AND source_id = ?",
                        Integer.class, receipt.getId())).isZero();
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_finance_payable_item WHERE source_type = 'PURCHASE_RECEIPT_ITEM' "
                                + "AND source_id = ? AND payable_id > 0", Integer.class, receiptItemId)).isZero();
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_finance_operation_log l WHERE l.business_type = 'PAYABLE' "
                                + "AND l.business_id IN (SELECT id FROM xsy_finance_payable "
                                + "                   WHERE source_type = 'PURCHASE_RECEIPT' AND source_id = ?)",
                        Integer.class, receipt.getId()))
                        .as("应付未成立 → 不留生成日志").isZero();

                // DIRECT 收货确认的库存写入也在同一事务里，因此同样零残留
                assertThat(movementCount(seedWarehouseId(), skuId)).isZero();
                assertThat(balanceRowCount(seedWarehouseId(), skuId)).isZero();
            } finally {
                // 占位行是本用例故意造的假数据：append-only 表在库里没有删除入口，测试负责清干净
                jdbc.update("DELETE FROM xsy_finance_payable_item WHERE payable_id = -1");
            }
        }

        // ------------------------------------------------------------------
        // 2. 生成器不吃「自己开一个事务」这条路
        // ------------------------------------------------------------------

        @Test
        @DisplayName("无外层事务调用生成器：MANDATORY 直接拒绝，绝不会自己提交一张应付")
        void generatorRefusesToRunWithoutTheTriggerTransaction() {
            assertThatThrownBy(() -> financePayableService.generateOnReceiptConfirm(-1L))
                    .isInstanceOf(IllegalTransactionStateException.class);

            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_finance_payable WHERE source_id = -1", Integer.class)).isZero();
        }
    }

    @Nested
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("同一退款并发双付的收敛（F1-3B，PG IT，无外层事务）")
    class ScmFinancePaymentRacePgIT extends ScmW5PgITBase {
        @Autowired
        private FinancePaymentService financePaymentService;

        @Autowired
        private OrderReturnService returns;

        @Autowired
        private OrderRefundService refunds;

        @Override
        protected void evictMybatisCache() {
            // 无外层事务 → 每次 DAO 调用都是新 session → 一级缓存天然为空
        }

        private record Refund(Long refundId, Long customerId, BigDecimal refundAmount) {
        }

        /** 真链路建到「退款 COMPLETED」，两条竞争事务的前置全部已提交。 */
        private Refund completedRefund(String tag) {
            Long customerId = newCustomer();
            Long skuId = newOnShelfSku(tag);
            Long orderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
            Long orderItemId = confirmedSalesOrderItemId(orderId);

            var create = new OrderReturnAddForm();
            create.setOrderId(orderId);
            create.setReason("品质问题 " + tag);
            var row = new OrderReturnItemForm();
            row.setOrderItemId(orderItemId);
            row.setRequestedQuantity("3.0000");
            create.setItems(List.of(row));
            var created = returns.create(create, key("rc:" + tag));

            var approve = new OrderReturnApproveForm();
            approve.setReturnId(created.getReturnId());
            approve.setVersion(created.getVersion());
            var approvedRow = new OrderReturnApproveItemForm();
            approvedRow.setOrderItemId(orderItemId);
            approvedRow.setApprovedQuantity("3.0000");
            approve.setItems(List.of(approvedRow));
            returns.approve(approve, key("ra:" + tag));

            // 按 order_id 直查退款行，不绕 refunds.query(...).getFirst()：
            // 那个分页读接口按 orderSellerScope 收窄（非超管身份下会拿不到行），
            // 而夹具要的只是「这一单的退款」，直查不依赖读侧范围也不依赖行序。
            Long refundId = jdbc.queryForObject(
                    "SELECT id FROM xsy_order_refund WHERE order_id = ? AND deleted = FALSE", Long.class, orderId);
            Integer refundVersion = jdbc.queryForObject(
                    "SELECT version FROM xsy_order_refund WHERE id = ?", Integer.class, refundId);
            var complete = new OrderRefundCompleteForm();
            complete.setRefundId(refundId);
            complete.setVersion(refundVersion);
            // order_refund.external_reference 上有部分唯一索引（uk_order_refund_external_reference_active），
            // 与财务侧「可重复」的同名列正相反；本类是真提交，所以这里的值必须每次唯一，
            // 否则第二遍跑同一个库就会被自己的上一轮挡住（并被 complete 映射成状态错误）。
            complete.setExternalReference("ORDER-SIDE-" + tag + "-" + UUID.randomUUID());
            refunds.complete(complete, key("fc:" + tag));

            return new Refund(refundId, customerId,
                    jdbc.queryForObject("SELECT refund_amount FROM xsy_order_refund WHERE id = ?",
                            BigDecimal.class, refundId));
        }

        private FinancePaymentAddForm form(Refund refund) {
            var form = new FinancePaymentAddForm();
            form.setCounterpartyType("CUSTOMER");
            form.setCounterpartyId(refund.customerId());
            form.setAmount(refund.refundAmount().toPlainString());
            form.setMethod("BANK_TRANSFER");
            form.setPaidAt(OffsetDateTime.now().minusDays(1));
            form.setSourceType("ORDER_REFUND");
            form.setSourceId(refund.refundId());
            form.setExternalReference("RACE-" + UUID.randomUUID().toString().substring(0, 8));
            form.setRemark("F1-3B 并发用例");
            return form;
        }

        private String key(String tag) {
            return prefix + ":" + tag + ":" + UUID.randomUUID();
        }

        private void login(String name) {
            var employee = new RequestEmployee();
            employee.setEmployeeId(1L);
            employee.setActualName(name);
            employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            employee.setAdministratorFlag(true);
            XsyRequestUtil.setRequestUser(employee);
        }

        private void run(String name, Runnable body, AtomicReference<Throwable> failure,
                         CountDownLatch ready, CountDownLatch start, CountDownLatch done) {
            new Thread(() -> {
                login(name);
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

        @Test
        @DisplayName("两个不同防重复请求编号同时付同一张退款：恰好一笔付款 + 一条日志，失败侧是 41139 而不是裸唯一键异常")
        void concurrentRefundPaymentsConvergeToOneFact() throws Exception {
            Refund refund = completedRefund("RACE1");

            var firstFailure = new AtomicReference<Throwable>();
            var secondFailure = new AtomicReference<Throwable>();
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var done = new CountDownLatch(2);

            run("pay-thread-a",
                    () -> financePaymentService.add(form(refund), key("race-a")),
                    firstFailure, ready, start, done);
            run("pay-thread-b",
                    () -> financePaymentService.add(form(refund), key("race-b")),
                    secondFailure, ready, start, done);

            assertThat(ready.await(30, TimeUnit.SECONDS)).as("两个线程都要到达起跑线").isTrue();
            start.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS))
                    .as("两个事务必须在 60 秒内结束（否则疑似死锁或长等待）").isTrue();

            // 必须且只能有一侧失败
            boolean aFailed = firstFailure.get() != null;
            boolean bFailed = secondFailure.get() != null;
            assertThat(aFailed ^ bFailed)
                    .as("一侧成功、一侧被来源唯一索引拒绝，不得双双成功或双双失败")
                    .isTrue();
            Throwable loser = aFailed ? firstFailure.get() : secondFailure.get();
            assertThat(loser)
                    .as("失败必须是可读的业务异常，不能把 DuplicateKeyException / SQLState / 约束名抛到接口")
                    .isInstanceOf(ScmBusinessException.class)
                    .isNotInstanceOf(DataAccessException.class);
            assertThat(((ScmBusinessException) loser).getErrorCode().getCode())
                    .as("FINANCE_PAYMENT_SOURCE_INVALID").isEqualTo(41139);

            // 最终记录：一笔付款、一条日志，两个 claim 各自成对（成功侧完成、失败侧随事务回滚）
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_finance_payment WHERE source_id = ?", Integer.class, refund.refundId()))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_finance_operation_log l JOIN xsy_finance_payment p ON p.id = l.business_id"
                            + " WHERE l.business_type = 'PAYMENT' AND l.operation_type = 'PAY'"
                            + " AND p.source_id = ?", Integer.class, refund.refundId()))
                    .as("付款与日志同事务，因此只可能有一条").isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_finance_payment WHERE source_id = ? AND entry_type = 'NORMAL'"
                            + " AND amount = ?", Integer.class, refund.refundId(), refund.refundAmount()))
                    .as("留下的那一笔金额正是退款应退额").isEqualTo(1);
        }
    }

    @Nested
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("付款登记的事务原子性（F1-3B，PG IT，无外层事务）")
    class ScmFinancePaymentRollbackPgIT extends ScmW5PgITBase {
        /** 只有本用例的日志行带这个 remark 标记，触发函数因此不可能影响其它用例。 */
        private static final String MARKER = "F1-3B-LOG-FAILURE-" + UUID.randomUUID();

        @Autowired
        private FinancePaymentService financePaymentService;

        @Autowired
        private OrderReturnService returns;

        @Autowired
        private OrderRefundService refunds;

        @Override
        protected void evictMybatisCache() {
            // 无外层事务 → 每次 DAO 调用都是新 session → 一级缓存天然为空
        }

        private FinancePaymentAddForm form(Long counterpartyId, String amount, String remark,
                                           String sourceType, Long sourceId) {
            var form = new FinancePaymentAddForm();
            form.setCounterpartyType(sourceType == null ? "SUPPLIER" : "CUSTOMER");
            form.setCounterpartyId(counterpartyId);
            form.setAmount(amount);
            form.setMethod("BANK_TRANSFER");
            form.setPaidAt(OffsetDateTime.now().minusDays(2));
            form.setSourceType(sourceType);
            form.setSourceId(sourceId);
            form.setExternalReference("RB-" + UUID.randomUUID().toString().substring(0, 8));
            form.setRemark(remark);
            return form;
        }

        private Long supplier() {
            return newSupplier("RB" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 1000));
        }

        /** 真链路造一张 COMPLETED 退款，返回 {@code (refundId, customerId, refundAmount)}。 */
        private Object[] completedRefund(String tag) {
            Long customerId = newCustomer();
            Long skuId = newOnShelfSku(tag);
            Long orderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
            Long orderItemId = confirmedSalesOrderItemId(orderId);

            var create = new OrderReturnAddForm();
            create.setOrderId(orderId);
            create.setReason("品质问题 " + tag);
            var row = new OrderReturnItemForm();
            row.setOrderItemId(orderItemId);
            row.setRequestedQuantity("2.0000");
            create.setItems(List.of(row));
            var created = returns.create(create, prefix + ":rc:" + tag + UUID.randomUUID());

            var approve = new OrderReturnApproveForm();
            approve.setReturnId(created.getReturnId());
            approve.setVersion(created.getVersion());
            var approvedRow = new OrderReturnApproveItemForm();
            approvedRow.setOrderItemId(orderItemId);
            approvedRow.setApprovedQuantity("2.0000");
            approve.setItems(List.of(approvedRow));
            returns.approve(approve, prefix + ":ra:" + tag + UUID.randomUUID());

            // 按 order_id 直查退款行，不绕 refunds.query(...).getFirst()：
            // 那个分页读接口按 orderSellerScope 收窄（非超管身份下会拿不到行），
            // 而夹具要的只是「这一单的退款」，直查不依赖读侧范围也不依赖行序。
            Long refundId = jdbc.queryForObject(
                    "SELECT id FROM xsy_order_refund WHERE order_id = ? AND deleted = FALSE", Long.class, orderId);
            Integer refundVersion = jdbc.queryForObject(
                    "SELECT version FROM xsy_order_refund WHERE id = ?", Integer.class, refundId);
            var complete = new OrderRefundCompleteForm();
            complete.setRefundId(refundId);
            complete.setVersion(refundVersion);
            // order_refund.external_reference 上有部分唯一索引（uk_order_refund_external_reference_active），
            // 与财务侧「可重复」的同名列正相反；本类是真提交，所以这里的值必须每次唯一，
            // 否则第二遍跑同一个库就会被自己的上一轮挡住（并被 complete 映射成状态错误）。
            complete.setExternalReference("ORDER-SIDE-" + tag + "-" + UUID.randomUUID());
            refunds.complete(complete, prefix + ":fc:" + tag + UUID.randomUUID());

            BigDecimal amount = jdbc.queryForObject(
                    "SELECT refund_amount FROM xsy_order_refund WHERE id = ?", BigDecimal.class, refundId);
            return new Object[]{refundId, customerId, amount};
        }

        private int count(String sql, Object... args) {
            Integer value = jdbc.queryForObject(sql, Integer.class, args);
            return value == null ? 0 : value;
        }

        // ------------------------------------------------------------------
        // A. 付款已插入、操作日志写入失败 → 整笔回滚
        // ------------------------------------------------------------------

        @Test
        @DisplayName("日志阶段失败：付款单、日志、防重复处理记录三者零残留")
        void operationLogFailureRollsBackTheWholePayment() {
            Long supplierId = supplier();
            String idempotencyKey = prefix + ":log-failure:" + UUID.randomUUID();

            jdbc.execute("CREATE OR REPLACE FUNCTION scm_f13b_break_payment_log() RETURNS trigger AS "
                    + "$BODY$ BEGIN IF NEW.after_data->>'remark' = '" + MARKER + "' THEN"
                    + " RAISE EXCEPTION 'injected finance_operation_log failure'; END IF;"
                    + " RETURN NEW; END $BODY$ LANGUAGE plpgsql");
            jdbc.execute("CREATE TRIGGER trg_scm_f13b_break_payment_log"
                    + " BEFORE INSERT ON xsy_finance_operation_log"
                    + " FOR EACH ROW EXECUTE FUNCTION scm_f13b_break_payment_log()");
            try {
                assertThatThrownBy(() -> financePaymentService.add(
                        form(supplierId, "88.0000", MARKER, null, null), idempotencyKey))
                        .isInstanceOf(Throwable.class);

                assertThat(count("SELECT count(*) FROM xsy_finance_payment WHERE counterparty_id = ?", supplierId))
                        .as("付款单不得留下").isZero();
                assertThat(count("SELECT count(*) FROM xsy_finance_operation_log WHERE business_type = 'PAYMENT'"
                        + " AND after_data->>'remark' = ?", MARKER))
                        .as("日志本身也没有").isZero();
                assertThat(count("SELECT count(*) FROM xsy_idempotency_record WHERE idempotency_key = ?", idempotencyKey))
                        .as("claim 随事务回滚，否则同一 key 的重放会读到空结果").isZero();
            } finally {
                jdbc.execute("DROP TRIGGER IF EXISTS trg_scm_f13b_break_payment_log ON xsy_finance_operation_log");
                jdbc.execute("DROP FUNCTION IF EXISTS scm_f13b_break_payment_log()");
            }

            // 触发器已摘除，同一 key 现在能正常登记一次（也证明上一条的零残留不是假象）
            Long paymentId = financePaymentService.add(
                    form(supplierId, "88.0000", MARKER, null, null), idempotencyKey).getPaymentId();
            assertThat(paymentId).isNotNull();
            assertThat(count("SELECT count(*) FROM xsy_finance_operation_log WHERE business_type = 'PAYMENT'"
                    + " AND business_id = ?", paymentId)).isEqualTo(1);
        }

        // ------------------------------------------------------------------
        // B. 同一退款二次付款：来源唯一索引冲突 → 原记录保持、失败侧零残留
        // ------------------------------------------------------------------

        @Test
        @DisplayName("退款来源唯一冲突：原付款保持、无第二笔、无第二份日志、失败请求的 claim 不留在库里")
        void duplicateRefundSourceRollsBackCleanly() {
            Object[] refund = completedRefund("B");
            Long refundId = (Long) refund[0];
            Long customerId = (Long) refund[1];
            String amount = ((BigDecimal) refund[2]).toPlainString();
            String firstKey = prefix + ":paid:" + UUID.randomUUID();
            String secondKey = prefix + ":again:" + UUID.randomUUID();

            Long firstId = financePaymentService.add(form(customerId, amount, "首次付款", "ORDER_REFUND", refundId),
                    firstKey).getPaymentId();

            int paymentsBefore = count("SELECT count(*) FROM xsy_finance_payment");
            int logsBefore = count("SELECT count(*) FROM xsy_finance_operation_log WHERE business_type = 'PAYMENT'");

            assertThatThrownBy(() -> financePaymentService.add(
                    form(customerId, amount, "重复付款", "ORDER_REFUND", refundId), secondKey))
                    .isInstanceOf(com.xsy.scm.common.exception.ScmBusinessException.class);

            assertThat(count("SELECT count(*) FROM xsy_finance_payment")).isEqualTo(paymentsBefore);
            assertThat(count("SELECT count(*) FROM xsy_finance_payment WHERE source_id = ?", refundId)).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM xsy_finance_operation_log WHERE business_type = 'PAYMENT'"))
                    .as("失败的那一次一条日志都不留").isEqualTo(logsBefore);
            assertThat(count("SELECT count(*) FROM xsy_idempotency_record WHERE idempotency_key = ?", secondKey))
                    .as("既有框架的真实语义：claim 与业务写同事务，整笔回滚后即 0 行，"
                            + "因此同一把失败 key 可以重放而不是留下一个空结果占位").isZero();
            assertThat(count("SELECT count(*) FROM xsy_finance_payment WHERE id = ?", firstId))
                    .as("原付款一字未动，付款是 append-only 记录").isEqualTo(1);
        }
    }

    @Nested
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("收款登记的事务原子性（F1-3A，PG IT，无外层事务）")
    class ScmFinanceReceiptRollbackPgIT extends ScmW5PgITBase {
        /** 只有本用例的日志行带这个 remark 标记，触发函数因此不可能影响其它用例。 */
        private static final String MARKER = "F1-3A-LOG-FAILURE-" + UUID.randomUUID();

        @Autowired
        private FinanceReceiptService financeReceiptService;

        @Override
        protected void evictMybatisCache() {
            // 无外层事务 → 每次 DAO 调用都是新 session → 一级缓存天然为空
        }

        private FinanceReceiptAddForm form(Long customerId, String amount, String remark) {
            var form = new FinanceReceiptAddForm();
            form.setCustomerId(customerId);
            form.setAmount(amount);
            form.setMethod("CASH");
            form.setReceivedAt(java.time.OffsetDateTime.now().minusDays(1));
            form.setExternalReference("RB-" + UUID.randomUUID().toString().substring(0, 8));
            form.setRemark(remark);
            return form;
        }

        private Long customer() {
            Long customerId = newCustomer();
            return customerId;
        }

        private int count(String sql, Object... args) {
            Integer value = jdbc.queryForObject(sql, Integer.class, args);
            return value == null ? 0 : value;
        }

        // ------------------------------------------------------------------
        // 1. 收款已插入、操作日志写入失败 → 整笔回滚
        // ------------------------------------------------------------------

        @Test
        @DisplayName("日志阶段失败：收款单、日志、防重复处理记录三者零残留")
        void operationLogFailureRollsBackTheWholeReceipt() {
            Long customerId = customer();
            String idempotencyKey = prefix + ":log-failure:" + UUID.randomUUID();

            // 可控的「后段失败」：只在带本次标记的日志行上抛异常，真实链路照常。
            jdbc.execute("CREATE OR REPLACE FUNCTION scm_f13a_break_receipt_log() RETURNS trigger AS "
                    + "$BODY$ BEGIN IF NEW.after_data->>'remark' = '" + MARKER + "' THEN"
                    + " RAISE EXCEPTION 'injected finance_operation_log failure'; END IF;"
                    + " RETURN NEW; END $BODY$ LANGUAGE plpgsql");
            jdbc.execute("CREATE TRIGGER trg_scm_f13a_break_receipt_log"
                    + " BEFORE INSERT ON xsy_finance_operation_log"
                    + " FOR EACH ROW EXECUTE FUNCTION scm_f13a_break_receipt_log()");
            try {
                assertThatThrownBy(() -> financeReceiptService.add(form(customerId, "66.0000", MARKER), idempotencyKey))
                        .isInstanceOf(Throwable.class);

                assertThat(count("SELECT count(*) FROM xsy_finance_receipt WHERE customer_id = ?", customerId))
                        .as("收款单不得留下").isZero();
                assertThat(count("SELECT count(*) FROM xsy_finance_operation_log WHERE business_type = 'RECEIPT'"
                        + " AND after_data->>'remark' = ?", MARKER))
                        .as("日志本身也没有").isZero();
                assertThat(count("SELECT count(*) FROM xsy_idempotency_record WHERE idempotency_key = ?", idempotencyKey))
                        .as("claim 随事务回滚，否则同一 key 的重放会读到空结果").isZero();
            } finally {
                jdbc.execute("DROP TRIGGER IF EXISTS trg_scm_f13a_break_receipt_log ON xsy_finance_operation_log");
                jdbc.execute("DROP FUNCTION IF EXISTS scm_f13a_break_receipt_log()");
            }

            // 触发器已摘除，同一 key 现在能正常登记一次（也证明上一条的零残留不是假象）
            Long receiptId = financeReceiptService.add(form(customerId, "66.0000", MARKER), idempotencyKey)
                    .getReceiptId();
            assertThat(receiptId).isNotNull();
            assertThat(count("SELECT count(*) FROM xsy_finance_operation_log WHERE business_type = 'RECEIPT'"
                    + " AND business_id = ?", receiptId)).isEqualTo(1);
        }

        // ------------------------------------------------------------------
        // 2. 收款插入本身失败（单号撞唯一索引）→ 日志与 claim 都不留
        // ------------------------------------------------------------------

        @Test
        @DisplayName("插入阶段失败（receipt_no 撞 uk_finance_receipt_no）：日志与防重复处理记录零残留")
        void receiptNoCollisionRollsBackLogAndClaim() {
            Long customerId = customer();
            String idempotencyKey = prefix + ":no-collision:" + UUID.randomUUID();

            // 固定雪花 ID，预先占掉服务侧下一次将要使用的单号，制造 receipt_no 唯一键冲突
            String taken = "XSY1723456789012345678";
            jdbc.update("INSERT INTO xsy_finance_receipt (receipt_no, customer_id, customer_name_snapshot,"
                            + " settlement_customer_id, settlement_customer_name_snapshot,"
                            + " amount, method, received_at, entry_type, created_at, updated_at, created_by)"
                            + " VALUES (?, ?, '占位客户', ?, '占位客户', 1.0000, 'CASH', now(), 'NORMAL', now(), now(), '2:1')",
                    taken, customerId, customerId);

            // 基线：本类是 NOT_SUPPORTED（真提交），前一个用例留下的收款与日志是既有记录，
            // 因此「零残留」只能相对本次调用之前测量，按全局零断言会把别人的证据算成自己的泄漏。
            int receiptsBefore = count("SELECT count(*) FROM xsy_finance_receipt");
            int receiveLogsBefore = count("SELECT count(*) FROM xsy_finance_operation_log"
                    + " WHERE business_type = 'RECEIPT'");

            try (var numbers = mockStatic(ScmDocumentNumbers.class)) {
                numbers.when(ScmDocumentNumbers::next).thenReturn(taken);
                assertThatThrownBy(() -> financeReceiptService.add(form(customerId, "33.0000", "撞号用例"),
                        idempotencyKey))
                        .isInstanceOf(DataIntegrityViolationException.class);

                assertThat(count("SELECT count(*) FROM xsy_finance_receipt"))
                        .as("基线已含夹具那一行，服务侧什么都没写")
                        .isEqualTo(receiptsBefore);
                assertThat(count("SELECT count(*) FROM xsy_finance_receipt WHERE customer_id = ?"
                        + " AND receipt_no <> ?", customerId, taken))
                        .as("除夹具那一行外没有新收款单").isZero();
                assertThat(count("SELECT count(*) FROM xsy_finance_operation_log WHERE business_type = 'RECEIPT'"))
                        .as("日志与收款单同事务，一起回滚").isEqualTo(receiveLogsBefore);
                assertThat(count("SELECT count(*) FROM xsy_idempotency_record WHERE idempotency_key = ?", idempotencyKey))
                        .as("防重复处理 claim 也随事务回滚").isZero();
            } finally {
                jdbc.update("DELETE FROM xsy_finance_receipt WHERE receipt_no = ?", taken);
            }
        }
    }

    @Nested
    @DisplayName("退货批准生成红字应收（Finance R1 F1-2C，PG IT）")
    class ScmFinanceReceivableRedPgIT extends ScmW6PgITBase {
        @Autowired
        private DeliveryRouteService routeService;

        @Autowired
        private OrderReturnService returns;

        @Autowired
        private FinanceReceivableService financeReceivableService;

        @Autowired
        private PromotionCouponService promotionCouponService;

        // ------------------------------------------------------------------
        // 夹具
        // ------------------------------------------------------------------

        private record Chain(Long routeId, Long warehouseId, Long skuId, Long customerId, Long orderId,
                             Long orderItemId) {
        }

        /**
         * 单行链路：已确认订单（可指定单价）+ 已分拣 + 线路已规划，未发车。
         *
         * <p>单价走订单手工改价通道（{@code manualPriceOverride}）：这是订单域真实支持的定价路径，
         * 且 0 元赠品行也合法（{@code OrderValidator.decimal(value, false)}），
         * 不需要为了测试去改商品主数据。
         */
        private Chain planned(String tag, String ordered, String price, String sorted) {
            Long warehouseId = locatedWarehouse(tag);
            Long skuId = newOnShelfSku(tag);
            // 库存备足：本类的少拣 / 超额退货场景要的是「出库量与订单量不一致」，
            // 不是「无货可发」，库存不足会让发车失败并把原因混淆成夹具问题。
            stockIn(warehouseId, skuId, tag, "1000.0000");
            Long customerId = addressedCustomer(tag);
            Long orderId = confirmedOrder(tag, customerId, List.of(line(skuId, ordered, price)), ordered);
            sortLines(orderId, List.of(sorted), warehouseId);
            Long routeId = newRoute(warehouseId);
            attach(routeId, orderId);
            locateAllStops(routeId);
            plan(routeId);
            Long orderItemId = confirmedSalesOrderItemId(orderId);
            return new Chain(routeId, warehouseId, skuId, customerId, orderId, orderItemId);
        }

        /** 两行链路（两个 SKU 各自单价），两行都按同一实发量分拣。 */
        private Chain plannedTwoLines(String tag, Long skuA, String priceA, Long skuB, String priceB,
                                      String ordered, String sorted) {
            Long warehouseId = locatedWarehouse(tag);
            stockIn(warehouseId, skuA, tag + "A", "1000.0000");
            stockIn(warehouseId, skuB, tag + "B", "1000.0000");
            Long customerId = addressedCustomer(tag);
            Long orderId = confirmedOrder(tag, customerId,
                    List.of(line(skuA, ordered, priceA), line(skuB, ordered, priceB)), ordered);
            sortLines(orderId, List.of(sorted, sorted), warehouseId);
            Long routeId = newRoute(warehouseId);
            attach(routeId, orderId);
            locateAllStops(routeId);
            plan(routeId);
            List<Long> itemIds = orderItemIds(orderId);
            return new Chain(routeId, warehouseId, skuA, customerId, orderId, itemIds.getFirst());
        }

        private SalesOrderItemForm line(Long skuId, String quantity, String price) {
            var item = new SalesOrderItemForm();
            item.setSkuId(skuId);
            item.setOrderedQuantity(quantity);
            item.setManualPriceOverride(true);
            item.setUnitPrice(price);
            item.setOverrideReason("F1-2C IT 协议价");
            return item;
        }

        private List<Long> orderItemIds(Long orderId) {
            return jdbc.queryForList(
                    "SELECT id FROM xsy_sales_order_item WHERE order_id = ? AND deleted = FALSE ORDER BY id",
                    Long.class, orderId);
        }

        /** 已确认订单：下单 → 提交 → 逐行实数量 → 确认（非标品的 confirm 前置）。 */
        private Long confirmedOrder(String tag, Long customerId, List<SalesOrderItemForm> lines,
                                    String actualQuantity) {
            return confirmedOrder(tag, customerId, lines, actualQuantity, null);
        }

        private Long confirmedOrder(String tag, Long customerId, List<SalesOrderItemForm> lines,
                                    String actualQuantity, Long couponInstanceId) {
            var form = new SalesOrderAddForm();
            form.setCustomerId(customerId);
            form.setOrderSource("ADMIN");
            form.setRemark("F1-2C IT 订单");
            var address = new OrderAddressForm();
            address.setReceiverName("F1-2C IT");
            address.setReceiverPhone("13800000000");
            address.setAddress("F1-2C IT 地址");
            form.setAddress(address);
            form.setItems(new ArrayList<>(lines));

            var scope = prefix + ":" + tag + ":so";
            SalesOrderDetailVO order = salesOrderService.create(form, scope + ":create");
            for (SalesOrderItemVO item : order.getItems()) {
                var actual = new OrderActualQuantityForm();
                actual.setOrderId(order.getOrderId());
                actual.setItemId(item.getItemId());
                actual.setVersion(item.getVersion());
                actual.setActualQuantity(actualQuantity);
                actual.setReason("F1-2C IT 实重");
                order = salesOrderService.actualQuantity(actual, scope + ":actual:" + item.getItemId());
            }
            var confirmation = new OrderConfirmForm();
            confirmation.setOrderId(order.getOrderId());
            confirmation.setVersion(order.getVersion());
            confirmation.setCouponInstanceId(couponInstanceId);
            Long orderId = salesOrderService.confirm(confirmation, scope + ":confirm").getOrderId();
            evictMybatisCache();
            return orderId;
        }

        private Chain plannedWithFixedAmountCoupon(String tag, String ordered, String price, String sorted,
                                                   String discountAmount) {
            Long warehouseId = locatedWarehouse(tag);
            Long skuId = newOnShelfSku(tag);
            stockIn(warehouseId, skuId, tag, "1000.0000");
            Long customerId = addressedCustomer(tag);
            Long couponInstanceId = issueFixedAmountCoupon(tag, customerId, discountAmount);
            Long orderId = confirmedOrder(tag, customerId, List.of(line(skuId, ordered, price)), ordered,
                    couponInstanceId);
            sortLines(orderId, List.of(sorted), warehouseId);
            Long routeId = newRoute(warehouseId);
            attach(routeId, orderId);
            locateAllStops(routeId);
            plan(routeId);
            Long orderItemId = confirmedSalesOrderItemId(orderId);
            return new Chain(routeId, warehouseId, skuId, customerId, orderId, orderItemId);
        }

        private Long issueFixedAmountCoupon(String tag, Long customerId, String discountAmount) {
            var coupon = new PromotionCouponForm();
            coupon.setCouponCode("RETURN-" + java.util.UUID.randomUUID());
            coupon.setCouponName("退货金额计算规则 " + tag);
            coupon.setDiscountType("AMOUNT");
            coupon.setDiscountValue(new BigDecimal(discountAmount));
            coupon.setMinOrderAmount(BigDecimal.ZERO);
            coupon.setValidFrom(OffsetDateTime.now().minusMinutes(1));
            coupon.setValidTo(OffsetDateTime.now().plusDays(1));
            Long couponId = promotionCouponService.save(coupon);

            var activation = new PromotionStatusForm();
            activation.setVersion(0);
            activation.setStatus("ACTIVE");
            promotionCouponService.updateStatus(couponId, activation);

            var issue = new PromotionCouponIssueForm();
            issue.setCouponId(couponId);
            issue.setCustomerId(customerId);
            issue.setQuantity(1);
            promotionCouponService.issue(issue, key("coupon-issue:" + tag));
            return jdbc.queryForObject(
                    "SELECT id FROM xsy_promotion_coupon_instance WHERE coupon_id = ? AND customer_id = ?",
                    Long.class, couponId, customerId);
        }

        private OrderVersionForm versionOf(SalesOrderDetailVO order) {
            var form = new OrderVersionForm();
            form.setOrderId(order.getOrderId());
            form.setVersion(order.getVersion());
            return form;
        }

        /** 建单并批准退货：一次请求一行数量、一次批准一行数量（0 批准量是合法的「这行不退」）。 */
        private OrderReturnDetailVO approvedReturn(String tag, Long orderId, List<Long> orderItemIds,
                                                   List<String> requested, List<String> approved) {
            var create = new OrderReturnAddForm();
            create.setOrderId(orderId);
            create.setReason("品质问题 " + tag);
            var rows = new ArrayList<OrderReturnItemForm>();
            for (int index = 0; index < orderItemIds.size(); index++) {
                var row = new OrderReturnItemForm();
                row.setOrderItemId(orderItemIds.get(index));
                row.setRequestedQuantity(requested.get(index));
                rows.add(row);
            }
            create.setItems(rows);
            OrderReturnDetailVO created = returns.create(create, key("return-create:" + tag));

            var approve = new OrderReturnApproveForm();
            approve.setReturnId(created.getReturnId());
            approve.setVersion(created.getVersion());
            var approvedRows = new ArrayList<OrderReturnApproveItemForm>();
            for (int index = 0; index < orderItemIds.size(); index++) {
                var row = new OrderReturnApproveItemForm();
                row.setOrderItemId(orderItemIds.get(index));
                row.setApprovedQuantity(approved.get(index));
                approvedRows.add(row);
            }
            approve.setItems(approvedRows);
            return returns.approve(approve, key("return-approve:" + tag));
        }

        private Long addressedCustomer(String tag) {
            Long customerId = newCustomer();
            jdbc.update("UPDATE xsy_customer SET address = ?, longitude = 113.94, latitude = 22.54,"
                    + " geom_crs = 'GCJ02' WHERE id = ?", "F1-2C IT 地址" + tag, customerId);
            evictMybatisCache();
            return customerId;
        }

        private Long locatedWarehouse(String tag) {
            Long warehouseId = newWarehouse(tag);
            jdbc.update("UPDATE xsy_warehouse SET longitude = 113.90, latitude = 22.50, geom_crs = 'GCJ02' WHERE id = ?",
                    warehouseId);
            evictMybatisCache();
            return warehouseId;
        }

        private void stockIn(Long warehouseId, Long skuId, String tag, String quantity) {
            Long supplierId = newPurchasableSupplier(tag, skuId);
            PurchaseOrderVO order = purchaseOrderService.create(
                    orderForm(supplierId, warehouseId, skuId, quantity, "6.2000"), prefix + ":" + tag + ":po");
            submitOrder(order.getId());
            confirmReceipt(createReceipt(order.getId()).getId(), quantity);
            evictMybatisCache();
        }

        /** 一个任务覆盖全部活动行并录入实发量后完成（配送资格硬前置：每条有效行都被已完成任务覆盖）。 */
        private void sortLines(Long orderId, List<String> sortedQuantities, Long warehouseId) {
            List<Long> itemIds = orderItemIds(orderId);
            var create = new SortingTaskCreateForm();
            create.setWarehouseId(warehouseId);
            create.setAssigneeEmployeeId(1L);
            create.setRemark("F1-2C 前置夹具");
            create.setSalesOrderItemIds(new ArrayList<>(itemIds));
            var detail = sortingTaskService.create(create, key("sort"));

            var entries = new ArrayList<SortingEntryItemForm>();
            for (int index = 0; index < detail.getItems().size(); index++) {
                var row = detail.getItems().get(index);
                var entry = new SortingEntryItemForm();
                entry.setId(row.getId());
                entry.setVersion(row.getVersion());
                entry.setSortedQuantity(new BigDecimal(sortedQuantities.get(index)));
                entry.setResult("NORMAL");
                entries.add(entry);
            }
            var entryForm = new SortingEntryForm();
            entryForm.setItems(entries);
            Long taskId = detail.getTask().getId();
            sortingTaskService.enter(taskId, entryForm);
            var action = new SortingActionForm();
            action.setVersion(jdbc.queryForObject("SELECT version FROM xsy_sorting_task WHERE id = ?",
                    Integer.class, taskId));
            sortingTaskService.complete(taskId, action);
            evictMybatisCache();
        }

        private Long newRoute(Long warehouseId) {
            var form = new DeliveryRouteForm();
            form.setRouteName("F1-2C IT 线路");
            form.setDeliveryDate(LocalDate.now());
            form.setWarehouseId(warehouseId);
            return routeService.create(form);
        }

        private void attach(Long routeId, Long orderId) {
            var form = new DeliveryOrdersForm();
            form.setVersion(versionOf(routeId));
            form.setOrderIds(List.of(orderId));
            form.setReason("组单");
            routeService.addOrders(routeId, form);
        }

        private void locateAllStops(Long routeId) {
            jdbc.update("UPDATE xsy_delivery_route_stop SET longitude = 113.95, latitude = 22.55, geom_crs = 'GCJ02'"
                    + " WHERE route_id = ? AND deleted = FALSE", routeId);
            evictMybatisCache();
        }

        private void plan(Long routeId) {
            routeService.plan(routeId, versionForm(routeId));
        }

        private void dispatch(Long routeId) {
            routeService.dispatch(routeId, versionForm(routeId), key("dispatch"));
        }

        private void sign(Long routeId, Long orderId, String result, String reason) {
            var form = new DeliverySignForm();
            form.setVersion(jdbc.queryForObject(
                    "SELECT version FROM xsy_delivery_route_order WHERE route_id = ? AND order_id = ? AND deleted = FALSE",
                    Integer.class, routeId, orderId));
            form.setResult(result);
            form.setReason(reason);
            routeService.sign(routeId, orderId, form);
            evictMybatisCache();
        }

        private DeliveryVersionForm versionForm(Long routeId) {
            var form = new DeliveryVersionForm();
            form.setVersion(versionOf(routeId));
            return form;
        }

        private Integer versionOf(Long routeId) {
            return jdbc.queryForObject("SELECT version FROM xsy_delivery_route WHERE id = ?", Integer.class, routeId);
        }

        private String key(String tag) {
            return prefix + ":" + tag + ":" + UUID.randomUUID();
        }

        private Long assignmentId(Long routeId, Long orderId) {
            return jdbc.queryForObject(
                    "SELECT id FROM xsy_delivery_route_order WHERE route_id = ? AND order_id = ? AND deleted = FALSE",
                    Long.class, routeId, orderId);
        }

        // ------------------------------------------------------------------
        // 断言辅助
        // ------------------------------------------------------------------

        private Map<String, Object> normalOf(Long orderId) {
            return single("SELECT * FROM xsy_finance_receivable WHERE source_type = 'SALES_ORDER'"
                    + " AND entry_type = 'NORMAL' AND source_id = ?", orderId);
        }

        private Map<String, Object> redOf(Long returnId) {
            return single("SELECT * FROM xsy_finance_receivable WHERE source_type = 'ORDER_RETURN'"
                    + " AND entry_type = 'RED' AND source_id = ?", returnId);
        }

        private Map<String, Object> single(String sql, Object id) {
            List<Map<String, Object>> rows = jdbc.queryForList(sql, id);
            assertThat(rows).as("SQL %s 参数 %s 应恰好一行", sql, id).hasSize(1);
            return rows.getFirst();
        }

        private int count(String sql, Object... args) {
            Integer value = jdbc.queryForObject(sql, Integer.class, args);
            return value == null ? 0 : value;
        }

        private int redsOfOrder(Long orderId) {
            return count("SELECT count(*) FROM xsy_finance_receivable WHERE entry_type = 'RED' AND order_id = ?",
                    orderId);
        }

        private List<Map<String, Object>> itemsOf(Long receivableId) {
            return jdbc.queryForList(
                    "SELECT * FROM xsy_finance_receivable_item WHERE receivable_id = ? ORDER BY id", receivableId);
        }

        private List<Map<String, Object>> logsOf(Long receivableId) {
            return jdbc.queryForList(
                    "SELECT * FROM xsy_finance_operation_log WHERE business_type = 'RECEIVABLE' AND business_id = ?"
                            + " ORDER BY id", receivableId);
        }

        private boolean sameAs(String expression, Object... args) {
            Boolean result = jdbc.queryForObject("SELECT " + expression, Boolean.class, args);
            return Boolean.TRUE.equals(result);
        }

        private static Long longOf(Map<String, Object> row, String column) {
            return ((Number) row.get(column)).longValue();
        }

        private static BigDecimal decimalOf(Map<String, Object> row, String column) {
            return (BigDecimal) row.get(column);
        }

        // ------------------------------------------------------------------
        // A / E. 先签后退：红字正常生成并挂对原应收
        // ------------------------------------------------------------------

        @Test
        @DisplayName("优惠退货：退款单金额按已确认优惠净额计算，与红字应收一致")
        void refundAmountUsesTheFrozenOrderDiscount() {
            Chain chain = plannedWithFixedAmountCoupon("RDN", "1.0000", "100.0000", "1.0000", "10.0000");
            BigDecimal frozenDiscount = jdbc.queryForObject(
                    "SELECT discount_amount FROM xsy_order_discount WHERE sales_order_id = ?",
                    BigDecimal.class, chain.orderId());
            assertThat(frozenDiscount).isEqualByComparingTo("10.0000");

            OrderReturnDetailVO returned = approvedReturn("RDN", chain.orderId(), List.of(chain.orderItemId()),
                    List.of("1.0000"), List.of("1.0000"));
            BigDecimal refundAmount = jdbc.queryForObject(
                    "SELECT refund_amount FROM xsy_order_refund WHERE return_id = ?", BigDecimal.class,
                    returned.getReturnId());

            assertThat(returned.getApprovedAmount()).isEqualByComparingTo("100.0000");
            assertThat(refundAmount).as("正常应收尚未生成时，退款单也按已已确认优惠折算净额")
                    .isEqualByComparingTo("90.0000");
            assertThat(redsOfOrder(chain.orderId())).as("先退后签时不生成孤立红字").isZero();

            dispatch(chain.routeId());
            sign(chain.routeId(), chain.orderId(), "SIGNED", null);

            Map<String, Object> red = redOf(returned.getReturnId());
            assertThat(decimalOf(red, "amount")).isLessThan(new BigDecimal("100.0000"));
            assertThat(refundAmount).as("签收补生成的红字金额必须与已建退款单逐值一致")
                    .isEqualByComparingTo(decimalOf(red, "amount"));
            assertThat(decimalOf(itemsOf(longOf(red, "id")).getFirst(), "discount_amount"))
                    .isEqualByComparingTo(frozenDiscount);
        }

        @Test
        @DisplayName("优惠抵满订单金额：允许退货、无需退款，不生成 0 元退款单")
        void fullyDiscountedReturnApprovesWithoutRefund() {
            Chain chain = plannedWithFixedAmountCoupon("RDF", "1.0000", "100.0000", "1.0000", "100.0000");

            OrderReturnDetailVO returned = approvedReturn("RDF", chain.orderId(), List.of(chain.orderItemId()),
                    List.of("1.0000"), List.of("1.0000"));

            assertThat(returned.getStatus()).as("退款净额为 0 不阻塞退货批准").isEqualTo("APPROVED");
            assertThat(returned.getApprovedAmount()).as("审批金额仍是退货毛额，不退化成净额").isEqualByComparingTo("100.0000");
            assertThat(count("SELECT count(*) FROM xsy_order_refund WHERE return_id = ?", returned.getReturnId()))
                    .as("没有可退的钱就不建退款单，order_refund.refund_amount 恒 > 0").isZero();
            assertThat(count("SELECT count(*) FROM xsy_order_operation_log WHERE order_id = ? AND operation_type = 'RETURN'"
                    + " AND reason LIKE '%无需退款%'", chain.orderId())).as("无需退款仍要留痕").isEqualTo(1);
            assertThat(redsOfOrder(chain.orderId())).as("净额为 0 的退货不生成红字").isZero();
        }

        @Test
        @DisplayName("先签后退：RED 挂原 NORMAL，对方与保存的数据继承原应收，时点与原因继承退货记录")
        void returnApprovedAfterSignGeneratesRedReceivable() {
            Chain chain = planned("RDA", "10.0000", "3.5000", "10.0000");
            dispatch(chain.routeId());
            sign(chain.routeId(), chain.orderId(), "SIGNED", null);
            Map<String, Object> normal = normalOf(chain.orderId());

            OrderReturnDetailVO returned = approvedReturn("RDA", chain.orderId(), List.of(chain.orderItemId()),
                    List.of("4.0000"), List.of("4.0000"));

            Map<String, Object> red = redOf(returned.getReturnId());
            assertThat(red.get("entry_type")).isEqualTo("RED");
            assertThat(red.get("source_type")).isEqualTo("ORDER_RETURN");
            assertThat(longOf(red, "source_id")).isEqualTo(returned.getReturnId());
            assertThat(longOf(red, "order_id")).isEqualTo(chain.orderId());
            assertThat(longOf(red, "original_receivable_id")).isEqualTo(longOf(normal, "id"));
            assertThat(longOf(red, "customer_id")).isEqualTo(longOf(normal, "customer_id"));
            assertThat(red.get("customer_name_snapshot")).isEqualTo(normal.get("customer_name_snapshot"));
            assertThat(decimalOf(red, "amount")).as("4 × 3.5").isEqualByComparingTo("14.0000");
            assertThat(red.get("reason")).isEqualTo(jdbc.queryForObject(
                    "SELECT reason FROM xsy_order_return WHERE id = ?", String.class, returned.getReturnId()));
            assertThat(sameAs("r.event_at = rt.approved_at FROM xsy_finance_receivable r"
                            + " JOIN xsy_order_return rt ON rt.id = ? WHERE r.id = ?",
                    returned.getReturnId(), longOf(red, "id")))
                    .as("红字时点必须逐值等于退货批准时刻").isTrue();
            assertThat(sameAs("r.created_by = rt.updated_by FROM xsy_finance_receivable r"
                            + " JOIN xsy_order_return rt ON rt.id = ? WHERE r.id = ?",
                    returned.getReturnId(), longOf(red, "id")))
                    .as("红字创建人必须是批准人本人（order_return.updated_by）").isTrue();

            // 原正常应收一字未改：红字是追加的反向记录，不修改原记录
            assertThat(decimalOf(normalOf(chain.orderId()), "amount"))
                    .isEqualByComparingTo(decimalOf(normal, "amount"));
            assertThat(longOf(normalOf(chain.orderId()), "version")).isEqualTo(longOf(normal, "version"));

            Map<String, Object> line = itemsOf(longOf(red, "id")).getFirst();
            assertThat(line.get("source_type")).isEqualTo("ORDER_RETURN_ITEM");

            List<Map<String, Object>> logs = logsOf(longOf(red, "id"));
            assertThat(logs).hasSize(1);
            assertThat(logs.getFirst().get("operation_type")).isEqualTo("RED_GENERATE");
            assertThat(logs.getFirst().get("before_data")).isNull();
            assertThat(logs.getFirst().get("reason")).isEqualTo(red.get("reason"));
            // 操作人必须追溯到本次批准的人（order_return.updated_by），不是财务侧现取的请求身份。
            // 谓词必须带 business_type：business_id 只在同一 business_type 内唯一（各记录表各有自己的
            // id 序列），只按 business_id 查会把同号的 PAYABLE / RECEIPT 日志一起捞出来。
            assertThat(sameAs("l.operator = rt.updated_by FROM xsy_finance_operation_log l"
                            + " JOIN xsy_order_return rt ON rt.id = ?"
                            + " WHERE l.business_type = 'RECEIVABLE' AND l.business_id = ?",
                    returned.getReturnId(), longOf(red, "id")))
                    .as("红字日志的操作人必须是批准人本人").isTrue();
            assertThat(String.valueOf(logs.getFirst().get("after_data")))
                    .contains("originalReceivableId").contains("RED").contains("sourceReturnId");
        }

        // ------------------------------------------------------------------
        // B / L. 先退后签：批准时跳过，签收时补生成（可多张）
        // ------------------------------------------------------------------

        @Test
        @DisplayName("先退后签：批准时零红字，签收后自动补生成；两张退货先于签收也全部补齐")
        void returnApprovedBeforeSignIsBackfilledWhenSigning() {
            Chain chain = planned("RDB", "10.0000", "3.5000", "10.0000");

            // 退货批准发生在签收之前
            OrderReturnDetailVO first = approvedReturn("RDB-1", chain.orderId(), List.of(chain.orderItemId()),
                    List.of("4.0000"), List.of("4.0000"));
            OrderReturnDetailVO second = approvedReturn("RDB-2", chain.orderId(), List.of(chain.orderItemId()),
                    List.of("6.0000"), List.of("6.0000"));

            // 批准成功、退款单已成立，但财务侧什么都没记（不造孤立红字）
            assertThat(first.getStatus()).isEqualTo("APPROVED");
            assertThat(second.getStatus()).isEqualTo("APPROVED");
            assertThat(count("SELECT count(*) FROM xsy_finance_receivable WHERE entry_type = 'RED' AND order_id = ?",
                    chain.orderId()))
                    .as("没有正常应收时不得生成孤立红字").isZero();
            assertThat(count("SELECT count(*) FROM xsy_order_refund WHERE order_id = ?", chain.orderId()))
                    .as("退款单是订单域记录，照常两张").isEqualTo(2);

            dispatch(chain.routeId());
            sign(chain.routeId(), chain.orderId(), "SIGNED", null);

            assertThat(redsOfOrder(chain.orderId())).as("签收补生成两张红字").isEqualTo(2);
            Map<String, Object> normal = normalOf(chain.orderId());
            Map<String, Object> redOne = redOf(first.getReturnId());
            Map<String, Object> redTwo = redOf(second.getReturnId());
            assertThat(longOf(redOne, "original_receivable_id")).isEqualTo(longOf(normal, "id"));
            assertThat(longOf(redTwo, "original_receivable_id")).isEqualTo(longOf(normal, "id"));
            assertThat(longOf(redOne, "id")).isNotEqualTo(longOf(redTwo, "id"));
            assertThat(decimalOf(redOne, "amount")).isEqualByComparingTo("14.0000");
            assertThat(decimalOf(redTwo, "amount")).isEqualByComparingTo("21.0000");
            assertThat(logsOf(longOf(redOne, "id"))).hasSize(1);
            assertThat(logsOf(longOf(redTwo, "id"))).hasSize(1);
        }

        // ------------------------------------------------------------------
        // F. 红字金额完全采用订单域已写入数据库的 approved_amount（不重算）
        // ------------------------------------------------------------------

        @Test
        @DisplayName("金额不重算：红字逐值等于 order_return_item.approved_amount，单价与数量另各归一列")
        void redAmountIsTheStoredApprovedAmountNotARecomputation() {
            Chain chain = planned("RDF", "10.0000", "3.5000", "10.0000");
            dispatch(chain.routeId());
            sign(chain.routeId(), chain.orderId(), "SIGNED", null);
            OrderReturnDetailVO returned = approvedReturn("RDF", chain.orderId(), List.of(chain.orderItemId()),
                    List.of("4.0000"), List.of("4.0000"));
            Long redId = longOf(redOf(returned.getReturnId()), "id");
            Long returnItemId = jdbc.queryForObject(
                    "SELECT id FROM xsy_order_return_item WHERE return_id = ?", Long.class, returned.getReturnId());

            // 判别性夹具：把 approved_amount 改成一个「与 quantity × price 不等」的合法值（CHECK 只要求 >= 0），
            // 再删掉本次红字并重跑生成器。重算实现会得到 14.0000，采用写入数据库值得到 9.9900。
            assertThat(jdbc.update("UPDATE xsy_order_return_item SET approved_amount = 9.9900 WHERE id = ?",
                    returnItemId)).isEqualTo(1);
            jdbc.update("DELETE FROM xsy_finance_operation_log WHERE business_type = 'RECEIVABLE' AND business_id = ?",
                    redId);
            jdbc.update("DELETE FROM xsy_finance_receivable_item WHERE receivable_id = ?", redId);
            assertThat(jdbc.update("DELETE FROM xsy_finance_receivable WHERE id = ?", redId)).isEqualTo(1);
            evictMybatisCache();

            financeReceivableService.generateRedOnReturnApproved(returned.getReturnId());

            Map<String, Object> line = itemsOf(longOf(redOf(returned.getReturnId()), "id")).getFirst();
            assertThat(decimalOf(line, "amount")).as("必须取写入数据库的 approved_amount，不是乘出来的 14.0000")
                    .isEqualByComparingTo("9.9900");
            assertThat(decimalOf(line, "quantity")).isEqualByComparingTo("4.0000");
            assertThat(decimalOf(line, "unit_price")).isEqualByComparingTo("3.5000");
            assertThat(sameAs("i.amount = ri.approved_amount FROM xsy_finance_receivable_item i"
                            + " JOIN xsy_order_return_item ri ON ri.id = i.source_id WHERE i.id = ?",
                    line.get("id"))).as("红字明细金额与订单域写入数据库金额逐值相同").isTrue();
        }

        // ------------------------------------------------------------------
        // G. 合法的 0 批准行只跳过该行，正金额行照常成行
        // ------------------------------------------------------------------

        @Test
        @DisplayName("两行退货（一行 0 批准量）：0 行不成明细，正金额行正常生成，单头为行之和")
        void zeroApprovedLineIsSkippedPerLine() {
            Long skuA = newOnShelfSku("RDG-A");
            Long skuB = newOnShelfSku("RDG-B");
            Chain chain = plannedTwoLines("RDG", skuA, "3.5000", skuB, "8.0000", "10.0000", "10.0000");
            dispatch(chain.routeId());
            sign(chain.routeId(), chain.orderId(), "SIGNED", null);

            List<Long> itemIds = orderItemIds(chain.orderId());
            OrderReturnDetailVO returned = approvedReturn("RDG", chain.orderId(), itemIds,
                    List.of("4.0000", "2.0000"), List.of("4.0000", "0.0000"));

            Map<String, Object> red = redOf(returned.getReturnId());
            List<Map<String, Object>> lines = itemsOf(longOf(red, "id"));
            assertThat(lines).as("0 批准量不成行，只留正金额那一行").hasSize(1);
            assertThat(decimalOf(lines.getFirst(), "quantity")).isEqualByComparingTo("4.0000");
            assertThat(decimalOf(red, "amount")).as("14.0000 + 0").isEqualByComparingTo("14.0000");
            // 行级来源指向那条正金额的退货行；0 批准行不占用来源唯一键
            assertThat(longOf(lines.getFirst(), "source_id")).isEqualTo(jdbc.queryForObject(
                    "SELECT id FROM xsy_order_return_item WHERE return_id = ? AND approved_quantity > 0",
                    Long.class, returned.getReturnId()));
        }

        // ------------------------------------------------------------------
        // H. 已核销不影响红字生成
        // ------------------------------------------------------------------

        @Test
        @DisplayName("D-2：NORMAL 100 已全额核销，退货批准 20 仍生成 RED 20，approve 不被阻塞")
        void writtenOffReceivableDoesNotBlockRedGeneration() {
            Chain chain = planned("RDH", "100.0000", "1.0000", "100.0000");
            dispatch(chain.routeId());
            sign(chain.routeId(), chain.orderId(), "SIGNED", null);
            Map<String, Object> normal = normalOf(chain.orderId());
            assertThat(decimalOf(normal, "amount")).isEqualByComparingTo("100.0000");

            // 受控夹具：按 V65 schema 直接落一条合法核销行
            //（source_id 不指向真实收款单，本用例只需要「存在一笔已核销额」这一判据）。
            jdbc.update("INSERT INTO xsy_finance_write_off (write_off_no, source_type, source_id, target_type,"
                            + " target_id, amount, entry_type, written_off_at, operator, created_at, updated_at)"
                            + " VALUES (?, 'RECEIPT', 900001, 'RECEIVABLE', ?, 100.0000, 'NORMAL',"
                            + " now(), '2:1', now(), now())",
                    "WO-FIXTURE-" + chain.orderId(), longOf(normal, "id"));
            evictMybatisCache();

            OrderReturnDetailVO returned = approvedReturn("RDH", chain.orderId(), List.of(chain.orderItemId()),
                    List.of("20.0000"), List.of("20.0000"));

            Map<String, Object> red = redOf(returned.getReturnId());
            assertThat(decimalOf(red, "amount")).as("已核销 100 不抵扣可生成额度（D-2）")
                    .isEqualByComparingTo("20.0000");
            // 财务记录净额可直接用 SQL 读出，不经过读侧派生
            assertThat(jdbc.queryForObject(
                    "SELECT sum(CASE WHEN entry_type = 'NORMAL' THEN amount ELSE -amount END)"
                            + " FROM xsy_finance_receivable WHERE order_id = ?", BigDecimal.class, chain.orderId()))
                    .as("净应收 = 80").isEqualByComparingTo("80.0000");
            assertThat(returned.getStatus()).isEqualTo("APPROVED");
        }

        // ------------------------------------------------------------------
        // I. 红字大于正常应收也全额生成
        // ------------------------------------------------------------------

        @Test
        @DisplayName("D-4：少拣导致退货批准额 50 大于应收 30 → RED 全额 50，不封顶、不抛 41137")
        void redLargerThanNormalIsGeneratedInFull() {
            // 下单 10，确认时订单实发量也是 10：此时批准退货 10 是合法的（可退上限 = 实发量）。
            // 随后分拣只出 6，分拣称重回写把订单实发量改成 6，正常应收按出库量生成 6 × 5.0000 = 30；
            // 已批准的退货仍是 10 × 5.0000 = 50 —— 红字不因正常应收后来变小而封顶。
            Long warehouseId = locatedWarehouse("RDI");
            Long skuId = newOnShelfSku("RDI");
            stockIn(warehouseId, skuId, "RDI", "1000.0000");
            Long customerId = addressedCustomer("RDI");
            Long orderId = confirmedOrder("RDI", customerId, List.of(line(skuId, "10.0000", "5.0000")), "10.0000");
            Long orderItemId = confirmedSalesOrderItemId(orderId);

            OrderReturnDetailVO returned = approvedReturn("RDI", orderId, List.of(orderItemId),
                    List.of("10.0000"), List.of("10.0000"));

            sortLines(orderId, List.of("6.0000"), warehouseId);
            Long routeId = newRoute(warehouseId);
            attach(routeId, orderId);
            locateAllStops(routeId);
            plan(routeId);
            dispatch(routeId);
            sign(routeId, orderId, "SIGNED", null);
            assertThat(decimalOf(normalOf(orderId), "amount")).isEqualByComparingTo("30.0000");

            Map<String, Object> red = redOf(returned.getReturnId());
            assertThat(decimalOf(red, "amount")).as("超额红字必须全额保留").isEqualByComparingTo("50.0000");
            assertThat(jdbc.queryForObject(
                    "SELECT sum(CASE WHEN entry_type = 'NORMAL' THEN amount ELSE -amount END)"
                            + " FROM xsy_finance_receivable WHERE order_id = ?", BigDecimal.class, orderId))
                    .as("允许净应收为负（D-4），表达属 F1-5 读侧派生").isEqualByComparingTo("-20.0000");
            // 订单域记录未被财务改动
            assertThat(jdbc.queryForObject(
                    "SELECT approved_amount FROM xsy_order_return WHERE id = ?", BigDecimal.class,
                    returned.getReturnId())).isEqualByComparingTo("50.0000");
            assertThat(jdbc.queryForObject(
                    "SELECT approved_quantity FROM xsy_order_return_item WHERE return_id = ?", BigDecimal.class,
                    returned.getReturnId())).isEqualByComparingTo("10.0000");
        }

        // ------------------------------------------------------------------
        // D. 重放不产生第二张红字 / 第二份日志
        // ------------------------------------------------------------------

        @Test
        @DisplayName("重复触发：同一退货被再生成一次 → 仍是一张红字、一份 RED_GENERATE 日志")
        void replayKeepsSingleRedReceivableAndSingleLog() {
            Chain chain = planned("RDD", "10.0000", "3.5000", "10.0000");
            dispatch(chain.routeId());
            sign(chain.routeId(), chain.orderId(), "SIGNED", null);
            OrderReturnDetailVO returned = approvedReturn("RDD", chain.orderId(), List.of(chain.orderItemId()),
                    List.of("4.0000"), List.of("4.0000"));
            Long redId = longOf(redOf(returned.getReturnId()), "id");

            financeReceivableService.generateRedOnReturnApproved(returned.getReturnId());
            // 签收派生整体重放：正常应收与红字都必须收敛回原状，不重复插、不重复留痕
            financeReceivableService.generateOnSign(assignmentId(chain.routeId(), chain.orderId()));

            assertThat(longOf(redOf(returned.getReturnId()), "id")).as("重放不换单不换号").isEqualTo(redId);
            assertThat(count("SELECT count(*) FROM xsy_finance_receivable WHERE source_type = 'ORDER_RETURN'"
                    + " AND source_id = ?", returned.getReturnId())).isEqualTo(1);
            assertThat(itemsOf(redId)).hasSize(1);
            assertThat(logsOf(redId)).as("红字重放不重复留痕").hasSize(1);
            assertThat(count("SELECT count(*) FROM xsy_finance_receivable WHERE source_type = 'SALES_ORDER'"
                    + " AND source_id = ?", chain.orderId())).isEqualTo(1);
            assertThat(logsOf(longOf(normalOf(chain.orderId()), "id"))).hasSize(1);
        }

        // ------------------------------------------------------------------
        // K. NORMAL 生成器重放能修复漏掉的红字
        // ------------------------------------------------------------------

        @Test
        @DisplayName("漏账修复：NORMAL 已存在而红字缺失时，重跑 generateOnSign 就把它补回来")
        void signGeneratorReplayRepairsAMissingRed() {
            Chain chain = planned("RDK", "10.0000", "3.5000", "10.0000");
            dispatch(chain.routeId());
            sign(chain.routeId(), chain.orderId(), "SIGNED", null);
            OrderReturnDetailVO returned = approvedReturn("RDK", chain.orderId(), List.of(chain.orderItemId()),
                    List.of("4.0000"), List.of("4.0000"));
            Long redId = longOf(redOf(returned.getReturnId()), "id");

            // 构造「红字丢了」的状态（例如那条派生事务被回滚掉，而两个记录都还在）
            jdbc.update("DELETE FROM xsy_finance_operation_log WHERE business_type = 'RECEIVABLE' AND business_id = ?",
                    redId);
            jdbc.update("DELETE FROM xsy_finance_receivable_item WHERE receivable_id = ?", redId);
            assertThat(jdbc.update("DELETE FROM xsy_finance_receivable WHERE id = ?", redId)).isEqualTo(1);
            evictMybatisCache();
            assertThat(redsOfOrder(chain.orderId())).isZero();

            financeReceivableService.generateOnSign(assignmentId(chain.routeId(), chain.orderId()));

            assertThat(redsOfOrder(chain.orderId())).as("派生生成器可重放即自然收敛").isEqualTo(1);
            Map<String, Object> red = redOf(returned.getReturnId());
            assertThat(decimalOf(red, "amount")).isEqualByComparingTo("14.0000");
            assertThat(decimalOf(normalOf(chain.orderId()), "amount")).as("正常应收不被改动").
                    isEqualByComparingTo("35.0000");
            assertThat(count("SELECT count(*) FROM xsy_finance_receivable WHERE source_type = 'SALES_ORDER'"
                    + " AND source_id = ?", chain.orderId())).isEqualTo(1);
        }

        // ------------------------------------------------------------------
        // M. 异常签收不补红字
        // ------------------------------------------------------------------

        @Test
        @DisplayName("EXCEPTION 签收 + 已有批准退货：不造 NORMAL，因此也不造孤立红字")
        void exceptionSignNeverCreatesNormalNorOrphanRed() {
            Chain chain = planned("RDM", "10.0000", "3.5000", "10.0000");
            dispatch(chain.routeId());
            OrderReturnDetailVO returned = approvedReturn("RDM", chain.orderId(), List.of(chain.orderItemId()),
                    List.of("4.0000"), List.of("4.0000"));

            sign(chain.routeId(), chain.orderId(), "EXCEPTION", "客户拒收");

            assertThat(count("SELECT count(*) FROM xsy_finance_receivable WHERE order_id = ?", chain.orderId()))
                    .as("异常签收不形成应收（第二批 Q6）").isZero();
            assertThat(count("SELECT count(*) FROM xsy_finance_receivable WHERE source_type = 'ORDER_RETURN'"
                    + " AND source_id = ?", returned.getReturnId()))
                    .as("没有正常应收就没有可挂的红字").isZero();
            assertThat(returned.getStatus()).isEqualTo("APPROVED");
        }
    }

    @Nested
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("签收与退货批准并发下的红字收敛（F1-2C，PG IT，无外层事务）")
    class ScmFinanceReceivableRedRacePgIT extends ScmW6PgITBase {
        @Autowired
        private DeliveryRouteService routeService;

        @Autowired
        private OrderReturnService returns;

        @Override
        protected void evictMybatisCache() {
            // 无外层事务 → 每次 DAO 调用都是新 session → 一级缓存天然为空
        }

        private record Prepared(Long routeId, Long warehouseId, Long orderId, Long orderItemId,
                                Long returnId, Integer returnVersion, Integer signVersion) {
        }

        /** 真实链路建到「已发车在途 + 退货单待批准」，两条竞争事务的前置全部已提交。 */
        private Prepared prepared(String tag) {
            Long warehouseId = newWarehouse(tag);
            jdbc.update("UPDATE xsy_warehouse SET longitude = 113.90, latitude = 22.50, geom_crs = 'GCJ02' WHERE id = ?",
                    warehouseId);
            Long skuId = newOnShelfSku(tag);
            Long supplierId = newPurchasableSupplier(tag, skuId);
            PurchaseOrderVO purchase = purchaseOrderService.create(
                    orderForm(supplierId, warehouseId, skuId, "1000.0000", "6.2000"), prefix + ":" + tag + ":po");
            submitOrder(purchase.getId());
            confirmReceipt(createReceipt(purchase.getId()).getId(), "1000.0000");

            Long customerId = newCustomer();
            jdbc.update("UPDATE xsy_customer SET address = ?, longitude = 113.94, latitude = 22.54,"
                    + " geom_crs = 'GCJ02' WHERE id = ?", "F1-2C 并发地址", customerId);
            Long orderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
            Long orderItemId = confirmedSalesOrderItemId(orderId);

            var createTask = new SortingTaskCreateForm();
            createTask.setWarehouseId(warehouseId);
            createTask.setAssigneeEmployeeId(1L);
            createTask.setRemark("F1-2C 并发前置");
            createTask.setSalesOrderItemIds(new ArrayList<>(List.of(orderItemId)));
            var detail = sortingTaskService.create(createTask, key("sort"));
            var entry = new SortingEntryItemForm();
            entry.setId(detail.getItems().getFirst().getId());
            entry.setVersion(detail.getItems().getFirst().getVersion());
            entry.setSortedQuantity(new BigDecimal("10.0000"));
            entry.setResult("NORMAL");
            var entryForm = new SortingEntryForm();
            entryForm.setItems(new ArrayList<>(List.of(entry)));
            Long taskId = detail.getTask().getId();
            sortingTaskService.enter(taskId, entryForm);
            var action = new SortingActionForm();
            action.setVersion(jdbc.queryForObject("SELECT version FROM xsy_sorting_task WHERE id = ?",
                    Integer.class, taskId));
            sortingTaskService.complete(taskId, action);

            var routeForm = new DeliveryRouteForm();
            routeForm.setRouteName("F1-2C 并发线路");
            routeForm.setDeliveryDate(LocalDate.now());
            routeForm.setWarehouseId(warehouseId);
            Long routeId = routeService.create(routeForm);
            var attach = new DeliveryOrdersForm();
            attach.setVersion(routeVersion(routeId));
            attach.setOrderIds(List.of(orderId));
            attach.setReason("组单");
            routeService.addOrders(routeId, attach);
            jdbc.update("UPDATE xsy_delivery_route_stop SET longitude = 113.95, latitude = 22.55, geom_crs = 'GCJ02'"
                    + " WHERE route_id = ? AND deleted = FALSE", routeId);
            routeService.plan(routeId, routeForm2(routeId));
            routeService.dispatch(routeId, routeForm2(routeId), key("dispatch"));

            var returnAdd = new OrderReturnAddForm();
            returnAdd.setOrderId(orderId);
            returnAdd.setReason("品质问题 并发");
            var returnLine = new OrderReturnItemForm();
            returnLine.setOrderItemId(orderItemId);
            returnLine.setRequestedQuantity("4.0000");
            returnAdd.setItems(List.of(returnLine));
            OrderReturnDetailVO created = returns.create(returnAdd, key("return-create"));

            Integer signVersion = jdbc.queryForObject(
                    "SELECT version FROM xsy_delivery_route_order WHERE route_id = ? AND order_id = ? AND deleted = FALSE",
                    Integer.class, routeId, orderId);
            return new Prepared(routeId, warehouseId, orderId, orderItemId, created.getReturnId(),
                    created.getVersion(), signVersion);
        }

        private Integer routeVersion(Long routeId) {
            return jdbc.queryForObject("SELECT version FROM xsy_delivery_route WHERE id = ?", Integer.class, routeId);
        }

        private DeliveryVersionForm routeForm2(Long routeId) {
            var form = new DeliveryVersionForm();
            form.setVersion(routeVersion(routeId));
            return form;
        }

        private String key(String tag) {
            return prefix + ":" + tag + ":" + UUID.randomUUID();
        }

        /**
         * 工作线程必须自带请求身份：{@code XsyRequestUtil} 是 ThreadLocal，
         * 而签收人与批准人要落进财务记录（{@code signed_by} / {@code updated_by}）。
         */
        private void login(String name) {
            var employee = new RequestEmployee();
            employee.setEmployeeId(1L);
            employee.setActualName(name);
            employee.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
            employee.setAdministratorFlag(true);
            XsyRequestUtil.setRequestUser(employee);
        }

        private void run(String name, Runnable body, AtomicReference<Throwable> failure,
                         CountDownLatch ready, CountDownLatch start, CountDownLatch done) {
            new Thread(() -> {
                login(name);
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

        @Test
        @DisplayName("两条事务同时冲同一订单：最终 1 张正常 + 1 张红字，无漏账无重复无死锁")
        void concurrentSignAndApprovalConvergeToCompleteFacts() throws Exception {
            Prepared prepared = prepared("RACE1");
            Long orderId = prepared.orderId();

            var signFailure = new AtomicReference<Throwable>();
            var approveFailure = new AtomicReference<Throwable>();
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var done = new CountDownLatch(2);

            var signForm = new DeliverySignForm();
            signForm.setVersion(prepared.signVersion());
            signForm.setResult("SIGNED");

            var approve = new OrderReturnApproveForm();
            approve.setReturnId(prepared.returnId());
            approve.setVersion(prepared.returnVersion());
            var approvedLine = new OrderReturnApproveItemForm();
            approvedLine.setOrderItemId(prepared.orderItemId());
            approvedLine.setApprovedQuantity("4.0000");
            approve.setItems(List.of(approvedLine));

            try {
                run("sign-thread",
                        () -> routeService.sign(prepared.routeId(), orderId, signForm),
                        signFailure, ready, start, done);
                run("approve-thread",
                        () -> returns.approve(approve, key("return-approve")),
                        approveFailure, ready, start, done);

                assertThat(ready.await(30, TimeUnit.SECONDS)).as("两个线程都要到达起跑线").isTrue();
                start.countDown();
                assertThat(done.await(60, TimeUnit.SECONDS)).as("两个事务必须在 60 秒内结束（否则疑似死锁等待）")
                        .isTrue();

                assertThat(signFailure.get()).as("签收侧不得失败（死锁会被 PostgreSQL 牺牲成异常）").isNull();
                assertThat(approveFailure.get()).as("批准侧不得失败").isNull();

                // 最终记录：一正一红，红字挂对原单、金额与来源正确
                Map<String, Object> normal = jdbc.queryForMap(
                        "SELECT * FROM xsy_finance_receivable WHERE source_type = 'SALES_ORDER'"
                                + " AND entry_type = 'NORMAL' AND source_id = ?", orderId);
                Map<String, Object> red = jdbc.queryForMap(
                        "SELECT * FROM xsy_finance_receivable WHERE source_type = 'ORDER_RETURN'"
                                + " AND entry_type = 'RED' AND source_id = ?", prepared.returnId());
                assertThat(((Number) red.get("original_receivable_id")).longValue())
                        .isEqualTo(((Number) normal.get("id")).longValue());
                assertThat((BigDecimal) red.get("amount")).as("4 × 1.2000").isEqualByComparingTo("4.8000");

                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_finance_receivable WHERE order_id = ?", Integer.class, orderId))
                        .as("恰好一张正常 + 一张红字").isEqualTo(2);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_finance_receivable_item WHERE receivable_id = ?",
                        Integer.class, ((Number) red.get("id")).longValue())).isEqualTo(1);

                // 两边都尝试过派生，但每条记录只落一次
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_finance_operation_log WHERE business_type = 'RECEIVABLE'"
                                + " AND business_id = ? AND operation_type = 'GENERATE'",
                        Integer.class, ((Number) normal.get("id")).longValue())).isEqualTo(1);
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_finance_operation_log WHERE business_type = 'RECEIVABLE'"
                                + " AND business_id = ? AND operation_type = 'RED_GENERATE'",
                        Integer.class, ((Number) red.get("id")).longValue())).isEqualTo(1);

                // 业务侧终态：签收、批准、退款单三件都在
                assertThat(jdbc.queryForObject(
                        "SELECT fulfillment_status FROM xsy_delivery_route_order WHERE route_id = ? AND order_id = ?"
                                + " AND deleted = FALSE", String.class, prepared.routeId(), orderId))
                        .isEqualTo("SIGNED");
                assertThat(jdbc.queryForObject("SELECT status FROM xsy_order_return WHERE id = ?", String.class,
                        prepared.returnId())).isEqualTo("APPROVED");
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_order_refund WHERE return_id = ? AND status = 'PENDING'",
                        Integer.class, prepared.returnId())).isEqualTo(1);
            } finally {
                disableWarehouse(prepared.warehouseId());
            }
        }
    }

    @Nested
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("红字生成失败与退货批准回滚（F1-2C，PG IT，无外层事务）")
    class ScmFinanceReceivableRedRollbackPgIT extends ScmW6PgITBase {
        @Autowired
        private DeliveryRouteService routeService;

        @Autowired
        private OrderReturnService returns;

        @Override
        protected void evictMybatisCache() {
            // 无外层事务 → 每次 DAO 调用都是新 session → 一级缓存天然为空
        }

        // ------------------------------------------------------------------
        // 夹具（真实链路：入库 → 下单 → 分拣 → 发车 → 签收 → 建退货单）
        // ------------------------------------------------------------------

        private Long locatedWarehouse(String tag) {
            Long warehouseId = newWarehouse(tag);
            jdbc.update("UPDATE xsy_warehouse SET longitude = 113.90, latitude = 22.50, geom_crs = 'GCJ02' WHERE id = ?",
                    warehouseId);
            return warehouseId;
        }

        private Long addressedCustomer(String tag) {
            Long customerId = newCustomer();
            jdbc.update("UPDATE xsy_customer SET address = ?, longitude = 113.94, latitude = 22.54,"
                    + " geom_crs = 'GCJ02' WHERE id = ?", "F1-2C rollback 地址" + tag, customerId);
            return customerId;
        }

        private void stockIn(Long warehouseId, Long skuId, String tag, String quantity) {
            Long supplierId = newPurchasableSupplier(tag, skuId);
            PurchaseOrderVO order = purchaseOrderService.create(
                    orderForm(supplierId, warehouseId, skuId, quantity, "6.2000"), prefix + ":" + tag + ":po");
            submitOrder(order.getId());
            confirmReceipt(createReceipt(order.getId()).getId(), quantity);
        }

        private void sortOneLine(Long orderId, String sortedQuantity, Long warehouseId) {
            var create = new SortingTaskCreateForm();
            create.setWarehouseId(warehouseId);
            create.setAssigneeEmployeeId(1L);
            create.setRemark("F1-2C rollback 前置");
            create.setSalesOrderItemIds(new ArrayList<>(List.of(confirmedSalesOrderItemId(orderId))));
            var detail = sortingTaskService.create(create, key("sort"));
            Long taskId = detail.getTask().getId();
            var entry = new SortingEntryItemForm();
            entry.setId(detail.getItems().getFirst().getId());
            entry.setVersion(detail.getItems().getFirst().getVersion());
            entry.setSortedQuantity(new BigDecimal(sortedQuantity));
            entry.setResult("NORMAL");
            var entries = new ArrayList<SortingEntryItemForm>();
            entries.add(entry);
            var entryForm = new SortingEntryForm();
            entryForm.setItems(entries);
            sortingTaskService.enter(taskId, entryForm);
            var action = new SortingActionForm();
            action.setVersion(jdbc.queryForObject("SELECT version FROM xsy_sorting_task WHERE id = ?",
                    Integer.class, taskId));
            sortingTaskService.complete(taskId, action);
        }

        private Integer versionOf(Long routeId) {
            return jdbc.queryForObject("SELECT version FROM xsy_delivery_route WHERE id = ?", Integer.class, routeId);
        }

        private DeliveryVersionForm versionForm(Long routeId) {
            var form = new DeliveryVersionForm();
            form.setVersion(versionOf(routeId));
            return form;
        }

        private String key(String tag) {
            return prefix + ":" + tag + ":" + UUID.randomUUID();
        }

        private Map<String, Object> row(String sql, Object... args) {
            return jdbc.queryForMap(sql, args);
        }

        private int count(String sql, Object... args) {
            Integer value = jdbc.queryForObject(sql, Integer.class, args);
            return value == null ? 0 : value;
        }

        // ------------------------------------------------------------------
        // 1. 红字明细撞来源唯一键 → 整笔退货批准回滚
        // ------------------------------------------------------------------

        @Test
        @DisplayName("退货行来源键被占：approve 抛错 → 退货仍 PENDING、无退款单、无日志、NORMAL 未变")
        void redGenerationFailureRollsBackTheWholeApproval() {
            Long warehouseId = locatedWarehouse("RC1");
            Long skuId = newOnShelfSku("RC1");
            stockIn(warehouseId, skuId, "RC1", "1000.0000");
            Long customerId = addressedCustomer("RC1");
            Long orderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
            Long orderItemId = confirmedSalesOrderItemId(orderId);
            sortOneLine(orderId, "10.0000", warehouseId);

            var routeForm = new DeliveryRouteForm();
            routeForm.setRouteName("F1-2C rollback 线路");
            routeForm.setDeliveryDate(LocalDate.now());
            routeForm.setWarehouseId(warehouseId);
            Long routeId = routeService.create(routeForm);
            var attach = new DeliveryOrdersForm();
            attach.setVersion(versionOf(routeId));
            attach.setOrderIds(List.of(orderId));
            attach.setReason("组单");
            routeService.addOrders(routeId, attach);
            jdbc.update("UPDATE xsy_delivery_route_stop SET longitude = 113.95, latitude = 22.55, geom_crs = 'GCJ02'"
                    + " WHERE route_id = ? AND deleted = FALSE", routeId);
            routeService.plan(routeId, versionForm(routeId));
            routeService.dispatch(routeId, versionForm(routeId), key("dispatch"));
            var sign = new DeliverySignForm();
            sign.setVersion(jdbc.queryForObject(
                    "SELECT version FROM xsy_delivery_route_order WHERE route_id = ? AND order_id = ? AND deleted = FALSE",
                    Integer.class, routeId, orderId));
            sign.setResult("SIGNED");
            routeService.sign(routeId, orderId, sign);

            // 前置记录：正常应收已成立（红字要挂在它上面）
            Map<String, Object> normal = row(
                    "SELECT * FROM xsy_finance_receivable WHERE source_type = 'SALES_ORDER' AND source_id = ?", orderId);
            Long normalId = ((Number) normal.get("id")).longValue();

            var create = new OrderReturnAddForm();
            create.setOrderId(orderId);
            create.setReason("品质问题 rollback");
            var returnLine = new OrderReturnItemForm();
            returnLine.setOrderItemId(orderItemId);
            returnLine.setRequestedQuantity("4.0000");
            create.setItems(List.of(returnLine));
            OrderReturnDetailVO created = returns.create(create, key("return-create"));
            Long returnItemId = jdbc.queryForObject(
                    "SELECT id FROM xsy_order_return_item WHERE return_id = ?", Long.class, created.getReturnId());

            // 先占掉这条退货行的红字来源键（模拟「同一退货行已被别的应收单记账」的数据异常）
            jdbc.update("INSERT INTO xsy_finance_receivable_item (receivable_id, source_type, source_id,"
                            + " order_item_id, sku_id, sku_name_snapshot, unit_snapshot,"
                            + " quantity, unit_price, amount, created_at, updated_at)"
                            + " VALUES (-1, 'ORDER_RETURN_ITEM', ?, ?, ?, '占位', 'kg', 1.0000, 1.0000, 1.0000,"
                            + " now(), now())",
                    returnItemId, orderItemId, skuId);

            try {
                var approve = new OrderReturnApproveForm();
                approve.setReturnId(created.getReturnId());
                approve.setVersion(created.getVersion());
                var approvedLine = new OrderReturnApproveItemForm();
                approvedLine.setOrderItemId(orderItemId);
                approvedLine.setApprovedQuantity("4.0000");
                approve.setItems(List.of(approvedLine));

                assertThatThrownBy(() -> returns.approve(approve, key("return-approve")))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("退货行已挂在别的应收单上");

                // 订单域回到批准前：状态、时点、批准量与金额、退款单、操作日志全部未写入数据库
                Map<String, Object> returnRow = row("SELECT * FROM xsy_order_return WHERE id = ?",
                        created.getReturnId());
                assertThat(returnRow.get("status")).isEqualTo("PENDING");
                assertThat(returnRow.get("approved_at")).isNull();
                assertThat((BigDecimal) returnRow.get("approved_amount")).isEqualByComparingTo("0.0000");
                assertThat(row("SELECT approved_quantity, approved_amount FROM xsy_order_return_item WHERE id = ?",
                        returnItemId).get("approved_quantity")).isNull();
                assertThat(count("SELECT count(*) FROM xsy_order_refund WHERE return_id = ?", created.getReturnId()))
                        .as("退款单与批准同事务，必须一起回滚").isZero();
                assertThat(count("SELECT count(*) FROM xsy_order_operation_log WHERE order_id = ?"
                        + " AND operation_type = 'RETURN'", orderId))
                        .as("只剩建单那一条日志，批准那条随事务回滚").isEqualTo(1);

                // 财务侧：没有半张红字、没有红字明细、没有红字日志；正常应收一字未改
                assertThat(count("SELECT count(*) FROM xsy_finance_receivable WHERE source_type = 'ORDER_RETURN'"
                        + " AND source_id = ?", created.getReturnId())).isZero();
                assertThat(count("SELECT count(*) FROM xsy_finance_receivable_item WHERE source_type ="
                        + " 'ORDER_RETURN_ITEM' AND source_id = ? AND receivable_id > 0", returnItemId)).isZero();
                assertThat(count("SELECT count(*) FROM xsy_finance_operation_log WHERE business_type ="
                        + " 'RECEIVABLE' AND operation_type = 'RED_GENERATE' AND business_id IN"
                        + " (SELECT id FROM xsy_finance_receivable WHERE order_id = ?)", orderId)).isZero();
                Map<String, Object> normalAfter = row(
                        "SELECT * FROM xsy_finance_receivable WHERE id = ?", normalId);
                assertThat(normalAfter.get("amount")).isEqualTo(normal.get("amount"));
                assertThat(normalAfter.get("version")).isEqualTo(normal.get("version"));
                assertThat(count("SELECT count(*) FROM xsy_finance_receivable_item WHERE receivable_id = ?", normalId))
                        .isEqualTo(1);

                // 签收与出库记录不受影响
                assertThat(row("SELECT fulfillment_status FROM xsy_delivery_route_order WHERE route_id = ?"
                                + " AND order_id = ? AND deleted = FALSE", routeId, orderId)
                        .get("fulfillment_status")).isEqualTo("SIGNED");
                assertThat(count("SELECT count(*) FROM xsy_inventory_movement WHERE movement_type = 'SALES_OUT'"
                        + " AND sku_id = ? AND deleted = FALSE", skuId)).isEqualTo(1);
            } finally {
                jdbc.update("DELETE FROM xsy_finance_receivable_item WHERE receivable_id = -1");
                disableWarehouse(warehouseId);
            }
        }
    }

    @Nested
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("应收生成失败与签收回滚（F1-2B，PG IT，无外层事务）")
    class ScmFinanceReceivableRollbackPgIT extends ScmW6PgITBase {
        @Autowired
        private DeliveryRouteService routeService;

        @Autowired
        private FinanceReceivableService financeReceivableService;

        @Override
        protected void evictMybatisCache() {
            // 无外层事务 → 每次 DAO 调用都是新 session → 一级缓存天然为空
        }

        private record Chain(Long routeId, Long warehouseId, Long skuId, Long orderId) {
        }

        /** 真实链路建到「已发车在途」，返回线路与订单 id。 */
        private Chain dispatchedChain(String tag) {
            Long warehouseId = locatedWarehouse(tag);
            Long skuId = newOnShelfSku(tag);
            stockIn(warehouseId, skuId, tag, "20.0000");
            Long customerId = newCustomer();
            jdbc.update("UPDATE xsy_customer SET address = ?, longitude = 113.94, latitude = 22.54,"
                    + " geom_crs = 'GCJ02' WHERE id = ?", "F1-2B rollback 地址", customerId);
            Long orderId = confirmedSalesOrder(customerId, skuId, "10.0000", "10.0000");
            sortOneLine(orderId, "10.0000", warehouseId);

            Long routeId = newRoute(warehouseId);
            attach(routeId, orderId);
            locateAllStops(routeId);
            routeService.plan(routeId, versionForm(routeId));
            routeService.dispatch(routeId, versionForm(routeId), key("dispatch"));
            return new Chain(routeId, warehouseId, skuId, orderId);
        }

        private Long locatedWarehouse(String tag) {
            Long warehouseId = newWarehouse(tag);
            jdbc.update("UPDATE xsy_warehouse SET longitude = 113.90, latitude = 22.50, geom_crs = 'GCJ02' WHERE id = ?",
                    warehouseId);
            return warehouseId;
        }

        private void stockIn(Long warehouseId, Long skuId, String tag, String quantity) {
            Long supplierId = newPurchasableSupplier(tag, skuId);
            PurchaseOrderVO order = purchaseOrderService.create(
                    orderForm(supplierId, warehouseId, skuId, quantity, "6.2000"), prefix + ":" + tag + ":po");
            submitOrder(order.getId());
            confirmReceipt(createReceipt(order.getId()).getId(), quantity);
        }

        private void sortOneLine(Long orderId, String sortedQuantity, Long warehouseId) {
            var create = new SortingTaskCreateForm();
            create.setWarehouseId(warehouseId);
            create.setAssigneeEmployeeId(1L);
            create.setRemark("F1-2B rollback 前置");
            create.setSalesOrderItemIds(new ArrayList<>(List.of(confirmedSalesOrderItemId(orderId))));
            var detail = sortingTaskService.create(create, key("sort"));
            Long taskId = detail.getTask().getId();

            var entry = new SortingEntryItemForm();
            entry.setId(detail.getItems().getFirst().getId());
            entry.setVersion(detail.getItems().getFirst().getVersion());
            entry.setSortedQuantity(new BigDecimal(sortedQuantity));
            entry.setResult("NORMAL");
            var entries = new ArrayList<SortingEntryItemForm>();
            entries.add(entry);
            var entryForm = new SortingEntryForm();
            entryForm.setItems(entries);
            sortingTaskService.enter(taskId, entryForm);
            var action = new SortingActionForm();
            action.setVersion(jdbc.queryForObject("SELECT version FROM xsy_sorting_task WHERE id = ?",
                    Integer.class, taskId));
            sortingTaskService.complete(taskId, action);
        }

        private Long newRoute(Long warehouseId) {
            var form = new DeliveryRouteForm();
            form.setRouteName("F1-2B rollback 线路");
            form.setDeliveryDate(LocalDate.now());
            form.setWarehouseId(warehouseId);
            return routeService.create(form);
        }

        private void attach(Long routeId, Long orderId) {
            var form = new DeliveryOrdersForm();
            form.setVersion(versionOf(routeId));
            form.setOrderIds(List.of(orderId));
            form.setReason("组单");
            routeService.addOrders(routeId, form);
        }

        private void locateAllStops(Long routeId) {
            jdbc.update("UPDATE xsy_delivery_route_stop SET longitude = 113.95, latitude = 22.55, geom_crs = 'GCJ02'"
                    + " WHERE route_id = ? AND deleted = FALSE", routeId);
        }

        private DeliveryVersionForm versionForm(Long routeId) {
            var form = new DeliveryVersionForm();
            form.setVersion(versionOf(routeId));
            return form;
        }

        private Integer versionOf(Long routeId) {
            return jdbc.queryForObject("SELECT version FROM xsy_delivery_route WHERE id = ?", Integer.class, routeId);
        }

        private Long assignmentId(Long routeId, Long orderId) {
            return jdbc.queryForObject(
                    "SELECT id FROM xsy_delivery_route_order WHERE route_id = ? AND order_id = ? AND deleted = FALSE",
                    Long.class, routeId, orderId);
        }

        private void sign(Long routeId, Long orderId) {
            var form = new DeliverySignForm();
            form.setVersion(jdbc.queryForObject(
                    "SELECT version FROM xsy_delivery_route_order WHERE id = ?", Integer.class,
                    assignmentId(routeId, orderId)));
            form.setResult("SIGNED");
            routeService.sign(routeId, orderId, form);
        }

        private String key(String tag) {
            return prefix + ":" + tag + ":" + UUID.randomUUID();
        }

        // ------------------------------------------------------------------
        // 1. 财务明细写入失败 → 签收整笔回滚
        // ------------------------------------------------------------------

        @Test
        @DisplayName("出库行来源键被占：sign 抛错 → 仍是 IN_TRANSIT、signed_at/signed_by 为空、无半张应收")
        void financeFailureRollsBackTheWholeSign() {
            Chain chain = dispatchedChain("FRR1");
            Long assignment = assignmentId(chain.routeId(), chain.orderId());
            Long outboundItemId = jdbc.queryForObject(
                    "SELECT id FROM xsy_inventory_outbound_item WHERE sales_order_id = ? AND deleted = FALSE ORDER BY id LIMIT 1",
                    Long.class, chain.orderId());

            // 先占掉这条出库行的应收来源键（模拟「同一出库行已被别的应收单记账」的数据异常）
            jdbc.update("INSERT INTO xsy_finance_receivable_item (receivable_id, source_type, source_id,"
                            + " order_item_id, sku_id, sku_name_snapshot, unit_snapshot,"
                            + " quantity, unit_price, amount, created_at, updated_at)"
                            + " VALUES (-1, 'INVENTORY_OUTBOUND_ITEM', ?, ?, ?, '占位', 'kg',"
                            + "         1.0000, 1.0000, 1.0000, now(), now())",
                    outboundItemId, confirmedSalesOrderItemId(chain.orderId()), chain.skuId());

            try {
                assertThatThrownBy(() -> sign(chain.routeId(), chain.orderId()))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("出库行已挂在别的应收单上");

                // 配送侧回到签收前：状态、时点、签收人、乐观锁版本全部未变
                assertThat(jdbc.queryForObject(
                        "SELECT fulfillment_status FROM xsy_delivery_route_order WHERE id = ?",
                        String.class, assignment)).isEqualTo("IN_TRANSIT");
                assertThat(jdbc.queryForObject("SELECT signed_at FROM xsy_delivery_route_order WHERE id = ?",
                        Object.class, assignment)).isNull();
                assertThat(jdbc.queryForObject("SELECT signed_by FROM xsy_delivery_route_order WHERE id = ?",
                        String.class, assignment)).isNull();
                assertThat(jdbc.queryForObject("SELECT version FROM xsy_delivery_route_order WHERE id = ?",
                        Integer.class, assignment)).as("回滚后不留下被消耗过的版本号").isEqualTo(1);

                // 财务侧没有半张单：单头 0 条、真实明细 0 条、日志 0 条
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_finance_receivable WHERE source_type = 'SALES_ORDER' AND source_id = ?",
                        Integer.class, chain.orderId())).isZero();
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_finance_receivable_item WHERE source_id = ? AND receivable_id > 0",
                        Integer.class, outboundItemId)).isZero();
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_finance_operation_log l WHERE l.business_type = 'RECEIVABLE'"
                                + " AND l.business_id IN (SELECT id FROM xsy_finance_receivable"
                                + "                       WHERE source_type = 'SALES_ORDER' AND source_id = ?)",
                        Integer.class, chain.orderId())).as("应收未成立 → 不留生成日志").isZero();

                // 库存记录与出库单不因财务失败被改动：本 SKU 的 SALES_OUT 还是发车时那一条
                assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM xsy_inventory_movement WHERE movement_type = 'SALES_OUT'"
                                + " AND sku_id = ? AND deleted = FALSE", Integer.class, chain.skuId()))
                        .as("发车流水不受财务失败影响").isEqualTo(1);
            } finally {
                // 占位行是本用例故意造的假数据：append-only 表没有删除入口，测试负责物理清掉
                jdbc.update("DELETE FROM xsy_finance_receivable_item WHERE receivable_id = -1");
                // 本用例新建的仓必须停用：NOT_SUPPORTED 让造数随各自事务提交，而「启用仓库恰好唯一一个」
                // 是订单默认仓库解析（41018）与 ScmInventoryOutboundIT 等既有用例的前置假设。
                // 停用是仓库既有做法（DeliveryPrintConcurrencyIT 同一处理），不是绕过断言。
                disableWarehouse(chain.warehouseId());
            }
        }

        // ------------------------------------------------------------------
        // 2. 生成器不接受「自己开一个事务」
        // ------------------------------------------------------------------

        @Test
        @DisplayName("无外层事务调用生成器：MANDATORY 直接拒绝，绝不会自己提交一张应收")
        void generatorRefusesToRunWithoutTheSignTransaction() {
            assertThatThrownBy(() -> financeReceivableService.generateOnSign(-1L))
                    .isInstanceOf(IllegalTransactionStateException.class);

            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM xsy_finance_receivable WHERE source_id = -1", Integer.class)).isZero();
        }
    }
}
