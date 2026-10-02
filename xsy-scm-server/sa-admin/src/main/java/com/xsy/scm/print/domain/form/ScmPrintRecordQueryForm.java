package com.xsy.scm.print.domain.form;

import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.print.constant.ScmPrintDocumentTypeEnum;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 打印记录列表查询。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScmPrintRecordQueryForm extends PageParam {

    @ScmEnumValue(enumClass = ScmPrintDocumentTypeEnum.class, message = "单据类型无效")
    private String documentType;

    /**
     * 业务单号模糊匹配（采购单号等）。
     */
    @Size(max = 64, message = "业务单号不能超过64个字符")
    private String businessNo;

    @Override
    @Min(value = 1, message = "页码必须至少为1")
    public Long getPageNum() {
        return super.getPageNum();
    }

    @Override
    @Max(value = 100, message = "每页条数不能超过100")
    @Min(value = 1, message = "每页条数必须至少为1")
    public Long getPageSize() {
        return super.getPageSize();
    }
}
