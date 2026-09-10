package com.xianshuyuan.scm.system;

import com.xianshuyuan.scm.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public final class SystemErrorCodes {
    private SystemErrorCodes() {
    }

    public static final ErrorCode MENU_TOO_DEEP = new ErrorCode(40045, HttpStatus.BAD_REQUEST, "菜单层级超过64层安全上限，请通过列表重新调整父菜单");
    public static final ErrorCode MENU_NOT_FOUND = new ErrorCode(40444, HttpStatus.NOT_FOUND, "菜单不存在");
    public static final ErrorCode PROTECTED_MENU = new ErrorCode(40343, HttpStatus.FORBIDDEN, "不允许修改菜单或提升导航访问");
    public static final ErrorCode MENU_IN_USE = new ErrorCode(40044, HttpStatus.BAD_REQUEST, "菜单存在未删除子菜单或角色引用");
    public static final ErrorCode PERMISSION_NOT_FOUND = new ErrorCode(40443, HttpStatus.NOT_FOUND, "权限不存在");
    public static final ErrorCode PROTECTED_PERMISSION = new ErrorCode(40342, HttpStatus.FORBIDDEN, "不允许修改受保护权限、变更权限标识或提升授权");
    public static final ErrorCode PERMISSION_IN_USE = new ErrorCode(40043, HttpStatus.BAD_REQUEST, "权限仍被未删除菜单引用");
    public static final ErrorCode ROLE_NOT_FOUND = new ErrorCode(40442, HttpStatus.NOT_FOUND, "角色不存在");
    public static final ErrorCode PROTECTED_ROLE = new ErrorCode(40341, HttpStatus.FORBIDDEN, "不允许修改受保护角色或移除自身权限");
    public static final ErrorCode DEPARTMENT_NOT_FOUND = new ErrorCode(40441, HttpStatus.NOT_FOUND, "部门不存在");
    public static final ErrorCode DEPARTMENT_CYCLE = new ErrorCode(40041, HttpStatus.BAD_REQUEST, "部门层级不能形成循环");
    public static final ErrorCode DEPARTMENT_IN_USE = new ErrorCode(40042, HttpStatus.BAD_REQUEST, "部门存在未删除的子部门或用户");
    public static final ErrorCode USER_NOT_FOUND = new ErrorCode(40440, HttpStatus.NOT_FOUND, "用户不存在");
    public static final ErrorCode INVALID_DEPARTMENT = new ErrorCode(40040, HttpStatus.BAD_REQUEST, "部门不存在或未启用");
    public static final ErrorCode PROTECTED_USER = new ErrorCode(40340, HttpStatus.FORBIDDEN, "不允许修改受保护用户");
    public static final ErrorCode PASSWORD_POLICY = new ErrorCode(40046, HttpStatus.BAD_REQUEST, "密码不符合安全策略");
    public static final ErrorCode PASSWORD_CURRENT_INVALID = new ErrorCode(40047, HttpStatus.BAD_REQUEST, "当前密码错误");
    public static final ErrorCode PASSWORD_REUSED = new ErrorCode(40048, HttpStatus.BAD_REQUEST, "新密码不能与当前密码相同");
    public static final ErrorCode PASSWORD_TARGET_UNUSABLE = new ErrorCode(40344, HttpStatus.FORBIDDEN, "目标账号当前不可重置密码");
}
