package org.thornex.musicparty.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiRequestException.class)
    public ResponseEntity<Object> handleApiRequestException(ApiRequestException ex) {
        // For failures related to external APIs, return 502 Bad Gateway
        Map<String, Object> body = Map.of(
                "message", sanitizeMessage(ex.getMessage()),
                "status", HttpStatus.BAD_GATEWAY.value()
        );
        return new ResponseEntity<>(body, HttpStatus.BAD_GATEWAY);
    }

    /** L5：外部 API 错误原文可能携带敏感信息（cookie/token 等），统一脱敏：过滤 + 截断 */
    private String sanitizeMessage(String msg) {
        if (msg == null || msg.isBlank()) return "外部服务错误";
        String m = msg.replaceAll("(?i)(cookie|token|authorization|password|session|accesskey)[=:][^\\s,;\"']{4,}", "$1=***");
        return m.length() > 200 ? m.substring(0, 200) + "..." : m;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, Object> body = Map.of(
                "message", ex.getReason() != null ? ex.getReason() : "请求失败",
                "status", ex.getStatusCode().value()
        );
        return new ResponseEntity<>(body, ex.getStatusCode());
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Object> handleMethodNotSupported(org.springframework.web.HttpRequestMethodNotSupportedException ex) {
        Map<String, Object> body = Map.of(
                "message", "接口不存在或方法不支持",
                "status", HttpStatus.METHOD_NOT_ALLOWED.value()
        );
        return new ResponseEntity<>(body, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<Object> handleNoResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        Map<String, Object> body = Map.of(
                "message", "接口不存在",
                "status", 404
        );
        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGenericException(Exception ex) {
        // For all other unexpected errors, return 500 Internal Server Error
        Map<String, Object> body = Map.of(
                "message", "An unexpected internal server error occurred.",
                "error", ex.getClass().getSimpleName(),
                "status", HttpStatus.INTERNAL_SERVER_ERROR.value()
        );
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}