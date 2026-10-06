package com.fc.v2.model.custom;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 核销册回执：一册办完给的交代。
 * 栏上只有三个数——册上原报几行、这一趟落地几行、原路退回几行，
 * 退回的与落地的并起来要等于原报的数；三个数都只认回库一行一行数出来的，
 * 屏上先亮的那份不作数。没落成的行把纸面行次随回执带出去，好让街道对得上号。
 *
 * @author fuce
 * @date 2026-10-06
 */
public class PayBookReceipt implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 这一趟的落法：LANDED 收下办理 / EMPTY 空册 / DUPLICATE 照面（同一册码第二遍） / OVER_LIMIT 整册退回（压过单册上限） */
    private String outcome;

    /** 月度补助核销册码 */
    private String batchNo;

    /** 三数之一：册上原报几行（回库数出） */
    private int reportedRows;

    /** 三数之二：这一趟落地几行（收下；回库数出） */
    private int landedRows;

    /** 三数之三：原路退回几行（送回；回库数出） */
    private int returnedRows;

    /** 空册：一行都没落着。空册只写「空册」两字，不出核讫 */
    private boolean empty;

    /** 给递册人的一句话（落法与三数） */
    private String message;

    /** 没落成的行：纸面行次＋送回缘故，随回执带出 */
    private List<ReturnedItem> returnedItems = new ArrayList<>();

    /** 合不上的地方点名（册头自报不合、照面不合、空悬行等），不用「差不离」糊过去 */
    private List<String> problems = new ArrayList<>();

    /** 落法：收下办理（有行落成，册上落核讫） */
    public static final String OUTCOME_LANDED = "LANDED";
    /** 落法：空册（一行都没落着，不出核讫） */
    public static final String OUTCOME_EMPTY = "EMPTY";
    /** 落法：照面（同一册码第二遍，不添一行不减一行） */
    public static final String OUTCOME_DUPLICATE = "DUPLICATE";
    /** 落法：整册退回（压过单册行数上限，库里不留半行） */
    public static final String OUTCOME_OVER_LIMIT = "OVER_LIMIT";

    /** 没落成的一行：纸面行次＋缘故 */
    public static class ReturnedItem implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 原报行次（系统进门时排上的那个，与街道纸面报册对得上号） */
        private int rowNo;

        /** 送回缘故（写在行上的那条） */
        private String reason;

        public ReturnedItem() {
        }

        public ReturnedItem(int rowNo, String reason) {
            this.rowNo = rowNo;
            this.reason = reason;
        }

        public int getRowNo() {
            return rowNo;
        }

        public void setRowNo(int rowNo) {
            this.rowNo = rowNo;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
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

    public boolean isEmpty() {
        return empty;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<ReturnedItem> getReturnedItems() {
        return returnedItems;
    }

    public void setReturnedItems(List<ReturnedItem> returnedItems) {
        this.returnedItems = returnedItems;
    }

    public List<String> getProblems() {
        return problems;
    }

    public void setProblems(List<String> problems) {
        this.problems = problems;
    }
}
