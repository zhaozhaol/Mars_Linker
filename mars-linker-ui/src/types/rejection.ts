export type RejectionType = 'connect_refused' | 'acl_subscribe_denied' | 'acl_publish_denied'

export type RejectionReason =
  | 'protocol_name_invalid'
  | 'unsupported_protocol_level'
  | 'client_id_empty'
  | 'will_qos2_unsupported'
  | 'auth_failed'
  | 'acl_subscribe_denied'
  | 'acl_publish_denied'

export interface RejectionMessage {
  id: number
  timestamp: number
  clientId: string
  type: RejectionType
  reason: RejectionReason
  remoteAddress: string
  detail: string | null
  connackCode: number | null
}

export interface RejectionSummary {
  connectRefused: number
  aclSubscribeDenied: number
  aclPublishDenied: number
  total: number
}

export interface PagedResult<T> {
  items: T[]
  totalCount: number
  page: number
  size: number
}
