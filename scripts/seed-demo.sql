-- 데모 계정 시드 데이터
--
-- 목적: 배포된 서비스를 회원가입 없이 바로 둘러볼 수 있도록 데모 계정에 현실적인 거래 내역을 넣는다.
-- 심사자·면접관이 빈 화면 대신 실제로 동작하는 목록과 통계를 보게 하는 것이 목적이다.
--
-- 사전 조건: demo@example.com 계정이 가입되어 있어야 한다(가입 시 기본 카테고리 6개가 함께 생성된다).
--            example.com은 RFC 2606이 문서·예제용으로 예약한 도메인이라 실제 소유자가 존재하지 않는다.
--
-- 실행 (EC2에서):
--   cd /home/ec2-user/moneylog
--   docker exec -i moneylog-mysql mysql -u root -p"$DB_ROOT_PASSWORD" moneylog < scripts/seed-demo.sql
--
-- 여러 번 실행해도 안전하다. 기존 데모 거래를 지우고 다시 넣으므로,
-- 방문자가 데모 데이터를 바꿔놨을 때 초기화 용도로 그대로 재실행하면 된다.

SET @uid    = (SELECT id FROM users WHERE email = 'demo@example.com');
SET @food   = (SELECT id FROM categories WHERE user_id = @uid AND name = '식비' AND type = 'EXPENSE');
SET @trans  = (SELECT id FROM categories WHERE user_id = @uid AND name = '교통' AND type = 'EXPENSE');
SET @home   = (SELECT id FROM categories WHERE user_id = @uid AND name = '주거' AND type = 'EXPENSE');
SET @cult   = (SELECT id FROM categories WHERE user_id = @uid AND name = '문화' AND type = 'EXPENSE');
SET @salary = (SELECT id FROM categories WHERE user_id = @uid AND name = '급여' AND type = 'INCOME');
SET @allow  = (SELECT id FROM categories WHERE user_id = @uid AND name = '용돈' AND type = 'INCOME');

-- @uid가 NULL이면 계정이 없다는 뜻이며, 아래 INSERT가 외래키 제약으로 실패해 알려준다.

DELETE FROM transactions WHERE user_id = @uid;

INSERT INTO transactions
  (user_id, category_id, type, amount, description, transaction_date, created_at, updated_at)
VALUES
  (@uid, @home, 'EXPENSE', 550000, '월세', '2026-07-01', NOW(), NOW()),
  (@uid, @food, 'EXPENSE', 23000, '장보기', '2026-07-01', NOW(), NOW()),
  (@uid, @food, 'EXPENSE', 14500, '저녁 식사', '2026-07-01', NOW(), NOW()),
  (@uid, @home, 'EXPENSE', 62000, '관리비', '2026-07-02', NOW(), NOW()),
  (@uid, @cult, 'EXPENSE', 55000, '영화', '2026-07-02', NOW(), NOW()),
  (@uid, @salary, 'INCOME', 2780000, '7월 급여', '2026-07-05', NOW(), NOW()),
  (@uid, @trans, 'EXPENSE', 20000, '지하철 충전', '2026-07-06', NOW(), NOW()),
  (@uid, @trans, 'EXPENSE', 47000, 'KTX 예매', '2026-07-07', NOW(), NOW()),
  (@uid, @food, 'EXPENSE', 14500, '점심 식사', '2026-07-10', NOW(), NOW()),
  (@uid, @food, 'EXPENSE', 9000, '저녁 식사', '2026-07-10', NOW(), NOW()),
  (@uid, @allow, 'INCOME', 100000, '용돈', '2026-07-12', NOW(), NOW()),
  (@uid, @trans, 'EXPENSE', 8900, '버스 환승', '2026-07-12', NOW(), NOW()),
  (@uid, @food, 'EXPENSE', 35000, '카페', '2026-07-15', NOW(), NOW()),
  (@uid, @food, 'EXPENSE', 23000, '배달 음식', '2026-07-16', NOW(), NOW()),
  (@uid, @trans, 'EXPENSE', 47000, '버스 환승', '2026-07-16', NOW(), NOW()),
  (@uid, @cult, 'EXPENSE', 55000, '전시회', '2026-07-20', NOW(), NOW()),
  (@uid, @food, 'EXPENSE', 12000, '장보기', '2026-07-25', NOW(), NOW()),
  (@uid, @food, 'EXPENSE', 9000, '장보기', '2026-07-28', NOW(), NOW()),
  (@uid, @home, 'EXPENSE', 550000, '월세', '2026-08-01', NOW(), NOW()),
  (@uid, @home, 'EXPENSE', 84000, '관리비', '2026-08-02', NOW(), NOW()),
  (@uid, @food, 'EXPENSE', 18000, '카페', '2026-08-04', NOW(), NOW()),
  (@uid, @food, 'EXPENSE', 14500, '점심 식사', '2026-08-04', NOW(), NOW()),
  (@uid, @salary, 'INCOME', 2780000, '8월 급여', '2026-08-05', NOW(), NOW()),
  (@uid, @cult, 'EXPENSE', 17000, '도서 구입', '2026-08-05', NOW(), NOW()),
  (@uid, @trans, 'EXPENSE', 8900, '버스 환승', '2026-08-09', NOW(), NOW()),
  (@uid, @food, 'EXPENSE', 9000, '편의점', '2026-08-10', NOW(), NOW()),
  (@uid, @allow, 'INCOME', 100000, '용돈', '2026-08-11', NOW(), NOW()),
  (@uid, @food, 'EXPENSE', 23000, '배달 음식', '2026-08-12', NOW(), NOW()),
  (@uid, @food, 'EXPENSE', 12000, '저녁 식사', '2026-08-14', NOW(), NOW()),
  (@uid, @cult, 'EXPENSE', 13000, '넷플릭스 구독', '2026-08-16', NOW(), NOW()),
  (@uid, @food, 'EXPENSE', 14500, '회식', '2026-08-18', NOW(), NOW()),
  (@uid, @trans, 'EXPENSE', 30000, '버스 환승', '2026-08-18', NOW(), NOW()),
  (@uid, @trans, 'EXPENSE', 30000, '버스 환승', '2026-08-22', NOW(), NOW()),
  (@uid, @cult, 'EXPENSE', 17000, '전시회', '2026-08-27', NOW(), NOW()),
  (@uid, @food, 'EXPENSE', 23000, '편의점', '2026-08-28', NOW(), NOW()),
  (@uid, @food, 'EXPENSE', 9000, '저녁 식사', '2026-08-29', NOW(), NOW()),
  (@uid, @home, 'EXPENSE', 550000, '월세', '2026-09-01', NOW(), NOW()),
  (@uid, @food, 'EXPENSE', 18000, '점심 식사', '2026-09-01', NOW(), NOW()),
  (@uid, @home, 'EXPENSE', 62000, '관리비', '2026-09-02', NOW(), NOW()),
  (@uid, @trans, 'EXPENSE', 20000, 'KTX 예매', '2026-09-03', NOW(), NOW()),
  (@uid, @food, 'EXPENSE', 14500, '카페', '2026-09-04', NOW(), NOW()),
  (@uid, @food, 'EXPENSE', 12000, '점심 식사', '2026-09-04', NOW(), NOW()),
  (@uid, @cult, 'EXPENSE', 55000, '도서 구입', '2026-09-04', NOW(), NOW()),
  (@uid, @salary, 'INCOME', 2780000, '9월 급여', '2026-09-05', NOW(), NOW()),
  (@uid, @trans, 'EXPENSE', 1550, '버스 환승', '2026-09-06', NOW(), NOW()),
  (@uid, @cult, 'EXPENSE', 55000, '넷플릭스 구독', '2026-09-06', NOW(), NOW()),
  (@uid, @food, 'EXPENSE', 23000, '카페', '2026-09-07', NOW(), NOW()),
  (@uid, @food, 'EXPENSE', 12000, '배달 음식', '2026-09-09', NOW(), NOW()),
  (@uid, @trans, 'EXPENSE', 20000, '택시', '2026-09-09', NOW(), NOW());

SELECT CONCAT('시드 완료: ', COUNT(*), '건') AS result FROM transactions WHERE user_id = @uid;
