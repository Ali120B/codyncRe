import { useState } from 'react'
import { draftOf, type Bot } from '@shared/models'
import { Button, CardForm, CardSection, IconButton } from '../../components/Controls'
import { ModalHeader } from '../../components/Overlay'
import { font } from '../../lib/fonts'
import { useStore } from '../../store/context'
import { Field, FormSheet } from '../marketplace/pieces'

/**
 * The Command allowlist (Maus's Ask-mode extra): commands that start with one of these run
 * without an approval card. Saved as it changes, so the running bot sees it right away.
 */
export function CommandAllowlistSheet({ bot, open, onClose }: { bot: Bot; open: boolean; onClose: () => void }) {
  const store = useStore()
  const [draft, setDraft] = useState('')
  const commands = bot.allowlist

  const save = (allowlist: string[]) => void store.save({ ...draftOf(bot), allowlist })

  const add = () => {
    const value = draft.trim()
    setDraft('')
    if (!value || commands.includes(value)) return
    save([...commands, value])
  }

  return (
    <FormSheet open={open} onClose={onClose} height={520}>
      <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
        <ModalHeader title="Command allowlist" />
        <CardForm>
          <CardSection
            title="Commands"
            footer="Commands that start with one of these run without asking while this bot is in Ask for approval. Your agent’s own permission rules still apply."
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Field value={draft} mono placeholder="npm test" label="Add a command" onChange={setDraft} onKeyDown={(e) => { if (e.key === 'Enter') add() }} />
              <Button disabled={!draft.trim()} onClick={add}>
                Add
              </Button>
            </div>
            {commands.length === 0 ? (
              <span style={{ color: 'var(--secondary)' }}>Nothing yet — every command is asked.</span>
            ) : (
              commands.map((command) => (
                <div key={command} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <span style={{ ...font('callout', 'regular', 'monospaced'), flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {command}
                  </span>
                  <IconButton title={`Remove ${command}`} icon="trash" onClick={() => save(commands.filter((c) => c !== command))} />
                </div>
              ))
            )}
          </CardSection>
        </CardForm>
      </div>
    </FormSheet>
  )
}
