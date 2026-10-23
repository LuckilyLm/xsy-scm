package com.xsy.scm.trace.domain.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

import lombok.Data;

/**
 * 同步某个业务对象当前引用的附件：列表里没有的旧附件会被撤销授权。
 *
 * <p>
 * 传空列表表示清空，不传与传空在语义上一致——附件列表是「当前状态」，不是增量追加。
 */
@Data
public class TraceAttachmentSaveForm {

    @Size(max = 20, message = "一个对象最多关联 20 个附件")
    private List<@NotBlank(message = "附件标识不能为空") String> fileKeys;
}
