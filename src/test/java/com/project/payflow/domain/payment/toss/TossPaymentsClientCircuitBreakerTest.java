package com.project.payflow.domain.payment.toss;

import com.project.payflow.domain.payment.enums.FailureReason;
import com.project.payflow.domain.payment.exception.PaymentFallbackException;
import com.project.payflow.domain.payment.toss.dto.TossCancelRequest;
import com.project.payflow.domain.payment.toss.dto.TossConfirmRequest;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class TossPaymentsClientCircuitBreakerTest {

    @Autowired
    private TossPaymentsClient tossPaymentsClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @MockitoBean
    private WebClient tossPaymentsWebClient;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry.circuitBreaker("tosspayments")
                .transitionToOpenState();
    }

    @AfterEach
    void tearDown() {
        circuitBreakerRegistry.circuitBreaker("tosspayments")
                .transitionToClosedState();
    }

    @Test
    void CB_OPEN_상태에서_confirm_호출_시_CIRCUIT_OPEN_이유로_PaymentFallbackException_반환() {
        TossConfirmRequest request = new TossConfirmRequest("paymentKey", "orderId-1", 10000L);

        assertThatThrownBy(() -> tossPaymentsClient.confirm(request))
                .isInstanceOf(PaymentFallbackException.class)
                .satisfies(e -> assertThat(((PaymentFallbackException) e).getFailureReason())
                        .isEqualTo(FailureReason.CIRCUIT_OPEN));
    }

    @Test
    void CB_OPEN_상태에서_cancel_호출_시_CIRCUIT_OPEN_이유로_PaymentFallbackException_반환() {
        assertThatThrownBy(() ->
                tossPaymentsClient.cancel("paymentKey", new TossCancelRequest("사용자 요청")))
                .isInstanceOf(PaymentFallbackException.class)
                .satisfies(e -> assertThat(((PaymentFallbackException) e).getFailureReason())
                        .isEqualTo(FailureReason.CIRCUIT_OPEN));
    }
}