export const MAX_FACE_IMAGE_SIZE_BYTES = 5 * 1024 * 1024

export const FACE_IMAGE_SIZE_ERROR =
  'Profil şəkli 5 MB-dan böyük ola bilməz. Daha kiçik şəkil seçin.'

export const validateFaceImageSize = (file: File): string | null =>
  file.size > MAX_FACE_IMAGE_SIZE_BYTES ? FACE_IMAGE_SIZE_ERROR : null
