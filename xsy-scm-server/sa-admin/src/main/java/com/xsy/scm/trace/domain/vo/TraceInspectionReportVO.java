package com.xsy.scm.trace.domain.vo;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Data;

@Data
public class TraceInspectionReportVO {
    private Long id;
    private Long lotId;
    private String reportNo;
    private String reportName;
    private LocalDate inspectionDate;
    private String inspectionOrg;
    private String conclusion;
    private OffsetDateTime expiresAt;
    private String status;
    private String publishedBy;
    private OffsetDateTime publishedAt;
    private String withdrawnBy;
    private OffsetDateTime withdrawnAt;
    private String createdBy;
    private OffsetDateTime createdAt;
    private String updatedBy;
    private OffsetDateTime updatedAt;
    private Integer version;
    private boolean expired;
    private List<TraceAttachmentVO> attachments;
}
