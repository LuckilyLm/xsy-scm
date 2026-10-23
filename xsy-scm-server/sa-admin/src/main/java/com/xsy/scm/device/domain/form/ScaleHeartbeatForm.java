package com.xsy.scm.device.domain.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 设备心跳，对应秤端 {@code POST /agents/heartbeat}。
 *
 * <p>
 * 心跳只更新观测字段。它证明设备在线，但不证明云端没有遗漏事件，因此不能作为对账依据。
 */
@Data
public class ScaleHeartbeatForm {

    @Size(max = 64, message = "设备编码最多 64 个字符")
    private String deviceCode;

    @Size(max = 40, message = "设备程序版本最多 40 个字符")
    private String agentVersion;

    @Size(max = 40, message = "协议类型最多 40 个字符")
    private String protocolType;

    private Boolean scaleConnected;

    /**
     * 最近一次成功读数的采集时间；设备失联后该值会停止推进。
     */
    private OffsetDateTime lastWeightAt;

    /**
     * 打印机连接状态；驱动不提供可信反馈时为 null，不能推断为已连接。
     */
    private Boolean printerConnected;

    private Boolean printerQueueAvailable;

    @Min(value = 0, message = "待同步数量不能为负数")
    private Integer queueDepth;

    private OffsetDateTime sentAt;
}
