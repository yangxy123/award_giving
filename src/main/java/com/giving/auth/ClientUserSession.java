package com.giving.auth;

import lombok.Data;

/**
 * 客户端JWT请求上下文
 */
@Data
public class ClientUserSession {

    private String userId;

    private Integer roomMasterId;

    private String roomMasterTitle;

    private String operator;
}
