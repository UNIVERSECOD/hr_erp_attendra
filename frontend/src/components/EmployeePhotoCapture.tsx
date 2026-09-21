import { useEffect, useRef, useState } from 'react'

interface EmployeePhotoCaptureProps {
  previewUrl: string | null
  onPhotoSelected: (file: File) => void
}

type CameraState = 'idle' | 'starting' | 'active' | 'capturing'

const stopStream = (stream: MediaStream | null) => {
  stream?.getTracks().forEach((track) => track.stop())
}

const cameraErrorMessage = (error: unknown) => {
  if (error instanceof DOMException) {
    if (error.name === 'NotAllowedError' || error.name === 'SecurityError') {
      return 'Kameradan istifadə icazəsi verilmədi.'
    }
    if (error.name === 'NotFoundError' || error.name === 'DevicesNotFoundError') {
      return 'Bu cihazda kamera tapılmadı.'
    }
    if (error.name === 'NotReadableError' || error.name === 'TrackStartError') {
      return 'Kamera başqa proqram tərəfindən istifadə olunur.'
    }
  }
  return error instanceof Error && error.message
    ? error.message
    : 'Kamera açılmadı. Brauzerin kamera icazəsini yoxlayın.'
}

export default function EmployeePhotoCapture({ previewUrl, onPhotoSelected }: EmployeePhotoCaptureProps) {
  const videoRef = useRef<HTMLVideoElement | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const fileInputRef = useRef<HTMLInputElement | null>(null)
  const mountedRef = useRef(true)
  const cameraRequestRef = useRef(0)
  const [cameraState, setCameraState] = useState<CameraState>('idle')
  const [cameraError, setCameraError] = useState<string | null>(null)

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
      cameraRequestRef.current += 1
      stopStream(streamRef.current)
      streamRef.current = null
    }
  }, [])

  const closeCamera = () => {
    cameraRequestRef.current += 1
    stopStream(streamRef.current)
    streamRef.current = null
    if (videoRef.current) {
      videoRef.current.srcObject = null
    }
    setCameraState('idle')
  }

  const startCamera = async () => {
    setCameraError(null)

    if (!navigator.mediaDevices?.getUserMedia) {
      setCameraError('Bu brauzer kamera ilə şəkil çəkməni dəstəkləmir.')
      return
    }

    closeCamera()
    const requestId = cameraRequestRef.current
    setCameraState('starting')

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: false,
        video: {
          facingMode: 'user',
          width: { ideal: 720 },
          height: { ideal: 720 },
        },
      })

      if (!mountedRef.current || requestId !== cameraRequestRef.current) {
        stopStream(stream)
        return
      }

      streamRef.current = stream
      await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()))

      const video = videoRef.current
      if (!video) {
        throw new Error('Kamera önizləməsi hazırlanmadı.')
      }

      video.srcObject = stream
      await video.play()
      if (mountedRef.current && requestId === cameraRequestRef.current) {
        setCameraState('active')
      } else {
        stopStream(stream)
      }
    } catch (error) {
      stopStream(streamRef.current)
      streamRef.current = null
      if (mountedRef.current && requestId === cameraRequestRef.current) {
        setCameraState('idle')
        setCameraError(cameraErrorMessage(error))
      }
    }
  }

  const capturePhoto = async () => {
    const video = videoRef.current
    if (!video || cameraState !== 'active' || !video.videoWidth || !video.videoHeight) {
      setCameraError('Kamera görüntüsü hələ hazır deyil.')
      return
    }

    const requestId = cameraRequestRef.current
    setCameraState('capturing')

    const sourceSize = Math.min(video.videoWidth, video.videoHeight)
    const sourceX = (video.videoWidth - sourceSize) / 2
    const sourceY = (video.videoHeight - sourceSize) / 2
    const outputSize = Math.min(sourceSize, 1024)
    const canvas = document.createElement('canvas')
    canvas.width = outputSize
    canvas.height = outputSize

    const context = canvas.getContext('2d')
    if (!context) {
      setCameraError('Kamera görüntüsü emal edilmədi.')
      setCameraState('active')
      return
    }

    // Mirror the saved frame so it matches the live selfie preview.
    context.translate(outputSize, 0)
    context.scale(-1, 1)
    context.drawImage(
      video,
      sourceX,
      sourceY,
      sourceSize,
      sourceSize,
      0,
      0,
      outputSize,
      outputSize,
    )

    const blob = await new Promise<Blob | null>((resolve) => {
      canvas.toBlob(resolve, 'image/jpeg', 0.92)
    })

    if (!mountedRef.current || requestId !== cameraRequestRef.current) return

    if (!blob) {
      setCameraError('Şəkil yaradılmadı. Yenidən cəhd edin.')
      setCameraState('active')
      return
    }

    onPhotoSelected(new File([blob], `employee-camera-${Date.now()}.jpg`, { type: 'image/jpeg' }))
    setCameraError(null)
    closeCamera()
  }

  const onFileSelected = (file?: File) => {
    if (!file) return
    setCameraError(null)
    onPhotoSelected(file)
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }
  }

  const cameraIsOpen = cameraState !== 'idle'

  return (
    <div className="flex flex-col items-center gap-4">
      <div className="relative w-64 aspect-square border-2 border-dashed border-gray-300 rounded-lg overflow-hidden flex items-center justify-center bg-gray-50">
        {cameraIsOpen ? (
          <>
            <video
              ref={videoRef}
              autoPlay
              muted
              playsInline
              className="w-full h-full object-cover"
              style={{ transform: 'scaleX(-1)' }}
              aria-label="Kamera önizləməsi"
            />
            {cameraState === 'starting' && (
              <div className="absolute inset-0 flex items-center justify-center bg-gray-900/70 text-sm font-medium text-white">
                Kamera açılır...
              </div>
            )}
          </>
        ) : previewUrl ? (
          <img src={previewUrl} alt="Əməkdaş şəkli" className="w-full h-full object-cover" />
        ) : (
          <div className="text-center text-gray-400">
            <svg className="w-10 h-10 mx-auto mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 7h4l2-2h6l2 2h4v12H3V7zm9 3a4 4 0 100 8 4 4 0 000-8z" />
            </svg>
            <p>Şəkil seçilməyib</p>
          </div>
        )}
      </div>

      {cameraError && (
        <p className="text-sm text-red-600" role="alert">{cameraError}</p>
      )}

      <div className="flex flex-wrap justify-center gap-3">
        {cameraIsOpen ? (
          <>
            <button
              type="button"
              onClick={capturePhoto}
              disabled={cameraState !== 'active'}
              className="px-4 py-2 text-sm text-white rounded-lg disabled:cursor-not-allowed disabled:opacity-50"
              style={{ background: '#a855f7' }}
            >
              {cameraState === 'capturing' ? 'Şəkil çəkilir...' : 'Şəkil çək'}
            </button>
            <button
              type="button"
              onClick={closeCamera}
              className="px-4 py-2 text-sm text-gray-700 rounded-lg border border-gray-300 bg-white hover:bg-gray-50"
            >
              Kameranı bağla
            </button>
          </>
        ) : (
          <>
            <button
              type="button"
              onClick={startCamera}
              className="px-4 py-2 text-sm text-white rounded-lg flex items-center gap-2"
              style={{ background: '#a855f7' }}
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 7h4l2-2h6l2 2h4v12H3V7zm9 3a4 4 0 100 8 4 4 0 000-8z" />
              </svg>
              Cihazdan şəkil çək
            </button>
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              className="px-4 py-2 text-sm text-gray-700 rounded-lg flex items-center gap-2 border border-gray-300 bg-gray-100 hover:bg-gray-200"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16a4 4 0 01.88-7.903A5 5 0 0115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
              </svg>
              Kompüterdən yüklə
            </button>
          </>
        )}
        <input
          ref={fileInputRef}
          type="file"
          accept="image/jpeg,image/png"
          className="hidden"
          onChange={(event) => onFileSelected(event.target.files?.[0])}
        />
      </div>
    </div>
  )
}
