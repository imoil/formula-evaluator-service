# 📋 [Hand-off Document] 관리자 동적 룰(Dynamic Rule) 발행 및 일괄 동기화 구현

## 1. 개요 및 배경 (Context & Decision)

### 1.1 배경 및 문제 정의
* 현재 `formula-flink`의 `StreamingEvaluatorJob`은 카프카의 `rule-data` 토픽을 `OffsetsInitializer.latest()`로 소비하고 있습니다.
* 수식 센서 메타데이터와 수식 표현식(`formulaExpression`)은 Oracle 19c `SENSOR_REGISTRY`에 영속화(SSOT)되지만, 현재 `formula-api`에서 카프카 `rule-data` 토픽으로의 자동 발행 로직이 누락되어 있습니다.
* 따라서 서비스를 전체 재시작하거나 초기화할 경우, Flink 클러스터 메모리의 `BroadcastState`가 비어 있어 실시간 수식 평가가 동작하지 않는 문제가 발생합니다.

### 1.2 아키텍처 결정 사항 (Architectural Decision)
* **Flink 오프셋 정책 유지**: Flink 컨슈머는 **`OffsetsInitializer.latest()`를 그대로 유지**합니다.
  - *결정 사유*: `earliest`를 적용할 경우, 과거 수십 차례 수정된 동일 센서의 구버전 수식들이 불필요하게 반복 재생(Replay Churn)되어 상태와 메트릭이 왜곡되기 때문입니다.
* **해결 원리**: 단일 진실 공급원(SSOT)인 **Oracle DB의 최신 활성 수식 센서 목록을 기준으로 카프카 `rule-data` 토픽에 최신 수식만 일괄 동기화(Bulk Synchronization)**하여 Flink 메모리를 갱신합니다.

---

## 2. 모듈별 상세 개발 요구사항 (Task Specifications)

### 2.1 `formula-api` (백엔드)

1. **Spring Kafka 프로듀서 연동**:
   - `build.gradle`에 `org.springframework.kafka:spring-kafka` 의존성 확인/추가.
   - 카프카 토픽 설정: `rule-data` (기본값: `rule-data`, `bootstrap-servers: localhost:9092`).
2. **동적 룰 발행 서비스 (`RulePublisherService`) 신설**:
   - `publishRule(DynamicRule rule)`: 단일 룰 JSON 직렬화 후 `rule-data` 토픽으로 전송 (`key = rule.getTargetSensorId()`).
   - `publishFormulaSensor(SensorRegistryEntity entity)`: `SensorRegistryEntity`를 `DynamicRule` 객체로 변환하여 발행.
     - `targetSensorId`: 수식의 대상 센서 일련번호 (물리 센서 번호 또는 입력 센서 번호)
     - `expression`: `entity.getFormulaExpression()`
     - `derivedSensorId`: `String.valueOf(entity.getSerialNumber())`
     - `usesWindow`: 표현식에 `window_` 또는 `previous_` 포함 여부 검사
3. **센서 등록/수정 시 실시간 룰 발행 연계**:
   - `SensorRegistryService.createSensor()`: `sensorType == FORMULA`일 경우 DB 저장 후 룰 자동 발행.
   - `SensorRegistryService.updateSensor()`: 수식이 변경되었을 경우 DB 갱신 후 룰 자동 갱신 발행.
   - `SensorRegistryService.deleteSensor()`: 센서 삭제/비활성화 시 룰 무효화 처리(선택 사항).
4. **일괄 동기화 REST API 신설**:
   - **엔드포인트**: `POST /api/v1/registry/sensors/rules/sync` (및 `/rules/sync`)
   - **동작**:
     - `sensorRegistryRepository.findBySensorTypeAndStatus(SensorType.FORMULA, SensorStatus.ACTIVE)` 조회.
     - 조회된 모든 활성 수식 센서를 `DynamicRule`로 변환하여 순차적으로 카프카 `rule-data` 토픽으로 발행.
     - 응답: `{ "syncedCount": N, "message": "Successfully synchronized N rules to Kafka" }`.
5. **서버 시작 시 자동 동기화 (옵션)**:
   - `@EventListener(ApplicationReadyEvent.class)` 및 설정 프로퍼티(`formula.rules.sync-on-startup=true`, 기본 활성화).
   - 백엔드가 재시작될 때 DB의 최신 수식을 카프카로 자동 1회 발행하여 Flink와 동기화 보장.

---

### 2.2 `formula-ui` (프론트엔드)

1. **API 클라이언트 확장 (`sensorRegistryApi.ts`)**:
   ```typescript
   export const syncRulesToFlink = async (): Promise<{ syncedCount: number; message: string }> => {
     const response = await fetch(`${API_BASE_URL}/sensors/rules/sync`, { method: 'POST', ... });
     return response.json();
   };
   ```
2. **스토어 액션 추가 (`sensorRegistryStore.ts`)**:
   - `syncRulesToFlink()` 액션 구현: 로딩 상태(`syncingRules`) 및 스낵바 알림 처리.
3. **UI 버튼 배치 (`SensorManagement.vue`)**:
   - 상단 헤더 툴바(IoT 장치 일괄 등록 버튼 옆)에 **"🔄 Sync Rules to Flink" (Flink 룰 일괄 동기화)** 버튼 추가.
   - 원클릭 시 로딩 인디케이터 표시 후 "성공적으로 N개의 룰이 Flink로 동기화되었습니다" 안내.

---

### 2.3 `formula-flink` (스트리밍 잡 검증)

1. `StreamingEvaluatorJob`:
   - `OffsetsInitializer.latest()` 유지.
   - 백엔드에서 `POST /api/v1/registry/sensors/rules/sync` 호출 시, `RuleBroadcastProcessFunction`의 `processBroadcastElement`가 정상 호출되어 메모리(`BroadcastState`)에 등록되는지 검증.
2. 테스트 하네스 검증:
   - 복수 개의 룰이 브로드캐스트로 유입될 때 각 센서별로 독립적으로 올바른 수식이 적용되는지 단위 테스트 수행 (`testBulkDynamicRuleBroadcastAndEvaluation`).

---

## 3. 검증 시나리오 (Verification Checklist)

- [x] **단위 테스트**: `formula-api`의 `RulePublisherServiceTest` 및 `SensorRegistryControllerTest` 검증 완료.
- [x] **UI 단위 테스트**: `formula-ui`의 `sensorRegistryStore.test.ts`에 룰 동기화 테스트 추가 및 통과.
- [x] **스트리밍 잡 단위/하네스 테스트**: `formula-flink`의 `RuleBroadcastProcessFunctionTest`에서 일괄 브로드캐스트 독립 평가 검증 완료.
- [x] **통합 시나리오 (End-to-End)**:
  1. 전체 서비스 재기동 (DB 데이터는 보존된 상태).
  2. UI에서 "🔄 Sync Rules to Flink" 버튼 클릭 (또는 백엔드 시작 시 자동 호출).
  3. 에뮬레이터에서 센서 데이터 유입 시, Flink가 재기동 전 DB에 있던 수식대로 계산하여 QuestDB에 적재하는 메커니즘 확인.
  4. UI에서 수식을 수정했을 때 Flink 잡 재시작 없이 수식 결과가 즉시 변경되는 실시간성 확인.
