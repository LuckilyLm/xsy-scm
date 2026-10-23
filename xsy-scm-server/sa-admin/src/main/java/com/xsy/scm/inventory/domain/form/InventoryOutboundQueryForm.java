package com.xsy.scm.inventory.domain.form;

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 出库单分页查询条件。
 *
 * <p>
 * 刻意不提供 {@code sortItemList}：列表 SQL 的排序列写死在 mapper 里（{@code created_at DESC, id DESC}），客户端传入排序会与联表列名产生歧义。 与库存余额查询做法一致。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InventoryOutboundQueryForm extends PageParam {

    /**
     * 出库单号（模糊）。
     */
    private String outboundNo;

    /**
     * 仓库（精确）。
     */
    private Long warehouseId;

    /**
     * 状态（精确，{@code ScmInventoryOutboundStatusEnum}）。
     */
    private String status;
}
