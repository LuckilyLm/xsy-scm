package com.xsy.scm.print.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.xsy.scm.common.error.ScmErrorCode;

/**
 * 打印域使用 41301–41310 错误码（41150/412xx 已被报表域占用）。
 *
 * <p>
 * 「不属于你的单据」不走这里的业务码：那等于回答「这个 id 存在但你不该看」，
 * 由各域既有的范围判定统一回 30005，打印不新增一套可见性回答。
 */
@Getter
@RequiredArgsConstructor
public enum ScmPrintErrorCode implements ScmErrorCode {

    TEMPLATE_NOT_FOUND(41301, "打印模板不存在或已被删除"),

    TEMPLATE_CODE_DUPLICATED(41302, "同类型下已存在相同编码的打印模板"),

    TEMPLATE_IN_USE(41303, "默认模板不能删除，请先指定其它模板为默认"),

    DOCUMENT_TYPE_UNSUPPORTED(41304, "该单据类型暂不支持可配置打印"),

    MODEL_INVALID(41305, "打印模板内容不合法"),

    FIELD_NOT_ALLOWED(41306, "模板包含该单据类型不支持的字段"),

    COLUMN_REQUIRED(41307, "打印模板至少需要选择一列明细字段"),

    PAPER_ORIENTATION_INVALID(41308, "该纸张不支持横向"),

    TEXT_NOT_PLAIN(41309, "标题与页脚备注只能是纯文本，不能包含尖括号"),

    RECORD_NOT_FOUND(41310, "打印记录不存在");

    private final int code;
    private final String msg;
}
