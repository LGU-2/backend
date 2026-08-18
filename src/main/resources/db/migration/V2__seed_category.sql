-- 확정된 최상위 카테고리 5종을 초기 데이터로 심는다
INSERT INTO category (name, created_at, updated_at) VALUES
('수산물', NOW(6), NOW(6)),
('육류',   NOW(6), NOW(6)),
('채소',   NOW(6), NOW(6)),
('과일',   NOW(6), NOW(6)),
('유제품', NOW(6), NOW(6));
