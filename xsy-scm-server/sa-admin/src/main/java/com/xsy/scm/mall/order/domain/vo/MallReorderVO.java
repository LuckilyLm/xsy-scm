package com.xsy.scm.mall.order.domain.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 再来一单结果：按当前可见性与客户价重新校验后写入购物车，逐条给出跳过原因。 */
@Data
public class MallReorderVO {
    /** 成功加入购物车的明细行数 */
    private Integer addedCount = 0;
    /** 跳过明细 */
    private List<Skipped> skipped = new ArrayList<>();

    @Data
    public static class Skipped {
        private Long skuId;
        private String productName;
        /** 跳过原因，直接面向客户展示 */
        private String reason;
    }
}
