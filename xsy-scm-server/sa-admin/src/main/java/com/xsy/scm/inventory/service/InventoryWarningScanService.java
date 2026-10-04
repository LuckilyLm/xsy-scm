package com.xsy.scm.inventory.service;

import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.inventory.dao.InventoryWarningThresholdDao;
import com.xsy.scm.inventory.domain.vo.InventoryWarningScanVO;
import com.xsy.scm.inventory.domain.vo.InventoryWarningVO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 库存预警通知的一轮扫描。
 *
 * <p>
 * <b>为什么是扫描而不是挂在余额写路径上</b>：余额变更分散在九条记账腿（采购入库、销售出库、
 * 退货回库、盘点、报损报溢、调拨进出、转换进出）与预留的占用 / 释放上，逐点挂钩意味着把
 * 通知逻辑编进库存记账这条最热的路径，且任何新增记账腿都会静默漏掉通知。改成扫描后，
 * 检测与记账解耦：新增记账腿不需要改通知代码，扫描天然覆盖它。
 *
 * <p>
 * <b>代价是延迟</b>：事件时点从「余额变更的那一刻」变成「下一轮扫描」，间隔由任务配置决定。
 * 这个取舍由 ADR-007 记录；恢复语义不受影响 —— 扫描看到的是当前状态，纪元照常推进。
 *
 * <p>
 * <b>本类不加事务</b>：整轮共用一个事务会让一行的失败回滚掉前面所有已投递的通知。
 * 逐行事务由 {@link InventoryWarningNotifier} 提供，单行失败只影响该行，下一轮会重新评估它。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryWarningScanService {

    /**
     * 单轮扫描上限。超出部分留给下一轮，避免阈值配置极多时把整表读进内存。
     */
    private static final int MAX_SCAN_ROWS = 2000;

    private final InventoryWarningThresholdDao inventoryWarningThresholdDao;

    private final InventoryWarningNotifier inventoryWarningNotifier;

    /**
     * 扫描一轮并投递跃迁通知。
     *
     * @param warehouseScope
     *            仓库范围；定时任务传 {@link ScmValueScope#all()}，手动检查传调用者的范围
     * @return 扫描的检查条数与实际投递条数
     */
    public InventoryWarningScanVO scan(ScmValueScope warehouseScope) {
        InventoryWarningScanVO result = new InventoryWarningScanVO();
        if (warehouseScope == null || warehouseScope.isEmpty()) {
            // 空范围失败关闭：不是「没有预警」，而是「没有可检查的仓」
            return result;
        }
        List<InventoryWarningVO> rows = inventoryWarningThresholdDao.listScanRows(MAX_SCAN_ROWS, warehouseScope);
        result.setScannedCount(rows.size());
        int sent = 0;
        for (InventoryWarningVO row : rows) {
            try {
                sent += inventoryWarningNotifier.evaluateAndNotify(row);
            } catch (RuntimeException exception) {
                // 单行失败不拖垮整轮：这一行的状态与通知会一起回滚，下一轮重新评估
                log.error("库存预警通知失败 warehouseId={} skuId={}", row.getWarehouseId(), row.getSkuId(), exception);
            }
        }
        result.setSentCount(sent);
        return result;
    }
}
