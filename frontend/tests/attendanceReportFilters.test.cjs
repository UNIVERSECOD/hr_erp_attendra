const { test } = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const ts = require('typescript')
const vm = require('node:vm')

function loadTs(file) {
  const exports = {}
  const source = fs.readFileSync(file, 'utf8')
  vm.runInNewContext(ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS },
  }).outputText, { exports, require: name => loadTs(path.resolve(path.dirname(file), name)) })
  return exports
}

const { defaultAttendanceReportFilters, attendanceReportDatePresets } = loadTs(
  path.join(__dirname, '../src/utils/attendanceReportFilters.ts'))

test('report opens on the current Baku day in DAILY view', () => {
  const filters = defaultAttendanceReportFilters(new Date('2026-10-12T21:00:00Z'))
  assert.equal(filters.view, 'DAILY')
  assert.equal(filters.start, '2026-10-13')
  assert.equal(filters.end, filters.start)
})

for (const [instant, yesterday] of [
  ['2026-10-12T21:00:00Z', '2026-10-12'],
  ['2026-10-12T19:59:59Z', '2026-10-11'],
  ['2025-12-31T21:00:00Z', '2025-12-31'],
  ['2024-02-29T21:00:00Z', '2024-02-29'],
  ['2026-02-28T21:00:00Z', '2026-02-28'],
]) {
  test(`yesterday is exactly one Baku calendar day at ${instant}`, () => {
    const presets = attendanceReportDatePresets(new Date(instant))
    assert.equal(presets[0].label, 'Bu gün')
    assert.equal(presets[1].label, 'Dünən')
    assert.equal(presets[1].start, yesterday)
    assert.equal(presets[1].end, yesterday)
  })
}

test('month and year shortcuts preserve their existing ranges through today', () => {
  const presets = attendanceReportDatePresets(new Date('2026-10-12T21:00:00Z'))
  assert.equal(presets[2].start, '2026-10-01')
  assert.equal(presets[3].start, '2026-01-01')
  assert.equal(presets[2].end, '2026-10-13')
  assert.equal(presets[3].end, '2026-10-13')
})
