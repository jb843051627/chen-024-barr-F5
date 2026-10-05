package com.fc.v2.service;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TBarrAcceptRecord;
import com.fc.v2.model.custom.AcceptRecordForm;

/**
 * 当场验收记录（受理簿）Service接口。
 * 受理簿只许当场落一笔、翻簿回看；联户签认单只由这里记着的某场验收带出。
 *
 * @author fuce
 * @date 2026-10-05
 */
public interface ITBarrAcceptRecordService {

    /** 当场落簿一笔，回落好的记录（记录号系统按日排） */
    TBarrAcceptRecord register(AcceptRecordForm form, String holder);

    /** 按主键翻一场验收 */
    TBarrAcceptRecord selectById(Long id);

    /** 按受理簿记录号翻一场验收 */
    TBarrAcceptRecord selectByAcceptNo(String acceptNo);

    /** 受理簿台账（在册全列，按当场时刻倒序） */
    List<TBarrAcceptRecord> selectList(QueryWrapper<TBarrAcceptRecord> wrapper);
}
