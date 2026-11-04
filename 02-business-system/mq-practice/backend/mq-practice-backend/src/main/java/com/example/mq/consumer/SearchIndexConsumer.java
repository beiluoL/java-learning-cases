package com.example.mq.consumer;

import com.example.mq.common.exception.BusinessRejectException;
import com.example.mq.common.message.ProductChangeMessage;
import com.example.mq.common.mq.MqConstants;
import com.example.mq.common.mq.MqScene;
import com.example.mq.config.RabbitMqConfig;
import com.example.mq.entity.MqSearchIndex;
import com.example.mq.mapper.MqSearchIndexMapper;
import com.example.mq.mq.AbstractMqConsumer;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * 【场景三】消费者 B：检索索引同步（模拟 Elasticsearch 写入）。
 *
 * <h3>两个刻意保留的真实细节</h3>
 * <ol>
 *   <li><b>模拟 120ms 写入延迟</b>：ES 写索引本来就比 Redis 慢一个数量级。
 *       把它放慢，才能在页面上直观看到"缓存已更新，索引还在追"的最终一致性窗口。</li>
 *   <li><b>doc_version 乐观并发</b>：ES 文档写入同样可能乱序，
 *       用版本号比较保证旧文档不会覆盖新文档（真实项目里对应 ES 的 {@code version_type=external}）。</li>
 * </ol>
 */
@Slf4j
@Component
public class SearchIndexConsumer extends AbstractMqConsumer<ProductChangeMessage> {

    private final MqSearchIndexMapper searchIndexMapper;

    public SearchIndexConsumer(MqSearchIndexMapper searchIndexMapper) {
        this.searchIndexMapper = searchIndexMapper;
    }

    @RabbitListener(
            queues = MqConstants.PRODUCT_SEARCH_QUEUE,
            containerFactory = RabbitMqConfig.MANUAL_ACK_FACTORY,
            concurrency = "2-3")
    public void onMessage(ProductChangeMessage payload, Message message, Channel channel) throws IOException {
        handle(payload, message, channel);
    }

    @Override
    protected MqScene scene() {
        return MqScene.PRODUCT_SEARCH_SYNC;
    }

    @Override
    protected String bizKeyOf(ProductChangeMessage payload) {
        return payload.getProductId() + ":v" + payload.getVersion();
    }

    @Override
    protected void doConsume(ProductChangeMessage payload, Message message) throws Exception {
        // 模拟 ES 建索引耗时（真实场景还会有分片刷新、副本同步）
        Thread.sleep(120);

        MqSearchIndex existing = searchIndexMapper.selectByProductId(payload.getProductId());
        if (existing != null && existing.getDocVersion() != null
                && existing.getDocVersion() > payload.getVersion()) {
            log.warn("[SEARCH-SYNC] 丢弃过期文档 productId={} msgVersion={} docVersion={}",
                    payload.getProductId(), payload.getVersion(), existing.getDocVersion());
            return;
        }

        if (existing == null) {
            MqSearchIndex doc = new MqSearchIndex();
            doc.setProductId(payload.getProductId());
            doc.setProductNo(payload.getProductNo());
            doc.setName(payload.getName());
            doc.setPrice(payload.getPrice());
            doc.setStatus(payload.getStatus());
            doc.setDocVersion(payload.getVersion());
            doc.setIndexedAt(LocalDateTime.now());
            searchIndexMapper.insert(doc);
            log.info("[SEARCH-SYNC] 已创建索引文档 productId={} name={} version={}",
                    payload.getProductId(), payload.getName(), payload.getVersion());
            return;
        }

        existing.setName(payload.getName());
        existing.setPrice(payload.getPrice());
        existing.setStatus(payload.getStatus());
        existing.setDocVersion(payload.getVersion());
        existing.setIndexedAt(LocalDateTime.now());
        searchIndexMapper.updateById(existing);
        log.info("[SEARCH-SYNC] 已更新索引文档 productId={} name={} price={} version={}",
                payload.getProductId(), payload.getName(), payload.getPrice(), payload.getVersion());
    }
}
