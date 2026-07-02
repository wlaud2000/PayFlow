package com.project.payflow.domain.payment.event;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.project.payflow.domain.member.entity.Member;
import com.project.payflow.domain.member.repository.MemberRepository;
import com.project.payflow.domain.order.entity.Order;
import com.project.payflow.domain.order.enums.OrderStatus;
import com.project.payflow.domain.order.repository.OrderRepository;
import com.project.payflow.domain.payment.dto.ConfirmRequest;
import com.project.payflow.domain.payment.entity.Payment;
import com.project.payflow.domain.payment.entity.PaymentEvent;
import com.project.payflow.domain.payment.enums.PaymentEventType;
import com.project.payflow.domain.payment.exception.PaymentException;
import com.project.payflow.domain.payment.repository.PaymentEventRepository;
import com.project.payflow.domain.payment.repository.PaymentRepository;
import com.project.payflow.domain.payment.service.PaymentService;
import com.project.payflow.domain.product.entity.Product;
import com.project.payflow.domain.product.repository.ProductRepository;
import com.project.payflow.global.kafka.event.PaymentApprovedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class PaymentEventPublisherIntegrationTest {

    // 테스트가 @Transactional을 사용하지 않는 이유:
    // 테스트 트랜잭션이 PaymentService 트랜잭션을 감싸면 AFTER_COMMIT이 테스트 트랜잭션 커밋 시점에 발동.
    // @Rollback이면 테스트 트랜잭션이 롤백되어 AFTER_COMMIT이 발동하지 않음 → kafkaTemplate.send() 미호출.
    // 실제 커밋이 일어나야 AFTER_COMMIT이 동작하므로 각 테스트에서 직접 데이터를 정리한다.

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
    private PaymentService paymentService;

    @MockitoBean
    @SuppressWarnings("rawtypes")
    private KafkaTemplate kafkaTemplate;  // 제네릭 타입 소거로 raw type 사용 — @MockBean이 실제 빈을 교체

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentEventRepository paymentEventRepository;

    private Long orderId;
    private Long paymentId;
    private static final Long AMOUNT = 10000L;
    private static final String PAYMENT_KEY = "toss-test-payment-key";

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();

        Member member = memberRepository.save(
                Member.builder().email("test@test.com").password("encoded-password").build()
        );

        Product product = productRepository.save(
                Product.builder().name("테스트 상품").price(AMOUNT).stock(100).build()
        );

        Order order = orderRepository.save(
                Order.builder()
                        .member(member)
                        .product(product)
                        .quantity(1)
                        .status(OrderStatus.PAYING)
                        .build()
        );
        orderId = order.getId();

        Payment payment = paymentRepository.save(
                Payment.builder().order(order).amount(AMOUNT).build()
        );
        paymentId = payment.getId();

        String idempotencyKey = orderId + ":" + PaymentEventType.CONFIRM.name();
        paymentEventRepository.save(PaymentEvent.pending(payment, idempotencyKey, PaymentEventType.CONFIRM));
    }

    @AfterEach
    void tearDown() {
        paymentEventRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("결제 승인 완료 후 트랜잭션 커밋 시 PaymentApprovedEvent가 Kafka에 발행된다")
    void 결제_승인_완료_후_이벤트_발행() {
        // given: TossPayments 성공 응답 스텁
        wireMockServer.stubFor(post(urlEqualTo("/v1/payments/confirm"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                                "paymentKey": "%s",
                                "orderId": "%s",
                                "status": "DONE"
                            }
                        """.formatted(PAYMENT_KEY, orderId))
                )
        );

        ConfirmRequest request = new ConfirmRequest(PAYMENT_KEY, orderId.toString(), AMOUNT);

        // when
        paymentService.confirmPayment(request);

        // then — 검증 1: 트랜잭션 커밋 후 kafkaTemplate.send() 호출됨
        verify(kafkaTemplate).send(
                eq("payment-approved"),
                eq(paymentId.toString()),
                any(PaymentApprovedEvent.class)
        );

        // then — 검증 2: 발행된 이벤트의 핵심 필드 검증
        ArgumentCaptor<PaymentApprovedEvent> captor = ArgumentCaptor.forClass(PaymentApprovedEvent.class);
        verify(kafkaTemplate).send(anyString(), anyString(), captor.capture());
        PaymentApprovedEvent publishedEvent = captor.getValue();
        assertThat(publishedEvent.getPaymentId()).isEqualTo(paymentId);
        assertThat(publishedEvent.getEventType()).isEqualTo("PAYMENT_APPROVED");
        assertThat(publishedEvent.getEventId()).isNotNull();           // UUID 자동 생성
        assertThat(publishedEvent.getOccurredAt()).isNotNull();
        assertThat(publishedEvent.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("TossPayments API 실패 시 트랜잭션 롤백되어 Kafka 이벤트 미발행")
    void PG_실패_시_이벤트_미발행() {
        // given: TossPayments 실패 응답 스텁
        wireMockServer.stubFor(post(urlEqualTo("/v1/payments/confirm"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                                "code": "INVALID_PAYMENT",
                                "message": "결제 정보가 올바르지 않습니다."
                            }
                        """)
                )
        );

        ConfirmRequest request = new ConfirmRequest(PAYMENT_KEY, orderId.toString(), AMOUNT);

        // when
        assertThatThrownBy(() -> paymentService.confirmPayment(request))
                .isInstanceOf(PaymentException.class);

        // then — 검증 3: 트랜잭션 롤백 시 kafkaTemplate.send() 호출 없음
        verifyNoInteractions(kafkaTemplate);
    }
}
