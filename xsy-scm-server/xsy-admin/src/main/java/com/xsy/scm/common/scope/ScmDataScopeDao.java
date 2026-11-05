package com.xsy.scm.common.scope;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 数据范围的只读查询入口，供 {@link ScmDataScopeService} 解析。业务 Dao 不各自拼范围条件。
 */
@Mapper
public interface ScmDataScopeDao {

    /** 员工被授权的仓库 id；没有授权行时返回空清单。 */
    List<Long> listAuthorizedWarehouseIds(@Param("employeeId") Long employeeId);

    /** 员工绑定的活动司机 id；一个员工最多绑一个，返回 List 只是不想让 Mapper 依赖这条约束。 */
    List<Long> listDriverIdsByEmployee(@Param("employeeId") Long employeeId);
}
