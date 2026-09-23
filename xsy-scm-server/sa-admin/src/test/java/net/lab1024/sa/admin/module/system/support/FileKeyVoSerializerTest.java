package net.lab1024.sa.admin.module.system.support;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.json.serializer.FileKeyVoSerializer;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import net.lab1024.sa.base.module.support.file.dao.FileDao;
import net.lab1024.sa.base.module.support.file.domain.vo.FileVO;
import net.lab1024.sa.base.module.support.file.service.FileAccessGuard;
import net.lab1024.sa.base.module.support.file.service.FileAccessIdentity;
import net.lab1024.sa.base.module.support.file.service.FileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.*;

/**
 * Regression coverage for the F0-DEBT-01 read-side gap: business VO fields serialized through
 * this class must obey the same per-file policy as the direct /file/getFileUrl and /file/downLoad
 * endpoints (FileAccessGuard), instead of resolving every stored key unconditionally.
 */
class FileKeyVoSerializerTest {
    final FileDao dao = mock(FileDao.class);
    final FileAccessIdentity identity = mock(FileAccessIdentity.class);
    final FileAccessGuard guard = new FileAccessGuard(dao, identity);
    final FileService fileService = mock(FileService.class);
    final JsonGenerator generator = mock(JsonGenerator.class);
    final SerializerProvider provider = mock(SerializerProvider.class);

    final FileKeyVoSerializer serializer = wired();

    private FileKeyVoSerializer wired() {
        FileKeyVoSerializer s = new FileKeyVoSerializer();
        ReflectionTestUtils.setField(s, "fileService", fileService);
        ReflectionTestUtils.setField(s, "fileAccessGuard", guard);
        return s;
    }

    private RequestEmployee employee(long id) {
        RequestEmployee user = new RequestEmployee();
        user.setEmployeeId(id);
        user.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        return user;
    }

    @AfterEach
    void clear() {
        SmartRequestUtil.remove();
    }

    @Test
    void dropsKeysTheCallerMayNotReadBeforeAskingFileServiceToResolveUrls() throws Exception {
        RequestEmployee caller = employee(44L);
        SmartRequestUtil.setRequestUser(caller);

        FileVO ownFile = new FileVO();
        ownFile.setCreatorId(44L);
        ownFile.setCreatorUserType(UserTypeEnum.ADMIN_EMPLOYEE.getValue());
        when(dao.getByFileKey("private/feedback/own.png")).thenReturn(ownFile);

        FileVO othersFile = new FileVO();
        othersFile.setCreatorId(45L);
        othersFile.setCreatorUserType(UserTypeEnum.ADMIN_EMPLOYEE.getValue());
        when(dao.getByFileKey("private/feedback/others.png")).thenReturn(othersFile);

        // Before the fix, both keys reached fileService.getFileList() unconditionally and the
        // caller got a resolved URL for someone else's feedback attachment.
        serializer.serialize("private/feedback/own.png,private/feedback/others.png", generator, provider);

        verify(fileService).getFileList(List.of("private/feedback/own.png"));
        verify(fileService, never()).getFileList(argThat(list -> list.contains("private/feedback/others.png")));
    }

    @Test
    void resolvesEverySharedOrOwnedKeyForAnOrdinaryCaller() throws Exception {
        RequestEmployee caller = employee(44L);
        SmartRequestUtil.setRequestUser(caller);

        serializer.serialize("public/a.png,private/notice/b.pdf", generator, provider);

        verify(fileService).getFileList(List.of("public/a.png", "private/notice/b.pdf"));
    }

    @Test
    void resolvesNothingWithoutAnAuthenticatedCaller() throws Exception {
        // No SmartRequestUtil.setRequestUser(...) call: simulates serialization outside a
        // logged-in request context. Must fail closed, not fall back to the old unfiltered path.
        serializer.serialize("private/common/a.png", generator, provider);

        verify(fileService).getFileList(List.of());
    }

    @Test
    void failsClosedIfTheGuardWasNeverWired() throws Exception {
        FileKeyVoSerializer unwired = new FileKeyVoSerializer();
        ReflectionTestUtils.setField(unwired, "fileService", fileService);
        // fileAccessGuard intentionally left null.
        SmartRequestUtil.setRequestUser(employee(44L));

        unwired.serialize("public/a.png", generator, provider);

        verify(fileService).getFileList(List.of());
    }
}
