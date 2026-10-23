package com.xsy.scm.device.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 设备上的员工授权会话，对应秤端请求头 {@code X-Operator-Session}。
 *
 * <p>
 * 服务端据此解析真实员工，业务写入的 {@code sorted_by} / {@code accepted_by} 取该员工。 客户端不得自报员工 ID，否则秤端可以伪造成任意员工做分拣确认。
 */
@Data
@TableName(value = "xsy_scale_device_session")
public class ScaleDeviceSessionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long deviceId;

    private Long employeeId;

    /** 会话令牌摘要；不保存明文。 */
    private String sessionTokenHash;

    private OffsetDateTime issuedAt;

    private OffsetDateTime expiresAt;

    /** 会话结束时间；结束原因写入 {@code endReason}，不删除历史。 */
    private OffsetDateTime endedAt;

    private String endReason;

    private OffsetDateTime createdAt;

    private String createdBy;
}
