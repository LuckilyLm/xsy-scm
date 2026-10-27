package com.xsy.scm.customer.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xsy.scm.common.scope.ScmValueScope;
import com.xsy.scm.customer.domain.entity.CustomerEntity;
import com.xsy.scm.customer.domain.form.CustomerQueryForm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CustomerDao extends BaseMapper<CustomerEntity> {

    /**
     * 分页查询；排序字段由 Service 校验后通过 {@link Page} 的 orders 传入。
     *
     * <p>
     * {@code scope} 是 {@code xsy_customer.seller_id} 维度的授权取值，由 Service 解析后下传；传 {@code null} 在 SQL 里是恒假条件，不是「不加限制」。
     */
    List<CustomerEntity> queryPage(Page<?> page, @Param("query") CustomerQueryForm query,
        @Param("scope") ScmValueScope scope);

    List<CustomerEntity> selectOptions(@Param("scope") ScmValueScope scope);

    /**
     * 原子软删：{@code id + version} 双条件，返回 0 表示版本冲突。
     *
     * <p>
     * 不用 {@code updateById} + {@code deleteById} 分两步：中间没有条件保护，并发删除会互相覆盖 {@code updated_by}。
     */
    int softDelete(@Param("customerId") Long customerId, @Param("version") Integer version,
        @Param("operator") String operator);

    /** 活动客户按类型计数，供删除客户类型前检查引用。 */
    long countActiveByTypeId(@Param("typeId") Long typeId);

    /** 加锁读取有效客户行，作为结算与授信的正式依据。 */
    CustomerEntity lock(@Param("customerId") Long customerId);

    int updateMallDeliveryWarehouse(@Param("customerId") Long customerId, @Param("version") Integer version,
        @Param("warehouseId") Long warehouseId, @Param("operator") String operator);

    List<CustomerEntity> selectActiveByCodes(@Param("codes") List<String> codes);

    /** 按客户名称批量取有效客户，供送货单导入按「收货单位」匹配；重名由调用方判定。 */
    List<CustomerEntity> selectActiveByNames(@Param("names") List<String> names);
}
