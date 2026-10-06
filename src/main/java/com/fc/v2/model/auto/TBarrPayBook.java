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
 * 补助核销册头对象 t_barr_pay_book（一道门：一册一轮一行）
 *
 * @author fuce
 * @date 2026-10-06
 */
@TableName("t_barr_pay_book")
@ApiModel(value = "TBarrPayBook", description = "补助核销册头")
public class TBarrPayBook implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 在册（本趟有效） */
    public static final int STATUS_ACTIVE = 0;
    /** 已撤（撤册手续办完，同册码才许重新递） */
    public static final int STATUS_WITHDRAWN = 1;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 月度补助核销册码 */
    @TableField("book_no")
    @ApiModelProperty(value = "月度补助核销册码")
    private String bookNo;

    /** 第几趟递的（撤册办完重新递另起一轮） */
    @TableField("round_no")
    @ApiModelProperty(value = "第几趟递的")
    private Integer roundNo;

    /** 册头那格写法：报册方自称的行数（只照面不翻账，作准的是逐行点出来的数） */
    @TableField("declared_rows")
    @ApiModelProperty(value = "册头那格写法：报册方自称的行数")
    private Integer declaredRows;

    /** 册子来路 DISTRICT区里自录 / STREET街道转递（两条来路进同一道门） */
    @TableField("src_kind")
    @ApiModelProperty(value = "册子来路 DISTRICT区里自录 / STREET街道转递")
    private String srcKind;

    /** 报册街道代号（当地那条封顶线按它寻） */
    @TableField("street_code")
    @ApiModelProperty(value = "报册街道代号")
    private String streetCode;

    /** 回执·册上原报几行（进门那一趟回库点出来的，屏上先亮的不作数） */
    @TableField("reported_rows")
    @ApiModelProperty(value = "回执·册上原报几行")
    private Integer reportedRows;

    /** 回执·这一趟落地几行（收下+未勾） */
    @TableField("landed_rows")
    @ApiModelProperty(value = "回执·这一趟落地几行")
    private Integer landedRows;

    /** 回执·原路退回几行（退回+落地=原报） */
    @TableField("returned_rows")
    @ApiModelProperty(value = "回执·原路退回几行")
    private Integer returnedRows;

    /** 核讫号（空册不出：一行都没落着的册用不着它） */
    @TableField("check_no")
    @ApiModelProperty(value = "核讫号")
    private String checkNo;

    /** 册情形 0在册 1已撤（撤册手续办完才许同册码重新递） */
    @TableField("status")
    @ApiModelProperty(value = "册情形 0在册 1已撤")
    private Integer status;

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

    public String getBookNo() {
        return bookNo;
    }

    public void setBookNo(String bookNo) {
        this.bookNo = bookNo;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public Integer getDeclaredRows() {
        return declaredRows;
    }

    public void setDeclaredRows(Integer declaredRows) {
        this.declaredRows = declaredRows;
    }

    public String getSrcKind() {
        return srcKind;
    }

    public void setSrcKind(String srcKind) {
        this.srcKind = srcKind;
    }

    public String getStreetCode() {
        return streetCode;
    }

    public void setStreetCode(String streetCode) {
        this.streetCode = streetCode;
    }

    public Integer getReportedRows() {
        return reportedRows;
    }

    public void setReportedRows(Integer reportedRows) {
        this.reportedRows = reportedRows;
    }

    public Integer getLandedRows() {
        return landedRows;
    }

    public void setLandedRows(Integer landedRows) {
        this.landedRows = landedRows;
    }

    public Integer getReturnedRows() {
        return returnedRows;
    }

    public void setReturnedRows(Integer returnedRows) {
        this.returnedRows = returnedRows;
    }

    public String getCheckNo() {
        return checkNo;
    }

    public void setCheckNo(String checkNo) {
        this.checkNo = checkNo;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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
