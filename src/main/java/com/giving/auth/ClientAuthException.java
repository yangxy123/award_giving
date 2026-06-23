package com.giving.auth;

/**
 * 客户端认证异常
 */
public class ClientAuthException extends RuntimeException {

    public ClientAuthException(String message) {
        super(message);
    }
}
