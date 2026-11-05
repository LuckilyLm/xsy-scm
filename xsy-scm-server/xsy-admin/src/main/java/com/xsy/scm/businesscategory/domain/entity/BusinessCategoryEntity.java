package com.xsy.scm.businesscategory.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 客户 / 供应商业务分类。作用域与层级由服务端维护，可选字段按 null 保存。
 */
@Data
@TableName(value = "xsy_business_category", autoResultMap = true)
public class BusinessCategoryEntity {

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

    private String scope;

    private String categoryCode;

    private String name;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long parentId;

    private Integer level;

    private Integer sortOrder;

    private String status;
}
