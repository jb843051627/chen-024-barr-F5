package com.fc.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

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
import com.fc.v2.service.impl.TBarrPayBookServiceImpl;

/**
 * 核销册一道门核心规矩单测（不起 Spring、不连库；mapper 与回库口全打桩）。
 * 盯：册头先点行数、超限整册退、两数缺任一个不许凑、零负/超封顶/代号空或超长/缺影像送回、
 * 没对上未勾、同户两行各走各的、行次系统排、回执三数回库点、第二遍照面不添不减、
 * 撤册重递另起一轮、并发撞键当照面、对账点名、空册不出核讫、往期层两个拨付年度。
 */
public class PayBookServiceTest {

    private TBarrPayBookServiceImpl service;
    private TBarrPayRowMapper rowMapper;
    private TBarrPayBookMapper bookMapper;
    private TBarrSiteCardMapper cardMapper;
    private BarrPayBookDao bookDao;

    private final List<TBarrPayRow> rowStore = new ArrayList<>();
    private final List<TBarrPayBook> bookStore = new ArrayList<>();
    private final List<TBarrPayCap> capStore = new ArrayList<>();
    private final List<TBarrSiteCard> cardStore = new ArrayList<>();

    @BeforeEach
    public void setUp() throws Exception {
        service = new TBarrPayBookServiceImpl();
        rowMapper = mock(TBarrPayRowMapper.class);
        bookMapper = mock(TBarrPayBookMapper.class);
        cardMapper = mock(TBarrSiteCardMapper.class);
        bookDao = mock(BarrPayBookDao.class);
        setField(service, "barrPayRowMapper", rowMapper);
        setField(service, "barrPayBookMapper", bookMapper);
        setField(service, "barrSiteCardMapper", cardMapper);
        setField(service, "barrPayBookDao", bookDao);

        // 模拟库：行撞 (batch_no,round_no,row_no) 唯一键要炸（删了的旧行也占键）；
        // 册头撞 (book_no,round_no) 唯一键要炸。统一 doAnswer 打桩，免得重打桩时旧 Answer 先被 null 调一遍。
        doAnswer(inv -> {
            TBarrPayRow r = inv.getArgument(0);
            boolean dup = rowStore.stream().anyMatch(x -> sameBatchRoundRow(x, r));
            if (dup) {
                throw new DuplicateKeyException("uk_batch_round_row");
            }
            if (r.getId() == null) {
                r.setId((long) (rowStore.size() + 1));
            }
            rowStore.add(r);
            return 1;
        }).when(rowMapper).insert(any());

        doAnswer(inv -> {
            TBarrPayBook b = inv.getArgument(0);
            boolean dup = bookStore.stream().anyMatch(x ->
                    Objects.equals(x.getBookNo(), b.getBookNo()) && Objects.equals(x.getRoundNo(), b.getRoundNo()));
            if (dup) {
                throw new DuplicateKeyException("uk_book_round");
            }
            if (b.getId() == null) {
                b.setId((long) (bookStore.size() + 1));
            }
            bookStore.add(b);
            return 1;
        }).when(bookMapper).insert(any());

        doAnswer(inv -> {
            TBarrPayBook b = inv.getArgument(0);
            for (int i = 0; i < bookStore.size(); i++) {
                if (Objects.equals(bookStore.get(i).getId(), b.getId())) {
                    bookStore.set(i, b);
                    return 1;
                }
            }
            return 0;
        }).when(bookMapper).updateById(any());

        doAnswer(inv -> new ArrayList<>(cardStore)).when(cardMapper).selectList(any());

        // 回库点账口：全从内存库现点，与库里那一遍是同一回算的账
        doAnswer(inv -> bookStore.stream()
                .filter(b -> Objects.equals(b.getBookNo(), inv.getArgument(0)) && Objects.equals(b.getStatus(), 0))
                .max(Comparator.comparing(TBarrPayBook::getRoundNo)).orElse(null)
        ).when(bookDao).findCurrentBook(any());
        doAnswer(inv -> bookStore.stream()
                .filter(b -> Objects.equals(b.getBookNo(), inv.getArgument(0))
                        && Objects.equals(b.getRoundNo(), inv.getArgument(1)))
                .findFirst().orElse(null)
        ).when(bookDao).findBook(any(), any());
        doAnswer(inv -> bookStore.stream()
                .filter(b -> Objects.equals(b.getBookNo(), inv.getArgument(0)))
                .map(TBarrPayBook::getRoundNo).filter(Objects::nonNull)
                .max(Integer::compareTo).orElse(0)
        ).when(bookDao).maxRound(any());
        doAnswer(inv -> (int) rowStore.stream().filter(r -> inRound(r, inv.getArgument(0), inv.getArgument(1)))
                .count()).when(bookDao).countRound(any(), any());
        doAnswer(inv -> (int) rowStore.stream()
                .filter(r -> inRound(r, inv.getArgument(0), inv.getArgument(1))
                        && Objects.equals(r.getStatus(), inv.getArgument(2)))
                .count()).when(bookDao).countRoundStatus(any(), any(), any());
        doAnswer(inv -> rowStore.stream().filter(r -> inRound(r, inv.getArgument(0), inv.getArgument(1)))
                .sorted(Comparator.comparing(TBarrPayRow::getRowNo)).collect(Collectors.toList())
        ).when(bookDao).listRoundRows(any(), any());
        doAnswer(inv -> rowStore.stream()
                .filter(r -> inRound(r, inv.getArgument(0), inv.getArgument(1))
                        && Objects.equals(r.getStatus(), inv.getArgument(2)))
                .sorted(Comparator.comparing(TBarrPayRow::getRowNo)).collect(Collectors.toList())
        ).when(bookDao).listRoundStatusRows(any(), any(), any());
        doAnswer(inv -> rowStore.stream()
                .filter(r -> (r.getDelFlag() == null || r.getDelFlag() == 0)
                        && Objects.equals(r.getStatus(), 0)
                        && (r.getPastFlag() == null || r.getPastFlag() == 0))
                .collect(Collectors.toList())
        ).when(bookDao).listHangingCurrent();
        doAnswer(inv -> {
            String bookNo = inv.getArgument(0);
            return rowStore.stream()
                    .filter(r -> (r.getDelFlag() == null || r.getDelFlag() == 0)
                            && r.getPastFlag() != null && r.getPastFlag() == 1)
                    .filter(r -> bookNo == null || Objects.equals(r.getBatchNo(), bookNo))
                    .collect(Collectors.toList());
        }).when(bookDao).listPast(any());
        doAnswer(inv -> {
            Long id = inv.getArgument(0);
            for (TBarrPayRow r : rowStore) {
                if (Objects.equals(r.getId(), id)
                        && (r.getDelFlag() == null || r.getDelFlag() == 0)
                        && Objects.equals(r.getStatus(), 0)
                        && (r.getPastFlag() == null || r.getPastFlag() == 0)) {
                    r.setPastFlag(1);
                    return 1;
                }
            }
            return 0;
        }).when(bookDao).markPast(any(), any());
        doAnswer(inv -> {
            String bookNo = inv.getArgument(0);
            Integer round = inv.getArgument(1);
            int n = 0;
            for (TBarrPayRow r : rowStore) {
                if (Objects.equals(r.getBatchNo(), bookNo) && Objects.equals(r.getRoundNo(), round)
                        && (r.getDelFlag() == null || r.getDelFlag() == 0)) {
                    r.setDelFlag(1);
                    n++;
                }
            }
            return n;
        }).when(bookDao).withdrawRows(any(), any(), any(), any());
        doAnswer(inv -> capStore.stream()
                .filter(c -> Objects.equals(c.getStreetCode(), inv.getArgument(0)))
                .findFirst().orElse(null)
        ).when(bookDao).findCap(any());
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private boolean sameBatchRoundRow(TBarrPayRow a, TBarrPayRow b) {
        return Objects.equals(a.getBatchNo(), b.getBatchNo())
                && Objects.equals(a.getRoundNo(), b.getRoundNo())
                && Objects.equals(a.getRowNo(), b.getRowNo());
    }

    private boolean inRound(TBarrPayRow r, String bookNo, Integer round) {
        return Objects.equals(r.getBatchNo(), bookNo) && Objects.equals(r.getRoundNo(), round)
                && (r.getDelFlag() == null || r.getDelFlag() == 0);
    }

    // ============================ 件 ============================

    private TBarrPayRow row(String itemCode, String qty, String quali, String img) {
        TBarrPayRow r = new TBarrPayRow();
        r.setItemCode(itemCode);
        r.setQty(qty == null ? null : new BigDecimal(qty));
        r.setQualiNum(quali == null ? null : new BigDecimal(quali));
        r.setVoucherImg(img);
        return r;
    }

    /** 好行：代号、两个数、影像齐，额在封顶尖内 */
    private TBarrPayRow good(String code) {
        return row(code, "100", "2", "IMG-1");
    }

    private PayBookForm form(String bookNo, List<TBarrPayRow> rows) {
        PayBookForm f = new PayBookForm();
        f.setBookNo(bookNo);
        f.setStreetCode("BSJD");
        f.setSrcKind("STREET");
        f.setRows(rows);
        return f;
    }

    private TBarrSiteCard card(String cardNo) {
        TBarrSiteCard c = new TBarrSiteCard();
        c.setCardNo(cardNo);
        c.setDelFlag(0);
        return c;
    }

    private TBarrPayCap cap(String street, String qty) {
        TBarrPayCap c = new TBarrPayCap();
        c.setStreetCode(street);
        c.setCapQty(new BigDecimal(qty));
        return c;
    }

    private List<TBarrPayRow> rowsOf(TBarrPayRow... rows) {
        List<TBarrPayRow> list = new ArrayList<>();
        for (TBarrPayRow r : rows) {
            list.add(r);
        }
        return list;
    }

    private Date atYear(int year) {
        return Date.from(LocalDateTime.of(LocalDate.of(year, 6, 1), LocalTime.NOON)
                .atZone(ZoneId.systemDefault()).toInstant());
    }

    // ============================ 进门头一步：册头点行数 ============================

    @Test
    public void 册码空着_递不进来_一行一册都不落() {
        PayBookReceipt r1 = service.enterBook(null, "op");
        PayBookReceipt r2 = service.enterBook(form("  ", rowsOf(good("K-1"))), "op");
        assertFalse(r1.isEntered());
        assertFalse(r2.isEntered());
        assertTrue(r1.getMessage().contains("册码"));
        assertTrue(rowStore.isEmpty());
        assertTrue(bookStore.isEmpty());
    }

    @Test
    public void 压过上限_整册退回_库里不许留半行() {
        List<TBarrPayRow> tooMany = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            tooMany.add(good("K-" + i));
        }
        PayBookReceipt r = service.enterBook(form("BIG", tooMany), "op");
        assertTrue(r.isRejectedWhole());
        assertFalse(r.isEntered());
        assertTrue(r.getMessage().contains("整册退回"));
        assertTrue(rowStore.isEmpty(), "一行不入");
        assertTrue(bookStore.isEmpty(), "册头也不立");
    }

    @Test
    public void 册头那格写法与实际对不上_以实际落库的为准_照面记一笔() {
        cardStore.add(card("K-1"));
        PayBookForm f = form("B-H", rowsOf(good("K-1")));
        f.setDeclaredRows(5);
        PayBookReceipt r = service.enterBook(f, "op");
        assertTrue(r.isEntered());
        assertFalse(r.isDeclaredMatch(), "那格写5实际递1，照面不合");
        assertEquals(1, r.getReported(), "作准的是实际落库点出来的数");
        assertTrue(r.getFaceNotes().get(0).contains("以实际"));
        assertEquals(5, bookStore.get(0).getDeclaredRows(), "那格写法照存，只照面不翻账");
    }

    // ============================ 逐行判收判退 ============================

    @Test
    public void 两个数缺任一个_送回_不许替它凑() {
        cardStore.add(card("K-1"));
        cardStore.add(card("K-2"));
        cardStore.add(card("K-3"));
        PayBookReceipt r = service.enterBook(form("B1", rowsOf(
                row("K-1", "100", null, "IMG"),   // 处数缺：送回
                row("K-2", null, "2", "IMG"),     // 请领额缺：送回
                good("K-3"))), "op");             // 齐：收下
        assertEquals(3, r.getReported());
        assertEquals(1, r.getLanded());
        assertEquals(2, r.getReturned());
        assertEquals("验收合格处数缺着，两个数得一块儿递，不许替它凑", rowStore.get(0).getReturnReason());
        assertEquals("请领额缺着，两个数得一块儿递，不许替它凑", rowStore.get(1).getReturnReason());
        assertEquals(1, rowStore.get(2).getStatus().intValue(), "齐整的收下");
    }

    @Test
    public void 零负额_代号空_代号长过尺_各按各送回() {
        PayBookReceipt r = service.enterBook(form("B2", rowsOf(
                row("K-1", "0", "1", "IMG"),
                row("K-2", "-5", "1", "IMG"),
                row("  ", "10", "1", "IMG"),
                row(null, "10", "1", "IMG"),
                row("K" + repeat('x', 64), "10", "1", "IMG"))), "op");
        assertEquals(0, r.getLanded());
        assertEquals(5, r.getReturned());
        assertTrue(rowStore.get(0).getReturnReason().contains("零或负"));
        assertTrue(rowStore.get(1).getReturnReason().contains("零或负"));
        assertTrue(rowStore.get(2).getReturnReason().contains("空着"));
        assertTrue(rowStore.get(3).getReturnReason().contains("空着"));
        assertTrue(rowStore.get(4).getReturnReason().contains("那一把尺"));
    }

    private String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    @Test
    public void 高过当地封顶线_送回_没立线的街不卡() {
        capStore.add(cap("BSJD", "5000"));
        cardStore.add(card("K-1"));
        cardStore.add(card("K-2"));
        cardStore.add(card("K-3"));
        // 白沙街：封顶尖 5000，压线收、冒头送
        PayBookReceipt r1 = service.enterBook(form("B3", rowsOf(
                row("K-1", "5000", "1", "IMG"),
                row("K-2", "5000.01", "1", "IMG"))), "op");
        assertEquals(1, r1.getLanded());
        assertEquals(1, r1.getReturned());
        assertTrue(rowStore.get(1).getReturnReason().contains("封顶线"));
        // 没立线的街：再大的额也不在这格卡
        PayBookForm f2 = form("B4", rowsOf(row("K-3", "999999", "1", "IMG")));
        f2.setStreetCode("WJJD");
        PayBookReceipt r2 = service.enterBook(f2, "op");
        assertEquals(1, r2.getLanded(), "没有行=这条街不卡");
        assertEquals(0, r2.getReturned());
    }

    @Test
    public void 影像没随册来_按没备齐送回_缘故写明缺的是影像() {
        cardStore.add(card("K-1"));
        cardStore.add(card("K-2"));
        PayBookReceipt r = service.enterBook(form("B5", rowsOf(
                row("K-1", "10", "1", null),
                row("K-2", "10", "1", "  "))), "op");
        assertEquals(2, r.getReturned());
        assertTrue(rowStore.get(0).getReturnReason().contains("影像"));
        assertTrue(rowStore.get(1).getReturnReason().contains("影像"));
    }

    @Test
    public void 认户只认进度卡代号_没对上未勾_对上收下() {
        cardStore.add(card("K-1"));
        PayBookReceipt r = service.enterBook(form("B6", rowsOf(
                good("K-1"),      // 对上：收下
                good("K-2"),      // 没对上：未勾挂着
                good("K-202"))), "op"); // 代号念得不全：一律按没对上办
        assertEquals(3, r.getLanded(), "收下与未勾都算落地");
        assertEquals(0, r.getReturned());
        assertEquals(1, rowStore.get(0).getStatus());
        assertEquals(0, rowStore.get(1).getStatus());
        assertEquals(0, rowStore.get(2).getStatus());
        assertEquals(2, r.getHangingRows().size(), "空悬的行随回执点名");
        assertEquals(2, r.getHangingRows().get(0).getRowNo());
    }

    @Test
    public void 同户一册占两行_各走各的_谁也不盖谁() {
        cardStore.add(card("K-1"));
        PayBookReceipt r = service.enterBook(form("B7", rowsOf(
                good("K-1"),                    // 行1 收下
                row("K-1", "0", "1", "IMG"),    // 行2 送回（额零）
                good("K-1"))), "op");           // 行3 收下
        assertEquals(1, rowStore.get(0).getStatus(), "收下头一行不捎带第二行");
        assertEquals(2, rowStore.get(1).getStatus());
        assertEquals(1, rowStore.get(2).getStatus(), "送回第二行不牵连第三行");
        assertEquals(2, r.getLanded());
        assertEquals(1, r.getReturned());
    }

    @Test
    public void 行次系统排_排定不再挪_送回行带纸面行次出门() {
        cardStore.add(card("K-1"));
        PayBookReceipt r = service.enterBook(form("B8", rowsOf(
                good("K-1"), row("K-2", "0", "1", "IMG"), good("K-1"), row(null, "1", "1", "IMG"))), "op");
        for (int i = 0; i < 4; i++) {
            assertEquals(i + 1, rowStore.get(i).getRowNo(), "行次按落库先后排上");
        }
        assertEquals(2, r.getReturnedRows().size());
        assertEquals(2, r.getReturnedRows().get(0).getRowNo(), "退回的行带纸面行次");
        assertEquals(4, r.getReturnedRows().get(1).getRowNo());
        // 第二天再翻还是原行次，与街道纸面那份报册对得上号
        List<TBarrPayRow> again = service.listBookRows("B8");
        assertEquals(4, again.size());
        assertEquals(2, again.get(1).getRowNo());
        assertEquals(2, again.get(1).getStatus(), "去向也不许被谁改");
    }

    // ============================ 回执与空册 ============================

    @Test
    public void 回执三数_回库点出_退回加落地等于原报() {
        cardStore.add(card("K-1"));
        PayBookReceipt r = service.enterBook(form("B9", rowsOf(
                good("K-1"),                    // 收下
                good("K-X"),                    // 未勾
                row("K-1", "0", "1", "IMG"),    // 送回
                row("K-1", "1", null, "IMG"),   // 送回
                row("K-1", "1", "1", null))), "op"); // 送回
        assertEquals(5, r.getReported());
        assertEquals(2, r.getLanded());
        assertEquals(3, r.getReturned());
        assertEquals(r.getReported(), r.getLanded() + r.getReturned(), "退回与落地并起来等于原报");
        assertEquals("HJ-B9-R1", r.getCheckNo());
        // 册头那本与逐行那套说的是同一批行
        TBarrPayBook book = bookStore.get(0);
        assertEquals(5, book.getReportedRows());
        assertEquals(2, book.getLandedRows());
        assertEquals(3, book.getReturnedRows());
        assertEquals(3, r.getReturnedRows().size(), "没落成的行随回执带出行次");
    }

    @Test
    public void 空册_一行都没落着_回执只写空册不出核讫() {
        // 一行都没递
        PayBookReceipt r1 = service.enterBook(form("E1", new ArrayList<>()), "op");
        assertTrue(r1.isEmptyBook());
        assertEquals("空册", r1.getMessage());
        assertNull(r1.getCheckNo(), "核讫是给有行数的册备的");
        assertEquals(0, bookStore.get(0).getLandedRows());
        // 递了但全送回
        PayBookReceipt r2 = service.enterBook(form("E2", rowsOf(row("K-1", "0", "1", "IMG"))), "op");
        assertTrue(r2.isEmptyBook());
        assertEquals("空册", r2.getMessage());
        assertNull(r2.getCheckNo());
        assertEquals(1, r2.getReturned());
    }

    // ============================ 第二遍照面 / 撤册 / 并发 ============================

    @Test
    public void 同一册码第二遍递来_只照面_不添一行不减一行() {
        cardStore.add(card("K-1"));
        cardStore.add(card("K-2"));
        service.enterBook(form("C1", rowsOf(good("K-1"), good("K-2"))), "op");
        assertEquals(2, rowStore.size());

        // 第二遍原样再递：照面对得上
        PayBookReceipt again = service.enterBook(form("C1", rowsOf(good("K-1"), good("K-2"))), "op");
        assertTrue(again.isDuplicate());
        assertFalse(again.isEntered());
        assertEquals(2, rowStore.size(), "不添一行");
        assertEquals(2, again.getLanded(), "头一遍落成的去向盖不掉");
        assertTrue(again.getFaceNotes().isEmpty(), "原样再递一册对得上");

        // 第二遍递来的同库里有出入：照面点名，以库里为准
        PayBookReceipt diff = service.enterBook(form("C1", rowsOf(
                row("K-1", "999", "2", "IMG"),  // 额与库里对不上
                good("K-2"),
                good("K-3"))), "op");           // 多出一行
        assertTrue(diff.isDuplicate());
        assertEquals(2, rowStore.size(), "照面不添不减");
        assertEquals(2, diff.getFaceNotes().size());
        assertTrue(diff.getFaceNotes().get(0).contains("行次1"));
        assertTrue(diff.getFaceNotes().get(1).contains("不添"));
    }

    @Test
    public void 撤册手续办完_同册码才许重新递_另起一轮旧轮留底() {
        cardStore.add(card("K-1"));
        service.enterBook(form("C2", rowsOf(good("K-1"), row("K-1", "0", "1", "IMG"))), "op");

        // 没撤就递：只照面
        PayBookReceipt face = service.enterBook(form("C2", rowsOf(good("K-1"))), "op");
        assertTrue(face.isDuplicate());
        assertEquals(2, rowStore.size());

        // 撤册：在册轮置已撤、行置删留底
        String msg = service.withdrawBook("C2", "op");
        assertTrue(msg.contains("已撤"));
        assertEquals(1, bookStore.get(0).getStatus());
        assertTrue(rowStore.stream().allMatch(r -> r.getDelFlag() == 1), "旧轮行置删留底，不真抽走");

        // 重新递：另起一轮，两轮分开放
        PayBookReceipt r2 = service.enterBook(form("C2", rowsOf(good("K-1"))), "op");
        assertTrue(r2.isEntered());
        assertEquals(2, r2.getRoundNo().intValue());
        assertEquals(3, rowStore.size(), "新轮行照落，旧轮留底");
        assertEquals(2, rowStore.get(2).getRoundNo().intValue());
        assertEquals("HJ-C2-R2", r2.getCheckNo());

        // 第二轮也撤掉后，册上没有在册的轮：再撤不动库
        service.withdrawBook("C2", "op");
        String noop = service.withdrawBook("C2", "op");
        assertTrue(noop.contains("没有在册的轮"));
        // 回执翻看：撤了的册照旧翻得着，回执是撤前那一趟点出来的
        PayBookReceipt receipt = service.receiptOf("C2");
        assertEquals(2, receipt.getRoundNo().intValue());
        assertEquals(1, receipt.getLanded());
        assertTrue(receipt.getMessage().contains("已撤"));
    }

    @Test
    public void 并发撞上册头唯一键_后到一趟当照面不炸() {
        // 另一趟已把册头与一行落进库里；这趟查的时候却还看着空（两趟都查空、各落各的）
        TBarrPayBook standing = new TBarrPayBook();
        standing.setId(9L);
        standing.setBookNo("C3");
        standing.setRoundNo(1);
        standing.setStatus(0);
        standing.setReportedRows(1);
        standing.setLandedRows(1);
        standing.setReturnedRows(0);
        standing.setCheckNo("HJ-C3-R1");
        bookStore.add(standing);
        cardStore.add(card("K-1"));
        TBarrPayRow landed = good("K-1");
        landed.setId(7L);
        landed.setBatchNo("C3");
        landed.setRoundNo(1);
        landed.setRowNo(1);
        landed.setStatus(1);
        landed.setDelFlag(0);
        rowStore.add(landed);
        // 重打桩：这趟查册头/查轮次都看着空，落册头时才会撞上 uk_book_round
        doAnswer(inv -> null).when(bookDao).findCurrentBook(any());
        doAnswer(inv -> 0).when(bookDao).maxRound(any());

        PayBookReceipt r = service.enterBook(form("C3", rowsOf(good("K-1"))), "op");
        assertTrue(r.isDuplicate(), "撞册头键按照面认，不把异常甩出去");
        assertEquals(1, rowStore.size(), "不添一行");
        assertEquals(1, r.getLanded());
    }

    // ============================ 对账点名 ============================

    @Test
    public void 对账_行上去向与回执三数彼此对上_合不上点名() {
        cardStore.add(card("K-1"));
        service.enterBook(form("A1", rowsOf(
                good("K-1"), good("K-X"), row("K-1", "0", "1", "IMG"))), "op");

        // 没人动过：两处合得上
        PayBookAudit ok = service.audit("A1");
        assertTrue(ok.isFound());
        assertTrue(ok.isConsistent(), "回执三数与逐行点出来的彼此对上");
        assertTrue(ok.getMismatches().isEmpty());

        // 账外被人挪了一行（收下→送回且缘故空着）：两处合不上，点名
        rowStore.get(0).setStatus(2);
        PayBookAudit bad = service.audit("A1");
        assertFalse(bad.isConsistent());
        assertTrue(bad.getMismatches().stream().anyMatch(m -> m.contains("落地")), "落地数合不上要报");
        assertTrue(bad.getMismatches().stream().anyMatch(m -> m.contains("退回")), "退回数合不上要报");
        assertTrue(bad.getMismatches().stream().anyMatch(m -> m.contains("行次1")), "合不上的那几行点名报出来");

        // 查无此册
        assertFalse(service.audit("NO-SUCH").isFound());
    }

    // ============================ 往期层 ============================

    @Test
    public void 未勾长压两个拨付年度_挪往期_现行名单不再列_往期照旧翻得着() {
        // 2024 年落的未勾行：到 2026 已连着两个拨付年度没着落
        rowStore.add(hanging(1L, "OLD-1", 2024));
        // 2025 年落的未勾行：才压了一个年度，留着
        rowStore.add(hanging(2L, "MID-1", 2025));
        // 2024 年落的收下行：去向有着落，不挪
        TBarrPayRow taken = hanging(3L, "OLD-2", 2024);
        taken.setStatus(1);
        rowStore.add(taken);
        // 落库时刻空着的旧行：没凭据，不挪
        TBarrPayRow noTime = hanging(4L, "OLD-3", 2024);
        noTime.setCreateTime(null);
        rowStore.add(noTime);

        int moved = service.movePastDue(2026, atYear(2026));
        assertEquals(1, moved, "只挪连着两个拨付年度还没着落的未勾行");
        assertEquals(1, rowStore.get(0).getPastFlag().intValue());
        assertEquals(0, rowStore.get(1).getPastFlag().intValue());
        assertEquals(0, rowStore.get(2).getPastFlag().intValue());
        assertEquals(0, rowStore.get(3).getPastFlag().intValue(), "落库时刻空着的没凭据，不挪");

        // 现行名单不再列它，往期那层照旧翻得着
        List<TBarrPayRow> current = service.listHangingCurrent();
        assertEquals(2, current.size());
        assertTrue(current.stream().noneMatch(r -> r.getPastFlag() != null && r.getPastFlag() == 1));
        List<TBarrPayRow> past = service.listPast(null);
        assertEquals(1, past.size());
        assertEquals("OLD-1", past.get(0).getItemCode());
        // 这条路只朝旧册开：新落的行走不到它跟前
        assertTrue(rowStore.stream().filter(r -> r.getPastFlag() != null && r.getPastFlag() == 1)
                .allMatch(r -> r.getCreateTime().before(atYear(2025))));
    }

    private TBarrPayRow hanging(long id, String code, int createYear) {
        TBarrPayRow r = good(code);
        r.setId(id);
        r.setBatchNo("Y" + createYear);
        r.setRoundNo(1);
        r.setRowNo(1);
        r.setStatus(0);
        r.setDelFlag(0);
        r.setPastFlag(0);
        r.setCreateTime(atYear(createYear));
        return r;
    }
}
