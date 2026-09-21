package net.lab1024.sa.admin.module.scm.order.domain.form;

import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import net.lab1024.sa.admin.module.scm.common.json.ScmStrictDecimalStringDeserializer;
import net.lab1024.sa.base.common.domain.PageParam;

@Data
@EqualsAndHashCode(callSuper = true)
public class SalesOrderQueryForm extends PageParam {
    private Long orderId;
    private Long customerId;
    @Size(max = 150)
    private String keyword;
    @Size(max = 30)
    private String status;
    @Size(max = 30)
    private String orderSource;
    @Size(max = 40)
    private String operationType;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
}
