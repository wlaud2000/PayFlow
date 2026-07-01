package com.project.payflow.global.init;

import com.project.payflow.domain.product.entity.Product;
import com.project.payflow.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("local")
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final ProductRepository productRepository;

    @Override
    public void run(ApplicationArguments args) {
        List<Product> products = List.of(
                Product.builder()
                        .name("스타벅스 x BC카드 아메리카노 50% 할인권")
                        .price(2500L)
                        .stock(100)
                        .campaignDescription("BC카드로 결제 시 스타벅스 아메리카노 50% 할인. 선착순 100명 한정.")
                        .build(),
                Product.builder()
                        .name("CGV x 삼성카드 주말 영화 2인권 (30% 할인)")
                        .price(14000L)
                        .stock(50)
                        .campaignDescription("삼성카드 결제 시 CGV 주말 영화 2인 관람권 30% 할인. 선착순 50매 한정.")
                        .build(),
                Product.builder()
                        .name("이마트 x 신한카드 5천원 즉시할인 쿠폰")
                        .price(5000L)
                        .stock(30)
                        .campaignDescription("신한카드 결제 시 이마트 5,000원 즉시할인. 3만원 이상 구매 시 적용, 선착순 30명 한정.")
                        .build(),
                Product.builder()
                        .name("올리브영 x 현대카드 한정 뷰티박스")
                        .price(19900L)
                        .stock(20)
                        .campaignDescription("현대카드 전용 올리브영 한정판 뷰티박스. 정가 대비 40% 할인, 선착순 20개 한정.")
                        .build()
        );

        for (Product product : products) {
            boolean exists = productRepository.findAll().stream()
                    .anyMatch(p -> p.getName().equals(product.getName()));
            if (!exists) {
                productRepository.save(product);
            }
        }
    }
}
