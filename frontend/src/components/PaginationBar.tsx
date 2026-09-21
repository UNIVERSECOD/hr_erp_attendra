import { useState } from 'react'
import { t } from '../i18n/index.ts'

const DEFAULT_PAGE_SIZE_OPTIONS = [12, 24, 50, 100]

type Props = {
  /** 0-based current page */
  page: number
  pageSize: number
  totalItems: number
  loading?: boolean
  pageSizeOptions?: number[]
  onPageChange: (page: number) => void
  onPageSizeChange: (pageSize: number) => void
  idPrefix?: string
}

/**
 * Shared pagination footer used by Device Log Search and Access Logs.
 */
export default function PaginationBar({
  page,
  pageSize,
  totalItems,
  loading = false,
  pageSizeOptions = DEFAULT_PAGE_SIZE_OPTIONS,
  onPageChange,
  onPageSizeChange,
  idPrefix = 'pagination',
}: Props) {
  const [gotoPage, setGotoPage] = useState('')
  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize) || 1)

  const handleGoto = () => {
    const n = parseInt(gotoPage, 10)
    if (Number.isNaN(n)) return
    const clamped = Math.max(0, Math.min(totalPages - 1, n - 1))
    setGotoPage('')
    onPageChange(clamped)
  }

  const handlePageSizeChange = (newSize: number) => {
    onPageSizeChange(newSize)
  }

  return (
    <div
      className="flex flex-wrap items-center justify-between gap-3 px-5 py-4"
      style={{ borderTop: '1px solid #f1f5f9' }}
    >
      {/* Left: page size + total */}
      <div className="flex items-center gap-3">
        <div className="flex items-center gap-2 text-xs text-gray-500">
          <span>{t('deviceLogSearch.itemsPerPage')}</span>
          <div className="relative">
            <select
              id={`${idPrefix}-page-size-select`}
              value={pageSize}
              onChange={(e) => handlePageSizeChange(Number(e.target.value))}
              disabled={loading}
              className="border border-gray-200 rounded-lg px-2 py-1 text-xs pr-6 appearance-none focus:outline-none focus:ring-1 disabled:opacity-50"
              style={{ color: '#7c3aed', fontWeight: 600 }}
            >
              {pageSizeOptions.map((s) => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
            <div className="absolute inset-y-0 right-1.5 flex items-center pointer-events-none">
              <svg className="w-3 h-3 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
              </svg>
            </div>
          </div>
        </div>
        <span className="text-xs text-gray-400">
          {t('deviceLogSearch.totalItems', { n: totalItems.toLocaleString() })}
        </span>
      </div>

      {/* Center: Prev / page numbers / Next */}
      <div className="flex items-center gap-1">
        <button
          id={`${idPrefix}-prev-btn`}
          type="button"
          onClick={() => onPageChange(page - 1)}
          disabled={page === 0 || loading}
          className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-medium transition-all disabled:opacity-40 disabled:cursor-not-allowed"
          style={{ background: '#f3f4f6', color: '#374151' }}
          onMouseEnter={(e) => { if (page > 0) (e.currentTarget as HTMLElement).style.background = '#ede9fe' }}
          onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.background = '#f3f4f6' }}
        >
          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
          </svg>
          {t('common.previous')}
        </button>

        {Array.from({ length: Math.min(totalPages, 7) }, (_, i) => {
          let p: number
          if (totalPages <= 7) {
            p = i
          } else if (page < 4) {
            p = i
          } else if (page > totalPages - 5) {
            p = totalPages - 7 + i
          } else {
            p = page - 3 + i
          }
          return (
            <button
              key={p}
              id={`${idPrefix}-page-${p + 1}-btn`}
              type="button"
              onClick={() => onPageChange(p)}
              disabled={loading}
              className="w-8 h-8 rounded-lg text-xs font-medium transition-all"
              style={
                p === page
                  ? { background: '#a855f7', color: '#fff', boxShadow: '0 2px 6px rgba(168,85,247,0.4)' }
                  : { background: '#f3f4f6', color: '#374151' }
              }
            >
              {p + 1}
            </button>
          )
        })}

        <button
          id={`${idPrefix}-next-btn`}
          type="button"
          onClick={() => onPageChange(page + 1)}
          disabled={page >= totalPages - 1 || loading}
          className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-medium transition-all disabled:opacity-40 disabled:cursor-not-allowed"
          style={{ background: '#f3f4f6', color: '#374151' }}
          onMouseEnter={(e) => { if (page < totalPages - 1) (e.currentTarget as HTMLElement).style.background = '#ede9fe' }}
          onMouseLeave={(e) => { (e.currentTarget as HTMLElement).style.background = '#f3f4f6' }}
        >
          {t('common.next')}
          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
          </svg>
        </button>
      </div>

      {/* Right: Go to page */}
      <div className="flex items-center gap-2 text-xs text-gray-500">
        <span>{t('deviceLogSearch.goToPage')}</span>
        <input
          id={`${idPrefix}-goto-page-input`}
          type="number"
          min={1}
          max={totalPages}
          value={gotoPage}
          onChange={(e) => setGotoPage(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && handleGoto()}
          placeholder={String(page + 1)}
          disabled={loading}
          className="w-14 border border-gray-200 rounded-lg px-2 py-1 text-xs text-center focus:outline-none focus:ring-1 disabled:opacity-50"
        />
        <button
          id={`${idPrefix}-goto-page-btn`}
          type="button"
          onClick={handleGoto}
          disabled={loading}
          className="px-3 py-1 rounded-lg text-xs font-medium transition-all disabled:opacity-50"
          style={{ background: '#ede9fe', color: '#7c3aed' }}
        >
          {t('deviceLogSearch.go')}
        </button>
      </div>
    </div>
  )
}
