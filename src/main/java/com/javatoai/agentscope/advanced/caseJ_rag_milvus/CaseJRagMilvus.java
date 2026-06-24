package com.javatoai.agentscope.advanced.caseJ_rag_milvus;

import com.javatoai.agentscope.support.EnvSupport;
import com.javatoai.agentscope.support.ModelSupport;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 案例 J —— RAG（检索增强生成）+ Milvus 向量数据库教学。
 *
 * <p>完整演示 RAG 管线的五个阶段：
 * <ol>
 *   <li><b>文档分块</b> — 把长文档切成小块</li>
 *   <li><b>向量化</b> — 文本块 → 向量（本案例用模拟向量）</li>
 *   <li><b>存储</b> — 向量存入数据库（默认内存，可切换到 Milvus）</li>
 *   <li><b>检索</b> — 用户问题 → 向量 → 相似度搜索 → Top-K 相关文档</li>
 *   <li><b>增强生成</b> — 检索结果注入 Agent 提示词 → Agent 回答</li>
 * </ol>
 *
 * <p>本案例默认使用内存向量存储（开箱即用），同时详细注释了如何切换到
 * <a href="https://milvus.io">Milvus</a> 生产级向量数据库。
 *
 * <p>核心概念:
 * <ul>
 *   <li>文档分块策略（固定大小 + 滑动窗口）</li>
 *   <li>向量相似度检索（余弦相似度）</li>
 *   <li>Embedding 模型的选择（文本 → 浮点向量）</li>
 *   <li>Milvus Collection / Index / Search 的使用方式</li>
 *   <li>RAG 结果如何注入 Agent 上下文</li>
 * </ul>
 *
 * <p>运行命令:
 * <pre>{@code
 * mvn -q exec:java -Dexec.mainClass=com.javatoai.agentscope.advanced.caseJ_rag_milvus.CaseJRagMilvus
 * }</pre>
 */
public final class CaseJRagMilvus {

    private CaseJRagMilvus() {}

    public static void main(String[] args) {
        EnvSupport.requireApiKey();
        EnvSupport.printBanner("Case J · RAG + Milvus 向量数据库",
                "文档分块 → 向量化 → 存储 → 检索 → 增强生成");

        // ══════════════════════════════════════════════════════════
        // 第一步：准备知识库文档
        // ══════════════════════════════════════════════════════════
        System.out.println("━".repeat(55));
        System.out.println("  第一步：准备知识库文档");
        System.out.println("━".repeat(55));

        List<String> rawDocs = List.of(
                """
                AgentScope Java 2.0 是一个企业级多智能体框架，于 2025 年发布。
                它引入了 HarnessAgent 作为生产级入口，内置 Workspace、会话持久化、
                Compaction（对话压缩）和 Middleware（中间件）体系。
                核心设计思想：Agent 实例无状态，所有可变状态由 AgentStateStore 管理。
                """,
                """
                MiddlewareBase 是 AgentScope 2.0 的中间件抽象基类，替代了 1.x 的 Hook 机制。
                它提供四个插桩点：onAgent（洋葱，包裹整轮调用）、onSystemPrompt（变换，串行加工提示词）、
                onModelCall（洋葱，包裹 LLM HTTP 请求）、onActing（洋葱，包裹工具执行）。
                洋葱模式让你在前后插入逻辑，变换模式让你串行修改值。
                """,
                """
                RuntimeContext 是每次 agent.call() 级别的元数据容器，不持久化。
                它包含 userId、sessionId、自由键值对、以及类型化 POJO。
                类型化 POJO 会自动注入到 @Tool 方法中声明了同类型参数的参数位置。
                AgentState 则是框架内部管理的持久化对象，按 (userId, sessionId) 存储对话历史。
                """,
                """
                AgentScope 2.0 的流式机制基于 Project Reactor 的 Flux<AgentEvent>。
                旧版 Event 类（6 种粗粒度事件）已废弃，新版 AgentEventType 有 27 种细粒度事件。
                streamEvents() 方法替代了旧的 stream() 方法，MiddlewareBase 替代了 StreamingHook。
                """,
                """
                智谱 AI (Zhipu AI) 提供了完整的模型系列：GLM-5 用于对话，
                embedding-2 用于文本向量化（1024 维），charglm-3 用于角色扮演。
                AgentScope 通过 OpenAIChatModel + OpenAI 兼容端点对接智谱 API，
                模型 ID 格式为 zai:model-name，由 ModelRegistry 的工厂模式解析。
                """,
                """
                Milvus 是一个开源的向量数据库，专为 AI 应用设计。
                支持十亿级向量检索，提供 IVFFlat、HNSW 等多种索引算法。
                部署方式：Docker 单机（docker run milvusdb/milvus）、
                Docker Compose 集群、或 Milvus Cloud 托管服务。
                Java SDK：io.milvus:milvus-sdk-java，对应 Maven 依赖。
                """
        );

        // ══════════════════════════════════════════════════════════
        // 第二步：文档分块（Chunking）
        // ══════════════════════════════════════════════════════════
        System.out.println("━".repeat(55));
        System.out.println("  第二步：文档分块 (Chunking)");
        System.out.println("━".repeat(55));

        DocumentChunker chunker = new DocumentChunker(200, 30);
        List<DocumentChunk> chunks = chunker.chunkAll(rawDocs);
        System.out.println("  原始文档: " + rawDocs.size() + " 篇");
        System.out.println("  分块结果: " + chunks.size() + " 个块");
        chunks.forEach(c -> System.out.printf("    [chunk-%d] %s...%n",
                c.id(), c.text().substring(0, Math.min(60, c.text().length()))));
        System.out.println();

        // ══════════════════════════════════════════════════════════
        // 第三步：向量化 + 存储
        // ══════════════════════════════════════════════════════════
        System.out.println("━".repeat(55));
        System.out.println("  第三步：向量化 → 存储 (Embedding + Store)");
        System.out.println("━".repeat(55));

        // ── 当前方案：内存向量存储（可立即运行）──
        VectorStore store = new InMemoryVectorStore();
        System.out.println("  [存储后端] 内存向量存储 (InMemoryVectorStore)");

        // ── 生产方案：Milvus（切换方法见文件末尾）──
        // VectorStore store = new MilvusVectorStore(
        //     "localhost", 19530, "agentscope_knowledge");

        // 模拟向量化（实际应调用 Embedding API）
        EmbeddingService embedder = new SimulatedEmbeddingService();
        for (DocumentChunk chunk : chunks) {
            float[] vector = embedder.embed(chunk.text());
            store.insert(chunk.id(), vector, chunk.text());
        }
        System.out.println("  [向量化] 完成 " + chunks.size() + " 条向量");
        System.out.println("  [向量维度] " + embedder.dimension() + " 维");
        System.out.println();

        // ══════════════════════════════════════════════════════════
        // 第四步：检索（Retrieval）
        // ══════════════════════════════════════════════════════════
        System.out.println("━".repeat(55));
        System.out.println("  第四步：检索 (Retrieval)");
        System.out.println("━".repeat(55));

        String userQuestion = "Middleware 的洋葱模式和变换模式有什么区别？";

        // 把用户问题也向量化，然后检索
        float[] queryVector = embedder.embed(userQuestion);
        List<SearchResult> results = store.search(queryVector, 3);  // Top-3
        System.out.println("  用户问题: " + userQuestion);
        System.out.println("  检索结果 (Top-3):");
        results.forEach(r -> System.out.printf("    [得分=%.3f] %s...%n",
                r.score(), r.text().substring(0, Math.min(70, r.text().length()))));
        System.out.println();

        // ══════════════════════════════════════════════════════════
        // 第五步：增强生成（Augmented Generation）
        // ══════════════════════════════════════════════════════════
        System.out.println("━".repeat(55));
        System.out.println("  第五步：增强生成 (Augmented Generation)");
        System.out.println("━".repeat(55));

        // 把检索到的文档注入到提示词中
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            context.append("【参考资料 %d】%s%n%n".formatted(i + 1, results.get(i).text()));
        }

        String augmentedPrompt = """
                你是一个技术助手。请严格根据下面提供的参考资料回答用户问题。
                如果参考资料中没有相关信息，请明确说明"参考资料中未包含此信息"。

                %s
                用户问题: %s
                """.formatted(context.toString(), userQuestion);

        System.out.println("  [增强后的提示词]");
        System.out.println("  ─────────────────────────────────────────");
        System.out.println("  " + augmentedPrompt.replace("\n", "\n  "));
        System.out.println();

        // 用增强后的提示词调用 Agent
        ReActAgent agent = ReActAgent.builder()
                .name("rag-assistant")
                .sysPrompt(augmentedPrompt)
                .model(ModelSupport.defaultModel())
                .toolkit(new Toolkit())
                .build();

        Msg reply = agent.call(
                List.of(new UserMessage(userQuestion)),
                RuntimeContext.empty()).block();

        System.out.println("  [Agent 回答（基于检索文档）]");
        System.out.println("  " + reply.getTextContent());
        System.out.println();

        // ══════════════════════════════════════════════════════════
        // 对比：不带 RAG 的 Agent 回答
        // ══════════════════════════════════════════════════════════
        System.out.println("━".repeat(55));
        System.out.println("  对比实验：无 RAG 的 Agent 回答");
        System.out.println("━".repeat(55));

        ReActAgent agentNoRag = ReActAgent.builder()
                .name("no-rag-assistant")
                .sysPrompt("你是一个技术助手。请用中文回答用户问题。")
                .model(ModelSupport.defaultModel())
                .toolkit(new Toolkit())
                .build();

        Msg replyNoRag = agentNoRag.call(
                List.of(new UserMessage(userQuestion)),
                RuntimeContext.empty()).block();

        System.out.println("  [无RAG回答] " + replyNoRag.getTextContent());
        System.out.println();

        System.out.println(">>> Case J 完成：文档分块、向量检索、增强生成、对比实验");
        System.out.println();
        System.out.println("=".repeat(55));
        System.out.println("  如何切换到 Milvus 生产级向量数据库");
        System.out.println("=".repeat(55));
        System.out.println("""
                1. 启动 Milvus (Docker):
                   docker run -d --name milvus-standalone \\
                     -p 19530:19530 -p 9091:9091 \\
                     milvusdb/milvus:latest

                2. 添加 Maven 依赖:
                   <dependency>
                     <groupId>io.milvus</groupId>
                     <artifactId>milvus-sdk-java</artifactId>
                     <version>2.4.3</version>
                   </dependency>

                3. 把 InMemoryVectorStore 替换为 MilvusVectorStore
                   (完整实现代码见文件末尾注释)
                """);
    }
}

// ════════════════════════════════════════════════════════════════
// 文档分块器
// ════════════════════════════════════════════════════════════════

/**
 * 简单的文档分块器：按固定窗口大小切分，相邻块之间有一段重叠。
 *
 * <p>分块策略是 RAG 质量的关键：
 * <ul>
 *   <li>块太小 → 语义不完整</li>
 *   <li>块太大 → 检索噪音多，超出 Embedding 模型的上下文窗口</li>
 *   <li>重叠窗口 → 避免关键信息恰好落在两个块的边界上被切断</li>
 * </ul>
 */
class DocumentChunker {

    private final int chunkSize;     // 每块最大字符数
    private final int overlapSize;   // 相邻块重叠字符数
    private int nextId = 0;

    DocumentChunker(int chunkSize, int overlapSize) {
        this.chunkSize = chunkSize;
        this.overlapSize = overlapSize;
    }

    List<DocumentChunk> chunkAll(List<String> documents) {
        List<DocumentChunk> result = new ArrayList<>();
        for (String doc : documents) {
            result.addAll(chunk(doc));
        }
        return result;
    }

    private List<DocumentChunk> chunk(String text) {
        List<DocumentChunk> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            result.add(new DocumentChunk(nextId++, text.substring(start, end)));
            if (end >= text.length()) break;
            start = end - overlapSize;
        }
        return result;
    }
}

record DocumentChunk(int id, String text) {}

// ════════════════════════════════════════════════════════════════
// 向量存储接口（抽象层）
// ════════════════════════════════════════════════════════════════

record SearchResult(int chunkId, float score, String text) {}

interface VectorStore {
    void insert(int id, float[] vector, String text);
    List<SearchResult> search(float[] queryVector, int topK);
}

// ════════════════════════════════════════════════════════════════
// 内存向量存储实现（演示用，开箱即用）
// ════════════════════════════════════════════════════════════════

class InMemoryVectorStore implements VectorStore {

    private final Map<Integer, float[]> vectors = new HashMap<>();
    private final Map<Integer, String> texts = new HashMap<>();

    @Override
    public void insert(int id, float[] vector, String text) {
        vectors.put(id, vector);
        texts.put(id, text);
    }

    @Override
    public List<SearchResult> search(float[] queryVector, int topK) {
        return vectors.entrySet().stream()
                .map(e -> {
                    int id = e.getKey();
                    float score = cosineSimilarity(queryVector, e.getValue());
                    return new SearchResult(id, score, texts.get(id));
                })
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
                .limit(topK)
                .toList();
    }

    /** 余弦相似度：值域 [-1, 1]，越大越相似 */
    private static float cosineSimilarity(float[] a, float[] b) {
        float dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB) + 1e-8));
    }
}

// ════════════════════════════════════════════════════════════════
// 向量化服务（模拟）
// ════════════════════════════════════════════════════════════════

/**
 * 模拟 Embedding 服务。实际项目应使用真实的 Embedding 模型。
 *
 * <p>以智谱 embedding-2 为例（1024 维，需 API_KEY_ZAI）：
 * <pre>{@code
 * EmbeddingModel model = ZhipuTextEmbedding.builder()
 *     .apiKey(System.getenv("API_KEY_ZAI"))
 *     .modelName("embedding-2")
 *     .dimensions(1024)
 *     .build();
 * List<float[]> vectors = model.embed(texts);
 * }</pre>
 *
 * <p>或使用 DashScope:
 * <pre>{@code
 * EmbeddingModel model = DashScopeTextEmbedding.builder()
 *     .apiKey(System.getenv("DASHSCOPE_API_KEY"))
 *     .modelName("text-embedding-v3")
 *     .dimensions(1024)
 *     .build();
 * }</pre>
 */
class EmbeddingService {
    float[] embed(String text) { return new float[0]; }
    int dimension() { return 0; }
}

class SimulatedEmbeddingService extends EmbeddingService {

    private static final int DIM = 8;  // 演示用小维度，实际通常是 768/1024/1536

    @Override
    public float[] embed(String text) {
        // 简单哈希模拟：把文本映射为固定维度的向量
        // 实际生产环境必须使用真实 Embedding 模型（见上方注释）
        float[] vec = new float[DIM];
        int hash = text.hashCode();
        for (int i = 0; i < DIM; i++) {
            vec[i] = ((hash >> i) & 1) == 1 ? 0.5f : -0.3f + 0.01f * i;
        }
        // 归一化
        float norm = 0;
        for (float v : vec) norm += v * v;
        norm = (float) Math.sqrt(norm);
        for (int i = 0; i < DIM; i++) vec[i] /= norm;
        return vec;
    }

    @Override
    public int dimension() { return DIM; }
}

// ════════════════════════════════════════════════════════════════
// Milvus 向量存储实现（生产用，仅供参考，需要 Milvus 服务 + SDK）
// ════════════════════════════════════════════════════════════════

/*
 * 以下是一个完整的 MilvusVectorStore 实现骨架。
 * 使用时:
 *   1. 启动 Milvus: docker run -d --name milvus -p 19530:19530 milvusdb/milvus
 *   2. 添加依赖: io.milvus:milvus-sdk-java:2.4.3
 *   3. 取消注释并替换 InMemoryVectorStore
 *
 * ── 完整代码 ──
 *
 * import io.milvus.client.MilvusServiceClient;
 * import io.milvus.grpc.DataType;
 * import io.milvus.grpc.SearchResult;
 * import io.milvus.param.*;
 * import io.milvus.param.collection.*;
 * import io.milvus.param.dml.*;
 * import io.milvus.param.dml.SearchParam;
 * import io.milvus.param.index.*;
 * import io.milvus.response.SearchResultsWrapper;
 * import java.util.*;
 *
 * class MilvusVectorStore implements VectorStore {
 *
 *     private final MilvusServiceClient client;
 *     private final String collectionName;
 *     private static final int DIM = 1024;  // 向量维度
 *
 *     MilvusVectorStore(String host, int port, String collectionName) {
 *         this.collectionName = collectionName;
 *         this.client = new MilvusServiceClient(
 *             ConnectParam.newBuilder()
 *                 .withHost(host)
 *                 .withPort(port)
 *                 .build()
 *         );
 *         initCollection();
 *     }
 *
 *     // ── 创建 Collection（相当于关系数据库的"表"）──
 *     private void initCollection() {
 *         // 1. 检查是否已存在
 *         boolean exists = client.hasCollection(
 *             HasCollectionParam.newBuilder()
 *                 .withCollectionName(collectionName)
 *                 .build()
 *         ).getData();
 *
 *         if (exists) return;
 *
 *         // 2. 定义字段 Schema
 *         FieldType idField = FieldType.newBuilder()
 *             .withName("chunk_id")
 *             .withDataType(DataType.Int64)
 *             .withPrimaryKey(true)
 *             .withAutoID(false)
 *             .build();
 *
 *         FieldType vectorField = FieldType.newBuilder()
 *             .withName("embedding")
 *             .withDataType(DataType.FloatVector)
 *             .withDimension(DIM)
 *             .build();
 *
 *         FieldType textField = FieldType.newBuilder()
 *             .withName("text")
 *             .withDataType(DataType.VarChar)
 *             .withMaxLength(2048)
 *             .build();
 *
 *         // 3. 创建 Collection
 *         CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
 *             .withCollectionName(collectionName)
 *             .withDescription("AgentScope RAG 知识库")
 *             .addFieldType(idField)
 *             .addFieldType(vectorField)
 *             .addFieldType(textField)
 *             .build();
 *         client.createCollection(createParam);
 *
 *         // 4. 创建索引（IVFFlat: 适合百万级数据）
 *         CreateIndexParam indexParam = CreateIndexParam.newBuilder()
 *             .withCollectionName(collectionName)
 *             .withFieldName("embedding")
 *             .withIndexType(IndexType.IVF_FLAT)
 *             .withMetricType(MetricType.COSINE)  // 余弦相似度
 *             .withExtraParam("{\"nlist\":128}")
 *             .build();
 *         client.createIndex(indexParam);
 *
 *         // 5. 加载到内存
 *         client.loadCollection(
 *             LoadCollectionParam.newBuilder()
 *                 .withCollectionName(collectionName)
 *                 .build()
 *         );
 *
 *         System.out.println("[Milvus] Collection '" + collectionName
 *             + "' 创建完成, 维度=" + DIM);
 *     }
 *
 *     // ── 插入向量 ──
 *     @Override
 *     public void insert(int id, float[] vector, String text) {
 *         List<InsertParam.Field> fields = List.of(
 *             new InsertParam.Field("chunk_id",
 *                 Collections.singletonList((long) id)),
 *             new InsertParam.Field("embedding",
 *                 Collections.singletonList(Arrays.asList(
 *                     // float[] → List<Float>
 *                     (Object) floatArrayToList(vector)))),
 *             new InsertParam.Field("text",
 *                 Collections.singletonList(text))
 *         );
 *
 *         InsertParam insertParam = InsertParam.newBuilder()
 *             .withCollectionName(collectionName)
 *             .withFields(fields)
 *             .build();
 *         client.insert(insertParam);
 *     }
 *
 *     // ── 向量检索 ──
 *     @Override
 *     public List<SearchResult> search(float[] queryVector, int topK) {
 *         SearchParam searchParam = SearchParam.newBuilder()
 *             .withCollectionName(collectionName)
 *             .withMetricType(MetricType.COSINE)
 *             .withOutFields(List.of("chunk_id", "text"))
 *             .withTopK(topK)
 *             .withFloatVectors(
 *                 Collections.singletonList(floatArrayToList(queryVector)))
 *             .withParams("{\"nprobe\":16}")  // 搜索时探测的聚类数
 *             .build();
 *
 *         R<SearchResult> response = client.search(searchParam);
 *         SearchResultsWrapper wrapper = new SearchResultsWrapper(
 *             response.getData().getResults());
 *
 *         List<SearchResult> results = new ArrayList<>();
 *         for (int i = 0; i < wrapper.getRowRecords().size(); i++) {
 *             results.add(new SearchResult(
 *                 (int) wrapper.getLongFieldData("chunk_id", i),
 *                 wrapper.getScore(i),
 *                 wrapper.getStringFieldData("text", i)
 *             ));
 *         }
 *         return results;
 *     }
 *
 *     private static List<Float> floatArrayToList(float[] arr) {
 *         List<Float> list = new ArrayList<>();
 *         for (float v : arr) list.add(v);
 *         return list;
 *     }
 * }
 *
 * ── Milvus 索引类型选择指南 ──
 *
 * | 索引类型   | 原理         | 适用规模   | 特点                   |
 * |-----------|-------------|-----------|----------------------|
 * | FLAT      | 暴力搜索     | <10万     | 100% 精度，速度最慢       |
 * | IVFFLAT   | 聚类+搜索    | 百万级     | 精度~95%，速度中等        |
 * | HNSW      | 图索引       | 千万级     | 精度~98%，内存占用大       |
 * | SCANN     | 量化+图      | 亿级       | 精度~90%，速度最快        |
 *
 * 推荐：开发/测试用 FLAT，生产用 HNSW。
 *
 * ── Milvus Docker 部署 ──
 *
 * # 单机版（开发测试）
 * docker run -d --name milvus-standalone \
 *   -p 19530:19530 -p 9091:9091 \
 *   -e ETCD_USE_EMBED=true \
 *   -e COMMON_STORAGETYPE=local \
 *   milvusdb/milvus:latest
 *
 * # 集群版（生产）
 * curl -sfL https://raw.githubusercontent.com/milvus-io/milvus/master/scripts/standalone_embed.sh \
 *   -o standalone_embed.sh && bash standalone_embed.sh start
 */
