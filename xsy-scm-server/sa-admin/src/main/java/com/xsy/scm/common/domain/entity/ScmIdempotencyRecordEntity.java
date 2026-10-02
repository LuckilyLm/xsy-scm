package com.xsy.scm.common.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.xsy.scm.common.json.JsonbObjectMapTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;

/** Shared persistence record for idempotent SCM commands. */
@Data
@TableName(value = "idempotency_record", autoResultMap = true)
public class ScmIdempotencyRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String operationScope;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String idempotencyKey;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String requestHash;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String resultType;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long resultId;

    @TableField(typeHandler = JsonbObjectMapTypeHandler.class, updateStrategy = FieldStrategy.ALWAYS)
    private Map<String, Object> resultData;

    @Version
    private Integer version = 0;

    @TableLogic(value = "false", delval = "true")
    private Boolean deleted = false;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime createdAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private OffsetDateTime updatedAt;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String createdBy;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String updatedBy;
}
