package com.agentpluginhub.publish;

// 发布校验失败;code 供前端/CLI 区分,message 为 actionable 反馈
public class ValidationException extends RuntimeException {

    private final String code;

    public ValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
