package com.project.payflow.domain.product.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class StockMetrics {

    private final MeterRegistry registry;

    private Counter stockDecreaseSuccess;
    private Counter stockDecreaseOutOfStock;
    private Counter stockDecreaseConcurrency;
    private Counter stockSyncInconsistency;

    @PostConstruct
    public void init() {
        stockDecreaseSuccess = Counter.builder("stock.decrease.success")
                .description("재고 차감 성공 횟수")
                .register(registry);

        stockDecreaseOutOfStock = Counter.builder("stock.decrease.failure")
                .tag("reason", "out_of_stock")
                .description("재고 부족으로 인한 차감 실패")
                .register(registry);

        stockDecreaseConcurrency = Counter.builder("stock.decrease.failure")
                .tag("reason", "concurrency")
                .description("동시성 문제로 인한 차감 실패")
                .register(registry);

        stockSyncInconsistency = Counter.builder("stock.sync.inconsistency")
                .description("Redis-DB 재고 불일치 감지 횟수")
                .register(registry);
    }

    public void incrementDecreaseSuccess() {
        stockDecreaseSuccess.increment();
    }

    public void incrementOutOfStock() {
        stockDecreaseOutOfStock.increment();
    }

    public void incrementConcurrencyFailure() {
        stockDecreaseConcurrency.increment();
    }

    public void incrementSyncInconsistency() {
        stockSyncInconsistency.increment();
    }

    public void registerStockGauge(Long productId, Supplier<Long> stockSupplier) {
        if (registry.find("stock.current").tag("productId", String.valueOf(productId)).gauge() == null) {
            Gauge.builder("stock.current", stockSupplier, s -> {
                        Long stock = s.get();
                        return stock != null ? stock : 0;
                    })
                    .tag("productId", String.valueOf(productId))
                    .description("상품별 현재 재고 수량")
                    .register(registry);
        }
    }
}
