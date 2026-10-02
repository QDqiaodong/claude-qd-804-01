<template>
  <div class="ops-page">
    <!-- ============ 存储过程：当月缺陷数与消缺率 ============ -->
    <div class="ops-row">
      <div class="ops-block ops-flex-1">
        <div class="ops-title">
          当月缺陷数与消缺率（存储过程 sp_defect_summary）
          <span class="ops-sub">{{ month }} · 仅「验收通过」计为闭环，待验收/驳回返工计入未闭环</span>
        </div>
        <el-table :data="summary" size="small" border>
          <el-table-column prop="substationName" label="变电站" min-width="120" />
          <el-table-column prop="totalCount" label="当月登记" width="90" />
          <el-table-column prop="closedCount" label="已闭环" width="80" />
          <el-table-column prop="overdueCount" label="超期未消" width="90" />
          <el-table-column prop="openCount" label="在期在建" width="90" />
          <el-table-column label="消缺率" min-width="200">
            <template #default="{ row }">
              <el-progress
                :percentage="Number(row.fixRate || 0)"
                :stroke-width="12"
                :text-inside="true"
                :color="row.fixRate >= 60 ? '#2e7d32' : '#e65100'"
              />
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- ============ 缺陷超期排行 ============ -->
    <div class="ops-block" style="margin-top: 14px">
      <div class="ops-title">
        缺陷超期排行
        <span class="ops-sub">按超期天数降序 · 前三名标红 · 待验收/返工仍在榜 · 点条展开消缺闭环</span>
      </div>

      <div v-if="!rows.length" class="ops-empty">当前没有超期缺陷，现场态势良好</div>

      <div v-for="(r, idx) in rows" :key="r.id" class="rank-wrap">
        <div class="rank-row" :class="{ top3: idx < 3, open: expanded === r.id }" @click="toggle(r)">
          <div class="rank-idx" :class="{ red: idx < 3 }">{{ idx + 1 }}</div>
          <div class="rank-main">
            <div class="rank-top">
              <span class="rank-no">{{ r.defectNo }}</span>
              <el-tag size="small" :type="levelTag(r.level)">{{ levelName(r.level) }}</el-tag>
              <el-tag size="small" :type="statusTag(r.status)" effect="plain">{{ statusName(r.status) }}</el-tag>
              <span class="rank-title">{{ r.title }}</span>
            </div>
            <div class="rank-bar-outer">
              <div class="rank-bar" :style="{ width: barWidth(r) + '%', background: idx < 3 ? '#c62828' : '#ff9800' }"></div>
            </div>
          </div>
          <div class="rank-days" :class="{ red: idx < 3 }">
            超期 <b>{{ r.overdueDays }}</b> 天
          </div>
          <div class="rank-due">限期 {{ r.dueDate }}</div>
          <div class="rank-arrow">{{ expanded === r.id ? '▲' : '▼' }}</div>
        </div>

        <!-- 展开：详情 + 独立的处理 / 验收操作 -->
        <div v-if="expanded === r.id" class="rank-detail">
          <el-descriptions :column="3" size="small" border>
            <el-descriptions-item label="缺陷单号">{{ detail.defectNo }}</el-descriptions-item>
            <el-descriptions-item label="所属间隔">{{ detail.bayName }} · {{ detail.voltageLevel }}</el-descriptions-item>
            <el-descriptions-item label="缺陷设备">{{ detail.equipmentNo || '—' }}</el-descriptions-item>
            <el-descriptions-item label="发现时间">{{ fmt(detail.foundTime) }}</el-descriptions-item>
            <el-descriptions-item label="发现人">{{ detail.reporter }}</el-descriptions-item>
            <el-descriptions-item label="消缺负责人（处理人）">{{ detail.owner || '未派工' }}</el-descriptions-item>
            <el-descriptions-item label="限期消缺">{{ detail.dueDate }}</el-descriptions-item>
            <el-descriptions-item label="当前环节">
              <el-tag size="small" :type="statusTag(detail.status)">{{ detail.statusName }}</el-tag>
              <span v-if="detail.reworkCount > 0" class="rework-badge">已返工 {{ detail.reworkCount }} 次</span>
            </el-descriptions-item>
            <el-descriptions-item label="闭环时间">{{ fmt(detail.closedTime) }}<template v-if="detail.acceptedBy">（验收人 {{ detail.acceptedBy }}）</template></el-descriptions-item>
            <el-descriptions-item label="缺陷描述" :span="3">{{ detail.description || '—' }}</el-descriptions-item>
            <el-descriptions-item v-if="detail.processResult" label="本次处理结果" :span="3">
              <div>{{ detail.processResult }}</div>
              <div class="result-meta">
                {{ detail.processedBy }} 于 {{ fmt(detail.processedTime) }} 提交
                <span v-if="detail.status === 'PENDING_ACCEPTANCE'" class="result-waiting">· 待另一名值班员验收，尚未闭环</span>
              </div>
            </el-descriptions-item>
          </el-descriptions>

          <div class="next-tip">下一步：{{ detail.waitingText }}</div>

          <div class="close-flow">
            <div
              v-for="(st, i) in FLOW"
              :key="st.key"
              class="flow-node"
              :class="{
                on: flowLit(detail.status, st.key),
                current: detail.status === st.key,
                reject: detail.status === 'PROCESSING' && st.key === 'PENDING_ACCEPTANCE' && detail.reworkCount > 0
              }"
            >
              <span class="flow-dot">{{ i + 1 }}</span>{{ st.label }}
            </div>
          </div>

          <!-- 操作区：严格按后端 canXxx 标志点亮，刷新后显示真实阶段 -->
          <div class="action-area">
            <!-- 已登记：派工 -->
            <template v-if="detail.canDispatch">
              <el-input v-model="ownerInput" size="small" placeholder="派工负责人（处理人）" style="width: 200px" />
              <el-button type="primary" size="small" :loading="busy" @click="doDispatch(detail.id)">派工给该负责人</el-button>
            </template>

            <!-- 已派工：开工 -->
            <template v-else-if="detail.canStartProcess">
              <el-button type="primary" size="small" :loading="busy" @click="doStart(detail.id)">
                由 {{ detail.owner }} 开工处理
              </el-button>
            </template>

            <!-- 处理中 / 驳回返工：处理人提交结果 -->
            <template v-else-if="detail.canSubmitResult">
              <span v-if="detail.reworkCount > 0" class="reject-hint">
                上轮验收被驳回（{{ latestRejectReason }}），请由 {{ detail.owner }} 处理后重新提交
              </span>
              <el-input
                v-model="resultInput"
                type="textarea"
                :rows="2"
                size="small"
                placeholder="填写本次处理情况 / 消缺结果，提交后进入「待验收」，由另一名值班员验收"
                style="width: 460px"
              />
              <div class="action-line">
                <el-input v-model="processorInput" size="small" placeholder="提交人（须为处理人本人）" style="width: 200px" />
                <el-button type="primary" size="small" :loading="busy" @click="doSubmit(detail.id)">提交结果，报完工</el-button>
                <span class="action-hint">提交后不闭环，仍在超期排行、仍阻断工作票终结</span>
              </div>
            </template>

            <!-- 待验收：另一名值班员独立填写结论 -->
            <template v-else-if="detail.canAccept">
              <div class="accept-box">
                <div class="accept-head">
                  验收结论（验收人不得为处理人「{{ detail.processedBy }}」）
                </div>
                <el-input
                  v-model="opinionInput"
                  type="textarea"
                  :rows="2"
                  size="small"
                  :placeholder="acceptPass ? '验收意见（可空）' : '驳回原因（必填，处理人将据此返工）'"
                  style="width: 460px"
                />
                <div class="action-line">
                  <el-input v-model="acceptorInput" size="small" placeholder="验收人签名（另一名值班员）" style="width: 200px" />
                  <el-button type="success" size="small" :loading="busy" @click="doAccept(detail.id, true)">验收通过，闭环</el-button>
                  <el-button type="danger" plain size="small" :loading="busy" @click="doAccept(detail.id, false)">驳回返工</el-button>
                </div>
              </div>
            </template>

            <!-- 已闭环 -->
            <div v-else-if="detail.closed" class="closed-tip">
              ✓ 已由验收人 {{ detail.acceptedBy }} 验收闭环（{{ fmt(detail.closedTime) }}），已移出超期排行并解除工作票终结阻断
            </div>
          </div>

          <!-- 历轮处理 / 验收记录（驳回返工全程留痕） -->
          <div v-if="detail.acceptances && detail.acceptances.length" class="history">
            <div class="history-title">处理 / 验收记录（只增不改）</div>
            <el-timeline>
              <el-timeline-item
                v-for="a in detail.acceptances"
                :key="a.id"
                :type="a.result === 'PASS' ? 'success' : a.result === 'REJECTED' ? 'danger' : 'warning'"
                :hollow="a.pending"
                :timestamp="`第 ${a.roundNo} 轮 · ${a.processedBy} 提交于 ${fmt(a.submittedTime)}`"
              >
                <div class="hist-result">处理结果：{{ a.processResult }}</div>
                <div v-if="!a.pending" class="hist-accept">
                  <el-tag size="small" :type="a.result === 'PASS' ? 'success' : 'danger'">{{ a.resultName }}</el-tag>
                  验收人：{{ a.acceptedBy }} · {{ fmt(a.acceptedTime) }}
                  <span v-if="a.acceptOpinion">—— {{ a.acceptOpinion }}</span>
                </div>
                <div v-else class="hist-accept pending-text">待验收：等待处理人之外的值班员填写结论</div>
              </el-timeline-item>
            </el-timeline>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  acceptDefect,
  defectSummary,
  defects,
  dispatchDefect,
  overdueRanking,
  startProcessDefect,
  submitDefectResult
} from '../api'

const props = defineProps({
  date: { type: String, required: true },
  substationId: { type: [Number, String, null], default: null }
})

// 登记 → 派工 → 处理 → 待验收 → 验收闭环（驳回时从待验收退回处理，轨迹保留）
const FLOW = [
  { key: 'REGISTERED', label: '登记' },
  { key: 'DISPATCHED', label: '派工' },
  { key: 'PROCESSING', label: '处理' },
  { key: 'PENDING_ACCEPTANCE', label: '待验收' },
  { key: 'ACCEPTED', label: '验收闭环' }
]

const rows = ref([])
const summary = ref([])
const expanded = ref(null)
const detail = ref({})
const busy = ref(false)

const ownerInput = ref('')
const processorInput = ref('')
const resultInput = ref('')
const acceptorInput = ref('')
const opinionInput = ref('')
const acceptPass = ref(true)

const month = computed(() => (props.date || '').slice(0, 7))

const STATUS_NAME = {
  REGISTERED: '已登记',
  DISPATCHED: '已派工',
  PROCESSING: '处理中',
  PENDING_ACCEPTANCE: '待验收',
  ACCEPTED: '已验收'
}

const latestRejectReason = computed(() => {
  const list = detail.value.acceptances || []
  for (let i = list.length - 1; i >= 0; i--) {
    if (list[i].result === 'REJECTED') {
      return list[i].acceptOpinion || '未填写原因'
    }
  }
  return ''
})

function fmt(v) {
  return v ? String(v).replace('T', ' ') : '—'
}

function levelName(level) {
  return { URGENT: '紧急', MAJOR: '重大', MINOR: '一般' }[level] || level
}

function levelTag(level) {
  return { URGENT: 'danger', MAJOR: 'warning', MINOR: 'info' }[level] || 'info'
}

function statusName(status) {
  return STATUS_NAME[status] || status
}

function statusTag(status) {
  return {
    REGISTERED: 'info',
    DISPATCHED: 'info',
    PROCESSING: 'warning',
    PENDING_ACCEPTANCE: 'warning',
    ACCEPTED: 'success'
  }[status] || 'info'
}

/** 流程图点亮规则：返工后仍在「处理中」，但待验收节点保留已到达痕迹 */
function flowLit(status, key) {
  const idx = FLOW.findIndex((f) => f.key === status)
  const nodeIdx = FLOW.findIndex((f) => f.key === key)
  if (status === 'PROCESSING' && (detail.value.reworkCount > 0 || (detail.value.acceptances || []).length > 0)) {
    // 驳回返工：前 3 节点亮，待验收节点不亮（等待重新提交）
    return nodeIdx <= 2
  }
  return idx >= 0 && nodeIdx <= idx
}

function barWidth(r) {
  const max = Math.max(1, ...rows.value.map((i) => i.overdueDays))
  return Math.max(8, Math.round((r.overdueDays / max) * 100))
}

async function toggle(r) {
  if (expanded.value === r.id) {
    expanded.value = null
    return
  }
  await reloadDetail(r)
  expanded.value = r.id
}

/** 详情始终从后端重取：刷新/并发后提交者看到的就是库内真实阶段与下一步 */
async function reloadDetail(r) {
  const res = await defects(r.bayId)
  const found = (res.data || []).find((d) => d.id === r.id)
  if (!found) {
    // 已被其他人验收闭环并移出超期排行
    detail.value = {}
    expanded.value = null
    ElMessage.info('该缺陷已被验收闭环并移出超期排行')
    return
  }
  detail.value = found
  ownerInput.value = found.owner || ''
  processorInput.value = found.owner || ''
  resultInput.value = found.status === 'PROCESSING' ? (found.processResult || '') : ''
  acceptorInput.value = ''
  opinionInput.value = ''
  acceptPass.value = true
}

async function afterAction(id, successMsg) {
  const keep = rows.value.find((r) => r.id === id)
  await load()
  if (keep) {
    const stillThere = rows.value.find((r) => r.id === id)
    if (stillThere) {
      await reloadDetail(stillThere)
    } else {
      // 验收通过闭环 → 移出超期排行
      detail.value = {}
      expanded.value = null
    }
  }
  if (successMsg) {
    ElMessage.success(successMsg)
  }
}

async function doDispatch(id) {
  if (!ownerInput.value.trim()) {
    ElMessage.warning('请填写派工负责人')
    return
  }
  busy.value = true
  try {
    await dispatchDefect(id, ownerInput.value.trim())
    await afterAction(id, '已派工，等待开工处理')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    busy.value = false
  }
}

async function doStart(id) {
  busy.value = true
  try {
    await startProcessDefect(id)
    await afterAction(id, '已开工处理')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    busy.value = false
  }
}

async function doSubmit(id) {
  if (!processorInput.value.trim()) {
    ElMessage.warning('请填写提交人（须为处理人本人）')
    return
  }
  if (!resultInput.value.trim()) {
    ElMessage.warning('请填写本次处理情况 / 消缺结果')
    return
  }
  busy.value = true
  try {
    await submitDefectResult(id, processorInput.value.trim(), resultInput.value.trim())
    await afterAction(id, '处理结果已提交，进入待验收')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    busy.value = false
  }
}

async function doAccept(id, pass) {
  acceptPass.value = pass
  if (!acceptorInput.value.trim()) {
    ElMessage.warning('请填写验收人（须为处理人之外的值班员）')
    return
  }
  if (!pass && !opinionInput.value.trim()) {
    ElMessage.warning('驳回返工必须填写驳回原因')
    return
  }
  busy.value = true
  try {
    await acceptDefect(id, acceptorInput.value.trim(), pass, opinionInput.value.trim() || null)
    await afterAction(id, pass ? '验收通过，缺陷闭环' : '已驳回，缺陷退回处理中返工')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    busy.value = false
  }
}

async function load() {
  try {
    const [rankRes, sumRes] = await Promise.all([
      overdueRanking(),
      defectSummary(props.substationId || null, month.value)
    ])
    rows.value = rankRes.data || []
    summary.value = sumRes.data || []
    // 当前展开的缺陷若已被他人验收移出排行，收起面板避免展示陈旧阶段
    if (expanded.value && !rows.value.some((r) => r.id === expanded.value)) {
      expanded.value = null
      detail.value = {}
    }
  } catch (e) {
    ElMessage.error(`缺陷数据加载失败：${e.message}`)
  }
}

watch(() => [props.date, props.substationId], load, { immediate: true })
</script>

<style scoped>
.rank-wrap {
  margin-bottom: 8px;
}

.rank-row {
  display: flex;
  align-items: center;
  gap: 12px;
  border: 1px solid var(--ops-line);
  border-radius: 8px;
  padding: 9px 12px;
  cursor: pointer;
  background: #fff;
  transition: all 0.15s;
}

.rank-row:hover {
  border-color: var(--ops-orange);
}

.rank-row.top3 {
  border-left: 4px solid #c62828;
}

.rank-row.open {
  border-radius: 8px 8px 0 0;
  background: var(--ops-orange-soft);
}

.rank-idx {
  width: 26px;
  height: 26px;
  border-radius: 6px;
  background: #efebe9;
  color: var(--ops-ink-2);
  font-weight: 800;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}

.rank-idx.red {
  background: #c62828;
  color: #fff;
}

.rank-main {
  flex: 1;
  min-width: 0;
}

.rank-top {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
}

.rank-no {
  color: var(--ops-orange-dark);
  font-weight: 700;
}

.rank-title {
  color: var(--ops-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-bar-outer {
  height: 9px;
  background: #efebe9;
  border-radius: 5px;
  margin-top: 5px;
  overflow: hidden;
}

.rank-bar {
  height: 100%;
  border-radius: 5px;
  transition: width 0.4s ease;
}

.rank-days {
  width: 96px;
  text-align: right;
  font-size: 12px;
  color: var(--ops-ink-2);
  flex: none;
}

.rank-days b {
  font-size: 17px;
  color: #c62828;
}

.rank-due {
  width: 116px;
  font-size: 11.5px;
  color: var(--ops-grey);
  flex: none;
}

.rank-arrow {
  width: 14px;
  color: var(--ops-orange);
  font-size: 11px;
  flex: none;
}

.rank-detail {
  border: 1px solid var(--ops-line);
  border-top: none;
  border-radius: 0 0 8px 8px;
  padding: 12px;
  background: #fffaf7;
}

.rework-badge {
  margin-left: 8px;
  color: #c62828;
  font-size: 12px;
  font-weight: 700;
}

.result-meta {
  margin-top: 4px;
  color: var(--ops-grey);
  font-size: 12px;
}

.result-waiting {
  color: #b26a00;
}

.next-tip {
  margin: 10px 0 4px;
  padding: 7px 10px;
  background: #fff3e0;
  border-left: 3px solid var(--ops-orange);
  border-radius: 4px;
  font-size: 12.5px;
  color: var(--ops-orange-dark);
}

.close-flow {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.flow-node {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12.5px;
  color: #9e9e9e;
}

.flow-node.on {
  color: var(--ops-orange-dark);
  font-weight: 700;
}

.flow-node.current {
  text-decoration: underline;
}

.flow-node.reject .flow-dot {
  background: #c62828;
  border-color: #c62828;
}

.flow-dot {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 1px solid var(--ops-line);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  background: #fafafa;
}

.flow-node.on .flow-dot {
  background: var(--ops-orange);
  border-color: var(--ops-orange);
  color: #fff;
}

.action-area {
  margin-top: 12px;
  padding: 10px;
  border: 1px dashed var(--ops-line);
  border-radius: 6px;
  background: #fff;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.action-line {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.action-hint {
  font-size: 11.5px;
  color: var(--ops-grey);
}

.reject-hint {
  width: 100%;
  font-size: 12.5px;
  color: #c62828;
  font-weight: 700;
}

.accept-box {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.accept-head {
  font-size: 12.5px;
  font-weight: 700;
  color: var(--ops-orange-dark);
}

.closed-tip {
  color: #2e7d32;
  font-size: 12.5px;
  font-weight: 700;
}

.history {
  margin-top: 14px;
}

.history-title {
  font-size: 12.5px;
  font-weight: 700;
  color: var(--ops-ink);
  margin-bottom: 8px;
}

.hist-result {
  font-size: 12.5px;
  color: var(--ops-ink);
}

.hist-accept {
  margin-top: 3px;
  font-size: 12px;
  color: var(--ops-ink-2);
}

.hist-accept.pending-text {
  color: #b26a00;
}
</style>
