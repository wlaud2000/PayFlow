package com.project.payflow.domain.product.service;

import com.project.payflow.domain.product.entity.Product;
import com.project.payflow.domain.product.exception.ProductErrorCode;
import com.project.payflow.domain.product.exception.ProductException;
import com.project.payflow.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductStockService {

    private final ProductRepository productRepository;

    @Autowired
    @Lazy
    private ProductStockService self;

    private static final int MAX_RETRY = 3;

    public void decreaseWithOptimisticLock(Long productId, int quantity) {
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            try {
                self.doDecrease(productId, quantity);
                return;
            } catch (ObjectOptimisticLockingFailureException e) {
                if (attempt == MAX_RETRY - 1) {
                    throw new ProductException(ProductErrorCode.STOCK_DECREASE_FAILED);
                } try {
                    Thread.sleep(50L << attempt); // 50ms -> 100ms, thundering herd 완화
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ProductException(ProductErrorCode.STOCK_DECREASE_FAILED);
                }
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void doDecrease(Long productId, int quantity) {
        Product product = productRepository.findByIdWithOptimisticLock(productId)
                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND));
        product.decreaseStock(quantity);
    }

    @Transactional
    public void increaseStock(Long productId, int quantity) {
        Product product = productRepository.findByIdWithOptimisticLock(productId)
                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND));
        product.increaseStock(quantity);
    }
}
