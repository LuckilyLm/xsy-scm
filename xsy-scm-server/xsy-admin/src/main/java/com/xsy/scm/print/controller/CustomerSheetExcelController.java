package com.xsy.scm.print.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.xsy.scm.order.permission.OrderPermission;
import com.xsy.scm.print.service.CustomerSheetExcelService;
import com.xsy.scm.report.constant.ScmReportPermission;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.base.common.util.XsyResponseUtil;
import com.xsy.scm.base.module.support.operatelog.annotation.OperateLog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 客户版Excel出口与订单查看、报表导出共用权限，不触发正式打印。 */
@RestController
@RequiredArgsConstructor
@SaCheckPermission(value = {OrderPermission.QUERY, ScmReportPermission.EXPORT}, mode = SaMode.AND)
public class CustomerSheetExcelController {
    private final CustomerSheetExcelService excelService;

    /** 导出指定订单的订货单，明细取下单量；不存在或越权沿用订单错误。 */
    @GetMapping("/scm/print/customer-sheet/{orderId}/order.xlsx")
    @OperateLog
    public void order(@PathVariable Long orderId, HttpServletResponse response) throws IOException {
        byte[] bytes = excelService.export(orderId, false);
        XsyResponseUtil.setDownloadFileHeader(response, "订货单-" + orderId + ".xlsx", null);
        response.getOutputStream().write(bytes);
    }

    /** 导出指定订单的送货单，未形成正式实发量的行保留空值。 */
    @GetMapping("/scm/print/customer-sheet/{orderId}/delivery.xlsx")
    @OperateLog
    public void delivery(@PathVariable Long orderId, HttpServletResponse response) throws IOException {
        byte[] bytes = excelService.export(orderId, true);
        XsyResponseUtil.setDownloadFileHeader(response, "送货单-" + orderId + ".xlsx", null);
        response.getOutputStream().write(bytes);
    }
}
