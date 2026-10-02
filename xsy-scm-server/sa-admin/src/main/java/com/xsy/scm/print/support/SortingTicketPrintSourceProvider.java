package com.xsy.scm.print.support;

import com.xsy.scm.print.constant.ScmPrintDocumentTypeEnum;
import com.xsy.scm.sorting.constant.ScmSortingResultEnum;
import com.xsy.scm.sorting.permission.SortingPermission;
import com.xsy.scm.sorting.service.SortingQueryService;
import java.util.ArrayList;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 分拣票据沿用仓库与指派人交集范围，不读价格、不汇总不同单位数量。 */
@Component
@RequiredArgsConstructor
public class SortingTicketPrintSourceProvider implements ScmPrintSourceProvider {

    private final SortingQueryService sortingQueryService;

    @Override
    public ScmPrintDocumentTypeEnum documentType() {
        return ScmPrintDocumentTypeEnum.SORTING_TICKET;
    }

    @Override
    public String queryPermission() {
        return SortingPermission.TASK_QUERY;
    }

    @Override
    public void requireVisible(Long businessId) {
        sortingQueryService.detail(businessId);
    }

    @Override
    public ScmPrintSource load(Long businessId) {
        var ticket = sortingQueryService.printPreview(businessId);
        var header = ScmPrintSource.values();
        header.put("warehouseName", ScmPrintText.text(ticket.getWarehouseNameSnapshot()));
        header.put("assigneeName", ScmPrintText.text(ticket.getAssigneeName()));
        var rows = new ArrayList<Map<String, String>>();
        for (var item : ticket.getItems()) {
            var row = ScmPrintSource.row();
            row.put("orderNo", ScmPrintText.text(item.getOrderNoSnapshot()));
            row.put("customerName", ScmPrintText.text(item.getCustomerNameSnapshot()));
            row.put("productName", ScmPrintText.text(item.getProductNameSnapshot()));
            row.put("skuCode", ScmPrintText.text(item.getSkuCodeSnapshot()));
            row.put("specName", ScmPrintText.text(item.getSpecNameSnapshot()));
            row.put("saleUnit", ScmPrintText.text(item.getSaleUnitSnapshot()));
            row.put("plannedQuantity", ScmPrintText.fixed(item.getPlannedQuantitySnapshot()));
            row.put("sortedQuantity", ScmPrintText.fixed(item.getSortedQuantity()));
            row.put("result", resultLabel(item.getResult()));
            row.put("reason", ScmPrintText.text(item.getReason()));
            rows.add(row);
        }
        return new ScmPrintSource(ticket.getTaskNo(), header, rows, Map.of());
    }

    private String resultLabel(String result) {
        if (result == null) {
            return "未录入";
        }
        return switch (ScmSortingResultEnum.valueOf(result)) {
            case NORMAL -> "正常";
            case SHORT -> "短缺";
            case OUT_OF_STOCK -> "缺货";
            case OVER -> "超量";
        };
    }
}
