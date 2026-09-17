package com.xsy.scm.admin.module.business.finance.domain.form;

import com.xsy.scm.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 会计凭证 分页查询表单
 *
 * @author xsy-scm
 */
@Data
public class VoucherQueryForm extends PageParam {

    @Schema(description = "搜索词（凭证号 / 外部单号）")
    private String searchWord;

    @Schema(description = "凭证类型：1 收款，2 付款，3 应收，4 应付，5 费用")
    private Integer voucherType;

    @Schema(description = "同步状态：1 未同步，2 同步中，3 同步成功，4 同步失败")
    private Integer syncStatus;

    @Schema(description = "凭证状态：1 已生成，2 已作废")
    private Integer status;

    @Schema(hidden = true)
    private Boolean deletedFlag;
}
