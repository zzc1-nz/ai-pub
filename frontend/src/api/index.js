import request from './request'

export const login = (data) => {
  return request({
    url: '/auth/login',
    method: 'post',
    data
  })
}

export const getStatistics = (params) => {
  return request({
    url: '/statistics',
    method: 'get',
    params
  })
}

export const getAccountList = (params) => {
  return request({
    url: '/wechat/accounts',
    method: 'get',
    params
  })
}

export const getAccount = (id) => {
  return request({
    url: `/wechat/accounts/${id}`,
    method: 'get'
  })
}

export const createAccount = (data) => {
  return request({
    url: '/wechat/accounts',
    method: 'post',
    data
  })
}

export const updateAccount = (id, data) => {
  return request({
    url: `/wechat/accounts/${id}`,
    method: 'put',
    data
  })
}

export const deleteAccount = (id) => {
  return request({
    url: `/wechat/accounts/${id}`,
    method: 'delete'
  })
}

export const getMenuList = (accountId) => {
  return request({
    url: '/wechat/menus',
    method: 'get',
    params: { accountId }
  })
}

export const createMenu = (data) => {
  return request({
    url: '/wechat/menus',
    method: 'post',
    data
  })
}

export const updateMenu = (id, data) => {
  return request({
    url: `/wechat/menus/${id}`,
    method: 'put',
    data
  })
}

export const deleteMenu = (id) => {
  return request({
    url: `/wechat/menus/${id}`,
    method: 'delete'
  })
}

export const syncMenu = (accountId) => {
  return request({
    url: `/wechat/menus/sync/${accountId}`,
    method: 'post'
  })
}

export const getMaterialList = (params) => {
  return request({
    url: '/wechat/materials',
    method: 'get',
    params
  })
}

export const uploadMaterial = (data) => {
  return request({
    url: '/wechat/materials/upload',
    method: 'post',
    data
  })
}

export const deleteMaterial = (id) => {
  return request({
    url: `/wechat/materials/${id}`,
    method: 'delete'
  })
}

export const getReplyRuleList = (params) => {
  return request({
    url: '/wechat/reply-rules',
    method: 'get',
    params
  })
}

export const createReplyRule = (data) => {
  return request({
    url: '/wechat/reply-rules',
    method: 'post',
    data
  })
}

export const updateReplyRule = (id, data) => {
  return request({
    url: `/wechat/reply-rules/${id}`,
    method: 'put',
    data
  })
}

export const deleteReplyRule = (id) => {
  return request({
    url: `/wechat/reply-rules/${id}`,
    method: 'delete'
  })
}

export const getMessageList = (params) => {
  return request({
    url: '/wechat/messages',
    method: 'get',
    params
  })
}

export const getUserList = (params) => {
  return request({
    url: '/sys/users',
    method: 'get',
    params
  })
}

export const createUser = (data) => {
  return request({
    url: '/sys/users',
    method: 'post',
    data
  })
}

export const updateUser = (id, data) => {
  return request({
    url: `/sys/users/${id}`,
    method: 'put',
    data
  })
}

export const deleteUser = (id) => {
  return request({
    url: `/sys/users/${id}`,
    method: 'delete'
  })
}
