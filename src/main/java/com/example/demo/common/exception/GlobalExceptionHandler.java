package com.example.demo.common.exception;

import com.example.demo.common.api.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.exceptions.Neo4jException;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.neo4j.driver.exceptions.SessionExpiredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        return ResponseEntity.status(resolveStatus(exception.getCode()))
            .body(ApiResponse.failure(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(
        MethodArgumentNotValidException exception
    ) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null ? "invalid request" : fieldError.getDefaultMessage();
        return ResponseEntity.badRequest().body(ApiResponse.failure(400, message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
        ConstraintViolationException exception
    ) {
        return ResponseEntity.badRequest().body(ApiResponse.failure(400, exception.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolationException(
        DataIntegrityViolationException exception
    ) {
        log.error("Database constraint violation", exception);
        return ResponseEntity.internalServerError().body(ApiResponse.failure(500, "数据操作异常，请检查输入后重试"));
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNeo4jServiceUnavailable(ServiceUnavailableException exception) {
        log.error("Neo4j service unavailable: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ApiResponse.failure(503, "知识图谱服务暂时不可用，请稍后重试"));
    }

    @ExceptionHandler(SessionExpiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleNeo4jSessionExpired(SessionExpiredException exception) {
        log.warn("Neo4j session expired: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ApiResponse.failure(503, "图谱会话已过期，请重试"));
    }

    @ExceptionHandler(ClientException.class)
    public ResponseEntity<ApiResponse<Void>> handleNeo4jClientException(ClientException exception) {
        String message = exception.getMessage();
        if (message != null && message.contains("Unable to acquire connection")) {
            log.error("Neo4j connection pool exhausted: {}", message);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.failure(503, "图谱数据库连接繁忙，请稍后重试"));
        }
        log.warn("Neo4j client error: {}", message);
        return ResponseEntity.badRequest()
            .body(ApiResponse.failure(400, "图谱查询失败，请检查查询参数"));
    }

    @ExceptionHandler(Neo4jException.class)
    public ResponseEntity<ApiResponse<Void>> handleNeo4jException(Neo4jException exception) {
        log.error("Neo4j error: {}", exception.getMessage(), exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ApiResponse.failure(503, "知识图谱服务异常，请稍后重试"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(413).body(ApiResponse.failure(413, "上传文件大小超出限制"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException exception) {
        log.warn("Resource not found: {}", exception.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.failure(404, "resource not found"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
        log.error("Unhandled server exception", exception);
        return ResponseEntity.internalServerError().body(ApiResponse.failure(500, "服务器内部错误，请稍后重试"));
    }

    private HttpStatus resolveStatus(int code) {
        return switch (code) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

}
