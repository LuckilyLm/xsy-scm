package com.xianshuyuan.scm.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.xianshuyuan.scm.common.persistence.JsonbJsonNodeTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.time.OffsetDateTime;

@TableName(value = "customer_agreement_price_operation_log", autoResultMap = true)
public class AgreementPriceOperationLogEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long agreementPriceId;
    private String operationType;
    private String operator;
    @TableField(typeHandler = JsonbJsonNodeTypeHandler.class, jdbcType = JdbcType.OTHER)
    private JsonNode beforeData;
    @TableField(typeHandler = JsonbJsonNodeTypeHandler.class, jdbcType = JdbcType.OTHER)
    private JsonNode afterData;
    private OffsetDateTime createdAt;
    private String createdBy;

    public Long getId() {
        return id;
    }

    public void setId(Long value) {
        id = value;
    }

    public Long getAgreementPriceId() {
        return agreementPriceId;
    }

    public void setAgreementPriceId(Long value) {
        agreementPriceId = value;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String value) {
        operationType = value;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String value) {
        operator = value;
    }

    public JsonNode getBeforeData() {
        return beforeData;
    }

    public void setBeforeData(JsonNode value) {
        beforeData = value;
    }

    public JsonNode getAfterData() {
        return afterData;
    }

    public void setAfterData(JsonNode value) {
        afterData = value;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime value) {
        createdAt = value;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String value) {
        createdBy = value;
    }
}
