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
 * 库存预警定时扫描余额和预留变化，不在每次记账时直接发通知。通知可能延迟到下一轮扫描；每个预警由独立事务投递，单条失败不回滚整轮。
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
            // 范围为空时直接返回：不是「没有预警」，而是「没有可检查的仓」
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
