package com.xsy.scm.customer.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.time.OffsetDateTime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xsy.scm.customer.dao.CustomerSkuVisibilityDao;
import com.xsy.scm.customer.constant.CustomerVisibilityPolicy;
import com.xsy.scm.customer.domain.entity.CustomerSkuVisibilityEntity;
import com.xsy.scm.customer.domain.form.CustomerSkuVisibilityItemForm;
import com.xsy.scm.customer.domain.form.CustomerVisibilityQueryForm;
import com.xsy.scm.customer.domain.vo.CustomerSkuVisibilityReverseVO;
import com.xsy.scm.customer.domain.vo.CustomerSkuVisibilityVO;
import com.xsy.scm.product.dao.ProductSkuOptionDao;
import com.xsy.scm.pricing.manager.PriceValidation;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeContext;
import com.xsy.scm.common.scope.ScmDataScopeService;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.util.SmartPageUtil;

import static com.xsy.scm.customer.constant.CustomerErrorCode.CUSTOMER_NOT_FOUND;
import static com.xsy.scm.customer.constant.CustomerErrorCode.VISIBILITY_ITEM_INVALID;
import static com.xsy.scm.customer.constant.CustomerErrorCode.VISIBILITY_NOT_OWNED;
import static com.xsy.scm.customer.constant.CustomerErrorCode.VISIBILITY_POLICY_CONFLICT;
import static com.xsy.scm.customer.constant.CustomerErrorCode.VISIBILITY_SKU_NOT_SELLABLE;
import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;

@Service
@RequiredArgsConstructor
public class CustomerSkuVisibilityService {
    private final CustomerSkuVisibilityDao customerSkuVisibilityDao;
    private final ProductSkuOptionDao productSkuOptionDao;
    private final ScmDataScopeService dataScopeService;

    public List<CustomerSkuVisibilityVO> list(Long customerId) {
        return existingForCustomer(customerId).stream()
                .map(customerVisibility -> new CustomerSkuVisibilityVO(
                        customerVisibility.getId(), customerVisibility.getVersion(), customerVisibility.getSkuId()))
                .toList();
    }

    private List<CustomerSkuVisibilityEntity> existingForCustomer(Long customerId) {
        return customerSkuVisibilityDao.selectList(new LambdaQueryWrapper<CustomerSkuVisibilityEntity>()
                .eq(CustomerSkuVisibilityEntity::getCustomerId, customerId));
    }

    public PageResult<CustomerSkuVisibilityReverseVO> reverse(CustomerVisibilityQueryForm form) {
        // 反向列表的主体是客户，因此与客户列表同一套归属范围：读不到客户的人也不该看到它的商品白名单。
        ScmDataScopeContext scope = dataScopeService.resolve();
        if (scope.getCustomerSellerScope().isEmpty()) {
            return ScmDataScopeService.emptyPage(form);
        }
        form.setSortItemList(List.of());
        var page = SmartPageUtil.convert2PageQuery(form);
        return SmartPageUtil.convert2PageResult(
                page, customerSkuVisibilityDao.reverse(page, form, scope.getCustomerSellerScope()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void replace(Long customerId, String visibilityPolicy,
                        List<CustomerSkuVisibilityItemForm> requestedVisibilities) {
        // 调用方在「只改可见性策略、不动清单」时不会带 visibilities，此时 null 与空清单同义：
        // 按空清单收敛，否则切回 ALL_ENABLED 这个唯一合法请求会被 40034 拒掉，
        // 而那个错误码描述的是清单项非法，与真实原因无关。策略取值本身仍要校验。
        if (requestedVisibilities == null) requestedVisibilities = List.of();
        if (!CustomerVisibilityPolicy.ALL_ENABLED.equals(visibilityPolicy)
                && !CustomerVisibilityPolicy.ALLOWLIST.equals(visibilityPolicy))
            throw new ScmBusinessException(VISIBILITY_ITEM_INVALID);
        if (CustomerVisibilityPolicy.ALL_ENABLED.equals(visibilityPolicy) && !requestedVisibilities.isEmpty())
            throw new ScmBusinessException(VISIBILITY_POLICY_CONFLICT);
        if (customerSkuVisibilityDao.lockCustomer(customerId) == null) {
            throw new ScmBusinessException(CUSTOMER_NOT_FOUND);
        }
        var existingVisibilities = existingForCustomer(customerId);
        var existingVisibilityById = new HashMap<Long, CustomerSkuVisibilityEntity>();
        var existingVisibilityBySkuId = new HashMap<Long, CustomerSkuVisibilityEntity>();
        existingVisibilities.forEach(existingVisibility -> {
            existingVisibilityById.put(existingVisibility.getId(), existingVisibility);
            existingVisibilityBySkuId.put(existingVisibility.getSkuId(), existingVisibility);
        });
        Set<Long> retainedVisibilityIds = new HashSet<>();
        Set<Long> requestedSkuIds = new HashSet<>();
        for (var requestedVisibility : requestedVisibilities) {
            if (requestedVisibility == null || requestedVisibility.getSkuId() == null
                    || !requestedSkuIds.add(requestedVisibility.getSkuId())
                    || (requestedVisibility.getId() == null
                    && (requestedVisibility.getVersion() != null
                    || existingVisibilityBySkuId.containsKey(requestedVisibility.getSkuId())))
                    || (requestedVisibility.getId() != null && requestedVisibility.getVersion() == null)) {
                throw new ScmBusinessException(VISIBILITY_ITEM_INVALID);
            }
            if (requestedVisibility.getId() != null
                    && existingVisibilityById.containsKey(requestedVisibility.getId())
                    && !Objects.equals(existingVisibilityById.get(requestedVisibility.getId()).getSkuId(),
                    requestedVisibility.getSkuId())) {
                throw new ScmBusinessException(VISIBILITY_ITEM_INVALID);
            }
        }
        for (var requestedVisibility : requestedVisibilities) {
            if (requestedVisibility.getId() != null) {
                var existingVisibility = existingVisibilityById.get(requestedVisibility.getId());
                if (existingVisibility == null) {
                    throw new ScmBusinessException(VISIBILITY_NOT_OWNED);
                }
                if (!Objects.equals(existingVisibility.getVersion(), requestedVisibility.getVersion())) {
                    throw new ScmBusinessException(VERSION_CONFLICT);
                }
                retainedVisibilityIds.add(requestedVisibility.getId());
            }
        }
        if (!requestedSkuIds.isEmpty()) {
            var skuOptions = productSkuOptionDao.selectByIds(new ArrayList<>(requestedSkuIds));
            if (skuOptions.size() != requestedSkuIds.size()
                    || skuOptions.stream()
                    .anyMatch(skuOption -> PriceValidation.unavailable(skuOption, true) != null)) {
                throw new ScmBusinessException(VISIBILITY_SKU_NOT_SELLABLE);
            }
        }
        for (var existingVisibility : existingVisibilities) {
            if (!retainedVisibilityIds.contains(existingVisibility.getId())
                    && customerSkuVisibilityDao.softDelete(existingVisibility.getId(), existingVisibility.getVersion(),
                    customerId, ScmOperator.current()) != 1) {
                throw new ScmBusinessException(VERSION_CONFLICT);
            }
        }
        for (var requestedVisibility : requestedVisibilities) {
            var customerVisibility = requestedVisibility.getId() == null
                    ? new CustomerSkuVisibilityEntity()
                    : existingVisibilityById.get(requestedVisibility.getId());
            customerVisibility.setCustomerId(customerId);
            customerVisibility.setSkuId(requestedVisibility.getSkuId());
            customerVisibility.setUpdatedAt(OffsetDateTime.now());
            customerVisibility.setUpdatedBy(ScmOperator.current());
            if (customerVisibility.getId() == null) {
                customerVisibility.setCreatedAt(customerVisibility.getUpdatedAt());
                customerVisibility.setCreatedBy(customerVisibility.getUpdatedBy());
                customerSkuVisibilityDao.insert(customerVisibility);
            } else if (customerSkuVisibilityDao.updateById(customerVisibility) != 1) {
                throw new ScmBusinessException(VERSION_CONFLICT);
            }
        }
    }
}
