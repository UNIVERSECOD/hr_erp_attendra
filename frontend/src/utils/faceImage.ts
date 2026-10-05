export const MAX_FACE_IMAGE_SIZE_BYTES = 200 * 1024

export const FACE_IMAGE_SIZE_ERROR =
  'Üz şəkli 200 KB-dan böyük ola bilməz. Şəkli kiçildib yenidən seçin.'

export const validateFaceImageSize = (file: File): string | null =>
  file.size > MAX_FACE_IMAGE_SIZE_BYTES ? FACE_IMAGE_SIZE_ERROR : null
