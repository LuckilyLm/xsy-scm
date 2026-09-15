package net.lab1024.sa.admin.module.scm.pricing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import net.lab1024.sa.admin.module.scm.pricing.dao.*;
import net.lab1024.sa.admin.module.scm.pricing.domain.form.*;
import net.lab1024.sa.admin.module.scm.pricing.domain.vo.*;
import net.lab1024.sa.admin.module.scm.pricing.manager.PriceValidation;
import net.lab1024.sa.admin.module.scm.customer.dao.CustomerTypeDao;
import net.lab1024.sa.admin.module.scm.product.dao.ProductSkuOptionDao;
import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductSkuOptionVO;
import net.lab1024.sa.admin.module.scm.customer.domain.entity.CustomerTypeEntity;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import static net.lab1024.sa.admin.module.scm.pricing.constant.PricingErrorCode.PRICE_BATCH_KEY_DUPLICATE;
@Service @RequiredArgsConstructor public class PriceBatchWriter {
 private final CustomerTypePriceDao prices; private final CustomerTypePriceService service;
 private final CustomerTypeDao types; private final ProductSkuOptionDao skus; private final PriceBatchAuditDao audits;
 public static class Rejected extends RuntimeException {
  public final List<PriceBatchRowFailureVO> failures;
  public Rejected(List<PriceBatchRowFailureVO> failures) {this.failures=List.copyOf(failures);}
 }
 @Transactional(rollbackFor=Exception.class)
 public PriceBatchResultVO write(PriceBatchForm form) {
  if(audits.successCount(form.getBatchKey())>0) throw new ScmBusinessException(PRICE_BATCH_KEY_DUPLICATE);
  var rows=form.getRows().stream().sorted(Comparator.comparing(PriceBatchRowForm::getCustomerTypeId)).toList();
  rows.stream().map(PriceBatchRowForm::getCustomerTypeId).distinct().forEach(prices::lockParent);
  var typeMap=new HashMap<Long,CustomerTypeEntity>();types.selectByIds(rows.stream().map(PriceBatchRowForm::getCustomerTypeId).distinct().toList()).forEach(t->typeMap.put(t.getId(),t));
  var skuMap=new HashMap<Long,ProductSkuOptionVO>();skus.selectByIds(rows.stream().map(PriceBatchRowForm::getSkuId).distinct().toList()).forEach(s->skuMap.put(s.getSkuId(),s));
  List<PriceBatchRowFailureVO> failures=new ArrayList<>();
  for(var r:rows) {
   var type=typeMap.get(r.getCustomerTypeId());
   if(type==null || !"ENABLED".equals(type.getStatus())) failures.add(new PriceBatchRowFailureVO(r.getRowNumber(),r.getCustomerTypeId(),r.getSkuId(),40431,"客户类型不存在或已停用"));
   if(PriceValidation.unavailable(skuMap.get(r.getSkuId()),true)!=null) failures.add(new PriceBatchRowFailureVO(r.getRowNumber(),r.getCustomerTypeId(),r.getSkuId(),40949,"SKU 不可售"));
   if(prices.countOverlapping(r.getCustomerTypeId(),r.getSkuId(),r.getEffectiveFrom(),r.getEffectiveTo(),null)>0) failures.add(new PriceBatchRowFailureVO(r.getRowNumber(),r.getCustomerTypeId(),r.getSkuId(),40935,"客户类型价有效期重叠"));
  }
  if(!failures.isEmpty()) throw new Rejected(failures);
  List<Long> ids=new ArrayList<>();
  for(var r:rows) {var f=new CustomerTypePriceAddForm();f.setCustomerTypeId(r.getCustomerTypeId());f.setSkuId(r.getSkuId());f.setUnitPrice(r.getUnitPrice());f.setEffectiveFrom(r.getEffectiveFrom());f.setEffectiveTo(r.getEffectiveTo());ids.add(service.add(f));}
  audits.insert(form.getBatchKey(),"SUCCESS",rows.size(),null,ScmOperator.current());
  return new PriceBatchResultVO(form.getBatchKey(),true,ids.size(),ids,List.of());
 }
}
