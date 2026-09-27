package com.xsy.scm.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("product_tag_relation")
public class ProductTagRelationEntity {
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
    private Long spuId;
    private Long tagId;
}
