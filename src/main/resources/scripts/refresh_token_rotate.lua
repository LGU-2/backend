-- KEYS[1] = 옛 리프레시 토큰 키(refreshToken:{옛 토큰 해시})
-- KEYS[2] = 새 리프레시 토큰 키(refreshToken:{새 토큰 해시})
-- ARGV[1] = TTL(ms)
--
-- opaque 전환(2026-08-19) 이후: 리프레시 토큰 자체엔 아무 정보가 없어서(SEC-1-04, JWT처럼
-- role/id를 클라이언트가 보낸 토큰에서 직접 못 꺼낸다) 원자적 CAS의 형태가 바뀌었다 — 예전엔
-- "고정된 role:id 슬롯의 값이 옛 해시와 같으면 새 해시로 교체"였는데, 이제는 키 자체가 토큰마다
-- 다르니 "옛 토큰 키가 있으면 그 값(memberId|role|type|remember)을 새 토큰 키로 그대로 옮기고
-- 옛 키는 지운다"가 된다. 옛 키가 없으면(이미 한 번 회전돼서 지워졌거나 애초에 없던 토큰) 재사용
-- 의심으로 보고 실패시킨다. Redis가 싱글스레드라 이 스크립트 전체가 원자적으로 실행되므로,
-- 동시에 같은 옛 토큰으로 두 번 재발급 요청이 와도 하나만 성공한다.

local value = redis.call('GET', KEYS[1])
if not value then
    return false
end
redis.call('SET', KEYS[2], value, 'PX', ARGV[1])
redis.call('DEL', KEYS[1])
return value
