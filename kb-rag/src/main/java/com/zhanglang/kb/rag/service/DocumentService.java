package com.zhanglang.kb.rag.service;

import com.zhanglang.kb.rag.dto.DocumentVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档服务。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
public interface DocumentService {

    /**
     * 上传文档。立即返回，解析与向量化在后台异步执行。
     *
     * @param kbId 知识库 ID
     * @param file 上传文件
     * @return 文档信息，不含解析结果
     */
    DocumentVO upload(Long kbId, MultipartFile file);

    /**
     * 查询知识库下的文档列表。
     *
     * @param kbId 知识库 ID
     * @return 文档列表
     */
    List<DocumentVO> list(Long kbId);

    /**
     * 查询单个文档状态，供前端轮询。
     *
     * @param docId 文档 ID
     * @return 文档信息
     */
    DocumentVO status(Long docId);

    /**
     * 删除文档，同时清理向量与文件。
     *
     * @param docId 文档 ID
     */
    void delete(Long docId);

    /**
     * 重试失败的文档解析。
     *
     * @param docId 文档 ID
     */
    void retry(Long docId);
}
