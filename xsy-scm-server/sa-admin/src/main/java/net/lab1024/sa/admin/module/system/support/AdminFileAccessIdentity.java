package net.lab1024.sa.admin.module.system.support;

import net.lab1024.sa.base.common.domain.RequestUser;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.module.support.file.constant.FileRelationBizTypeEnum;
import net.lab1024.sa.base.module.support.file.service.FileAccessIdentity;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

/**
 * F0/FA-2 的文件读取身份桥：把「能否读某个业务对象」的回答留在业务侧，
 * 底座只按 {@link FileRelationBizTypeEnum} 派活。
 */
@Component
public class AdminFileAccessIdentity implements FileAccessIdentity {

    private final FileBizVisibilityDao visibilityDao;

    public AdminFileAccessIdentity(FileBizVisibilityDao visibilityDao) {
        this.visibilityDao = visibilityDao;
    }

    @Override
    public boolean canReadAllFiles(RequestUser user) {
        if (!(user instanceof RequestEmployee employee)) {
            return false;
        }
        if (Boolean.TRUE.equals(employee.getAdministratorFlag())) {
            return true;
        }
        // Sa-Token 上下文只在真实请求线程里存在；异步导出、SmartJob 线程与 IT 里直接调
        // StpUtil 会抛「SaTokenContext 未初始化」。这里按 fail-closed 处理而不是让它冒泡：
        // 拿不到权限就只能走「非超管」分支，静默放行才是问题。
        try {
            return StpUtil.hasPermission("support:file:query");
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean canReadBizObject(FileRelationBizTypeEnum bizType, Long bizId, RequestUser user) {
        if (bizId == null || user == null || user.getUserType() != UserTypeEnum.ADMIN_EMPLOYEE) {
            return false;
        }
        return switch (bizType) {
            // 商品图沿用商品查看权：有商品可见性就等于能看它的图片，不引入第三种权限模型。
            // 同时要求 SPU 本身还活着 —— 删除商品不回收关系行，只看权限会留下长期可读的孤儿授权。
            case PRODUCT -> StpUtil.hasPermission("scm:product:query")
                    && visibilityDao.countLiveSpu(bizId) > 0;
            // 公告按既有可见范围判定（全部可见或员工/部门命中），不比公告列表更宽。
            case NOTICE -> visibilityDao.countVisibleNotice(bizId, user.getUserId(),
                    ((RequestEmployee) user).getDepartmentId()) > 0;
            // 帮助文档是内部全员资料；仍然要求确有该文档的关系行，
            // 比原先「private/help-doc/ 前缀一律放行」严格。
            case HELP_DOC -> visibilityDao.countHelpDoc(bizId) > 0;
            // 反馈截图只有提交人与管理员可读：提交人由守卫的「上传者」规则覆盖，
            // 这里不额外开放，避免把别人的反馈截图给任意同权限的人。
            case FEEDBACK -> false;
            // 企业档案（营业执照等）的查看权归属 OA 模块，其权限码随正式业务角色一起落地；
            // 在此之前不猜测字符串，维持现状（上传者/管理员可读）。
            case ENTERPRISE -> false;
        };
    }
}
