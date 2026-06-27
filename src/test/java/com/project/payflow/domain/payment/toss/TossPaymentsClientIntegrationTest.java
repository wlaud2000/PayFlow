package com.project.payflow.domain.payment.toss;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.project.payflow.domain.payment.enums.FailureReason;
import com.project.payflow.domain.payment.exception.PaymentFallbackException;
import com.project.payflow.domain.payment.toss.dto.TossConfirmRequest;
import com.project.payflow.domain.payment.toss.dto.TossConfirmResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class TossPaymentsClientIntegrationTest {

    private static final WireMockServer wireMockServer = new WireMockServer(
            new WireMockConfiguration().dynamicPort()
    );

    static {
        wireMockServer.start();
    }

    @DynamicPropertySource
    static void overrideBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("toss.payments.base-url", wireMockServer::baseUrl);
    }

    @Autowired
    private TossPaymentsClient tossPaymentsClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        circuitBreakerRegistry.circuitBreaker("tosspayments").transitionToClosedState();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @Test
    void 시나리오1_PG_정상_응답_시_결제_승인_성공() {
        TossPaymentsMockServer.stubConfirmSuccess(wireMockServer);
        TossConfirmRequest request = new TossConfirmRequest("paymentKey", "order-001", 10000L);

        TossConfirmResponse response = tossPaymentsClient.confirm(request);

        assertThat(response.paymentKey()).isEqualTo("pg-tx-key-001");
        wireMockServer.verify(1, postRequestedFor(urlEqualTo(TossPaymentsMockServer.CONFIRM_URL)));
    }

    @Test
    void 시나리오2_PG_503_응답_시_Retry_3회_후_RETRY_EXHAUSTED_Fallback() {
        TossPaymentsMockServer.stubConfirmServerError(wireMockServer);
        TossConfirmRequest request = new TossConfirmRequest("paymentKey", "order-001", 10000L);

        assertThatThrownBy(() -> tossPaymentsClient.confirm(request))
                .isInstanceOf(PaymentFallbackException.class)
                .satisfies(e -> assertThat(((PaymentFallbackException) e).getFailureReason())
                        .isEqualTo(FailureReason.RETRY_EXHAUSTED));

        // maxAttempts: 3 = 최초 1회 + 재시도 2회 = 총 3회 HTTP 요청
        wireMockServer.verify(3, postRequestedFor(urlEqualTo(TossPaymentsMockServer.CONFIRM_URL)));
    }

    @Test
    void 시나리오3_PG_응답_지연_시_responseTimeout_초과로_Fallback() {
        // 5000ms delay > 3s responseTimeout → WebClient가 3초 후 취소
        TossPaymentsMockServer.stubConfirmTimeout(wireMockServer, 5000);
        TossConfirmRequest request = new TossConfirmRequest("paymentKey", "order-001", 10000L);

        assertThatThrownBy(() -> tossPaymentsClient.confirm(request))
                .isInstanceOf(PaymentFallbackException.class)
                .satisfies(e -> assertThat(((PaymentFallbackException) e).getFailureReason())
                        .isEqualTo(FailureReason.RETRY_EXHAUSTED));

        // responseTimeout 예외가 WebClientRequestException으로 래핑되어
        // Resilience4j Retry 매칭 실패 → 재시도 없이 1회만 호출
        wireMockServer.verify(1, postRequestedFor(urlEqualTo(TossPaymentsMockServer.CONFIRM_URL)));
    }

    @Test
    void 시나리오4_실패율_50pct_초과_시_CB_OPEN_전이_후_즉시_Fallback() {
        TossPaymentsMockServer.stubConfirmServerError(wireMockServer);
        TossConfirmRequest request = new TossConfirmRequest("paymentKey", "order-001", 10000L);

        // minimumNumberOfCalls: 5번 모두 실패 → failureRate 100% → CB OPEN
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> tossPaymentsClient.confirm(request))
                    .isInstanceOf(PaymentFallbackException.class)
                    .satisfies(e -> assertThat(((PaymentFallbackException) e).getFailureReason())
                            .isEqualTo(FailureReason.RETRY_EXHAUSTED));
        }

        assertThat(circuitBreakerRegistry.circuitBreaker("tosspayments").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        // CB OPEN 상태 → WireMock 도달 없이 즉시 CIRCUIT_OPEN Fallback
        assertThatThrownBy(() -> tossPaymentsClient.confirm(request))
                .isInstanceOf(PaymentFallbackException.class)
                .satisfies(e -> assertThat(((PaymentFallbackException) e).getFailureReason())
                        .isEqualTo(FailureReason.CIRCUIT_OPEN));

        // 5회 × 3 retry = 15회 (CB OPEN 이후 호출은 WireMock 미도달)
        wireMockServer.verify(15, postRequestedFor(urlEqualTo(TossPaymentsMockServer.CONFIRM_URL)));
    }

    @Test
    void 시나리오5_CB_OPEN_후_waitDuration_경과_시_HALF_OPEN_전이_성공으로_CLOSED() throws InterruptedException {
        TossPaymentsMockServer.stubConfirmServerError(wireMockServer);
        TossConfirmRequest request = new TossConfirmRequest("paymentKey", "order-001", 10000L);

        // 1. 5번 실패 → CB OPEN
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> tossPaymentsClient.confirm(request))
                    .isInstanceOf(PaymentFallbackException.class);
        }
        assertThat(circuitBreakerRegistry.circuitBreaker("tosspayments").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        // 2. waitDurationInOpenState(200ms) 경과 대기
        Thread.sleep(250);

        // 3. 성공 응답으로 stub 교체
        wireMockServer.resetAll();
        TossPaymentsMockServer.stubConfirmSuccess(wireMockServer);

        // 4. permittedNumberOfCallsInHalfOpenState(3)번 성공 → CB CLOSED 전이
        //    첫 번째 호출 시점에 CB가 OPEN → HALF_OPEN으로 lazy 전이됨
        for (int i = 0; i < 3; i++) {
            assertThatNoException().isThrownBy(() -> tossPaymentsClient.confirm(request));
        }

        assertThat(circuitBreakerRegistry.circuitBreaker("tosspayments").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }
}