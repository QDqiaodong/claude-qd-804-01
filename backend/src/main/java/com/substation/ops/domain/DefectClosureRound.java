package com.substation.ops.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 缺陷消缺验收轮次（只增不改）。
 *
 * 处理人每「报完工」一次新增一行（状态 SUBMITTED）；另一名值班员验收时在该行
 * 补写结论：PASS 通过 / REJECT 驳回。驳回返工后处理人重新报完工再新增一行，
 * 上一轮（含处理人、处理结果、验收人、驳回原因与时间）原样保留，全程可追溯。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "defect_closure_round")
public class DefectClosureRound {

    /** 本轮已提交处理结果，等待独立验收 */
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    /** 验收通过（本轮闭环） */
    public static final String STATUS_PASS = "PASS";
    /** 验收驳回，退回处理中返工 */
    public static final String STATUS_REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "defect_id")
    private Long defectId;

    /** 第几轮，从 1 开始，驳回返工后自增 */
    @Column(name = "round_no")
    private Integer roundNo;

    @Column(name = "process_result", length = 512)
    private String processResult;

    @Column(name = "process_person", length = 32)
    private String processPerson;

    @Column(name = "process_time")
    private LocalDateTime processTime;

    /** 验收结论：PASS / REJECT；未验收时为 null */
    @Column(name = "accept_verdict", length = 16)
    private String acceptVerdict;

    @Column(name = "accept_comment", length = 512)
    private String acceptComment;

    @Column(name = "accept_person", length = 32)
    private String acceptPerson;

    @Column(name = "accept_time")
    private LocalDateTime acceptTime;

    /** 本轮状态：SUBMITTED 待验收 / PASS 通过 / REJECTED 已驳回 */
    @Column(name = "status", length = 24)
    private String status;
}
