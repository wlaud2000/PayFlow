package com.project.payflow.domain.product.controller;

import com.project.payflow.domain.product.dto.ProductList;
import com.project.payflow.domain.product.dto.ProductRequest;
import com.project.payflow.domain.product.dto.ProductResponse;
import com.project.payflow.domain.product.service.ProductService;
import com.project.payflow.global.apiPayload.CustomResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomResponse<ProductResponse> create(@RequestBody @Valid ProductRequest request) {
        return CustomResponse.onSuccess(HttpStatus.CREATED, "상품이 등록되었습니다", productService.create(request));
    }

    @GetMapping
    public CustomResponse<ProductList> findAll() {
        return CustomResponse.success(productService.findAll());
    }

    @GetMapping("/{productId}")
    public CustomResponse<ProductResponse> findById(@PathVariable Long productId) {
        return CustomResponse.success(productService.findById(productId));
    }
}