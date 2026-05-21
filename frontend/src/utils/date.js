const pad = (n) => String(n).padStart(2, '0')

/**
 * 格式化日期时间。
 * 后端 Jackson 将 LocalDateTime 序列化为数组 [year, month, day, hour, minute, second]
 * 或字符串 "yyyy-MM-dd HH:mm:ss"，均为 Asia/Shanghai (UTC+8) 时间。
 */
export const formatDate = (dateValue) => {
  if (!dateValue) return '-'

  try {
    let year, month, day, hours, minutes, seconds

    if (Array.isArray(dateValue)) {
      ;[year, month, day, hours = 0, minutes = 0, seconds = 0] = dateValue
    } else if (typeof dateValue === 'string') {
      // "2024-05-18 14:30:00" 或 "2024-05-18T14:30:00"
      const parts = dateValue.split(/[\sT]/)
      const dateParts = parts[0].split('-')
      year = parseInt(dateParts[0], 10)
      month = parseInt(dateParts[1], 10)
      day = parseInt(dateParts[2], 10)
      if (parts[1]) {
        const timeParts = parts[1].split(':')
        hours = parseInt(timeParts[0], 10) || 0
        minutes = parseInt(timeParts[1], 10) || 0
        seconds = parseInt(timeParts[2], 10) || 0
      }
    } else {
      return String(dateValue)
    }

    if (!year || isNaN(year)) {
      return typeof dateValue === 'string' ? dateValue : String(dateValue)
    }

    return `${year}-${pad(month)}-${pad(day)} ${pad(hours)}:${pad(minutes)}:${pad(seconds)}`
  } catch {
    return typeof dateValue === 'string' ? dateValue : String(dateValue)
  }
}
