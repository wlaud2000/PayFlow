package com.project.payflow.global.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException{
        String traceId = generateTraceId();
        try {
            MDC.put(TRACE_ID_KEY, traceId);
            response.addHeader(TRACE_ID_HEADER, traceId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
            // [중요] 서블릿 컨테이너는 스레드 풀을 재사용함.
            // clear() 없으면 다음 요청에 이전 traceId가 오염되어 추적 불가.
        }
    }

    // [설계 결정] traceId: UUID 앞 8자리 사용
    // 전체 UUID(36자)는 로그에서 가독성이 떨어짐.
    // 8자리도 충돌 확률이 실질적으로 무시 가능한 수준 (16^8 = 약 43억 가지).
    // 운영 환경에서 분산 추적이 필요하다면 OpenTelemetry TraceId(32자) 전환 고려.
    private String generateTraceId(){
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
