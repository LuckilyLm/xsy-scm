package net.lab1024.sa.admin.module.scm.inventory.support;

/**
 * 盘点导入的快照漂移信号：持锁读取的当前余额与导出快照（余额 id / 版本 / 记账单位 / 账面量）不一致。
 *
 * <p>按计划裁决「从导出到导入任一参与行余额版本变化即整批拒绝」，因此这是<b>整批</b>失败而非跳过该行，
 * 由 {@code InventoryStocktakeImportService} 捕获后映射为导入结果里的 {@code SNAPSHOT_STALE} 错误并回滚，
 * 不产生任何草稿。它刻意不是 {@code ScmBusinessException} —— 库存域的读侧核验不是对外业务错误码，
 * 而是导入这条批处理链路的内部拒绝信号。
 */
public class StocktakeSnapshotDriftException extends RuntimeException {

    private final String skuCode;

    public StocktakeSnapshotDriftException(String skuCode) {
        super("盘点快照与当前余额不一致：" + skuCode);
        this.skuCode = skuCode;
    }

    public String getSkuCode() {
        return skuCode;
    }
}
