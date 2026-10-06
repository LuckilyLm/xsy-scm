package com.xsy.scm.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xsy.scm.common.constant.ScmCustomerStatusEnum;
import com.xsy.scm.common.constant.ScmOperator;
import com.xsy.scm.common.constant.ScmSettleModeEnum;
import com.xsy.scm.common.error.ScmCommonErrorCode;
import com.xsy.scm.common.exception.ScmBusinessException;
import com.xsy.scm.common.scope.ScmDataScopeException;
import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.common.util.ScmDecimalStrings;
import com.xsy.scm.customer.constant.CustomerErrorCode;
import com.xsy.scm.customer.constant.CustomerVisibilityPolicy;
import com.xsy.scm.customer.dao.CustomerDao;
import com.xsy.scm.customer.dao.CustomerSkuVisibilityDao;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.domain.form.CustomerAddForm;
import com.xsy.scm.customer.domain.form.CustomerDeleteForm;
import com.xsy.scm.customer.domain.form.CustomerSellerReassignForm;
import com.xsy.scm.customer.domain.form.CustomerStatusForm;
import com.xsy.scm.customer.domain.form.CustomerUpdateForm;
import com.xsy.scm.customer.manager.CustomerValidator;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.domain.RequestUser;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.xsy.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static com.xsy.scm.customer.constant.CustomerErrorCode.CUSTOMER_CODE_DUPLICATE;
import static com.xsy.scm.customer.constant.CustomerErrorCode.CUSTOMER_NOT_FOUND;
import static com.xsy.scm.customer.constant.CustomerErrorCode.CUSTOMER_NOT_TRADABLE;

/**
 * 客户写路径。
 *
 * <p>
 * 所有写方法都在事务内，并按固定顺序执行：校验 → 读取并比对版本 → 归属校验 → 查重 → 落库。顺序固定是为了让并发场景下的失败原因可预测（版本冲突永远先于编码冲突暴露）。
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

    /**
     * 新建客户的初始状态。
     */
    public static final String INITIAL_STATUS = ScmCustomerStatusEnum.POTENTIAL.name();

    private final CustomerDao customerDao;
    private final ScmDataScopeService dataScopeService;
    private final CustomerSkuVisibilityService customerSkuVisibilityService;
    private final CustomerSkuVisibilityDao customerSkuVisibilityDao;

    private final CustomerValidator customerValidator;

    private final CustomerTypeService customerTypeService;

    /**
     * 读取客户，不存在或已删除 → 40430。
     */
    public CustomerEntity require(Long customerId) {
        CustomerEntity entity = customerId == null ? null : customerDao.selectById(customerId);
        if (entity == null) {
            throw new ScmBusinessException(CUSTOMER_NOT_FOUND);
        }
        return entity;
    }

    /**
     * 读取客户并校验乐观锁版本：不存在 → 40430，版本不一致 → 40921。
     */
    public CustomerEntity require(Long customerId, Integer version) {
        CustomerEntity entity = require(customerId);
        if (!Objects.equals(entity.getVersion(), version)) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
        return entity;
    }

    /**
     * 读取「可交易」客户。
     *
     * <p>
     * 订单创建、提交和确认都通过此方法校验交易资格，避免各服务自行比较状态。
     */
    public CustomerEntity requireSettlementCustomer(CustomerEntity customer) {
        return requireSettlementAccount(customer, true);
    }

    /** Resolve a legally configured settlement account without changing historical finance tradability rules. */
    public CustomerEntity requireSettlementAccount(CustomerEntity customer) {
        return requireSettlementAccount(customer, false);
    }

    private CustomerEntity requireSettlementAccount(CustomerEntity customer, boolean requireTradable) {
        Long settlementCustomerId = customer.getSettlementCustomerId() == null
                ? customer.getId()
                : customer.getSettlementCustomerId();
        CustomerEntity settlement = requireTradable
                ? requireTradable(settlementCustomerId)
                : require(settlementCustomerId);
        boolean self = Objects.equals(customer.getId(), settlement.getId());
        boolean directParent = Objects.equals(customer.getParentCustomerId(), settlement.getId());
        boolean groupMode = ScmSettleModeEnum.GROUP.name().equals(customer.getSettleMode())
                && ScmSettleModeEnum.GROUP.name().equals(settlement.getSettleMode());
        boolean settlementIsGroup = CustomerValidator.GROUP_TYPE_CODE
                .equals(customerTypeService.require(settlement.getCustomerTypeId()).getTypeCode());
        // 结算主体只允许自己或直接上级。兄弟客户即使拥有同一 parent，也不能互相充当结算主体；
        // 集团统一结算必须由 GROUP 类型、GROUP 模式且自身结算的权威父客户承担。
        if ((!self && !(directParent && groupMode && settlementIsGroup))
                || !Objects.equals(settlement.getSettlementCustomerId(), settlement.getId())) {
            throw new ScmBusinessException(CustomerErrorCode.CUSTOMER_PARENT_INVALID);
        }
        return settlement;
    }

    /** Historical documents retain their settlement account even after group membership changes. */
    public CustomerEntity lockCreditAccount(Long settlementCustomerId) {
        CustomerEntity locked = customerDao.lock(settlementCustomerId);
        if (locked == null) {
            throw new ScmBusinessException(CUSTOMER_NOT_FOUND);
        }
        return locked;
    }

    public CustomerEntity requireTradable(Long customerId) {
        CustomerEntity entity = require(customerId);
        if (!ScmCustomerStatusEnum.valueOf(entity.getStatus()).tradable()) {
            throw new ScmBusinessException(CUSTOMER_NOT_TRADABLE);
        }
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long add(CustomerAddForm form) {
        customerValidator.validateCreditPeriod(form);
        customerTypeService.requireSelectableType(form.getCustomerTypeId());
        customerValidator.validateParent(form.getParentCustomerId(), null);

        String code = CustomerValidator.normalizeCode(form.getCustomerCode());
        if (existsCode(code, null)) {
            throw new ScmBusinessException(CUSTOMER_CODE_DUPLICATE);
        }

        CustomerEntity entity = new CustomerEntity();
        apply(entity, form);
        entity.setSellerId(resolveSellerOnCreate(form.getSellerId()));
        entity.setStatus(INITIAL_STATUS);
        entity.setVersion(0);
        entity.setDeleted(false);
        stamp(entity, true);
        try {
            customerDao.insert(entity);
            if (entity.getSettlementCustomerId() == null) {
                entity.setSettlementCustomerId(entity.getId());
            }
        } catch (DuplicateKeyException e) {
            throw new ScmBusinessException(CUSTOMER_CODE_DUPLICATE);
        }
        if (form.getVisibilityPolicy() != null || form.getVisibilities() != null)
            customerSkuVisibilityService.replace(entity.getId(), entity.getVisibilityPolicy(), form.getVisibilities());
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(CustomerUpdateForm form) {
        customerValidator.validateCreditPeriod(form);
        customerSkuVisibilityDao.lockCustomer(form.getCustomerId());
        CustomerEntity entity = require(form.getCustomerId(), form.getVersion());
        customerTypeService.requireSelectableType(form.getCustomerTypeId());
        customerValidator.validateParent(form.getParentCustomerId(), form.getCustomerId());

        String code = CustomerValidator.normalizeCode(form.getCustomerCode());
        if (existsCode(code, form.getCustomerId())) {
            throw new ScmBusinessException(CUSTOMER_CODE_DUPLICATE);
        }

        // apply 不触碰 status；状态只能通过 updateStatus 变更。
        // 也不触碰 seller_id —— 归属只能通过 reassignSeller 变更（裁决 第 6 条）：
        // 编辑表单里的 sellerId 对任何角色都只是回显值，否则普通销售把客户回传成别人的 id
        // 就能把它挪出自己的范围（或挪进别人的范围），行级范围随之失效。
        apply(entity, form);
        entity.setVersion(form.getVersion());
        stamp(entity, false);
        try {
            if (customerDao.updateById(entity) != 1) {
                throw new ScmBusinessException(VERSION_CONFLICT);
            }
        } catch (DuplicateKeyException e) {
            throw new ScmBusinessException(CUSTOMER_CODE_DUPLICATE);
        }
        if (form.getVisibilityPolicy() != null || form.getVisibilities() != null)
            customerSkuVisibilityService.replace(entity.getId(), entity.getVisibilityPolicy(), form.getVisibilities());
    }

    /**
     * 改派客户业务归属（独立端点，权限 {@code scm:customer:assign}）。
     *
     * <p>
     * 与 {@link #update} 同一把客户行锁 + 同一套版本比对，改派与编辑因此互斥：两个动作都在动「这行归谁」这件事的两种口径，不能一个走乐观锁一个不走。
     *
     * <p>
     * 权限判定在 Controller 的 {@code @SaCheckPermission} 上，本方法不再重复判断： Service 被别的写路径复用时，调用方必须自己带着范围判定。
     */
    @Transactional(rollbackFor = Exception.class)
    public void reassignSeller(CustomerSellerReassignForm form) {
        customerSkuVisibilityDao.lockCustomer(form.getCustomerId());
        CustomerEntity entity = require(form.getCustomerId(), form.getVersion());
        entity.setSellerId(form.getSellerId());
        entity.setVersion(form.getVersion());
        stamp(entity, false);
        if (customerDao.updateById(entity) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /**
     * 新建客户的归属：无分配权者一律记在当前员工名下，客户端传来的 {@code sellerId} 忽略。
     *
     * <p>
     * 这是行级范围能成立的前提——否则「建在别人名下、再按列表去读别人的客户」就是留着的口子。有分配权者（销售主管 / 超管）可以指定别人，也可以留空表示<b>暂不分配</b>；未分配客户只对持分配权或全量范围者可见（见
     * {@code ScmValueScope#allows}）。
     *
     * <p>
     * 取不到当前员工时直接拒绝而不是落成未分配：落成 NULL 会让这条客户对建它的人自己不可见。
     */
    private Long resolveSellerOnCreate(Long submittedSellerId) {
        if (ScmDataScopeService.hasPermission(ScmDataScopeService.CUSTOMER_ASSIGN_PERM)) {
            return submittedSellerId;
        }
        RequestUser requestUser = SmartRequestUtil.getRequestUser();
        Long employeeId = requestUser instanceof RequestEmployee employee ? employee.getEmployeeId() : null;
        if (employeeId == null) {
            throw new ScmDataScopeException();
        }
        return employeeId;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(CustomerStatusForm form) {
        CustomerEntity entity = require(form.getCustomerId(), form.getVersion());
        entity.setStatus(form.getStatus());
        entity.setVersion(form.getVersion());
        stamp(entity, false);
        if (customerDao.updateById(entity) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /**
     * 删除客户（软删）。
     *
     * <p>
     * 使用 {@code id + version} 原子谓词，而不是先查后改：并发删除时只有一次能成功。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(CustomerDeleteForm form) {
        customerSkuVisibilityDao.lockCustomer(form.getCustomerId());
        require(form.getCustomerId(), form.getVersion());
        assertNotReferenced(form.getCustomerId());
        if (customerDao.softDelete(form.getCustomerId(), form.getVersion(), ScmOperator.current()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /**
     * 活动记录内编码是否已存在（编码大小写不敏感：先归一化再比较）。
     */
    public boolean existsCode(String normalizedCode, Long excludeId) {
        LambdaQueryWrapper<CustomerEntity> wrapper = new LambdaQueryWrapper<CustomerEntity>()
                .eq(CustomerEntity::getCustomerCode, normalizedCode);
        if (excludeId != null) {
            wrapper.ne(CustomerEntity::getId, excludeId);
        }
        return customerDao.selectCount(wrapper) > 0;
    }

    /**
     * 删除前检查客户 SKU 可见性引用。
     *
     * <p>
     * 销售订单引用由订单域拦截器在软删除前检查，避免客户域直接依赖订单表。
     */
    private void assertNotReferenced(Long customerId) {
        if (customerSkuVisibilityDao.customerReferences(customerId) > 0)
            throw new ScmBusinessException(CustomerErrorCode.CUSTOMER_REFERENCED);
    }

    private void apply(CustomerEntity entity, CustomerAddForm form) {
        if (form.getVisibilityPolicy() != null)
            entity.setVisibilityPolicy(form.getVisibilityPolicy());
        else if (entity.getVisibilityPolicy() == null) {
            entity.setVisibilityPolicy(CustomerVisibilityPolicy.ALL_ENABLED);
        }
        entity.setCustomerCode(CustomerValidator.normalizeCode(form.getCustomerCode()));
        entity.setName(CustomerValidator.normalizeName(form.getName()));
        entity.setCustomerTypeId(form.getCustomerTypeId());
        entity.setSettleMode(form.getSettleMode());
        entity.setParentCustomerId(form.getParentCustomerId());
        Long settlementCustomerId = form.getSettlementCustomerId();
        entity.setSettlementCustomerId(settlementCustomerId == null ? entity.getId() : settlementCustomerId);
        if (settlementCustomerId != null && !Objects.equals(settlementCustomerId, entity.getId())) {
            CustomerEntity settlement = requireSettlementCustomer(entity);
            if (!dataScopeService.resolve().getCustomerSellerScope().allows(settlement.getSellerId())) {
                throw new ScmDataScopeException();
            }
        }
        // 归属只由创建时解析或专用改派命令变更。
        entity.setSupplierId(form.getSupplierId());
        entity.setContactName(CustomerValidator.normalizeOptional(form.getContactName()));
        entity.setContactPhone(CustomerValidator.normalizeOptional(form.getContactPhone()));
        entity.setAddress(CustomerValidator.normalizeOptional(form.getAddress()));
        entity.setProvinceCode(form.getProvinceCode());
        entity.setProvinceName(CustomerValidator.normalizeOptional(form.getProvinceName()));
        entity.setCityCode(form.getCityCode());
        entity.setCityName(CustomerValidator.normalizeOptional(form.getCityName()));
        entity.setDistrictCode(form.getDistrictCode());
        entity.setDistrictName(CustomerValidator.normalizeOptional(form.getDistrictName()));
        if (!form.isLocationComplete())
            throw new ScmBusinessException(ScmCommonErrorCode.VALIDATION_ERROR);
        entity.setLongitude(form.getLongitude());
        entity.setLatitude(form.getLatitude());
        entity.setGeomCrs(form.getGeomCrs());
        entity.setRemark(CustomerValidator.normalizeOptional(form.getRemark()));

        // 授信额度列非空，缺省按 0 处理；其余账期字段保持可空语义
        BigDecimal creditLimit = ScmDecimalStrings.parseScale4(form.getCreditLimit());
        entity.setCreditLimit(creditLimit == null ? BigDecimal.ZERO.setScale(ScmDecimalStrings.SCALE) : creditLimit);
        entity.setCreditPeriodType(form.getCreditPeriodType());
        entity.setCreditAmountThreshold(ScmDecimalStrings.parseScale4(form.getCreditAmountThreshold()));
        entity.setCreditPeriodValue(form.getCreditPeriodValue());
        entity.setCreditPeriodUnit(form.getCreditPeriodUnit());
        entity.setSettleDay(form.getSettleDay());
    }

    private void stamp(CustomerEntity entity, boolean creating) {
        OffsetDateTime now = OffsetDateTime.now();
        String operator = ScmOperator.current();
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(operator);
        if (creating) {
            entity.setCreatedAt(now);
            entity.setCreatedBy(operator);
        }
    }
}
