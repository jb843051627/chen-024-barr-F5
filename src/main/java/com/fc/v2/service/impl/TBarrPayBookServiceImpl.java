package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Resource;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TBarrPayBookMapper;
import com.fc.v2.mapper.auto.TBarrPayRowMapper;
import com.fc.v2.mapper.auto.TBarrSiteCardMapper;
import com.fc.v2.mapper.custom.BarrPayBookDao;
import com.fc.v2.model.auto.TBarrPayBook;
import com.fc.v2.model.auto.TBarrPayCap;
import com.fc.v2.model.auto.TBarrPayRow;
import com.fc.v2.model.auto.TBarrSiteCard;
import com.fc.v2.model.custom.PayBookAudit;
import com.fc.v2.model.custom.PayBookForm;
import com.fc.v2.model.custom.PayBookReceipt;
import com.fc.v2.service.ITBarrPayBookService;
import com.fc.v2.util.StringUtils;

/**
 * 补助核销册一道门：册子进门只此一道手续，走法全在 {@link #enterBook} 这一处讲清。
 *
 * 口径定死（别处不许自揣）：
 * 一、册头那格写法与实际递来的对不上时，以逐行落库、回库点出来的数为准——照面而不翻账。
 * 二、行上两个数（请领额、验收合格处数）得一块儿递，缺任一个不许谁替它凑，行送回。
 * 三、认户只认进度卡代号；拿户名去对的、代号念得不全的，一律按没对上办（未勾，挂着）。
 * 四、行落地情形只此三格：0未勾、1收下、2送回；同户占两行各走各的，谁的结果都不盖谁。
 * 五、行次由系统按落库先后排上，排定不再挪，不给手填；送回的行再翻还是原行次。
 * 六、回执三个数只认这一趟回库点出来的，屏上先亮的那份不算；退回+落地=原报。
 * 七、同一个册码第二遍递来只照面：不添一行、不减一行；要动先办撤册，办完重新递另起一轮。
 * 八、空册（一行都没落着）回执上只写"空册"两字，不出核讫；核讫是给有行数的册备的。
 * 九、未勾行不许长压：连着两个拨付年度（当前年−落库年≥2）还没着落，挪去往期层。
 *
 * @author fuce
 * @date 2026-10-06
 */
@Service
public class TBarrPayBookServiceImpl implements ITBarrPayBookService {

    /** 单册行数上限：头一步先点行数，压过上限整册退回，库里不许留半行 */
    public static final int MAX_ROWS = 500;
    /** 代号那把尺：与列宽对齐，长过它的行原路送回 */
    public static final int CODE_RULER = 64;

    /** 行落地情形：只此三格 */
    private static final int ROW_PENDING = 0;
    private static final int ROW_TAKEN = 1;
    private static final int ROW_RETURNED = 2;

    /** 空册回话：一行都没落着的册，回执上只写这两个字，不出核讫 */
    private static final String MSG_EMPTY_BOOK = "空册";

    @Resource
    private TBarrPayRowMapper barrPayRowMapper;
    @Resource
    private TBarrPayBookMapper barrPayBookMapper;
    @Resource
    private TBarrSiteCardMapper barrSiteCardMapper;
    @Resource
    private BarrPayBookDao barrPayBookDao;

    // ============================ 进门（只此一道手续） ============================

    /**
     * 整册进门。册头先点行数，随后逐行判收判退，末了回执三数一并轧出，中途不过第二处的手。
     * 全趟只拍这一次钟：册头与逐行的落库时刻同出一个快照，别处不许另读一遍钟。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayBookReceipt enterBook(PayBookForm form, String operator) {
        Date at = new Date();
        PayBookReceipt receipt = new PayBookReceipt();

        // 册码空着：门都认不得，回话不收，一行一册都不落
        String bookNo = form == null ? null : trim(form.getBookNo());
        if (bookNo == null) {
            receipt.setMessage("册码空着，递不进来");
            return receipt;
        }
        receipt.setBookNo(bookNo);

        List<TBarrPayRow> rows = form.getRows() == null ? Collections.<TBarrPayRow>emptyList() : form.getRows();
        int actual = rows.size();

        // 册头先点行数（头一步）：册头那格写法只照面不翻账，作准的是实际递来的行数
        receipt.setDeclaredRows(form.getDeclaredRows());
        receipt.setDeclaredMatch(form.getDeclaredRows() != null && form.getDeclaredRows() == actual);
        if (form.getDeclaredRows() != null && form.getDeclaredRows() != actual) {
            receipt.getFaceNotes().add("册头那格写" + form.getDeclaredRows() + "行，实际递来" + actual
                    + "行，以实际落库点出来的为准");
        }

        // 处里划的上限：压过上限整册退回，库里不许留半行（册头也不立）
        if (actual > MAX_ROWS) {
            receipt.setRejectedWhole(true);
            receipt.setMessage("一册" + actual + "行，压过单册上限" + MAX_ROWS + "行，整册退回，库里一行不留");
            return receipt;
        }

        // 同一个册码第二遍递来：只许照面——不添一行、不减一行，头一遍落成的去向盖不掉它
        TBarrPayBook current = this.barrPayBookDao.findCurrentBook(bookNo);
        if (current != null) {
            return faceReceipt(current, rows, receipt);
        }

        // 撤过的册重新递：另起一轮，两轮分开放，旧轮留底
        int round = this.barrPayBookDao.maxRound(bookNo) + 1;
        receipt.setRoundNo(round);

        // 册头先落（本趟有效）：并发两趟由 uk_book_round 兜底，撞上的那趟当照面认，不炸
        TBarrPayBook book = new TBarrPayBook();
        book.setBookNo(bookNo);
        book.setRoundNo(round);
        book.setDeclaredRows(form.getDeclaredRows());
        book.setSrcKind(trim(form.getSrcKind()));
        book.setStreetCode(trim(form.getStreetCode()));
        book.setStatus(TBarrPayBook.STATUS_ACTIVE);
        book.setCreateBy(operator);
        book.setCreateTime(at);
        try {
            this.barrPayBookMapper.insert(book);
        } catch (DuplicateKeyException e) {
            TBarrPayBook standing = this.barrPayBookDao.findBook(bookNo, round);
            if (standing == null) {
                // 极端：撞上键却翻不着册头（并发那趟回滚了）——这趟不动库，回话稍后再递
                receipt.setMessage("册" + bookNo + "进门撞上并发，库里册头没落定，这趟不动库，稍后再递");
                return receipt;
            }
            return faceReceipt(standing, rows, receipt);
        }

        // 逐行判收判退：行次按落库先后排上，排定不再挪；两行各走各的，谁的结果都不盖谁
        BigDecimal cap = capOf(book.getStreetCode());
        Set<String> cards = loadCardCodes(rows);
        for (int i = 0; i < rows.size(); i++) {
            TBarrPayRow r = rows.get(i) == null ? new TBarrPayRow() : rows.get(i);
            r.setBatchNo(bookNo);
            r.setRoundNo(round);
            r.setRowNo(i + 1);
            r.setDelFlag(0);
            r.setPastFlag(0);
            r.setCreateBy(operator);
            r.setCreateTime(at);
            judge(r, cap, cards);
            try {
                this.barrPayRowMapper.insert(r);
            } catch (DuplicateKeyException e) {
                // 并发另一趟已把这一行落下：当已落过，不重不炸，这趟照数往下走
            }
        }

        // 回执三个数：回库一行一行点出来，屏上先亮的不作数
        int reported = this.barrPayBookDao.countRound(bookNo, round);
        int hanging = this.barrPayBookDao.countRoundStatus(bookNo, round, ROW_PENDING);
        int taken = this.barrPayBookDao.countRoundStatus(bookNo, round, ROW_TAKEN);
        int returned = this.barrPayBookDao.countRoundStatus(bookNo, round, ROW_RETURNED);
        int landed = hanging + taken;

        // 空册：一行都没落着，回执上只写"空册"两字，不出核讫
        String checkNo = landed > 0 ? "HJ-" + bookNo + "-R" + round : null;

        // 册头补上回执三数与核讫号：册头这本与逐行那套说的是同一批行，两处联动
        book.setReportedRows(reported);
        book.setLandedRows(landed);
        book.setReturnedRows(returned);
        book.setCheckNo(checkNo);
        book.setUpdateBy(operator);
        book.setUpdateTime(at);
        this.barrPayBookMapper.updateById(book);

        fillReceipt(receipt, bookNo, round, reported, landed, returned, checkNo);
        receipt.setEntered(true);
        receipt.setMessage(landed > 0
                ? "册" + bookNo + "第" + round + "轮落讫：原报" + reported + "行，落地" + landed
                + "行，退回" + returned + "行，核讫号" + checkNo
                : MSG_EMPTY_BOOK);
        return receipt;
    }

    // ============================ 回执 / 对账 / 撤册 / 往期 ============================

    /** 回执翻看：三个数只认回库现点出来的，屏上先亮的那份不算 */
    @Override
    public PayBookReceipt receiptOf(String bookNo) {
        PayBookReceipt receipt = new PayBookReceipt();
        String no = trim(bookNo);
        receipt.setBookNo(no);
        if (no == null) {
            receipt.setMessage("册码空着，查不了");
            return receipt;
        }
        TBarrPayBook current = this.barrPayBookDao.findCurrentBook(no);
        if (current == null) {
            int max = this.barrPayBookDao.maxRound(no);
            if (max == 0) {
                receipt.setMessage("查无此册");
                return receipt;
            }
            // 撤了的册：回执是撤前那一趟点出来的，照旧翻得着
            TBarrPayBook last = this.barrPayBookDao.findBook(no, max);
            receipt.setRoundNo(last.getRoundNo());
            receipt.setDeclaredRows(last.getDeclaredRows());
            receipt.setReported(nz(last.getReportedRows()));
            receipt.setLanded(nz(last.getLandedRows()));
            receipt.setReturned(nz(last.getReturnedRows()));
            receipt.setCheckNo(last.getCheckNo());
            receipt.setEmptyBook(nz(last.getLandedRows()) == 0);
            receipt.setMessage("册" + no + "第" + max + "轮已撤，回执是撤前那一趟落成的");
            return receipt;
        }
        int round = current.getRoundNo();
        int reported = this.barrPayBookDao.countRound(no, round);
        int landed = this.barrPayBookDao.countRoundStatus(no, round, ROW_PENDING)
                + this.barrPayBookDao.countRoundStatus(no, round, ROW_TAKEN);
        int returned = this.barrPayBookDao.countRoundStatus(no, round, ROW_RETURNED);
        fillReceipt(receipt, no, round, reported, landed, returned, current.getCheckNo());
        receipt.setEntered(true);
        receipt.setDeclaredRows(current.getDeclaredRows());
        receipt.setDeclaredMatch(current.getDeclaredRows() != null && current.getDeclaredRows() == reported);
        receipt.setMessage(landed > 0
                ? "册" + no + "第" + round + "轮：原报" + reported + "行，落地" + landed + "行，退回" + returned + "行"
                : MSG_EMPTY_BOOK);
        return receipt;
    }

    /** 对账：册头那本与逐行那套两处联动，合不上的那几行点名报出来 */
    @Override
    public PayBookAudit audit(String bookNo) {
        PayBookAudit audit = new PayBookAudit();
        String no = trim(bookNo);
        audit.setBookNo(no);
        TBarrPayBook current = no == null ? null : this.barrPayBookDao.findCurrentBook(no);
        if (current == null) {
            audit.setFound(false);
            return audit;
        }
        audit.setFound(true);
        int round = current.getRoundNo();
        audit.setRoundNo(round);
        audit.setStoredReported(nz(current.getReportedRows()));
        audit.setStoredLanded(nz(current.getLandedRows()));
        audit.setStoredReturned(nz(current.getReturnedRows()));

        int reported = this.barrPayBookDao.countRound(no, round);
        int hanging = this.barrPayBookDao.countRoundStatus(no, round, ROW_PENDING);
        int taken = this.barrPayBookDao.countRoundStatus(no, round, ROW_TAKEN);
        int returned = this.barrPayBookDao.countRoundStatus(no, round, ROW_RETURNED);
        int landed = hanging + taken;
        audit.setActualReported(reported);
        audit.setActualLanded(landed);
        audit.setActualReturned(returned);

        List<String> mismatches = new ArrayList<>();
        if (audit.getStoredReported() != reported) {
            mismatches.add("回执原报" + audit.getStoredReported() + "行，回库点出" + reported + "行");
        }
        if (audit.getStoredLanded() != landed) {
            mismatches.add("回执落地" + audit.getStoredLanded() + "行，回库点出" + landed
                    + "行（收下" + taken + "+未勾" + hanging + "）");
        }
        if (audit.getStoredReturned() != returned) {
            mismatches.add("回执退回" + audit.getStoredReturned() + "行，回库点出" + returned + "行");
        }
        if (landed + returned != reported) {
            mismatches.add("落地" + landed + "+退回" + returned + "与原报" + reported + "合不上");
        }
        // 任指一行：去向落不进三格的、送回行缘故空着的，逐行点名
        for (TBarrPayRow r : this.barrPayBookDao.listRoundRows(no, round)) {
            if (r.getStatus() == null || r.getStatus() < ROW_PENDING || r.getStatus() > ROW_RETURNED) {
                mismatches.add("行次" + r.getRowNo() + "：去向落不进三格（status=" + r.getStatus() + "）");
            } else if (r.getStatus() == ROW_RETURNED && trim(r.getReturnReason()) == null) {
                mismatches.add("行次" + r.getRowNo() + "：送回行缘故空着");
            }
        }
        audit.setMismatches(mismatches);
        audit.setConsistent(mismatches.isEmpty());
        return audit;
    }

    /** 撤册手续：在册那轮册头置已撤、行置删留底；办完同册码才许重新递（另起一轮） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String withdrawBook(String bookNo, String operator) {
        String no = trim(bookNo);
        if (no == null) {
            return "册码空着，撤不了";
        }
        TBarrPayBook current = this.barrPayBookDao.findCurrentBook(no);
        if (current == null) {
            return "册" + no + "没有在册的轮（未递过或已撤），撤册不动库";
        }
        Date at = new Date();
        int rows = this.barrPayBookDao.withdrawRows(no, current.getRoundNo(), operator, at);
        current.setStatus(TBarrPayBook.STATUS_WITHDRAWN);
        current.setUpdateBy(operator);
        current.setUpdateTime(at);
        this.barrPayBookMapper.updateById(current);
        return "册" + no + "第" + current.getRoundNo() + "轮已撤（" + rows + "行置删留底），同册码可重新递";
    }

    /**
     * 挪往期：未勾行连着两个拨付年度还没着落（当前年−落库年≥2），挪去往期那一层。
     * 只动还在现行层的未勾在册行；落库时刻空着的旧行没凭据，不挪。
     */
    @Override
    public int movePastDue(Integer yearNow, Date passedAt) {
        Date at = passedAt != null ? passedAt : new Date();
        int year = yearNow != null ? yearNow
                : LocalDateTime.ofInstant(at.toInstant(), ZoneId.systemDefault()).getYear();
        int moved = 0;
        for (TBarrPayRow r : this.barrPayBookDao.listHangingCurrent()) {
            if (r.getCreateTime() == null) {
                continue;
            }
            int rowYear = LocalDateTime.ofInstant(r.getCreateTime().toInstant(), ZoneId.systemDefault()).getYear();
            if (year - rowYear >= 2) {
                moved += this.barrPayBookDao.markPast(r.getId(), at);
            }
        }
        return moved;
    }

    /** 翻册：在册那一轮的全部行，按原报行次排；没有在册轮=一册都翻不着 */
    @Override
    public List<TBarrPayRow> listBookRows(String bookNo) {
        String no = trim(bookNo);
        if (no == null) {
            return Collections.emptyList();
        }
        TBarrPayBook current = this.barrPayBookDao.findCurrentBook(no);
        if (current == null) {
            return Collections.emptyList();
        }
        return this.barrPayBookDao.listRoundRows(no, current.getRoundNo());
    }

    @Override
    public List<TBarrPayRow> listHangingCurrent() {
        return this.barrPayBookDao.listHangingCurrent();
    }

    @Override
    public List<TBarrPayRow> listPast(String bookNo) {
        return this.barrPayBookDao.listPast(trim(bookNo));
    }

    /** 册头集合只读翻看：写册没有第二处口子，这里只读不写 */
    @Override
    public List<TBarrPayBook> selectBookList(com.baomidou.mybatisplus.core.conditions.Wrapper<TBarrPayBook> wrapper) {
        return this.barrPayBookMapper.selectList(wrapper);
    }

    // ============================ 内部 ============================

    /**
     * 逐行判收判退，只此一处的走法（缘故写在行上，同册别行不受牵动）：
     * 代号空→送回；代号长过那把尺→送回；两个数缺任一个→送回；额零或负→送回；
     * 额高过当地封顶线→送回；影像没随册来→送回；代号没对上进度卡→未勾挂着；余下→收下。
     */
    private void judge(TBarrPayRow r, BigDecimal cap, Set<String> cards) {
        String code = trim(r.getItemCode());
        r.setItemCode(code);
        if (code == null) {
            sendBack(r, "进度卡代号空着");
            return;
        }
        if (code.length() > CODE_RULER) {
            sendBack(r, "代号长过那一把尺（" + CODE_RULER + "位）");
            return;
        }
        if (r.getQty() == null) {
            sendBack(r, "请领额缺着，两个数得一块儿递，不许替它凑");
            return;
        }
        if (r.getQualiNum() == null) {
            sendBack(r, "验收合格处数缺着，两个数得一块儿递，不许替它凑");
            return;
        }
        if (r.getQty().signum() <= 0) {
            sendBack(r, "请领额写成零或负数");
            return;
        }
        if (cap != null && r.getQty().compareTo(cap) > 0) {
            sendBack(r, "请领额高过当地封顶线（" + cap.stripTrailingZeros().toPlainString() + "）");
            return;
        }
        if (trim(r.getVoucherImg()) == null) {
            sendBack(r, "验收凭证影像没随册来，没备齐");
            return;
        }
        if (!cards.contains(code)) {
            // 没对上：拿户名去对的、代号念得不全的，一律照此办——未勾挂着，等对上
            r.setStatus(ROW_PENDING);
            r.setRemark("代号没对上进度卡，挂着等对上");
            return;
        }
        r.setStatus(ROW_TAKEN);
    }

    /** 原路送回：去向落送回，缘故写在行上 */
    private void sendBack(TBarrPayRow r, String reason) {
        r.setStatus(ROW_RETURNED);
        r.setReturnReason(reason);
    }

    /** 当地那条封顶线：册头落的那条街寻一行；没有行=这条街不卡 */
    private BigDecimal capOf(String streetCode) {
        if (streetCode == null) {
            return null;
        }
        TBarrPayCap cap = this.barrPayBookDao.findCap(streetCode);
        return cap == null ? null : cap.getCapQty();
    }

    /** 认户只认进度卡代号：整册的代号一把捞出来对，户名一概不看 */
    private Set<String> loadCardCodes(List<TBarrPayRow> rows) {
        Set<String> codes = new HashSet<>();
        for (TBarrPayRow r : rows) {
            String code = r == null ? null : trim(r.getItemCode());
            if (code != null && code.length() <= CODE_RULER) {
                codes.add(code);
            }
        }
        Set<String> matched = new HashSet<>();
        if (codes.isEmpty()) {
            return matched;
        }
        List<TBarrSiteCard> cards = this.barrSiteCardMapper.selectList(new QueryWrapper<TBarrSiteCard>()
                .select("id", "card_no", "del_flag")
                .in("card_no", codes));
        for (TBarrSiteCard c : cards) {
            if (c.getCardNo() != null && (c.getDelFlag() == null || c.getDelFlag() == 0)) {
                matched.add(c.getCardNo().trim());
            }
        }
        return matched;
    }

    /**
     * 第二遍照面：拿递来的同库里已有的行照个面——不添一行、不减一行，
     * 头一遍落成的去向盖不掉；对不上的地方逐条点名，以库里为准。
     */
    private PayBookReceipt faceReceipt(TBarrPayBook current, List<TBarrPayRow> incoming, PayBookReceipt receipt) {
        String bookNo = current.getBookNo();
        int round = current.getRoundNo();
        receipt.setBookNo(bookNo);
        receipt.setRoundNo(round);
        receipt.setDuplicate(true);

        // 回执三数只认回库现点的（照面这一趟不添不减，点出来的就是头一遍落成的）
        int reported = this.barrPayBookDao.countRound(bookNo, round);
        int landed = this.barrPayBookDao.countRoundStatus(bookNo, round, ROW_PENDING)
                + this.barrPayBookDao.countRoundStatus(bookNo, round, ROW_TAKEN);
        int returned = this.barrPayBookDao.countRoundStatus(bookNo, round, ROW_RETURNED);
        fillReceipt(receipt, bookNo, round, reported, landed, returned, current.getCheckNo());

        List<TBarrPayRow> standing = this.barrPayBookDao.listRoundRows(bookNo, round);
        List<String> notes = receipt.getFaceNotes();
        int n = Math.max(standing.size(), incoming.size());
        for (int i = 0; i < n; i++) {
            TBarrPayRow s = i < standing.size() ? standing.get(i) : null;
            TBarrPayRow inc = i < incoming.size() ? incoming.get(i) : null;
            if (s == null) {
                notes.add("行次" + (i + 1) + "：递来的多出一行（" + brief(inc) + "），库里没有，不添");
            } else if (inc == null) {
                notes.add("行次" + s.getRowNo() + "：库里已有（" + brief(s) + "），这趟没递来，不减");
            } else if (!sameRow(s, inc)) {
                notes.add("行次" + s.getRowNo() + "：递来的（" + brief(inc) + "）与库里（" + brief(s)
                        + "）对不上，以库里为准");
            }
        }
        receipt.setMessage("册" + bookNo + "第" + round + "轮早已落成，这趟只照面：不添一行、不减一行"
                + (notes.isEmpty() ? "，递来的与库里一册对得上" : "，" + notes.size() + "处对不上已点名"));
        return receipt;
    }

    /** 回执装车：三数落定，退回与空悬的行把纸面行次随回执带出去 */
    private void fillReceipt(PayBookReceipt receipt, String bookNo, int round,
                             int reported, int landed, int returned, String checkNo) {
        receipt.setReported(reported);
        receipt.setLanded(landed);
        receipt.setReturned(returned);
        receipt.setCheckNo(checkNo);
        receipt.setEmptyBook(landed == 0);
        List<PayBookReceipt.RowItem> returnedRows = new ArrayList<>();
        for (TBarrPayRow r : this.barrPayBookDao.listRoundStatusRows(bookNo, round, ROW_RETURNED)) {
            returnedRows.add(new PayBookReceipt.RowItem(r.getRowNo(), r.getItemCode(), r.getReturnReason()));
        }
        receipt.setReturnedRows(returnedRows);
        List<PayBookReceipt.RowItem> hangingRows = new ArrayList<>();
        for (TBarrPayRow r : this.barrPayBookDao.listRoundStatusRows(bookNo, round, ROW_PENDING)) {
            hangingRows.add(new PayBookReceipt.RowItem(r.getRowNo(), r.getItemCode(), r.getRemark()));
        }
        receipt.setHangingRows(hangingRows);
    }

    /** 照面比一比：代号与请领额逐项对（行次由站位定，不在此列） */
    private boolean sameRow(TBarrPayRow a, TBarrPayRow b) {
        return Objects.equals(trim(a.getItemCode()), trim(b.getItemCode()))
                && sameAmount(a.getQty(), b.getQty())
                && sameAmount(a.getQualiNum(), b.getQualiNum());
    }

    private boolean sameAmount(BigDecimal x, BigDecimal y) {
        if (x == null || y == null) {
            return x == null && y == null;
        }
        return x.compareTo(y) == 0;
    }

    /** 行上一眼：代号/请领额/合格处数 */
    private String brief(TBarrPayRow r) {
        if (r == null) {
            return "空行";
        }
        return (r.getItemCode() == null ? "（无代号）" : r.getItemCode())
                + "/" + (r.getQty() == null ? "额缺" : r.getQty().stripTrailingZeros().toPlainString())
                + "/" + (r.getQualiNum() == null ? "处数缺" : r.getQualiNum().stripTrailingZeros().toPlainString());
    }

    private int nz(Integer v) {
        return v == null ? 0 : v;
    }

    /** 空串、全空白都归一到 null；其余掐头去尾 */
    private String trim(String s) {
        return StringUtils.isEmpty(s) ? null : s.trim();
    }
}
