# 📐 Coding Standards (`CODING_STANDARDS.md`)

본 문서는 코드 리뷰(`/code-review`) 및 아키텍처 변경 시 준수해야 하는 모듈 경계, 도메인 어휘, 보안 및 테스트 불변식을 정의합니다.

---

## 1. 도메인 어휘 및 센서 일련번호 규약 ([`GLOSSARY.md`](GLOSSARY.md))

* **용어 일관성**: 식별자 변환·바인딩 메서드와 변수명은 [`GLOSSARY.md`](GLOSSARY.md)에 정의된 **Canonical Serial ID**(`"10001"`)와 **Prefixed Serial ID**(`"P_10001"`, 하위 호환: `"SN_10001"`) 구분을 엄격히 따릅니다.
  - 정규 일련번호 변환: `toCanonicalSerialId`
  - 접두사 일련번호 변환: `toPrefixedSerialId`, `toPrefixedOrCanonicalSerialId`
  - 평가 환경 양방향 바인딩: `SensorSerialResolver.bindSerialIdentifiers(env, sensorId, value)`
* **단일 정규화 키 상태 관리**:
  - Flink `BroadcastState`(`RuleBroadcastProcessFunction.RULE_STATE_DESCRIPTOR`)는 반드시 `DynamicRule.canonicalTargetSerialId()`가 반환하는 **단일 정규화 키(`"10001"`)** 하나로만 인덱싱합니다. `"10001"`과 `"P_10001"`을 중복 저장하지 않습니다.
* **`formula-core` 순수성**:
  - `formula-core`는 프레임워크 및 로거 의존성(`org.springframework.*`, `org.slf4j.*`, `lombok.extern.*`)을 일절 포함하지 않습니다 (`./gradlew :formula-core:verifyPureDomain`에 의해 강제됨).
  - [`DeviceSensorKey`](formula-core/src/main/java/com/imoil/formula/domain/DeviceSensorKey.java)는 생성 및 변경 시점에 `deviceId`를 대문자/트림 정규화하여 `equals`/`hashCode`가 추가 문자열 할당 없이 동작하도록 유지합니다.

---

## 2. 수식 엔진 및 윈도우 평가 캡슐화 (`formula-engine`)

* **단일 평가 진입점 (`RuleEngineService`)**:
  - 단건 스트리밍 평가는 [`RuleEngineService.evaluatePoint(expression, env, windowHistory)`](formula-engine/src/main/java/com/imoil/formula/engine/RuleEngineService.java)를, 시계열 배치 평가는 [`RuleEngineService.evaluateSeries(expression, series)`](formula-engine/src/main/java/com/imoil/formula/engine/RuleEngineService.java)를 호출합니다.
  - 호출자(`OnDemandEvaluationService`, `RuleBroadcastProcessFunction`)에서 슬라이딩 윈도우 `LinkedList` 루프나 AviatorScript `execute` 호출을 중복 구현하지 않습니다.
* **윈도우 히스토리 키 캡슐화 (`AbstractWindowFunction`)**:
  - `"window_data"` 매직 스트링을 `AbstractWindowFunction` 외부에서 직접 참조하거나 `env.put("window_data", ...)`로 바인딩하지 않습니다. 반드시 `AbstractWindowFunction.bindWindowHistory` / `extractWindowHistory`를 통해서만 접근합니다.
* **보안 샌드박스**:
  - 모든 수식은 평가 또는 브로드캐스트 등록 전에 [`RuleEngineService.validateOnce`](formula-engine/src/main/java/com/imoil/formula/engine/RuleEngineService.java)(내부 [`ExpressionValidator.validate`](formula-engine/src/main/java/com/imoil/formula/engine/security/ExpressionValidator.java))를 통과해야 합니다.
  - `AviatorEvaluatorInstance`는 반드시 [`RuleEngineConfig.createDefaultEvaluator()`](formula-engine/src/main/java/com/imoil/formula/engine/config/RuleEngineConfig.java)로 생성하여 `Options.ALLOWED_CLASS_SET = Collections.emptySet()`, `MAX_LOOP_COUNT = 1000`, 커스텀 윈도우 함수 등록 구성을 공유합니다.

---

## 3. 데이터소스 어댑터 및 영속성 경계 (`formula-api`, `formula-flink`)

* **QuestDB 쓰기 포트 (`SensorDataWriter`)**:
  - QuestDB ILP 적재는 [`SensorDataWriter.forQuestDbSender(Sender)`](formula-core/src/main/java/com/imoil/formula/domain/SensorDataWriter.java) 어댑터를 사용하며, Mock 환경에서는 `SensorDataWriter.noOp()`을 주입합니다 (`java.lang.reflect.Proxy` 기반 동적 프록시 사용 금지).
* **QuestDB 읽기 및 SQL 인젝션 방어 (`QuestDBDataSourceAdapter`)**:
  - QuestDB PGWire 조회 쿼리, `SAMPLE BY` 정규식 검증(`^[1-9][0-9]*(?:s|m|h|d|w|ms|M|y)$`), `ResultSet` 매핑은 [`QuestDBDataSourceAdapter`](formula-api/src/main/java/com/imoil/formula/datasource/QuestDBDataSourceAdapter.java) 내부에 응집하여 유지합니다.
* **카테고리 및 센서 타입 기본값 응집**:
  - 센서 타입(`PHYSICAL`, `FORMULA`)별 기본 카테고리·단위는 [`SensorType`](formula-api/src/main/java/com/imoil/formula/registry/domain/SensorType.java) 메서드로, 카테고리별 시뮬레이션 파형 파라미터는 [`MockSensorCatalogService.CATEGORY_SPECS`](formula-api/src/main/java/com/imoil/formula/service/MockSensorCatalogService.java) 단일 테이블로 관리하며, 중복 `switch`/`if` 분기를 추가하지 않습니다.

---

## 4. 프론트엔드 단방향 반응형 스토어 및 통합 센서 레지스트리 (`formula-ui`)

* **단방향 카탈로그 및 수식 파생**:
  - [`sensorRegistryStore.ts`](formula-ui/src/stores/sensorRegistryStore.ts)는 오직 레지스트리 CRUD(물리 센서 및 수식 센서 생성/수정/복제/삭제) 및 Flink 룰 동기화만 수행하며, `sensorStore`나 `formulaStore`를 import하거나 수동 동기화 함수를 호출하지 않습니다.
  - 통합 센서 카탈로그는 [`sensorStore.ts`](formula-ui/src/stores/sensorStore.ts)의 반응형 `computed` 프로퍼티(`sensors`)에서, 수식 목록은 [`formulaStore.ts`](formula-ui/src/stores/formulaStore.ts)의 반응형 `computed` 프로퍼티(`formulas`)에서 각각 `sensorRegistryStore.sensors`를 병합·파생합니다.
* **통합 센서·수식 레지스트리 UI (`SensorManagement.vue`)**:
  - 모든 물리 센서와 수식 센서는 별도 탭으로 분리하지 않고 [`SensorManagement.vue`](formula-ui/src/components/SensorManagement.vue) (**Unified Sensor Registry**)에서 통합 관리합니다.
* **원클릭 시뮬레이션 조정**:
  - 레지스트리 센서의 시뮬레이션 준비(수식 보장, 센서 선택, 표현식 바인딩)는 [`simulationStore.prepareRegistrySensorSimulation`](formula-ui/src/stores/simulationStore.ts) 단일 액션에서 처리하며, UI 컴포넌트(`App.vue`, `SensorManagement.vue`)는 탭 전환만 수행합니다.

---

## 5. 테스트 규약

* **Flink Broadcast State 테스트**:
  - `ProcessFunctionTestHarnesses.forKeyedBroadcastProcessFunction` 호출 시 4번째 인자로 반드시 `RuleBroadcastProcessFunction.RULE_STATE_DESCRIPTOR`를 전달합니다.
