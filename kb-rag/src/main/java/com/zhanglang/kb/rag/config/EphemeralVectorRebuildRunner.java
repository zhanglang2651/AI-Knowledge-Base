package com.zhanglang.kb.rag.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhanglang.kb.rag.entity.DocumentDO;
import com.zhanglang.kb.rag.mapper.DocumentMapper;
import com.zhanglang.kb.rag.service.DocumentProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 内存向量库的启动自愈。
 * <p>
 * <b>解决的痛点：</b>h2 模式下向量存在 JVM 堆内存里，但文档记录在 H2 文件中。
 * 服务重启后会出现「文档状态显示 COMPLETED，但问答检索不到任何内容」的矛盾状态，
 * 用户会以为系统坏了。
 * <p>
 * 做法：启动时把所有 COMPLETED 的文档重置为 PENDING 并重新投递解析，
 * 让文档状态与实际向量数据重新对齐。
 * <p>
 * <b>仅在 {@code kb.vector.ephemeral=true} 时启用</b>（h2 profile 专用）。
 * prod 用 pgvector 持久化，重启不需要也不应该重建向量 ——
 * 全量重建会消耗大量 embedding 额度。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "kb.vector.ephemeral", havingValue = "true")
public class EphemeralVectorRebuildRunner implements ApplicationRunner {

    /** 已完成状态 */
    private static final String STATUS_COMPLETED = "COMPLETED";

    /** 待解析状态 */
    private static final String STATUS_PENDING = "PENDING";

    /** 未删除 */
    private static final int NOT_DELETED = 0;

    private final DocumentMapper documentMapper;

    private final DocumentProcessService documentProcessService;

    private final ThreadPoolTaskExecutor docExecutor;

    /**
     * 应用启动完成后执行重建。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        List<DocumentDO> documents = documentMapper.selectList(Wrappers.<DocumentDO>lambdaQuery()
                .eq(DocumentDO::getParseStatus, STATUS_COMPLETED)
                .eq(DocumentDO::getIsDeleted, NOT_DELETED));

        if (documents.isEmpty()) {
            return;
        }

        log.warn("内存向量库模式：检测到 {} 个已完成的文档，开始重新向量化（重启后向量已丢失）",
                documents.size());

        for (DocumentDO document : documents) {
            // 先改状态再投递：即使重建中途失败，前端也能通过状态看出异常，
            // 不会出现"显示完成但检索不到"的静默不一致
            document.setParseStatus(STATUS_PENDING);
            document.setChunkCount(0);
            document.setErrorMsg(null);
            documentMapper.updateById(document);
        }

        // 提交到解析线程池串行执行，不阻塞应用启动
        for (DocumentDO document : documents) {
            Long docId = document.getId();
            docExecutor.execute(() -> {
                try {
                    documentProcessService.process(docId);
                } catch (Exception ex) {
                    log.error("重建向量失败, docId={}", docId, ex);
                }
            });
        }
    }
}
