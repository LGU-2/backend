# 인증

회원은 카카오 OIDC 로, 관리자는 자체 계정으로 로그인한다. **두 경로는 섞이지 않는다.**
공통 규약은 [README.md](./README.md) 에 있다.

| 구분 | 회원 | 관리자 |
|---|---|---|
| 수단 | 카카오 OIDC (인가 코드) | 아이디, 비밀번호 |
| 비밀번호 | 보관하지 않는다 | BCrypt 저장 |
| 계정 생성 | 최초 로그인 시 자동 | 최고관리자가 발급 |
| Access / Refresh | 30분 / 14일 | 30분 / 1일 |

카카오는 **로그인 시점의 신원 확인까지만** 쓴다. 이후 API 인증은 자체 JWT 로 하고 카카오 토큰은 보관하지 않는다.

## 회원

### 로그인 시작

```
GET /v1/auth/kakao/authorize
```

서버가 `state` 와 `nonce` 를 만들어 저장(TTL 5분)하고 카카오 인가 URL 로 302 리다이렉트한다.

| 응답 | |
|---|---|
| `302` | `Location` 에 카카오 authorize URL |

**`state` 는 CSRF 를, `nonce` 는 ID 토큰 재생 공격을 막는다.** 둘 다 카카오 권장 사항이다.

### 콜백

```
GET /v1/auth/kakao/callback?code={code}&state={state}
```

| 파라미터 | 필수 | 설명 |
|---|---|---|
| `code` | O | 카카오 인가 코드 |
| `state` | O | 시작 단계에서 발급한 값 |

서버가 하는 일은 넷이다.

```
1. state 검증
2. 토큰 엔드포인트로 code 와 client_secret 전송
3. id_token 검증 (iss, aud, exp, nonce, JWKS 서명 RS256)
4. sub 로 회원 조회. 없으면 PENDING_PROFILE 상태로 생성
```

```json
{
  "code": "SUCCESS",
  "data": {
    "accessToken": "eyJ...",
    "tokenType": "Bearer",
    "expiresInSeconds": 1800,
    "refreshToken": "...",
    "member": { "memberId": 1, "nickname": "홍길동", "status": "PENDING_PROFILE" }
  }
}
```

**`status` 가 `PENDING_PROFILE` 이면 추가 정보 입력이 남아 있다.** 프론트가 입력 폼으로 보낸다.

| 오류 | 코드 | 언제 |
|---|---|---|
| `401` | `AUTH-001` | `state` 또는 `nonce` 불일치 |
| `401` | `AUTH-002` | `id_token` 검증 실패 |
| `503` | `AUTH-003` | 카카오 응답 없음. **재시도 가능하다** |

### 토큰 재발급

```
POST /v1/auth/tokens:refresh
```

```json
{ "refreshToken": "..." }
```

**Rotation 을 적용한다.** 재발급하면 이전 Refresh 토큰은 즉시 무효가 된다.
같은 토큰으로 두 번 오면 탈취를 의심해 해당 회원의 토큰을 전부 폐기한다.

| 오류 | 코드 | 언제 |
|---|---|---|
| `401` | `AUTH-004` | 만료되었거나 이미 사용된 토큰 |

### 로그아웃

```
POST /v1/auth/logout
```

서비스 Access 와 Refresh 를 폐기하고, 카카오 로그아웃 API 를 어드민 키와 회원번호로 호출한다.
**카카오계정 함께 로그아웃은 제공하지 않는다.**

| 응답 | |
|---|---|
| `204` | 본문 없음 |
| `401` | 로그인 상태가 아니다 |

## 관리자

### 로그인

```
POST /v1/admin/sessions
```

로그인을 **세션 생성**으로 모델링한다 (AIP-133). 커스텀 동사(`:login`) 대신 표준 Create 로 표현된다.
로그아웃은 같은 자원의 Delete 다.

```json
{ "loginId": "admin.kim", "password": "..." }
```

| 필드 | 필수 | 제약 |
|---|---|---|
| `loginId` | O | 50자 이하 |
| `password` | O | **72자 이하.** BCrypt 가 그 이상을 조용히 잘라낸다 |

```json
{
  "code": "SUCCESS",
  "data": {
    "accessToken": "eyJ...",
    "tokenType": "Bearer",
    "expiresInSeconds": 1800,
    "refreshToken": "...",
    "admin": { "loginId": "admin.kim", "name": "김관리", "role": "ADMIN" }
  }
}
```

| 응답 | 코드 | 언제 |
|---|---|---|
| `201` | | 발급 성공 |
| `401` | `ADMIN-001` | 아이디 또는 비밀번호 불일치. **사유를 구분해 알리지 않는다** |
| `403` | `ADMIN-002` | 비활성 계정 |

**실패 응답이 계정 존재 여부를 구분해 주지 않는다** (`SEC-6-04`). 메시지뿐 아니라 **응답 시간도 맞춘다.**
계정이 없을 때도 더미 해시로 BCrypt 를 돌려, 시간 차이로 아이디 존재가 드러나지 않게 한다.

### 로그아웃

```
DELETE /v1/admin/sessions/current
```

현재 토큰을 폐기한다. 단일 인스턴스라 싱글톤 리소스로 둔다 (`API-2-07`).

| 응답 | |
|---|---|
| `204` | 본문 없음 |

### 비밀번호 변경

```
POST /v1/admin/sessions/current:changePassword
```

```json
{ "currentPassword": "...", "newPassword": "..." }
```

**변경하면 그 계정의 토큰을 전량 폐기한다.** 임시 비밀번호 계정은 첫 로그인 시 변경이 강제된다.

| 응답 | 코드 | 언제 |
|---|---|---|
| `204` | | 변경 성공 |
| `401` | `ADMIN-003` | 현재 비밀번호 불일치 |
| `422` | `ADMIN-004` | 정책 미충족. 영문 대소문자, 숫자, 특수문자 조합 10자 이상 |

## 정하지 못한 것

**5회 실패 시 30분 잠금이 빠져 있다.** 요구사항에는 있으나 `admin` 테이블에 실패 횟수와 잠금 시각
컬럼이 없다. 넣으려면 컬럼 두 개를 더하는 마이그레이션이 필요하고, 그때 이 문서에
`423 Locked` 응답과 잠금 해제 경로를 더한다.

**레이트 리밋도 정해지지 않았다.** 계정 단위 잠금과 별개로 IP 단위 제한이 필요한데,
애플리케이션과 앞단(ALB, WAF) 중 어디서 할지 결정되지 않았다.
