import assert from 'node:assert/strict'
import { test } from 'node:test'
import { accountInitial, localAccountName } from './local-account.ts'

test('a saved name wins over the computer user name', () => {
  assert.equal(localAccountName('Ali', 'ali'), 'Ali')
  assert.equal(localAccountName('  Ali  ', 'ali'), 'Ali')
})

test('with no saved name the computer user name stands in, then a generic one', () => {
  assert.equal(localAccountName('', 'ali'), 'ali')
  assert.equal(localAccountName('   ', ''), 'Local account')
})

test('the avatar letter is the first character, uppercased', () => {
  assert.equal(accountInitial('ali'), 'A')
  assert.equal(accountInitial('  béla'), 'B')
  assert.equal(accountInitial(''), '?')
})
