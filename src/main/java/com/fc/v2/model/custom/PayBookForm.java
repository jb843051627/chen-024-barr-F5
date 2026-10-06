package com.fc.v2.model.custom;

import java.io.Serializable;
import java.util.List;

import com.fc.v2.model.auto.TBarrPayRow;

/**
 * 核销册进门报文：一册一次递齐、整册进门，不接逐行零着递。
 * 区里自录与街道转递两条来路都递这一份，进的是同一道门。
 *
 * @author fuce
 * @date 2026-10-06
 */
public class PayBookForm implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 月度补助核销册码（空着门都认不得） */
    private String bookNo;

    /** 册头那格写法：报册方自称的行数（只照面不翻账，作准的是逐行落库点出来的数） */
    private Integer declaredRows;

    /** 册子来路 DISTRICT区里自录 / STREET街道转递 */
    private String srcKind;

    /** 报册街道代号（当地那条封顶线按它寻） */
    private String streetCode;

    /** 逐行：本行请领额、验收合格处数、进度卡代号、验收凭证影像都挂行上 */
    private List<TBarrPayRow> rows;

    public String getBookNo() {
        return bookNo;
    }

    public void setBookNo(String bookNo) {
        this.bookNo = bookNo;
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

    public List<TBarrPayRow> getRows() {
        return rows;
    }

    public void setRows(List<TBarrPayRow> rows) {
        this.rows = rows;
    }
}
