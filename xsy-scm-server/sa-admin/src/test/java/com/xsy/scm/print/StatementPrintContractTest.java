package com.xsy.scm.print;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.print.constant.ScmPrintDocumentTypeEnum;
import com.xsy.scm.print.support.ScmPrintSource;
import com.xsy.scm.print.support.StatementPrintSourceProvider;
import com.xsy.scm.report.constant.ScmReportPermission;
import com.xsy.scm.report.dao.ScmCustomerStatementDao;
import com.xsy.scm.report.domain.vo.ScmCustomerStatementItemVO;
import com.xsy.scm.report.domain.vo.ScmCustomerStatementVO;
import com.xsy.scm.report.service.ScmCustomerStatementService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatementPrintContractTest {

    @Test
    void statementUsesTheExistingStatementQueryPermissionAndFrozenVersionRead() {
        var provider = new StatementPrintSourceProvider(null);

        assertThat(provider.documentType()).isEqualTo(ScmPrintDocumentTypeEnum.STATEMENT);
        assertThat(provider.queryPermission()).isEqualTo(ScmReportPermission.CUSTOMER_STATEMENT_QUERY);
    }

    @Test
    void statementCatalogMarksEveryMoneyFieldAndSupportsCustomerBinding() {
        var type = ScmPrintDocumentTypeEnum.STATEMENT;

        assertThat(type.requiresMoneyPermission()).isTrue();
        assertThat(type.headerField("openingReceivable").money()).isTrue();
        assertThat(type.headerField("closingReceivable").money()).isTrue();
        assertThat(type.headerField("settlementCustomerName").money()).isFalse();
        assertThat(type.column("receivableDelta").money()).isTrue();
        assertThat(type.column("receiptDelta").money()).isTrue();
        assertThat(type.column("writeOffDelta").money()).isTrue();
        assertThat(type.column("refundDelta").money()).isTrue();
        assertThat(type.column("receivableBalance").money()).isTrue();
        assertThat(type.column("factType").money()).isFalse();
        assertThat(type.total("closingReceivable").money()).isTrue();
        assertThat(type.supportsCustomerBinding()).isTrue();
        assertThat(ScmPrintDocumentTypeEnum.SALES_ORDER.supportsCustomerBinding()).isTrue();
        assertThat(ScmPrintDocumentTypeEnum.DELIVERY_NOTE.supportsCustomerBinding()).isFalse();
        assertThat(ScmPrintDocumentTypeEnum.SORTING_TICKET.supportsCustomerBinding()).isFalse();
    }

    @Test
    void frozenStatementBecomesAPrintSourceWithoutRecalculatingMoney() {
        ScmCustomerStatementService service = mock(ScmCustomerStatementService.class);
        when(service.loadForPrint(9L)).thenReturn(statement());

        var provider = new StatementPrintSourceProvider(service);
        assertThat(provider.customerId(9L)).isEqualTo(88L);

        ScmPrintSource source = provider.load(9L);
        assertThat(source.documentNo()).isEqualTo("#9");
        assertThat(source.headerValues())
            .containsEntry("settlementCustomerName", "测试结算方")
            .containsEntry("period", "2026-10-01 至 2026-10-31")
            .containsEntry("openingReceivable", "1.0000")
            .containsEntry("closingReceivable", "2.0000")
            .containsEntry("generatedAt", "2026-10-31 10:00:00")
            .containsEntry("statementVersion", "9")
            .containsEntry("partialScope", "完整结算方");
        assertThat(source.rows()).hasSize(1);
        assertThat(source.rows().getFirst())
            .containsEntry("eventAt", "2026-10-20 09:30:00")
            .containsEntry("factType", "实际收款")
            .containsEntry("documentNo", "SK-001")
            .containsEntry("relatedNo", "SO-001")
            .containsEntry("receivableBalance", "2.0000");
        assertThat(source.totalValues())
            .containsEntry("closingReceivable", "2.0000")
            .containsEntry("receivableIncrease", "3.0000")
            .containsEntry("receiptNet", "1.0000");
    }

    @Test
    void printReadDoesNotRequireTheGeneratorButStillRequiresVisibleSources() {
        ScmCustomerStatementDao statementDao = mock(ScmCustomerStatementDao.class);
        ScmDataScopeService dataScopeService = mock(ScmDataScopeService.class);
        ScmDataScopeContext scope = scope();
        when(dataScopeService.resolve()).thenReturn(scope);

        ScmCustomerStatementVO header = statement();
        // 生成人是别人：普通详情会拒绝，打印读取应只看当前数据范围
        header.setGeneratedByEmployeeId(999L);
        when(statementDao.selectStatementById(9L)).thenReturn(header);
        when(statementDao.selectItems(9L)).thenReturn(header.getItems());
        when(statementDao.selectEvents(any(), any(), any(), any(), anyInt())).thenReturn(header.getItems());
        when(statementDao.selectSources(9L)).thenReturn(header.getItems());

        var service = new ScmCustomerStatementService(statementDao, dataScopeService);
        ScmCustomerStatementVO loaded = service.loadForPrint(9L);

        assertThat(loaded.getId()).isEqualTo(9L);
        verify(statementDao, never()).selectStatement(any(), any());
    }

    @Test
    void printReadRejectsWhenAnySourceFactIsNoLongerVisible() {
        ScmCustomerStatementDao statementDao = mock(ScmCustomerStatementDao.class);
        ScmDataScopeService dataScopeService = mock(ScmDataScopeService.class);
        ScmDataScopeContext scope = scope();
        when(dataScopeService.resolve()).thenReturn(scope);

        ScmCustomerStatementVO header = statement();
        when(statementDao.selectStatementById(9L)).thenReturn(header);
        when(statementDao.selectItems(9L)).thenReturn(header.getItems());
        when(statementDao.selectEvents(any(), any(), any(), any(), anyInt()))
            .thenReturn(List.of(fact("RECEIPT", 100L)));
        when(statementDao.selectSources(9L)).thenReturn(List.of(fact("RECEIPT", 100L), fact("RECEIPT", 200L)));

        var service = new ScmCustomerStatementService(statementDao, dataScopeService);

        assertThatThrownBy(() -> service.loadForPrint(9L)).isInstanceOf(ScmBusinessException.class);
    }

    private static ScmDataScopeContext scope() {
        ScmDataScopeContext scope = mock(ScmDataScopeContext.class);
        when(scope.getEmployeeId()).thenReturn(7L);
        when(scope.getWarehouseScope()).thenReturn(ScmValueScope.all());
        when(scope.getCustomerSellerScope()).thenReturn(ScmValueScope.all());
        when(scope.getOrderSellerScope()).thenReturn(ScmValueScope.all());
        return scope;
    }

    private static ScmCustomerStatementItemVO fact(String factType, Long factId) {
        ScmCustomerStatementItemVO item = new ScmCustomerStatementItemVO();
        item.setFactType(factType);
        item.setFactId(factId);
        return item;
    }

    private static ScmCustomerStatementVO statement() {
        ScmCustomerStatementVO statement = new ScmCustomerStatementVO();
        statement.setId(9L);
        statement.setSettlementCustomerId(88L);
        statement.setSettlementCustomerName("测试结算方");
        statement.setStartDate(LocalDate.of(2026, 10, 1));
        statement.setEndDate(LocalDate.of(2026, 10, 31));
        statement.setPartialScope(false);
        statement.setGeneratedAt(OffsetDateTime.of(2026, 10, 31, 10, 0, 0, 0, ZoneOffset.ofHours(8)));
        statement.setOpeningReceivable(new BigDecimal("1.0000"));
        statement.setClosingReceivable(new BigDecimal("2.0000"));
        statement.setReceivableIncrease(new BigDecimal("3.0000"));
        statement.setReceivableRed(new BigDecimal("0.0000"));
        statement.setWriteOffNet(new BigDecimal("0.0000"));
        statement.setReceiptNet(new BigDecimal("1.0000"));
        statement.setRefundNet(new BigDecimal("0.0000"));

        ScmCustomerStatementItemVO item = new ScmCustomerStatementItemVO();
        item.setLineNo(1);
        item.setFactType("RECEIPT");
        item.setFactId(100L);
        item.setEventAt(OffsetDateTime.of(2026, 10, 20, 9, 30, 0, 0, ZoneOffset.ofHours(8)));
        item.setDocumentNo("SK-001");
        item.setRelatedNo("SO-001");
        item.setReceivableDelta(new BigDecimal("0.0000"));
        item.setReceiptDelta(new BigDecimal("1.0000"));
        item.setWriteOffDelta(new BigDecimal("0.0000"));
        item.setRefundDelta(new BigDecimal("0.0000"));
        item.setReceivableBalance(new BigDecimal("2.0000"));
        statement.setItems(List.of(item));
        return statement;
    }
}
