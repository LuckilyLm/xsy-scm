package com.xsy.scm.mall.cart.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.xsy.scm.common.json.ScmFixedScale4Serializer;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class MallCartVO {
    private List<MallCartItemVO> items = new ArrayList<>();
    /** 可结算（可售且有客户价）行的预估金额合计；非标品最终以实重结算 */
    @JsonSerialize(using = ScmFixedScale4Serializer.class)
    private BigDecimal estimatedAmount = BigDecimal.ZERO;
    /** 可结算行数，用于「去结算(n)」 */
    private Integer availableCount = 0;
    private Integer totalCount = 0;
}
