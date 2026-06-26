package com.project.payflow.domain.payment.toss;

import com.project.payflow.domain.payment.enums.FailureReason;
import com.project.payflow.domain.payment.exception.PaymentFallbackException;
import com.project.payflow.domain.payment.toss.dto.TossCancelRequest;
import com.project.payflow.domain.payment.toss.dto.TossConfirmRequest;
import com.project.payflow.domain.payment.toss.dto.TossConfirmResponse;
import com.project.payflow.domain.payment.toss.dto.TossErrorResponse;
import com.project.payflow.domain.payment.toss.exception.PaymentAlreadyApprovedException;
import com.project.payflow.domain.payment.toss.exception.TossPaymentsClientException;
import com.project.payflow.domain.payment.toss.exception.TossPaymentsServerException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class TossPaymentsClient {

    private final WebClient tossPaymentsWebClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;

    @PostConstruct
    void registerEventListeners() {
        circuitBreakerRegistry.circuitBreaker("tosspayments")
                .getEventPublisher()
                .onStateTransition(event -> log.warn(
                        "[CircuitBreaker-tosspayments] 상태 전이: {} → {}",
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()
                ));

        retryRegistry.retry("tosspayments")
                .getEventPublisher()
                .onRetry(event -> log.warn(
                        "[Retry-tosspayments] 재시도 발생 - 시도 횟수: {}, 대기 시간: {}ms, 예외: {}",
                        event.getNumberOfRetryAttempts(),
                        event.getWaitInterval().toMillis(),
                        event.getLastThrowable().getClass().getSimpleName()
                ));
    }

    @CircuitBreaker(name = "tosspayments", fallbackMethod = "confirmFallback")
    @Retry(name = "tosspayments")
    public TossConfirmResponse confirm(TossConfirmRequest request) {
        return tossPaymentsWebClient.post()
                .uri("/v1/payments/confirm")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(TossErrorResponse.class)
                                .flatMap(error -> {
                                    if ("ALREADY_PROCESSED_PAYMENT".equals(error.code())) {
                                        return Mono.error(new PaymentAlreadyApprovedException(error));
                                    }
                                    return Mono.error(new TossPaymentsClientException(error));
                                })
                )
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        response.bodyToMono(TossErrorResponse.class)
                                .flatMap(error -> Mono.error(new TossPaymentsServerException(error)))
                )
                .bodyToMono(TossConfirmResponse.class)
                .block();
    }

    private TossConfirmResponse confirmFallback(TossConfirmRequest request, Throwable t) {
        FailureReason reason = classifyFailureReason(t);
        log.error("[CONFIRM_FAILED] TossPayments 결제 승인 실패 — reason={}, exception={}",
                reason, t.getClass().getSimpleName());
        throw new PaymentFallbackException(reason, t);
    }

    @CircuitBreaker(name = "tosspayments", fallbackMethod = "cancelFallback")
    @Retry(name = "tosspayments")
    public void cancel(String paymentKey, TossCancelRequest request) {
        tossPaymentsWebClient.post()
                .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(TossErrorResponse.class)
                                .flatMap(error -> Mono.error(new TossPaymentsClientException(error)))
                )
                .onStatus(HttpStatusCode::is5xxServerError, response ->
                        response.bodyToMono(TossErrorResponse.class)
                                .flatMap(error -> Mono.error(new TossPaymentsServerException(error)))
                )
                .bodyToMono(Void.class)
                .block();
    }

    private void cancelFallback(String paymentKey, TossCancelRequest request, Throwable t) {
        FailureReason reason = classifyFailureReason(t);
        log.error("[REFUND_FAILED] TossPayments 환불 처리 실패 — reason={}, paymentKey={}, exception={}",
                reason, paymentKey, t.getClass().getSimpleName());
        throw new PaymentFallbackException(reason, t);
    }

    private FailureReason classifyFailureReason(Throwable t) {
        if (t instanceof CallNotPermittedException) {
            return FailureReason.CIRCUIT_OPEN;
        }
        if (t instanceof TimeoutException) {
            return FailureReason.TIMEOUT;
        }
        // 미분류 예외는 RETRY_EXHAUSTED로 처리.
        // 새로운 예외 타입이 추가될 경우 이 분기를 먼저 확인할 것.
        return FailureReason.RETRY_EXHAUSTED;
    }
}