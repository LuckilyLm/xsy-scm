package com.xsy.scm.mall.promotion.domain.form;

import com.xsy.scm.common.validation.ScmEnumValue;
import com.xsy.scm.mall.promotion.constant.MallCouponTabEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MallCouponQueryForm extends MallPromotionPageForm {
    @ScmEnumValue(enumClass = MallCouponTabEnum.class, message = "优惠券分类无效")
    private String tab;
}
