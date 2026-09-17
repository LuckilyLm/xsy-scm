package com.xsy.scm.admin.module.business.finance.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 会计凭证详情 返回对象
 *
 * @author xsy-scm
 */
@Data
public class FinanceVoucherDetailVO extends FinanceVoucherVO {

    @Schema(description = "凭证分录")
    private List<VoucherEntryVO> entries;
}
