package net.lab1024.sa.admin.module.system.support;

import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import net.lab1024.sa.base.module.support.file.controller.FileController;
import net.lab1024.sa.base.module.support.file.dao.FileDao;
import net.lab1024.sa.base.module.support.file.domain.vo.FileVO;
import net.lab1024.sa.base.module.support.file.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FileAccessGuardTest {
    final FileDao dao = mock(FileDao.class);
    final FileAccessIdentity identity = mock(FileAccessIdentity.class);
    final FileAccessGuard guard = new FileAccessGuard(dao, identity);
    final RequestEmployee employee = employee();

    private RequestEmployee employee() {
        RequestEmployee user = new RequestEmployee();
        user.setEmployeeId(44L);
        user.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        return user;
    }

    @AfterEach
    void clear() {
        SmartRequestUtil.remove();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"publicity/a", "privatex/a", "PUBLIC/a", "Public/a", "private/other/a",
            "private/notice/../common/a", "private/notice/%2e%2e/common/a", "private/notice//a",
            "private/notice/a?x", "private/notice/a#x", "private/notice/a\\x", "private/notice/", "public/a,"})
    void rejectsInvalidKeysEvenForPrivilegedUser(String key) {
        when(identity.canReadAllFiles(employee)).thenReturn(true);
        assertThatThrownBy(() -> guard.checkRead(key, employee)).isInstanceOf(FileAccessGuard.AccessDenied.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"public/a.png", "private/notice/a.pdf", "private/help-doc/a.pdf"})
    void sharedResourcesRequireAnAuthenticatedEmployee(String key) {
        assertThatCode(() -> guard.checkRead(key, employee)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.checkRead(key, null)).isInstanceOf(FileAccessGuard.AccessDenied.class);
        verifyNoInteractions(dao, identity);
    }

    @ParameterizedTest
    @ValueSource(strings = {"private/common/a.png", "private/feedback/a.png"})
    void strictFoldersAllowCreatorOrPrivilegeAndRejectOtherEmployees(String key) {
        FileVO file = new FileVO();
        file.setCreatorId(44L);
        file.setCreatorUserType(UserTypeEnum.ADMIN_EMPLOYEE.getValue());
        when(dao.getByFileKey(key)).thenReturn(file);
        assertThatCode(() -> guard.checkRead(key, employee)).doesNotThrowAnyException();
        file.setCreatorId(45L);
        assertThatThrownBy(() -> guard.checkRead(key, employee)).isInstanceOf(FileAccessGuard.AccessDenied.class);
        file.setCreatorId(44L);
        file.setCreatorUserType(-1);
        assertThatThrownBy(() -> guard.checkRead(key, employee)).isInstanceOf(FileAccessGuard.AccessDenied.class);
        when(identity.canReadAllFiles(employee)).thenReturn(true);
        assertThatCode(() -> guard.checkRead(key, employee)).doesNotThrowAnyException();
    }

    @Test
    void everyKeyInBatchMustBeAuthorized() {
        assertThatThrownBy(() -> guard.checkRead("private/notice/a,private/common/other", employee))
                .isInstanceOf(FileAccessGuard.AccessDenied.class);
    }

    @Test
    void filterReadableKeepsOnlyKeysTheCallerMayRead() {
        FileVO ownFile = new FileVO();
        ownFile.setCreatorId(44L);
        ownFile.setCreatorUserType(UserTypeEnum.ADMIN_EMPLOYEE.getValue());
        when(dao.getByFileKey("private/common/own")).thenReturn(ownFile);

        FileVO othersFile = new FileVO();
        othersFile.setCreatorId(45L);
        othersFile.setCreatorUserType(UserTypeEnum.ADMIN_EMPLOYEE.getValue());
        when(dao.getByFileKey("private/common/others")).thenReturn(othersFile);

        // Mirrors the exact key set FileKeyVoSerializer would pass through: a mix of shared,
        // own, someone else's, and a malformed key, all in one VO field.
        List<String> readable = guard.filterReadable(
                List.of("public/a", "private/common/own", "private/common/others",
                        "private/notice/../common/escape"),
                employee);

        assertThat(readable).containsExactly("public/a", "private/common/own");
        // Same per-key outcome as the throwing path, just collected instead of failing fast.
        assertThatCode(() -> guard.checkRead("public/a,private/common/own", employee)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.checkRead("public/a,private/common/others", employee))
                .isInstanceOf(FileAccessGuard.AccessDenied.class);
    }

    @Test
    void filterReadableReturnsEmptyRatherThanThrowingWithNoAuthenticatedCaller() {
        assertThat(guard.filterReadable(List.of("public/a"), null)).isEmpty();
        assertThat(guard.filterReadable(null, employee)).isEmpty();
        assertThat(guard.filterReadable(List.of(), employee)).isEmpty();
    }

    @Test
    void administratorNeedsNoRoleOrPermissionLookup() {
        employee.setAdministratorFlag(true);
        assertThat(new AdminFileAccessIdentity().canReadAllFiles(employee)).isTrue();
    }

    @Test
    void nonAdministratorUsesExistingFileQueryPermission() {
        try (var stp = mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
            stp.when(() -> cn.dev33.satoken.stp.StpUtil.hasPermission("support:file:query")).thenReturn(true);
            assertThat(new AdminFileAccessIdentity().canReadAllFiles(employee)).isTrue();
            stp.when(() -> cn.dev33.satoken.stp.StpUtil.hasPermission("support:file:query")).thenReturn(false);
            assertThat(new AdminFileAccessIdentity().canReadAllFiles(employee)).isFalse();
        }
    }

    @Test
    void forbiddenEndpointsReturnHttp403AndNativeEnvelopeWithoutStorageCalls() throws Exception {
        FileController controller = new FileController();
        FileService service = mock(FileService.class);
        ReflectionTestUtils.setField(controller, "fileService", service);
        ReflectionTestUtils.setField(controller, "fileAccessGuard", guard);
        SmartRequestUtil.setRequestUser(employee);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        for (String endpoint : new String[]{"getFileUrl", "downLoad"}) {
            mvc.perform(get("/support/file/" + endpoint).param("fileKey", "private/common/other.png"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(30005))
                    .andExpect(jsonPath("$.ok").value(false));
        }
        verifyNoInteractions(service);
    }
}
