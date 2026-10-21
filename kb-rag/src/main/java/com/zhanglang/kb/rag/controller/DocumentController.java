package com.zhanglang.kb.rag.controller;

import com.zhanglang.kb.common.result.Result;
import com.zhanglang.kb.rag.dto.DocumentVO;
import com.zhanglang.kb.rag.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档接口。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /**
     * 上传文档。
     * <p>
     * 接口立即返回，解析与向量化在后台异步执行。前端拿到 docId 后轮询
     * {@code GET /api/v1/doc/{id}/status} 获取进度。
     *
     * @param kbId 知识库 ID
     * @param file 上传文件
     * @return 文档信息，parseStatus 初始为 PENDING
     */
    @PostMapping("/kb/{kbId}/doc")
    public Result<DocumentVO> upload(@PathVariable Long kbId,
                                     @RequestParam("file") MultipartFile file) {
        return Result.success(documentService.upload(kbId, file));
    }

    /**
     * 查询知识库下的文档列表。
     *
     * @param kbId 知识库 ID
     * @return 文档列表
     */
    @GetMapping("/kb/{kbId}/doc")
    public Result<List<DocumentVO>> list(@PathVariable Long kbId) {
        return Result.success(documentService.list(kbId));
    }

    /**
     * 查询文档解析状态（轮询接口）。
     *
     * @param id 文档 ID
     * @return 文档信息
     */
    @GetMapping("/doc/{id}/status")
    public Result<DocumentVO> status(@PathVariable Long id) {
        return Result.success(documentService.status(id));
    }

    /**
     * 删除文档。
     *
     * @param id 文档 ID
     * @return 空响应
     */
    @DeleteMapping("/doc/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return Result.success();
    }

    /**
     * 重试失败的文档解析。
     *
     * @param id 文档 ID
     * @return 空响应
     */
    @PostMapping("/doc/{id}/retry")
    public Result<Void> retry(@PathVariable Long id) {
        documentService.retry(id);
        return Result.success();
    }
}
