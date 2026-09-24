package net.lab1024.sa.base.module.support.file.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;
import jakarta.annotation.Resource;
import net.lab1024.sa.base.common.code.UserErrorCode;
import net.lab1024.sa.base.common.constant.StringConst;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.RequestUser;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.util.SmartEnumUtil;
import net.lab1024.sa.base.common.util.SmartPageUtil;
import net.lab1024.sa.base.common.util.SmartStringUtil;
import net.lab1024.sa.base.module.support.file.constant.FileFolderTypeEnum;
import net.lab1024.sa.base.module.support.file.dao.FileDao;
import net.lab1024.sa.base.module.support.file.domain.entity.FileEntity;
import net.lab1024.sa.base.module.support.file.domain.form.FileQueryForm;
import net.lab1024.sa.base.module.support.file.domain.vo.FileDownloadVO;
import net.lab1024.sa.base.module.support.file.domain.vo.FileUploadVO;
import net.lab1024.sa.base.module.support.file.domain.vo.FileVO;
import net.lab1024.sa.base.module.support.redis.RedisService;
import net.lab1024.sa.base.module.support.securityprotect.service.SecurityFileService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文件服务
 *
 */
@Service
public class FileService {

    /**
     * 文件名最大长度
     */
    private static final int FILE_NAME_MAX_LENGTH = 100;

    /**
     * 每个用户同时存在的未绑定暂存件上限（docs/decisions.md「P0 基线收口裁决」第 14 条）。
     */
    public static final int SCRATCH_MAX_UNBOUND_PER_USER = 100;

    @Resource
    private IFileStorageService fileStorageService;

    @Resource
    private FileDao fileDao;

    @Resource
    private SecurityFileService securityFileService;

    @Resource
    private FileAccessGuard fileAccessGuard;

    /**
     * 文件上传服务
     *
     * @param file
     * @param folderType 文件夹类型
     * @return
     */
    public ResponseDTO<FileUploadVO> fileUpload(MultipartFile file, Integer folderType, RequestUser requestUser) {
        FileFolderTypeEnum folderTypeEnum = SmartEnumUtil.getEnumByValue(folderType, FileFolderTypeEnum.class);
        if (null == folderTypeEnum) {
            return ResponseDTO.userErrorParam("文件夹错误");
        }

        if (null == file || file.getSize() == 0) {
            return ResponseDTO.userErrorParam("上传文件不能为空");
        }

        // 校验文件名称
        String originalFilename = file.getOriginalFilename();
        if (StringUtils.isBlank(originalFilename)) {
            return ResponseDTO.userErrorParam("上传文件名称不能为空");
        }

        if (originalFilename.length() > FILE_NAME_MAX_LENGTH) {
            return ResponseDTO.userErrorParam("文件名称最大长度为：" + FILE_NAME_MAX_LENGTH);
        }

        // 校验文件大小以及安全性
        ResponseDTO<String> validateFile = securityFileService.checkFile(file);
        if (!validateFile.getOk()) {
            return ResponseDTO.error(validateFile);
        }

        // F0: enum remains the sole folder whitelist; reject malformed storage paths.
        if (!FileKeyPolicy.isValid(folderTypeEnum.getFolder())) {
            return ResponseDTO.error(UserErrorCode.NO_PERMISSION);
        }
        // 暂存件必须有界：上传成功但表单没保存的垃圾若不清理会永久堆积，
        // 因此除定时回收外再加一道在制上限（超出即拒绝，让用户先完成绑定或等待回收）。
        if (folderTypeEnum == FileFolderTypeEnum.SCRATCH && requestUser != null
                && fileDao.countUnboundScratchByCreator(requestUser.getUserId(),
                requestUser.getUserType().getValue()) >= SCRATCH_MAX_UNBOUND_PER_USER) {
            return ResponseDTO.userErrorParam("未完成的暂存文件已达 " + SCRATCH_MAX_UNBOUND_PER_USER
                    + " 个上限，请先完成表单保存或等待系统清理");
        }

        // 进行上传
        ResponseDTO<FileUploadVO> response = fileStorageService.upload(file, folderTypeEnum.getFolder());
        if (!response.getOk()) {
            return response;
        }

        // 上传成功 保存记录数据库
        FileUploadVO uploadVO = response.getData();
        FileEntity fileEntity = new FileEntity();
        fileEntity.setFolderType(folderTypeEnum.getValue());
        fileEntity.setFileName(originalFilename);
        fileEntity.setFileSize(file.getSize());
        fileEntity.setFileKey(uploadVO.getFileKey());
        fileEntity.setFileType(uploadVO.getFileType());
        fileEntity.setCreatorId(requestUser == null ? null : requestUser.getUserId());
        fileEntity.setCreatorName(requestUser == null ? null : requestUser.getUserName());
        fileEntity.setCreatorUserType(requestUser == null ? null : requestUser.getUserType().getValue());
        fileDao.insert(fileEntity);

        // 将fileId 返回给前端
        uploadVO.setFileId(fileEntity.getFileId());

        return response;
    }

    /**
     * 按调用者身份批量读取文件信息（服务端展开 URL 的唯一受控入口）。
     *
     * <p>可读性判定与 {@code /support/file/getFileUrl} 等 HTTP 读接口共用
     * {@link FileAccessGuard} 一份规则；不可读的 key **不出现在结果里**
     * （而不是返回空 URL），否则「这个 key 存在」本身就成了旁路信号。
     */
    public List<FileVO> getFileList(List<String> fileKeyList, RequestUser requestUser) {
        return resolveWithUrl(fileAccessGuard.filterReadable(fileKeyList, requestUser));
    }

    /**
     * 无调用者身份的批量读取：只服务公开目录。
     *
     * <p>本方法没有任何主体可以用来判断授权，若照旧解析私有 key 的 URL，等于替调用方
     * 假设「他有权读」。因此非 {@code public/} 前缀一律不查询、不解析、不返回；
     * 业务读路径必须改用 {@link #getFileList(List, RequestUser)}。
     */
    public List<FileVO> getFileList(List<String> fileKeyList) {
        if (CollectionUtils.isEmpty(fileKeyList)) {
            return Lists.newArrayList();
        }
        String publicFolder = FileFolderTypeEnum.FOLDER_PUBLIC + StringConst.SEPARATOR_SLASH;
        return resolveWithUrl(fileKeyList.stream().filter(key -> key != null && key.startsWith(publicFolder)).toList());
    }

    /**
     * 只要元数据（存在性 / 文件名 / 大小），**永不生成 URL**。
     *
     * <p>给写路径回答「这个 key 真的存在吗」。它不需要、也不应该拿到 URL：
     * 拿到 URL 就意味着要替一个可能没有读取权的人解析私有附件。
     */
    public List<FileVO> getFileMetadata(List<String> fileKeyList) {
        if (CollectionUtils.isEmpty(fileKeyList)) {
            return Lists.newArrayList();
        }
        return fileDao.selectByFileKeyList(new HashSet<>(fileKeyList));
    }

    private List<FileVO> resolveWithUrl(List<String> fileKeyList) {
        if (CollectionUtils.isEmpty(fileKeyList)) {
            return Lists.newArrayList();
        }

        // 查询数据库，并获取 file url
        HashSet<String> fileKeySet = new HashSet<>(fileKeyList);
        Map<String, FileVO> fileMap = fileDao.selectByFileKeyList(fileKeySet)
                .stream().collect(Collectors.toMap(FileVO::getFileKey, Function.identity()));

        for (FileVO fileVO : fileMap.values()) {
            ResponseDTO<String> fileUrlResponse = fileStorageService.getFileUrl(fileVO.getFileKey());
            if (fileUrlResponse.getOk()) {
                fileVO.setFileUrl(fileUrlResponse.getData());
            }
        }

        // 返回结果
        List<FileVO> result = Lists.newArrayListWithCapacity(fileKeyList.size());
        for (String fileKey : fileKeyList) {
            FileVO fileVO = fileMap.get(fileKey);
            if (fileVO != null) {
                result.add(fileVO);
            }
        }

        return result;
    }


    /**
     * 根据文件绝对路径 获取文件URL
     * 支持单个 key 逗号分隔的形式
     *
     * @param fileKeys
     * @return
     */
    public ResponseDTO<String> getFileUrl(String fileKeys) {
        if (StringUtils.isBlank(fileKeys)) {
            return ResponseDTO.error(UserErrorCode.PARAM_ERROR);
        }

        List<String> fileKeyArray = StrUtil.split(fileKeys, StringConst.SEPARATOR);
        List<String> fileUrlList = Lists.newArrayListWithCapacity(fileKeyArray.size());
        for (String fileKey : fileKeyArray) {
            ResponseDTO<String> fileUrlResponse = fileStorageService.getFileUrl(fileKey);
            if (fileUrlResponse.getOk()) {
                fileUrlList.add(fileUrlResponse.getData());
            }
        }
        return ResponseDTO.ok(SmartStringUtil.join(StringConst.SEPARATOR, fileUrlList));
    }


    /**
     * 根据文件服务类型 和 FileKey 下载文件
     */
    public ResponseDTO<FileDownloadVO> getDownloadFile(String fileKey, String userAgent) {
        FileVO fileVO = fileDao.getByFileKey(fileKey);
        if (fileVO == null) {
            return ResponseDTO.userErrorParam("文件不存在");
        }

        // 根据文件服务类 获取对应文件服务 查询 url
        ResponseDTO<FileDownloadVO> download = fileStorageService.download(fileKey);
        if (download.getOk()) {
            download.getData().getMetadata().setFileName(fileVO.getFileName());
        }
        return download;
    }

    /**
     * 分页查询
     */
    public PageResult<FileVO> queryPage(FileQueryForm queryForm) {
        Page<?> page = SmartPageUtil.convert2PageQuery(queryForm);
        List<FileVO> list = fileDao.queryPage(page, queryForm);
        return SmartPageUtil.convert2PageResult(page, list);
    }


}
