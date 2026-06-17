package com.project.payflow.domain.order.exception;

import com.project.payflow.global.apiPayload.exception.CustomException;

public class OrderException extends CustomException{
    public OrderException(OrderErrorCode errorCode){
        super(errorCode);
    }
}