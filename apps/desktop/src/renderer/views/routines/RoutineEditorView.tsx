import { useEffect, useRef, useState } from 'react'
import { ChoicePicker, IconButton } from '../../components/Controls'
import { Icon } from '../../components/Icon'
import { Dialog, ModalHeader, useDismiss } from '../../components/Overlay'
import { font } from '../../lib/fonts'
import { useStore } from '../../store/context'
import { emptyDraft, errorText, sameDraft, type Routine, type RoutineScheduleDraft, type RoutineSchedulePreview } from './routine'
import { Field, RoutineWebhookPanel } from './RoutineWebhookPanel'
import { REPEATS, TIME_ZONES, TimeField, WEEKDAYS, dateValue, pad } from './RoutineTimeField'
import './routines.css'

const caption = { ...font('caption'), paddingLeft: 4, lineHeight: 1.4 } as const

export function RoutineEditorView({ botId, routine, saved }: {
  botId: string
  routine: Routine | null
  /** Called after a save (the routine) or a delete (null). */
  saved: (routine: Routine | null) => void
}) {
  const store = useStore()
  const dismiss = useDismiss()
  const [name, setName] = useState(routine?.name ?? '')
  const [instruction, setInstruction] = useState(routine?.instruction ?? '')
  const [schedule, setSchedule] = useState<RoutineScheduleDraft>(emptyDraft)
  const [timeout, setTimeoutText] = useState(String(routine?.timeoutSeconds ?? 3600))
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [advanced, setAdvanced] = useState(false)
  const [loaded, setLoaded] = useState(false)
  const [preview, setPreview] = useState<RoutineSchedulePreview | null>(null)
  const [previewError, setPreviewError] = useState<string | null>(null)
  const [checkedSchedule, setCheckedSchedule] = useState<RoutineScheduleDraft | null>(null)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [started, setStarted] = useState(false)
  const latest = useRef(schedule)
  latest.current = schedule

  // Events and several triggers stay as they are until replaced; times and repeats are editable.
  const keepsOriginal = routine
    ? routine.triggers.length === 1
      ? !['cron', 'webhook', 'once', 'interval'].includes(routine.triggers[0]!.type)
      : routine.triggers.length > 0
    : false
  const options = [
    ...(routine && keepsOriginal ? [{ id: 'keep', label: routine.triggerDescriptions.join(' · ') }] : []),
    { id: 'cron', label: 'Schedule' },
    { id: 'once', label: 'Once' },
    { id: 'interval', label: 'Repeat' },
    { id: 'webhook', label: 'Webhook' },
  ]
  const canSave = !!name.trim() && !!instruction.trim() && !store.isOffline && !busy && loaded && sameDraft(checkedSchedule, schedule) && previewError === null

  const loadSchedule = async () => {
    try {
      const client = await store.ready()
      const result = await client.call<RoutineSchedulePreview>('routineSchedule', {
        triggers: routine?.triggers ?? [],
        timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
      })
      // The host recognises daily/weekly/monthly cron too; keep that friendly style.
      const draft = result.draft
      if (!['cron', 'interval', 'once', 'webhook'].includes(draft.kind)) draft.kind = 'keep'
      setSchedule(draft)
      setPreview(result)
      setCheckedSchedule(result.draft)
      setLoaded(true)
      setError(null)
    } catch (e) {
      setError(errorText(e))
    }
  }

  const checkSchedule = async () => {
    const requested = latest.current
    setPreview(null)
    setPreviewError(null)
    setCheckedSchedule(null)
    try {
      const result = await (await store.ready()).call<RoutineSchedulePreview>('routineSchedule', { draft: requested })
      if (latest.current !== requested) return
      setPreview(result)
      setCheckedSchedule(requested)
    } catch (e) {
      if (latest.current !== requested) return
      setPreviewError(errorText(e))
    }
  }

  useEffect(() => {
    void loadSchedule()
  }, [])

  // The host checks each edit to the schedule, after a short pause in typing.
  useEffect(() => {
    if (!loaded) return
    const timer = setTimeout(() => void checkSchedule(), 300)
    return () => clearTimeout(timer)
  }, [schedule])

  const save = async () => {
    if (!canSave) return
    // Saving ends editing (a new webhook routine stays open on its URL and key).
    ;(document.activeElement as HTMLElement | null)?.blur()
    setBusy(true)
    setError(null)
    try {
      const client = await store.ready()
      const result = await client.call<{ routine: Routine }>('saveRoutine', {
        botId,
        id: routine?.id ?? null,
        name,
        instruction,
        schedule,
        timeoutSeconds: timeout,
      })
      saved(result.routine)
    } catch (e) {
      setError(errorText(e))
    } finally {
      setBusy(false)
    }
  }

  // ⌘S saves.
  const saveRef = useRef(save)
  saveRef.current = save
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 's') {
        e.preventDefault()
        void saveRef.current()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  const update = (change: Partial<RoutineScheduleDraft>) => setSchedule((s) => ({ ...s, ...change }))
  const shownPreview = sameDraft(checkedSchedule, schedule) ? preview : null

  const previewBlock = () => (
    <div style={{ paddingLeft: 4 }}>
      {previewError ? (
        <span style={{ ...font('caption'), color: 'var(--danger)', lineHeight: 1.4 }}>{previewError}</span>
      ) : shownPreview ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <span style={font('caption', 'medium')}>{shownPreview.summary}</span>
          {shownPreview.nextRunAt ? (
            <span style={{ ...font('caption'), color: 'var(--secondary)' }}>
              Next {new Date(shownPreview.nextRunAt).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })}
            </span>
          ) : null}
          {shownPreview.warning ? <span style={{ ...font('caption'), color: 'var(--secondary)' }}>{shownPreview.warning}</span> : null}
        </div>
      ) : null}
    </div>
  )

  const cronFields = () => {
    const style = schedule.calendarStyle
    const withTime = ['daily', 'weekdays', 'weekly', 'monthly', 'days'].includes(style)
    return (
      <div className="routine-schedule">
        <Field label="Repeat">
          <ChoicePicker selection={style} options={REPEATS} onChange={(id) => update({ calendarStyle: id })} fits />
        </Field>
        {style === 'minutes' ? (
          <Field label="Every">
            <select className="routine-time-select routine-time-wide" value={schedule.minuteStep} onChange={(e) => update({ minuteStep: Number(e.target.value) })}>
              {[1, 5, 10, 15, 20, 30].map((n) => (
                <option key={n} value={n}>{n} minutes</option>
              ))}
            </select>
          </Field>
        ) : null}
        {style === 'hourly' ? (
          <Field label="At minute">
            <select className="routine-time-select routine-time-wide" value={schedule.minute} onChange={(e) => update({ minute: Number(e.target.value) })}>
              {Array.from({ length: 60 }, (_, i) => i).map((m) => (
                <option key={m} value={m}>:{pad(m)}</option>
              ))}
            </select>
          </Field>
        ) : null}
        {withTime ? (
          <Field label="Time">
            <TimeField hour={schedule.hour} minute={schedule.minute} onChange={(hour, minute) => update({ hour, minute })} />
          </Field>
        ) : null}
        {style === 'weekly' ? (
          <Field label="Day">
            <select className="routine-time-select routine-time-wide" value={schedule.weekday} onChange={(e) => update({ weekday: Number(e.target.value) })}>
              {WEEKDAYS.map((label, value) => (
                <option key={label} value={value}>{label}</option>
              ))}
            </select>
          </Field>
        ) : null}
        {style === 'monthly' ? (
          <Field label="Day of month">
            <select className="routine-time-select routine-time-wide" value={schedule.monthDay} onChange={(e) => update({ monthDay: Number(e.target.value) })}>
              {Array.from({ length: 31 }, (_, i) => i + 1).map((day) => (
                <option key={day} value={day}>{day}</option>
              ))}
            </select>
          </Field>
        ) : null}
        {style === 'days' ? (
          <Field label="Days">
            <div className="routine-days">
              {WEEKDAYS.map((label, day) => {
                const on = schedule.selectedDays.includes(day)
                return (
                  <button
                    key={label}
                    type="button"
                    className="routine-day"
                    aria-pressed={on}
                    onClick={() =>
                      update({ selectedDays: on ? schedule.selectedDays.filter((d) => d !== day) : [...schedule.selectedDays, day].sort() })
                    }
                  >
                    {label.slice(0, 3)}
                  </button>
                )
              })}
            </div>
          </Field>
        ) : null}
        {style === 'custom' ? (
          <Field label="Cron">
            <input
              className="routine-input"
              style={font('body', undefined, 'monospaced')}
              value={schedule.expression}
              placeholder="0 9 * * 1-5"
              spellCheck={false}
              autoCorrect="off"
              onChange={(e) => update({ expression: e.target.value })}
            />
          </Field>
        ) : null}
        <Field label="Time zone">
          <input
            className="routine-input"
            list="routine-time-zones"
            value={schedule.zone}
            placeholder="Asia/Taipei"
            spellCheck={false}
            autoCorrect="off"
            onChange={(e) => update({ zone: e.target.value })}
          />
          <datalist id="routine-time-zones">
            {TIME_ZONES.map((zone) => (
              <option key={zone} value={zone} />
            ))}
          </datalist>
        </Field>
        {previewBlock()}
      </div>
    )
  }

  const onceFields = () => {
    const at = schedule.at || Date.now() + 3_600_000
    const date = new Date(at)
    return (
      <div className="routine-schedule">
        <Field label="Date">
          <input
            className="routine-input routine-date"
            type="date"
            value={dateValue(at)}
            onChange={(e) => {
              const [year, month, day] = e.target.value.split('-').map(Number)
              if (!year || !month || !day) return
              const next = new Date(schedule.at || Date.now())
              next.setFullYear(year, month - 1, day)
              update({ at: next.getTime() })
            }}
          />
        </Field>
        <Field label="Time">
          <TimeField
            hour={date.getHours()}
            minute={date.getMinutes()}
            onChange={(hour, minute) => {
              const next = new Date(schedule.at || Date.now())
              next.setHours(hour, minute, 0, 0)
              update({ at: next.getTime() })
            }}
          />
        </Field>
        {previewBlock()}
      </div>
    )
  }

  const intervalFields = () => (
    <div className="routine-schedule">
      <Field label="Every">
        <div className="routine-interval">
          <input
            className="routine-input"
            inputMode="numeric"
            value={schedule.amount}
            aria-label="Interval amount"
            onChange={(e) => update({ amount: e.target.value.replace(/[^0-9]/g, '') })}
          />
          <select className="routine-time-select" value={schedule.unit} onChange={(e) => update({ unit: Number(e.target.value) })} aria-label="Interval unit">
            <option value={1}>seconds</option>
            <option value={60}>minutes</option>
            <option value={3600}>hours</option>
            <option value={86400}>days</option>
          </select>
        </div>
      </Field>
      {previewBlock()}
    </div>
  )

  const triggerFields = () => {
    switch (schedule.kind) {
      case 'cron':
        return cronFields()
      case 'once':
        return onceFields()
      case 'interval':
        return intervalFields()
      case 'webhook':
        return routine && routine.triggers.some((t) => t.type === 'webhook' || t.type === 'event') ? (
          <RoutineWebhookPanel botId={botId} routineId={routine.id} />
        ) : (
          <span style={{ ...caption, color: 'var(--secondary)' }}>Runs each time something is posted to its URL. Save to get the URL and key.</span>
        )
      default:
        return <span style={{ ...caption, color: 'var(--secondary)' }}>Set up by the bot. Pick a schedule, Once, Repeat or Webhook to replace it.</span>
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', maxHeight: 700, minHeight: 0, background: 'var(--background)', color: 'var(--text)', ...font(13) }}>
      <ModalHeader title={routine ? 'Edit routine' : 'Set up a routine'} close={false} />
      {/* The card fits the form; it scrolls only when taller than the window allows. */}
      <fieldset className="routine-form" disabled={busy || !loaded} style={{ flex: '1 1 auto', minHeight: 0, overflowY: 'auto' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 24, padding: 20 }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <Field label="Name">
              <input className="routine-input" value={name} placeholder="e.g. Morning summary" onChange={(e) => setName(e.target.value)} />
            </Field>
            <Field label="Instruction">
              <textarea className="routine-input" value={instruction} placeholder="Describe what this bot should do each time it runs." onChange={(e) => setInstruction(e.target.value)} />
            </Field>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <Field label="When to run">
              <ChoicePicker
                selection={schedule.kind}
                options={options}
                onChange={(kind) => update(kind === 'once' && !schedule.at ? { kind, at: Date.now() + 3_600_000 } : { kind })}
                fits
              />
            </Field>
            {loaded ? (
              triggerFields()
            ) : (
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6, color: 'var(--secondary)' }}>
                <Icon name="clock" size={12} />
                Loading schedule…
              </span>
            )}
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <button
              className="press"
              aria-expanded={advanced}
              onClick={() => setAdvanced((a) => !a)}
              style={{ display: 'flex', alignItems: 'center', gap: 6, paddingLeft: 4, ...font('compactSecondary'), color: 'var(--secondary)', width: '100%' }}
            >
              Run settings
              <span style={{ display: 'flex', transform: `rotate(${advanced ? 180 : 0}deg)`, transition: 'transform var(--layout)' }}>
                <Icon name="chevron.down" size={10} weight="semibold" />
              </span>
              <span style={{ flex: 1 }} />
              {!advanced ? <span style={{ color: 'var(--tertiary)' }}>Timeout {timeout}s</span> : null}
            </button>
            {advanced ? (
              <div className="routine-fade" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                <Field label="Timeout (seconds)">
                  <input className="routine-input" value={timeout} placeholder="3600" onChange={(e) => setTimeoutText(e.target.value)} />
                </Field>
                <span style={{ ...caption, color: 'var(--secondary)' }}>A run is stopped after this long. 1 second to 24 hours.</span>
              </div>
            ) : null}
          </div>
        </div>
      </fieldset>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 12, padding: 20, flex: 'none' }}>
        {previewError !== null ? (
          <button className="press" style={{ alignSelf: 'flex-start' }} disabled={store.isOffline} onClick={() => void checkSchedule()}>
            Check schedule again
          </button>
        ) : null}
        {!loaded && error !== null ? (
          <button className="press" style={{ alignSelf: 'flex-start' }} onClick={() => void loadSchedule()}>
            Retry loading schedule
          </button>
        ) : null}
        {!['cron', 'once', 'interval'].includes(schedule.kind) && previewError ? (
          <span style={{ ...font('caption'), color: 'var(--danger)' }}>{previewError}</span>
        ) : null}
        {error ? (
          <span style={{ display: 'flex', gap: 6, alignItems: 'flex-start', ...font('callout'), color: 'var(--danger)', userSelect: 'text' }}>
            <Icon name="exclamationmark.circle" size={12} style={{ marginTop: 1 }} />
            {error}
          </span>
        ) : null}
        {store.isOffline ? (
          <span style={{ display: 'flex', gap: 6, alignItems: 'center', ...font('caption'), color: 'var(--danger)' }}>
            <Icon name="wifi.slash" size={10} />
            Connect to this computer to save your routine.
          </span>
        ) : null}
        <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
          {routine ? (
            <>
              <IconButton title="Delete routine" icon="trash" onClick={() => setConfirmDelete(true)} />
              <IconButton
                title={started ? 'Started' : 'Test run'}
                icon="play.circle"
                disabled={started || store.isOffline}
                onClick={async () => {
                  try {
                    await (await store.ready()).call('runRoutine', { botId, id: routine.id })
                    setStarted(true)
                  } catch (e) {
                    setError(errorText(e))
                  }
                }}
              />
            </>
          ) : null}
          <button className="editor-cancel" disabled={busy} onClick={() => dismiss()}>
            Cancel
          </button>
          <span style={{ flex: 1 }} />
          <button className="editor-done" disabled={!canSave} onClick={() => void save()} title="Save (⌘S)">
            {busy ? 'Saving…' : 'Done'}
          </button>
        </div>
      </div>

      <Dialog
        open={confirmDelete}
        title="Delete routine?"
        message="This deletes the routine and stops its future runs. This can't be undone."
        actions={[
          {
            title: 'Delete routine',
            destructive: true,
            action: async () => {
              if (!routine) return
              try {
                await (await store.ready()).call('deleteRoutine', { botId, id: routine.id })
                saved(null)
              } catch (e) {
                setError(errorText(e))
              }
            },
          },
        ]}
        onClose={() => setConfirmDelete(false)}
      />
    </div>
  )
}
