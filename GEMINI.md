# **🚀 대규모 IoT 시계열 데이터 평가 시스템 (Dual-Path Architecture) \- Development Harness**

## **1\. 프로젝트 개요 (Project Overview)**

본 프로젝트는 1초 간격으로 유입되는 수백만 개의 IoT 센서 데이터를 지연 없이 수집하고, 사용자가 정의한 수십만 개의 복잡한 수식(Formula)과 윈도우 함수(Window Functions)를 동적으로 평가하여 파생 데이터를 생성하는 고성능 분산 처리 시스템을 구축하는 것을 목표로 합니다.  
**핵심 과제:**

* 극단적인 카디널리티(High Cardinality) 환경에서의 초고속 데이터 삽입 및 조회.  
* 런타임에 동적으로 변경되는 수만\~수십만 개의 사용자 정의 룰(Rule) 실시간 평가.  
* 중첩 함수 및 과거 데이터를 요구하는 커스텀 윈도우 연산 지원.

## **2\. 핵심 아키텍처: 듀얼 패스 실행(Dual-Path Execution)**

시스템의 평가 로직은 성능과 실시간성을 모두 충족하기 위해 두 가지 독립적인 경로로 분리하여 설계 및 구현합니다.

1. **스트리밍 평가 경로 (Streaming Path \- Apache Flink 2.2)**  
   * 원본 데이터가 유입되는 즉시 1초 단위로 주기적 계산 수행 후 DB 저장.  
   * 단순 수식: Flink Materialized Tables 활용.  
   * 복잡 수식: KeyedProcessFunction 및 Broadcast State 패턴을 통해 룰을 동적으로 브로드캐스트하여 실시간 처리.  
2. **실시간 조회 평가 경로 (On-Demand Path \- Spring Boot & QuestDB)**  
   * 사용자가 대시보드/API를 통해 특정 기간의 계산을 요청할 때 즉시(On-Demand) 평가.  
   * DB 레벨: QuestDB 9.3.4의 WINDOW JOIN, SAMPLE BY, 벡터화 연산(Vectorized Execution) 적극 활용.  
   * App 레벨: 복합 수식은 Spring Boot 메모리 내에 초기화된 표현식 엔진으로 로컬 평가.

## **3\. 기술 스택 및 버전 규약 (Technology Stack & Versions)**

코드 생성 및 구현 시 다음의 버전과 기술 요소를 엄격하게 준수합니다.

* **Language:** Java 21  
* **Framework:** Spring Boot 3.4.x  
* **Time-Series Database (TSDB):** QuestDB 9.3.4  
  * 쓰기: QuestDB Official ILP Client (TCP/HTTP)  
  * 읽기: PostgreSQL Wire (Spring Data JPA / JdbcTemplate)  
* **Streaming Engine:** Apache Flink 2.2.x  
  * State Backend: RocksDB 8.10.0 (통합 파일 병합 메커니즘 활성화)  
* **Expression Engine:** AviatorScript 5.4.1  
  * 동적 바이트코드 컴파일 지원, 가비지 컬렉션(GC) 최소화 목적  
* **Testing & CI/CD:** Podman Compose, Testcontainers (JUnit Jupiter)  
* **Message Broker:** Apache Kafka (Sensor Data & Rule Stream)

## **4\. 핵심 구현 지침 (Implementation Directives)**

1. **표현식 엔진 최적화:** \- AviatorScript를 사용하여 수식을 즉시 Java Bytecode로 컴파일하고 인스턴스를 캐싱하여 파싱 오버헤드를 제거합니다.  
   * window\_avg, window\_max 등 커스텀 윈도우 로직은 AviatorEvaluator.addFunction()을 통해 엔진에 주입합니다.  
2. **데이터베이스 I/O 병목 제거:** \- Flink 및 Spring Boot에서 QuestDB로 데이터를 쓸 때는 **반드시 공식 ILP 클라이언트**를 사용하여 비동기/배치 방식으로 전송합니다. JPA를 통한 저장은 지양합니다.  
3. **메모리 및 상태 관리 (Flink):** \- 센서 데이터 버퍼링 시 메모리 폭발을 방지하기 위해 Flink의 MapState와 Event-Time Timer를 결합하여 상태 생명주기(TTL)를 철저히 관리합니다.

## **5\. 단계별 개발 마일스톤 (Development Milestones)**

### **Phase 1: 기반 인프라 및 프로젝트 스캐폴딩**

* \[ \] Java 21 및 Spring Boot 3.4 기반 프로젝트 뼈대 생성 (Maven/Gradle).  
* \[ \] podman-compose.yml 작성 (QuestDB 9.3.4, Kafka, Zookeeper 구성).  
* \[ \] QuestDB 접속을 위한 ILP Client Bean 및 DB 커넥션 풀(PGWire) 설정.

### **Phase 2: 코어 도메인 및 고성능 수집 파이프라인 구현**

* \[ \] SensorData, DynamicRule 등 코어 도메인 모델 정의.  
* \[ \] QuestDBIngestionService 구현 (ILP Sender 활용).  
* \[ \] 대량 데이터 삽입 단위 테스트 (Testcontainers \+ Podman 연동).

### **Phase 3: AviatorScript 기반 동적 표현식 엔진(Rule Engine) 구현**

* \[ \] AviatorEvaluator 스프링 빈 등록 및 캐싱 전략 구현.  
* \[ \] 커스텀 윈도우 함수(AbstractFunction 확장) 정의 및 엔진 바인딩.  
* \[ \] 중첩 함수 및 복잡 수식 파싱/평가 단위 테스트 로직 작성.

### **Phase 4: Flink 2.2 스트리밍 평가 파이프라인 (Streaming Path) 구축**

* \[ \] Kafka Source(데이터 스트림, 룰 메타데이터 스트림) 연동.  
* \[ \] Broadcast State 패턴을 적용한 룰 분배 로직 구현.  
* \[ \] KeyedBroadcastProcessFunction 내부에서 AviatorScript 엔진을 호출하여 실시간 평가 연산 수행.  
* \[ \] RocksDB 상태 백엔드 설정 적용 및 Flink Sink(QuestDB ILP) 연동.

### **Phase 5: 온디맨드 API 및 DB 네이티브 조회 경로 (On-Demand Path) 구축**

* \[ \] Spring Boot 기반 REST API 엔드포인트 설계 (/api/v1/evaluate).  
* \[ \] QuestDB의 SAMPLE BY 및 WINDOW JOIN 구문을 활용한 고성능 쿼리 리포지토리 구현.  
* \[ \] DB 조회 결과(Raw Data)를 Spring Boot 메모리로 가져와 AviatorScript로 최종 평가하는 로직 결합.

### **Phase 6: 통합 테스트, 검증 및 성능 튜닝**

* \[ \] Flink Job과 Spring Boot App, QuestDB를 모두 띄우는 End-to-End 통합 테스트 수행.  
* \[ \] QuestDB 보존 정책(Drop Partition) 및 ILP 배치 버퍼 튜닝.  
* \[ \] Flink 윈도우 상태 크기 모니터링 및 GC 최적화 검토.

**\[Note to Gemini AI\]** 이후 개발 요청 시, 본 GEMINI.md의 **듀얼 패스 아키텍처**와 지정된 **기술 스택 버전**을 최우선으로 고려하여 코드를 생성할 것. 특히 시계열 데이터 처리에 있어서 성능(메모리 GC 억제, 배치 처리)에 초점을 맞춘 코드를 제시해야 함.