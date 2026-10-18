/**
 * 로컬 일시(Local Date Time) 포맷팅 및 조작 유틸리티
 * 
 * 시스템 전반의 모든 시계열 및 센서 데이터 처리 시간을
 * UTC가 아닌 브라우저/시스템의 로컬 일시(Local Date Time) 기준으로 통일합니다.
 */

function toDate(input?: number | Date | string): Date {
  if (input === undefined || input === null) {
    return new Date()
  }
  if (input instanceof Date) {
    return input
  }
  return new Date(input)
}

/**
 * 로컬 시간(시:분:초) 포맷 반환 (예: 22:45:00)
 */
export function formatLocalTime(input?: number | Date | string): string {
  const d = toDate(input)
  if (isNaN(d.getTime())) {
    return '00:00:00'
  }
  const h = String(d.getHours()).padStart(2, '0')
  const m = String(d.getMinutes()).padStart(2, '0')
  const s = String(d.getSeconds()).padStart(2, '0')
  return `${h}:${m}:${s}`
}

/**
 * 로컬 일시 포맷 반환 (예: 2026-10-18 22:45:00)
 */
export function formatLocalDateTime(input?: number | Date | string): string {
  const d = toDate(input)
  if (isNaN(d.getTime())) {
    return ''
  }
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const h = String(d.getHours()).padStart(2, '0')
  const m = String(d.getMinutes()).padStart(2, '0')
  const s = String(d.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${h}:${m}:${s}`
}

/**
 * 로컬 ISO 일시 포맷 반환 (예: 2026-10-18T22:45:00)
 */
export function formatLocalIso(input?: number | Date | string): string {
  const d = toDate(input)
  if (isNaN(d.getTime())) {
    return ''
  }
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const h = String(d.getHours()).padStart(2, '0')
  const m = String(d.getMinutes()).padStart(2, '0')
  const s = String(d.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day}T${h}:${m}:${s}`
}

/**
 * 온디맨드 쿼리용 최근 1시간 로컬 일시 범위 반환
 */
export function getRecentLocalTimeRange(durationSeconds = 3600): { startTime: string; endTime: string } {
  const now = new Date()
  const start = new Date(now.getTime() - durationSeconds * 1000)
  return {
    startTime: formatLocalIso(start),
    endTime: formatLocalIso(now),
  }
}
