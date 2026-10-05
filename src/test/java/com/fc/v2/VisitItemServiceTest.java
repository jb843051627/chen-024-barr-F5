package com.fc.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import com.fc.v2.mapper.auto.TBarrArchMapper;
import com.fc.v2.mapper.auto.TBarrOwnerQuotaMapper;
import com.fc.v2.mapper.auto.TBarrVisitItemMapper;
import com.fc.v2.mapper.custom.BarrVisitRunDao;
import com.fc.v2.model.auto.TBarrArch;
import com.fc.v2.model.auto.TBarrOwnerQuota;
import com.fc.v2.model.auto.TBarrVisitItem;
import com.fc.v2.model.custom.VisitRunResult;
import com.fc.v2.service.impl.TBarrVisitItemServiceImpl;

/**
 * 质保与养护复检日跑核心规矩单测（不起 Spring、不连库；四个库口全打桩）。
 * 盯：窗口外一格不动只回话、止点压线、成事日数法、脏数据与两类天生办不成当场挂、
 * 同街同楼组分段不拆、定额满载挂递不动、五百封顶、双印回写、三数逐桩点、本轮无到期。
 */
public class VisitItemServiceTest {

    private TBarrVisitItemServiceImpl service;
    private TBarrVisitItemMapper itemMapper;
    private TBarrArchMapper archMapper;
    private TBarrOwnerQuotaMapper quotaMapper;
    private BarrVisitRunDao runDao;

    private final List<TBarrVisitItem> store = new ArrayList<>();
    private final List<TBarrArch> archStore = new ArrayList<>();
    private final List<TBarrOwnerQuota> quotaStore = new ArrayList<>();
    private final List<LocalDate> nonWorking = new ArrayList<>();
    private final List<Map<String, Object>> usedRows = new ArrayList<>();
    /** 逐桩回写流水：id -> 1递出 2递不动 */
    private final List<long[]> writeLog = new ArrayList<>();
    private final List<String> stallReasons = new ArrayList<>();
    private final List<String> deliveredBy = new ArrayList<>();

    private static final LocalDate D = LocalDate.of(2026, 10, 5); // 2026Q4

    @BeforeEach
    public void setUp() throws Exception {
        service = new TBarrVisitItemServiceImpl();
        itemMapper = mock(TBarrVisitItemMapper.class);
        archMapper = mock(TBarrArchMapper.class);
        quotaMapper = mock(TBarrOwnerQuotaMapper.class);
        runDao = mock(BarrVisitRunDao.class);
        setField(service, "barrVisitItemMapper", itemMapper);
        setField(service, "barrArchMapper", archMapper);
        setField(service, "barrOwnerQuotaMapper", quotaMapper);
        setField(service, "barrVisitRunDao", runDao);

        when(itemMapper.selectList(any())).thenAnswer((InvocationOnMock inv) ->
                store.stream().map(this::copy).collect(Collectors.toList()));
        when(archMapper.selectBatchIds(any())).thenAnswer(inv -> new ArrayList<>(archStore));
        when(quotaMapper.selectList(any())).thenAnswer(inv -> new ArrayList<>(quotaStore));
        when(runDao.listNonWorkingDays(any(), any())).thenAnswer(inv -> new ArrayList<>(nonWorking));
        when(runDao.countDeliveredByOwnerSeason()).thenAnswer(inv -> new ArrayList<>(usedRows));
        when(runDao.markDelivered(any(), any(), anyString())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            deliveredBy.add(inv.getArgument(2));
            writeLog.add(new long[]{id, 1});
            return 1;
        });
        when(runDao.markStalled(any(), any(), anyString())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            stallReasons.add(inv.getArgument(2));
            writeLog.add(new long[]{id, 2});
            return 1;
        });
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private Date at(LocalDate d, int hour) {
        return Date.from(LocalDateTime.of(d, java.time.LocalTime.of(hour, 0))
                .atZone(ZoneId.systemDefault()).toInstant());
    }

    private TBarrVisitItem copy(TBarrVisitItem s) {
        TBarrVisitItem r = new TBarrVisitItem();
        r.setId(s.getId());
        r.setItemNo(s.getItemNo());
        r.setDueAt(s.getDueAt());
        r.setAmount(s.getAmount());
        r.setContent(s.getContent());
        r.setStreetCode(s.getStreetCode());
        r.setStreetName(s.getStreetName());
        r.setGroupNo(s.getGroupNo());
        r.setArchId(s.getArchId());
        r.setOwnerId(s.getOwnerId());
        r.setOwnerName(s.getOwnerName());
        r.setContact(s.getContact());
        r.setSeason(s.getSeason());
        r.setStatus(s.getStatus());
        r.setDelFlag(s.getDelFlag());
        return r;
    }

    private Date due(LocalDate d) {
        return at(d, 10);
    }

    private TBarrVisitItem item(long id, String no, LocalDate dueDay, String amount) {
        TBarrVisitItem r = new TBarrVisitItem();
        r.setId(id);
        r.setItemNo(no);
        r.setDueAt(dueDay == null ? null : due(dueDay));
        r.setAmount(amount == null ? null : new BigDecimal(amount));
        r.setStatus(0);
        r.setDelFlag(0);
        r.setStreetCode("S1");
        r.setGroupNo("G1");
        r.setSeason("2026Q3");
        r.setContact("13800000000");
        r.setOwnerId(7L);
        r.setOwnerName("经办人甲");
        return r;
    }

    private TBarrArch arch(long id, int status, int delFlag) {
        TBarrArch a = new TBarrArch();
        a.setId(id);
        a.setStatus(status);
        a.setDelFlag(delFlag);
        return a;
    }

    private TBarrOwnerQuota quota(long owner, String season, int q) {
        TBarrOwnerQuota o = new TBarrOwnerQuota();
        o.setOwnerId(owner);
        o.setSeason(season);
        o.setQuota(q);
        return o;
    }

    private long[] counts() {
        long delivered = writeLog.stream().filter(x -> x[1] == 1).count();
        long stalled = writeLog.stream().filter(x -> x[1] == 2).count();
        return new long[]{delivered, stalled};
    }

    @Test
    public void 窗口外只回话_库里一格不动() {
        store.add(item(1, "V1", D, "0"));
        for (Date t : new Date[]{at(D, 0), at(D, 1), at(D.minusDays(1), 23), at(D, 5), at(D, 23)}) {
            writeLog.clear();
            VisitRunResult r = service.runDaily(t);
            assertFalse(r.isRan(), "02:00 前与 05:00 后都不开口");
            assertTrue(r.getMessage().contains("没动过库"));
            assertTrue(writeLog.isEmpty(), "窗口外一条回写都不许有");
        }
        // 压线：02:00 开口、04:00 在窗内
        assertTrue(service.runDaily(at(D, 2)).isRan());
        assertTrue(service.runDaily(at(D, 4)).isRan());
    }

    @Test
    public void 止点赶在前头或卡在这一瞬都办_没走到的留着() {
        store.add(item(1, "V1", D, "0"));       // 止点就是触发当天
        store.add(item(2, "V2", D.minusDays(3), "0")); // 已逾期，照办
        store.add(item(3, "V3", D.plusDays(1), "0"));  // 日子没走到
        VisitRunResult r = service.runDaily(at(D, 3));
        long[] c = counts();
        assertEquals(2, c[0], "压线与逾期都递出");
        assertEquals(0, c[1]);
        // 全局序：逾期的（止点早）在前，止点压线的在后
        assertEquals(java.util.Arrays.asList(2L, 1L), writeLog.stream().map(x -> x[0]).collect(Collectors.toList()));
    }

    @Test
    public void 提前只数成事日_节假日与停办日不数_周末照数() {
        // 止点 10-08（周四）；许提前 2 个成事日；10-07、10-06 是停办日，
        // 10-04（周日）、10-03（周六）寻常周末照数 → 往前两个成事日落在 10-04
        store.add(item(1, "V1", LocalDate.of(2026, 10, 8), "2"));
        nonWorking.add(LocalDate.of(2026, 10, 7));
        nonWorking.add(LocalDate.of(2026, 10, 6));
        // 10-03（周六）：只凑到一个成事日，不办——证明周末照数、停办日不数
        writeLog.clear();
        assertEquals(0, service.runDaily(at(LocalDate.of(2026, 10, 3), 3)).getDelivered());
        // 10-04（周日）：第二个成事日压线凑足，办
        writeLog.clear();
        assertEquals(1, service.runDaily(at(LocalDate.of(2026, 10, 4), 3)).getDelivered());
    }

    @Test
    public void 行站不住当场挂递不动_标完继续往下走() {
        store.add(item(1, "V1", D, "0"));
        store.add(item(2, null, D, "0"));                 // 代号没落
        TBarrVisitItem noDue = item(3, "V3", null, "0");  // 止点空
        store.add(noDue);
        store.add(item(4, "V4", D, null));                // 提前日数空
        store.add(item(5, "V5", D, "-2"));                // 负数
        store.add(item(6, "V6", D, "5000"));              // 大得没边
        store.add(item(7, "V7", D, "0"));                 // 好行，不许被连累
        VisitRunResult r = service.runDaily(at(D, 3));
        long[] c = counts();
        assertEquals(2, c[0], "1 号与 7 号递出");
        assertEquals(5, c[1], "五桩站不住全挂");
        assertEquals(5, stallReasons.size());
        assertEquals(5, r.getStalled());
    }

    @Test
    public void 住户撤走或联络空好几季_天生办不成直接挂() {
        store.add(item(1, "V1", D, "0"));
        TBarrVisitItem withdrawn = item(2, "V2", D, "0");
        withdrawn.setArchId(100L);
        store.add(withdrawn);
        archStore.add(arch(100L, 1, 0)); // 底册已停用
        TBarrVisitItem blankOld = item(3, "V3", D, "0");
        blankOld.setContact(null);
        blankOld.setSeason("2026Q2");     // 空了好几季
        store.add(blankOld);
        TBarrVisitItem blankNew = item(4, "V4", D, "0");
        blankNew.setContact(null);
        blankNew.setSeason("2026Q4");     // 本季新立，不算"好几季"
        store.add(blankNew);

        VisitRunResult r = service.runDaily(at(D, 3));
        assertEquals(2, r.getDelivered(), "1 号与本季新立的 4 号递出");
        assertEquals(2, r.getStalled(), "撤走、联络空多季各挂一桩");
    }

    @Test
    public void 定额装满_摊上的当场挂递不动连缘故写回() {
        quotaStore.add(quota(7L, "2026Q3", 2));
        // 库里本季已递 2 条：定额已满
        usedRows.add(usedRow(7L, "2026Q3", 2));
        store.add(item(1, "V1", D, "0"));
        store.add(item(2, "V2", D, "0"));
        VisitRunResult r = service.runDaily(at(D, 3));
        assertEquals(0, r.getDelivered());
        assertEquals(2, r.getStalled());
        assertTrue(stallReasons.get(0).contains("定额"));
    }

    @Test
    public void 同街同楼组并一段_不拆街_回写按段连着走() {
        // 全局序（日子、代号）把 B 街排在中间，但同街同组的桩必须连着走完
        store.add(mk(1, "A1", "S1", "G1", D.plusDays(2)));
        store.add(mk(2, "B1", "S2", "G9", D));
        store.add(mk(3, "A2", "S1", "G1", D.plusDays(1)));
        store.add(mk(4, "A3", "S1", "G1", D.plusDays(3)));
        service.runDaily(at(D.plusDays(4), 3));
        List<Long> order = writeLog.stream().map(x -> x[0]).collect(Collectors.toList());
        // S2 一段（2），随后 S1/G1 一段（3、1、4 按全局序）整段连着，不与别街穿插
        assertEquals(Arrays.asList(2L, 3L, 1L, 4L), order);
    }

    @Test
    public void 五百条封顶只办头五百_余下挪到下一趟() {
        for (long i = 1; i <= 502; i++) {
            store.add(item(i, "V" + (1000 - i), D, "0"));
        }
        VisitRunResult r = service.runDaily(at(D, 3));
        assertEquals(500, r.getPicked());
        assertEquals(500, r.getDelivered());
        assertEquals(0, r.getStalled());
        assertEquals(2, r.getWaiting(), "余下两条挪到下一趟");
    }

    @Test
    public void 递出双印同回_只认真正改动的行() {
        store.add(item(1, "V1", D, "0"));
        store.add(item(2, "V2", D, "0"));
        // 第 2 条回库没改成（并发已被别人递走）：不计递出，也不挂递不动。
        // 用 doAnswer().when() 重打桩，避免先以 null 参数触发旧 Answer（拆箱 NPE）。
        org.mockito.Mockito.doAnswer(inv -> {
            Long id = inv.getArgument(0);
            if (id == 2L) {
                return 0;
            }
            writeLog.add(new long[]{id, 1});
            deliveredBy.add(inv.getArgument(2));
            return 1;
        }).when(runDao).markDelivered(any(), any(), anyString());
        VisitRunResult r = service.runDaily(at(D, 3));
        assertEquals(1, r.getDelivered(), "三数只逐桩点回库返回值");
        assertEquals(0, r.getStalled());
        assertEquals(1, r.getWaiting(), "没挪成的那一桩仍搁着，算还欠开口，下趟再来");
        assertEquals(1, deliveredBy.size());
        assertEquals("经办人甲", deliveredBy.get(0), "递出经办那位照行上落印");
        verify(runDao, never()).markStalled(any(), any(), anyString());
    }

    @Test
    public void 一条没挑着_回本轮无到期_寻常不报事故() {
        VisitRunResult r = service.runDaily(at(D, 3));
        assertTrue(r.isRan());
        assertEquals("本轮无到期", r.getMessage());
        assertEquals(0, r.getWaiting());
        assertEquals(0, r.getDelivered());
        assertEquals(0, r.getStalled());
    }

    @Test
    public void 已递出的行不再当新债捞回() {
        TBarrVisitItem done = item(1, "V1", D, "0");
        done.setStatus(1); // 册子里若已是已递出（口上只捞搁着的），跑趟不碰它
        store.add(done);
        VisitRunResult r = service.runDaily(at(D, 3));
        assertEquals(0, r.getDelivered() + r.getStalled());
    }

    private TBarrVisitItem mk(long id, String no, String street, String group, LocalDate dueDay) {
        TBarrVisitItem r = item(id, no, dueDay, "0");
        r.setStreetCode(street);
        r.setGroupNo(group);
        return r;
    }

    private Map<String, Object> usedRow(long owner, String season, int cnt) {
        Map<String, Object> m = new HashMap<>();
        m.put("ownerId", owner);
        m.put("season", season);
        m.put("cnt", cnt);
        return m;
    }
}
