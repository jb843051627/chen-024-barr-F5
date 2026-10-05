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
 * 当场验收记录对象 t_barr_accept_record（当日受理簿：一场验收一行）。
 * 联户验收签认单只能由这里关联带出，别处另起手建的一张不算；
 * 记录上已有的字段（楼组/底册/街/户数/当场时刻/落笔者），单上直接沿用。
 *
 * @author fuce
 * @date 2026-10-05
 */
@TableName("t_barr_accept_record")
@ApiModel(value = "TBarrAcceptRecord", description = "当场验收记录（受理簿）")
public class TBarrAcceptRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 当场验收记录号（受理簿上那一笔，一场一号） */
    @TableField("accept_no")
    @ApiModelProperty(value = "当场验收记录号")
    private String acceptNo;

    /** 楼组片号（一个楼组几户一道改、一场验收） */
    @TableField("group_no")
    @ApiModelProperty(value = "楼组片号")
    private String groupNo;

    /** 挂在哪一处底册名下 */
    @TableField("arch_id")
    @ApiModelProperty(value = "底册id")
    private Long archId;

    /** 底册代号（以底册为准，随记录冗余） */
    @TableField("arch_no")
    @ApiModelProperty(value = "底册代号")
    private String archNo;

    /** 底册全称（以底册为准，随记录冗余） */
    @TableField("arch_name")
    @ApiModelProperty(value = "底册全称")
    private String archName;

    /** 底册类别（随记录冗余） */
    @TableField("arch_kind")
    @ApiModelProperty(value = "底册类别")
    private String archKind;

    /** 验收所在街代号（由底册属地落到街道） */
    @TableField("street_code")
    @ApiModelProperty(value = "街代号")
    private String streetCode;

    /** 街名（随记录冗余） */
    @TableField("street_name")
    @ApiModelProperty(value = "街名")
    private String streetName;

    /** 本场合验户数（楼组里一道改的几户） */
    @TableField("site_count")
    @ApiModelProperty(value = "本场合验户数")
    private Integer siteCount;

    /** 当场验收那一刻 */
    @TableField("accept_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "当场验收那一刻")
    private Date acceptTime;

    /** 当场受理簿落笔者（登录身份） */
    @TableField("holder")
    @ApiModelProperty(value = "受理簿落笔者")
    private String holder;

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
