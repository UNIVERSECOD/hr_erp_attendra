import { useEffect, useMemo, useRef, useState } from 'react'
import Layout from '../components/Layout.tsx'
import EmployeeDetailModal from '../components/EmployeeDetailModal.tsx'
import EmployeeAvatar from '../components/EmployeeAvatar.tsx'
import EmployeePhotoCapture from '../components/EmployeePhotoCapture.tsx'
import { useEmployeeStore } from '../store/employeeStore.ts'
import { useBranchStore } from '../store/branchStore.ts'
import { Department, Employee, Position, Timetable } from '../types'
import { employeeApi } from '../api/employeeApi.ts'
import { departmentApi } from '../api/departmentApi.ts'
import { positionApi } from '../api/positionApi.ts'
import { deviceApi } from '../api/deviceApi.ts'
import { deviceUserApi } from '../api/deviceUserApi.ts'
import { timetableApi } from '../api/timetableApi.ts'
import { getApiErrorMessage } from '../utils/apiError.ts'

const UI_SHIFT_TYPES = ['STANDARD', 'FLEXIBLE'] as const
const SHIFT_TYPE_LABELS: Record<string, string> = {
  STANDARD: 'Standart Növbə',
  FLEXIBLE: 'Sərbəst Növbə',
  FIRST_ENTRY: 'Sərbəst Növbə',
  SERBEST: 'Sərbəst Növbə',
  FREE_SHIFT: 'Sərbəst Növbə',
  FREE: 'Sərbəst Növbə',
  MORNING: 'Standart Növbə',
  NIGHT: 'Standart Növbə',
}

interface EmployeeFormData {
  firstName: string
  lastName: string
  fatherName: string
  email: string
  mobilePhone: string
  gender: string
  finNumber: string
  serialNumber: string
  birthDate: string
  positionId: number | ''
  departmentId: number | ''
  contractNumber: string
  branchId: number | ''
  hireDate: string
  contractEndDate: string
  annualLeaveDuration: number | ''
  annualLeaveBalance: number | ''
  employmentStatus: string
  timetableId: number | ''
  shiftType: string
  salary: number | ''
  hourlyRate: number | ''
  allowance: string
  emergencyContact: string
  address: string
  notes: string
  area: string
}

type EmployeeFormErrors = Partial<Record<keyof EmployeeFormData, string>>

const isRecord = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null
const extractStatusCode = (value: unknown): number | undefined => {
  if (!isRecord(value)) return undefined
  const response = value.response
  if (!isRecord(response)) return undefined
  return typeof response.status === 'number' ? response.status : undefined
}

const defaultForm: EmployeeFormData = {
  firstName: '',
  lastName: '',
  fatherName: '',
  email: '',
  mobilePhone: '',
  gender: '',
  finNumber: '',
  serialNumber: '',
  birthDate: '',
  positionId: '',
  departmentId: '',
  contractNumber: '',
  branchId: '',
  hireDate: new Date().toISOString().split('T')[0],
  contractEndDate: '',
  annualLeaveDuration: 30,
  annualLeaveBalance: 30,
  employmentStatus: 'ACTIVE',
  timetableId: '',
  shiftType: '',
  salary: '',
  hourlyRate: '',
  allowance: '',
  emergencyContact: '',
  address: '',
  notes: '',
  area: '',
}

const AVATAR_COLORS = ['#6366f1', '#a855f7', '#10b981', '#f59e0b', '#ef4444', '#3b82f6']

function getAvatarColor(name: string) {
  let hash = 0
  for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash)
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length]
}

/** Match ISAPI device user by Hikvision person ID (deviceEmployeeNo), not prefixed HR code. */
function matchesDevicePerson(deviceEmployeeNo: string | undefined, employeeId: string | undefined, candidateNo: unknown): boolean {
  const no = String(candidateNo || '').trim()
  if (!no) return false
  const deviceNo = (deviceEmployeeNo || '').trim()
  if (deviceNo && no === deviceNo) return true
  const hrId = (employeeId || '').trim()
  return !!hrId && no === hrId
}

export default function EmployeesPage() {
  const defaultDeviceId = Number(import.meta.env.VITE_DEFAULT_DEVICE_ID || 1)
  const { employees, loading, error, fetchEmployees, deleteEmployee, totalPages, currentPage, totalElements } = useEmployeeStore()
  const { branches, fetchBranches } = useBranchStore()
  const [search, setSearch] = useState('')
  const [departments, setDepartments] = useState<Department[]>([])
  const [positions, setPositions] = useState<Position[]>([])
  const [timetables, setTimetables] = useState<Timetable[]>([])
  const [employeeDoors, setEmployeeDoors] = useState<string[]>([])
  const [filterDept, setFilterDept] = useState<string>('')
  const [filterStatus, setFilterStatus] = useState<string>('')
  const [filterShift, setFilterShift] = useState<string>('')
  const [filterBranch, setFilterBranch] = useState<string>('')
  const [showWizard, setShowWizard] = useState(false)
  const [currentStep, setCurrentStep] = useState(1)
  const [editingEmployee, setEditingEmployee] = useState<Employee | null>(null)
  const [form, setForm] = useState<EmployeeFormData>(defaultForm)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<EmployeeFormErrors>({})
  const [deleteConfirm, setDeleteConfirm] = useState<Employee | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const [deletingEmployee, setDeletingEmployee] = useState(false)
  const [uploadingFaceEmployeeId, setUploadingFaceEmployeeId] = useState<number | null>(null)
  const [deletingFaceEmployeeId, setDeletingFaceEmployeeId] = useState<number | null>(null)
  const [uploadFaceError, setUploadFaceError] = useState<string | null>(null)
  const [showProfileModal, setShowProfileModal] = useState(false)
  const [profileLoading, setProfileLoading] = useState(false)
  const [profileError, setProfileError] = useState<string | null>(null)
  const [selectedEmployee, setSelectedEmployee] = useState<Employee | null>(null)
  const [profileImageSrc, setProfileImageSrc] = useState<string | null>(null)
  const [wizardImagePreview, setWizardImagePreview] = useState<string | null>(null)
  const [wizardImageFile, setWizardImageFile] = useState<File | null>(null)
  const wizardImageObjectUrlRef = useRef<string | null>(null)
  const wizardImageLoadTokenRef = useRef(0)

  useEffect(() => {
    fetchEmployees(0, 20)
    departmentApi.getAll().then((res) => setDepartments(res.data?.data ?? []))
    positionApi.getAll().then((res) => setPositions(res.data?.data ?? []))
    fetchBranches()
    timetableApi.getAll().then((res) => setTimetables(res.data?.data ?? []))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => () => {
    if (wizardImageObjectUrlRef.current) {
      URL.revokeObjectURL(wizardImageObjectUrlRef.current)
    }
  }, [])

  const formatDate = (value?: string) => {
    if (!value) return '—'
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return value
    return date.toLocaleDateString('az-AZ')
  }

  const statusLabel = (status: Employee['employmentStatus']) => {
    if (status === 'ACTIVE') return 'Aktiv'
    if (status === 'ON_LEAVE') return 'Məzuniyyətdə'
    return 'Deaktiv'
  }

  const stepTitles = ['Ümumi məlumat', 'İş məlumatları', 'Şəkil']

  const employeeIdPreview = useMemo(() => {
    if (editingEmployee?.employeeId) return editingEmployee.employeeId
    return `EMP${String((totalElements || employees.length) + 1).padStart(4, '0')}`
  }, [editingEmployee, totalElements, employees.length])

  const branchLabelById = (branchId?: number) => {
    if (!branchId) return '—'
    return branches.find((b) => b.id === branchId)?.name || '—'
  }

  const loadEmployeeDoors = async (employeeId?: number) => {
    if (!employeeId) {
      setEmployeeDoors([])
      return
    }
    try {
      const res = await employeeApi.getDoors(employeeId)
      setEmployeeDoors(res.data?.data ?? [])
    } catch {
      setEmployeeDoors([])
    }
  }

  const openCreate = () => {
    wizardImageLoadTokenRef.current += 1
    if (wizardImageObjectUrlRef.current) {
      URL.revokeObjectURL(wizardImageObjectUrlRef.current)
      wizardImageObjectUrlRef.current = null
    }
    setEditingEmployee(null)
    setForm(defaultForm)
    setEmployeeDoors([])
    setWizardImageFile(null)
    setWizardImagePreview(null)
    setCurrentStep(1)
    setFormError(null)
    setFieldErrors({})
    setShowWizard(true)
  }

  const openEdit = (emp: Employee) => {
    const imageLoadToken = ++wizardImageLoadTokenRef.current
    setEditingEmployee(emp)
    setForm({
      firstName: emp.firstName,
      lastName: emp.lastName,
      fatherName: emp.fatherName || '',
      email: emp.email || '',
      mobilePhone: emp.mobilePhone || '',
      gender: emp.gender || '',
      finNumber: emp.finNumber || '',
      serialNumber: emp.serialNumber || '',
      birthDate: emp.birthDate || '',
      positionId: emp.positionId || '',
      departmentId: emp.departmentId || '',
      contractNumber: emp.contractNumber || '',
      branchId: emp.branchId || '',
      hireDate: emp.hireDate || defaultForm.hireDate,
      contractEndDate: emp.contractEndDate || '',
      annualLeaveDuration: emp.annualLeaveDuration ?? 30,
      annualLeaveBalance: emp.annualLeaveBalance ?? 30,
      employmentStatus: emp.employmentStatus || 'ACTIVE',
      timetableId: emp.timetableId || '',
      shiftType: emp.shiftType || '',
      salary: emp.salary ?? '',
      hourlyRate: emp.hourlyRate ?? '',
      allowance: emp.allowance || '',
      emergencyContact: emp.emergencyContact || '',
      address: emp.address || '',
      notes: emp.notes || '',
      area: emp.area || '',
    })
    loadEmployeeDoors(emp.id)
    setWizardImageFile(null)
    if (wizardImageObjectUrlRef.current) {
      URL.revokeObjectURL(wizardImageObjectUrlRef.current)
      wizardImageObjectUrlRef.current = null
    }
    setWizardImagePreview(null)
    setCurrentStep(1)
    setFormError(null)
    setFieldErrors({})
    setShowWizard(true)

    if (emp.faceImageUrl) {
      void employeeApi.getFaceImage(emp.id)
        .then((response) => {
          if (wizardImageLoadTokenRef.current !== imageLoadToken) return
          const previewUrl = URL.createObjectURL(response.data)
          if (wizardImageObjectUrlRef.current) {
            URL.revokeObjectURL(wizardImageObjectUrlRef.current)
          }
          wizardImageObjectUrlRef.current = previewUrl
          setWizardImagePreview(previewUrl)
        })
        .catch(() => {
          if (wizardImageLoadTokenRef.current === imageLoadToken) {
            setWizardImagePreview(null)
          }
        })
    }
  }

  const closeWizard = () => {
    wizardImageLoadTokenRef.current += 1
    if (wizardImageObjectUrlRef.current) {
      URL.revokeObjectURL(wizardImageObjectUrlRef.current)
      wizardImageObjectUrlRef.current = null
    }
    setShowWizard(false)
    setWizardImageFile(null)
    setWizardImagePreview(null)
    setCurrentStep(1)
    setFormError(null)
    setFieldErrors({})
  }

  const openProfile = async (employee: Employee) => {
    setShowProfileModal(true)
    setProfileLoading(true)
    setProfileError(null)
    setSelectedEmployee(employee)
    try {
      let res = await employeeApi.getById(employee.id)
      if (res.data?.data) {
        let details = res.data.data
        if (!details.faceImageUrl && details.deviceIds && details.deviceIds.length > 0) {
          const devicesRes = await deviceApi.getAll()
          const devicesList = (devicesRes.data as any)?.data ?? (Array.isArray(devicesRes.data) ? devicesRes.data : [])
          const deviceMap = new Map<number, number>()
          for (const d of devicesList) {
            if (d.id != null && d.deviceId != null) {
              deviceMap.set(Number(d.id), Number(d.deviceId))
            }
          }
          // Match device user by Hikvision person ID (deviceEmployeeNo), not prefixed HR employeeId.
          for (const backendDeviceId of details.deviceIds) {
            const isapiDeviceId = deviceMap.get(Number(backendDeviceId))
            if (!isapiDeviceId) continue
            try {
              const usersRes = await deviceUserApi.getAll(isapiDeviceId)
              const users = Array.isArray(usersRes.data) ? usersRes.data : (usersRes.data as any)?.data ?? []
              const deviceUser = users.find((u: any) =>
                matchesDevicePerson(details.deviceEmployeeNo, details.employeeId, u.employeeNo)
              )
              if (deviceUser) {
                const syncRes = await deviceUserApi.syncFaceFromDevice(isapiDeviceId, deviceUser.id, details.id)
                if (syncRes.data?.status === 'SUCCESS' || (syncRes.data as any)?.status === 'SUCCESS') {
                  res = await employeeApi.getById(employee.id)
                  details = res.data?.data || details
                  break
                }
              }
            } catch {
              // Try next device
            }
          }
        }
        if (profileImageSrc) {
          URL.revokeObjectURL(profileImageSrc)
          setProfileImageSrc(null)
        }
        if (details.faceImageUrl) {
          try {
            const imageResponse = await employeeApi.getFaceImage(details.id)
            setProfileImageSrc(URL.createObjectURL(imageResponse.data))
          } catch {
            setProfileImageSrc(null)
          }
        }
        setSelectedEmployee(details)
      }
    } catch (e: unknown) {
      setProfileError((e as Error).message || 'Əməkdaş məlumatları yüklənmədi')
    } finally {
      setProfileLoading(false)
    }
  }

  const closeProfile = () => {
    if (profileImageSrc) {
      URL.revokeObjectURL(profileImageSrc)
    }
    setProfileImageSrc(null)
    setShowProfileModal(false)
    setProfileError(null)
  }

  const setFormField = <K extends keyof EmployeeFormData>(key: K, value: EmployeeFormData[K]) => {
    setForm((prev) => ({ ...prev, [key]: value }))
    setFieldErrors((prev) => {
      if (!prev[key]) return prev
      const next = { ...prev }
      delete next[key]
      return next
    })
  }

  const getStepValidationErrors = (step: number): EmployeeFormErrors => {
    const errors: EmployeeFormErrors = {}

    if (step === 1) {
      if (!form.finNumber.trim()) errors.finNumber = 'FIN daxil edilməlidir.'
      if (!form.firstName.trim()) errors.firstName = 'Ad daxil edilməlidir.'
      if (!form.lastName.trim()) errors.lastName = 'Soyad daxil edilməlidir.'
    }

    if (step === 2) {
      if (!form.departmentId) errors.departmentId = 'Departament seçilməlidir.'
      if (!form.timetableId) errors.timetableId = 'İş cədvəli seçilməlidir.'
    }

    return errors
  }

  const validateStep = (step: number) => {
    const errors = getStepValidationErrors(step)
    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  const handleNextStep = () => {
    if (!validateStep(currentStep)) return
    setFormError(null)
    setCurrentStep((step) => Math.min(step + 1, stepTitles.length))
  }

  /**
   * Same captured photo is used for:
   * 1) employee profile photo (always persisted)
   * 2) Hikvision / face detection (best-effort per assigned device)
   */
  const uploadFaceForEmployee = async (employee: Employee, file: File | null): Promise<string[]> => {
    if (!file) return []

    // Profile photo first — must not depend on Hikvision success.
    await employeeApi.uploadFaceImage(employee.id, file)

    const deviceIds = employee.deviceIds && employee.deviceIds.length > 0
      ? employee.deviceIds
      : [defaultDeviceId]
    console.info('[uploadFace] employee.deviceIds:', deviceIds)

    let isapiDeviceIds: number[] = []
    try {
      const devicesRes = await deviceApi.getAll()
      const devicesList = devicesRes.data?.data ?? (Array.isArray(devicesRes.data) ? devicesRes.data : [])
      console.info('[uploadFace] devicesList:', devicesList)
      const deviceMap = new Map<number, string>()
      for (const d of devicesList) {
        if (d.id != null && d.deviceId != null) {
          deviceMap.set(d.id, d.deviceId)
        }
      }
      console.info('[uploadFace] deviceMap:', Array.from(deviceMap.entries()))
      isapiDeviceIds = deviceIds
        .map((id) => Number(deviceMap.get(Number(id)) ?? id))
        .filter((id) => !isNaN(id) && id > 0)
    } catch (e) {
      console.error('[uploadFace] deviceApi.getAll failed:', e)
      isapiDeviceIds = deviceIds.map(Number)
    }
    console.info('[uploadFace] isapiDeviceIds:', isapiDeviceIds)

    if (isapiDeviceIds.length === 0) {
      return []
    }

    const errors: string[] = []

    for (const isapiDeviceId of isapiDeviceIds) {
      try {
        console.info('[uploadFace] checking device', isapiDeviceId)
        const usersRes = await deviceUserApi.getAll(isapiDeviceId)
        console.info('[uploadFace] usersRes for', isapiDeviceId, ':', usersRes.data)
        const users = Array.isArray(usersRes.data) ? usersRes.data : (usersRes.data as any)?.data ?? []
        const deviceUser = users.find((u: any) =>
          matchesDevicePerson(employee.deviceEmployeeNo, employee.employeeId, u.employeeNo)
        )
        console.info('[uploadFace] deviceUser for', isapiDeviceId, ':', deviceUser)
        if (deviceUser) {
          console.info('[uploadFace] uploading face to device', isapiDeviceId, 'user', deviceUser.id)
          // employeeId omitted: profile image already saved above
          await deviceUserApi.uploadFace(isapiDeviceId, deviceUser.id, file)
          console.info('[uploadFace] uploaded face to device', isapiDeviceId)
        } else {
          errors.push(`Cihaz ${isapiDeviceId}: istifadəçi tapılmadı`)
        }
      } catch (e) {
        console.error('[uploadFace] error for device', isapiDeviceId, ':', e)
        errors.push(`Cihaz ${isapiDeviceId}: ${(e as Error).message}`)
      }
    }

    if (errors.length > 0) {
      console.info('[uploadFace] Hikvision sync warnings (profile photo saved):', errors)
    }
    return errors
  }

  const handleSave = async () => {
    if (!validateStep(1)) {
      setCurrentStep(1)
      return
    }
    if (!validateStep(2)) {
      setCurrentStep(2)
      return
    }
    setSaving(true)
    setFormError(null)
    try {
      const payload: Partial<Employee> = {
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        fatherName: form.fatherName,
        email: form.email,
        mobilePhone: form.mobilePhone,
        gender: form.gender,
        finNumber: form.finNumber.trim(),
        serialNumber: form.serialNumber,
        birthDate: form.birthDate || undefined,
        departmentId: Number(form.departmentId),
        positionId: form.positionId ? Number(form.positionId) : undefined,
        contractNumber: form.contractNumber,
        branchId: form.branchId ? Number(form.branchId) : undefined,
        hireDate: form.hireDate,
        contractEndDate: form.contractEndDate || undefined,
        annualLeaveDuration: form.annualLeaveDuration === '' ? undefined : Number(form.annualLeaveDuration),
        annualLeaveBalance: form.annualLeaveBalance === '' ? undefined : Number(form.annualLeaveBalance),
        employmentStatus: form.employmentStatus as 'ACTIVE' | 'INACTIVE' | 'ON_LEAVE',
        timetableId: Number(form.timetableId),
        shiftType: form.shiftType,
        salary: form.salary === '' ? undefined : Number(form.salary),
        hourlyRate: form.hourlyRate === '' ? undefined : Number(form.hourlyRate),
        allowance: form.allowance,
        emergencyContact: form.emergencyContact,
        address: form.address,
        notes: form.notes,
        area: form.area,
      }

      let savedEmployee: Employee | undefined
      if (editingEmployee) {
        const res = await employeeApi.update(editingEmployee.id, payload)
        savedEmployee = res.data?.data
      } else {
        const res = await employeeApi.create(payload)
        savedEmployee = res.data?.data
      }

      if (savedEmployee && wizardImageFile) {
        try {
          const freshRes = await employeeApi.getById(savedEmployee.id)
          if (freshRes.data?.data) {
            savedEmployee = freshRes.data.data
          }
        } catch (e) {
          console.info('[handleSave] refetch employee failed, using original', e)
        }
        try {
          const faceErrors = await uploadFaceForEmployee(savedEmployee, wizardImageFile)
          if (faceErrors.length > 0) {
            // Profile photo is already saved; device sync issues are non-blocking.
            console.info('[handleSave] Hikvision face sync warnings:', faceErrors)
          }
        } catch (e) {
          setFormError((e as Error).message || 'Profil şəkli yadda saxlanılmadı')
          setSaving(false)
          return
        }
      }

      await fetchEmployees(currentPage, 20)
      closeWizard()
      if (showProfileModal && selectedEmployee?.id === savedEmployee?.id) {
        openProfile(savedEmployee)
      }
    } catch (e: unknown) {
      setFormError(getApiErrorMessage(e, 'Saxlamaq alınmadı'))
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    if (!deleteConfirm) return
    setDeletingEmployee(true)
    setDeleteError(null)
    try {
      await deleteEmployee(deleteConfirm.id)
      setDeleteConfirm(null)
      if (selectedEmployee?.id === deleteConfirm.id) {
        closeProfile()
      }
    } catch (e: unknown) {
      setDeleteError(getApiErrorMessage(e, 'Əməkdaşı silmək alınmadı'))
    } finally {
      setDeletingEmployee(false)
    }
  }

  const openDeleteConfirm = (employee: Employee) => {
    setDeleteError(null)
    setDeleteConfirm(employee)
  }

  const closeDeleteConfirm = () => {
    if (deletingEmployee) return
    setDeleteError(null)
    setDeleteConfirm(null)
  }

  const handleSearch = () => {
    if (!search.trim()) {
      fetchEmployees(0, 20)
    }
  }

  const handleFaceUpload = async (employee: Employee, file: File | undefined, input?: HTMLInputElement) => {
    if (!file) return
    setUploadFaceError(null)
    setUploadingFaceEmployeeId(employee.id)
    try {
      await uploadFaceForEmployee(employee, file)
      await fetchEmployees(currentPage, 20)
    } catch (e: unknown) {
      setUploadFaceError((e as Error).message || 'Şəkil yüklənmədi')
    } finally {
      setUploadingFaceEmployeeId(null)
      if (input) {
        input.value = ''
      }
    }
  }

  const handleFaceDelete = async (employee: Employee) => {
    setUploadFaceError(null)
    setDeletingFaceEmployeeId(employee.id)
    try {
      const usersRes = await deviceUserApi.getAll(defaultDeviceId)
      const deviceUser = usersRes.data.find((u) =>
        matchesDevicePerson(employee.deviceEmployeeNo, employee.employeeId, u.employeeNo)
      )
      if (!deviceUser) {
        throw new Error(`Cihaz istifadəçi tapılmadı (${employee.deviceEmployeeNo || employee.employeeId})`)
      }
      await deviceUserApi.deleteFace(defaultDeviceId, deviceUser.id, employee.id)
      await fetchEmployees(currentPage, 20)
      if (selectedEmployee?.id === employee.id) {
        await openProfile(employee)
      }
    } catch (e: unknown) {
      if (extractStatusCode(e) === 404) {
        await fetchEmployees(currentPage, 20)
        if (selectedEmployee?.id === employee.id) {
          await openProfile(employee)
        }
      } else {
        setUploadFaceError((e as Error).message || 'Şəkil silinmədi')
      }
    } finally {
      setDeletingFaceEmployeeId(null)
    }
  }

  const onWizardPhotoSelected = (file: File) => {
    wizardImageLoadTokenRef.current += 1
    if (wizardImageObjectUrlRef.current) {
      URL.revokeObjectURL(wizardImageObjectUrlRef.current)
    }
    const previewUrl = URL.createObjectURL(file)
    wizardImageObjectUrlRef.current = previewUrl
    setWizardImageFile(file)
    setWizardImagePreview(previewUrl)
  }

  const filtered = employees.filter((e: Employee) => {
    const matchSearch = !search || `${e.firstName} ${e.lastName}`.toLowerCase().includes(search.toLowerCase()) || e.employeeId.toLowerCase().includes(search.toLowerCase())
    const matchDept = !filterDept || String(e.departmentId) === filterDept
    const matchStatus = !filterStatus || e.employmentStatus === filterStatus
    const matchShift = !filterShift || e.shiftType === filterShift || (
      filterShift === 'STANDARD' && ['MORNING', 'NIGHT', 'STANDARD'].includes((e.shiftType ?? '').toUpperCase())
    ) || (
      filterShift === 'FLEXIBLE' && ['FLEXIBLE', 'FIRST_ENTRY', 'SERBEST', 'FREE_SHIFT', 'FREE'].includes((e.shiftType ?? '').toUpperCase())
    )
    const matchBranch = !filterBranch || String(e.branchId) === filterBranch
    return matchSearch && matchDept && matchStatus && matchShift && matchBranch
  })

  const activeCount = employees.filter((e) => e.employmentStatus === 'ACTIVE').length
  const onLeaveCount = employees.filter((e) => e.employmentStatus === 'ON_LEAVE').length

  return (
    <Layout>
      <div className="p-4 sm:p-8" style={{ background: '#f8fafc', minHeight: '100vh' }}>
        {/* Header */}
        <div className="flex flex-wrap items-start justify-between gap-3 mb-6">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Bütün əməkdaşlar</h1>
            <p className="text-sm text-gray-500 mt-1">
              <span className="font-medium text-gray-700">{totalElements ?? employees.length}</span> ümumi ·&nbsp;
              <span className="text-green-600 font-medium">{activeCount} aktiv</span> ·&nbsp;
              <span className="text-yellow-600 font-medium">{onLeaveCount} məzuniyyətdə</span>
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => fetchEmployees(currentPage, 20)}
              className="flex items-center gap-1.5 px-3 py-2 text-sm font-medium text-gray-600 bg-white border border-gray-200 rounded-lg hover:bg-gray-50"
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
              Yenilə
            </button>
            <button
              onClick={openCreate}
              className="flex items-center gap-1.5 px-4 py-2 text-sm font-medium text-white rounded-lg"
              style={{ background: '#a855f7' }}
            >
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
              </svg>
              + Əməkdaş əlavə et
            </button>
          </div>
        </div>

        {/* Search + Filters */}
        <div className="bg-white rounded-xl shadow-sm p-4 mb-4 flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-2 flex-1 min-w-[200px] border border-gray-200 rounded-lg px-3 py-2">
            <svg className="w-4 h-4 text-gray-400 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
            <input
              type="text"
              placeholder="Əməkdaş axtar..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
              className="flex-1 outline-none text-sm text-gray-700 placeholder-gray-400"
            />
            {search && (
              <button onClick={() => { setSearch(''); fetchEmployees(0, 20) }} className="text-gray-400 hover:text-gray-600">
                <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            )}
          </div>

          <select value={filterDept} onChange={e => setFilterDept(e.target.value)} className="border border-gray-200 rounded-lg px-3 py-2 text-sm text-gray-600 focus:outline-none focus:ring-1 focus:ring-purple-400">
            <option value="">Bütün departamentlər</option>
            {departments.map(d => <option key={d.id} value={String(d.id)}>{d.departmentName}</option>)}
          </select>

          <select value={filterStatus} onChange={e => setFilterStatus(e.target.value)} className="border border-gray-200 rounded-lg px-3 py-2 text-sm text-gray-600 focus:outline-none focus:ring-1 focus:ring-purple-400">
            <option value="">Bütün statuslar</option>
            <option value="ACTIVE">Aktiv</option>
            <option value="INACTIVE">Deaktiv</option>
            <option value="ON_LEAVE">Məzuniyyətdə</option>
          </select>

          <select value={filterShift} onChange={e => setFilterShift(e.target.value)} className="border border-gray-200 rounded-lg px-3 py-2 text-sm text-gray-600 focus:outline-none focus:ring-1 focus:ring-purple-400">
            <option value="">Bütün növbələr</option>
            {UI_SHIFT_TYPES.map(s => (
              <option key={s} value={s}>{SHIFT_TYPE_LABELS[s] ?? s}</option>
            ))}
          </select>

          <select value={filterBranch} onChange={e => setFilterBranch(e.target.value)} className="border border-gray-200 rounded-lg px-3 py-2 text-sm text-gray-600 focus:outline-none focus:ring-1 focus:ring-purple-400">
            <option value="">Bütün filiallar</option>
            {branches.map(b => <option key={b.id} value={String(b.id)}>{b.name}</option>)}
          </select>
        </div>

        {/* Table */}
        {uploadFaceError && (
          <div className="bg-red-50 border border-red-200 text-red-700 px-3 py-2 rounded-lg mb-4 text-sm">{uploadFaceError}</div>
        )}
        {loading ? (
          <div className="bg-white rounded-xl shadow-sm p-12 text-center text-gray-400">
            <div className="w-8 h-8 border-2 border-purple-300 border-t-purple-600 rounded-full animate-spin mx-auto mb-3"></div>
            Yüklənir...
          </div>
        ) : error ? (
          <div className="bg-white rounded-xl shadow-sm p-8 text-center text-red-500">{error}</div>
        ) : filtered.length === 0 ? (
          <div className="bg-white rounded-xl shadow-sm p-12 text-center text-gray-400">
            {search ? 'Axtarışa uyğun əməkdaş tapılmadı.' : 'Hələ heç bir əməkdaş yoxdur.'}
          </div>
        ) : (
          <div className="bg-white rounded-xl shadow-sm overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead>
                <tr style={{ background: '#f9fafb' }}>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">ƏMƏLİYYATLAR</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">ŞƏKİL</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">ƏMƏKDAŞ ID</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">AD VƏ SOYAD</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">ATA ADI</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">DEPARTAMENT</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">VƏZİFƏ</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">FİLİAL</th>
                  <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">STATUS</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {filtered.map((emp: Employee) => {
                  const name = `${emp.firstName} ${emp.lastName}`
                  const initials = `${emp.firstName.charAt(0)}${emp.lastName.charAt(0)}`.toUpperCase()
                  const avatarColor = getAvatarColor(name)
                  return (
                    <tr key={emp.id} className="hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-1.5">
                          <button
                            onClick={() => openProfile(emp)}
                            className="p-1.5 rounded hover:bg-violet-50 transition-colors"
                            title="Məlumatlara bax"
                          >
                            <svg className="w-4 h-4" style={{ color: '#a855f7' }} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5s8.268 2.943 9.542 7c-1.274 4.057-5.065 7-9.542 7S3.732 16.057 2.458 12z" />
                            </svg>
                          </button>
                          <button
                            onClick={() => openEdit(emp)}
                            className="p-1.5 rounded hover:bg-purple-50 transition-colors"
                            title="Redaktə et"
                          >
                            <svg className="w-4 h-4" style={{ color: '#a855f7' }} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                            </svg>
                          </button>
                          <label className="p-1.5 rounded hover:bg-blue-50 transition-colors cursor-pointer" title="Şəkil yüklə">
                            <input
                              type="file"
                              accept="image/*"
                              className="hidden"
                              aria-label={`${emp.firstName} ${emp.lastName} üçün şəkil yüklə`}
                              onChange={(e) => {
                                handleFaceUpload(emp, e.target.files?.[0], e.currentTarget)
                              }}
                            />
                            <svg className={`w-4 h-4 ${uploadingFaceEmployeeId === emp.id ? 'animate-pulse' : ''}`} fill="none" stroke="currentColor" viewBox="0 0 24 24" style={{ color: '#2563eb' }}>
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 16l4-4a3 3 0 014.243 0L15 15.757m-2-2 1.586-1.586a3 3 0 014.243 0L21 14m-6-10h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                            </svg>
                          </label>
                          <button
                            onClick={() => handleFaceDelete(emp)}
                            className="p-1.5 rounded hover:bg-amber-50 transition-colors"
                            title="Şəkili sil"
                            disabled={deletingFaceEmployeeId === emp.id}
                          >
                            <svg className={`w-4 h-4 ${deletingFaceEmployeeId === emp.id ? 'animate-pulse' : ''}`} fill="none" stroke="currentColor" viewBox="0 0 24 24" style={{ color: '#d97706' }}>
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 7h4l2-2h6l2 2h4v12H3V7zm9 3a4 4 0 100 8 4 4 0 000-8z" />
                              <line x1="17" y1="7" x2="23" y2="1" strokeWidth={2} strokeLinecap="round" />
                              <line x1="23" y1="7" x2="17" y2="1" strokeWidth={2} strokeLinecap="round" />
                            </svg>
                          </button>
                          <button
                            onClick={() => openDeleteConfirm(emp)}
                            className="p-1.5 rounded hover:bg-red-50 transition-colors"
                            title="Sil"
                          >
                            <svg className="w-4 h-4 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                            </svg>
                          </button>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <EmployeeAvatar
                          faceImageUrl={emp.faceImageUrl}
                          initials={initials}
                          background={avatarColor}
                          alt={name}
                        />
                      </td>
                      <td className="px-4 py-3 font-mono text-xs text-gray-600">{emp.employeeId}</td>
                      <td className="px-4 py-3 font-semibold text-gray-800">{name}</td>
                      <td className="px-4 py-3 text-gray-600">{emp.fatherName || '—'}</td>
                      <td className="px-4 py-3 text-gray-600">{emp.departmentName || '—'}</td>
                      <td className="px-4 py-3 text-gray-600">{emp.positionName || '—'}</td>
                      <td className="px-4 py-3 text-gray-600">{emp.branchName || branchLabelById(emp.branchId)}</td>
                      <td className="px-4 py-3">
                        <span className="px-2 py-0.5 rounded-full text-xs font-medium"
                          style={emp.employmentStatus === 'ACTIVE'
                            ? { background: '#d1fae5', color: '#065f46' }
                            : emp.employmentStatus === 'ON_LEAVE'
                            ? { background: '#fef3c7', color: '#92400e' }
                            : { background: '#fee2e2', color: '#991b1b' }}
                        >
                          {emp.employmentStatus === 'ACTIVE' ? 'Aktiv' : emp.employmentStatus === 'ON_LEAVE' ? 'Məzuniyyətdə' : 'Deaktiv'}
                        </span>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="mt-4 flex items-center justify-between">
            <button onClick={() => fetchEmployees(currentPage - 1)} disabled={currentPage === 0} className="px-4 py-2 text-sm border border-gray-300 bg-white rounded-lg disabled:opacity-50 hover:bg-gray-50">
              Əvvəlki
            </button>
            <span className="text-sm text-gray-500">Səhifə {currentPage + 1} / {totalPages}</span>
            <button onClick={() => fetchEmployees(currentPage + 1)} disabled={currentPage >= totalPages - 1} className="px-4 py-2 text-sm border border-gray-300 bg-white rounded-lg disabled:opacity-50 hover:bg-gray-50">
              Növbəti
            </button>
          </div>
        )}
      </div>

      {showWizard && (
        <div className="fixed inset-0 z-50 bg-black/50 p-4 md:p-6 overflow-y-auto">
          <div className="min-h-full rounded-2xl bg-white p-6 md:p-8">
            <div className="flex items-start justify-between mb-6">
              <div>
                <h2 className="text-2xl font-bold text-gray-900">{editingEmployee ? 'Əməkdaşı redaktə et' : 'Yeni əməkdaş əlavə et'}</h2>
                <p className="text-sm text-gray-500 mt-1">3 addımda əməkdaş məlumatlarını tamamlayın</p>
              </div>
              <button onClick={closeWizard} className="p-2 rounded-lg hover:bg-gray-100 text-gray-500">
                <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-3 mb-6">
              {stepTitles.map((title, idx) => {
                const step = idx + 1
                const isActive = step === currentStep
                const isCompleted = step < currentStep
                return (
                  <button
                    type="button"
                    key={title}
                    onClick={() => setCurrentStep(step)}
                    disabled={step > currentStep}
                    className={`border rounded-lg p-3 flex items-center gap-3 text-left ${step > currentStep ? 'cursor-not-allowed' : 'cursor-pointer'}`}
                    style={
                      isCompleted
                        ? { borderColor: '#86efac', background: '#f0fdf4' }
                        : isActive
                        ? { borderColor: '#a855f7', background: '#f5edff' }
                        : { borderColor: '#e5e7eb', background: '#ffffff' }
                    }
                  >
                    <div
                      className="w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold"
                      style={
                        isCompleted
                          ? { background: '#16a34a', color: '#ffffff' }
                          : isActive
                          ? { background: '#a855f7', color: '#ffffff' }
                          : { background: '#e5e7eb', color: '#6b7280' }
                      }
                    >
                      {isCompleted ? '✓' : step}
                    </div>
                    <div className="text-xs font-semibold text-gray-700">{title}</div>
                  </button>
                )
              })}
            </div>

            {formError && (
              <div className="bg-red-50 border border-red-200 text-red-700 px-3 py-2 rounded-lg mb-4 text-sm">{formError}</div>
            )}

            {currentStep === 1 && (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">DAXİLİ ƏMƏKDAŞ ID</label>
                  <input value={employeeIdPreview} readOnly className="w-full border border-gray-300 rounded-lg px-3 py-2 bg-gray-50 text-sm" />
                </div>
                <div>
                  <label htmlFor="employee-fin" className="block text-xs font-semibold text-gray-500 mb-1">
                    FIN <span className="text-red-600">*</span>
                  </label>
                  <input
                    id="employee-fin"
                    value={form.finNumber}
                    onChange={(e) => setFormField('finNumber', e.target.value)}
                    aria-required="true"
                    aria-invalid={Boolean(fieldErrors.finNumber)}
                    aria-describedby={fieldErrors.finNumber ? 'employee-fin-error' : undefined}
                    className={`w-full border rounded-lg px-3 py-2 text-sm ${fieldErrors.finNumber ? 'border-red-500 focus:border-red-500' : 'border-gray-300'}`}
                  />
                  {fieldErrors.finNumber && (
                    <p id="employee-fin-error" className="mt-1 text-xs text-red-600" role="alert">{fieldErrors.finNumber}</p>
                  )}
                </div>
                <div>
                  <label htmlFor="employee-first-name" className="block text-xs font-semibold text-gray-500 mb-1">
                    AD <span className="text-red-600">*</span>
                  </label>
                  <input
                    id="employee-first-name"
                    value={form.firstName}
                    onChange={(e) => setFormField('firstName', e.target.value)}
                    aria-required="true"
                    aria-invalid={Boolean(fieldErrors.firstName)}
                    className={`w-full border rounded-lg px-3 py-2 text-sm ${fieldErrors.firstName ? 'border-red-500 focus:border-red-500' : 'border-gray-300'}`}
                  />
                  {fieldErrors.firstName && (
                    <p className="mt-1 text-xs text-red-600" role="alert">{fieldErrors.firstName}</p>
                  )}
                </div>
                <div>
                  <label htmlFor="employee-last-name" className="block text-xs font-semibold text-gray-500 mb-1">
                    SOYAD <span className="text-red-600">*</span>
                  </label>
                  <input
                    id="employee-last-name"
                    value={form.lastName}
                    onChange={(e) => setFormField('lastName', e.target.value)}
                    aria-required="true"
                    aria-invalid={Boolean(fieldErrors.lastName)}
                    className={`w-full border rounded-lg px-3 py-2 text-sm ${fieldErrors.lastName ? 'border-red-500 focus:border-red-500' : 'border-gray-300'}`}
                  />
                  {fieldErrors.lastName && (
                    <p className="mt-1 text-xs text-red-600" role="alert">{fieldErrors.lastName}</p>
                  )}
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">E-POÇT</label>
                  <input type="email" value={form.email} onChange={(e) => setFormField('email', e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">ATA ADI</label>
                  <input value={form.fatherName} onChange={(e) => setFormField('fatherName', e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">SERİYA NÖMRƏSİ</label>
                  <input value={form.serialNumber} onChange={(e) => setFormField('serialNumber', e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">DOĞUM TARİXİ</label>
                  <input type="date" value={form.birthDate} onChange={(e) => setFormField('birthDate', e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">TELEFON NÖMRƏSİ</label>
                  <input value={form.mobilePhone} onChange={(e) => setFormField('mobilePhone', e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">TƏCİLİ ƏLAQƏ</label>
                  <input value={form.emergencyContact} onChange={(e) => setFormField('emergencyContact', e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                </div>
                <div className="md:col-span-2">
                  <label className="block text-xs font-semibold text-gray-500 mb-1">ÜNVAN</label>
                  <input value={form.address} onChange={(e) => setFormField('address', e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                </div>
                <div className="md:col-span-2">
                  <label className="block text-xs font-semibold text-gray-500 mb-1">ƏLAVƏ QEYDLƏR</label>
                  <textarea value={form.notes} onChange={(e) => setFormField('notes', e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm h-24 resize-none" />
                </div>
              </div>
            )}

            {currentStep === 2 && (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">FİLİAL / OFİS MƏKANI</label>
                  <select
                    value={form.branchId}
                    onChange={(e) => {
                      const val = e.target.value ? Number(e.target.value) : ''
                      setFormField('branchId', val)
                      setFormField('departmentId', '')
                      setFormField('positionId', '')
                    }}
                    className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
                  >
                    <option value="">Seçin...</option>
                    {branches.map((b) => (
                      <option key={b.id} value={b.id}>{b.name}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">
                    DEPARTAMENT <span className="text-red-600">*</span>
                  </label>
                  <select
                    value={form.departmentId}
                    onChange={(e) => {
                      const val = e.target.value ? Number(e.target.value) : ''
                      setFormField('departmentId', val)
                      setFormField('positionId', '')
                    }}
                    aria-required="true"
                    aria-invalid={Boolean(fieldErrors.departmentId)}
                    className={`w-full border rounded-lg px-3 py-2 text-sm ${fieldErrors.departmentId ? 'border-red-500 focus:border-red-500' : 'border-gray-300'}`}
                  >
                    <option value="">Seçin...</option>
                    {departments
                      .filter((d) => !form.branchId || d.branchId === Number(form.branchId))
                      .map((d) => (
                        <option key={d.id} value={d.id}>{d.departmentName}</option>
                      ))}
                  </select>
                  {fieldErrors.departmentId && (
                    <p className="mt-1 text-xs text-red-600" role="alert">{fieldErrors.departmentId}</p>
                  )}
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">VƏZİFƏ</label>
                  <select
                    value={form.positionId}
                    onChange={(e) => setFormField('positionId', e.target.value ? Number(e.target.value) : '')}
                    className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm"
                  >
                    <option value="">Seçin...</option>
                    {positions
                      .filter((p) => !form.departmentId || p.departmentId === Number(form.departmentId))
                      .map((p) => (
                        <option key={p.id} value={p.id}>{p.positionName}</option>
                      ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">MÜQAVİLƏ NÖMRƏSİ</label>
                  <input value={form.contractNumber} onChange={(e) => setFormField('contractNumber', e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">İŞƏ BAŞLAMA TARİXİ</label>
                  <input type="date" value={form.hireDate} onChange={(e) => setFormField('hireDate', e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">MÜQAVİLƏ BİTMƏ TARİXİ</label>
                  <input type="date" value={form.contractEndDate} onChange={(e) => setFormField('contractEndDate', e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">İLLİK MƏZUNİYYƏT MÜDDƏTİ</label>
                  <input type="number" value={form.annualLeaveDuration} onChange={(e) => setFormField('annualLeaveDuration', e.target.value ? Number(e.target.value) : '')} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">İLLİK MƏZUNİYYƏT BALANSI</label>
                  <input type="number" value={form.annualLeaveBalance} onChange={(e) => setFormField('annualLeaveBalance', e.target.value ? Number(e.target.value) : '')} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">MƏŞĞULLUQ STATUSU</label>
                  <select value={form.employmentStatus} onChange={(e) => setFormField('employmentStatus', e.target.value)} className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm">
                    <option value="ACTIVE">Aktiv</option>
                    <option value="INACTIVE">Deaktiv</option>
                    <option value="ON_LEAVE">Məzuniyyətdə</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-500 mb-1">
                    İŞ CƏDVƏLİ <span className="text-red-600">*</span>
                  </label>
                  <select
                    value={form.timetableId}
                    onChange={(e) => {
                      const value = e.target.value ? Number(e.target.value) : ''
                      setFormField('timetableId', value)
                      if (value !== '') {
                        const selectedTimetable = timetables.find((t) => t.id === value)
                        if (selectedTimetable?.shiftType) {
                          setFormField('shiftType', selectedTimetable.shiftType)
                        }
                      }
                    }}
                    aria-required="true"
                    aria-invalid={Boolean(fieldErrors.timetableId)}
                    className={`w-full border rounded-lg px-3 py-2 text-sm ${fieldErrors.timetableId ? 'border-red-500 focus:border-red-500' : 'border-gray-300'}`}
                  >
                    <option value="">Seçin...</option>
                    {timetables.map((t) => (
                      <option key={t.id} value={t.id}>{t.name}</option>
                    ))}
                  </select>
                  {fieldErrors.timetableId && (
                    <p className="mt-1 text-xs text-red-600" role="alert">{fieldErrors.timetableId}</p>
                  )}
                </div>
              </div>
            )}

            {currentStep === 3 && (
              <EmployeePhotoCapture
                previewUrl={wizardImagePreview}
                onPhotoSelected={onWizardPhotoSelected}
              />
            )}

            <div className="flex items-center justify-between mt-8">
              <button onClick={closeWizard} className="px-4 py-2 text-sm border border-gray-300 rounded-lg hover:bg-gray-50">Ləğv et</button>
              <div className="flex gap-2">
                {currentStep > 1 && (
                  <button onClick={() => setCurrentStep((s) => s - 1)} className="px-4 py-2 text-sm border border-gray-300 rounded-lg hover:bg-gray-50">Əvvəlki</button>
                )}
                {currentStep < stepTitles.length ? (
                  <button onClick={handleNextStep} className="px-4 py-2 text-sm text-white rounded-lg" style={{ background: '#a855f7' }}>Növbəti</button>
                ) : (
                  <button onClick={handleSave} disabled={saving} className="px-4 py-2 text-sm text-white rounded-lg disabled:opacity-50" style={{ background: '#a855f7' }}>
                    {saving ? 'Saxlanılır...' : 'Yadda saxla'}
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {showProfileModal && selectedEmployee && (
        <EmployeeDetailModal
          employee={selectedEmployee}
          profileImageSrc={profileImageSrc}
          loading={profileLoading}
          error={profileError}
          branchLabel={branchLabelById(selectedEmployee.branchId)}
          onClose={closeProfile}
          onEdit={(emp) => { closeProfile(); openEdit(emp) }}
          onDelete={(emp) => openDeleteConfirm(emp)}
        />
      )}

      {/* Delete Confirmation */}
      {deleteConfirm && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-sm mx-4 p-6">
            <h2 className="text-lg font-bold text-gray-900 mb-2">Əməkdaşı sil</h2>
            <div className="flex items-center gap-3 mb-4">
              <EmployeeAvatar
                faceImageUrl={deleteConfirm.faceImageUrl}
                initials={`${deleteConfirm.firstName?.[0] || ''}${deleteConfirm.lastName?.[0] || ''}`.toUpperCase()}
                background={getAvatarColor(`${deleteConfirm.firstName} ${deleteConfirm.lastName}`)}
                sizeClass="w-16 h-16"
                textClass="text-lg"
                alt={`${deleteConfirm.firstName} ${deleteConfirm.lastName}`}
              />
              <p className="text-gray-600 text-sm">
                <strong>{deleteConfirm.firstName} {deleteConfirm.lastName}</strong> adlı əməkdaşı silmək istədiyinizdən əminsiniz? Bu əməliyyat geri alına bilməz.
              </p>
            </div>
            {deleteError && (
              <p className="mb-4 text-sm text-red-600" role="alert">{deleteError}</p>
            )}
            <div className="flex justify-end gap-3">
              <button
                onClick={closeDeleteConfirm}
                disabled={deletingEmployee}
                className="px-4 py-2 text-sm border border-gray-300 rounded-lg hover:bg-gray-50 disabled:opacity-50"
              >
                Ləğv et
              </button>
              <button
                onClick={handleDelete}
                disabled={deletingEmployee}
                className="px-4 py-2 text-sm bg-red-600 text-white rounded-lg hover:bg-red-700 disabled:opacity-50"
              >
                {deletingEmployee ? 'Silinir...' : 'Sil'}
              </button>
            </div>
          </div>
        </div>
      )}
    </Layout>
  )
}
