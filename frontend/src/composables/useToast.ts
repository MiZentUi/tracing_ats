import { reactive } from 'vue'

export type ToastType = 'success' | 'error' | 'info'

export interface Toast {
  id: number
  type: ToastType
  title: string
  message?: string
}

const toasts = reactive<Toast[]>([])
let nextId = 1

function remove(id: number) {
  const index = toasts.findIndex((toast) => toast.id === id)
  if (index >= 0) {
    toasts.splice(index, 1)
  }
}

function push(type: ToastType, title: string, message?: string) {
  const id = nextId++
  toasts.push({ id, type, title, message })
  window.setTimeout(() => remove(id), 4500)
}

export function useToast() {
  return {
    toasts,
    remove,
    success: (title: string, message?: string) => push('success', title, message),
    error: (title: string, message?: string) => push('error', title, message),
    info: (title: string, message?: string) => push('info', title, message),
  }
}
