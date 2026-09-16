package net.lab1024.sa.admin.module.scm.order.manager;

import net.lab1024.sa.admin.module.scm.order.domain.entity.*;
import net.lab1024.sa.admin.module.scm.order.domain.form.*;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import static net.lab1024.sa.admin.module.scm.order.constant.OrderErrorCode.*;
import java.math.BigDecimal;
import java.util.*;
public final class OrderValidator {
    private OrderValidator() {}
    public static String trim(String value) {return value==null||value.isBlank()?null:value.trim();}
    public static void reason(String value,net.lab1024.sa.admin.module.scm.common.error.ScmErrorCode code) {
        if(trim(value)==null) throw new ScmBusinessException(code);
    }
    public static BigDecimal decimal(String value,boolean positive) {
        if(value==null || !value.matches("[0-9]{1,14}\\.[0-9]{4}")) throw new ScmBusinessException(positive?ORDER_QUANTITY_INVALID:ORDER_PRICE_INVALID);
        var result=new BigDecimal(value);
        if(positive&&result.signum()<=0) throw new ScmBusinessException(ORDER_QUANTITY_INVALID);
        return result;
    }
    public static void draft(SalesOrderAddForm f) {
        if(f.getItems()==null||f.getItems().isEmpty()) throw new ScmBusinessException(ORDER_QUANTITY_INVALID);
        if("SUPPLEMENT".equals(f.getOrderSource())) reason(f.getSupplementReason(),ORDER_SUPPLEMENT_REASON_REQUIRED);
        else if(f.getOriginalOrderId()!=null||trim(f.getSupplementReason())!=null) throw new ScmBusinessException(ORDER_SUPPLEMENT_INVALID);
        var seen=new HashSet<Long>();
        for(var x:f.getItems()) {
            if(x.getSkuId()==null||!seen.add(x.getSkuId())) throw new ScmBusinessException(ORDER_SKU_DUPLICATE);
            decimal(x.getOrderedQuantity(),true);
            if(Boolean.TRUE.equals(x.getManualPriceOverride())) {
                reason(x.getOverrideReason(),ORDER_PRICE_OVERRIDE_REASON_REQUIRED);
                if(x.getUnitPrice()==null) throw new ScmBusinessException(ORDER_PRICE_OVERRIDE_REASON_REQUIRED);
                decimal(x.getUnitPrice(),false);
            } else if(x.getUnitPrice()!=null||trim(x.getOverrideReason())!=null) throw new ScmBusinessException(ORDER_PRICE_OVERRIDE_INVALID);
        }
    }
}
