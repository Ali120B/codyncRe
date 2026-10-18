import assert from 'node:assert/strict'
import { test } from 'node:test'
import { itemsByKind, kindOrder, placeKind, rosterKind } from './roster-sections.ts'

test('kind order drops unknown entries and appends missing kinds with groups first', () => {
  assert.deepEqual(kindOrder([]), ['groups', 'bots'])
  assert.deepEqual(kindOrder(['bots', 'groups']), ['bots', 'groups'])
  assert.deepEqual(kindOrder(['bots', 'gone']), ['bots', 'groups'])
  assert.deepEqual(kindOrder(['groups']), ['groups', 'bots'])
})

test('dragging a section heading places it before or after its neighbour', () => {
  assert.deepEqual(placeKind(['groups', 'bots'], 'groups', 'bots', 'after'), ['bots', 'groups'])
  assert.deepEqual(placeKind(['bots', 'groups'], 'groups', 'bots', 'before'), ['groups', 'bots'])
  assert.deepEqual(placeKind(['groups', 'bots'], 'groups', 'groups', 'after'), ['groups', 'bots'])
  assert.deepEqual(placeKind(['groups', 'bots'], 'gone', 'bots', 'after'), ['groups', 'bots'])
  assert.deepEqual(placeKind(['groups', 'bots'], 'groups', 'gone', 'after'), ['groups', 'bots'])
})

test('roster rows follow the drawn sections, keeping each kind in its own order', () => {
  const items = [{ bot: { id: 1, kind: 'group' } }, { bot: { id: 2 } }, { bot: { id: 3, kind: 'group' } }, { bot: { id: 4 } }]
  assert.deepEqual(itemsByKind(items, ['groups', 'bots']).map((i) => i.bot.id), [1, 3, 2, 4])
  assert.deepEqual(itemsByKind(items, ['bots', 'groups']).map((i) => i.bot.id), [2, 4, 1, 3])
  assert.equal(rosterKind({ kind: 'group' }), 'groups')
  assert.equal(rosterKind({}), 'bots')
  assert.deepEqual(items, [{ bot: { id: 1, kind: 'group' } }, { bot: { id: 2 } }, { bot: { id: 3, kind: 'group' } }, { bot: { id: 4 } }])
})
