package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.exception.PayBookRuleException;
import com.fc.v2.mapper.auto.TBarrPayBatchMapper;
import com.fc.v2.mapper.auto.TBarrPayRowMapper;
import com.fc.v2.mapper.auto.TSysStreetMapper;
import com.fc.v2.mapper.custom.BarrPayBookDao;
import com.fc.v2.model.auto.TBarrCapLine;
import com.fc.v2.model.auto.TBarrPayBatch;
import com.fc.v2.model.auto.TBarrPayRow;
import com.fc.v2.model.auto.TSysStreet;
import com.fc.v2.model.custom.PayBookAudit;
import com.fc.v2.model.custom.PayBookForm;
import com.fc.v2.model.custom.PayBookReceipt;
import com.fc.v2.service.ITBarrPayRowService;
import com.fc.v2.util.StringUtils;

/**
 * 补助核销册 Service业务层处理：册子进门只此一道手续，全在 {@link #intakeBook} 一处讲清。
 *
 * 走法钉死：册头先点行数（压过单册上限整册退回，库里不留半行）→ 同一册码已落过只照面
 * （不添一行、不减一行，头一遍落成的去向盖不掉；要动先撤册再重递）→ 新册逐行判收判退
 * （同一户占两行各走各的，谁的结果都不盖到谁身上）→ 末了回库一行一行数出回执那三个数
 * （屏上先亮的那份不作数）→ 册头落核讫／空册。中途不过第二处的手。
 *
 * 口径定死：册头自报行数与实际递来不合，以实际落库数为准，自报数原样留底、对不上点名；
 * 行上两个数缺任一个不替它凑；认户只认进度卡代号全等；行落地情形只许未勾／收下／送回三格。
 *
 * @author fuce
 * @date 2026-10-06
 */
@Service
public class TBarrPayRowServiceImpl implements ITBarrPayRowService {

    /** 单册行数上限：处里划的线，头一步先点行数，压过线整册退回、库里不许留半行 */
    private static final int MAX_ROWS = 500;

    /** 行落地情形：0未勾 1已收下 2已送回——只此三格，落不进第四种 */
    private static final int ROW_PENDING = 0;
    private static final int ROW_LANDED = 1;
    private static final int ROW_RETURNED = 2;

    /** 册情形：1已核讫 2空册 9已撤册（核讫只给有行落成的册；空册不出核讫） */
    private static final int BOOK_VERIFIED = 1;
    private static final int BOOK_EMPTY = 2;

    /** 进度卡代号那一把尺：与列宽对齐，长过它行站不住 */
    private static final int ITEM_CODE_MAX_LEN = 64;

    /** 对不上点名一次最多报这么多行，余下的并成一句总数 */
    private static final int PROBLEM_ROW_LIMIT = 20;

    @Resource
    private TBarrPayRowMapper barrPayRowMapper;

    @Resource
    private TBarrPayBatchMapper barrPayBatchMapper;

    @Resource
    private BarrPayBookDao barrPayBookDao;

    @Autowired(required = false)
    private TSysStreetMapper sysStreetMapper;

    @Override
    public TBarrPayRow selectTBarrPayRowById(Long id) {
        if (id == null) {
            return null;
        }
        return this.barrPayRowMapper.selectById(id);
    }

    /**
     * 进门一道手续。不开整册长事务之外的第二处手：正常路径一个事务落到底；
     * 并发撞活行／活册唯一键时，本趟已写的行当场抹掉（同事务删除），整趟按照面收场——
     * 并发的两回只落一回，去年冬那种两趟各记一回的口子不再留。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayBookReceipt intakeBook(PayBookForm form, String operator) {
        String batchNo = trim(form == null ? null : form.getBatchNo());
        if (StringUtils.isEmpty(batchNo)) {
            throw new PayBookRuleException("册码空着，册子进不了门");
        }
        List<PayBookForm.Row> inRows = form.getRows() == null ? new ArrayList<>() : form.getRows();

        // 头一步先点行数：压过单册上限整册退回，库里不许留半行
        if (inRows.size() > MAX_ROWS) {
            PayBookReceipt over = new PayBookReceipt();
            over.setOutcome(PayBookReceipt.OUTCOME_OVER_LIMIT);
            over.setBatchNo(batchNo);
            over.setReportedRows(inRows.size());
            over.setLandedRows(0);
            over.setReturnedRows(inRows.size());
            over.setMessage("册上递来" + inRows.size() + "行，压过单册上限" + MAX_ROWS
                    + "行，整册退回，库里未留半行");
            return over;
        }

        // 同一册码第二遍：只照面不翻账，头一遍落成的去向盖不掉
        TBarrPayBatch existing = this.barrPayBookDao.findLiveBook(batchNo);
        if (existing != null) {
            return faceBook(existing, inRows);
        }

        // 新册进门：册头自报行数与原报实际不合，以实际落库数为准（口径定死），自报数留底、对不上点名
        List<String> problems = new ArrayList<>();
        Integer declared = parseDeclaredRows(form.getDeclaredRows(), problems);
        if (declared != null && declared != inRows.size()) {
            problems.add("册头自报" + declared + "行，实际递来" + inRows.size() + "行，以实际落库为准");
        }
        int payYear = parsePayYear(form.getPayYear());
        String streetCode = trim(form.getStreetCode());
        String payKind = trim(form.getPayKind());
        BigDecimal cap = loadCap(streetCode);

        // 逐行判收判退：行次系统按落库先后排上；同一户占两行各走各的，谁也不捎带谁
        List<Long> insertedIds = new ArrayList<>();
        try {
            for (int i = 0; i < inRows.size(); i++) {
                TBarrPayRow r = judgeRow(batchNo, i + 1, inRows.get(i), payKind, cap, operator);
                this.barrPayRowMapper.insert(r);
                insertedIds.add(r.getId());
            }
            TBarrPayBatch book = new TBarrPayBatch();
            book.setBatchNo(batchNo);
            book.setStreetCode(streetCode);
            book.setStreetName(resolveStreetName(streetCode));
            book.setPayKind(payKind);
            book.setPayYear(payYear);
            book.setDeclaredRows(declared);
            book.setDelFlag(0);
            book.setCreateBy(operator);
            this.barrPayBatchMapper.insert(book);
        } catch (DuplicateKeyException e) {
            // 并发另一趟已落册：本趟已写的行当场抹掉，整趟按照面收场，不把异常甩出去
            if (!insertedIds.isEmpty()) {
                this.barrPayRowMapper.deleteBatchIds(insertedIds);
            }
            TBarrPayBatch winner = this.barrPayBookDao.findLiveBook(batchNo);
            if (winner != null) {
                return faceBook(winner, inRows);
            }
            PayBookReceipt rc = receiptFromDb(batchNo, PayBookReceipt.OUTCOME_DUPLICATE);
            rc.setMessage("册码「" + batchNo + "」正有另一趟在落册，这一趟只照面不翻账");
            return rc;
        }

        // 末了回库一行一行数出回执三数（屏上先亮的那份不作数），册头情形随数落
        PayBookReceipt rc = receiptFromDb(batchNo, null);
        rc.setOutcome(rc.isEmpty() ? PayBookReceipt.OUTCOME_EMPTY : PayBookReceipt.OUTCOME_LANDED);
        rc.setMessage(rc.isEmpty()
                ? "空册：一行都没落着，不出核讫（原报" + rc.getReportedRows() + "行，退回" + rc.getReturnedRows() + "行）"
                : "册已收下：原报" + rc.getReportedRows() + "行，落地" + rc.getLandedRows()
                + "行，退回" + rc.getReturnedRows() + "行");
        rc.getProblems().addAll(0, problems);

        TBarrPayBatch landed = this.barrPayBookDao.findLiveBook(batchNo);
        landed.setStatus(rc.isEmpty() ? BOOK_EMPTY : BOOK_VERIFIED);
        this.barrPayBatchMapper.updateById(landed);
        return rc;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdrawBook(String batchNo, String operator) {
        String no = trim(batchNo);
        if (StringUtils.isEmpty(no)) {
            throw new PayBookRuleException("册码空着，没册可撤");
        }
        TBarrPayBatch book = this.barrPayBookDao.findLiveBook(no);
        if (book == null) {
            throw new PayBookRuleException("册码「" + no + "」查无在册的册，没册可撤");
        }
        // 册头与全册行一并撤下留底，活册活行占位让位；撤完才许重新递
        int changed = this.barrPayBookDao.withdrawBook(book.getId(), operator);
        if (changed != 1) {
            throw new PayBookRuleException("撤册没落成：册子刚被别人动过，照原样再办一回");
        }
        this.barrPayBookDao.withdrawRows(no, operator);
    }

    @Override
    public PayBookReceipt receiptOf(String batchNo) {
        String no = trim(batchNo);
        if (StringUtils.isEmpty(no)) {
            throw new PayBookRuleException("册码空着，给不了回执");
        }
        TBarrPayBatch book = this.barrPayBookDao.findLiveBook(no);
        if (book == null) {
            throw new PayBookRuleException("册码「" + no + "」查无在册的册，给不了回执");
        }
        PayBookReceipt rc = receiptFromDb(no, null);
        rc.setOutcome(rc.isEmpty() ? PayBookReceipt.OUTCOME_EMPTY : PayBookReceipt.OUTCOME_LANDED);
        rc.setMessage(rc.isEmpty()
                ? "空册：一行都没落着，不出核讫"
                : "原报" + rc.getReportedRows() + "行，落地" + rc.getLandedRows()
                + "行，退回" + rc.getReturnedRows() + "行");
        return rc;
    }

    @Override
    public PayBookAudit auditBook(String batchNo) {
        String no = trim(batchNo);
        if (StringUtils.isEmpty(no)) {
            throw new PayBookRuleException("册码空着，对不了账");
        }
        TBarrPayBatch book = this.barrPayBookDao.findLiveBook(no);
        if (book == null) {
            throw new PayBookRuleException("册码「" + no + "」查无在册的册，对不了账");
        }
        PayBookAudit audit = new PayBookAudit();
        audit.setBatchNo(no);
        audit.setBookStatus(book.getStatus());
        audit.setDeclaredRows(book.getDeclaredRows());

        List<TBarrPayRow> rows = this.barrPayBookDao.listRows(no);
        int landed = 0;
        int returned = 0;
        List<String> problems = new ArrayList<>();
        for (TBarrPayRow r : rows) {
            Integer st = r.getStatus();
            if (st == null || st == ROW_PENDING) {
                nameRow(problems, r.getRowNo(), "去向空悬（未勾），回执三数合不上它");
            } else if (st == ROW_LANDED) {
                landed++;
            } else if (st == ROW_RETURNED) {
                returned++;
            } else {
                nameRow(problems, r.getRowNo(), "落法写出三选之外（情形=" + st + "），只许未勾／收下／送回");
            }
        }
        int reported = rows.size();
        audit.setReportedRows(reported);
        audit.setLandedRows(landed);
        audit.setReturnedRows(returned);
        if (landed + returned != reported) {
            problems.add("落地" + landed + "行＋退回" + returned + "行，并起来对不上原报" + reported + "行");
        }
        if (book.getDeclaredRows() != null && book.getDeclaredRows() != reported) {
            problems.add("册头自报" + book.getDeclaredRows() + "行，实际落库" + reported + "行，以实际落库为准");
        }
        if (book.getStatus() != null && book.getStatus() == BOOK_VERIFIED && landed == 0) {
            problems.add("册头落着核讫，库里一行都没落地，两处合不上");
        }
        if (book.getStatus() != null && book.getStatus() == BOOK_EMPTY && landed > 0) {
            problems.add("册头落着空册，库里有" + landed + "行落地，两处合不上");
        }
        audit.setProblems(problems);
        audit.setMatched(problems.isEmpty());
        return audit;
    }

    @Override
    public List<TBarrPayBatch> listBooks() {
        return this.barrPayBookDao.listLiveBooks();
    }

    @Override
    public List<TBarrPayRow> listCurrentRows(String batchNo) {
        if (StringUtils.isEmpty(batchNo)) {
            return new ArrayList<>();
        }
        return this.barrPayBookDao.listCurrentRows(batchNo.trim());
    }

    @Override
    public List<TBarrPayRow> listPastRows(String batchNo) {
        if (StringUtils.isEmpty(batchNo)) {
            return new ArrayList<>();
        }
        return this.barrPayBookDao.listPastRows(batchNo.trim());
    }

    @Override
    public int moveStaleToPast(int currentPayYear, String operator) {
        // 连着两个拨付年度还没着落才挪：本年度减去两个年度，是那道坎
        return this.barrPayBookDao.moveStaleToPast(currentPayYear - 2, operator);
    }

    // ============================ 内部 ============================

    /**
     * 照面：同一册码第二遍递来，只拿它同库里已有的行照个面——不添一行、不减一行，
     * 头一遍落成的去向盖不掉它；对不上的行次点名，旁处不许自揣。
     */
    private PayBookReceipt faceBook(TBarrPayBatch book, List<PayBookForm.Row> inRows) {
        String batchNo = book.getBatchNo();
        PayBookReceipt rc = receiptFromDb(batchNo, PayBookReceipt.OUTCOME_DUPLICATE);
        List<TBarrPayRow> stored = this.barrPayBookDao.listRows(batchNo);
        List<String> diffs = new ArrayList<>();
        if (inRows.size() != stored.size()) {
            diffs.add("这一趟递来" + inRows.size() + "行，册里落着" + stored.size() + "行，行数对不上");
        }
        int common = Math.min(inRows.size(), stored.size());
        int mismatched = 0;
        for (int i = 0; i < common; i++) {
            TBarrPayRow db = stored.get(i);
            PayBookForm.Row in = inRows.get(i);
            if (!sameRow(db, in)) {
                mismatched++;
                if (mismatched <= PROBLEM_ROW_LIMIT) {
                    diffs.add("行次" + db.getRowNo() + " 与册里那行对不上（册里：代号" + db.getItemCode()
                            + "／额" + db.getQty() + "／处数" + db.getQualiNum() + "；这趟：代号" + trim(in.getItemCode())
                            + "／额" + trim(in.getQty()) + "／处数" + trim(in.getQualiNum()) + "）");
                }
            }
        }
        if (mismatched > PROBLEM_ROW_LIMIT) {
            diffs.add("……另有" + (mismatched - PROBLEM_ROW_LIMIT) + "行与册里对不上，不一一点名");
        }
        rc.getProblems().addAll(diffs);
        rc.setMessage("册码「" + batchNo + "」已落过册，这一趟只照面不翻账：原报" + rc.getReportedRows()
                + "行，落地" + rc.getLandedRows() + "行，退回" + rc.getReturnedRows()
                + "行；确实要动先办撤册，再重新递");
        return rc;
    }

    /** 照面逐位对坐：代号全等、两个数数值相等才算同一行；户名、半截代号一概不认 */
    private boolean sameRow(TBarrPayRow db, PayBookForm.Row in) {
        String inCode = trim(in.getItemCode());
        if (db.getItemCode() == null ? inCode != null : !db.getItemCode().equals(inCode)) {
            return false;
        }
        return numEq(db.getQty(), parseNumber(in.getQty()))
                && numEq(db.getQualiNum(), parseNumber(in.getQualiNum()));
    }

    /**
     * 回执三数只认这一趟回库点出来的数：原报、落地、退回逐行数出；
     * 没落成的行把纸面行次随回执带出去；有空悬未勾的行，当场点名——
     * 退回与落地并起来对不上原报，不许拿「差不离」糊过去。
     */
    private PayBookReceipt receiptFromDb(String batchNo, String outcome) {
        PayBookReceipt rc = new PayBookReceipt();
        rc.setOutcome(outcome);
        rc.setBatchNo(batchNo);
        int reported = this.barrPayBookDao.countRows(batchNo);
        int landed = this.barrPayBookDao.countRowsByStatus(batchNo, ROW_LANDED);
        int returned = this.barrPayBookDao.countRowsByStatus(batchNo, ROW_RETURNED);
        rc.setReportedRows(reported);
        rc.setLandedRows(landed);
        rc.setReturnedRows(returned);
        rc.setEmpty(landed == 0);
        List<PayBookReceipt.ReturnedItem> items = new ArrayList<>();
        for (TBarrPayRow r : this.barrPayBookDao.listRowsByStatus(batchNo, ROW_RETURNED)) {
            items.add(new PayBookReceipt.ReturnedItem(r.getRowNo(), r.getReturnReason()));
        }
        rc.setReturnedItems(items);
        if (landed + returned != reported) {
            List<TBarrPayRow> pending = this.barrPayBookDao.listRowsByStatus(batchNo, ROW_PENDING);
            int named = 0;
            for (TBarrPayRow r : pending) {
                named++;
                if (named <= PROBLEM_ROW_LIMIT) {
                    nameRow(rc.getProblems(), r.getRowNo(), "去向空悬（未勾），回执三数合不上它");
                }
            }
            if (named > PROBLEM_ROW_LIMIT) {
                rc.getProblems().add("……另有" + (named - PROBLEM_ROW_LIMIT) + "行空悬未勾，不一一点名");
            }
        }
        return rc;
    }

    /**
     * 逐行判收判退：行次按落库先后排上，合法非法都认这一行号；
     * 送回的缘故写在行上，同册别行的去向不受它牵动。
     */
    private TBarrPayRow judgeRow(String batchNo, int rowNo, PayBookForm.Row in, String payKind,
                                 BigDecimal cap, String operator) {
        TBarrPayRow r = new TBarrPayRow();
        r.setBatchNo(batchNo);
        r.setRowNo(rowNo);
        r.setItemCode(trim(in.getItemCode()));
        r.setQty(parseNumber(in.getQty()));
        r.setQualiNum(parseNumber(in.getQualiNum()));
        r.setVoucherImg(trim(in.getVoucherImg()));
        r.setPayKind(payKind);
        r.setPastFlag(0);
        r.setDelFlag(0);
        r.setCreateBy(operator);
        String reason = returnReason(in, r, cap);
        r.setStatus(reason == null ? ROW_LANDED : ROW_RETURNED);
        r.setReturnReason(reason);
        return r;
    }

    /**
     * 送回缘故只此一把尺，按行逐格问下去，头一格站不住就原路送回：
     * 代号空着／长过尺 → 两个数缺任一个（不许谁替它凑）→ 额写成零或负 →
     * 高过当地封顶线 → 影像没随册来。一行只落头一个缘故。
     */
    private String returnReason(PayBookForm.Row in, TBarrPayRow r, BigDecimal cap) {
        if (StringUtils.isEmpty(r.getItemCode())) {
            return "进度卡代号空着：认户只认代号，这一行没户可认";
        }
        if (r.getItemCode().length() > ITEM_CODE_MAX_LEN) {
            return "代号长过那一把尺（" + ITEM_CODE_MAX_LEN + "字），这一行站不住";
        }
        if (StringUtils.isEmpty(trim(in.getQty()))) {
            return "请领补助额没随行来：两个数得一块儿递，不许谁替它凑一个";
        }
        if (r.getQty() == null) {
            return "请领补助额写得不像个数（原文「" + abbrev(in.getQty()) + "」）";
        }
        if (StringUtils.isEmpty(trim(in.getQualiNum()))) {
            return "验收合格处数没随行来：两个数得一块儿递，不许谁替它凑一个";
        }
        if (r.getQualiNum() == null) {
            return "验收合格处数写得不像个数（原文「" + abbrev(in.getQualiNum()) + "」）";
        }
        if (r.getQty().compareTo(BigDecimal.ZERO) <= 0) {
            return "请领额写成零或负数，这一行原路送回";
        }
        if (cap != null && r.getQty().compareTo(cap) > 0) {
            return "请领额高过当地那条封顶线（线" + cap.stripTrailingZeros().toPlainString()
                    + "，本行" + r.getQty().stripTrailingZeros().toPlainString() + "）";
        }
        if (StringUtils.isEmpty(r.getVoucherImg())) {
            return "验收凭证影像没随册来，按没备齐办";
        }
        return null;
    }

    /** 当地那条封顶线：街上没划线（或册上没落街）回空，进门不拿它卡行 */
    private BigDecimal loadCap(String streetCode) {
        if (StringUtils.isEmpty(streetCode)) {
            return null;
        }
        TBarrCapLine line = this.barrPayBookDao.findCapLine(streetCode);
        return line == null ? null : line.getCapAmount();
    }

    /** 街名随册冗余：照街道册对代号，对不上不强凑，留空不卡册 */
    private String resolveStreetName(String streetCode) {
        if (StringUtils.isEmpty(streetCode) || this.sysStreetMapper == null) {
            return null;
        }
        List<TSysStreet> list = this.sysStreetMapper.selectList(new QueryWrapper<TSysStreet>()
                .and(w -> w.isNull("data_state").or().ne("data_state", 1))
                .eq("street_code", streetCode)
                .last("limit 1"));
        return list.isEmpty() ? null : list.get(0).getStreetName();
    }

    /** 册头自报行数：空着当没写；写得不像个数记一笔、按没写办，不拿它卡册 */
    private Integer parseDeclaredRows(String raw, List<String> problems) {
        String v = trim(raw);
        if (StringUtils.isEmpty(v)) {
            return null;
        }
        try {
            return Integer.valueOf(v);
        } catch (NumberFormatException e) {
            problems.add("册头自报行数写得不像个数（原文「" + abbrev(raw) + "」），按没写办");
            return null;
        }
    }

    /** 拨付年度：空着按进门那年落；写得不像个数，册头落不了档，整册拒在门外 */
    private int parsePayYear(String raw) {
        String v = trim(raw);
        if (StringUtils.isEmpty(v)) {
            return LocalDate.now().getYear();
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new PayBookRuleException("拨付年度写得不像个数（原文「" + abbrev(raw) + "」），册子进不了门");
        }
    }

    /** 原文收进来的数：空着回空（缺数不许凑），不像个数回空（缘故里带原文） */
    private BigDecimal parseNumber(String raw) {
        String v = trim(raw);
        if (StringUtils.isEmpty(v)) {
            return null;
        }
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean numEq(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.compareTo(b) == 0;
    }

    /** 对不上点名：哪一行、哪一格合不上，一句一句讲清 */
    private void nameRow(List<String> problems, Integer rowNo, String what) {
        problems.add("行次" + (rowNo == null ? "?" : rowNo) + " " + what);
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }

    /** 缘故里引原文，最长引这么一截，免得把行撑爆 */
    private String abbrev(String raw) {
        String v = trim(raw);
        if (v == null) {
            return "";
        }
        return v.length() <= 20 ? v : v.substring(0, 20) + "…";
    }
}
