package com.giving.auth;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.AlgorithmMismatchException;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 客户端JWT工具
 */
public class ClientJwtUtil {
    private static final String CLIENT_ISSUER = "GAME_CLIENT";

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
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            DecodedJWT decodedJWT = JWT.require(algorithm)
                    .withIssuer(CLIENT_ISSUER)
                    .build()
                    .verify(token);
            return parsePayload(decodedJWT.getPayload());
        } catch (AlgorithmMismatchException e) {
            throw new ClientAuthException("JWT算法不支持");
        } catch (SignatureVerificationException e) {
            throw new ClientAuthException("JWT签名无效");
        } catch (TokenExpiredException e) {
            throw new ClientAuthException("JWT已过期");
        } catch (JWTDecodeException e) {
            throw new ClientAuthException("JWT格式错误");
        } catch (JWTVerificationException e) {
            throw new ClientAuthException("JWT校验失败");
        }
    }

    /**
     * 解析JWT payload
     * @param payload Base64URL payload
     * @return JSON对象
     */
    private static JSONObject parsePayload(String payload) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(payload);
            return JSON.parseObject(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ClientAuthException("JWT内容解析失败");
        }
    }
}
