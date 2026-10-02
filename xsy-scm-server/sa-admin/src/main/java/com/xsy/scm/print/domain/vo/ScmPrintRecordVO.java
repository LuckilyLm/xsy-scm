package com.xsy.scm.print.domain.vo;

import java.time.OffsetDateTime;
import lombok.Data;

/**
 * 正式打印记录（列表与重印入口）。
 *
 * <p>
 * 列表不带快照正文：快照可能很大，列表只需要回答「谁在什么时候按哪份模板的哪一版打了哪张单」。
 * 重印时再按 id 取快照。
 */
@Data
public class ScmPrintRecordVO {

    private Long id;

    private String documentType;

    private String documentTypeLabel;

    private Long businessId;

    private String businessNo;

    private Long templateId;

    private String templateCode;

    private String templateName;

    private Integer templateVersion;

    private OffsetDateTime printedAt;

    private String printedBy;
}
