package com.codemind.platform.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleIllegalArgumentException(
            IllegalArgumentException exception
    ) {
        return errorBody(
                400,
                "Bad Request",
                "BAD_REQUEST",
                exception.getMessage(),
                exception
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleDataIntegrityViolationException(
            DataIntegrityViolationException exception
    ) {
        String message = exception.getMessage();

        if (message != null && message.contains("uk_project_module_key")) {
            return errorBody(
                    400,
                    "Bad Request",
                    "DATA_INTEGRITY_VIOLATION",
                    "模块节点 Key 已存在，请更换节点 Key 或重新保存。",
                    exception
            );
        }

        return errorBody(
                400,
                "Bad Request",
                "DATA_INTEGRITY_VIOLATION",
                "数据唯一性或关联约束校验失败，请检查输入内容。",
                exception
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Map<String, Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException exception
    ) {
        return errorBody(
                413,
                "Payload Too Large",
                "MAX_UPLOAD_SIZE_EXCEEDED",
                "附件超过服务器允许的上传大小。请检查 spring.servlet.multipart.max-file-size 和 max-request-size 配置是否已在当前启动配置中生效。",
                exception
        );
    }

    @ExceptionHandler(MultipartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleMultipartException(
            MultipartException exception
    ) {
        return errorBody(
                400,
                "Bad Request",
                "MULTIPART_REQUEST_ERROR",
                "附件上传请求解析失败：" + safeMessage(exception),
                exception
        );
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleMissingServletRequestPartException(
            MissingServletRequestPartException exception
    ) {
        return errorBody(
                400,
                "Bad Request",
                "MISSING_MULTIPART_FILE",
                "附件上传缺少 file 字段，请确认前端 FormData 使用的字段名为 file。",
                exception
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException exception
    ) {
        return errorBody(
                400,
                "Bad Request",
                "MISSING_REQUEST_PARAMETER",
                "请求参数缺失：" + exception.getParameterName(),
                exception
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public Map<String, Object> handleHttpMediaTypeNotSupportedException(
            HttpMediaTypeNotSupportedException exception
    ) {
        return errorBody(
                415,
                "Unsupported Media Type",
                "UNSUPPORTED_MEDIA_TYPE",
                "请求 Content-Type 不受支持：" + safeMessage(exception),
                exception
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleUnexpectedException(
            Exception exception
    ) {
        exception.printStackTrace();

        return errorBody(
                500,
                "Internal Server Error",
                "INTERNAL_SERVER_ERROR",
                "服务器内部异常：" + exception.getClass().getSimpleName() + " - " + safeMessage(exception),
                exception
        );
    }

    private Map<String, Object> errorBody(
            int status,
            String error,
            String code,
            String message,
            Exception exception
    ) {
        Map<String, Object> body = new LinkedHashMap<>();

        body.put("timestamp", Instant.now().toString());
        body.put("status", status);
        body.put("error", error);
        body.put("code", code);
        body.put("message", message == null || message.isBlank() ? "未返回具体异常信息。" : message);
        body.put("exception", exception.getClass().getName());

        Throwable cause = exception.getCause();

        if (cause != null) {
            body.put("cause", cause.getClass().getName());
            body.put("causeMessage", safeMessage(cause));
        }

        return body;
    }

    private String safeMessage(
            Throwable throwable
    ) {
        if (
                throwable == null ||
                        throwable.getMessage() == null ||
                        throwable.getMessage().isBlank()
        ) {
            return "未返回具体异常信息。";
        }

        return throwable.getMessage();
    }
}