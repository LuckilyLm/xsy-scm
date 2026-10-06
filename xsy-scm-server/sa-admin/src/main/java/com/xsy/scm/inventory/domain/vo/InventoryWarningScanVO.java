package com.xsy.scm.inventory.domain.vo;

import lombok.Data;

/**
 * 一次库存预警扫描的结果。
 *
 * <p>
 * 两个计数都要返回：只有「投递条数」时，{@code 0} 既可能是「检查了 30 条配置、都没有跃迁」，也可能是「一条配置都没有」—— 这两种情况的下一步操作完全不同（前者正常，后者说明阈值还没配），所以必须能把它们区分开。
 */
@Data
public class InventoryWarningScanVO {

    /**
     * 实际检查的阈值配置条数（受单次扫描上限约束）。
     */
    private int scannedCount;

    /**
     * 投递的站内信条数；同一跃迁发给多名接收人会各计一条。
     */
    private int sentCount;
}
