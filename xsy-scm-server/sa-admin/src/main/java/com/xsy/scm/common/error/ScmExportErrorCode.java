package com.xsy.scm.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 同步导出超过行数上限时的公共错误码。
 */
@Getter
@RequiredArgsConstructor
public enum ScmExportErrorCode implements ScmErrorCode {

    EXPORT_ROW_LIMIT_EXCEEDED(41112, "当前筛选结果超过导出行数上限，请缩小查询范围后重试");

    private final int code;

    private final String msg;
}
