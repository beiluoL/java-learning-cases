package com.example.mq.service.impl;

import com.example.mq.common.api.ResultCode;
import com.example.mq.common.enums.ProductChangeType;
import com.example.mq.common.enums.ProductStatus;
import com.example.mq.common.exception.BusinessException;
import com.example.mq.common.message.ProductChangeMessage;
import com.example.mq.common.util.Ids;
import com.example.mq.dto.ProductChangeRequest;
import com.example.mq.dto.ProductChangeResult;
import com.example.mq.dto.ProductView;
import com.example.mq.entity.MqProduct;
import com.example.mq.entity.MqSearchIndex;
import com.example.mq.entity.MqStock;
import com.example.mq.mapper.MqProductMapper;
import com.example.mq.mapper.MqSearchIndexMapper;
import com.example.mq.mapper.MqStockMapper;
import com.example.mq.mq.ProductEventPublisher;
import com.example.mq.mq.RedisStockService;
import com.example.mq.service.ProductCacheService;
import com.example.mq.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final MqProductMapper productMapper;
    private final MqStockMapper stockMapper;
    private final MqSearchIndexMapper searchIndexMapper;
    private final RedisStockService redisStockService;
    private final ProductCacheService cacheService;
    private final ProductEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Override
    public List<ProductView> list() {
        List<MqProduct> products = productMapper.selectList(null);
        List<ProductView> views = new ArrayList<>(products.size());
        for (MqProduct product : products) {
            views.add(toView(product, false));
        }
        return views;
    }

    @Override
    public ProductView snapshot(Long productId) {
        MqProduct product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }
        return toView(product, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductChangeResult change(Long productId, ProductChangeRequest request) {
        MqProduct product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }

        ProductChangeType type = request.getChangeType();
        String newName = request.getName() != null && !request.getName().isBlank()
                ? request.getName() : product.getName();
        BigDecimal newPrice = request.getPrice() != null ? request.getPrice() : product.getPrice();
        int newStatus = switch (type) {
            case ONLINE -> ProductStatus.ONLINE.getCode();
            case OFFLINE -> ProductStatus.OFFLINE.getCode();
            case PRICE_CHANGED, INFO_UPDATED -> product.getStatus();
        };

        // 乐观锁更新：version 必须匹配，保证事件版本号单调递增
        int updated = productMapper.updateWithVersion(productId, newName, newPrice, newStatus,
                product.getVersion());
        if (updated == 0) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "商品已被其它请求修改，请刷新后重试");
        }

        MqProduct latest = productMapper.selectById(productId);
        String messageId = Ids.messageId();
        ProductChangeMessage message = ProductChangeMessage.builder()
                .messageId(messageId)
                .productId(latest.getId())
                .productNo(latest.getProductNo())
                .name(latest.getName())
                .price(latest.getPrice())
                .status(latest.getStatus())
                .changeType(type)
                .version(latest.getVersion())
                .operator(request.getOperator())
                .changeTime(LocalDateTime.now())
                .build();

        // 提交后再发布：否则消费者可能在事务提交前读到旧数据，把旧版本写进缓存/索引。
        // afterCommit 在本方法返回前执行，因此返回体里的 messageId 一定已经真实投递过。
        com.example.mq.mq.AfterCommit.run(() -> eventPublisher.publish(message));

        log.info("[PRODUCT] {} 完成 productId={} version={} messageId={}",
                type, productId, latest.getVersion(), messageId);

        return ProductChangeResult.builder()
                .product(toView(latest, true))
                .changeType(type.name())
                .version(latest.getVersion())
                .messageId(messageId)
                .message("事件已发布到 " + com.example.mq.common.mq.MqConstants.PRODUCT_EXCHANGE
                        + "，两个消费者正在并行处理")
                .build();
    }

    /* ------------------------------- 组装视图 ------------------------------- */

    private ProductView toView(MqProduct product, boolean withSnapshots) {
        MqStock stock = stockMapper.selectByProductId(product.getId());
        Integer redisStock = redisStockService.currentStock(product.getId());

        String cacheJson = null;
        Long cacheTtl = null;
        String indexJson = null;
        if (withSnapshots) {
            cacheJson = cacheService.readRaw(product.getId());
            cacheTtl = cacheService.ttlSeconds(product.getId());
            MqSearchIndex index = searchIndexMapper.selectByProductId(product.getId());
            indexJson = index == null ? null : toJson(index);
        }

        return ProductView.builder()
                .id(product.getId())
                .productNo(product.getProductNo())
                .name(product.getName())
                .price(product.getPrice())
                .status(product.getStatus())
                .statusText(ProductStatus.of(product.getStatus()).getDescription())
                .version(product.getVersion())
                .totalStock(stock == null ? null : stock.getTotal())
                .availableStock(stock == null ? null : stock.getAvailable())
                .redisStock(redisStock)
                .cacheJson(cacheJson)
                .cacheTtlSeconds(cacheTtl)
                .indexJson(indexJson)
                .build();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
