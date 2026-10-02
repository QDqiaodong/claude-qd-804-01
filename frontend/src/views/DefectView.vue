<template>
  <div class="ops-page">
    <!-- ============ 存储过程：当月缺陷数与消缺率 ============ -->
    <div class="ops-row">
      <div class="ops-block ops-flex-1">
        <div class="ops-title">
          当月缺陷数与消缺率（存储过程 sp_defect_summary）
          <span class="ops-sub">{{ month }} · 仅验收通过计入闭环</span>
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
        <span class="ops-sub">按超期天数降序 · 前三名标红 · 点条展开「派工 → 处理 → 待验收 → 独立验收」</span>
      </div>

      <div v-if="!rows.length" class="ops-empty">当前没有超期缺陷，现场态势良好</div>

      <div v-for="(r, idx) in rows" :key="r.id" class="rank-wrap">
        <div class="rank-row" :class="{ top3: idx < 3, open: expanded === r.id }" @click="toggle(r)">
          <div class="rank-idx" :class="{ red: idx < 3 }">{{ idx + 1 }}</div>
          <div class="rank-main">
            <div class="rank-top">
              <span class="rank-no">{{ r.defectNo }}</span>
              <el-tag size="small" :type="levelTag(r.level)">{{ levelName(r.level) }}</el-tag>
              <el-tag size="small" :type="stageTag(r.status)" effect="plain">{{ r.statusName }}</el-tag>
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

        <!-- 展开：详情 + 消缺/验收闭环 -->
        <div v-if="expanded === r.id" class="rank-detail">
          <el-descriptions :column="3" size="small" border>
            <el-descriptions-item label="缺陷单号">{{ detail.defectNo }}</el-descriptions-item>
            <el-descriptions-item label="所属间隔">{{ detail.bayName }} · {{ detail.voltageLevel }}</el-descriptions-item>
            <el-descriptions-item label="缺陷设备">{{ detail.equipmentNo || '—' }}</el-descriptions-item>
            <el-descriptions-item label="发现时间">{{ fmt(detail.foundTime) }}</el-descriptions-item>
            <el-descriptions-item label="发现人">{{ detail.reporter }}</el-descriptions-item>
            <el-descriptions-item label="消缺负责人">{{ detail.owner || '未派工' }}</el-descriptions-item>
            <el-descriptions-item label="限期消缺">{{ detail.dueDate }}</el-descriptions-item>
            <el-descriptions-item label="当前环节">
              <el-tag size="small" :type="stageTag(detail.status)">{{ detail.statusName }}</el-tag>
              <span v-if="detail.rejectCount > 0" class="reject-badge">已驳回 {{ detail.rejectCount }} 次</span>
            </el-descriptions-item>
            <el-descriptions-item label="闭环时间">{{ fmt(detail.closedTime) }}</el-descriptions-item>
            <el-descriptions-item label="缺陷描述" :span="3">{{ detail.description || '—' }}</el-descriptions-item>
          </el-descriptions>

          <!-- 流程节点：登记 → 派工 → 处理 → 待验收 → 已验收（驳回会回到「处理」节点） -->
          <div class="close-flow">
            <div
              v-for="(st, i) in FLOW"
              :key="st.key"
              class="flow-node"
              :class="{ on: flowIndex(detail.status) >= i, current: flowIndex(detail.status) === i }"
            >
              <span class="flow-dot">{{ i + 1 }}</span>{{ st.label }}
            </div>
          </div>

          <!-- ================= 按真实阶段给出的操作区（刷新后与后端口径一致） ================= -->
          <div class="action-box">
            <!-- 已登记：派工 -->
            <template v-if="detail.canDispatch">
              <el-input v-model="ownerInput" size="small" placeholder="派工负责人姓名" style="width: 160px" />
              <el-button type="primary" size="small" :loading="busy" @click="doDispatch(r.id)">
                派工给负责人
              </el-button>
            </template>

            <!-- 已派工：开始处理 -->
            <template v-else-if="detail.canStartProcess">
              <el-button type="primary" size="small" :loading="busy" @click="doStartProcess(r.id)">
                负责人「{{ detail.owner }}」开始处理
              </el-button>
            </template>

            <!-- 处理中（含驳回返工）：处理人报完工，提交后进入待验收，不能自己验收 -->
            <template v-else-if="detail.canSubmitResult">
              <div class="action-hint">
                {{ detail.rejectCount > 0 ? '验收被驳回，请处理人返工后重新提交处理结果' : '处理完成后由处理人提交结果，进入待验收（处理人本人不能验收）' }}
              </div>
              <el-input v-model="processForm.person" size="small" placeholder="处理人姓名" style="width: 140px" />
              <el-input
                v-model="processForm.result"
                size="small"
                type="textarea"
                :rows="2"
                placeholder="本次处理结果：处理措施、更换部件、试验结论……"
                style="width: 420px"
              />
              <el-button type="primary" size="small" :loading="busy" @click="doSubmitResult(r.id)">
                {{ detail.rejectCount > 0 ? '返工完成，重新提交验收' : '提交处理结果（报完工）' }}
              </el-button>
            </template>

            <!-- 待验收：必须由与处理人不同的值班员独立验收 -->
            <template v-else-if="detail.canAccept">
              <div class="action-hint warn">
                待独立验收：处理人「{{ detail.processPerson }}」已报完工，请由<b>另一名值班员</b>填写结论；通过才闭环，驳回退回处理中并保留本次记录。
              </div>
              <el-input v-model="acceptForm.person" size="small" placeholder="验收值班员姓名（≠ 处理人）" style="width: 200px" />
              <el-radio-group v-model="acceptForm.verdict" size="small">
                <el-radio-button value="PASS">验收通过</el-radio-button>
                <el-radio-button value="REJECT">驳回返工</el-radio-button>
              </el-radio-group>
              <el-input
                v-model="acceptForm.comment"
                size="small"
                type="textarea"
                :rows="2"
                :placeholder="acceptForm.verdict === 'REJECT' ? '驳回原因（必填）：说明未通过验收的问题' : '验收意见（选填）'"
                style="width: 420px"
              />
              <el-button
                :type="acceptForm.verdict === 'REJECT' ? 'danger' : 'primary'"
                size="small"
                :loading="busy"
                @click="doAccept(r.id)"
              >
                {{ acceptForm.verdict === 'REJECT' ? '驳回并退回处理中' : '验收通过并闭环' }}
              </el-button>
            </template>

            <!-- 已验收闭环 -->
            <template v-else>
              <el-tag type="success" size="small">已验收闭环 · 验收人 {{ detail.acceptPerson || '—' }}</el-tag>
            </template>
          </div>

          <!-- ================= 历次处理/验收留痕 ================= -->
          <div v-if="detail.rounds && detail.rounds.length" class="round-box">
            <div class="round-title">处理 / 验收记录（驳回返工全过程保留）</div>
            <el-timeline>
              <el-timeline-item
                v-for="rd in detail.rounds"
                :key="rd.id"
                :type="roundTagType(rd.status)"
                :hollow="rd.status === 'SUBMITTED'"
                :timestamp="`第 ${rd.roundNo} 轮`"
                placement="top"
              >
                <div class="round-line">
                  <el-tag size="small" :type="roundTagType(rd.status)" effect="plain">{{ rd.statusName }}</el-tag>
                  <span class="round-person">处理人：{{ rd.processPerson }}</span>
                  <span class="round-time">报完工 {{ fmt(rd.processTime) }}</span>
                </div>
                <div class="round-result">处理结果：{{ rd.processResult }}</div>
                <div v-if="rd.acceptPerson" class="round-line">
                  <span class="round-person">验收人：{{ rd.acceptPerson }}</span>
                  <span class="round-time">验收 {{ fmt(rd.acceptTime) }}</span>
                  <span v-if="rd.acceptVerdict" class="round-verdict">（{{ rd.acceptVerdictName }}）</span>
                </div>
                <div v-if="rd.acceptComment" class="round-result" :class="{ reject: rd.status === 'REJECTED' }">
                  {{ rd.status === 'REJECTED' ? '驳回原因' : '验收意见' }}：{{ rd.acceptComment }}
                </div>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  acceptDefect,
  advanceDefect,
  defectSummary,
  defects,
  overdueRanking,
  submitDefectResult
} from '../api'

const props = defineProps({
  date: { type: String, required: true },
  substationId: { type: [Number, String, null], default: null }
})

// 五步闭环；驳回返工时当前节点回到「处理」（index 2），已走过的「待验收」不再高亮
const FLOW = [
  { key: 'REGISTERED', label: '登记' },
  { key: 'DISPATCHED', label: '派工' },
  { key: 'PROCESSING', label: '处理' },
  { key: 'PENDING_ACCEPTANCE', label: '待验收' },
  { key: 'ACCEPTED', label: '已验收' }
]

const rows = ref([])
const summary = ref([])
const expanded = ref(null)
const detail = ref({})
const ownerInput = ref('')
const busy = ref(false)
const processForm = ref({ person: '', result: '' })
const acceptForm = ref({ person: '', verdict: 'PASS', comment: '' })

const month = computed(() => (props.date || '').slice(0, 7))

function fmt(v) {
  return v ? String(v).replace('T', ' ') : '—'
}

function levelName(level) {
  return { URGENT: '紧急', MAJOR: '重大', MINOR: '一般' }[level] || level
}

function levelTag(level) {
  return { URGENT: 'danger', MAJOR: 'warning', MINOR: 'info' }[level] || 'info'
}

// 真实阶段着色：待验收（橙）、处理中（蓝）、已验收（绿）
function stageTag(status) {
  return {
    REGISTERED: 'info',
    DISPATCHED: 'info',
    PROCESSING: 'primary',
    PENDING_ACCEPTANCE: 'warning',
    ACCEPTED: 'success'
  }[status] || 'info'
}

function roundTagType(status) {
  return { SUBMITTED: 'warning', PASS: 'success', REJECTED: 'danger' }[status] || 'info'
}

function flowIndex(status) {
  return FLOW.findIndex((f) => f.key === status)
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

async function reloadDetail(r) {
  const res = await defects(r.bayId)
  const found = (res.data || []).find((d) => d.id === r.id) || {}
  detail.value = found
  ownerInput.value = found.owner || ''
  processForm.value = { person: found.processPerson || found.owner || '', result: '' }
  acceptForm.value = { person: '', verdict: 'PASS', comment: '' }
}

// 任何推进后都重新拉排行（超期榜可能增删）+ 重新拉详情（显示真实阶段与下一步）
async function refresh(r) {
  await load()
  if (expanded.value === r.id) {
    await reloadDetail(r)
  }
}

async function doDispatch(id) {
  if (!ownerInput.value || !ownerInput.value.trim()) {
    ElMessage.warning('请填写派工负责人')
    return
  }
  busy.value = true
  try {
    await advanceDefect(id, ownerInput.value.trim())
    ElMessage.success('已派工')
    const keep = rows.value.find((r) => r.id === id)
    await refresh(keep)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    busy.value = false
  }
}

async function doStartProcess(id) {
  busy.value = true
  try {
    await advanceDefect(id, null)
    ElMessage.success('已进入处理中')
    const keep = rows.value.find((r) => r.id === id)
    await refresh(keep)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    busy.value = false
  }
}

async function doSubmitResult(id) {
  if (!processForm.value.person || !processForm.value.person.trim()) {
    ElMessage.warning('请填写处理人')
    return
  }
  if (!processForm.value.result || !processForm.value.result.trim()) {
    ElMessage.warning('请填写本次处理结果')
    return
  }
  busy.value = true
  try {
    await submitDefectResult(id, {
      person: processForm.value.person.trim(),
      result: processForm.value.result.trim()
    })
    ElMessage.success('处理结果已提交，缺陷进入待验收，请由另一名值班员验收')
    const keep = rows.value.find((r) => r.id === id)
    await refresh(keep)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    busy.value = false
  }
}

async function doAccept(id) {
  if (!acceptForm.value.person || !acceptForm.value.person.trim()) {
    ElMessage.warning('请填写验收值班员')
    return
  }
  if (acceptForm.value.verdict === 'REJECT' && !acceptForm.value.comment.trim()) {
    ElMessage.warning('驳回返工必须填写原因')
    return
  }
  const verb = acceptForm.value.verdict === 'PASS' ? '验收通过' : '驳回返工'
  try {
    await ElMessageBox.confirm(
      `确认由「${acceptForm.value.person.trim()}」对该缺陷作出「${verb}」结论？`,
      '独立验收确认',
      { type: acceptForm.value.verdict === 'REJECT' ? 'warning' : 'info', confirmButtonText: '确认', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  busy.value = true
  try {
    const res = await acceptDefect(id, {
      person: acceptForm.value.person.trim(),
      verdict: acceptForm.value.verdict,
      comment: acceptForm.value.comment.trim()
    })
    ElMessage.success(res.message || (acceptForm.value.verdict === 'PASS' ? '验收通过，缺陷已闭环' : '已驳回返工'))
    const keep = rows.value.find((r) => r.id === id)
    await refresh(keep)
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
    // 展开中的缺陷若因验收通过而离榜，自动收起
    if (expanded.value != null && !rows.value.some((r) => r.id === expanded.value)) {
      expanded.value = null
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

.close-flow {
  margin-top: 12px;
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

.reject-badge {
  margin-left: 8px;
  color: #c62828;
  font-size: 12px;
  font-weight: 700;
}

.action-box {
  margin-top: 12px;
  padding: 10px 12px;
  border: 1px dashed var(--ops-line);
  border-radius: 8px;
  background: #fff;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.action-hint {
  width: 100%;
  font-size: 12px;
  color: var(--ops-ink-2);
}

.action-hint.warn {
  color: #b25b00;
}

.round-box {
  margin-top: 14px;
}

.round-title {
  font-size: 12.5px;
  font-weight: 700;
  color: var(--ops-orange-dark);
  margin-bottom: 8px;
}

.round-line {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12.5px;
  color: var(--ops-ink-2);
}

.round-person {
  font-weight: 700;
  color: var(--ops-ink);
}

.round-time {
  font-size: 12px;
  color: var(--ops-grey);
}

.round-verdict {
  font-size: 12px;
}

.round-result {
  margin-top: 4px;
  font-size: 12.5px;
  color: var(--ops-ink);
}

.round-result.reject {
  color: #c62828;
}
</style>
