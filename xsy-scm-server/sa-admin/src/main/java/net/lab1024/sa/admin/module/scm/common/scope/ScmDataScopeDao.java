package net.lab1024.sa.admin.module.scm.common.scope;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 数据范围的只读取数入口：把「这个员工能看哪些仓 / 绑了哪个司机」集中成一次查询，
 * 供 {@link ScmDataScopeService} 解析。业务 Dao 不各自拼范围条件。
 */
@Mapper
public interface ScmDataScopeDao {

    /**
     * 员工被授权的仓库 id；无授权行即返回空清单（调用方按「看不到任何仓库数据」处理）。
     */
    List<Long> listAuthorizedWarehouseIds(@Param("employeeId") Long employeeId);

    /**
     * 员工绑定的活动司机 id；一个员工最多绑一个活动司机，返回清单只为不让 Mapper 侧依赖这个约束。
     */
    List<Long> listDriverIdsByEmployee(@Param("employeeId") Long employeeId);
}
