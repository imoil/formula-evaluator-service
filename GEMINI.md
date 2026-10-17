# **🚀 대규모 IoT 시계열 데이터 평가 시스템 (Dual-Path Architecture) - Development Harness**

## **1. 프로젝트 개요 (Project Overview)**

본 프로젝트는 1초 간격으로 유입되는 수백만 개의 IoT 센서 데이터를 지연 없이 수집하고, 사용자가 정의한 수십만 개의 복잡한 수식(Formula)과 윈도우 함수(Window Functions)를 동적으로 평가하여 파생 데이터를 생성하는 고성능 분산 처리 시스템입니다.  
**핵심 과제:**

* 극단적인 카디널리티(High Cardinality) 환경에서의 초고속 데이터 삽입 및 조회.  
* 런타임에 동적으로 변경되는 수만~수십만 개의 사용자 정의 룰(Rule) 실시간 평가.  
* 임의 코드 실행(RCE) 및 무한 루프 DoS 공격을 원천 차단하는 수식 보안 샌드박스(`ExpressionValidator`).
* 중첩 함수 및 과거 데이터를 요구하는 커스텀 윈도우 연산 지원 (`window_avg`, `window_max`).
* 관심사 분리(SoC)를 보장하는 Gradle 멀티 모듈 및 프론트엔드 모노레포 아키텍처 구축.

## **2. 핵심 아키텍처: 듀얼 패스 실행(Dual-Path Execution)**

시스템의 평가 로직은 성능과 실시간성을 모두 충족하기 위해 세 가지 독립적인 경로로 분리하여 설계 및 구현합니다.

1. **스트리밍 평가 경로 (Streaming Path - Apache Flink 1.19.3)**  
   * 원본 데이터가 유입되는 즉시 1초 단위로 주기적 계산 수행 후 DB 저장.  
   * `KeyedProcessFunction` 및 `Broadcast State` 패턴을 통해 룰을 동적으로 브로드캐스트하여 실시간 처리.  
   * `Configuration` 기반 RocksDB StateBackend(`StateBackendOptions.STATE_BACKEND = "rocksdb"`) 및 30분 TTL을 통한 무제한 힙 메모리 팽창 방지.
   * `ExpressionValidator`를 통한 룰 수식 사전 정적 분석.
2. **실시간 조회 평가 경로 (On-Demand Path - Spring Boot 3.5 & QuestDB 10.0.1)**  
   * 사용자가 대시보드/API를 통해 특정 기간의 계산을 요청할 때 즉시(On-Demand) 평가 (`GET /api/v1/evaluate`).  
   * DB 레벨: QuestDB 10.0.1의 `SAMPLE BY`, 벡터화 연산(Vectorized Execution) 적극 활용 (정규식 화이트리스트로 인젝션 방어).  
   * App 레벨: `TimeSeriesDataSourceAdapter` 계층을 통해 QuestDB, RDBMS, 인메모리 링버퍼, Mock 등 다중 소스 지원.
   * `LinkedList` 기반 $O(1)$ 슬라이딩 윈도우 및 평가 맵 객체 재사용(Zero-Allocation)을 통한 GC 최적화.
   * AviatorScript 5.9.0 JIT 바이트코드 컴파일 캐싱 및 보안 샌드박스 기반 로컬 평가.
3. **인터랙티브 웹 UI 경로 (Interactive UI Path - Vue 3 & Vite 8)**  
   * 사용자가 웹 브라우저에서 다단 합성 수식(Composite Formula DAG)을 작성하고 실시간 검증.  
   * `CircularBuffer` (`Float64Array`) 및 사전 바인딩 추출을 통한 86,400초 시계열 초고속 시뮬레이션.  
   * 백엔드 `MockSensorCatalogService` 연동 100개 가상 센서 원본 시계열 시각화 (Apache ECharts 6.1).

## **3. 프로젝트 멀티 모듈 구조 (Multi-Module Layout)**

```
formula-evaluator-service/
├── formula-core/    # 순수 도메인 모델 (SensorData, DynamicRule)
├── formula-engine/  # AviatorScript 5.9.0 엔진, 보안 검증(ExpressionValidator), 윈도우 집계 함수
├── formula-api/     # Spring Boot 3.5 웹 서비스, 플러그형 DataSource 어댑터, 100개 센서 카탈로그, 파티션 관리
├── formula-flink/   # Flink 1.19.3 분산 스트리밍 잡, Broadcast State, QuestDB ILP 싱크
└── formula-ui/      # Vite 8 + Vue 3 + Vuetify 4 프론트엔드 SPA, 초고속 시뮬레이터, ECharts
```

## **4. 기술 스택 및 버전 규약 (Technology Stack & Versions)**

코드 생성 및 구현 시 다음의 버전과 기술 요소를 엄격하게 준수합니다.

* **Language:** Java 25 (OpenJDK 25.0.1 LTS Toolchain), TypeScript 5.8
* **Build Tool:** Gradle 9.8.0 (Version Catalog: `gradle/libs.versions.toml`), pnpm 12
* **Framework:** Spring Boot 3.5.16 (`io.spring.dependency-management` 1.1.7)
* **Time-Series Database (TSDB):** QuestDB 10.0.1  
  * 쓰기: QuestDB Official Client 1.3.9 (`org.questdb:questdb-client:1.3.9`, ILP / QWP 고속 스트리밍 지원, `ChronoUnit.NANOS`)  
  * 읽기: PostgreSQL Wire (Spring Data JPA / JdbcTemplate, Driver `org.postgresql:42.7.14`)  
* **Streaming Engine:** Apache Flink 1.19.3  
  * Connector: `org.apache.flink:flink-connector-kafka:3.3.0-1.19`  
  * State Backend: `Configuration` 및 `StateBackendOptions.STATE_BACKEND` 기반 RocksDB StateBackend (Flink 1.19+ 최신 API 표준)  
* **Expression Engine:** AviatorScript 5.9.0 (`io.github.aviatorscript:aviator:5.9.0`)
  * 동적 바이트코드 컴파일 지원, 화이트리스트 샌드박스 및 가비지 컬렉션(GC) 최소화 목적
  * `ExpressionValidator` 기반 사전 필터링 및 `ALLOWED_CLASS_SET = emptySet()` 클래스 격리  
* **Frontend:** Vue 3.5, Vuetify 4.2.4, Vite 8.3, Apache ECharts 6.1, Pinia 4.0.3, Vitest 5.0.3
* **Testing & CI/CD:** Podman Compose, Testcontainers 1.21.4 (`org.testcontainers.kafka.ConfluentKafkaContainer`, `disabledWithoutDocker = true`), Vitest
* **Message Broker:** Apache Kafka 3.9.2 (Sensor Data & Rule Stream)

## **5. 핵심 구현 지침 (Implementation Directives)**

1. **표현식 엔진 최적화 및 보안 샌드박스:**
   * 모든 수식은 평가 전에 반드시 `ExpressionValidator.validate()`를 통과해야 합니다. 길이 500자 제한, 화이트리스트 문자 및 연산자 검증, 단독 대입문(`=`) 금지, 시스템/리플렉션 키워드 차단.
   * `RuleEngineConfig`에서 `Options.ALLOWED_CLASS_SET`을 빈 집합으로 지정하여 클래스 로딩을 차단하고, 무한 루프 DoS를 방지하기 위해 `MAX_LOOP_COUNT = 1000`, `EVAL_TIMEOUT_MS = 5000L`을 유지합니다.
   * `window_avg`, `window_max` 등 커스텀 윈도우 로직은 `AbstractWindowFunction`을 상속받아 안전하게 슬라이스 계산을 수행합니다.
2. **데이터베이스 I/O 및 인젝션 방어:**
   * QuestDB로 데이터를 쓸 때는 **공식 최신 ILP/QWP 클라이언트(`questdb-client:1.3.9`)**를 사용하여 비동기/배치 방식으로 전송합니다 (`auto_flush_rows=100000`, `ChronoUnit.NANOS`).
   * `QuestDBQueryRepository`의 `SAMPLE BY` 구문은 정규식 패턴(`^[1-9][0-9]*(?:s|m|h|d|w|ms|M|y)$`)으로 엄격히 검증 후 바인딩합니다.
3. **메모리 및 상태 관리 (Flink & Backend):**
   * 센서 데이터 버퍼링 시 메모리 폭발을 방지하기 위해 Flink의 `ListState`와 `StateTtlConfig`(30분)를 결합하여 상태 수명주기를 관리합니다.
   * 백엔드 온디맨드 평가 루프에서는 `LinkedList`를 사용하여 $O(1)$ 슬라이딩 윈도우를 유지하고, `env` 맵을 재사용하여 GC 부담을 최소화합니다.
4. **프론트엔드 고성능 아키텍처:**
   * `formula-ui`의 시뮬레이션에서는 자바스크립트 기본 배열 `shift()` 대신 `Float64Array` 기반 `CircularBuffer`를 사용합니다.
   * 시뮬레이션 루프 진입 전 센서와 수식의 바인딩을 사전 추출하여 루프 내 `Map.get()` 호출을 배제합니다.
5. **플러그형 데이터 소스 분리:**
   * API 계층의 온디맨드 쿼리는 `TimeSeriesDataSourceAdapter` 인터페이스를 거치며, 구체 클래스(`QuestDBDataSourceAdapter`, `MockNormalDistributionDataSourceAdapter`, `RdbmsDataSourceAdapter`)를 통해 구현합니다.
   * 100개 센서 메타데이터와 시계열 원천은 `MockSensorCatalogService`가 Single Source of Truth로 동작합니다.
6. **테스트 안정성:**
   * 모든 Testcontainers 기반 테스트에는 최신 모듈인 `ConfluentKafkaContainer`와 Docker 데몬 미구동 시 자동 스킵되는 `@Testcontainers(disabledWithoutDocker = true)`를 적용합니다.
