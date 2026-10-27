package com.xsy.scm.customer.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 客户分组：按批次约定价格的客户集合，与「客户类型」（个人 / 公司 / 集团）无关。
 *
 * <p>
 * 成员关系存在 {@code xsy_customer.customer_group_id}；保存分组时至少要有 一个客户，否则这条分组价永远解析不到。
 */
@Data
@TableName(value = "xsy_customer_group", autoResultMap = true)
public class CustomerGroupEntity {

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

    private String groupCode;

    private String groupName;

    private String status;
}
