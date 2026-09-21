package net.lab1024.sa.admin.module.scm.inventory.domain.form;

import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 库存预留分页查询条件。
 *
 * <p>与库存余额 / 出库单一致：不提供 {@code sortItemList}，排序列写死在 mapper。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InventoryReservationQueryForm extends PageParam {

    /**
     * 仓库（精确）。
     */
    private Long warehouseId;

    /**
     * SKU（精确）。
     */
    private Long skuId;

    /**
     * 状态（精确，{@code ScmInventoryReservationStatusEnum}）。
     */
    private String status;

    /**
     * 来源单据 id（精确）。
     */
    private Long sourceDocumentId;
}
