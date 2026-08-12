# 스키마 설계의 근거

이 문서는 `src/main/resources/db/migration/V1__init_schema.sql` 이 지금 모양이 된 이유를 남긴다.
DDL 주석이 **무엇을** 하는지를 적는다면 이 문서는 **왜 그렇게 했는지**와 **무엇을 포기했는지**를 적는다.

점검 항목을 뽑아내지 않는다. 판단 기준은 다른 가이드가 갖는다.

* 식별자: [identifier-strategy-guideline.md](./identifier-strategy-guideline.md)
* 엔티티 매핑: [entity-creation-guideline.md](./entity-creation-guideline.md), [jpa-rdb-guideline.md](./jpa-rdb-guideline.md)
* DB 제약 일반론: `LGU-2/.github` 의 `qa-data-integrity.md` 3장

---

## 1. 전체 구조

활성 33개 표를 8장으로 나눈다. 장 순서는 **참조가 앞에서 뒤로만 흐르도록** 정했다.

```
1. 회원 / 권한   member_grade, member, address, admin
2. 상품 / 재고   category, supplier, product, product_option, product_image, stock_lot
3. 쿠폰         coupon, coupon_product_option, member_coupon, member_coupon_status_history
4. 장바구니      cart, cart_item
5. 주문 / 결제   orders, order_item, stock_allocation, stock_disposal, stock_movement,
                daily_sales, order_status_history, payment
6. 클레임        claim, claim_attachment, claim_item, refund, shipment, shipment_photo
7. 리뷰 / Q&A   review, qna
8. 공통         audit_log
```

**쿠폰이 주문보다 앞에 있는 것이 이 순서의 유일한 비직관 지점이다.**
`orders.member_coupon_id` 와 `order_item.member_coupon_id` 가 `member_coupon` 을 참조하기 때문이며,
뒤에 두면 외래 키를 `ALTER TABLE` 로 따로 걸어야 한다. 파일 안에 `ALTER TABLE` 이 하나도 없는 것이 이 배치의 결과다.

`notification` 은 주석 처리해 두었다. 발송 대상 리소스를 가리키는 참조가 없어 알림을 눌러 이동할 곳을 찾을 수 없고,
그 참조 형태는 알림 기능을 설계할 때 정해진다. 지금 만들면 쓰이지 않는 채로 형태가 굳는다.

---

## 2. 시각은 전부 `DATETIME(6)`

시각 컬럼 77개가 모두 마이크로초 정밀도다. 근거는 셋이고 첫째가 결정적이다.

**MySQL 은 정밀도가 낮은 컬럼에 넣을 때 버리지 않고 반올림한다.**

```
앱이 만든 값        2026-08-12 10:00:00.600
DATETIME 에 저장 -> 2026-08-12 10:00:01      아직 오지 않은 시각이 기록된다
```

절반의 확률로 미래 시각이 남는다. `ordered_at`, `paid_at`, `shipped_at` 처럼 실제 시각을 기록하는 컬럼에서 그대로 문제가 된다.
시각 순서를 보는 CHECK 가 여럿 있어(`chk_shipment_delivered_at`, `chk_claim_collect_at`)
앱이 검증한 값과 DB 값이 달라지면 예상 밖으로 걸린다.

**Hibernate 6 의 MySQL 방언은 `LocalDateTime` 을 `datetime(6)` 으로 잡는다.**
엔티티를 붙이고 `ddl-auto: validate` 를 켜는 순간 정밀도가 0이면 어긋난다.

같은 초 안의 순서를 가리지 못하는 문제도 있지만, `AUTO_INCREMENT` PK 가 타이브레이커라 실무상 해결된다.
`ORDER BY is_main DESC, sort_order ASC, product_image_id ASC` 처럼 정렬의 마지막 키로 PK 를 쓰는 곳이 그 예다.

`DATE` 다섯(`valid_from`, `valid_to`, `received_date`, `expiry_date`, `stat_date`)은 날짜만 필요한 값이라 그대로 둔다.

---

## 3. 조건부 유일성

"전체가 아니라 **일부 행 사이에서만** 유일" 이 여러 곳에 필요하다.
MySQL 에는 부분 인덱스(`CREATE UNIQUE INDEX ... WHERE`)가 없어 우회가 필요하다.

```sql
<이름>_key <타입> GENERATED ALWAYS AS (CASE WHEN <조건> THEN <유일해야 할 값> ELSE NULL END),
UNIQUE KEY uk_... (<이름>_key)
```

조건 밖 행은 `NULL` 이 되고, UNIQUE 가 `NULL` 을 여러 개 허용하므로 검사에서 빠진다.

### 남긴 넷

| 표 | 컬럼 | 막는 것 |
|---|---|---|
| `member_grade` | `is_default_key` | 기본 등급은 전체에서 1개 |
| `address` | `is_default_key` | 회원별 기본 배송지 1개 |
| `product_image` | `is_main_key` | 상품별 대표 이미지 1개 |
| `member` | `active_provider_key` | 활성 회원만 카카오 계정 유일 |

### 걷어낸 둘

`product` 와 `review` 는 **재사용을 포기하고 평범한 UNIQUE 로 바꿨다.**

```sql
UNIQUE KEY uk_product_code (product_code)        -- 삭제한 상품코드를 다시 쓰지 않는다
UNIQUE KEY uk_review_orderitem (order_item_id)   -- 지운 리뷰의 주문 상품에 다시 쓰지 않는다
```

`product_code` 는 자동 생성 코드라 재사용할 이유가 없고, 리뷰는 수정으로 정정하면 된다.
**잃는 것이 거의 없어서 트릭을 쓸 값이 없었다.**

### 검토했다가 안 쓴 방법

* **함수 인덱스** (`UNIQUE KEY uk_... ((CASE WHEN ...)))`, MySQL 8.0.13+)
  컬럼이 안 보이지만 MySQL 이 내부에서 숨은 생성 컬럼을 만드는 것이라 기법이 같고 이식성도 그대로다.
* **부모 쪽으로 참조를 뒤집기** (`product.main_image_id` 같은 모양)
  "최대 하나" 는 구조적으로 보장되지만 순환 외래 키가 생기고,
  **대표 이미지가 그 상품 것인지, 확정된 이미지인지를 DB 가 못 막게 된다.** 지금은 둘 다 막힌다.
* **별도 표로 분리** (`product_main_image(product_id PK, ...)`)
  표 3개와 교차 검증 3건이 늘어 계산 컬럼 3개를 없애는 값보다 비싸다.

---

## 4. 금액

### 항등식을 DB 가 강제한다

```sql
CONSTRAINT chk_order_total    CHECK (total_amount = product_amount - discount_amount + shipping_fee)
CONSTRAINT chk_order_discount_cap CHECK (discount_amount <= product_amount)
```

각 항목이 0 이상인지만 보면 `total_amount` 가 아무 값이나 될 수 있다. 한 행 안의 값들이라 CHECK 로 닫힌다.
할인 상한을 상품금액으로 잡은 것은 **배송비를 깎는 쿠폰을 두지 않기 때문**이다. 배송비 할인이 생기면 이 식이 바뀐다.

### 라인별 배분을 저장한다

`order_item.discount_amount` 는 **부분 반품 환불액의 근거**다.

```
환불액 = (unit_price * qty - discount_amount) * 반품수량 / qty
```

이 컬럼이 없으면 어느 라인이 할인받았는지 몰라 전 라인에 안분할 수밖에 없고, 특정 라인만 할인한 경우 금액이 틀어진다.

### 장바구니 쿠폰은 잔액 비례로 나눈다

```
1) 상품 쿠폰을 각 라인에 적용해 coupon_discount 를 확정한다
2) 라인 잔액 = unit_price * qty - coupon_discount
3) 장바구니 쿠폰을 라인 잔액 비례로 안분한다
4) discount_amount = coupon_discount + 안분액
5) 잔차는 잔액이 가장 큰 라인에 더한다
```

**정가 비례가 아닌 이유는 취향이 아니라 제약을 깨기 때문이다.**

```
10,000원 라인에 상품 쿠폰 9,500원이 붙은 경우
  정가 비례 -> 1,667원을 더 배정해 할인이 11,167원. chk_orderitem_discount 위반
  잔액 비례 -> 잔액 500원에 비례해 122원만 배정. 넘칠 수 없다
```

이미 깎인 만큼은 더 깎을 수 없다는 것을 비율에 반영하면 넘침이 구조적으로 사라진다.

### 0원 주문은 결제 행을 만들지 않는다

할인이 상품금액과 배송비를 다 덮으면 `total_amount = 0` 이 되고, PG 를 타지 않으므로 `payment` 행이 없다.
`refund` 도 없다. 돌려줄 돈이 없다는 것이 사실이기 때문이다.

**대가는 조회가 갈린다는 것이다.** 결제 여부를 묻는 조회에 `INNER JOIN payment` 를 쓰면 그런 주문이 결과에서 사라진다.

### 무통장입금을 두지 않는다

결제 수단은 `CARD` 와 `EASY_PAY` 둘이다. **신선식품이라 입금까지 최대 24시간 재고를 붙잡는 것을 감당할 수 없다.**
그동안 소비기한이 줄고, 그 로트를 살 수 있었던 다른 손님을 막는다.

즉시 결제만 받으므로 주문에서 결제까지의 구간이 짧고, `stock_allocation.RESERVED` 의 해제 유예도 그만큼 짧게 잡을 수 있다.
이 결정으로 `payment.payment_due_dt` 컬럼이 사라졌다.

---

## 5. 쿠폰

### `coupon` 은 틀이고 `member_coupon` 이 실제 쿠폰이다

`member_coupon` 이 발급 시점에 조건 8개를 복사해 온다.

```
coupon_name  scope  discount_type  discount_value
max_discount_amount  min_order_amount  valid_from  valid_to
```

**복사하는 이유는 `coupon` 이 살아 있는 표라 관리자가 고칠 수 있기 때문이다.**
참조만 두면 이미 받아 둔 쿠폰의 이름과 할인액이 나중에 바뀌고, 과거 주문 내역의 표시도 함께 바뀐다.

```
5월  coupon 42: '신규가입 5,000원', 5000   -> 주문. coupon_discount = 5000 저장
8월  관리자가 그 쿠폰을 재활용해 '여름 특가 8,000원', 8000 으로 수정
     -> 5월 주문을 열면 "여름 특가 8,000원 적용, -5,000원"
```

금액은 주문에 박혀 있고 이름만 현재 값을 읽어 와서 앞뒤가 안 맞는다.
`order_item` 이 `name_snapshot` 과 `unit_price` 를 남기는 것과 같은 문제이고 같은 해법이다.

**쿠폰함도 함께 해결된다.** 관리자가 할인액을 고쳐도 이미 받아 둔 사람의 쿠폰은 안 바뀐다.

### 대상 옵션은 필수이고 복사하지 않는다

`ITEM` 쿠폰은 대상 옵션을 하나 이상 반드시 갖는다. 행이 없는 것을 "대상 제한 없음" 으로 해석하지 않는다.
그 해석은 관리자가 대상을 실수로 지웠을 때 전 상품 할인으로 바뀌고, 그 사고가 조용하다.
대상이 필수라야 `order_item` 이 복합 외래 키로 대상 여부를 강제할 수 있기도 하다.

`coupon_product_option` 은 참조 그대로 두고 사용 시점의 현재 목록을 본다.
**금액과 조건은 발급 시점에 고정되어야 하지만, "어디에 쓸 수 있나" 는 운영이 조정하는 것**이라 성질이 다르다.
임박 재고가 팔리면 대상에서 빼는 식으로 조정할 수 있어야 한다.

### 선착순은 별도 표를 두지 않는다

`coupon.total_quantity` 가 `NULL` 이면 일반 쿠폰, 값이 있으면 선착순이다.

캠페인을 별도 표로 두었다가 걷어냈다. 그때는 `member_coupon` 이
**유도 가능한 참조를 셋**(대상 옵션 -> 캠페인 -> 쿠폰) 들고 있었고,
셋의 정합을 아무도 검증하지 않았으며, 발급 카운터가 둘로 나뉘어 합이 맞는지도 볼 수 없었다.

`is_active` 만 두고 `SCHEDULED/OPEN/CLOSED` 상태는 뺐다.
**소진 여부는 `issued_quantity` 로 유도되므로** 저장하면 어긋날 자리만 생긴다. 사람이 끄는 경우만 컬럼으로 표현한다.

### 상태 전이를 이력으로 남긴다

`member_coupon.status` 는 현재 상태만 갖고 `updated_at` 은 마지막 전이 시각만 가리킨다.
**"언제 왜 이렇게 됐나" 를 답할 수 없어** `member_coupon_status_history` 를 둔다.
`orders` 가 `order_status_history` 로 푸는 것과 같은 형태다.

`reason` 컬럼이 이 표의 핵심이다. 상태가 `ISSUED / USED / EXPIRED` 셋뿐이라
**만료가 유효기간 도래인지 어뷰징 발급 취소인지 상태값만으로는 갈리지 않는다.**
`changed_by` 가 `NULL` 이면 배치나 사용자 동작으로 자동 전이한 것이고, 값이 있으면 관리자가 손댄 것이다.

선착순 쿠폰은 소진과 취소를 두고 분쟁이 생기는 자리라 근거가 남아야 한다.
주문 취소로 쿠폰을 되살리는 전이(`USED -> ISSUED`)도 행으로 남는다.

### 층은 발행 시점에 정해진다

`coupon.scope` 가 `ORDER`(장바구니 쿠폰) 또는 `ITEM`(상품 쿠폰) 이다.
주문당 1장과 라인당 1장은 **컬럼이 하나라는 사실만으로** 보장되고,
한 쿠폰이 두 곳에 쓰이는 것은 각 `UNIQUE (member_coupon_id)` 가 막는다.
취소하면 `NULL` 로 비워 되돌린다.

**계산 컬럼을 쓰지 않은 이유가 이 `NULL` 처리다.** 취소 이력을 남기는 대신 참조를 비우기로 해서
조건부 유일성이 필요 없어졌다.

---

## 6. 재고

### `available_qty` 는 판매 가능 수량이다

```
INBOUND   +qty    신규 입고
RESTOCK   +qty    반품 재입고
RESERVE   -qty    주문 시점에 남이 못 잡게 뺀다
CONFIRM    0      이미 RESERVE 에서 뺐다
RELEASE   +qty    결제 취소/만료로 되돌린다
DISPOSE   -qty
EXPIRE    -qty
```

**`CONFIRM` 이 값을 바꾸지 않는 것이 이 표의 가장 오해하기 쉬운 지점이다.**
`stock_allocation.status` 가 `CONFIRMED=차감 확정(결제)` 이라고만 되어 있으면 결제에서 또 빼게 되고 재고가 두 배로 줄어든다.
세 표의 주석이 모두 이 사실을 말하도록 맞춰 두었다.

그래서 `stock_movement` 에 `CONFIRM` 행은 `qty_before = qty_after` 다.
재고를 옮기지 않고 예약이 확정으로 넘어간 사실만 남긴다. `movement_type` 별로 `qty_after` 를 검사하려면
이전 행을 봐야 해서 CHECK 로는 막을 수 없다.

### 반품은 원래 로트로 되돌린다

소비기한이 로트에 달려 있어 다른 로트에 넣으면 기한을 잃는다.
어느 로트였는지는 `claim_item -> order_item -> stock_allocation` 으로 찾는다.
잔여 소비기한이 `product.min_shelf_life_days` 에 못 미치면 되돌리지 않고 폐기한다(`stock_disposal.reason='RETURNED'`).
그 경우 로트로 돌아간 적이 없으므로 `available_qty` 를 줄이지 않고 `stock_movement` 행도 남지 않는다.

### 집계는 옵션 단위다

`daily_sales` 와 `coupon_product_option` 이 모두 `product_option_id` 를 본다.

**재고와 소비기한이 옵션 단위인데 집계만 상품 단위면 200g 와 1kg 의 수량을 더한 값이 된다.**
그 위에 세운 소진율로 "소비기한 임박 + 판매율 저조" 상품을 고르면 어느 옵션이 임박했는지가 사라지고,
캠페인 대상이 상품이면 1kg 만 임박했는데 200g 에도 쿠폰이 먹어 임박 재고가 안 빠진다.

`daily_sales` 는 원장이 아니라 스냅샷과 집계다.
`closing = opening + inbound + restocked - sold - disposed - expired` 가 기본이지만,
마감 시점에 예약만 되고 결제되지 않은 수량만큼 어긋난다. 재고의 진실은 `stock_lot.available_qty` 이고 근거는 `stock_movement` 다.

---

## 7. 이미지 업로드

업로드가 앱을 거치지 않고 클라이언트가 S3 로 직접 올린다. 그래서 행이 두 시점에 걸쳐 만들어진다.

```
발급   서버가 key 를 정해 행을 INSERT (upload_status='PENDING')
PUT    클라이언트 -> S3
확정   HeadObject 로 존재를 확인하고 CONFIRMED 로 바꾼다
```

**발급 시점에 행을 만드는 이유는 키를 클라이언트에게 받지 않기 위해서다.**
키를 돌려받아 저장하면 남의 키를 실어 보내 남의 이미지를 자기 리소스에 붙일 수 있다.
서명은 **올리는 것**만 막지 **저장하는 것**은 막지 못한다.

그 대가로 "아직 올라오지 않은 행" 이 표에 남고, 조회가 `upload_status='CONFIRMED'` 를 빠뜨리면 깨진 이미지가 나간다.

크기와 `Content-Type` 은 저장하지 않는다. **S3 객체 메타데이터가 진실이고 조회는 브라우저가 직접 받는다.**
상한과 허용 목록은 모든 업로드에 공통인 설정값이지 객체의 속성이 아니다.

세 표(`product_image`, `claim_attachment`, `shipment_photo`)로 나눈 것은 다형 참조를 피하기 위해서다.
한 표에 `owner_type` + `owner_id` 로 담으면 외래 키를 걸 수 없어 고아 행을 DB 가 막지 못한다.
나눠 두니 대표 이미지 소유 검증과 조회 확정 적용 범위를 표 단위로 정할 수 있는 이점도 따라왔다.

자세한 흐름은 `LGU-2/infra` 의 `백엔드공통_이미지저장소_설계.md` 6.2절에 있다.

---

## 8. 상태와 시각을 짝으로 묶는다

```sql
CONSTRAINT chk_payment_paid_at CHECK (
    (status IN ('PENDING','FAILED') AND paid_at IS NULL)
 OR (status IN ('PAID','REFUNDED')  AND paid_at IS NOT NULL)
 OR  status = 'CANCELED')
```

같은 모양이 여섯 곳에 있다.

| 표 | 짝 |
|---|---|
| `payment` | `status='PAID'` 와 `paid_at`, `pg_tid` |
| `shipment` | 세 상태와 `shipped_at`, `delivered_at` |
| `claim` | `REQUESTED` 가 아니면 `processed_at` |
| `refund` | `status='DONE'` 과 `refunded_at` |
| `member_coupon` | `status='USED'` 와 `used_at` |
| `qna` | `status='ANSWERED'` 와 `answer`, `answered_by` |

시각 순서를 보는 CHECK 는 있는데 **상태와 시각의 짝은 없던** 비대칭을 메운 것이다.
`payment.CANCELED` 만 예외인데, 결제 전 취소와 결제 후 취소가 모두 정상이라 한쪽으로 묶을 수 없다.

`member` 의 탈퇴도 같은 형태다. `status='WITHDRAWN'` 과 `deleted_at` 이 어긋나면
`active_provider_key` 가 잘못 계산되어 탈퇴자가 재가입을 못 하거나 활성 회원이 중복 가입된다.

---

## 9. DB 가 못 막아서 앱이 지켜야 하는 것

CHECK 는 **자기 행만** 볼 수 있다. 다른 행이나 다른 표를 봐야 하는 조건은 원리적으로 표현할 수 없다.

### 두 외래 키의 조합은 대부분 DB 가 막는다

각각의 외래 키가 유효해도 조합이 틀릴 수 있다. **리뷰가 A 상품을 가리키는데 근거인 주문 상품은 B 상품인 경우**가 그렇다.

이 문제의 뿌리는 모든 표가 대리 키를 쓴다는 것이다. 자연 키 설계였다면 조상 키가 자식으로 전파되어
`claim_item` 의 키에 `order_id` 가 이미 들어 있었을 것이고, 다른 주문의 항목을 넣을 수가 없었을 것이다.
대리 키를 쓰면 그 전파가 끊긴다. **복합 외래 키는 끊긴 전파를 선택적으로 되살리는 표준 기법**이고,
복제한 값이 외래 키로 강제되므로 어긋날 수 없다는 점에서 일반적인 비정규화와 성격이 다르다.

여섯 중 넷은 **애초에 중복이라 컬럼을 지워 없앴다.** 저장하지 않으면 틀릴 수 없다.

| 없앤 컬럼 | 어디서 유도되나 |
|---|---|
| `refund.payment_id` | `claim.order_id -> payment`. `uk_payment_order` 가 주문당 결제 1건을 보장한다 |
| `stock_disposal.product_id` | `stock_lot -> product_option -> product`. `stock_lot_id` 를 `NOT NULL` 로 바꿔 유도가 항상 성립한다 |

나머지는 복합 외래 키 열로 막는다.

| 표 | 공유하는 조상 키 | 막는 것 |
|---|---|---|
| `claim_item` | `order_id` | 다른 주문의 주문 상품을 클레임에 넣는 것 |
| `orders` | `member_id` | 남의 쿠폰을 주문에 붙이는 것 |
| `order_item` | `member_id`, `coupon_id` | 남의 쿠폰을 붙이는 것, 대상이 아닌 옵션에 쓰는 것 |
| `review` | `product_option_id`, `member_id` | 다른 상품에 리뷰를 쓰거나 남의 구매로 쓰는 것 |
| `coupon_product_option` | `scope` | 장바구니 쿠폰에 대상 옵션을 다는 것 |

`review` 는 사슬이 둘이다. `order_item` 이 `product_id` 를 갖지 않고 `product_option_id` 만 갖기 때문에
가운데 고리로 `product_option_id` 를 복제해야 두 외래 키가 이어진다.

```
review(order_item_id, product_option_id, member_id) -> order_item
review(product_option_id, product_id)               -> product_option
```

`product_id` 를 지우고 조인으로 유도할 수도 있었지만, 상품별 리뷰 목록이 상품 상세마다 도는 경로라
**컬럼 하나를 더해 조회 성능을 지키고 강제도 얻는 쪽**을 택했다.

부모마다 참조 대상 UNIQUE 가 하나씩 필요하다. `claim_id` 처럼 이미 PK 인 컬럼이라도
그 조합에 인덱스가 있어야 외래 키 대상이 될 수 있다.

### 목록 검사도 외래 키로 옮겼다

"이 라인의 옵션이 그 쿠폰의 대상 목록에 있는가" 는 언뜻 `EXISTS` 검사라 외래 키로 표현할 수 없어 보인다.
그런데 **목록 자체가 표이므로 그 표를 참조하면 된다.**

```sql
order_item.coupon_id 를 복제하고
  FOREIGN KEY (member_coupon_id, coupon_id)    -> member_coupon      그 쿠폰이 맞는지
  FOREIGN KEY (coupon_id, product_option_id)   -> coupon_product_option  그 대상에 이 옵션이 있는지
```

두 번째 외래 키가 참조할 행이 없으면 `INSERT` 가 거부된다. **대상이 아닌 옵션에는 쿠폰이 붙지 않는다.**

전제는 **`ITEM` 쿠폰이 대상 옵션을 반드시 하나 이상 갖는 것**이다.
원래는 "행이 없으면 대상 제한 없음" 이었는데 그 규칙을 버렸다.
**부재를 의미로 쓰면 사고가 조용하다.** 관리자가 대상을 실수로 전부 지우면 전 상품 할인이 되고,
데이터가 사라진 것인지 원래 없었던 것인지 구분할 수 없다.
아무 상품에나 쓰는 쿠폰이 필요하면 `scope='ORDER'` 가 그 자리다.

`coupon_product_option` 쪽도 같은 기법이다. `scope` 를 복제해 `CHECK (scope = 'ITEM')` 으로 못 박고
`FOREIGN KEY (coupon_id, scope) -> coupon (coupon_id, scope)` 를 걸면
장바구니 쿠폰은 이 표에 행을 가질 수 없다.

**결과로 `DI-3-05` 로 남는 조합 검증이 없다.** 여섯 중 둘은 컬럼을 지워 없앴고 넷은 외래 키로 막는다.

### 자식 행 합계 (`DI-3-06`)

행 하나씩은 유효한데 합이 넘는 경우다. **CHECK 는 자기 행만 보므로 원리적으로 표현할 수 없다.**

클레임 수량이 그런 자리였다. 3개 산 것을 2개짜리 반품 두 건으로 나누면 각 행은 정상이고 합만 넘는다.
**`claim_item` 에서 수량을 없애 이 문제를 만들지 않기로 했다.**

클레임은 라인 단위로 걸고 부분 수량을 지정하지 않는다.
중복은 `order_item.item_status` 조건부 UPDATE 가 막는다.

```sql
UPDATE order_item SET item_status = 'RETURN_REQ'
 WHERE order_item_id = ? AND item_status = 'ORDERED';
-- affected rows 0 이면 이미 클레임이 걸린 라인이다
```

수량을 유지하려면 `order_item` 에 카운터를 하나 더 만들어야 했다.
`stock_lot.available_qty`, `coupon.issued_quantity` 에 이어 세 번째이고, 각각 되돌리기 경로와 정합성 검사가 따라붙는다.
**부분 수량 반품이 필요해지면 `claim_item.qty` 와 `order_item.claimed_qty` 를 추가만 하는 마이그레이션으로 넣는다.**

남는 것은 하나다.

| 위치 | 확인할 것 |
|---|---|
| `order_item.discount_amount` | 합이 `orders.discount_amount` 와 같은가 |

주문 생성 한 트랜잭션 안에서 함께 쓰이는 값이라 동시성 문제가 없고, 배분 계산이 맞는지만 확인하면 된다.

### 조건부 유일성 중 앱으로 내린 것

없다. 넷은 계산 컬럼으로, 둘은 재사용을 포기해 평범한 UNIQUE 로 처리했다.

---

## 10. 의도적으로 넣지 않은 것

| | 이유 |
|---|---|
| 외부 노출 식별자 (`public_id`) | API 가 설계되지 않아 어느 표가 단독 지목 대상인지 답할 수 없다. 도입할 때는 추가만 하는 마이그레이션으로 얹는다 |
| 포인트 | 적립 시점, 소멸 정책, 잔액을 원장 합으로 낼지 컬럼으로 둘지가 정해지지 않았다 |
| 등급 할인 | `member_grade` 는 등급 구분만 하고 혜택을 갖지 않는다. 혜택 형태를 정할 때 함께 본다 |
| 알림 | 발송 대상 리소스를 가리키는 참조 형태가 정해지지 않았다 |
| 부분 반품 후 쿠폰 조건 위반 처리 | 안분액만 회수할지, 쿠폰을 무효화하고 재계산할지 정하지 않았다. 후자는 추가 결제가 필요해질 수 있다 |

---

## 11. 검증 상태

**이 스키마는 아직 실행된 적이 없다.** 정적 검사만 거쳤다.

MySQL 8.4 컨테이너에 올려 35개 표(당시)를 만들고 제약 18건이 실제로 동작하는지 확인한 적은 있으나,
그 이후 쿠폰 재설계, `DATETIME(6)` 전환, 계산 컬럼 정리가 들어갔다.
Flyway 로 한 번에 실행해 보는 것이 다음 순서다.
