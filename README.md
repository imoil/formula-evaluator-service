# 🚀 대규모 IoT 시계열 데이터 평가 시스템 (Formula Evaluator Service)

이 프로젝트는 1초 이하의 간격으로 밀려드는 수십만 개의 IoT 센서 데이터를 지연 없이 거둬들이고, 텍스트 형태의 수리/로직(동적 룰, Dynamic Rule)을 즉석에서 컴파일하여 파생 데이터를 계산하는 고성능 **듀얼-패스(Dual-Path) 분산 처리 시스템**입니다.

## 🏗 핵심 아키텍처: 듀얼 패스 (Dual-Path Execution)

1. **실시간 스트리밍 경로 (Streaming Path - Apache Flink)**
   - 센서 데이터 스트림과 수식(Rule) 스트림을 Kafka에서 수신하여 Flink의 **Broadcast State Pattern**으로 워커 노드 간 초고속 분배합니다.
   - 윈도우 평균(`window_avg`), 최댓값(`window_max`) 등을 다루는 `ListState` 기반 TTL 메모리가 OOM(OutOfMemory) 개입을 방지하며 연속된 결괏값을 도출합니다.
   
2. **REST 기반 온디맨드 경로 (On-Demand Path - Spring Boot & QuestDB)**
   - 대시보드나 클라이언트 API 요청 시, QuestDB의 고유 기능인 **시간 축 다운샘플링 (`SAMPLE BY`)** 엔진으로 백엔드로 가져올 데이터 페이로드를 압축합니다.
   - Spring Boot 서버 로컬 메모리에 탑재된 슬라이딩 윈도우 객체와 AviatorScript 혼합 엔진이 결과를 최종 계산합니다.

## 🛠 기술 스택 (Tech Stack)

* **언어:** Java 17
* **프레임워크:** Spring Boot 3.4.x
* **스트리밍 분산 환경:** Apache Flink 1.19.0 (RocksDB State Backend 명시적 탑재)
* **시계열 데이터베이스:** QuestDB 9.x (ILP TCP/HTTP 공식 클라이언트 사용)
* **메시지 브로커:** Apache Kafka 7.5.3 커넥터 환경
* **규칙 연산 엔진:** AviatorScript 5.4.1 (Java 런타임 Bytecode 즉시 변환 캐싱)

## 🗂 주요 기능 명세

1. **Rule 엔진 최적화 캐싱**: 수식이 들어올 때마다 파싱하는 느린 리플렉션을 배제하고 바이트코드로 변환된 수식을 LRU 방식으로 메모리에 캐싱.
2. **비동기 적재 버퍼 방식**: 개별 `insert` 대신 큐에 쌓아 주기적으로 플러시(Flush)하여 네트워크 I/O 병목 방지. 멀티 스레드 진입 시 단일 연결 파이프의 `Thread-Safe(synchronized)` 격리 완비.
3. **오래된 시계열 데이터 자동 삭제 (`DROP PARTITION`)**: Spring 캘린더 작업(`@Scheduled`)이 만료일(30일)이 지난 QuestDB 디스크 파티션을 락(Lock) 없이 0초대에 포맷시켜 스토리지 운영 부담 해소.

## 🚀 빠른 시작 가이드 (Getting Started)

### 1. 테스트 인프라 컨테이너 실행
프로젝트 루트 디렉토리에서 podman-compose 환경을 통해 Kafka 및 QuestDB를 데몬(Daemon)으로 구동합니다.
```bash
podman-compose up -d
```

### 2. Maven 빌드
```bash
mvn clean compile package
```

### 3. Flink Job E2E Local Test
본 프로젝트 `src/test/java/`의 `StreamingEvaluatorE2ETest`를 구동할 시 자체적으로 미니 Flink 클러스터를 띄우고 카프카로 룰과 센서 값을 전송해 End-To-End가 정상 관통하는지 체크합니다.

### 4. Flink Job 외부 설정 파라미터로 실행하기
Flink Job 실행 시 다양한 환경(Kafka 연결 정보, 보존 시간, QuestDB URL 등)에 대응하기 위해 `ParameterTool`을 활용한 외부 설정 인자 주입을 지원합니다. 다음과 같은 방법으로 커스텀 설정을 주입하여 Flink 클러스터에 제출할 수 있습니다.

```bash
flink run -c com.imoil.formula.flink.StreamingEvaluatorJob target/formula-evaluator-service-0.0.1-SNAPSHOT.jar \
  --kafka-bootstrap-servers "prod-kafka:9092" \
  --kafka-topic-sensor "prod-sensor-data" \
  --kafka-topic-rule "prod-rule-data" \
  --retention-time-minutes 60 \
  --questdb-url "http::addr=prod-questdb:9000;auto_flush_interval=1000;auto_flush_rows=200000;"
```

**지원하는 파라미터 목록:**
* `--kafka-bootstrap-servers`: Kafka 서버 주소 (기본값: `localhost:9092`)
* `--kafka-topic-sensor`: 센서 데이터 토픽 (기본값: `sensor-data`)
* `--kafka-group-sensor`: 센서 데이터 컨슈머 그룹 (기본값: `flink-sensor-group`)
* `--kafka-topic-rule`: 동적 룰 데이터 토픽 (기본값: `rule-data`)
* `--kafka-group-rule`: 동적 룰 컨슈머 그룹 (기본값: `flink-rule-group`)
* `--retention-time-minutes`: 윈도우 함수 계산을 위한 히스토리 상태 보존 시간 (분) (기본값: `30`)
* `--questdb-url`: QuestDB ILP 연결 URL 및 설정 (기본값: `http::addr=localhost:9000;auto_flush_interval=1000;auto_flush_rows=100000;`)

---
**Note:** 이 시스템의 Flink Job 클래스는 로컬 의존성에 종속되지 않고 외부 설정 파라미터를 읽어 프로덕션 레포지토리에 클라우드-네이티브로 즉시 띄울 수 있도록 구성되어 있습니다.
