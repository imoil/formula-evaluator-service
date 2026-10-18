# 📡 IoT 센서 시뮬레이션 및 엔드투엔드 시계열 파이프라인 가이드

본 문서는 **Formula Evaluator Service**의 IoT 센서 에뮬레이션 툴(`formula-emulator`)의 기술적인 원리와 실행 방법, 그리고 **센서 변동 발생부터 카프카 전송, 단일 일련번호 매핑, Flink 실시간 수식 평가, QuestDB 최종 적재까지의 엔드투엔드 워크플로우**를 다이어그램과 함께 상세히 정리한 엔지니어링 기술 가이드입니다.

---

## 1. 🔄 엔드투엔드 워크플로우 다이어그램 (Workflow Diagram)

센서에서 측정값 변동이 일어나는 순간부터 Flink 분산 룰 엔진에서 계산되어 TSDB(QuestDB)에 도달하기까지의 전체 처리 흐름입니다.

```mermaid
flowchart TD
    %% 1단계: 에뮬레이터 센서 변동
    subgraph Step1["1단계: IoT 센서 에뮬레이션 (formula-emulator)"]
        direction TB
        Sensors["1,000개 EmulatedSensor 풀<br/>(독립 타이머: 1s ~ 10s 주기)"]
        StateCheck{"상태 전이 판정<br/>(99% normal, 1% down)"}
        ValueDrift["정규분포 기반 랜덤 워크 측정값 갱신<br/>(소수점 3자리 반올림)"]
        TickScheduler["1초 주기 무지연 스케줄러<br/>(scheduleAtFixedRate & Jitter 측정)"]
        BatchCollector["1초 동안 변동된 센서 목록 취합<br/>(List&lt;SensorItem&gt; 집계)"]

        Sensors --> StateCheck
        StateCheck --> ValueDrift
        ValueDrift --> BatchCollector
        TickScheduler --> BatchCollector
    end

    %% 2단계: 카프카 전송
    subgraph Step2["2단계: 카프카 무지연 비동기 전송"]
        direction TB
        KafkaHeaders["Kafka Record Headers<br/>- deviceId: 'DEVICE-001'<br/>- timestamp: 1729152000000"]
        KafkaBody["Kafka Record Value (Body)<br/>JSON Array: [ {sensorIndex, value, status}, ... ]"]
        AsyncSend["KafkaProducer.send() (Non-blocking Async)<br/>- linger.ms = 0 (버퍼 대기 제거)<br/>- acks = 1 (초고속 승인)"]

        BatchCollector --> KafkaHeaders & KafkaBody
        KafkaHeaders & KafkaBody --> AsyncSend
    end

    %% 3단계: 메시지 브로커
    subgraph Step3["3단계: Apache Kafka 3.9"]
        SensorTopic[("Topic: sensor-data<br/>(Partitioned Stream)")]
        AsyncSend -->|즉각 스트리밍 전송| SensorTopic
    end

    %% 4단계: Flink 역직렬화 & 단일 일련번호 매핑
    subgraph Step4["4단계: Flink 역직렬화 및 단일 일련번호 매핑 (formula-flink)"]
        direction TB
        Deserializer["DeviceSensorRecordDeserializer<br/>(KafkaRecordDeserializationSchema)"]
        ExtractHeader["헤더 추출: deviceId, 전송 timestamp"]
        SerialResolver["SensorSerialResolver (전역 리졸버)<br/>(deviceId, sensorIndex) ➔ serialNumber (10001+)"]
        FlatMapArray["바디 JSON 배열 FlatMap<br/>(Collector.collect() N회 방출)"]
        EmitSensorData["DataStream&lt;SensorData&gt; 생성<br/>- sensorId: '10001' (단일 일련번호)<br/>- value, timestamp, state"]

        SensorTopic --> Deserializer
        Deserializer --> ExtractHeader & FlatMapArray
        ExtractHeader & FlatMapArray --> SerialResolver --> EmitSensorData
    end

    %% 5단계: 분산 스트림 처리 & 수식 평가
    subgraph Step5["5단계: 동적 룰 평가 및 상태 계산"]
        direction TB
        KeyByStream["keyBy(SensorData::getSensorId)<br/>(단일 일련번호 해시 분산, 장치 간 충돌 원천 차단)"]
        BroadcastRules["Rule Broadcast Stream<br/>(targetSensorId: '10001' or 'P_10001', derivedSensorId: '10201')"]
        ProcessFunc["RuleBroadcastProcessFunction<br/>(KeyedBroadcastProcessFunction)"]
        RocksDBState["RocksDB State Backend<br/>(30분 슬라이딩 윈도우 + TTL 관리)"]
        AviatorJIT["AviatorScript 5.9.0 JIT 컴파일러<br/>(window_avg, window_max 연산)"]
        OutCollect["원본 SensorData(10001) + 파생 수식 SensorData(10201) 방출"]

        EmitSensorData --> KeyByStream
        KeyByStream & BroadcastRules --> ProcessFunc
        ProcessFunc <--> RocksDBState
        ProcessFunc --> AviatorJIT
        AviatorJIT --> OutCollect
    end

    %% 6단계: QuestDB 저장
    subgraph Step6["6단계: 시계열 데이터 저장소 적재"]
        direction TB
        QuestSink["QuestDbIlpSink<br/>(Influx Line Protocol 클라이언트)"]
        ILPBuffer["Zero-GC TCP 소켓 버퍼링<br/>(auto_flush_rows=100,000)"]
        QuestDB[("QuestDB 10.0.1<br/>sensor_data 테이블<br/>(일별 파티션, Columnar 스토리지)")]

        OutCollect --> QuestSink
        QuestSink --> ILPBuffer
        ILPBuffer -->|ILP TCP 통신| QuestDB
    end

    %% 연결 라인 스타일
    classDef stepClass fill:#f8f9fa,stroke:#343a40,stroke-width:2px;
    class Step1,Step2,Step3,Step4,Step5,Step6 stepClass;
```

---

## 2. 🧩 단계별 기술 메커니즘 및 상세 분석

### 단계 1: IoT 센서 에뮬레이터 (`formula-emulator`)
* **센서 풀(Sensor Pool) 관리**:
  - 기본 1,000개의 [`EmulatedSensor`](file:///C:/Users/imoil/repo/formula-evaluator-service/formula-emulator/src/main/java/com/imoil/formula/emulator/model/EmulatedSensor.java) 인스턴스를 메모리에 유지합니다.
  - 각 센서는 인덱스(0 ~ 999)에 따라 고유한 기준값(`baseMean`, 20.0 ~ 170.0)과 표준편차(`stdDev`)를 가지며, 온도·압력·전압 등 다양한 물리량 특성을 모의합니다.
* **1초 ~ 10초 무작위 변동 주기**:
  - 센서마다 다음 변동 시점(`nextChangeTimeMs`)을 `현재시각 + Random(1~10초)`로 개별 스케줄링합니다.
  - 변동 시점이 도래하면 정규분포 랜덤 워크를 통해 값을 갱신하고, 설정된 확률(기본 1%)에 따라 `normal` $\leftrightarrow$ `down` 상태를 전이합니다.
* **1초 무지연 스케줄러 (`ScheduledExecutorService`)**:
  - `scheduleAtFixedRate`를 채택하여 앞선 작업의 소요 시간과 무관하게 정확히 1,000ms 간격으로 고정 실행됩니다.
  - 이전 틱 대비 지연 시간(Jitter)을 마이크로초 단위로 측정하여 100ms 초과 시 경고를 발생시킵니다.

---

### 단계 2: 카프카 무지연 비동기 전송 (`SensorKafkaProducer`)
* **Kafka Record 구조 규격**:
  - **Record Headers**:
    - `deviceId`: 장치 고유명 (예: `DEVICE-001`, UTF-8 byte)
    - `timestamp`: 전송 기준 에포크 밀리초 (예: `1729152000000`, UTF-8 byte)
  - **Record Value (Body)**:
    - 1초 동안 변동이 일어난 센서들만 선별된 JSON 배열:
      ```json
      [
        {
          "sensorIndex": 3,
          "value": 45.215,
          "status": "normal"
        },
        {
          "sensorIndex": 42,
          "value": 0.0,
          "status": "down"
        }
      ]
      ```
* **논블로킹 비동기 전송 (Non-blocking Async Send)**:
  - Kafka 전송 시 `.send(record, callback)`으로 호출하여 네트워크 왕복 대기(RTT) 없이 즉시 다음 루프로 복귀합니다.
  - 프로듀서 옵션: `linger.ms = 0` (대기 버퍼링 없이 즉시 패킷 송출), `acks = 1`, `max.block.ms = 1000`.

---

### 단계 3 & 4: Flink 레코드 역직렬화 및 단일 일련번호 매핑 (`formula-flink`)
* **[`DeviceSensorRecordDeserializer`](file:///C:/Users/imoil/repo/formula-evaluator-service/formula-flink/src/main/java/com/imoil/formula/flink/DeviceSensorRecordDeserializer.java)**:
  - Flink 1.19 표준 `KafkaRecordDeserializationSchema<SensorData>`를 구현합니다.
  - `ConsumerRecord`로부터 Kafka Headers의 `deviceId`와 `timestamp`를 파싱합니다.
  - Value 바디의 JSON 배열을 순회하며 [`SensorSerialResolver`](file:///C:/Users/imoil/repo/formula-evaluator-service/formula-core/src/main/java/com/imoil/formula/domain/SensorSerialResolver.java)를 호출합니다:
    ```java
    long serialNumber = resolver.resolveSerialNumber(deviceId, item.getSensorIndex());
    String sensorId = String.valueOf(serialNumber); // 예: "10001", "10101"
    ```
  - **충돌 방지 원리**: `DEVICE-001`의 0번 센서(`10001`)와 `DEVICE-002`의 0번 센서(`10101`)가 서로 다른 고유 일련번호로 매핑되므로, 다운스트림 `keyBy` 파티셔닝에서 상호 간섭이 발생하지 않습니다.
  - 변동된 센서 수($N$개)만큼 `Collector.collect(SensorData)`를 반복 호출(FlatMap)합니다.

---

### 단계 5: 동적 룰 평가 및 슬라이딩 윈도우 계산
* **KeyBy 수평 분산**: `keyBy(SensorData::getSensorId)`로 센서별 이벤트를 Flink 워커에 해시 분산합니다.
* **Broadcast State 동적 룰 전파 (Canonical Serial ID 단일 키)**:
  - `rule-data` 토픽을 통해 발행된 관리자 수식을 모든 Flink 작업 노드로 복제(Broadcast)합니다.
  - `RuleBroadcastProcessFunction`은 `DynamicRule.canonicalTargetSerialId()`를 통해 정규화된 단일 일련번호 키(`"10001"`) 하나로 `BroadcastState`를 관리하고, 유입 센서 ID도 `SensorSerialResolver.toCanonicalSerialId(value.getSensorId())`로 정규화하여 `"10001"`과 `"P_10001"`(하위 호환 `"SN_10001"`) 입력을 모두 $O(1)$로 매칭합니다.
  - 평가 시 `SensorSerialResolver.bindSerialIdentifiers(env, sensorId, value)`를 통해 `"10001"`과 `"P_10001"`(하위 호환 `"SN_10001"`)을 양방향 바인딩하고, `DynamicRule.resolveOutputSensorId(value.getSensorId())`를 통해 `derivedSensorId`(예: `"10201"`)가 부여된 파생 `SensorData`를 방출합니다.
* **보안 샌드박스 및 `RuleEngineService.evaluatePoint` 평가**:
  - 브로드캐스트 룰 수신 시점에 `RuleEngineService.validateOnce`(`ExpressionValidator`)가 시스템 명령어, 리플렉션, 무한 루프 시도를 원천 차단하며, `RuleEngineConfig.createDefaultEvaluator()`로 구성된 AviatorScript 샌드박스 인스턴스를 공유합니다.
  - `RuleEngineService.evaluatePoint(expression, env, windowHistory)`가 `AbstractWindowFunction.bindWindowHistory`를 내부 캡슐화하여 `window_avg(10)` 또는 `value > 50`과 같은 수식을 나노초 단위로 평가합니다.
* **RocksDB StateBackend & TTL**:
  - 30분 슬라이딩 윈도우 데이터를 RocksDB에 저장하며, 만료된 상태 데이터는 RocksDB TTL에 의해 자동 청소되어 OOM(Out of Memory)을 방지합니다.

---

### 단계 6: QuestDB ILP 고속 적재 (`QuestDbIlpSink` & `SensorDataWriter`)
* **도메인 포트(`SensorDataWriter`) 기반 Influx Line Protocol(ILP) 적재**:
  - `formula-core`의 `SensorDataWriter.forQuestDbSender(Sender)` 어댑터(`QuestDbIlpSensorDataWriter`)와 QuestDB 전용 초경량 Zero-GC 클라이언트(`questdb-client:1.3.9`)를 사용하여 `formula-api`와 `formula-flink` 간 ILP 컬럼/태그/나노초 타임스탬프 바인딩 규격을 단일화합니다.
  - 원본 센서 데이터(`10001`)와 Flink 파생 데이터(`10201`)를 일괄 전송합니다.
  - `auto_flush_rows = 100000`, `auto_flush_interval = 1000` 설정을 통해 초당 수십만 건의 데이터를 디스크 IO 오버헤드 없이 적재합니다.

---

## 3. ⚙️ 에뮬레이터 CLI 파라미터 레퍼런스

에뮬레이터는 다양한 테스트 시나리오를 지원하기 위해 다음과 같은 CLI 인자를 제공합니다.

| CLI 파라미터 | 기본값 | 설명 |
| :--- | :---: | :--- |
| `--device-id` | `DEVICE-001` | 카프카 헤더에 포함될 디바이스 고유명 |
| `--sensor-count` | `1000` | 에뮬레이션할 전체 센서 개수 (예: 500, 1000, 5000) |
| `--min-interval` | `1` | 센서별 최소 변동 주기 (초) |
| `--max-interval` | `10` | 센서별 최대 변동 주기 (초) |
| `--emit-interval-ms` | `1000` | 카프카 배치 전송 주기 (밀리초, 기본 1초) |
| `--kafka-servers` | `localhost:9092` | Kafka 브로커 주소 |
| `--kafka-topic` | `sensor-data` | 전송할 카프카 센서 토픽명 |
| `--down-probability` | `0.01` | 센서 틱당 'down' 장애 상태 전이 확률 (0.01 = 1%) |

---

## 4. 🛠️ 로컬 핸즈온(Hands-on) 실습 가이드

본 실습 가이드는 로컬 환경에서 **인프라 기동 → 백엔드 및 Flink 스트리밍 엔진 기동 → 프론트엔드 UI 연동 → 대용량 센서 에뮬레이션 및 구간별 중간 처리 검증 → 동적 룰 핫 업데이트 → 안전한 프로세스 종료**까지의 전체 파이프라인 수명주기를 단계별로 직접 검증할 수 있도록 구성되었습니다.

---

### 4.0 🗺️ 서비스 구성 및 포트 맵 (Port & Service Map)

실습 시작 전 각 프로세스가 점유하는 포트와 담당 역할을 확인합니다:

| 계층 | 프로세스 / 서비스 | 포트 | 프로토콜 / 접속 엔드포인트 | 역할 |
| :--- | :--- | :--- | :--- | :--- |
| **인프라** | **Kafka Broker** | `9092` | PLAINTEXT | 센서 패킷(`sensor-data`) 및 동적 룰(`rule-data`) 메시징 |
| **인프라** | **Zookeeper** | `2181` | TCP | Kafka 클러스터 메타데이터 관리 |
| **인프라** | **QuestDB** | `9000` (Web UI), `9009` (ILP TCP), `8812` (PGWire) | 시계열 데이터 저장 및 실시간 SQL 조회 (Zero-GC 초고속 적재) |
| **인프라** | **Oracle 19c** | `1521` | JDBC (`ORCLPDB1`) | 센서 메타데이터, IoT 장치 레지스트리 및 마스터 수식 영구 보관 |
| **백엔드** | **formula-api** | `8080` | HTTP REST (`http://localhost:8080`) | 센서/룰 레지스트리 REST API 및 Kafka 룰 동기화 서비스 |
| **엔진** | **formula-flink** | `8081` (Flink UI) | JVM Streaming Engine | 스트리밍 수식 평가, RocksDB 윈도우 집계, Broadcast State |
| **프론트엔드**| **formula-ui** | `3000` | HTTP Web (`http://localhost:3000`) | 센서 관리, 룰 일괄 동기화(Sync to Flink), 실시간 스트리밍 대시보드 |
| **생성기** | **formula-emulator**| CLI Daemon | Kafka Client | 대규모(1,000~5,000개) 가상 센서 데이터 고속 시뮬레이터 |

---

### 4.1 [1단계] 인프라 컨테이너 기동 및 헬스체크

파이프라인 실행에 필요한 백본 인프라(Zookeeper, Kafka, QuestDB, Oracle 19c)를 기동합니다.

#### 1) 컨테이너 기동
프로젝트 루트 디렉토리에서 Podman Compose(또는 Docker Compose)를 백그라운드(`-d`)로 기동합니다:
```bash
# Podman 사용 시
podman-compose -f docker/podman-compose.yml up -d

# Docker Compose 사용 시
docker compose -f docker/podman-compose.yml up -d
```

#### 2) 정상 기동 및 헬스체크 확인
모든 컨테이너가 정상적으로 `Up` 상태인지 확인합니다:
```bash
podman ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```
* **Kafka 브로커 헬스체크**:
  ```bash
  # Kafka 토픽 목록 조회 (정상 응답 시 0 반환)
  podman exec -it kafka kafka-topics --bootstrap-server localhost:9092 --list
  ```
* **QuestDB 헬스체크**: 브라우저에서 `http://localhost:9000` 접속 후 웹 콘솔 화면이 열리는지 확인합니다.
* **Oracle 19c 헬스체크**: 컨테이너 로그에서 `DATABASE IS READY TO USE!` 메시지가 출력되었는지 확인합니다:
  ```bash
  podman logs formula-oracle19c --tail 20
  ```

---

### 4.2 [2단계] 백엔드(formula-api) 기동 및 기동 확인

`formula-api`는 센서 마스터 레지스트리를 관리하며, 기동 시 DB에 저장된 활성 수식 룰을 Kafka `rule-data` 토픽으로 자동 발행(Startup Sync)합니다.

#### 1) 백엔드 기동
새 터미널 창을 열고 프로젝트 루트에서 실행합니다:
```bash
./gradlew :formula-api:bootRun
```

#### 2) 정상 기동 로그 및 자동 룰 동기화 확인
터미널 로그에서 다음 메시지들이 출력되는지 확인합니다:
1. `Started FormulaApiApplication in ... seconds` (Spring Boot 기동 완료)
2. `[RulePublisherService] Synchronized 2 active formula rules to Kafka topic: rule-data` (초기 수식 룰 자동 브로드캐스트)

#### 3) REST API 헬스체크 호출
```bash
curl -X GET http://localhost:8080/api/v1/registry/sensors
```
* **정상 응답**: `200 OK`와 함께 등록된 센서 목록(기본 마스터 센서 및 수식 센서) JSON이 반환됩니다.

---

### 4.3 [3단계] 스트리밍 엔진(formula-flink) 잡 기동 및 상태 확인

Flink 스트리밍 평가 엔진은 Kafka로부터 센서 패킷과 동적 룰을 동시에 소비하여 `RuleBroadcastProcessFunction`으로 실시간 계산을 수행합니다.

#### 1) Flink 스트리밍 잡 실행
새 터미널 창을 열고 프로젝트 루트에서 실행합니다:
```bash
./gradlew :formula-flink:run
```

#### 2) 정상 연결 로그 확인
터미널 콘솔에 다음과 같은 로그가 출력되는지 확인합니다:
* `Connected to Kafka broker at localhost:9092`
* `Subscribed to topics: sensor-data, rule-data`
* `Initialized RocksDB StateBackend with window TTL 30m`
* `RuleBroadcastProcessFunction ready to accept broadcast rules`

> **참고**: Flink Web UI(`http://localhost:8081`)가 활성화된 경우 브라우저에서 실행 중인 토폴로지 그래프와 TaskManager 상태를 시각적으로 확인할 수 있습니다.

---

### 4.4 [4단계] 프론트엔드 UI(formula-ui) 기동 및 통합 센서 레지스트리 확인

웹 인터페이스의 **[Unified Sensor Registry]** 탭에서 물리 센서(`PHYSICAL`)와 파생 수식 센서(`FORMULA`)를 단일 일련번호(`10001+`) 체계로 통합 관리하고, Flink 메모리로의 룰 일괄 동기화 상태를 제어합니다.

#### 1) 프론트엔드 개발 서버 기동
새 터미널 창을 열고 `formula-ui` 디렉토리에서 실행합니다:
```bash
cd formula-ui
pnpm run dev
```

#### 2) 웹 브라우저 접속 및 통합 수식 센서 확인
1. 브라우저에서 `http://localhost:3000`으로 접속합니다.
2. 상단 메뉴에서 **[Unified Sensor Registry]** 탭을 확인합니다 (물리 센서 및 수식 센서가 단일 테이블에 통합 표시됨).
3. 상단 KPI 요약 카드 중 **[Formula Sensors (Derived / Aviator)]** 카드를 클릭하거나 Type 필터에서 `Formula Sensor`를 선택하면 수식 센서 목록만 즉시 필터링할 수 있습니다:
   - `#10005` (`FML-PUMP-EFF-01`): Pump Thermal Efficiency Index (`window_avg(10) * 1.15 + (P_10002 * 0.05)`)
   - `#10006` (`FML-THERMAL-DELTA`): Device 1 vs Device 2 Temp Delta (`abs(P_10001 - P_10003)`)
   - `#10007` (`TEMP_FAHRENHEIT`): Temperature (Fahrenheit) (`P_10001 * 1.8 + 32`)
   - `#10008` (`SMOOTHED_TEMP`): Smoothed Temperature (60s Window Avg) (`window_avg(60)`)
   - `#10009` (`POWER_EFFICIENCY_INDEX`): Power Efficiency Composite Index (`(TEMP_FAHRENHEIT * 0.6) + (window_max(120) * 0.4)`)
4. 상단 **"+ New Formula Sensor"** 또는 **"Register Sensor / Formula"** 버튼을 통해 신규 물리/수식 센서 등록, 수식 의존성 분석, 문법 검증 및 Dry-Run 테스트를 한 화면에서 수행할 수 있습니다.

#### 3) Flink 룰 일괄 동기화(Sync Rules) 버튼 검증
* 툴바 상단의 **"Sync Rules to Flink"** 버튼을 클릭합니다.
* **정상 처리 확인**:
  - 버튼에 로딩 스피너가 표시된 후 초록색 알림 배너(`Successfully synchronized 5 active formula rule(s) to Kafka topic 'rule-data'.`)가 표시됩니다.
  - `formula-api` 로그에 `Publishing rule to Kafka [rule-data]`가 출력됩니다.
  - `formula-flink` 콘솔에 브로드캐스트 룰 수신 및 갱신 로그가 실시간 출력됩니다.

---

### 4.5 [5단계] 센서 에뮬레이터 기동 및 파이프라인 구간별 정상 처리 검증

1,000개의 센서가 1초 주기로 실시간 데이터를 생성하도록 에뮬레이터를 기동하고, 데이터가 파이프라인의 각 구간을 통과하는 과정을 단계별로 검증합니다.

#### 1) 센서 에뮬레이터 기동
새 터미널 창을 열고 프로젝트 루트에서 실행합니다:
```bash
./gradlew :formula-emulator:run --args="--sensor-count 1000 --emit-interval-ms 1000"
```
* 터미널에 `[SensorEmulator] Emitting batch of 1000 sensor readings to Kafka topic 'sensor-data'...` 로그가 1초마다 지속적으로 출력됩니다.

---

#### 2) 중간 과정 처리 검증 (4-Step Verification)

데이터가 파이프라인의 각 컴포넌트를 정확히 경유하는지 아래의 4단계로 검증합니다:

#### [검증 A] Kafka 인제스천 확인 (Kafka Console Consumer)
Kafka 브로커에 센서 데이터가 정상 유입되는지 확인합니다:
```bash
podman exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic sensor-data \
  --max-messages 5
```
* **정상 결과**: `{"sensorId":"P_10001","value":25.4,"timestamp":...,"status":"NORMAL"}` 형태의 JSON 메시지가 즉시 콘솔에 출력됩니다.

#### [검증 B] Flink 스트리밍 평가 엔진 처리 확인
Flink 터미널 로그를 확인하여 원본 센서와 동적 룰 매칭이 작동하는지 검증합니다:
* 원본 센서 `P_10001`(또는 `10001`)의 패킷을 수신했을 때:
  ```text
  [Aviator Evaluation] Rule applied: rule_fahrenheit on sensor 10001 -> input=25.4, result=77.72, derivedSensorId=10201
  [QuestDbIlpSink] Emitted derived sensor event: sensorId=10201, value=77.72
  ```
* 룰에 정의된 `derivedSensorId`가 `"10201"`로 부여되어 QuestDB Sink로 정상 방출되는지 확인합니다.

#### [검증 C] QuestDB 시계열 저장 확인 (Web Console SQL)
브라우저에서 QuestDB 웹 콘솔(`http://localhost:9000`)에 접속하여 원본 센서와 파생 센서가 동시에 적재되고 있는지 SQL 쿼리를 실행합니다:
```sql
-- 원본 센서(10001) 및 파생 화씨 센서(10201) 실시간 데이터 조회
SELECT timestamp, sensor_id, value, state, has_inaccurate_data
FROM sensor_data
WHERE sensor_id IN ('10001', '10201', 'P_10001')
ORDER BY timestamp DESC
LIMIT 20;
```
* **정상 결과**: 동일 타임스탬프 구간에서 원본 섭씨 값(`25.4`)과 함께 공식에 의해 계산된 파생 화씨 값(`77.72`)이 `sensor_id = '10201'`로 함께 저장되는 것을 확인할 수 있습니다.

#### [검증 D] 프론트엔드 실시간 대시보드 시각화 확인
1. 브라우저에서 `http://localhost:3000`의 **[Realtime Monitoring]** 탭으로 이동합니다.
2. 센서 목록에서 `10001`(원본 온도)과 `10201`(파생 화씨)을 선택합니다.
3. 1초 주기로 두 곡선이 실시간 스트리밍 차트로 매핑되어 시각화되는지 확인합니다.

---

### 4.6 [6단계] 동적 룰(Dynamic Rule) 무중단 핫 업데이트 검증

Flink 애플리케이션을 **재시작하지 않고** 비즈니스 수식을 실시간으로 변경했을 때 즉각 반영되는지 검증합니다.

#### 1) 수식 변경 및 재동기화 (방법 1 또는 방법 2)
* **방법 1 (웹 UI 권장)**:
  1. `http://localhost:3000`의 **[Unified Sensor Registry]** 탭에서 `P_10201` 수식 센서의 [Edit] 버튼 클릭.
  2. 수식을 `value * 1.8 + 32.0`에서 `value * 2.0 + 10.0`으로 수정 후 저장.
  3. 상단 **"🔄 Sync Rules to Flink"** 버튼 클릭.
* **방법 2 (Kafka CLI 직접 전송)**:
  ```bash
  echo '{"ruleId":"rule_fahrenheit","targetSensorId":"P_10001","expression":"value * 2.0 + 10.0","derivedSensorId":"10201","usesWindow":false}' | podman exec -i kafka kafka-console-producer --bootstrap-server localhost:9092 --topic rule-data
  ```

#### 2) 무중단 즉시 반영 검증
1. Flink 엔진이 재시작되지 않고 가동 상태를 유지하는 것을 확인합니다.
2. Flink 로그에 즉시 새로운 룰 반영 로그가 출력됩니다:
   ```text
   [Broadcast Rule Updated] ruleId=rule_fahrenheit, expr=value * 2.0 + 10.0
   [Aviator Evaluation] Rule applied: rule_fahrenheit -> input=25.0, result=60.0, derivedSensorId=10201
   ```
3. QuestDB(`http://localhost:9000`)에서 SQL을 재조회하여 계산 결과가 신규 공식(`25.0 * 2.0 + 10.0 = 60.0`)으로 즉각 전환되어 적재되는 것을 확인합니다.

---

### 4.7 [7단계] 실습 프로세스 안전 종료 및 자원 정리 (Graceful Shutdown)

실습이 끝난 후 시스템 자원의 누수 없이 파이프라인을 안전하게 역순으로 종료합니다.

#### 1) 애플리케이션 프로세스 정상 종료 (역순 종료)
각 프로세스가 실행 중인 터미널에서 `Ctrl + C`를 입력하여 안전하게 종료합니다:

1. **[1단계: 센서 에뮬레이터 중지]**:
   - 에뮬레이터 터미널에서 `Ctrl + C`를 눌러 Kafka 프로듀서 전송을 중단합니다.
2. **[2단계: Flink 스트리밍 잡 중지]**:
   - Flink 터미널에서 `Ctrl + C`를 입력합니다. RocksDB State가 안전하게 디스크에 플러시되고 컨슈머가 정상 언서브스크라이브됩니다.
3. **[3단계: 프론트엔드 개발 서버 중지]**:
   - `formula-ui` 터미널에서 `Ctrl + C`를 눌러 Vite 개발 서버를 종료합니다.
4. **[4단계: 백엔드 API 서버 중지]**:
   - `formula-api` 터미널에서 `Ctrl + C`를 눌러 Spring Boot 애플리케이션 컨텍스트를 정상 종료합니다.

#### 2) 인프라 컨테이너 정리
```bash
# A. 데이터 볼륨을 유지하며 컨테이너만 정지 (다음 실습 시 데이터 보존)
podman-compose -f docker/podman-compose.yml down

# B. (선택사항) 테스트 데이터를 완전히 초기화하고 클린 상태로 정리할 경우
podman-compose -f docker/podman-compose.yml down -v
```

#### 3) 잔여 프로세스 및 포트 점유 해제 점검
종료 후 포트 충돌이 남아있는지 확인합니다 (PowerShell):
```powershell
# 주요 포트 점유 여부 확인
Get-NetTCPConnection -LocalPort 8080, 8081, 9092, 9000, 3000 -ErrorAction SilentlyContinue | Format-Table LocalAddress, LocalPort, State, OwningProcess
```
* 아무 결과도 출력되지 않으면 모든 리소스가 정상적으로 정리된 것입니다.

---

## 📚 관련 문서
* [시스템 전체 아키텍처 및 상세 엔지니어링 가이드 (ARCHITECTURE_GUIDE.md)](../ARCHITECTURE_GUIDE.md)
* [도메인 용어 사전 (GLOSSARY.md)](../GLOSSARY.md)
* [스트리밍 평가 룰 전파 아키텍처 가이드 (STREAMING_EVALUATOR_GUIDE.md)](STREAMING_EVALUATOR_GUIDE.md)
* [Formula UI 아키텍처 및 사용자 가이드 (FRONTEND_UI_GUIDE.md)](FRONTEND_UI_GUIDE.md)
