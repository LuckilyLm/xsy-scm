package com.xsy.scm.trace.domain.vo;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import lombok.Data;
import com.xsy.scm.common.json.ScmOperatorNameSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

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
    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String publishedBy;
    private OffsetDateTime publishedAt;
    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String withdrawnBy;
    private OffsetDateTime withdrawnAt;
    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String createdBy;
    private OffsetDateTime createdAt;
    @JsonSerialize(using = ScmOperatorNameSerializer.class)
    private String updatedBy;
    private OffsetDateTime updatedAt;
    private Integer version;
    private boolean expired;
    private List<TraceAttachmentVO> attachments;
}
