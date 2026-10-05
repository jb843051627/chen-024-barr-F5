package com.fc.v2.service;

import java.util.List;

import com.fc.v2.model.auto.TBarrJointSignBill;
import com.fc.v2.model.custom.JointSignAudit;
import com.fc.v2.model.custom.JointSignBoard;
import com.fc.v2.model.custom.JointSignForm;
import com.fc.v2.model.custom.JointSignView;

/**
 * 联户验收签认单（会签版）Service接口。
 * 单只能由当场验收记录带出来（openFromSign），全单只此一处走口（sign）：
 * 没有新建、改写、追加三类暗门；三口怎么算齐只认 JointSignRuleBook 这一处裁量。
 *
 * @author fuce
 * @date 2026-10-05
 */
public interface ITBarrJointSignBillService {

    /**
     * 由当场验收记录关联带单：同一场验收只带得出一张，再带一回原样回那张，不另起。
     * 记录上已有的字段（楼组/底册/街/户数/当场时刻/落笔者）单上直接沿用。
     */
    TBarrJointSignBill openFromAccept(Long acceptId, String operator);

    /** 按主键取单（不存在回 null） */
    TBarrJointSignBill selectBillById(Long billId);

    /**
     * 全单唯一走口：当前停口的某一席落「可/不可」。
     * 报文段号给了就得与单停口对得上，对不上这一笔不认；席位只认规则书里这一口的席位；
     * 已签数不接手填，落完照留痕当场数回。
     */
    JointSignView sign(JointSignForm form, String operator);

    /** 单面回显：三口四栏 + 各轮留底（作废笔另摆，不抹） */
    JointSignView view(Long billId);

    /** 签认单台账 */
    List<TBarrJointSignBill> listBills();

    /** 事后翻查：从第三口一口一口数回第一口，与受理簿一笔一笔合得拢 */
    JointSignAudit audit(Long billId);

    /** 板面：在签张数（随带单加一、随签讫减一）与移交名册同一回算 */
    JointSignBoard board();
}
