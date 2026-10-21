package com.zhanglang.kb.rag.controller;

import com.zhanglang.kb.common.result.PageResult;
import com.zhanglang.kb.common.result.Result;
import com.zhanglang.kb.rag.dto.KbCreateRequest;
import com.zhanglang.kb.rag.dto.KbVO;
import com.zhanglang.kb.rag.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库接口。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@RestController
@RequestMapping("/api/v1/kb")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 创建知识库。
     *
     * @param request 创建请求
     * @return 新知识库 ID
     */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody KbCreateRequest request) {
        return Result.success(knowledgeBaseService.create(request));
    }

    /**
     * 分页查询知识库。
     *
     * @param keyword  名称关键字，可选
     * @param pageNo   页码，默认 1
     * @param pageSize 每页条数，默认 10
     * @return 分页结果
     */
    @GetMapping
    public Result<PageResult<KbVO>> page(@RequestParam(required = false) String keyword,
                                         @RequestParam(defaultValue = "1") long pageNo,
                                         @RequestParam(defaultValue = "10") long pageSize) {
        return Result.success(knowledgeBaseService.page(keyword, pageNo, pageSize));
    }

    /**
     * 查询知识库详情。
     *
     * @param id 知识库 ID
     * @return 详情
     */
    @GetMapping("/{id}")
    public Result<KbVO> detail(@PathVariable Long id) {
        return Result.success(knowledgeBaseService.detail(id));
    }

    /**
     * 更新知识库。
     *
     * @param id      知识库 ID
     * @param request 更新内容
     * @return 空响应
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody KbCreateRequest request) {
        knowledgeBaseService.update(id, request);
        return Result.success();
    }

    /**
     * 删除知识库。
     *
     * @param id 知识库 ID
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeBaseService.delete(id);
        return Result.success();
    }
}
