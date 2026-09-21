package net.lab1024.sa.admin.module.scm.pricing.domain.vo;

import java.util.List;

public record PriceBatchResultVO(String batchKey, boolean committed, int rowCount, List<Long> ids,
                                 List<PriceBatchRowFailureVO> failures) {
}
