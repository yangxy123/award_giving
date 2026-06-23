package com.giving.auth;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 客户端JWT工具
 */
public class ClientJwtUtil {
    private static final String HMAC_SHA256 = "HmacSHA256";

    private ClientJwtUtil() {
    }

    /**
     * 校验HS256 JWT并解析payload
     * @param token JWT
     * @param secret 密钥
     * @return payload
     */
    public static JSONObject verifyHs256(String token, String secret) {
        if (!StringUtils.hasText(token)) {
            throw new ClientAuthException("JWT不能为空");
        }
        if (!StringUtils.hasText(secret)) {
            throw new ClientAuthException("CLIENT_API_SECRET未配置");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new ClientAuthException("JWT格式错误");
        }

        JSONObject header = parsePart(parts[0]);
        if (!"HS256".equals(header.getString("alg"))) {
            throw new ClientAuthException("JWT算法不支持");
        }

        String signingInput = parts[0] + "." + parts[1];
        String expected = sign(signingInput, secret);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new ClientAuthException("JWT签名无效");
        }

        JSONObject payload = parsePart(parts[1]);
        Number exp = payload.getObject("exp", Number.class);
        if (exp != null && System.currentTimeMillis() / 1000L >= exp.longValue()) {
            throw new ClientAuthException("JWT已过期");
        }
        return payload;
    }

    /**
     * 解析JWT分段
     * @param part Base64URL分段
     * @return JSON对象
     */
    private static JSONObject parsePart(String part) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(part);
            return JSON.parseObject(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ClientAuthException("JWT内容解析失败");
        }
    }

    /**
     * 生成HS256签名
     * @param content 签名内容
     * @param secret 密钥
     * @return Base64URL签名
     */
    private static String sign(String content, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] signature = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception e) {
            throw new ClientAuthException("JWT签名校验失败");
        }
    }
}
