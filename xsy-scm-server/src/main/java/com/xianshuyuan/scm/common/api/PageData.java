package com.xianshuyuan.scm.common.api;

import java.util.List;

public record PageData<T>(List<T> records, long page, long pageSize, long total) {

    public PageData {
        records = List.copyOf(records);
    }
}
