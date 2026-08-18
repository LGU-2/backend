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

## 경로

```
회원      POST   /v1/auth/tokens
          POST   /v1/auth/tokens:refresh
          DELETE /v1/auth/tokens
          GET    /v1/auth/kakao/authorize

관리자     POST   /v1/admin/auth/tokens
          POST   /v1/admin/auth/tokens:refresh
          DELETE /v1/admin/auth/tokens
          PUT    /v1/admin/auth/password
```

**리소스를 `tokens` 로 둔 것은 그것이 서버가 실제로 보관하는 것이기 때문이다.**

```sql
refresh_token_hash        CHAR(64)     -- SHA-256 hex. NULL 이면 로그아웃 상태다
refresh_token_expires_at  DATETIME(6)
```

세션 테이블은 없다. `DELETE /v1/auth/tokens` 는 위 두 컬럼을 비우는 일과 그대로 대응한다.
**세션을 리소스로 세웠다면 실재하지 않는 것에 이름을 붙이는 셈이 된다.**

**Access 토큰은 무상태라 폐기할 수단이 없다.** 로그아웃해도 이미 나간 Access 토큰은
남은 수명(최대 30분) 동안 유효하다. 즉시 끊어야 하면 블랙리스트가 따로 필요하고, 지금은 없다.

`:refresh` 만 커스텀 메서드다. 갱신은 클라이언트가 필드를 고치는 것이 아니라
**서버가 규칙에 따라 수행하는 동작**이라 `PATCH` 로 표현되지 않는다 (`API-3-08`).

## 토큰을 어떻게 전달하나

**둘을 다르게 준다.** 발급할 때와 쓸 때를 나눠 봐야 한다.

| | 발급 (서버 -> 클라이언트) | 사용 (클라이언트 -> 서버) |
|---|---|---|
| Access | 응답 본문 | `Authorization: Bearer <token>` |
| Refresh | **`Set-Cookie` 헤더** | **브라우저가 쿠키로 자동 첨부** |

`Authorization` 은 요청 헤더라 서버가 응답으로 돌려줄 자리가 아니다. 그래서 **발급은 본문, 사용은 헤더**다.

리프레시는 반대로 클라이언트가 손댈 일이 없다. 브라우저가 알아서 붙이므로
**스크립트가 값을 알 필요가 없고, 그래서 `HttpOnly` 로 막을 수 있다.**

```
Set-Cookie: refreshToken=...; HttpOnly; Secure; SameSite=Strict;
            Path=/v1/auth/tokens; Max-Age=1209600
```

**수명이 갈라서 그렇다.** Access 는 30분이라 탈취돼도 구간이 짧지만, Refresh 는 14일이라
XSS 로 한 번 새면 2주 동안 재발급이 가능하다. `HttpOnly` 를 걸면 스크립트가 아예 읽지 못한다.

`Path` 를 `:refresh` 와 `DELETE` 가 쓰는 경로로 좁힌다. **다른 API 요청에는 실려 가지 않는다.**

**대신 CSRF 가 성립하게 된다.** 브라우저가 쿠키를 자동으로 붙이기 때문이다.
`SameSite=Strict` 가 대부분을 막지만, 그 경로에 한해 CSRF 대응이 필요한지 따로 판단한다.
쿠키를 쓰지 않았다면 없었을 문제이고, **이것이 XSS 위험과 맞바꾼 대가다.**

## 회원

### 로그인 시작

```
GET /v1/auth/kakao/authorize
```

서버가 `state` 와 `nonce` 를 만들어 저장(TTL 5분)하고 카카오 인가 URL 을 돌려준다.

```json
{ "authorizationUrl": "https://kauth.kakao.com/oauth/authorize?..." }
```

**`state` 는 CSRF 를, `nonce` 는 ID 토큰 재생 공격을 막는다.** 둘 다 카카오 권장 사항이다.

**콜백은 프론트가 받는다.** 카카오가 `redirect_uri` 로 `code` 를 붙여 302 로 되돌리면
프론트가 그 값을 아래 경로로 넘긴다. 이렇게 하면 **회원과 관리자의 발급 경로가 같은 모양이 되고**,
인증 수단이 늘어도(구글, 애플) 본문만 갈린다.

이 경로는 리소스 조작이 아니라 프로토콜 단계라 콜론 표기를 쓰지 않는다.

### 로그인

```
POST /v1/auth/tokens
```

```json
{ "authorizationCode": "...", "state": "..." }
```

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
    "member": { "memberId": 1, "nickname": "홍길동", "status": "PENDING_PROFILE" }
  }
}
```

리프레시 토큰은 본문에 없다. **`Set-Cookie` 헤더로 나간다.**

**`status` 가 `PENDING_PROFILE` 이면 추가 정보 입력이 남아 있다.** 프론트가 입력 폼으로 보낸다.

| 응답 | 코드 | 언제 |
|---|---|---|
| `201` | | 발급 성공 |
| `401` | `AUTH-001` | `state` 또는 `nonce` 불일치 |
| `401` | `AUTH-002` | `id_token` 검증 실패 |
| `503` | `AUTH-003` | 카카오 응답 없음. **재시도 가능하다** |

### 재발급

```
POST /v1/auth/tokens:refresh
```

**요청 본문이 없다.** 리프레시 토큰은 쿠키로 실려 온다.

**Rotation 을 적용한다.** 새 토큰을 다시 `Set-Cookie` 로 내린다. 재발급하면 이전 리프레시 토큰은 즉시 무효가 된다.
같은 토큰으로 두 번 오면 탈취를 의심해 그 회원의 리프레시 토큰을 비운다.

| 오류 | 코드 | 언제 |
|---|---|---|
| `401` | `AUTH-004` | 만료되었거나 이미 사용된 토큰 |

### 로그아웃

```
DELETE /v1/auth/tokens
```

`refresh_token_hash` 를 비우고 **쿠키를 만료시킨다**(`Max-Age=0`).
그 뒤 카카오 로그아웃 API 를 어드민 키와 회원번호로 호출한다.
**카카오계정 함께 로그아웃은 제공하지 않는다.**

식별자를 받지 않는다. **지울 대상은 토큰의 주체로 정해진다** (`SEC-1-02`).
기기별 다중 로그인이 생기면 그때 `/v1/auth/tokens/{tokenId}` 가 의미를 갖는다.
지금은 컬럼이 하나라 기기 한 대만 유지된다.

| 응답 | |
|---|---|
| `204` | 본문 없음 |
| `401` | 로그인 상태가 아니다 |

## 관리자

### 로그인

```
POST /v1/admin/auth/tokens
```

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

### 재발급과 로그아웃

```
POST   /v1/admin/auth/tokens:refresh
DELETE /v1/admin/auth/tokens
```

회원과 같다. 수명과 쿠키 경로만 다르다.

```
Set-Cookie: refreshToken=...; HttpOnly; Secure; SameSite=Strict;
            Path=/v1/admin/auth/tokens; Max-Age=86400
```

Refresh 가 1일인 것은 **자동 로그인을 제공하지 않기 때문이다.** 관리자 콘솔은 회원 서비스보다
권한이 크므로 로그인 상태를 오래 끌지 않는다.

`Path` 가 달라 **관리자 쿠키가 회원 API 요청에 실려 가지 않는다.**

**요구사항이 `HttpOnly` 쿠키를 지정한 것은 회원뿐이다.** 관리자는 전달 방식을 적어 두지 않았고,
여기서 같은 방식으로 정했다. 근거는 보안이다.

```
관리자 콘솔도 브라우저 앱이라 XSS 위험이 같다
관리자 토큰이 더 값지다. 털리면 상품 삭제, 환불, 권한 변경까지 열린다
수명 1일은 위험을 줄일 뿐 없애지 못한다
```

**전제가 하나 있다. 관리자 콘솔과 API 가 같은 사이트여야 한다.**
다른 사이트면 `SameSite=Strict` 쿠키가 실리지 않아 `None` 으로 낮춰야 하는데,
그러면 CSRF 방어가 사라져 쿠키를 쓴 이점이 반감된다.

배포를 나눠야 하는 상황이 오면 **`SameSite` 를 낮추기 전에 같은 사이트로 묶는 방법을 먼저 찾는다.**
서브도메인은 같은 사이트로 취급되므로 `admin.example.com` 과 `api.example.com` 은 문제없다.

### 비밀번호 변경

```
PUT /v1/admin/auth/password
```

```json
{ "currentPassword": "...", "newPassword": "..." }
```

싱글톤 하위 리소스다 (`API-2-07`). 비밀번호는 회원당 하나뿐이라 식별자가 없고,
**교체이므로 `PUT` 이 맞다.**

**변경하면 그 계정의 리프레시 토큰을 비운다.** 임시 비밀번호 계정은 첫 로그인 시 변경이 강제된다.

| 응답 | 코드 | 언제 |
|---|---|---|
| `204` | | 변경 성공 |
| `401` | `ADMIN-003` | 현재 비밀번호 불일치 |
| `422` | `ADMIN-004` | 정책 미충족. 영문 대소문자, 숫자, 특수문자 조합 10자 이상 |

**회원에게는 이 경로가 없다.** 비밀번호를 보관하지 않고 카카오가 관리한다.

## 정하지 못한 것

**5회 실패 시 30분 잠금이 빠져 있다.** 요구사항에는 있으나 `admin` 테이블에 실패 횟수와 잠금 시각
컬럼이 없다. 넣으려면 컬럼 두 개를 더하는 마이그레이션이 필요하고, 그때 이 문서에
`423 Locked` 응답을 더한다.

**레이트 리밋도 정해지지 않았다.** 계정 단위 잠금과 별개로 IP 단위 제한이 필요한데,
애플리케이션과 앞단(ALB, WAF) 중 어디서 할지 결정되지 않았다.
