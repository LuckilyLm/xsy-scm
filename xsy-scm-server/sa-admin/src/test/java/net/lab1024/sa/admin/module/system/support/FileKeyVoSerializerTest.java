package net.lab1024.sa.admin.module.system.support;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import net.lab1024.sa.admin.module.system.login.domain.RequestEmployee;
import net.lab1024.sa.base.common.enumeration.UserTypeEnum;
import net.lab1024.sa.base.common.json.serializer.FileKeyVoSerializer;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import net.lab1024.sa.base.module.support.file.service.FileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FA-1 读侧收口在序列化层的落点：内嵌 VO 字段必须走**带调用者身份**的批量入口，
 * 并且任何退化路径都不回显原始 key。
 *
 * <p>逐 key 的放行规则本身不在这里测（那是 {@code FileAccessGuardTest} 与
 * {@code FileServiceReadTest} 的职责）；本类只钉住序列化器的两件事：
 * 它把当前登录人交给受控入口，以及它拿不到依赖时输出空集合。
 */
class FileKeyVoSerializerTest {

    final FileService fileService = mock(FileService.class);

    final JsonGenerator generator = mock(JsonGenerator.class);

    final SerializerProvider provider = mock(SerializerProvider.class);

    private FileKeyVoSerializer wired() {
        FileKeyVoSerializer serializer = new FileKeyVoSerializer();
        ReflectionTestUtils.setField(serializer, "fileService", fileService);
        return serializer;
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
    void handsTheCallerIdentityToTheControlledBatchEntry() throws Exception {
        RequestEmployee caller = employee(44L);
        SmartRequestUtil.setRequestUser(caller);
        when(fileService.getFileList(anyList(), org.mockito.ArgumentMatchers.eq(caller)))
                .thenReturn(List.of());

        wired().serialize("private/feedback/own.png,private/feedback/others.png", generator, provider);

        verify(fileService).getFileList(
                List.of("private/feedback/own.png", "private/feedback/others.png"), caller);
    }

    @Test
    void neverFallsBackToTheUnauthenticatedBatchEntry() throws Exception {
        // 无身份入口只服务公开目录；序列化器拿不到身份时必须什么都不解析，
        // 而不是退回旧入口把私有附件展开成 URL。
        wired().serialize("private/common/a.png", generator, provider);

        verify(fileService, never()).getFileList(anyList());
        verify(fileService).getFileList(anyList(), org.mockito.ArgumentMatchers.isNull());
        verify(generator).writeObject(List.of());
    }

    @Test
    void emptyFieldWritesAnEmptyList() throws Exception {
        SmartRequestUtil.setRequestUser(employee(44L));

        wired().serialize("", generator, provider);

        verify(fileService, never()).getFileList(anyList(), org.mockito.ArgumentMatchers.any());
        verify(generator).writeObject(List.of());
    }

    @Test
    void neverEmitsTheRawKeyIfFileServiceWasNeverWired() throws Exception {
        FileKeyVoSerializer unwired = new FileKeyVoSerializer();
        SmartRequestUtil.setRequestUser(employee(44L));

        unwired.serialize("private/common/a.pdf", generator, provider);

        // 旧实现会在依赖缺失时把存储里的 key 原样写进响应，那仍然泄露私有附件的存在与路径。
        verify(generator, never()).writeString(anyString());
        verify(generator).writeObject(List.of());
    }
}
