package com.project.payflow.domain.product.service;

import com.project.payflow.domain.product.dto.ProductList;
import com.project.payflow.domain.product.dto.ProductRequest;
import com.project.payflow.domain.product.dto.ProductResponse;
import com.project.payflow.domain.product.entity.Product;
import com.project.payflow.domain.product.exception.ProductErrorCode;
import com.project.payflow.domain.product.exception.ProductException;
import com.project.payflow.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService{

    private final ProductRepository productRepository;
    private final RedisStockService redisStockService;

    @Transactional
    public ProductResponse create(ProductRequest request){
        Product product = Product.builder()
                .name(request.name())
                .price(request.price())
                .stock(request.stock())
                .build();
        Product saved = productRepository.save(product);

        try {
            redisStockService.initStock(saved.getId(), saved.getStock());
        } catch (RedisConnectionFailureException e) {
            log.warn("[Redis 장애] 상품 등록 시 재고 초기화 실패 (productId={}). 첫 결제 시 DB에서 자동 재초기화됨.", saved.getId());
        }

        return ProductResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ProductList findAll(){
        return ProductList.from(
                productRepository.findAll().stream()
                        .map(ProductResponse::from)
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long productId){
        return productRepository.findById(productId)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }

    @Transactional
    public void decreaseStock(Long productId, int quantity){
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND));
        product.decreaseStock(quantity);
    }

    @Transactional
    public void increaseStock(Long productId, int quantity){
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND));
        product.increaseStock(quantity);
    }
}
