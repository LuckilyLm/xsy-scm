package net.lab1024.sa.base.common.json.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.google.common.collect.Lists;
import jakarta.annotation.Resource;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import net.lab1024.sa.base.module.support.file.domain.vo.FileVO;
import net.lab1024.sa.base.module.support.file.service.FileAccessGuard;
import net.lab1024.sa.base.module.support.file.service.FileService;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 文件key进行序列化对象
 *
 */
public class FileKeyVoSerializer extends JsonSerializer<String> {

    @Resource
    private FileService fileService;

    @Resource
    private FileAccessGuard fileAccessGuard;


    @Override
    public void serialize(String value, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        if (StringUtils.isEmpty(value)) {
            jsonGenerator.writeObject(Lists.newArrayList());
            return;
        }
        // Unwired dependencies fail closed. Emitting the raw key string here would still leak the
        // existence and path of private attachments to any caller, so no fallback exposes `value`.
        if (fileService == null || fileAccessGuard == null) {
            jsonGenerator.writeObject(Lists.newArrayList());
            return;
        }
        String[] fileKeyArray = value.split(",");
        List<String> fileKeyList = Arrays.asList(fileKeyArray);
        // Embedded VO fields must obey the same per-file read policy as the direct
        // /file/getFileUrl and /file/downLoad endpoints (see FileAccessGuard). Silently drop
        // keys the current caller may not read rather than failing the whole response, since one
        // field can legitimately mix files the caller owns with files they don't.
        List<String> readableKeyList = fileAccessGuard.filterReadable(fileKeyList, SmartRequestUtil.getRequestUser());
        List<FileVO> fileKeyVOList = fileService.getFileList(readableKeyList);
        jsonGenerator.writeObject(fileKeyVOList);
    }
}
