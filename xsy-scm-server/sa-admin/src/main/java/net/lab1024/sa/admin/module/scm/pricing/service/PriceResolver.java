package net.lab1024.sa.admin.module.scm.pricing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.lab1024.sa.admin.module.scm.pricing.dao.*;
import net.lab1024.sa.admin.module.scm.pricing.domain.vo.*;
import net.lab1024.sa.admin.module.scm.pricing.domain.entity.*;
import net.lab1024.sa.admin.module.scm.pricing.constant.*;
import net.lab1024.sa.admin.module.scm.pricing.manager.PriceValidation;
import net.lab1024.sa.admin.module.scm.customer.service.CustomerService;
import net.lab1024.sa.admin.module.scm.customer.dao.CustomerTypeDao;
import net.lab1024.sa.admin.module.scm.customer.dao.CustomerSkuVisibilityDao;
import net.lab1024.sa.admin.module.scm.product.dao.ProductSkuOptionDao;
import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductSkuOptionVO;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import static net.lab1024.sa.admin.module.scm.pricing.constant.PricingErrorCode.*;
@Service @RequiredArgsConstructor public class PriceResolver {
 private final CustomerService customers;
 private final CustomerTypeDao types;
 private final AgreementPriceDao agreements;
 private final CustomerTypePriceDao typePrices;
 private final ProductSkuOptionDao skus;
 private final CustomerSkuVisibilityDao visibility;
 public PriceResolveResultVO preview(Long customerId,List<Long> ids,OffsetDateTime at) {
  var customer=customers.requireTradable(customerId);
  var type=types.selectById(customer.getCustomerTypeId());
  if(type==null || !"ENABLED".equals(type.getStatus())) throw new ScmBusinessException(PRICE_RESOLVE_CUSTOMER_TYPE_MISSING);
  var instant=at==null?OffsetDateTime.now():at;
  return new PriceResolveResultVO(customerId,type.getId(),type.getName(),instant,resolve(customerId,ids,instant));
 }
 public List<ResolvedPriceVO> resolve(Long customerId,List<Long> ids,OffsetDateTime at) {
  if(ids.isEmpty()) return List.of();
  var customer=customers.requireTradable(customerId);
  var type=types.selectById(customer.getCustomerTypeId());
  if(type==null || !"ENABLED".equals(type.getStatus())) throw new ScmBusinessException(PRICE_RESOLVE_CUSTOMER_TYPE_MISSING);
  var instant=at==null?OffsetDateTime.now():at;
  var byId=skus.selectByIds(ids).stream().collect(Collectors.toMap(ProductSkuOptionVO::getSkuId,Function.identity()));
  var a=agreements.selectEffective(customerId,ids,instant).stream().collect(Collectors.toMap(AgreementPriceEntity::getSkuId,Function.identity(),(first,next)->first));
  var t=typePrices.selectEffective(type.getId(),ids,instant).stream().collect(Collectors.toMap(CustomerTypePriceEntity::getSkuId,Function.identity(),(first,next)->first));
  boolean all="ALL_ENABLED".equals(visibility.policy(customerId));
  Set<Long> visible=all?Set.of():new HashSet<>(visibility.visibleIds(customerId,ids));
  return ids.stream().map(id->{
   var row=new ResolvedPriceVO(); row.setSkuId(id); var sku=byId.get(id);
   if(sku!=null) {row.setSkuCode(sku.getSkuCode());row.setProductName(sku.getProductName());row.setSpecName(sku.getSpecName());}
   var reason=PriceValidation.unavailable(sku,all||visible.contains(id));
   row.setUnavailableReason(reason);row.setSellable(reason==null);
   // Q2: eligibility never short-circuits price lookup or erases a valid zero price.
   if(a.containsKey(id)) {var p=a.get(id);row.price(p.getUnitPrice(),ScmPriceSourceEnum.AGREEMENT,p.getId());}
   else if(t.containsKey(id)) {var p=t.get(id);row.price(p.getUnitPrice(),ScmPriceSourceEnum.CUSTOMER_TYPE,p.getId());}
   else row.price(sku==null?null:sku.getMarketPrice(),ScmPriceSourceEnum.MARKET,null);
   return row;
  }).toList();
 }
 public List<ResolvedPriceVO> requireResolvable(Long customerId,List<Long> ids,OffsetDateTime at) {
  var rows=resolve(customerId,ids,at);
  if(rows.stream().anyMatch(r->!r.isSellable()||r.getPriceStatus()==ScmPriceStatusEnum.UNPRICED)) throw new ScmBusinessException(SKU_NOT_SELLABLE);
  return rows;
 }
}
