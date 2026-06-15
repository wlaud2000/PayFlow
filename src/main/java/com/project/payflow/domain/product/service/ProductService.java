package com.project.payflow.domain.product.service;

import com.project.payflow.domain.product.dto.ProductList;
import com.project.payflow.domain.product.dto.ProductRequest;
import com.project.payflow.domain.product.dto.ProductResponse;
import com.project.payflow.domain.product.entity.Product;
import com.project.payflow.domain.product.exception.ProductErrorCode;
import com.project.payflow.domain.product.exception.ProductException;
import com.project.payflow.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = Product.builder()
                .name(request.name())
                .price(request.price())
                .stock(request.stock())
                .build();
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public ProductList findAll() {
        return ProductList.from(
                productRepository.findAll().stream()
                        .map(ProductResponse::from)
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long productId) {
        return productRepository.findById(productId)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }

    @Transactional
    public void decreaseStock(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND));
        product.decreaseStock(quantity);
    }

    @Transactional
    public void increaseStock(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND));
        product.increaseStock(quantity);
    }
}
