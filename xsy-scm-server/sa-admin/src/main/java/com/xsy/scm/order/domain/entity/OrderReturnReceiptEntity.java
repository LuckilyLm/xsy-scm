package com.xsy.scm.order.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("order_return_receipt")
public class OrderReturnReceiptEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long returnId;
    private Long warehouseId;
    private String idempotencyKey;
    private OffsetDateTime receivedAt;
    private String operator;
    private OffsetDateTime createdAt;
    private String createdBy;
}
