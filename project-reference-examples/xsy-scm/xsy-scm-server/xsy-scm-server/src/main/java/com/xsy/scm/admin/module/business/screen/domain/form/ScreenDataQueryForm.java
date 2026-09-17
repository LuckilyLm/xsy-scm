package com.xsy.scm.admin.module.business.screen.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 大屏数据 查询表单
 *
 * <p>startTime / endTime 为空时默认统计「今日」。</p>
 *
 * @author xsy-scm
 */
@Data
public class ScreenDataQueryForm {

    @Schema(description = "统计开始时间")
    private LocalDateTime startTime;

    @Schema(description = "统计结束时间")
    private LocalDateTime endTime;
}
