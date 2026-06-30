package com.agentpluginhub.publish;

// 同一 (package, version) 已 PUBLISHED,不可覆盖(不可变发布)
public class DuplicatePublishException extends RuntimeException {

    public DuplicatePublishException(String message) {
        super(message);
    }
}
