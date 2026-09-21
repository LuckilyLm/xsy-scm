package net.lab1024.sa.admin.module.scm.inventory.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 盘点单分页查询条件。
 *
 * <p>与出库单查询同取向：刻意**不提供** {@code sortItemList} ——
 * 列表 SQL 的排序列写死在 mapper 里（{@code created_at DESC, id DESC}），
 * 客户端传入排序会与联表列名产生歧义。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InventoryStocktakeQueryForm extends PageParam {

    /**
     * 盘点单号（模糊）。
     */
    private String stocktakeNo;

    /**
     * 仓库（精确）。
     */
    private Long warehouseId;

    /**
     * 状态（精确，{@code ScmInventoryStocktakeStatusEnum}）。
     *
     * <p>不加 {@code @Pattern} 白名单，与出库单查询保持同一取向：
     * 非法状态值在这里只会筛出空列表（无害），而加白名单会引入
     * 「前端把『全部』提交成空串 → 400/30001」这一类与业务无关的失败。
     */
    @Size(max = 20)
    private String status;

    @Override
    @Min(1)
    public Long getPageNum() {
        return super.getPageNum();
    }

    @Override
    @Min(1)
    @Max(100)
    public Long getPageSize() {
        return super.getPageSize();
    }
}
