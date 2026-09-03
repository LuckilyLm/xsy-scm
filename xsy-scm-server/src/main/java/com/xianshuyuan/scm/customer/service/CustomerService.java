package com.xianshuyuan.scm.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xianshuyuan.scm.common.exception.BusinessException;
import com.xianshuyuan.scm.customer.converter.CustomerConverter;
import com.xianshuyuan.scm.customer.dto.*;
import com.xianshuyuan.scm.customer.entity.*;
import com.xianshuyuan.scm.customer.mapper.*;
import com.xianshuyuan.scm.product.mapper.ProductSkuMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service public class CustomerService {
 private final CustomerMapper customers; private final CustomerTypeMapper types; private final CustomerSkuVisibilityMapper visibility; private final ProductSkuMapper skus;
 public CustomerService(CustomerMapper c,CustomerTypeMapper t,CustomerSkuVisibilityMapper v,ProductSkuMapper s){customers=c;types=t;visibility=v;skus=s;}
 public CustomerEntity require(long id){var e=customers.selectById(id);if(e==null||Boolean.TRUE.equals(e.getDeleted()))throw new BusinessException(CustomerErrorCodes.CUSTOMER_NOT_FOUND);return e;}
 public CustomerEntity requireEnabled(long id){var e=require(id);if(e.getStatus()!=EnabledStatus.ENABLED)throw new BusinessException(CustomerErrorCodes.CUSTOMER_DISABLED);return e;}
 private void requireType(long id){var t=types.selectById(id);if(t==null||Boolean.TRUE.equals(t.getDeleted())||t.getStatus()!=EnabledStatus.ENABLED)throw new BusinessException(CustomerErrorCodes.CUSTOMER_TYPE_NOT_FOUND);}
 @Transactional public long create(CustomerSaveRequest r){requireType(r.customerTypeId());validateVisibilityRequest(r);validateSkuIds(r);var e=CustomerConverter.toCustomer(r);e.setVersion(0);e.setDeleted(false);e.setCreatedBy("SYSTEM");customers.insert(e);insertVisibility(e.getId(),r.visibilities());return e.getId();}
 @Transactional public void update(long id,CustomerSaveRequest r){require(id);requireType(r.customerTypeId());validateVisibilityRequest(r);validateSkuIds(r);if(r.version()==null)throw new BusinessException(CustomerErrorCodes.VERSION_CONFLICT);var changes=CustomerSkuVisibilityChangeSet.between(visibility.selectActiveByCustomerId(id),r.visibilities());var e=CustomerConverter.toCustomer(r);e.setId(id);if(customers.updateById(e)!=1)throw new BusinessException(CustomerErrorCodes.VERSION_CONFLICT);for(var x:changes.updated()){if(x.version()==null)throw new BusinessException(CustomerErrorCodes.VERSION_CONFLICT);var v=new CustomerSkuVisibilityEntity();v.setId(x.id());v.setCustomerId(id);v.setSkuId(x.skuId());v.setVersion(x.version());v.setUpdatedBy("SYSTEM");if(visibility.updateById(v)!=1)throw new BusinessException(CustomerErrorCodes.VERSION_CONFLICT);}insertVisibility(id,changes.inserted());if(!changes.removedIds().isEmpty())visibility.softDeleteOwned(id,changes.removedIds());}
 @Transactional public void updateStatus(long id,int version,EnabledStatus status){var e=require(id);e.setVersion(version);e.setStatus(status);e.setUpdatedBy("SYSTEM");if(customers.updateById(e)!=1)throw new BusinessException(CustomerErrorCodes.VERSION_CONFLICT);}
 @Transactional public void delete(long id,int version){require(id);var rows=visibility.selectActiveByCustomerId(id);if(!rows.isEmpty())visibility.softDeleteOwned(id,rows.stream().map(CustomerSkuVisibilityEntity::getId).toList());if(customers.softDelete(id,version)!=1)throw new BusinessException(CustomerErrorCodes.VERSION_CONFLICT);}
 public List<CustomerTypeEntity> listTypes(){return types.selectList(new LambdaQueryWrapper<CustomerTypeEntity>().orderByAsc(CustomerTypeEntity::getName));}
 @Transactional public long createType(CustomerTypeSaveRequest r){var e=CustomerConverter.toType(r);e.setVersion(0);e.setDeleted(false);e.setCreatedBy("SYSTEM");types.insert(e);return e.getId();}
 @Transactional public void updateType(long id,CustomerTypeSaveRequest r){if(types.selectById(id)==null)throw new BusinessException(CustomerErrorCodes.CUSTOMER_TYPE_NOT_FOUND);if(r.version()==null)throw new BusinessException(CustomerErrorCodes.VERSION_CONFLICT);var e=CustomerConverter.toType(r);e.setId(id);if(types.updateById(e)!=1)throw new BusinessException(CustomerErrorCodes.VERSION_CONFLICT);}
 private void validateVisibilityRequest(CustomerSaveRequest r){var duplicate=r.visibilities().stream().map(CustomerSkuVisibilityRequest::skuId).distinct().count()!=r.visibilities().size();if(duplicate)throw new BusinessException(CustomerErrorCodes.SKU_NOT_VISIBLE,"SKU 可见性重复");if(r.visibilityPolicy()==VisibilityPolicy.ALL_ENABLED&&!r.visibilities().isEmpty())throw new BusinessException(CustomerErrorCodes.SKU_NOT_VISIBLE,"全部可见策略不能提交可见性明细");}
 private void validateSkuIds(CustomerSaveRequest r){if(r.visibilityPolicy()==VisibilityPolicy.ALLOWLIST&&!r.visibilities().isEmpty()){var ids=r.visibilities().stream().map(CustomerSkuVisibilityRequest::skuId).distinct().toList();if(skus.selectBatchIds(ids).size()!=ids.size())throw new BusinessException(CustomerErrorCodes.SKU_NOT_VISIBLE);}}
 private void insertVisibility(long customerId,List<CustomerSkuVisibilityRequest> rows){for(var x:rows){var v=new CustomerSkuVisibilityEntity();v.setCustomerId(customerId);v.setSkuId(x.skuId());v.setVersion(0);v.setDeleted(false);v.setCreatedBy("SYSTEM");visibility.insert(v);}}
}
