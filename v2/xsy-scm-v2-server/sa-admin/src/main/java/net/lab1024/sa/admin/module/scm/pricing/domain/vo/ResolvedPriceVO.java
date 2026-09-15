package net.lab1024.sa.admin.module.scm.pricing.domain.vo;

import lombok.Data;
import java.math.BigDecimal;
import net.lab1024.sa.admin.module.scm.pricing.constant.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmFixedScale4Serializer;
/** Price availability and sale eligibility are independent. Zero is a price. */
@Data
public class ResolvedPriceVO {
    private Long skuId;
    private String skuCode;
    private String productName;
    private String specName;
    @JsonSerialize(using=ScmFixedScale4Serializer.class,nullsUsing=ScmFixedScale4Serializer.class)
    private BigDecimal unitPrice;
    private ScmPriceStatusEnum priceStatus;
    private ScmPriceSourceEnum priceSource;
    private Long sourceRecordId;
    private ScmUnpricedReasonEnum unpricedReason;
    private boolean sellable;
    private ScmUnavailableReasonEnum unavailableReason;
    public void price(BigDecimal amount, ScmPriceSourceEnum source, Long recordId) {
        unitPrice=amount;
        priceStatus=amount==null ? ScmPriceStatusEnum.UNPRICED : ScmPriceStatusEnum.PRICED;
        priceSource=amount==null ? null : source;
        sourceRecordId=amount==null ? null : recordId;
        unpricedReason=amount==null ? ScmUnpricedReasonEnum.NO_PRICE_SOURCE : null;
    }
}
