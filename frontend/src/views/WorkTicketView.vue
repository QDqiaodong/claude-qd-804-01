<template>
  <div class="ops-page">
    <!-- ============ 顶部：列表 + 状态统计 ============ -->
    <div class="ops-row" style="align-items: flex-start">
      <div class="ops-block" style="width: 320px">
        <div class="ops-title">
          {{ date }} 工作票
          <span class="ops-sub">{{ list.length }} 张</span>
          <el-button size="small" text type="primary" style="float: right" @click="reload(active && active.id)">刷新</el-button>
        </div>
        <el-button size="small" type="primary" plain @click="openCreate">新建工作票</el-button>
        <div class="tk-list">
          <div
            v-for="t in list"
            :key="t.id"
            class="tk-item"
            :class="{ 'is-active': active && active.id === t.id }"
            @click="selectTicket(t)"
          >
            <div class="tk-item-top">
              <span class="tk-no">{{ t.ticketNo }}</span>
              <el-tag size="small" :type="statusType(t.status)">{{ t.statusName }}</el-tag>
            </div>
            <div class="tk-item-bay">{{ t.bayName }} · {{ t.voltageLevel }}</div>
            <div class="tk-steps">
              <i :class="{ on: t.issuer }">签</i><i :class="{ on: t.permittor }">许</i><i :class="{ on: t.terminator }">终</i>
              <span v-if="t.permitPhase" class="tk-phase">{{ phaseName(t.permitPhase) }}</span>
            </div>
            <div v-if="t.measureTotal" class="tk-measures">
              <b class="m-pending">待{{ t.pendingCount }}</b>
              <b class="m-installed">装{{ t.installedCount }}</b>
              <b class="m-toremove">拆{{ t.toRemoveCount }}</b>
              <b class="m-removed">毕{{ t.removedCount }}</b>
            </div>
          </div>
          <div v-if="!list.length" class="ops-empty">当天暂无工作票，可在时间轴上换一天</div>
        </div>

        <div class="ops-title" style="margin-top: 12px">票面状态统计</div>
        <div v-for="s in stats" :key="s.status" class="stat-row">
          <span class="stat-name">{{ s.statusName }}</span>
          <el-progress
            :percentage="Math.round((s.count / Math.max(1, statsTotal)) * 100)"
            :stroke-width="10"
            color="#e65100"
            style="flex: 1"
          />
          <b class="stat-count">{{ s.count }}</b>
        </div>
        <div v-if="!stats.length" class="ops-empty">当日暂无统计</div>
      </div>

      <!-- ============ 右侧：票面 + 安措闭环 ============ -->
      <div class="ops-block ops-flex-1" style="min-width: 620px">
        <template v-if="active">
          <div class="wt-head">
            <div>
              <span class="wt-no">{{ active.ticketNo }}</span>
              <el-tag :type="statusType(active.status)" size="small" style="margin-left: 8px">
                {{ active.statusName }}
              </el-tag>
              <el-tag v-if="active.permitPhase" type="warning" effect="dark" size="small" style="margin-left: 6px">
                {{ active.permitPhaseName }}
              </el-tag>
            </div>
            <div class="wt-head-actions">
              <el-button size="small" :disabled="!active.canStartRemove" type="warning" plain @click="askStartRemove">
                通知拆除安措
              </el-button>
              <el-button size="small" :disabled="!active.canWithdraw" type="danger" plain @click="askWithdraw">
                撤回许可
              </el-button>
              <el-button size="small" :disabled="!active.canCancel" @click="askCancel">作废</el-button>
            </div>
          </div>

          <!-- 票面基本信息 -->
          <div class="wt-meta">
            <span><label>作业间隔</label>{{ active.bayName }}（{{ active.bayStatusName }}）</span>
            <span><label>负责人</label>{{ active.workLeader }}</span>
            <span><label>班成员</label>{{ active.teamMembers || '—' }}</span>
            <span><label>计划时间</label>{{ fmt(active.planStartTime) }} ~ {{ fmt(active.planEndTime) }}</span>
          </div>
          <div class="wt-content"><label>工作内容</label>{{ active.workContent }}</div>
          <div class="wt-content"><label>停电范围</label>{{ active.powerCutRange || '—' }}</div>

          <!-- ====== 安全措施逐项执行闭环表 ====== -->
          <div class="measure-head">
            <div class="ops-title" style="margin: 0">安全措施 · 逐项确认执行闭环</div>
            <el-button
              v-if="active.canChange"
              size="small"
              type="primary"
              plain
              @click="openAppend"
            >＋ 新增/变更安措（撤回许可后）</el-button>
          </div>

          <!-- 许可 / 终结 闸门提示 -->
          <el-alert
            v-if="active.status === 'ISSUED'"
            :type="active.permitReady ? 'success' : 'error'"
            :closable="false"
            show-icon
            style="margin: 8px 0"
            :title="active.permitReady
              ? '安措装设与关联操作步骤两侧均已完成，可以许可开工'
              : `禁止许可：尚有 ${active.permitBlockers.length} 项卡点（工作票侧 / 关联操作票侧）`"
          >
            <div v-if="!active.permitReady" class="block-list">
              <div v-for="(b, i) in active.permitBlockers" :key="'p' + i">· {{ b }}</div>
            </div>
          </el-alert>
          <el-alert
            v-if="active.status === 'PERMITTED' && active.permitPhase === 'DEMOLISHING'"
            :type="active.terminateReady ? 'success' : 'error'"
            :closable="false"
            show-icon
            style="margin: 8px 0"
            :title="active.terminateReady
              ? '安措已全部拆除、关联恢复步骤均已完成，可以终结'
              : `禁止终结：尚有 ${active.terminateBlockers.length} 项卡点（拆除 / 恢复操作）`"
          >
            <div v-if="!active.terminateReady" class="block-list">
              <div v-for="(b, i) in active.terminateBlockers" :key="'t' + i">· {{ b }}</div>
            </div>
          </el-alert>

          <table class="measure-table">
            <thead>
              <tr>
                <th style="width: 38px">序号</th>
                <th style="width: 74px">类型</th>
                <th style="width: 110px">所在间隔</th>
                <th>具体位置 / 要求动作</th>
                <th style="width: 150px">关联倒闸操作</th>
                <th style="width: 130px">装设人 / 时间</th>
                <th style="width: 130px">拆除人 / 时间</th>
                <th style="width: 86px">当前阶段</th>
                <th style="width: 96px">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="m in active.measures"
                :key="m.id"
                :class="['st-' + m.status.toLowerCase(), { voided: m.voided }]"
              >
                <td class="c-seq">
                  {{ m.seqNo }}
                  <span v-if="m.versionNo > 1" class="ver">v{{ m.versionNo }}</span>
                </td>
                <td>
                  <el-tag size="small" :type="measureTypeTag(m.measureType)" effect="plain">
                    {{ m.measureTypeName }}
                  </el-tag>
                </td>
                <td>{{ m.locationBayName }}</td>
                <td class="c-loc">
                  <div class="loc-desc">{{ m.locationDesc }}</div>
                  <div class="loc-action">{{ m.requiredAction }}<span v-if="!m.needRestore" class="no-restore">（无需拆除）</span></div>
                  <div v-if="m.voided" class="void-reason">变更：{{ m.voidReason }}</div>
                </td>
                <td class="c-ref">
                  <template v-if="m.installStep && m.installStep.bound">
                    <div class="ref-line" :class="{ refBad: !m.installStep.done }">
                      装设：{{ m.installStep.ticketNo }} #{{ m.installStep.stepNo }}
                      <el-tag size="small" :type="m.installStep.done ? 'success' : 'danger'">
                        {{ m.installStep.statusName }}
                      </el-tag>
                    </div>
                    <div v-if="m.installStep.failReason" class="fail-reason">失败：{{ m.installStep.failReason }}</div>
                  </template>
                  <div v-else class="ref-unbound">装设步骤未绑定</div>
                  <template v-if="m.needRestore">
                    <template v-if="m.restoreStep && m.restoreStep.bound">
                      <div class="ref-line" :class="{ refBad: !m.restoreStep.done }">
                        恢复：{{ m.restoreStep.ticketNo }} #{{ m.restoreStep.stepNo }}
                        <el-tag size="small" :type="m.restoreStep.done ? 'success' : 'danger'">
                          {{ m.restoreStep.statusName }}
                        </el-tag>
                      </div>
                      <div v-if="m.restoreStep.failReason" class="fail-reason">失败：{{ m.restoreStep.failReason }}</div>
                    </template>
                    <div v-else class="ref-unbound">恢复步骤未绑定</div>
                  </template>
                </td>
                <td class="c-person">
                  <template v-if="m.installPerson">
                    <b>{{ m.installPerson }}</b>
                    <span>{{ fmtShort(m.installTime) }}</span>
                  </template>
                  <span v-else class="dim">—</span>
                </td>
                <td class="c-person">
                  <template v-if="m.removePerson">
                    <b>{{ m.removePerson }}</b>
                    <span>{{ fmtShort(m.removeTime) }}</span>
                  </template>
                  <span v-else class="dim">—</span>
                </td>
                <td>
                  <el-tag size="small" :type="measureStatusTag(m.status)" effect="dark">
                    {{ m.statusName }}
                  </el-tag>
                </td>
                <td class="c-ops">
                  <el-button
                    v-if="m.canInstall && active.canInstall"
                    size="small"
                    type="primary"
                    @click="askInstall(m)"
                  >装设确认</el-button>
                  <el-button
                    v-if="m.canRemove && active.canRemove"
                    size="small"
                    type="success"
                    @click="askRemove(m)"
                  >拆除确认</el-button>
                  <el-button
                    v-if="m.canChange && active.canChange"
                    size="small"
                    text
                    type="warning"
                    @click="openChange(m)"
                  >变更</el-button>
                  <span v-else-if="m.voided" class="dim">留痕</span>
                </td>
              </tr>
            </tbody>
          </table>
          <div v-if="!active.measures.length" class="ops-empty">该票尚未编制逐项安全措施</div>

          <!-- 图例 -->
          <div class="legend">
            <span><i class="lg lg-pending"></i>待执行</span>
            <span><i class="lg lg-installed"></i>已执行（装设/合上）</span>
            <span><i class="lg lg-toremove"></i>待拆除</span>
            <span><i class="lg lg-removed"></i>已拆除</span>
            <span><i class="lg lg-voided"></i>变更作废留痕</span>
          </div>

          <!-- ====== 签字 + 事件流水 ====== -->
          <div class="sign-and-log">
            <div class="sign-bar">
              <el-button
                v-for="btn in signButtons"
                :key="btn.stage"
                :type="btn.reached ? 'primary' : 'default'"
                :class="{ 'btn-next': btn.next }"
                :disabled="!btn.next"
                size="large"
                @click="doSign(btn.stage)"
              >
                {{ btn.label }}
              </el-button>
              <span class="ops-sub" style="margin-left: 8px">{{ active.waitingText }}</span>
            </div>

            <div class="event-log">
              <div class="ops-title" style="font-size: 13px">事件流水（撤回/作废/失败后仍可追溯真实经过）</div>
              <div v-for="e in active.events" :key="e.id" class="event-row">
                <span class="ev-time">{{ fmtShort(e.eventTime) }}</span>
                <el-tag size="small" :type="eventTag(e.eventType)">{{ e.eventTypeName }}</el-tag>
                <span class="ev-person">{{ e.operatorName }}</span>
                <span class="ev-detail">{{ e.detail }}</span>
              </div>
              <div v-if="!active.events.length" class="ops-empty">暂无事件</div>
            </div>
          </div>

          <!-- 安措痕迹（可折叠） -->
          <el-collapse style="margin-top: 10px">
            <el-collapse-item title="安全措施执行痕迹（只增不改，含撤回回置与变更痕迹）" name="logs">
              <div v-for="l in active.measureLogs" :key="l.id" class="log-row">
                <span class="ev-time">{{ fmtShort(l.operateTime) }}</span>
                <b>{{ l.actionName }}</b>
                <span v-if="l.fromStatusName || l.toStatusName" class="log-flow">
                  {{ l.fromStatusName || '—' }} → {{ l.toStatusName }}
                </span>
                <span class="ev-person">{{ l.operatorName }}</span>
                <span class="ev-detail">{{ l.detail }}</span>
              </div>
            </el-collapse-item>
          </el-collapse>
        </template>

        <div v-else class="ops-empty">左侧选择一张工作票查看票面</div>
      </div>
    </div>

    <!-- ============ 通用人员确认弹窗 ============ -->
    <el-dialog v-model="personVisible" :title="personTitle" width="380">
      <div class="ops-sub" style="margin-bottom: 8px">{{ personHint }}</div>
      <el-input v-model="personName" placeholder="请输入值班员姓名" />
      <el-input
        v-if="needReason"
        v-model="personReason"
        type="textarea"
        :rows="2"
        style="margin-top: 10px"
        :placeholder="reasonPlaceholder"
      />
      <template #footer>
        <el-button @click="personVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmPerson">确认</el-button>
      </template>
    </el-dialog>

    <!-- ============ 新建工作票（含逐项安措编制） ============ -->
    <el-dialog v-model="createVisible" title="新建工作票（票号由存储过程生成）" width="860px" top="5vh">
      <el-form :model="newForm" label-width="92px" size="small">
        <div style="display: flex; gap: 10px">
          <el-form-item label="作业间隔" style="flex: 1">
            <el-select v-model="newForm.bayId" placeholder="选择间隔" style="width: 100%">
              <el-option v-for="b in bayOptions" :key="b.id" :label="`${b.code} ${b.name}`" :value="b.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="负责人" style="flex: 1">
            <el-input v-model="newForm.workLeader" />
          </el-form-item>
        </div>
        <el-form-item label="班成员">
          <el-input v-model="newForm.teamMembers" placeholder="逗号分隔" />
        </el-form-item>
        <el-form-item label="工作内容">
          <el-input v-model="newForm.workContent" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="停电范围">
          <el-input v-model="newForm.powerCutRange" type="textarea" :rows="1" />
        </el-form-item>

        <div class="ops-title" style="margin: 4px 0 6px">
          安全措施（逐项编制，区分接地线 / 接地刀闸 / 遮栏 / 标示牌）
        </div>
        <div v-for="(m, i) in newForm.measures" :key="i" class="edit-measure">
          <el-select v-model="m.measureType" style="width: 108px" placeholder="类型">
            <el-option v-for="t in MEASURE_TYPES" :key="t.v" :label="t.k" :value="t.v" />
          </el-select>
          <el-select v-model="m.locationBayId" style="width: 150px" placeholder="所在间隔">
            <el-option v-for="b in bayOptions" :key="b.id" :label="b.name" :value="b.id" />
          </el-select>
          <el-input v-model="m.locationDesc" placeholder="具体位置，如 2017 接地刀闸" style="width: 180px" />
          <el-input v-model="m.requiredAction" placeholder="要求动作，如 合上/装设一组" style="width: 170px" />
          <el-select v-model="m.needRestore" style="width: 100px" placeholder="拆除">
            <el-option label="需拆除" :value="true" />
            <el-option label="无需拆除" :value="false" />
          </el-select>
          <el-button text type="danger" @click="newForm.measures.splice(i, 1)">删除</el-button>
          <div class="ref-edit">
            <span class="ref-label">装设对应：</span>
            <el-select v-model="m.installTicketId" placeholder="操作票" style="width: 150px" clearable
                       @change="m.installStepNo = null">
              <el-option v-for="o in opTicketOptions" :key="o.id" :label="o.ticketNo" :value="o.id" />
            </el-select>
            <el-select v-model="m.installStepNo" placeholder="步骤号" style="width: 130px" :disabled="!m.installTicketId">
              <el-option v-for="s in opStepOptions(m.installTicketId)" :key="s.stepNo"
                         :label="`#${s.stepNo} ${s.actionDesc.slice(0, 12)}`" :value="s.stepNo" />
            </el-select>
            <span class="ref-label" style="margin-left: 8px">恢复对应：</span>
            <el-select v-model="m.restoreTicketId" placeholder="操作票" style="width: 150px" clearable
                       @change="m.restoreStepNo = null">
              <el-option v-for="o in opTicketOptions" :key="o.id" :label="o.ticketNo" :value="o.id" />
            </el-select>
            <el-select v-model="m.restoreStepNo" placeholder="步骤号" style="width: 130px" :disabled="!m.restoreTicketId">
              <el-option v-for="s in opStepOptions(m.restoreTicketId)" :key="s.stepNo"
                         :label="`#${s.stepNo} ${s.actionDesc.slice(0, 12)}`" :value="s.stepNo" />
            </el-select>
          </div>
        </div>
        <el-button size="small" type="primary" plain style="margin-top: 8px" @click="addMeasureRow()">
          ＋ 添加一项安全措施
        </el-button>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">开票</el-button>
      </template>
    </el-dialog>

    <!-- ============ 变更 / 新增措施弹窗 ============ -->
    <el-dialog v-model="measureDialogVisible" :title="measureDialogTitle" width="640px">
      <el-form label-width="92px" size="small">
        <el-form-item label="类型">
          <el-select v-model="measureForm.measureType" style="width: 200px">
            <el-option v-for="t in MEASURE_TYPES" :key="t.v" :label="t.k" :value="t.v" />
          </el-select>
        </el-form-item>
        <el-form-item label="所在间隔">
          <el-select v-model="measureForm.locationBayId" style="width: 100%">
            <el-option v-for="b in bayOptions" :key="b.id" :label="`${b.code} ${b.name}`" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="具体位置">
          <el-input v-model="measureForm.locationDesc" />
        </el-form-item>
        <el-form-item label="要求动作">
          <el-input v-model="measureForm.requiredAction" />
        </el-form-item>
        <el-form-item label="是否拆除">
          <el-radio-group v-model="measureForm.needRestore">
            <el-radio :value="true">需要拆除恢复</el-radio>
            <el-radio :value="false">无需拆除（验电/监护等）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="装设对应">
          <el-select v-model="measureForm.installTicketId" placeholder="操作票" style="width: 200px" clearable
                     @change="measureForm.installStepNo = null">
            <el-option v-for="o in opTicketOptions" :key="o.id" :label="o.ticketNo" :value="o.id" />
          </el-select>
          <el-select v-model="measureForm.installStepNo" placeholder="步骤号" style="width: 200px; margin-left: 8px"
                     :disabled="!measureForm.installTicketId">
            <el-option v-for="s in opStepOptions(measureForm.installTicketId)" :key="s.stepNo"
                       :label="`#${s.stepNo} ${s.actionDesc.slice(0, 16)}`" :value="s.stepNo" />
          </el-select>
        </el-form-item>
        <el-form-item label="恢复对应">
          <el-select v-model="measureForm.restoreTicketId" placeholder="操作票" style="width: 200px" clearable
                     @change="measureForm.restoreStepNo = null">
            <el-option v-for="o in opTicketOptions" :key="o.id" :label="o.ticketNo" :value="o.id" />
          </el-select>
          <el-select v-model="measureForm.restoreStepNo" placeholder="步骤号" style="width: 200px; margin-left: 8px"
                     :disabled="!measureForm.restoreTicketId">
            <el-option v-for="s in opStepOptions(measureForm.restoreTicketId)" :key="s.stepNo"
                       :label="`#${s.stepNo} ${s.actionDesc.slice(0, 16)}`" :value="s.stepNo" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="measureDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmMeasureDialog">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  allOperationTicketsBrief,
  appendMeasure,
  bays,
  cancelWorkTicket,
  changeMeasure,
  createWorkTicket,
  installMeasure,
  removeMeasure,
  signWorkTicket,
  startRemoveMeasures,
  withdrawPermit,
  workTicket,
  workTicketStats,
  workTickets
} from '../api'

const props = defineProps({
  date: { type: String, required: true },
  substationId: { type: [Number, String, null], default: null }
})

const MEASURE_TYPES = [
  { v: 'GROUND_LINE', k: '接地线' },
  { v: 'EARTH_SWITCH', k: '接地刀闸' },
  { v: 'BARRIER', k: '遮栏' },
  { v: 'SIGNBOARD', k: '标示牌' },
  { v: 'OTHER', k: '其他措施' }
]

const list = ref([])
const stats = ref([])
const active = ref(null)
const bayOptions = ref([])
const opTickets = ref([])

const personVisible = ref(false)
const personTitle = ref('')
const personHint = ref('')
const personName = ref('')
const personReason = ref('')
const needReason = ref(false)
const reasonPlaceholder = ref('请填写原因')
let personAction = null

const createVisible = ref(false)
const newForm = ref(emptyForm())

const measureDialogVisible = ref(false)
const measureDialogMode = ref('append') // append | change
const measureTarget = ref(null)
const measureForm = ref(emptyMeasure())

function emptyMeasure() {
  return {
    measureType: 'GROUND_LINE',
    locationBayId: null,
    locationDesc: '',
    requiredAction: '',
    needRestore: true,
    installTicketId: null,
    installStepNo: null,
    restoreTicketId: null,
    restoreStepNo: null
  }
}

function emptyForm() {
  return {
    bayId: null,
    issueDate: props.date,
    workLeader: '',
    teamMembers: '',
    workContent: '',
    powerCutRange: '',
    safetyMeasures: '',
    measures: []
  }
}

const statsTotal = computed(() => stats.value.reduce((s, i) => s + Number(i.count), 0))

const opTicketOptions = computed(() =>
  opTickets.value.map((t) => ({ id: t.id, ticketNo: t.ticketNo, bayId: t.bayId }))
)

const signButtons = computed(() => {
  if (!active.value) return []
  const signed = active.value.signedCount || 0
  return [
    { stage: 'ISSUE', label: '① 签发', index: 0 },
    { stage: 'PERMIT', label: '② 许可', index: 1 },
    { stage: 'TERMINATE', label: '③ 终结', index: 2 }
  ].map((b) => ({
    ...b,
    reached: signed > b.index,
    next: active.value && active.value.nextStage === b.stage
  }))
})

const measureDialogTitle = computed(() =>
  measureDialogMode.value === 'append' ? '撤回许可后 · 新增安全措施' : '现场变更安全措施（原项作废留痕）'
)

function statusType(status) {
  if (status === 'TERMINATED') return 'success'
  if (status === 'CANCELED') return 'danger'
  if (status === 'DRAFT') return 'info'
  return 'warning'
}

function phaseName(p) {
  return p === 'DEMOLISHING' ? '拆除恢复中' : '作业中'
}

function measureTypeTag(t) {
  return { GROUND_LINE: 'danger', EARTH_SWITCH: 'warning', BARRIER: 'success', SIGNBOARD: 'primary', OTHER: 'info' }[t] || 'info'
}

function measureStatusTag(s) {
  return {
    PENDING: 'info',
    INSTALLED: 'success',
    TO_REMOVE: 'warning',
    REMOVED: '',
    VOIDED: 'danger'
  }[s] || 'info'
}

function eventTag(t) {
  return { ISSUE: 'primary', PERMIT: 'success', WITHDRAW: 'danger', START_REMOVE: 'warning', TERMINATE: 'success', CANCEL: 'info' }[t] || 'info'
}

function fmt(v) {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '—'
}

function fmtShort(v) {
  return v ? String(v).replace('T', ' ').slice(5, 16) : ''
}

function selectTicket(t) {
  loadDetail(t.id)
}

async function loadDetail(id) {
  const d = await workTicket(id)
  active.value = d.data
}

async function reload(keepId) {
  const [res, statRes] = await Promise.all([workTickets(props.date), workTicketStats(props.date)])
  list.value = res.data || []
  stats.value = statRes.data || []
  const targetId = keepId || (active.value && active.value.id) || (list.value[0] && list.value[0].id)
  if (targetId && list.value.some((i) => i.id === targetId)) {
    await loadDetail(targetId)
  } else if (list.value[0]) {
    await loadDetail(list.value[0].id)
  } else {
    active.value = null
  }
}

// ---------- 操作票步骤选项（编制安措时绑定） ----------
async function loadOpTickets() {
  const res = await allOperationTicketsBrief()
  opTickets.value = res.data || []
}

function opStepOptions(ticketId) {
  const t = opTickets.value.find((i) => i.id === ticketId)
  return t ? t.steps : []
}

// ---------- 人员确认类动作 ----------
function openPerson(title, hint, action, withReason, placeholder) {
  personTitle.value = title
  personHint.value = hint
  personName.value = active.value && active.value.workLeader ? active.value.workLeader : ''
  personReason.value = ''
  needReason.value = !!withReason
  reasonPlaceholder.value = placeholder || '请填写原因'
  personAction = action
  personVisible.value = true
}

function askInstall(m) {
  openPerson(`装设确认 · 第${m.seqNo}项 ${m.measureTypeName}`,
    `${m.locationDesc} —— ${m.requiredAction}`,
    (person) => installMeasure(active.value.id, m.id, person))
}

function askRemove(m) {
  openPerson(`拆除确认 · 第${m.seqNo}项 ${m.measureTypeName}`,
    `${m.locationDesc} —— ${m.requiredAction}`,
    (person) => removeMeasure(active.value.id, m.id, person))
}

function askStartRemove() {
  openPerson('通知拆除安措', '通知后已装设措施进入「待拆除」，并须完成关联恢复操作票步骤',
    (person) => startRemoveMeasures(active.value.id, person))
}

function askWithdraw() {
  ElMessageBox.confirm('撤回许可后：已装/已拆确认痕迹全部保留，待拆除、已拆除项回到待执行，需重新装设并再次许可。确定撤回？',
    '撤回许可', { type: 'warning', confirmButtonText: '我知道了，继续' })
    .then(() => openPerson('撤回许可', '已许可现场需变更时，必须先撤回许可再改措施',
      (person, reason) => withdrawPermit(active.value.id, person, reason), true, '请填写撤回原因（现场变更情况）'))
    .catch(() => {})
}

function askCancel() {
  openPerson('作废工作票', '作废后票面冻结，安措与签字痕迹保留可查',
    (person, reason) => cancelWorkTicket(active.value.id, person, reason), true, '请填写作废原因')
}

function doSign(stage) {
  const map = { ISSUE: '签发人签字', PERMIT: '许可人签字（系统将核验安措与关联操作步骤）', TERMINATE: '终结人签字（系统将核验拆除与恢复步骤）' }
  openPerson(map[stage], '', (person) => signWorkTicket(active.value.id, stage, person))
}

async function confirmPerson() {
  if (!personName.value) {
    ElMessage.warning('请输入值班员姓名')
    return
  }
  if (needReason.value && !personReason.value) {
    ElMessage.warning(reasonPlaceholder.value)
    return
  }
  try {
    await personAction(personName.value, personReason.value)
    ElMessage.success('操作完成')
    personVisible.value = false
    await reload(active.value.id)
  } catch (e) {
    ElMessage({ message: e.message, type: 'error', duration: 4200, showClose: true })
  }
}

// ---------- 变更 / 新增 ----------
function openAppend() {
  measureDialogMode.value = 'append'
  measureTarget.value = null
  measureForm.value = { ...emptyMeasure(), locationBayId: active.value.bayId }
  measureDialogVisible.value = true
}

function openChange(m) {
  measureDialogMode.value = 'change'
  measureTarget.value = m
  measureForm.value = {
    measureType: m.measureType,
    locationBayId: m.locationBayId,
    locationDesc: m.locationDesc,
    requiredAction: m.requiredAction,
    needRestore: m.needRestore,
    installTicketId: m.installStep ? m.installStep.ticketId : null,
    installStepNo: m.installStep ? m.installStep.stepNo : null,
    restoreTicketId: m.restoreStep ? m.restoreStep.ticketId : null,
    restoreStepNo: m.restoreStep ? m.restoreStep.stepNo : null
  }
  measureDialogVisible.value = true
}

async function confirmMeasureDialog() {
  const f = measureForm.value
  if (!f.measureType || !f.locationBayId || !f.locationDesc || !f.requiredAction) {
    ElMessage.warning('类型、间隔、位置、要求动作均必填')
    return
  }
  try {
    const { value: person } = await ElMessageBox.prompt('请输入值班员姓名', '执行人', { confirmButtonText: '确定' })
    const { value: reason } = await ElMessageBox.prompt(
      measureDialogMode.value === 'append' ? '新增安措的现场原因' : '现场变更原因（原确认痕迹将保留）',
      '原因', { confirmButtonText: '确定' }
    )
    if (measureDialogMode.value === 'append') {
      await appendMeasure(active.value.id, person, reason, f)
    } else {
      await changeMeasure(active.value.id, measureTarget.value.id, person, reason, f)
    }
    ElMessage.success('措施已保存为待执行')
    measureDialogVisible.value = false
    await reload(active.value.id)
  } catch (e) {
    if (e && e.message) ElMessage.error(e.message)
  }
}

// ---------- 新建 ----------
function openCreate() {
  newForm.value = emptyForm()
  newForm.value.bayId = props.substationId ? Number(props.substationId) : null
  addMeasureRow()
  loadOpTickets()
  createVisible.value = true
}

function addMeasureRow() {
  newForm.value.measures.push({
    ...emptyMeasure(),
    locationBayId: newForm.value.bayId,
    needRestore: true
  })
}

async function submitCreate() {
  const f = newForm.value
  if (!f.bayId || !f.workLeader || !f.workContent) {
    ElMessage.warning('间隔、负责人、工作内容为必填')
    return
  }
  if (!f.measures.length) {
    ElMessage.warning('至少编制一项安全措施')
    return
  }
  for (const [i, m] of f.measures.entries()) {
    if (!m.measureType || !m.locationBayId || !m.locationDesc || !m.requiredAction) {
      ElMessage.warning(`第 ${i + 1} 项安措的类型/间隔/位置/动作未填完整`)
      return
    }
  }
  try {
    const payload = {
      ...f,
      issueDate: props.date,
      safetyMeasures: f.measures
        .map((m, i) => {
          const tn = MEASURE_TYPES.find((t) => t.v === m.measureType)?.k
          return `${i + 1}.[${tn}] ${m.locationDesc}：${m.requiredAction}`
        })
        .join('；')
    }
    const res = await createWorkTicket(payload)
    ElMessage.success(`开票成功，票号 ${res.data.ticketNo}`)
    createVisible.value = false
    reload()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

watch(() => props.date, () => reload(), { immediate: true })

// 多终端协作：每 8 秒静默刷新当前票，另一值班员的确认/许可/撤回会及时反映为真实阶段
let pollTimer = setInterval(() => {
  if (!personVisible.value && !createVisible.value && !measureDialogVisible.value && active.value) {
    workTicket(active.value.id).then((d) => { active.value = d.data }).catch(() => {})
  }
}, 8000)
onBeforeUnmount(() => clearInterval(pollTimer))

watch(
  () => props.substationId,
  async () => {
    const res = await bays(props.substationId || null, props.date)
    bayOptions.value = (res.data || []).filter((b) => b.status !== 'RUNNING')
  },
  { immediate: true }
)

watch(createVisible, async (v) => {
  if (v) {
    if (!bayOptions.value.length) {
      const res = await bays(props.substationId || null, props.date)
      bayOptions.value = (res.data || []).filter((b) => b.status !== 'RUNNING')
    }
    loadOpTickets()
  }
})
</script>

<style scoped>
.tk-list {
  margin-top: 10px;
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
  transition: all 0.15s;
}

.tk-item:hover { border-color: var(--ops-orange); }
.tk-item.is-active { border-color: var(--ops-orange); background: var(--ops-orange-soft); }

.tk-item-top { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.tk-no { font-size: 12.5px; font-weight: 700; color: var(--ops-orange-dark); }
.tk-item-bay { font-size: 12px; color: var(--ops-grey); margin-top: 3px; }

.tk-steps { display: flex; gap: 5px; margin-top: 6px; align-items: center; }
.tk-steps i {
  width: 20px; height: 20px; border-radius: 50%; font-style: normal; font-size: 11px;
  display: flex; align-items: center; justify-content: center;
  border: 1px solid var(--ops-line); color: #9e9e9e; background: #fafafa;
}
.tk-steps i.on { background: var(--ops-orange); border-color: var(--ops-orange); color: #fff; font-weight: 700; }
.tk-phase { font-size: 11px; color: #b71c1c; font-weight: 700; margin-left: 4px; }

.tk-measures { display: flex; gap: 8px; margin-top: 5px; font-size: 11px; }
.tk-measures b { font-weight: 700; font-variant-numeric: tabular-nums; }
.m-pending { color: #757575; }
.m-installed { color: #2e7d32; }
.m-toremove { color: #e65100; }
.m-removed { color: #1565c0; }

.stat-row { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.stat-name { font-size: 12px; width: 56px; color: var(--ops-ink-2); }
.stat-count { width: 22px; text-align: right; font-size: 12.5px; color: var(--ops-orange-dark); }

.wt-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.wt-no { font-size: 16px; font-weight: 800; color: var(--ops-orange-dark); }

.wt-meta { display: flex; flex-wrap: wrap; gap: 14px; font-size: 12.5px; color: var(--ops-ink-2); margin-bottom: 6px; }
.wt-meta label { color: var(--ops-grey); margin-right: 4px; }
.wt-content { font-size: 12.5px; color: var(--ops-ink); margin-bottom: 4px; line-height: 1.5; }
.wt-content label { color: var(--ops-grey); margin-right: 6px; }

.measure-head { display: flex; justify-content: space-between; align-items: center; margin-top: 10px; }

.block-list {
  max-height: 150px; overflow: auto; font-size: 12px; line-height: 1.7; margin-top: 4px;
}

.measure-table {
  width: 100%; border-collapse: collapse; margin-top: 8px; font-size: 12px;
  border: 1px solid var(--ops-line);
}
.measure-table th {
  background: #f6ece6; color: var(--ops-ink-2); font-weight: 700; padding: 7px 6px;
  border: 1px solid #e0d2ca; text-align: left; white-space: nowrap;
}
.measure-table td { border: 1px solid #e8e0db; padding: 6px; vertical-align: top; color: var(--ops-ink); }
.measure-table tbody tr { background: #fff; }
.measure-table tbody tr.st-installed { background: #f4faf1; }
.measure-table tbody tr.st-toremove { background: #fff7e8; }
.measure-table tbody tr.st-removed { background: #eef5fd; }
.measure-table tbody tr.st-voided td { opacity: 0.62; background: #faf3f2; }
.measure-table tbody tr.st-voided .c-seq::after { content: ' 作废'; font-size: 10px; color: #c62828; }

.c-seq { font-weight: 800; text-align: center; color: var(--ops-orange-dark); }
.c-seq .ver { display: block; font-size: 10px; color: #c62828; font-weight: 700; }
.c-loc .loc-desc { font-weight: 700; }
.c-loc .loc-action { color: var(--ops-ink-2); margin-top: 2px; }
.no-restore { color: var(--ops-grey); font-size: 11px; margin-left: 4px; }
.void-reason { color: #c62828; font-size: 11px; margin-top: 2px; }

.c-ref { font-size: 11px; }
.ref-line { display: flex; justify-content: space-between; align-items: center; gap: 4px; margin-bottom: 3px; }
.ref-line.refBad { color: #c62828; }
.ref-unbound { color: #9e9e9e; font-size: 11px; }
.fail-reason { color: #c62828; font-size: 10.5px; margin-bottom: 3px; }

.c-person b { display: block; font-size: 11.5px; color: var(--ops-ink); }
.c-person span { font-size: 10.5px; color: var(--ops-grey); }
.dim { color: #bdbdbd; font-size: 11px; }
.c-ops { white-space: nowrap; }

.legend { display: flex; gap: 16px; margin: 10px 0; font-size: 11.5px; color: var(--ops-ink-2); }
.legend i { display: inline-block; width: 12px; height: 12px; border-radius: 3px; margin-right: 4px; vertical-align: -1px; }
.lg-pending { background: #e0e0e0; }
.lg-installed { background: #a5d6a7; }
.lg-toremove { background: #ffcc80; }
.lg-removed { background: #90caf9; }
.lg-voided { background: #ef9a9a; }

.sign-and-log { display: flex; gap: 14px; margin-top: 12px; align-items: flex-start; }
.sign-bar { flex: none; display: flex; flex-direction: column; gap: 8px; width: 170px; }
.sign-bar .btn-next { box-shadow: 0 0 0 3px rgba(230, 81, 0, 0.16); font-weight: 700; }
.sign-bar .ops-sub { margin-left: 0 !important; font-size: 11.5px; line-height: 1.5; }

.event-log { flex: 1; border: 1px dashed var(--ops-line); border-radius: 8px; padding: 8px 10px; max-height: 210px; overflow: auto; }
.event-row { display: flex; align-items: baseline; gap: 8px; font-size: 11.5px; padding: 3px 0; border-bottom: 1px dotted #f0e8e3; }
.ev-time { color: var(--ops-grey); font-variant-numeric: tabular-nums; flex: none; width: 78px; }
.ev-person { color: #2e7d32; font-weight: 700; flex: none; }
.ev-detail { color: var(--ops-ink-2); }

.log-row { display: flex; align-items: baseline; gap: 8px; font-size: 11.5px; padding: 3px 0; }
.log-flow { color: var(--ops-orange-dark); flex: none; }

.edit-measure {
  border: 1px solid var(--ops-line); border-radius: 8px; padding: 8px;
  margin-bottom: 8px; display: flex; flex-wrap: wrap; gap: 8px; align-items: center;
}
.ref-edit { flex-basis: 100%; display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.ref-label { font-size: 11.5px; color: var(--ops-grey); }
</style>
