package com.project.payflow.domain.product.service;

import com.project.payflow.domain.product.entity.Product;
import com.project.payflow.domain.product.exception.ProductErrorCode;
import com.project.payflow.domain.product.exception.ProductException;
import com.project.payflow.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisStockService {

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> stockDecrScript;
    private final ProductRepository productRepository;

    private static final String STOCK_KEY_PREFIX = "STOCK:";

    private String key(Long productId) {
        return STOCK_KEY_PREFIX + productId;
    }

    public void initStock(Long productId, int quantity) {
        stringRedisTemplate.opsForValue().set(key(productId), String.valueOf(quantity));
    }

    public void decreaseStock(Long productId, int quantity) {
        String key = key(productId);
        Long result = stringRedisTemplate.execute(stockDecrScript, List.of(key), String.valueOf(quantity));

        if (result == null) {
            throw new ProductException(ProductErrorCode.STOCK_DECREASE_FAILED);
        }

        if (result == -1L) {
            syncFromDB(productId);
            result = stringRedisTemplate.execute(stockDecrScript, List.of(key), String.valueOf(quantity));

            if (result == null || result == -1L) {
                throw new ProductException(ProductErrorCode.STOCK_DECREASE_FAILED);
            }
        }

        if (result == 0L) {
            throw new ProductException(ProductErrorCode.INSUFFICIENT_STOCK);
        }
    }

    public void increaseStock(Long productId, int quantity){
        stringRedisTemplate.opsForValue().increment(key(productId), (long) quantity);
    }

    public Long getStock(Long productId){
        String value = stringRedisTemplate.opsForValue().get(key(productId));
        return value != null ? Long.parseLong(value) : null;
    }

    private void syncFromDB(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND));

        boolean initialized = Boolean.TRUE.equals(
                stringRedisTemplate.opsForValue().setIfAbsent(key(productId), String.valueOf(product.getStock()))
        );

        if (!initialized) {
            log.debug("Redis 재고 초기화 스킵 (다른 스레드가 선점): productId={}", productId);
        }
    }
}
