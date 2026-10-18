# 🚀 대규모 IoT 시계열 데이터 평가 시스템 (Development Harness)

## 1. 프로젝트 개요 및 모듈 구조

1초 간격으로 유입되는 대규모 IoT 센서 데이터를 수집하고, 동적 수식(Formula)과 슬라이딩 윈도우 함수(`window_avg`, `window_max`)를 실시간 스트리밍(Flink)과 온디멘드(Spring Boot API + Vue UI) 듀얼 패스로 평가하는 분산 처리 시스템입니다.

```
formula-evaluator-service/
├── formula-core/      # 순수 Java 도메인 모델 (SensorData, DynamicRule, DeviceSensorKey, SensorSerialResolver, SensorDataWriter)
├── formula-engine/    # AviatorScript 샌드박스 엔진 (RuleEngineService, RuleEngineConfig, ExpressionValidator, AbstractWindowFunction)
├── formula-api/       # Spring Boot 웹 서비스, Oracle 19c 센서 레지스트리(SSOT), QuestDB 어댑터, 온디멘드 평가 및 Kafka 룰 발행
├── formula-flink/     # Flink 스트리밍 잡, Kafka 역직렬화 일련번호 매핑, RuleBroadcastProcessFunction, QuestDbIlpSink
├── formula-emulator/  # 대규모 IoT 장치 센서 에뮬레이터 (JSON 배열 배치 Kafka 송신)
└── formula-ui/        # Vue 3 + Vuetify 4 + Pinia SPA, 통합 센서 레지스트리 관리 및 시계열 시뮬레이터
```

* 기술 스택 및 의존성 버전의 단일 진실 공급원(SSOT)은 [`gradle/libs.versions.toml`](gradle/libs.versions.toml) 및 [`formula-ui/package.json`](formula-ui/package.json)입니다.

---

## 2. 컨텍스트 포인터 (Context Pointers)

작업 성격에 따라 아래 문서를 먼저 조회하십시오:

* **도메인 용어·식별자 변환(`10001` vs `SN_10001`)·메서드 명명 시** → [`GLOSSARY.md`](GLOSSARY.md)
* **코드 리뷰(`/code-review`)·모듈 경계·보안·테스트 불변식 확인 시** → [`CODING_STANDARDS.md`](CODING_STANDARDS.md)
* **전체 아키텍처·듀얼 패스 설계·DB 스키마 파악 시** → [`ARCHITECTURE_GUIDE.md`](ARCHITECTURE_GUIDE.md)
* **Flink 스트리밍 파이프라인·BroadcastState 상세 수정 시** → [`docs/STREAMING_EVALUATOR_GUIDE.md`](docs/STREAMING_EVALUATOR_GUIDE.md)
* **관리자 동적 룰 발행·Flink 일괄 동기화(Bulk Sync) 수정 시** → [`docs/HANDOFF_DYNAMIC_RULE_SYNC.md`](docs/HANDOFF_DYNAMIC_RULE_SYNC.md)
* **프론트엔드 UI·Pinia 스토어 수정 시** → [`docs/FRONTEND_UI_GUIDE.md`](docs/FRONTEND_UI_GUIDE.md)
* **IoT 에뮬레이터·로컬 엔드투엔드 파이프라인 실행 시** → [`docs/SENSOR_SIMULATION_AND_PIPELINE_GUIDE.md`](docs/SENSOR_SIMULATION_AND_PIPELINE_GUIDE.md)

---

## 3. 핵심 아키텍처 불변식 (Core Invariants)

1. **단일 일련번호(Serial Number) 체계 (`10001+`)**:
   - 물리 센서(`(deviceId, sensorIndex)`)와 파생 수식 센서는 Oracle 19c `SENSOR_REGISTRY`(`SENSOR_SERIAL_SEQ`, 10001+)에서 전역 고유 일련번호를 부여받습니다.
   - [`DeviceSensorRecordDeserializer`](formula-flink/src/main/java/com/imoil/formula/flink/DeviceSensorRecordDeserializer.java)는 수신 즉시 [`SensorSerialResolver`](formula-core/src/main/java/com/imoil/formula/domain/SensorSerialResolver.java)로 일련번호를 매핑하여 `keyBy(SensorData::getSensorId)` 시 장치 간 키 충돌을 원천 차단합니다.
   - [`RuleBroadcastProcessFunction`](formula-flink/src/main/java/com/imoil/formula/flink/RuleBroadcastProcessFunction.java)은 정규 일련번호(`DynamicRule.canonicalTargetSerialId()`, 예: `"10001"`) 단일 키로 `BroadcastState`를 관리하고, 평가 시 [`SensorSerialResolver.bindSerialIdentifiers`](formula-core/src/main/java/com/imoil/formula/domain/SensorSerialResolver.java)로 `"10001"`과 `"SN_10001"`을 양방향 바인딩합니다.
2. **수식 평가 및 보안 샌드박스 단일화**:
   - 모든 수식 검증과 평가는 [`RuleEngineService`](formula-engine/src/main/java/com/imoil/formula/engine/RuleEngineService.java)(`validateOnce`, `evaluatePoint`, `evaluateSeries`, `usesWindow`)와 [`RuleEngineConfig.createDefaultEvaluator()`](formula-engine/src/main/java/com/imoil/formula/engine/config/RuleEngineConfig.java)를 경유합니다.
   - 윈도우 히스토리 바인딩은 [`AbstractWindowFunction.bindWindowHistory`](formula-engine/src/main/java/com/imoil/formula/engine/functions/AbstractWindowFunction.java) 내부로 캡슐화합니다.
3. **데이터소스 I/O 경계**:
   - QuestDB ILP 적재는 [`SensorDataWriter.forQuestDbSender(Sender)`](formula-core/src/main/java/com/imoil/formula/domain/SensorDataWriter.java)(Mock 시 `SensorDataWriter.noOp()`)를 사용합니다.
   - QuestDB PGWire 조회 및 `SAMPLE BY` 정규식 검증(`^[1-9][0-9]*(?:s|m|h|d|w|ms|M|y)$`)은 [`QuestDBDataSourceAdapter`](formula-api/src/main/java/com/imoil/formula/datasource/QuestDBDataSourceAdapter.java)에서 수행합니다.
4. **프론트엔드 단방향 상태 파생 및 통합 센서 레지스트리**:
   - 물리 센서(`PHYSICAL`)와 파생 수식 센서(`FORMULA`)는 [`SensorManagement.vue`](formula-ui/src/components/SensorManagement.vue) (**Unified Sensor Registry**) 단일 화면에서 통합 관리됩니다.
   - [`sensorStore.ts`](formula-ui/src/stores/sensorStore.ts)의 `sensors`와 [`formulaStore.ts`](formula-ui/src/stores/formulaStore.ts)의 `formulas`는 모두 [`sensorRegistryStore.ts`](formula-ui/src/stores/sensorRegistryStore.ts)를 반응형 `computed`로 파생하며, 레지스트리 시뮬레이션 전환은 [`simulationStore.prepareRegistrySensorSimulation`](formula-ui/src/stores/simulationStore.ts) 단일 진입점을 사용합니다.

---

## 4. 검증 명령어 (Verification Commands)

* **백엔드 전체 검증 (`verifyPureDomain` 포함)**: `./gradlew check --no-daemon`
* **프론트엔드 타입 체크 및 테스트**: `pnpm --dir formula-ui typecheck && pnpm --dir formula-ui test`
