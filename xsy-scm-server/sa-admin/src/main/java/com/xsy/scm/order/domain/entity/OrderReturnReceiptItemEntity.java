package com.xsy.scm.order.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@TableName("order_return_receipt_item")
public class OrderReturnReceiptItemEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long receiptId;
    private Long returnItemId;
    private Long sourceSalesOutMovementId;
    private Long sourceOutboundItemId;
    private String disposition;
    private BigDecimal quantity;
    private String unitSnapshot;
    private BigDecimal unitCost;
    private OffsetDateTime createdAt;
    private String createdBy;
}
