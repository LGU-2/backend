package com.freshmarket.common.auth.jwt;

import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

// (2026-08-18 11:05) com.example.freshdemo.common.auth.jwt에서 이식, 로직 무변경.
/**
 * Refresh Token 저장소. key = "refreshToken:{role}:{id}". 순수 Redis 저장소 — Member/Admin을
 * 전혀 모른다. Redis 장애 시 DataAccessException을 그대로 던지며, DB 백업/폴백은 호출자(도메인
 * 소유의 ~TokenService)의 책임이다.
 *
 * TODO(opaque refresh token 전환, JwtTokenProvider.createRefreshToken() 주석 참고): 토큰이
 * opaque해지면 클라이언트가 보낸 토큰만 봐서는 role/id를 알 수 없다 — 지금처럼 role:id로 키를
 * 만들어 조회하는 게 아니라, 토큰(해시)을 키로 저장해 값에서 role/id를 꺼내는 구조로 뒤집어야
 * 한다. 커밋 이후 opaque 도입 시 같이 고친다.
 */
@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "refreshToken:";

    private static final RedisScript<Long> COMPARE_AND_SAVE_SCRIPT = loadCompareAndSaveScript();

    private static RedisScript<Long> loadCompareAndSaveScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/refresh_token_cas.lua"));
        script.setResultType(Long.class);
        return script;
    }

    private final StringRedisTemplate redisTemplate;

    public void save(String role, Long id, String refreshToken, Duration ttl) {
        redisTemplate.opsForValue().set(key(role, id), TokenHasher.sha256(refreshToken), ttl);
    }

    public void delete(String role, Long id) {
        redisTemplate.delete(key(role, id));
    }

    /** @return true면 회전 성공(원자적 compare-and-set), false면 저장된 값과 불일치(재사용 의심). */
    public boolean compareAndSave(String role, Long id, String oldRefreshToken, String newRefreshToken, Duration ttl) {
        String oldHash = TokenHasher.sha256(oldRefreshToken);
        String newHash = TokenHasher.sha256(newRefreshToken);

        Long result = redisTemplate.execute(
                COMPARE_AND_SAVE_SCRIPT,
                List.of(key(role, id)),
                oldHash, newHash, String.valueOf(ttl.toMillis())
        );
        return result != null && result == 1L;
    }

    private String key(String role, Long id) {
        return KEY_PREFIX + role + ":" + id;
    }
}
