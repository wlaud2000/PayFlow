package com.project.payflow.domain.product.service;

import com.project.payflow.domain.product.entity.Product;
import com.project.payflow.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockSyncBatchService {

    private final ProductRepository productRepository;
    private final RedisStockService redisStockService;

    // TODO : Prometheus 연동 시 MeterRegistry Gauge로 전환 예정
    public final AtomicInteger inconsistencyCount = new AtomicInteger(0);

    @Scheduled(fixedRate = 600_000)
    @Transactional(readOnly = true)
    public void checkStockConsistency() {
        inconsistencyCount.set(0);

        List<Product> products = productRepository.findAll();
        int totalCount = products.size();

        for (Product product : products) {
            Long productId = product.getId();
            Long redisStock = redisStockService.getStock(productId);

            if (redisStock == null) {
                log.warn("[StockSync] Redis 키 없음 - productId={}, DB 재고={}", productId, product.getStock());
                inconsistencyCount.incrementAndGet();
                continue;
            }

            if (!redisStock.equals(product.getStock().longValue())) {
                log.warn("[StockSync] 재고 불일치 감지 - productId={}, redis={}, db={}", productId, redisStock, product.getStock());
                inconsistencyCount.incrementAndGet();
            }
        }

        log.info("[StockSync] 배치 완료 - 총 {}건 중 불일치 {}건 감지",
                totalCount, inconsistencyCount.get());
    }
}
