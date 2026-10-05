package com.fc.v2.controller.admin;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.base.PageInfo;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.exception.JointSignRuleException;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TBarrAcceptRecord;
import com.fc.v2.model.auto.TBarrArch;
import com.fc.v2.mapper.auto.TBarrArchMapper;
import com.fc.v2.model.custom.AcceptRecordForm;
import com.fc.v2.service.ITBarrAcceptRecordService;
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
 * 当场验收记录（受理簿）Controller。
 * 一场验收一行；联户验收签认单只由这里的某一场关联带出，不许别处另起手建。
 *
 * @author fuce
 * @date 2026-10-05
 */
@Api(value = "当场验收记录")
@Controller
@RequestMapping("/BarrAcceptRecordController")
public class BarrAcceptRecordController extends BaseController {

    private final String prefix = "admin/barrAcceptRecord";

    @Autowired
    private ITBarrAcceptRecordService acceptRecordService;

    @Autowired
    private TBarrArchMapper barrArchMapper;

    @ApiOperation(value = "受理簿跳转", notes = "受理簿跳转")
    @GetMapping("/view")
    @RequiresPermissions("barr:barrAccept:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "当场验收记录集合", action = "list")
    @ApiOperation(value = "受理簿台账", notes = "受理簿台账")
    @GetMapping("/list")
    @RequiresPermissions("barr:barrAccept:list")
    @ResponseBody
    public ResultTable list() {
        startPage();
        List<TBarrAcceptRecord> list = acceptRecordService.selectList(new QueryWrapper<>());
        PageInfo<TBarrAcceptRecord> page = new PageInfo<>(list);
        return pageTable(page.getList(), page.getTotal());
    }

    @ApiOperation(value = "当场落簿跳转", notes = "当场落簿跳转")
    @GetMapping("/register")
    @RequiresPermissions("barr:barrAccept:register")
    public String registerPage(ModelMap model) {
        model.put("archList", barrArchMapper.selectList(new QueryWrapper<TBarrArch>()
                .eq("del_flag", 0).eq("status", 0)
                .orderByAsc("arch_kind").orderByAsc("arch_no")));
        return prefix + "/register";
    }

    @Log(title = "当场验收落簿", action = "register")
    @ApiOperation(value = "当场落一笔受理簿", notes = "记录号系统按日排")
    @PostMapping("/register")
    @RequiresPermissions("barr:barrAccept:register")
    @ResponseBody
    public AjaxResult registerSave(AcceptRecordForm form) {
        try {
            TBarrAcceptRecord r = acceptRecordService.register(form, ShiroUtils.getLoginName());
            return AjaxResult.success("已落受理簿，记录号：" + r.getAcceptNo());
        } catch (JointSignRuleException e) {
            return error(e.getMessage());
        }
    }
}
