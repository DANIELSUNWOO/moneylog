# 백업/복구 리허설 기록

## 배경

앞서 `docs/troubleshooting-cors-signup.md`에서 CORS 문제를 진단하다가 MySQL 볼륨의 root 비밀번호가 오래전 초기화 시점 값 그대로 굳어있다는 걸 발견했다. 그 일을 계기로 "만약 진짜로 데이터가 통째로 날아가면 어떻게 되는가"를 미리 검증해두기로 했다. `scripts/backup-db.sh` + cron으로 매일 새벽 4시 자동 백업은 이미 구성해뒀지만(`mysqldump --single-transaction --no-tablespaces`로 덤프 후 gzip, S3 `moneylog-db-backup-847263217053` 버킷에 업로드, 로컬은 최근 7개만 보관), **백업 파일이 실제로 복구 가능한지는 한 번도 검증한 적이 없었다.** 백업은 "찍기만 하고 되감아본 적 없는 테이프"일 때 가장 위험하다 — 파일이 쌓이고 있다는 사실 자체가 안전하다는 증거가 되지는 않는다. 그래서 실제로 데이터를 지우고, 그 백업으로 되살리는 전 과정을 리허설했다.

## 설계

두 가지를 의도적으로 진짜 재해 상황에 가깝게 맞췄다.

- **로컬 디스크의 기존 백업을 쓰지 않고, S3에서 새로 내려받았다.** EC2 로컬 `backups/` 폴더에 있는 파일을 그대로 쓰면 "EC2 인스턴스 자체는 멀쩡히 살아있다"는 전제가 깔린다. 만약 EC2가 통째로 사라지는(인스턴스 종료, 디스크 손상 등) 진짜 재해라면 로컬 백업도 함께 사라진다. 그래서 `aws s3 cp`로 별도 디렉터리(`restore-test/`)에 새로 받아, "인스턴스 바깥에 있는 백업만으로 복구 가능한가"를 검증했다.
- **DB 자체를 지우는 대신 테이블 세 개를 전부 `DROP TABLE`했다.** `DROP DATABASE`는 애플리케이션 계정(`DB_USER`)의 스키마 레벨 권한 범위가 불확실해서 피했고(공식 MySQL 이미지는 `MYSQL_USER`에게 지정된 DB 이름 안에서의 권한만 부여한다), 테이블을 전부 지우면 앱 입장에서는 DB가 통째로 사라진 것과 체감상 동일한 장애(`Table doesn't exist`)가 나기 때문에 리허설 목적에는 충분했다.

## 실행 결과

SSM Session Manager로 EC2에 접속해(포트 22 없이) 아래 순서로 진행했다.

**1) 복구 전 기준점(스냅샷) 확인**

```
$ docker exec -i moneylog-mysql mysql -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -e "SHOW TABLES;"
Tables_in_moneylog
categories
transactions
users

users_count: 1
categories_count: 6
transactions_count: 0
```

**2) S3에서 최신 백업을 새 디렉터리로 다운로드**

```
$ aws s3 ls s3://moneylog-db-backup-847263217053/
2026-09-07 10:59:42       1430 moneylog-20260907-105940.sql.gz
2026-09-07 11:01:48       1430 moneylog-20260907-110146.sql.gz

$ aws s3 cp s3://moneylog-db-backup-847263217053/moneylog-20260907-110146.sql.gz /home/ec2-user/moneylog/restore-test/
$ gunzip -k /home/ec2-user/moneylog/restore-test/moneylog-20260907-110146.sql.gz
```

`head`로 내용을 확인해 `-- Host: localhost  Database: moneylog`, `Server version 8.0.46`이 찍힌 정상적인 `mysqldump` 결과물임을 확인했다.

**3) 재해 시뮬레이션 — 테이블 전체 삭제**

```
$ docker exec -i moneylog-mysql mysql -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -e "SET FOREIGN_KEY_CHECKS=0; DROP TABLE transactions; DROP TABLE categories; DROP TABLE users; SET FOREIGN_KEY_CHECKS=1;"

$ docker exec -i moneylog-mysql mysql -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -e "SHOW TABLES;"
(출력 없음 — 테이블이 하나도 없음)
```

`transactions`가 `users`/`categories`를 참조할 가능성이 있어 `FOREIGN_KEY_CHECKS=0`으로 순서 제약 없이 한 번에 지웠다. `SHOW TABLES`가 완전히 빈 결과를 반환해, DB는 살아있지만 스키마 자체가 사라진 상태를 확정했다.

**4) 복구**

```
$ docker exec -i moneylog-mysql mysql -u "$DB_USER" -p"$DB_PASSWORD" < /home/ec2-user/moneylog/restore-test/moneylog-20260907-110146.sql
```

`mysqldump`가 `--databases` 옵션으로 만들어졌기 때문에 덤프 파일 안에 `CREATE DATABASE IF NOT EXISTS`/`USE moneylog` 구문이 포함돼 있어, 복구 명령에 DB 이름을 따로 지정할 필요가 없었다. 에러 없이 완료됐다.

**5) 복구 검증 — 재해 이전 스냅샷과 정확히 일치**

```
$ docker exec -i moneylog-mysql mysql -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -e "SHOW TABLES; SELECT COUNT(*) AS users_count FROM users; SELECT COUNT(*) AS categories_count FROM categories; SELECT COUNT(*) AS transactions_count FROM transactions;"

Tables_in_moneylog
categories
transactions
users

users_count: 1
categories_count: 6
transactions_count: 0
```

1)번에서 기록해둔 값과 테이블 목록·행 개수 모두 완전히 일치했다.

**6) 정리**

```
$ rm -rf /home/ec2-user/moneylog/restore-test
```

리허설용으로 받았던 임시 백업 파일은 삭제했다. S3의 원본 백업과 로컬 `backups/` 디렉터리의 정기 백업에는 영향 없다.

## 결론

- 매일 새벽 4시 자동으로 쌓이는 백업이, 실제로 데이터를 완전히 잃어버린 뒤 원상 복구할 수 있는 유효한 백업이라는 걸 실제 삭제·복구로 검증했다.
- 복구는 로컬 EC2 디스크가 아니라 S3에서 새로 내려받은 파일만으로 이루어졌으므로, EC2 인스턴스 자체가 사라지는 재해 상황도 커버한다.
- 다음에 실제로 데이터 유실이 발생하면: `aws s3 ls s3://moneylog-db-backup-847263217053/`로 최신 백업 파일명을 확인 → `aws s3 cp`로 다운로드 → `gunzip` → `docker exec -i moneylog-mysql mysql -u "$DB_USER" -p"$DB_PASSWORD" < 파일.sql`로 복구하면 된다. 이 문서의 2)·4)번 명령을 그대로 쓰면 된다.

## 배운 점

백업 자동화(cron)를 구성한 시점과, 그 백업이 실제로 쓸모 있다는 걸 증명한 시점은 다르다. 백업 스크립트가 매일 조용히 파일을 쌓고 있어도, 복구 명령이 실제로 동작하는지, 압축이 깨지지 않았는지, 계정 권한이 복구 시점에도 충분한지는 직접 지우고 되살려보기 전까지는 가정에 불과하다. 이번 리허설로 "백업이 존재한다"와 "백업으로 복구할 수 있다" 사이의 간극을 실제로 좁혀뒀다.

또한 로컬 백업이 아니라 S3에서 새로 받는 방식으로 진행한 것은, 롤백 리허설 때 배운 것과 같은 원칙의 연장이다 — 리허설은 가장 쉬운 경로가 아니라 실제 장애 시나리오와 가장 가까운 경로로 해야 의미가 있다.
