package net.lab1024.sa.admin.module.scm.pricing.domain.form;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.OffsetDateTime;
import net.lab1024.sa.base.common.domain.PageParam;
@Data @EqualsAndHashCode(callSuper=true) public class PriceHistoryQueryForm extends PageParam {
 private String source; private Long customerId; private Long customerTypeId; private Long skuId;
 private String operationType; private OffsetDateTime effectiveFrom; private OffsetDateTime effectiveTo;
 private OffsetDateTime operatedFrom; private OffsetDateTime operatedTo;
}
