# formula-ui: 대규모 IoT 센서 레지스트리 및 수식 시뮬레이터 UI

Vite 8, Vue 3, TypeScript, Vuetify 4, Apache ECharts 6.1 기반의 고성능 대규모 IoT 센서 관리 및 시계열 수식 시뮬레이션 프론트엔드 애플리케이션입니다.

---

## 🌟 주요 기능 (Key Features)

### 1. 단일 센서 레지스트리 관리 (`SensorManagement.vue`)
- **통합 센서 체계 (Unified Serial Numbering)**:
  - 물리 IoT 센서(`PHYSICAL`) 및 계산된 파생 수식 센서(`FORMULA`)를 단일 일련번호(`10001+`) 체계로 일관되게 관리.
  - 오라클 19c 백엔드 REST API(`/api/v1/registry/sensors`) 연동 CRUD (추가, 수정, 삭제, 상세 조회).
- **IoT 장치 센서 일괄 동기화 (Batch Sync)**:
  - 에뮬레이터에서 전송하는 특정 장치(예: `DEVICE-001`)의 1,000개 센서(`0 ~ 999`)를 원클릭으로 스캔하여 고유 일련번호를 자동 채번 등록.
- **온디멘드 시뮬레이션 즉시 연계 (One-Click On-Demand Jump)**:
  - 수식 센서 목록에서 **"⚡ Simulation"** 버튼 클릭 시, 해당 센서의 일련번호와 등록된 AviatorScript 수식이 자동으로 시뮬레이터 탭(`FormulaSimulation.vue`)으로 바인딩되어 즉시 파형과 연산 결과를 확인할 수 있습니다.

### 2. 고성능 86,400초 수식 시뮬레이션 (`FormulaSimulation.vue`)
- **초고속 시계열 버퍼링**:
  - 표준 자바스크립트 배열의 `shift()` 오버헤드를 배제하고, `Float64Array` 기반 고정 크기 `CircularBuffer`를 사용하여 브라우저에서 24시간(86,400초) 시계열 데이터를 렉(Lag) 없이 실시간 렌더링.
- **다단 합성 수식 DAG 지원**:
  - 기본 센서의 연산 결과를 다음 수식의 입력 변수로 사용하는 다단계 의존성 수식 실시간 평가.
- **대화형 시각화**:
  - Apache ECharts 6.1을 탑재하여 줌(DataZoom), 툴팁, 시리즈 토글 등 고반응성 차트 지원.

---

## 🏗️ 상태 관리 아키텍처 (State Management)

Pinia를 기반으로 도메인별 스토어가 관심사에 따라 분리되어 있습니다.

| 스토어 | 파일 경로 | 주요 역할 |
| :--- | :--- | :--- |
| `useSensorRegistryStore` | `src/stores/sensorRegistryStore.ts` | 오라클 19c 센서 레지스트리 CRUD, 장치 일괄 동기화, 검색/필터링 |
| `useSensorStore` | `src/stores/sensorStore.ts` | 시뮬레이션 대상 활성 센서 선택, 시계열 데이터 캐싱 |
| `useFormulaStore` | `src/stores/formulaStore.ts` | 작성 중인 수식 목록, 검증 상태, DAG 종속성 트리 관리 |

---

## 🛠️ 개발 및 빌드 (Development & Build)

### 요구 사양
- Node.js 22 LTS 이상
- pnpm 12 이상

### 명령어
```bash
# 의존성 설치
pnpm install

# 로컬 개발 서버 기동 (http://localhost:3000)
pnpm dev

# 단위 및 통합 테스트 실행 (Vitest)
pnpm test

# 타입 검사 및 프로덕션 빌드
pnpm build
```

---

## 🧪 테스트 구성 (Testing)
- `src/stores/__tests__/sensorRegistryStore.test.ts`: 센서 등록, 수정, 삭제, 일괄 동기화 단위 테스트
- `src/stores/__tests__/sensorRegistryIntegration.test.ts`: 센서 레지스트리에서 시뮬레이터로의 데이터 연계 통합 테스트
