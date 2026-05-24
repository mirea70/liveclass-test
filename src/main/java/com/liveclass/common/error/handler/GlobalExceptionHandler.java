package com.liveclass.common.error.handler;

import com.liveclass.common.error.exception.BusinessException;
import com.liveclass.common.error.exception.DomainException;
import com.liveclass.common.error.info.ErrorInfo;
import com.liveclass.common.error.info.SystemErrorInfo;
import com.liveclass.common.error.response.ErrorResponse;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(DomainException e, HttpServletRequest request) {
        ErrorInfo errorInfo = e.getErrorInfo();
        return ResponseEntity.status(errorInfo.getStatus())
                .body(new ErrorResponse(errorInfo.getCode(), errorInfo.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e, HttpServletRequest request) {
        ErrorInfo errorInfo = e.getErrorInfo();
        return ResponseEntity.status(errorInfo.getStatus())
                .body(new ErrorResponse(errorInfo.getCode(), errorInfo.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(BindException e, HttpServletRequest request) {
        Map<String, Object> details = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "유효하지 않은 값이 존재합니다.",
                        (a, b) -> a
                ));
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_REQUEST", "요청 정보 중 유효하지 않은 값이 있습니다.", request.getRequestURI(), details));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestHeader(MissingRequestHeaderException e, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        String.valueOf(HttpStatus.BAD_REQUEST.value()),
                        "필수 헤더가 누락되었습니다: " + e.getHeaderName(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(NoResourceFoundException e, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(String.valueOf(HttpStatus.NOT_FOUND.value()), "요청을 수신할 API가 존재하지 않습니다.", request.getRequestURI()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(String.valueOf(HttpStatus.BAD_REQUEST.value()), e.getMessage() != null ? e.getMessage() : "잘못된 요청 요소가 존재합니다.", request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {

        if (e.getRequiredType() != null && e.getRequiredType().isEnum()) {
            return handleInvalidEnumQueryParam(e, request);
        }

        // 일반적인 타입 변환 실패 (예: Long 파라미터에 문자열 전달)
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        "INVALID_REQUEST",
                        "요청 파라미터의 값이 올바르지 않습니다.",
                        request.getRequestURI()
                ));
    }

    private ResponseEntity<ErrorResponse> handleInvalidEnumQueryParam(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {

        String paramName = e.getName();
        Object invalidValue = e.getValue();
        Class<?> enumType = e.getRequiredType();

        String allowedValues = Arrays.stream(enumType.getEnumConstants())
                .map(Object::toString)
                .collect(Collectors.joining(", ", "[", "]"));

        String detailMessage = String.format("'%s' 값은 유효하지 않습니다. 허용되는 값: %s",
                invalidValue, allowedValues);

        Map<String, Object> details = Map.of(paramName, detailMessage);

        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        "INVALID_REQUEST",
                        "요청 정보 중 유효하지 않은 값이 있습니다.",
                        request.getRequestURI(),
                        details
                ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException e, HttpServletRequest request) {

        Throwable cause = e.getCause();
        if (cause instanceof InvalidFormatException invalidFormatException) {
            Class<?> targetType = invalidFormatException.getTargetType();
            if (targetType != null && targetType.isEnum()) {
                return handleInvalidEnumValue(invalidFormatException, request);
            }
        }

        // 일반적인 JSON 파싱 실패 (잘못된 JSON 형식 등)
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        "INVALID_REQUEST",
                        "요청 본문을 읽을 수 없습니다. JSON 형식을 확인해주세요.",
                        request.getRequestURI()
                ));
    }

    private ResponseEntity<ErrorResponse> handleInvalidEnumValue(
            InvalidFormatException e, HttpServletRequest request) {

        String fieldName = e.getPath().isEmpty()
                ? "unknown"
                : e.getPath().get(0).getFieldName();

        Object invalidValue = e.getValue();
        Class<?> enumType = e.getTargetType();

        String allowedValues = Arrays.stream(enumType.getEnumConstants())
                .map(Object::toString)
                .collect(Collectors.joining(", ", "[", "]"));

        String detailMessage = String.format("'%s' 값은 유효하지 않습니다. 허용되는 값: %s",
                invalidValue, allowedValues);

        Map<String, Object> details = Map.of(fieldName, detailMessage);

        return ResponseEntity.badRequest()
                .body(new ErrorResponse(
                        "INVALID_REQUEST",
                        "요청 정보 중 유효하지 않은 값이 있습니다.",
                        request.getRequestURI(),
                        details
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e, HttpServletRequest request) {
        log.error("Unexpected error", e);
        ErrorInfo errorInfo = SystemErrorInfo.UNKNOWN_ERROR;
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(errorInfo.getCode(), errorInfo.getMessage(), request.getRequestURI()));
    }
}
