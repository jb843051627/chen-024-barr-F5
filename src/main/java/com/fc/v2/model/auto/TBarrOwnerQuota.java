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
 * 经办人季度定额对象 t_barr_owner_quota
 *
 * @author fuce
 * @date 2026-10-05
 */
@TableName("t_barr_owner_quota")
@ApiModel(value = "TBarrOwnerQuota", description = "经办人季度定额")
public class TBarrOwnerQuota implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 经办用户id */
    @TableField("owner_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "经办用户id")
    private Long ownerId;

    /** 定额所属季 如 2026Q4 */
    @TableField("season")
    @ApiModelProperty(value = "定额所属季")
    private String season;

    /** 本季最多递出几条 */
    @TableField("quota")
    @ApiModelProperty(value = "本季最多递出几条")
    private Integer quota;

    /** 创建者 */
    @TableField(value = "create_by", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = com.baomidou.mybatisplus.annotation.FieldFill.UPDATE)
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = com.baomidou.mybatisplus.annotation.FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getSeason() {
        return season;
    }

    public void setSeason(String season) {
        this.season = season;
    }

    public Integer getQuota() {
        return quota;
    }

    public void setQuota(Integer quota) {
        this.quota = quota;
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
}
