package com.xsy.scm.print.domain.form;

import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.print.constant.ScmPrintDocumentTypeEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.Data;

/**
 * 打印模板新建 / 编辑（编辑时带 {@code id} 与 {@code version}）。
 *
 * <p>
 * {@code model} 收成 {@code Map} 而不是模型对象，是为了让「结构不对」由服务端校验器统一回答
 * （{@code MODEL_INVALID}），而不是让 Jackson 在反序列化阶段抛一个字段位置不明的 400。
 */
@Data
public class ScmPrintTemplateForm {

    /**
     * 编辑时必填；新建时忽略。
     */
    private Long id;

    @NotBlank(message = "单据类型不能为空")
    @ScmEnumValue(enumClass = ScmPrintDocumentTypeEnum.class, message = "单据类型无效")
    private String documentType;

    @NotBlank(message = "模板编码不能为空")
    @Size(max = 64, message = "模板编码不能超过64个字符")
    @Pattern(regexp = "[A-Za-z0-9_\\-]+", message = "模板编码只能包含字母、数字、下划线与连字符")
    private String templateCode;

    @NotBlank(message = "模板名称不能为空")
    @Size(max = 100, message = "模板名称不能超过100个字符")
    private String templateName;

    /**
     * 是否设为默认模板；为空按不设为默认处理。同类型至多一个默认。
     */
    private Boolean defaultFlag;

    /**
     * 是否启用；为空按启用处理。
     */
    private Boolean enabledFlag;

    @NotNull(message = "模板内容不能为空")
    private Map<String, Object> model;

    @Size(max = 200, message = "备注不能超过200个字符")
    private String remark;

    /**
     * 编辑时的乐观锁版本；新建时可为空。
     */
    private Integer version;
}
