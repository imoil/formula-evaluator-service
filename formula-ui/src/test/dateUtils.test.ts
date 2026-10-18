import { describe, it, expect } from 'vitest'
import { formatLocalTime, formatLocalDateTime, formatLocalIso, getRecentLocalTimeRange } from '../utils/dateUtils'

describe('dateUtils - Local Date Time utility', () => {
  it('formats local time as HH:mm:ss', () => {
    const fixedDate = new Date(2026, 9, 18, 22, 45, 30) // 2026-10-18 22:45:30 (Month is 0-indexed: 9 = Oct)
    expect(formatLocalTime(fixedDate)).toBe('22:45:30')
  })

  it('formats local date time as YYYY-MM-DD HH:mm:ss', () => {
    const fixedDate = new Date(2026, 9, 18, 22, 45, 30)
    expect(formatLocalDateTime(fixedDate)).toBe('2026-10-18 22:45:30')
  })

  it('formats local ISO as YYYY-MM-DDTHH:mm:ss', () => {
    const fixedDate = new Date(2026, 9, 18, 22, 45, 30)
    expect(formatLocalIso(fixedDate)).toBe('2026-10-18T22:45:30')
  })

  it('generates recent 1-hour range in local date time format', () => {
    const range = getRecentLocalTimeRange(3600)
    expect(range.startTime).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/)
    expect(range.endTime).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/)
  })

  it('handles invalid or undefined inputs gracefully', () => {
    expect(formatLocalTime('invalid-date')).toBe('00:00:00')
    expect(formatLocalDateTime('invalid-date')).toBe('')
    expect(formatLocalIso('invalid-date')).toBe('')
  })
})
