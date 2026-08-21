-- API-5-07(AIP-155) 대응: 로트 입고 재시도 시 중복 생성을 막기 위한 요청 식별자
ALTER TABLE stock_lot
    ADD COLUMN request_id VARCHAR(100) NOT NULL AFTER product_option_id,
    ADD CONSTRAINT uk_lot_request_id UNIQUE (request_id);
