package com.zhanglang.kb.rag.manager;

import com.zhanglang.kb.common.exception.BizException;
import com.zhanglang.kb.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 文件存储管理器（本地磁盘实现）。
 * <p>
 * 默认落盘到 {@code kb.storage.root}，按「日期 + 知识库 ID」分目录，避免单目录文件数过多
 * 导致的 ext4/NTFS 目录索引性能下降。
 * <p>
 * <b>为什么先用本地实现</b>：MinIO 需要额外容器与账户配置，会抬高"跑起来"的门槛。
 * 这里把存储行为收敛到本类的三个方法（保存/读取/删除），
 * 后续切换到 MinIO 或 OSS 只需新增一个实现类，业务代码零改动。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Slf4j
@Component
public class StorageManager {

    /** 目录名格式：yyyyMMdd */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final Path rootPath;

    /**
     * 构造存储管理器。
     *
     * @param root 存储根目录，默认 ./data/files
     */
    public StorageManager(@Value("${kb.storage.root:./data/files}") String root) {
        this.rootPath = Paths.get(root).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootPath);
        } catch (IOException ex) {
            // 根目录创建失败属于部署配置问题，启动即失败比运行到上传时才报错更好
            throw new IllegalStateException("无法创建文件存储目录: " + this.rootPath, ex);
        }
        log.info("文件存储根目录: {}", this.rootPath);
    }

    /**
     * 保存上传文件。
     *
     * @param file 上传的文件
     * @param kbId 知识库 ID，用于分目录
     * @return 相对存储键，形如 {@code 20261020/1/xxxx.pdf}，入库时持久化该值
     * @throws BizException 写入失败时抛出
     */
    public String save(MultipartFile file, Long kbId) {
        String dateDir = LocalDate.now().format(DATE_FORMATTER);
        // 用 UUID 重命名，避免原文件名中的特殊字符、重名、路径穿越等问题
        String suffix = extractSuffix(file.getOriginalFilename());
        String fileName = UUID.randomUUID().toString().replace("-", "") + suffix;
        String key = dateDir + "/" + kbId + "/" + fileName;

        Path target = resolveSafely(key);
        try {
            Files.createDirectories(target.getParent());
            // 直接流式拷贝，不把整个文件读进内存，避免大文件导致堆溢出
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            log.error("文件写入失败, key={}", key, ex);
            throw new BizException(ResultCode.SYSTEM_ERROR, "文件保存失败");
        }
        return key;
    }

    /**
     * 读取文件全部字节。
     * <p>
     * 仅用于解析，调用方需自行保证文件大小在限制范围内
     * （上传入口已限制 50MB，此处不再重复校验）。
     *
     * @param key 相对存储键
     * @return 文件内容
     * @throws BizException 文件不存在或读取失败时抛出
     */
    public byte[] read(String key) {
        Path target = resolveSafely(key);
        try {
            return Files.readAllBytes(target);
        } catch (IOException ex) {
            log.error("文件读取失败, key={}", key, ex);
            throw new BizException(ResultCode.DOC_PARSE_FAILED, "文件读取失败，可能已被清理");
        }
    }

    /**
     * 删除文件。文件不存在时静默返回，保证删除操作的幂等性。
     *
     * @param key 相对存储键
     */
    public void delete(String key) {
        Path target = resolveSafely(key);
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            // 删除失败不影响业务主流程，仅记录日志，可由定时任务兜底清理
            log.warn("文件删除失败, key={}", key, ex);
        }
    }

    /**
     * 将相对键解析为绝对路径，并校验未越出根目录。
     * <p>
     * 若 key 来自外部输入（如接口参数），必须做此校验，
     * 否则攻击者可用 {@code ../../etc/passwd} 读取任意文件。
     *
     * @param key 相对存储键
     * @return 绝对路径
     * @throws BizException 路径越界时抛出
     */
    private Path resolveSafely(String key) {
        Path resolved = rootPath.resolve(key).normalize();
        if (!resolved.startsWith(rootPath)) {
            log.warn("检测到路径穿越尝试, key={}", key);
            throw new BizException(ResultCode.PARAM_INVALID, "非法的文件路径");
        }
        return resolved;
    }

    /**
     * 提取文件后缀（含点），无后缀时返回空字符串。
     *
     * @param originalFilename 原始文件名
     * @return 形如 {@code .pdf}
     */
    private String extractSuffix(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(dotIndex).toLowerCase();
    }
}
