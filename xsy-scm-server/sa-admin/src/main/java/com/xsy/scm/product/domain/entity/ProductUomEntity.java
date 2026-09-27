package com.xsy.scm.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("scm_uom")
public class ProductUomEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    @Version
    private Integer version = 0;
    @TableLogic(value = "false", delval = "true")
    private Boolean deleted = false;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private String uomCode;
    private String name;
    private String category;
    private Integer precisionScale;
    private String status;
    private Integer sortOrder;
}
