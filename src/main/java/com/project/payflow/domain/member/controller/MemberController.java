package com.project.payflow.domain.member.controller;

import com.project.payflow.domain.member.dto.MemberResponse;
import com.project.payflow.domain.member.entity.Member;
import com.project.payflow.global.apiPayload.CustomResponse;
import com.project.payflow.global.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members")
public class MemberController{

    @GetMapping("/me")
    public CustomResponse<MemberResponse> getMyInfo(@AuthenticationPrincipal CustomUserDetails userDetails){
        Member member = userDetails.getMember();
        return CustomResponse.success(new MemberResponse(member.getId(), member.getEmail(), member.getName()));
    }
}
