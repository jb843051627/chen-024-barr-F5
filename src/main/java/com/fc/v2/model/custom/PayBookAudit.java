package com.fc.v2.model.custom;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 核销册对账：回头任指一行，它行上的去向与回执上那三个数要能彼此对上。
 * 两个地方合不上，就把合不上的那几行点名报出来，不用「差不离」糊过去。
 * 三数与行去向出自同一回回库点算，没有第二处手填。
 *
 * @author fuce
 * @date 2026-10-06
 */
public class PayBookAudit implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 月度补助核销册码 */
    private String batchNo;

    /** 对下来合不合得拢 */
    private boolean matched;

    /** 册情形（1已核讫 2空册 9已撤册） */
    private Integer bookStatus;

    /** 册头自报行数（留底那格；与实际不合以实际为准） */
    private Integer declaredRows;

    /** 回库数出的原报行数 */
    private int reportedRows;

    /** 回库数出的落地行数 */
    private int landedRows;

    /** 回库数出的退回行数 */
    private int returnedRows;

    /** 合不上的地方一句句讲清（哪一行、哪一格对不上） */
    private List<String> problems = new ArrayList<>();

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public boolean isMatched() {
        return matched;
    }

    public void setMatched(boolean matched) {
        this.matched = matched;
    }

    public Integer getBookStatus() {
        return bookStatus;
    }

    public void setBookStatus(Integer bookStatus) {
        this.bookStatus = bookStatus;
    }

    public Integer getDeclaredRows() {
        return declaredRows;
    }

    public void setDeclaredRows(Integer declaredRows) {
        this.declaredRows = declaredRows;
    }

    public int getReportedRows() {
        return reportedRows;
    }

    public void setReportedRows(int reportedRows) {
        this.reportedRows = reportedRows;
    }

    public int getLandedRows() {
        return landedRows;
    }

    public void setLandedRows(int landedRows) {
        this.landedRows = landedRows;
    }

    public int getReturnedRows() {
        return returnedRows;
    }

    public void setReturnedRows(int returnedRows) {
        this.returnedRows = returnedRows;
    }

    public List<String> getProblems() {
        return problems;
    }

    public void setProblems(List<String> problems) {
        this.problems = problems;
    }
}
