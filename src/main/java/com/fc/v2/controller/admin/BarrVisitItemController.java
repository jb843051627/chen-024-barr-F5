package com.fc.v2.controller.admin;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.base.PageInfo;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TBarrVisitItem;
import com.fc.v2.service.ITBarrSiteCardService;
import com.fc.v2.service.ITBarrVisitItemService;
import com.fc.v2.shiro.util.ShiroUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 质保与养护复检条目 Controller（街口这头）。
 * 只两条口：册子只读看一眼、手动开跑一趟。跑趟的规矩全在 service 唯一口子里，
 * 街口与后台日跑联动的是同一个 runDaily，本体不开第二个口子；
 * 窗口外按下只听见同一句回话，库里一格不动。
 *
 * @author fuce
 * @date 2026-10-05
 */
@Api(value = "质保养护复检")
@Controller
@RequestMapping("/BarrVisitItemController")
public class BarrVisitItemController extends BaseController {

    private final String prefix = "admin/barrVisitItem";

    @Autowired
    private ITBarrVisitItemService barrVisitItemService;
    @Autowired
    private ITBarrSiteCardService barrSiteCardService;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("barr:barrVisitItem:view")
    public String view(ModelMap model) {
        model.put("season", barrSiteCardService.currentSeason());
        return prefix + "/list";
    }

    @Log(title = "复检条目集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "只读册子，看不见的街一条都不进")
    @GetMapping("/list")
    @RequiresPermissions("barr:barrVisitItem:list")
    @ResponseBody
    public ResultTable list(String itemNo, Integer status, String season) {
        startPage();
        List<String> streets = barrSiteCardService.visibleStreetCodes(ShiroUtils.getUserId());
        QueryWrapper<TBarrVisitItem> wrapper = new QueryWrapper<TBarrVisitItem>()
                .eq("del_flag", 0)
                .orderByDesc("due_at");
        // 与进度卡同一口径：没授权就是空，绝不借邻街
        if (streets == null || streets.isEmpty()) {
            wrapper.apply("1 = 0");
        } else {
            wrapper.in("street_code", streets);
        }
        if (itemNo != null && !itemNo.trim().isEmpty()) {
            wrapper.likeRight("item_no", itemNo.trim());
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        if (season != null && !season.trim().isEmpty()) {
            wrapper.eq("season", season.trim());
        }
        PageInfo<TBarrVisitItem> page = new PageInfo<>(barrVisitItemService.selectVisitItemList(wrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "复检手动开跑", action = "run")
    @ApiOperation(value = "手动开跑一趟", notes = "与后台日跑同一个口子；窗口外只回话不动库")
    @PostMapping("/run")
    @RequiresPermissions("barr:barrVisitItem:run")
    @ResponseBody
    public AjaxResult run() {
        // 街口按下与凌晨日跑进的是同一处；窗口外谁按都听见同一句，不算事故
        return AjaxResult.successData(200, barrVisitItemService.runDaily(new java.util.Date()));
    }
}
