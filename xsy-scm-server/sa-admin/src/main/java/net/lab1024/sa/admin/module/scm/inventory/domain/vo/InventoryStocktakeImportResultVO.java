package net.lab1024.sa.admin.module.scm.inventory.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 盘点 Excel 导入结果。
 *
 * <p><b>整批语义</b>：{@code totalErrors > 0} 表示没有任何草稿被创建（校验或快照核验在写库前挡住，
 * 或写库中途漂移导致整事务回滚）；成功时 {@code stocktakeId} 为新建草稿单 id。
 *
 * <p>{@code replayed} 为真表示本次是同一 {@code Idempotency-Key} 的重试（响应丢失后再发），
 * 返回的是首次创建的那张草稿 id，而非第二张。
 */
@Data
public class InventoryStocktakeImportResultVO {

    private int totalRows;

    private int totalErrors;

    /** 成功创建的草稿单 id；有错误时为 {@code null}。 */
    private Long stocktakeId;

    /** 本次导入写入的明细行数（成功时）。 */
    private int importedItems;

    /** 命中幂等重放：未重复建单，返回既有草稿。 */
    private boolean replayed;

    private List<InventoryStocktakeImportErrorVO> errors = new ArrayList<>();
}
