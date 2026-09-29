import { ChangeEvent, useRef, useState } from 'react'
import {
  DataImportResult,
  DataTransferEntity,
  DataTransferFormat,
  dataTransferApi,
} from '../api/dataTransferApi.ts'
import { getApiErrorMessage } from '../utils/apiError.ts'

interface DataTransferControlsProps {
  entity: DataTransferEntity
  onImported: () => void | Promise<void>
}

const FILE_NAMES: Record<DataTransferEntity, string> = {
  employees: 'employees',
  departments: 'departments',
  positions: 'positions',
}

export default function DataTransferControls({ entity, onImported }: DataTransferControlsProps) {
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [menu, setMenu] = useState<'export' | 'template' | null>(null)
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState<DataImportResult | null>(null)
  const [error, setError] = useState<string | null>(null)

  const download = async (kind: 'export' | 'template', format: DataTransferFormat) => {
    setBusy(true)
    setError(null)
    setMenu(null)
    try {
      const response = kind === 'export'
        ? await dataTransferApi.exportFile(entity, format)
        : await dataTransferApi.downloadTemplate(entity, format)
      const url = URL.createObjectURL(response.data)
      const link = document.createElement('a')
      link.href = url
      link.download = `${FILE_NAMES[entity]}${kind === 'template' ? '_template' : ''}.${format}`
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(url)
    } catch (requestError: unknown) {
      setError(getApiErrorMessage(requestError, 'Fayl endirilə bilmədi'))
    } finally {
      setBusy(false)
    }
  }

  const importFile = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return

    setBusy(true)
    setError(null)
    try {
      const response = await dataTransferApi.importFile(entity, file)
      const importResult = response.data.data
      setResult(importResult)
      if (importResult.importedRows > 0) {
        await onImported()
      }
    } catch (requestError: unknown) {
      setError(getApiErrorMessage(requestError, 'Fayl import edilə bilmədi'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <div className="flex items-center gap-2">
        <input
          ref={fileInputRef}
          type="file"
          accept=".csv,.xls,.xlsx"
          className="hidden"
          onChange={importFile}
        />
        <button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          disabled={busy}
          className="flex items-center gap-1.5 px-3 py-2 text-sm font-medium text-gray-600 bg-white border border-gray-200 rounded-lg hover:bg-gray-50 disabled:opacity-50"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v2a2 2 0 002 2h12a2 2 0 002-2v-2M12 4v12m0-12l-4 4m4-4l4 4" />
          </svg>
          İmport
        </button>

        <div className="relative">
          <button
            type="button"
            onClick={() => setMenu((current) => current === 'export' ? null : 'export')}
            disabled={busy}
            aria-expanded={menu === 'export'}
            className="flex items-center gap-1.5 px-3 py-2 text-sm font-medium text-gray-600 bg-white border border-gray-200 rounded-lg hover:bg-gray-50 disabled:opacity-50"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 8V6a2 2 0 012-2h12a2 2 0 012 2v2M12 4v12m0 0l-4-4m4 4l4-4M4 16v2a2 2 0 002 2h12a2 2 0 002-2v-2" />
            </svg>
            Eksport
          </button>
          {menu === 'export' && (
            <FormatMenu onSelect={(format) => download('export', format)} />
          )}
        </div>

        <div className="relative">
          <button
            type="button"
            onClick={() => setMenu((current) => current === 'template' ? null : 'template')}
            disabled={busy}
            aria-expanded={menu === 'template'}
            className="flex items-center gap-1.5 px-3 py-2 text-sm font-medium text-gray-600 bg-white border border-gray-200 rounded-lg hover:bg-gray-50 disabled:opacity-50"
          >
            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6M9 8h2m-5 12h12a2 2 0 002-2V6l-4-4H6a2 2 0 00-2 2v14a2 2 0 002 2z" />
            </svg>
            Şablon
          </button>
          {menu === 'template' && (
            <FormatMenu onSelect={(format) => download('template', format)} />
          )}
        </div>
      </div>

      {busy && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/20" aria-live="polite">
          <div className="bg-white border border-gray-200 rounded-lg shadow-lg px-5 py-4 text-sm text-gray-700">
            Fayl emal edilir...
          </div>
        </div>
      )}

      {(result || error) && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="w-full max-w-lg bg-white rounded-lg shadow-xl p-5" role="dialog" aria-modal="true">
            <div className="flex items-start justify-between gap-4 mb-4">
              <div>
                <h2 className="text-lg font-bold text-gray-900">İmport nəticəsi</h2>
                {result && (
                  <p className={`mt-1 text-sm ${result.successful ? 'text-green-700' : 'text-red-700'}`}>
                    {result.successful
                      ? `${result.importedRows} sətir import edildi.`
                      : 'Xətalara görə heç bir sətir import edilmədi.'}
                  </p>
                )}
              </div>
              <button
                type="button"
                onClick={() => { setResult(null); setError(null) }}
                className="w-8 h-8 flex items-center justify-center text-gray-500 hover:bg-gray-100 rounded-lg"
                aria-label="Bağla"
              >
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            {error && <p className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg p-3">{error}</p>}

            {result?.deviceSyncDeferred && result.importedRows > 0 && (
              <p className="text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded-lg p-3 mb-3">
                Cihazlara göndərmə avtomatik edilmədi. Ərazi üzrə əməkdaş sinxronizasiyasını ayrıca başladın.
              </p>
            )}

            {result && result.errors.length > 0 && (
              <div className="max-h-72 overflow-y-auto border border-red-200 rounded-lg divide-y divide-red-100">
                {result.errors.map((item, index) => (
                  <div key={`${item.row}-${item.field}-${index}`} className="p-3 text-sm">
                    <span className="font-semibold text-red-700">
                      {item.row > 0 ? `Sətir ${item.row}` : 'Fayl'} · {item.field}
                    </span>
                    <p className="text-gray-700 mt-0.5">{item.message}</p>
                  </div>
                ))}
              </div>
            )}

            <div className="flex justify-end mt-5">
              <button
                type="button"
                onClick={() => { setResult(null); setError(null) }}
                className="px-4 py-2 text-sm font-medium text-white rounded-lg bg-violet-600 hover:bg-violet-700"
              >
                Bağla
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}

function FormatMenu({ onSelect }: { onSelect: (format: DataTransferFormat) => void }) {
  return (
    <div className="absolute right-0 top-full mt-1 z-30 w-36 overflow-hidden bg-white border border-gray-200 rounded-lg shadow-lg">
      <button
        type="button"
        onClick={() => onSelect('xlsx')}
        className="w-full px-3 py-2 text-left text-sm text-gray-700 hover:bg-gray-50"
      >
        Excel (.xlsx)
      </button>
      <button
        type="button"
        onClick={() => onSelect('csv')}
        className="w-full px-3 py-2 text-left text-sm text-gray-700 hover:bg-gray-50"
      >
        CSV (.csv)
      </button>
    </div>
  )
}
