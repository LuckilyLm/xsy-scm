package com.xsy.scm.admin.module.business.external.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 外部平台同步日志 实体类
 *
 * <p>报错可重试；重试不改写原始失败记录语义，只递增 retry_count 并回到「待同步」。</p>
 *
 * @author xsy-scm
 */
@Data
@TableName("t_external_sync_log")
public class ExternalSyncLogEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long logId;

    /**
     * 平台类型，见 finance 模块 ExternalSystemTypeEnum
     */
    private Integer systemType;

    /**
     * 业务单据类型
     */
    private Integer bizType;

    /**
     * 业务单据 ID
     */
    private Long bizId;

    /**
     * 同步方向：1 上报，2 拉取
     */
    private Integer syncType;

    /**
     * 同步状态：1 成功，2 失败，3 待同步
     */
    private Integer syncStatus;

    /**
     * 失败原因
     */
    private String failReason;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 同步时间
     */
    private LocalDateTime syncTime;

    /**
     * 创建人ID
     */
    private Long createUserId;

    /**
     * 创建人姓名
     */
    private String createUserName;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 删除状态：0 否，1 是
     */
    private Boolean deletedFlag;
}
