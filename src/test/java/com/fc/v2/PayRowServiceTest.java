package com.fc.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import com.fc.v2.common.exception.PayBookRuleException;
import com.fc.v2.mapper.auto.TBarrPayBatchMapper;
import com.fc.v2.mapper.auto.TBarrPayRowMapper;
import com.fc.v2.mapper.custom.BarrPayBookDao;
import com.fc.v2.model.auto.TBarrCapLine;
import com.fc.v2.model.auto.TBarrPayBatch;
import com.fc.v2.model.auto.TBarrPayRow;
import com.fc.v2.model.custom.PayBookAudit;
import com.fc.v2.model.custom.PayBookForm;
import com.fc.v2.model.custom.PayBookReceipt;
import com.fc.v2.service.impl.TBarrPayRowServiceImpl;

/**
 * 补助核销册进门核心规矩单测（不起 Spring、不连库；mapper 与回库口全打桩）。
 * 盯：点行数超限整册退、两数缺一个不凑、代号空与超长、零负、过封顶线、缺影像、
 * 行次系统排定不挪、同户两行各走各的、同册码第二遍只照面、撤册后重递、
 * 回执三数回库数出、对账点名、两年度未勾挪往期、并发撞键只落一回。
 */
public class PayRowServiceTest {

    private TBarrPayRowServiceImpl service;
    private TBarrPayRowMapper rowMapper;
    private TBarrPayBatchMapper batchMapper;
    private BarrPayBookDao bookDao;

    /** 模拟库：行（含撤册留底的） */
    private final List<TBarrPayRow> rowStore = new ArrayList<>();
    /** 模拟库：册头（含撤册留底的） */
    private final List<TBarrPayBatch> bookStore = new ArrayList<>();
    /** 模拟库：各街封顶线 */
    private final Map<String, BigDecimal> capStore = new HashMap<>();

    @BeforeEach
    public void setUp() throws Exception {
        service = new TBarrPayRowServiceImpl();
        rowMapper = mock(TBarrPayRowMapper.class);
        batchMapper = mock(TBarrPayBatchMapper.class);
        bookDao = mock(BarrPayBookDao.class);
        setField(service, "barrPayRowMapper", rowMapper);
        setField(service, "barrPayBatchMapper", batchMapper);
        setField(service, "barrPayBookDao", bookDao);

        // 行落库：撞活行唯一键（同册同次在册只容一行）要炸，与 uk_live_row 同一副脾气
        doAnswer(inv -> {
            TBarrPayRow r = inv.getArgument(0);
            boolean dup = liveRows(r.getBatchNo()).stream()
                    .anyMatch(x -> Objects.equals(x.getRowNo(), r.getRowNo()));
            if (dup) {
                throw new DuplicateKeyException("uk_live_row");
            }
            if (r.getId() == null) {
                r.setId((long) (rowStore.size() + 1));
            }
            rowStore.add(r);
            return 1;
        }).when(rowMapper).insert(any());
        doAnswer(inv -> {
            List<Long> ids = inv.getArgument(0);
            rowStore.removeIf(r -> ids.contains(r.getId()));
            return ids.size();
        }).when(rowMapper).deleteBatchIds(anyList());

        // 册头落库：撞活册唯一键（同一册码在册只容一册）要炸，与 uk_live_batch 同一副脾气
        doAnswer(inv -> {
            TBarrPayBatch b = inv.getArgument(0);
            boolean dup = bookStore.stream()
                    .anyMatch(x -> x.getDelFlag() != null && x.getDelFlag() == 0
                            && Objects.equals(x.getBatchNo(), b.getBatchNo()));
            if (dup) {
                throw new DuplicateKeyException("uk_live_batch");
            }
            if (b.getId() == null) {
                b.setId((long) (bookStore.size() + 1));
            }
            bookStore.add(b);
            return 1;
        }).when(batchMapper).insert(any());
        doAnswer(inv -> 1).when(batchMapper).updateById(any());

        // 回库口：三数、照面、撤册、挪往期全照库里现状答
        doAnswer(inv -> bookStore.stream()
                .filter(b -> b.getDelFlag() != null && b.getDelFlag() == 0
                        && Objects.equals(b.getBatchNo(), inv.getArgument(0)))
                .findFirst().orElse(null)).when(bookDao).findLiveBook(anyString());
        doAnswer(inv -> bookStore.stream()
                .filter(b -> b.getDelFlag() != null && b.getDelFlag() == 0)
                .collect(Collectors.toList())).when(bookDao).listLiveBooks();
        doAnswer(inv -> {
            BigDecimal cap = capStore.get(inv.getArgument(0));
            if (cap == null) {
                return null;
            }
            TBarrCapLine line = new TBarrCapLine();
            line.setStreetCode(inv.getArgument(0));
            line.setCapAmount(cap);
            return line;
        }).when(bookDao).findCapLine(anyString());
        doAnswer(inv -> (int) liveRows(inv.getArgument(0)).size()).when(bookDao).countRows(anyString());
        doAnswer(inv -> (int) liveRows(inv.getArgument(0)).stream()
                .filter(r -> r.getStatus() != null && r.getStatus() == (int) inv.getArgument(1))
                .count()).when(bookDao).countRowsByStatus(anyString(), anyInt());
        doAnswer(inv -> liveRows(inv.getArgument(0))).when(bookDao).listRows(anyString());
        doAnswer(inv -> liveRows(inv.getArgument(0)).stream()
                .filter(r -> r.getStatus() != null && r.getStatus() == (int) inv.getArgument(1))
                .collect(Collectors.toList())).when(bookDao).listRowsByStatus(anyString(), anyInt());
        doAnswer(inv -> liveRows(inv.getArgument(0)).stream()
                .filter(r -> r.getPastFlag() == null || r.getPastFlag() == 0)
                .collect(Collectors.toList())).when(bookDao).listCurrentRows(anyString());
        doAnswer(inv -> liveRows(inv.getArgument(0)).stream()
                .filter(r -> r.getPastFlag() != null && r.getPastFlag() == 1)
                .collect(Collectors.toList())).when(bookDao).listPastRows(anyString());
        doAnswer(inv -> {
            Long id = inv.getArgument(0);
            for (TBarrPayBatch b : bookStore) {
                if (Objects.equals(b.getId(), id) && b.getDelFlag() != null && b.getDelFlag() == 0) {
                    b.setDelFlag(1);
                    b.setStatus(9);
                    return 1;
                }
            }
            return 0;
        }).when(bookDao).withdrawBook(any(), anyString());
        doAnswer(inv -> {
            String batchNo = inv.getArgument(0);
            int n = 0;
            for (TBarrPayRow r : rowStore) {
                if (Objects.equals(r.getBatchNo(), batchNo) && r.getDelFlag() != null && r.getDelFlag() == 0) {
                    r.setDelFlag(1);
                    n++;
                }
            }
            return n;
        }).when(bookDao).withdrawRows(anyString(), anyString());
        doAnswer(inv -> {
            int maxYear = inv.getArgument(0);
            int n = 0;
            List<String> oldBooks = bookStore.stream()
                    .filter(b -> b.getDelFlag() != null && b.getDelFlag() == 0
                            && b.getPayYear() != null && b.getPayYear() <= maxYear)
                    .map(TBarrPayBatch::getBatchNo).collect(Collectors.toList());
            for (TBarrPayRow r : rowStore) {
                if (oldBooks.contains(r.getBatchNo()) && r.getDelFlag() != null && r.getDelFlag() == 0
                        && (r.getPastFlag() == null || r.getPastFlag() == 0)
                        && r.getStatus() != null && r.getStatus() == 0) {
                    r.setPastFlag(1);
                    n++;
                }
            }
            return n;
        }).when(bookDao).moveStaleToPast(anyInt(), anyString());
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    /** 在册行（撤册留底的不算），按行次摆开 */
    private List<TBarrPayRow> liveRows(String batchNo) {
        return rowStore.stream()
                .filter(r -> Objects.equals(r.getBatchNo(), batchNo)
                        && r.getDelFlag() != null && r.getDelFlag() == 0)
                .sorted((a, b) -> a.getRowNo() - b.getRowNo())
                .collect(Collectors.toList());
    }

    private PayBookForm form(String batchNo, List<PayBookForm.Row> rows) {
        PayBookForm f = new PayBookForm();
        f.setBatchNo(batchNo);
        f.setRows(rows);
        return f;
    }

    private PayBookForm.Row row(String itemCode, String qty, String qualiNum, String voucherImg) {
        return new PayBookForm.Row(itemCode, qty, qualiNum, voucherImg);
    }

    private PayBookForm.Row okRow(String code) {
        return row(code, "100", "2", "img/1.png");
    }

    /** 往库里直接栽一册（不走进门手续）：对账、挪往期、撤册这些后续手续的台子 */
    private TBarrPayBatch plantBook(String batchNo, Integer payYear, int status) {
        TBarrPayBatch b = new TBarrPayBatch();
        b.setId((long) (bookStore.size() + 1));
        b.setBatchNo(batchNo);
        b.setPayYear(payYear);
        b.setStatus(status);
        b.setDelFlag(0);
        bookStore.add(b);
        return b;
    }

    private TBarrPayRow plantRow(String batchNo, int rowNo, int status, int pastFlag) {
        TBarrPayRow r = new TBarrPayRow();
        r.setId((long) (rowStore.size() + 1));
        r.setBatchNo(batchNo);
        r.setRowNo(rowNo);
        r.setItemCode("K-" + rowNo);
        r.setQty(new BigDecimal("10"));
        r.setQualiNum(new BigDecimal("1"));
        r.setStatus(status);
        r.setPastFlag(pastFlag);
        r.setDelFlag(0);
        rowStore.add(r);
        return r;
    }

    // ============================ 册头先点行数 ============================

    @Test
    public void 空批进门_回执写空册_不出核讫() {
        PayBookReceipt rc = service.intakeBook(form("B-EMPTY", new ArrayList<>()), "tester");
        assertEquals(PayBookReceipt.OUTCOME_EMPTY, rc.getOutcome());
        assertEquals(0, rc.getReportedRows());
        assertEquals(0, rc.getLandedRows());
        assertEquals(0, rc.getReturnedRows());
        assertTrue(rc.isEmpty(), "一行都没落着：空册");
        assertEquals(1, bookStore.size(), "空册也立册头留档");
        assertEquals(2, bookStore.get(0).getStatus(), "空册情形落空册，不出核讫");
    }

    @Test
    public void 压过单册上限_整册退回_库里不留半行() {
        List<PayBookForm.Row> tooMany = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            tooMany.add(okRow("K-" + i));
        }
        PayBookReceipt rc = service.intakeBook(form("B-BIG", tooMany), "tester");
        assertEquals(PayBookReceipt.OUTCOME_OVER_LIMIT, rc.getOutcome());
        assertEquals(501, rc.getReportedRows());
        assertEquals(0, rc.getLandedRows());
        assertEquals(501, rc.getReturnedRows());
        assertTrue(rowStore.isEmpty(), "压过上限整册退回，库里不许留半行");
        assertTrue(bookStore.isEmpty(), "册头也不许落");
    }

    @Test
    public void 册码空着_册子进不了门() {
        assertThrows(PayBookRuleException.class, () -> service.intakeBook(form("  ", new ArrayList<>()), "tester"));
        assertThrows(PayBookRuleException.class, () -> service.intakeBook(new PayBookForm(), "tester"));
    }

    // ============================ 逐行判收判退 ============================

    @Test
    public void 两个数缺任一个_不许谁替它凑() {
        List<PayBookForm.Row> rows = new ArrayList<>();
        rows.add(row("K-1", null, "2", "img/1.png"));   // 缺请领额
        rows.add(row("K-2", "100", null, "img/2.png")); // 缺合格处数
        rows.add(row("K-3", "abc", "2", "img/3.png"));  // 额写得不像个数
        rows.add(row("K-4", "100", "x", "img/4.png"));  // 处数写得不像个数
        rows.add(okRow("K-5"));                          // 齐齐的：收下
        PayBookReceipt rc = service.intakeBook(form("B-MISS", rows), "tester");
        assertEquals(1, rc.getLandedRows());
        assertEquals(4, rc.getReturnedRows());
        List<TBarrPayRow> stored = liveRows("B-MISS");
        assertEquals(2, stored.get(0).getStatus());
        assertTrue(stored.get(0).getReturnReason().contains("没随行来"), "缺额缘故写在行上");
        assertNull(stored.get(0).getQty(), "缺了不许替它凑一个");
        assertTrue(stored.get(1).getReturnReason().contains("没随行来"), "缺处数缘故写在行上");
        assertTrue(stored.get(2).getReturnReason().contains("不像个数"), "不像个数缘故写在行上");
        assertTrue(stored.get(3).getReturnReason().contains("不像个数"), "不像个数缘故写在行上");
        assertEquals(1, stored.get(4).getStatus());
    }

    @Test
    public void 代号空着或长过那把尺_原路送回() {
        List<PayBookForm.Row> rows = new ArrayList<>();
        rows.add(row("  ", "100", "2", "img/1.png"));                       // 代号空着
        rows.add(row("K" + repeat('x', 64), "100", "2", "img/2.png"));      // 65字：长过尺
        rows.add(row("K" + repeat('x', 63), "100", "2", "img/3.png"));      // 64字：贴着尺，收下
        PayBookReceipt rc = service.intakeBook(form("B-CODE", rows), "tester");
        assertEquals(1, rc.getLandedRows());
        List<TBarrPayRow> stored = liveRows("B-CODE");
        assertTrue(stored.get(0).getReturnReason().contains("代号空着"));
        assertTrue(stored.get(1).getReturnReason().contains("长过那一把尺"));
        assertEquals(1, stored.get(2).getStatus());
    }

    private String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    @Test
    public void 额写成零或负_原路送回() {
        List<PayBookForm.Row> rows = new ArrayList<>();
        rows.add(row("K-1", "0", "2", "img/1.png"));
        rows.add(row("K-2", "-5", "2", "img/2.png"));
        rows.add(row("K-3", "0.01", "2", "img/3.png"));
        PayBookReceipt rc = service.intakeBook(form("B-ZERO", rows), "tester");
        assertEquals(1, rc.getLandedRows());
        assertTrue(liveRows("B-ZERO").get(0).getReturnReason().contains("零或负"));
        assertTrue(liveRows("B-ZERO").get(1).getReturnReason().contains("零或负"));
    }

    @Test
    public void 高过当地封顶线_原路送回_没划线的街不卡() {
        capStore.put("BSJD", new BigDecimal("5000"));
        List<PayBookForm.Row> rows = new ArrayList<>();
        rows.add(row("K-1", "5000", "2", "img/1.png"));     // 贴着线：收下
        rows.add(row("K-2", "5000.01", "2", "img/2.png"));  // 高过线：送回
        PayBookForm f = form("B-CAP", rows);
        f.setStreetCode("BSJD");
        PayBookReceipt rc = service.intakeBook(f, "tester");
        assertEquals(1, rc.getLandedRows());
        assertTrue(liveRows("B-CAP").get(1).getReturnReason().contains("封顶线"));

        List<PayBookForm.Row> noLine = new ArrayList<>();
        noLine.add(row("K-9", "999999", "2", "img/9.png"));
        PayBookForm f2 = form("B-NOCAP", noLine);
        f2.setStreetCode("NOWHERE");
        assertEquals(1, service.intakeBook(f2, "tester").getLandedRows(), "街上没划线，进门不拿它卡行");
    }

    @Test
    public void 影像没随册来_按没备齐办送回() {
        List<PayBookForm.Row> rows = new ArrayList<>();
        rows.add(row("K-1", "100", "2", null));          // 缺影像
        rows.add(row("K-2", "100", "2", "  "));          // 影像栏空串
        rows.add(row("K-3", "100", "2", "img/3.png"));   // 影像随册：收下
        PayBookReceipt rc = service.intakeBook(form("B-IMG", rows), "tester");
        assertEquals(1, rc.getLandedRows());
        List<TBarrPayRow> stored = liveRows("B-IMG");
        assertTrue(stored.get(0).getReturnReason().contains("影像"), "缘故写明缺的是影像");
        assertTrue(stored.get(1).getReturnReason().contains("影像"));
    }

    @Test
    public void 行次系统排定_送回行次日再翻还是原行次() {
        List<PayBookForm.Row> rows = new ArrayList<>();
        rows.add(okRow("K-1"));
        rows.add(row("  ", "100", "2", "img/2.png"));   // 送回
        rows.add(okRow("K-3"));
        rows.add(row("K-4", "0", "2", "img/4.png"));    // 送回
        rows.add(okRow("K-5"));
        service.intakeBook(form("B-SEQ", rows), "tester");
        List<TBarrPayRow> stored = liveRows("B-SEQ");
        for (int i = 0; i < 5; i++) {
            assertEquals(i + 1, stored.get(i).getRowNo(), "行次按落库先后排上，排定不再挪");
        }
        // 第二天再翻：送回的还是行次2、行次4，与街道纸面报册对得上号
        List<TBarrPayRow> returned = liveRows("B-SEQ").stream()
                .filter(r -> r.getStatus() == 2).collect(Collectors.toList());
        assertEquals(2, returned.get(0).getRowNo());
        assertEquals(4, returned.get(1).getRowNo());
    }

    @Test
    public void 同一户占两行_各走各的_谁也不盖谁() {
        List<PayBookForm.Row> rows = new ArrayList<>();
        rows.add(okRow("K-SAME"));                       // 头一行：收下
        rows.add(row("K-SAME", "0", "2", "img/2.png")); // 同一户第二行：送回
        PayBookReceipt rc = service.intakeBook(form("B-DUP-HH", rows), "tester");
        assertEquals(1, rc.getLandedRows());
        assertEquals(1, rc.getReturnedRows());
        List<TBarrPayRow> stored = liveRows("B-DUP-HH");
        assertEquals(1, stored.get(0).getStatus(), "收下头一行不捎带第二行");
        assertEquals(2, stored.get(1).getStatus(), "送回第二行不牵连头一行");
    }

    // ============================ 同一册码第二遍：照面 ============================

    @Test
    public void 同一册码第二遍_只照面不翻账() {
        List<PayBookForm.Row> first = new ArrayList<>();
        first.add(okRow("K-1"));
        first.add(okRow("K-2"));
        assertEquals(2, service.intakeBook(form("B-TWICE", first), "tester").getLandedRows());

        List<PayBookForm.Row> second = new ArrayList<>();
        second.add(okRow("K-1"));
        second.add(row("K-2", "999", "9", "img/x.png")); // 内容变了也没用
        second.add(okRow("K-3"));                         // 多递一行也不添
        PayBookReceipt again = service.intakeBook(form("B-TWICE", second), "tester");
        assertEquals(PayBookReceipt.OUTCOME_DUPLICATE, again.getOutcome());
        assertEquals(2, again.getReportedRows(), "回执还是头一遍落成的数");
        assertEquals(2, again.getLandedRows());
        assertEquals(2, liveRows("B-TWICE").size(), "不添一行、不减一行");
        assertEquals(new BigDecimal("100"), liveRows("B-TWICE").get(1).getQty(), "头一遍落成的去向盖不掉");
        assertFalse(again.getProblems().isEmpty(), "对不上的行次点名报出来");
        assertTrue(again.getProblems().stream().anyMatch(p -> p.contains("行次2")), "变了的那行点名");
    }

    @Test
    public void 撤册留底_撤完才许重新递() {
        List<PayBookForm.Row> rows = new ArrayList<>();
        rows.add(okRow("K-1"));
        service.intakeBook(form("B-RE", rows), "tester");

        service.withdrawBook("B-RE", "tester");
        assertEquals(1, rowStore.size(), "撤册的行留底，一条不少");
        assertEquals(1, rowStore.get(0).getDelFlag(), "留底的行不再占活位");
        assertEquals(9, bookStore.get(0).getStatus().intValue(), "册头落已撤");

        // 撤完重新递：同一册码立得起新册，行次重排
        List<PayBookForm.Row> again = new ArrayList<>();
        again.add(okRow("K-9"));
        again.add(okRow("K-10"));
        PayBookReceipt rc = service.intakeBook(form("B-RE", again), "tester");
        assertEquals(2, rc.getLandedRows());
        assertEquals(2, liveRows("B-RE").size(), "现行只数新册的行");
        assertEquals(3, rowStore.size(), "留底一行加新册两行，都在库里");

        assertThrows(PayBookRuleException.class, () -> service.withdrawBook("B-RE-NONE", "tester"),
                "查无在册的册不许撤");
    }

    // ============================ 回执与对账 ============================

    @Test
    public void 回执三数回库数出_退回行带纸面行次() {
        List<PayBookForm.Row> rows = new ArrayList<>();
        rows.add(okRow("K-1"));
        rows.add(row("  ", "100", "2", "img/2.png"));
        rows.add(row("K-3", "0", "2", "img/3.png"));
        rows.add(okRow("K-4"));
        PayBookReceipt rc = service.intakeBook(form("B-RCPT", rows), "tester");
        assertEquals(4, rc.getReportedRows());
        assertEquals(2, rc.getLandedRows());
        assertEquals(2, rc.getReturnedRows());
        assertEquals(rc.getReportedRows(), rc.getLandedRows() + rc.getReturnedRows(),
                "退回的与落地的并起来要等于原报的数");
        assertEquals(2, rc.getReturnedItems().size(), "没落成的行随回执带出去");
        assertEquals(2, rc.getReturnedItems().get(0).getRowNo(), "带的是纸面行次");
        assertNotNull(rc.getReturnedItems().get(0).getReason());

        // 回头再要回执：还是回库数出来的那三个数
        PayBookReceipt again = service.receiptOf("B-RCPT");
        assertEquals(4, again.getReportedRows());
        assertEquals(2, again.getLandedRows());
        assertEquals(2, again.getReturnedRows());
        assertThrows(PayBookRuleException.class, () -> service.receiptOf("B-NONE"), "查无此册不给回执");
    }

    @Test
    public void 册头自报与实际不合_以实际为准_对不上点名() {
        List<PayBookForm.Row> rows = new ArrayList<>();
        rows.add(okRow("K-1"));
        rows.add(okRow("K-2"));
        rows.add(okRow("K-3"));
        PayBookForm f = form("B-DECL", rows);
        f.setDeclaredRows("5");
        PayBookReceipt rc = service.intakeBook(f, "tester");
        assertEquals(3, rc.getReportedRows(), "以实际落库数为准");
        assertTrue(rc.getProblems().stream().anyMatch(p -> p.contains("自报5行") && p.contains("实际递来3行")),
                "册头那格与实际对不上，点名报出来");
        assertEquals(5, bookStore.get(0).getDeclaredRows().intValue(), "自报数原样留底");
    }

    @Test
    public void 对账_空悬行与合不上的都点名() {
        // 一册齐齐的：对得拢
        List<PayBookForm.Row> rows = new ArrayList<>();
        rows.add(okRow("K-1"));
        rows.add(row("K-2", "0", "2", "img/2.png"));
        service.intakeBook(form("B-AUD", rows), "tester");
        PayBookAudit ok = service.auditBook("B-AUD");
        assertTrue(ok.isMatched(), "齐齐的册对得拢");
        assertTrue(ok.getProblems().isEmpty());

        // 栽一行去向空悬的（去年那种接不平的）：点名报出来
        plantRow("B-AUD", 3, 0, 0);
        PayBookAudit bad = service.auditBook("B-AUD");
        assertFalse(bad.isMatched());
        assertTrue(bad.getProblems().stream().anyMatch(p -> p.contains("行次3") && p.contains("空悬")),
                "空悬的行点名，不用差不离糊过去");
        assertEquals(3, bad.getReportedRows());
        assertEquals(1, bad.getLandedRows());
        assertEquals(1, bad.getReturnedRows());
        assertThrows(PayBookRuleException.class, () -> service.auditBook("B-NONE"), "查无此册对不了账");
    }

    // ============================ 往期层 ============================

    @Test
    public void 连着两个拨付年度未勾_挪往期_新册走不到那层() {
        plantBook("B-2024", 2024, 1);
        plantRow("B-2024", 1, 0, 0);   // 空悬至今：挪
        plantRow("B-2024", 2, 1, 0);   // 已收下：不挪
        plantBook("B-2026", 2026, 1);
        plantRow("B-2026", 1, 0, 0);   // 本年度的册：这道手续够不着

        int moved = service.moveStaleToPast(2026, "tester");
        assertEquals(1, moved, "只挪连着两个拨付年度还没着落的");

        List<TBarrPayRow> current = service.listCurrentRows("B-2024");
        assertEquals(1, current.size(), "现行名单不再列它");
        assertEquals(2, current.get(0).getRowNo());
        List<TBarrPayRow> past = service.listPastRows("B-2024");
        assertEquals(1, past.size(), "往期层照旧翻得着");
        assertEquals(1, past.get(0).getRowNo());
        assertEquals(1, service.listCurrentRows("B-2026").size(), "新册的行走不到往期层");
    }

    // ============================ 并发撞键 ============================

    @Test
    public void 并发撞上活行唯一键_整趟按照面收场() {
        // 另一趟已把册落库（查册头那一眼没看着，落行时撞个正着）
        plantBook("B-RACE", 2026, 1);
        plantRow("B-RACE", 1, 1, 0);
        plantRow("B-RACE", 2, 1, 0);
        // 头一遍查册头装没看着（并发窗口），第二遍起照实查
        final int[] calls = {0};
        doAnswer(inv -> {
            calls[0]++;
            if (calls[0] == 1) {
                return null;
            }
            return bookStore.stream()
                    .filter(b -> b.getDelFlag() != null && b.getDelFlag() == 0
                            && Objects.equals(b.getBatchNo(), inv.getArgument(0)))
                    .findFirst().orElse(null);
        }).when(bookDao).findLiveBook(anyString());

        List<PayBookForm.Row> rows = new ArrayList<>();
        rows.add(okRow("K-1"));
        rows.add(okRow("K-2"));
        PayBookReceipt rc = service.intakeBook(form("B-RACE", rows), "tester");
        assertEquals(PayBookReceipt.OUTCOME_DUPLICATE, rc.getOutcome(), "撞键按另一趟已落认，不重复入库");
        assertEquals(2, liveRows("B-RACE").size(), "并发的两回只落一回");
        assertEquals(2, rc.getReportedRows());
        assertEquals(2, rc.getLandedRows());
    }
}
