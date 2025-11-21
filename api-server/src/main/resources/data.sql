-- ========================================
-- 초기 데이터 스크립트
-- ========================================

-- 관리자 생성
INSERT INTO admin (id, username, role, created_at) VALUES
    (1, 'admin1', 'ADMIN', CURRENT_TIMESTAMP),
    (2, 'manager1', 'MANAGER', CURRENT_TIMESTAMP),
    (3, 'manager2', 'MANAGER', CURRENT_TIMESTAMP);

-- 사업장 생성
INSERT INTO business_place (business_number, name, collection_status, created_at, updated_at) VALUES
    ('1234567890', '테스트 주식회사', 'NOT_REQUESTED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('0987654321', '샘플 상사', 'NOT_REQUESTED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('1111111111', '데모 기업', 'NOT_REQUESTED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 권한 부여 (manager1은 사업장 2개, manager2는 사업장 1개)
INSERT INTO business_place_admin (id, business_number, admin_id, granted_at) VALUES
    (1, '1234567890', 2, CURRENT_TIMESTAMP),
    (2, '0987654321', 2, CURRENT_TIMESTAMP),
    (3, '0987654321', 3, CURRENT_TIMESTAMP);
