package com.xsy.scm.trace.domain.vo;

import lombok.Data;

/**
 * 溯源附件。
 *
 * <p>
 * 只带元数据，不带 URL：读取 URL 要按调用者身份判定授权，所以下载统一走文件接口， 由那一侧再做一次鉴权。公开扫码页同理，下载走溯源域的公开下载接口。
 */
@Data
public class TraceAttachmentVO {

    private String fileKey;

    private String fileName;

    private String fileType;

    private Integer fileSize;

    /** 见 {@code TraceAttachmentCategoryEnum}。 */
    private String category;
}
