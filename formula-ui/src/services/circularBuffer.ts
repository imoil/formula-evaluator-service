export class CircularBuffer {
  private buffer: Float64Array
  private capacity: number
  private head: number = 0
  private size: number = 0

  constructor(capacity: number = 100) {
    this.capacity = capacity
    this.buffer = new Float64Array(capacity)
  }

  push(val: number): void {
    this.buffer[this.head] = val
    this.head = (this.head + 1) % this.capacity
    if (this.size < this.capacity) {
      this.size++
    }
  }

  get length(): number {
    return this.size
  }

  avg(windowSize: number): number {
    const count = Math.min(this.size, windowSize)
    if (count <= 0) return 0
    let sum = 0
    const start = (this.head - count + this.capacity) % this.capacity
    for (let i = 0; i < count; i++) {
      sum += this.buffer[(start + i) % this.capacity]
    }
    return sum / count
  }

  max(windowSize: number): number {
    const count = Math.min(this.size, windowSize)
    if (count <= 0) return 0
    const start = (this.head - count + this.capacity) % this.capacity
    let maxVal = this.buffer[start]
    for (let i = 1; i < count; i++) {
      const v = this.buffer[(start + i) % this.capacity]
      if (v > maxVal) maxVal = v
    }
    return maxVal
  }

  min(windowSize: number): number {
    const count = Math.min(this.size, windowSize)
    if (count <= 0) return 0
    const start = (this.head - count + this.capacity) % this.capacity
    let minVal = this.buffer[start]
    for (let i = 1; i < count; i++) {
      const v = this.buffer[(start + i) % this.capacity]
      if (v < minVal) minVal = v
    }
    return minVal
  }
}
