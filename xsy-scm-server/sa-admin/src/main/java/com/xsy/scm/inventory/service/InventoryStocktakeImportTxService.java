package com.xsy.scm.inventory.service;

import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.idempotency.ScmIdempotencyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 盘点导入在一个事务里完成幂等认领、锁定库存、验证库存版本、创建草稿和幂等结果记录；任一失败整批回滚。 */
@Service
@RequiredArgsConstructor
public class InventoryStocktakeImportTxService {

    /** 一次提交的结果：新建草稿 id + 是否命中幂等重放。 */
    public record CommitResult(Long stocktakeId, boolean replayed) {
    }

    private final InventoryStocktakeService inventoryStocktakeService;

    private final ScmIdempotencyService idempotencyService;

    /**
     * @param fingerprint
     *            参与哈希的请求指纹（凭证令牌 + 各行实盘量）：内容变了即视为不同请求，不会把「改了实盘数的重传」误判成首次请求的重放。
     */
    @Transactional(rollbackFor = Exception.class)
    public CommitResult commit(Long warehouseId, List<InventoryStocktakeService.SnapshotLine> lines,
            String idempotencyKey, Object fingerprint) {
        var claim = idempotencyService.claim("STOCKTAKE_IMPORT", idempotencyKey, fingerprint);
        if (claim.replay()) {
            Long existing = idempotencyService.replay(claim, Long.class);
            return new CommitResult(existing, true);
        }
        Long stocktakeId = inventoryStocktakeService.createFromSnapshot(warehouseId, lines);
        idempotencyService.complete(claim, "STOCKTAKE_IMPORT_DRAFT", stocktakeId, stocktakeId);
        return new CommitResult(stocktakeId, false);
    }
}
