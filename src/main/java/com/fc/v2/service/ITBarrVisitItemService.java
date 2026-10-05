package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TBarrVisitItem;
import com.fc.v2.model.custom.VisitRunResult;

/**
 * 质保与养护复检条目 Service。
 * 跑一趟的走法只有 runDaily 这一处能讲清：窗口内外两段怎么取舍、怎么并段、
 * 怎么标递不动、末尾三个数怎么凑，统统一处办完。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITBarrVisitItemService {

    /** 按主键回查条目 */
    TBarrVisitItem selectTBarrVisitItemById(Long id);

    /** 册子查询（街口列表用；不承载任何跑趟算法） */
    List<TBarrVisitItem> selectVisitItemList(Wrapper<TBarrVisitItem> wrapper);

    /**
     * 跑一趟（唯一口子；街口手动按下与后台日跑都进这里，本体不开第二个）。
     * 窗口 02:00 起到 05:00 前止：窗口外只应一声，库里一格不动。
     */
    VisitRunResult runDaily(Date at);
}
