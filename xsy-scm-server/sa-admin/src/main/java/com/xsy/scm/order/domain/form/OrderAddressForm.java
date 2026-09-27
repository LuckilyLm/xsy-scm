package com.xsy.scm.order.domain.form;

import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.xsy.scm.common.json.ScmStrictDecimalStringDeserializer;

@Data
public class OrderAddressForm {
    @NotBlank(message = "收货人姓名不能为空")
    @Size(max = 100, message = "收货人姓名不能超过100个字符")
    private String receiverName;
    @NotBlank(message = "收货人手机号不能为空")
    @Size(max = 32, message = "收货人手机号不能超过32个字符")
    private String receiverPhone;
    @NotBlank(message = "收货地址不能为空")
    @Size(max = 500, message = "收货地址不能超过500个字符")
    private String address;
}
