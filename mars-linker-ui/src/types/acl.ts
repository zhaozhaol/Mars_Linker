export interface AclConfig {
  aclEnabled: boolean
  aclMode: string
  aclDefaultDeny: boolean
  allowSubscribePrefixes: string[]
  allowPublishPrefixes: string[]
  denySubscribePrefixes: string[]
  denyPublishPrefixes: string[]
  aclHttpUrl: string | null
  aclHttpRefreshIntervalMs: number
}

export interface AclTestRequest {
  topic: string
  action: 'subscribe' | 'publish'
}

export interface AclTestResult {
  topic: string
  action: string
  allowed: boolean
}
