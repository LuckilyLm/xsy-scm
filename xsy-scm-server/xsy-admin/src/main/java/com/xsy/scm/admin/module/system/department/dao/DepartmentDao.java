package com.xsy.scm.admin.module.system.department.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xsy.scm.admin.module.system.department.domain.entity.DepartmentEntity;
import com.xsy.scm.admin.module.system.department.domain.vo.DepartmentVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 部门
 *
 */
@Mapper
public interface DepartmentDao extends BaseMapper<DepartmentEntity> {

    /**
     * 根据部门id，查询此部门直接子部门的数量
     *
     */
    Integer countSubDepartment(@Param("departmentId") Long departmentId);

    /**
     * 获取全部部门列表
     */
    List<DepartmentVO> listAll();

    DepartmentVO selectDepartmentVO(@Param("departmentId") Long departmentId);
}
