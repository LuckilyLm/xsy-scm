package net.lab1024.sa.admin.module.scm.customer.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.lab1024.sa.admin.module.scm.customer.domain.entity.CustomerEntity;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerQueryForm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CustomerDao extends BaseMapper<CustomerEntity> {

    /** 分页查询；排序由 Service 的白名单校验后通过 {@link Page} 的 orders 传入。 */
    List<CustomerEntity> queryPage(Page<?> page, @Param("query") CustomerQueryForm query);

    /**
     * 原子软删：{@code id + version} 双谓词，返回 0 即冲突（legacy 不变量 C8）。
     *
     * <p>刻意不用 {@code updateById} + {@code deleteById} 两步走：两步之间没有谓词保护，
     * 并发删除会互相覆盖 {@code updated_by}。
     */
    int softDelete(@Param("customerId") Long customerId,
                   @Param("version") Integer version,
                   @Param("operator") String operator);

    /** 活动客户按类型计数，供删除客户类型前的引用检查（T6）。 */
    long countActiveByTypeId(@Param("typeId") Long typeId);

    List<CustomerEntity> selectActiveByCodes(@Param("codes") List<String> codes);
}
