package com.xsy.scm.customer.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 客户类型（可维护字典表）。
 *
 * <p>
 * 客户类型存储在 {@code customer_type} 表中，类型编码由种子数据提供，新增类型不需要在前端维护另一份枚举。
 */
@Data
@TableName(value = "customer_type", autoResultMap = true)
public class CustomerTypeEntity {

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

    private String typeCode;

    private String name;

    private String status;
}
