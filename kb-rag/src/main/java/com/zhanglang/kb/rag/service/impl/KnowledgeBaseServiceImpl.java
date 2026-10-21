package com.zhanglang.kb.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhanglang.kb.common.context.UserContext;
import com.zhanglang.kb.common.exception.BizException;
import com.zhanglang.kb.common.result.PageResult;
import com.zhanglang.kb.common.result.ResultCode;
import com.zhanglang.kb.rag.dto.KbCreateRequest;
import com.zhanglang.kb.rag.dto.KbDocCount;
import com.zhanglang.kb.rag.dto.KbVO;
import com.zhanglang.kb.rag.entity.DocumentDO;
import com.zhanglang.kb.rag.entity.KnowledgeBaseDO;
import com.zhanglang.kb.rag.manager.EmbeddingPipeline;
import com.zhanglang.kb.rag.manager.StorageManager;
import com.zhanglang.kb.rag.mapper.DocumentMapper;
import com.zhanglang.kb.rag.mapper.KnowledgeBaseMapper;
import com.zhanglang.kb.rag.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识库服务实现。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    /** 默认切分块大小 */
    private static final int DEFAULT_CHUNK_SIZE = 600;

    /** 默认重叠字符数 */
    private static final int DEFAULT_CHUNK_OVERLAP = 80;

    /** 默认召回条数 */
    private static final int DEFAULT_TOP_K = 5;

    /** 默认相似度阈值 */
    private static final BigDecimal DEFAULT_SIMILARITY = new BigDecimal("0.50");

    /** 启用状态 */
    private static final int STATUS_ENABLED = 1;

    /** 未删除 */
    private static final int NOT_DELETED = 0;

    /** 已删除 */
    private static final int DELETED = 1;

    /** 单页最大条数，防止前端传入 size=999999 拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 100L;

    /** 向量模型标识，仅用于展示 */
    @Value("${kb.embedding.model-name:bge-m3}")
    private String embeddingModelName;

    /** 当前向量维度，用于与模型实际维度校对 */
    @Value("${kb.embedding.dimension:1024}")
    private Integer embeddingDimension;

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    private final DocumentMapper documentMapper;

    private final EmbeddingPipeline embeddingPipeline;

    private final StorageManager storageManager;

    /**
     * 创建知识库。
     *
     * @param request 创建请求
     * @return 新知识库 ID
     */
    @Override
    public Long create(KbCreateRequest request) {
        KnowledgeBaseDO kb = new KnowledgeBaseDO();
        kb.setName(request.getName());
        kb.setDescription(request.getDescription());
        kb.setOwnerId(UserContext.getUserId());
        kb.setEmbeddingModel(embeddingModelName);
        // 落库维度必须与向量表实际维度一致，否则检索时会因维度不匹配直接报错
        kb.setEmbeddingDim(embeddingDimension);
        kb.setChunkSize(request.getChunkSize() == null ? DEFAULT_CHUNK_SIZE : request.getChunkSize());
        kb.setChunkOverlap(request.getChunkOverlap() == null
                ? DEFAULT_CHUNK_OVERLAP : request.getChunkOverlap());
        kb.setTopK(request.getTopK() == null ? DEFAULT_TOP_K : request.getTopK());
        kb.setSimilarityThreshold(request.getSimilarityThreshold() == null
                ? DEFAULT_SIMILARITY : request.getSimilarityThreshold());
        kb.setDocCount(0);
        kb.setStatus(STATUS_ENABLED);
        kb.setIsDeleted(NOT_DELETED);
        knowledgeBaseMapper.insert(kb);

        log.info("知识库创建成功, kbId={}, ownerId={}", kb.getId(), kb.getOwnerId());
        return kb.getId();
    }

    /**
     * 分页查询当前用户的知识库。
     *
     * @param keyword  名称关键字
     * @param pageNo   页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @Override
    public PageResult<KbVO> page(String keyword, long pageNo, long pageSize) {
        long safePageNo = Math.max(1, pageNo);
        long safePageSize = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);

        LambdaQueryWrapper<KnowledgeBaseDO> wrapper = Wrappers.<KnowledgeBaseDO>lambdaQuery()
                .eq(KnowledgeBaseDO::getOwnerId, UserContext.getUserId())
                .eq(KnowledgeBaseDO::getIsDeleted, NOT_DELETED)
                .like(StringUtils.hasText(keyword), KnowledgeBaseDO::getName, keyword)
                .orderByDesc(KnowledgeBaseDO::getGmtCreate);

        // 统计必须用不带 ORDER BY 的条件对象：
        // MyBatis-Plus 的 selectCount 会把 wrapper 里的排序原样拼进 COUNT 查询，
        // 生成 "SELECT COUNT(*) ... ORDER BY gmt_create DESC"，
        // 而排序列既不在 GROUP BY 也不在聚合函数里，H2 2.4 与 PostgreSQL 都会直接报错。
        // 排序对总数没有任何意义，剔除后语义也更干净。
        LambdaQueryWrapper<KnowledgeBaseDO> countWrapper = Wrappers.<KnowledgeBaseDO>lambdaQuery()
                .eq(KnowledgeBaseDO::getOwnerId, UserContext.getUserId())
                .eq(KnowledgeBaseDO::getIsDeleted, NOT_DELETED)
                .like(StringUtils.hasText(keyword), KnowledgeBaseDO::getName, keyword);

        Long total = knowledgeBaseMapper.selectCount(countWrapper);
        if (total == null || total == 0) {
            return PageResult.empty(safePageNo, safePageSize);
        }

        // 使用 LIMIT/OFFSET 手写分页，参数为程序计算的 long 值（非用户原始输入），不存在注入风险。
        // 未使用 MyBatis-Plus 分页插件的原因见 MybatisPlusConfig 类注释
        long offset = (safePageNo - 1) * safePageSize;
        List<KnowledgeBaseDO> records = knowledgeBaseMapper.selectList(
                wrapper.last("LIMIT " + safePageSize + " OFFSET " + offset));

        // 文档数实时统计：一次 GROUP BY 覆盖本页所有知识库，避免逐个 COUNT 的 N+1
        Map<Long, Integer> docCountMap = countDocsByKb(
                records.stream().map(KnowledgeBaseDO::getId).toList());

        List<KbVO> vos = records.stream()
                .map(kb -> toVO(kb, docCountMap.getOrDefault(kb.getId(), 0)))
                .toList();
        return new PageResult<>(safePageNo, safePageSize, total, vos);
    }

    /**
     * 批量统计可用文档数。
     * <p>
     * 空集合必须提前返回：{@code IN ()} 是非法 SQL，会直接抛语法错误。
     */
    private Map<Long, Integer> countDocsByKb(List<Long> kbIds) {
        if (CollectionUtils.isEmpty(kbIds)) {
            return Collections.emptyMap();
        }
        return documentMapper.countCompletedByKbIds(kbIds).stream()
                .collect(Collectors.toMap(KbDocCount::getKbId,
                        item -> item.getCount() == null ? 0 : item.getCount()));
    }

    /**
     * 查询详情。
     *
     * @param id 知识库 ID
     * @return 详情
     */
    @Override
    public KbVO detail(Long id) {
        KnowledgeBaseDO kb = getOwnedOrThrow(id);
        int docCount = countDocsByKb(List.of(id)).getOrDefault(id, 0);
        return toVO(kb, docCount);
    }

    /**
     * 更新知识库。
     * <p>
     * 注意：切分参数变更只影响后续上传的文档，已入库的向量不会自动重建。
     * 这是刻意设计 —— 重建全库向量成本很高，应由用户显式触发。
     *
     * @param id      知识库 ID
     * @param request 更新内容
     */
    @Override
    public void update(Long id, KbCreateRequest request) {
        KnowledgeBaseDO kb = getOwnedOrThrow(id);
        kb.setName(request.getName());
        kb.setDescription(request.getDescription());
        if (request.getChunkSize() != null) {
            kb.setChunkSize(request.getChunkSize());
        }
        if (request.getChunkOverlap() != null) {
            kb.setChunkOverlap(request.getChunkOverlap());
        }
        if (request.getTopK() != null) {
            kb.setTopK(request.getTopK());
        }
        if (request.getSimilarityThreshold() != null) {
            kb.setSimilarityThreshold(request.getSimilarityThreshold());
        }
        knowledgeBaseMapper.updateById(kb);
    }

    /**
     * 删除知识库及其全部文档与向量。
     * <p>
     * 顺序很重要：<b>先删向量，再删文档记录</b>。
     * 若反过来，一旦中途失败，文档记录已没了，向量就再也定位不到，成为永久垃圾数据。
     *
     * @param id 知识库 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        KnowledgeBaseDO kb = getOwnedOrThrow(id);

        List<DocumentDO> documents = documentMapper.selectList(Wrappers.<DocumentDO>lambdaQuery()
                .eq(DocumentDO::getKbId, id)
                .eq(DocumentDO::getIsDeleted, NOT_DELETED));

        for (DocumentDO document : documents) {
            embeddingPipeline.deleteByDocId(document.getId());
            storageManager.delete(document.getFileKey());
            document.setIsDeleted(DELETED);
            documentMapper.updateById(document);
        }

        kb.setIsDeleted(DELETED);
        knowledgeBaseMapper.updateById(kb);
        log.info("知识库已删除, kbId={}, 清理文档数={}", id, documents.size());
    }

    /**
     * 获取知识库并校验归属。
     *
     * @param id 知识库 ID
     * @return 知识库实体
     */
    @Override
    public KnowledgeBaseDO getOwnedOrThrow(Long id) {
        KnowledgeBaseDO kb = knowledgeBaseMapper.selectById(id);
        if (kb == null || Integer.valueOf(DELETED).equals(kb.getIsDeleted())) {
            throw new BizException(ResultCode.KB_NOT_FOUND);
        }
        // 越权访问返回 403 而非 404：知识库确实存在，只是无权访问，
        // 返回 404 会掩盖真实原因，增加排查成本
        if (!kb.getOwnerId().equals(UserContext.getUserId())) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
        return kb;
    }

    /**
     * 实体转展示对象。
     *
     * @param kb       知识库实体
     * @param docCount 实时统计出的可用文档数
     */
    private KbVO toVO(KnowledgeBaseDO kb, int docCount) {
        return KbVO.builder()
                .id(kb.getId())
                .name(kb.getName())
                .description(kb.getDescription())
                .docCount(docCount)
                .embeddingModel(kb.getEmbeddingModel())
                .embeddingDim(kb.getEmbeddingDim())
                .chunkSize(kb.getChunkSize())
                .chunkOverlap(kb.getChunkOverlap())
                .topK(kb.getTopK())
                .similarityThreshold(kb.getSimilarityThreshold())
                .status(kb.getStatus())
                .gmtCreate(kb.getGmtCreate())
                .build();
    }
}
