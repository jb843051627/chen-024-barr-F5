package com.fc.v2.controller.admin;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.base.PageInfo;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TBarrPayBook;
import com.fc.v2.model.auto.TBarrPayRow;
import com.fc.v2.model.custom.PayBookForm;
import com.fc.v2.service.ITBarrPayBookService;
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
 * 补助核销册一道门 Controller（街口这头）。
 * 写册只此一条口（enter）：册头先点行数、逐行判收判退、回执三数一并轧出，
 * 规矩全在 service 唯一口子里，这里不另起算法；其余各口都是只读翻看，
 * 单行既没有自己落册的路，也没有自己改去向的路。
 *
 * @author fuce
 * @date 2026-10-06
 */
@Api(value = "补助核销册一道门")
@Controller
@RequestMapping("/BarrPayBookController")
public class BarrPayBookController extends BaseController {

    private final String prefix = "admin/barrPayBook";

    @Autowired
    private ITBarrPayBookService barrPayBookService;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("barr:barrPayBook:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "核销册集合查询", action = "list")
    @ApiOperation(value = "册头集合", notes = "在册/已撤都翻得着，只读")
    @GetMapping("/list")
    @RequiresPermissions("barr:barrPayBook:list")
    @ResponseBody
    public ResultTable list(String bookNo, Integer status) {
        startPage();
        QueryWrapper<TBarrPayBook> wrapper = new QueryWrapper<TBarrPayBook>()
                .orderByDesc("create_time").orderByDesc("round_no");
        if (bookNo != null && !bookNo.trim().isEmpty()) {
            wrapper.likeRight("book_no", bookNo.trim());
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        PageInfo<TBarrPayBook> page = new PageInfo<>(barrPayBookService.selectBookList(wrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "整册进门", action = "enter")
    @ApiOperation(value = "整册进门", notes = "一册一次递齐；同册码第二遍只照面；压过上限整册退回")
    @PostMapping("/enter")
    @RequiresPermissions("barr:barrPayBook:enter")
    @ResponseBody
    public AjaxResult enter(@RequestBody PayBookForm form) {
        // 区里自录与街道转递两条来路进的是同一道门；回执三数只认回库点出来的
        return AjaxResult.successData(200, barrPayBookService.enterBook(form, ShiroUtils.getLoginName()));
    }

    @Log(title = "回执翻看", action = "receipt")
    @ApiOperation(value = "回执翻看", notes = "三个数只认回库现点出来的，屏上先亮的不作数")
    @GetMapping("/receipt")
    @RequiresPermissions("barr:barrPayBook:receipt")
    @ResponseBody
    public AjaxResult receipt(String bookNo) {
        return AjaxResult.successData(200, barrPayBookService.receiptOf(bookNo));
    }

    @Log(title = "对账点名", action = "audit")
    @ApiOperation(value = "对账点名", notes = "行上去向与回执三数彼此对上，合不上的那几行点名")
    @GetMapping("/audit")
    @RequiresPermissions("barr:barrPayBook:audit")
    @ResponseBody
    public AjaxResult audit(String bookNo) {
        return AjaxResult.successData(200, barrPayBookService.audit(bookNo));
    }

    @Log(title = "撤册手续", action = "withdraw")
    @ApiOperation(value = "撤册手续", notes = "办完才许同册码重新递；撤的那轮留底不真抽走")
    @PostMapping("/withdraw")
    @RequiresPermissions("barr:barrPayBook:withdraw")
    @ResponseBody
    public AjaxResult withdraw(String bookNo) {
        return AjaxResult.success(barrPayBookService.withdrawBook(bookNo, ShiroUtils.getLoginName()));
    }

    @Log(title = "翻册", action = "rows")
    @ApiOperation(value = "翻册", notes = "在册那一轮的全部行，行次排定不再挪")
    @GetMapping("/rows")
    @RequiresPermissions("barr:barrPayBook:list")
    @ResponseBody
    public ResultTable rows(String bookNo) {
        List<TBarrPayRow> rows = barrPayBookService.listBookRows(bookNo);
        return dataTable(rows);
    }

    @Log(title = "现行空悬名单", action = "hanging")
    @ApiOperation(value = "现行空悬名单", notes = "未勾、在册、还没挪往期的行")
    @GetMapping("/hanging")
    @RequiresPermissions("barr:barrPayBook:list")
    @ResponseBody
    public ResultTable hanging() {
        return dataTable(barrPayBookService.listHangingCurrent());
    }

    @Log(title = "往期层翻看", action = "past")
    @ApiOperation(value = "往期层翻看", notes = "往期那层照旧翻得着、一条不少；只朝旧册开")
    @GetMapping("/past")
    @RequiresPermissions("barr:barrPayBook:past")
    @ResponseBody
    public ResultTable past(String bookNo) {
        return dataTable(barrPayBookService.listPast(bookNo));
    }

    @Log(title = "手动挪往期", action = "movePast")
    @ApiOperation(value = "手动挪往期", notes = "与年挪同一口子：连着两个拨付年度没着落的未勾行挪往期")
    @PostMapping("/movePast")
    @RequiresPermissions("barr:barrPayBook:movePast")
    @ResponseBody
    public AjaxResult movePast() {
        int moved = barrPayBookService.movePastDue(null, new Date());
        return AjaxResult.success("这一趟挪往期" + moved + "行");
    }
}
