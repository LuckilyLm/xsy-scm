package net.lab1024.sa.admin.module.system.support;

import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.module.support.file.dao.FileDao;
import net.lab1024.sa.base.module.support.file.dao.FileRelationDao;
import net.lab1024.sa.base.module.support.file.domain.vo.FileVO;
import net.lab1024.sa.base.module.support.file.service.FileAccessGuard;
import net.lab1024.sa.base.module.support.file.service.FileAccessIdentity;
import net.lab1024.sa.base.module.support.file.service.FileService;
import net.lab1024.sa.base.module.support.file.service.IFileStorageService;
import net.lab1024.sa.base.module.support.securityprotect.service.SecurityFileService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FA-1：FileService 的读侧入口本身。
 *
 * <p>序列化器测试只证明「谁调了哪个入口」，证明不了入口是不是真的守得住。
 * 这里接**真实的 FileAccessGuard**（只 mock DAO 与身份判定），因此断言的是
 * 「无身份入口拿不到私有附件的 URL」这条收口目标，而不是某个调用习惯。
 */
class FileServiceReadTest {

    final FileDao fileDao = mock(FileDao.class);

    final FileAccessIdentity identity = mock(FileAccessIdentity.class);

    final IFileStorageService storage = mock(IFileStorageService.class);

    final SecurityFileService securityFileService = mock(SecurityFileService.class);

    final FileService fileService = build();

    private FileService build() {
        FileService service = new FileService();
        ReflectionTestUtils.setField(service, "fileDao", fileDao);
        ReflectionTestUtils.setField(service, "fileStorageService", storage);
        ReflectionTestUtils.setField(service, "securityFileService", securityFileService);
        ReflectionTestUtils.setField(service, "fileAccessGuard",
                new FileAccessGuard(fileDao, identity, mock(FileRelationDao.class)));
        return service;
    }

    private RequestEmployee employee(long id) {
        RequestEmployee user = new RequestEmployee();
        user.setEmployeeId(id);
        user.setUserType(UserTypeEnum.ADMIN_EMPLOYEE);
        return user;
    }

    private FileVO stored(String key, Long creatorId) {
        FileVO file = new FileVO();
        file.setFileKey(key);
        file.setFileName(key.substring(key.lastIndexOf('/') + 1));
        file.setCreatorId(creatorId);
        file.setCreatorUserType(UserTypeEnum.ADMIN_EMPLOYEE.getValue());
        return file;
    }

    private void stubStorageUrl() {
        when(storage.getFileUrl(anyString())).thenAnswer(invocation ->
                ResponseDTO.ok("https://storage.example/" + invocation.getArgument(0)));
    }

    private void stubLookup(List<FileVO> rows) {
        when(fileDao.selectByFileKeyList(org.mockito.ArgumentMatchers.anyCollection()))
                .thenAnswer(invocation -> rows.stream()
                        .filter(row -> asKeys(invocation.getArgument(0)).contains(row.getFileKey()))
                        .toList());
        // FileAccessGuard 的「是不是上传者」判定走单 key 入口，与批量解析是两个 DAO 方法；
        // 只桩批量那个会让守卫把所有私有 key 判成不可读，测试空过而不是验到规则。
        for (FileVO row : rows) {
            when(fileDao.getByFileKey(row.getFileKey())).thenReturn(row);
        }
    }

    private Collection<String> asKeys(Object argument) {
        return (Collection<String>) argument;
    }

    @Test
    void controlledEntryResolvesOnlyWhatTheCallerMayRead() {
        stubLookup(List.of(stored("private/feedback/own.png", 44L), stored("private/feedback/others.png", 45L)));
        stubStorageUrl();

        List<FileVO> result = fileService.getFileList(
                List.of("private/feedback/own.png", "private/feedback/others.png"), employee(44L));

        assertThat(result).extracting(FileVO::getFileKey).containsExactly("private/feedback/own.png");
        assertThat(result.getFirst().getFileUrl()).isNotBlank();
        // 不可读的那条连解析都不该发生：URL 不存在 = 既不泄露内容，也不泄露「它还在」
        verify(storage, never()).getFileUrl("private/feedback/others.png");
    }

    @Test
    void controlledEntryWithoutACallerResolvesNothing() {
        stubLookup(List.of(stored("public/a.png", null)));
        stubStorageUrl();

        assertThat(fileService.getFileList(List.of("public/a.png"), null)).isEmpty();
        verify(storage, never()).getFileUrl(anyString());
    }

    @Test
    void unauthenticatedEntryServesPublicFolderOnly() {
        stubLookup(List.of(stored("public/image/a.png", null), stored("private/common/b.png", 44L)));
        stubStorageUrl();

        List<FileVO> result = fileService.getFileList(List.of("public/image/a.png", "private/common/b.png"));

        assertThat(result).extracting(FileVO::getFileKey).containsExactly("public/image/a.png");
        verify(fileDao, never()).selectByFileKeyList(
                org.mockito.ArgumentMatchers.argThat(keys -> keys.contains("private/common/b.png")));
    }

    @Test
    void metadataLookupProvesExistenceWithoutEverProducingAUrl() {
        // 写路径要的是「这个 key 真的存在吗」。解析 URL 等于替一个可能无读取权的人读附件，
        // 因此元数据入口必须完全不触达存储层。
        stubLookup(List.of(stored("private/common/legacy.png", 44L)));

        List<FileVO> result = fileService.getFileMetadata(List.of("private/common/legacy.png"));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getFileUrl()).isNull();
        verify(storage, never()).getFileUrl(anyString());
    }
}
