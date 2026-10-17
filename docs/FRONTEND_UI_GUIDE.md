# 📊 Formula UI 사용자 및 아키텍처 가이드 (Frontend UI Guide)

본 문서는 **Formula Evaluator Service**의 온디멘드 요청(On-Demand Path) 및 센서 마스터 관리를 담당하는 프론트엔드 모듈인 **`formula-ui`**의 아키텍처 구성, 단일 센서 일련번호 체계 관리(`SensorManagement.vue`), 온디멘드 수식 시뮬레이션 메커니즘, 백엔드 서비스 연동 규격, 그리고 프론트엔드-백엔드 동시 실행 및 트러블슈팅 방법을 상세히 설명합니다.

---

## 1. 개요 및 기술 스택

`formula-ui`는 대규모 IoT 설비에서 유입되는 시계열 센서 데이터를 온디멘드로 조회하고, 사용자가 정의한 동적 수식(Formula)과 다중 센서 간 복합 연산 및 윈도우 집계 로직을 인터랙티브하게 검증 및 시뮬레이션할 수 있는 Single Page Application (SPA)입니다.

### 🏛️ 시스템 데이터 파이프라인 아키텍처

> [!IMPORTANT]
> **센서 데이터 및 일련번호 단일 진실 공급원 (Single Source of Truth)**:
> - 물리 센서와 수식 센서는 **Oracle 19c `SENSOR_REGISTRY` 테이블 및 `SENSOR_SERIAL_SEQ` (10001번 시작)**를 통해 전역 고유 일련번호(`serialNumber`)를 부여받습니다.
> - `formula-ui`의 `SensorManagement.vue`에서 추가, 수정, 삭제 및 IoT 장치 동기화가 수행되면, 백엔드(`formula-api`) 및 온디멘드 시뮬레이션 카탈로그(`sensorStore`)에 즉시 반영됩니다.

```mermaid
flowchart LR
    subgraph ClientLayer["프론트엔드 (formula-ui :3000)"]
        direction TB
        UI_Registry["SensorManagement.vue (단일 일련번호 관리)"]
        UI_Sim["FormulaSimulation.vue (시뮬레이션 스튜디오)"]
        UI_Editor["FormulaEditor.vue (수식 편집기)"]
        Stores["Pinia 상태 관리 (sensorRegistryStore / sensorStore / simulationStore)"]
        Engine["클라이언트 수식 엔진 (DAG 위상 정렬)"]
        Visualizer["ECharts 6.1 인터랙티브 시각화"]

        UI_Registry <--> Stores
        UI_Registry -->|⚡ On-Demand Simulation 바로가기| UI_Sim
        UI_Sim <--> Stores
        Stores --> Engine
        Engine --> Visualizer
    end

    subgraph BackendLayer["백엔드 (formula-api :8080) - Source of Truth"]
        direction TB
        RegistryController["SensorRegistryController (/api/v1/registry/sensors)"]
        RegistryService["SensorRegistryService (JPA CRUD & 일련번호 채번)"]
        EvalController["OnDemandEvaluationController (/api/v1/evaluate)"]
        SensorController["SensorController (/api/v1/sensors)"]
        Catalog["MockSensorCatalogService (Oracle DB 통합 시계열 원천)"]
        OracleDB[("Oracle 19c SENSOR_REGISTRY")]
        QuestDB[("QuestDB 10.0.1")]

        RegistryController --> RegistryService
        RegistryService <--> OracleDB
        SensorController --> Catalog
        Catalog <--> RegistryService
        EvalController --> QuestDB
    end

    Stores ==>|"1. 센서 등록/수정/삭제/동기화"| RegistryController
    Stores ==>|"2. 센서 카탈로그 & 시계열 조회"| SensorController
    UI_Sim -.->|"3. 서버 온디멘드 평가 검증"| EvalController
```

### 주요 기술 스택
| 구분 | 라이브러리 / 도구 | 버전 | 역할 |
| :--- | :--- | :--- | :--- |
| **Framework** | [Vue.js](https://vuejs.org/) | `3.5.43` | Composition API (`<script setup lang="ts">`) 기반 컴포넌트 개발 |
| **UI Library** | [Vuetify](https://vuetifyjs.com/) | `4.2.4` | Material Design 기반 엔지니어링 테마 대시보드 UI (`v-app`, `v-card`, `v-table`, `v-dialog`, `v-chip`) |
| **Icons** | [@mdi/font](https://materialdesignicons.com/) | `7.4.47` | Material Design Vector Icons (`mdi` 아이콘셋 바인딩) |
| **Bundler** | [Vite](https://vite.dev/) | `8.3.4` | 초고속 HMR 및 프로덕션 롤다운 빌드, 백엔드 API 리버스 프록시 |
| **State Management**| [Pinia](https://pinia.vuejs.org/) | `4.0.3` | 센서 레지스트리(`sensorRegistryStore`), 수식 정의(`formulaStore`), 센서 연동(`sensorStore`), 시뮬레이션 실행(`simulationStore`) |
| **Chart Visualizer**| [Apache ECharts](https://echarts.apache.org/) | `6.1.0` | 3,600초 시계열 인터랙티브 DataZoom 슬라이더 및 멀티 시리즈 렌더링 |
| **Testing** | [Vitest](https://vitest.dev/) | `5.0.3` | 센서 레지스트리 스토어, 수식 엔진, DAG 위상 정렬 단위/통합 테스트 (25개 테스트) |
| **Package Manager** | [pnpm](https://pnpm.io/) | `12.10.1` | 고속 및 디스크 효율적인 모노레포 패키지 관리 |

---

## 2. 모듈 디렉터리 구조

```
formula-ui/
├── src/
│   ├── components/
│   │   ├── SensorManagement.vue       # [신규] 통합 센서 일련번호 관리 (CRUD, IoT 장치 일괄 동기화)
│   │   ├── FormulaList.vue            # 작성 수식 목록 보기 및 관리
│   │   ├── FormulaEditor.vue          # 수식 작성, 수정 및 실시간 DAG 분석 모달
│   │   ├── FormulaSimulation.vue      # 1시간 시계열 결과 시뮬레이션 및 ECharts 차트
│   │   └── SensorDataExplorer.vue     # 백엔드 연동 센서 카탈로그 및 파형 탐색기
│   ├── services/
│   │   ├── sensorRegistryApi.ts       # [신규] Oracle 센서 레지스트리 REST API 클라이언트
│   │   ├── apiClient.ts               # formula-api REST 연동 클라이언트
│   │   ├── circularBuffer.ts          # Float64Array 기반 O(1) 슬라이딩 윈도우 링 버퍼
│   │   ├── formulaEngine.ts           # 토큰 파서, DAG 위상 정렬, 윈도우 함수 JIT 엔진
│   │   └── sensorDataGenerator.ts     # [오프라인/테스트 Fallback] 로컬 시계열 생성기
│   ├── stores/
│   │   ├── sensorRegistryStore.ts     # [신규] 센서 레지스트리 상태 관리 Pinia 스토어
│   │   ├── sensorStore.ts             # 백엔드 formula-api 센서 카탈로그 및 시계열 스토어
│   │   ├── formulaStore.ts            # 수식 정의 CRUD, localStorage 영속화
│   │   └── simulationStore.ts         # 백엔드 센서 스트림 기반 시뮬레이션 조정자
│   ├── test/
│   │   ├── sensorRegistryStore.test.ts # [신규] 레지스트리 Pinia 스토어 CRUD 단위 테스트
│   │   ├── sensorRegistryIntegration.test.ts # [신규] UI-온디멘드 시뮬레이션 통합 테스트
│   │   ├── formulaEngine.test.ts      # 수식 엔진 및 DAG 위상 정렬 Vitest 단위 테스트
│   │   ├── circularBuffer.test.ts     # CircularBuffer 단위 테스트
│   │   ├── formulaStore.test.ts       # Pinia 수식 스토어 CRUD Vitest 단위 테스트
│   │   └── benchmark.test.ts          # 86,400건 시계열 시뮬레이션 성능 벤치마크 테스트
│   ├── types/
│   │   ├── sensorRegistry.ts          # [신규] 센서 레지스트리 DTO 및 엔티티 타입 정의
│   │   ├── formula.ts                 # 수식 및 시뮬레이션 결과 타입 정의
│   │   └── sensor.ts                  # 센서 메타데이터 및 데이터포인트 타입 정의
│   ├── App.vue                        # 네비게이션 툴바, 탭 라우팅, 서버 온라인 상태 배지
│   └── main.ts                        # Vuetify 4 및 MDI Iconset 등록
```

---

## 3. UI 화면별 상세 기능 및 사용 방법

```mermaid
flowchart TD
    App["v-app (전체 레이아웃)"]
    AppBar["v-app-bar (서버 Online 상태 칩, 새 수식 생성 버튼, 다크/라이트 테마 스위치)"]
    Tabs["v-tabs (화면 전환 네비게이션)"]

    Tab0["0. Unified Sensor Registry (단일 센서 일련번호 관리 & 동기화)"]
    Tab1["1. Formula Management (수식 목록 및 관리)"]
    Tab2["2. Simulation Studio (1시간 시계열 결과 시뮬레이션)"]
    Tab3["3. Sensors Explorer (백엔드 formula-api 센서 카탈로그 & 파형 탐색)"]

    App --> AppBar
    App --> Tabs
    Tabs --> Tab0 & Tab1 & Tab2 & Tab3
```

### 화면 0: 통합 센서 일련번호 관리 ([`SensorManagement.vue`](file:///C:/Users/imoil/repo/formula-evaluator-service/formula-ui/src/components/SensorManagement.vue))
* **단일 일련번호 체계 (`serialNumber: 10001+`)**:
  - IoT 장치의 물리 센서(`PHYSICAL`)와 수식 센서(`FORMULA`)가 통일된 일련번호로 관리됩니다.
  - 상단 KPI 통계 요약 카드: 총 등록 센서, 물리 센서, 수식 센서, 연결된 IoT 장치 수 실시간 집계.
* **센서 추가 모달**:
  - 물리 센서: 장치명(예: `DEVICE-001`), 장치 로컬 센서 인덱스(예: `0`), 센서명, 카테고리, 단위 입력.
  - 수식 센서: 일련번호 기반 계산식(예: `'SN_10001 * 0.8 + SN_10002 * 0.2'`) 입력.
  - 다음 채번될 일련번호가 모달 상단에 칩으로 자동 프리뷰(`Next Serial: #10001`).
* **IoT 장치 일괄 동기화 (Sync Device)**:
  - `formula-emulator`의 IoT 장치(예: `DEVICE-001`) 센서 1,000개를 한 번에 등록하여 고유 일련번호를 순차 부여.
  - 기존 등록 센서 건너뛰기(`skipExisting: true`) 옵션 제공.
* **⚡ On-Demand Simulation 원클릭 연계**:
  - 각 센서 행의 번개 모양 아이콘(`⚡`)을 클릭하면 즉시 `FormulaSimulation` 탭으로 전환되며, 해당 센서가 대상 스트림으로 자동 선택되어 시뮬레이션이 실행됩니다.

---

## 4. 백엔드 연동 및 검증 가이드

### UI 테스트 실행
```bash
cd formula-ui

# 25개 단위/통합 테스트 전체 실행 (Vitest)
pnpm test

# 프로덕션 번들 타입 체크 및 빌드
pnpm build
```

---

## 📚 관련 문서
* [시스템 전체 아키텍처 및 상세 엔지니어링 가이드 (ARCHITECTURE_GUIDE.md)](../ARCHITECTURE_GUIDE.md)
* [스트리밍 평가 룰 전파 아키텍처 가이드 (STREAMING_EVALUATOR_GUIDE.md)](STREAMING_EVALUATOR_GUIDE.md)
* [IoT 센서 에뮬레이션 및 엔드투엔드 파이프라인 가이드 (SENSOR_SIMULATION_AND_PIPELINE_GUIDE.md)](SENSOR_SIMULATION_AND_PIPELINE_GUIDE.md)
