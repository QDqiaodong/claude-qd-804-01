-- =============================================================================
-- 存储过程定义。由 com.substation.ops.config.ProcedureInitializer 在应用启动时
-- 读取本文件并通过 DataSource 逐条执行（语句之间用单独一行的美元符分隔）。
--
-- 之所以不在 mysql/schema.sql 里写：那里是构建期 MySQL 容器里跑的，带 BEGIN...END
-- 的过程体在 >> 重定向到 mysql 客户端时会被 ; 截断，所以一律挪到这里，由 backend 建。
-- =============================================================================

DROP PROCEDURE IF EXISTS sp_defect_summary
$$
CREATE PROCEDURE sp_defect_summary(IN p_substation_id BIGINT, IN p_month CHAR(7))
BEGIN
    SET p_month = IFNULL(p_month, DATE_FORMAT(CURDATE(), '%Y-%m'));

    SELECT
        s.id                                                                                AS stat_id,
        s.id                                                                                AS substation_id,
        s.name                                                                              AS substation_name,
        COUNT(d.id)                                                                         AS total_count,
        SUM(CASE WHEN d.status = 'ACCEPTED' THEN 1 ELSE 0 END)                              AS closed_count,
        SUM(CASE WHEN d.status <> 'ACCEPTED' AND d.due_date < CURDATE() THEN 1 ELSE 0 END)  AS overdue_count,
        SUM(CASE WHEN d.status <> 'ACCEPTED' AND d.due_date >= CURDATE() THEN 1 ELSE 0 END) AS open_count,
        ROUND(100.0 * IFNULL(SUM(CASE WHEN d.status = 'ACCEPTED' THEN 1 ELSE 0 END), 0)
              / NULLIF(COUNT(d.id), 0), 1)                                                  AS fix_rate
    FROM substation s
             LEFT JOIN bay b ON b.substation_id = s.id
             LEFT JOIN defect d ON d.bay_id = b.id AND DATE_FORMAT(d.found_time, '%Y-%m') = p_month
    WHERE (p_substation_id IS NULL OR p_substation_id = 0 OR s.id = p_substation_id)
    GROUP BY s.id, s.name
    ORDER BY overdue_count DESC, total_count DESC, s.id ASC;
END
$$

DROP PROCEDURE IF EXISTS sp_equipment_summary_by_voltage
$$
CREATE PROCEDURE sp_equipment_summary_by_voltage(IN p_substation_id BIGINT)
BEGIN
    SELECT
        MIN(x.id)                                                                            AS stat_id,
        x.voltage_level                                                                      AS voltage_level,
        COUNT(x.id)                                                                          AS equip_total,
        SUM(CASE WHEN x.status = 'RUNNING' THEN 1 ELSE 0 END)                                AS running_total,
        SUM(CASE WHEN x.last_check_date IS NULL
                      OR x.last_check_date < DATE_SUB(CURDATE(), INTERVAL 365 DAY)
                 THEN 1 ELSE 0 END)                                                          AS pending_total,
        IFNULL(SUM(x.open_defect_count), 0)                                                  AS open_defect_total
    FROM (
             SELECT e.id,
                    e.status,
                    e.last_check_date,
                    b.voltage_level,
                    (SELECT COUNT(1) FROM defect d WHERE d.equipment_id = e.id AND d.status <> 'ACCEPTED')
                        AS open_defect_count
             FROM equipment e
                      JOIN bay b ON b.id = e.bay_id
             WHERE (p_substation_id IS NULL OR p_substation_id = 0 OR b.substation_id = p_substation_id)
         ) x
    GROUP BY x.voltage_level
    ORDER BY FIELD(x.voltage_level, '110kV', '35kV', '10kV'), x.voltage_level;
END
$$

DROP PROCEDURE IF EXISTS sp_next_work_ticket_no
$$
CREATE PROCEDURE sp_next_work_ticket_no(
    IN p_bay_id BIGINT,
    IN p_issue_date CHAR(10),
    OUT p_ticket_no VARCHAR(64)
)
BEGIN
    DECLARE v_seq INT DEFAULT 1;
    DECLARE v_bay_code VARCHAR(32);
    DECLARE v_station_code VARCHAR(32);

    SELECT COUNT(1) + 1 INTO v_seq FROM work_ticket wt WHERE wt.bay_id = p_bay_id AND wt.issue_date = p_issue_date;

    SELECT b.code, s.code
    INTO v_bay_code, v_station_code
    FROM bay b
             LEFT JOIN substation s ON s.id = b.substation_id
    WHERE b.id = p_bay_id;

    SET v_bay_code = IFNULL(v_bay_code, 'NA');
    SET v_station_code = IFNULL(v_station_code, 'XX');

    SET p_ticket_no = CONCAT('GZP-', v_station_code, '-', REPLACE(IFNULL(p_issue_date, DATE_FORMAT(CURDATE(), '%Y-%m-%d')), '-', ''),
                             '-', LPAD(v_seq, 2, '0'), '-', v_bay_code);

    -- 回写：给该间隔当天尚未编号的票打好生成痕迹
    UPDATE work_ticket wt
    SET wt.remark = CONCAT('票号由存储过程 sp_next_work_ticket_no 于 ', DATE_FORMAT(NOW(), '%Y-%m-%d %H:%i:%s'), ' 自动签发')
    WHERE wt.bay_id = p_bay_id
      AND wt.issue_date = IFNULL(p_issue_date, CURDATE())
      AND (wt.ticket_no IS NULL OR wt.ticket_no = '');
END
$$
