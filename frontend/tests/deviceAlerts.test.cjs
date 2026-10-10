const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const ts = require('typescript')
const vm = require('node:vm')
const source = fs.readFileSync(require('node:path').join(__dirname, '../src/utils/deviceAlerts.ts'), 'utf8')
const exportsObject = {}
vm.runInNewContext(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText, { exports: exportsObject })
const { evaluateDeviceAlerts: run, OFFLINE_REMINDER_MS: interval } = exportsObject
const device = { id: 1, status: 'ACTIVE', online: false }
test('warn immediately, suppress polls and reloads until 30 minutes', () => {
  const first = run({ offline: {} }, [device], true, 1000)
  assert.equal(first.warn.length, 1)
  assert.equal(run(JSON.parse(JSON.stringify(first.state)), [device], true, interval).warn.length, 0)
  assert.equal(run(first.state, [device], true, interval + 1000).warn.length, 1)
})
test('group offline devices and emit recovery exactly once', () => {
  const first = run({ offline: {} }, [device, { ...device, id: 2 }], true, 1000)
  assert.equal(first.warn.length, 2)
  const recovered = run(first.state, [{ ...device, online: true }, { ...device, id: 2 }], true, 2000)
  assert.equal(recovered.recovered.length, 1)
  assert.equal(run(recovered.state, [{ ...device, online: true }], true, 3000).recovered.length, 0)
})
test('bridge outage never invents device recovery or offline status', () => {
  const first = run({ offline: { 1: 1000 } }, [{ ...device, online: true }], false, 2000)
  assert.equal(first.bridgeWarning, true)
  assert.equal(first.recovered.length, 0)
  assert.equal(first.warn.length, 0)
  assert.equal(run(first.state, [], false, 3000).bridgeWarning, false)
})
test('inactive or removed devices are not reported as recovered', () => {
  const result = run({ offline: { 1: 1000, 2: 1000 } }, [{ ...device, status: 'INACTIVE' }], true, 2000)
  assert.equal(result.warn.length, 0)
  assert.equal(result.recovered.length, 0)
  assert.equal(Object.keys(result.state.offline).length, 0)
})
