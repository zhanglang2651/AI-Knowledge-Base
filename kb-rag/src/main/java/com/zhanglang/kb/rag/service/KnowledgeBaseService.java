package com.zhanglang.kb.rag.service;

import com.zhanglang.kb.common.result.PageResult;
import com.zhanglang.kb.rag.dto.KbCreateRequest;
import com.zhanglang.kb.rag.dto.KbVO;
import com.zhanglang.kb.rag.entity.KnowledgeBaseDO;

/**
 * 知识库服务。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
public interface KnowledgeBaseService {

    /**
     * 创建知识库。
     *
     * @param request 创建请求
     * @return 新知识库 ID
     */
    Long create(KbCreateRequest request);

    /**
     * 分页查询当前用户的知识库。
     *
     * @param keyword  名称关键字，可为空
     * @param pageNo   页码，从 1 开始
     * @param pageSize 每页条数
     * @return 分页结果
     */
    PageResult<KbVO> page(String keyword, long pageNo, long pageSize);

    /**
     * 查询详情。
     *
     * @param id 知识库 ID
     * @return 详情
     */
    KbVO detail(Long id);

    /**
     * 更新知识库。
     *
     * @param id      知识库 ID
     * @param request 更新内容
     */
    void update(Long id, KbCreateRequest request);

    /**
     * 删除知识库及其全部文档与向量。
     *
     * @param id 知识库 ID
     */
    void delete(Long id);

    /**
     * 获取知识库并校验归属。供其他服务内部调用，避免重复写权限判断。
     *
     * @param id 知识库 ID
     * @return 知识库实体
     */
    KnowledgeBaseDO getOwnedOrThrow(Long id);
}
