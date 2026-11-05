package com.xsy.scm.base.module.support.reload.core.domain;

import lombok.Data;

/**
 * reload项目
 *
 */
@Data
public class XsyReloadItem {

    /**
     * 项名称
     */
    private String tag;

    /**
     * 参数
     */
    private String args;

    /**
     * 标识
     */
    private String identification;

}
