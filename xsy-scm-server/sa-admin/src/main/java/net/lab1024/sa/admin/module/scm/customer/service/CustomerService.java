package net.lab1024.sa.admin.module.scm.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.admin.module.scm.common.constant.ScmCustomerStatusEnum;
import net.lab1024.sa.admin.module.scm.common.constant.ScmOperator;
import net.lab1024.sa.admin.module.scm.common.exception.ScmBusinessException;
import net.lab1024.sa.admin.module.scm.common.util.ScmDecimalStrings;
import net.lab1024.sa.admin.module.scm.customer.dao.CustomerDao;
import net.lab1024.sa.admin.module.scm.customer.domain.entity.CustomerEntity;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerAddForm;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerDeleteForm;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerStatusForm;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerUpdateForm;
import net.lab1024.sa.admin.module.scm.customer.manager.CustomerValidator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

import static net.lab1024.sa.admin.module.scm.common.error.ScmCommonErrorCode.VERSION_CONFLICT;
import static net.lab1024.sa.admin.module.scm.customer.constant.CustomerErrorCode.CUSTOMER_CODE_DUPLICATE;
import static net.lab1024.sa.admin.module.scm.customer.constant.CustomerErrorCode.CUSTOMER_NOT_FOUND;
import static net.lab1024.sa.admin.module.scm.customer.constant.CustomerErrorCode.CUSTOMER_NOT_TRADABLE;

/**
 * 客户写路径。
 *
 * <p>所有写方法都在事务内，并按固定顺序执行：校验 → 读取并比对版本 → 归属校验 → 查重 → 落库。
 * 顺序固定是为了让并发场景下的失败原因可预测（版本冲突永远先于编码冲突暴露）。
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

    /** 新建客户的初始状态（Target Design Q13）。 */
    public static final String INITIAL_STATUS = ScmCustomerStatusEnum.POTENTIAL.name();

    private final CustomerDao dao;
    private final CustomerSkuVisibilityService visibility;
    private final net.lab1024.sa.admin.module.scm.customer.dao.CustomerSkuVisibilityDao visibilityDao;

    private final CustomerValidator validator;

    private final CustomerTypeService customerTypeService;

    /** 读取客户，不存在或已删除 → 40430。 */
    public CustomerEntity require(Long customerId) {
        CustomerEntity entity = customerId == null ? null : dao.selectById(customerId);
        if (entity == null) {
            throw new ScmBusinessException(CUSTOMER_NOT_FOUND);
        }
        return entity;
    }

    /** 读取客户并校验乐观锁版本：不存在 → 40430，版本不一致 → 40921。 */
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
     * <p>W2 没有订单域，因此当前没有生产调用方；方法先落地，作为 W3 唯一允许的「能否下单」判定入口
     * （legacy 不变量 C4）。
     */
    public CustomerEntity requireTradable(Long customerId) {
        CustomerEntity entity = require(customerId);
        if (!ScmCustomerStatusEnum.valueOf(entity.getStatus()).tradable()) {
            throw new ScmBusinessException(CUSTOMER_NOT_TRADABLE);
        }
        return entity;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long add(CustomerAddForm form) {
        validator.validateCreditPeriod(form);
        customerTypeService.requireSelectableType(form.getCustomerTypeId());
        validator.validateParent(form.getParentCustomerId(), null);

        String code = CustomerValidator.normalizeCode(form.getCustomerCode());
        if (existsCode(code, null)) {
            throw new ScmBusinessException(CUSTOMER_CODE_DUPLICATE);
        }

        CustomerEntity entity = new CustomerEntity();
        apply(entity, form);
        entity.setStatus(INITIAL_STATUS);
        entity.setVersion(0);
        entity.setDeleted(false);
        stamp(entity, true);
        try {
            dao.insert(entity);
        } catch (DuplicateKeyException e) {
            throw new ScmBusinessException(CUSTOMER_CODE_DUPLICATE);
        }
        if (form.getVisibilityPolicy()!=null || form.getVisibilities()!=null) visibility.replace(entity.getId(),entity.getVisibilityPolicy(),form.getVisibilities());
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(CustomerUpdateForm form) {
        validator.validateCreditPeriod(form);
        visibilityDao.lockCustomer(form.getCustomerId());
        CustomerEntity entity = require(form.getCustomerId(), form.getVersion());
        customerTypeService.requireSelectableType(form.getCustomerTypeId());
        validator.validateParent(form.getParentCustomerId(), form.getCustomerId());

        String code = CustomerValidator.normalizeCode(form.getCustomerCode());
        if (existsCode(code, form.getCustomerId())) {
            throw new ScmBusinessException(CUSTOMER_CODE_DUPLICATE);
        }

        // 注意：apply 不触碰 status —— 状态只能通过 updateStatus 变更（C7）
        apply(entity, form);
        entity.setVersion(form.getVersion());
        stamp(entity, false);
        try {
            if (dao.updateById(entity) != 1) {
                throw new ScmBusinessException(VERSION_CONFLICT);
            }
        } catch (DuplicateKeyException e) {
            throw new ScmBusinessException(CUSTOMER_CODE_DUPLICATE);
        }
        if(form.getVisibilityPolicy()!=null || form.getVisibilities()!=null) visibility.replace(entity.getId(),entity.getVisibilityPolicy(),form.getVisibilities());
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(CustomerStatusForm form) {
        CustomerEntity entity = require(form.getCustomerId(), form.getVersion());
        entity.setStatus(form.getStatus());
        entity.setVersion(form.getVersion());
        stamp(entity, false);
        if (dao.updateById(entity) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /**
     * 删除客户（软删）。
     *
     * <p>使用 {@code id + version} 原子谓词，而不是先查后改：并发删除时只有一次能成功。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(CustomerDeleteForm form) {
        visibilityDao.lockCustomer(form.getCustomerId());
        require(form.getCustomerId(), form.getVersion());
        assertNotReferenced(form.getCustomerId());
        if (dao.softDelete(form.getCustomerId(), form.getVersion(), ScmOperator.current()) != 1) {
            throw new ScmBusinessException(VERSION_CONFLICT);
        }
    }

    /** 活动记录内编码是否已存在（编码大小写不敏感：先归一化再比较）。 */
    public boolean existsCode(String normalizedCode, Long excludeId) {
        LambdaQueryWrapper<CustomerEntity> wrapper = new LambdaQueryWrapper<CustomerEntity>()
                .eq(CustomerEntity::getCustomerCode, normalizedCode);
        if (excludeId != null) {
            wrapper.ne(CustomerEntity::getId, excludeId);
        }
        return dao.selectCount(wrapper) > 0;
    }

    /**
     * 删除前的下游引用检查（legacy 不变量 C9）。
     *
     * <p>W2 没有任何下游业务表引用客户（定价 / 订单 / 商城都在 W3+），因此当前恒通过。
     * W3 接入定价后在这里补查询并抛出 {@code CUSTOMER_REFERENCED}(40939)。
     * 检查位先落地，避免 W3 忘记加而导致删掉被引用的客户。
     */
    private void assertNotReferenced(Long customerId) {
        if(visibilityDao.customerReferences(customerId)>0) throw new ScmBusinessException(net.lab1024.sa.admin.module.scm.customer.constant.CustomerErrorCode.CUSTOMER_REFERENCED);
    }

    private void apply(CustomerEntity entity, CustomerAddForm form) {
        if(form.getVisibilityPolicy()!=null) entity.setVisibilityPolicy(form.getVisibilityPolicy());
        else if(entity.getVisibilityPolicy()==null) entity.setVisibilityPolicy("ALL_ENABLED");
        entity.setCustomerCode(CustomerValidator.normalizeCode(form.getCustomerCode()));
        entity.setName(CustomerValidator.normalizeName(form.getName()));
        entity.setCustomerTypeId(form.getCustomerTypeId());
        entity.setSettleMode(form.getSettleMode());
        entity.setParentCustomerId(form.getParentCustomerId());
        entity.setSellerId(form.getSellerId());
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
