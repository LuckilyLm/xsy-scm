package com.xsy.scm.trace.domain.vo;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;

/** 公开投影不包含内部主键、操作人或草稿。 */
@Data
public class TracePublicInspectionReportVO {
    private String reportNo;
    private String reportName;
    private LocalDate inspectionDate;
    private String inspectionOrg;
    private String conclusion;
    private OffsetDateTime expiresAt;
    private String status;
    private boolean expired;
    private List<TraceAttachmentVO> attachments;
}
