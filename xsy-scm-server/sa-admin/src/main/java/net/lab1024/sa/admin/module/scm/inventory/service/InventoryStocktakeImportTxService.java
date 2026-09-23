package net.lab1024.sa.admin.module.scm.inventory.service;

import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.order.service.OrderIdempotencyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 盘点导入的<b>事务提交</b>侧：把「幂等认领 → 持锁核验并建草稿 → 完成幂等记录」放进同一事务，
 * 供非事务的 {@link InventoryStocktakeImportService} 在校验通过后调用。
 *
 * <p><b>为什么幂等认领必须与建草稿同事务</b>：核验一旦漂移，{@code createFromSnapshot} 抛异常 →
 * 整个事务回滚，连认领写下的 {@code idempotency_record} 也一并撤销，用户重导同一份 Excel 不会被判成
 * 「上次已建单」。反过来，如果认领是独立提交的事务，漂移失败后会残留一条无结果的认领，
 * 使合法重试撞上「Incomplete claim」而永远无法导入。
 */
@Service
@RequiredArgsConstructor
public class InventoryStocktakeImportTxService {

    /** 一次提交的结果：新建草稿 id + 是否命中幂等重放。 */
    public record CommitResult(Long stocktakeId, boolean replayed) {
    }

    private final InventoryStocktakeService stocktakeService;

    private final OrderIdempotencyService idempotency;

    /**
     * @param fingerprint 参与哈希的请求指纹（凭证令牌 + 各行实盘量）：内容变了即视为不同请求，
     *                    不会把「改了实盘数的重传」误判成首次请求的重放。
     */
    @Transactional(rollbackFor = Exception.class)
    public CommitResult commit(Long warehouseId, List<InventoryStocktakeService.SnapshotLine> lines,
                               String idempotencyKey, Object fingerprint) {
        var claim = idempotency.claim("STOCKTAKE_IMPORT", idempotencyKey, fingerprint);
        if (claim.replay()) {
            Long existing = idempotency.replay(claim, Long.class);
            return new CommitResult(existing, true);
        }
        Long stocktakeId = stocktakeService.createFromSnapshot(warehouseId, lines);
        idempotency.complete(claim, "STOCKTAKE_IMPORT_DRAFT", stocktakeId, stocktakeId);
        return new CommitResult(stocktakeId, false);
    }
}
