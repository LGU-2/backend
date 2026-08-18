# API 명세

요구사항 명세서(`Freshmarket_요구사항명세서.xlsx`) 72개 기능을 리소스로 옮긴 것이다.
설계 규칙은 [api-design-guideline.md](../code-architecture/api-design-guideline.md), 도메인 배치는
[domain-map.md](../code-architecture/domain-map.md), 저장 구조는 `V1__init_schema.sql` 을 따른다.

## 문서

| 문서 | 다루는 것 |
|---|---|
| [auth.md](./auth.md) | 회원 카카오 로그인, 관리자 로그인, 토큰 |
| [member.md](./member.md) | 회원 정보, 배송지, 등급, 관리자의 회원 관리 |
| [admin.md](./admin.md) | 관리자 계정 발급과 비활성화 |
| [product.md](./product.md) | 상품, 카테고리, 리뷰, Q&A |
| [stock.md](./stock.md) | 로트 입고, 재고 변동, 조정, 폐기 |
| [order.md](./order.md) | 장바구니, 주문, 결제, 취소, 반품, 교환, 배송 |
| [coupon.md](./coupon.md) | 쿠폰함, 선착순 발급, 쿠폰 관리 |
| [statistics.md](./statistics.md) | 판매 집계, 소진율, 주문 통계, 캠페인 대상 |

## 도메인과 문서

문서는 도메인이 아니라 **사용자 흐름**으로 묶었다. 주문은 장바구니부터 클레임까지가 한 흐름이라
나누면 상태 전이를 따라가기 어렵다. 코드 패키지는 [domain-map.md](../code-architecture/domain-map.md)
의 13개를 따르므로 대응을 여기 적어 둔다.

| 도메인 | 문서 | 절 |
|---|---|---|
| `member` | [member.md](./member.md) | 회원 정보, 배송지 |
| `admin` | [admin.md](./admin.md), [auth.md](./auth.md) | 관리자 계정, 인증 |
| `product` | [product.md](./product.md) | 상품, 카테고리 |
| `review` | [product.md](./product.md) | 리뷰 |
| `qna` | [product.md](./product.md) | Q&A |
| `stock` | [stock.md](./stock.md) | 전체 |
| `cart` | [order.md](./order.md) | 장바구니 |
| `order` | [order.md](./order.md) | 주문 |
| `payment` | [order.md](./order.md) | 결제 |
| `claim` | [order.md](./order.md) | 클레임 |
| `shipment` | [order.md](./order.md) | 관리자 주문 관리, 배송 사진 |
| `coupon` | [coupon.md](./coupon.md) | 전체 |
| `statistics` | [statistics.md](./statistics.md) | 전체 |

## 공통 규약

### 경로

```
/v1/...           회원과 비회원
/v1/admin/...     관리자
```

**소비자가 다르면 경로를 나눈다** (`API-2-08`). 같은 리소스라도 노출 필드와 인증 방식이 다르다.
리소스는 명사 복수형이고 계층을 경로로 드러낸다 (`API-2-03`).

```
GET    /v1/products/{productId}            단건
GET    /v1/products                        목록
POST   /v1/carts/items                     생성
PATCH  /v1/carts/items/{cartItemId}        부분 수정
DELETE /v1/carts/items/{cartItemId}        삭제
```

표준 메서드로 표현할 수 없을 때만 콜론 표기의 커스텀 메서드를 쓴다 (`API-3-08`).

```
POST /v1/orders/{orderId}:cancel
POST /v1/admin/claims/{claimId}:approve
```

### 인증

| 주체 | 수단 | 토큰 |
|---|---|---|
| 회원 | 카카오 OIDC (인가 코드) | `type=MEMBER`, `role=USER` |
| 관리자 | 자체 아이디와 비밀번호 | `type=ADMIN`, `role=ADMIN` 또는 `SUPER_ADMIN` |

```
Authorization: Bearer <accessToken>
```

**`type` 클레임으로 회원 토큰의 관리자 API 접근을 막는다.** 권한은 `role` 로 판정한다 (RBAC).
기본값은 거부다. 공개 경로를 제외한 모든 경로가 인증을 요구한다 (`SEC-1-04`).

수명은 회원이 Access 30분 / Refresh 14일, 관리자가 Access 30분 / Refresh 1일이다.

### 응답 봉투

성공과 실패가 같은 모양이다. 클라이언트가 분기 전에 파싱을 끝낼 수 있다.

```json
{
  "code": "SUCCESS",
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": { }
}
```

**성공 여부는 `code` 가 `SUCCESS` 인지로 판별한다.** 실패면 `data` 가 `null` 이고 `code` 와 `message` 가
`ErrorCode` 에서 나온다. 던지는 자리에서 문장을 지어내지 않는다.

```json
{
  "code": "ORDER-004",
  "message": "이미 취소된 주문입니다.",
  "data": null
}
```

### 상태 코드

| 코드 | 언제 |
|---|---|
| `200` | 조회, 수정 성공 |
| `201` | 생성 성공 |
| `204` | 삭제 성공, 본문 없음 |
| `400` | 입력 형식이나 값이 잘못됨 |
| `401` | 인증이 없거나 유효하지 않음 |
| `403` | 인증은 됐으나 권한이 없음 |
| `404` | 리소스가 없음 |
| `409` | 현재 상태에서 할 수 없는 요청 (중복, 상태 충돌) |
| `422` | 형식은 맞으나 업무 규칙 위반 |

**권한이 없으면 존재 여부와 무관하게 `403` 을 낸다** (`API-7-05`). 권한은 있는데 없을 때만 `404` 다.
그렇지 않으면 응답 코드가 리소스 존재를 알려주는 통로가 된다.

### 목록과 페이지네이션

```
GET /v1/products?pageSize=20&pageToken=eyJ...&sort=SALES_DESC
```

| 파라미터 | 뜻 |
|---|---|
| `pageSize` | 한 번에 받을 개수. 기본 20, 최대 100 |
| `pageToken` | 다음 페이지 커서. 비우면 처음부터 |
| `sort` | 정렬 키. 리소스마다 허용값이 다르다 |

```json
{
  "products": [ ],
  "nextPageToken": "eyJ..."
}
```

**`nextPageToken` 이 비어 있으면 마지막 페이지다.** 전체 건수는 기본으로 주지 않는다.
커서 방식이라 페이지를 넘기는 동안 새 데이터가 들어와도 항목이 밀리거나 건너뛰지 않는다.

정렬 키는 **화이트리스트로 검증한다** (`SEC-2-02`). 클라이언트가 보낸 문자열을 쿼리에 붙이지 않는다.

### 식별자

**응답에 내부 `Long id` 를 그대로 내보낸다.** 외부 노출 식별자(`public_id`)는 아직 도입하지 않았다.
근거와 도입 방법은 [identifier-strategy-guideline.md](../code-architecture/identifier-strategy-guideline.md)
머리말에 있다. 도입하면 이 명세의 경로 변수와 응답 필드가 함께 바뀐다.

### 개인정보

**응답과 로그에서 이름, 이메일, 연락처, 주소, 회원번호는 마스킹한다.** 더미 데이터도 예외가 아니다.
카카오 `sub` 는 로그에 평문으로 남기지 않는다.

### 멱등성

쿠폰 발급과 사용, 결제, 클레임 승인은 **멱등키를 받는다.**

```
Idempotency-Key: <클라이언트가 만든 UUID>
```

같은 키로 다시 오면 새로 처리하지 않고 최초 결과를 그대로 돌려준다.

## 아직 정해지지 않은 것

명세를 쓰면서 **요구사항에는 있으나 현재 스키마에 근거가 없는 것**들이다.
API 를 확정하기 전에 결정이 필요하다.

| 항목 | 요구사항 | 현재 스키마 |
|---|---|---|
| 등급별 할인율 | 주문서의 등급 할인 기준 데이터 | `member_grade` 에 할인율 컬럼 없음 |
| 로그인 5회 실패 30분 잠금 | 관리자 인증 요구사항 | `admin` 에 실패 횟수, 잠금 컬럼 없음 |
| 배송비 정책 | N원 이상 무료 등 규칙 정의 필요 | 정책 테이블 없음. `orders.shipping_fee` 만 있다 |
| 알림 발송 | Q&A 답변, 주문 상태 변경, 배송 시작, 소비기한 임박 | `notification` 테이블이 주석 처리되어 있다 |

**이 문서는 위 항목을 스키마에 있는 것만으로 기술한다.** 없는 것은 경로를 적지 않고 이 표에 남겼다.
결정되면 추가만 하는 마이그레이션(V2)과 함께 해당 문서에 절을 더한다.
