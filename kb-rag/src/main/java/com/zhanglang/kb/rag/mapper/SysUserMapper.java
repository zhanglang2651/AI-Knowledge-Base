package com.zhanglang.kb.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhanglang.kb.rag.entity.SysUserDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表数据访问接口。
 * <p>
 * 继承 {@link BaseMapper} 已具备单表增删改查能力，无需再写 XML。
 * 只有跨表联查、批量更新等 BaseMapper 覆盖不到的场景，才在 resources/mapper 下补 XML，
 * 并且禁止在 XML 中使用 {@code ${}} 拼接业务参数（SQL 注入风险，手册安全规约强制项）。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUserDO> {
}
