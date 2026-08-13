-- 초기 스키마.
--
-- 이 파일의 주석은 각 컬럼과 제약이 무엇인지만 적는다.
-- 왜 그렇게 했고 무엇을 포기했는지는 docs/code-architecture/schema-design-rationale.md 에 있다.
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
    name            VARCHAR(50)  NOT NULL, -- 등급명(브론즈/실버/골드)
    promotion_rule  VARCHAR(255) NULL, -- 승급 기준
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE, -- 회원가입 시 부여할 기본 등급 여부. UNIQUE 가 최대 1개를 막고, 최소 1개는 DB 가 못 막아 정합성 검사가 본다
    is_default_key  TINYINT GENERATED ALWAYS AS (CASE WHEN is_default THEN 1 ELSE NULL END), -- is_default=TRUE가 최대 1개임을 DB가 강제하기 위한 계산 컬럼
    created_at      DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME(6)  NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (member_grade_id),
    UNIQUE KEY uk_member_grade_name (name),
    UNIQUE KEY uk_member_grade_single_default (is_default_key)
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
    refresh_token_hash CHAR(64)   NULL, -- 리프레시 토큰의 SHA-256 hex. 평문은 저장하지 않는다. NULL 은 로그아웃 상태다
    refresh_token_expires_at DATETIME(6) NULL, -- 리프레시 토큰 만료 시각. 지나면 재로그인을 요구한다. 컬럼이 하나라 기기 한 대만 로그인이 유지되며, 다중 기기가 필요해지면 별도 테이블로 뺀다
    deleted_at       DATETIME(6)  NULL, -- 소프트딜리트(탈퇴 시, 주문 이력은 법정 기간 보존)
    active_provider_key VARCHAR(140) GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN CONCAT(provider, ':', provider_user_id) ELSE NULL END), -- 탈퇴 후 같은 카카오 계정 재가입 허용: 활성 회원만 유일성 강제
    created_at      DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME(6)  NOT NULL, -- 수정 시각(애플리케이션에서 생성)
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
    created_at      DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME(6)  NOT NULL, -- 수정 시각(애플리케이션에서 생성)
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
    created_at      DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME(6)  NOT NULL, -- 수정 시각(애플리케이션에서 생성)
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
    created_at      DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME(6)  NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (category_id),
    UNIQUE KEY uk_category_parent_name (parent_id, name),
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES category (category_id)
); -- 카테고리

CREATE TABLE supplier (
    supplier_id     BIGINT       NOT NULL AUTO_INCREMENT, -- 공급처 PK
    name            VARCHAR(100) NOT NULL, -- 공급처
    contact         VARCHAR(100) NULL, -- 연락처
    created_at      DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME(6)  NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (supplier_id),
    UNIQUE KEY uk_supplier_name (name)
); -- 공급처

CREATE TABLE product (
    product_id          BIGINT       NOT NULL AUTO_INCREMENT, -- product PK
    product_code        VARCHAR(50)  NOT NULL, -- 자동생성 상품코드. 소프트딜리트해도 이 값은 다시 쓰지 않는다
    name                VARCHAR(255) NOT NULL, -- 상품명
    category_id         BIGINT       NOT NULL, -- 카테고리 FK
    supplier_id         BIGINT       NOT NULL, -- 공급처 FK
    sale_status         VARCHAR(30)  NOT NULL DEFAULT 'ON_SALE', -- 판매 상태(ON_SALE/SOLD_OUT/OFF_SALE)
    storage_type        VARCHAR(30)  NOT NULL, -- 보관 온도(ROOM 실온/COLD 냉장/FROZEN 냉동)
    min_shelf_life_days INT          NOT NULL DEFAULT 0, -- 판매 최소 잔여 소비기한 N
    description         TEXT         NULL, -- 상품 설명
    deleted_at          DATETIME(6)  NULL, -- 소프트딜리트
    created_at      DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME(6)  NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (product_id),
    CONSTRAINT chk_product_sale_status CHECK (sale_status IN ('ON_SALE','SOLD_OUT','OFF_SALE')),
    CONSTRAINT chk_product_storage_type CHECK (storage_type IN ('ROOM','COLD','FROZEN')),
    UNIQUE KEY uk_product_code (product_code), -- 상품코드 유일. 삭제한 코드를 다시 쓰지 않는다
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
    created_at        DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at        DATETIME(6)  NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (product_option_id),
    UNIQUE KEY uk_option_product_name (product_id, name), -- 한 상품 내 옵션명 중복 방지
    UNIQUE KEY uk_option_id_product (product_option_id, product_id), -- review 가 복합 외래 키로 참조한다. product_option_id 만으로도 유일하지만 FK 대상이 되려면 이 조합에 인덱스가 있어야 한다
    CONSTRAINT fk_option_product FOREIGN KEY (product_id) REFERENCES product (product_id),
    CONSTRAINT chk_option_price CHECK (price >= 0),
    CONSTRAINT chk_option_sale_status CHECK (sale_status IN ('ON_SALE','SOLD_OUT','OFF_SALE'))
); -- 상품 옵션(판매 단위 SKU: 가격, 재고 기준)

CREATE TABLE product_image (
    product_image_id BIGINT       NOT NULL AUTO_INCREMENT, -- product_image PK
    product_id       BIGINT       NOT NULL, -- 상품 FK
    upload_id        BINARY(16)   NOT NULL, -- 업로드 세션 식별자(UUID v7). 완료 통지에서 이 값으로 행을 찾는다(INF-11-04). 리소스 식별자가 아니다
    object_key       VARCHAR(255) NOT NULL, -- S3 객체 key (예: products/ab/3f9c1d2e.jpg). URL 을 통째로 저장하지 않는다 (INF-11-05). 도메인은 환경마다 달라 설정에서 붙인다
    upload_status    VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING 발급만 됨 / CONFIRMED HeadObject 로 확인함(INF-11-10). 조회는 CONFIRMED 만(INF-11-09)
    sort_order       INT          NOT NULL DEFAULT 0, -- 상품 안에서의 표시 순서(작을수록 앞). 서버가 MAX+1로 정한다. 유일성은 일부러 걸지 않는다
    is_main          BOOLEAN      NOT NULL DEFAULT FALSE, -- 대표 이미지 여부
    is_main_key      BIGINT GENERATED ALWAYS AS (CASE WHEN is_main THEN product_id ELSE NULL END), -- 상품별로 is_main=TRUE가 최대 1개임을 DB가 강제하기 위한 계산 컬럼
    created_at       DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at       DATETIME(6)  NOT NULL, -- 수정 시각(애플리케이션에서 생성)
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
    available_qty   INT          NOT NULL, -- 판매 가능 수량. 예약(RESERVE)에서 빼고 해제(RELEASE)에서 되돌린다. 확정(CONFIRM)은 바꾸지 않는다
    status          VARCHAR(30)  NOT NULL DEFAULT 'AVAILABLE', -- 로트 상태(AVAILABLE/SOLD_OUT/DISPOSED)
    created_at      DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME(6)  NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (stock_lot_id),
    CONSTRAINT chk_lot_status CHECK (status IN ('AVAILABLE','SOLD_OUT','DISPOSED')),
    KEY idx_lot_fefo (product_option_id, status, expiry_date), -- FEFO 조회 최적화: 옵션+상태별 소비기한 임박순
    CONSTRAINT fk_lot_option FOREIGN KEY (product_option_id) REFERENCES product_option (product_option_id),
    CONSTRAINT chk_lot_qty CHECK (available_qty >= 0 AND available_qty <= initial_qty),
    CONSTRAINT chk_lot_expiry_date CHECK (expiry_date >= received_date),
    CONSTRAINT chk_lot_status_qty CHECK (status = 'AVAILABLE' OR available_qty = 0) -- 소진/폐기된 로트에 가용재고가 남아 있으면 FEFO 조회가 없는 재고를 집는다
); -- 입고 로트(실재고 단위, 공급처는 product.supplier_id 기준)

-- =====================================================================
-- 3. 쿠폰
-- =====================================================================

CREATE TABLE coupon (
    coupon_id           BIGINT       NOT NULL AUTO_INCREMENT, -- coupon PK
    name                VARCHAR(100) NOT NULL, -- 쿠폰명
    scope               VARCHAR(20)  NOT NULL, -- 적용 범위(ORDER 장바구니 쿠폰, 주문 전체에 1장/ITEM 상품 쿠폰, 라인 하나에 1장)
    discount_type       VARCHAR(30)  NOT NULL, -- 할인 유형(AMOUNT 정액 원/RATE 정률 %)
    discount_value      INT          NOT NULL, -- 할인 값
    max_discount_amount INT          NULL, -- 정률 쿠폰의 할인 상한. 없으면 고액 주문에서 할인이 무제한이 된다. 정액 쿠폰에서는 NULL 이다
    min_order_amount    INT          NOT NULL DEFAULT 0, -- 사용 조건(이 금액 이상일 때만 쓸 수 있다)
    total_quantity      INT          NULL, -- 발급 한정 수량. NULL 이면 일반 쿠폰, 값이 있으면 선착순이다
    issued_quantity     INT          NOT NULL DEFAULT 0, -- 발급된 수 겸 순번 발급기. 조건부 UPDATE 로 올린 값을 member_coupon.issue_seq 에 넣는다
    issue_start_at      DATETIME(6)  NULL, -- 발급 시작 시각(선착순 오픈 시각). NULL 이면 제한 없음
    issue_end_at        DATETIME(6)  NULL, -- 발급 마감 시각. NULL 이면 소진까지
    valid_from          DATE         NOT NULL, -- 사용 유효 시작일
    valid_to            DATE         NOT NULL, -- 사용 유효 종료일
    target_grade_id     BIGINT       NULL, -- 대상 등급(NULL=전체)
    is_active           BOOLEAN      NOT NULL DEFAULT FALSE, -- 발급 스위치. 초안(FALSE)으로 태어나 사람이 켠다. ITEM 쿠폰은 대상이 하나 이상 있어야 켤 수 있고 그 검사는 앱이 한다. 소진 여부는 issued_quantity 로 유도한다
    created_at          DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at          DATETIME(6)  NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (coupon_id),
    UNIQUE KEY uk_coupon_id_scope (coupon_id, scope), -- coupon_product_option 이 복합 외래 키로 참조한다. coupon_id 만으로도 유일하지만 FK 대상이 되려면 이 조합에 인덱스가 있어야 한다
    CONSTRAINT fk_coupon_grade FOREIGN KEY (target_grade_id) REFERENCES member_grade (member_grade_id),
    CONSTRAINT chk_coupon_scope CHECK (scope IN ('ORDER','ITEM')),
    CONSTRAINT chk_coupon_discount_type CHECK (discount_type IN ('AMOUNT','RATE')),
    CONSTRAINT chk_coupon_values CHECK (discount_value > 0 AND min_order_amount >= 0),
    CONSTRAINT chk_coupon_rate CHECK ( -- 정률은 100%를 넘을 수 없고 상한은 정률에만 붙는다
        (discount_type = 'RATE'   AND discount_value <= 100)
     OR (discount_type = 'AMOUNT' AND max_discount_amount IS NULL)),
    CONSTRAINT chk_coupon_max_discount CHECK (max_discount_amount IS NULL OR max_discount_amount > 0),
    CONSTRAINT chk_coupon_quantity CHECK (issued_quantity >= 0 AND (total_quantity IS NULL OR (total_quantity > 0 AND issued_quantity <= total_quantity))),
    CONSTRAINT chk_coupon_issue_period CHECK (issue_start_at IS NULL OR issue_end_at IS NULL OR issue_end_at >= issue_start_at),
    CONSTRAINT chk_coupon_valid_period CHECK (valid_from <= valid_to)
); -- 쿠폰 정의(발급 틀). total_quantity 가 있으면 선착순이다

CREATE TABLE coupon_product_option (
    coupon_product_option_id BIGINT NOT NULL AUTO_INCREMENT, -- coupon_product_option PK
    coupon_id         BIGINT      NOT NULL, -- 쿠폰 FK
    scope             VARCHAR(20) NOT NULL DEFAULT 'ITEM', -- 조상 키 복제. CHECK 와 복합 외래 키가 함께 ORDER 쿠폰의 행을 막는다
    product_option_id BIGINT      NOT NULL, -- 이 쿠폰을 쓸 수 있는 옵션 FK
    is_active         BOOLEAN     NOT NULL DEFAULT TRUE, /* 현재 대상인지 여부. 대상에서 뺄 때 행을 지우지 않고 이 값을 내린다.
                                                            order_item 이 이 표를 복합 외래 키로 참조하므로 지우면 이미 팔린 조합에서 삭제가 막힌다.
                                                            쿠폰을 적용할 때 현재 대상인지는 앱이 이 값으로 본다 */
    created_at        DATETIME(6) NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at        DATETIME(6) NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (coupon_product_option_id),
    UNIQUE KEY uk_cpo_coupon_option (coupon_id, product_option_id), -- 쿠폰당 옵션 1행
    CONSTRAINT chk_cpo_scope CHECK (scope = 'ITEM'),
    CONSTRAINT fk_cpo_coupon FOREIGN KEY (coupon_id, scope) REFERENCES coupon (coupon_id, scope),
    CONSTRAINT fk_cpo_option FOREIGN KEY (product_option_id) REFERENCES product_option (product_option_id)
); -- 상품 쿠폰의 대상 옵션. ITEM 쿠폰은 활성 대상을 하나 이상 반드시 지정한다. 행은 지우지 않고 is_active 로 내린다

CREATE TABLE member_coupon (
    member_coupon_id    BIGINT       NOT NULL AUTO_INCREMENT, -- member_coupon PK
    coupon_id           BIGINT       NOT NULL, -- 발급 틀 FK. 대상 옵션을 찾을 때와 어느 틀에서 나왔는지 추적할 때만 쓴다. 아래 조건 값들은 이 참조를 따라가지 않는다
    member_id           BIGINT       NOT NULL, -- 보유 회원 FK
    coupon_name         VARCHAR(100) NOT NULL, -- 발급 시점 쿠폰명
    scope               VARCHAR(20)  NOT NULL, -- 발급 시점 적용 범위(ORDER/ITEM)
    discount_type       VARCHAR(30)  NOT NULL, -- 발급 시점 할인 유형(AMOUNT/RATE)
    discount_value      INT          NOT NULL, -- 발급 시점 할인 값
    max_discount_amount INT          NULL, -- 발급 시점 정률 할인 상한
    min_order_amount    INT          NOT NULL DEFAULT 0, -- 발급 시점 사용 조건
    valid_from          DATE         NOT NULL, -- 발급 시점 사용 유효 시작일
    valid_to            DATE         NOT NULL, -- 발급 시점 사용 유효 종료일
    issue_limit         INT          NULL, -- 발급 시점 한정 수량(coupon.total_quantity 복사). NULL 이면 무제한 쿠폰이다
    issue_seq           INT          NULL, -- 선착순 발급 순번(1 부터). 무제한 쿠폰은 NULL 이다
    status              VARCHAR(30)  NOT NULL DEFAULT 'ISSUED', -- 발급분 상태(ISSUED 발급/USED 사용/EXPIRED 만료)
    issued_at           DATETIME(6)  NOT NULL, -- 발급 시각(서버 애플리케이션이 기록)
    used_at             DATETIME(6)  NULL, -- 사용 시각(서버 애플리케이션이 기록)
    created_at          DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at          DATETIME(6)  NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (member_coupon_id),
    UNIQUE KEY uk_mc_coupon_member (coupon_id, member_id), -- 쿠폰당 1인 1매
    UNIQUE KEY uk_mc_id_member (member_coupon_id, member_id), -- orders 와 order_item 이 복합 외래 키로 참조한다
    UNIQUE KEY uk_mc_id_coupon (member_coupon_id, coupon_id), -- order_item 이 복합 외래 키로 참조한다
    UNIQUE KEY uk_mc_id_scope (member_coupon_id, scope), -- orders 가 복합 외래 키로 참조한다
    UNIQUE KEY uk_mc_coupon_seq (coupon_id, issue_seq), -- 한 쿠폰에서 같은 순번을 두 번 쓸 수 없다. MySQL UNIQUE 는 NULL 을 중복으로 보지 않아 무제한 쿠폰은 걸리지 않는다
    CONSTRAINT fk_mc_coupon FOREIGN KEY (coupon_id) REFERENCES coupon (coupon_id),
    CONSTRAINT fk_mc_member FOREIGN KEY (member_id) REFERENCES member (member_id),
    CONSTRAINT chk_mc_status CHECK (status IN ('ISSUED','USED','EXPIRED')),
    CONSTRAINT chk_mc_used_at CHECK ( -- 사용 시각은 사용 상태에만 있다
        (status =  'USED' AND used_at IS NOT NULL)
     OR (status <> 'USED' AND used_at IS NULL)),
    CONSTRAINT chk_mc_scope CHECK (scope IN ('ORDER','ITEM')),
    CONSTRAINT chk_mc_discount_type CHECK (discount_type IN ('AMOUNT','RATE')),
    CONSTRAINT chk_mc_values CHECK (discount_value > 0 AND min_order_amount >= 0),
    CONSTRAINT chk_mc_rate CHECK ( -- 정률은 100%를 넘을 수 없고 상한은 정률에만 붙는다
        (discount_type = 'RATE'   AND discount_value <= 100)
     OR (discount_type = 'AMOUNT' AND max_discount_amount IS NULL)),
    CONSTRAINT chk_mc_max_discount CHECK (max_discount_amount IS NULL OR max_discount_amount > 0),
    CONSTRAINT chk_mc_valid_period CHECK (valid_from <= valid_to),
    CONSTRAINT chk_mc_issue_seq CHECK ( /* 순번은 한정 수량 안에서만 매겨진다. 위 UNIQUE 와 짝이 되어 coupon.issued_quantity 가 어긋나도 초과 발급을 막는다.
                                           IS NOT NULL 을 명시하는 이유는 CHECK 가 결과 NULL 을 통과시키기 때문이다 */
        (issue_limit IS NULL     AND issue_seq IS NULL)
     OR (issue_limit IS NOT NULL AND issue_seq IS NOT NULL AND issue_seq >= 1 AND issue_seq <= issue_limit))
); -- 발급 쿠폰(쿠폰함). 발급 시점에 coupon 의 조건을 복사해 온다

CREATE TABLE member_coupon_status_history (
    member_coupon_status_history_id BIGINT NOT NULL AUTO_INCREMENT, -- member_coupon_status_history PK
    member_coupon_id BIGINT       NOT NULL, -- 발급 쿠폰 FK
    from_status      VARCHAR(30)  NULL, -- 이전 상태(최초 발급은 NULL)
    to_status        VARCHAR(30)  NOT NULL, -- 변경 상태(member_coupon.status 와 동일 집합)
    reason           VARCHAR(255) NULL, -- 사유 상세. 만료가 유효기간 도래인지 어뷰징 발급 취소인지 구분하는 유일한 근거다. status 집합이 셋뿐이라 상태값만으로는 갈리지 않는다
    changed_by       BIGINT       NULL, -- 처리 관리자 FK. 배치나 사용자 동작으로 자동 전이한 것은 NULL 이다
    created_at       DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    PRIMARY KEY (member_coupon_status_history_id),
    KEY idx_mcsh_coupon_time (member_coupon_id, member_coupon_status_history_id), -- 쿠폰별 전이 순서 조회
    CONSTRAINT chk_mcsh_to_status CHECK (to_status IN ('ISSUED','USED','EXPIRED')),
    CONSTRAINT chk_mcsh_from_status CHECK (from_status IS NULL OR from_status IN ('ISSUED','USED','EXPIRED')),
    CONSTRAINT chk_mcsh_transition CHECK (from_status IS NULL OR from_status <> to_status), -- 같은 상태로 바뀌는 전이는 기록할 것이 없다
    CONSTRAINT fk_mcsh_member_coupon FOREIGN KEY (member_coupon_id) REFERENCES member_coupon (member_coupon_id),
    CONSTRAINT fk_mcsh_admin FOREIGN KEY (changed_by) REFERENCES admin (admin_id)
); -- 발급 쿠폰 상태 이력. reason 이 만료와 어뷰징 취소를 가른다

-- =====================================================================
-- 4. 장바구니
-- =====================================================================

CREATE TABLE cart (
    cart_id         BIGINT       NOT NULL AUTO_INCREMENT, -- cart PK
    member_id       BIGINT       NOT NULL, -- 회원 FK(회원당 1개 UK)
    created_at      DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME(6)  NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (cart_id),
    UNIQUE KEY uk_cart_member (member_id), -- 1인 1카트
    CONSTRAINT fk_cart_member FOREIGN KEY (member_id) REFERENCES member (member_id)
); -- 장바구니

CREATE TABLE cart_item (
    cart_item_id    BIGINT       NOT NULL AUTO_INCREMENT, -- cart_item PK
    cart_id         BIGINT       NOT NULL, -- 장바구니 FK
    product_option_id BIGINT     NOT NULL, -- 담은 옵션 FK
    qty             INT          NOT NULL DEFAULT 1, -- 수량
    created_at      DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME(6)  NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (cart_item_id),
    UNIQUE KEY uk_cart_option (cart_id, product_option_id), -- 동일 옵션 중복 방지
    CONSTRAINT fk_cartitem_cart FOREIGN KEY (cart_id) REFERENCES cart (cart_id),
    CONSTRAINT fk_cartitem_option FOREIGN KEY (product_option_id) REFERENCES product_option (product_option_id),
    CONSTRAINT chk_cartitem_qty CHECK (qty > 0)
); -- 장바구니 상품

-- =====================================================================
-- 5. 주문 / 결제
-- =====================================================================

CREATE TABLE orders (
    order_id        BIGINT       NOT NULL AUTO_INCREMENT, -- orders PK
    order_no        VARCHAR(30)  NOT NULL, -- 주문번호
    member_id       BIGINT       NOT NULL, -- 주문 회원 FK
    status          VARCHAR(30)  NOT NULL DEFAULT 'PAYMENT_PENDING', /* 주문 상태(PAYMENT_PENDING/PAID/PRODUCT_PREPARING/SHIPMENT_PREPARING/SHIPPING/DELIVERED/CONFIRMED/RETURN_REQUESTED/RETURNED/EXCHANGE_REQUESTED/EXCHANGED/CANCELED).
                                                                        CANCELED 와 RETURNED 는 주문 전체에만 쓴다. 취소는 라인 단위로 일어나지 않고, 부분 반품은 헤더를 바꾸지 않고 라인만 바꾼다.
                                                                        아래 계산 컬럼이 이 전제 위에 서 있으며 DB 는 강제하지 못한다. order_item.item_status 와 함께 바꾼다 */
    product_amount  INT          NOT NULL, -- 총상품금액. SUM(order_item.unit_price * qty) 와 같아야 한다(DI-3-06)
    discount_amount INT          NOT NULL DEFAULT 0, -- 할인 총액(장바구니 쿠폰 + 상품 쿠폰). SUM(order_item.discount_amount) 와 같아야 한다(DI-3-06)
    member_coupon_id BIGINT      NULL, -- 주문 전체에 적용한 장바구니 쿠폰. 취소해도 비우지 않는다. 어느 쿠폰을 썼는지가 이력이고, 재사용은 계산 컬럼이 연다
    coupon_scope    VARCHAR(20)  NULL, -- 조상 키 복제. CHECK 와 복합 외래 키가 함께 ITEM 쿠폰을 이 자리에 넣지 못하게 한다
    coupon_discount INT          NOT NULL DEFAULT 0, -- 장바구니 쿠폰이 깎은 금액. 쿠폰 정의는 나중에 바뀔 수 있어 주문 시점 금액을 남긴다
    active_coupon_key BIGINT GENERATED ALWAYS AS (CASE WHEN status NOT IN ('CANCELED','RETURNED') THEN member_coupon_id ELSE NULL END), -- 살아 있는 주문 한정으로 쿠폰당 1건임을 DB가 강제하기 위한 계산 컬럼. order_item 과 같은 조건이다. 교환은 상품이 유지되므로 쿠폰도 유지한다
    shipping_fee    INT          NOT NULL DEFAULT 0, -- 배송비
    total_amount    INT          NOT NULL, -- 최종결제금액. 할인이 상품금액과 배송비를 다 덮으면 0이 될 수 있고, 그 주문은 payment 행 없이 바로 PAID 가 된다
    ship_recipient  VARCHAR(50)  NOT NULL, -- 배송지 스냅샷
    ship_phone      VARCHAR(20)  NOT NULL, -- 연락처(원문, 암호화 추후 적용)
    ship_zipcode    VARCHAR(10)  NOT NULL, -- 배송지 우편번호 스냅샷
    ship_address    VARCHAR(500) NOT NULL, -- 배송지 주소 스냅샷
    ship_message    VARCHAR(255) NULL, -- 배송 메시지
    ordered_at      DATETIME(6)  NOT NULL, -- 주문 접수 시각(서버 애플리케이션이 기록)
    created_at      DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME(6)  NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (order_id),
    CONSTRAINT chk_order_status CHECK (status IN ('PAYMENT_PENDING','PAID','PRODUCT_PREPARING','SHIPMENT_PREPARING','SHIPPING','DELIVERED','CONFIRMED','RETURN_REQUESTED','RETURNED','EXCHANGE_REQUESTED','EXCHANGED','CANCELED')),
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_order_id_member (order_id, member_id), -- order_item 이 복합 외래 키로 참조한다
    UNIQUE KEY uk_order_id_total (order_id, total_amount), -- payment 가 복합 외래 키로 참조한다. order_id 만으로도 유일하지만 FK 대상이 되려면 이 조합에 인덱스가 있어야 한다
    UNIQUE KEY uk_order_active_coupon (active_coupon_key), -- 살아 있는 주문 한정 쿠폰당 1건. 취소나 전체 반품이면 계산 컬럼이 NULL 이 되어 그 쿠폰을 다시 쓸 수 있다
    CONSTRAINT fk_order_member FOREIGN KEY (member_id) REFERENCES member (member_id),
    CONSTRAINT fk_order_member_coupon FOREIGN KEY (member_coupon_id, member_id) REFERENCES member_coupon (member_coupon_id, member_id), -- member_id 를 함께 요구해 남의 쿠폰을 붙일 수 없다
    CONSTRAINT fk_order_coupon_scope FOREIGN KEY (member_coupon_id, coupon_scope) REFERENCES member_coupon (member_coupon_id, scope), -- scope 를 함께 요구해 ITEM 쿠폰을 붙일 수 없다
    CONSTRAINT chk_order_amounts CHECK (product_amount >= 0 AND discount_amount >= 0 AND shipping_fee >= 0 AND total_amount >= 0 AND coupon_discount >= 0),
    CONSTRAINT chk_order_total CHECK (total_amount = product_amount - discount_amount + shipping_fee), -- 합계가 항목과 맞는지 DB가 강제한다. 각 항목이 0 이상인 것만 봐서는 total_amount가 아무 값이나 될 수 있다
    CONSTRAINT chk_order_discount_parts CHECK (coupon_discount <= discount_amount), -- 장바구니 쿠폰분은 할인 총액의 일부이고 나머지는 상품 쿠폰분이다
    CONSTRAINT chk_order_discount_cap CHECK (discount_amount <= product_amount), -- 할인은 상품금액을 넘지 못한다. 배송비를 깎는 쿠폰이 없으므로 배송비는 할인 대상이 아니다
    CONSTRAINT chk_order_coupon_amount CHECK (member_coupon_id IS NOT NULL OR coupon_discount = 0), -- 쿠폰 없이 쿠폰 할인액만 있을 수 없다
    CONSTRAINT chk_order_coupon_scope CHECK ( /* 쿠폰을 썼으면 장바구니 쿠폰이어야 하고, 안 썼으면 비어 있다.
                                                 coupon_scope 만 비워 두는 우회를 막으려고 IS NOT NULL 을 명시한다.
                                                 CHECK 는 결과가 NULL 이면 통과시키고, 복합 외래 키도 컬럼에 NULL 이 끼면 검사하지 않는다 */
        (member_coupon_id IS NOT NULL AND coupon_scope IS NOT NULL AND coupon_scope = 'ORDER')
     OR (member_coupon_id IS NULL     AND coupon_scope IS NULL))
); -- 주문(헤더)

CREATE TABLE order_item (
    order_item_id   BIGINT       NOT NULL AUTO_INCREMENT, -- order_item PK
    order_id        BIGINT       NOT NULL, -- 주문 FK
    product_option_id BIGINT     NOT NULL, -- 주문 옵션 FK
    name_snapshot   VARCHAR(255) NOT NULL, -- 주문시점 상품명
    option_name_snapshot VARCHAR(100) NOT NULL, -- 주문시점 옵션명
    unit_price      INT          NOT NULL, -- 주문시점 가격
    qty             INT          NOT NULL, -- 주문 수량
    member_id       BIGINT       NOT NULL, -- 조상 키 복제. 복합 외래 키가 쿠폰 소유자와 주문자를 묶는다
    member_coupon_id BIGINT      NULL, -- 이 라인에만 적용한 상품 쿠폰. 컬럼이 하나라 라인당 1장이 구조적으로 보장된다. 취소해도 비우지 않는다
    coupon_id       BIGINT       NULL, -- 조상 키 복제. 복합 외래 키가 이 라인의 옵션이 쿠폰 대상인지 강제한다
    coupon_discount INT          NOT NULL DEFAULT 0, -- 상품 쿠폰이 깎은 금액
    discount_amount INT          NOT NULL DEFAULT 0, -- 이 라인에 배분된 할인 총액. 부분 반품 환불액의 근거다. 배분 규칙은 schema-design-rationale.md 4장
    item_status     VARCHAR(30)  NOT NULL DEFAULT 'ORDERED', -- 주문 상품 상태(ORDERED/CANCELED/RETURN_REQ/RETURNED/EXCHANGE_REQ/EXCHANGED). 클레임 중복을 조건부 UPDATE 로 이 컬럼이 막는다
    active_coupon_key BIGINT GENERATED ALWAYS AS (CASE WHEN item_status NOT IN ('CANCELED','RETURNED') THEN member_coupon_id ELSE NULL END), -- 살아 있는 라인 한정으로 쿠폰당 1건임을 DB가 강제하기 위한 계산 컬럼. 교환은 상품이 유지되므로 쿠폰도 유지한다. orders.status 를 보지 않으므로 주문을 취소할 때 라인도 함께 CANCELED 로 바꿔야 상품 쿠폰이 풀린다
    created_at      DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME(6)  NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (order_item_id),
    CONSTRAINT chk_orderitem_status CHECK (item_status IN ('ORDERED','CANCELED','RETURN_REQ','RETURNED','EXCHANGE_REQ','EXCHANGED')),
    UNIQUE KEY uk_order_option (order_id, product_option_id), -- 동일 옵션 중복 라인 방지
    UNIQUE KEY uk_orderitem_id_order (order_item_id, order_id), -- claim_item 이 복합 외래 키로 참조한다
    UNIQUE KEY uk_orderitem_id_option_member (order_item_id, product_option_id, member_id), -- review 가 복합 외래 키로 참조한다
    UNIQUE KEY uk_orderitem_active_coupon (active_coupon_key), -- 살아 있는 라인 한정 쿠폰당 1건. 취소나 반품이면 계산 컬럼이 NULL 이 되어 그 쿠폰을 다시 쓸 수 있다
    CONSTRAINT fk_orderitem_order FOREIGN KEY (order_id, member_id) REFERENCES orders (order_id, member_id),
    CONSTRAINT fk_orderitem_option FOREIGN KEY (product_option_id) REFERENCES product_option (product_option_id),
    CONSTRAINT fk_orderitem_member_coupon FOREIGN KEY (member_coupon_id, member_id) REFERENCES member_coupon (member_coupon_id, member_id),
    CONSTRAINT fk_orderitem_coupon FOREIGN KEY (member_coupon_id, coupon_id) REFERENCES member_coupon (member_coupon_id, coupon_id),
    CONSTRAINT fk_orderitem_coupon_target FOREIGN KEY (coupon_id, product_option_id) REFERENCES coupon_product_option (coupon_id, product_option_id),
    CONSTRAINT chk_orderitem_qty CHECK (qty > 0),
    CONSTRAINT chk_orderitem_discount CHECK (discount_amount >= 0 AND discount_amount <= unit_price * qty), -- 할인이 라인 금액을 넘으면 환불액이 음수가 된다
    CONSTRAINT chk_orderitem_coupon_amount CHECK (coupon_discount >= 0 AND coupon_discount <= discount_amount), -- 상품 쿠폰분은 이 라인 할인 총액의 일부다
    CONSTRAINT chk_orderitem_coupon CHECK (member_coupon_id IS NOT NULL OR coupon_discount = 0), -- 쿠폰 없이 쿠폰 할인액만 있을 수 없다
    CONSTRAINT chk_orderitem_coupon_pair CHECK ((member_coupon_id IS NULL) = (coupon_id IS NULL)) -- 한쪽만 채우면 복합 외래 키가 검사에서 빠져 사슬이 끊긴다
); -- 주문 상품

CREATE TABLE stock_allocation (
    stock_allocation_id   BIGINT       NOT NULL AUTO_INCREMENT, -- stock_allocation PK
    order_item_id   BIGINT       NOT NULL, -- 주문 상품 FK
    stock_lot_id          BIGINT       NOT NULL, -- 차감 대상 로트 FK
    qty             INT          NOT NULL, -- FEFO 예약/차감 수량
    status          VARCHAR(30)  NOT NULL DEFAULT 'RESERVED', -- RESERVED=예약(available_qty 를 뺀다) / CONFIRMED=차감 확정(available_qty 무변동) / RELEASED=해제(되돌린다). 재예약은 새 행이 아니라 이 값을 RESERVED 로 되돌린다
    created_at      DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME(6)  NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (stock_allocation_id),
    UNIQUE KEY uk_alloc_orderitem_lot (order_item_id, stock_lot_id), -- 한 주문 상품이 한 로트를 잡는 행은 하나. 재시도로 예약이 두 번 들어가 available_qty 가 이중 차감되는 것을 막는다
    CONSTRAINT fk_alloc_orderitem FOREIGN KEY (order_item_id) REFERENCES order_item (order_item_id),
    CONSTRAINT fk_alloc_lot FOREIGN KEY (stock_lot_id) REFERENCES stock_lot (stock_lot_id),
    CONSTRAINT chk_alloc_qty CHECK (qty > 0),
    CONSTRAINT chk_alloc_status CHECK (status IN ('RESERVED','CONFIRMED','RELEASED'))
); -- 주문상품-로트 할당(주문상품이 어느 로트를 얼마나 잡고 있나 추적 이력은 stock_movement 에 있다): 반품 시 원래 로트를 찾는 경로(claim_item -> order_item -> stock_allocation)를 위한 테이블

CREATE TABLE stock_movement (
    stock_movement_id BIGINT       NOT NULL AUTO_INCREMENT, -- stock_movement PK
    stock_lot_id      BIGINT       NOT NULL, -- 변동이 일어난 로트 FK
    movement_type     VARCHAR(30)  NOT NULL, -- 변동 유형(INBOUND 신규 입고/RESTOCK 반품 재입고/RESERVE 예약/CONFIRM 차감확정/RELEASE 예약해제/DISPOSE 폐기/EXPIRE 만료전환/ADJUST 수동조정). RESTOCK 은 원래 로트로 되돌리고, 잔여 소비기한이 모자라면 되돌리지 않고 DISPOSE 로 폐기한다
    quantity          INT          NOT NULL, -- 변동 수량(절대값, 증감 방향은 movement_type으로 판단)
    qty_before        INT          NOT NULL, -- 변동 전 로트 available_qty
    qty_after         INT          NOT NULL, -- 변동 후 로트 available_qty. CONFIRM 은 두 값이 같다
    order_id          BIGINT       NULL, -- 관련 주문 FK(주문 기인 변동만, 그 외 NULL)
    admin_id          BIGINT       NULL, -- 처리 관리자 FK(수동조정/폐기 등, 시스템 자동은 NULL)
    disposal_reason   VARCHAR(30)  NULL, -- 폐기 사유(EXPIRED 소비기한/DAMAGED 손상/RETURNED 재입고하지 않은 회수품). DISPOSE 일 때만 채운다
    reason            VARCHAR(200) NULL, -- 사유 상세(만료/조정 등 자유 서술)
    created_at        DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    PRIMARY KEY (stock_movement_id),
    KEY idx_movement_lot_time (stock_lot_id, created_at), -- 로트별 시간순 조회(정합성 추적)
    KEY idx_movement_order (order_id), -- 주문별 변동 추적
    CONSTRAINT fk_movement_lot FOREIGN KEY (stock_lot_id) REFERENCES stock_lot (stock_lot_id),
    CONSTRAINT fk_movement_order FOREIGN KEY (order_id) REFERENCES orders (order_id),
    CONSTRAINT fk_movement_admin FOREIGN KEY (admin_id) REFERENCES admin (admin_id),
    CONSTRAINT chk_movement_type CHECK (movement_type IN ('INBOUND','RESTOCK','RESERVE','CONFIRM','RELEASE','DISPOSE','EXPIRE','ADJUST')),
    CONSTRAINT chk_movement_qty CHECK (quantity > 0 AND qty_before >= 0 AND qty_after >= 0),
    CONSTRAINT chk_movement_disposal CHECK ( -- 폐기는 사유와 처리자를 반드시 갖는다. 다른 유형에는 폐기 사유가 없다
        (movement_type =  'DISPOSE' AND disposal_reason IS NOT NULL AND admin_id IS NOT NULL)
     OR (movement_type <> 'DISPOSE' AND disposal_reason IS NULL)),
    CONSTRAINT chk_movement_delta CHECK ( /* 원장의 항등식. 세 수치가 서로 맞고, 유형이 정한 방향과도 맞아야 한다.
                                             ADJUST 만 양방향이고 나머지는 방향이 하나로 고정된다.
                                             CONFIRM 과 회수품 폐기는 available_qty 를 바꾸지 않으므로 앞뒤가 같다 */
        (movement_type IN ('INBOUND','RESTOCK','RELEASE') AND qty_after = qty_before + quantity)
     OR (movement_type IN ('RESERVE','EXPIRE')            AND qty_after = qty_before - quantity)
     OR (movement_type =  'CONFIRM'                       AND qty_after = qty_before)
     OR (movement_type =  'DISPOSE' AND disposal_reason IS NOT NULL AND disposal_reason =  'RETURNED' AND qty_after = qty_before)
     OR (movement_type =  'DISPOSE' AND disposal_reason IS NOT NULL AND disposal_reason <> 'RETURNED' AND qty_after = qty_before - quantity)
     OR (movement_type =  'ADJUST'  AND (qty_after = qty_before + quantity OR qty_after = qty_before - quantity)))
); -- 재고 변동 이력(로트 단위 시간순). 재고에 관한 모든 사건의 단일 창구다. available_qty 를 바꾸는 연산과 같은 트랜잭션에서 함께 INSERT 하고, 재입고하지 않은 회수품 폐기처럼 값을 안 바꾸는 것은 qty_before = qty_after 로 남긴다

CREATE TABLE daily_sales (
    daily_sales_id  BIGINT       NOT NULL AUTO_INCREMENT, -- daily_sales PK
    product_option_id BIGINT     NOT NULL, -- 집계 대상 옵션 FK. 재고(stock_lot)와 소비기한이 옵션 단위라 집계도 같은 단위여야 한다. 상품 단위 수치는 옵션을 합산해 얻는다(반대 방향은 불가능하다)
    stat_date       DATE         NOT NULL, -- 집계 일자
    opening_stock   INT          NOT NULL DEFAULT 0, -- 기초 재고(그날 시작 시점 가용재고 스냅샷). 소진율 분모이고 다음 날 행의 이 값이 곧 이 날의 기말이다
    inbound_qty     INT          NOT NULL DEFAULT 0, -- 당일 신규 입고 수량. 소진율 분모
    restocked_qty   INT          NOT NULL DEFAULT 0, -- 당일 반품 재입고 수량. 소진율 분모에는 넣지 않는다. 새로 들여온 물량이 아니라 팔았다가 돌아온 것이라 분모에 넣으면 소진율이 낮아 보인다
    sold_qty        INT          NOT NULL DEFAULT 0, -- 당일 판매 수량(결제 완료 기준). 소진율 분자
    sold_amount     BIGINT       NOT NULL DEFAULT 0, -- 당일 판매 금액(결제 완료 기준)
    disposed_qty    INT          NOT NULL DEFAULT 0, -- 당일 폐기 수량. 로트로 돌아가지 않은 회수품 폐기는 available_qty 를 바꾸지 않으므로 여기 안 들어간다
    expired_qty     INT          NOT NULL DEFAULT 0, -- 당일 만료 전환 수량. 소비기한이 지나 판매 불가로 바뀐 것이며 폐기와 별개다
    created_at      DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME(6)  NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (daily_sales_id),
    UNIQUE KEY uk_daily_option_date (product_option_id, stat_date), -- 옵션+일자 1행(배치 재실행 시 UPSERT 덮어쓰기, 재조회 동일 결과 보장)
    CONSTRAINT fk_daily_option FOREIGN KEY (product_option_id) REFERENCES product_option (product_option_id),
    CONSTRAINT chk_daily_qty CHECK (opening_stock >= 0 AND inbound_qty >= 0 AND restocked_qty >= 0 AND sold_qty >= 0 AND sold_amount >= 0 AND disposed_qty >= 0 AND expired_qty >= 0)
); /* 판매 집계(일 1회 배치가 옵션별/일자별로 집계). 소진율 = 기간 sold_qty 합 / (기간 시작 opening_stock + 기간 inbound_qty 합).
      재고 대조는 이 표가 아니라 stock_movement 의 qty_before/qty_after 로 한다.
      배치는 움직임이 없는 옵션에도 행을 만든다. 다음 날 opening_stock 이 전날 기말이 되려면 날짜가 끊기면 안 된다 */

CREATE TABLE order_status_history (
    order_status_history_id      BIGINT       NOT NULL AUTO_INCREMENT, -- order_status_history PK
    order_id        BIGINT       NOT NULL, -- 주문 FK
    from_status     VARCHAR(30)  NULL, -- 이전 상태
    to_status       VARCHAR(30)  NOT NULL, -- 변경 상태(orders.status와 동일 집합)
    created_at      DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    PRIMARY KEY (order_status_history_id),
    CONSTRAINT chk_osh_to_status CHECK (to_status IN ('PAYMENT_PENDING','PAID','PRODUCT_PREPARING','SHIPMENT_PREPARING','SHIPPING','DELIVERED','CONFIRMED','RETURN_REQUESTED','RETURNED','EXCHANGE_REQUESTED','EXCHANGED','CANCELED')),
    CONSTRAINT chk_osh_from_status CHECK (from_status IS NULL OR from_status IN ('PAYMENT_PENDING','PAID','PRODUCT_PREPARING','SHIPMENT_PREPARING','SHIPPING','DELIVERED','CONFIRMED','RETURN_REQUESTED','RETURNED','EXCHANGE_REQUESTED','EXCHANGED','CANCELED')),
    CONSTRAINT fk_history_order FOREIGN KEY (order_id) REFERENCES orders (order_id)
); -- 주문 상태 이력

CREATE TABLE payment (
    payment_id      BIGINT       NOT NULL AUTO_INCREMENT, -- payment PK
    order_id        BIGINT       NOT NULL, -- 주문 FK(1:1)
    method          VARCHAR(30)  NOT NULL, -- 결제수단(CARD 카드/EASY_PAY 간편결제). 무통장입금은 두지 않는다
    amount          INT          NOT NULL, -- 결제 금액. 아래 복합 외래 키가 orders.total_amount 와 같기를 요구한다
    status          VARCHAR(30)  NOT NULL DEFAULT 'PENDING', -- 결제 상태(PENDING/PAID/FAILED/CANCELED/REFUNDED)
    refunded_amount INT          NOT NULL DEFAULT 0, -- 환불 누적액(PENDING 환불 포함). refund 행을 만들 때 조건부 UPDATE 로 올린다
    pg_tid          VARCHAR(100) NULL, -- PG 거래번호. 결제 요청 전에는 NULL 이다. UNIQUE 는 한 거래가 두 주문에 붙는 것을 막는다
    paid_at         DATETIME(6)  NULL, -- 결제 완료 시각(서버 애플리케이션이 기록)
    created_at      DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME(6)  NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (payment_id),
    CONSTRAINT chk_payment_method CHECK (method IN ('CARD','EASY_PAY')),
    CONSTRAINT chk_payment_status CHECK (status IN ('PENDING','PAID','FAILED','CANCELED','REFUNDED')),
    UNIQUE KEY uk_payment_order (order_id),
    UNIQUE KEY uk_payment_pg_tid (pg_tid),
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id, amount) REFERENCES orders (order_id, total_amount), /* 결제 금액이 주문 최종금액과 다를 수 없다.
                                                                                                              결제 행이 있는 동안 orders.total_amount 수정도 막힌다 */
    CONSTRAINT chk_payment_amount CHECK (amount > 0), -- 0원 주문은 이 행을 만들지 않으므로 0을 허용하지 않는다
    CONSTRAINT chk_payment_refunded CHECK (refunded_amount >= 0 AND refunded_amount <= amount), -- 환불 총액이 결제액을 넘을 수 없다
    CONSTRAINT chk_payment_pg_tid CHECK ( -- 완료된 결제는 거래번호를 갖는다. 모든 결제 수단이 PG를 타므로 예외가 없다
        status <> 'PAID' OR pg_tid IS NOT NULL),
    CONSTRAINT chk_payment_paid_at CHECK ( -- 상태와 완료 시각이 따로 놀지 않도록. CANCELED 는 결제 전 취소와 결제 후 취소가 모두 정상이라 제외한다
        (status IN ('PENDING','FAILED') AND paid_at IS NULL)
     OR (status IN ('PAID','REFUNDED')  AND paid_at IS NOT NULL)
     OR  status = 'CANCELED')
); -- 결제(주문당 최대 1건). total_amount 가 0인 주문은 이 행이 없다. refunded_amount 가 환불 총액의 상한을 쥔다

-- =====================================================================
-- 6. 클레임 (취소 / 반품 / 교환)
-- =====================================================================

CREATE TABLE claim (
    claim_id        BIGINT       NOT NULL AUTO_INCREMENT, -- claim PK
    order_id        BIGINT       NOT NULL, -- 주문 FK
    type            VARCHAR(30)  NOT NULL, -- 클레임 유형(CANCEL/RETURN/EXCHANGE)
    status          VARCHAR(30)  NOT NULL DEFAULT 'REQUESTED', -- 클레임 상태(REQUESTED/APPROVED/REJECTED/COMPLETED)
    reason_type     VARCHAR(30)  NULL, -- 사유 유형(CHANGE_OF_MIND/DEFECT)
    reason          VARCHAR(500) NULL, -- 사유 상세
    processed_by    BIGINT       NULL, -- admin (NULL=시스템 자동)
    processed_at    DATETIME(6)  NULL, -- 처리 시각
    collect_carrier      VARCHAR(50)  NULL, -- 회수 택배사(반품/교환. 회수=고객이 반품할 상품을 창고로 보내는 배송, 고객 -> 창고)
    collect_tracking_no  VARCHAR(50)  NULL, -- 회수 송장번호
    collect_shipped_at   DATETIME(6)  NULL, -- 회수(수거) 발송 시각
    collect_delivered_at DATETIME(6)  NULL, -- 회수 완료(입고) 시각
    reship_carrier       VARCHAR(50)  NULL, -- 재배송 택배사(교환. 재배송=창고가 교환할 새 상품을 고객에게 보내는 배송, 창고 -> 고객)
    reship_tracking_no   VARCHAR(50)  NULL, -- 재배송 송장번호
    reship_shipped_at    DATETIME(6)  NULL, -- 재배송 발송 시각
    reship_delivered_at  DATETIME(6)  NULL, -- 재배송 완료 시각
    created_at      DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME(6)  NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (claim_id),
    CONSTRAINT chk_claim_type CHECK (type IN ('CANCEL','RETURN','EXCHANGE')),
    CONSTRAINT chk_claim_status CHECK (status IN ('REQUESTED','APPROVED','REJECTED','COMPLETED')),
    CONSTRAINT chk_claim_reason_type CHECK (reason_type IS NULL OR reason_type IN ('CHANGE_OF_MIND','DEFECT')),
    CONSTRAINT chk_claim_collect_at CHECK (collect_delivered_at IS NULL OR collect_shipped_at IS NULL OR collect_delivered_at >= collect_shipped_at),
    CONSTRAINT chk_claim_collect_type CHECK ( -- 회수는 반품/교환에만 있다. 취소는 물건이 나간 적이 없다
        type <> 'CANCEL'
     OR (collect_carrier IS NULL AND collect_tracking_no IS NULL AND collect_shipped_at IS NULL AND collect_delivered_at IS NULL)),
    CONSTRAINT chk_claim_reship_type CHECK ( /* 재배송은 승인된 교환에만 있다. 취소와 반품은 새 상품을 보내지 않고,
                                                거부되거나 아직 접수 상태인 교환도 새 상품을 보내지 않는다.
                                                회수(collect)는 승인 전에 물건이 먼저 도착할 수 있어 상태를 걸지 않는다 */
        (type = 'EXCHANGE' AND status IN ('APPROVED','COMPLETED'))
     OR (reship_carrier IS NULL AND reship_tracking_no IS NULL AND reship_shipped_at IS NULL AND reship_delivered_at IS NULL)),
    CONSTRAINT chk_claim_reship_at CHECK (reship_delivered_at IS NULL OR reship_shipped_at IS NULL OR reship_delivered_at >= reship_shipped_at),
    CONSTRAINT chk_claim_processed_at CHECK ( -- 처리된 클레임은 처리 시각을 갖는다. processed_by 는 시스템 자동 처리가 있어 NULL 을 허용하므로 시각만 본다
        (status =  'REQUESTED' AND processed_at IS NULL)
     OR (status <> 'REQUESTED' AND processed_at IS NOT NULL)),
    UNIQUE KEY uk_claim_id_order (claim_id, order_id), -- claim_item 이 복합 외래 키로 참조한다. claim_id 만으로도 유일하지만 FK 대상이 되려면 이 조합에 인덱스가 있어야 한다
    CONSTRAINT fk_claim_order FOREIGN KEY (order_id) REFERENCES orders (order_id),
    CONSTRAINT fk_claim_admin FOREIGN KEY (processed_by) REFERENCES admin (admin_id)
); -- 클레임(취소/반품/교환. 회수/재배송 송장, 시각, 상태 흡수)

CREATE TABLE claim_attachment (
    claim_attachment_id BIGINT       NOT NULL AUTO_INCREMENT, -- claim_attachment PK
    claim_id            BIGINT       NOT NULL, -- 클레임 FK
    upload_id           BINARY(16)   NOT NULL, -- 업로드 세션 식별자(UUID v7). 완료 통지에서 이 값으로 행을 찾는다(INF-11-04). 리소스 식별자가 아니다
    object_key          VARCHAR(255) NOT NULL, -- S3 객체 key. URL 을 통째로 저장하지 않는다(INF-11-05)
    upload_status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING 발급만 됨 / CONFIRMED HeadObject 로 확인함(INF-11-10). 조회는 CONFIRMED 만(INF-11-09), PENDING 은 정리 대상
    created_at          DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at          DATETIME(6)  NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (claim_attachment_id),
    UNIQUE KEY uk_claim_attachment_upload (upload_id),
    UNIQUE KEY uk_claim_attachment_key (object_key), -- 완료 통지를 두 번 받아도 같은 key 가 두 행이 되지 않는다
    KEY idx_claim_attachment_pending (upload_status, created_at), -- 미확정 행 스윕(정리 배치)이 풀스캔이 되지 않도록
    CONSTRAINT chk_claim_attachment_status CHECK (upload_status IN ('PENDING','CONFIRMED')),
    CONSTRAINT fk_claim_attachment_claim FOREIGN KEY (claim_id) REFERENCES claim (claim_id)
); -- 클레임 증빙 사진(파손, 오배송). 비공개다

CREATE TABLE claim_item (
    claim_item_id   BIGINT       NOT NULL AUTO_INCREMENT, -- claim_item PK
    claim_id        BIGINT       NOT NULL, -- 클레임 FK
    order_item_id   BIGINT       NOT NULL, -- 대상 주문 상품 FK
    order_id        BIGINT       NOT NULL, -- 조상 키 복제. 복합 외래 키가 클레임과 주문 상품을 같은 주문으로 묶는다
    created_at      DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    PRIMARY KEY (claim_item_id),
    UNIQUE KEY uk_claimitem_claim_orderitem (claim_id, order_item_id), -- '클레임 내 동일 항목 중복 방지'
    CONSTRAINT fk_claimitem_claim FOREIGN KEY (claim_id, order_id) REFERENCES claim (claim_id, order_id),
    CONSTRAINT fk_claimitem_orderitem FOREIGN KEY (order_item_id, order_id) REFERENCES order_item (order_item_id, order_id)
); -- 클레임 대상 상품(라인 단위. 부분 수량을 지정하지 않는다)

CREATE TABLE refund (
    refund_id           BIGINT   NOT NULL AUTO_INCREMENT, -- refund PK
    claim_id            BIGINT   NOT NULL, -- 클레임 FK
    order_id            BIGINT   NOT NULL, /* 조상 키 복제. 아래 외래 키 둘이 이 값을 함께 요구한다.
                                              하나는 클레임의 주문임을 고정하고, 다른 하나는 그 주문에 결제가 있기를 요구한다.
                                              0원 주문은 payment 행이 없으므로 환불 행도 만들 수 없다 */
    amount              INT      NOT NULL, -- 환불액
    shipping_deduction  INT      NOT NULL DEFAULT 0, -- 단순변심 배송비 차감
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- 환불 상태(PENDING/DONE)
    refunded_at         DATETIME(6) NULL, -- 환불 완료 시각
    created_at      DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME(6)  NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (refund_id),
    CONSTRAINT chk_refund_status CHECK (status IN ('PENDING','DONE')),
    UNIQUE KEY uk_refund_claim (claim_id),
    CONSTRAINT fk_refund_claim FOREIGN KEY (claim_id, order_id) REFERENCES claim (claim_id, order_id),
    CONSTRAINT fk_refund_payment FOREIGN KEY (order_id) REFERENCES payment (order_id), -- 환불 상한(payment.refunded_amount)을 쥔 행에 직접 닿는다
    CONSTRAINT chk_refund_amount CHECK (amount >= 0),
    CONSTRAINT chk_refund_shipping_deduction CHECK (shipping_deduction >= 0),
    CONSTRAINT chk_refund_refunded_at CHECK ( -- 완료된 환불은 완료 시각을 갖는다
        (status = 'PENDING' AND refunded_at IS NULL)
     OR (status = 'DONE'    AND refunded_at IS NOT NULL))
); -- 환불(돈에 대한 것만). 클레임당 1건이고, 총액 상한은 payment.refunded_amount 가 쥔다

CREATE TABLE shipment (
    shipment_id   BIGINT       NOT NULL AUTO_INCREMENT, -- shipment PK
    order_id      BIGINT       NOT NULL, -- 주문 FK(1:1). 분할 배송을 하지 않으므로 주문의 모든 라인이 이 배송에 실린다
    carrier       VARCHAR(50)  NULL, -- 택배사
    tracking_no   VARCHAR(50)  NULL, -- 송장번호
    status        VARCHAR(30)  NOT NULL DEFAULT 'PREPARING', -- 배송 상태(PREPARING 준비/SHIPPING 배송중/DELIVERED 배송완료)
    shipped_at    DATETIME(6)  NULL, -- 발송 시각
    delivered_at  DATETIME(6)  NULL, -- 배송 완료 시각
    created_at    DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at    DATETIME(6)  NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (shipment_id),
    UNIQUE KEY uk_shipment_order (order_id), -- 주문당 1건. 분할 배송을 하면 어느 라인이 어느 송장에 실렸는지 담을 곳이 필요해진다
    CONSTRAINT chk_shipment_status CHECK (status IN ('PREPARING','SHIPPING','DELIVERED')),
    CONSTRAINT fk_shipment_order FOREIGN KEY (order_id) REFERENCES orders (order_id),
    CONSTRAINT chk_shipment_delivered_at CHECK (delivered_at IS NULL OR shipped_at IS NULL OR delivered_at >= shipped_at),
    CONSTRAINT chk_shipment_status_at CHECK ( -- 상태마다 채워져 있어야 할 시각이 정해진다. 위 제약은 순서만 보고 짝은 보지 않는다
        (status = 'PREPARING' AND shipped_at IS NULL     AND delivered_at IS NULL)
     OR (status = 'SHIPPING'  AND shipped_at IS NOT NULL AND delivered_at IS NULL)
     OR (status = 'DELIVERED' AND shipped_at IS NOT NULL AND delivered_at IS NOT NULL))
); -- 배송(출고 전용. 주문당 1건. 회수/재배송은 claim이 흡수)

CREATE TABLE shipment_photo (
    shipment_photo_id BIGINT       NOT NULL AUTO_INCREMENT, -- shipment_photo PK
    shipment_id       BIGINT       NOT NULL, -- 배송 FK
    upload_id         BINARY(16)   NOT NULL, -- 업로드 세션 식별자(UUID v7). claim_attachment 와 같은 용도다
    object_key        VARCHAR(255) NOT NULL, -- S3 객체 key. URL 을 통째로 저장하지 않는다(INF-11-05)
    upload_status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING 발급만 됨 / CONFIRMED HeadObject 로 확인함(INF-11-10). 조회는 CONFIRMED 만(INF-11-09), PENDING 은 정리 대상
    created_at        DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at        DATETIME(6)  NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (shipment_photo_id),
    UNIQUE KEY uk_shipment_photo_upload (upload_id),
    UNIQUE KEY uk_shipment_photo_key (object_key),
    KEY idx_shipment_photo_pending (upload_status, created_at), -- 미확정 행 스윕(정리 배치)이 풀스캔이 되지 않도록
    CONSTRAINT chk_shipment_photo_status CHECK (upload_status IN ('PENDING','CONFIRMED')),
    CONSTRAINT fk_shipment_photo_shipment FOREIGN KEY (shipment_id) REFERENCES shipment (shipment_id)
); -- 문앞 배송 완료 사진. 비공개다

-- =====================================================================
-- 7. 리뷰 / Q&A
-- =====================================================================

CREATE TABLE review (
    review_id       BIGINT       NOT NULL AUTO_INCREMENT, -- review PK
    product_id      BIGINT       NOT NULL, -- 상품 FK. 조회를 위해 들고 있으며 아래 복합 외래 키가 order_item 의 상품과 같음을 강제한다
    product_option_id BIGINT     NOT NULL, -- 조상 키 복제. order_item 과 product 를 잇는 사슬의 가운데 고리다
    member_id       BIGINT       NOT NULL, -- 작성 회원 FK. 아래 복합 외래 키가 그 주문의 회원과 같음을 강제한다
    order_item_id   BIGINT       NOT NULL, -- 구매 확인용 주문 상품 FK(구매 건당 1회)
    rating          TINYINT      NOT NULL, -- 1~5
    title           VARCHAR(255) NULL, -- 리뷰 제목
    content         TEXT         NOT NULL, -- 리뷰 본문
    is_public       BOOLEAN      NOT NULL DEFAULT TRUE, -- 공개 여부
    deleted_at      DATETIME(6)  NULL, -- 소프트딜리트
    created_at      DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME(6)  NOT NULL, -- 수정 시각(애플리케이션에서 생성)
    PRIMARY KEY (review_id),
    UNIQUE KEY uk_review_orderitem (order_item_id), -- 구매 건당 1회. 지운 리뷰의 주문 상품에 다시 쓰지 않는다
    CONSTRAINT fk_review_orderitem FOREIGN KEY (order_item_id, product_option_id, member_id) REFERENCES order_item (order_item_id, product_option_id, member_id), -- 옵션과 회원을 함께 요구해 남의 구매나 다른 상품에 리뷰를 쓸 수 없다
    CONSTRAINT fk_review_option FOREIGN KEY (product_option_id, product_id) REFERENCES product_option (product_option_id, product_id), -- 옵션과 상품을 잇는다. product_id 는 이 사슬로 검증되므로 product 를 직접 참조하지 않는다
    CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5)
); -- 상품 리뷰(복합 외래 키 둘이 주문 상품, 옵션, 상품, 구매자를 묶는다)

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
    deleted_at      DATETIME(6)  NULL, -- 소프트딜리트
    created_at      DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    updated_at      DATETIME(6)  NOT NULL, -- 수정 시각(애플리케이션에서 생성)
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
--     created_at      DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
--     updated_at      DATETIME(6)  NOT NULL, -- 수정 시각(애플리케이션에서 생성)
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
    created_at      DATETIME(6)  NOT NULL, -- 생성 시각(애플리케이션에서 생성)
    PRIMARY KEY (audit_log_id),
    CONSTRAINT fk_audit_admin FOREIGN KEY (admin_id) REFERENCES admin (admin_id)
); -- 감사 로그
