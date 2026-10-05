package com.fc.v2.mapper.auto;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fc.v2.model.auto.TBarrJointSignBill;

/**
 * 联户验收签认单（会签版）数据层。
 *
 * @author fuce
 * @date 2026-10-05
 */
public interface TBarrJointSignBillMapper extends BaseMapper<TBarrJointSignBill> {

    /**
     * 落签前锁单头行（FOR UPDATE）：同一张单并着递两笔时在这道串行，
     * 各笔按锁上的先后各记各的 line_seq，不许并成一句。
     */
    @Select("SELECT * FROM t_barr_joint_sign_bill WHERE id = #{id} FOR UPDATE")
    TBarrJointSignBill selectByIdForUpdate(@Param("id") Long id);
}
