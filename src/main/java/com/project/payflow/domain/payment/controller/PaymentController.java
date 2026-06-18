package com.project.payflow.domain.payment.controller;

import com.project.payflow.domain.payment.dto.*;
import com.project.payflow.domain.payment.service.PaymentService;
import com.project.payflow.global.apiPayload.CustomResponse;
import com.project.payflow.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController{

    private final PaymentService paymentService;

    @PostMapping("/request")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomResponse<PaymentRequestResponse> requestPayment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid PaymentRequestDto request){
        return CustomResponse.onSuccess(HttpStatus.CREATED, "결제 요청이 생성되었습니다",
                paymentService.requestPayment(userDetails.getMember().getId(), request));
    }

    @PostMapping("/confirm")
    public CustomResponse<PaymentResponse> confirmPayment(@RequestBody @Valid ConfirmRequest request){
        return CustomResponse.onSuccess("결제 승인이 완료되었습니다", paymentService.confirmPayment(request));
    }

    @PostMapping("/{paymentId}/cancel")
    public CustomResponse<PaymentResponse> cancelPayment(@PathVariable Long paymentId){
        return CustomResponse.onSuccess("결제가 취소되었습니다", paymentService.cancelPayment(paymentId));
    }

    @PostMapping("/{paymentId}/refund")
    public CustomResponse<PaymentResponse> refundPayment(
            @PathVariable Long paymentId,
            @RequestBody @Valid RefundRequest request){
        return CustomResponse.onSuccess("환불이 완료되었습니다", paymentService.refundPayment(paymentId, request));
    }

    @GetMapping("/{paymentId}")
    public CustomResponse<PaymentResponse> getPayment(@PathVariable Long paymentId){
        return CustomResponse.success(paymentService.getPayment(paymentId));
    }
}