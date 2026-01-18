package com.giving.base.constans;

/**
 * 响应码常量
 * @author zzby
 * @version 创建时间： 2026/1/18 下午4:06
 */
public interface ResCodeContants {
    /**
     * 操作成功
     */
    public static final String SUCESS = "000000";


    /**
     * 触发规则
     */
    public static final String RULE = "000107";

    /**
     * 参数错误
     */
    public static final String PARAM_ERROR = "000101";

    /**
     * 系统错误
     */
    public static final String SYS_ERROR = "000102";

    /**
     * 业务错误
     */
    public static final String BUS_ERROR = "000103";

    /**
     * 权限错误
     */
    public static final String AUTH_ERROR = "000104";

    /**
     * TOKEN无效
     */
    public static final String JWT_ERROR = "000105";

    /**
     * 验证码错误
     */
    public static final String CODE_ERROR = "000106";
}

