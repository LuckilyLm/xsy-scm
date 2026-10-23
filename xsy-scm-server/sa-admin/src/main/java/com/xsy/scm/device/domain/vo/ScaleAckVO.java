package com.xsy.scm.device.domain.vo;

import java.time.OffsetDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备接口的确认应答。只表示服务端已收到并处理，不代表任何业务结果已确认。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScaleAckVO {

    private Boolean accepted;

    private OffsetDateTime receivedAt;
}
