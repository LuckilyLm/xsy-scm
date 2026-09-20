package net.lab1024.sa.admin.module.scm.product.domain.vo;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import java.util.*;
/**
 * 批量命令的结果：预校验失败时 updatedCount 为 0 且没有任何写入，失败行可逐条定位；
 * 全部通过时 failedCount 为 0。整批在一个事务里应用，不会部分成功。
 */
@Data
public class ProductBatchResultVO {
private int updatedCount;
private int failedCount;
private List<Failure> failures = new ArrayList<>();

    @Data
    @RequiredArgsConstructor
    public static class Failure {
        private final Long spuId;
        private final String spuCode;
        private final int reasonCode;
        private final String reasonMsg;
    }
}
