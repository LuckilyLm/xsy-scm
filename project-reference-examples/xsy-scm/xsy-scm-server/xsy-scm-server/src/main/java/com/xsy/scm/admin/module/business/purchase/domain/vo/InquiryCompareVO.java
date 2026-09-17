package com.xsy.scm.admin.module.business.purchase.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 询价方案对比 返回对象
 *
 * @author xsy-scm
 */
@Data
public class InquiryCompareVO {

    @Schema(description = "询价单ID")
    private Long inquiryId;

    @Schema(description = "询价单号")
    private String inquiryNo;

    @Schema(description = "询价单名称")
    private String inquiryName;

    @Schema(description = "各商品对比结果")
    private List<InquiryCompareItemVO> items;
}
