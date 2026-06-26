package com.project.payflow.domain.payment.toss;

import com.project.payflow.domain.payment.exception.PaymentErrorCode;
import com.project.payflow.domain.payment.exception.PaymentException;
import com.project.payflow.domain.payment.toss.dto.TossCancelRequest;
import com.project.payflow.domain.payment.toss.dto.TossConfirmRequest;
import com.project.payflow.domain.payment.toss.dto.TossConfirmResponse;
import com.project.payflow.domain.payment.toss.dto.TossErrorResponse;
import com.project.payflow.domain.payment.toss.exception.TossPaymentsException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class TossPaymentsClient {

    private final WebClient tossPaymentsWebClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @PostConstruct
    void registerEventListener() {
        circuitBreakerRegistry.circuitBreaker("tosspayments")
                .getEventPublisher()
                .onStateTransition(event -> log.warn(
                        "[CircuitBreaker-tosspayments] 상태 전이: {} → {}",
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()
                ));
    }

    @CircuitBreaker(name = "tosspayments", fallbackMethod = "confirmFallback")
    public TossConfirmResponse confirm(TossConfirmRequest request) {
        return tossPaymentsWebClient.post()
                .uri("/v1/payments/confirm")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(TossErrorResponse.class)
                        .flatMap(error -> Mono.error(new TossPaymentsException(error))))
                .bodyToMono(TossConfirmResponse.class)
                .block();
    }

    private TossConfirmResponse confirmFallback(TossConfirmRequest request, CallNotPermittedException e) {
        throw new PaymentException(PaymentErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);
    }

    @CircuitBreaker(name = "tosspayments", fallbackMethod = "cancelFallback")
    public void cancel(String paymentKey, TossCancelRequest request) {
        tossPaymentsWebClient.post()
                .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(TossErrorResponse.class)
                                .flatMap(error -> Mono.error(new TossPaymentsException(error)))
                )
                .bodyToMono(Void.class)
                .block();
    }

    private void cancelFallback(String paymentKey, TossCancelRequest request, CallNotPermittedException e) {
        // 환불 실패는 결제 완료 상태에서 돈이 미반환된 케이스 — 운영자 수동 처리 필수
        log.error("[CircuitBreaker-tosspayments] 환불 CB 차단 — 운영자 수동 처리 필요 (paymentKey={})", paymentKey);
        throw new PaymentException(PaymentErrorCode.REFUND_GATEWAY_UNAVAILABLE);
    }
}