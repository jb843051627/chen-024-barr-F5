package com.fc.v2.controller.admin;

import java.util.List;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.base.PageInfo;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.exception.PayBookRuleException;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TBarrPayBatch;
import com.fc.v2.model.auto.TBarrPayRow;
import com.fc.v2.model.custom.PayBookForm;
import com.fc.v2.model.custom.PayBookReceipt;
import com.fc.v2.service.ITBarrPayRowService;
import com.fc.v2.shiro.util.ShiroUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 补助核销册 Controller。
 * 册子进门只此一道手续（intake）：一册一次递齐、整册进门，不接逐行零着递；
 * 区里自录与街道转递两条来路进同一道门。单行既没有自己落册的路，
 * 也没有自己改去向的路——这里不开单行的增删改口子。
 *
 * @author fuce
 * @date 2026-10-06
 */
@Api(value = "补助核销册")
@Controller
@RequestMapping("/BarrPayBookController")
public class BarrPayBookController extends BaseController {

    private final String prefix = "admin/barrPayBook";

    @Autowired
    private ITBarrPayRowService payRowService;

    @ApiOperation(value = "核销册台账跳转", notes = "核销册台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("barr:barrPayBook:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "补助核销册集合", action = "list")
    @ApiOperation(value = "在册核销册台账", notes = "已撤留底的不列")
    @GetMapping("/list")
    @RequiresPermissions("barr:barrPayBook:list")
    @ResponseBody
    public ResultTable list() {
        startPage();
        List<TBarrPayBatch> list = payRowService.listBooks();
        PageInfo<TBarrPayBatch> page = new PageInfo<>(list);
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "核销册现行行", action = "rows")
    @ApiOperation(value = "现行名单", notes = "在册且没挪往期的行，按原报行次摆开")
    @GetMapping("/rows")
    @RequiresPermissions("barr:barrPayBook:list")
    @ResponseBody
    public ResultTable rows(String batchNo) {
        startPage();
        List<TBarrPayRow> list = payRowService.listCurrentRows(batchNo);
        PageInfo<TBarrPayRow> page = new PageInfo<>(list);
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "核销册往期层", action = "pastRows")
    @ApiOperation(value = "往期层翻看", notes = "挪了往期的行照旧翻得着、一条不少")
    @GetMapping("/pastRows")
    @RequiresPermissions("barr:barrPayBook:past")
    @ResponseBody
    public ResultTable pastRows(String batchNo) {
        startPage();
        List<TBarrPayRow> list = payRowService.listPastRows(batchNo);
        PageInfo<TBarrPayRow> page = new PageInfo<>(list);
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "核销册进门", action = "intake")
    @ApiOperation(value = "整册进门", notes = "点行数→逐行判收判退→回执三数；同一册码第二遍只照面")
    @PostMapping("/intake")
    @RequiresPermissions("barr:barrPayBook:intake")
    @ResponseBody
    public AjaxResult intake(@RequestBody PayBookForm form) {
        try {
            PayBookReceipt receipt = payRowService.intakeBook(form, ShiroUtils.getLoginName());
            return AjaxResult.successData(200, receipt);
        } catch (PayBookRuleException e) {
            return error(e.getMessage());
        }
    }

    @Log(title = "核销册回执", action = "receipt")
    @ApiOperation(value = "回执", notes = "三个数回库一行一行数出来，屏上先亮的不作数")
    @GetMapping("/receipt")
    @RequiresPermissions("barr:barrPayBook:receipt")
    @ResponseBody
    public AjaxResult receipt(String batchNo) {
        try {
            return AjaxResult.successData(200, payRowService.receiptOf(batchNo));
        } catch (PayBookRuleException e) {
            return error(e.getMessage());
        }
    }

    @Log(title = "核销册撤册", action = "withdraw")
    @ApiOperation(value = "撤册", notes = "确实要动先撤册，办完再重新递")
    @PostMapping("/withdraw")
    @RequiresPermissions("barr:barrPayBook:withdraw")
    @ResponseBody
    public AjaxResult withdraw(String batchNo) {
        try {
            payRowService.withdrawBook(batchNo, ShiroUtils.getLoginName());
            return AjaxResult.success("册已撤下留底，同一册码可以重新递了");
        } catch (PayBookRuleException e) {
            return error(e.getMessage());
        }
    }

    @Log(title = "核销册对账", action = "audit")
    @ApiOperation(value = "对账", notes = "行上去向与回执三数彼此对得上；合不上的行点名")
    @GetMapping("/audit")
    @RequiresPermissions("barr:barrPayBook:audit")
    @ResponseBody
    public AjaxResult audit(String batchNo) {
        try {
            return AjaxResult.successData(200, payRowService.auditBook(batchNo));
        } catch (PayBookRuleException e) {
            return error(e.getMessage());
        }
    }

    @Log(title = "未勾挪往期", action = "movePast")
    @ApiOperation(value = "挪往期", notes = "连着两个拨付年度未勾的挪往期层，现行名单不再列")
    @PostMapping("/movePast")
    @RequiresPermissions("barr:barrPayBook:movePast")
    @ResponseBody
    public AjaxResult movePast(Integer currentPayYear) {
        if (currentPayYear == null) {
            return error("本年度没递上来，挪往期这把尺子落不下去");
        }
        int moved = payRowService.moveStaleToPast(currentPayYear, ShiroUtils.getLoginName());
        return AjaxResult.success("这一趟挪了" + moved + "行去往期层");
    }
}
