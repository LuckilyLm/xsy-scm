package net.lab1024.sa.admin.module.scm.inventory.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 盘点 Excel 导入的单条错误（行 / SKU / 列 / 机器码 / 可读原因）。
 *
 * <p>与商品、订单导入同一取向：错误以行为粒度回报且<b>整批不落库</b>，因此这里承载的是「哪一行的哪一列
 * 因为什么被拒」，供前端逐条高亮，不是一次性抛出中断整批。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryStocktakeImportErrorVO {

    /** Excel 行号（表头为第 1 行，数据行从 2 起）；文件级错误用 0。 */
    private Integer row;

    private String skuCode;

    private String column;

    /** 机器可判读的码，如 {@code BLANK_ACTUAL}、{@code SNAPSHOT_STALE}、{@code DUPLICATE_SKU}。 */
    private String code;

    private String message;
}
