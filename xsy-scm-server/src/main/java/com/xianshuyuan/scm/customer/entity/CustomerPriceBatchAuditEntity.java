package com.xianshuyuan.scm.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.xianshuyuan.scm.common.persistence.JsonbJsonNodeTypeHandler;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.type.JdbcType;

import java.time.OffsetDateTime;

@Getter
@Setter
@TableName(value = "customer_price_batch_audit", autoResultMap = true)
public class CustomerPriceBatchAuditEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchKey;
    private String operationType;
    private String result;
    private Integer rowCount;
    @TableField(typeHandler = JsonbJsonNodeTypeHandler.class, jdbcType = JdbcType.OTHER)
    private JsonNode errorData;
    private OffsetDateTime createdAt;
    private String createdBy;
}
