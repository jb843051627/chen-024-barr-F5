package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TBarrArchMapper;
import com.fc.v2.mapper.auto.TBarrOwnerQuotaMapper;
import com.fc.v2.mapper.auto.TBarrVisitItemMapper;
import com.fc.v2.mapper.custom.BarrVisitRunDao;
import com.fc.v2.model.auto.TBarrArch;
import com.fc.v2.model.auto.TBarrOwnerQuota;
import com.fc.v2.model.auto.TBarrVisitItem;
import com.fc.v2.model.custom.VisitRunResult;
import com.fc.v2.service.ITBarrVisitItemService;
import com.fc.v2.util.StringUtils;

/**
 * 质保与养护复检条目 Service：跑一趟的走法只在 {@link #runDaily} 这一处讲清。
 *
 * 时段比较全在 Java 里走（册子小，整册捞出再逐条问）：
 * JDBC 的 serverTimezone 与本机时区不对称，把 Date 绑进 SQL 或转墙钟字符串都会整体偏移，
 * 边界判定在"恰好到期/开窗当日"这类用例上会随机错档。故挑单与回写只认
 * 本方法入口拍下的同一个时刻快照，别处不许另读一遍钟、另起一轮回库点账。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TBarrVisitItemServiceImpl implements ITBarrVisitItemService {

    /** 跑趟口：凌晨两点过起、到五点前止（含 02:00:00，不含 05:00:00） */
    private static final int WINDOW_FROM_HOUR = 2;
    private static final int WINDOW_TO_HOUR = 5;
    /** 窗口内外，按门的人听见的都是同一句回话 */
    private static final String MSG_OUTSIDE = "这一趟没动过库（跑趟口为每日 02:00 起到 05:00 前止）";
    /** 一条也没挑着，寻常事，不当事故 */
    private static final String MSG_NONE_DUE = "本轮无到期";

    /** 一趟最多办五百桩，余下的挪到下一趟，不许一口气捞穿整张表 */
    private static final int RUN_LIMIT = 500;
    /** 提前日数大过这个数就算"大得没边"，行站不住 */
    private static final int MAX_EARLY_DAYS = 1000;

    /** 条目情形：0搁着 1已递出 2递不动——只此三格 */
    private static final int STATUS_WAIT = 0;

    /** 办成回写时，经办那栏实在空着就落这个名，但不许空着 */
    private static final String UNASSIGNED_OPERATOR = "(未分派经办)";

    @Resource
    private TBarrVisitItemMapper barrVisitItemMapper;
    @Resource
    private TBarrArchMapper barrArchMapper;
    @Resource
    private TBarrOwnerQuotaMapper barrOwnerQuotaMapper;
    @Resource
    private BarrVisitRunDao barrVisitRunDao;

    @Override
    public TBarrVisitItem selectTBarrVisitItemById(Long id) {
        return this.barrVisitItemMapper.selectById(id);
    }

    @Override
    public List<TBarrVisitItem> selectVisitItemList(
            com.baomidou.mybatisplus.core.conditions.Wrapper<TBarrVisitItem> wrapper) {
        return this.barrVisitItemMapper.selectList(wrapper);
    }

    /**
     * 跑一趟。窗口内外怎么取舍、怎么并段、怎么标递不动、末尾三个数怎么凑，全在此一处。
     *
     * 三个数只认这一趟回库逐桩 update 的返回值（真正改动一行才计数），
     * 屏上那一眼与库里那一遍是同一回算的账；历史不并进来。
     * 不开整趟事务：每条回写各自落库，一条写不进不连累其余，标完继续往下走。
     */
    @Override
    public VisitRunResult runDaily(java.util.Date passedAt) {
        // 全趟只拍这一次钟：窗口判定、挑单、递出时刻落印都用它，杜绝两处各读一套
        java.util.Date at = passedAt != null ? passedAt : new java.util.Date();
        LocalDateTime wall = LocalDateTime.ofInstant(at.toInstant(), ZoneId.systemDefault());

        VisitRunResult result = new VisitRunResult();
        result.setFiredAt(at);

        // 窗口外：应一声就应一声，库里一格也不动
        if (wall.getHour() < WINDOW_FROM_HOUR || wall.getHour() >= WINDOW_TO_HOUR) {
            result.setRan(false);
            result.setMessage(MSG_OUTSIDE);
            return result;
        }
        result.setRan(true);

        LocalDate runDate = wall.toLocalDate();
        String currentSeason = seasonOf(runDate);

        // 全册只捞"搁着且在册"的：上一趟递出去过的不会再被当新债捞回来；递不动的也不重进册
        List<TBarrVisitItem> waiting = this.barrVisitItemMapper.selectList(new QueryWrapper<TBarrVisitItem>()
                .eq("del_flag", 0)
                .eq("status", STATUS_WAIT));

        Set<Long> withdrawnArchs = withdrawnArchIds(waiting);
        Set<LocalDate> nonWorking = loadNonWorkingDays(waiting, runDate);
        Map<String, Integer> quotaMap = loadQuotas();
        Map<String, Integer> usedMap = loadDeliveredCounts();

        // 第一遍：行站不住的、天生办不成的，当场挂递不动，剩下的照旧往下走，不中断整趟；
        // 够得上日子的进挑中册，日子没走到的留着自己赶上来
        List<TBarrVisitItem> eligible = new ArrayList<>();
        int stalled = 0;
        for (TBarrVisitItem r : waiting) {
            // 再守一道：只认"搁着"的在册行，已递出的绝不许被当新债捞回来办
            if (r.getStatus() == null || r.getStatus() != STATUS_WAIT
                    || (r.getDelFlag() != null && r.getDelFlag() != 0)) {
                continue;
            }
            String invalid = invalidReason(r);
            if (invalid != null) {
                stalled += markStalled(r, at, invalid);
                continue;
            }
            if (isHouseholdWithdrawn(r, withdrawnArchs)) {
                stalled += markStalled(r, at, "住户已从底册撤走，本桩挂起");
                continue;
            }
            if (isContactLongBlank(r, currentSeason)) {
                stalled += markStalled(r, at, "联络栏空着好几季没人填，本桩挂起");
                continue;
            }
            int earlyDays = r.getAmount().setScale(0, RoundingMode.DOWN).intValue();
            LocalDate dueDay = toLocalDate(r.getDueAt());
            LocalDate earliest = subtractWorkingDays(dueDay, earlyDays, nonWorking);
            // 压线收进：触发日已到最早可上门日即算；止点走过的逾期行同样照办
            if (!runDate.isBefore(earliest)) {
                eligible.add(r);
            }
        }

        // 全局排定：按下门日子先后，同一日子再按代号从小到大（代号空的头一遍已挂死，到不了这）
        Collections.sort(eligible, new Comparator<TBarrVisitItem>() {
            @Override
            public int compare(TBarrVisitItem a, TBarrVisitItem r) {
                int byDay = toLocalDate(a.getDueAt()).compareTo(toLocalDate(r.getDueAt()));
                if (byDay != 0) {
                    return byDay;
                }
                int byNo = a.getItemNo().compareTo(r.getItemNo());
                if (byNo != 0) {
                    return byNo;
                }
                return Long.compare(a.getId(), r.getId());
            }
        });

        // 五百条封顶：按上面的全局序只取头五百条办，余下的整批挪到下一趟，
        // 不许一口气捞穿整张表。切口由（日子、代号）序定死，不随段乱拆。
        int pickedSize = Math.min(RUN_LIMIT, eligible.size());
        List<TBarrVisitItem> picked = new ArrayList<>(eligible.subList(0, pickedSize));

        // 并段：头五百条里，同一条街、同一片楼组并成一段；段序随段里最早一桩在全局序里的先后。
        // 一段一趟走完：进了段的桩全部走完才收工，不跳下一段。
        LinkedHashMap<String, List<TBarrVisitItem>> segments = new LinkedHashMap<>();
        for (TBarrVisitItem r : picked) {
            segments.computeIfAbsent(segmentKey(r), k -> new ArrayList<>()).add(r);
        }

        int delivered = 0;
        int stalledThisPass = 0;
        int unchanged = 0;
        for (List<TBarrVisitItem> rows : segments.values()) {
            for (TBarrVisitItem r : rows) {
                String itemSeason = StringUtils.isNotEmpty(r.getSeason()) ? r.getSeason().trim() : currentSeason;
                if (isQuotaFull(r, itemSeason, quotaMap, usedMap)) {
                    int q = quotaMap.get(quotaKey(r.getOwnerId(), itemSeason));
                    int used = usedMap.getOrDefault(quotaKey(r.getOwnerId(), itemSeason), 0);
                    stalledThisPass += markStalled(r, at, "本季定额已装满（定额" + q + "条，已递" + used + "条），摊不进来");
                    continue;
                }
                String by = StringUtils.isNotEmpty(r.getOwnerName()) ? r.getOwnerName().trim() : UNASSIGNED_OPERATOR;
                int changed = this.barrVisitRunDao.markDelivered(r.getId(), at, by);
                if (changed == 1) {
                    delivered++;
                    if (r.getOwnerId() != null) {
                        usedMap.merge(quotaKey(r.getOwnerId(), itemSeason), 1, Integer::sum);
                    }
                } else {
                    // 回库没挪成（并发已被别人递走等）：这桩仍是搁着，计入"还欠开口"，下趟再来
                    unchanged++;
                }
            }
        }
        stalled += stalledThisPass;

        // 还欠开口：头五百条之外整批挪下一趟的，加挑中却没挪成（仍搁着）的桩。
        // 日子没走到的不进这趟的账；三个数只逐桩点本次回写，不并历史。
        int deferred = eligible.size() - pickedSize + unchanged;

        result.setPicked(pickedSize);
        result.setDelivered(delivered);
        result.setStalled(stalled);
        result.setWaiting(deferred);
        // 一条没挑着、也没挂过任何行，才算寻常的"本轮无到期"；有挂死则照实报汇总
        result.setMessage((delivered == 0 && stalled == 0) ? MSG_NONE_DUE
                : "本趟递出" + delivered + "条，挂递不动" + stalled + "条，挪到下一趟" + deferred + "条");
        return result;
    }

    // ============================ 内部 ============================

    /** Date 到墙钟日的唯一转法：与窗口判定同一个时区，不另起一套 */
    private LocalDate toLocalDate(java.util.Date d) {
        return LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault()).toLocalDate();
    }

    /** 行自己站不住：代号没落、止点空、提前日数没个准数（空/负/大得没边）。卡在哪格写哪格 */
    private String invalidReason(TBarrVisitItem r) {
        if (StringUtils.isEmpty(r.getItemNo())) {
            return "条目代号没落，行站不住";
        }
        if (r.getDueAt() == null) {
            return "止点时刻那一栏空着，行站不住";
        }
        BigDecimal amount = r.getAmount();
        if (amount == null) {
            return "最多许提前几日没个数，行站不住";
        }
        if (amount.signum() < 0) {
            return "最多许提前几日是个负数，行站不住";
        }
        if (amount.setScale(0, RoundingMode.DOWN).intValue() > MAX_EARLY_DAYS) {
            return "最多许提前几日大得没了边（逾" + MAX_EARLY_DAYS + "日），行站不住";
        }
        return null;
    }

    /** 挂递不动：只认回库真正改动的那一行；单条写不进不拖垮整趟 */
    private int markStalled(TBarrVisitItem r, java.util.Date at, String reason) {
        try {
            return this.barrVisitRunDao.markStalled(r.getId(), at, reason);
        } catch (RuntimeException e) {
            // 这一条落不进库，跳过继续，不许因它中断整趟
            return 0;
        }
    }

    /** 住户撤走：指了底册却寻不着在册行（已停用/已删/查无此册） */
    private boolean isHouseholdWithdrawn(TBarrVisitItem r, Set<Long> withdrawnArchs) {
        return r.getArchId() != null && withdrawnArchs.contains(r.getArchId());
    }

    /** 联络栏空着好几季：空着且不是本季新立的（旧档季度空着也算好几季） */
    private boolean isContactLongBlank(TBarrVisitItem r, String currentSeason) {
        if (StringUtils.isNotEmpty(r.getContact())) {
            return false;
        }
        String season = r.getSeason() == null ? null : r.getSeason().trim();
        return !currentSeason.equals(season);
    }

    /** 定额满：册上给这（人，季）立过行，且已递数已到定额；没立行=这季不卡 */
    private boolean isQuotaFull(TBarrVisitItem r, String season, Map<String, Integer> quota,
                                Map<String, Integer> used) {
        if (r.getOwnerId() == null) {
            return false;
        }
        Integer q = quota.get(quotaKey(r.getOwnerId(), season));
        return q != null && used.getOrDefault(quotaKey(r.getOwnerId(), season), 0) >= q;
    }

    private String quotaKey(Long ownerId, String season) {
        return ownerId + "|" + season;
    }

    /** 段键：街代号 + 楼组片号；空着的并作空段，仍按整段走，不借邻街 */
    private String segmentKey(TBarrVisitItem r) {
        String street = r.getStreetCode() == null ? "" : r.getStreetCode().trim();
        String group = r.getGroupNo() == null ? "" : r.getGroupNo().trim();
        return street + "|" + group;
    }

    /**
     * 成事日回推：从止点那一日往前一日一日数，法定节假日、区停办日不数，
     * 余下的（含寻常周末）一天一天凑足 n 日；只按整日，跨夜那截凑不成整日。
     */
    private LocalDate subtractWorkingDays(LocalDate due, int n, Set<LocalDate> nonWorking) {
        LocalDate d = due;
        int gathered = 0;
        while (gathered < n) {
            d = d.minusDays(1);
            if (!nonWorking.contains(d)) {
                gathered++;
            }
        }
        return d;
    }

    /** 把最早止点回推上千日到最晚止点之间的节假日/停办日一次取齐 */
    private Set<LocalDate> loadNonWorkingDays(List<TBarrVisitItem> rows, LocalDate runDate) {
        LocalDate minDue = null;
        LocalDate maxDue = runDate;
        for (TBarrVisitItem r : rows) {
            if (r.getDueAt() != null) {
                LocalDate d = toLocalDate(r.getDueAt());
                if (minDue == null || d.isBefore(minDue)) {
                    minDue = d;
                }
                if (d.isAfter(maxDue)) {
                    maxDue = d;
                }
            }
        }
        // 下界留足缓冲：凑 1000 个成事日，遇节假日/停办日要跨更多日历日，
        // 多放 400 日容下数年假日，免得回推算到取数范围之外把假日误当工作日。
        LocalDate from = (minDue != null ? minDue : runDate).minusDays(MAX_EARLY_DAYS + 400L);
        return new HashSet<>(this.barrVisitRunDao.listNonWorkingDays(from, maxDue));
    }

    /** 已撤底册 id：条目指到的底册里，删除/停用/查无的 */
    private Set<Long> withdrawnArchIds(List<TBarrVisitItem> rows) {
        Set<Long> ids = new HashSet<>();
        for (TBarrVisitItem r : rows) {
            if (r.getArchId() != null) {
                ids.add(r.getArchId());
            }
        }
        Set<Long> withdrawn = new HashSet<>();
        if (ids.isEmpty()) {
            return withdrawn;
        }
        List<TBarrArch> archs = this.barrArchMapper.selectBatchIds(ids);
        Set<Long> present = new HashSet<>();
        for (TBarrArch a : archs) {
            present.add(a.getId());
            boolean dead = (a.getDelFlag() != null && a.getDelFlag() == 1)
                    || (a.getStatus() != null && a.getStatus() == 1);
            if (dead) {
                withdrawn.add(a.getId());
            }
        }
        // 指了却查无此册：按撤走论
        for (Long id : ids) {
            if (!present.contains(id)) {
                withdrawn.add(id);
            }
        }
        return withdrawn;
    }

    /** 定额册一次取齐（人+季） */
    private Map<String, Integer> loadQuotas() {
        Map<String, Integer> map = new HashMap<>();
        for (TBarrOwnerQuota q : this.barrOwnerQuotaMapper.selectList(new QueryWrapper<TBarrOwnerQuota>())) {
            if (q.getOwnerId() != null && q.getSeason() != null && q.getQuota() != null) {
                map.put(quotaKey(q.getOwnerId(), q.getSeason().trim()), q.getQuota());
            }
        }
        return map;
    }

    /** 库内已递出按（人，季）数一遍：定额占用以库里现状为准，不由屏上现轧 */
    private Map<String, Integer> loadDeliveredCounts() {
        Map<String, Integer> map = new HashMap<>();
        for (Map<String, Object> row : this.barrVisitRunDao.countDeliveredByOwnerSeason()) {
            Object owner = row.get("ownerId");
            Object season = row.get("season");
            Object cnt = row.get("cnt");
            if (owner == null || season == null) {
                continue;
            }
            map.put(quotaKey(((Number) owner).longValue(), season.toString()),
                    cnt == null ? 0 : ((Number) cnt).intValue());
        }
        return map;
    }

    /** 季码与进度卡同一套：2026Q4 */
    private String seasonOf(LocalDate d) {
        int q = (d.getMonthValue() - 1) / 3 + 1;
        return d.getYear() + "Q" + q;
    }
}
