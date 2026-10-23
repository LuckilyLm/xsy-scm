package com.xsy.scm.device.domain.vo;

import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 一筐读数的受理结果。
 *
 * <p>
 * {@code accepted} 恒为 {@code false}：只表示服务端已保存，正式分拣结果要等集合完成才变化。
 */
@Data
public class ScaleMeasurementVO {

    private Long measurementId;

    private String eventKey;

    private Integer basketNo;

    private String status;

    private Boolean duplicated;

    private Boolean accepted;

    private OffsetDateTime receivedAt;
}
