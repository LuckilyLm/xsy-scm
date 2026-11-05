package com.xsy.scm.base.common.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import com.xsy.scm.base.common.enumeration.SystemEnvironmentEnum;

/**
 * 系统环境
 *
 */
@AllArgsConstructor
@Getter
public class SystemEnvironment {

    /**
     * 是否位生产环境
     */
    private boolean isProd;

    /**
     * 项目名称
     */
    private String projectName;

    /**
     * 当前环境
     */
    private SystemEnvironmentEnum currentEnvironment;
}
