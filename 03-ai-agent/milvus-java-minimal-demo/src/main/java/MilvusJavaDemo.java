import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.R;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
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
