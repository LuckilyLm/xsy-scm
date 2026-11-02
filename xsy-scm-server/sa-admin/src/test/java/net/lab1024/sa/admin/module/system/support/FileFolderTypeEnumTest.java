package net.lab1024.sa.admin.module.system.support;

import net.lab1024.sa.base.module.support.file.constant.FileFolderTypeEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 2026-11-02 口径：当前所有业务上传目录都公开；后续新增敏感目录时才允许出现 private/。
 */
class FileFolderTypeEnumTest {

    @Test
    void allCurrentUploadFoldersArePublic() {
        assertThat(FileFolderTypeEnum.values()).allSatisfy(
                folder -> assertThat(folder.getFolder()).startsWith(FileFolderTypeEnum.FOLDER_PUBLIC + "/"));
    }
}
