package com.agentpluginhub.review;

// 审核状态机非法跃迁(如对终态再审批)
public class IllegalTransitionException extends RuntimeException {

    public IllegalTransitionException(String message) {
        super(message);
    }
}
