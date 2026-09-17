package net.lab1024.sa.admin.module.system.role.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import net.lab1024.sa.base.common.domain.PageParam;

/**
 * 角色的员工查询
 *
 * @Author 1024创新实验室: 善逸
 * @Date 2022-04-08 21:53:04
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
@Data
public class RoleEmployeeQueryForm extends PageParam {

    @Schema(description = "关键字")
    private String keywords;

    // t_role_employee.role_id 是 bigint。此处必须用 Long：
    // 若声明为 String，PostgreSQL 会以 varchar 绑定参数并报
    // "operator does not exist: bigint = character varying"（MySQL 有隐式转换，故旧库无感）。
    @Schema(description = "角色id")
    private Long roleId;
}
