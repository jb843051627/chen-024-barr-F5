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
import java.math.BigDecimal;
import java.util.Date;

/**
 * 质保与养护复检条目对象 t_barr_visit_item
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_barr_visit_item")
@ApiModel(value = "TBarrVisitItem", description = "质保与养护复检条目")
public class TBarrVisitItem implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 质保与养护复检条目代号 */
    @TableField("item_no")
    @ApiModelProperty(value = "质保与养护复检条目代号")
    private String itemNo;

    /** 该上门那一日的止点时刻 */
    @TableField("due_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "该上门那一日的止点时刻")
    private Date dueAt;

    /** 最多许提前几日 */
    @TableField("amount")
    @ApiModelProperty(value = "最多许提前几日")
    private BigDecimal amount;

    /** 事由与所对住户的记要 */
    @TableField("content")
    @ApiModelProperty(value = "事由与所对住户的记要")
    private String content;

    /** 条目所在街代号（同街同楼组并成一段，一趟走完） */
    @TableField("street_code")
    @ApiModelProperty(value = "条目所在街代号")
    private String streetCode;

    /** 街名（随条冗余） */
    @TableField("street_name")
    @ApiModelProperty(value = "街名")
    private String streetName;

    /** 楼组片号（同街同号并一段） */
    @TableField("group_no")
    @ApiModelProperty(value = "楼组片号")
    private String groupNo;

    /** 所对住户的底册（底册已停用/撤下即住户撤走，本桩递不动） */
    @TableField("arch_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "所对住户的底册")
    private Long archId;

    /** 摊到谁名下（经办用户id；定额按人按季卡） */
    @TableField("owner_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "经办用户id")
    private Long ownerId;

    /** 经办登录名（随条冗余，递出时照抄落印） */
    @TableField("owner_name")
    @ApiModelProperty(value = "经办登录名")
    private String ownerName;

    /** 联络栏（空着好几季没人填的，天生办不成） */
    @TableField("contact")
    @ApiModelProperty(value = "联络栏")
    private String contact;

    /** 本条目所属季（如 2026Q4，录入时定死） */
    @TableField("season")
    @ApiModelProperty(value = "所属季")
    private String season;

    /** 条目情形 0搁着 1已递出 2递不动（只此三格，落不进第四种） */
    @TableField("status")
    @ApiModelProperty(value = "条目情形 0搁着 1已递出 2递不动")
    private Integer status;

    /** 递出那一刻（与状态同回；缺了不算走完） */
    @TableField("delivered_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "递出那一刻")
    private Date deliveredAt;

    /** 递出经办那位（与状态同回；缺了不算走完） */
    @TableField("delivered_by")
    @ApiModelProperty(value = "递出经办那位")
    private String deliveredBy;

    /** 卡壳缘故（挂递不动时连环节一起写回，日后照着捞出重排） */
    @TableField("stall_reason")
    @ApiModelProperty(value = "卡壳缘故")
    private String stallReason;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getItemNo() {
        return itemNo;
    }

    public void setItemNo(String itemNo) {
        this.itemNo = itemNo;
    }

    public Date getDueAt() {
        return dueAt;
    }

    public void setDueAt(Date dueAt) {
        this.dueAt = dueAt;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getSeason() {
        return season;
    }

    public void setSeason(String season) {
        this.season = season;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Date getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Date deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public String getDeliveredBy() {
        return deliveredBy;
    }

    public void setDeliveredBy(String deliveredBy) {
        this.deliveredBy = deliveredBy;
    }

    public String getStallReason() {
        return stallReason;
    }

    public void setStallReason(String stallReason) {
        this.stallReason = stallReason;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
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
