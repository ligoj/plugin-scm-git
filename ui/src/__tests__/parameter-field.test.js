/*
 * The subscribe wizard's input of `service:scm:git:repository`: the repositories of the
 * selected instance, searched as the user types (the host LjRemoteSearchField).
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { mount, flushPromises } from '@vue/test-utils'
import def from '../index.js'

const PARAMETER = { id: 'service:scm:git:repository', type: 'TEXT', mandatory: true }

function jsonResponse(body) {
  return { ok: true, status: 200, headers: { get: () => 'application/json' }, json: async () => body }
}
// Expose the bindings of the field; the test types through `update:search`
const inputStub = (name) => ({ name, props: ['items'], emits: ['update:search', 'update:modelValue'], template: '<input />' })
const stubs = { LigojAutocomplete: inputStub('LigojAutocomplete'), LigojCombobox: inputStub('LigojCombobox') }

beforeEach(() => {
  // The search waits for a pause in the typing
  vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
  setActivePinia(createPinia())
  def.install()
  globalThis.fetch = vi.fn(async () => jsonResponse([{ id: 'ligoj-api', name: 'ligoj-api' }]))
})
afterEach(() => { vi.useRealTimers() })

describe('service:scm:git:repository parameter field', () => {
  it('is a remote search to subscribe, the default input to edit a node or for another parameter', () => {
    const field = def.feature('parameterField', { parameter: PARAMETER, isNode: false })
    expect(field).toBeTruthy()
    // The same component on each render, else the wizard would remount the input
    expect(def.feature('parameterField', { parameter: PARAMETER, isNode: false })).toBe(field)
    expect(def.feature('parameterField', { parameter: PARAMETER, isNode: true })).toBeNull()
    expect(def.feature('parameterField', { parameter: { id: 'service:scm:git:url' }, isNode: false })).toBeNull()
  })

  it('searches the repositories and stores the repository name, or a typed new one', async () => {
    const Field = def.feature('parameterField', { parameter: PARAMETER, isNode: false })
    const w = mount(Field, { props: { modelValue: '', parameter: PARAMETER, formValues: {}, mode: 'link', isNode: false, instanceNodeId: 'service:scm:git:local' }, global: { stubs } })
    const input = w.findComponent({ name: 'LigojCombobox' })
    input.vm.$emit('update:search', 'ligoj')
    vi.advanceTimersByTime(300)
    await flushPromises()
    expect(fetch.mock.calls[0][0]).toBe('rest/service/scm/git/service%3Ascm%3Agit%3Alocal/ligoj')
    expect(input.props('items')).toEqual([{ title: 'ligoj-api', subtitle: null, value: 'ligoj-api' }])
  })
})
