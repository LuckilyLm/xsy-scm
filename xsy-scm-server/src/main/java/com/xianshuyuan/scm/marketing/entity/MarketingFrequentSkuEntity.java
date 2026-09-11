package com.xianshuyuan.scm.marketing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 客户常用菜品（常购清单）。下单成功后累加 buy_count，供"常用菜品"与"再来一单"使用。
 */
@Data
@TableName("marketing_frequent_sku")
public class MarketingFrequentSkuEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long customerId;
    private Long skuId;
    private Integer buyCount;
    private Long lastOrderId;
    private OffsetDateTime lastOrderedAt;
    @Version
    private Integer version;
    @TableLogic
    private Boolean deleted;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
