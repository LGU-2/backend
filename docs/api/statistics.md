# 통계

**전부 관리자 전용이다.** 판매 집계와 소진율이며, 원천은 `daily_sales` 테이블이다.

집계 단위는 상품이 아니라 **옵션(`product_option`)** 이다. 재고와 소비기한이 옵션 단위라
집계도 같은 단위여야 한다. **상품 단위 수치는 옵션을 합산해 얻는다. 반대 방향은 불가능하다.**

## 판매 집계

### 조회

```
GET /v1/admin/statistics/sales?from=2026-08-01&to=2026-08-17&categoryId=&productId=
```

| 파라미터 | 필수 | 설명 |
|---|---|---|
| `from`, `to` | O | 집계 일자 범위 |
| `categoryId` | | 카테고리로 좁힌다 |
| `productId` | | 상품으로 좁힌다 |

```json
{
  "rows": [
    {
      "statDate": "2026-08-17",
      "productOptionId": 31,
      "productName": "제주 감귤 1kg",
      "optionName": "1kg",
      "openingStock": 200,
      "inboundQty": 0,
      "restockedQty": 3,
      "soldQty": 57,
      "soldAmount": 735300,
      "disposedQty": 0,
      "expiredQty": 0
    }
  ]
}
```

| 필드 | 뜻 |
|---|---|
| `openingStock` | 그날 시작 시점의 가용 재고 스냅샷 |
| `inboundQty` | 당일 신규 입고 |
| `restockedQty` | 당일 반품 재입고 |
| `soldQty`, `soldAmount` | **결제 완료 기준** |
| `disposedQty` | 당일 폐기 |
| `expiredQty` | 당일 만료 전환 |

**`openingStock` 은 다음 날 행의 기초 재고이자 이 날의 기말이다.** 그래서 배치는 움직임이 없는
옵션에도 행을 만든다. 날짜가 끊기면 이 연결이 깨진다.

**재고 대조는 이 표로 하지 않는다.** `stock_movement` 의 `qty_before` 와 `qty_after` 가
그 용도다. 이 표는 집계용이다.

### 배치 실행

```
POST /v1/admin/statistics/sales:aggregate
```

```json
{ "statDate": "2026-08-17" }
```

하루 한 번 도는 배치이며 이 경로는 수동 실행과 재집계용이다.

**같은 일자를 다시 돌리면 덮어쓴다.** `(product_option_id, stat_date)` UNIQUE 로 행이 하나만
유지되고, 재실행해도 같은 결과가 나온다.

| 오류 | 코드 | 언제 |
|---|---|---|
| `422` | `STAT-001` | 미래 일자다 |

## 소진율

```
GET /v1/admin/statistics/sell-through?from=&to=&categoryId=
```

```json
{
  "rows": [
    { "productOptionId": 31, "productName": "제주 감귤 1kg", "sellThroughRate": 0.72 }
  ]
}
```

계산식은 이렇다.

```
소진율 = 기간 soldQty 합
        ────────────────────────────────────────
        기간 시작 openingStock + 기간 inboundQty 합
```

**`restockedQty` 는 분모에 넣지 않는다.** 새로 들여온 물량이 아니라 팔았다가 돌아온 것이라,
분모에 넣으면 소진율이 실제보다 낮아 보인다.

**절대 판매량으로 저조를 판단하지 않는다.** 그러면 갓 입고한 상품이 무조건 저조로 잡힌다.

| 오류 | 코드 | 언제 |
|---|---|---|
| `422` | `STAT-002` | 분모가 0이다. **확보 재고가 없으면 산출 대상에서 제외한다** |

## 폐기율

```
GET /v1/admin/statistics/disposal?from=&to=&categoryId=
```

폐기 등록이 원천이다. 사유별로 나눠 준다.

```json
{
  "rows": [
    {
      "productOptionId": 31,
      "disposedQty": 18,
      "byReason": { "EXPIRED": 12, "DAMAGED": 2, "RETURNED": 4 }
    }
  ]
}
```

**`RETURNED` 는 가용 재고를 줄이지 않은 폐기다.** 회수품이 판매 재고로 돌아온 적이 없기 때문이다.
그래서 `daily_sales.disposedQty` 에는 들어가지 않는다. 이 경로는 원장에서 직접 센다.

## 쿠폰 캠페인과의 연결

소진율과 소비기한이 **선착순 쿠폰 캠페인의 대상 선정 기준**이다.

```
소비기한 임박 (기본 10일 이내)  +  소진율 저조
        |
        v
쿠폰 대상 옵션으로 등록
```

같은 기준을 [product.md](./product.md) 의 소비기한 임박 상품 조회가 쓴다.
**두 곳이 다른 기준을 쓰면 캠페인 대상과 노출 상품이 어긋난다.**
