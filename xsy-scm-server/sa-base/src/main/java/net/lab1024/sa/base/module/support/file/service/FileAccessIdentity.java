package net.lab1024.sa.base.module.support.file.service;

import net.lab1024.sa.base.common.domain.RequestUser;

/** Application identity bridge; the base module does not depend on admin employee classes. */
public interface FileAccessIdentity {
    boolean canReadAllFiles(RequestUser user);
}
