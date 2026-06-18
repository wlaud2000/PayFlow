package com.project.payflow.domain.product.entity;

import com.project.payflow.domain.product.exception.ProductErrorCode;
import com.project.payflow.domain.product.exception.ProductException;
import com.project.payflow.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "product")
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Long price;

    @Column(nullable = false)
    private Integer stock;

    @Version
    private Long version;

    public void decreaseStock(int quantity) {
        if (this.stock < quantity) {
            throw new ProductException(ProductErrorCode.INSUFFICIENT_STOCK);
        }
        this.stock -= quantity;
    }

    public void increaseStock(int quantity) {
        this.stock += quantity;
    }
}