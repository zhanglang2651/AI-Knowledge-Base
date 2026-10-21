# AI 知识库问答平台（Java 实现）

基于 **Spring Boot 4.1 + Spring AI 2.0 + PostgreSQL/pgvector** 的 RAG 知识库问答平台后端。
原方案（FastAPI + LangChain）已整体迁移至 Java 技术栈，功能语义完全等价。

---

## 一、技术栈

| 层 | 选型 | 版本 |
|---|---|---|
| 运行时 | JDK | 17（Temurin） |
| 框架 | Spring Boot | 4.1.1 |
| AI | Spring AI | 2.0.1 |
| ORM | MyBatis-Plus | 3.5.17（**spring-boot4 专用 starter**） |
| 数据库 | PostgreSQL + pgvector | PG 16 / vector 0.8 |
| 缓存与队列 | Redis | 7（Stream 作异步队列） |
| 文档解析 | Apache Tika | 2.9.2 |
| 认证 | JWT（jjwt）+ BCrypt | 0.12.6 |
| 迁移 | Flyway | Boot 管理版本 |
| 前端 | React + TypeScript + Ant Design | React 18.3 / AntD 5.29 / Vite 5.4 |

> ⚠️ **两个版本陷阱**
> 1. MyBatis-Plus 必须用 `mybatis-plus-spring-boot4-starter`，用 `mybatis-plus-boot-starter` 会因 mybatis-spring 版本不匹配导致启动失败。
> 2. Flyway 10+ 操作 PG 必须额外引 `flyway-database-postgresql`，否则报 `Unsupported Database: PostgreSQL`。

---

## 二、目录结构

```
ai-kb-platform/
├── pom.xml                      父 POM，统一版本
├── docker-compose.yml           PG + Redis + 应用
├── Dockerfile
├── deploy/nginx.conf            生产反代示例（含 SSE 关闭缓冲的关键配置）
├── kb-common/                   通用层：响应体、错误码、全局异常、JWT、用户上下文
│   └── result / exception / util / context
├── kb-rag/                      核心层：知识库、文档、RAG 问答
│   ├── controller/              5 个 REST 接口（含后台管理）
│   ├── service/                 接口 + 实现
│   ├── manager/                 Tika 解析、分块、向量化、文件存储
│   ├── retriever/               混合检索：关键词召回 + RRF 融合
│   ├── queue/                   Redis Stream 生产者/消费者
│   ├── entity / mapper / dto
│   └── config/                  拦截器、线程池、MyBatis、密码编码器、ChatClient
├── kb-admin/                    启动层
│   ├── AiKbApplication.java
│   └── resources/
│       ├── application.yml
│       ├── application-h2.yml   免 Docker 降级 profile
│       └── db/migration/        Flyway：V1 业务表、V2 分块正文表
└── kb-web/                      前端（React + TS + AntD）
    ├── vite.config.ts           /api 代理到 8080，前端无需 CORS
    └── src/
        ├── api/                 请求封装 + SSE 流式解析
        ├── context/             登录态、主题
        ├── components/          引用列表、趋势图、状态标签
        ├── layouts/ pages/      布局与 5 个页面
        └── styles/global.css    主题变量与气泡/登录页样式
```

依赖方向严格单向：`kb-admin → kb-rag → kb-common`。

---

## 三、快速开始

### 3.1 环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 17+ | 本机已装 Temurin 17.0.20 |
| Maven | 3.9+ | 若未装，可用项目根目录上一级的便携版：`..\.tools\apache-maven-3.9.9\bin\mvn.cmd` |
| Docker Desktop | 最新 | **必需**，用于跑 PostgreSQL + pgvector 与 Redis |
| 大模型 API Key | — | 任意 OpenAI 兼容服务（OpenAI / DeepSeek / 通义 / 豆包 / 硅基流动） |

### 3.2 启动依赖服务

> ⚠️ **先进入项目目录。** 本项目路径含中文（`Desktop\项目\`），
> 在 CMD 里必须整体加引号，并用 `/d` 确保盘符也能切换。
> 直接在用户目录执行 `cd ai-kb-platform` 会报"系统找不到指定的路径"。

```cmd
cd /d "C:\Users\张浪\Desktop\项目\ai-kb-platform"
docker compose up -d postgres redis
```

确认就绪：

```cmd
docker compose ps          :: 两个服务都应为 healthy
```

> 不想跑完整 compose 也可以只起这两个服务，应用在宿主机用 Maven 跑，调试更方便。

**如果提示 `docker 不是内部或外部命令`**：说明还没装 Docker Desktop。
本项目强依赖 PostgreSQL + pgvector 与 Redis，没有 Docker 就需要手动安装这两套服务，
Windows 上装 pgvector 需要自行编译扩展，成本远高于装 Docker，因此建议先装 Docker Desktop。

### 3.3 配置模型

三选一：

**方式 A：环境变量（推荐）**

```bash
# Windows PowerShell
$env:OPENAI_API_KEY="sk-xxxx"
$env:OPENAI_BASE_URL="https://api.deepseek.com"     # 换供应商只改这个
$env:CHAT_MODEL="deepseek-chat"

# 注意：DeepSeek 不提供 embedding，需另配 embedding 接口
```

**方式 B：直接改 `kb-admin/src/main/resources/application.yml`**（仅本地调试，不要提交）

**方式 C：启动参数**

```bash
java -jar kb-admin.jar --spring.ai.openai.api-key=sk-xxx
```

### 3.4 启动应用

在项目根目录 `ai-kb-platform` 下执行（Maven 未加入 PATH，用全路径调用便携版）：

```cmd
"C:\Users\张浪\Desktop\项目\.tools\apache-maven-3.9.9\bin\mvn.cmd" clean package -DskipTests

java -jar kb-admin\target\kb-admin-1.0.0.jar
```

如果只是想快速看启动日志，跳过打包直接跑（首次会自动编译）：

```cmd
"C:\Users\张浪\Desktop\项目\.tools\apache-maven-3.9.9\bin\mvn.cmd" -pl kb-admin -am spring-boot:run
```

启动成功后：

- 健康检查：`http://localhost:8080/actuator/health`
- Flyway 会自动执行 `V1__init_schema.sql` 建表
- Spring AI 会自动创建 `vector_store` 表与 HNSW 索引

### 3.5 免 Docker 启动（h2 模式，最快看到效果）

如果本机没有 Docker、拉不了镜像，用内置的 h2 profile 可以直接跑通全部业务：
**不需要 PostgreSQL、不需要 pgvector、不需要 Redis**。

```cmd
set CHAT_API_KEY=你的对话模型key
set EMBEDDING_API_KEY=你的向量模型key

java -jar kb-admin\target\kb-admin-1.0.0.jar ^
  --spring.profiles.active=h2 ^
  --server.port=8080
```

三处降级（业务代码零改动）：

| 组件 | prod | h2 |
|---|---|---|
| 数据库 | PostgreSQL | H2 文件库（`data/h2/`，重启不丢） |
| 向量库 | pgvector | SimpleVectorStore（JVM 内存，重启后自动重建） |
| 解析队列 | Redis Stream | 本地线程池 |

**已验证可用的双供应商配置**（生成与向量化分离）：

| 用途 | 供应商 | base-url | model |
|---|---|---|---|
| 对话 | agnes | `https://apihub.agnes-ai.com/v1` | `agnes-3.0-flash` |
| 向量 | 硅基流动 | `https://api.siliconflow.cn/v1` | `BAAI/bge-m3`（1024 维） |

> ⚠️ **base-url 必须带 `/v1`**。Spring AI 2.0 底层是官方 `openai-java` SDK，
> 它会直接拼 `{base-url}/embeddings`，少写 `/v1` 会得到 404。
> 这是本项目实测踩过的坑，报错信息只有 `404: Unknown`，看不出是路径问题。

> ⚠️ **h2 模式下不要配 `SERVER_PORT` 环境变量**。某些 IDE / 工具会注入这个变量，
> Spring Boot 的环境变量优先级高于 yml，会覆盖 `server.port` 导致端口冲突。
> 用命令行参数 `--server.port=8080` 覆盖最稳，它的优先级最高。

### 3.6 启动前端

后端起来之后（8080 端口），另开一个终端：

```cmd
cd /d "C:\Users\张浪\Desktop\项目\ai-kb-platform\kb-web"

:: 首次安装依赖。国内务必走镜像，官方源会卡在几百个 tarball 上
npm install --registry=https://registry.npmmirror.com

npm run dev
```

浏览器打开 **http://127.0.0.1:5173**，用测试账号登录：

| 账号 | 密码 | 角色 |
|---|---|---|
| `zhanglang` | `test1234` | ADMIN（可进后台） |
| `normaluser` | `test1234` | USER |

> 依赖安装卡住时先检查代理：`HTTP_PROXY` 指向失效端口会让 npm 每个包都超时。
> 临时绕过：`$env:HTTP_PROXY=""; $env:HTTPS_PROXY=""; $env:NO_PROXY="*"` 再执行安装。

生产构建与部署：

```cmd
npm run build          :: 产物在 kb-web/dist
```

把 `dist` 放到 Nginx 的 root，按 `deploy/nginx.conf` 配置反代即可。
**SSE 接口必须 `proxy_buffering off`**，否则流式输出会退化成一次性刷出整段。

---

## 四、接口清单与实测命令

统一前缀 `/api/v1`，统一响应体 `{"code":"00000","message":"成功","data":...,"timestamp":...}`。

### 4.1 注册

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"zhanglang","password":"abc12345","nickname":"张浪"}'
```

### 4.2 登录（拿 Token）

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"zhanglang","password":"abc12345"}'
```

响应里的 `accessToken` 用于后续所有请求。

### 4.3 创建知识库

```bash
curl -X POST http://localhost:8080/api/v1/kb \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <TOKEN>" \
  -d '{"name":"员工手册","description":"公司制度","chunkSize":600,"topK":5,"similarityThreshold":0.5}'
```

### 4.4 上传文档

```bash
curl -X POST http://localhost:8080/api/v1/kb/1/doc \
  -H "Authorization: Bearer <TOKEN>" \
  -F "file=@./员工手册.pdf"
```

立刻返回 `docId`，解析在后台进行。

### 4.5 轮询解析进度

```bash
curl http://localhost:8080/api/v1/doc/1/status -H "Authorization: Bearer <TOKEN>"
```

`parseStatus` 依次变化：`PENDING → PARSING → EMBEDDING → COMPLETED`

### 4.6 流式问答（核心）

```bash
curl -N -X POST http://localhost:8080/api/v1/chat/stream \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <TOKEN>" \
  -d '{"kbId":1,"question":"试用期是多久？"}'
```

返回标准 SSE：

```
event:sources
data:[{"docId":1,"docName":"员工手册.pdf","pageNo":7,"chunkNo":3,"score":0.83,"content":"..."}]

event:content
data:{"delta":"根据"}

event:content
data:{"delta":"员工手册第 7 页..."}

event:done
data:{"conversationId":1,"promptTokens":1820,"completionTokens":356,"latencyMs":4210}
```

> **必须加 `-N`**（禁用 curl 缓冲），否则看不到逐字效果。

### 4.7 完整接口表

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/auth/register` | 注册 |
| POST | `/auth/login` | 登录 |
| GET | `/auth/me` | 当前用户 |
| POST | `/kb` | 创建知识库 |
| GET | `/kb?keyword=&pageNo=1&pageSize=10` | 分页列表 |
| GET | `/kb/{id}` | 详情 |
| PUT | `/kb/{id}` | 更新 |
| DELETE | `/kb/{id}` | 删除（级联清理文档与向量） |
| POST | `/kb/{kbId}/doc` | 上传文档 |
| GET | `/kb/{kbId}/doc` | 文档列表 |
| GET | `/doc/{id}/status` | 解析进度 |
| DELETE | `/doc/{id}` | 删除文档 |
| POST | `/doc/{id}/retry` | 重试失败文档 |
| POST | `/chat/stream` | **SSE 流式问答** |
| GET | `/chat/conversations?kbId=1` | 会话列表 |
| GET | `/chat/conversations/{id}/messages` | 消息列表 |
| DELETE | `/chat/conversations/{id}` | 删除会话 |
| GET | `/admin/models?modelType=` | 模型配置列表（**密钥脱敏**） |
| POST | `/admin/models` | 新增模型配置（密钥 AES 加密入库） |
| PUT | `/admin/models/{id}` | 更新配置（apiKey 留空=不改） |
| DELETE | `/admin/models/{id}` | 删除配置 |
| POST | `/admin/models/{id}/test` | **连通性测试**（真实发起一次请求） |
| GET | `/admin/stat/overview` | 调用总览（今日/累计） |
| GET | `/admin/stat/trend?days=7` | 调用趋势（按天） |
| POST | `/admin/eval/retrieval?kbId=1` | 检索质量评估（不传用例则用内置用例） |

> `/admin/**` 全部需要 **ADMIN** 角色。首个注册用户自动成为管理员
> （否则系统里不存在 ADMIN，后台没有任何入口可进入）。
> 普通用户访问会返回 **403**，这个校验在 `JwtAuthInterceptor` 里完成，不依赖前端隐藏菜单。

---

## 五、关键设计决策

### 5.1 为什么用 Redis Stream 而不是 MQ

已有 Redis，零额外运维；消费者组支持多实例消费与失败重投；消息持久化，重启不丢任务。对比线程池的关键优势是**任务不随进程退出而消失**。

### 5.2 为什么文档必须异步解析

100 页 PDF 的解析 + 向量化需要 30~120 秒，同步接口必然被网关断开。上传接口只做"校验 + 落盘 + 落库 + 投递"，200ms 内返回。

### 5.3 引用溯源靠入库时写 metadata

`docId / docName / pageNo / chunkNo` 在写入向量库的那一刻就塞进 metadata，检索后直接读取，不回查数据库。**这一步漏了无法补救，只能全量重建向量。**

### 5.4 删除必须"先删向量，再删记录"

反过来一旦中途失败，记录没了，向量就再也定位不到，成为永久垃圾数据且仍会被检索命中。`EmbeddingPipeline.deleteByDocId()` 用 JDBC 直接按 metadata 条件删除。

### 5.5 向量维度是硬约束

`vector(n)` 建表时固定。换 embedding 模型必须同步改三处：
1. `application.yml` 的 `spring.ai.vectorstore.pgvector.dimensions`
2. `application.yml` 的 `kb.embedding.dimension`
3. **重建 `vector_store` 表**（`DROP TABLE vector_store;` 后重启）

不一致会在写入时报维度错误。

### 5.6 异步线程读不到 UserContext

`UserContext` 基于 ThreadLocal。SSE 问答在独立线程池执行，用户 ID 必须在主线程取出后传参，否则拿到 null 直接空指针。

### 5.7 为什么不用 EventSource

浏览器的 `EventSource` 只能发 GET、且无法设置自定义请求头，而本项目鉴权依赖 `Authorization: Bearer`。
因此前端用 `fetch` + `ReadableStream.getReader()` 手动解析 SSE 文本流（`src/api/chat.ts`），
协议格式仍是标准 SSE，代价是必须自己处理：**分帧（`\n\n` 切分）、跨网络块的粘包、CRLF 归一化、心跳注释行**。
其中 CRLF 归一化最容易漏 —— Windows 下某些网关会把 `\n` 改写成 `\r\n`，不处理会表现为「一直转圈不出内容」。

### 5.8 引用快照必须能被反序列化

`SourceVO` 同时承担两个角色：SSE 推送对象（只需序列化）和历史消息快照（需要反序列化）。
只加 `@Builder` 会让 Jackson 找不到无参构造，反序列化抛异常后被 catch 吞掉，**外部表现为"历史引用列表为空"，而 SSE 实时推送却完全正常** —— 这类 bug 只有真正点开历史会话才能发现。

### 5.9 趋势图手写 SVG 而不用图表库

只需柱状图一种形态，echarts/recharts 会让产物体积增加数百 KB；SVG 用 CSS 变量取色还能天然跟随明暗主题。
对一张单一形态的图表来说，自己画 150 行比引入依赖更划算。

### 5.10 混合检索：向量 + 关键词，用 RRF 融合

**要解决的问题**：向量检索对"语义相近但字面不同"很擅长，对"字面完全一致却语义稀薄"却很差。
搜 `ZK-8823` 这种错误码时，`ZK-8823` 与 `KV-1107` 在 embedding 空间里几乎同质，模型分不出主次。

**四个组成部分**：

| 组件 | 位置 | 职责 |
|---|---|---|
| `kb_chunk` 表 | `V2__init_chunk_table.sql` | 分块正文副本，关键词分支的唯一数据来源 |
| `KeywordRetriever` | `rag/retriever/` | 中文二元切分 → LIKE 粗筛 → 加权打分 |
| `HybridRetriever` | `rag/retriever/` | 双路召回 + RRF 融合 + 准入门槛 |
| `SourceVO.via` | `dto/` | 告诉前端这条是「语义」还是「关键词」命中 |

**为什么必须落一份正文**：向量表只存向量，Spring AI 不暴露按条件查文本的接口。
不另存正文，关键词分支根本无从实现——这是混合检索的硬性前提，也是本项目为此新增一张表的唯一原因。

**为什么用 RRF 而不是加权求和**：向量相似度（0~1）与关键词得分（词频归一化）量纲完全不同，
强行归一化就要维护一组换模型即失效的系数。RRF 只用排名不用分数，天然免疫量纲问题：

```
score(d) = Σ_i w_i / (k + rank_i(d))     k=60, w = 0.6(向量) / 0.4(关键词)
```

**中文没有空格分词怎么办**：采用**二元切分（bigram）**——"试用期多长" → 试用期/期多/多长。
无需词典与额外依赖，代价是召回偏宽，因此对「只有关键词命中、向量没命中」的片段设了
`min-keyword-score=0.25` 准入门槛，否则宽召回会把擦边片段塞进上下文干扰模型。

**可观测性**：`HybridRetriever` 每次都会打印 `vector=? keyword=? fused=?`，
界面上每条引用带「语义 / 关键词 / 双路命中」标签——混合检索最怕的就是"改了参数但看不出变化"。

**回退开关**：`kb.retrieval.hybrid-enabled=false` 即退化为纯向量，便于线上出问题时的快速回滚。

### 5.11 检索质量评估：让调参有数据，而不是靠手感

没有评估时，`min-keyword-score` 填 0.25、向量权重填 0.6，全凭"感觉差不多"。
一旦答辩被问"你怎么知道 0.25 是合适的"，就只能答"试了几次"——这在工程上等于没答。

于是加了一个**只跑检索、不调模型**的评估入口（因此零 token 成本，可以反复跑），
同一批用例分别用 HYBRID / VECTOR / KEYWORD 三种策略执行，输出三项指标：

| 指标 | 含义 | 为什么必须有 |
|---|---|---|
| Recall@K | 该找到的找没找到 | 基础指标 |
| MRR | 找到的排得靠不靠前 | 命中率 100% 但全排最后，等于没优化 |
| 负样本拒绝率 | 不该召回的有没有被召回 | **知识库幻觉的主要来源**，只看正样本会得到虚高的分数 |

**它立刻发现了真问题**。内置 4 条用例（3 正 1 负）在阈值 0.40 时的结果：

```
HYBRID  recall=1.0  mrr=1.0  negPass=0.0  acc=0.75
VECTOR  recall=1.0  mrr=1.0  negPass=0.0  acc=0.75
KEYWORD recall=1.0  mrr=1.0  negPass=1.0  acc=1.0
```

负样本「公司股票期权怎么行权？」被向量分支召回了 3 条（相似度 0.44 > 阈值 0.40）——
检索器在**硬凑答案**。把该知识库阈值调到 0.55 后复测，三种策略全部满分：

```
HYBRID / VECTOR / KEYWORD  recall=1.0  mrr=1.0  negPass=1.0  acc=1.0
```

这就是"用数据驱动调参"的完整闭环：**先建评估 → 跑出问题 → 调一个参数 → 复测验证**。

界面在「后台管理 → 检索评估」，用例可直接在页面上编辑，格式为
`问题 | 期望关键词 | 期望文档名 | 是否负样本`。

### 5.12 两个"看着没问题、实际会炸"的坑

**① `selectCount` 不能带排序**

MyBatis-Plus 的 `selectCount(wrapper)` 会把 wrapper 里的 `ORDER BY` 原样拼进 COUNT 查询：

```sql
SELECT COUNT(*) AS total FROM kb_knowledge_base WHERE ... ORDER BY gmt_create DESC
```

排序列既不在 `GROUP BY` 也不在聚合函数里，**H2 2.4 与 PostgreSQL 都会直接报错**。
现象很有迷惑性：创建知识库成功（写操作正常），紧接着刷新列表却 500。
解法是统计单独建一个不带排序的条件对象。

**② 文档数不能靠事件累加**

早期版本在解析成功时 `doc_count + 1`，而失败重试、每次重启的内存向量重建都会重复累加，
**2 个文档被统计成 11 个**。这类漂移不报错、不崩溃，只会慢慢变得离谱，最难发现。
现已改为列表/详情实时 `COUNT ... GROUP BY kb_id`（一次查询覆盖整页，避免 N+1）。

**③ 删除文档必须能"精确地"删向量**

Spring AI 默认给每条向量生成随机 UUID——后果是删除文档时**根本无法反查该删哪些向量**。
本项目 h2 模式下曾真实出现：用户删掉了某文档，问答却仍然引用它，且引用指向一个已不存在的文件。
修复分两半：

```java
// 入库时指定确定性 ID，而不是框架默认的随机 UUID
Document.builder().id(docId + ":" + chunkNo).text(...).metadata(...).build();

// 删除时先从 kb_chunk 反查 chunkNo，拼出 ID 列表精确删除
vectorStore.delete(ids);                       // 内存向量库与 pgvector 通吃
jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'docId' = ?", ...);  // 存量数据兜底
```

确定性 ID 还带来一个副产品：**重试解析天然幂等**（同 ID 覆盖旧向量，不会产生双份）。

---

## 六、已知限制（诚实清单）

| 限制 | 说明 | 影响 |
|---|---|---|
| **Token 数为估算值** | 用字符数/2 估算，非模型返回的精确 usage | 成本看板仅供演示，不能对账 |
| 评估用例需人工维护 | 内置用例只覆盖演示文档 | 换数据后要自己补用例，否则评估失真 |
| 评估只看检索不看生成 | 不调用模型、不评答案质量 | 检索准 ≠ 答得好，生成质量目前靠人工看 |
| 扫描件 PDF 无法解析 | Tika 不做 OCR，会明确报"未提取到文本" | 需接入 OCR 才能支持 |
| 关键词召回走 LIKE | `%前缀%` 用不上 B 树索引 | 分块上万后需换 `pg_trgm` GIN 索引，接口不变 |
| 中文二元切分偏宽 | 无词典分词，召回略粗 | 已用准入阈值 + RRF 兜住，实测可用 |
| 测试只覆盖纯逻辑层 | 36 个用例锁住分块/切分/融合/JWT/AES；Controller 与 SQL 路径仍靠接口实测 | 修改 Mapper SQL 或接口入参时需手动回归 |
| h2 模式向量在内存 | 重启会丢，靠启动自愈重新向量化 | 生产用 pgvector 即可，无此问题 |
| 模型配置未接入推理链路 | 后台表用于管理与核算，改配置需改 yml 并重启 | 尚未做运行时动态切换 |
| 无 Maven Wrapper | 依赖本机 Maven 或便携版 | 换机器需重新准备 |

---

## 七、下一步

1. **生成质量评估**：在检索评估之外再加一档"答案是否忠于原文"的人工/模型评分
2. **精确 Token 计量**：改用 `stream().chatResponse()` 读 `metadata.usage`，让成本看板可对账
3. **集成测试**：Testcontainers 起 PG + pgvector，把 Mapper SQL 与 Flyway 迁移纳入回归
4. **文档内容去重**：同一文件重复上传时按 md5 拦截，避免重复消耗 embedding 额度

---

## 八、合规说明

代码遵循《阿里巴巴 Java 开发手册》（黄山版 1.7.1）强制项：

- 类/方法均带 Javadoc（`@author` / `@since` / `@date`）
- 错误码采用 `A/B/C + 4 位数字`，成功为 `00000`
- 线程池显式指定核心数/队列/拒绝策略，禁用 `Executors.newXxx()`
- 禁止 `SELECT *`、禁止 `${}` 拼接 SQL、禁止空 catch、禁止 `e.printStackTrace()`
- 密码 BCrypt 加盐哈希，禁止明文存储
- 文件上传走后缀白名单 + 大小限制 + 路径穿越校验
- 逻辑删除统一 `is_deleted`，三必备字段 `id / gmt_create / gmt_modified`

前端（kb-web）约定：

- 所有接口调用集中在 `src/api/`，页面不直接写 fetch
- 组件卸载必须清理副作用：轮询 `clearTimeout`、流式请求 `abort()`
- 主题统一走 ConfigProvider + CSS 变量，禁止硬编码颜色
- 不引入 axios：需要 `ReadableStream` 流式读取，统一用 fetch 只维护一套请求/错误处理

建议本地安装 IDEA 插件 **Alibaba Java Coding Guidelines (P3C)** 做提交前扫描。

---

## 九、单元测试

```cmd
:: 项目根目录执行（便携版 Maven）
.tools\apache-maven-3.9.9\bin\mvn.cmd -s .tools\settings.xml test
```

36 个用例、约 10 秒跑完，全部为**纯单元测试**：不启动 Spring 上下文、不连数据库、不调模型，
被测类的依赖（Mapper、VectorStore）全部用 Mockito 桩替换，因此可以高频反复运行。

| 测试类 | 用例数 | 锁住的行为 |
|---|---|---|
| `ChunkSplitterTest` | 7 | 块号连续、块长上限、标点归属前块、overlap 生效、非法参数不死循环、偏移与原文一致 |
| `KeywordRetrieverTest` | 10 | ASCII/中文切分规则、去重、短词丢弃、得分 0~1、全命中排在部分命中前、Mapper 异常降级 |
| `HybridRetrieverTest` | 7 | **RRF 双路命中排第一**、低分关键词丢弃、hybrid 开关退化、零权重分支剔除、topK 截断、向量异常降级 |
| `JwtUtilTest` | 6 | 签发解析还原、过期/篡改/换钥/垃圾输入返回 null、短密钥快速失败 |
| `AesUtilTest` | 6 | 加解密还原、随机 IV、**篡改检测（GCM 认证）**、换口令失败、脱敏规则 |

这些测试不是"为覆盖率而写"：RRF 排序规则、GCM 篡改检测、分块边界，
都是本项目答辩护身之处，也是最容易在重构时被悄悄改坏的地方。
