package com.fc.v2.model.custom;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 签认板面：在签张数与移交名册同回一趟算，不搞两处取数。
 * 在签张数随带单加一、随签讫减一；屏上这个数与逐口点出来的数是同一回算——
 * runningTotal 与 atNode 三口之和出自同一批在签单，绝不许一头 count、一头手填。
 * 移交名册每行第三口结论也是这一趟照落笔数出的，没有第二处手记结论栏。
 *
 * @author fuce
 * @date 2026-10-05
 */
public class JointSignBoard implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 移交名册一行：一张签讫单 */
    public static final class RosterRow {
        private String billNo;
        private String acceptNo;
        private String groupNo;
        private String streetName;
        private Integer siteCount;
        /** 第三口定案结论（照第三口算数笔同一回数出；签讫单必为「可」） */
        private String finalConclusion;
        private Date finishedTime;

        public String getBillNo() {
            return billNo;
        }

        public void setBillNo(String billNo) {
            this.billNo = billNo;
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

        public String getFinalConclusion() {
            return finalConclusion;
        }

        public void setFinalConclusion(String finalConclusion) {
            this.finalConclusion = finalConclusion;
        }

        public Date getFinishedTime() {
            return finishedTime;
        }

        public void setFinishedTime(Date finishedTime) {
            this.finishedTime = finishedTime;
        }
    }

    /** 在签总张数（= 三口各停几张之和，同一回算） */
    private int runningTotal;
    /** 停在各口的在签张数（下标即段号 0..2） */
    private int[] atNode = new int[3];
    /** 已签讫张数 */
    private int finishedTotal;
    /** 移交名册（签讫单） */
    private List<RosterRow> roster = new ArrayList<>();

    public int getRunningTotal() {
        return runningTotal;
    }

    public void setRunningTotal(int runningTotal) {
        this.runningTotal = runningTotal;
    }

    public int[] getAtNode() {
        return atNode;
    }

    public void setAtNode(int[] atNode) {
        this.atNode = atNode;
    }

    public int getFinishedTotal() {
        return finishedTotal;
    }

    public void setFinishedTotal(int finishedTotal) {
        this.finishedTotal = finishedTotal;
    }

    public List<RosterRow> getRoster() {
        return roster;
    }

    public void setRoster(List<RosterRow> roster) {
        this.roster = roster;
    }
}
