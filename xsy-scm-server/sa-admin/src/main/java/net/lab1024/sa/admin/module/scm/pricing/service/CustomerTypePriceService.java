package net.lab1024.sa.admin.module.scm.pricing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.BeanUtils;
import java.time.OffsetDateTime;
import java.util.Objects;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.lab1024.sa.admin.module.scm.pricing.dao.CustomerTypePriceDao;
import net.lab1024.sa.admin.module.scm.pricing.domain.entity.CustomerTypePriceEntity;
import net.lab1024.sa.admin.module.scm.pricing.domain.form.*;
import net.lab1024.sa.admin.module.scm.pricing.manager.PriceValidation;
import net.lab1024.sa.admin.module.scm.customer.service.CustomerService;
import net.lab1024.sa.admin.module.scm.customer.service.CustomerTypeService;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.util.ScmDecimalStrings;
import static net.lab1024.sa.admin.module.scm.pricing.constant.PricingErrorCode.*;
import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
@Service @RequiredArgsConstructor public class CustomerTypePriceService {
    private final CustomerTypePriceDao dao;
    private final CustomerService customers;
    private final CustomerTypeService types;
    private final PriceValidation validation;
    private final ObjectMapper json;
    @Transactional(rollbackFor=Exception.class)
    public Long add(CustomerTypePriceAddForm form) {
        validateAndLock(form,null);
        var entity=new CustomerTypePriceEntity(); apply(entity,form);
        dao.insert(entity); log(entity,"CREATE",null); return entity.getId();
    }
    @Transactional(rollbackFor=Exception.class)
    public void update(CustomerTypePriceUpdateForm form) {
        // Lock old and new parents in ascending order if the dimension is changed.
        var existing=require(form.getCustomerTypePriceId(),form.getVersion());
        java.util.stream.Stream.of(existing.getCustomerTypeId(),form.getCustomerTypeId()).filter(Objects::nonNull).distinct().sorted().forEach(dao::lockParent);
        existing=require(form.getCustomerTypePriceId(),form.getVersion());
        validateAndLock(form,existing.getId());
        String before=snapshot(existing);
        apply(existing,form); existing.setVersion(form.getVersion());
        if(dao.updateById(existing)!=1) throw new ScmBusinessException(VERSION_CONFLICT);
        log(existing,"UPDATE",before);
    }
    @Transactional(rollbackFor=Exception.class)
    public void delete(CustomerTypePriceDeleteForm form) {
        var e=require(form.getCustomerTypePriceId(),form.getVersion()); String before=snapshot(e);
        if(dao.softDelete(e.getId(),form.getVersion(),ScmOperator.current())!=1) throw new ScmBusinessException(VERSION_CONFLICT);
        e.setDeleted(true); e.setVersion(e.getVersion()+1); log(e,"DELETE",before);
    }
    private CustomerTypePriceEntity require(Long id,Integer version) {
        var e=dao.selectById(id); if(e==null) throw new ScmBusinessException(CUSTOMER_TYPE_PRICE_NOT_FOUND);
        if(!Objects.equals(version,e.getVersion())) throw new ScmBusinessException(VERSION_CONFLICT); return e;
    }
    private void validateAndLock(CustomerTypePriceAddForm form,Long exclude) {
        PriceValidation.amountAndPeriod(form.getUnitPrice(),form.getEffectiveFrom(),form.getEffectiveTo());
        if(form.getCustomerTypeId()==null) throw new ScmBusinessException(PRICE_BATCH_ROW_INVALID);
        if(dao.lockParent(form.getCustomerTypeId())==null) { types.requireSelectableType(form.getCustomerTypeId()); }
        types.requireSelectableType(form.getCustomerTypeId());
        validation.requireSellable(form.getSkuId());
        if(dao.countOverlapping(form.getCustomerTypeId(),form.getSkuId(),form.getEffectiveFrom(),form.getEffectiveTo(),exclude)>0) throw new ScmBusinessException(CUSTOMER_TYPE_PRICE_OVERLAP);
    }
    private void apply(CustomerTypePriceEntity e,CustomerTypePriceAddForm f) {
        e.setCustomerTypeId(f.getCustomerTypeId()); e.setSkuId(f.getSkuId()); e.setUnitPrice(ScmDecimalStrings.parseScale4(f.getUnitPrice()));
        e.setEffectiveFrom(f.getEffectiveFrom()); e.setEffectiveTo(f.getEffectiveTo());
        e.setUpdatedAt(OffsetDateTime.now());e.setUpdatedBy(ScmOperator.current());
        if(e.getId()==null) {e.setCreatedAt(e.getUpdatedAt());e.setCreatedBy(e.getUpdatedBy());}
    }
    private String snapshot(CustomerTypePriceEntity e) {
        var fields=new java.util.LinkedHashMap<String,Object>(); fields.put("id",e.getId());fields.put("customerTypeId",e.getCustomerTypeId());fields.put("skuId",e.getSkuId());
        fields.put("unitPrice",e.getUnitPrice().setScale(4).toPlainString());fields.put("effectiveFrom",e.getEffectiveFrom().toString());fields.put("effectiveTo",e.getEffectiveTo()==null?null:e.getEffectiveTo().toString());fields.put("version",e.getVersion());fields.put("deleted",e.getDeleted());
        try {return json.writeValueAsString(fields);} catch(com.fasterxml.jackson.core.JsonProcessingException ex) {throw new IllegalStateException("Cannot serialize price audit",ex);}
    }
    private void log(CustomerTypePriceEntity e,String action,String before) {dao.log(e.getId(),action,ScmOperator.current(),before,snapshot(e));}
}
