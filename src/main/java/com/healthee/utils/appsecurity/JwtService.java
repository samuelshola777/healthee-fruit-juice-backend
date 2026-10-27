package com.healthee.utils.appsecurity;



import com.healthee.utils.TokenType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
@Log4j2
public class JwtService {

    @Value("${app.secret-key}")
    private String SECRET_KEY;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        T val = claimsResolver.apply(claims);
        if (log.isDebugEnabled()) {
            log.debug("[JWT] extractClaim END -> {}", (val == null ? "null" : val.toString()));
        }
        return val;
    }

    public String generateToken(String username, Map<String, Object> extraClaims) {
        return generateToken(new HashMap<>(extraClaims), username);
    }

    public String generateToken(Map<String, Object> extraClaims, String username) {
        if (log.isDebugEnabled()) {
            log.debug("[JWT] generateToken username={}", username);
        }
        try {
            Map<String, Object> mutable = new HashMap<>(extraClaims);
            mutable.put("type", TokenType.ACCESS_TOKEN.name());
            String jwt = Jwts.builder()
                    .setClaims(mutable)
                    .setSubject(username)
                    .setIssuedAt(new Date(System.currentTimeMillis()))
                    .setExpiration(new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24))
                    .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                    .compact();
            log.info("[JWT] Access token generated successfully, length={}", jwt.length());
            return jwt;
        } catch (Exception e) {
            log.error("[JWT] generateToken ERROR: {}", e.getMessage(), e);
            return "";
        }
    }

    public String generateRefreshToken(String username, Map<String, Object> extraClaims) {
        if (log.isDebugEnabled()) {
            log.debug("[JWT] generateRefreshToken username={}", username);
        }
        try {
            Map<String, Object> mutable = new HashMap<>(extraClaims);
            mutable.put("type", TokenType.REFRESH_TOKEN.name());
            String jwt = Jwts.builder()
                    .setClaims(mutable)
                    .setSubject(username)
                    .setIssuedAt(new Date(System.currentTimeMillis()))
                    .setExpiration(Date.from(LocalDate.now().plusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant()))
                    .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                    .compact();
            log.info("[JWT] Refresh token generated successfully, length={}", jwt.length());
            return jwt;
        } catch (Exception e) {
            log.error("[JWT] generateRefreshToken ERROR: {}", e.getMessage(), e);
            return "";
        }
    }

    public void validateToken(final String token) {
        try {
            Jwts.parserBuilder().setSigningKey(getSignInKey()).build();
            if (log.isDebugEnabled()) {
                log.debug("[JWT] validateToken: parser built successfully");
            }
        } catch (Exception e) {
            log.error("[JWT] validateToken ERROR: {}", e.getMessage(), e);
        }
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            boolean ok = (username != null && username.equals(userDetails.getUsername())) && !isTokenExpired(token);
            if (log.isDebugEnabled()) {
                log.debug("[JWT] isTokenValid(userDetails): {}", ok);
            }
            return ok;
        } catch (Exception e) {
            log.error("[JWT] isTokenValid(userDetails) ERROR: {}", e.getMessage(), e);
            return false;
        }
    }

    public boolean isTokenValid(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(getSignInKey()).build().parseClaimsJws(token);
            boolean ok = !isTokenExpired(token);
            if (log.isDebugEnabled()) {
                log.debug("[JWT] isTokenValid(token): {}", ok);
            }
            return ok;
        } catch (Exception e) {
            log.error("[JWT] isTokenValid(token) ERROR: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        boolean expired = extractExpiration(token).before(new Date());
        if (log.isDebugEnabled()) {
            log.debug("[JWT] isTokenExpired: {}", expired);
        }
        return expired;
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public Claims extractAllClaims(String token) {
        try {
            Claims c = Jwts.parserBuilder()
                    .setSigningKey(getSignInKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            if (log.isDebugEnabled()) {
                log.debug("[JWT] extractAllClaims OK (sub={})", c.getSubject());
            }
            return c;
        } catch (Exception e) {
            log.error("[JWT] extractAllClaims ERROR: {}", e.getMessage(), e);
            throw e;
        }
    }

    private Key getSignInKey() {
        try {
            byte[] keyBytes = Decoders.BASE64.decode(SECRET_KEY);
            Key key = Keys.hmacShaKeyFor(keyBytes);
            if (log.isDebugEnabled()) {
                log.debug("[JWT] getSignInKey OK (len={})", keyBytes.length);
            }
            return key;
        } catch (Exception e) {
            log.error("[JWT] getSignInKey ERROR: {}", e.getMessage(), e);
            throw e;
        }
    }

    public Claims parse(String token) {
        try {
            Claims body = Jwts.parserBuilder()
                    .setSigningKey(getSignInKey())
                    .setAllowedClockSkewSeconds(60)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            if (log.isDebugEnabled()) {
                log.debug("[JWT] parse OK (sub={})", body.getSubject());
            }
            return body;
        } catch (Exception e) {
            log.error("[JWT] parse ERROR: {}", e.getMessage(), e);
            throw e;
        }
    }
}
