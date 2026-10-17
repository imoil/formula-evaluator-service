import { describe, it, expect } from 'vitest'
import { CircularBuffer } from '../services/circularBuffer'

describe('CircularBuffer', () => {
  it('should maintain capacity and correct length', () => {
    const buf = new CircularBuffer(5)
    expect(buf.length).toBe(0)

    buf.push(10)
    buf.push(20)
    expect(buf.length).toBe(2)

    buf.push(30)
    buf.push(40)
    buf.push(50)
    expect(buf.length).toBe(5)

    buf.push(60) // Overflow, drops 10
    expect(buf.length).toBe(5)
  })

  it('should calculate avg correctly for various window sizes and wrap around', () => {
    const buf = new CircularBuffer(5)
    expect(buf.avg(3)).toBe(0)

    buf.push(10)
    buf.push(20)
    buf.push(30)
    // Buffer: [10, 20, 30]
    expect(buf.avg(2)).toBe(25) // (20 + 30) / 2
    expect(buf.avg(5)).toBe(20) // (10 + 20 + 30) / 3

    buf.push(40)
    buf.push(50)
    buf.push(60) // Buffer now has [60, 20, 30, 40, 50], elements in order: 20, 30, 40, 50, 60
    expect(buf.avg(3)).toBe(50) // (40 + 50 + 60) / 3
    expect(buf.avg(5)).toBe(40) // (20 + 30 + 40 + 50 + 60) / 5
  })

  it('should calculate max and min correctly with buffer wrap around', () => {
    const buf = new CircularBuffer(5)
    expect(buf.max(3)).toBe(0)
    expect(buf.min(3)).toBe(0)

    buf.push(10)
    buf.push(50)
    buf.push(20)
    buf.push(40)
    buf.push(30)
    // Order: 10, 50, 20, 40, 30

    expect(buf.max(3)).toBe(40) // max(20, 40, 30) = 40
    expect(buf.min(3)).toBe(20) // min(20, 40, 30) = 20

    buf.push(5) // Overwrites 10. Order: 50, 20, 40, 30, 5
    expect(buf.max(5)).toBe(50)
    expect(buf.min(5)).toBe(5)
    expect(buf.min(2)).toBe(5) // min(30, 5) = 5
  })
})
