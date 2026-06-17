package com.project.payflow.domain.payment.toss;

import com.project.payflow.domain.payment.toss.dto.TossCancelRequest;
import com.project.payflow.domain.payment.toss.dto.TossConfirmRequest;
import com.project.payflow.domain.payment.toss.dto.TossConfirmResponse;
import com.project.payflow.domain.payment.toss.dto.TossErrorResponse;
import com.project.payflow.domain.payment.toss.exception.TossPaymentsException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class TossPaymentsClient {

    private final WebClient tossPaymentsWebClient;

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

    public void cancel(String paymentKey, TossCancelRequest request){
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
}
