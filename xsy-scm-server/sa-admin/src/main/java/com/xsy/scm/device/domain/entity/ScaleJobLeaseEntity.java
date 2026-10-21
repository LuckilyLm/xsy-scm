package com.xsy.scm.device.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * 秤端领取分拣任务的工作租约。同一任务同时只允许一个有效租约，由数据库部分唯一索引仲裁。
 *
 * <p>
 * {@code taskVersion} 是领取时的任务版本：任务被改派或重开导致版本变化后，旧租约不能再写业务， 必须重新领取。不能只靠设备端禁用按钮保证这一点。
 */
@Data
@TableName(value = "xsy_scale_job_lease")
public class ScaleJobLeaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long deviceId;

    private Long sessionId;

    private Long employeeId;

    private Long taskId;

    private Integer taskVersion;

    private OffsetDateTime leasedAt;

    private OffsetDateTime expiresAt;

    /** 释放时间；为空表示租约仍有效。 */
    private OffsetDateTime releasedAt;

    private String releaseReason;
}
