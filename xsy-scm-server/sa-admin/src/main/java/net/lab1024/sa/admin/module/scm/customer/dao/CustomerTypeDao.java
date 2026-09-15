package net.lab1024.sa.admin.module.scm.customer.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.lab1024.sa.admin.module.scm.customer.domain.entity.CustomerTypeEntity;
import net.lab1024.sa.admin.module.scm.customer.domain.form.CustomerTypeQueryForm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CustomerTypeDao extends BaseMapper<CustomerTypeEntity> {

    /** 分页查询；排序由 Service 的白名单校验后通过 {@link Page} 的 orders 传入。 */
    List<CustomerTypeEntity> queryPage(Page<?> page, @Param("query") CustomerTypeQueryForm query);

    /** 原子软删：{@code id + version} 双谓词，返回 0 即冲突。 */
    int softDelete(@Param("typeId") Long typeId,
                   @Param("version") Integer version,
                   @Param("operator") String operator);
}
