<template>
  <div class="ops-page">
    <div class="ops-row">
      <!-- ============ 当日操作票 ============ -->
      <div class="ops-block" style="width: 280px">
        <div class="ops-title">
          {{ date }} 操作票
          <span class="ops-sub">{{ list.length }} 张</span>
          <el-button size="small" text type="primary" style="float: right"
                     @click="current ? loadDetail(current.id) : reload()">刷新</el-button>
        </div>
        <div class="tk-list">
          <div
            v-for="t in list"
            :key="t.id"
            class="tk-item"
            :class="{ 'is-active': current && current.id === t.id }"
            @click="selectTicket(t)"
          >
            <div class="tk-item-top">
              <span class="tk-no">{{ t.ticketNo }}</span>
              <el-tag size="small" :type="t.status === 'FINISHED' ? 'success' : 'warning'">
                {{ t.statusName }}
              </el-tag>
            </div>
            <div class="tk-item-bay">{{ t.taskName }}</div>
            <el-progress
              :percentage="Math.round((t.doneSteps / Math.max(1, t.totalSteps)) * 100)"
              :stroke-width="8"
              color="#e65100"
              style="margin-top: 6px"
            />
            <div v-if="t.failedSteps" class="tk-fail">有 {{ t.failedSteps }} 步操作失败，待重试</div>
          </div>
          <div v-if="!list.length" class="ops-empty">当天暂无操作票</div>
        </div>
      </div>

      <!-- ============ 倒闸操作步骤勾选器 ============ -->
      <div class="ops-block ops-flex-1" style="min-width: 420px">
        <div class="ops-title">
          倒闸操作步骤（必须按顺序执行）
          <span class="ops-sub" v-if="current">
            {{ current.ticketNo }} · 已执行 {{ current.doneSteps }}/{{ current.totalSteps }}
          </span>
        </div>

        <div v-if="!current" class="ops-empty">选择左侧操作票开始执行</div>
        <div v-else class="step-list">
          <el-alert
            v-if="current.hasFailure"
            type="error"
            :closable="false"
            show-icon
            style="margin-bottom: 8px"
            title="存在操作失败的步骤：已停在该步，后续步骤不可越过，请查明原因后在原步重新执行"
          />
          <div
            v-for="s in current.steps"
            :key="s.stepNo"
            class="step-row"
            :class="{ done: s.done, next: s.executable, blocked: !s.done && !s.executable, failed: s.failed }"
          >
            <div class="step-no">{{ s.stepNo }}</div>
            <div class="step-desc">
              {{ s.actionDesc }}
              <div v-if="s.failed" class="step-fail">
                操作失败：{{ s.failReason }}（{{ s.failPerson }} · {{ fmt(s.failTime) }}），请在本步重试
              </div>
            </div>
            <div class="step-meta">
              <template v-if="s.done">
                <span class="who">{{ s.executePerson }}</span>
                <span class="when">{{ fmt(s.executeTime) }}</span>
              </template>
              <span v-else class="hint">{{ s.blockedHint || '待执行' }}</span>
            </div>
            <el-button
              v-if="!s.done"
              size="small"
              :type="s.executable ? 'primary' : 'default'"
              :disabled="!s.executable"
              @click="execute(s.stepNo)"
            >
              {{ s.failed ? '重新执行' : (s.executable ? '勾选执行' : '不可勾选') }}
            </el-button>
            <el-button
              v-if="s.executable"
              size="small"
              type="danger"
              plain
              @click="askFail(s.stepNo)"
            >登记失败</el-button>
            <el-tag v-else-if="s.done" size="small" type="success">已执行</el-tag>
          </div>
        </div>
      </div>

      <!-- ============ 右侧实时票面预览 ============ -->
      <div class="ops-block" style="width: 300px">
        <div class="ops-title">票面执行进度预览</div>
        <div v-if="!current" class="ops-empty">—</div>
        <template v-else>
          <div class="preview-head">
            <div class="preview-no">{{ current.ticketNo }}</div>
            <div class="preview-task">{{ current.taskName }}</div>
            <div class="preview-line">操作人：{{ current.operatorName }}　监护人：{{ current.guardian }}</div>
            <div class="preview-line">发令人：{{ current.dispatcher }}</div>
          </div>

          <el-progress
            type="dashboard"
            :percentage="Math.round((current.doneSteps / Math.max(1, current.totalSteps)) * 100)"
            :width="130"
            color="#e65100"
          >
            <template #default="{ percentage }">
              <div class="pct">{{ percentage }}%</div>
              <div class="pct-sub">{{ current.doneSteps }}/{{ current.totalSteps }} 步</div>
            </template>
          </el-progress>

          <div class="preview-title">已执行步骤</div>
          <div v-if="!doneSteps.length" class="ops-empty">尚未开始</div>
          <div v-for="s in doneSteps" :key="s.stepNo" class="preview-step">
            <span class="ps-no">{{ s.stepNo }}</span>
            <span class="ps-desc">{{ s.actionDesc }}</span>
            <span class="ps-who">{{ s.executePerson }}</span>
          </div>
          <div class="preview-status">票面状态：{{ current.statusName }}</div>
        </template>
      </div>
    </div>

    <el-dialog v-model="visible" :title="`执行第 ${targetStep} 步`" width="360">
      <div class="ops-sub" style="margin-bottom: 8px">{{ targetDesc }}</div>
      <el-input v-model="person" placeholder="执行人姓名" />
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="confirmExecute">确认执行</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="failVisible" :title="`登记第 ${targetStep} 步操作失败`" width="380">
      <div class="ops-sub" style="margin-bottom: 8px">{{ targetDesc }}</div>
      <el-input v-model="failPersonInput" placeholder="登记人姓名" style="margin-bottom: 10px" />
      <el-input v-model="failReason" type="textarea" :rows="2" placeholder="失败原因（如闭锁未解除、位置指示不到位）" />
      <template #footer>
        <el-button @click="failVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmFail">确认登记失败</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { executeStep, failStep, operationTicket, operationTickets } from '../api'

const props = defineProps({
  date: { type: String, required: true },
  substationId: { type: [Number, String, null], default: null }
})

const list = ref([])
const current = ref(null)
const visible = ref(false)
const person = ref('')
const targetStep = ref(1)
const targetDesc = ref('')
const failVisible = ref(false)
const failPersonInput = ref('')
const failReason = ref('')

const doneSteps = computed(() => (current.value ? current.value.steps.filter((s) => s.done) : []))

function fmt(v) {
  return v ? String(v).replace('T', ' ').slice(5, 16) : ''
}

function selectTicket(t) {
  loadDetail(t.id)
}

async function loadDetail(id) {
  const res = await operationTicket(id)
  current.value = res.data
}

async function reload(keepId) {
  const res = await operationTickets(props.date)
  list.value = res.data || []
  if (list.value.length) {
    await loadDetail(keepId || list.value[0].id)
  } else {
    current.value = null
  }
}

function execute(stepNo) {
  const s = current.value.steps.find((i) => i.stepNo === stepNo)
  targetStep.value = stepNo
  targetDesc.value = s ? s.actionDesc : ''
  person.value = current.value.operatorName || ''
  visible.value = true
}

async function confirmExecute() {
  if (!person.value) {
    ElMessage.warning('请填写执行人')
    return
  }
  try {
    await executeStep(current.value.id, targetStep.value, person.value)
    ElMessage.success(`第 ${targetStep.value} 步已执行`)
    visible.value = false
    await reload(current.value.id)
  } catch (e) {
    ElMessage({ message: e.message, type: 'error', duration: 3600, showClose: true })
  }
}

function askFail(stepNo) {
  const s = current.value.steps.find((i) => i.stepNo === stepNo)
  targetStep.value = stepNo
  targetDesc.value = s ? s.actionDesc : ''
  failPersonInput.value = current.value.operatorName || ''
  failReason.value = ''
  failVisible.value = true
}

async function confirmFail() {
  if (!failPersonInput.value || !failReason.value) {
    ElMessage.warning('登记人与失败原因必填')
    return
  }
  try {
    await failStep(current.value.id, targetStep.value, failPersonInput.value, failReason.value)
    ElMessage.warning(`第 ${targetStep.value} 步已登记失败，停在该步待重试`)
    failVisible.value = false
    await reload(current.value.id)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

watch(() => props.date, () => reload(), { immediate: true })

// 多终端协作：每 8 秒静默刷新，另一值班员的步骤执行/失败登记会及时反映为真实阶段
let pollTimer = setInterval(() => {
  if (!visible.value && !failVisible.value && current.value) {
    loadDetail(current.value.id).catch(() => {})
  }
}, 8000)
onBeforeUnmount(() => clearInterval(pollTimer))
</script>

<style scoped>
.tk-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tk-item {
  border: 1px solid var(--ops-line);
  border-radius: 8px;
  padding: 8px 10px;
  cursor: pointer;
  background: #fff;
}

.tk-item:hover {
  border-color: var(--ops-orange);
}

.tk-item.is-active {
  border-color: var(--ops-orange);
  background: var(--ops-orange-soft);
}

.tk-item-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 6px;
}

.tk-no {
  font-size: 12.5px;
  font-weight: 700;
  color: var(--ops-orange-dark);
}

.tk-item-bay {
  font-size: 11.5px;
  color: var(--ops-grey);
  margin-top: 3px;
  line-height: 1.4;
}

/* ---------- 步骤列表 ---------- */
.step-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.step-row {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid var(--ops-line);
  border-left: 4px solid var(--ops-line);
  border-radius: 7px;
  padding: 8px 10px;
  background: #fff;
}

.step-row.done {
  border-left-color: var(--ops-green);
  background: #f1f8e9;
}

.step-row.next {
  border-left-color: var(--ops-orange);
  box-shadow: 0 0 0 2px rgba(230, 81, 0, 0.12);
}

.step-row.blocked {
  opacity: 0.62;
}

.step-row.failed {
  border-left-color: #c62828;
  background: #fdecea;
  box-shadow: 0 0 0 2px rgba(198, 40, 40, 0.12);
}

.step-fail {
  font-size: 11px;
  color: #c62828;
  margin-top: 3px;
  font-weight: 700;
}

.tk-fail {
  font-size: 11px;
  color: #c62828;
  font-weight: 700;
  margin-top: 4px;
}

.step-no {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--ops-orange-soft);
  color: var(--ops-orange-dark);
  font-weight: 800;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}

.step-desc {
  flex: 1;
  font-size: 13px;
  color: var(--ops-ink);
  min-width: 0;
}

.step-meta {
  width: 128px;
  font-size: 11px;
  color: var(--ops-grey);
  text-align: right;
  flex: none;
}

.step-meta .who {
  color: var(--ops-green);
  font-weight: 700;
}

.step-meta .hint {
  color: #c62828;
}

/* ---------- 右侧预览 ---------- */
.preview-head {
  border-bottom: 1px dashed var(--ops-line);
  padding-bottom: 8px;
  margin-bottom: 8px;
}

.preview-no {
  font-size: 13px;
  font-weight: 800;
  color: var(--ops-orange-dark);
}

.preview-task {
  font-size: 12px;
  color: var(--ops-ink);
  margin: 3px 0 6px;
  line-height: 1.4;
}

.preview-line {
  font-size: 11px;
  color: var(--ops-grey);
}

.pct {
  font-size: 22px;
  font-weight: 800;
  color: var(--ops-orange-dark);
}

.pct-sub {
  font-size: 11px;
  color: var(--ops-grey);
}

.preview-title {
  font-size: 12px;
  font-weight: 700;
  margin: 6px 0;
  color: var(--ops-ink-2);
}

.preview-step {
  display: flex;
  gap: 6px;
  font-size: 11.5px;
  padding: 4px 0;
  border-bottom: 1px dotted var(--ops-line);
}

.ps-no {
  color: var(--ops-orange);
  font-weight: 700;
  width: 16px;
  flex: none;
}

.ps-desc {
  flex: 1;
  color: var(--ops-ink-2);
  min-width: 0;
}

.ps-who {
  color: var(--ops-green);
  flex: none;
}

.preview-status {
  margin-top: 10px;
  font-size: 12px;
  color: var(--ops-ink-2);
  font-weight: 700;
}
</style>
