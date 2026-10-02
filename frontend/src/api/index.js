import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

http.interceptors.response.use(
  (res) => res.data,
  (err) => {
    const msg = err?.response?.data?.message || err.message || '请求失败'
    return Promise.reject(new Error(msg))
  }
)

// 顶部时间轴（每天的「两票」票数徽章）
export const timeline = (from, to) => http.get('/dashboard/timeline', { params: { from, to } })

export const overview = (date) => http.get('/dashboard/overview', { params: { date } })

// 变电站 / 间隔 / 设备台账
export const substations = () => http.get('/substations')

export const bays = (substationId, date) =>
  http.get('/substations/bays', { params: { substationId, date } })

export const equipments = (bayId) => http.get('/substations/equipments', { params: { bayId } })

// 存储过程：按电压等级汇总设备台数与待检数
export const equipmentSummary = (substationId) =>
  http.get('/substations/equipment-summary', { params: { substationId } })

// 巡检与缺陷
export const inspectionPlans = (date) => http.get('/defects/inspection-plans', { params: { date } })

export const inspectionRecords = (date) => http.get('/defects/inspection-records', { params: { date } })

export const defects = (bayId) => http.get('/defects', { params: { bayId } })

export const overdueRanking = () => http.get('/defects/overdue-ranking')

// 存储过程：当月缺陷数与消缺率
export const defectSummary = (substationId, month) =>
  http.get('/defects/summary', { params: { substationId, month } })

export const registerDefect = (payload) => http.post('/defects', payload)

export const advanceDefect = (id, owner) => http.post(`/defects/${id}/advance`, null, { params: { owner } })

// 工作票（三审）
export const workTickets = (date) => http.get('/work-tickets', { params: { date } })

export const workTicket = (id) => http.get(`/work-tickets/${id}`)

export const workTicketStats = (date) => http.get('/work-tickets/status-stats', { params: { date } })

export const createWorkTicket = (payload) => http.post('/work-tickets', payload)

export const signWorkTicket = (id, stage, person) => http.post(`/work-tickets/${id}/sign`, { stage, person })

// 安全措施逐项执行闭环
export const installMeasure = (id, measureId, person) =>
  http.post(`/work-tickets/${id}/measures/${measureId}/install`, { person })

export const removeMeasure = (id, measureId, person) =>
  http.post(`/work-tickets/${id}/measures/${measureId}/remove`, { person })

export const startRemoveMeasures = (id, person) =>
  http.post(`/work-tickets/${id}/start-remove`, { person })

export const withdrawPermit = (id, person, reason) =>
  http.post(`/work-tickets/${id}/withdraw`, { person, reason })

export const changeMeasure = (id, measureId, person, reason, measure) =>
  http.post(`/work-tickets/${id}/measures/${measureId}/change`, { person, reason, measure })

export const appendMeasure = (id, person, reason, measure) =>
  http.post(`/work-tickets/${id}/measures`, { person, reason, measure })

export const cancelWorkTicket = (id, person, reason) =>
  http.post(`/work-tickets/${id}/cancel`, { person, reason })

// 操作票（倒闸操作步骤）
export const operationTickets = (date) => http.get('/operation-tickets', { params: { date } })

export const allOperationTicketsBrief = () => http.get('/operation-tickets/all-brief')

export const operationTicket = (id) => http.get(`/operation-tickets/${id}`)

export const executeStep = (id, stepNo, person) =>
  http.post(`/operation-tickets/${id}/steps/${stepNo}/execute`, { person })

export const failStep = (id, stepNo, person, reason) =>
  http.post(`/operation-tickets/${id}/steps/${stepNo}/fail`, { person, reason })

export default http
