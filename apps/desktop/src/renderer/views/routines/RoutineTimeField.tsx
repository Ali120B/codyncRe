/** Time and weekday helpers for the routine schedule editor. */

export const WEEKDAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday']

export const TIME_ZONES = [
  'UTC',
  'America/Los_Angeles',
  'America/Denver',
  'America/Chicago',
  'America/New_York',
  'Europe/London',
  'Europe/Berlin',
  'Europe/Paris',
  'Europe/Istanbul',
  'Asia/Dubai',
  'Asia/Kolkata',
  'Asia/Taipei',
  'Asia/Tokyo',
  'Asia/Seoul',
  'Australia/Sydney',
]

export const REPEATS = [
  { id: 'daily', label: 'Every day' },
  { id: 'weekdays', label: 'Weekdays' },
  { id: 'weekly', label: 'Every week' },
  { id: 'monthly', label: 'Every month' },
  { id: 'days', label: 'Selected days' },
  { id: 'hourly', label: 'Every hour' },
  { id: 'minutes', label: 'Every few minutes' },
  { id: 'custom', label: 'Custom cron' },
]

export const pad = (n: number) => String(n).padStart(2, '0')

/** A 12-hour time picker (hours, minutes, AM/PM); the value stays 24-hour. */
export function TimeField({ hour, minute, onChange }: { hour: number; minute: number; onChange: (hour: number, minute: number) => void }) {
  const h24 = Math.min(Math.max(hour, 0), 23)
  const m = Math.min(Math.max(minute, 0), 59)
  const h12 = h24 % 12 || 12
  const pm = h24 >= 12
  return (
    <div className="routine-time">
      <select
        className="routine-time-select"
        aria-label="Hour"
        value={h12}
        onChange={(e) => onChange((Number(e.target.value) % 12) + (pm ? 12 : 0), m)}
      >
        {Array.from({ length: 12 }, (_, i) => i + 1).map((h) => (
          <option key={h} value={h}>{h}</option>
        ))}
      </select>
      <span className="routine-time-colon">:</span>
      <select className="routine-time-select" aria-label="Minute" value={m} onChange={(e) => onChange(h24, Number(e.target.value))}>
        {Array.from({ length: 60 }, (_, i) => i).map((value) => (
          <option key={value} value={value}>{pad(value)}</option>
        ))}
      </select>
      <div className="routine-meridiem" role="group" aria-label="AM or PM">
        <button type="button" aria-pressed={!pm} onClick={() => onChange(h12 % 12, m)}>AM</button>
        <button type="button" aria-pressed={pm} onClick={() => onChange((h12 % 12) + 12, m)}>PM</button>
      </div>
    </div>
  )
}

/** `2026-10-20` in the computer's own time zone, for a date input. */
export function dateValue(at: number) {
  const d = new Date(at)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
