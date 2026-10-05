package com.fc.v2.model.custom;

import java.io.Serializable;

/**
 * 当场验收落簿表单（受理簿一笔）。
 *
 * @author fuce
 * @date 2026-10-05
 */
public class AcceptRecordForm implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 挂在哪一处底册名下（底册id） */
    private String archId;
    /** 楼组片号（一个楼组几户一道改） */
    private String groupNo;
    /** 本场合验户数（正整数） */
    private String siteCount;
    /** 当场验收时刻（可空，空着落当下） */
    private String acceptTime;
    /** 备注 */
    private String remark;

    public String getArchId() {
        return archId;
    }

    public void setArchId(String archId) {
        this.archId = archId;
    }

    public String getGroupNo() {
        return groupNo;
    }

    public void setGroupNo(String groupNo) {
        this.groupNo = groupNo;
    }

    public String getSiteCount() {
        return siteCount;
    }

    public void setSiteCount(String siteCount) {
        this.siteCount = siteCount;
    }

    public String getAcceptTime() {
        return acceptTime;
    }

    public void setAcceptTime(String acceptTime) {
        this.acceptTime = acceptTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
