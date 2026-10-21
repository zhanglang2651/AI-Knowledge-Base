package com.zhanglang.kb.rag.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhanglang.kb.common.exception.BizException;
import com.zhanglang.kb.common.result.ResultCode;
import com.zhanglang.kb.common.util.AesUtil;
import com.zhanglang.kb.rag.dto.ModelConfigRequest;
import com.zhanglang.kb.rag.dto.ModelConfigVO;
import com.zhanglang.kb.rag.dto.StatOverviewVO;
import com.zhanglang.kb.rag.dto.StatTrendItemVO;
import com.zhanglang.kb.rag.entity.AiModelConfigDO;
import com.zhanglang.kb.rag.mapper.AiCallLogMapper;
import com.zhanglang.kb.rag.mapper.AiModelConfigMapper;
import com.zhanglang.kb.rag.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台管理服务实现。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    /** 聊天模型类型 */
    private static final String TYPE_CHAT = "chat";

    /** 向量模型类型 */
    private static final String TYPE_EMBEDDING = "embedding";

    /** 启用状态 */
    private static final int STATUS_ENABLED = 1;

    /** 默认模型标记 */
    private static final int DEFAULT_FLAG = 1;

    /** 趋势统计允许的最大天数，防止前端传入超大值导致全表扫描 */
    private static final int MAX_TREND_DAYS = 90;

    private final AiModelConfigMapper modelConfigMapper;

    private final AiCallLogMapper callLogMapper;

    private final JdbcTemplate jdbcTemplate;

    private final AesUtil aesUtil;

    /**
     * 查询模型配置列表。
     *
     * @param modelType 类型过滤
     * @return 配置列表
     */
    @Override
    public List<ModelConfigVO> listModels(String modelType) {
        List<AiModelConfigDO> configs = modelConfigMapper.selectList(
                Wrappers.<AiModelConfigDO>lambdaQuery()
                        .eq(StringUtils.hasText(modelType), AiModelConfigDO::getModelType, modelType)
                        .orderByAsc(AiModelConfigDO::getModelType)
                        .orderByDesc(AiModelConfigDO::getIsDefault)
                        .orderByDesc(AiModelConfigDO::getId));
        return configs.stream().map(this::toVO).toList();
    }

    /**
     * 新增模型配置。
     *
     * @param request 配置内容
     * @return 新配置 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createModel(ModelConfigRequest request) {
        if (!StringUtils.hasText(request.getApiKey())) {
            // 新增时 key 必填：没有 key 的配置无法调用，存进去只会让人误以为配好了
            throw new BizException(ResultCode.PARAM_INVALID, "新增配置时 API Key 不能为空");
        }

        AiModelConfigDO config = new AiModelConfigDO();
        config.setProvider(request.getProvider());
        config.setModelName(request.getModelName());
        config.setModelType(request.getModelType());
        config.setBaseUrl(request.getBaseUrl());
        config.setApiKey(aesUtil.encrypt(request.getApiKey()));
        config.setInputPrice(request.getInputPrice());
        config.setOutputPrice(request.getOutputPrice());
        config.setIsDefault(request.getIsDefault() == null ? 0 : request.getIsDefault());
        config.setStatus(request.getStatus() == null ? STATUS_ENABLED : request.getStatus());
        modelConfigMapper.insert(config);

        if (Integer.valueOf(DEFAULT_FLAG).equals(config.getIsDefault())) {
            clearOtherDefaults(config.getId(), config.getModelType());
        }
        log.info("新增模型配置, id={}, model={}", config.getId(), config.getModelName());
        return config.getId();
    }

    /**
     * 更新模型配置。
     *
     * @param id      配置 ID
     * @param request 配置内容
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateModel(Long id, ModelConfigRequest request) {
        AiModelConfigDO config = requireConfig(id);
        config.setProvider(request.getProvider());
        config.setModelName(request.getModelName());
        config.setModelType(request.getModelType());
        config.setBaseUrl(request.getBaseUrl());
        // 留空表示不修改：前端只能拿到脱敏值，强制重填 key 的体验不可接受
        if (StringUtils.hasText(request.getApiKey())) {
            config.setApiKey(aesUtil.encrypt(request.getApiKey()));
        }
        config.setInputPrice(request.getInputPrice());
        config.setOutputPrice(request.getOutputPrice());
        if (request.getIsDefault() != null) {
            config.setIsDefault(request.getIsDefault());
        }
        if (request.getStatus() != null) {
            config.setStatus(request.getStatus());
        }
        modelConfigMapper.updateById(config);

        if (Integer.valueOf(DEFAULT_FLAG).equals(config.getIsDefault())) {
            clearOtherDefaults(config.getId(), config.getModelType());
        }
    }

    /**
     * 删除模型配置。
     *
     * @param id 配置 ID
     */
    @Override
    public void deleteModel(Long id) {
        requireConfig(id);
        modelConfigMapper.deleteById(id);
        log.info("删除模型配置, id={}", id);
    }

    /**
     * 连通性测试。
     * <p>
     * 真实发起一次最小请求，而不是只判断"字段是否填了"——
     * 配错 baseUrl 或 key 失效是最常见的问题，只有真调一次才能发现。
     *
     * @param id 配置 ID
     * @return 成功返回耗时，失败返回原因
     */
    @Override
    public String testModel(Long id) {
        AiModelConfigDO config = requireConfig(id);
        String apiKey = aesUtil.decrypt(config.getApiKey());
        if (!StringUtils.hasText(apiKey)) {
            return "失败：密钥解密失败，请重新录入 API Key";
        }

        boolean isChat = TYPE_CHAT.equals(config.getModelType());
        String url = trimTrailingSlash(config.getBaseUrl()) + (isChat ? "/chat/completions" : "/embeddings");

        Map<String, Object> body = new HashMap<>(4);
        body.put("model", config.getModelName());
        if (isChat) {
            body.put("messages", List.of(Map.of("role", "user", "content", "ping")));
            body.put("max_tokens", 5);
        } else {
            body.put("input", "ping");
        }

        long start = System.currentTimeMillis();
        try {
            RestClient.create()
                    .post()
                    .uri(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            long cost = System.currentTimeMillis() - start;
            log.info("模型连通性测试通过, id={}, 耗时={}ms", id, cost);
            return "连接成功，耗时 " + cost + " ms";
        } catch (Exception ex) {
            // 只返回异常消息，不回传完整堆栈：堆栈里可能包含请求头与密钥
            String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            log.warn("模型连通性测试失败, id={}, reason={}", id, reason);
            return "连接失败：" + reason;
        }
    }

    /**
     * 调用统计总览。
     *
     * @return 总览指标
     */
    @Override
    public StatOverviewVO overview() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        // 聚合直接在数据库完成，不把明细捞到内存再统计：
        // 调用日志是增长最快的表，全量捞取在几千条后就会明显拖慢接口
        Map<String, Object> today = jdbcTemplate.queryForMap(
                "SELECT COUNT(*) AS calls, "
                        + "COALESCE(SUM(prompt_tokens + completion_tokens), 0) AS tokens, "
                        + "COALESCE(SUM(cost), 0) AS cost, "
                        + "COALESCE(SUM(CASE WHEN is_success = 0 THEN 1 ELSE 0 END), 0) AS fails, "
                        + "COALESCE(AVG(latency_ms), 0) AS avg_latency "
                        + "FROM ai_call_log WHERE gmt_create >= ?",
                todayStart);

        Map<String, Object> total = jdbcTemplate.queryForMap(
                "SELECT COUNT(*) AS calls, "
                        + "COALESCE(SUM(prompt_tokens + completion_tokens), 0) AS tokens "
                        + "FROM ai_call_log");

        long todayCalls = toLong(today.get("calls"));
        long failCount = toLong(today.get("fails"));
        BigDecimal failRate = todayCalls == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(failCount)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(todayCalls), 1, RoundingMode.HALF_UP);

        return StatOverviewVO.builder()
                .todayCalls(todayCalls)
                .todayTokens(toLong(today.get("tokens")))
                .todayCost(toDecimal(today.get("cost")))
                .todayFailCount(failCount)
                .todayFailRate(failRate)
                .todayAvgLatencyMs(toLong(today.get("avg_latency")))
                .totalCalls(toLong(total.get("calls")))
                .totalTokens(toLong(total.get("tokens")))
                .build();
    }

    /**
     * 调用趋势。
     *
     * @param days 统计天数
     * @return 按日期升序的统计列表
     */
    @Override
    public List<StatTrendItemVO> trend(int days) {
        int safeDays = Math.min(Math.max(1, days), MAX_TREND_DAYS);
        LocalDateTime from = LocalDate.now().minusDays(safeDays - 1L).atStartOfDay();

        // CAST(... AS DATE) 在 PostgreSQL 与 H2 下行为一致，
        // 不用 DATE_TRUNC：H2 不支持该函数，会让 h2 profile 直接报错
        return jdbcTemplate.query(
                "SELECT CAST(gmt_create AS DATE) AS stat_date, "
                        + "COUNT(*) AS call_count, "
                        + "COALESCE(SUM(prompt_tokens + completion_tokens), 0) AS total_tokens, "
                        + "COALESCE(SUM(cost), 0) AS total_cost, "
                        + "COALESCE(SUM(CASE WHEN is_success = 0 THEN 1 ELSE 0 END), 0) AS fail_count "
                        + "FROM ai_call_log WHERE gmt_create >= ? "
                        + "GROUP BY CAST(gmt_create AS DATE) ORDER BY stat_date",
                (rs, rowNum) -> {
                    StatTrendItemVO item = new StatTrendItemVO();
                    item.setStatDate(String.valueOf(rs.getDate("stat_date")));
                    item.setCallCount(rs.getLong("call_count"));
                    item.setTotalTokens(rs.getLong("total_tokens"));
                    item.setTotalCost(rs.getBigDecimal("total_cost"));
                    item.setFailCount(rs.getLong("fail_count"));
                    return item;
                },
                from);
    }

    /**
     * 查询配置，不存在则抛出业务异常。
     */
    private AiModelConfigDO requireConfig(Long id) {
        AiModelConfigDO config = modelConfigMapper.selectById(id);
        if (config == null) {
            throw new BizException(ResultCode.PARAM_INVALID, "模型配置不存在");
        }
        return config;
    }

    /**
     * 清除同类型下的其他默认标记。
     * <p>
     * 同类型只允许一个默认模型：否则「默认用哪个」就变成随机结果，
     * 排查问题时会非常困惑。
     */
    private void clearOtherDefaults(Long keepId, String modelType) {
        AiModelConfigDO update = new AiModelConfigDO();
        update.setIsDefault(0);
        modelConfigMapper.update(update, Wrappers.<AiModelConfigDO>lambdaUpdate()
                .eq(AiModelConfigDO::getModelType, modelType)
                .eq(AiModelConfigDO::getIsDefault, DEFAULT_FLAG)
                .ne(AiModelConfigDO::getId, keepId));
    }

    /**
     * 去掉 baseUrl 末尾的斜杠，避免拼出 {@code //chat/completions} 这种路径。
     */
    private String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * 实体转展示对象（密钥脱敏）。
     */
    private ModelConfigVO toVO(AiModelConfigDO config) {
        String plainKey = aesUtil.decrypt(config.getApiKey());
        return ModelConfigVO.builder()
                .id(config.getId())
                .provider(config.getProvider())
                .modelName(config.getModelName())
                .modelType(config.getModelType())
                .baseUrl(config.getBaseUrl())
                .apiKeyMasked(aesUtil.mask(plainKey))
                .inputPrice(config.getInputPrice())
                .outputPrice(config.getOutputPrice())
                .isDefault(config.getIsDefault())
                .status(config.getStatus())
                .gmtCreate(config.getGmtCreate())
                .build();
    }

    /**
     * 安全转 long：数据库聚合结果可能为 null 或不同数值类型。
     */
    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        return ((Number) value).longValue();
    }

    /**
     * 安全转 BigDecimal。
     */
    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.toString());
    }
}
