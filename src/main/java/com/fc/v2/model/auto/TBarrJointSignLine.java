package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 联户签认落笔留痕对象 t_barr_joint_sign_line：一笔一行，只许追加，不许改行删行。
 * 三口每笔落在哪一口、第几轮、哪个席位，全由系统在唯一的落签口里钉死，报文段号/席位不作数；
 * 已签几人不入别的账，永远照本表数出来。打回只把本口本轮已落的笔置作废，更早各口旧签一笔不动；
 * 同席改口旧笔标 supersede 另落新笔；并发各笔按 line_seq 各记各的先后，不并成一句。
 *
 * @author fuce
 * @date 2026-10-05
 */
@TableName("t_barr_joint_sign_line")
@ApiModel(value = "TBarrJointSignLine", description = "联户签认落笔留痕")
public class TBarrJointSignLine implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 落笔结论：可 */
    public static final String VERDICT_YES = "可";
    /** 落笔结论：不可 */
    public static final String VERDICT_NO = "不可";

    /** 还算数 */
    public static final int VALID = 1;
    /** 已作废 */
    public static final int VOID = 0;

    /** 作废缘故：本口判不予通过，打回抹去 */
    public static final String VOID_REJECT = "reject";
    /** 作废缘故：同席改口，旧笔让给新笔 */
    public static final String VOID_SUPERSEDE = "supersede";

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 落在哪张联户签认单上 */
    @TableField("bill_id")
    private Long billId;

    /** 验收记录号（随笔冗余，受理簿对笔用） */
    @TableField("accept_no")
    private String acceptNo;

    /** 落在哪一口 0..2（系统钉死，报文段号不作数） */
    @TableField("node_no")
    private Integer nodeNo;

    /** 第几轮落的（打回新旧两轮分开放） */
    @TableField("round_no")
    private Integer roundNo;

    /** 席位 HOUSEHOLD/BUILDER/ASSESSOR/DISTRICT/CITY（规则书回的，报文不许自拟） */
    @TableField("seat_key")
    private String seatKey;

    /** 席位名（规则书回的，不由报文定） */
    @TableField("seat_name")
    private String seatName;

    /** 落笔人（当场签字那位） */
    @TableField("signer")
    private String signer;

    /** 落笔结论 可/不可 */
    @TableField("verdict")
    private String verdict;

    /** 本口本轮落笔次第（并发各记各的先后） */
    @TableField("line_seq")
    private Integer lineSeq;

    /** 还算不算数 1算数 0已作废 */
    @TableField("valid_flag")
    private Integer validFlag;

    /** 作废缘故 reject / supersede */
    @TableField("void_reason")
    private String voidReason;

    /** 录笔经办（登录身份） */
    @TableField("operator")
    private String operator;

    /** 这一笔附言 */
    @TableField("note")
    private String note;

    /** 落笔时间 */
    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBillId() {
        return billId;
    }

    public void setBillId(Long billId) {
        this.billId = billId;
    }

    public String getAcceptNo() {
        return acceptNo;
    }

    public void setAcceptNo(String acceptNo) {
        this.acceptNo = acceptNo;
    }

    public Integer getNodeNo() {
        return nodeNo;
    }

    public void setNodeNo(Integer nodeNo) {
        this.nodeNo = nodeNo;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public String getSeatKey() {
        return seatKey;
    }

    public void setSeatKey(String seatKey) {
        this.seatKey = seatKey;
    }

    public String getSeatName() {
        return seatName;
    }

    public void setSeatName(String seatName) {
        this.seatName = seatName;
    }

    public String getSigner() {
        return signer;
    }

    public void setSigner(String signer) {
        this.signer = signer;
    }

    public String getVerdict() {
        return verdict;
    }

    public void setVerdict(String verdict) {
        this.verdict = verdict;
    }

    public Integer getLineSeq() {
        return lineSeq;
    }

    public void setLineSeq(Integer lineSeq) {
        this.lineSeq = lineSeq;
    }

    public Integer getValidFlag() {
        return validFlag;
    }

    public void setValidFlag(Integer validFlag) {
        this.validFlag = validFlag;
    }

    public String getVoidReason() {
        return voidReason;
    }

    public void setVoidReason(String voidReason) {
        this.voidReason = voidReason;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Date getCreateTime() {
        return createTime;
    }

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
