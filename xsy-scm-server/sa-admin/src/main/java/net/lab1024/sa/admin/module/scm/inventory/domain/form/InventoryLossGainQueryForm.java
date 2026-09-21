package net.lab1024.sa.admin.module.scm.inventory.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 报损报溢单分页查询条件。
 *
 * <p>与出库单 / 盘点单查询同取向：刻意**不提供** {@code sortItemList} ——
 * 列表 SQL 的排序列写死在 mapper 里（{@code created_at DESC, id DESC}），
 * 客户端传入排序会与联表列名产生歧义。
 *
 * <p>{@code adjustType} / {@code status} 不加 {@code @Pattern} 白名单：非法值只会筛出空列表
 * （无害），而加白名单会引入「前端把『全部』提交成空串 → 40000」这一类与业务无关的失败。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InventoryLossGainQueryForm extends PageParam {

    /**
     * 单据号（模糊）。
     */
    @Size(max = 64)
    private String lossGainNo;

    /**
     * 仓库（精确）。
     */
    private Long warehouseId;

    /**
     * 调整类型：{@code LOSS} / {@code OVERFLOW}（精确）。
     */
    @Size(max = 20)
    private String adjustType;

    /**
     * 状态：{@code PENDING} / {@code COMPLETED} / {@code REJECTED}（精确）。
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
