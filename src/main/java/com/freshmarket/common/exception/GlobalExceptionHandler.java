package com.freshmarket.common.exception;

import com.freshmarket.common.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/* 예외를 AIP-193 구조로 옮기는 유일한 지점이다.
   컨트롤러가 예외를 잡아 응답을 만들면 구조가 엔드포인트마다 갈린다. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /* 오류의 출처를 나타내는 값이다 (API-7-03 의 domain).
       환경마다 다르므로 소스에 박지 않고 설정에서 받는다 (FLX-4-01). */
    private final String domain;

    public GlobalExceptionHandler(@Value("${app.error.domain:api.freshmarket.com}") String domain) {
        this.domain = domain;
    }

    /* 인증 실패는 필터 체인에서 난다.
       SecurityConfig 가 HandlerExceptionResolver 로 넘겨 주므로 여기서 받는다. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleUnauthenticated(AuthenticationException e) {
        log.warn("unauthenticated. type={}", e.getClass().getSimpleName());
        return respond(CommonErrorCode.UNAUTHENTICATED, Map.of());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        // 4xx 는 호출자의 문제이므로 스택 트레이스를 남기지 않는다
        if (code.getHttpStatus().is5xxServerError()) {
            log.error("business error. reason={}, metadata={}", code.getReason(), e.getMetadata(), e);
        } else {
            log.warn("business error. reason={}, metadata={}", code.getReason(), e.getMetadata());
        }
        return respond(code, e.getMetadata(), code.getMessage());
    }

    /** 요청 본문 검증 실패. 어느 필드가 왜 틀렸는지를 metadata 로 돌려준다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBody(MethodArgumentNotValidException e) {
        Map<String, String> metadata = new LinkedHashMap<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            metadata.put(error.getField(), error.getDefaultMessage());
        }
        return respond(CommonErrorCode.INVALID_ARGUMENT, metadata);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        Map<String, String> metadata = new LinkedHashMap<>();
        e.getConstraintViolations().forEach(
                v -> metadata.put(v.getPropertyPath().toString(), v.getMessage()));
        return respond(CommonErrorCode.INVALID_ARGUMENT, metadata);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException e) {
        return respond(CommonErrorCode.INVALID_ARGUMENT, Map.of(e.getParameterName(), "필수 항목입니다."));
    }

    /* 경로 변수의 타입 변환 실패다.
       UUID 파싱 실패가 여기로 들어와 500 이 아니라 400 이 된다 (IDS-8-01). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return respond(CommonErrorCode.INVALID_IDENTIFIER, Map.of(e.getName(), "형식이 올바르지 않습니다."));
    }

    /* 권한이 없으면 리소스 존재 여부와 무관하게 403 이다 (API-7-05).
       존재 여부를 응답으로 구분해 주면 그 자체가 정보 노출이다.

       ExceptionTranslationFilter 는 chain.doFilter 전체를 감싸므로 필터와 컨트롤러 양쪽을 다 받는다.
       다만 컨트롤러에서 난 것은 DispatcherServlet 의 HandlerExceptionResolver 가 먼저 보고,
       이 핸들러가 처리하면 예외가 서블릿 밖으로 나가지 않아 필터까지 가지 않는다.
       URL 인가 실패는 SecurityConfig 가 이 핸들러로 넘겨 주므로 결국 여기로 모인다. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        log.warn("access denied. message={}", e.getMessage());
        return respond(CommonErrorCode.PERMISSION_DENIED, Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return respond(CommonErrorCode.METHOD_NOT_ALLOWED, Map.of("method", String.valueOf(e.getMethod())));
    }

    /* 유일 제약 위반과 낙관적 잠금 충돌은 재시도로 풀릴 수 있다 (CMP-4-03).
       제약 이름이나 SQL 은 응답에 넣지 않는다 (CMP-4-04). */
    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> handleConflict(Exception e) {
        log.warn("conflict. type={}", e.getClass().getSimpleName());
        return respond(CommonErrorCode.CONFLICT, Map.of());
    }

    /* 마지막 그물이다.
       예외 메시지를 응답에 싣지 않는다. 내부 구현과 경로가 그대로 새어 나간다 (CMP-4-04). */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("unexpected error", e);
        return respond(CommonErrorCode.INTERNAL, Map.of());
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode code, Map<String, String> metadata) {
        return respond(code, metadata, code.getMessage());
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode code, Map<String, String> metadata, String message) {
        return ResponseEntity.status(code.getHttpStatus())
                .body(ErrorResponse.of(code, domain, metadata, message));
    }
}
