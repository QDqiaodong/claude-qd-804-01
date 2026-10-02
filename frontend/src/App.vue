<template>
  <div class="ops-app">
    <!-- ============ 顶部：站标 + 标题 ============ -->
    <header class="ops-header">
      <div class="ops-brand">
        <div class="ops-logo">亚</div>
        <div>
          <div class="ops-brand-title">变电站 · 设备巡检与两票管理</div>
          <div class="ops-brand-sub">Substation Equipment Inspection &amp; Two-Ticket Management</div>
        </div>
      </div>

      <div class="ops-view-switch">
        <button
          v-for="r in routes"
          :key="r.path"
          class="ops-view-btn"
          :class="{ 'is-active': currentPath === r.path }"
          @click="go(r.path)"
        >
          {{ r.title }}
        </button>
      </div>

      <div class="ops-header-right">
        <el-select
          v-model="substationId"
          size="small"
          placeholder="全部变电站"
          style="width: 150px"
          clearable
        >
          <el-option v-for="s in stationOptions" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <div class="ops-clock">
          <div class="ops-clock-date">{{ selectedDate }}</div>
          <div class="ops-clock-week">{{ weekdayText }} · {{ shiftText }}</div>
        </div>
      </div>
    </header>

    <!-- ============ 顶部时间轴导航（核心：按天切换当日数据） ============ -->
    <nav class="ops-timeline">
      <div class="ops-tl-arrow">
        <button class="ops-tl-btn" title="前一周" @click="shiftDays(-7)">&#171;</button>
        <button class="ops-tl-btn" title="前一天" @click="shiftDays(-1)">&#8249;</button>
      </div>

      <div class="ops-tl-rail">
        <div class="ops-tl-line"></div>
        <div
          v-for="day in days"
          :key="day.date"
          class="ops-tl-cell"
          :class="{
            'is-active': day.date === selectedDate,
            'is-today': day.date === today,
            'is-empty': day.total === 0
          }"
          @click="selectDate(day.date)"
        >
          <div class="ops-tl-node"></div>
          <div class="ops-tl-week">{{ dayWeek(day.date) }}</div>
          <div class="ops-tl-day">{{ dayOfMonth(day.date) }}</div>
          <el-tooltip
            placement="bottom"
            :content="`工作票 ${day.workTicketCount} 张 / 操作票 ${day.operationTicketCount} 张`"
          >
            <div class="ops-tl-badge">票 {{ day.total }}</div>
          </el-tooltip>
        </div>
      </div>

      <div class="ops-tl-arrow">
        <button class="ops-tl-btn" title="后一天" @click="shiftDays(1)">&#8250;</button>
        <button class="ops-tl-btn" title="后一周" @click="shiftDays(7)">&#187;</button>
      </div>

      <button class="ops-tl-today" @click="backToToday">今天</button>
    </nav>

    <!-- ============ 内容区 ============ -->
    <main class="ops-content">
      <component :is="currentView().component" :date="selectedDate" :substation-id="substationId" />
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { currentPath, currentView, go, routes } from './router'
import { substations, timeline } from './api'

/** 时间轴可见天数 */
const WINDOW = 7

const today = fmt(new Date())
const cursorStart = ref(addDays(today, -3))
const selectedDate = ref(today)
const days = ref([])
const stationOptions = ref([])
const substationId = ref(null)

const weekdayText = computed(() => dayWeekCn(selectedDate.value))
const shiftText = computed(() => (new Date().getHours() < 16 ? '白班 · 08:00—16:00' : '夜班 · 16:00—08:00'))

function fmt(d) {
  const m = `${d.getMonth() + 1}`.padStart(2, '0')
  const day = `${d.getDate()}`.padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

function addDays(base, n) {
  const d = new Date(`${base}T00:00:00`)
  d.setDate(d.getDate() + n)
  return fmt(d)
}

function dayOfMonth(date) {
  return Number(date.slice(8, 10))
}

function dayWeek(date) {
  const d = new Date(`${date}T00:00:00`)
  return ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
}

function dayWeekCn(date) {
  const d = new Date(`${date}T00:00:00`)
  return ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'][d.getDay()]
}

async function loadTimeline() {
  const from = cursorStart.value
  const to = addDays(from, WINDOW - 1)
  try {
    const res = await timeline(from, to)
    days.value = res.data || []
  } catch (e) {
    days.value = []
  }
}

function shiftDays(n) {
  cursorStart.value = addDays(cursorStart.value, n)
  loadTimeline()
}

function selectDate(date) {
  selectedDate.value = date
}

function backToToday() {
  selectedDate.value = today
  cursorStart.value = addDays(today, -3)
  loadTimeline()
}

async function loadStations() {
  try {
    const res = await substations()
    stationOptions.value = res.data || []
  } catch (e) {
    ElMessage.error(`变电站列表加载失败：${e.message}`)
  }
}

onMounted(() => {
  loadTimeline()
  loadStations()
})
</script>

<style scoped>
.ops-app {
  min-height: 100%;
  background: var(--ops-bg);
  padding-bottom: 24px;
}

/* ---------- 头部 ---------- */
.ops-header {
  background: linear-gradient(90deg, #bf360c 0%, var(--ops-orange) 55%, #ef6c17 100%);
  color: #fff;
  padding: 12px 20px;
  display: flex;
  align-items: center;
  gap: 20px;
  box-shadow: 0 2px 10px rgba(191, 54, 12, 0.25);
}

.ops-brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ops-logo {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 700;
}

.ops-brand-title {
  font-size: 17px;
  font-weight: 800;
  letter-spacing: 0.5px;
}

.ops-brand-sub {
  font-size: 11px;
  opacity: 0.75;
  letter-spacing: 0.4px;
}

.ops-view-switch {
  display: flex;
  gap: 6px;
  margin-left: 10px;
  background: rgba(255, 255, 255, 0.16);
  padding: 4px;
  border-radius: 999px;
}

.ops-view-btn {
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.86);
  font-size: 12.5px;
  padding: 6px 12px;
  border-radius: 999px;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.18s;
}

.ops-view-btn:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.16);
}

.ops-view-btn.is-active {
  background: #fff;
  color: var(--ops-orange-dark);
  font-weight: 700;
}

.ops-header-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 14px;
}

.ops-clock {
  text-align: right;
  line-height: 1.25;
}

.ops-clock-date {
  font-size: 14px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.ops-clock-week {
  font-size: 11px;
  opacity: 0.8;
}

/* ---------- 时间轴导航 ---------- */
.ops-timeline {
  position: sticky;
  top: 0;
  z-index: 20;
  background: #fff;
  border-bottom: 1px solid var(--ops-line);
  box-shadow: 0 1px 6px rgba(94, 66, 55, 0.08);
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
}

.ops-tl-arrow {
  display: flex;
  gap: 4px;
}

.ops-tl-btn {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  border: 1px solid var(--ops-line);
  background: #fff;
  color: var(--ops-orange);
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
}

.ops-tl-btn:hover {
  background: var(--ops-orange-soft);
  border-color: var(--ops-orange);
}

.ops-tl-today {
  border: 1px solid var(--ops-orange);
  background: var(--ops-orange-soft);
  color: var(--ops-orange-dark);
  border-radius: 999px;
  padding: 4px 12px;
  font-size: 12px;
  cursor: pointer;
  font-weight: 700;
}

.ops-tl-rail {
  position: relative;
  display: flex;
  flex: 1;
  justify-content: space-between;
  padding: 14px 6px 6px;
  overflow-x: auto;
}

.ops-tl-line {
  position: absolute;
  left: 0;
  right: 0;
  top: 40px;
  height: 2px;
  background: repeating-linear-gradient(90deg, var(--ops-line) 0 6px, transparent 6px 12px);
}

.ops-tl-cell {
  flex: 1 0 100px;
  text-align: center;
  cursor: pointer;
  position: relative;
  padding-bottom: 2px;
}

.ops-tl-cell.is-empty {
  opacity: 0.55;
}

.ops-tl-node {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 2px solid var(--ops-line);
  background: #fff;
  margin: 0 auto 6px;
  position: relative;
  z-index: 2;
}

.ops-tl-cell.is-today .ops-tl-node {
  border-color: var(--ops-blue);
  box-shadow: 0 0 0 3px rgba(21, 101, 192, 0.14);
}

.ops-tl-cell.is-active .ops-tl-node {
  border-color: var(--ops-orange);
  background: var(--ops-orange);
  transform: scale(1.35);
  box-shadow: 0 0 0 4px rgba(230, 81, 0, 0.18);
}

.ops-tl-week {
  font-size: 11px;
  color: var(--ops-grey);
}

.ops-tl-day {
  font-size: 15px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: var(--ops-ink-2);
}

.ops-tl-cell.is-active .ops-tl-day {
  color: var(--ops-orange-dark);
}

.ops-tl-badge {
  display: inline-block;
  min-width: 44px;
  margin-top: 3px;
  padding: 1px 7px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  background: var(--ops-orange-soft);
  color: var(--ops-orange-dark);
  border: 1px solid #ffcc80;
}

.ops-tl-cell.is-active .ops-tl-badge {
  background: var(--ops-orange);
  color: #fff;
  border-color: var(--ops-orange);
}

.ops-tl-cell.is-empty .ops-tl-badge {
  background: #f5f5f5;
  color: #9e9e9e;
  border-color: #e0e0e0;
}

.ops-content {
  padding-top: 0;
}
</style>
