package com.xsy.scm.device.domain.form;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

/** 电子秤设备台账查询；数据范围按绑定仓库收窄。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScaleDeviceQueryForm extends PageParam {
    @Size(max = 100, message = "关键词不能超过100个字符")
    private String keyword;
    @Size(max = 32, message = "状态不能超过32个字符")
    private String status;
    private Long warehouseId;
}
