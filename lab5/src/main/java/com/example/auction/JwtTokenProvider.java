package com.example.auction;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.util.*;

@Component
public class JwtTokenProvider {
    private final SecretKey key;
    private final long accessTtl, refreshTtl;
    public JwtTokenProvider(@Value("${jwt.secret}") String secret,
            @Value("${jwt.accessExpiration}") long accessTtl,
            @Value("${jwt.refreshExpiration}") long refreshTtl) {
        byte[] bytes=Decoders.BASE64.decode(secret);
        if(bytes.length<32)throw new IllegalArgumentException("JWT_SECRET must contain at least 32 random bytes (Base64)");
        this.key=Keys.hmacShaKeyFor(bytes);
        this.accessTtl=accessTtl;this.refreshTtl=refreshTtl;
    }
    public String issue(String username,String type,String jti,String sessionId) {
        long now=System.currentTimeMillis();
        return Jwts.builder().subject(username).id(jti).claim("type",type)
            .claim("sid",sessionId).issuedAt(new Date(now))
            .expiration(new Date(now+("access".equals(type)?accessTtl:refreshTtl)))
            .signWith(key,Jwts.SIG.HS256).compact();
    }
    public Claims parse(String token,String expectedType) {
        Claims c=Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        if(!expectedType.equals(c.get("type",String.class)) || c.getSubject()==null || c.getSubject().isBlank()
           || c.getId()==null || c.get("sid",String.class)==null || c.getExpiration()==null)
            throw new JwtException("Invalid token claims or token purpose");
        return c;
    }
    public long refreshTtl(){return refreshTtl;}
}
