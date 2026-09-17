package com.xsy.scm.admin.module.business.external.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 外部平台同步方向
 *
 * 数据库字段：t_external_sync_log.sync_type
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum ExternalSyncTypeEnum implements BaseEnum {

    /**
     * 1 上报（推送给外部平台）
     */
    PUSH(1, "上报"),

    /**
     * 2 拉取（从外部平台获取）
     */
    PULL(2, "拉取"),

    ;

    private final Integer value;

    private final String desc;
}
