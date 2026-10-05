package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
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
 * 联户验收签认单（会签版）对象 t_barr_joint_sign_bill。
 * 只由当场验收记录（t_barr_accept_record）关联带出，同一场验收只带得出一张（uk_joint_accept）；
 * 记录上已有的字段单上直接沿用，随单快照。单上不存"已签几人"这种上账数——
 * 那栏只由系统照落笔留痕一口一口数出来回显；段序、轮次、情形也只在落签那一处按落笔实况挪，
 * 报文里的段号到了这里不作数。没有删除标记：签讫一笔进不来、一字改不了、整张也抽不走。
 *
 * @author fuce
 * @date 2026-10-05
 */
@TableName("t_barr_joint_sign_bill")
@ApiModel(value = "TBarrJointSignBill", description = "联户验收签认单（会签版）")
public class TBarrJointSignBill implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 单子情形：在签（打回退一格重走仍落在在签） */
    public static final int STATUS_RUNNING = 0;
    /** 单子情形：已签讫（末口同判点齐，此后四封） */
    public static final int STATUS_FINISHED = 1;

    /** 头一口：住户本人或看护人 */
    public static final int NODE_HOUSEHOLD = 0;
    /** 第二口：施工方、第三方评估机构各一名 */
    public static final int NODE_ON_SITE = 1;
    /** 第三口：区残联一名、市残联一名 */
    public static final int NODE_DISABLED_FED = 2;
    /** 三口走完：单签讫，不再有下一口 */
    public static final int NODE_DONE = 3;

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 联户验收签认单号（由验收记录号带出，一场一号） */
    @TableField("bill_no")
    @ApiModelProperty(value = "联户验收签认单号")
    private String billNo;

    /** 只由哪份当场验收记录带出（来路唯一，不许另起手建） */
    @TableField("accept_id")
    @ApiModelProperty(value = "带出本单的验收记录id")
    private Long acceptId;

    /** 验收记录号（记录上已有，单上直接沿用） */
    @TableField("accept_no")
    @ApiModelProperty(value = "验收记录号")
    private String acceptNo;

    /** 楼组片号（沿自验收记录） */
    @TableField("group_no")
    @ApiModelProperty(value = "楼组片号")
    private String groupNo;

    /** 底册id（沿自验收记录） */
    @TableField("arch_id")
    @ApiModelProperty(value = "底册id")
    private Long archId;

    /** 底册代号（沿自验收记录） */
    @TableField("arch_no")
    @ApiModelProperty(value = "底册代号")
    private String archNo;

    /** 底册全称（沿自验收记录） */
    @TableField("arch_name")
    @ApiModelProperty(value = "底册全称")
    private String archName;

    /** 底册类别（沿自验收记录） */
    @TableField("arch_kind")
    @ApiModelProperty(value = "底册类别")
    private String archKind;

    /** 街代号（沿自验收记录） */
    @TableField("street_code")
    @ApiModelProperty(value = "街代号")
    private String streetCode;

    /** 街名（沿自验收记录） */
    @TableField("street_name")
    @ApiModelProperty(value = "街名")
    private String streetName;

    /** 合验户数（沿自验收记录） */
    @TableField("site_count")
    @ApiModelProperty(value = "合验户数")
    private Integer siteCount;

    /** 当场验收那一刻（沿自验收记录） */
    @TableField("accept_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "当场验收那一刻")
    private Date acceptTime;

    /** 当场受理簿落笔者（沿自验收记录） */
    @TableField("holder")
    @ApiModelProperty(value = "受理簿落笔者")
    private String holder;

    /** 单停在第几段 0住户 1到场两方 2两级残联（系统按落笔挪，报文段号不作数） */
    @TableField("node_no")
    @ApiModelProperty(value = "单停在第几段（系统数出，不许手填）")
    private Integer nodeNo;

    /** 本单走到第几轮（打回一轮旧笔作废留底，新笔另起一轮，两轮分开放） */
    @TableField("round_no")
    @ApiModelProperty(value = "本单走到第几轮")
    private Integer roundNo;

    /** 单子情形 0在签 1已签讫 */
    @TableField("status")
    @ApiModelProperty(value = "单子情形 0在签 1已签讫（由落笔实况派生）")
    private Integer status;

    /** 签讫那一刻（末口同判点齐才落） */
    @TableField("finished_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "签讫那一刻")
    private Date finishedTime;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public Long getAcceptId() {
        return acceptId;
    }

    public void setAcceptId(Long acceptId) {
        this.acceptId = acceptId;
    }

    public String getAcceptNo() {
        return acceptNo;
    }

    public void setAcceptNo(String acceptNo) {
        this.acceptNo = acceptNo;
    }

    public String getGroupNo() {
        return groupNo;
    }

    public void setGroupNo(String groupNo) {
        this.groupNo = groupNo;
    }

    public Long getArchId() {
        return archId;
    }

    public void setArchId(Long archId) {
        this.archId = archId;
    }

    public String getArchNo() {
        return archNo;
    }

    public void setArchNo(String archNo) {
        this.archNo = archNo;
    }

    public String getArchName() {
        return archName;
    }

    public void setArchName(String archName) {
        this.archName = archName;
    }

    public String getArchKind() {
        return archKind;
    }

    public void setArchKind(String archKind) {
        this.archKind = archKind;
    }

    public String getStreetCode() {
        return streetCode;
    }

    public void setStreetCode(String streetCode) {
        this.streetCode = streetCode;
    }

    public String getStreetName() {
        return streetName;
    }

    public void setStreetName(String streetName) {
        this.streetName = streetName;
    }

    public Integer getSiteCount() {
        return siteCount;
    }

    public void setSiteCount(Integer siteCount) {
        this.siteCount = siteCount;
    }

    public Date getAcceptTime() {
        return acceptTime;
    }

    public void setAcceptTime(Date acceptTime) {
        this.acceptTime = acceptTime;
    }

    public String getHolder() {
        return holder;
    }

    public void setHolder(String holder) {
        this.holder = holder;
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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Date getFinishedTime() {
        return finishedTime;
    }

    public void setFinishedTime(Date finishedTime) {
        this.finishedTime = finishedTime;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
