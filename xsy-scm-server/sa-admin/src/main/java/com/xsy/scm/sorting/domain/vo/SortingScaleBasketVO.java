package com.xsy.scm.sorting.domain.vo;

import java.time.OffsetDateTime;

import lombok.Data;

/**
 * 一筐读数的受理结果。
 *
 * <p>
 * {@code accepted} 恒为 {@code false}：这里只表示服务端已收到并保存，正式分拣结果要等人工完成商品后才变化。
 */
@Data
public class SortingScaleBasketVO {

    private Long measurementId;

    private String eventKey;

    private String operationId;

    private Integer basketNo;

    private String status;

    /** 同一幂等键重复提交且内容一致时为 {@code true}。 */
    private Boolean duplicated;

    private Boolean accepted;

    private OffsetDateTime receivedAt;
}
