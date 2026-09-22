package net.lab1024.sa.admin.module.scm.delivery.domain.form;

import lombok.Data;
import jakarta.validation.constraints.*;

import java.util.List;

/**
 * 按订单正式生成打印：提交预览时确认的具体订单集合，不接受含义不明的「ALL」。
 */
@Data
public class DeliveryPrintOrdersForm {
    @NotNull
    @Min(0)
    private Integer version;
    @NotEmpty
    @Size(max = 500)
    private List<@NotNull @Positive Long> orderIds;
}
