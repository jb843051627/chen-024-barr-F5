package com.fc.v2.model.custom;

import java.io.Serializable;

/**
 * 落签请求：全单只此一处走口。
 * 没有「已签几人」录入格——那栏只由系统数；报文里的 nodeNo 只是递话，库里不作数，
 * 落哪一口只认单子停的那一口；席位也只认规则书里这一口的席位。
 *
 * @author fuce
 * @date 2026-10-05
 */
public class JointSignForm implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 签认单id */
    private String billId;

    /** 报文递的段号（不作数；给了就得与单停口对得上，对不上这一笔不认） */
    private Integer nodeNo;

    /** 席位代号（规则书回的：HOUSEHOLD/BUILDER/ASSESSOR/DISTRICT/CITY） */
    private String seatKey;

    /** 落笔人（当场签字那位，外部到场方由当场经办据实录） */
    private String signer;

    /** 落笔结论：可 / 不可 */
    private String verdict;

    /** 附言（可空） */
    private String note;

    public String getBillId() {
        return billId;
    }

    public void setBillId(String billId) {
        this.billId = billId;
    }

    public Integer getNodeNo() {
        return nodeNo;
    }

    public void setNodeNo(Integer nodeNo) {
        this.nodeNo = nodeNo;
    }

    public String getSeatKey() {
        return seatKey;
    }

    public void setSeatKey(String seatKey) {
        this.seatKey = seatKey;
    }

    public String getSigner() {
        return signer;
    }

    public void setSigner(String signer) {
        this.signer = signer;
    }

    public String getVerdict() {
        return verdict;
    }

    public void setVerdict(String verdict) {
        this.verdict = verdict;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
