package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.exception.JointSignRuleException;
import com.fc.v2.mapper.auto.TBarrAcceptRecordMapper;
import com.fc.v2.mapper.auto.TBarrJointSignBillMapper;
import com.fc.v2.mapper.auto.TBarrJointSignLineMapper;
import com.fc.v2.model.auto.TBarrAcceptRecord;
import com.fc.v2.model.auto.TBarrJointSignBill;
import com.fc.v2.model.auto.TBarrJointSignLine;
import com.fc.v2.model.custom.JointSignAudit;
import com.fc.v2.model.custom.JointSignBoard;
import com.fc.v2.model.custom.JointSignForm;
import com.fc.v2.model.custom.JointSignRuleBook;
import com.fc.v2.model.custom.JointSignRuleBook.Node;
import com.fc.v2.model.custom.JointSignRuleBook.Seat;
import com.fc.v2.model.custom.JointSignView;
import com.fc.v2.service.ITBarrJointSignBillService;
import com.fc.v2.util.StringUtils;

/**
 * 联户验收签认单（会签版）Service业务层处理——全系统只此一处走口。
 *
 * 来路：单只能由当场验收记录关联带出（openFromAccept），同一场验收只带得出一张，
 *      别处另起手建的一张不算；记录上已有的字段单上直接沿用。本类没有新建/改写/追加三类暗门。
 * 段号：报文里写下的段号库里不作数，落哪一口只认单子停的那一口；席位只认规则书里这一口的席位。
 * 计数：已签几人不接手填、不入上账，落完照落笔留痕一口一口数出来回显；
 *      在签张数、停口分布、第三口结论、移交名册同出 board() 一趟计算，不搞两处取数。
 * 三口各管各的（JointSignRuleBook）：第一口一名即过；第二口先笔定调、后笔照样累；
 *      第三口两名同判，一可一不可挂待定，同席再落笔以新换旧（旧笔 supersede 留底）。
 * 进退：往前只迈紧邻下一口，上一口没点齐下一口笔迹无效（越口一笔不认）；
 *      打回只抹本口本轮已落的签（reject 置作废），更早各口旧签一笔不动，单退一格、轮次加一。
 * 签讫：末口同判点齐即签讫，此后一笔进不来、一字改不了、整张抽不走。
 *
 * @author fuce
 * @date 2026-10-05
 */
@Service
public class TBarrJointSignBillServiceImpl implements ITBarrJointSignBillService {

    @Autowired
    private TBarrJointSignBillMapper billMapper;

    @Autowired
    private TBarrJointSignLineMapper lineMapper;

    @Autowired
    private TBarrAcceptRecordMapper acceptRecordMapper;

    // ============================ 带单（唯一来路） ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TBarrJointSignBill openFromAccept(Long acceptId, String operator) {
        if (acceptId == null) {
            throw new JointSignRuleException("不知道由哪场验收带单：验收记录空着");
        }
        TBarrAcceptRecord record = acceptRecordMapper.selectById(acceptId);
        if (record == null) {
            throw new JointSignRuleException("这场验收在受理簿上翻不着，签认单带不出来；别处另起手建的一张不算");
        }
        TBarrJointSignBill existing = billMapper.selectOne(new QueryWrapper<TBarrJointSignBill>()
                .eq("accept_id", acceptId).last("limit 1"));
        if (existing != null) {
            // 同一场验收只带得出那一张：再带一回原样回那张，不另起第二张
            return normalize(existing);
        }
        TBarrJointSignBill bill = new TBarrJointSignBill();
        // 单号由受理簿那笔的记录号带出（YS-… → QRB-…），一场一号，不自起号段
        bill.setBillNo(buildBillNo(record.getAcceptNo()));
        bill.setAcceptId(record.getId());
        // 记录上已有的字段，单上直接沿用
        bill.setAcceptNo(record.getAcceptNo());
        bill.setGroupNo(record.getGroupNo());
        bill.setArchId(record.getArchId());
        bill.setArchNo(record.getArchNo());
        bill.setArchName(record.getArchName());
        bill.setArchKind(record.getArchKind());
        bill.setStreetCode(record.getStreetCode());
        bill.setStreetName(record.getStreetName());
        bill.setSiteCount(record.getSiteCount());
        bill.setAcceptTime(record.getAcceptTime());
        bill.setHolder(record.getHolder());
        bill.setNodeNo(JointSignRuleBook.firstNode());
        bill.setRoundNo(1);
        bill.setStatus(TBarrJointSignBill.STATUS_RUNNING);
        try {
            billMapper.insert(bill);
        } catch (DuplicateKeyException e) {
            // 并发两回同时带单：uk_joint_accept 兜底，后到这趟回头一张，不另起
            TBarrJointSignBill again = billMapper.selectOne(new QueryWrapper<TBarrJointSignBill>()
                    .eq("accept_id", acceptId).last("limit 1"));
            if (again == null) {
                throw e;
            }
            return normalize(again);
        }
        return normalize(bill);
    }

    @Override
    public TBarrJointSignBill selectBillById(Long billId) {
        if (billId == null) {
            return null;
        }
        TBarrJointSignBill bill = billMapper.selectById(billId);
        return bill == null ? null : normalize(bill);
    }

    // ============================ 落签（全单唯一走口） ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JointSignView sign(JointSignForm form, String operator) {
        if (form == null) {
            throw new JointSignRuleException("这一笔空着，录不进去");
        }
        Long billId = parseId(form.getBillId());
        // 同单并发两笔：先锁单头行，在锁内数次第、落留痕、挪停口，各记各的先后
        TBarrJointSignBill bill = billMapper.selectByIdForUpdate(billId);
        if (bill == null) {
            throw new JointSignRuleException("这张联户验收签认单翻不着");
        }
        normalize(bill);
        int nodeNo = bill.getNodeNo();
        if (bill.getStatus() != null && bill.getStatus() == TBarrJointSignBill.STATUS_FINISHED) {
            throw new JointSignRuleException("这张单已签讫：此后一笔进不来、一字改不了、整张也抽不走");
        }
        // 报文段号只是递话：给了就得与单停口对得上，对不上这一笔不认（越过第二口直落第三口不算）
        if (form.getNodeNo() != null && form.getNodeNo() != nodeNo) {
            throw new JointSignRuleException("单此刻停在第" + (nodeNo + 1) + "口，递来的却是第"
                    + (form.getNodeNo() + 1) + "口的笔——上一口没点齐，这一口的笔迹无效");
        }
        Node node = JointSignRuleBook.node(nodeNo);
        Seat seat = JointSignRuleBook.seatOf(nodeNo, form.getSeatKey());
        if (seat == null) {
            throw new JointSignRuleException("席位「" + form.getSeatKey()
                    + "」不是第" + (nodeNo + 1) + "口裁量书里的席位，这一笔不认");
        }
        String signer = trim(form.getSigner());
        if (StringUtils.isEmpty(signer)) {
            throw new JointSignRuleException("落笔人空着，这笔不知道是谁签的");
        }
        String verdict = normalizeVerdict(form.getVerdict());
        int round = nz(bill.getRoundNo());

        List<TBarrJointSignLine> roundLines = validRoundLines(billId, nodeNo, round);
        TBarrJointSignLine sameSeat = findSeatLine(roundLines, seat.getKey());

        if (sameSeat != null) {
            // 同席改口只在第三口（两名同判、挂待定再落笔）接住：旧笔 supersede 留底、新笔另落；
            // 头两口一席只落一笔，不许多写顶掉
            if (!JointSignRuleBook.RULE_UNANIMOUS.equals(node.getRule())) {
                throw new JointSignRuleException("第" + (nodeNo + 1) + "口「" + seat.getName()
                        + "」这一席本轮已经落过笔，不许再写第二笔");
            }
            sameSeat.setValidFlag(TBarrJointSignLine.VOID);
            sameSeat.setVoidReason(TBarrJointSignLine.VOID_SUPERSEDE);
            lineMapper.updateById(sameSeat);
        }

        insertLine(bill, nodeNo, round, seat, signer, verdict, trim(form.getNote()), operator);

        // 落完照留痕当场数：已签数、停口、轮次全由这一回数说了算
        List<TBarrJointSignLine> nowLines = validRoundLines(billId, nodeNo, round);
        applyRuleAfterSign(bill, node, nowLines);
        billMapper.updateById(bill);
        return view(bill.getId());
    }

    /**
     * 三口各管各的算齐与进退，全在此处一处裁量：
     * 第一口 一名即过：可→点齐挪口；不可→本口判不予通过，本轮抹掉重走。
     * 第二口 先笔定调：两名到齐才算数，先落笔那方怎么说就怎么定（可→挪口；不可→打回退一格）。
     * 第三口 两名同判：两可→签讫；一可一不可→挂待定，不挪不收；两不可→打回退一格。
     */
    private void applyRuleAfterSign(TBarrJointSignBill bill, Node node, List<TBarrJointSignLine> validLines) {
        int nodeNo = node.getNo();
        String rule = node.getRule();
        if (JointSignRuleBook.RULE_FIRST_PASS.equals(rule)) {
            TBarrJointSignLine only = validLines.get(0);
            if (TBarrJointSignLine.VERDICT_YES.equals(only.getVerdict())) {
                advance(bill);
            } else {
                rejectRound(bill, "住户本人/看护人判不予通过");
            }
            return;
        }
        if (JointSignRuleBook.RULE_FIRST_DECIDE.equals(rule)) {
            if (validLines.size() < node.getNeedCount()) {
                return; // 这一口等两名：施工方、第三方还没到齐，留在本口继续签
            }
            // 两名到齐才定：先落笔那方怎么说就怎么定；后一名那笔不是白写——
            // 已累入已签数，两方各写了什么都留底，屏上两行都看得见
            TBarrJointSignLine first = Collections.min(validLines, Comparator.comparingInt(l -> nz(l.getLineSeq())));
            if (TBarrJointSignLine.VERDICT_YES.equals(first.getVerdict())) {
                advance(bill);
            } else {
                rejectRound(bill, "先落笔的「" + first.getSeatName() + "」判不予通过，按先笔定调打回");
            }
            return;
        }
        if (JointSignRuleBook.RULE_UNANIMOUS.equals(rule)) {
            if (validLines.size() < node.getNeedCount()) {
                return; // 区、市残联还没到齐
            }
            boolean allYes = true;
            boolean anyYes = false;
            for (TBarrJointSignLine l : validLines) {
                boolean yes = TBarrJointSignLine.VERDICT_YES.equals(l.getVerdict());
                allYes &= yes;
                anyYes |= yes;
            }
            if (allYes) {
                finish(bill);
            } else if (anyYes) {
                // 一可一不可：挂在待定上，既不往下挪，也不就此收（同席再落笔才能解套）
                return;
            } else {
                rejectRound(bill, "区残联、市残联两名同判均不可，打回");
            }
        }
    }

    /** 往前只迈紧邻的下一口；末口点齐走 finish 不进这里 */
    private void advance(TBarrJointSignBill bill) {
        int next = nz(bill.getNodeNo()) + 1;
        if (next > JointSignRuleBook.lastNode()) {
            throw new JointSignRuleException("前面没有下一口了，末口点齐请走签讫");
        }
        bill.setNodeNo(next);
        bill.setStatus(TBarrJointSignBill.STATUS_RUNNING);
    }

    /** 末口两名同判都写「可」：整单签讫 */
    private void finish(TBarrJointSignBill bill) {
        bill.setStatus(TBarrJointSignBill.STATUS_FINISHED);
        bill.setFinishedTime(new Date());
    }

    /**
     * 打回：抹去的只限本口这一轮已落的签（置作废留底，不是物理删行），
     * 更早各口的旧签一笔不动；单往回收一格（头一口前面没口，停在头一口），轮次加一。
     */
    private void rejectRound(TBarrJointSignBill bill, String reason) {
        int nodeNo = nz(bill.getNodeNo());
        int round = nz(bill.getRoundNo());
        List<TBarrJointSignLine> current = validRoundLines(bill.getId(), nodeNo, round);
        for (TBarrJointSignLine l : current) {
            l.setValidFlag(TBarrJointSignLine.VOID);
            l.setVoidReason(TBarrJointSignLine.VOID_REJECT);
            // 作废缘故把话留在附言后头，日后翻得着为什么打回
            l.setNote(appendReason(l.getNote(), reason));
            lineMapper.updateById(l);
        }
        int back = nodeNo <= 0 ? 0 : nodeNo - 1;
        bill.setNodeNo(back);
        bill.setRoundNo(round + 1);
        bill.setStatus(TBarrJointSignBill.STATUS_RUNNING);
    }

    // ============================ 回显（数出来的，不录入） ============================

    @Override
    public JointSignView view(Long billId) {
        TBarrJointSignBill bill = loadBill(billId);
        List<TBarrJointSignLine> all = lineMapper.selectList(new QueryWrapper<TBarrJointSignLine>()
                .eq("bill_id", billId)
                .orderByAsc("node_no").orderByAsc("round_no").orderByAsc("line_seq").orderByAsc("id"));
        boolean finished = bill.getStatus() != null && bill.getStatus() == TBarrJointSignBill.STATUS_FINISHED;
        int currentNode = finished ? TBarrJointSignBill.NODE_DONE : nz(bill.getNodeNo());

        JointSignView v = new JointSignView();
        copyBillHead(bill, v);
        v.setCurrentNode(currentNode);
        v.setRoundNo(nz(bill.getRoundNo()));
        v.setStatus(nz(bill.getStatus()));
        v.setFinishedTime(bill.getFinishedTime());

        for (Node node : JointSignRuleBook.nodes()) {
            JointSignView.NodeView nv = new JointSignView.NodeView();
            nv.setNodeNo(node.getNo());
            nv.setNodeName(node.getName());
            nv.setRule(node.getRule());
            nv.setNeedCount(node.getNeedCount());
            nv.setLit(!finished && node.getNo() == nz(bill.getNodeNo()));

            List<TBarrJointSignLine> nodeLines = linesOfNode(all, node.getNo());
            // 该口各轮笔都摆出来：作废笔另摆，本轮笔迹与上轮笔迹分开放
            for (TBarrJointSignLine l : nodeLines) {
                nv.getLines().add(toLineView(l));
            }
            // 已签几人照落笔数出来（屏上只读、没有录入格）：
            // 亮着的口只数本单当前轮（打回重走、还没补笔时就是 0，旧轮那笔另摆不算本轮）；
            // 没亮的口数它自己最后落过笔的那一轮
            int countRound;
            if (nv.isLit()) {
                countRound = nz(bill.getRoundNo());
            } else {
                int lastWithLines = latestRound(nodeLines, 0);
                countRound = lastWithLines > 0 ? lastWithLines : nz(bill.getRoundNo());
            }
            List<TBarrJointSignLine> counted = validOfRound(nodeLines, countRound);
            int signed = counted.size();
            nv.setSignedCount(signed);
            boolean complete = evaluateComplete(node, counted);
            nv.setComplete(complete);
            // 只有当前停着的第三口、两笔到齐却一可一不可才叫挂待定
            nv.setSplitPending(nv.isLit()
                    && JointSignRuleBook.RULE_UNANIMOUS.equals(node.getRule())
                    && signed == node.getNeedCount() && !complete);
            v.getNodes().add(nv);
        }
        return v;
    }

    @Override
    public List<TBarrJointSignBill> listBills() {
        List<TBarrJointSignBill> list = billMapper.selectList(new QueryWrapper<TBarrJointSignBill>()
                .orderByDesc("create_time").orderByDesc("id"));
        for (TBarrJointSignBill b : list) {
            normalize(b);
        }
        return list;
    }

    // ============================ 事后对受理簿 ============================

    @Override
    public JointSignAudit audit(Long billId) {
        TBarrJointSignBill bill = loadBill(billId);
        JointSignAudit audit = new JointSignAudit();
        audit.setBillId(bill.getId());
        audit.setBillNo(bill.getBillNo());
        audit.setAcceptNo(bill.getAcceptNo());
        audit.setStatus(nz(bill.getStatus()));
        audit.setFinishedTime(bill.getFinishedTime());

        TBarrAcceptRecord ledger = null;
        if (bill.getAcceptId() != null) {
            ledger = acceptRecordMapper.selectById(bill.getAcceptId());
        }
        boolean ledgerFound = ledger != null && bill.getAcceptNo() != null
                && bill.getAcceptNo().equals(ledger.getAcceptNo());
        audit.setLedgerFound(ledgerFound);

        List<TBarrJointSignLine> all = lineMapper.selectList(new QueryWrapper<TBarrJointSignLine>()
                .eq("bill_id", billId)
                .orderByAsc("node_no").orderByAsc("round_no").orderByAsc("line_seq").orderByAsc("id"));

        boolean finished = bill.getStatus() != null && bill.getStatus() == TBarrJointSignBill.STATUS_FINISHED;
        List<String> problems = new ArrayList<>();
        // 从第三口一口一口数回第一口
        for (int nodeNo = JointSignRuleBook.lastNode(); nodeNo >= JointSignRuleBook.firstNode(); nodeNo--) {
            Node node = JointSignRuleBook.node(nodeNo);
            List<TBarrJointSignLine> nodeLines = linesOfNode(all, nodeNo);
            JointSignAudit.NodeCount nc = new JointSignAudit.NodeCount();
            nc.setNodeNo(nodeNo);
            nc.setNodeName(node.getName());
            nc.setNeedCount(node.getNeedCount());

            TreeMap<Integer, List<TBarrJointSignLine>> byRound = groupByRound(nodeLines);
            int finalRound = byRound.isEmpty() ? nz(bill.getRoundNo()) : byRound.lastKey();
            for (Map.Entry<Integer, List<TBarrJointSignLine>> e : byRound.entrySet()) {
                nc.getValidByRound().add((int) e.getValue().stream()
                        .filter(l -> nz(l.getValidFlag()) == TBarrJointSignLine.VALID).count());
            }
            List<TBarrJointSignLine> finalValid = validOfRound(nodeLines, finalRound);
            nc.setFinalRound(finalRound);
            nc.setFinalValid(finalValid.size());
            boolean complete = evaluateComplete(node, finalValid);
            nc.setComplete(complete);
            audit.getNodes().add(nc);

            if (finished) {
                // 签讫单逐口对簿：定案轮笔数须与应签人数一笔一笔合得拢
                if (finalValid.size() != node.getNeedCount()) {
                    problems.add("第" + (nodeNo + 1) + "口定案轮算数笔数 " + finalValid.size()
                            + " 与应签 " + node.getNeedCount() + " 合不拢");
                }
                if (!complete) {
                    problems.add("第" + (nodeNo + 1) + "口定案轮按本口规矩没点齐，单却已签讫");
                }
            }
        }
        // 每一笔都得对得上当日受理簿那一场（accept_no 随笔冗余）
        for (TBarrJointSignLine l : all) {
            if (bill.getAcceptNo() == null || !bill.getAcceptNo().equals(l.getAcceptNo())) {
                problems.add("留痕笔（第" + (nz(l.getNodeNo()) + 1) + "口）的受理簿号对不上这场验收");
                break;
            }
        }
        if (!ledgerFound) {
            problems.add("受理簿上翻不着「" + bill.getAcceptNo() + "」这场验收");
        }
        // 第三口结论与移交名册同一回计算：这里直接照第三口定案轮数，不另设手填结论；
        // 走到签讫的单子第三口必是两可，故签讫单名册上落「可」
        String thirdConclusion = finalConclusionOf(linesOfNode(all, JointSignRuleBook.lastNode()));
        audit.setFinalConclusion(finished ? "可" : thirdConclusion);

        audit.setProblems(problems);
        audit.setMatched(ledgerFound && problems.isEmpty());
        return audit;
    }

    // ============================ 板面（在签张数 / 移交名册同一回算） ============================

    @Override
    public JointSignBoard board() {
        List<TBarrJointSignBill> bills = billMapper.selectList(new QueryWrapper<TBarrJointSignBill>()
                .orderByAsc("id"));
        List<TBarrJointSignLine> allLines = lineMapper.selectList(new QueryWrapper<TBarrJointSignLine>()
                .orderByAsc("bill_id").orderByAsc("node_no").orderByAsc("round_no").orderByAsc("line_seq"));
        Map<Long, List<TBarrJointSignLine>> linesByBill = new LinkedHashMap<>();
        for (TBarrJointSignLine l : allLines) {
            linesByBill.computeIfAbsent(l.getBillId(), k -> new ArrayList<>()).add(l);
        }

        JointSignBoard board = new JointSignBoard();
        int[] atNode = new int[JointSignRuleBook.nodes().size()];
        int running = 0;
        int finishedCount = 0;
        // 一趟扫过全部在签单：总张数与三口分布同出这批数，不许一头 count 一头手填
        for (TBarrJointSignBill b : bills) {
            normalize(b);
            int status = nz(b.getStatus());
            if (status == TBarrJointSignBill.STATUS_FINISHED) {
                finishedCount++;
                JointSignBoard.RosterRow row = new JointSignBoard.RosterRow();
                row.setBillNo(b.getBillNo());
                row.setAcceptNo(b.getAcceptNo());
                row.setGroupNo(b.getGroupNo());
                row.setStreetName(b.getStreetName());
                row.setSiteCount(b.getSiteCount());
                // 第三口结论与名册同一回算：直接照第三口算数笔数出，没有第二处手记栏
                row.setFinalConclusion(finalConclusionOf(linesOfNode(linesByBill.get(b.getId()),
                        JointSignRuleBook.lastNode())));
                row.setFinishedTime(b.getFinishedTime());
                board.getRoster().add(row);
            } else {
                running++;
                int node = nz(b.getNodeNo());
                if (node >= 0 && node < atNode.length) {
                    atNode[node]++;
                }
            }
        }
        board.setRunningTotal(running);
        board.setAtNode(atNode);
        board.setFinishedTotal(finishedCount);
        // 同一回算的自检：三口分布之和必须正好等于在签总张数
        int sum = 0;
        for (int n : atNode) {
            sum += n;
        }
        if (sum != running) {
            throw new JointSignRuleException("在签张数与逐口点数对不上（总" + running + "、三口合计" + sum + "）");
        }
        return board;
    }

    // ============================ 内部 ============================

    private TBarrJointSignBill loadBill(Long billId) {
        if (billId == null) {
            throw new JointSignRuleException("不知道动的是哪一张签认单");
        }
        TBarrJointSignBill bill = billMapper.selectById(billId);
        if (bill == null) {
            throw new JointSignRuleException("这张联户验收签认单翻不着");
        }
        return normalize(bill);
    }

    private TBarrJointSignBill normalize(TBarrJointSignBill bill) {
        if (bill.getNodeNo() == null) {
            bill.setNodeNo(JointSignRuleBook.firstNode());
        }
        if (bill.getRoundNo() == null || bill.getRoundNo() <= 0) {
            bill.setRoundNo(1);
        }
        if (bill.getStatus() == null) {
            bill.setStatus(TBarrJointSignBill.STATUS_RUNNING);
        }
        return bill;
    }

    private void insertLine(TBarrJointSignBill bill, int nodeNo, int round, Seat seat,
                            String signer, String verdict, String note, String operator) {
        // 本口本轮落笔次第：各记各的先后，并发票据不许并成一句。撞 (bill,node,round,seq)
        // 唯一键时重数一遍再落，绝不把两笔挤到同一个次第上。
        for (int attempt = 0; attempt < 2; attempt++) {
            List<TBarrJointSignLine> allRound = lineMapper.selectList(new QueryWrapper<TBarrJointSignLine>()
                    .eq("bill_id", bill.getId()).eq("node_no", nodeNo).eq("round_no", round));
            int seq = 1;
            for (TBarrJointSignLine l : allRound) {
                if (nz(l.getLineSeq()) >= seq) {
                    seq = nz(l.getLineSeq()) + 1;
                }
            }
            TBarrJointSignLine line = new TBarrJointSignLine();
            line.setBillId(bill.getId());
            line.setAcceptNo(bill.getAcceptNo());
            line.setNodeNo(nodeNo);
            line.setRoundNo(round);
            line.setSeatKey(seat.getKey());
            line.setSeatName(seat.getName());
            line.setSigner(signer);
            line.setVerdict(verdict);
            line.setLineSeq(seq);
            line.setValidFlag(TBarrJointSignLine.VALID);
            line.setOperator(operator);
            line.setNote(note);
            line.setCreateTime(new Date());
            try {
                lineMapper.insert(line);
                return;
            } catch (DuplicateKeyException e) {
                if (attempt == 1) {
                    throw new JointSignRuleException("这一笔与同时递来的另一笔挤在同一落笔次第上，请再递一回");
                }
            }
        }
    }

    private List<TBarrJointSignLine> validRoundLines(Long billId, int nodeNo, int round) {
        List<TBarrJointSignLine> list = lineMapper.selectList(new QueryWrapper<TBarrJointSignLine>()
                .eq("bill_id", billId).eq("node_no", nodeNo).eq("round_no", round)
                .eq("valid_flag", TBarrJointSignLine.VALID)
                .orderByAsc("line_seq").orderByAsc("id"));
        return list == null ? new ArrayList<>() : list;
    }

    private TBarrJointSignLine findSeatLine(List<TBarrJointSignLine> lines, String seatKey) {
        for (TBarrJointSignLine l : lines) {
            if (seatKey.equals(l.getSeatKey())) {
                return l;
            }
        }
        return null;
    }

    private List<TBarrJointSignLine> linesOfNode(List<TBarrJointSignLine> all, int nodeNo) {
        List<TBarrJointSignLine> out = new ArrayList<>();
        if (all != null) {
            for (TBarrJointSignLine l : all) {
                if (nz(l.getNodeNo()) == nodeNo) {
                    out.add(l);
                }
            }
        }
        return out;
    }

    private TreeMap<Integer, List<TBarrJointSignLine>> groupByRound(List<TBarrJointSignLine> nodeLines) {
        TreeMap<Integer, List<TBarrJointSignLine>> map = new TreeMap<>();
        for (TBarrJointSignLine l : nodeLines) {
            map.computeIfAbsent(nz(l.getRoundNo()), k -> new ArrayList<>()).add(l);
        }
        return map;
    }

    private int latestRound(List<TBarrJointSignLine> nodeLines, int currentRound) {
        int latest = 0;
        for (TBarrJointSignLine l : nodeLines) {
            if (nz(l.getRoundNo()) > latest) {
                latest = nz(l.getRoundNo());
            }
        }
        return latest == 0 ? currentRound : latest;
    }

    private List<TBarrJointSignLine> validOfRound(List<TBarrJointSignLine> nodeLines, int round) {
        List<TBarrJointSignLine> out = new ArrayList<>();
        for (TBarrJointSignLine l : nodeLines) {
            if (nz(l.getRoundNo()) == round && nz(l.getValidFlag()) == TBarrJointSignLine.VALID) {
                out.add(l);
            }
        }
        out.sort(Comparator.comparingInt(l -> nz(l.getLineSeq())));
        return out;
    }

    /**
     * 这一轮按本口规矩点没点齐——三口各管各的，不互相套：
     * 第一口 一名即过：有一笔「可」即齐；
     * 第二口 先笔定调：两名到齐且先笔为「可」才算齐（先笔不可已当场打回）；
     * 第三口 两名同判：两名到齐且都「可」才算齐（一可一不可是挂待定，不算齐）。
     */
    private boolean evaluateComplete(Node node, List<TBarrJointSignLine> validLines) {
        String rule = node.getRule();
        if (JointSignRuleBook.RULE_FIRST_PASS.equals(rule)) {
            return validLines.size() >= 1 && TBarrJointSignLine.VERDICT_YES.equals(validLines.get(0).getVerdict());
        }
        if (JointSignRuleBook.RULE_FIRST_DECIDE.equals(rule)) {
            if (validLines.size() < node.getNeedCount()) {
                return false;
            }
            TBarrJointSignLine first = Collections.min(validLines, Comparator.comparingInt(l -> nz(l.getLineSeq())));
            return TBarrJointSignLine.VERDICT_YES.equals(first.getVerdict());
        }
        if (JointSignRuleBook.RULE_UNANIMOUS.equals(rule)) {
            if (validLines.size() < node.getNeedCount()) {
                return false;
            }
            for (TBarrJointSignLine l : validLines) {
                if (!TBarrJointSignLine.VERDICT_YES.equals(l.getVerdict())) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /** 第三口定案结论：两可=可；一可一不可=待定；两不可=不可；没落笔=空 */
    private String finalConclusionOf(List<TBarrJointSignLine> nodeLines) {
        if (nodeLines == null || nodeLines.isEmpty()) {
            return "";
        }
        int lastRound = latestRound(nodeLines, 0);
        List<TBarrJointSignLine> valid = validOfRound(nodeLines, lastRound);
        if (valid.isEmpty()) {
            return "";
        }
        boolean anyYes = false;
        boolean allYes = true;
        for (TBarrJointSignLine l : valid) {
            boolean yes = TBarrJointSignLine.VERDICT_YES.equals(l.getVerdict());
            anyYes |= yes;
            allYes &= yes;
        }
        if (allYes) {
            return "可";
        }
        return anyYes ? "待定" : "不可";
    }

    private JointSignView.LineView toLineView(TBarrJointSignLine l) {
        JointSignView.LineView lv = new JointSignView.LineView();
        lv.setRoundNo(nz(l.getRoundNo()));
        lv.setNodeNo(nz(l.getNodeNo()));
        lv.setSeatKey(l.getSeatKey());
        lv.setSeatName(l.getSeatName());
        lv.setSigner(l.getSigner());
        lv.setVerdict(l.getVerdict());
        lv.setLineSeq(nz(l.getLineSeq()));
        lv.setValid(nz(l.getValidFlag()) == TBarrJointSignLine.VALID);
        lv.setVoidReason(l.getVoidReason());
        lv.setNote(l.getNote());
        lv.setCreateTime(l.getCreateTime());
        return lv;
    }

    private void copyBillHead(TBarrJointSignBill bill, JointSignView v) {
        v.setId(bill.getId());
        v.setBillNo(bill.getBillNo());
        v.setAcceptId(bill.getAcceptId());
        v.setAcceptNo(bill.getAcceptNo());
        v.setGroupNo(bill.getGroupNo());
        v.setArchId(bill.getArchId());
        v.setArchNo(bill.getArchNo());
        v.setArchName(bill.getArchName());
        v.setArchKind(bill.getArchKind());
        v.setStreetCode(bill.getStreetCode());
        v.setStreetName(bill.getStreetName());
        v.setSiteCount(bill.getSiteCount());
        v.setAcceptTime(bill.getAcceptTime());
        v.setHolder(bill.getHolder());
    }

    private String buildBillNo(String acceptNo) {
        // YS-20261005-0001 → QRB-20261005-0001：单号由受理簿那笔带出，不自起号段
        if (StringUtils.isNotEmpty(acceptNo) && acceptNo.startsWith("YS-")) {
            return "QRB-" + acceptNo.substring(3);
        }
        return "QRB-" + acceptNo;
    }

    private String normalizeVerdict(String raw) {
        String v = trim(raw);
        if (TBarrJointSignLine.VERDICT_YES.equals(v) || TBarrJointSignLine.VERDICT_NO.equals(v)) {
            return v;
        }
        throw new JointSignRuleException("落笔结论只许写「可」或「不可」，写的是：" + raw);
    }

    private Long parseId(String raw) {
        if (StringUtils.isEmpty(raw)) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new JointSignRuleException("签认单代号不对，找不到这张单");
        }
    }

    private String appendReason(String note, String reason) {
        if (StringUtils.isEmpty(note)) {
            return "【打回】" + reason;
        }
        return note + " 【打回】" + reason;
    }

    private int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }
}
