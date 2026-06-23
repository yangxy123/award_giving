package com.giving.auth;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.giving.base.resp.ApiResp;
import com.giving.entity.RoomMasterEntity;
import com.giving.entity.UserEntity;
import com.giving.mapper.RoomMasterMapper;
import com.giving.mapper.UserMapper;
import com.giving.util.TableNameUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * 客户端投注Token认证过滤器
 */
@Component
public class ClientAuthFilter extends OncePerRequestFilter {
    private static final String JWT_COOKIE_NAME = "JWT";
    private static final String URL_COOKIE_MARK = "1.1.1";
    private static final String KICKED_TOKEN = "invalid-token-for-kicking-out-player";

    @Value("${client.jwt.secret:GAME_CLIENT}")
    private String clientJwtSecret;

    @Value("${client.auth.enabled:true}")
    private boolean clientAuthEnabled;

    @Autowired
    private RoomMasterMapper roomMasterMapper;
    @Autowired
    private UserMapper userMapper;

    /**
     * 执行客户端JWT认证
     * @param request HTTP请求
     * @param response HTTP响应
     * @param filterChain 过滤器链
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!clientAuthEnabled || shouldSkip(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = extractToken(request);
            JSONObject payload = ClientJwtUtil.verifyHs256(token, clientJwtSecret);
            ClientUserSession session = buildSession(token, payload);
            ClientUserSessionHolder.set(session);
            filterChain.doFilter(request, response);
        } catch (ClientAuthException e) {
            writeJwtError(response, e.getMessage());
        } finally {
            ClientUserSessionHolder.clear();
        }
    }

    /**
     * 判断当前请求是否跳过认证
     * @param request HTTP请求
     * @return 是否跳过
     */
    private boolean shouldSkip(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = normalizePath(request);
        return !("/game/order".equals(path) || path.startsWith("/game/order/"));
    }

    /**
     * 从请求中读取JWT
     * @param request HTTP请求
     * @return JWT
     */
    private String extractToken(HttpServletRequest request) {
        String pathToken = extractPathToken(request);
        if (StringUtils.hasText(pathToken) && !URL_COOKIE_MARK.equals(pathToken)) {
            return pathToken;
        }

        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (JWT_COOKIE_NAME.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                    return cookie.getValue();
                }
            }
        }

        String cookieHeaderToken = extractCookieHeaderToken(request);
        if (StringUtils.hasText(cookieHeaderToken)) {
            return cookieHeaderToken;
        }

        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization)) {
            if (authorization.startsWith("Bearer ")) {
                return authorization.substring("Bearer ".length());
            }
            return authorization;
        }

        throw new ClientAuthException("缺少JWT");
    }

    /**
     * 从Cookie请求头中读取JWT
     * @param request HTTP请求
     * @return Cookie请求头中的JWT
     */
    private String extractCookieHeaderToken(HttpServletRequest request) {
        String cookieHeader = request.getHeader("Cookie");
        if (!StringUtils.hasText(cookieHeader)) {
            cookieHeader = request.getHeader("cookie");
        }
        return extractCookieToken(cookieHeader);
    }

    /**
     * 从Cookie格式内容中读取JWT
     * @param cookieText Cookie格式内容
     * @return JWT
     */
    private String extractCookieToken(String cookieText) {
        if (!StringUtils.hasText(cookieText)) {
            return null;
        }
        String[] cookies = cookieText.split(";");
        for (String cookie : cookies) {
            String[] pair = cookie.trim().split("=", 2);
            if (pair.length == 2 && JWT_COOKIE_NAME.equals(pair[0].trim()) && StringUtils.hasText(pair[1])) {
                return decodeToken(pair[1].trim(), "Cookie JWT解析失败");
            }
        }
        return null;
    }

    /**
     * 从URL路径中读取可选JWT
     * @param request HTTP请求
     * @return URL中的JWT
     */
    private String extractPathToken(HttpServletRequest request) {
        String path = normalizePath(request);
        String prefix = "/game/order/";
        if (!path.startsWith(prefix)) {
            return null;
        }
        String token = path.substring(prefix.length());
        return decodeToken(token, "URL JWT解析失败");
    }

    /**
     * 解码可能被URL编码的JWT
     * @param token JWT
     * @param errorMessage 失败提示
     * @return 解码后的JWT
     */
    private String decodeToken(String token, String errorMessage) {
        try {
            return URLDecoder.decode(token, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new ClientAuthException(errorMessage);
        }
    }

    /**
     * 创建客户端请求上下文
     * @param token JWT
     * @param payload JWT payload
     * @return 客户端请求上下文
     */
    private ClientUserSession buildSession(String token, JSONObject payload) {
        String userId = requireString(payload, "user_id");
        Integer roomMasterId = requireInteger(payload, "room_master_id");
        String payloadTitle = requireString(payload, "room_master_title");
        String operator = payload.getString("operator");

        RoomMasterEntity roomMaster = roomMasterMapper.selectOne(new LambdaQueryWrapper<RoomMasterEntity>()
                .eq(RoomMasterEntity::getMasterId, roomMasterId));
        if (roomMaster == null) {
            throw new ClientAuthException("roomMasterId not found");
        }
        String title = TableNameUtil.safePrefix(roomMaster.getTitle());
        if (!title.equals(payloadTitle)) {
            throw new ClientAuthException("roomMasterTitle mismatch");
        }
        validateRoomMasterAccess(roomMaster, operator);

        UserEntity user = userMapper.selectByUserId(title, userId);
        if (user == null) {
            throw new ClientAuthException("user_id not found");
        }
        if (KICKED_TOKEN.equals(user.getLoginToken())) {
            throw new ClientAuthException("Your account has been logged out. Contact customer support for more info.");
        }
        if (!token.equals(user.getLoginToken())) {
            throw new ClientAuthException("user_id was logged in by other device");
        }

        ClientUserSession session = new ClientUserSession();
        session.setUserId(userId);
        session.setRoomMasterId(roomMasterId);
        session.setRoomMasterTitle(title);
        session.setOperator(operator == null ? "" : operator);
        return session;
    }

    /**
     * 校验厅主和operator是否可用
     * @param roomMaster 厅主
     * @param operator operator代码
     */
    private void validateRoomMasterAccess(RoomMasterEntity roomMaster, String operator) {
        if (!Integer.valueOf(1).equals(roomMaster.getIsActive())) {
            throw new ClientAuthException("roomMasterId is not active");
        }

        JSONObject miscInfo = parseMiscInfo(roomMaster.getMiscInfo());
        if (miscInfo.containsKey("enable") && !isEnabled(miscInfo.get("enable"))) {
            throw new ClientAuthException("roomMasterId is disable");
        }

        if (isMultiOperator(roomMaster) && StringUtils.hasText(operator)) {
            JSONObject multiOperator = miscInfo.getJSONObject("multiOperator");
            JSONObject operatorInfo = multiOperator == null ? null : multiOperator.getJSONObject(operator);
            if (operatorInfo != null) {
                if (operatorInfo.containsKey("is_active") && !isEnabled(operatorInfo.get("is_active"))) {
                    throw new ClientAuthException("operator is not active");
                }
                if (operatorInfo.containsKey("enable") && !isEnabled(operatorInfo.get("enable"))) {
                    throw new ClientAuthException("operator is disable");
                }
            }
        }
    }

    /**
     * 解析厅主miscInfo
     * @param miscInfo misc_info JSON
     * @return JSON对象
     */
    private JSONObject parseMiscInfo(String miscInfo) {
        if (!StringUtils.hasText(miscInfo)) {
            throw new ClientAuthException("miscInfo error");
        }
        try {
            return JSON.parseObject(miscInfo);
        } catch (Exception e) {
            throw new ClientAuthException("miscInfo error");
        }
    }

    /**
     * 判断厅主是否多operator登录
     * @param roomMaster 厅主
     * @return 是否多operator
     */
    private boolean isMultiOperator(RoomMasterEntity roomMaster) {
        return Integer.valueOf(1).equals(roomMaster.getBusinessType())
                && Integer.valueOf(1).equals(roomMaster.getLoginType());
    }

    /**
     * 判断配置值是否启用
     * @param value 配置值
     * @return 是否启用
     */
    private boolean isEnabled(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() == 1;
        }
        String text = String.valueOf(value);
        return "1".equals(text) || "true".equalsIgnoreCase(text);
    }

    /**
     * 必填字符串字段
     * @param payload JWT payload
     * @param key 字段名
     * @return 字段值
     */
    private String requireString(JSONObject payload, String key) {
        String value = payload.getString(key);
        if (!StringUtils.hasText(value)) {
            throw new ClientAuthException("JWT缺少" + key);
        }
        return value;
    }

    /**
     * 必填整数字段
     * @param payload JWT payload
     * @param key 字段名
     * @return 字段值
     */
    private Integer requireInteger(JSONObject payload, String key) {
        Integer value = payload.getInteger(key);
        if (value == null) {
            throw new ClientAuthException("JWT缺少" + key);
        }
        return value;
    }

    /**
     * 标准化请求路径
     * @param request HTTP请求
     * @return 去除contextPath后的路径
     */
    private String normalizePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    /**
     * 写出JWT错误响应
     * @param response HTTP响应
     * @param message 错误消息
     * @throws IOException 写出异常
     */
    private void writeJwtError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSON.toJSONString(ApiResp.jwtError(message)));
    }
}
