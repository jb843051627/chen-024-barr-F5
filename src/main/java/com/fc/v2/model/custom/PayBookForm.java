package com.fc.v2.model.custom;

import java.io.Serializable;
import java.util.List;

/**
 * 核销册进门报文：一册一次递齐、整册进门，不接逐行零着递。
 * 册头那几格（册码、来路、拨付年度、自报行数）与逐行那一套说的是同一批行；
 * 行上两个数（请领额、合格处数）都按原文收字符串，缺了不许谁替它凑一个，
 * 写得不像个数的由进门那一道判送回、缘故写在行上。
 *
 * @author fuce
 * @date 2026-10-06
 */
public class PayBookForm implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 月度补助核销册码（一册一码） */
    private String batchNo;

    /** 来册街道代号（街道转递带来的；区里自录可空） */
    private String streetCode;

    /** 拨付口径／来路（区级自录／市级转递，两条来路进同一道门） */
    private String payKind;

    /** 拨付年度（空着按进门那年落） */
    private String payYear;

    /** 册头自报行数（原样留底；与实际递来不合以实际为准） */
    private String declaredRows;

    /** 册行：整册一次递齐，照月度报册本来的行次序排好 */
    private List<Row> rows;

    /**
     * 册上一行：两个数都按原文递，谁也不许替它凑；
     * 行次不给手填，进门头一步由系统按落库先后排上。
     */
    public static class Row implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 被对上的改造进度卡代号（认户只认代号全等） */
        private String itemCode;

        /** 本行请领补助额（原文） */
        private String qty;

        /** 本行验收合格处数（原文） */
        private String qualiNum;

        /** 验收凭证影像（没随册来按没备齐办） */
        private String voucherImg;

        public Row() {
        }

        public Row(String itemCode, String qty, String qualiNum, String voucherImg) {
            this.itemCode = itemCode;
            this.qty = qty;
            this.qualiNum = qualiNum;
            this.voucherImg = voucherImg;
        }

        public String getItemCode() {
            return itemCode;
        }

        public void setItemCode(String itemCode) {
            this.itemCode = itemCode;
        }

        public String getQty() {
            return qty;
        }

        public void setQty(String qty) {
            this.qty = qty;
        }

        public String getQualiNum() {
            return qualiNum;
        }

        public void setQualiNum(String qualiNum) {
            this.qualiNum = qualiNum;
        }

        public String getVoucherImg() {
            return voucherImg;
        }

        public void setVoucherImg(String voucherImg) {
            this.voucherImg = voucherImg;
        }
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public String getStreetCode() {
        return streetCode;
    }

    public void setStreetCode(String streetCode) {
        this.streetCode = streetCode;
    }

    public String getPayKind() {
        return payKind;
    }

    public void setPayKind(String payKind) {
        this.payKind = payKind;
    }

    public String getPayYear() {
        return payYear;
    }

    public void setPayYear(String payYear) {
        this.payYear = payYear;
    }

    public String getDeclaredRows() {
        return declaredRows;
    }

    public void setDeclaredRows(String declaredRows) {
        this.declaredRows = declaredRows;
    }

    public List<Row> getRows() {
        return rows;
    }

    public void setRows(List<Row> rows) {
        this.rows = rows;
    }
}
