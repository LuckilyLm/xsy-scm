package com.xsy.scm.mall.cart.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Data;

@Data
@TableName("mall_cart_item")
public class MallCartItemEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long accountId;
    private Long customerId;
    private Long skuId;
    private BigDecimal quantity;
    @Version
    private Integer version;
    @TableLogic(value = "false", delval = "true")
    private Boolean deleted;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
