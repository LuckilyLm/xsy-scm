package com.xsy.scm.inventory.domain.entity;

import java.time.OffsetDateTime;
import lombok.Data;

/**
 * 库存预警通知的去重状态（按仓库 + SKU）。
 *
 * <p>
 * <b>它记的是「上次通知时的状态」，不是库存状态本身</b>：{@code NORMAL / LOW / HIGH} 仍然由 {@code ScmInventoryWarningStatusEnum#evaluate}
 * 在读取时算出、不落库。这张表只回答两个问题 —— 「上次是什么状态」（决定要不要发）和「回到过几次 NORMAL」（{@link #epoch}，决定再次异常时 的 event_key 是否是一个新的键）。
 *
 * <p>
 * {@code epoch} 在状态<b>回到 NORMAL</b> 时 +1。异常持续期间它不动，因此同一段异常反复评估 得到的是同一个 event_key，只会发出一条通知；而「异常 → 正常 → 再异常」会落在新的纪元上，
 * 可以再提醒一次。这正是 ADR-007 要求的语义。
 */
@Data
public class InventoryWarningStateEntity {

    private Long warehouseId;

    private Long skuId;

    /**
     * 最近一次评估得到的状态；取值 {@code NORMAL / LOW / HIGH}。
     */
    private String status;

    /**
     * 纪元号：每次从异常回到 {@code NORMAL} 自增 1。
     */
    private Integer epoch;

    private OffsetDateTime updatedAt;

    private String updatedBy;
}
