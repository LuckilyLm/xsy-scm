package com.xsy.scm.admin.module.business.external.constant;

import com.xsy.scm.base.common.enumeration.BaseEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 外部平台同步状态
 *
 * 数据库字段：t_external_sync_log.sync_status
 *
 * @author xsy-scm
 */
@AllArgsConstructor
@Getter
public enum ExternalSyncStatusEnum implements BaseEnum {

    /**
     * 1 成功
     */
    SYNCED(1, "成功"),

    /**
     * 2 失败
     */
    FAILED(2, "失败"),

    /**
     * 3 待同步（排队中 / 重试待处理）
     */
    PENDING(3, "待同步"),

    ;

    private final Integer value;

    private final String desc;
}
