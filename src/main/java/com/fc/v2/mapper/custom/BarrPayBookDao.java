package com.fc.v2.mapper.custom;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.fc.v2.model.auto.TBarrCapLine;
import com.fc.v2.model.auto.TBarrPayBatch;
import com.fc.v2.model.auto.TBarrPayRow;

/**
 * 补助核销册专用回库口：回执三数、照面、撤册、挪往期全从这一处落。
 * 回执上那三个数只认这里一行一行数出来的，屏上先亮的那份不作数；
 * 别处不许另开口子逐行点账，也不许自揣「同一个册码再来一遍」的答案。
 *
 * @author fuce
 * @date 2026-10-06
 */
@Mapper
public interface BarrPayBookDao {

    /** 在册册头（撤册留底的不算）：同一册码全库只此一册 */
    @Select("SELECT * FROM t_barr_pay_batch WHERE batch_no = #{batchNo} AND del_flag = 0 LIMIT 1")
    TBarrPayBatch findLiveBook(@Param("batchNo") String batchNo);

    /** 册台账：在册的册，按落库先后倒序 */
    @Select("SELECT * FROM t_barr_pay_batch WHERE del_flag = 0 ORDER BY create_time DESC, id DESC")
    List<TBarrPayBatch> listLiveBooks();

    /** 这条街在位的那条封顶线；街上没划线回空，进门不拿它卡行 */
    @Select("SELECT * FROM t_barr_cap_line WHERE street_code = #{streetCode} AND del_flag = 0 "
            + "AND (status IS NULL OR status = 0) LIMIT 1")
    TBarrCapLine findCapLine(@Param("streetCode") String streetCode);

    /** 原报几行：只数在册行，回库现数 */
    @Select("SELECT COUNT(*) FROM t_barr_pay_row WHERE batch_no = #{batchNo} AND del_flag = 0")
    int countRows(@Param("batchNo") String batchNo);

    /** 某种去向几行（1已收下 2已送回）：回库现数 */
    @Select("SELECT COUNT(*) FROM t_barr_pay_row WHERE batch_no = #{batchNo} AND del_flag = 0 AND status = #{status}")
    int countRowsByStatus(@Param("batchNo") String batchNo, @Param("status") int status);

    /** 全册在册行，按原报行次摆开（照面、对账都用这一份） */
    @Select("SELECT * FROM t_barr_pay_row WHERE batch_no = #{batchNo} AND del_flag = 0 ORDER BY row_no")
    List<TBarrPayRow> listRows(@Param("batchNo") String batchNo);

    /** 某种去向的行，按原报行次摆开（送回明细随回执带出） */
    @Select("SELECT * FROM t_barr_pay_row WHERE batch_no = #{batchNo} AND del_flag = 0 AND status = #{status} ORDER BY row_no")
    List<TBarrPayRow> listRowsByStatus(@Param("batchNo") String batchNo, @Param("status") int status);

    /** 现行名单：在册且没挪往期的行，按行次摆开 */
    @Select("SELECT * FROM t_barr_pay_row WHERE batch_no = #{batchNo} AND del_flag = 0 AND past_flag = 0 ORDER BY row_no")
    List<TBarrPayRow> listCurrentRows(@Param("batchNo") String batchNo);

    /** 往期层：挪了往期的行照旧翻得着、一条不少 */
    @Select("SELECT * FROM t_barr_pay_row WHERE batch_no = #{batchNo} AND del_flag = 0 AND past_flag = 1 ORDER BY row_no")
    List<TBarrPayRow> listPastRows(@Param("batchNo") String batchNo);

    /**
     * 撤册：册头撤下留底（del_flag=1、情形落已撤），活册占位同时让位，重递才立得起新册。
     * 只动在册那一册；回 1 才算撤成。
     */
    @Update("UPDATE t_barr_pay_batch SET del_flag = 1, status = 9, update_by = #{operator}, update_time = NOW() "
            + "WHERE id = #{id} AND del_flag = 0")
    int withdrawBook(@Param("id") Long id, @Param("operator") String operator);

    /** 撤册连行：全册在册行一并撤下留底，活行占位让位；册外无行可动 */
    @Update("UPDATE t_barr_pay_row SET del_flag = 1, update_by = #{operator}, update_time = NOW() "
            + "WHERE batch_no = #{batchNo} AND del_flag = 0")
    int withdrawRows(@Param("batchNo") String batchNo, @Param("operator") String operator);

    /**
     * 挪往期：连着两个拨付年度还没勾掉的行挪去往期层（past_flag=1），现行名单不再列它。
     * 只朝旧册开：册头拨付年度不晚于 maxPayYear 的在册册、行还空悬（未勾）的才挪；
     * 新册的行落库就是 0，这道手续够不着它们。
     */
    @Update("UPDATE t_barr_pay_row r JOIN t_barr_pay_batch b ON r.batch_no = b.batch_no AND b.del_flag = 0 "
            + "SET r.past_flag = 1, r.update_by = #{operator}, r.update_time = NOW() "
            + "WHERE b.pay_year IS NOT NULL AND b.pay_year <= #{maxPayYear} "
            + "AND r.del_flag = 0 AND r.past_flag = 0 AND r.status = 0")
    int moveStaleToPast(@Param("maxPayYear") int maxPayYear, @Param("operator") String operator);
}
