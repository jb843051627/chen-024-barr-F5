package com.fc.v2.model.custom;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 核销册对账结果：回头任指一行，它行上的去向与回执上那三个数要能彼此对上。
 * 册头那本（回执三数）与逐行那套（回库现点）两处合不上，
 * 就把合不上的那几行点名报出来，别用"差不离"糊过去。
 *
 * @author fuce
 * @date 2026-10-06
 */
public class PayBookAudit implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 册码 */
    private String bookNo;
    /** 对的是第几轮（在册那轮） */
    private Integer roundNo;
    /** 册在不在（在册轮寻不着=不对这本） */
    private boolean found;
    /** 册头回执·原报 */
    private int storedReported;
    /** 册头回执·落地 */
    private int storedLanded;
    /** 册头回执·退回 */
    private int storedReturned;
    /** 回库现点·原报 */
    private int actualReported;
    /** 回库现点·落地（收下+未勾） */
    private int actualLanded;
    /** 回库现点·退回 */
    private int actualReturned;
    /** 两处合不合得上 */
    private boolean consistent;
    /** 合不上的点名（逐条写清哪一行、哪一格对不上） */
    private List<String> mismatches = new ArrayList<>();

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

    public boolean isFound() {
        return found;
    }

    public void setFound(boolean found) {
        this.found = found;
    }

    public int getStoredReported() {
        return storedReported;
    }

    public void setStoredReported(int storedReported) {
        this.storedReported = storedReported;
    }

    public int getStoredLanded() {
        return storedLanded;
    }

    public void setStoredLanded(int storedLanded) {
        this.storedLanded = storedLanded;
    }

    public int getStoredReturned() {
        return storedReturned;
    }

    public void setStoredReturned(int storedReturned) {
        this.storedReturned = storedReturned;
    }

    public int getActualReported() {
        return actualReported;
    }

    public void setActualReported(int actualReported) {
        this.actualReported = actualReported;
    }

    public int getActualLanded() {
        return actualLanded;
    }

    public void setActualLanded(int actualLanded) {
        this.actualLanded = actualLanded;
    }

    public int getActualReturned() {
        return actualReturned;
    }

    public void setActualReturned(int actualReturned) {
        this.actualReturned = actualReturned;
    }

    public boolean isConsistent() {
        return consistent;
    }

    public void setConsistent(boolean consistent) {
        this.consistent = consistent;
    }

    public List<String> getMismatches() {
        return mismatches;
    }

    public void setMismatches(List<String> mismatches) {
        this.mismatches = mismatches;
    }
}
