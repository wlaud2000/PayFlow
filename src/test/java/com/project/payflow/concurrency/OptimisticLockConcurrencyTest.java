package com.project.payflow.concurrency;

import com.project.payflow.domain.product.entity.Product;
import com.project.payflow.domain.product.repository.ProductRepository;
import com.project.payflow.domain.product.service.ProductStockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.AopTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class OptimisticLockConcurrencyTest {

    @Autowired
    private ProductStockService productStockService;

    @Autowired
    private ProductRepository productRepository;

    // CGLIB 프록시를 언래핑해야 retryCount 필드에 접근 가능
    private ProductStockService target;

    private Long productId;

    private static final int INITIAL_STOCK = 100;
    private static final int THREAD_COUNT = 200;

    @BeforeEach
    void setUp() {
        target = AopTestUtils.getTargetObject(productStockService);
        target.retryCount.set(0);

        Product product = Product.builder()
                .name("테스트상품")
                .price(10000L)
                .stock(INITIAL_STOCK)
                .build();
        productId = productRepository.save(product).getId();
    }

    @AfterEach
    void tearDown() {
        productRepository.deleteAll();
    }

    @Test
    void 낙관적_락_동시성_테스트() throws InterruptedException {
        ConcurrencyTestUtils.ConcurrencyResult result = ConcurrencyTestUtils.execute(
                THREAD_COUNT,
                () -> productStockService.decreaseWithOptimisticLock(productId, 1)
        );

        Product product = productRepository.findById(productId).orElseThrow();

        System.out.println("\n========================================");
        System.out.println("  낙관적 락 동시성 테스트 결과");
        System.out.println("========================================");
        System.out.println("  최종 재고          : " + product.getStock());
        System.out.println("  성공 건수           : " + result.successCount());
        System.out.println("  실패 건수           : " + result.failureCount());
        System.out.println("  충돌(재시도) 횟수   : " + target.retryCount.get());
        System.out.println("  전체 처리 시간      : " + result.elapsedMs() + "ms");
        System.out.println("========================================\n");

        assertThat(product.getStock()).isEqualTo(0);
        assertThat(result.successCount()).isEqualTo(INITIAL_STOCK);
        assertThat(result.failureCount()).isEqualTo(THREAD_COUNT - INITIAL_STOCK);
    }
}
