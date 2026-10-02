package com.substation.ops.common;

import java.util.HashMap;
import java.util.Map;

/**
 * 本仓的词面对照表（供电运行口径的中文说法）。所有 Controller/Service 共用。
 */
public final class Labels {

    private Labels() {
    }

    private static final Map<String, String> BAY_STATUS = new HashMap<>();
    private static final Map<String, String> BAY_TYPE = new HashMap<>();
    private static final Map<String, String> DEFECT_LEVEL = new HashMap<>();
    private static final Map<String, String> DEFECT_STATUS = new HashMap<>();
    private static final Map<String, String> WORK_STATUS = new HashMap<>();
    private static final Map<String, String> OP_STATUS = new HashMap<>();
    private static final Map<String, String> STEP_STATUS = new HashMap<>();
    private static final Map<String, String> MEASURE_TYPE = new HashMap<>();
    private static final Map<String, String> MEASURE_STATUS = new HashMap<>();
    private static final Map<String, String> MEASURE_ACTION = new HashMap<>();
    private static final Map<String, String> PERMIT_PHASE = new HashMap<>();
    private static final Map<String, String> TICKET_EVENT = new HashMap<>();
    private static final Map<String, String> EQUIP_TYPE = new HashMap<>();
    private static final Map<String, String> EQUIP_STATUS = new HashMap<>();
    private static final Map<String, String> INSPECT_RESULT = new HashMap<>();

    static {
        BAY_STATUS.put("RUNNING", "运行");
        BAY_STATUS.put("HOT_STANDBY", "热备");
        BAY_STATUS.put("COLD_STANDBY", "冷备");
        BAY_STATUS.put("MAINTENANCE", "检修");

        BAY_TYPE.put("MAIN_TRANSFORMER", "主变间隔");
        BAY_TYPE.put("OUTGOING", "出线间隔");
        BAY_TYPE.put("CAPACITOR", "电容器间隔");
        BAY_TYPE.put("BUS_TIE", "母联间隔");

        DEFECT_LEVEL.put("URGENT", "紧急");
        DEFECT_LEVEL.put("MAJOR", "重大");
        DEFECT_LEVEL.put("MINOR", "一般");

        DEFECT_STATUS.put("REGISTERED", "已登记");
        DEFECT_STATUS.put("DISPATCHED", "已派工");
        DEFECT_STATUS.put("PROCESSING", "处理中");
        DEFECT_STATUS.put("ACCEPTED", "已验收");

        WORK_STATUS.put("DRAFT", "待签发");
        WORK_STATUS.put("ISSUED", "已签发");
        WORK_STATUS.put("PERMITTED", "已许可");
        WORK_STATUS.put("TERMINATED", "已终结");
        WORK_STATUS.put("CANCELED", "已作废");

        OP_STATUS.put("PENDING", "待执行");
        OP_STATUS.put("RUNNING", "执行中");
        OP_STATUS.put("FINISHED", "已完成");
        OP_STATUS.put("CANCELED", "已作废");

        STEP_STATUS.put("PENDING", "待执行");
        STEP_STATUS.put("DONE", "已执行");
        STEP_STATUS.put("FAILED", "操作失败");

        MEASURE_TYPE.put("GROUND_LINE", "接地线");
        MEASURE_TYPE.put("EARTH_SWITCH", "接地刀闸");
        MEASURE_TYPE.put("BARRIER", "遮栏");
        MEASURE_TYPE.put("SIGNBOARD", "标示牌");
        MEASURE_TYPE.put("OTHER", "其他措施");

        MEASURE_STATUS.put("PENDING", "待执行");
        MEASURE_STATUS.put("INSTALLED", "已执行");
        MEASURE_STATUS.put("TO_REMOVE", "待拆除");
        MEASURE_STATUS.put("REMOVED", "已拆除");
        MEASURE_STATUS.put("VOIDED", "已变更作废");

        MEASURE_ACTION.put("CREATE", "编制入票");
        MEASURE_ACTION.put("INSTALL", "装设/合上确认");
        MEASURE_ACTION.put("REMOVE", "拆除/取下确认");
        MEASURE_ACTION.put("VOID", "现场变更");
        MEASURE_ACTION.put("REACTIVATE", "撤回许可回置待执行");

        PERMIT_PHASE.put("WORKING", "作业中");
        PERMIT_PHASE.put("DEMOLISHING", "安措拆除恢复中");

        TICKET_EVENT.put("ISSUE", "签发");
        TICKET_EVENT.put("PERMIT", "许可开工");
        TICKET_EVENT.put("WITHDRAW", "撤回许可");
        TICKET_EVENT.put("START_REMOVE", "通知拆除安措");
        TICKET_EVENT.put("TERMINATE", "终结");
        TICKET_EVENT.put("CANCEL", "作废");

        EQUIP_TYPE.put("TRANSFORMER", "主变压器");
        EQUIP_TYPE.put("BREAKER", "断路器");
        EQUIP_TYPE.put("DISCONNECTOR", "隔离开关");
        EQUIP_TYPE.put("CT_PT", "电流/电压互感器");
        EQUIP_TYPE.put("ARRESTER", "避雷器");

        EQUIP_STATUS.put("RUNNING", "运行");
        EQUIP_STATUS.put("STANDBY", "备用");
        EQUIP_STATUS.put("MAINTENANCE", "检修");
        EQUIP_STATUS.put("DEFECTIVE", "缺陷运行");

        INSPECT_RESULT.put("NORMAL", "正常");
        INSPECT_RESULT.put("ABNORMAL", "异常");
    }

    public static String of(Map<String, String> source, String key) {
        return key == null ? "" : source.getOrDefault(key, key);
    }

    public static String bayStatus(String status) {
        return of(BAY_STATUS, status);
    }

    public static String bayType(String type) {
        return of(BAY_TYPE, type);
    }

    public static String defectLevel(String level) {
        return of(DEFECT_LEVEL, level);
    }

    public static String defectStatus(String status) {
        return of(DEFECT_STATUS, status);
    }

    public static String workStatus(String status) {
        return of(WORK_STATUS, status);
    }

    public static String opStatus(String status) {
        return of(OP_STATUS, status);
    }

    public static String stepStatus(String status) {
        return of(STEP_STATUS, status);
    }

    public static String measureType(String type) {
        return of(MEASURE_TYPE, type);
    }

    public static String measureStatus(String status) {
        return of(MEASURE_STATUS, status);
    }

    public static String measureAction(String action) {
        return of(MEASURE_ACTION, action);
    }

    public static String permitPhase(String phase) {
        return of(PERMIT_PHASE, phase);
    }

    public static String ticketEvent(String event) {
        return of(TICKET_EVENT, event);
    }

    public static String equipType(String type) {
        return of(EQUIP_TYPE, type);
    }

    public static String equipStatus(String status) {
        return of(EQUIP_STATUS, status);
    }

    public static String inspectResult(String result) {
        return of(INSPECT_RESULT, result);
    }
}
