package net.lab1024.sa.admin.module.system.support;

import cn.dev33.satoken.stp.StpUtil;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.domain.RequestUser;
import net.lab1024.sa.base.module.support.file.service.FileAccessIdentity;
import org.springframework.stereotype.Component;

/** F0 file-only bridge to the existing SmartAdmin administrator and permission model. */
@Component
public class AdminFileAccessIdentity implements FileAccessIdentity {
    @Override
    public boolean canReadAllFiles(RequestUser user) {
        return user instanceof RequestEmployee employee
                && (Boolean.TRUE.equals(employee.getAdministratorFlag())
                || StpUtil.hasPermission("support:file:query"));
    }
}
