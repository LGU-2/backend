package com.freshmarket.common.auth.jwt;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

/**
 * Refresh Token 저장소. 순수 Redis 저장소 — Member/Admin을 전혀 모른다. Redis 장애 시
 * DataAccessException을 그대로 던지며, DB 백업/폴백은 호출자(도메인 소유의 ~TokenService)의
 * 책임이다.
 *
 * (2026-08-19) opaque 토큰 전환(SEC-1-04 정리): 리프레시 토큰이 JWT일 때는 클라이언트가 보낸
 * 토큰 자체에서 role/id를 꺼낼 수 있어 "role:id → 토큰" 한 방향 키만으로 충분했다. opaque(무작위
 * 문자열)는 토큰만 봐서는 누구 건지 전혀 알 수 없어서, 실제 조회/회전은 "토큰(해시) → 소유자
 * 정보"인 기본 레코드로 처리한다. 그런데 로그아웃/재사용 의심 시 "이 회원의 현재 토큰을 찾아서
 * 지운다"처럼 반대 방향(회원 → 토큰) 조회도 여전히 필요해서, 기본 레코드와 별개로 role:id →
 * 현재 토큰 해시를 가리키는 보조 인덱스를 하나 더 둔다. 보조 인덱스는 원자적 CAS의 대상이
 * 아니라 조회 편의를 위한 포인터일 뿐이다 — 실제 회전(rotate)의 원자성은 기본 레코드에 대한
 * Lua 스크립트가 보장한다.
 */
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "refreshToken:";
    private static final String ACTIVE_KEY_PREFIX = "activeRefreshToken:";
    private static final String FIELD_DELIMITER = "\\|";

    private static final RedisScript<String> ROTATE_SCRIPT = loadRotateScript();

    private static RedisScript<String> loadRotateScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/refresh_token_rotate.lua"));
        script.setResultType(String.class);
        return script;
    }

    private final StringRedisTemplate redisTemplate;

    /** 로그인/온보딩 발급 시 새 리프레시 토큰을 저장한다. */
    public void save(String refreshToken, Long memberId, String role, TokenType type, boolean remember, Duration ttl) {
        String hash = TokenHasher.sha256(refreshToken);
        redisTemplate.opsForValue().set(primaryKey(hash), serialize(memberId, role, type, remember), ttl);
        redisTemplate.opsForValue().set(activeKey(role, memberId), hash, ttl);
    }

    /** @return 저장된 값이 있으면 그 소유자 정보. 없거나 만료됐으면(=우리가 발급한 적 없으면) empty. */
    public Optional<RefreshTokenData> find(String refreshToken) {
        String value = redisTemplate.opsForValue().get(primaryKey(TokenHasher.sha256(refreshToken)));
        return value == null ? Optional.empty() : Optional.of(parse(value));
    }

    /**
     * 원자적 회전(로테이션). oldRefreshToken 자리의 레코드를 newRefreshToken 자리로 옮기고
     * old는 지운다(기본 레코드는 Lua로 원자적으로 처리). 보조 인덱스(회원 → 현재 토큰)는 그
     * 원자적 연산의 대상이 아니라 바로 이어서 갱신한다 — 동시에 같은 값을 두고 경쟁하는 다른
     * 요청이 없어서(기본 레코드 CAS가 이미 승자를 하나로 정한 뒤라) 원자성이 없어도 안전하다.
     * @return 회전에 성공했으면 그 소유자 정보(=재발급 계속 진행), old 토큰이 없었으면(이미 한 번
     *         쓰였거나 우리 게 아니면) empty(=재사용 의심).
     */
    public Optional<RefreshTokenData> compareAndRotate(String oldRefreshToken, String newRefreshToken, Duration ttl) {
        String oldHash = TokenHasher.sha256(oldRefreshToken);
        String newHash = TokenHasher.sha256(newRefreshToken);

        String value = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(primaryKey(oldHash), primaryKey(newHash)),
                String.valueOf(ttl.toMillis())
        );
        if (value == null) {
            return Optional.empty();
        }

        RefreshTokenData data = parse(value);
        redisTemplate.opsForValue().set(activeKey(data.role(), data.memberId()), newHash, ttl);
        return Optional.of(data);
    }

    /** 이 회원의 현재 리프레시 토큰을 찾아서 지운다(로그아웃/탈퇴/웹훅 공용 — 예전 인터페이스와 동일). */
    public void delete(String role, Long id) {
        String activeKey = activeKey(role, id);
        String hash = redisTemplate.opsForValue().get(activeKey);
        if (hash != null) {
            redisTemplate.delete(primaryKey(hash));
        }
        redisTemplate.delete(activeKey);
    }

    private String primaryKey(String tokenHash) {
        return KEY_PREFIX + tokenHash;
    }

    private String activeKey(String role, Long id) {
        return ACTIVE_KEY_PREFIX + role + ":" + id;
    }

    private String serialize(Long memberId, String role, TokenType type, boolean remember) {
        return memberId + "|" + role + "|" + type.name() + "|" + remember;
    }

    private RefreshTokenData parse(String raw) {
        String[] parts = raw.split(FIELD_DELIMITER, 4);
        return new RefreshTokenData(Long.valueOf(parts[0]), parts[1], TokenType.valueOf(parts[2]), Boolean.parseBoolean(parts[3]));
    }

    public record RefreshTokenData(Long memberId, String role, TokenType type, boolean remember) {
    }
}
