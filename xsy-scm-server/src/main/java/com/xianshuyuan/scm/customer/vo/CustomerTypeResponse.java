package com.xianshuyuan.scm.customer.vo;

import com.xianshuyuan.scm.customer.entity.EnabledStatus;

public record CustomerTypeResponse(Long id, Integer version, String typeCode, String name, EnabledStatus status) {
}
