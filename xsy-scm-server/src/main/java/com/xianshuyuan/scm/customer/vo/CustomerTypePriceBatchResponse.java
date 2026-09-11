package com.xianshuyuan.scm.customer.vo;

import java.util.List;

public record CustomerTypePriceBatchResponse(String batchKey, int rowCount, List<Long> ids) {
}
