# 1. 프로젝트 개요

퓨쳐스콜레 프로덕트 엔지니어 채용 과제 중 **BE 과제 A - 수강 신청 시스템**을 구현한 프로젝트입니다.

크리에이터는 강의를 등록하고 모집 상태를 관리할 수 있으며, 클래스메이트는 모집 중인 강의에 수강 신청, 결제 확정, 취소를 수행할 수 있습니다. 정원 초과 신청과 중복 신청을 방지하고, 동시에 여러 사용자가 마지막 자리에 신청하는 상황을 고려해 수강 인원 카운트를 별도 엔티티로 관리합니다.

구현 범위는 다음과 같습니다.

- 강의 관리
  - 강의 등록
  - 강의 상태 변경
  - 강의 목록 조회
  - 강의 상세 조회
  - 강의별 수강생 목록 조회
- 수강 신청 관리
  - 수강 신청
  - 결제 확정
  - 수강 취소
  - 내 신청 목록 조회 (페이지네이션 처리)
- 대기열 관리
  - 대기열 등록
  - 대기 취소
  - 수강 신청으로 승격처리

# 2. 기술 스택

- Java 21
- Spring Boot 3.5.0
- Spring Data JPA
- QueryDSL 7.1
- Spring Retry
- H2
- Gradle
- JUnit 5
- Lombok

# 3. 실행 방법

### 사전 요구사항

- JDK 21

### 애플리케이션 실행

```bash
./gradlew bootRun
```

Windows PowerShell에서는 다음 명령을 사용할 수 있습니다.

```powershell
.\gradlew.bat bootRun
```

애플리케이션은 기본적으로 `http://localhost:8080`에서 실행됩니다.

H2 데이터베이스는 파일 기반으로 실행되며 경로는 `./data/liveclass`입니다.

- H2 Console: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:file:./data/liveclass;MODE=MySQL;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE`
- User Name: `sa`
- Password: 빈 값

# 4. 요구사항 해석 및 가정

- 인증/인가 시스템은 과제 범위 외로 확인하여, 요청 헤더 `X-Member-Id`를 현재 사용자 식별자로 사용했습니다.
- 강의 생성자는 `X-Member-Id`로 전달된 사용자가 됩니다.
- 회원 가입 API는 구현하지 않았습니다. 대신 애플리케이션 실행 시, 회원 데이터를 5명이 저장되도록하여 테스트 시 활용할 수 있도록 하였습니다.
- **정원이 차는 타이밍** : `CONFIRMED` 전환 시점을 기준으로 잡을 경우 정원이 이 때 차도록한다면, 마감임박이나 인기 강의의 경우 높아질 수 있는 동시성 상황에서 사용성에 대한 우려가 예상되었습니다.
  - 예를 들어 결제 화면까지 갔던 99명이 마지막에 **정원 마감**으로 거부된다면 **사용자는 서비스에 대해 큰 불편함을 느낄 것**이라고 생각되었습니다. 
  - 또한 결제 PG에 진입한 후 거절된다면 환불 처리도 필요할 수 있어, 결제 확정 처리 기능 하나가 너무 무거운 책임을 가지게된다고 느꼈고 신청완료 단계인 `PENDING` 상태에 정원을 점유하도록 상황을 가정했습니다.
- 유저는 강의 별로 수강신청을 한번씩만 가능한 것으로 요구사항을 이해했으며, 취소 후에는 재신청이 가능하다고 가정하였습니다. 

# 5. 설계 결정과 이유

## 5-1. 정원 카운트 데이터의 위치 결정

현재 신청 인원은 강의의 고정 속성이라기보다 수강 신청/취소에 따라 자주 바뀌는 파생 상태라고 판단했습니다. 따라서 `course.capacity`처럼 강의 자체의 정책 값은 `course` 테이블에 두고, 실시간으로 증감되는 `count`는 `course_enroll_count` 테이블로 분리했습니다.

### `강의 테이블에서 관리한다면`

만약 `course` 테이블에 **정원 카운트**를 함께 둔다면 수강 신청이 몰릴 때마다 해당 강의 행 전체가 갱신 대상이 됩니다. 이 경우 제목, 설명, 가격, 기간처럼 상대적으로 변경 빈도가 낮은 강의 메타데이터까지 정원 카운트 갱신과 같은 버전 경합 범위에 묶이게 됩니다. 마지막 자리 신청처럼 카운트만 경쟁적으로 바뀌는 상황에서도 강의 행의 `version`이 계속 증가하고, 강의 정보 수정 같은 다른 작업과 불필요하게 충돌할 수 있을 것으로 예상했습니다.

다만 강의의 `status`는 단순 메타데이터가 아니라 수강 신청 가능 여부를 결정하는 핵심 조건입니다. 예를 들어 수강 신청 트랜잭션이 `OPEN` 상태를 읽은 직후, 신청 완료 전에 크리에이터가 강의를 `CLOSED`로 변경하면 "신청 시작 시점에는 OPEN이었지만 커밋 시점에는 CLOSED"인 레이스가 발생할 수 있습니다. 따라서 모집 상태와 수강 신청 사이의 일관성까지 보장하려면 `course` 행도 동시성 제어 대상에 포함되어야 합니다.

### `별도 테이블로 분리한다면`

반대로 별도 테이블로 분리하면 **정원 수량 경합**의 대상은 `course_enroll_count` 한 행으로 좁힐 수 있습니다. 수강 신청은 `capacity`를 읽고 `count`를 증가시키며, 취소는 `count`를 감소시킵니다. 이 구조에서는 "정원을 예약한다"와 "예약한 정원을 반환한다"는 도메인 행위가 `CourseEnrollCount.tryReserve()`와 `release()`로 명확하게 드러납니다.

결론적으로 동시성 관심사를 두 축으로 나누어 볼 수 있습니다. 첫 번째는 마지막 자리 신청처럼 정원 수량을 둘러싼 경합이고, 이는 `course_enroll_count`의 낙관적 락으로 제어합니다. 두 번째는 `OPEN -> CLOSED` 상태 변경과 수강 신청의 경합이고, 이는 수강 신청 시 `Course`를 명시적 낙관적 락으로 조회하거나 버전 검증을 강제해 커밋 시점에 상태 변경 충돌을 감지하는 방식으로 제어할 수 있습니다. 충돌이 감지되면 신청 로직을 재시도하고, 재조회 시 이미 `CLOSED`라면 신청을 거부하는 흐름이 됩니다.

### `분리에서 따라오는 운영상 이점 — 충돌 관심사별 모니터링`

테이블·엔티티를 분리하면 낙관적 락 충돌이 발생했을 때 **어떤 종류의 경합이었는지를 구분해서 집계**할 수 있습니다. 기존처럼 `course` 한 테이블에 모든 상태가 들어가면, 재시도가 발생해도 그것이 "정원 수량 경합" 때문인지 "강의 상태 변경 경합" 때문인지 알기 어렵습니다.

분리된 구조에서는 재시도 시 발생한 `ObjectOptimisticLockingFailureException`의 엔티티명을 보고, `CourseEnrollCount` 충돌이면 **정원 수량 경합 카운터**에, `Course` 충돌이면 **강의 상태 경합 카운터**에 각각 누적할 수 있습니다(`EnrollmentRetryMetrics`). 이를 통해:

- **정원 수량 경합 횟수**가 임계치를 넘는 강의는 "마지막 자리 / 신청 burst가 몰리는 인기 강의"로 식별되며, 부분적으로 비관적 락이나 원자적 `UPDATE` 같은 다른 전략으로 전환하는 후속 운영 정책 분기에 활용할 수 있습니다. 평상시에는 낙관적 락의 성능 이점을 누리고, 고경합 상황에서만 일관성 우선 전략으로 전환하는 것이 가능해집니다.
- **강의 상태 경합 횟수**는 크리에이터의 강의 상태 전이가 수강 신청과 얼마나 자주 부딪히는지를 별개의 신호로 보여줍니다. 두 신호가 섞이지 않으므로 "이 강의는 인기 강의인가, 운영자의 상태 변경이 잦은 강의인가" 같은 진단이 가능해집니다.

<br>

## 5-2. 강의의 상태와 수강신청의 Race Condition
수강신청 시작 시점에 `OPEN`이었지만, 신청완료 시점에 `CLOSED`로 강의의 상태가 변경될 경우 마감된 강의에 수강신청이
완료될 수 있는 문제가 예상되었습니다. 따라서 크게 3가지 방안 고려 끝에 **명시적 낙관적 락 처리 방식**을 사용하기로 결정했습니다.

### `방안1. 수강신청 완료될 동안 해당 강의에 Row Lock`

수강신청 로직이 시작되고 강의가 OPEN임을 확인 후, 수강신청이 완료되기 전까지 해당 강의에 Row Lock을 거는 비관적 락을 거는 방식을 고려해보았습니다. 하지만 이렇게되면 일반 유저가 수강신청을 할 때마다 해당 강의 데이터에 Lock이 걸려 크리에이터가 강의 정보를 수정하는데 지연이 발생할 수 있었고, 이는 크리에이터 사용자의 입장에서 서비스를 이용하는데 충분히 불편을 초래할 수 있을 것이라는 생각이 들었습니다.

### `방안2. 수강신청 데이터 Insert 시점에 Open 체크`

Course의 상태가 Open임을 체크하여 삽입하는 커스텀 Insert 쿼리를 작성하는 방법입니다. 수강신청 데이터 저장완료 시점에 Insert가 Open임을 원자적으로 보장가능하며, Course에 Lock을 거는 시간이 방안1에 비해 줄어든다는 장점이 있습니다.

하지만 엔티티로 관리되지않아 영속성 컨텍스트를 생략하고 DB에 저장하기 때문에 아래와 같은 문제들이 존재합니다. 때문에 이를 우회하여 JDBC 기술을 통해 Insert문에 대한 결과값을 반환받는 방식도 고려해보았습니다.

- QueryDsl의 삽입쿼리가 반환 데이터가 아닌 결과영향 행수를 반환하는 문제
- Auditing이 동작하지 않는 문제
- Version이 초기화되지 않는 문제

### `방안3. Course 데이터 Version을 체크하여 명시적 낙관적 락 적용`

```java
entityManager.lock(course, LockModeType.OPTIMISTIC);
```

수강신청 시, 낙관적 락을 명시적으로 사용해서 `Course`의 버전체크를 강제하는 방법입니다. 위 두 비관적 Lock 방식에 비해 Lock을 잡지않아 성능을 확보할 수 있는 이점이 있습니다. 수강신청 도중에, 강의의 상태를 `CLOSED`로 바뀌는 빈도가 적으므로 이 방식이 가장 합리적이라고 판단하여 사용하기로 결정했습니다.

<br>

## 5-3. 수강신청·대기등록 데이터의 중복 저장 방지

기존에는 수강신청 로직에 대상 강의에 동일한 유저가 신청했던 데이터 중, `취소 상태가 아닌 것이 있는지 체크`하는 로직이 존재했습니다.
만약 서로 다른 두 트랜잭션이 해당 로직을 `동시에 통과`할 경우, `수강신청 데이터가 중복되어 생성될 수 있는 문제`가 예상되었습니다.

또한 대기열 등록과 수강신청 중 하나만 허용가능하게끔 하려면 애플리케이션 로직에서 검증한다해도 결국 DB 삽입 시점에 Race Condition이 발생할 수 있었습니다. 이에 따라 결국 `DB 레벨에서 원자적인 처리가 필요`하다고 생각되어 `두 등록 로직이 함께 바라보는 별도 테이블을 관리`해야겠다고 판단하여 아래와 같이 처리하기로 결정했습니다.


- 별도 `course_reservation` 테이블을 두어, 한 사용자가 한 강의에 대해 **수강신청 또는 대기열 중 하나만** 등록이 보장될 수 있도록 하였습니다.
  - `course_reservation` 테이블은 `(course_id, member_id)` UNIQUE 제약을 가집니다.
  - 수강신청·대기등록 시 동일 트랜잭션 안에서 `course_reservation`을 삽입하고, 취소 시 제거합니다.
  - 동시 등록은 이 테이블의 DB UNIQUE 제약으로 원자적으로 방어가 가능해집니다.
- enrollment·waitlist **양쪽 모두**에 대한 중복(예: 대기 중인데 수강신청, enrollment 중인데 대기등록)도 DB 레벨에서 차단됩니다.
- 취소 시 reservation 데이터를 제거하므로 재신청·재대기가 가능합니다.

<br>

## 5-4. 도메인 규칙의 위치

상태 전이, 소유자 검증, 취소 가능 기간 검증 등 핵심 규칙은 가능한 도메인 객체에 배치했습니다. 서비스는 트랜잭션 경계, 리포지토리 조회, 권한/흐름 조합을 담당하도록 분리했습니다.

<br>

## 5-5. 대기열 기능의 설계

대기열 기능 구현 시 거쳤던 주요 고민과정을 아래와 같이 정리해보았습니다.

### 5-5-1. 수강신청의 WAITING 상태 VS 별도 테이블로 관리

- 특성의 상이함
    - Enrollment의 주요 관심사 : 한 수강생의 신청 상태 관리
        - 결제 상태
        - 취소 가능 여부
        - 수강 상태
    - 대기열 데이터의 주요 관심사 : 정원 초과 상황에서의 순번 관리
        - 순번
        - 승급 (빈 자리 채우기)
        - 재정렬
- 순번, 만료기한 등 대기열 관리만을 위한 데이터 필요/추가의 가능성

위 이유를 근거로 대기열을 별도 테이블로 관리하기로 결정했습니다.

<br>

### 5-5-2. 수강신청 정원초과 시, 자동 대기열 등록 VS 별도 API로 분리

사용자 입장에서 예상과 달리 정원이 가득찼을 때, 대기열에 등록할지 의사를 확인하는 등 그 상황을 명확히 인지할 수 있도록 하는 것이 UX 측면에서 더 좋을 것이라고 생각되어 별도  API로 분리하기로 결정했습니다.

<br>

### 5-5-3. `승격처리: 비동기 VS 동기`

아래와 같은 근거로 비동기 구조를 적용하기로 했습니다.

`동기처리`

수강 취소 시점에 대기열에 있던 유저 한명을 신청자로 승격하는 동기적인 구조를 고려해볼 수 있을 것입니다. 하지만 다음과 같은 문제가 예상되었습니다.

- 환불 등이 포함되면 수강취소는 본래 무겁다는 특성
- 승격 처리도 좌석 확인, 다음 대기자 조회, 대기열 재정렬 등이 포함되므로 가벼운 기능이라고 볼 수 없음
- 수강취소를 요청한 유저와 대기상태인 유저가 서로 다른데도 불구하고 영향을 주어 응답시간이 길어질 수 있음
- 승격 처리가 실패해도 수강 취소를 요청했던 유저는 다른 유저이므로 성공해야하지만 장애가 전파되어 함께 실패하게됨
- 대기자의 승격이 수강취소가되자마자 즉시 처리 되어야하는 것은 아니라고 판단됨

`비동기 구조`

- 취소요청은 승격처리와 상관없이 완료되면 바로 응답하여 응답시간이 짧아질 수 있음
- 장애 전파 문제가 해결되어 실패의 격리가 가능해짐
- 재처리 등의 설계를 독립적으로 사용 가능해짐

<br>

### 5-5-4. `비동기 처리 방식`

비동기 처리 방식은 크게 3가지를 고려했습니다.

#### `방식1. Spring Event`

`@Async` 를 활용하면 비동기 구조를 빠르게 적용할 수 있을 것입니다. 그러나 메모리 기반으로 동작되기 때문에 이벤트 처리 도중 서버가 내려가거나 장애가 발생할 경우 이벤트가 유실될 수 있다는 문제가 예상되었습니다. 대기열 상태인 유저의 경우 계속 기다리고 있으므로 이는 서비스 사용성에도 악영향을 줄 것으로 생각되었습니다.

<br>

#### `방식2. 이벤트 브로커 도입`

인프라가 추가되므로 본 과제의 범위를 벗어난다고 생각되어 배제하기로 하였습니다.

<br>

#### `방식3. DB에 Event를 저장해두고 Polling`

수강취소 시점에 트랜잭션을 이용하여 취소 이벤트를 원자적으로 발행하는 방식입니다. 브로커 도입은 제약 상 어려우므로 스케줄러를 활용해 대기열 데이터를 Polling하여 1명을 승격처리합니다. **DB에서 이벤트를 영속적으로 관리하므로 서버 장애에 대응**할 수 있고, 스케줄러에 할당 스레드를 늘려 처리량도 늘릴 수 있어 **확장성도 꽤 확보**할 수 있습니다.

주기적인 Polling으로 인해 불필요한 DB 조회가 발생할 수 있지만 현 제약 상황에서 나름 합리적인 방안이라고 판단하여 해당 방식을 사용하기로 결정했습니다. 이벤트는 추후 다른 이벤트들도 추가될 수 있으므로 이벤트 종류를 `type` 컬럼을 두어 관리하도록 하였습니다.

<br>

# 미구현 / 제약사항

- 과제 실행 편의를 위해 H2와 `ddl-auto: update`를 사용했습니다.
- 이벤트의 멱등처리


# AI 활용 범위

- 과제 요구사항을 기능 단위로 분해하고 도메인 모델 후보를 정리하는 데 AI를 활용했습니다.
- 동시성 제어 방식, 상태 전이 규칙, 테스트 케이스 목록을 검토하는 데 AI를 활용했습니다.
- 반복적인 테스트 시나리오와 README 초안 작성에 AI를 활용했습니다.
- 최종 구현 판단, 코드 반영, 테스트 실행 및 요구사항 충족 여부 검토는 직접 수행했습니다.

# API 목록 및 예시 (API 명세서 추가 필요)

모든 사용자 식별이 필요한 API는 `X-Member-Id` 헤더를 사용합니다.

## 강의 등록

`POST /api/courses`

```bash
curl -X POST http://localhost:8080/api/courses \
  -H "Content-Type: application/json" \
  -H "X-Member-Id: 1" \
  -d '{
    "title": "Spring Boot 실전반",
    "description": "수강 신청 시스템을 구현하며 Spring Boot를 학습합니다.",
    "price": 50000,
    "capacity": 30,
    "startDate": "2026-06-01",
    "endDate": "2026-07-31"
  }'
```

응답 예시:

```json
{
  "id": 1,
  "creatorId": 1,
  "title": "Spring Boot 실전반",
  "description": "수강 신청 시스템을 구현하며 Spring Boot를 학습합니다.",
  "price": 50000,
  "capacity": 30,
  "enrollCount": 0,
  "startDate": "2026-06-01",
  "endDate": "2026-07-31",
  "status": "DRAFT"
}
```

## 강의 상태 변경

`PATCH /api/courses/{courseId}/status`

```bash
curl -X PATCH http://localhost:8080/api/courses/1/status \
  -H "Content-Type: application/json" \
  -H "X-Member-Id: 1" \
  -d '{ "status": "OPEN" }'
```

성공 시 `204 No Content`를 반환합니다.

## 강의 목록 조회

`GET /api/courses`

```bash
curl "http://localhost:8080/api/courses?status=OPEN"
```

상태 필터는 선택 값이며 `DRAFT`, `OPEN`, `CLOSED`를 사용할 수 있습니다.

## 강의 상세 조회

`GET /api/courses/{courseId}`

```bash
curl http://localhost:8080/api/courses/1
```

상세 응답에는 현재 신청 인원 `enrollCount`가 포함됩니다.

## 수강 신청

`POST /api/enrollments`

```bash
curl -X POST http://localhost:8080/api/enrollments \
  -H "Content-Type: application/json" \
  -H "X-Member-Id: 2" \
  -d '{ "courseId": 1 }'
```

응답 예시:

```json
{
  "id": 1,
  "courseId": 1,
  "memberId": 2,
  "status": "PENDING",
  "confirmedAt": null,
  "cancelledAt": null
}
```

## 결제 확정

`POST /api/enrollments/{enrollmentId}/confirmation`

```bash
curl -X POST http://localhost:8080/api/enrollments/1/confirmation \
  -H "X-Member-Id: 2"
```

## 수강 취소

`POST /api/enrollments/{enrollmentId}/cancellation`

```bash
curl -X POST http://localhost:8080/api/enrollments/1/cancellation \
  -H "X-Member-Id: 2"
```

`CONFIRMED` 상태는 결제 확정 후 7일 이내에만 취소할 수 있습니다.

## 내 수강 신청 목록 조회

`GET /api/enrollments/me`

```bash
curl "http://localhost:8080/api/enrollments/me?page=0&size=20" \
  -H "X-Member-Id: 2"
```

응답은 다음 페이지 포맷을 사용합니다.

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

## 강의별 수강생 목록 조회

`GET /api/courses/{courseId}/students`

```bash
curl http://localhost:8080/api/courses/1/students \
  -H "X-Member-Id: 1"
```

강의 생성자만 조회할 수 있으며, `PENDING`, `CONFIRMED` 상태의 수강 신청만 반환합니다.

## 대기열 등록

`POST /api/waitlists`

```bash
curl -X POST http://localhost:8080/api/waitlists \
  -H "Content-Type: application/json" \
  -H "X-Member-Id: 3" \
  -d '{ "courseId": 1 }'
```

응답 예시:

```json
{
  "id": 1,
  "courseId": 1,
  "memberId": 3,
  "orderNum": 1
}
```

## 대기열 취소

`DELETE /api/waitlists/{waitlistId}`

```bash
curl -X DELETE http://localhost:8080/api/waitlists/1 \
  -H "X-Member-Id: 3"
```

성공 시 `204 No Content`를 반환합니다.

## 에러 응답 형식

```json
{
  "timestamp": "2026-05-24T12:00:00",
  "code": "ENROLLMENT_008",
  "message": "정원이 가득 찼습니다.",
  "path": "/api/enrollments",
  "details": {}
}
```

# 데이터 모델 설명

## Course

강의의 기본 정보를 저장합니다.

- `id`: 강의 ID
- `creatorId`: 강의 생성자 ID
- `title`, `description`: 강의 제목과 설명
- `price`: 수강 가격
- `capacity`: 최대 수강 인원
- `startDate`, `endDate`: 수강 기간
- `status`: `DRAFT`, `OPEN`, `CLOSED`
- `version`: 낙관적 락 버전

## CourseEnrollCount

강의별 활성 신청 수를 저장합니다.

- `courseId`: 강의 ID
- `count`: 현재 활성 신청 수
- `version`: 동시 신청 충돌 감지를 위한 낙관적 락 버전

## Enrollment

수강 신청 정보를 저장합니다.

- `id`: 수강 신청 ID
- `courseId`: 강의 ID
- `memberId`: 신청자 ID
- `status`: `PENDING`, `CONFIRMED`, `CANCELLED`
- `confirmedAt`: 결제 확정 시각
- `cancelledAt`: 취소 시각
- `activeMemberId`: 활성 신청 중복 방지를 위한 생성 컬럼
- `version`: 낙관적 락 버전

## Waitlist

정원 초과 시 대기 신청 정보를 저장합니다.

- `id`: 대기열 ID
- `courseId`: 강의 ID
- `memberId`: 대기 신청자 ID
- `orderNum`: 강의별 대기 순번

## Member

수강생 목록 조회 시 이름 표시를 위해 사용하는 회원 정보입니다.

- `id`: 회원 ID
- `name`: 회원 이름

## OutboxEvent

수강 취소와 같은 도메인 이벤트를 저장하기 위한 아웃박스 테이블입니다.

- `id`: 이벤트 ID
- `type`: 이벤트 타입
- `aggregateId`: 이벤트 대상 ID
- `status`: 이벤트 처리 상태

# 테스트 실행 방법

전체 테스트는 다음 명령으로 실행합니다.

```bash
./gradlew test
```

Windows PowerShell에서는 다음 명령을 사용할 수 있습니다.

```powershell
.\gradlew.bat test
```

테스트는 다음 범위를 포함합니다.

- 강의 생성, 상태 변경, 목록/상세 조회
- 수강 신청, 결제 확정, 취소, 내 신청 목록 조회
- 마지막 자리 동시 신청 시나리오
- 강의별 수강생 목록 조회
- 대기열 등록/취소
- 리포지토리와 도메인 규칙 단위 테스트
