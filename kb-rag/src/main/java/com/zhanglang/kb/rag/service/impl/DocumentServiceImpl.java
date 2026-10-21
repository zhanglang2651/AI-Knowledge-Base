package com.zhanglang.kb.rag.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhanglang.kb.common.exception.BizException;
import com.zhanglang.kb.common.result.ResultCode;
import com.zhanglang.kb.rag.dto.DocumentVO;
import com.zhanglang.kb.rag.entity.DocumentDO;
import com.zhanglang.kb.rag.entity.KnowledgeBaseDO;
import com.zhanglang.kb.rag.manager.EmbeddingPipeline;
import com.zhanglang.kb.rag.manager.StorageManager;
import com.zhanglang.kb.rag.mapper.DocumentMapper;
import com.zhanglang.kb.rag.queue.DocumentStreamProducer;
import com.zhanglang.kb.rag.service.DocumentProcessService;
import com.zhanglang.kb.rag.service.DocumentService;
import com.zhanglang.kb.rag.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * 文档服务实现。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    /**
     * 支持的文件后缀白名单。
     * 采用<b>白名单</b>而非黑名单：黑名单永远漏（.jsp、.sh、双扩展名都能绕过），
     * 只放行已知安全的格式才是可控的。
     */
    private static final Set<String> ALLOWED_SUFFIX = Set.of("pdf", "docx", "doc", "txt", "md");

    /** 单文件大小上限：50MB。与 application.yml 中的 multipart 配置需保持一致 */
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    /** 初始解析状态 */
    private static final String STATUS_PENDING = "PENDING";

    /** 失败状态 */
    private static final String STATUS_FAILED = "FAILED";

    /** 未删除 */
    private static final int NOT_DELETED = 0;

    /** 已删除 */
    private static final int DELETED = 1;

    private final DocumentMapper documentMapper;

    private final KnowledgeBaseService knowledgeBaseService;

    private final StorageManager storageManager;

    private final EmbeddingPipeline embeddingPipeline;

    /**
     * 解析任务执行器。
     * <p>
     * 用 {@link ObjectProvider} 而不是直接注入：关闭队列开关后生产者 Bean 不存在，
     * 直接注入会导致启动失败。这里取不到就退化为本地线程池异步处理。
     */
    private final ObjectProvider<DocumentStreamProducer> streamProducerProvider;

    /** 本地解析服务，队列关闭时直接调用 */
    private final DocumentProcessService documentProcessService;

    /** 队列关闭时用于异步执行解析，避免阻塞上传接口 */
    private final ThreadPoolTaskExecutor docExecutor;

    /**
     * 上传文档。
     * <p>
     * 只做「校验 + 落盘 + 落库 + 投递任务」四件事，全部是毫秒级操作，
     * 解析与向量化交给消费者。若在这里同步解析，一个 100 页 PDF 会让接口挂 1-2 分钟，
     * 前端必然超时，网关也会主动断开。
     *
     * @param kbId 知识库 ID
     * @param file 上传文件
     * @return 文档信息
     */
    @Override
    public DocumentVO upload(Long kbId, MultipartFile file) {
        // 校验归属：必须放在最前面，避免无权用户触发文件落盘
        KnowledgeBaseDO kb = knowledgeBaseService.getOwnedOrThrow(kbId);

        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.PARAM_INVALID, "上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(ResultCode.FILE_TOO_LARGE, "单个文件不能超过 50MB");
        }

        String originalName = file.getOriginalFilename();
        String suffix = extractSuffix(originalName);
        if (!ALLOWED_SUFFIX.contains(suffix)) {
            throw new BizException(ResultCode.FILE_TYPE_UNSUPPORTED,
                    "仅支持 pdf、docx、doc、txt、md 格式");
        }

        String md5 = calcMd5(file);
        // 同库内 MD5 去重：重复上传同一文件会产生两套完全相同的向量，
        // 检索时同一片段被命中两次，挤占上下文名额
        Long duplicated = documentMapper.selectCount(Wrappers.<DocumentDO>lambdaQuery()
                .eq(DocumentDO::getKbId, kbId)
                .eq(DocumentDO::getFileMd5, md5)
                .eq(DocumentDO::getIsDeleted, NOT_DELETED));
        if (duplicated != null && duplicated > 0) {
            throw new BizException(ResultCode.PARAM_INVALID, "该文件已存在于当前知识库");
        }

        String fileKey = storageManager.save(file, kbId);

        DocumentDO document = new DocumentDO();
        document.setKbId(kbId);
        document.setFileName(StringUtils.hasText(originalName) ? originalName : "未命名文件");
        document.setFileKey(fileKey);
        document.setFileSize(file.getSize());
        document.setFileType(suffix);
        document.setFileMd5(md5);
        document.setPageCount(0);
        document.setChunkCount(0);
        document.setParseStatus(STATUS_PENDING);
        document.setRetryCount(0);
        document.setIsDeleted(NOT_DELETED);
        documentMapper.insert(document);

        // 落库成功后再投递，保证消费者一定能查到这条记录
        dispatchParse(document.getId());

        log.info("文档上传成功, docId={}, kbId={}, size={}", document.getId(), kbId, file.getSize());
        return toVO(document);
    }

    /**
     * 查询知识库下的文档列表。
     *
     * @param kbId 知识库 ID
     * @return 文档列表
     */
    @Override
    public List<DocumentVO> list(Long kbId) {
        // 校验归属，避免通过猜测 kbId 拿到他人文档列表
        knowledgeBaseService.getOwnedOrThrow(kbId);
        List<DocumentDO> documents = documentMapper.selectList(Wrappers.<DocumentDO>lambdaQuery()
                .eq(DocumentDO::getKbId, kbId)
                .eq(DocumentDO::getIsDeleted, NOT_DELETED)
                .orderByDesc(DocumentDO::getGmtCreate));
        return documents.stream().map(this::toVO).toList();
    }

    /**
     * 查询单个文档状态。
     *
     * @param docId 文档 ID
     * @return 文档信息
     */
    @Override
    public DocumentVO status(Long docId) {
        return toVO(getOwnedOrThrow(docId));
    }

    /**
     * 删除文档。
     * <p>
     * 顺序：先删向量，再删文件，最后逻辑删除记录。
     * 理由同知识库删除 —— 记录一旦先删，向量就再也定位不到。
     *
     * @param docId 文档 ID
     */
    @Override
    public void delete(Long docId) {
        DocumentDO document = getOwnedOrThrow(docId);

        embeddingPipeline.deleteByDocId(docId);
        storageManager.delete(document.getFileKey());

        document.setIsDeleted(DELETED);
        documentMapper.updateById(document);
        log.info("文档已删除, docId={}", docId);
    }

    /**
     * 重试失败的文档。
     *
     * @param docId 文档 ID
     */
    @Override
    public void retry(Long docId) {
        DocumentDO document = getOwnedOrThrow(docId);
        if (!STATUS_FAILED.equals(document.getParseStatus())) {
            throw new BizException(ResultCode.PARAM_INVALID, "仅失败状态的文档可以重试");
        }
        // 重置状态后重新投递，重试次数保留，用于统计"这份文件反复失败"
        document.setParseStatus(STATUS_PENDING);
        document.setErrorMsg(null);
        documentMapper.updateById(document);
        dispatchParse(docId);
        log.info("文档重试已投递, docId={}, retryCount={}", docId, document.getRetryCount());
    }

    /**
     * 投递解析任务。
     * <p>
     * 两种模式二选一：
     * <ul>
     *     <li><b>队列模式</b>（默认）：投递到 Redis Stream，由消费者异步处理，支持多实例与失败重投</li>
     *     <li><b>本地模式</b>（{@code kb.queue.enabled=false}）：提交到本地线程池，
     *         功能等价但不持久化，服务重启时队列中的任务会丢失</li>
     * </ul>
     * 两条路径都不阻塞上传接口。
     */
    private void dispatchParse(Long docId) {
        DocumentStreamProducer producer = streamProducerProvider.getIfAvailable();
        if (producer != null) {
            producer.submit(docId);
            return;
        }
        log.info("队列已关闭，改为本地异步解析, docId={}", docId);
        docExecutor.execute(() -> {
            try {
                documentProcessService.process(docId);
            } catch (Exception ex) {
                // process 内部已把失败状态写库，这里只兜底记录，避免线程池吞掉异常
                log.error("本地解析任务异常, docId={}", docId, ex);
            }
        });
    }

    /**
     * 查询文档并校验其所属知识库归当前用户所有。
     */
    private DocumentDO getOwnedOrThrow(Long docId) {
        DocumentDO document = documentMapper.selectById(docId);
        if (document == null || Integer.valueOf(DELETED).equals(document.getIsDeleted())) {
            throw new BizException(ResultCode.DOC_NOT_FOUND);
        }
        // 通过知识库归属间接校验文档归属，避免在文档表冗余 ownerId
        knowledgeBaseService.getOwnedOrThrow(document.getKbId());
        return document;
    }

    /**
     * 计算文件 MD5。
     * 使用 Spring 的 DigestUtils，内部走流式摘要，不会把大文件整体读入内存。
     */
    private String calcMd5(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return DigestUtils.md5DigestAsHex(in);
        } catch (IOException ex) {
            log.error("计算文件 MD5 失败", ex);
            throw new BizException(ResultCode.SYSTEM_ERROR, "文件校验失败");
        }
    }

    /**
     * 提取小写后缀，无后缀返回空串。
     */
    private String extractSuffix(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase();
    }

    /**
     * 实体转展示对象。
     */
    private DocumentVO toVO(DocumentDO document) {
        return DocumentVO.builder()
                .id(document.getId())
                .kbId(document.getKbId())
                .fileName(document.getFileName())
                .fileSize(document.getFileSize())
                .fileType(document.getFileType())
                .pageCount(document.getPageCount())
                .chunkCount(document.getChunkCount())
                .parseStatus(document.getParseStatus())
                .errorMsg(document.getErrorMsg())
                .gmtCreate(document.getGmtCreate())
                .build();
    }
}
