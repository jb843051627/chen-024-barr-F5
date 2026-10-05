package com.fc.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
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
import com.fc.v2.model.custom.JointSignView;
import com.fc.v2.service.impl.TBarrJointSignBillServiceImpl;

/**
 * 联户验收签认单（会签版）核心规矩单测（不起 Spring、不连库；三张表用内存册子打桩）。
 * 盯五处：三口各管各的算齐、下一口几时亮、打回抹哪一截、签讫后还容不容补笔、
 * 计数与名册是不是同一回算。已签数一律照落笔留痕数，测试里也没有往里填的路。
 */
public class JointSignBillServiceTest {

    private TBarrJointSignBillServiceImpl service;
    private TBarrJointSignBillMapper billMapper;
    private TBarrJointSignLineMapper lineMapper;
    private TBarrAcceptRecordMapper acceptRecordMapper;

    private final Map<Long, TBarrJointSignBill> bills = new LinkedHashMap<>();
    private final List<TBarrJointSignLine> lines = new ArrayList<>();
    private final Map<Long, TBarrAcceptRecord> records = new LinkedHashMap<>();

    private long billSeq = 1000L;
    private long lineSeqId = 5000L;
    private boolean nextBillInsertDup = false;
    private boolean nextLineInsertDup = false;

    private static final Pattern EQ_COL =
            Pattern.compile("(?i)([a-z_][a-z0-9_]*)\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)}");

    @BeforeEach
    public void setUp() throws Exception {
        service = new TBarrJointSignBillServiceImpl();
        billMapper = mock(TBarrJointSignBillMapper.class);
        lineMapper = mock(TBarrJointSignLineMapper.class);
        acceptRecordMapper = mock(TBarrAcceptRecordMapper.class);
        setField(service, "billMapper", billMapper);
        setField(service, "lineMapper", lineMapper);
        setField(service, "acceptRecordMapper", acceptRecordMapper);

        when(billMapper.selectById(any())).thenAnswer(inv -> bills.get(inv.getArgument(0)));
        when(billMapper.selectByIdForUpdate(any())).thenAnswer(inv -> bills.get(inv.getArgument(0)));
        when(billMapper.selectOne(any())).thenAnswer(inv -> {
            List<TBarrJointSignBill> f = filterBills(inv.getArgument(0));
            return f.isEmpty() ? null : f.get(0);
        });
        when(billMapper.selectList(any())).thenAnswer(inv -> filterBills(inv.getArgument(0)));
        when(billMapper.insert(any())).thenAnswer(inv -> {
            TBarrJointSignBill b = inv.getArgument(0);
            if (nextBillInsertDup) {
                nextBillInsertDup = false;
                throw new DuplicateKeyException("uk_joint_accept");
            }
            if (b.getId() == null) {
                b.setId(++billSeq);
            }
            bills.put(b.getId(), b);
            return 1;
        });
        when(billMapper.updateById(any())).thenAnswer(inv -> {
            TBarrJointSignBill b = inv.getArgument(0);
            bills.put(b.getId(), b);
            return 1;
        });

        when(lineMapper.selectList(any())).thenAnswer(inv -> filterLines(inv.getArgument(0)));
        when(lineMapper.insert(any())).thenAnswer(inv -> {
            TBarrJointSignLine l = inv.getArgument(0);
            if (nextLineInsertDup) {
                nextLineInsertDup = false;
                throw new DuplicateKeyException("uk_joint_line_seq");
            }
            if (l.getId() == null) {
                l.setId(++lineSeqId);
            }
            lines.add(l);
            return 1;
        });
        when(lineMapper.updateById(any())).thenAnswer(inv -> 1);

        when(acceptRecordMapper.selectById(any())).thenAnswer(inv -> records.get(inv.getArgument(0)));

        seedAccept(100L, "YS-20261005-0001");
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private void seedAccept(Long id, String acceptNo) {
        TBarrAcceptRecord r = new TBarrAcceptRecord();
        r.setId(id);
        r.setAcceptNo(acceptNo);
        r.setGroupNo("祥和楼组1栋");
        r.setArchId(0L);
        r.setArchNo("DC00");
        r.setArchName("浈县区白沙街道·祥和楼组片区");
        r.setArchKind("小区楼组");
        r.setStreetCode("BSJD");
        r.setStreetName("白沙街道");
        r.setSiteCount(3);
        r.setHolder("seed");
        records.put(id, r);
    }

    /** 照 Wrapper 里等值条件过滤内存册子：按 SQL 段里列出现的先后与同名占位参数配对 */
    @SuppressWarnings("rawtypes")
    private List<Object[]> eqPairs(Wrapper<?> w) {
        List<Object[]> pairs = new ArrayList<>();
        AbstractWrapper aw = (AbstractWrapper) w;
        Matcher m = EQ_COL.matcher(w.getSqlSegment());
        while (m.find()) {
            String col = m.group(1).toLowerCase();
            Object val = aw.getParamNameValuePairs().get(m.group(2));
            pairs.add(new Object[]{col, val});
        }
        return pairs;
    }

    private List<TBarrJointSignBill> filterBills(Wrapper<?> w) {
        List<TBarrJointSignBill> out = new ArrayList<>();
        for (TBarrJointSignBill b : bills.values()) {
            if (w == null || matchesBill(b, eqPairs(w))) {
                out.add(b);
            }
        }
        return out;
    }

    private boolean matchesBill(TBarrJointSignBill b, List<Object[]> pairs) {
        for (Object[] p : pairs) {
            String col = (String) p[0];
            Object v = p[1];
            if ("accept_id".equals(col)) {
                if (v == null ? b.getAcceptId() != null : !v.toString().equals(String.valueOf(b.getAcceptId()))) {
                    return false;
                }
            } else if ("id".equals(col)) {
                if (!v.toString().equals(String.valueOf(b.getId()))) {
                    return false;
                }
            }
        }
        return true;
    }

    private List<TBarrJointSignLine> filterLines(Wrapper<?> w) {
        List<TBarrJointSignLine> out = new ArrayList<>();
        for (TBarrJointSignLine l : lines) {
            if (w == null || matchesLine(l, eqPairs(w))) {
                out.add(l);
            }
        }
        return out;
    }

    private boolean matchesLine(TBarrJointSignLine l, List<Object[]> pairs) {
        for (Object[] p : pairs) {
            String col = (String) p[0];
            int iv = ((Number) p[1]).intValue();
            switch (col) {
                case "bill_id":
                    if (l.getBillId() == null || l.getBillId() != ((Number) p[1]).longValue()) {
                        return false;
                    }
                    break;
                case "node_no":
                    if (nz(l.getNodeNo()) != iv) {
                        return false;
                    }
                    break;
                case "round_no":
                    if (nz(l.getRoundNo()) != iv) {
                        return false;
                    }
                    break;
                case "valid_flag":
                    if (nz(l.getValidFlag()) != iv) {
                        return false;
                    }
                    break;
                default:
                    break;
            }
        }
        return true;
    }

    private int nz(Integer x) {
        return x == null ? 0 : x;
    }

    // ============================ 帮手 ============================

    private TBarrJointSignBill open() {
        return service.openFromAccept(100L, "wang");
    }

    private JointSignForm form(Long billId, Integer nodeNo, String seat, String signer, String verdict) {
        JointSignForm f = new JointSignForm();
        f.setBillId(String.valueOf(billId));
        f.setNodeNo(nodeNo);
        f.setSeatKey(seat);
        f.setSigner(signer);
        f.setVerdict(verdict);
        return f;
    }

    private JointSignView sign(Long billId, Integer nodeNo, String seat, String signer, String verdict) {
        return service.sign(form(billId, nodeNo, seat, signer, verdict), "wang");
    }

    private JointSignView.NodeView node(JointSignView v, int nodeNo) {
        return v.getNodes().get(nodeNo);
    }

    private List<TBarrJointSignLine> validLines(long billId, int nodeNo, int round) {
        List<TBarrJointSignLine> out = new ArrayList<>();
        for (TBarrJointSignLine l : lines) {
            if (l.getBillId() == billId && nz(l.getNodeNo()) == nodeNo && nz(l.getRoundNo()) == round
                    && nz(l.getValidFlag()) == TBarrJointSignLine.VALID) {
                out.add(l);
            }
        }
        return out;
    }

    private List<TBarrJointSignLine> voidLines(long billId, int nodeNo, int round) {
        List<TBarrJointSignLine> out = new ArrayList<>();
        for (TBarrJointSignLine l : lines) {
            if (l.getBillId() == billId && nz(l.getNodeNo()) == nodeNo && nz(l.getRoundNo()) == round
                    && nz(l.getValidFlag()) == TBarrJointSignLine.VOID) {
                out.add(l);
            }
        }
        return out;
    }

    /** 三口走完一张签讫单：住户可 → 施工可、第三方可 → 区可、市可 */
    private TBarrJointSignBill happyFinish() {
        TBarrJointSignBill b = open();
        sign(b.getId(), 0, JointSignRuleBook.SEAT_HOUSEHOLD, "住户周", "可");
        sign(b.getId(), 1, JointSignRuleBook.SEAT_BUILDER, "施工钱", "可");
        sign(b.getId(), 1, JointSignRuleBook.SEAT_ASSESSOR, "评估孙", "可");
        sign(b.getId(), 2, JointSignRuleBook.SEAT_DISTRICT, "区残联李", "可");
        sign(b.getId(), 2, JointSignRuleBook.SEAT_CITY, "市残联赵", "可");
        return b;
    }

    // ============================ 一、来路 ============================

    @Test
    public void 单只能由验收记录带出_记录上字段直接沿用() {
        TBarrJointSignBill b = open();
        assertNotNull(b.getId());
        assertEquals("QRB-20261005-0001", b.getBillNo(), "单号由受理簿记录号带出");
        assertEquals(100L, b.getAcceptId());
        assertEquals("YS-20261005-0001", b.getAcceptNo());
        assertEquals("祥和楼组1栋", b.getGroupNo());
        assertEquals("DC00", b.getArchNo());
        assertEquals("BSJD", b.getStreetCode());
        assertEquals(3, b.getSiteCount());
        assertEquals(0, b.getNodeNo());
        assertEquals(1, b.getRoundNo());
        assertEquals(TBarrJointSignBill.STATUS_RUNNING, b.getStatus());
    }

    @Test
    public void 同一场验收只带得出一张_再带一回原样回那张() {
        TBarrJointSignBill b1 = open();
        TBarrJointSignBill b2 = open();
        assertEquals(b1.getId(), b2.getId());
        assertEquals(1, bills.size(), "不许另起第二张");
    }

    @Test
    public void 并发两回带单撞唯一键_后到一趟回同一张不另起() {
        open();
        nextBillInsertDup = true;
        TBarrJointSignBill again = open();
        assertEquals(1, bills.size());
        assertEquals(bills.keySet().iterator().next(), again.getId());
    }

    @Test
    public void 受理簿翻不着_带不出单() {
        JointSignRuleException e = assertThrows(JointSignRuleException.class,
                () -> service.openFromAccept(999L, "wang"));
        assertTrue(e.getMessage().contains("受理簿"));
        assertThrows(JointSignRuleException.class, () -> service.openFromAccept(null, "wang"),
                "验收记录空着也不许凭空带单");
    }

    // ============================ 二、三、三口算齐 ============================

    @Test
    public void 第一口一名即过_谁先落笔哪一口算过_挪紧邻下一口() {
        TBarrJointSignBill b = open();
        JointSignView v = sign(b.getId(), 0, JointSignRuleBook.SEAT_HOUSEHOLD, "住户周", "可");
        assertEquals(1, v.getCurrentNode(), "一笔点齐即挪第二口");
        assertTrue(node(v, 0).isComplete());
        assertEquals(1, node(v, 0).getSignedCount(), "已签数是数出来的回显");
        assertTrue(node(v, 1).isLit(), "只有紧邻下一口亮");
        assertFalse(node(v, 2).isLit());
        assertEquals(0, node(v, 1).getSignedCount());
    }

    @Test
    public void 第二口等两名_先笔定调可_后一名不可不白写两行都留底() {
        TBarrJointSignBill b = open();
        sign(b.getId(), 0, JointSignRuleBook.SEAT_HOUSEHOLD, "住户周", "可");
        JointSignView first = sign(b.getId(), 1, JointSignRuleBook.SEAT_BUILDER, "施工钱", "可");
        assertEquals(1, first.getCurrentNode(), "只来一方，留在第二口");
        assertEquals(1, node(first, 1).getSignedCount());
        assertFalse(node(first, 1).isComplete());

        JointSignView second = sign(b.getId(), 1, JointSignRuleBook.SEAT_ASSESSOR, "评估孙", "不可");
        assertEquals(2, second.getCurrentNode(), "两名到齐按先笔（可）定调，挪第三口；后一笔照样累入、留底");
        assertEquals(2, node(second, 1).getSignedCount());
        assertTrue(node(second, 1).isComplete());
        assertEquals(2, node(second, 1).getLines().size(), "两方各写了什么屏上两行都看得见");
        assertEquals(2, validLines(b.getId(), 1, 1).size(), "后一名那笔不是白写，算数笔两笔都在");
    }

    @Test
    public void 第二口先笔不可_等满两名才按先笔定调打回() {
        TBarrJointSignBill b = open();
        sign(b.getId(), 0, JointSignRuleBook.SEAT_HOUSEHOLD, "住户周", "可");
        JointSignView one = sign(b.getId(), 1, JointSignRuleBook.SEAT_BUILDER, "施工钱", "不可");
        assertEquals(1, one.getCurrentNode(), "先笔不可也得等第二名，不提前收");
        assertEquals(1, node(one, 1).getSignedCount());

        JointSignView two = sign(b.getId(), 1, JointSignRuleBook.SEAT_ASSESSOR, "评估孙", "可");
        assertEquals(0, two.getCurrentNode(), "先笔定调不可：打回，单往回收一格到第一口");
        assertEquals(2, two.getRoundNo(), "打回重走另起一轮");
        assertEquals(2, voidLines(b.getId(), 1, 1).size(), "抹的是本口本轮两笔");
        assertTrue(validLines(b.getId(), 0, 1).size() == 1, "更早第一口旧签一笔不动");
        assertEquals(0, node(two, 0).getSignedCount(), "重走的第一口当前轮一笔没有，旧轮另摆");
    }

    @Test
    public void 头两口一席本轮不许落两笔() {
        TBarrJointSignBill b = open();
        sign(b.getId(), 0, JointSignRuleBook.SEAT_HOUSEHOLD, "住户周", "可");
        sign(b.getId(), 1, JointSignRuleBook.SEAT_BUILDER, "施工钱", "可");
        JointSignRuleException e = assertThrows(JointSignRuleException.class,
                () -> sign(b.getId(), 1, JointSignRuleBook.SEAT_BUILDER, "施工钱改主意", "不可"));
        assertTrue(e.getMessage().contains("已经落过笔"));
        assertEquals(1, validLines(b.getId(), 1, 1).size());
    }

    @Test
    public void 第三口两名同判_两可才签讫() {
        TBarrJointSignBill b = happyFinish();
        assertEquals(TBarrJointSignBill.STATUS_FINISHED, b.getStatus());
        assertEquals(2, b.getNodeNo());
        assertNotNull(b.getFinishedTime());

        JointSignView v = service.view(b.getId());
        assertEquals(TBarrJointSignBill.NODE_DONE, v.getCurrentNode(), "三口走完当前段位走到 3");
        assertTrue(node(v, 2).isComplete());
        assertFalse(node(v, 0).isLit() || node(v, 1).isLit() || node(v, 2).isLit(), "签讫后没有亮着的口");
    }

    @Test
    public void 第三口一可一不可_挂待定_不往下挪也不就此收() {
        TBarrJointSignBill b = open();
        sign(b.getId(), 0, JointSignRuleBook.SEAT_HOUSEHOLD, "住户周", "可");
        sign(b.getId(), 1, JointSignRuleBook.SEAT_BUILDER, "施工钱", "可");
        sign(b.getId(), 1, JointSignRuleBook.SEAT_ASSESSOR, "评估孙", "可");
        JointSignView split = sign(b.getId(), 2, JointSignRuleBook.SEAT_DISTRICT, "区残联李", "可");
        assertEquals(2, split.getCurrentNode());
        split = sign(b.getId(), 2, JointSignRuleBook.SEAT_CITY, "市残联赵", "不可");
        assertEquals(2, split.getCurrentNode(), "分歧挂待定，停在第三口不收");
        assertEquals(TBarrJointSignBill.STATUS_RUNNING, bills.get(b.getId()).getStatus());
        assertEquals(2, node(split, 2).getSignedCount());
        assertFalse(node(split, 2).isComplete());
        assertTrue(node(split, 2).isSplitPending(), "一可一不可挂待定");
    }

    @Test
    public void 待定后同席改口_旧笔supersede留底_新笔另落_两可签讫() {
        TBarrJointSignBill b = open();
        sign(b.getId(), 0, JointSignRuleBook.SEAT_HOUSEHOLD, "住户周", "可");
        sign(b.getId(), 1, JointSignRuleBook.SEAT_BUILDER, "施工钱", "可");
        sign(b.getId(), 1, JointSignRuleBook.SEAT_ASSESSOR, "评估孙", "可");
        sign(b.getId(), 2, JointSignRuleBook.SEAT_DISTRICT, "区残联李", "可");
        sign(b.getId(), 2, JointSignRuleBook.SEAT_CITY, "市残联赵", "不可");
        // 市残联同席改判可：旧笔标 supersede 留底，另落新笔
        JointSignView v = sign(b.getId(), 2, JointSignRuleBook.SEAT_CITY, "市残联赵", "可");
        assertEquals(TBarrJointSignBill.STATUS_FINISHED, bills.get(b.getId()).getStatus());
        assertEquals(3, node(v, 2).getLines().size(), "新旧三笔都摆得出");
        TBarrJointSignLine old = lines.stream()
                .filter(l -> l.getBillId() == b.getId() && JointSignRuleBook.SEAT_CITY.equals(l.getSeatKey())
                        && "不可".equals(l.getVerdict()))
                .findFirst().orElse(null);
        assertNotNull(old);
        assertEquals(TBarrJointSignLine.VOID, nz(old.getValidFlag()));
        assertEquals(TBarrJointSignLine.VOID_SUPERSEDE, old.getVoidReason(), "哪天改口有凭据");
        assertEquals(2, validLines(b.getId(), 2, 1).size());
    }

    @Test
    public void 第三口两不可_打回第二口重来() {
        TBarrJointSignBill b = open();
        sign(b.getId(), 0, JointSignRuleBook.SEAT_HOUSEHOLD, "住户周", "可");
        sign(b.getId(), 1, JointSignRuleBook.SEAT_BUILDER, "施工钱", "可");
        sign(b.getId(), 1, JointSignRuleBook.SEAT_ASSESSOR, "评估孙", "可");
        sign(b.getId(), 2, JointSignRuleBook.SEAT_DISTRICT, "区残联李", "不可");
        JointSignView v = sign(b.getId(), 2, JointSignRuleBook.SEAT_CITY, "市残联赵", "不可");
        assertEquals(1, v.getCurrentNode(), "两不可打回收一格到第二口");
        assertEquals(2, v.getRoundNo());
        assertEquals(2, voidLines(b.getId(), 2, 1).size(), "只抹第三口本轮两笔");
        assertEquals(1, validLines(b.getId(), 0, 1).size(), "第一口旧签不动");
        assertEquals(2, validLines(b.getId(), 1, 1).size(), "第二口旧签不动");

        // 重走：第二口第2轮再齐 → 第三口第2轮两可 → 签讫
        sign(b.getId(), 1, JointSignRuleBook.SEAT_BUILDER, "施工钱", "可");
        sign(b.getId(), 1, JointSignRuleBook.SEAT_ASSESSOR, "评估孙", "可");
        sign(b.getId(), 2, JointSignRuleBook.SEAT_DISTRICT, "区残联李", "可");
        JointSignView done = sign(b.getId(), 2, JointSignRuleBook.SEAT_CITY, "市残联赵", "可");
        assertEquals(TBarrJointSignBill.STATUS_FINISHED, bills.get(b.getId()).getStatus());
        assertEquals(2, validLines(b.getId(), 2, 2).size(), "本轮笔迹与上轮笔迹分开放");
        assertTrue(node(done, 2).isComplete());
    }

    // ============================ 四、进退 ============================

    @Test
    public void 越过第二口直落第三口_笔迹无效_单照旧停第二口() {
        TBarrJointSignBill b = open();
        sign(b.getId(), 0, JointSignRuleBook.SEAT_HOUSEHOLD, "住户周", "可");
        // 报文段号写第三口
        JointSignRuleException e = assertThrows(JointSignRuleException.class,
                () -> sign(b.getId(), 2, JointSignRuleBook.SEAT_DISTRICT, "区残联李", "可"));
        assertTrue(e.getMessage().contains("笔迹无效"));
        assertEquals(1, bills.get(b.getId()).getNodeNo());
        assertTrue(validLines(b.getId(), 2, 1).isEmpty(), "越口一笔不留");

        // 报文段号不写、席位却给第三口的，也不认
        JointSignRuleException e2 = assertThrows(JointSignRuleException.class,
                () -> service.sign(form(b.getId(), null, JointSignRuleBook.SEAT_CITY, "市残联赵", "可"), "wang"));
        assertTrue(e2.getMessage().contains("席位"));
    }

    @Test
    public void 报文段号与停口不符_席位自拟_结论乱写_一律不认() {
        TBarrJointSignBill b = open();
        assertThrows(JointSignRuleException.class,
                () -> sign(b.getId(), 1, JointSignRuleBook.SEAT_HOUSEHOLD, "住户周", "可"));
        assertThrows(JointSignRuleException.class,
                () -> sign(b.getId(), 0, "FOO", "谁", "可"), "报文自拟席位");
        assertThrows(JointSignRuleException.class,
                () -> sign(b.getId(), 0, JointSignRuleBook.SEAT_HOUSEHOLD, "住户周", "同意"), "只许可/不可");
        assertThrows(JointSignRuleException.class,
                () -> sign(b.getId(), 0, JointSignRuleBook.SEAT_HOUSEHOLD, "  ", "可"), "落笔人空着");
        assertEquals(0, bills.get(b.getId()).getNodeNo());
        assertTrue(lines.isEmpty(), "这些笔一笔都不许落");
    }

    @Test
    public void 第一口写不可_头一口前面没口_停头口_只抹本轮_轮次加一() {
        TBarrJointSignBill b = open();
        JointSignView v = sign(b.getId(), 0, JointSignRuleBook.SEAT_HOUSEHOLD, "住户周", "不可");
        assertEquals(0, v.getCurrentNode());
        assertEquals(2, v.getRoundNo());
        assertEquals(TBarrJointSignBill.STATUS_RUNNING, bills.get(b.getId()).getStatus());
        assertEquals(1, voidLines(b.getId(), 0, 1).size());
        assertEquals(0, validLines(b.getId(), 0, 2).size(), "重走还没补笔，当前轮已签数为 0");

        // 旧轮笔留底摆着、新轮另起：第2轮签可才点齐
        JointSignView again = sign(b.getId(), 0, JointSignRuleBook.SEAT_HOUSEHOLD, "住户周", "可");
        assertEquals(1, again.getCurrentNode());
        assertEquals(1, validLines(b.getId(), 0, 2).size());
        assertEquals(1, voidLines(b.getId(), 0, 1).size(), "上一轮旧笔照旧存底");
    }

    @Test
    public void 并发两笔各记各的次第_不并成一句_撞次第重数再落() {
        TBarrJointSignBill b = open();
        sign(b.getId(), 0, JointSignRuleBook.SEAT_HOUSEHOLD, "住户周", "可");
        sign(b.getId(), 1, JointSignRuleBook.SEAT_BUILDER, "施工钱", "可");
        // 第二口第二笔与并发一笔撞同一 line_seq 唯一键：重数一遍再落，不挤同一次第
        nextLineInsertDup = true;
        JointSignView v = sign(b.getId(), 1, JointSignRuleBook.SEAT_ASSESSOR, "评估孙", "可");
        List<TBarrJointSignLine> node1 = validLines(b.getId(), 1, 1);
        assertEquals(2, node1.size());
        assertEquals(1, node1.get(0).getLineSeq().intValue());
        assertEquals(2, node1.get(1).getLineSeq().intValue(), "两笔次第各是各的");
        assertEquals(2, v.getCurrentNode());
    }

    // ============================ 五、签讫以后 ============================

    @Test
    public void 签讫后一笔进不来_一字改不了_整张抽不走() {
        TBarrJointSignBill b = happyFinish();
        // 走口封死
        JointSignRuleException e = assertThrows(JointSignRuleException.class,
                () -> sign(b.getId(), 2, JointSignRuleBook.SEAT_CITY, "市残联赵", "不可"));
        assertTrue(e.getMessage().contains("已签讫"));
        // 同席改口也进不来
        assertThrows(JointSignRuleException.class,
                () -> sign(b.getId(), 2, JointSignRuleBook.SEAT_DISTRICT, "区残联李改", "不可"));
        // 服务压根没有撤单/改写的口子（接口上翻不出来）
        assertEquals(5, lines.size(), "三口 1+2+2=5 笔；签讫后库里笔数不再变动");
        assertEquals(TBarrJointSignBill.STATUS_FINISHED, bills.get(b.getId()).getStatus());
    }

    @Test
    public void 单翻不着_三条路都回话不吞声() {
        assertThrows(JointSignRuleException.class,
                () -> service.sign(form(404L, 0, JointSignRuleBook.SEAT_HOUSEHOLD, "x", "可"), "wang"));
        assertThrows(JointSignRuleException.class, () -> service.view(404L));
        assertThrows(JointSignRuleException.class, () -> service.audit(404L));
        assertNull(service.selectBillById(404L));
    }

    // ============================ 事后翻查 / 板面 ============================

    @Test
    public void 事后从第三口数回第一口_笔笔与受理簿合得拢() {
        TBarrJointSignBill b = happyFinish();
        JointSignAudit a = service.audit(b.getId());
        assertTrue(a.isLedgerFound());
        assertTrue(a.isMatched(), a.getProblems().toString());
        assertEquals("可", a.getFinalConclusion(), "第三口结论照落笔数出");
        assertEquals(3, a.getNodes().size());
        assertEquals(2, a.getNodes().get(0).getNodeNo(), "头一行是第三口");
        assertEquals(1, a.getNodes().get(1).getNodeNo());
        assertEquals(0, a.getNodes().get(2).getNodeNo());
        for (JointSignAudit.NodeCount nc : a.getNodes()) {
            assertEquals(nc.getNeedCount(), nc.getFinalValid(), "定案轮笔数与应签人数合得拢");
            assertTrue(nc.isComplete());
        }
    }

    @Test
    public void 受理簿翻不着_逐口核对合不拢() {
        TBarrJointSignBill b = happyFinish();
        records.remove(100L);
        JointSignAudit a = service.audit(b.getId());
        assertFalse(a.isLedgerFound());
        assertFalse(a.isMatched());
        assertTrue(a.getProblems().stream().anyMatch(p -> p.contains("受理簿")));
    }

    @Test
    public void 留痕笔的受理簿号对不上这场_核对挑出() {
        TBarrJointSignBill b = happyFinish();
        // 有人从库外改坏了一笔的受理簿号（系统内没有这条路）
        lines.get(0).setAcceptNo("YS-别的一场");
        JointSignAudit a = service.audit(b.getId());
        assertFalse(a.isMatched());
        assertTrue(a.getProblems().stream().anyMatch(p -> p.contains("受理簿号对不上")));
    }

    @Test
    public void 在签张数随带单加一随签讫减一_与逐口点数同一回算_名册同出() {
        // 甲：停第一口
        TBarrJointSignBill a = open();
        // 乙：走到第三口在签
        seedAccept(101L, "YS-20261005-0002");
        TBarrJointSignBill b = service.openFromAccept(101L, "wang");
        sign(b.getId(), 0, JointSignRuleBook.SEAT_HOUSEHOLD, "住户吴", "可");
        sign(b.getId(), 1, JointSignRuleBook.SEAT_BUILDER, "施工冯", "可");
        sign(b.getId(), 1, JointSignRuleBook.SEAT_ASSESSOR, "评估陈", "可");
        // 丙：签讫
        seedAccept(102L, "YS-20261005-0003");
        TBarrJointSignBill c = service.openFromAccept(102L, "wang");
        sign(c.getId(), 0, JointSignRuleBook.SEAT_HOUSEHOLD, "住户郑", "可");
        sign(c.getId(), 1, JointSignRuleBook.SEAT_BUILDER, "施工卫", "可");
        sign(c.getId(), 1, JointSignRuleBook.SEAT_ASSESSOR, "评估蒋", "可");
        sign(c.getId(), 2, JointSignRuleBook.SEAT_DISTRICT, "区残联沈", "可");
        sign(c.getId(), 2, JointSignRuleBook.SEAT_CITY, "市残联韩", "可");

        JointSignBoard board = service.board();
        assertEquals(2, board.getRunningTotal(), "一张带单加一、一张签讫减一");
        assertEquals(1, board.getAtNode()[0]);
        assertEquals(0, board.getAtNode()[1]);
        assertEquals(1, board.getAtNode()[2]);
        int sum = board.getAtNode()[0] + board.getAtNode()[1] + board.getAtNode()[2];
        assertEquals(board.getRunningTotal(), sum, "屏上总数与逐口点数本就是同一回算");
        assertEquals(1, board.getFinishedTotal());
        assertEquals(1, board.getRoster().size());
        JointSignBoard.RosterRow row = board.getRoster().get(0);
        assertEquals("QRB-20261005-0003", row.getBillNo());
        assertEquals("可", row.getFinalConclusion(), "第三口结论与名册同一回算，没有手填处");
        assertNotNull(row.getFinishedTime());
    }
}
