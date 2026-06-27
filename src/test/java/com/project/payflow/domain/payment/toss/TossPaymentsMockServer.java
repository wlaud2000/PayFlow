package com.project.payflow.domain.payment.toss;

import com.github.tomakehurst.wiremock.WireMockServer;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

class TossPaymentsMockServer {

    static final String CONFIRM_URL = "/v1/payments/confirm";

    static void stubConfirmSuccess(WireMockServer server) {
        server.stubFor(post(urlEqualTo(CONFIRM_URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "paymentKey": "pg-tx-key-001",
                                  "orderId": "order-001",
                                  "totalAmount": 10000,
                                  "status": "DONE"
                                }
                                """)));
    }

    static void stubConfirmServerError(WireMockServer server) {
        server.stubFor(post(urlEqualTo(CONFIRM_URL))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "code": "PROVIDER_ERROR",
                                  "message": "일시적인 오류가 발생했습니다"
                                }
                                """)));
    }

    static void stubConfirmTimeout(WireMockServer server, int delayMs) {
        server.stubFor(post(urlEqualTo(CONFIRM_URL))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(delayMs)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "paymentKey": "pg-tx-key-001",
                                  "orderId": "order-001",
                                  "totalAmount": 10000,
                                  "status": "DONE"
                                }
                                """)));
    }
}