package com.xsy.scm.device.domain.vo;

import java.time.OffsetDateTime;
import java.util.List;

import lombok.Data;

/**
 * 秤端作业投影：一个分拣任务对应一个作业。
 *
 * <p>
 * 标识与数量一律用字符串交互：业务主键可能超过 JavaScript 安全整数范围，数量用字符串可以 保留原始小数而不经过浮点。{@code jobId} 取任务主键，不另造一套与任务并行的作业编号。
 */
@Data
public class ScaleJobVO {

    private String jobId;

    private String taskId;

    private String taskNo;

    /**
     * 任务版本；秤端领取时原样带回，用于发现改派或重开。
     */
    private String taskVersion;

    /**
     * 作业版本；与 {@code taskVersion} 同源，单独给出是因为协议里两者分属不同概念。
     */
    private String jobVersion;

    /**
     * 租约到期时间；过期后只能查看历史，不能继续写业务。
     */
    private OffsetDateTime expiresAt;

    /**
     * 已持有该作业的设备编号；为空表示尚未被领取。
     */
    private String claimedByDeviceCode;

    /**
     * 任务状态；秤端据此区分可作业与已结束，不自行推断。
     */
    private String status;

    private List<ScaleJobItemVO> items;
}
