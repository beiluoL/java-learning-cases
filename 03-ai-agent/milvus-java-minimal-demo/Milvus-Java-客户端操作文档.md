# Milvus 结合 Java 操作文档（官方 Java 客户端）

> 适用对象：使用 **Java** 语言，通过 **官方 Milvus Java 客户端 `milvus-sdk-java`**（`io.milvus:milvus-sdk-java`）直接操作 Milvus 服务。
> 本文**不涉及具体代码实现**，只说明「用什么接口、传什么参数、能得到什么结果、注意什么」。
> 运行环境假定：Milvus 2.5.x standalone / cluster，服务端口 `19530`（与你本机 `infra/docker/milvus-standalone-docker-compose.yml` 一致）。

---

## 0. 核心概念与全局入口

| 概念 | 含义（类比关系库） |
|------|--------------------|
| **Collection（集合）** | 一张「表」，向量数据的基本载体 |
| **Field（字段）** | 集合中的列；至少含 1 个主键字段 + 1 个向量字段 + 若干标量字段 |
| **Schema** | 集合的字段定义集合（名称、类型、主键、维度等） |
| **Index（索引）** | 建在向量/标量字段上的加速结构（如 HNSW、IVF） |
| **Entity（实体）** | 一行数据，由若干字段值组成 |
| **Partition（分区）** | 集合内的逻辑子表，用于数据分片/隔离 |

**全局入口类**：`MilvusServiceClient`
所有操作都围绕一个 `MilvusServiceClient` 实例展开——它是对 Milvus gRPC 服务的封装，持有连接、负责收发请求。客户端绝大多数方法返回一个统一包装对象（通常记为 `R<T>`），其中包含调用是否成功、错误信息以及真正的业务结果 `T`。

所有「查看类」操作（`showCollections`、`describeCollection`、`query` 等）都不修改数据，可放心在生产环境只读调用。

---

## 1. 连接 Milvus 服务

### 1.1 操作步骤

1. **引入客户端依赖**（`milvus-sdk-java`），版本需与服务端 2.5.x 兼容（如 `2.5.x` 系列）。
2. **构建连接参数对象**：指定要连的服务地址与端口，以及可选的鉴权、超时等。
3. **用连接参数创建 `MilvusServiceClient` 实例**，该实例即为后续所有操作的句柄。
4. **做一次轻量连通性验证**（见第 2 节），确认握手成功，再执行业务操作。

### 1.2 关键连接参数说明

| 参数 | 说明 | 典型取值 |
|------|------|----------|
| `host` | Milvus 服务主机地址 | `localhost` / 内网 IP / 域名 |
| `port` | Milvus 服务端口 | `19530`（standalone 与 cluster 相同） |
| `uri` | 一次性写法，形如 `http://host:port`，与 host+port 二选一 | — |
| `token` | 服务端开启鉴权后的访问令牌（或 `user:password` 形式） | 由运维提供 |
| `username` / `password` | 开启鉴权时的账号密码（等价于 token 的另一种写法） | `root` / 自定义 |
| `secure` / `sslContext` | 是否启用 TLS 加密传输，公网或安全要求高时开启 | `false`（内网）/ `true` |
| `connectTimeoutMs` | 建立连接的超时时间（毫秒） | 10000 左右 |
| `keepAliveTimeMs` / `keepAliveTimeoutMs` | gRPC keep-alive 心跳间隔与超时 | 视网络稳定性 |
| `idleTimeoutMs` | 连接空闲回收时间 | — |

### 1.3 注意事项

- **鉴权**：未开启鉴权时无需 token；开启后必须提供，否则所有请求返回「未授权」。
- **standalone vs cluster**：客户端写法完全一致，区别只在 `host`/`port` 指向的是单机还是负载均衡地址。
- **连接是长连接**：一个 `MilvusServiceClient` 实例可复用，避免每次操作都新建。
- 连接对象使用完毕后需显式**关闭**（见第 6 节），否则可能泄漏底层 gRPC 通道。

---

## 2. 查看连接状态与服务端信息

### 2.1 验证连接是否存活（连通性探针）

通过调用一个**轻量、只读**的接口来确认链路正常，最常用的是「获取服务端版本」：

- **操作方式**：调用「获取服务端版本」接口。
- **返回内容**：服务端 Milvus 的版本字符串（如 `2.5.27`）。
- **如何判断**：能拿到版本号即说明客户端与服务端握手成功、链路通；调用失败/超时则说明地址、端口、鉴权或网络有问题。

此外，「判断某集合是否存在」（`hasCollection`）也是一个常用的连通性与存在性探针——它既验证连接，又顺带确认目标集合是否在服务端。

> 2.5.x 客户端提示：该「获取服务端版本」接口在 V1 客户端中实际名为 **`getVersion()`**（返回 `GetVersionResponse`，含 `version` 字段）；**并不存在** `getServerVersion` / `getServerAddress` 这类方法，请勿按旧资料套用。

### 2.2 查看已连接的服务端地址

- **操作方式**：2.5.x 的 V1 客户端**未提供**「获取服务端地址」接口；连接目标由你构建 `ConnectParam` 时传入的 `host` / `port` 决定。
- **建议**：把 `host` / `port` 作为配置项统一管理（例如放进 `application.yml` 或环境变量），避免在代码里硬编码导致「连错环境」。
- **用途**：排查「连错环境」类问题时，直接核对配置项即可。

### 2.3 查看服务端 / 集群信息

- **版本信息**：见 2.1 的「服务端版本」接口，可判断客户端与服务端版本是否匹配。
- **运行时指标（QPS、内存、节点、磁盘等）**：
  - Java 客户端层面提供的是**轻量级**探针，主要靠「服务端版本 / 连通性」判断健康度。
  - 更细粒度的**集群与节点级指标**，建议通过 Milvus 暴露的 **HTTP `/metrics` 接口**（默认 `9091` 端口）拉取，或使用官方可视化管理工具 **Attu**、Grafana 看板查看。
  - 若客户端 SDK 提供了「获取指标 / getMetric」类接口，其返回通常是原始指标文本，需自行解析；生产环境更推荐走 `/metrics` 与监控面板。

> 小结：客户端的「连接状态」= 实例是否存活 + 能否成功完成一次轻量请求（版本/存在性探测）；「集群信息」则更多依赖 `/metrics` + Attu，而非仅靠 Java SDK。

---

## 3. 查看集合（Collection）列表

### 3.1 操作步骤

1. 确保已建立并验证连接（第 1、2 节）。
2. 调用「列出集合」或「展示集合」接口获取集合清单。
3. 从返回结果中读取集合名称及附属信息。

### 3.2 两种查看方式

| 方式 | 适用场景 | 返回内容 |
|------|----------|----------|
| **简单列表** | 只想知道有哪些集合 | 集合名称列表 |
| **详细展示** | 想同时看 ID、创建时间、内存加载比例等 | 集合名称、集合 ID、创建时间戳、内存加载百分比等 |

### 3.3 关键参数与返回说明

- **过滤类型（type）**：可按 `全部 / 已加载到内存 / 临时` 等维度过滤列表（典型枚举：All、Loaded、InMemory、Temp）。
  - 选 `Loaded` 可只看当前已 load 进内存、可立即查询的集合，便于排查「查不到数据是因为没 load」的问题。
- **返回字段含义**：
  - `collectionNames`：集合名数组。
  - `collectionIds`：每个集合对应的唯一 ID。
  - `createdUtcTimestamps`：创建时间（UTC 时间戳）。
  - `inMemoryPercentages`：内存加载进度（0~100，用于判断 load 是否完成）。

### 3.4 单集合存在性判断

- **操作方式**：调用「判断集合是否存在」接口，传入集合名。
- **返回内容**：布尔值（存在 / 不存在）。
- **用途**：执行 describe / query 前先确认目标存在，避免「集合不存在」异常；也常作连通性探针。

---

## 4. 查看指定集合的详细信息

拿到集合名后，可分别查看其 **Schema / 字段**、**实体数量** 与 **索引**。

### 4.1 查看 Schema 与字段（describe）

- **操作方式**：调用「描述集合」接口，传入目标集合名。
- **返回内容（核心）**：
  - **集合元信息**：集合 ID、描述、分片数（shards）、一致性级别、别名列表、创建时间等。
  - **字段定义列表（Schema）**：每个字段包含：
    | 字段属性 | 含义 |
    |----------|------|
    | `fieldName` | 字段名 |
    | `dataType` | 数据类型（见下方枚举） |
    | `dimension` | 向量字段的维度（仅向量类型有） |
    | `isPrimaryKey` | 是否主键 |
    | `isAutoID` | 主键是否由 Milvus 自增 |
    | `isPartitionKey` | 是否作为分区键 |
    | `maxLength` | VarChar 类型的最大长度 |
    | `elementType` | Array 类型中的元素类型 |
    | `description` | 字段备注 |
  - **常见数据类型（DataType）**：Bool、Int8/16/32/64、Float、Double、**VarChar**、BinaryVector、**FloatVector**、Array、JSON 等。

- **用途**：确认字段名、向量维度、主键设置是否与预期一致（维度不一致是插入/查询常见报错根因）。

### 4.2 查看实体数量

- **操作方式**：调用「统计实体数 / 获取集合统计」接口，传入集合名。
- **返回内容**：该集合当前实体（行）总数（Long）。
- **注意**：统计的是已落盘/已提交的数据量；若刚插入未 flush，可能与实时看到的有偏差。

### 4.3 查看索引（Index）

Milvus 的索引可在向量字段，也可在标量字段上建立。查看索引有两种粒度：

| 方式 | 操作 | 返回 |
|------|------|------|
| **描述单个索引** | 调用「描述索引」接口，指定集合名 + 字段名（或索引名） | 该字段上的索引详情 |
| **列出全部索引** | 调用「列出索引」接口，仅传集合名 | 该集合所有索引的概览列表 |
| **索引构建状态** | 调用「获取索引状态 / 构建进度」接口 | 索引是否就绪、已索引行数 / 总行数（构建进度） |

**索引详情包含的关键参数**：

| 参数 | 说明 |
|------|------|
| `indexName` | 索引名称 |
| `fieldName` | 索引建在哪个字段上 |
| `indexType` | 索引类型（如 FLAT、IVF_FLAT、IVF_PQ、HNSW、DISKANN、AUTOINDEX 等） |
| `metricType` | 相似度度量（L2、IP、COSINE 等，仅向量索引有） |
| `params` | 索引构建参数（如 `nlist`、`M`、`efConstruction` 等，随类型不同而异） |

- **用途**：确认向量字段是否已建索引、用的什么类型与度量方式——没建索引的向量字段只能做暴力全量检索，性能差。
- **2.5.x 读取方式**：`describeIndex` 返回的每个索引描述对象没有独立的 `getIndexType()` / `getMetricType()`，而是通过 **`getParamsList()`（键值对列表）** 携带 `index_type`、`metric_type` 以及额外参数（`params`），遍历该列表即可拿到类型与度量。

---

## 5. 查询集合中的实体数据（Query）

> 区分两个易混操作：
> - **Query（标量条件查询）**：按布尔表达式过滤，返回命中的**原始字段值**（类似 SQL 的 `SELECT ... WHERE`）。
> - **Search（向量相似度检索）**：给定向量找最近邻（ANN），用于语义/相似度场景（你本项目 Spring AI 默认走的就是 Search）。
> 本节专讲 **Query**。

### 5.1 操作步骤

1. 确认集合存在且已 load 到内存（否则查不到数据）。
2. 构造查询请求：指定集合名、过滤表达式、要返回的字段、分页与一致性级别。
3. 调用「查询」接口。
4. 从返回结果中按字段名提取列数据（结果是**按字段组织的列式结构**，而非逐行对象）。

### 5.2 关键查询参数说明

| 参数 | 说明 | 典型用法 |
|------|------|----------|
| `collectionName` | 目标集合名（必填） | — |
| `expr` | 布尔过滤表达式（类 SQL WHERE） | `id in [1,2,3]`、`age > 18`、`title like "Java%"` |
| `outputFields` | 指定返回哪些字段 | 留空默认返回标量字段；**向量字段需显式列出才会返回** |
| `partitionNames` | 限定只查某些分区 | 数据按分区隔离时加速/隔离查询 |
| `limit` | 返回最大条数 | 配合分页使用 |
| `offset` | 偏移量（跳过前 N 条） | 与 limit 组成分页 |
| `consistencyLevel` | 一致性级别 | 见 5.4 |

### 5.3 返回结果说明

- 返回结果是**列式（column-based）**结构：每个字段对应一个「字段数据」对象，内含字段名、数据类型与该列的所有值。
- 读取时需**按字段名**逐一提取对应列，再按行下标对齐拼接出一行实体；并非直接拿到「对象列表」。

### 5.4 一致性级别（consistencyLevel）说明

| 级别 | 含义 | 适用 |
|------|------|------|
| `STRONG` / `Strict` | 强一致，总能读到最新写入 | 对实时性要求高的查询 |
| `BOUNDED` | 有界 stale，可承受极短延迟 | 大多数读场景的默认推荐 |
| `SESSION` | 会话内一致 | 同一会话连续读写 |
| `EVENTUALLY` | 最终一致，延迟最低 | 对实时性不敏感的批量分析 |

### 5.5 注意事项

- **expr 语法**：需符合 Milvus 表达式规范（字段名、运算符、字符串用双引号等），语法错误会直接报错。
- **向量字段默认不返回**：若想看向量值，必须在 `outputFields` 显式写出向量字段名（或指定返回所有字段）。
- **分页**：用 `limit` + `offset` 控制，避免一次拉取海量数据。
- **必须先 load**：集合若未 load 进内存，query 会失败或返回空；可先用「展示集合」接口看 `inMemoryPercentages` 确认加载进度。

---

## 6. 关闭连接与资源释放

- **操作方式**：调用 `MilvusServiceClient` 的「关闭」接口。
- **说明**：关闭会释放底层 gRPC 通道与连接资源。应用退出前务必调用，尤其是短生命周期任务（如一次性脚本、批处理）。
- **注意**：关闭后该 client 实例不可再用，需重新创建方可再次操作。

---

## 7. 常见问题与排查清单

| 现象 | 可能原因 | 排查动作 |
|------|----------|----------|
| 连接失败 / 超时 | 地址端口错、服务未起、网络不通 | 用「获取服务端版本」探针；`docker compose ps` 看 Milvus 是否 healthy |
| 未授权错误 | 服务端开了鉴权但没传 token | 检查连接参数的 token / 账号密码 |
| 集合不存在 | 名称拼错、集合未创建 | 先「列出集合」确认名称；或用 `hasCollection` 判断 |
| query 返回空 | 集合未 load / 过滤表达式过滤掉全部 | 看 `inMemoryPercentages` 是否 100%；核对 `expr` |
| 维度不匹配 | 字段向量维度与写入/模型输出不一致 | 用「描述集合」核对 `dimension` |
| 查询慢 | 向量字段未建索引、走了暴力检索 | 用「列出索引」确认索引存在与类型 |

---

## 8. 与本机项目的对应关系（补充说明）

你本机 `spring-ai-milvus-demo` 用的是 **Spring AI 的 `MilvusVectorStore`** 抽象层（业务代码只依赖 `VectorStore` 接口，由 starter 自动装配），它底层正是封装了本文的 `milvus-sdk-java`。

- **Spring AI 层**：适合业务开发（写入文档、相似度检索），屏蔽了 SDK 细节。
- **本文 `milvus-sdk-java` 层**：适合**运维 / 排查 / 精确控制**——比如你想直接看有哪些集合、某个集合的 schema 和索引对不对、里面到底有多少条数据、连接的服务端版本等，就需要落到这一层。

两者不冲突：生产业务走 Spring AI 抽象，排障与巡检走本文的原生 SDK 操作。

> 注：milvus-sdk-java 2.5.x 同时提供 **V1 客户端 `MilvusServiceClient`**（本文前述所有接口）与更新的 **V2 客户端 `MilvusClientV2`**。本章示例统一使用 **V1**，以与上文第 1–7 节的术语、方法名完全一致。

---

## 9. 实战章节：最小可运行示例（Minimal Runnable Example）

### 9.1 场景说明

本示例用一个**完整、可直接运行**的小程序，把前文第 1–7 节的核心操作串起来演示一遍：

1. 连接 Milvus 并打印**连接状态 / 服务端版本**；
2. 创建一个集合（Schema = 主键 + 向量 + 标题），并建**索引**；
3. 加载集合到内存、插入 3 条实体；
4. **列出集合** → **查看集合 Schema/字段** → **查看索引** → **查询实体（Query）**；
5. 清理并关闭连接。

复制下面两份文件后，`mvn compile exec:java` 即可运行，无需任何额外配置。

### 9.2 运行环境要求

| 项目 | 要求 |
|------|------|
| Milvus 服务 | 已启动且 `19530` 端口可达（你本机即 `docker compose up -d` 拉起的 2.5.27 standalone） |
| Java | 8 及以上（本机 Java 17 完全兼容） |
| 构建工具 | Apache Maven 3.x |
| 网络 | 首次 `mvn` 需联网拉取 `milvus-sdk-java` 及其传递依赖 |

> 若服务端开启了鉴权，把代码中 `.withAuthorization("root", "Milvus")` 一行取消注释即可。

### 9.3 项目结构

```
milvus-java-minimal-demo/
├── pom.xml                     # 依赖 + 运行插件
└── src/main/java/
    └── MilvusJavaDemo.java     # 全部演示逻辑（单文件、无包名）
```

### 9.4 依赖与配置（pom.xml）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>milvus-java-minimal-demo</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.source>8</maven.compiler.source>
        <maven.compiler.target>8</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <!-- ★ 核心：官方 Milvus Java 客户端（2.5.x 系列均可，如 2.5.5 / 2.5.8） -->
        <dependency>
            <groupId>io.milvus</groupId>
            <artifactId>milvus-sdk-java</artifactId>
            <version>2.5.8</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- 直接一行命令运行 main 类 -->
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.1.0</version>
                <configuration>
                    <mainClass>MilvusJavaDemo</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

> 从 2.5.2 起 SDK 拆分为 `milvus-sdk-java`（核心）与 `milvus-sdk-java-bulkwriter`（批量写入，可选）。本示例只用核心包。

### 9.5 完整示例代码（MilvusJavaDemo.java）

```java
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.R;
import io.milvus.grpc.DataType;
import io.milvus.grpc.GetLoadStateResponse;
import io.milvus.grpc.LoadState;
import io.milvus.grpc.DescribeCollectionResponse;
import io.milvus.grpc.FieldSchema;
import io.milvus.grpc.KeyValuePair;
import io.milvus.grpc.DescribeIndexResponse;
import io.milvus.grpc.IndexDescription;
import io.milvus.grpc.ShowCollectionsResponse;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.collection.ShowCollectionsParam;
import io.milvus.param.collection.DescribeCollectionParam;
import io.milvus.param.collection.GetLoadStateParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.param.index.DescribeIndexParam;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.common.clientenum.ConsistencyLevelEnum;

import java.util.*;

public class MilvusJavaDemo {

    // ===== 可调参数：对应你本机 docker Milvus（端口 19530）=====
    private static final String HOST = "localhost";
    private static final int PORT = 19530;
    private static final String COLLECTION = "java_minimal_demo";
    private static final String ID_FIELD = "book_id";
    private static final String VECTOR_FIELD = "book_intro";
    private static final String TITLE_FIELD = "book_title";
    private static final int DIM = 4;

    public static void main(String[] args) throws Exception {
        // ---------- 1. 建立连接 ----------
        MilvusServiceClient client = new MilvusServiceClient(
                ConnectParam.newBuilder()
                        .withHost(HOST)
                        .withPort(PORT)
                        // 若服务端开启了鉴权，取消下一行注释并填入账号密码
                        // .withAuthorization("root", "Milvus")
                        .build());

        System.out.println("== 1. 连接状态 / 服务端信息 ==");
        // 2.5.x 中用 getVersion() 做连通性探针（无 getServerVersion / getServerAddress）
        System.out.println("服务端版本 : " + client.getVersion().getData().getVersion());
        System.out.println("连接目标   : " + HOST + ":" + PORT);

        // ---------- 2. 清场：若集合已存在则先删除，保证可重复运行 ----------
        if (client.hasCollection(HasCollectionParam.newBuilder().withCollectionName(COLLECTION).build()).getData()) {
            client.dropCollection(DropCollectionParam.newBuilder().withCollectionName(COLLECTION).build());
            System.out.println("已删除旧集合 " + COLLECTION);
        }

        // ---------- 3. 建集合：Schema = 主键 + 向量 + 标题 ----------
        FieldType idField = FieldType.newBuilder()
                .withName(ID_FIELD).withDataType(DataType.Int64)
                .withPrimaryKey(true).withAutoID(false).build();
        FieldType vecField = FieldType.newBuilder()
                .withName(VECTOR_FIELD).withDataType(DataType.FloatVector).withDimension(DIM).build();
        FieldType titleField = FieldType.newBuilder()
                .withName(TITLE_FIELD).withDataType(DataType.VarChar).withMaxLength(64).build();

        check(client.createCollection(CreateCollectionParam.newBuilder()
                .withCollectionName(COLLECTION)
                .withFieldTypes(Arrays.asList(idField, vecField, titleField))
                .build()), "createCollection");

        // ---------- 4. 建索引：向量字段（FLAT + L2）+ 标量字段（TRIE）----------
        // withSyncMode(true) 让建索引调用阻塞到完成，便于随后立即 describeIndex 查看
        client.createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(COLLECTION).withFieldName(VECTOR_FIELD)
                .withIndexName("idx_vector").withIndexType(IndexType.FLAT)
                .withMetricType(MetricType.L2).withSyncMode(true).build());
        client.createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(COLLECTION).withFieldName(TITLE_FIELD)
                .withIndexName("idx_title").withIndexType(IndexType.TRIE).withSyncMode(true).build());

        // ---------- 5. 加载集合到内存（查询/检索前提），并轮询等待加载完成 ----------
        client.loadCollection(LoadCollectionParam.newBuilder().withCollectionName(COLLECTION).build());
        for (int i = 0; i < 30; i++) {
            GetLoadStateResponse state = client.getLoadState(
                    GetLoadStateParam.newBuilder().withCollectionName(COLLECTION).build()).getData();
            if (state.getState() == LoadState.LoadStateLoaded) {
                System.out.println("集合已加载到内存");
                break;
            }
            Thread.sleep(1000);
        }

        // ---------- 6. 插入 3 条实体（列式写入：每个字段一个值列表）----------
        List<Long> ids = Arrays.asList(1L, 2L, 3L);
        List<String> titles = Arrays.asList("Java 编程思想", "Spring Boot 实战", "Milvus 向量数据库入门");
        List<List<Float>> vectors = new ArrayList<>();
        Random rnd = new Random(42);
        for (int i = 0; i < 3; i++) {
            List<Float> v = new ArrayList<>();
            for (int j = 0; j < DIM; j++) v.add(rnd.nextFloat());
            vectors.add(v);
        }
        client.insert(InsertParam.newBuilder()
                .withCollectionName(COLLECTION)
                .withFields(Arrays.asList(
                        new InsertParam.Field(ID_FIELD, ids),
                        new InsertParam.Field(TITLE_FIELD, titles),
                        new InsertParam.Field(VECTOR_FIELD, vectors)))
                .build());
        System.out.println("已插入 3 条实体");

        // ---------- 7. 列出集合 ----------
        System.out.println("\n== 7. 集合列表 ==");
        ShowCollectionsResponse list = client.showCollections(
                ShowCollectionsParam.newBuilder().build()).getData();
        System.out.println("集合名: " + list.getCollectionNamesList());

        // ---------- 8. 查看集合详情（Schema / 字段）----------
        System.out.println("\n== 8. 集合 Schema 与字段 ==");
        DescribeCollectionResponse desc = client.describeCollection(
                DescribeCollectionParam.newBuilder().withCollectionName(COLLECTION).build()).getData();
        for (FieldSchema f : desc.getSchema().getFieldsList()) {
            StringBuilder sb = new StringBuilder();
            sb.append("字段=").append(f.getName())
              .append(" | 类型=").append(f.getDataType())
              .append(" | 主键=").append(f.getIsPrimaryKey())
              .append(" | 自增=").append(f.getAutoID());
            // 维度 / 最大长度等附加属性都放在 typeParams 里（key 分别为 dim / max_length）
            for (KeyValuePair kv : f.getTypeParamsList()) {
                if ("dim".equals(kv.getKey())) sb.append(" | 维度=").append(kv.getValue());
                if ("max_length".equals(kv.getKey())) sb.append(" | 最大长度=").append(kv.getValue());
            }
            System.out.println(sb);
        }

        // ---------- 9. 查看索引 ----------
        System.out.println("\n== 9. 索引信息 ==");
        DescribeIndexResponse idx = client.describeIndex(
                DescribeIndexParam.newBuilder().withCollectionName(COLLECTION).build()).getData();
        for (IndexDescription d : idx.getIndexDescriptionsList()) {
            StringBuilder sb = new StringBuilder();
            sb.append("索引名=").append(d.getIndexName())
              .append(" | 字段=").append(d.getFieldName())
              .append(" | 状态=").append(d.getState());
            // 索引类型 / 度量方式在 2.5.x 中放在 params 键值对里（key 为 index_type / metric_type）
            for (KeyValuePair kv : d.getParamsList()) {
                sb.append(" | ").append(kv.getKey()).append("=").append(kv.getValue());
            }
            System.out.println(sb);
        }

        // ---------- 10. 查询实体数据（Query：标量条件过滤）----------
        System.out.println("\n== 10. 查询实体（Query）==");
        R<io.milvus.grpc.QueryResults> q = client.query(QueryParam.newBuilder()
                .withCollectionName(COLLECTION)
                .withExpr(ID_FIELD + " in [1, 2, 3]")          // 布尔过滤表达式，类似 SQL WHERE
                .withOutFields(Arrays.asList(ID_FIELD, TITLE_FIELD, VECTOR_FIELD)) // 向量字段需显式列出
                .withConsistencyLevel(ConsistencyLevelEnum.STRONG) // 强一致，确保读到刚插入的数据
                .withLimit(10L)
                .build());
        check(q, "query");
        QueryResultsWrapper qw = new QueryResultsWrapper(q.getData());
        List<?> qIds = qw.getFieldWrapper(ID_FIELD).getFieldData();
        List<?> qTitles = qw.getFieldWrapper(TITLE_FIELD).getFieldData();
        List<?> qVecs = qw.getFieldWrapper(VECTOR_FIELD).getFieldData();
        System.out.println("本次查询命中实体数: " + qw.getFieldWrapper(ID_FIELD).getRowCount());
        for (int i = 0; i < qIds.size(); i++) {
            System.out.println("  id=" + qIds.get(i)
                    + " | title=" + qTitles.get(i)
                    + " | vector=" + qVecs.get(i));
        }

        // ---------- 11. 清理 & 关闭 ----------
        client.dropCollection(DropCollectionParam.newBuilder().withCollectionName(COLLECTION).build());
        client.close();
        System.out.println("\n演示完成，连接已关闭。");
    }

    // 统一处理返回状态：非 Success 直接抛异常，便于定位
    private static void check(R<?> r, String op) {
        if (r.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException(op + " 失败: " + r.getMessage());
        }
    }
}
```

### 9.6 运行步骤

```bash
# 1) 确保 Milvus 已起来（你本机的 standalone）
cd /Users/beiluo/Documents/alProject/javalearn/docker
docker compose -f milvus-standalone-docker-compose.yml up -d

# 2) 编译并运行示例（首次会联网下载依赖）
cd milvus-java-minimal-demo
mvn compile exec:java
```

### 9.7 预期输出（示意，gRPC 自身可能打印少量 INFO 日志，可忽略）

```
== 1. 连接状态 / 服务端信息 ==
服务端版本 : 2.5.27
已连地址   : localhost:19530
已插入 3 条实体

== 7. 集合列表 ==
集合名: [java_minimal_demo]

== 8. 集合 Schema 与字段 ==
字段=book_id | 类型=Int64 | 主键=true | 自增=false
字段=book_intro | 类型=FloatVector | 主键=false | 自增=false | 维度=4
字段=book_title | 类型=VarChar | 主键=false | 自增=false | 最大长度=64

== 9. 索引信息 ==
索引名=idx_vector | 字段=book_intro | 状态=Finished | index_type=FLAT | metric_type=L2
索引名=idx_title | 字段=book_title | 状态=Finished | index_type=TRIE

== 10. 查询实体（Query）==
本次查询命中实体数: 3
  id=1 | title=Java 编程思想 | vector=[0.72, 0.11, 0.55, 0.90]
  id=2 | title=Spring Boot 实战 | vector=[0.34, 0.88, 0.21, 0.47]
  id=3 | title=Milvus 向量数据库入门 | vector=[0.63, 0.09, 0.77, 0.15]

演示完成，连接已关闭。
```

> 说明：`idx_title` 是标量索引，没有「相似度度量」，所以 `度量=` 为空属正常。向量值因使用随机种子固定为 `42`，你本机输出数值可能不同，但结构与顺序一致。

### 9.8 关键实现点说明

| 关键点 | 说明 |
|--------|------|
| **连接** | `MilvusServiceClient` 用 `ConnectParam` 构建，所有后续操作都复用这一个 client 实例；用 `getVersion()` 做连通性探针，连接目标（host:port）由 `ConnectParam` 决定。 |
| **集合 Schema** | 至少 1 个主键字段 + 1 个向量字段；向量字段必须指定 `dimension`，VarChar 必须指定 `maxLength`。 |
| **建索引 `withSyncMode(true)`** | 让 `createIndex` 阻塞直到索引构建完成，否则紧接着的 `describeIndex` 可能还看不到索引。 |
| **必须先 load 再查** | `loadCollection` 是异步的，示例用 `getLoadState` 轮询直到 `LoadStateLoaded` 才继续。 |
| **Query 结果列式读取** | `QueryResultsWrapper.getFieldWrapper(字段名).getFieldData()` 返回该字段整列值；向量字段返回 `List<List<Float>>`，按行下标与标量列对齐。 |
| **强一致 `STRONG`** | 刚插入就查，用 `STRONG` 一致性保证能立即读到，避免「读不到刚写的数据」。 |
| **实体数量** | 本例复用 query 结果的 `getRowCount()`；也可用专用接口 `countEntities` / `getCollectionStatistics`（需先 `flush` 以保证统计准确）。 |
| **资源释放** | 末尾 `dropCollection` + `close()`，避免残留集合与泄漏 gRPC 连接。 |

> 该示例覆盖的接口与第 1–7 节一一对应：连接/状态（§1–2）、集合列表（§3）、Schema/字段（§4.1）、索引（§4.3）、实体查询（§5）、关闭（§6）。把它跑通，就等于把前文所有「操作方式」在真实环境验证了一遍。
