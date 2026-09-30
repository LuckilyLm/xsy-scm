package com.xsy.scm.report.job;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Component;
import com.xsy.scm.report.service.PurchaseDailyGenerationService;
import com.xsy.scm.report.support.ScmReportTimeRangeResolver;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.module.support.job.core.SmartJob;

/** 执行时间由 SmartAdmin 定时任务配置；空参数生成昨日，日期参数供人工补生成。 */
@Component
@RequiredArgsConstructor
public class PurchaseDailyReportJob implements SmartJob {
    private final PurchaseDailyGenerationService purchaseDailyGenerationService;

    @Override
    public String run(String param) {
        LocalDate reportDate;
        try {
            reportDate = param == null || param.isBlank()
                    ? LocalDate.now(ScmReportTimeRangeResolver.BUSINESS_ZONE).minusDays(1)
                    : LocalDate.parse(param.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("任务参数请留空（生成昨日）或填写 yyyy-MM-dd 补生成日期", exception);
        }
        boolean generated = purchaseDailyGenerationService.generate(reportDate);
        return "采购商品清单 " + reportDate + (generated ? " 已生成" : " 已存在，保留原始快照");
    }
}
