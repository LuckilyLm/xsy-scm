package com.xsy.scm.inventory.domain.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 调拨单分页查询条件。
 *
 * <p>
 * 与其它库存单据查询同取向：刻意**不提供** {@code sortItemList} —— 列表 SQL 的排序列写死在 mapper 里（{@code created_at DESC, id DESC}）。
 *
 * <p>
 * 同时给出「源仓」与「目标仓」两个筛选维度：只看源仓回答「这个仓发出去了多少」， 只看目标仓回答「这个仓要收多少」，两者都有查询价值，不能只留一个。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InventoryTransferQueryForm extends PageParam {

    /**
     * 调拨单号（模糊）。
     */
    @Size(max = 64, message = "调拨单号不能超过64个字符")
    private String transferNo;

    /**
     * 源仓库（精确）。
     */
    private Long fromWarehouseId;

    /**
     * 目标仓库（精确）。
     */
    private Long toWarehouseId;

    /**
     * 状态：{@code DRAFT} / {@code SHIPPED} / {@code RECEIVED} / {@code CANCELLED}（精确）。
     */
    @Size(max = 20, message = "调拨状态不能超过20个字符")
    private String status;

    @Override
    @Min(value = 1, message = "页码必须至少为1")
    public Long getPageNum() {
        return super.getPageNum();
    }

    @Override
    @Min(value = 1, message = "每页条数必须至少为1")
    @Max(value = 100, message = "每页条数不能超过100")
    public Long getPageSize() {
        return super.getPageSize();
    }
}
