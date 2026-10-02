<template>
  <div class="ops-page">
    <!-- ============ 存储过程：当月缺陷数与消缺率 ============ -->
    <div class="ops-row">
      <div class="ops-block ops-flex-1">
        <div class="ops-title">
          当月缺陷数与消缺率（存储过程 sp_defect_summary）
          <span class="ops-sub">{{ month }}</span>
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
        <span class="ops-sub">按超期天数降序 · 前三名标红 · 点条展开消缺闭环</span>
      </div>

      <div v-if="!rows.length" class="ops-empty">当前没有超期缺陷，现场态势良好</div>

      <div v-for="(r, idx) in rows" :key="r.id" class="rank-wrap">
        <div class="rank-row" :class="{ top3: idx < 3, open: expanded === r.id }" @click="toggle(r)">
          <div class="rank-idx" :class="{ red: idx < 3 }">{{ idx + 1 }}</div>
          <div class="rank-main">
            <div class="rank-top">
              <span class="rank-no">{{ r.defectNo }}</span>
              <el-tag size="small" :type="levelTag(r.level)">{{ levelName(r.level) }}</el-tag>
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

        <!-- 展开：详情 + 消缺推进 -->
        <div v-if="expanded === r.id" class="rank-detail">
          <el-descriptions :column="3" size="small" border>
            <el-descriptions-item label="缺陷单号">{{ detail.defectNo }}</el-descriptions-item>
            <el-descriptions-item label="所属间隔">{{ detail.bayName }} · {{ detail.voltageLevel }}</el-descriptions-item>
            <el-descriptions-item label="缺陷设备">{{ detail.equipmentNo || '—' }}</el-descriptions-item>
            <el-descriptions-item label="发现时间">{{ fmt(detail.foundTime) }}</el-descriptions-item>
            <el-descriptions-item label="发现人">{{ detail.reporter }}</el-descriptions-item>
            <el-descriptions-item label="消缺负责人">{{ detail.owner || '未派工' }}</el-descriptions-item>
            <el-descriptions-item label="限期消缺">{{ detail.dueDate }}</el-descriptions-item>
            <el-descriptions-item label="当前环节">{{ detail.statusName }}</el-descriptions-item>
            <el-descriptions-item label="闭环时间">{{ fmt(detail.closedTime) }}</el-descriptions-item>
            <el-descriptions-item label="缺陷描述" :span="3">{{ detail.description || '—' }}</el-descriptions-item>
          </el-descriptions>

          <div class="close-flow">
            <div
              v-for="(st, i) in FLOW"
              :key="st.key"
              class="flow-node"
              :class="{ on: flowIndex(detail.status) >= i, current: flowIndex(detail.status) === i }"
            >
              <span class="flow-dot">{{ i + 1 }}</span>{{ st.label }}
            </div>
            <el-button
              type="primary"
              size="small"
              :disabled="detail.status === 'ACCEPTED'"
              @click="advance(r.id)"
            >
              {{ detail.status === 'ACCEPTED' ? '已闭环' : `推进至「${detail.nextStageName}」` }}
            </el-button>
            <el-input
              v-if="detail.status === 'REGISTERED'"
              v-model="ownerInput"
              size="small"
              placeholder="派工负责人"
              style="width: 130px"
            />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { advanceDefect, defectSummary, defects, overdueRanking } from '../api'

const props = defineProps({
  date: { type: String, required: true },
  substationId: { type: [Number, String, null], default: null }
})

const FLOW = [
  { key: 'REGISTERED', label: '登记' },
  { key: 'DISPATCHED', label: '派工' },
  { key: 'PROCESSING', label: '处理' },
  { key: 'ACCEPTED', label: '验收' }
]

const rows = ref([])
const summary = ref([])
const expanded = ref(null)
const detail = ref({})
const ownerInput = ref('')

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
}

async function advance(id) {
  try {
    await advanceDefect(id, ownerInput.value || null)
    ElMessage.success('消缺环节已推进')
    const keep = rows.value.find((r) => r.id === id)
    await load()
    if (keep) {
      await reloadDetail(keep)
    }
  } catch (e) {
    ElMessage.error(e.message)
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
</style>
