package com.xsy.scm.inventory.domain.entity;

import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 库存预警状态表记录上一次通知状态和变化轮次，不是实时库存事实。恢复正常后再异常属于新一轮通知，旧通知不能重复发送。
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
