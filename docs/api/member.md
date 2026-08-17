# 회원

회원 정보와 배송지, 그리고 관리자의 회원 관리다. 로그인은 [auth.md](./auth.md) 에 있다.

## 회원 정보

### 내 정보 조회

```
GET /v1/members/me
```

싱글톤 리소스다 (`API-2-07`). 자기 정보를 보는 데 식별자를 받지 않는다.
**클라이언트가 보낸 식별자가 아니라 토큰의 주체로 조회한다** (`SEC-1-02`).

```json
{
  "code": "SUCCESS",
  "data": {
    "memberId": 1,
    "nickname": "홍길동",
    "name": "홍*동",
    "email": "hon***@example.com",
    "phone": "010-****-5678",
    "status": "ACTIVE",
    "grade": { "gradeId": 2, "name": "실버" },
    "marketingAgreed": true
  }
}
```

**이름, 이메일, 연락처는 마스킹해서 내보낸다.**

### 내 정보 수정

```
PATCH /v1/members/me
```

부분 수정이다. 보낸 필드만 바뀐다 (`API-3-06`).

| 필드 | 제약 |
|---|---|
| `name` | 50자 이하 |
| `nickname` | 50자 이하 |
| `email` | 이메일 형식 |
| `phone` | 20자 이하 |
| `marketingAgreed` | 불리언 |

**비밀번호는 없다.** 카카오가 관리한다. `providerUserId` 도 바꿀 수 없다.

`PENDING_PROFILE` 상태에서 필수 항목을 채우면 `ACTIVE` 로 바뀐다.

**회원 행이 만들어질 때 장바구니도 함께 만들어진다.** 회원당 하나이고 생성 API 가 없다 ([order.md](./order.md) 참고).

### 탈퇴

```
POST /v1/members/me:withdraw
```

```json
{ "reason": "서비스를 더 이상 이용하지 않음" }
```

**카카오 재인증(`prompt=login`) 을 먼저 통과해야 한다.** 그 뒤 소프트 딜리트로 처리하고
카카오 연결 해제 API 를 호출한다. 주문 이력은 법정 기간 보존한다.

| 응답 | 코드 | 언제 |
|---|---|---|
| `204` | | 탈퇴 완료 |
| `409` | `MEMBER-001` | 진행 중 주문이 있다 |
| `409` | `MEMBER-002` | 미완료 환불이 있다 |

**탈퇴해도 같은 카카오 계정으로 다시 가입할 수 있다.** 활성 회원만 유일성을 강제한다.

## 배송지

### 목록

```
GET /v1/members/me/addresses
```

기본 배송지가 먼저 온다.

```json
{
  "addresses": [
    {
      "addressId": 3,
      "recipient": "홍*동",
      "phone": "010-****-5678",
      "zipcode": "06234",
      "roadAddress": "서울 강남구 테헤란로 1",
      "detailAddress": "10층",
      "isDefault": true
    }
  ]
}
```

### 등록, 수정, 삭제

```
POST   /v1/members/me/addresses
PATCH  /v1/members/me/addresses/{addressId}
DELETE /v1/members/me/addresses/{addressId}
```

| 필드 | 필수 | 제약 |
|---|---|---|
| `recipient` | O | 50자 이하 |
| `phone` | O | 20자 이하 |
| `zipcode` | O | 10자 이하. 도로명 주소 API 결과 |
| `roadAddress` | O | 255자 이하 |
| `detailAddress` | | 255자 이하 |
| `isDefault` | | 기본값 `false` |

**기본 배송지는 회원당 하나다.** 새로 지정하면 이전 것이 자동으로 내려간다.
DB 가 계산 컬럼과 UNIQUE 로 이 규칙을 강제한다.

| 오류 | 코드 | 언제 |
|---|---|---|
| `403` | `MEMBER-003` | 남의 배송지다 |
| `409` | `MEMBER-004` | 진행 중 주문이 참조하는 배송지는 지울 수 없다 |

## 관리자

### 회원 목록

```
GET /v1/admin/members?query={검색어}&status={상태}&gradeId={등급}
```

| 파라미터 | 설명 |
|---|---|
| `query` | 이름, 아이디, 연락처 부분 일치 |
| `status` | `PENDING_PROFILE`, `ACTIVE`, `BLOCKED`, `WITHDRAWN` |
| `gradeId` | 등급 |

가입일, 등급, 상태, 카카오 연동 일시를 함께 준다. **여기서도 개인정보는 마스킹한다.**

### 차단과 해제

```
POST /v1/admin/members/{memberId}:block
POST /v1/admin/members/{memberId}:unblock
```

```json
{ "reason": "부정 주문 반복" }
```

**차단하면 리프레시 토큰을 비운다.** 로그인, 주문, 글쓰기가 막힌다.

다만 **이미 나간 Access 토큰은 즉시 끊기지 않는다.** 무상태라 폐기할 수단이 없어
남은 수명(최대 30분) 동안 유효하다. 즉시 차단이 필요하면 블랙리스트가 있어야 한다.

| 응답 | 코드 | 언제 |
|---|---|---|
| `204` | | 처리 완료 |
| `409` | `MEMBER-005` | 이미 같은 상태다 |

### 등급 관리

```
GET    /v1/admin/member-grades
POST   /v1/admin/member-grades
PATCH  /v1/admin/member-grades/{gradeId}
DELETE /v1/admin/member-grades/{gradeId}
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `name` | O | 등급명. 중복 불가 |
| `promotionRule` | | 승급 기준 서술 |
| `isDefault` | | 가입 시 부여할 등급 |

**기본 등급은 최대 하나다.** DB 가 강제한다. 다만 **최소 하나가 있어야 한다는 것은 DB 가 못 막아**
정합성 검사가 본다.

초기 등급은 브론즈, 실버, 골드다. 선착순 쿠폰 캠페인의 대상 등급과 연동된다.

| 오류 | 코드 | 언제 |
|---|---|---|
| `409` | `MEMBER-006` | 소속 회원이 있는 등급은 지울 수 없다 |
| `409` | `MEMBER-007` | 등급명 중복 |

### 관리자 계정

```
GET    /v1/admin/admins
POST   /v1/admin/admins
DELETE /v1/admin/admins/{adminId}
```

**최고관리자만 할 수 있다** (`SUPER_ADMIN`). 나머지는 `403` 이다.

```json
{ "loginId": "admin.lee", "name": "이관리", "role": "ADMIN" }
```

임시 비밀번호를 발급하고 **첫 로그인 시 변경을 강제한다.**

삭제는 하드 삭제가 아니라 비활성화다. 이력 테이블 다섯이 `admin_id` 를 참조해 지울 수 없다.
비활성화하면 리프레시 토큰을 비운다. Access 토큰은 남은 수명 동안 유효하다.

| 오류 | 코드 | 언제 |
|---|---|---|
| `403` | `ADMIN-005` | 최고관리자가 아니다 |
| `409` | `ADMIN-006` | 아이디 중복 |
| `409` | `ADMIN-007` | 본인은 비활성화할 수 없다 |
| `409` | `ADMIN-008` | 마지막 최고관리자는 비활성화할 수 없다 |

## 정하지 못한 것

**등급별 할인율이 빠져 있다.** 요구사항은 등급마다 할인율을 두고 주문서에서 쓰라고 하는데,
`member_grade` 테이블에 할인율 컬럼이 없다. 지금은 등급명과 승급 기준만 다룰 수 있다.
