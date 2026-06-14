package com.project.payflow.global.apiPayload.code;

import com.project.payflow.global.apiPayload.CustomResponse;
import org.springframework.http.HttpStatus;

public interface BaseErrorCode {
    HttpStatus getHttpStatus();  // HTTP 상태 코드 반환
    String getCode();             // 커스텀 에러 코드 반환
    String getMessage();          // 에러 메시지 반환

    default CustomResponse<Void> getErrorResponse() {
        return CustomResponse.onFailure(getCode(), getMessage());
    }
}
