import assert from 'node:assert/strict'
import { test } from 'node:test'
import { lastMessagePreview, relativeTime } from './chat-items.ts'
import type { Entry, EntryData } from '../../../shared/models.ts'

const entry = (id: string, seq: number, kind: string, data: EntryData): Entry => ({
  id, seq, botId: 'bot', rev: seq, kind, turn: 0, createdAt: seq, updatedAt: seq, data,
})

test('a chat preview reads its last message, marked as yours', () => {
  const entries = [
    entry('a', 1, 'user', { text: 'First request' }),
    entry('b', 2, 'agent', { text: '**Done** with `it`' }),
    entry('c', 3, 'user', { text: 'One more thing\nand a second line' }),
  ]
  assert.equal(lastMessagePreview(entries, false, () => 'Bot'), 'You: One more thing')
})

test('a group preview names the bot that spoke last', () => {
  const entries = [
    entry('a', 1, 'user', { text: 'Hi all' }),
    entry('b', 2, 'agent', { text: 'On it', author: 'egan' }),
  ]
  assert.equal(lastMessagePreview(entries, true, (id) => (id === 'egan' ? 'Egan' : 'Bot')), 'Egan: On it')
})

test('a file-only last message falls back to its file name', () => {
  const entries = [entry('a', 1, 'agent', { files: [{ id: 'f', name: 'result.zip', size: 4, sha256: 'x' }] })]
  assert.equal(lastMessagePreview(entries, false, () => 'Bot'), 'result.zip')
})

test('the chat list groups by day without repeating a section', () => {
  const now = Date.now()
  assert.equal(relativeTime.section(now), 'Today')
  assert.equal(relativeTime.section(now - 86_400_000), 'Yesterday')
  assert.equal(relativeTime.section(now - 3 * 86_400_000), 'Previous 7 days')
  assert.equal(relativeTime.section(now - 10 * 86_400_000), 'Older')
})
