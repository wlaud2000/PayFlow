package com.project.payflow.domain.auth.exception;

import com.project.payflow.global.apiPayload.exception.CustomException;

public class AuthException extends CustomException{
    public AuthException(AuthErrorCode errorCode){
        super(errorCode);
    }
}