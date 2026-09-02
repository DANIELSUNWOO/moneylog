-- =====================================================================
-- 머니로그(MoneyLog) 테이블 정의서 (MySQL 8)
--
-- 목적: 설계 검증용 문서. 실제 테이블은 JPA(spring.jpa.hibernate.ddl-auto)가
--       엔티티로부터 생성한다. "내 엔티티가 어떤 테이블이 되는지"를 확인하고,
--       ERD(docs/erd.md)와 코드가 어긋나지 않았는지 대조하는 데 쓴다.
-- 관련: docs/erd.md, docs/requirements.md
-- =====================================================================

-- ---------------------------------------------------------------------
-- users : 로그인 주체이자 모든 데이터의 소유자
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    email       VARCHAR(255) NOT NULL,
    password    VARCHAR(255) NOT NULL,                 -- BCrypt 해시(평문 저장 금지)
    nickname    VARCHAR(50)  NOT NULL,
    created_at  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)                  -- 로그인 ID 중복 방지
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- categories : 거래 분류 (User 1:N Category)
-- D-7) 같은 사용자가 같은 이름/타입 카테고리를 중복 생성하지 못하게 유니크
-- ---------------------------------------------------------------------
CREATE TABLE categories (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    name        VARCHAR(50)  NOT NULL,
    type        ENUM('INCOME','EXPENSE') NOT NULL,
    created_at  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_categories_user_name_type (user_id, name, type),
    CONSTRAINT fk_categories_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- transactions : 수입/지출 한 건 (User 1:N, Category 1:N)
--
-- D-1) type은 categories.type과 중복이지만 의도적으로 유지한다.
--      통계/필터에서 JOIN 없이 집계하기 위함. 대신 서비스 레이어에서
--      category.type == transaction.type 을 반드시 검증한다.
-- D-2) 카테고리 삭제 시 거래가 있으면 차단 → ON DELETE RESTRICT
-- ---------------------------------------------------------------------
CREATE TABLE transactions (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    user_id           BIGINT       NOT NULL,
    category_id       BIGINT       NOT NULL,
    type              ENUM('INCOME','EXPENSE') NOT NULL,
    amount            BIGINT       NOT NULL,           -- 원 단위 long, 항상 > 0
    description       VARCHAR(255) NULL,
    transaction_date  DATE         NOT NULL,           -- LocalDate
    created_at        DATETIME     NOT NULL,
    updated_at        DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_transactions_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_transactions_category
        FOREIGN KEY (category_id) REFERENCES categories(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT ck_transactions_amount_positive CHECK (amount > 0),

    -- F-04 목록 조회(사용자별 + 월 범위)와 F-05 월별 통계의 주 경로
    KEY idx_tx_user_date (user_id, transaction_date),
    -- 카테고리별 지출 집계(byCategory)와 카테고리 필터
    KEY idx_tx_user_category_date (user_id, category_id, transaction_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- budgets : 월 예산 (도전 과제 F-11)
--
-- D-3) year_month → budget_month 로 변경 (YEAR_MONTH는 MySQL 예약어)
-- D-4) (user_id, category_id, budget_month) 유니크로 중복 방지.
--      단 MySQL은 NULL을 서로 다른 값으로 취급하므로, category_id가 NULL인
--      "전체 예산"은 이 유니크 키로 중복이 막히지 않는다.
--      → 전체 예산 중복은 서비스 레이어에서 검증한다.
--      → DB 레벨로 막고 싶다면 아래 생성 컬럼 방식을 쓸 수 있다:
--          category_key BIGINT AS (IFNULL(category_id, 0)) STORED,
--          UNIQUE KEY uk_budgets_user_catkey_month (user_id, category_key, budget_month)
-- ---------------------------------------------------------------------
CREATE TABLE budgets (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    user_id       BIGINT      NOT NULL,
    category_id   BIGINT      NULL,                    -- NULL = 전체 예산
    budget_month  VARCHAR(7)  NOT NULL,                -- "2026-09"
    limit_amount  BIGINT      NOT NULL,
    created_at    DATETIME    NOT NULL,
    updated_at    DATETIME    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_budgets_user_category_month (user_id, category_id, budget_month),
    CONSTRAINT fk_budgets_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_budgets_category
        FOREIGN KEY (category_id) REFERENCES categories(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT ck_budgets_limit_positive CHECK (limit_amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================
-- 설계 검증용 샘플 쿼리 (인가 규칙이 지켜지는지 확인하는 형태)
-- =====================================================================

-- F-04) 이번 달 목록 (항상 user_id 조건이 붙는다)
-- SELECT * FROM transactions
--  WHERE user_id = :loginUserId
--    AND transaction_date BETWEEN :monthStart AND :monthEnd
--  ORDER BY transaction_date DESC, id DESC
--  LIMIT :size OFFSET :offset;

-- F-05) 월별 총수입/총지출
-- SELECT type, SUM(amount) AS total
--   FROM transactions
--  WHERE user_id = :loginUserId
--    AND transaction_date BETWEEN :monthStart AND :monthEnd
--  GROUP BY type;

-- F-05) 카테고리별 지출 합계
-- SELECT c.id, c.name, SUM(t.amount) AS total
--   FROM transactions t
--   JOIN categories c ON c.id = t.category_id
--  WHERE t.user_id = :loginUserId
--    AND t.type = 'EXPENSE'
--    AND t.transaction_date BETWEEN :monthStart AND :monthEnd
--  GROUP BY c.id, c.name
--  ORDER BY total DESC;

-- F-06) 단건 조회/수정/삭제는 반드시 user_id를 함께 건다
-- SELECT * FROM transactions WHERE id = :txId AND user_id = :loginUserId;
