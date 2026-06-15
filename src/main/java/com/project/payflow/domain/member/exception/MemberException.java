package com.project.payflow.domain.member.exception;

import com.project.payflow.global.apiPayload.exception.CustomException;

public class MemberException extends CustomException{
    public MemberException(MemberErrorCode errorCode){
        super(errorCode);
    }
}