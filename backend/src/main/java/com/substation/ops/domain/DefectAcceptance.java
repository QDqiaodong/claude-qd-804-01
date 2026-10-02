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
 * 消缺验收记录：处理人每提交一次处理结果（进入待验收），对应一条待验收结论记录；
 * 由【不同于处理人】的值班员填写验收结论——通过闭环 / 驳回返工。
 *
 * 只增不改：驳回返工后本次处理人、处理结果、验收人、驳回原因全部保留，
 * 处理人重新提交会再产生一条新一轮记录，全程可追溯。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "defect_acceptance")
public class DefectAcceptance {

    /** 验收结论 */
    public static final String RESULT_PASS = "PASS";
    public static final String RESULT_REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "defect_id")
    private Long defectId;

    /** 第几轮处理/验收（首次提交为 1，每次驳回返工后重新提交 +1） */
    @Column(name = "round_no")
    private Integer roundNo;

    /** 本轮处理人（消缺负责人） */
    @Column(name = "processed_by", length = 32)
    private String processedBy;

    /** 本轮处理结果说明 */
    @Column(name = "process_result", length = 512)
    private String processResult;

    /** 处理结果提交时间（进入待验收时间） */
    @Column(name = "submitted_time")
    private LocalDateTime submittedTime;

    /** 验收人；结论未填写时为 null（待验收中） */
    @Column(name = "accepted_by", length = 32)
    private String acceptedBy;

    /** 验收结论：PASS / REJECTED；待验收中为 null */
    @Column(name = "result", length = 16)
    private String result;

    /** 驳回原因（驳回时必填）；通过时为验收意见，可空 */
    @Column(name = "accept_opinion", length = 512)
    private String acceptOpinion;

    /** 验收时间 */
    @Column(name = "accepted_time")
    private LocalDateTime acceptedTime;
}
