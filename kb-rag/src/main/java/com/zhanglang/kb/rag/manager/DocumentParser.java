package com.zhanglang.kb.rag.manager;

import com.zhanglang.kb.common.exception.BizException;
import com.zhanglang.kb.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.regex.Pattern;

/**
 * 文档解析器。
 * <p>
 * 基于 Apache Tika 统一处理 PDF / DOCX / DOC / TXT / Markdown。
 * Tika 的价值在于抹平格式差异：业务代码只面对「字节数组 → 纯文本」这一个接口，
 * 不需要为每种格式引一个解析库，也不需要判断该用哪个。
 * <p>
 * <b>已知边界</b>：扫描件（图片型 PDF）解析出来是空文本，Tika 不做 OCR。
 * 这类文件应当在上传时就提示用户，而不是等解析完发现 chunk 数为 0 再报错。
 *
 * @author zhanglang
 * @since 1.0.0
 * @date 2026-10-20
 */
@Slf4j
@Component
public class DocumentParser {

    /** 连续 3 个及以上换行压缩为 2 个，保留段落感同时去掉大量空白 */
    private static final Pattern MULTI_NEWLINE = Pattern.compile("\\n{3,}");

    /** 行内连续空白（空格、制表符）压缩为单个空格 */
    private static final Pattern MULTI_BLANK = Pattern.compile("[ \\t\\u00A0]{2,}");

    /** 不可见控制字符，解析 PDF 时常出现，会污染向量化结果 */
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\n\\r\\t]]");

    /**
     * 解析文档为纯文本。
     *
     * @param content  文件字节内容
     * @param fileName 原始文件名，仅用于日志定位
     * @return 解析结果，包含清洗后的正文与页数
     * @throws BizException 解析失败或正文为空时抛出
     */
    public ParseResult parse(byte[] content, String fileName) {
        Metadata metadata = new Metadata();
        // writeLimit 传 -1 表示不限制长度。
        // BodyContentHandler 默认上限是 100000 字符，超出部分会被静默丢弃，
        // 这是 Tika 最容易被忽略的坑：长文档只会入库前 10 万字，且没有任何报错。
        BodyContentHandler handler = new BodyContentHandler(-1);

        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
            // AutoDetectParser 内部持有解析器注册表，未声明线程安全，故每次解析新建实例
            new AutoDetectParser().parse(input, handler, metadata, new ParseContext());
        } catch (Exception ex) {
            log.error("文档解析失败, fileName={}, size={}", fileName, content.length, ex);
            throw new BizException(ResultCode.DOC_PARSE_FAILED, "文档解析失败，请确认文件未加密且格式正确");
        }

        String rawText = handler.toString();
        String text = clean(rawText);
        if (text.isBlank()) {
            // 空文本通常意味着扫描件或纯图片 PDF，这类文件无法向量化，必须明确告知
            throw new BizException(ResultCode.DOC_PARSE_FAILED,
                    "未提取到文本内容，可能是扫描件或图片型 PDF，请上传含文字层的文件");
        }

        int pageCount = resolvePageCount(metadata, text);
        log.info("文档解析完成, fileName={}, 字符数={}, 页数={}", fileName, text.length(), pageCount);
        return new ParseResult(text, pageCount);
    }

    /**
     * 文本清洗。
     * <p>
     * 清洗目标不是"变好看"，而是提高检索质量：
     * PDF 解析出的页眉页脚、乱码控制符、连字符断行都会稀释语义向量，
     * 让原本该命中的片段排在后面。
     *
     * @param raw 原始文本
     * @return 清洗后文本
     */
    private String clean(String raw) {
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        text = CONTROL_CHARS.matcher(text).replaceAll("");
        text = MULTI_BLANK.matcher(text).replaceAll(" ");
        // 去掉行尾多余空格，避免把空格计入 token
        text = text.replaceAll("[ ]+\\n", "\n");
        text = MULTI_NEWLINE.matcher(text).replaceAll("\n\n");
        return text.strip();
    }

    /**
     * 从元数据中读取页数，读不到时按字符数估算。
     * <p>
     * 不同格式的页数字段名不一致：PDF 常用 meta:page-count，
     * Office 系列用 xmpTPg:NPages。都取不到时按每页 800 字符粗略估算，
     * 该值仅用于展示与引用定位，不需要精确。
     */
    private int resolvePageCount(Metadata metadata, String text) {
        String[] candidateKeys = {"meta:page-count", "xmpTPg:NPages", "Page-Count"};
        for (String key : candidateKeys) {
            String value = metadata.get(key);
            if (value != null && value.matches("\\d+")) {
                return Integer.parseInt(value);
            }
        }
        return Math.max(1, text.length() / 800);
    }

    /**
     * 解析结果。
     *
     * @param text      清洗后的正文
     * @param pageCount 页数（估算值，仅用于展示与引用定位）
     */
    public record ParseResult(String text, int pageCount) {
    }
}
