package com.xsy.scm.admin.module.system.support;

import com.xsy.scm.base.module.support.file.permission.FilePermission;

import com.xsy.scm.common.scope.ScmDataScopeService;
import com.xsy.scm.trace.permission.TracePermission;
import com.xsy.scm.product.permission.ProductPermission;
import com.xsy.scm.base.common.domain.RequestUser;
import com.xsy.scm.base.common.enumeration.UserTypeEnum;
import com.xsy.scm.admin.module.system.login.domain.RequestEmployee;
import com.xsy.scm.base.module.support.file.constant.FileRelationBizTypeEnum;
import com.xsy.scm.base.module.support.file.service.FileAccessIdentity;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

/**
 * F0/FA-2 的文件读取身份桥：把「能否读某个业务对象」的回答留在业务侧， 底座只按 {@link FileRelationBizTypeEnum} 派活。
 */
@Component
public class AdminFileAccessIdentity implements FileAccessIdentity {

    private final FileBizVisibilityDao visibilityDao;

    private final ScmDataScopeService dataScopeService;

    public AdminFileAccessIdentity(FileBizVisibilityDao visibilityDao, ScmDataScopeService dataScopeService) {
        this.visibilityDao = visibilityDao;
        this.dataScopeService = dataScopeService;
    }

    @Override
    public boolean canReadAllFiles(RequestUser user) {
        if (!(user instanceof RequestEmployee employee)) {
            return false;
        }
        if (Boolean.TRUE.equals(employee.getAdministratorFlag())) {
            return true;
        }
        // 异步线程里没有 Sa-Token 上下文，按无权限处理，不能静默放行。
        try {
            return StpUtil.hasPermission(FilePermission.QUERY);
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
            case PRODUCT -> StpUtil.hasPermission(ProductPermission.QUERY) && visibilityDao.countLiveSpu(bizId) > 0;
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
            // 商品资质随商品查看权：能看这个商品，就能看它的资质；同样要求 SPU 还活着，
            // 否则已删商品的资质会对全体有商品查看权的人长期可读。
            case PRODUCT_QUALIFICATION ->
                StpUtil.hasPermission(ProductPermission.QUERY) && visibilityDao.countLiveSpu(bizId) > 0;
            // 批次检测报告随包装码查看权：批次在召回与追溯里出现的地方都能读到报告。
            case LOT_INSPECTION ->
                StpUtil.hasPermission(TracePermission.UNIT_QUERY) && visibilityDao.countTraceLot(bizId) > 0;
            case TRACE_INSPECTION_REPORT ->
                (StpUtil.hasPermission(TracePermission.PHOTO_QUERY) && visibilityDao.countInspectionPhoto(bizId) > 0)
                    || (StpUtil.hasPermission(TracePermission.UNIT_QUERY)
                        || StpUtil.hasPermission(TracePermission.INSPECTION_MANAGE))
                        && visibilityDao.countVisibleInspectionReport(bizId,
                            dataScopeService.resolve().getWarehouseScope()) > 0;
        };
    }
}
