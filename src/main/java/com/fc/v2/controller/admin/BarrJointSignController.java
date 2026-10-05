package com.fc.v2.controller.admin;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.base.PageInfo;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.exception.JointSignRuleException;
import com.fc.v2.common.log.Log;
import com.fc.v2.model.auto.TBarrAcceptRecord;
import com.fc.v2.model.auto.TBarrJointSignBill;
import com.fc.v2.model.custom.JointSignForm;
import com.fc.v2.service.ITBarrAcceptRecordService;
import com.fc.v2.service.ITBarrJointSignBillService;
import com.fc.v2.shiro.util.ShiroUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * 联户验收签认单（会签版）Controller。
 * 单只能由当场验收记录关联带出（open），落签全单只此一处走口（sign）：
 * 没有新建、改写、追加三类暗门；屏上已签数只是系统照落笔数出来的回显。
 *
 * @author fuce
 * @date 2026-10-05
 */
@Api(value = "联户验收签认单")
@Controller
@RequestMapping("/BarrJointSignController")
public class BarrJointSignController extends BaseController {

    private final String prefix = "admin/barrJointSign";

    @Autowired
    private ITBarrJointSignBillService jointSignBillService;

    @Autowired
    private ITBarrAcceptRecordService acceptRecordService;

    @ApiOperation(value = "签认台账跳转", notes = "签认台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("barr:barrJointSign:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "联户签认单集合", action = "list")
    @ApiOperation(value = "签认单台账", notes = "签认单台账")
    @GetMapping("/list")
    @RequiresPermissions("barr:barrJointSign:list")
    @ResponseBody
    public ResultTable list() {
        startPage();
        List<TBarrJointSignBill> list = jointSignBillService.listBills();
        PageInfo<TBarrJointSignBill> page = new PageInfo<>(list);
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "板面：在签张数与移交名册", action = "board")
    @ApiOperation(value = "在签张数/移交名册同一回算", notes = "随带单加一、随签讫减一")
    @GetMapping("/board")
    @RequiresPermissions("barr:barrJointSign:list")
    @ResponseBody
    public AjaxResult board() {
        return AjaxResult.successData(200, jointSignBillService.board());
    }

    @ApiOperation(value = "由验收记录带单跳转", notes = "从受理簿某一场带单")
    @GetMapping("/openPage/{acceptId}")
    @RequiresPermissions("barr:barrJointSign:open")
    public String openPage(@PathVariable("acceptId") Long acceptId, ModelMap model) {
        TBarrAcceptRecord record = acceptRecordService.selectById(acceptId);
        model.put("record", record);
        return prefix + "/open";
    }

    @Log(title = "联户签认带单", action = "open")
    @ApiOperation(value = "由当场验收记录关联带单", notes = "同一场验收只带得出一张")
    @PostMapping("/open")
    @RequiresPermissions("barr:barrJointSign:open")
    @ResponseBody
    public AjaxResult open(Long acceptId) {
        try {
            TBarrJointSignBill bill = jointSignBillService.openFromAccept(acceptId, ShiroUtils.getLoginName());
            // 雪花 id 到前端要当字符串递，裸 Long 会在 JS 里丢精度
            return AjaxResult.success("已由当场验收记录带出这一张签认单：" + bill.getBillNo())
                    .put("billId", String.valueOf(bill.getId()));
        } catch (JointSignRuleException e) {
            return error(e.getMessage());
        }
    }

    @ApiOperation(value = "签认单面跳转", notes = "三口四栏，逐口坐定")
    @GetMapping("/page/{id}")
    @RequiresPermissions("barr:barrJointSign:view")
    public String page(@PathVariable("id") Long id, ModelMap model) {
        model.put("billId", id);
        return prefix + "/sign";
    }

    @Log(title = "联户签认单面", action = "detail")
    @ApiOperation(value = "签认单面回显", notes = "三口四栏 + 各轮留底")
    @GetMapping("/detail/{id}")
    @RequiresPermissions("barr:barrJointSign:view")
    @ResponseBody
    public AjaxResult detail(@PathVariable("id") Long id) {
        return AjaxResult.successData(200, jointSignBillService.view(id));
    }

    @Log(title = "三口落签", action = "sign")
    @ApiOperation(value = "全单只此一处走口", notes = "落可/不可；报文段号不作数")
    @PostMapping("/sign")
    @RequiresPermissions("barr:barrJointSign:sign")
    @ResponseBody
    public AjaxResult sign(JointSignForm form) {
        try {
            return AjaxResult.successData(200, jointSignBillService.sign(form, ShiroUtils.getLoginName()));
        } catch (JointSignRuleException e) {
            return error(e.getMessage());
        }
    }

    @Log(title = "联户签认逐口核对", action = "audit")
    @ApiOperation(value = "事后对受理簿", notes = "从第三口数回第一口")
    @GetMapping("/audit/{id}")
    @RequiresPermissions("barr:barrJointSign:audit")
    @ResponseBody
    public AjaxResult audit(@PathVariable("id") Long id) {
        return AjaxResult.successData(200, jointSignBillService.audit(id));
    }
}
