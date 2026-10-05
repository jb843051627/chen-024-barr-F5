package com.fc.v2.model.custom;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 事后翻查：从第三口一口一口数回第一口，笔数与当日受理簿一笔一笔合得拢。
 * 笔数只照落笔留痕数（与屏上已签数同一回算），受理簿上那一笔即 t_barr_accept_record；
 * 第三口结论与移交名册出自同一回计算，这里存的是同一趟数出的结论，没有第二处手填。
 *
 * @author fuce
 * @date 2026-10-05
 */
public class JointSignAudit implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 一口的对簿结果 */
    public static final class NodeCount {
        private int nodeNo;
        private String nodeName;
        /** 这一口应签人数（规则书） */
        private int needCount;
        /** 这一口各轮算数笔数（受理簿上翻得着的，逐轮摆开） */
        private List<Integer> validByRound = new ArrayList<>();
        /** 定案那一轮（签讫单=最后一轮；在签单=当前轮） */
        private int finalRound;
        /** 定案轮算数笔数 */
        private int finalValid;
        /** 定案轮齐没齐 */
        private boolean complete;

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

        public int getNeedCount() {
            return needCount;
        }

        public void setNeedCount(int needCount) {
            this.needCount = needCount;
        }

        public List<Integer> getValidByRound() {
            return validByRound;
        }

        public void setValidByRound(List<Integer> validByRound) {
            this.validByRound = validByRound;
        }

        public int getFinalRound() {
            return finalRound;
        }

        public void setFinalRound(int finalRound) {
            this.finalRound = finalRound;
        }

        public int getFinalValid() {
            return finalValid;
        }

        public void setFinalValid(int finalValid) {
            this.finalValid = finalValid;
        }

        public boolean isComplete() {
            return complete;
        }

        public void setComplete(boolean complete) {
            this.complete = complete;
        }
    }

    private Long billId;
    private String billNo;
    private String acceptNo;
    /** 受理簿上翻得着这场验收吗 */
    private boolean ledgerFound;
    /** 三口一口一口对下来合不合得拢 */
    private boolean matched;
    /** 合不拢处一句句讲清 */
    private List<String> problems = new ArrayList<>();
    /** 从第三口数回第一口的明细 */
    private List<NodeCount> nodes = new ArrayList<>();
    /** 第三口定案结论（与移交名册同一回计算）：可/不可/挂待定 */
    private String finalConclusion;
    private int status;
    private Date finishedTime;

    public Long getBillId() {
        return billId;
    }

    public void setBillId(Long billId) {
        this.billId = billId;
    }

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

    public boolean isLedgerFound() {
        return ledgerFound;
    }

    public void setLedgerFound(boolean ledgerFound) {
        this.ledgerFound = ledgerFound;
    }

    public boolean isMatched() {
        return matched;
    }

    public void setMatched(boolean matched) {
        this.matched = matched;
    }

    public List<String> getProblems() {
        return problems;
    }

    public void setProblems(List<String> problems) {
        this.problems = problems;
    }

    public List<NodeCount> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeCount> nodes) {
        this.nodes = nodes;
    }

    public String getFinalConclusion() {
        return finalConclusion;
    }

    public void setFinalConclusion(String finalConclusion) {
        this.finalConclusion = finalConclusion;
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
}
