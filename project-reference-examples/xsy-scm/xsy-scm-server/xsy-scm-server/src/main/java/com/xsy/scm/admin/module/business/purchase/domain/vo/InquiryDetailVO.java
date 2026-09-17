package com.xsy.scm.admin.module.business.purchase.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 询价单详情 返回对象
 *
 * @author xsy-scm
 */
@Data
public class InquiryDetailVO extends InquiryVO {

    @Schema(description = "询价明细")
    private List<InquiryItemVO> items;
}
