# **대규모 IoT 환경을 위한 고성능 듀얼 패스 시계열 데이터 평가 시스템 아키텍처 및 구현**

## **1\. 서론: 대규모 시계열 데이터 처리의 아키텍처적 과제**

현대의 대규모 산업용 사물인터넷(IoT) 및 텔레메트리 환경에서는 1초 간격으로 수백만 개의 센서에서 발생하는 원본 시계열 데이터를 지연 없이 수집, 저장, 분석해야 하는 극단적인 엔지니어링 과제에 직면해 있다. 이러한 시스템의 핵심적인 요구사항은 단순히 데이터를 적재하는 것을 넘어, 사용자가 수십만 개 이상의 사용자 정의 수식(Formula)과 함수(Function)를 조합하여 새로운 파생 시계열 데이터를 동적으로 생성할 수 있도록 지원하는 것이다. 사용자가 정의하는 수식은 단순한 사칙연산을 초래하여, 입력값의 전처리, 조건부 반환, 함수의 다중 중첩(Nested Functions), 그리고 과거 일정 기간의 데이터를 연산하는 윈도우 함수(Window Functions)를 모두 포함할 수 있어야 한다.  
이러한 대규모의 복잡한 연산 요구사항을 충족하기 위해 수식은 데이터베이스에 중앙 집중적으로 저장되며, 시스템은 이를 두 가지의 완전히 독립적인 실행 경로(Dual-Path Execution)를 통해 평가해야 한다. 첫째, 원본 데이터가 유입되는 즉시 1초 단위의 지연을 방지하면서 주기적으로 계산하여 데이터베이스에 저장하는 '스트리밍 평가 경로'이다. 둘째, 사용자가 대시보드나 API를 통해 실시간으로 특정 기간의 계산을 요청할 때, 스트리밍 플랫폼을 거치지 않고 시계열 데이터베이스(TSDB)를 직접 조회하여 온디맨드(On-Demand)로 평가하는 '실시간 조회 평가 경로'이다.  
본 보고서에서는 이러한 요구사항을 완벽히 충족하기 위해 최신 Apache Flink 2.2, 고성능 시계열 데이터베이스인 QuestDB 9.3.4, AviatorScript, 그리고 Spring Boot 3.4(Java 21 환경)를 결합한 분산 시스템 아키텍처를 설계하고, 최신 릴리스의 기능을 활용한 심층적인 기술적 근거와 최적화 전략, 실제 구현 코드를 제시한다.

## **2\. 시계열 데이터베이스(TSDB) 선정 및 최신 기능 최적화: QuestDB 9.3.4**

수백만 개의 센서에서 1초 간격으로 유입되는 데이터를 처리하고, 사용자의 온디맨드 계산 요청에 대해 즉각적인 응답을 제공하기 위해서는 기반이 되는 시계열 데이터베이스의 쓰기 및 읽기 성능이 전체 시스템의 병목을 좌우한다.

### **2.1. QuestDB와 InfluxDB 3.0의 아키텍처 및 성능 비교**

최근 시계열 데이터베이스 환경에서는 초고속 수집 및 조회 성능을 두고 QuestDB와 InfluxDB 3.0이 강력한 대안으로 평가받고 있다. 최근 벤치마크 테스트(TSBS) 결과에 따르면, QuestDB는 극단적으로 높은 카디널리티(High Cardinality, 예: 100만 개 이상의 고유 센서) 환경에서 InfluxDB 3.0 Core 대비 12배에서 최대 36배 빠른 초당 데이터 삽입(Ingestion) 성능을 보여준다. QuestDB는 100만 개 호스트 환경에서도 성능 저하 없이 초당 1,136만\~1,140만 건의 압도적인 데이터 수집 속도를 유지한다.  
사용자의 실시간 온디맨드 계산 요청 처리 시, 여러 센서 데이터를 쿼리하거나 복잡한 분석을 수행할 때 QuestDB는 InfluxDB 3.0보다 17배에서 최대 418배 빠른 쿼리 성능을 기록했다. 이는 QuestDB가 Java와 C++로 설계된 컬럼형 스토리지(Columnar Storage)를 기반으로 불필요한 가비지 컬렉션을 최소화했기 때문이다.

| 성능 지표 및 기능 | QuestDB | InfluxDB 3.0 Core |
| :---- | :---- | :---- |
| **고카디널리티 삽입 성능** | 1,136만 rows/sec (약 12\~36배 빠름) | 약 32만 rows/sec (카디널리티에 무관하게 정체) |
| **복잡한 분석 쿼리 성능** | 17배 \~ 418배 빠름 | 비교적 느림 |
| **기반 스토리지/언어** | Java, C++ (Zero-GC, 컬럼형) | Rust, Apache Arrow/DataFusion |
| **지원 프로토콜 및 쿼리** | ILP, PostgreSQL Wire (표준 SQL 지원) | Arrow Flight, SQL/InfluxQL |

### **2.2. QuestDB 9.3.4 벡터화 연산 및 WINDOW JOIN 활용**

QuestDB 9.3.4 릴리스는 대규모 시계열 분석 성능을 극적으로 향상시키는 중요한 기능들을 포함한다.  
첫째, \*\*동적 윈도우 범위를 지원하는 WINDOW JOIN\*\*이다. 여러 센서의 과거 데이터를 특정 시간 범위(예: 이벤트 전후 10초)로 묶어 연산해야 하는 복잡한 교차 센서 수식을 평가할 때, 기존에는 애플리케이션 메모리에서 조인해야 했으나 이제는 DB 계층에서 직접 WINDOW JOIN을 통해 초고속으로 처리할 수 있다. 둘째, **벡터화된 GROUP BY 범위 확장 및 빠른 ORDER BY 처리** 기능이다. QuestDB의 쿼리 엔진은 SIMD 명령어를 활용하여 복수의 데이터 포인트를 병렬 처리하는 벡터화 실행(Vectorized Execution)을 지원하는데, 9.3.4 버전에서는 이 커버리지가 대폭 확대되어 대량의 과거 데이터를 온디맨드로 조회할 때 CPU 병목을 극도로 낮춘다.

## **3\. 사용자 정의 수식 및 윈도우 함수 처리를 위한 표현식 엔진**

사용자가 정의하는 수십만 개의 복잡한 수식을 초당 수백만 번 평가하기 위해서는 단순한 문자열 파서를 넘어선 고성능 표현식 엔진(Expression Engine)이 요구된다. 사용자는 process(moving\_avg(sensorA, 60s) \+ max(sensorB, 120s))와 같이 함수가 중첩되고(Nested), 과거 윈도우 데이터를 요구하는 수식을 자유롭게 작성할 수 있어야 한다.

### **3.1. 고성능 JVM 표현식 엔진 선정: AviatorScript**

Java 생태계에는 SpEL, JEXL, MVEL 등 다양한 표현식 언어가 존재한다. 그러나 SpEL은 런타임에 리플렉션에 크게 의존하며 대규모 스트림 처리에서 막대한 가비지 컬렉션(GC) 오버헤드를 발생시킨다.  
본 아키텍처에서는 경량화된 고성능 JVM 스크립팅 언어인 AviatorScript를 도입한다. AviatorScript의 가장 큰 장점은 입력된 수식을 즉시 Java 바이트코드(Bytecode)로 컴파일하여 실행한다는 점이다. 한 번 컴파일된 Expression 객체는 내부 캐시에 저장되어 이후 수백만 번의 호출에서도 파싱 오버헤드 없이 네이티브 Java 코드와 유사한 성능으로 실행된다.

### **3.2. 중첩 함수 및 커스텀 윈도우 함수 컨텍스트 구현**

사용자가 요구하는 '과거 일정 기간의 데이터를 연산하는 윈도우 함수'는 순수 수학 표현식만으로는 해결할 수 없다. AviatorScript는 Java의 인스턴스 메서드나 사용자 정의 로직을 표현식 내의 네이티브 함수로 등록할 수 있는 AviatorEvaluator.addFunction() 기능을 제공한다.  
이를 통해 아키텍처는 window\_avg(dataList)나 window\_max(dataList)와 같은 커스텀 함수를 엔진에 주입한다. 시스템이 센서의 최근 60초 데이터를 버퍼링하여 리스트 형태로 엔진에 전달하면, 엔진은 중첩된 수식 트리를 순회하며 가장 안쪽의 윈도우 함수부터 계산을 수행하고, 그 결과값을 다시 상위 함수의 입력값으로 전달하여 최종 결과를 산출한다.

## **4\. 스트리밍 플랫폼을 활용한 실시간 주기적 평가 아키텍처 (Flink 2.2)**

사용자가 정의한 파생 시계열 데이터를 1초 단위의 지연 없이 주기적으로 계산하여 DB에 저장하는 스트리밍 처리 엔진으로는 최신 Apache Flink 2.2가 핵심적인 역할을 수행한다.

### **4.1. Flink 2.2 구체화 뷰(Materialized Tables)를 통한 단순 수식 분리**

수십만 개의 룰 중 단순히 "A 센서의 지난 1분 평균"과 같이 표준화된 연속 집계를 요구하는 수식들은 굳이 무거운 커스텀 로직을 태울 필요가 없다. Flink 2.2에 정식으로 고도화된 **Materialized Tables (구체화 테이블)** 기능을 사용하면, FRESHNESS \= INTERVAL '1' SECOND와 같이 데이터 최신성 목표를 선언적으로 정의하기만 하면 Flink 엔진이 백그라운드에서 스트리밍 파이프라인을 자동 구성하여 결과를 도출한다. 이로써 자바 코드의 복잡성을 줄이고 표준 SQL만으로 처리가 가능해진다.

### **4.2. 저지연 커스텀 윈도우 처리를 위한 KeyedProcessFunction 설계**

복잡한 커스텀 로직이나 타 센서와의 조건부 결합이 필요한 수식의 경우 Flink의 KeyedProcessFunction을 활용하여 사용자 정의 윈도우 메커니즘을 구현한다. 각 센서 ID별로 Flink의 관리형 상태인 MapState를 선언하여 데이터를 중앙 버퍼에 단 한 번만 저장한다. 새로운 이벤트가 도착하면 상태에 추가함과 동시에, 해당 데이터가 윈도우 기간(예: 60초)을 벗어나는 시점에 상태에서 삭제되도록 이벤트 타임 타이머(Event-Time Timer)를 등록한다. 이를 통해 데이터 중복을 완전히 제거하면서 실시간 윈도우 계산을 달성할 수 있다.

### **4.3. Broadcast State 패턴을 이용한 동적 수식 평가**

수십만 개의 복잡한 사용자 정의 수식은 사용자에 의해 실시간으로 수정될 수 있다. 수식 메타데이터의 변경 사항은 Kafka 토픽으로 전송되며, Flink는 이 룰 스트림을 읽어 클러스터 내의 모든 태스크 매니저에게 브로드캐스트(Broadcast)한다.  
센서 원본 데이터 스트림은 sensor\_id를 기준으로 키잉(Keyed)된 후, 브로드캐스트된 룰 스트림과 연결(Connect)되어 KeyedBroadcastProcessFunction에서 함께 처리된다. 함수 내에서 새로운 수식이 도달하면 즉시 AviatorScript 엔진에 의해 컴파일되어 메모리에 캐싱되고, 유입되는 센서 데이터는 재시작 없이 새로운 수식에 의해 평가된다.

## **5\. QuestDB 기반의 온디맨드(On-Demand) 실시간 평가 아키텍처**

사용자가 대시보드를 통해 과거 특정 시점부터 현재까지의 파생 시계열 데이터를 즉시 요청하는 경우, 이 작업은 두 번째 실행 경로인 Spring Boot 애플리케이션을 통해 QuestDB를 직접 조회하여 온디맨드 평가를 수행하는 방식이다.

### **5.1. WINDOW JOIN 및 SAMPLE BY를 활용한 시계열 네이티브 최적화 (QuestDB 9.3.4)**

단순 주기적 집계(예: 분당 평균)를 요구하는 수식의 경우, QuestDB의 강력한 시계열 네이티브 기능인 SAMPLE BY 구문을 적극 활용한다. 또한, QuestDB 9.3.4에서 향상된 WINDOW JOIN 기능을 사용하면 두 개 이상의 센서 데이터를 조인하여 평가할 때 메모리로 끌어올릴 필요 없이 DB 엔진의 SIMD 명령어 세트를 활용해 병렬로 즉시 연산할 수 있다. 이는 네트워크 비용과 애플리케이션 GC 부하를 극적으로 감소시킨다.

### **5.2. Spring Boot 내장 AviatorScript를 통한 로컬 평가**

데이터베이스 레벨의 네이티브 함수만으로 해결하기 어려운 비표준 중첩 수식의 경우, Spring Boot가 쿼리 실행의 주체가 된다. QuestDB의 PostgreSQL Wire 호환성을 이용하여 복합 인덱스 스캔으로 데이터를 추출하고, Spring Boot 메모리 내에 초기화된 AviatorScript 엔진으로 전달하여 연산을 완료한다.

## **6\. Spring Boot 3.4 및 QuestDB 기반 시스템 통합 및 구현**

### **6.1. Maven 의존성 설정 (Flink 2.2 및 QuestDB)**

시스템은 Spring Data JPA(조회용 PGWire), QuestDB 공식 ILP 클라이언트(고속 쓰기용), Flink 2.2 라이브러리, 그리고 AviatorScript를 통합한다.  
`<properties>`  
    `<java.version>21</java.version>`  
    `<spring-boot.version>3.4.0</spring-boot.version>`  
    `<flink.version>2.2.0</flink.version>`  
    `<aviator.version>5.4.1</aviator.version>`  
    `<questdb-client.version>1.0.1</questdb-client.version>`  
`</properties>`

`<dependencies>`  
    `<dependency>`  
        `<groupId>org.springframework.boot</groupId>`  
        `<artifactId>spring-boot-starter-web</artifactId>`  
    `</dependency>`  
    `<dependency>`  
        `<groupId>org.springframework.boot</groupId>`  
        `<artifactId>spring-boot-starter-data-jpa</artifactId>`  
    `</dependency>`  
    `<dependency>`  
        `<groupId>org.postgresql</groupId>`  
        `<artifactId>postgresql</artifactId>`  
        `<scope>runtime</scope>`  
    `</dependency>`

    `<dependency>`  
        `<groupId>org.questdb</groupId>`  
        `<artifactId>questdb-client</artifactId>`  
        `<version>${questdb-client.version}</version>`  
    `</dependency>`

    `<dependency>`  
        `<groupId>org.apache.flink</groupId>`  
        `<artifactId>flink-streaming-java</artifactId>`  
        `<version>${flink.version}</version>`  
        `<scope>provided</scope>`   
    `</dependency>`

    `<dependency>`  
        `<groupId>com.googlecode.aviator</groupId>`  
        `<artifactId>aviator</artifactId>`  
        `<version>${aviator.version}</version>`  
    `</dependency>`  
      
    `<dependency>`  
        `<groupId>org.springframework.boot</groupId>`  
        `<artifactId>spring-boot-starter-test</artifactId>`  
        `<scope>test</scope>`  
    `</dependency>`  
    `<dependency>`  
        `<groupId>org.testcontainers</groupId>`  
        `<artifactId>junit-jupiter</artifactId>`  
        `<scope>test</scope>`  
    `</dependency>`  
`</dependencies>`

### **6.2. QuestDB ILP 기반 고속 수집 클라이언트 구현**

고주파수 센서 데이터나 Flink 연산 결과를 QuestDB에 초당 수백만 건씩 저장할 때는 JPA를 배제하고 공식 ILP 클라이언트를 사용하여 비동기/배치 방식으로 데이터를 밀어 넣어야 한다.  
`import io.questdb.client.Sender;`  
`import org.springframework.beans.factory.annotation.Value;`  
`import org.springframework.stereotype.Service;`  
`import jakarta.annotation.PreDestroy;`

`@Service`  
`public class QuestDBIngestionService {`

    `private final Sender sender;`

    `public QuestDBIngestionService(@Value("${questdb.ilp.url:http::addr=localhost:9000;}") String url) {`  
        `// ILP over HTTP를 통해 안전하고 빠른 데이터 전송 파이프라인 수립`  
        `this.sender = Sender.fromConfig(url);`  
    `}`

    `public void ingestSensorData(String sensorId, double value, long timestampNano) {`  
        `sender.table("sensor_data")`  
            `.symbol("sensor_id", sensorId)`  
            `.doubleColumn("value", value)`  
            `.at(timestampNano);`  
    `}`  
      
    `@PreDestroy`  
    `public void close() {`  
        `if (sender!= null) {`  
            `sender.close();`  
        `}`  
    `}`  
`}`

### **6.3. Flink 스트리밍 평가 파이프라인 구현**

Flink 파이프라인은 앞서 설명한 KeyedBroadcastProcessFunction을 활용하여 메모리 폭발 없이 동적 연산을 처리하고 그 결과를 QuestDB Sink로 전달한다.  
`import org.apache.flink.api.common.state.*;`  
`import org.apache.flink.api.common.typeinfo.Types;`  
`import org.apache.flink.streaming.api.datastream.*;`  
`import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;`  
`import org.apache.flink.streaming.api.functions.co.KeyedBroadcastProcessFunction;`  
`import org.apache.flink.util.Collector;`

`import java.util.ArrayList;`  
`import java.util.List;`  
`import java.util.Map;`

`public class StreamingEvaluationJob {`

    `public static void main(String args) throws Exception {`  
        `StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();`  
          
        `MapStateDescriptor<String, DynamicRule> ruleStateDescriptor =`   
            `new MapStateDescriptor<>("RulesBroadcastState", Types.STRING, Types.POJO(DynamicRule.class));`

        `DataStream<SensorData> sensorStream = env.addSource(new KafkaSensorSource());`  
        `DataStream<DynamicRule> ruleStream = env.addSource(new KafkaRuleSource());`

        `BroadcastStream<DynamicRule> broadcastRules = ruleStream.broadcast(ruleStateDescriptor);`

        `sensorStream`  
         `.keyBy(SensorData::getSensorId)`  
         `.connect(broadcastRules)`  
         `.process(new BroadcastFormulaEvaluator(ruleStateDescriptor))`  
         `.addSink(new QuestDBILPSink()); // ILP 기반의 고속 Sink 구현체 사용`

        `env.execute("대규모 센서 듀얼 패스 실시간 평가");`  
    `}`

    `// BroadcastFormulaEvaluator 내부 구현은 이전 아키텍처 설계와 동일하게 상태 버퍼 및 타이머 적용`  
`}`

### **6.4. Podman Compose 및 Testcontainers를 활용한 통합 테스트 구성**

개발 및 CI/CD 파이프라인에서 QuestDB 최신 버전(9.3.4)과 연동되는 Spring Boot 애플리케이션 로직을 완벽하게 검증하기 위해 podman-compose 설정과 Testcontainers를 활용한 통합 테스트를 구성한다.  
먼저, 프로젝트 루트에 헬스체크(Healthcheck)가 포함된 podman-compose.yml 파일을 작성한다.  
`# podman-compose.yml`  
`version: "3.8"`  
`services:`  
  `questdb:`  
    `image: questdb/questdb:9.3.4 # 최신 9.3.4 이미지 지정`  
    `ports:`  
      `- "8812:8812" # PostgreSQL Wire`  
      `- "9000:9000" # HTTP (Web Console & ILP HTTP)`  
      `- "9009:9009" # ILP TCP`  
      `- "9003:9003" # Metrics/Health`  
    `environment:`  
      `- QDB_METRICS_ENABLED=true`  
      `- QDB_HTTP_MIN_ENABLED=true`  
    `healthcheck:`  
      `test:`  
      `interval: 10s`  
      `timeout: 5s`  
      `retries: 5`  
      `start_period: 10s`

이후, Spring Boot 통합 테스트 코드에서 Testcontainers의 DockerComposeContainer 모듈을 이용하여 컨테이너 라이프사이클을 테스트와 동기화한다.  
`import org.junit.jupiter.api.Test;`  
`import org.springframework.beans.factory.annotation.Autowired;`  
`import org.springframework.boot.test.context.SpringBootTest;`  
`import org.springframework.jdbc.core.JdbcTemplate;`  
`import org.testcontainers.containers.DockerComposeContainer;`  
`import org.testcontainers.containers.wait.strategy.Wait;`  
`import org.testcontainers.junit.jupiter.Container;`  
`import org.testcontainers.junit.jupiter.Testcontainers;`

`import java.io.File;`  
`import static org.assertj.core.api.Assertions.assertThat;`

`@Testcontainers`  
`@SpringBootTest`  
`class QuestDBIntegrationTest {`

    `// podman-compose 파일을 읽어들여 테스트 컨테이너 실행 및 대기`  
    `@Container`  
    `public static DockerComposeContainer<?> environment =`  
        `new DockerComposeContainer<>(new File("podman-compose.yml"))`  
          `.withExposedService("questdb", 8812, Wait.forListeningPort())`  
          `.withExposedService("questdb", 9000, Wait.forHttp("/").forStatusCode(200));`

    `@Autowired`  
    `private QuestDBIngestionService ingestionService;`

    `@Autowired`  
    `private JdbcTemplate jdbcTemplate;`

    `@Test`  
    `void testSensorDataIngestionAndQuery() throws InterruptedException {`  
        `// 1. ILP를 통한 고속 데이터 삽입`  
        `ingestionService.ingestSensorData("SENSOR_TEST_1", 45.5, System.currentTimeMillis() * 1_000_000L);`  
          
        `// 데이터가 디스크에 플러시(Flush)될 때까지 약간의 대기 (QuestDB 특성)`  
        `Thread.sleep(1500);`

        `// 2. PGWire(JDBC)를 통한 데이터 조회 및 검증`  
        `Integer count = jdbcTemplate.queryForObject(`  
                `"SELECT count() FROM sensor_data WHERE sensor_id = 'SENSOR_TEST_1'",`   
                `Integer.class`  
        `);`  
          
        `assertThat(count).isGreaterThan(0);`  
    `}`  
`}`

## **7\. 대규모 상태 관리 및 성능 튜닝 전략**

### **7.1. Flink 2.2 RocksDB 8.10.0 상태 백엔드 최적화**

수십만 개의 수식과 센서 버퍼를 관리하기 위해서는 디스크 기반의 RocksDB를 상태 백엔드로 사용해야 한다. Flink 2.2 버전부터는 내장 RocksDB가 8.10.0 버전으로 대폭 업그레이드되어 I/O 성능이 비약적으로 향상되었다. 또한 Kryo 라이브러리가 5.6 버전으로 업데이트되어 직렬화 속도가 개선되었으므로, 이를 적극 활용하여 상태 저장에 따른 오버헤드를 최소화할 수 있다. Flink 2.0+ 버전의 '통합 파일 병합 메커니즘'을 활성화하면 분산 시스템 내 작은 파일들이 병합되어 메타데이터 부하도 현저히 낮출 수 있다.

### **7.2. QuestDB ILP 배치 최적화 및 보존 정책**

QuestDB로의 수집 시 성능과 안정성을 극대화하기 위해, ILP HTTP 커넥터의 자동 플러시 주기(auto\_flush\_interval) 및 버퍼(auto\_flush\_rows)를 환경에 맞게 조정하여 네트워크 오버헤드를 줄여야 한다. 또한 무한정 증식하는 파생 데이터를 통제하기 위해 QuestDB의 파티션 삭제(Drop Partition) 기능을 스케줄러와 결합, 과거 데이터 파티션을 주기적으로 비워 스토리지 낭비를 방지한다.

## **8\. 결론**

1초 간격의 원본 센서 데이터를 처리하면서 수십만 개의 사용자 정의 수식 및 윈도우 함수를 실시간으로 평가해야 하는 시스템은 단일 기술 스택으로는 해결할 수 없는 복합적인 과제이다. 본 시스템은 이를 해결하기 위해 최신 기술 버전의 이점을 온전히 끌어낸 듀얼 패스(Dual-Path) 평가 구조를 채택하였다.  
'스트리밍 경로'에서는 최신 Apache Flink 2.2의 Materialized Tables와 향상된 RocksDB 8.10.0 상태 백엔드를 결합하여, 메모리 폭발 없이 동적 연산을 안전하게 처리한다. 동시에 '실시간 조회 경로'에서는 인메모리급 삽입 성능과 WINDOW JOIN, 벡터화된 GROUP BY 확장을 통해 쿼리 처리량을 대폭 끌어올린 QuestDB 9.3.4를 도입하여 고빈도 온디맨드 계산의 병목을 완벽히 타개하였다.  
이 아키텍처는 고성능 JVM 표현식 엔진인 AviatorScript를 양 경로에 플러그인 형태로 주입하여 유연하고 일관된 룰 처리를 보장한다. 최신 Spring Boot 3.4 환경 위에서 podman-compose 및 Testcontainers로 신뢰성 있는 통합 테스트 생태계까지 구축된 이 설계는 극한의 트래픽을 감당하는 산업 표준 IoT 처리 시스템의 모범 사례가 될 것이다.