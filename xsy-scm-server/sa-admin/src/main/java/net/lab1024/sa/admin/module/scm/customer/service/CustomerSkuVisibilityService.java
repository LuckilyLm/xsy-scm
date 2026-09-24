package net.lab1024.sa.admin.module.scm.customer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import net.lab1024.sa.admin.module.scm.customer.dao.CustomerSkuVisibilityDao;
import net.lab1024.sa.admin.module.scm.customer.domain.entity.CustomerSkuVisibilityEntity;
import net.lab1024.sa.admin.module.scm.customer.domain.form.*;
import net.lab1024.sa.admin.module.scm.customer.domain.vo.*;
import net.lab1024.sa.admin.module.scm.product.dao.ProductSkuOptionDao;
import net.lab1024.sa.admin.module.scm.product.domain.vo.ProductSkuOptionVO;
import net.lab1024.sa.admin.module.scm.pricing.manager.PriceValidation;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeContext;
import net.lab1024.sa.admin.module.scm.common.scope.ScmDataScopeService;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;

import static net.lab1024.sa.admin.module.scm.customer.constant.CustomerErrorCode.*;
import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;

@Service
@RequiredArgsConstructor
public class CustomerSkuVisibilityService {
    private final CustomerSkuVisibilityDao dao;
    private final ProductSkuOptionDao skus;
    private final ScmDataScopeService scopeService;

    public List<CustomerSkuVisibilityVO> list(Long id) {
        return existing(id).stream().map(e -> new CustomerSkuVisibilityVO(e.getId(), e.getVersion(), e.getSkuId())).toList();
    }

    private List<CustomerSkuVisibilityEntity> existing(Long id) {
        return dao.selectList(new LambdaQueryWrapper<CustomerSkuVisibilityEntity>().eq(CustomerSkuVisibilityEntity::getCustomerId, id));
    }

    public PageResult<CustomerSkuVisibilityReverseVO> reverse(CustomerVisibilityQueryForm form) {
        // 反向列表的主体是客户，因此与客户列表同一套归属范围：读不到客户的人也不该看到它的商品白名单。
        ScmDataScopeContext scope = scopeService.resolve();
        if (scope.getCustomerSellerScope().isEmpty()) {
            return ScmDataScopeService.emptyPage(form);
        }
        form.setSortItemList(List.of());
        var page = SmartPageUtil.convert2PageQuery(form);
        return SmartPageUtil.convert2PageResult(page, dao.reverse(page, form, scope.getCustomerSellerScope()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void replace(Long customerId, String policy, List<CustomerSkuVisibilityItemForm> requested) {
        if (requested == null || (!"ALL_ENABLED".equals(policy) && !"ALLOWLIST".equals(policy)))
            throw new ScmBusinessException(VISIBILITY_ITEM_INVALID);
        if ("ALL_ENABLED".equals(policy) && !requested.isEmpty())
            throw new ScmBusinessException(VISIBILITY_POLICY_CONFLICT);
        if (dao.lockCustomer(customerId) == null) throw new ScmBusinessException(CUSTOMER_NOT_FOUND);
        var old = existing(customerId);
        var byId = new HashMap<Long, CustomerSkuVisibilityEntity>();
        var bySku = new HashMap<Long, CustomerSkuVisibilityEntity>();
        old.forEach(e -> {
            byId.put(e.getId(), e);
            bySku.put(e.getSkuId(), e);
        });
        Set<Long> ids = new HashSet<>(), skuIds = new HashSet<>();
        for (var r : requested) {
            if (r == null || r.getSkuId() == null || !skuIds.add(r.getSkuId()) || (r.getId() == null && (r.getVersion() != null || bySku.containsKey(r.getSkuId()))) || (r.getId() != null && r.getVersion() == null))
                throw new ScmBusinessException(VISIBILITY_ITEM_INVALID);
            if (r.getId() != null && byId.containsKey(r.getId()) && !Objects.equals(byId.get(r.getId()).getSkuId(), r.getSkuId()))
                throw new ScmBusinessException(VISIBILITY_ITEM_INVALID);
        }
        for (var r : requested)
            if (r.getId() != null) {
                var e = byId.get(r.getId());
                if (e == null) throw new ScmBusinessException(VISIBILITY_NOT_OWNED);
                if (!Objects.equals(e.getVersion(), r.getVersion())) throw new ScmBusinessException(VERSION_CONFLICT);
                ids.add(r.getId());
            }
        if (!skuIds.isEmpty()) {
            var options = skus.selectByIds(new ArrayList<>(skuIds));
            if (options.size() != skuIds.size() || options.stream().anyMatch(s -> PriceValidation.unavailable(s, true) != null))
                throw new ScmBusinessException(VISIBILITY_SKU_NOT_SELLABLE);
        }
        for (var e : old)
            if (!ids.contains(e.getId()) && dao.softDelete(e.getId(), e.getVersion(), customerId, ScmOperator.current()) != 1)
                throw new ScmBusinessException(VERSION_CONFLICT);
        for (var r : requested) {
            var e = r.getId() == null ? new CustomerSkuVisibilityEntity() : byId.get(r.getId());
            e.setCustomerId(customerId);
            e.setSkuId(r.getSkuId());
            e.setUpdatedAt(OffsetDateTime.now());
            e.setUpdatedBy(ScmOperator.current());
            if (e.getId() == null) {
                e.setCreatedAt(e.getUpdatedAt());
                e.setCreatedBy(e.getUpdatedBy());
                dao.insert(e);
            } else if (dao.updateById(e) != 1) throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }
}
