package com.fc.v2.model.custom;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 核销册回执：一册办完的交代。
 * 栏上只有三个数——册上原报几行、这一趟落地几行、原路退回几行，
 * 退回的与落地的并起来等于原报的数；三个数只认回库一行一行点出来的，
 * 屏上先亮的那份不作数。没落成的行把纸面行次随回执带出去，好让街道对得上号。
 *
 * @author fuce
 * @date 2026-10-06
 */
public class PayBookReceipt implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 回执上带出去的一行：纸面行次 + 代号 + 缘故 */
    public static class RowItem implements Serializable {
        private static final long serialVersionUID = 1L;
        private Integer rowNo;
        private String itemCode;
        private String reason;

        public RowItem() {
        }

        public RowItem(Integer rowNo, String itemCode, String reason) {
            this.rowNo = rowNo;
            this.itemCode = itemCode;
            this.reason = reason;
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

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    /** 册码 */
    private String bookNo;
    /** 第几轮 */
    private Integer roundNo;
    /** 这趟进门落成了（false=册码空着／整册退回／第二遍照面） */
    private boolean entered;
    /** 第二遍照面：不添一行、不减一行，头一遍落成的去向盖不掉 */
    private boolean duplicate;
    /** 压过单册上限：整册退回，库里不许留半行 */
    private boolean rejectedWhole;
    /** 空册：一行都没落着，回执上只写"空册"两字，不出核讫 */
    private boolean emptyBook;
    /** 核讫号（空册不出） */
    private String checkNo;
    /** 册头那格写法（报册方自称的行数） */
    private Integer declaredRows;
    /** 册头那格与实际递来的对不对得上（照面结果；那格没写亦为不合） */
    private boolean declaredMatch;
    /** 回执·册上原报几行 */
    private int reported;
    /** 回执·这一趟落地几行（收下+未勾） */
    private int landed;
    /** 回执·原路退回几行 */
    private int returned;
    /** 原路退回的行：纸面行次 + 缘故 */
    private List<RowItem> returnedRows = new ArrayList<>();
    /** 未勾空悬的行：纸面行次 + 代号（挂着等对上） */
    private List<RowItem> hangingRows = new ArrayList<>();
    /** 照面记要：对不上的地方逐条点名（第二遍照面、册头那格不合） */
    private List<String> faceNotes = new ArrayList<>();
    /** 回话 */
    private String message;

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

    public boolean isEntered() {
        return entered;
    }

    public void setEntered(boolean entered) {
        this.entered = entered;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public void setDuplicate(boolean duplicate) {
        this.duplicate = duplicate;
    }

    public boolean isRejectedWhole() {
        return rejectedWhole;
    }

    public void setRejectedWhole(boolean rejectedWhole) {
        this.rejectedWhole = rejectedWhole;
    }

    public boolean isEmptyBook() {
        return emptyBook;
    }

    public void setEmptyBook(boolean emptyBook) {
        this.emptyBook = emptyBook;
    }

    public String getCheckNo() {
        return checkNo;
    }

    public void setCheckNo(String checkNo) {
        this.checkNo = checkNo;
    }

    public Integer getDeclaredRows() {
        return declaredRows;
    }

    public void setDeclaredRows(Integer declaredRows) {
        this.declaredRows = declaredRows;
    }

    public boolean isDeclaredMatch() {
        return declaredMatch;
    }

    public void setDeclaredMatch(boolean declaredMatch) {
        this.declaredMatch = declaredMatch;
    }

    public int getReported() {
        return reported;
    }

    public void setReported(int reported) {
        this.reported = reported;
    }

    public int getLanded() {
        return landed;
    }

    public void setLanded(int landed) {
        this.landed = landed;
    }

    public int getReturned() {
        return returned;
    }

    public void setReturned(int returned) {
        this.returned = returned;
    }

    public List<RowItem> getReturnedRows() {
        return returnedRows;
    }

    public void setReturnedRows(List<RowItem> returnedRows) {
        this.returnedRows = returnedRows;
    }

    public List<RowItem> getHangingRows() {
        return hangingRows;
    }

    public void setHangingRows(List<RowItem> hangingRows) {
        this.hangingRows = hangingRows;
    }

    public List<String> getFaceNotes() {
        return faceNotes;
    }

    public void setFaceNotes(List<String> faceNotes) {
        this.faceNotes = faceNotes;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
