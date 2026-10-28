package com.xsy.scm.print.support;

import com.xsy.scm.print.constant.ScmPrintDocumentTypeEnum;
import com.xsy.scm.print.constant.ScmStatementFactType;
import com.xsy.scm.report.constant.ScmReportPermission;
import com.xsy.scm.report.domain.vo.ScmCustomerStatementItemVO;
import com.xsy.scm.report.domain.vo.ScmCustomerStatementVO;
import com.xsy.scm.report.service.ScmCustomerStatementService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 对账单打印只读已冻结的对账版本，不重算财务事实。
 *
 * <p>
 * 原对账详情只允许生成人查看；打印使用报表域提供的专用读取，按当前数据范围重新验证全部源事实。 生成人之外的人能否打印取决于是否仍能看到这些事实，而不是是否曾经由自己生成。
 */
@Component
@RequiredArgsConstructor
public class StatementPrintSourceProvider implements ScmPrintSourceProvider {

    private final ScmCustomerStatementService statementService;

    @Override
    public ScmPrintDocumentTypeEnum documentType() {
        return ScmPrintDocumentTypeEnum.STATEMENT;
    }

    @Override
    public String queryPermission() {
        return ScmReportPermission.CUSTOMER_STATEMENT_QUERY;
    }

    @Override
    public void requireVisible(Long businessId) {
        statementService.loadForPrint(businessId);
    }

    @Override
    public Long customerId(Long businessId) {
        return statementService.loadForPrint(businessId).getSettlementCustomerId();
    }

    @Override
    public ScmPrintSource load(Long businessId) {
        ScmCustomerStatementVO statement = statementService.loadForPrint(businessId);
        Map<String, String> header = ScmPrintSource.values();
        header.put("settlementCustomerName", ScmPrintText.text(statement.getSettlementCustomerName()));
        header.put("period", period(statement));
        header.put("openingReceivable", ScmPrintText.fixed(statement.getOpeningReceivable()));
        header.put("closingReceivable", ScmPrintText.fixed(statement.getClosingReceivable()));
        header.put("generatedAt", ScmPrintText.dateTime(statement.getGeneratedAt()));
        header.put("statementVersion", ScmPrintText.text(statement.getId()));
        header.put("partialScope", Boolean.TRUE.equals(statement.getPartialScope()) ? "授权范围内（非完整集团）" : "完整结算方");

        List<Map<String, String>> rows = new ArrayList<>();
        List<ScmCustomerStatementItemVO> items = statement.getItems() == null ? List.of() : statement.getItems();
        for (ScmCustomerStatementItemVO item : items) {
            Map<String, String> row = ScmPrintSource.row();
            row.put("eventAt", ScmPrintText.dateTime(item.getEventAt()));
            row.put("factType", factTypeLabel(item.getFactType()));
            row.put("documentNo", ScmPrintText.text(item.getDocumentNo()));
            row.put("relatedNo", ScmPrintText.text(item.getRelatedNo()));
            row.put("receivableDelta", ScmPrintText.fixed(item.getReceivableDelta()));
            row.put("receiptDelta", ScmPrintText.fixed(item.getReceiptDelta()));
            row.put("writeOffDelta", ScmPrintText.fixed(item.getWriteOffDelta()));
            row.put("refundDelta", ScmPrintText.fixed(item.getRefundDelta()));
            row.put("postingAmount", ScmPrintText.fixed(item.getPostingAmount()));
            row.put("receivableBalance", ScmPrintText.fixed(item.getReceivableBalance()));
            rows.add(row);
        }

        Map<String, String> totals = ScmPrintSource.values();
        totals.put("closingReceivable", ScmPrintText.fixed(statement.getClosingReceivable()));
        totals.put("receivableIncrease", ScmPrintText.fixed(statement.getReceivableIncrease()));
        totals.put("receivableRed", ScmPrintText.fixed(statement.getReceivableRed()));
        totals.put("writeOffNet", ScmPrintText.fixed(statement.getWriteOffNet()));
        totals.put("waiverNet", ScmPrintText.fixed(statement.getWaiverNet()));
        totals.put("receiptNet", ScmPrintText.fixed(statement.getReceiptNet()));
        totals.put("refundNet", ScmPrintText.fixed(statement.getRefundNet()));
        return new ScmPrintSource("#" + statement.getId(), header, rows, totals);
    }

    private static String period(ScmCustomerStatementVO statement) {
        String start = ScmPrintText.text(statement.getStartDate());
        String end = ScmPrintText.text(statement.getEndDate());
        if (start.isEmpty() && end.isEmpty()) {
            return "";
        }
        return start + " 至 " + end;
    }

    /** 财务事实类型没有统一枚举，打印件按页面同一套叫法逐项给中文；未知类型原样输出。 */
    private static String factTypeLabel(String factType) {
        if (factType == null) {
            return "";
        }
        String label = ScmStatementFactType.labelOf(factType);
        return label == null ? factType : label;
    }
}
