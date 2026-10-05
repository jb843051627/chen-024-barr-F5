package com.fc.v2.common.quartz.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fc.v2.model.custom.VisitRunResult;
import com.fc.v2.service.ITBarrVisitItemService;

/**
 * 质保与养护复检日跑任务。
 * 只是个敲门的：跑趟的规矩（窗口、挑单、并段、递不动、三数）全在 service 唯一口子里，
 * 这里不另起算法。原先的 v2Task 一概照旧，不动它本体。
 *
 * @author fuce
 * @date 2026-10-05
 */
@Component("visitItemTask")
public class VisitItemTask {

    @Autowired
    private ITBarrVisitItemService visitItemService;

    /**
     * 每日一趟（cron 每日 02:00）。窗口 02:00-05:00 外即便被错点触发，
     * service 也只应一声、库里一格不动。
     */
    public void runDaily() {
        VisitRunResult r = visitItemService.runDaily(null);
        System.out.println("[质保养护复检] " + r.getMessage()
                + "（欠开口" + r.getWaiting() + " 递出" + r.getDelivered()
                + " 递不动" + r.getStalled() + "）");
    }
}
