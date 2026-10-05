package com.fc.v2.model.custom;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 联户验收签认单回显：照规则书与落笔留痕当场凑成的一整份话。
 * 「该几人签 / 按什么规矩算齐」抄规则书；「已签几人」照本口本轮算数落笔数出来，
 * 屏上只读回显、没有录入格；「停在第几段」是系统按落笔挪出来的，报文段号不算。
 * 本对象只承回显，不承任何写入——全单只此一处走口（服务 sign 方法）。
 *
 * @author fuce
 * @date 2026-10-05
 */
public class JointSignView implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 一口一档：四栏 + 这一口各席位写下的留底笔 */
    public static final class NodeView {
        /** 段号（规则书回的） */
        private int nodeNo;
        /** 口名 */
        private String nodeName;
        /** 算齐规矩 */
        private String rule;
        /** 该几个人签（规则书回的，不由报文定） */
        private int needCount;
        /** 当下已签几人（照本口本轮算数落笔数出来，屏上只读） */
        private int signedCount;
        /** 这一口齐没齐：数出来的条数与应签人数持平才算齐 */
        private boolean complete;
        /** 整单停在第几段：只有停的那一口亮着，没点齐时下一口笔迹无效 */
        private boolean lit;
        /** 同判口（第三口）分歧挂待定：一可一不可，既不往下挪也不就此收 */
        private boolean splitPending;
        /** 这一口各席位落笔（两行都看得见，旧轮另摆，不并排） */
        private List<LineView> lines = new ArrayList<>();

        public int getNodeNo() {
            return nodeNo;
        }

        public void setNodeNo(int nodeNo) {
            this.nodeNo = nodeNo;
        }

        public String getNodeName() {
            return nodeName;
        }

        public void setNodeName(String nodeName) {
            this.nodeName = nodeName;
        }

        public String getRule() {
            return rule;
        }

        public void setRule(String rule) {
            this.rule = rule;
        }

        public int getNeedCount() {
            return needCount;
        }

        public void setNeedCount(int needCount) {
            this.needCount = needCount;
        }

        public int getSignedCount() {
            return signedCount;
        }

        public void setSignedCount(int signedCount) {
            this.signedCount = signedCount;
        }

        public boolean isComplete() {
            return complete;
        }

        public void setComplete(boolean complete) {
            this.complete = complete;
        }

        public boolean isLit() {
            return lit;
        }

        public void setLit(boolean lit) {
            this.lit = lit;
        }

        public boolean isSplitPending() {
            return splitPending;
        }

        public void setSplitPending(boolean splitPending) {
            this.splitPending = splitPending;
        }

        public List<LineView> getLines() {
            return lines;
        }

        public void setLines(List<LineView> lines) {
            this.lines = lines;
        }
    }

    /** 一笔留底的回显（作废笔不抹，另摆出来，哪天补的各有凭据） */
    public static final class LineView {
        private int roundNo;
        private int nodeNo;
        private String seatKey;
        private String seatName;
        private String signer;
        private String verdict;
        private int lineSeq;
        private boolean valid;
        private String voidReason;
        private String note;
        private Date createTime;

        public int getRoundNo() {
            return roundNo;
        }

        public void setRoundNo(int roundNo) {
            this.roundNo = roundNo;
        }

        public int getNodeNo() {
            return nodeNo;
        }

        public void setNodeNo(int nodeNo) {
            this.nodeNo = nodeNo;
        }

        public String getSeatKey() {
            return seatKey;
        }

        public void setSeatKey(String seatKey) {
            this.seatKey = seatKey;
        }

        public String getSeatName() {
            return seatName;
        }

        public void setSeatName(String seatName) {
            this.seatName = seatName;
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

        public int getLineSeq() {
            return lineSeq;
        }

        public void setLineSeq(int lineSeq) {
            this.lineSeq = lineSeq;
        }

        public boolean isValid() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }

        public String getVoidReason() {
            return voidReason;
        }

        public void setVoidReason(String voidReason) {
            this.voidReason = voidReason;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }

        public Date getCreateTime() {
            return createTime;
        }

        public void setCreateTime(Date createTime) {
            this.createTime = createTime;
        }
    }

    /** 单子头：单号、来路、记录上沿用下来的字段 */
    private Long id;
    private String billNo;
    private Long acceptId;
    private String acceptNo;
    private String groupNo;
    private Long archId;
    private String archNo;
    private String archName;
    private String archKind;
    private String streetCode;
    private String streetName;
    private Integer siteCount;
    private Date acceptTime;
    private String holder;

    /** 整单停在第几段（系统数出）；3 = 三口走完已签讫 */
    private int currentNode;
    /** 本单走到第几轮 */
    private int roundNo;
    /** 在签 / 已签讫 */
    private int status;
    private Date finishedTime;

    /** 三口四栏 */
    private List<NodeView> nodes = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public Long getAcceptId() {
        return acceptId;
    }

    public void setAcceptId(Long acceptId) {
        this.acceptId = acceptId;
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

    public Long getArchId() {
        return archId;
    }

    public void setArchId(Long archId) {
        this.archId = archId;
    }

    public String getArchNo() {
        return archNo;
    }

    public void setArchNo(String archNo) {
        this.archNo = archNo;
    }

    public String getArchName() {
        return archName;
    }

    public void setArchName(String archName) {
        this.archName = archName;
    }

    public String getArchKind() {
        return archKind;
    }

    public void setArchKind(String archKind) {
        this.archKind = archKind;
    }

    public String getStreetCode() {
        return streetCode;
    }

    public void setStreetCode(String streetCode) {
        this.streetCode = streetCode;
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

    public Date getAcceptTime() {
        return acceptTime;
    }

    public void setAcceptTime(Date acceptTime) {
        this.acceptTime = acceptTime;
    }

    public String getHolder() {
        return holder;
    }

    public void setHolder(String holder) {
        this.holder = holder;
    }

    public int getCurrentNode() {
        return currentNode;
    }

    public void setCurrentNode(int currentNode) {
        this.currentNode = currentNode;
    }

    public int getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(int roundNo) {
        this.roundNo = roundNo;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public Date getFinishedTime() {
        return finishedTime;
    }

    public void setFinishedTime(Date finishedTime) {
        this.finishedTime = finishedTime;
    }

    public List<NodeView> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeView> nodes) {
        this.nodes = nodes;
    }
}
