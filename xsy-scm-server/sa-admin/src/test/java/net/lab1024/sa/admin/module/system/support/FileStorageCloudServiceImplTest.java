package net.lab1024.sa.admin.module.system.support;

import net.lab1024.sa.base.config.FileConfig;
import net.lab1024.sa.base.module.support.file.dao.FileDao;
import net.lab1024.sa.base.module.support.file.domain.vo.FileVO;
import net.lab1024.sa.base.module.support.file.service.FileStorageCloudServiceImpl;
import net.lab1024.sa.base.module.support.redis.RedisService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileStorageCloudServiceImplTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void privateUploadSignsBeforeMetadataInsertAndAclIsOptional(boolean sendAcl) {
        FileConfig config = FileConfigTest.config();
        config.setCloudSendObjectAcl(sendAcl);
        S3Client client = mock(S3Client.class);
        FileDao dao = mock(FileDao.class);
        try (var signer = config.initS3Presigner()) {
            var storage = new FileStorageCloudServiceImpl();
            ReflectionTestUtils.setField(storage, "s3Client", client);
            ReflectionTestUtils.setField(storage, "s3Presigner", signer);
            ReflectionTestUtils.setField(storage, "cloudConfig", config);
            ReflectionTestUtils.setField(storage, "fileDao", dao);
            var result = storage.upload(new MockMultipartFile("file", "public.png", "image/png", new byte[]{1}), "private/common/");
            assertThat(result.getOk()).isTrue();
            assertThat(result.getData().getFileUrl()).contains("X-Amz-Signature=");
            var request = ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(client).putObject(request.capture(), any(RequestBody.class));
            assertThat(request.getValue().acl()).isEqualTo(sendAcl ? ObjectCannedACL.PRIVATE : null);
            verifyNoInteractions(dao);
        }
    }

    @Test
    void shortTtlDoesNotCreatePermanentRedisEntry() {
        FileConfig config = FileConfigTest.config();
        config.setCloudPrivateUrlExpireSeconds(2L);
        FileDao dao = mock(FileDao.class);
        RedisService redis = mock(RedisService.class);
        String key = "private/common/short.png";
        when(dao.getByFileKey(key)).thenReturn(new FileVO());
        try (var signer = config.initS3Presigner()) {
            var storage = new FileStorageCloudServiceImpl();
            ReflectionTestUtils.setField(storage, "s3Presigner", signer);
            ReflectionTestUtils.setField(storage, "cloudConfig", config);
            ReflectionTestUtils.setField(storage, "fileDao", dao);
            ReflectionTestUtils.setField(storage, "redisService", redis);
            assertThat(storage.getFileUrl(key).getData()).contains("X-Amz-Expires=2");
            verifyNoInteractions(redis);
        }
    }

    @Test
    void malformedPrefixNeverReachesS3() {
        var storage = new FileStorageCloudServiceImpl();
        assertThat(storage.getFileUrl("publicity/a.png").getCode()).isEqualTo(30005);
        assertThat(storage.download("privatex/a.png").getCode()).isEqualTo(30005);
        assertThat(storage.delete("private/common/../a.png").getCode()).isEqualTo(30005);
    }
}
