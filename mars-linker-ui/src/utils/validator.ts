import type { FormItemRule } from 'element-plus'

export const monitorRefreshMsRules: FormItemRule[] = [
  { required: true, message: '请输入刷新间隔', trigger: 'blur' },
  {
    type: 'number',
    validator: (_rule, value, callback) => {
      if (!Number.isInteger(value)) {
        callback(new Error('请输入正整数'))
      } else if (value < 1000) {
        callback(new Error('刷新间隔不得小于 1000 毫秒'))
      } else {
        callback()
      }
    },
    trigger: 'blur'
  }
]

export const collectModeRules: FormItemRule[] = [
  { required: true, message: '采集模式不能为空', trigger: 'blur' }
]
