package com.giving.auth;

/**
 * 客户端请求上下文持有器
 */
public class ClientUserSessionHolder {
    private static final ThreadLocal<ClientUserSession> HOLDER = new ThreadLocal<>();

    private ClientUserSessionHolder() {
    }

    /**
     * 设置当前请求上下文
     * @param session 客户端会话
     */
    public static void set(ClientUserSession session) {
        HOLDER.set(session);
    }

    /**
     * 获取当前请求上下文
     * @return 客户端会话
     */
    public static ClientUserSession get() {
        return HOLDER.get();
    }

    /**
     * 获取当前请求上下文，不存在则抛出认证异常
     * @return 客户端会话
     */
    public static ClientUserSession getRequired() {
        ClientUserSession session = HOLDER.get();
        if (session == null) {
            throw new ClientAuthException("未通过客户端Token认证");
        }
        return session;
    }

    /**
     * 清理当前请求上下文
     */
    public static void clear() {
        HOLDER.remove();
    }
}
