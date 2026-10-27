package com.xsy.scm.print.domain.entity;

import java.time.OffsetDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import lombok.Data;

/**
 * 客户专属打印模板：同一客户对同一单据类型最多一条有效绑定。
 *
 * <p>
 * 绑定只影响「不指定模板」时的默认选择；显式选择的模板仍然优先。
 */
@Data
@TableName(value = "xsy_print_customer_template", autoResultMap = true)
public class ScmPrintCustomerTemplateEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long customerId;

    private String documentType;

    private Long templateId;

    @Version
    private Integer version = 0;

    @TableLogic(value = "false", delval = "true")
    private Boolean deleted = false;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private String createdBy;

    private String updatedBy;
}
