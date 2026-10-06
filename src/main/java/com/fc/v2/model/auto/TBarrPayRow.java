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
 * 补助核销行对象 t_barr_pay_row
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_barr_pay_row")
@ApiModel(value = "TBarrPayRow", description = "补助核销行")
public class TBarrPayRow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 月度补助核销册码 */
    @TableField("batch_no")
    @ApiModelProperty(value = "月度补助核销册码")
    private String batchNo;

    /** 第几趟递的（撤册办完重新递另起一轮，两轮分开放；旧行按1接住） */
    @TableField("round_no")
    @ApiModelProperty(value = "第几趟递的")
    private Integer roundNo;

    /** 原报行次（进门由系统按落库先后排上，排定不再挪，不给手填） */
    @TableField("row_no")
    @ApiModelProperty(value = "原报行次")
    private Integer rowNo;

    /** 被对上的改造进度卡代号 */
    @TableField("item_code")
    @ApiModelProperty(value = "被对上的改造进度卡代号")
    private String itemCode;

    /** 本行请领补助额 */
    @TableField("qty")
    @ApiModelProperty(value = "本行请领补助额")
    private BigDecimal qty;

    /** 本行验收合格处数 */
    @TableField("quali_num")
    @ApiModelProperty(value = "本行验收合格处数")
    private BigDecimal qualiNum;

    /** 验收凭证影像（随册递来的影像号；空着=没备齐，行送回） */
    @TableField("voucher_img")
    @ApiModelProperty(value = "验收凭证影像")
    private String voucherImg;

    /** 拨付口径（区级／市级两条来路） */
    @TableField("pay_kind")
    @ApiModelProperty(value = "拨付口径（区级／市级两条来路）")
    private String payKind;

    /** 行落地情形 0未勾 1已收下 2已送回 */
    @TableField("status")
    @ApiModelProperty(value = "行落地情形 0未勾 1已收下 2已送回")
    private Integer status;

    /** 送回缘故（送回行必落在行上；收下/未勾为空） */
    @TableField("return_reason")
    @ApiModelProperty(value = "送回缘故")
    private String returnReason;

    /** 往期层标记 0现行 1已挪往期（只朝旧册开，新册走不到） */
    @TableField("past_flag")
    @ApiModelProperty(value = "往期层标记 0现行 1已挪往期")
    private Integer pastFlag;

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

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public Integer getRowNo() {
        return rowNo;
    }

    public void setRowNo(Integer rowNo) {
        this.rowNo = rowNo;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public BigDecimal getQualiNum() {
        return qualiNum;
    }

    public void setQualiNum(BigDecimal qualiNum) {
        this.qualiNum = qualiNum;
    }

    public String getVoucherImg() {
        return voucherImg;
    }

    public void setVoucherImg(String voucherImg) {
        this.voucherImg = voucherImg;
    }

    public String getPayKind() {
        return payKind;
    }

    public void setPayKind(String payKind) {
        this.payKind = payKind;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getReturnReason() {
        return returnReason;
    }

    public void setReturnReason(String returnReason) {
        this.returnReason = returnReason;
    }

    public Integer getPastFlag() {
        return pastFlag;
    }

    public void setPastFlag(Integer pastFlag) {
        this.pastFlag = pastFlag;
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
