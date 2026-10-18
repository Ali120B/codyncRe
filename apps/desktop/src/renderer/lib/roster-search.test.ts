import assert from 'node:assert/strict'
import { test } from 'node:test'
import { matchRoster } from './roster-search.ts'

const item = (name: string, description = '', lastMessage: string | null = null) =>
  ({ ref: { computerId: 'c', botId: name }, bot: { name, description, lastMessage } }) as never

test('empty query keeps every bot', () => {
  const items = [item('Brody'), item('GitScout')]
  assert.equal(matchRoster(items, '   ').length, 2)
})

test('matches name case-insensitively', () => {
  const items = [item('Brody'), item('GitScout')]
  assert.deepEqual(matchRoster(items, 'bro').map((i) => i.bot.name), ['Brody'])
})

test('matches description and last message', () => {
  const items = [item('A', 'a quick rundown', null), item('B', '', 'Fills the biggest gap')]
  assert.deepEqual(matchRoster(items, 'rundown').map((i) => i.bot.name), ['A'])
  assert.deepEqual(matchRoster(items, 'biggest gap').map((i) => i.bot.name), ['B'])
})

test('no matches yields an empty list', () => {
  assert.equal(matchRoster([item('Brody')], 'zzz').length, 0)
})
