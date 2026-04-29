import apiClient from './client'
import type { AclConfig, AclTestRequest, AclTestResult } from '../types/acl'

export function getAclConfig(): Promise<AclConfig> {
  return apiClient.get('/config/acl')
}

export function testAcl(request: AclTestRequest): Promise<AclTestResult> {
  return apiClient.post('/config/acl/test', request)
}
