package com.fc.v2.common.quartz.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fc.v2.service.ITBarrPayBookService;

/**
 * 补助核销往期年挪任务。
 * 只是个敲门的：挪往期的规矩（未勾、连着两个拨付年度没着落）全在 service 唯一口子里，
 * 这里不另起算法。原先的 v2Task 一概照旧，不动它本体。
 *
 * @author fuce
 * @date 2026-10-06
 */
@Component("payBookTask")
public class PayBookTask {

    @Autowired
    private ITBarrPayBookService payBookService;

    /**
     * 每年一趟（cron 每年 1 月 1 日 03:40）。手动按下与年挪进的是同一处：
     * 连着两个拨付年度还没着落的未勾行挪去往期层，现行名单不再列它。
     */
    public void movePast() {
        int moved = payBookService.movePastDue(null, null);
        System.out.println("[补助核销往期年挪] 这一趟挪往期 " + moved + " 行");
    }
}
