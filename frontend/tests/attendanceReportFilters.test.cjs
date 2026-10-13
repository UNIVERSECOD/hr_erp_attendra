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

const { defaultAttendanceReportFilters, attendanceReportDatePresets, attendanceReportMonths,
  attendanceReportPeriodRange, attendanceReportViewRange } = loadTs(
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

test('monthly choices contain all twelve Azerbaijani month names in calendar order', () => {
  assert.equal(attendanceReportMonths.length, 12)
  assert.equal(attendanceReportMonths[0], 'Yanvar')
  assert.equal(attendanceReportMonths[1], 'Fevral')
  assert.equal(attendanceReportMonths[11], 'Dekabr')
})

for (const [year, month, endDay] of [
  ['2026', '01', '31'], ['2026', '02', '28'], ['2024', '02', '29'],
  ['1900', '02', '28'], ['2000', '02', '29'], ['2026', '04', '30'],
  ['2026', '12', '31'], ['0001', '02', '28'],
]) {
  test(`monthly selection covers the entire calendar month ${year}-${month}`, () => {
    const range = attendanceReportPeriodRange('MONTHLY', year, month)
    assert.equal(range.start, `${year}-${month}-01`)
    assert.equal(range.end, `${year}-${month}-${endDay}`)
  })
}

for (const year of ['2024', '2026']) {
  test(`annual selection covers all of ${year} within the inclusive 366-day API limit`, () => {
    const range = attendanceReportPeriodRange('YEARLY', year, '10')
    assert.equal(range.start, `${year}-01-01`)
    assert.equal(range.end, `${year}-12-31`)
    const days = (Date.parse(range.end) - Date.parse(range.start)) / 86400000 + 1
    assert.equal(days, year === '2024' ? 366 : 365)
  })
}

for (const year of ['', '202', '0000', '10000', 'abcd', '-123', '20.5']) {
  test(`incomplete or invalid year '${year}' clears request dates`, () => {
    for (const view of ['MONTHLY', 'YEARLY']) {
      const range = attendanceReportPeriodRange(view, year, '10')
      assert.equal(range.start, '')
      assert.equal(range.end, '')
    }
  })
}

test('switching to summary views expands the selected day to its full month or year', () => {
  const dates = { start: '2024-02-16', end: '2024-02-16' }
  const monthly = attendanceReportViewRange('MONTHLY', dates)
  assert.equal(monthly.start, '2024-02-01')
  assert.equal(monthly.end, '2024-02-29')
  const yearly = attendanceReportViewRange('YEARLY', dates)
  assert.equal(yearly.start, '2024-01-01')
  assert.equal(yearly.end, '2024-12-31')
})

test('daily/detail view changes retain exact custom day ranges', () => {
  for (const view of ['DAILY', 'DETAILS']) {
    const range = attendanceReportViewRange(view, { start: '2026-09-27', end: '2026-10-13' })
    assert.equal(range.start, '2026-09-27')
    assert.equal(range.end, '2026-10-13')
  }
})

test('empty date ranges use the current Baku month/year when opening summary views', () => {
  const now = new Date('2025-12-31T21:00:00Z')
  const monthly = attendanceReportViewRange('MONTHLY', { start: '', end: '' }, now)
  const yearly = attendanceReportViewRange('YEARLY', { start: '', end: '' }, now)
  assert.equal(monthly.start, '2026-01-01')
  assert.equal(monthly.end, '2026-01-31')
  assert.equal(yearly.start, '2026-01-01')
  assert.equal(yearly.end, '2026-12-31')
})
