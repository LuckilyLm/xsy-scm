package com.xsy.scm.inventory.job;

import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.inventory.domain.vo.InventoryWarningScanVO;
import com.xsy.scm.inventory.service.InventoryWarningScanService;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.module.support.job.core.SmartJob;
import org.springframework.stereotype.Component;

/**
 * 库存预警阈值通知扫描任务。
 *
 * <p>
 * 执行频率由 SmartAdmin 定时任务配置决定（建议 10–30 分钟一次）：阈值预警不是秒级指标，
 * 而每次扫描都要读全部阈值配置，过密只会重复得出同一个结论。留空参数即可。
 *
 * <p>
 * 任务没有登录上下文，因此按<b>全部仓库</b>扫描（{@link ScmValueScope#all()}），不解析调用者范围；
 * 通知接收人由仓库授权行决定，与谁触发这次扫描无关 —— 手动检查和定时扫描对同一次跃迁
 * 产生的是同一个 event_key，所以两条路径不会各发一条。
 */
@Component
@RequiredArgsConstructor
public class InventoryWarningScanJob implements SmartJob {

    private final InventoryWarningScanService inventoryWarningScanService;

    @Override
    public String run(String param) {
        InventoryWarningScanVO result = inventoryWarningScanService.scan(ScmValueScope.all());
        if (result.getSentCount() > 0) {
            return "已检查 " + result.getScannedCount() + " 条阈值配置，投递 " + result.getSentCount() + " 条库存预警通知";
        }
        return "已检查 " + result.getScannedCount() + " 条阈值配置，没有新的预警跃迁";
    }
}
