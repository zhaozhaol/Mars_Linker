export class ApiError extends Error {
  public readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export class ModuleDisabledError extends Error {
  constructor() {
    super('UI 管理模块未启用')
    this.name = 'ModuleDisabledError'
  }
}
