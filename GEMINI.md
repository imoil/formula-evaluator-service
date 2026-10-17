# **🚀 대규모 IoT 시계열 데이터 평가 시스템 (Dual-Path Architecture) - Development Harness**

## **1. 프로젝트 개요 (Project Overview)**

본 프로젝트는 1초 간격으로 유입되는 수백만 개의 IoT 센서 데이터를 지연 없이 수집하고, 사용자가 정의한 수십만 개의 복잡한 수식(Formula)과 윈도우 함수(Window Functions)를 동적으로 평가하여 파생 데이터를 생성하는 고성능 분산 처리 시스템입니다.  
**핵심 과제:**

* 극단적인 카디널리티(High Cardinality) 환경에서의 초고속 데이터 삽입 및 조회.  
* 런타임에 동적으로 변경되는 수만~수십만 개의 사용자 정의 룰(Rule) 실시간 평가.  
* 중첩 함수 및 과거 데이터를 요구하는 커스텀 윈도우 연산 지원.
* 관심사 분리(SoC)를 보장하는 Gradle 멀티 모듈 아키텍처 구축.

## **2. 핵심 아키텍처: 듀얼 패스 실행(Dual-Path Execution)**

시스템의 평가 로직은 성능과 실시간성을 모두 충족하기 위해 두 가지 독립적인 경로로 분리하여 설계 및 구현합니다.

1. **스트리밍 평가 경로 (Streaming Path - Apache Flink 1.19.3)**  
   * 원본 데이터가 유입되는 즉시 1초 단위로 주기적 계산 수행 후 DB 저장.  
   * `KeyedProcessFunction` 및 `Broadcast State` 패턴을 통해 룰을 동적으로 브로드캐스트하여 실시간 처리.  
   * `Configuration` 기반 RocksDB StateBackend(`StateBackendOptions.STATE_BACKEND`) 및 30분 TTL을 통한 무제한 힙 메모리 팽창 방지.
2. **실시간 조회 평가 경로 (On-Demand Path - Spring Boot 3.5 & QuestDB 10.0.1)**  
   * 사용자가 대시보드/API를 통해 특정 기간의 계산을 요청할 때 즉시(On-Demand) 평가 (`GET /api/v1/evaluate`).  
   * DB 레벨: QuestDB 10.0.1의 `SAMPLE BY`, 벡터화 연산(Vectorized Execution) 적극 활용.  
   * App 레벨: `TimeSeriesDataSourceAdapter` 계층을 통해 QuestDB, RDBMS, 인메모리 링버퍼 등 다중 소스 지원.
   * AviatorScript 5.9.0 JIT 바이트코드 컴파일 캐싱 기반 로컬 평가.

## **3. 프로젝트 멀티 모듈 구조 (Multi-Module Layout)**

```
formula-evaluator-service/
├── formula-core/    # 순수 도메인 모델 (SensorData, DynamicRule)
├── formula-engine/  # AviatorScript 5.9.0 엔진 및 윈도우 집계 함수 (window_avg, window_max)
├── formula-api/     # Spring Boot 3.5 웹 서비스, 플러그형 DataSource 어댑터, 파티션 관리
└── formula-flink/   # Flink 1.19.3 분산 스트리밍 잡, Broadcast State, QuestDB ILP 싱크
```

## **4. 기술 스택 및 버전 규약 (Technology Stack & Versions)**

코드 생성 및 구현 시 다음의 버전과 기술 요소를 엄격하게 준수합니다. (모든 라이브러리는 웹 검색 기반 최신 안정 호환 버전으로 검증 완료)

* **Language:** Java 25 (OpenJDK 25.0.1 LTS Toolchain)
* **Build Tool:** Gradle 9.8.0 (Version Catalog: `gradle/libs.versions.toml`)
* **Framework:** Spring Boot 3.5.16 (Spring Boot 3.5.x 라인 최종 안정 릴리즈, `io.spring.dependency-management` 1.1.7)
* **Time-Series Database (TSDB):** QuestDB 10.0.1  
  * 쓰기: QuestDB Official Client 1.3.9 (`org.questdb:questdb-client:1.3.9`, ILP / QWP 고속 스트리밍 지원)  
  * 읽기: PostgreSQL Wire (Spring Data JPA / JdbcTemplate, Driver `org.postgresql:42.7.14`)  
* **Streaming Engine:** Apache Flink 1.19.3  
  * Connector: `org.apache.flink:flink-connector-kafka:3.3.0-1.19`  
  * State Backend: `Configuration` 및 `StateBackendOptions.STATE_BACKEND` 기반 RocksDB StateBackend (Flink 1.19+ 최신 API 표준)  
* **Expression Engine:** AviatorScript 5.9.0 (`io.github.aviatorscript:aviator:5.9.0`)
  * 동적 바이트코드 컴파일 지원, 화이트리스트 샌드박스 및 가비지 컬렉션(GC) 최소화 목적  
* **Testing & CI/CD:** Podman Compose, Testcontainers 1.21.4 (`org.testcontainers.kafka.ConfluentKafkaContainer`, `disabledWithoutDocker = true`)  
* **Message Broker:** Apache Kafka 3.9.2 (Sensor Data & Rule Stream)

## **5. 핵심 구현 지침 (Implementation Directives)**

1. **표현식 엔진 최적화:** AviatorScript를 사용하여 수식을 즉시 Java Bytecode로 컴파일하고 인스턴스를 캐싱하여 파싱 오버헤드를 제거합니다.  
   * `window_avg`, `window_max` 등 커스텀 윈도우 로직은 `AviatorEvaluator.addFunction()`을 통해 엔진에 주입합니다.  
2. **데이터베이스 I/O 병목 제거:** Flink 및 Spring Boot에서 QuestDB로 데이터를 쓸 때는 **반드시 공식 최신 ILP/QWP 클라이언트(`questdb-client:1.3.9`)**를 사용하여 비동기/배치 방식으로 전송합니다 (`auto_flush_rows=100000`).  
3. **메모리 및 상태 관리 (Flink):** 센서 데이터 버퍼링 시 메모리 폭발을 방지하기 위해 Flink의 `ListState`와 `StateTtlConfig`를 결합하여 상태 생명주기(TTL)를 철저히 관리합니다. 상태 백엔드는 Deprecated된 `env.setStateBackend()` 대신 `Configuration` 기반 설정을 사용합니다.  
4. **플러그형 데이터 소스 분리:** API 계층의 온디맨드 쿼리는 `TimeSeriesDataSourceAdapter` 인터페이스를 거치며, 구체 클래스(`QuestDBDataSourceAdapter`, `RdbmsDataSourceAdapter`)를 통해 구현합니다.
5. **테스트 안정성:** 모든 Testcontainers 기반 테스트에는 최신 모듈인 `ConfluentKafkaContainer`와 Docker 데몬 미구동 시 자동 스킵되는 `@Testcontainers(disabledWithoutDocker = true)`를 적용합니다.

**[Note to Gemini AI]** 이후 개발 요청 시, 본 GEMINI.md의 **듀얼 패스 아키텍처**와 지정된 **멀티 모듈 구조 및 최신 기술 스택 버전**을 최우선으로 고려하여 코드를 생성할 것.
