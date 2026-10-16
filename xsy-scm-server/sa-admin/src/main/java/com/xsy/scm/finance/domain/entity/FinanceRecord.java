package com.xsy.scm.finance.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.OffsetDateTime;

/** 财务单据共用创建人和时间等审计字段，记录实际资金和应收应付事实；操作日志另行记录。 */
@Data
public abstract class FinanceRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    @Version
    private Integer version = 0;
    private Boolean deleted = false;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
