package com.project.payflow.concurrency;

import com.project.payflow.domain.product.entity.Product;
import com.project.payflow.domain.product.repository.ProductRepository;
import com.project.payflow.domain.product.service.RedisStockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
class LuaScriptConcurrencyTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private RedisStockService redisStockService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private Long productId;

    private static final int INITIAL_STOCK = 100;
    private static final int THREAD_COUNT = 200;
    private static final String STOCK_KEY_PREFIX = "STOCK:";

    @BeforeEach
    void setUp() {
        redisStockService.resetCounts();

        Product product = Product.builder()
                .name("테스트상품")
                .price(10000L)
                .stock(INITIAL_STOCK)
                .build();
        product = productRepository.save(product);
        productId = product.getId();
        redisStockService.initStock(productId, INITIAL_STOCK);
    }

    @AfterEach
    void tearDown() {
        productRepository.deleteAll();
        stringRedisTemplate.delete(STOCK_KEY_PREFIX + productId);
    }

    @Test
    void Lua_Script_동시성_테스트() throws InterruptedException {
        ConcurrencyTestUtils.ConcurrencyResult result = ConcurrencyTestUtils.execute(
                THREAD_COUNT,
                () -> redisStockService.decreaseStock(productId, 1)
        );

        Long redisStock = redisStockService.getStock(productId);

        System.out.println("\n========================================");
        System.out.println("  Lua Script 동시성 테스트 결과");
        System.out.println("========================================");
        System.out.println("  최종 Redis 재고     : " + redisStock);
        System.out.println("  성공 건수           : " + result.successCount());
        System.out.println("  실패 건수           : " + result.failureCount());
        System.out.println("  충돌(재시도) 횟수   : 0 (Redis 단일 스레드 — 구조적으로 발생 불가)");
        System.out.println("  Lua 성공 건수       : " + redisStockService.getLuaSuccessCount() + " (측정값)");
        System.out.println("  Lua 재고부족 건수   : " + redisStockService.getLuaInsufficientCount() + " (측정값)");
        System.out.println("  전체 처리 시간      : " + result.elapsedMs() + "ms");
        System.out.println("========================================\n");

        assertThat(redisStock).isEqualTo(0L);
        assertThat(result.successCount()).isEqualTo(INITIAL_STOCK);
        assertThat(result.failureCount()).isEqualTo(THREAD_COUNT - INITIAL_STOCK);
    }
}
