import { ElMessage } from 'element-plus'
import { ApiError, ModuleDisabledError } from '../types/error'

export function useErrorHandler() {
  const handleError = (error: unknown, fallbackMessage = '操作失败，请重试') => {
    if (error instanceof ModuleDisabledError) {
      ElMessage.error('管理模块未启用，请在配置中开启 mars.linker.ui.enabled=true')
    } else if (error instanceof ApiError) {
      ElMessage.error(`服务异常（${error.status}）：${error.message}`)
    } else if (error instanceof Error && error.message.includes('Network Error')) {
      ElMessage.error('网络连接失败，请检查网络后重试')
    } else {
      ElMessage.error(fallbackMessage)
    }
  }

  return { handleError }
}
