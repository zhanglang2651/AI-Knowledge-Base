package com.zhanglang.kb.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhanglang.kb.rag.entity.KnowledgeBaseDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库数据访问接口。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBaseDO> {
}
