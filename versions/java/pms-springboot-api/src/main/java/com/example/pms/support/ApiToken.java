package com.example.pms.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 与 Gin 版、FastAPI 版互通的 HMAC-SHA256 Token。
 * 格式：base64url(JSON(claims)) + "." + hex(HMAC_SHA256(API_SECRET, payload))。
 * 验签只覆盖收到的 payload 串，不重新序列化 claims，因此 JSON 字段顺序/空格无关紧要。
 */
@Component
public class ApiToken {

    private static final long TTL_SECONDS = 86400L;

    private final String secret;
    private final ObjectMapper mapper;

    public ApiToken(@Value("${pms.api.secret}") String secret, ObjectMapper mapper) {
        this.secret = secret;
        this.mapper = mapper;
    }

    public String issue(long uid, String username, boolean staff) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("uid", uid);
        claims.put("username", username);
        claims.put("staff", staff);
        claims.put("exp", Instant.now().getEpochSecond() + TTL_SECONDS);
        try {
            String payload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mapper.writeValueAsBytes(claims));
            return payload + "." + sign(payload);
        } catch (Exception e) {
            throw new IllegalStateException("签发 Token 失败", e);
        }
    }

    /** 返回 claims；签名错误、过期、格式非法时返回 null。 */
    public Map<String, Object> verify(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return null;
        }
        String payload = token.substring(0, dot);
        String signature = token.substring(dot + 1);
        if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(payload);
            Map<String, Object> claims = mapper.readValue(json, new TypeReference<>() {
            });
            Object exp = claims.get("exp");
            if (!(exp instanceof Number number) || number.longValue() <= Instant.now().getEpochSecond()) {
                return null;
            }
            return claims;
        } catch (Exception e) {
            return null;
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }
}
