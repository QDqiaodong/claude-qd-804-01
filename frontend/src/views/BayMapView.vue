<template>
  <div class="ops-page">
    <!-- ================= 顶部说明条 ================= -->
    <div class="ops-row">
      <div class="ops-block ops-flex-1">
        <div class="ops-title">
          间隔平面示意图
          <span class="ops-sub">{{ date }} · 点击任意间隔格子可直接登记缺陷</span>
        </div>
        <div class="legend">
          <span v-for="s in STATUS" :key="s.key" class="legend-item">
            <i :style="{ background: s.color }"></i>{{ s.label }}
            <b>{{ countByStatus(s.key) }}</b>
          </span>
          <span class="ops-sub" style="margin-left: auto">
            共 {{ bayList.length }} 个间隔 / {{ equipmentCount }} 台设备
          </span>
        </div>
      </div>
    </div>

    <div class="ops-row" style="margin-top: 14px">
      <!-- ================= SVG 母线 + 间隔格子 ================= -->
      <div class="ops-block ops-flex-1" style="min-width: 620px">
        <div class="canvas-wrap" ref="wrap">
          <svg :width="svgWidth" :height="300" :viewBox="`0 0 ${svgWidth} 300`" class="bay-svg">
            <!-- 母线 -->
            <rect x="16" y="46" :width="svgWidth - 32" height="6" rx="3" fill="#bf360c" />
            <text x="16" y="38" class="svg-label">110kV 母线 · {{ currentStationName }}</text>

            <!-- 电压等级分排：示意 every bay hangs on the bus -->
            <g v-for="(b, i) in bayList" :key="b.id" class="bay-g" @click="openPop(b, i, $event)">
              <line :x1="cellX(i)" y1="52" :x2="cellX(i)" y2="96" stroke="#5d4037" stroke-width="2" />
              <!-- 断路器符号 -->
              <rect
                :x="cellX(i) - 12"
                y="96"
                width="24"
                height="24"
                rx="3"
                :fill="colorOf(b.status)"
                stroke="#4e342e"
                stroke-width="1.5"
              />
              <line :x1="cellX(i)" y1="120" :x2="cellX(i)" y2="146" stroke="#5d4037" stroke-width="2" />

              <!-- 间隔格子 -->
              <rect
                :x="cellX(i) - 52"
                y="146"
                width="104"
                height="86"
                rx="7"
                :fill="fillOf(b.status)"
                :stroke="colorOf(b.status)"
                stroke-width="2"
                class="bay-rect"
                :class="{ 'is-active': activeBay && activeBay.id === b.id }"
              />
              <text :x="cellX(i)" y="164" class="svg-code" text-anchor="middle">{{ b.code }}</text>
              <text :x="cellX(i)" y="180" class="svg-name" text-anchor="middle">{{ b.name }}</text>
              <text :x="cellX(i)" y="197" class="svg-sub" text-anchor="middle">{{ b.bayTypeName }}</text>
              <text :x="cellX(i)" y="212" class="svg-sub" text-anchor="middle">{{ b.voltageLevel }}</text>
              <text
                :x="cellX(i)"
                y="227"
                class="svg-status"
                text-anchor="middle"
                :fill="colorOf(b.status)"
              >{{ b.statusName }}</text>

              <!-- 缺陷徽章 -->
              <g v-if="b.openDefectCount > 0" @click.stop="focusDefects(b, $event)">
                <circle :cx="cellX(i) + 38" :cy="152" r="11" fill="#c62828" />
                <text :x="cellX(i) + 38" y="156" class="svg-badge" text-anchor="middle">
                  {{ b.openDefectCount }}
                </text>
              </g>
            </g>

            <text v-if="!bayList.length" :x="svgWidth / 2" y="180" class="svg-empty" text-anchor="middle">
              暂无间隔数据
            </text>
          </svg>

          <!-- 自绘 popover：登记缺陷 -->
          <div v-if="pop.visible" class="pop-card" :style="{ left: pop.x + 'px', top: pop.y + 'px' }">
            <div class="pop-title">
              登记缺陷 · {{ pop.bay.code }}
              <span class="pop-close" @click="pop.visible = false">×</span>
            </div>
            <div class="pop-body">
              <div class="pop-line">间隔：{{ pop.bay.name }}（{{ pop.bay.bayTypeName }} / {{ pop.bay.voltageLevel }}）</div>
              <el-select v-model="form.equipmentId" size="small" placeholder="关联设备（可选）" clearable>
                <el-option v-for="e in bayEquipments(pop.bay.id)" :key="e.id" :label="`${e.equipmentNo} ${e.name}`" :value="e.id" />
              </el-select>
              <el-radio-group v-model="form.level" size="small">
                <el-radio-button value="URGENT">紧急</el-radio-button>
                <el-radio-button value="MAJOR">重大</el-radio-button>
                <el-radio-button value="MINOR">一般</el-radio-button>
              </el-radio-group>
              <el-input v-model="form.title" size="small" placeholder="缺陷现象，如：触头红外测温偏高" />
              <el-input
                v-model="form.description"
                size="small"
                type="textarea"
                :rows="2"
                placeholder="详细描述（可选）"
              />
              <el-input v-model="form.reporter" size="small" placeholder="登记人姓名" />
              <el-button type="primary" size="small" @click="submitDefect">确认登记</el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- ================= 右侧：选中间隔的台账 ================= -->
      <div class="ops-block" style="width: 330px">
        <div class="ops-title">设备台账</div>
        <div v-if="activeBay" class="ops-sub" style="margin-bottom: 8px">
          {{ activeBay.code }} · {{ activeBay.name }}
        </div>
        <div v-if="!activeBay" class="ops-empty">点击左侧间隔格子查看该间隔下设备</div>
        <el-table v-else :data="bayEquipments(activeBay.id)" size="small" max-height="420">
          <el-table-column prop="equipmentNo" label="编号" width="86" />
          <el-table-column prop="name" label="名称" min-width="90" />
          <el-table-column label="上次检修" width="96">
            <template #default="{ row }">{{ row.lastCheckDate || '—' }}</template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- ================= 存储过程：按电压等级汇总 ================= -->
    <div class="ops-block" style="margin-top: 14px">
      <div class="ops-title">
        按电压等级汇总（存储过程 sp_equipment_summary_by_voltage）
        <span class="ops-sub">{{ date }}</span>
      </div>
      <div v-if="!summary.length" class="ops-empty">存储过程暂无返回</div>
      <el-table v-else :data="summary" size="small" border>
        <el-table-column prop="voltageLevel" label="电压等级" width="110" />
        <el-table-column prop="equipTotal" label="设备台数" width="100" />
        <el-table-column prop="runningTotal" label="运行台数" width="100" />
        <el-table-column label="待检台数" min-width="220">
          <template #default="{ row }">
            <el-progress
              :percentage="row.equipTotal ? Math.round((row.pendingTotal / row.equipTotal) * 100) : 0"
              :stroke-width="12"
              striped
              color="#e65100"
              :text-inside="true"
            />
            <span class="ops-sub">{{ row.pendingTotal }} 台超定检周期</span>
          </template>
        </el-table-column>
        <el-table-column prop="openDefectTotal" label="未闭环缺陷" width="110" />
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { bays, equipmentSummary, equipments, registerDefect } from '../api'

const props = defineProps({
  date: { type: String, required: true },
  substationId: { type: [Number, String, null], default: null }
})

const STATUS = [
  { key: 'RUNNING', label: '运行', color: '#2e7d32', fill: '#e8f5e9' },
  { key: 'HOT_STANDBY', label: '热备', color: '#f57c00', fill: '#fff8e1' },
  { key: 'COLD_STANDBY', label: '冷备', color: '#1565c0', fill: '#e3f2fd' },
  { key: 'MAINTENANCE', label: '检修', color: '#6d4c41', fill: '#efebe9' }
]

const bayList = ref([])
const allEquipments = ref([])
const summary = ref([])
const activeBay = ref(null)
const wrap = ref(null)

const pop = ref({ visible: false, x: 0, y: 0, bay: null })
const form = ref({ equipmentId: null, level: 'MAJOR', title: '', description: '', reporter: '' })

const svgWidth = computed(() => Math.max(720, 40 + bayList.value.length * 132))
const equipmentCount = computed(() => allEquipments.value.length)
const currentStationName = computed(() => {
  const s = bayList.value[0]
  return s ? s.substationName : '全部变电站'
})

function colorOf(status) {
  return (STATUS.find((s) => s.key === status) || STATUS[0]).color
}

function fillOf(status) {
  return (STATUS.find((s) => s.key === status) || STATUS[0]).fill
}

function countByStatus(key) {
  return bayList.value.filter((b) => b.status === key).length
}

function cellX(i) {
  return 92 + i * 132
}

function bayEquipments(bayId) {
  return allEquipments.value.filter((e) => e.bayId === bayId)
}

function openPop(bay, i, event) {
  activeBay.value = bay
  form.value = { equipmentId: null, level: 'MAJOR', title: '', description: '', reporter: '' }
  const rect = wrap.value.getBoundingClientRect()
  const x = event.clientX - rect.left - 110
  pop.value = {
    visible: true,
    x: Math.max(4, Math.min(x, svgWidth.value - 250)),
    y: 250,
    bay
  }
}

function focusDefects(bay, event) {
  event.stopPropagation()
  openPop(bay, bayList.value.indexOf(bay), event)
}

async function submitDefect() {
  if (!form.value.title) {
    ElMessage.warning('请填写缺陷现象')
    return
  }
  if (!form.value.reporter) {
    ElMessage.warning('请填写登记人')
    return
  }
  try {
    await registerDefect({
      bayId: pop.value.bay.id,
      equipmentId: form.value.equipmentId,
      title: form.value.title,
      description: form.value.description,
      level: form.value.level,
      reporter: form.value.reporter
    })
    ElMessage.success(`已在 ${pop.value.bay.code} 登记缺陷，限期按「${pop.value.bay.code}」等级自动推算`)
    pop.value.visible = false
    load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function load() {
  try {
    const [bayRes, eqRes, sumRes] = await Promise.all([
      bays(props.substationId || null, props.date),
      equipments(null),
      equipmentSummary(props.substationId || null)
    ])
    bayList.value = bayRes.data || []
    allEquipments.value = eqRes.data || []
    summary.value = sumRes.data || []
    if (!allEquipments.value.length && bayList.value.length) {
      const first = []
      for (const b of bayList.value) {
        const r = await equipments(b.id)
        first.push(...(r.data || []))
      }
      allEquipments.value = first
    }
  } catch (e) {
    ElMessage.error(`间隔数据加载失败：${e.message}`)
  }
}

watch(() => [props.date, props.substationId], load, { immediate: true })
</script>

<style scoped>
.legend {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 12.5px;
  color: var(--ops-ink-2);
  flex-wrap: wrap;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.legend-item i {
  width: 12px;
  height: 12px;
  border-radius: 3px;
  display: inline-block;
}

.legend-item b {
  color: var(--ops-orange-dark);
}

.canvas-wrap {
  position: relative;
  overflow-x: auto;
  padding-bottom: 4px;
}

.bay-svg {
  display: block;
}

.bay-g {
  cursor: pointer;
}

.bay-rect:hover {
  filter: brightness(0.97);
}

.bay-rect.is-active {
  stroke-width: 3.5;
}

.svg-label {
  font-size: 12px;
  fill: var(--ops-ink-2);
  font-weight: 700;
}

.svg-code {
  font-size: 12.5px;
  font-weight: 700;
  fill: var(--ops-orange-dark);
}

.svg-name {
  font-size: 12px;
  fill: var(--ops-ink);
}

.svg-sub {
  font-size: 10.5px;
  fill: #6d4c41;
}

.svg-status {
  font-size: 12px;
  font-weight: 700;
}

.svg-badge {
  font-size: 11px;
  font-weight: 700;
  fill: #fff;
}

.svg-empty {
  font-size: 14px;
  fill: #9e9e9e;
}

.pop-card {
  position: absolute;
  width: 240px;
  background: #fff;
  border: 1px solid var(--ops-line);
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(94, 66, 55, 0.22);
  z-index: 30;
  animation: pop-in 0.14s ease-out;
}

@keyframes pop-in {
  from {
    opacity: 0;
    transform: translateY(-6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.pop-title {
  position: relative;
  background: var(--ops-orange);
  color: #fff;
  font-size: 12.5px;
  font-weight: 700;
  padding: 7px 10px;
  border-radius: 9px 9px 0 0;
}

.pop-close {
  position: absolute;
  right: 9px;
  top: 5px;
  cursor: pointer;
  font-size: 15px;
  line-height: 1;
}

.pop-body {
  padding: 9px 10px 11px;
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.pop-line {
  font-size: 11.5px;
  color: var(--ops-grey);
}
</style>
