-- 초기 스키마.
--
-- 원본에 있던 DROP TABLE IF EXISTS 블록은 옮기지 않았다.
-- Flyway 마이그레이션은 전진 전용이며, 되돌리는 문장이 섞이면 재실행이나 baseline 설정이
-- 어긋났을 때 운영 데이터를 지운다. 로컬 초기화는 compose down -v 로 한다.
--
-- 외부 노출 식별자(public_id)는 이 스키마에 넣지 않는다. 추후 고려한다.
-- 지금은 API 가 설계되지 않아 어느 테이블이 단독 지목 대상인지 답할 수 없다.
-- 설계와 판단 기준은 docs/code-architecture/identifier-strategy-guideline.md 에 있다.
-- 도입할 때는 추가만 하는 마이그레이션(V2)으로 컬럼과 UNIQUE 를 얹는다. 이 파일을 고치지 않는다.

-- =====================================================================
-- 1. 회원 / 권한
-- =====================================================================

CREATE TABLE member_grade (
    member_grade_id BIGINT       NOT NULL AUTO_INCREMENT, -- 등급 PK
    name            VARCHAR(50)  NOT NULL, -- 등급명(브론즈/실버/골드. 선착순 등급별 쿠폰 캠페인과 연동)
    discount_rate   DECIMAL(5,2) NOT NULL DEFAULT 0.00, -- 등급 할인율(%)
    promotion_rule  VARCHAR(255) NULL, -- 승급 기준
    is_default      BOOLEAN      NOT NULL DEFAULT TRUE, -- 회원가입 시 부여할 기본 등급 여부(정확히 1개여야 함)
    is_default_key  TINYINT GENERATED ALWAYS AS (CASE WHEN is_default THEN 1 ELSE NULL END), -- is_default=TRUE가 항상 1개임을 DB가 강제하기 위한 계산 컬럼
    created_at      DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME     NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (member_grade_id),
    UNIQUE KEY uk_member_grade_name (name),
    UNIQUE KEY uk_member_grade_single_default (is_default_key),
    CONSTRAINT chk_member_grade_discount_rate CHECK (discount_rate BETWEEN 0 AND 100)
); -- 회원 등급

CREATE TABLE member (
    member_id        BIGINT       NOT NULL AUTO_INCREMENT, -- member PK
    provider         VARCHAR(30)  NOT NULL DEFAULT 'KAKAO', -- 인증 제공자(카카오 OIDC. 확장 대비 컬럼)
    provider_user_id VARCHAR(100) NOT NULL, -- 카카오 회원번호(OIDC sub). 로그 평문 출력 금지
    email            VARCHAR(255) NULL, -- 카카오 제공 이메일(원문, 암호화 저장 대상). 동의 안 하면 NULL 가능
    nickname         VARCHAR(50)  NULL, -- 카카오 제공 닉네임
    name             VARCHAR(50)  NULL, -- 이름(폼 입력, 원문-암호화 대상). PENDING_PROFILE에서는 NULL
    phone            VARCHAR(20)  NULL, -- 휴대전화(폼 입력, 원문-암호화 대상). PENDING_PROFILE에서는 NULL
    member_grade_id  BIGINT       NOT NULL, -- 회원 등급 FK
    is_marketing_agreed BOOLEAN NOT NULL DEFAULT FALSE, -- 마케팅 수신 동의
    status           VARCHAR(30)  NOT NULL DEFAULT 'PENDING_PROFILE', -- 회원 상태(PENDING_PROFILE 카카오 최초 로그인 후 추가정보 미입력/ACTIVE 활성/BLOCKED 차단/WITHDRAWN 탈퇴)
    refresh_token_hash CHAR(64)   NULL, -- 리프레시 토큰의 SHA-256 hex. 평문을 저장하지 않는다(유출되면 그대로 계정 탈취가 된다). 고엔트로피 난수라 bcrypt 가 아니라 단순 해시로 충분하다. NULL 은 로그아웃 상태이며, 액세스 토큰이 stateless 라 서버가 막을 수 있는 유일한 지점이 여기다
    refresh_token_expires_at DATETIME NULL, -- 리프레시 토큰 만료 시각. 지나면 재로그인을 요구한다. 컬럼이 하나라 기기 한 대만 로그인이 유지되며, 다중 기기가 필요해지면 별도 테이블로 뺀다
    deleted_at       DATETIME     NULL, -- 소프트딜리트(탈퇴 시, 주문 이력은 법정 기간 보존)
    active_provider_key VARCHAR(140) GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN CONCAT(provider, ':', provider_user_id) ELSE NULL END), -- 탈퇴 후 같은 카카오 계정 재가입 허용: 활성 회원만 유일성 강제
    created_at      DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME     NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (member_id),
    CONSTRAINT chk_member_status CHECK (status IN ('PENDING_PROFILE','ACTIVE','BLOCKED','WITHDRAWN')),
    CONSTRAINT chk_member_refresh_token CHECK ( -- 둘 다 있거나 둘 다 없거나. 해시만 남고 만료가 NULL 이면 영구 토큰이 된다
        (refresh_token_hash IS NULL     AND refresh_token_expires_at IS NULL)
     OR (refresh_token_hash IS NOT NULL AND refresh_token_expires_at IS NOT NULL)),
    CONSTRAINT chk_member_withdrawn CHECK ( -- 탈퇴가 status 와 deleted_at 두 곳에 표현되어 어긋날 수 있다. 둘을 묶는다
        (status =  'WITHDRAWN' AND deleted_at IS NOT NULL)
     OR (status <> 'WITHDRAWN' AND deleted_at IS NULL)),
    UNIQUE KEY uk_member_active_provider (active_provider_key), -- 활성 회원 한정 카카오 계정당 1회원(탈퇴 행은 NULL이라 제외)
    CONSTRAINT fk_member_grade FOREIGN KEY (member_grade_id) REFERENCES member_grade (member_grade_id)
); -- 회원(카카오 OIDC 인증. 자체 비밀번호 미보관, 인증은 카카오에 위임)

CREATE TABLE address (
    address_id      BIGINT       NOT NULL AUTO_INCREMENT, -- address PK
    member_id       BIGINT       NOT NULL, -- member FK
    recipient       VARCHAR(50)  NOT NULL, -- 수령인
    phone           VARCHAR(20)  NOT NULL, -- 연락처(원문, 암호화 추후 적용)
    zipcode         VARCHAR(10)  NOT NULL, -- 우편번호
    road_address    VARCHAR(255) NOT NULL, -- 도로명 주소
    detail_address  VARCHAR(255) NULL, -- 상세 주소
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE, -- 기본 배송지
    is_default_key  BIGINT GENERATED ALWAYS AS (CASE WHEN is_default THEN member_id ELSE NULL END), -- 회원별로 is_default=TRUE가 최대 1개임을 DB가 강제하기 위한 계산 컬럼
    created_at      DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME     NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (address_id),
    UNIQUE KEY uk_address_single_default_per_member (is_default_key),
    CONSTRAINT fk_address_member FOREIGN KEY (member_id) REFERENCES member (member_id)
); -- 회원 배송지

CREATE TABLE admin (
    admin_id        BIGINT       NOT NULL AUTO_INCREMENT, -- admin PK
    login_id        VARCHAR(50)  NOT NULL, -- 관리자 로그인 아이디
    password_hash   VARCHAR(255) NOT NULL, -- BCrypt 단방향 해시
    name            VARCHAR(50)  NOT NULL, -- 관리자 이름
    role            VARCHAR(30)  NOT NULL, -- RBAC 권한(SUPER_ADMIN/ADMIN)
    created_at      DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME     NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (admin_id),
    CONSTRAINT chk_admin_role CHECK (role IN ('SUPER_ADMIN','ADMIN')),
    UNIQUE KEY uk_admin_login_id (login_id)
); -- 관리자

-- =====================================================================
-- 2. 상품 / 재고
-- =====================================================================

CREATE TABLE category (
    category_id     BIGINT       NOT NULL AUTO_INCREMENT, -- 카테고리 PK
    parent_id       BIGINT       NULL, -- 상위 카테고리(확장용)
    name            VARCHAR(50)  NOT NULL, -- 해산물/육류/채소/과일
    created_at      DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME     NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (category_id),
    UNIQUE KEY uk_category_parent_name (parent_id, name),
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES category (category_id)
); -- 카테고리

CREATE TABLE supplier (
    supplier_id     BIGINT       NOT NULL AUTO_INCREMENT, -- 공급처 PK
    name            VARCHAR(100) NOT NULL, -- 공급처
    contact         VARCHAR(100) NULL, -- 연락처
    created_at      DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME     NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (supplier_id),
    UNIQUE KEY uk_supplier_name (name)
); -- 공급처

CREATE TABLE product (
    product_id          BIGINT       NOT NULL AUTO_INCREMENT, -- product PK
    product_code        VARCHAR(50)  NOT NULL, -- 자동생성 상품코드
    name                VARCHAR(255) NOT NULL, -- 상품명
    category_id         BIGINT       NOT NULL, -- 카테고리 FK
    supplier_id         BIGINT       NOT NULL, -- 공급처 FK
    sale_status         VARCHAR(30)  NOT NULL DEFAULT 'ON_SALE', -- 판매 상태(ON_SALE/SOLD_OUT/OFF_SALE)
    storage_type        VARCHAR(30)  NOT NULL, -- 보관 온도(ROOM 실온/COLD 냉장/FROZEN 냉동)
    min_shelf_life_days INT          NOT NULL DEFAULT 0, -- 판매 최소 잔여 소비기한 N
    description         TEXT         NULL, -- 상품 설명
    deleted_at          DATETIME     NULL, -- 소프트딜리트
    active_code_key     VARCHAR(50)  GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN product_code ELSE NULL END), -- 삭제 후 같은 상품코드 재등록 허용: 활성 상품만 유일성 강제
    created_at      DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME     NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (product_id),
    CONSTRAINT chk_product_sale_status CHECK (sale_status IN ('ON_SALE','SOLD_OUT','OFF_SALE')),
    CONSTRAINT chk_product_storage_type CHECK (storage_type IN ('ROOM','COLD','FROZEN')),
    UNIQUE KEY uk_product_active_code (active_code_key), -- 활성 상품 한정 코드 유일(삭제 행은 NULL이라 제외)
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category (category_id),
    CONSTRAINT fk_product_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (supplier_id),
    CONSTRAINT chk_product_shelf CHECK (min_shelf_life_days >= 0)
); -- 상품

CREATE TABLE product_option (
    product_option_id BIGINT       NOT NULL AUTO_INCREMENT, -- product_option PK
    product_id        BIGINT       NOT NULL, -- 상품 FK
    name              VARCHAR(100) NOT NULL, -- 옵션명(예: 200g, 500g, 1kg)
    price             INT          NOT NULL, -- 옵션 판매가(0 이상)
    sale_status       VARCHAR(30)  NOT NULL DEFAULT 'ON_SALE', -- 옵션 판매 상태(ON_SALE/SOLD_OUT/OFF_SALE)
    created_at        DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at        DATETIME     NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (product_option_id),
    UNIQUE KEY uk_option_product_name (product_id, name), -- 한 상품 내 옵션명 중복 방지
    CONSTRAINT fk_option_product FOREIGN KEY (product_id) REFERENCES product (product_id),
    CONSTRAINT chk_option_price CHECK (price >= 0),
    CONSTRAINT chk_option_sale_status CHECK (sale_status IN ('ON_SALE','SOLD_OUT','OFF_SALE'))
); -- 상품 옵션(판매 단위 SKU: 가격, 재고 기준)

CREATE TABLE product_image (
    product_image_id BIGINT       NOT NULL AUTO_INCREMENT, -- product_image PK
    product_id       BIGINT       NOT NULL, -- 상품 FK
    upload_id        BINARY(16)   NOT NULL, -- 업로드 세션 식별자(UUID v7). presigned 발급 때 서버가 만들어 클라이언트에 주고, 완료 통지에서 돌려받아 이 행을 찾는다. key 를 클라이언트에 주지 않기 위한 것이며(INF-11-04) 리소스 식별자가 아니다
    object_key       VARCHAR(255) NOT NULL, -- S3 객체 key (예: products/ab/3f9c1d2e.jpg). URL 을 통째로 저장하지 않는다 (INF-11-05). 도메인은 환경마다 달라 설정에서 붙인다
    upload_status    VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING 발급만 됨 / CONFIRMED HeadObject 로 객체 존재와 서명 조건 일치를 확인함(INF-11-10). 통지를 받은 것만으로는 확정하지 않는다. 조회는 CONFIRMED 만 노출한다(INF-11-09)
    sort_order       INT          NOT NULL DEFAULT 0, -- 상품 안에서의 표시 순서(작을수록 앞). 확정할 때 서버가 MAX+1로 정한다. (product_id, sort_order) 유일성은 일부러 걸지 않는다. 재정렬의 중간 상태가 항상 위반이 되기 때문이며, 결정적 순서는 조회 정렬의 product_image_id 타이브레이커로 얻는다
    is_main          BOOLEAN      NOT NULL DEFAULT FALSE, -- 대표 이미지 여부
    is_main_key      BIGINT GENERATED ALWAYS AS (CASE WHEN is_main THEN product_id ELSE NULL END), -- 상품별로 is_main=TRUE가 최대 1개임을 DB가 강제하기 위한 계산 컬럼
    created_at       DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at       DATETIME     NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (product_image_id),
    UNIQUE KEY uk_product_image_upload (upload_id),
    UNIQUE KEY uk_product_image_key (object_key), -- 완료 통지를 두 번 받아도 같은 key 가 두 행이 되지 않는다
    UNIQUE KEY uk_product_image_single_main (is_main_key), -- 상품당 대표 최대 1개(대표가 아닌 행은 NULL이라 제외). 교체할 때는 옛 대표를 먼저 내려야 위반이 나지 않는다
    KEY idx_product_image_pending (upload_status, created_at), -- 미확정 행 스윕(조회 확정 대상 조회와 정리 배치)이 풀스캔이 되지 않도록
    CONSTRAINT chk_product_image_status CHECK (upload_status IN ('PENDING','CONFIRMED')),
    CONSTRAINT chk_product_image_main CHECK (is_main = FALSE OR upload_status = 'CONFIRMED'), -- 확정 전인 행이 대표가 되면 상품 목록에 깨진 이미지가 나간다
    CONSTRAINT fk_image_product FOREIGN KEY (product_id) REFERENCES product (product_id)
); -- 상품 이미지. 크기와 Content-Type 은 저장하지 않는다(S3 객체 메타데이터가 진실이고 조회는 브라우저가 직접 받는다)

CREATE TABLE stock_lot (
    stock_lot_id          BIGINT       NOT NULL AUTO_INCREMENT, -- stock_lot PK
    product_option_id BIGINT     NOT NULL, -- 옵션 FK(로트는 옵션 단위)
    received_date   DATE         NOT NULL, -- 입고일
    expiry_date     DATE         NOT NULL, -- 소비기한
    initial_qty     INT          NOT NULL, -- 입고수량
    available_qty   INT          NOT NULL, -- 판매 가능 수량. 예약(RESERVE)에서 빼고 예약 해제(RELEASE)에서 되돌린다. 차감 확정(CONFIRM)은 이 값을 바꾸지 않는다. 예약 시점에 이미 뺐기 때문이며 여기서 또 빼면 이중 차감이 된다
    status          VARCHAR(30)  NOT NULL DEFAULT 'AVAILABLE', -- 로트 상태(AVAILABLE/SOLD_OUT/DISPOSED)
    created_at      DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME     NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (stock_lot_id),
    CONSTRAINT chk_lot_status CHECK (status IN ('AVAILABLE','SOLD_OUT','DISPOSED')),
    KEY idx_lot_fefo (product_option_id, status, expiry_date), -- FEFO 조회 최적화: 옵션+상태별 소비기한 임박순
    CONSTRAINT fk_lot_option FOREIGN KEY (product_option_id) REFERENCES product_option (product_option_id),
    CONSTRAINT chk_lot_qty CHECK (available_qty >= 0 AND available_qty <= initial_qty),
    CONSTRAINT chk_lot_expiry_date CHECK (expiry_date >= received_date),
    CONSTRAINT chk_lot_status_qty CHECK (status = 'AVAILABLE' OR available_qty = 0) -- 소진/폐기된 로트에 가용재고가 남아 있으면 FEFO 조회가 없는 재고를 집는다
); -- 입고 로트(실재고 단위, 공급처는 product.supplier_id 기준)

-- =====================================================================
-- 3. 장바구니
-- =====================================================================

CREATE TABLE cart (
    cart_id         BIGINT       NOT NULL AUTO_INCREMENT, -- cart PK
    member_id       BIGINT       NOT NULL, -- 회원 FK(회원당 1개 UK)
    created_at      DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME     NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (cart_id),
    UNIQUE KEY uk_cart_member (member_id), -- 1인 1카트
    CONSTRAINT fk_cart_member FOREIGN KEY (member_id) REFERENCES member (member_id)
); -- 장바구니

CREATE TABLE cart_item (
    cart_item_id    BIGINT       NOT NULL AUTO_INCREMENT, -- cart_item PK
    cart_id         BIGINT       NOT NULL, -- 장바구니 FK
    product_option_id BIGINT     NOT NULL, -- 담은 옵션 FK
    qty             INT          NOT NULL DEFAULT 1, -- 수량
    created_at      DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME     NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (cart_item_id),
    UNIQUE KEY uk_cart_option (cart_id, product_option_id), -- 동일 옵션 중복 방지
    CONSTRAINT fk_cartitem_cart FOREIGN KEY (cart_id) REFERENCES cart (cart_id),
    CONSTRAINT fk_cartitem_option FOREIGN KEY (product_option_id) REFERENCES product_option (product_option_id),
    CONSTRAINT chk_cartitem_qty CHECK (qty > 0)
); -- 장바구니 상품

-- =====================================================================
-- 4. 주문 / 결제
-- =====================================================================

CREATE TABLE orders (
    order_id        BIGINT       NOT NULL AUTO_INCREMENT, -- orders PK
    order_no        VARCHAR(30)  NOT NULL, -- 주문번호
    member_id       BIGINT       NOT NULL, -- 주문 회원 FK
    status          VARCHAR(30)  NOT NULL DEFAULT 'PAYMENT_PENDING', -- 주문 상태(PAYMENT_PENDING/PAID/PRODUCT_PREPARING/SHIPMENT_PREPARING/SHIPPING/DELIVERED/CONFIRMED/RETURN_REQUESTED/RETURNED/EXCHANGE_REQUESTED/EXCHANGED/CANCELED)
    product_amount  INT          NOT NULL, -- 총상품금액
    discount_amount INT          NOT NULL DEFAULT 0, -- 쿠폰+등급+포인트
    shipping_fee    INT          NOT NULL DEFAULT 0, -- 배송비
    total_amount    INT          NOT NULL, -- 최종결제금액. 전액 쿠폰/포인트면 0이 될 수 있고, 그 주문은 payment 행 없이 바로 PAID 가 된다
    earned_point    INT          NOT NULL DEFAULT 0, -- 적립예정포인트
    ship_recipient  VARCHAR(50)  NOT NULL, -- 배송지 스냅샷
    ship_phone      VARCHAR(20)  NOT NULL, -- 연락처(원문, 암호화 추후 적용)
    ship_zipcode    VARCHAR(10)  NOT NULL, -- 배송지 우편번호 스냅샷
    ship_address    VARCHAR(500) NOT NULL, -- 배송지 주소 스냅샷
    ship_message    VARCHAR(255) NULL, -- 배송 메시지
    ordered_at      DATETIME     NOT NULL, -- 주문 접수 시각(서버 애플리케이션이 기록)
    created_at      DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME     NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (order_id),
    CONSTRAINT chk_order_status CHECK (status IN ('PAYMENT_PENDING','PAID','PRODUCT_PREPARING','SHIPMENT_PREPARING','SHIPPING','DELIVERED','CONFIRMED','RETURN_REQUESTED','RETURNED','EXCHANGE_REQUESTED','EXCHANGED','CANCELED')),
    UNIQUE KEY uk_order_no (order_no),
    CONSTRAINT fk_order_member FOREIGN KEY (member_id) REFERENCES member (member_id),
    CONSTRAINT chk_order_amounts CHECK (product_amount >= 0 AND discount_amount >= 0 AND shipping_fee >= 0 AND total_amount >= 0 AND earned_point >= 0),
    CONSTRAINT chk_order_total CHECK (total_amount = product_amount - discount_amount + shipping_fee) -- 합계가 항목과 맞는지 DB가 강제한다. 각 항목이 0 이상인 것만 봐서는 total_amount가 아무 값이나 될 수 있다
); -- 주문(헤더)

CREATE TABLE order_item (
    order_item_id   BIGINT       NOT NULL AUTO_INCREMENT, -- order_item PK
    order_id        BIGINT       NOT NULL, -- 주문 FK
    product_option_id BIGINT     NOT NULL, -- 주문 옵션 FK
    name_snapshot   VARCHAR(255) NOT NULL, -- 주문시점 상품명
    option_name_snapshot VARCHAR(100) NOT NULL, -- 주문시점 옵션명
    unit_price      INT          NOT NULL, -- 주문시점 가격
    qty             INT          NOT NULL, -- 주문 수량
    item_status     VARCHAR(30)  NOT NULL DEFAULT 'ORDERED', -- 주문 상품 상태(ORDERED/CANCELED/RETURN_REQ/RETURNED/EXCHANGE_REQ/EXCHANGED)
    created_at      DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME     NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (order_item_id),
    CONSTRAINT chk_orderitem_status CHECK (item_status IN ('ORDERED','CANCELED','RETURN_REQ','RETURNED','EXCHANGE_REQ','EXCHANGED')),
    UNIQUE KEY uk_order_option (order_id, product_option_id), -- 동일 옵션 중복 라인 방지
    CONSTRAINT fk_orderitem_order FOREIGN KEY (order_id) REFERENCES orders (order_id),
    CONSTRAINT fk_orderitem_option FOREIGN KEY (product_option_id) REFERENCES product_option (product_option_id),
    CONSTRAINT chk_orderitem_qty CHECK (qty > 0)
); -- 주문 상품

CREATE TABLE stock_allocation (
    stock_allocation_id   BIGINT       NOT NULL AUTO_INCREMENT, -- stock_allocation PK
    order_item_id   BIGINT       NOT NULL, -- 주문 상품 FK
    stock_lot_id          BIGINT       NOT NULL, -- 차감 대상 로트 FK
    qty             INT          NOT NULL, -- FEFO 예약/차감 수량
    status          VARCHAR(30)  NOT NULL DEFAULT 'RESERVED', -- RESERVED=예약(주문 시점. 이때 stock_lot.available_qty 를 뺀다), CONFIRMED=차감 확정(결제 시점. available_qty 는 예약 때 이미 빠져 있어 바뀌지 않는다), RELEASED=해제(결제 취소/만료. available_qty 를 되돌린다)
    created_at      DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME     NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (stock_allocation_id),
    CONSTRAINT fk_alloc_orderitem FOREIGN KEY (order_item_id) REFERENCES order_item (order_item_id),
    CONSTRAINT fk_alloc_lot FOREIGN KEY (stock_lot_id) REFERENCES stock_lot (stock_lot_id),
    CONSTRAINT chk_alloc_qty CHECK (qty > 0),
    CONSTRAINT chk_alloc_status CHECK (status IN ('RESERVED','CONFIRMED','RELEASED'))
); -- 주문상품-로트 할당(예약/차감 이력. status로 예약->확정->해제 추적)

CREATE TABLE stock_disposal (
    stock_disposal_id     BIGINT       NOT NULL AUTO_INCREMENT, -- stock_disposal PK
    product_id      BIGINT       NOT NULL, -- 상품 FK. DB가 못 막는 조합이다(DI-3-05). 로트를 지정했다면 그 로트가 이 상품의 옵션에 속하는지 앱이 확인한다
    stock_lot_id          BIGINT       NULL, -- 폐기 대상 로트 FK
    admin_id        BIGINT       NOT NULL, -- 폐기 처리 관리자 FK
    qty             INT          NOT NULL, -- 폐기수량
    reason          VARCHAR(30)  NOT NULL, -- 폐기 사유(EXPIRED 소비기한/DAMAGED 손상/RETURNED 회수품). RETURNED 는 잔여 소비기한이 product.min_shelf_life_days 에 못 미치거나 상태가 나빠 재입고(RESTOCK)하지 않은 회수품이다. 로트로 돌아간 적이 없으므로 available_qty 를 줄이지 않으며 stock_movement 행도 남지 않는다
    created_at      DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    PRIMARY KEY (stock_disposal_id),
    CONSTRAINT chk_disposal_reason CHECK (reason IN ('EXPIRED','DAMAGED','RETURNED')),
    CONSTRAINT fk_disposal_product FOREIGN KEY (product_id) REFERENCES product (product_id),
    CONSTRAINT fk_disposal_lot FOREIGN KEY (stock_lot_id) REFERENCES stock_lot (stock_lot_id),
    CONSTRAINT fk_disposal_admin FOREIGN KEY (admin_id) REFERENCES admin (admin_id),
    CONSTRAINT chk_disposal_qty CHECK (qty > 0)
); -- 폐기 이력

CREATE TABLE stock_movement (
    stock_movement_id BIGINT       NOT NULL AUTO_INCREMENT, -- stock_movement PK
    stock_lot_id      BIGINT       NOT NULL, -- 변동이 일어난 로트 FK
    movement_type     VARCHAR(30)  NOT NULL, -- 변동 유형(INBOUND 신규 입고/RESTOCK 반품 재입고/RESERVE 예약/CONFIRM 차감확정/RELEASE 예약해제/DISPOSE 폐기/EXPIRE 만료전환/ADJUST 수동조정). RESTOCK 은 회수한 물건을 원래 로트로 되돌린다. 소비기한이 로트에 달려 있어 다른 로트로 넣으면 기한을 잃기 때문이며, 어느 로트였는지는 claim_item -> order_item -> stock_allocation 으로 찾는다. 잔여 소비기한이 product.min_shelf_life_days 에 못 미치면 되돌리지 않고 폐기한다
    quantity          INT          NOT NULL, -- 변동 수량(절대값, 증감 방향은 movement_type으로 판단)
    qty_before        INT          NOT NULL, -- 변동 전 로트 available_qty
    qty_after         INT          NOT NULL, -- 변동 후 로트 available_qty. CONFIRM 은 두 값이 같다. 이 유형은 재고를 옮기지 않고 예약이 확정으로 넘어간 사실만 남기기 때문이며, movement_type 별로 qty_after 를 검사하려면 이전 행을 봐야 해서 CHECK 로는 막을 수 없다
    order_id          BIGINT       NULL, -- 관련 주문 FK(주문 기인 변동만, 그 외 NULL)
    admin_id          BIGINT       NULL, -- 처리 관리자 FK(수동조정/폐기 등, 시스템 자동은 NULL)
    reason            VARCHAR(200) NULL, -- 사유 상세(폐기/만료/조정 등)
    created_at        DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    PRIMARY KEY (stock_movement_id),
    KEY idx_movement_lot_time (stock_lot_id, created_at), -- 로트별 시간순 조회(정합성 추적)
    KEY idx_movement_order (order_id), -- 주문별 변동 추적
    CONSTRAINT fk_movement_lot FOREIGN KEY (stock_lot_id) REFERENCES stock_lot (stock_lot_id),
    CONSTRAINT fk_movement_order FOREIGN KEY (order_id) REFERENCES orders (order_id),
    CONSTRAINT fk_movement_admin FOREIGN KEY (admin_id) REFERENCES admin (admin_id),
    CONSTRAINT chk_movement_type CHECK (movement_type IN ('INBOUND','RESTOCK','RESERVE','CONFIRM','RELEASE','DISPOSE','EXPIRE','ADJUST')),
    CONSTRAINT chk_movement_qty CHECK (quantity > 0 AND qty_before >= 0 AND qty_after >= 0)
); -- 재고 변동 이력(모든 재고 변동을 로트 단위 시간순 기록. 정합성 추적/감사용 통합 창구. stock_allocation은 예약 상태 관리, stock_disposal은 폐기 상세로 역할 분리. 쓰기 시점: stock_lot.available_qty를 바꾸는 모든 연산(입고/예약/해제/폐기/만료/조정)과 반드시 같은 트랜잭션 안에서 함께 INSERT하여 재고와 이력을 원자적으로 커밋)

CREATE TABLE daily_sales (
    daily_sales_id  BIGINT       NOT NULL AUTO_INCREMENT, -- daily_sales PK
    product_option_id BIGINT     NOT NULL, -- 집계 대상 옵션 FK. 재고(stock_lot)와 소비기한이 옵션 단위라 집계도 같은 단위여야 한다. 상품 단위 수치는 옵션을 합산해 얻는다(반대 방향은 불가능하다)
    stat_date       DATE         NOT NULL, -- 집계 일자
    opening_stock   INT          NOT NULL DEFAULT 0, -- 기초 재고(그날 시작 시점 가용재고 스냅샷). 소진율 분모
    inbound_qty     INT          NOT NULL DEFAULT 0, -- 당일 신규 입고 수량. 소진율 분모
    restocked_qty   INT          NOT NULL DEFAULT 0, -- 당일 반품 재입고 수량. 소진율 분모에는 넣지 않는다. 새로 들여온 물량이 아니라 팔았다가 돌아온 것이라 분모에 넣으면 소진율이 낮아 보인다
    sold_qty        INT          NOT NULL DEFAULT 0, -- 당일 판매 수량(결제 완료 기준). 소진율 분자
    sold_amount     BIGINT       NOT NULL DEFAULT 0, -- 당일 판매 금액(결제 완료 기준)
    disposed_qty    INT          NOT NULL DEFAULT 0, -- 당일 폐기 수량. 로트로 돌아가지 않은 회수품 폐기는 available_qty 를 바꾸지 않으므로 여기 안 들어간다
    expired_qty     INT          NOT NULL DEFAULT 0, -- 당일 만료 전환 수량. 소비기한이 지나 판매 불가로 바뀐 것이며 폐기와 별개다
    closing_stock   INT          NOT NULL DEFAULT 0, -- 기말 재고(그날 마감 시점 가용재고 스냅샷)
    created_at      DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME     NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (daily_sales_id),
    UNIQUE KEY uk_daily_option_date (product_option_id, stat_date), -- 옵션+일자 1행(배치 재실행 시 UPSERT 덮어쓰기, 재조회 동일 결과 보장)
    CONSTRAINT fk_daily_option FOREIGN KEY (product_option_id) REFERENCES product_option (product_option_id),
    CONSTRAINT chk_daily_qty CHECK (opening_stock >= 0 AND inbound_qty >= 0 AND restocked_qty >= 0 AND sold_qty >= 0 AND sold_amount >= 0 AND disposed_qty >= 0 AND expired_qty >= 0 AND closing_stock >= 0)
); -- 판매 집계(일 1회 배치가 옵션별/일자별로 집계. 소진율 산출과 선착순 캠페인 대상 선정(소비기한 임박+판매율 저조)의 원천. 소진율 = 기간 sold_qty 합 / (기간 시작 opening_stock + 기간 inbound_qty 합). 옵션 단위인 이유는 200g와 1kg의 수량을 더한 값으로는 소진율이 뜻을 잃기 때문이다. 집계 원천은 stock_movement 이며, available_qty 를 실제로 바꾼 것만 세어야 아래 항등식이 성립한다. closing = opening + inbound + restocked - sold - disposed - expired. 다만 마감 시점에 예약만 되고 결제되지 않은 수량만큼 어긋난다. available_qty 는 예약(RESERVE)에서 빠지는데 sold_qty 는 결제 완료 기준이라 그 사이 구간이 남기 때문이며, 다음 날 결제나 해제로 흡수된다)

CREATE TABLE order_status_history (
    order_status_history_id      BIGINT       NOT NULL AUTO_INCREMENT, -- order_status_history PK
    order_id        BIGINT       NOT NULL, -- 주문 FK
    from_status     VARCHAR(30)  NULL, -- 이전 상태
    to_status       VARCHAR(30)  NOT NULL, -- 변경 상태(orders.status와 동일 집합)
    created_at      DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    PRIMARY KEY (order_status_history_id),
    CONSTRAINT chk_osh_to_status CHECK (to_status IN ('PAYMENT_PENDING','PAID','PRODUCT_PREPARING','SHIPMENT_PREPARING','SHIPPING','DELIVERED','CONFIRMED','RETURN_REQUESTED','RETURNED','EXCHANGE_REQUESTED','EXCHANGED','CANCELED')),
    CONSTRAINT chk_osh_from_status CHECK (from_status IS NULL OR from_status IN ('PAYMENT_PENDING','PAID','PRODUCT_PREPARING','SHIPMENT_PREPARING','SHIPPING','DELIVERED','CONFIRMED','RETURN_REQUESTED','RETURNED','EXCHANGE_REQUESTED','EXCHANGED','CANCELED')),
    CONSTRAINT fk_history_order FOREIGN KEY (order_id) REFERENCES orders (order_id)
); -- 주문 상태 이력

CREATE TABLE payment (
    payment_id      BIGINT       NOT NULL AUTO_INCREMENT, -- payment PK
    order_id        BIGINT       NOT NULL, -- 주문 FK(1:1)
    method          VARCHAR(30)  NOT NULL, -- 결제수단(CARD 카드/TRANSFER 무통장입금, PG 가상계좌를 쓴다/EASY_PAY 간편결제)
    amount          INT          NOT NULL, -- 결제 금액
    status          VARCHAR(30)  NOT NULL DEFAULT 'PENDING', -- 결제 상태(PENDING/PAID/FAILED/CANCELED/REFUNDED)
    pg_tid          VARCHAR(100) NULL, -- PG 거래번호. 결제 요청 전에는 발급되지 않아 NULL 이다. UNIQUE 가 막는 것은 한 PG 거래가 두 주문에 붙는 것이며(남의 거래번호를 자기 주문에 실어 보내는 경우), 중복 콜백은 이것이 아니라 status='PENDING' 조건부 UPDATE 가 막는다(DI-2-01)
    payment_due_dt  DATETIME     NULL, -- 입금기한 = 주문+24h (무통장입금만 사용, 그 외 NULL)
    paid_at         DATETIME     NULL, -- 결제 완료 시각(서버 애플리케이션이 기록)
    created_at      DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME     NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (payment_id),
    CONSTRAINT chk_payment_method CHECK (method IN ('CARD','TRANSFER','EASY_PAY')),
    CONSTRAINT chk_payment_status CHECK (status IN ('PENDING','PAID','FAILED','CANCELED','REFUNDED')),
    UNIQUE KEY uk_payment_order (order_id),
    UNIQUE KEY uk_payment_pg_tid (pg_tid),
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES orders (order_id),
    CONSTRAINT chk_payment_amount CHECK (amount > 0), -- 0원 주문은 이 행을 만들지 않으므로 0을 허용하지 않는다
    CONSTRAINT chk_payment_pg_tid CHECK ( -- 완료된 결제는 거래번호를 갖는다. 무통장입금도 PG 가상계좌를 쓰므로 예외가 없다
        status <> 'PAID' OR pg_tid IS NOT NULL),
    CONSTRAINT chk_payment_paid_at CHECK ( -- 상태와 완료 시각이 따로 놀지 않도록. CANCELED 는 결제 전 취소와 결제 후 취소가 모두 정상이라 제외한다
        (status IN ('PENDING','FAILED') AND paid_at IS NULL)
     OR (status IN ('PAID','REFUNDED')  AND paid_at IS NOT NULL)
     OR  status = 'CANCELED')
); -- 결제(주문당 최대 1건. 전액 쿠폰/포인트로 total_amount 가 0인 주문은 PG를 타지 않아 이 행이 없다. 결제 여부를 묻는 조회에 INNER JOIN 을 쓰면 그런 주문이 결과에서 사라진다)

-- =====================================================================
-- 5. 클레임 (취소 / 반품 / 교환)
-- =====================================================================

CREATE TABLE claim (
    claim_id        BIGINT       NOT NULL AUTO_INCREMENT, -- claim PK
    order_id        BIGINT       NOT NULL, -- 주문 FK
    type            VARCHAR(30)  NOT NULL, -- 클레임 유형(CANCEL/RETURN/EXCHANGE)
    status          VARCHAR(30)  NOT NULL DEFAULT 'REQUESTED', -- 클레임 상태(REQUESTED/APPROVED/REJECTED/COMPLETED)
    reason_type     VARCHAR(30)  NULL, -- 사유 유형(CHANGE_OF_MIND/DEFECT)
    reason          VARCHAR(500) NULL, -- 사유 상세
    processed_by    BIGINT       NULL, -- admin (NULL=시스템 자동)
    processed_at    DATETIME     NULL, -- 처리 시각
    collect_carrier      VARCHAR(50)  NULL, -- 회수 택배사(반품/교환. 회수=고객이 반품할 상품을 창고로 보내는 배송, 고객 -> 창고)
    collect_tracking_no  VARCHAR(50)  NULL, -- 회수 송장번호
    collect_shipped_at   DATETIME     NULL, -- 회수(수거) 발송 시각
    collect_delivered_at DATETIME     NULL, -- 회수 완료(입고) 시각
    reship_carrier       VARCHAR(50)  NULL, -- 재배송 택배사(교환. 재배송=창고가 교환할 새 상품을 고객에게 보내는 배송, 창고 -> 고객)
    reship_tracking_no   VARCHAR(50)  NULL, -- 재배송 송장번호
    reship_shipped_at    DATETIME     NULL, -- 재배송 발송 시각
    reship_delivered_at  DATETIME     NULL, -- 재배송 완료 시각
    created_at      DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME     NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (claim_id),
    CONSTRAINT chk_claim_type CHECK (type IN ('CANCEL','RETURN','EXCHANGE')),
    CONSTRAINT chk_claim_status CHECK (status IN ('REQUESTED','APPROVED','REJECTED','COMPLETED')),
    CONSTRAINT chk_claim_reason_type CHECK (reason_type IS NULL OR reason_type IN ('CHANGE_OF_MIND','DEFECT')),
    CONSTRAINT chk_claim_collect_at CHECK (collect_delivered_at IS NULL OR collect_shipped_at IS NULL OR collect_delivered_at >= collect_shipped_at),
    CONSTRAINT chk_claim_collect_type CHECK ( -- 회수는 반품/교환에만 있다. 취소는 물건이 나간 적이 없다
        type <> 'CANCEL'
     OR (collect_carrier IS NULL AND collect_tracking_no IS NULL AND collect_shipped_at IS NULL AND collect_delivered_at IS NULL)),
    CONSTRAINT chk_claim_reship_type CHECK ( -- 재배송은 교환에만 있다. 취소와 반품은 새 상품을 보내지 않는다
        type =  'EXCHANGE'
     OR (reship_carrier IS NULL AND reship_tracking_no IS NULL AND reship_shipped_at IS NULL AND reship_delivered_at IS NULL)),
    CONSTRAINT chk_claim_reship_at CHECK (reship_delivered_at IS NULL OR reship_shipped_at IS NULL OR reship_delivered_at >= reship_shipped_at),
    CONSTRAINT chk_claim_processed_at CHECK ( -- 처리된 클레임은 처리 시각을 갖는다. processed_by 는 시스템 자동 처리가 있어 NULL 을 허용하므로 시각만 본다
        (status =  'REQUESTED' AND processed_at IS NULL)
     OR (status <> 'REQUESTED' AND processed_at IS NOT NULL)),
    CONSTRAINT fk_claim_order FOREIGN KEY (order_id) REFERENCES orders (order_id),
    CONSTRAINT fk_claim_admin FOREIGN KEY (processed_by) REFERENCES admin (admin_id)
); -- 클레임(취소/반품/교환. 회수/재배송 송장, 시각, 상태 흡수)

CREATE TABLE claim_attachment (
    claim_attachment_id BIGINT       NOT NULL AUTO_INCREMENT, -- claim_attachment PK
    claim_id            BIGINT       NOT NULL, -- 클레임 FK
    upload_id           BINARY(16)   NOT NULL, -- 업로드 세션 식별자(UUID v7). presigned 발급 때 서버가 만들어 클라이언트에 주고, 완료 통지에서 돌려받아 이 행을 찾는다. key 를 클라이언트에 주지 않기 위한 것이며(INF-11-04) 리소스 식별자가 아니다
    object_key          VARCHAR(255) NOT NULL, -- S3 객체 key. URL 을 통째로 저장하지 않는다(INF-11-05)
    upload_status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING 발급만 됨 / CONFIRMED HeadObject 로 객체 존재와 서명 조건 일치를 확인함(INF-11-10). 통지를 받은 것만으로는 확정하지 않는다. 조회는 CONFIRMED 만 노출하고(INF-11-09) PENDING 인 채로 남은 행은 정리 대상이다
    created_at          DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at          DATETIME     NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (claim_attachment_id),
    UNIQUE KEY uk_claim_attachment_upload (upload_id),
    UNIQUE KEY uk_claim_attachment_key (object_key), -- 완료 통지를 두 번 받아도 같은 key 가 두 행이 되지 않는다
    KEY idx_claim_attachment_pending (upload_status, created_at), -- 미확정 행 스윕(정리 배치)이 풀스캔이 되지 않도록
    CONSTRAINT chk_claim_attachment_status CHECK (upload_status IN ('PENDING','CONFIRMED')),
    CONSTRAINT fk_claim_attachment_claim FOREIGN KEY (claim_id) REFERENCES claim (claim_id)
); -- 클레임 증빙 사진(파손, 오배송). 비공개다. 집 안이 배경으로 찍히므로 본인과 처리 담당 관리자만 본다. 크기와 Content-Type 은 저장하지 않는다(S3 객체 메타데이터가 진실이고 조회는 브라우저가 직접 받는다)

CREATE TABLE claim_item (
    claim_item_id   BIGINT       NOT NULL AUTO_INCREMENT, -- claim_item PK
    claim_id        BIGINT       NOT NULL, -- 클레임 FK
    order_item_id   BIGINT       NOT NULL, -- 대상 주문 상품 FK. DB가 못 막는 조합이다(DI-3-05). 이 주문 상품이 claim.order_id 의 주문 것인지 앱이 확인한다
    qty             INT          NOT NULL, -- 부분 처리 수량. DB가 못 막는 합계다(DI-3-06). 같은 주문 상품에 클레임을 여러 번 열면 qty 합이 order_item.qty 를 넘을 수 있다. 주문 상품 행을 잠그고 합계를 다시 세어 검사한다
    created_at      DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    PRIMARY KEY (claim_item_id),
    UNIQUE KEY uk_claimitem_claim_orderitem (claim_id, order_item_id), -- '클레임 내 동일 항목 중복 방지'
    CONSTRAINT fk_claimitem_claim FOREIGN KEY (claim_id) REFERENCES claim (claim_id),
    CONSTRAINT fk_claimitem_orderitem FOREIGN KEY (order_item_id) REFERENCES order_item (order_item_id),
    CONSTRAINT chk_claimitem_qty CHECK (qty > 0)
); -- 클레임 대상 상품

CREATE TABLE refund (
    refund_id           BIGINT   NOT NULL AUTO_INCREMENT, -- refund PK
    claim_id            BIGINT   NOT NULL, -- 클레임 FK
    payment_id          BIGINT   NOT NULL, -- 원결제 FK. DB가 못 막는 조합이다(DI-3-05). 이 결제가 claim.order_id 의 결제인지 앱이 확인한다
    amount              INT      NOT NULL, -- 환불액
    shipping_deduction  INT      NOT NULL DEFAULT 0, -- 단순변심 배송비 차감
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- 환불 상태(PENDING/DONE)
    refunded_at         DATETIME NULL, -- 환불 완료 시각
    created_at      DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME     NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (refund_id),
    CONSTRAINT chk_refund_status CHECK (status IN ('PENDING','DONE')),
    UNIQUE KEY uk_refund_claim (claim_id),
    CONSTRAINT fk_refund_claim FOREIGN KEY (claim_id) REFERENCES claim (claim_id),
    CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id) REFERENCES payment (payment_id),
    CONSTRAINT chk_refund_amount CHECK (amount >= 0),
    CONSTRAINT chk_refund_shipping_deduction CHECK (shipping_deduction >= 0),
    CONSTRAINT chk_refund_refunded_at CHECK ( -- 완료된 환불은 완료 시각을 갖는다
        (status = 'PENDING' AND refunded_at IS NULL)
     OR (status = 'DONE'    AND refunded_at IS NOT NULL))
); -- 환불(돈에 대한 것만 기록한다. total_amount 가 0인 주문은 payment 행이 없어 이 행도 없으며, 쿠폰과 포인트 복원은 member_coupon.status 와 point_history 가 맡는다)

CREATE TABLE shipment (
    shipment_id   BIGINT       NOT NULL AUTO_INCREMENT, -- shipment PK
    order_id      BIGINT       NOT NULL, -- 주문 FK. 유일성을 걸지 않아 한 주문이 여러 번 나눠 나갈 수 있다(분할 배송)
    carrier       VARCHAR(50)  NULL, -- 택배사
    tracking_no   VARCHAR(50)  NULL, -- 송장번호
    status        VARCHAR(30)  NOT NULL DEFAULT 'PREPARING', -- 배송 상태(PREPARING 준비/SHIPPING 배송중/DELIVERED 배송완료)
    shipped_at    DATETIME     NULL, -- 발송 시각
    delivered_at  DATETIME     NULL, -- 배송 완료 시각
    created_at    DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at    DATETIME     NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (shipment_id),
    CONSTRAINT chk_shipment_status CHECK (status IN ('PREPARING','SHIPPING','DELIVERED')),
    CONSTRAINT fk_shipment_order FOREIGN KEY (order_id) REFERENCES orders (order_id),
    CONSTRAINT chk_shipment_delivered_at CHECK (delivered_at IS NULL OR shipped_at IS NULL OR delivered_at >= shipped_at),
    CONSTRAINT chk_shipment_status_at CHECK ( -- 상태마다 채워져 있어야 할 시각이 정해진다. 위 제약은 순서만 보고 짝은 보지 않는다
        (status = 'PREPARING' AND shipped_at IS NULL     AND delivered_at IS NULL)
     OR (status = 'SHIPPING'  AND shipped_at IS NOT NULL AND delivered_at IS NULL)
     OR (status = 'DELIVERED' AND shipped_at IS NOT NULL AND delivered_at IS NOT NULL))
); -- 배송(출고 전용. 분할 배송을 허용하므로 주문당 여러 행이 가능하다. 회수/재배송은 claim이 흡수)

CREATE TABLE shipment_photo (
    shipment_photo_id BIGINT       NOT NULL AUTO_INCREMENT, -- shipment_photo PK
    shipment_id       BIGINT       NOT NULL, -- 배송 FK
    upload_id         BINARY(16)   NOT NULL, -- 업로드 세션 식별자(UUID v7). claim_attachment 와 같은 용도다
    object_key        VARCHAR(255) NOT NULL, -- S3 객체 key. URL 을 통째로 저장하지 않는다(INF-11-05)
    upload_status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING 발급만 됨 / CONFIRMED HeadObject 로 객체 존재와 서명 조건 일치를 확인함(INF-11-10). 통지를 받은 것만으로는 확정하지 않는다. 조회는 CONFIRMED 만 노출하고(INF-11-09) PENDING 인 채로 남은 행은 정리 대상이다
    created_at        DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at        DATETIME     NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (shipment_photo_id),
    UNIQUE KEY uk_shipment_photo_upload (upload_id),
    UNIQUE KEY uk_shipment_photo_key (object_key),
    KEY idx_shipment_photo_pending (upload_status, created_at), -- 미확정 행 스윕(정리 배치)이 풀스캔이 되지 않도록
    CONSTRAINT chk_shipment_photo_status CHECK (upload_status IN ('PENDING','CONFIRMED')),
    CONSTRAINT fk_shipment_photo_shipment FOREIGN KEY (shipment_id) REFERENCES shipment (shipment_id)
); -- 문앞 배송 완료 사진. 비공개다. 현관과 도어락이 함께 찍혀 주거 형태가 드러나므로 수령인 본인만 본다. 크기와 Content-Type 은 저장하지 않는다(S3 객체 메타데이터가 진실이고 조회는 브라우저가 직접 받는다)


-- =====================================================================
-- 6. 리뷰 / Q&A
-- =====================================================================

CREATE TABLE review (
    review_id       BIGINT       NOT NULL AUTO_INCREMENT, -- review PK
    product_id      BIGINT       NOT NULL, -- 상품 FK
    member_id       BIGINT       NOT NULL, -- 작성 회원 FK
    order_item_id   BIGINT       NOT NULL, -- 구매 확인용 주문 상품 FK(활성 리뷰 한정 구매 건당 1회). DB가 못 막는 조합이다(DI-3-05). 이 주문 상품의 상품이 product_id 와 같은지, 그 주문의 회원이 member_id 와 같은지 앱이 확인한다
    rating          TINYINT      NOT NULL, -- 1~5
    title           VARCHAR(255) NULL, -- 리뷰 제목
    content         TEXT         NOT NULL, -- 리뷰 본문
    is_public       BOOLEAN      NOT NULL DEFAULT TRUE, -- 공개 여부
    deleted_at      DATETIME     NULL, -- 소프트딜리트
    active_orderitem_key BIGINT GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN order_item_id ELSE NULL END), -- 삭제 후 같은 주문상품에 재작성 허용: 활성 리뷰만 구매 건당 1회 강제
    created_at      DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME     NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (review_id),
    UNIQUE KEY uk_review_active_orderitem (active_orderitem_key), -- 활성 리뷰 한정 구매 건당 1회(삭제 행은 NULL이라 제외, 재작성 허용)
    CONSTRAINT fk_review_product FOREIGN KEY (product_id) REFERENCES product (product_id),
    CONSTRAINT fk_review_member FOREIGN KEY (member_id) REFERENCES member (member_id),
    CONSTRAINT fk_review_orderitem FOREIGN KEY (order_item_id) REFERENCES order_item (order_item_id),
    CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5)
); -- 상품 리뷰

CREATE TABLE qna (
    qna_id          BIGINT       NOT NULL AUTO_INCREMENT, -- qna PK
    product_id      BIGINT       NOT NULL, -- 상품 FK
    member_id       BIGINT       NOT NULL, -- 작성 회원 FK
    title           VARCHAR(255) NOT NULL, -- 질문 제목
    question        TEXT         NOT NULL, -- 질문 본문
    answer          TEXT         NULL, -- 답변 본문
    answered_by     BIGINT       NULL, -- admin
    is_public       BOOLEAN      NOT NULL DEFAULT TRUE, -- 공개 여부
    status          VARCHAR(30)  NOT NULL DEFAULT 'WAITING', -- 답변 상태(WAITING/ANSWERED)
    deleted_at      DATETIME     NULL, -- 소프트딜리트
    created_at      DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME     NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (qna_id),
    CONSTRAINT chk_qna_status CHECK (status IN ('WAITING','ANSWERED')),
    CONSTRAINT fk_qna_product FOREIGN KEY (product_id) REFERENCES product (product_id),
    CONSTRAINT fk_qna_member FOREIGN KEY (member_id) REFERENCES member (member_id),
    CONSTRAINT chk_qna_answered CHECK ( -- 답변 완료 상태는 본문과 답변자를 함께 갖는다
        (status = 'ANSWERED' AND answer IS NOT NULL AND answered_by IS NOT NULL)
     OR (status = 'WAITING'  AND answer IS NULL     AND answered_by IS NULL)),
    CONSTRAINT fk_qna_admin FOREIGN KEY (answered_by) REFERENCES admin (admin_id)
); -- 상품 Q&A

-- =====================================================================
-- 7. 쿠폰 / 포인트
-- =====================================================================

CREATE TABLE coupon (
    coupon_id        BIGINT      NOT NULL AUTO_INCREMENT, -- coupon PK
    name             VARCHAR(100) NOT NULL, -- 쿠폰명
    discount_type    VARCHAR(30)  NOT NULL, -- 할인 유형(AMOUNT 정액/RATE 정률)
    discount_value   INT         NOT NULL, -- 할인 값(정액 원 또는 정률 %)
    min_order_amount INT         NOT NULL DEFAULT 0, -- 사용조건
    target_grade_id  BIGINT      NULL, -- 대상 등급(NULL=전체)
    valid_from       DATE        NOT NULL, -- 사용 유효 시작일
    valid_to         DATE        NOT NULL, -- 사용 유효 종료일
    created_at      DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME     NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (coupon_id),
    CONSTRAINT chk_coupon_discount_type CHECK (discount_type IN ('AMOUNT','RATE')),
    CONSTRAINT fk_coupon_grade FOREIGN KEY (target_grade_id) REFERENCES member_grade (member_grade_id),
    CONSTRAINT chk_coupon_values CHECK (discount_value >= 0 AND min_order_amount >= 0),
    CONSTRAINT chk_coupon_rate_range CHECK (discount_type <> 'RATE' OR discount_value <= 100), -- 정률 쿠폰의 할인율이 100%를 넘을 수 없다. member_grade.discount_rate 와 같은 기준
    CONSTRAINT chk_coupon_valid_period CHECK (valid_from <= valid_to)
); -- 쿠폰 정의

CREATE TABLE coupon_campaign (
    coupon_campaign_id BIGINT      NOT NULL AUTO_INCREMENT, -- coupon_campaign PK
    coupon_id          BIGINT      NOT NULL, -- 어떤 쿠폰을 선착순으로 뿌리는가
    name               VARCHAR(100) NOT NULL, -- 캠페인명
    total_quantity     INT         NOT NULL, -- 한정 수량(예: 10000)
    issued_quantity    INT         NOT NULL DEFAULT 0, -- 점유 슬롯 수. 1단계에서는 발급(다운로드) 시점에 늘린다. 2단계에서는 결제 시점으로 옮긴다. 다운로드만 하고 사지 않는 사람에게 한정 수량이 소진되는 낭비를 막기 위해서다. 어느 쪽이든 조건부 UPDATE(issued_quantity < total_quantity)로 다투고 affected rows 가 0이면 소진이다. stock_lot.available_qty 가 주문(예약) 시점에 빠지는 것과 시점이 다른데, 재고는 실물이라 두 사람에게 팔 수 없지만 쿠폰은 실제 구매로 이어진 것만 세는 것이 목적이기 때문이다
    issue_start_at     DATETIME    NOT NULL, -- 발급 오픈 시각
    issue_end_at       DATETIME    NULL, -- 발급 마감 시각(NULL=소진까지)
    status             VARCHAR(30)  NOT NULL DEFAULT 'SCHEDULED', -- SCHEDULED/OPEN/CLOSED
    created_at      DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME     NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (coupon_campaign_id),
    CONSTRAINT fk_campaign_coupon FOREIGN KEY (coupon_id) REFERENCES coupon (coupon_id),
    CONSTRAINT chk_campaign_qty CHECK (total_quantity > 0 AND issued_quantity >= 0 AND issued_quantity <= total_quantity),
    CONSTRAINT chk_campaign_status CHECK (status IN ('SCHEDULED','OPEN','CLOSED')),
    CONSTRAINT chk_campaign_issue_period CHECK (issue_end_at IS NULL OR issue_end_at >= issue_start_at)
); -- 선착순 발급 캠페인(한정 수량/오픈 시각. 일반 쿠폰과 분리). 슬롯 차감 시점을 1단계 발급 -> 2단계 결제로 옮길 예정이며, 스키마는 두 방식을 모두 담는다. 옮길 때 바뀌는 것은 issued_quantity 를 늘리는 코드 위치와 member_coupon 이 RESERVED 를 거치는지 여부뿐이라 마이그레이션이 필요 없다

CREATE TABLE coupon_campaign_option (
    coupon_campaign_option_id BIGINT NOT NULL AUTO_INCREMENT, -- PK
    coupon_campaign_id BIGINT      NOT NULL, -- 캠페인 FK
    product_option_id  BIGINT      NOT NULL, -- 대상 옵션 FK(기획 1: 소비기한 임박/소진율 저조 선정). 상품이 아니라 옵션인 이유는 소비기한이 로트 단위이고 로트가 옵션에 달려 있기 때문이다. 상품 단위로 두면 1kg만 임박했는데 200g에도 쿠폰이 먹혀 임박 재고가 안 빠진다
    issuable_qty       INT         NOT NULL, -- 이 옵션에 발급 가능한 수량(임박 로트 잔량 기준)
    issued_quantity    INT         NOT NULL DEFAULT 0, -- 이 옵션에서 점유된 슬롯 수. 증감 시점은 coupon_campaign.issued_quantity 와 같다(1단계는 발급 시점, 2단계는 결제 시점). 그쪽이 캠페인 전체 상한이라면 이것은 옵션별 상한이며 둘 다 조건부 UPDATE 로 다툰다
    created_at      DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME     NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (coupon_campaign_option_id),
    UNIQUE KEY uk_cco_campaign_option (coupon_campaign_id, product_option_id), -- 캠페인당 옵션 1행
    CONSTRAINT fk_cco_campaign FOREIGN KEY (coupon_campaign_id) REFERENCES coupon_campaign (coupon_campaign_id),
    CONSTRAINT fk_cco_option FOREIGN KEY (product_option_id) REFERENCES product_option (product_option_id),
    CONSTRAINT chk_cco_qty CHECK (issuable_qty >= 0 AND issued_quantity >= 0 AND issued_quantity <= issuable_qty)
); -- 선착순 캠페인 대상 옵션(한 캠페인이 여러 옵션 대상, 사용자는 그중 하나에 쓰는 쿠폰. 선정 근거인 daily_sales 와 stock_lot 이 모두 옵션 단위라 같은 단위로 맞췄다)

CREATE TABLE member_coupon (
    member_coupon_id BIGINT      NOT NULL AUTO_INCREMENT, -- member_coupon PK
    coupon_id        BIGINT      NOT NULL, -- 쿠폰 정의 FK
    member_id        BIGINT      NOT NULL, -- 보유 회원 FK
    coupon_campaign_id BIGINT    NULL, -- 선착순 발급이면 캠페인 참조(일반 발급은 NULL). coupon_campaign_option_id 로 유도할 수 있지만 uk_mc_campaign_member 가 이 컬럼을 필요로 해서 함께 둔다
    coupon_campaign_option_id BIGINT NULL, -- 선착순 발급이면 어느 대상 옵션에 대한 발급인지. 이게 없으면 coupon_campaign_option.issuable_qty 를 소진 판정에 쓸 수 없다. DB가 못 막는 조합이다(DI-3-05). 이 옵션이 coupon_campaign_id 의 대상인지 앱이 확인한다
    order_id         BIGINT      NULL, -- 예약/사용 대상 주문 FK. RESERVED 부터 채운다. 이게 없으면 결제가 실패했을 때 어느 예약을 풀지 찾을 수 없다. DB가 못 막는 조합이다(DI-3-05). 이 주문이 member_id 의 주문인지 앱이 확인한다
    status           VARCHAR(30)  NOT NULL DEFAULT 'ISSUED', -- 발급분 상태(ISSUED 발급/RESERVED 예약/USED 사용/EXPIRED 만료/CANCELED 취소). RESERVED 는 2단계에서 쓴다. 슬롯을 점유하는 것이 아니라 이 쿠폰이 그 주문에 물렸다는 표시이며(슬롯은 결제 시점에 issued_quantity 를 다툰다), 같은 쿠폰이 두 주문에 동시에 물리는 것을 막는다. 결제하면 USED 로 가고, 결제 기한(payment.payment_due_dt)이 지나면 ISSUED 로 되돌려 다시 쓸 수 있게 한다. 되돌리는 일은 재고 예약 해제와 같은 배치에서 함께 처리한다. 따로 돌면 재고는 풀렸는데 쿠폰은 잠긴 주문이 생긴다. 1단계에서는 이 상태를 거치지 않고 ISSUED -> USED 로 간다. CANCELED: 봇 어뷰징 발급 취소, 재고 오류, 기획 오류 등으로 발급을 무효화하기 위해 필요
    issued_at        DATETIME    NOT NULL, -- 쿠폰 발급 시각(서버 애플리케이션이 기록)
    used_at          DATETIME    NULL, -- 쿠폰 사용 시각(서버 애플리케이션이 기록)
    created_at      DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME     NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (member_coupon_id),
    UNIQUE KEY uk_mc_campaign_member (coupon_campaign_id, member_id), -- 선착순 1인 1매(campaign별). 일반 발급은 campaign_id=NULL이라 미적용
    CONSTRAINT fk_mc_coupon FOREIGN KEY (coupon_id) REFERENCES coupon (coupon_id),
    CONSTRAINT fk_mc_campaign FOREIGN KEY (coupon_campaign_id) REFERENCES coupon_campaign (coupon_campaign_id),
    CONSTRAINT fk_mc_campaign_option FOREIGN KEY (coupon_campaign_option_id) REFERENCES coupon_campaign_option (coupon_campaign_option_id),
    CONSTRAINT chk_mc_campaign CHECK ( -- 선착순 발급이면 캠페인과 대상 옵션이 함께 있고, 일반 발급이면 둘 다 없다
        (coupon_campaign_id IS NULL     AND coupon_campaign_option_id IS NULL)
     OR (coupon_campaign_id IS NOT NULL AND coupon_campaign_option_id IS NOT NULL)),
    CONSTRAINT fk_mc_member FOREIGN KEY (member_id) REFERENCES member (member_id),
    CONSTRAINT fk_mc_order FOREIGN KEY (order_id) REFERENCES orders (order_id),
    CONSTRAINT chk_mc_status CHECK (status IN ('ISSUED','RESERVED','USED','EXPIRED','CANCELED')),
    CONSTRAINT chk_mc_order CHECK ( -- 예약과 사용만 주문을 갖는다. 발급/만료/취소 상태에 주문이 남아 있으면 해제가 안 끝난 것이다
        (status IN     ('RESERVED','USED') AND order_id IS NOT NULL)
     OR (status NOT IN ('RESERVED','USED') AND order_id IS NULL)),
    CONSTRAINT chk_mc_used_at CHECK ( -- 사용 시각은 사용 상태에만 있다. 예약은 아직 사용이 아니다
        (status =  'USED' AND used_at IS NOT NULL)
     OR (status <> 'USED' AND used_at IS NULL))
); -- 발급 쿠폰(쿠폰함. 선착순이면 coupon_campaign_id 참조)

CREATE TABLE point_history (
    point_history_id        BIGINT       NOT NULL AUTO_INCREMENT, -- point_history PK
    member_id       BIGINT       NOT NULL, -- 회원 FK
    order_id        BIGINT       NULL, -- 관련 주문 FK(nullable)
    type            VARCHAR(30)  NOT NULL, -- 포인트 유형(EARN 적립/USE 사용/EXPIRE 만료)
    amount          INT          NOT NULL, -- 변동 포인트(절대값, 증감 방향은 type으로 판단). stock_movement.quantity 와 같은 규칙이다
    balance         INT          NOT NULL, -- 잔액
    created_at      DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    PRIMARY KEY (point_history_id),
    CONSTRAINT chk_point_type CHECK (type IN ('EARN','USE','EXPIRE')),
    CONSTRAINT fk_point_member FOREIGN KEY (member_id) REFERENCES member (member_id),
    CONSTRAINT fk_point_order FOREIGN KEY (order_id) REFERENCES orders (order_id),
    CONSTRAINT chk_point_amount_balance CHECK (amount > 0 AND balance >= 0)
); -- 포인트 내역(원장)

-- =====================================================================
-- 8. 공통 (알림 / 감사 로그)
-- =====================================================================

-- 알림 테이블은 아직 쓰지 않는다. 발송 대상 리소스(주문, QnA 등)를 가리키는 참조가 없어
-- 알림을 눌러 이동할 곳을 찾을 수 없고, 그 참조 형태는 알림 기능을 설계할 때 정해진다.
-- 지금 만들어 두면 쓰이지 않는 채로 형태가 굳는다. 필요해질 때 추가 마이그레이션으로 넣는다.
-- CREATE TABLE notification (
--     notification_id BIGINT       NOT NULL AUTO_INCREMENT, -- notification PK
--     member_id       BIGINT       NOT NULL, -- 수신 회원 FK
--     channel         VARCHAR(30)  NOT NULL, -- 발송 채널(EMAIL/SMS/APP)
--     type            VARCHAR(30)  NOT NULL, -- 알림 유형(QNA_ANSWER/ORDER_STATUS/SHIPPING/EXPIRY)
--     content         TEXT         NOT NULL, -- 알림 내용
--     status          VARCHAR(30)  NOT NULL DEFAULT 'SENT', -- 발송 상태(SENT/FAILED/RETRY)
--     created_at      DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
--     updated_at      DATETIME     NOT NULL, -- 수정 시각(애플리케이션에서 생성)
--     PRIMARY KEY (notification_id),
--     CONSTRAINT chk_noti_channel CHECK (channel IN ('EMAIL','SMS','APP')),
--     CONSTRAINT chk_noti_type CHECK (type IN ('QNA_ANSWER','ORDER_STATUS','SHIPPING','EXPIRY')),
--     CONSTRAINT chk_noti_status CHECK (status IN ('SENT','FAILED','RETRY')),
--     CONSTRAINT fk_noti_member FOREIGN KEY (member_id) REFERENCES member (member_id)
-- ); -- 알림

CREATE TABLE audit_log (
    audit_log_id          BIGINT       NOT NULL AUTO_INCREMENT, -- audit_log PK
    admin_id        BIGINT       NOT NULL, -- 행위 관리자 FK
    action          VARCHAR(50)  NOT NULL, -- PRODUCT_DELETE/REFUND/GRADE_CHANGE 등
    target          VARCHAR(100) NULL, -- 대상 식별자
    detail          TEXT         NULL, -- 상세 내용
    created_at      DATETIME     NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    PRIMARY KEY (audit_log_id),
    CONSTRAINT fk_audit_admin FOREIGN KEY (admin_id) REFERENCES admin (admin_id)
); -- 감사 로그
