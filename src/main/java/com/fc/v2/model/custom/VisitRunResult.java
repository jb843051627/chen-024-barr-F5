package com.fc.v2.model.custom;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 复检日跑一趟交账的话：窗口取舍、并段、递不动、末尾三个数全在一处算成这一个东西，
 * 屏上那一眼只认这里给的数，不许另起一轮回库逐桩点一遍对冲。
 *
 * @author fuce
 * @date 2026-10-05
 */
public class VisitRunResult {

    /** 这一趟开没开口：窗口外只应声、库里一格不动时为 false */
    private boolean ran;

    /** 给按门人的回话（窗口外是同一句：这一趟没动过库；一条没挑着：本轮无到期） */
    private String message;

    /** 这一趟认的那个触发时刻（挑单与回写同一个读法） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date firedAt;

    /** 三数之一：还欠开口的有几条（本趟挑中却挪到下一趟的，历史不并进来） */
    private int waiting;

    /** 三数之二：这一趟递出去几条（回库 update 真正挪成「已递出」的条数） */
    private int delivered;

    /** 三数之三：挂成递不动几条（回库 update 真正挂成的条数） */
    private int stalled;

    /** 本趟按日子、代号排好后挑中的桩数 */
    private int picked;

    public boolean isRan() {
        return ran;
    }

    public void setRan(boolean ran) {
        this.ran = ran;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Date getFiredAt() {
        return firedAt;
    }

    public void setFiredAt(Date firedAt) {
        this.firedAt = firedAt;
    }

    public int getWaiting() {
        return waiting;
    }

    public void setWaiting(int waiting) {
        this.waiting = waiting;
    }

    public int getDelivered() {
        return delivered;
    }

    public void setDelivered(int delivered) {
        this.delivered = delivered;
    }

    public int getStalled() {
        return stalled;
    }

    public void setStalled(int stalled) {
        this.stalled = stalled;
    }

    public int getPicked() {
        return picked;
    }

    public void setPicked(int picked) {
        this.picked = picked;
    }
}
