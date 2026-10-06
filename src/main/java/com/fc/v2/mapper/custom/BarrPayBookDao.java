package com.fc.v2.mapper.custom;

import java.util.Date;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.fc.v2.model.auto.TBarrPayBook;
import com.fc.v2.model.auto.TBarrPayCap;
import com.fc.v2.model.auto.TBarrPayRow;

/**
 * 核销册一道门专用回库口：照面、点行数、列行、挪往期、撤册回写全从这一处落。
 * 回执那三个数只认这里一行一行点出来的数，屏上先亮的那份不作数；
 * 时刻读法只认调用方传进来的同一个时刻，别处不许另开口子逐行点账。
 *
 * @author fuce
 * @date 2026-10-06
 */
@Mapper
public interface BarrPayBookDao {

    /** 在册的册头（同册码同刻只许一本在册；并发两本由 uk_book_round 兜底） */
    @Select("SELECT * FROM t_barr_pay_book WHERE book_no = #{bookNo} AND status = 0 "
            + "ORDER BY round_no DESC LIMIT 1")
    TBarrPayBook findCurrentBook(@Param("bookNo") String bookNo);

    /** 指定那一轮的册头（撤了的也翻得着） */
    @Select("SELECT * FROM t_barr_pay_book WHERE book_no = #{bookNo} AND round_no = #{roundNo} LIMIT 1")
    TBarrPayBook findBook(@Param("bookNo") String bookNo, @Param("roundNo") Integer roundNo);

    /** 该册码走到第几轮（撤了的轮次也算；一趟没递过=0） */
    @Select("SELECT COALESCE(MAX(round_no), 0) FROM t_barr_pay_book WHERE book_no = #{bookNo}")
    int maxRound(@Param("bookNo") String bookNo);

    /** 回执·册上原报几行：该轮在册行一行一行点出来 */
    @Select("SELECT COUNT(*) FROM t_barr_pay_row "
            + "WHERE batch_no = #{bookNo} AND round_no = #{roundNo} AND del_flag = 0")
    int countRound(@Param("bookNo") String bookNo, @Param("roundNo") Integer roundNo);

    /** 回执·该轮某一去向（0未勾/1收下/2送回）几行：同样回库一行一行点出来 */
    @Select("SELECT COUNT(*) FROM t_barr_pay_row "
            + "WHERE batch_no = #{bookNo} AND round_no = #{roundNo} AND del_flag = 0 AND status = #{status}")
    int countRoundStatus(@Param("bookNo") String bookNo, @Param("roundNo") Integer roundNo,
                         @Param("status") Integer status);

    /** 该轮全部在册行，按原报行次排（行次排定不再挪，翻出来与街道纸面对得上号） */
    @Select("SELECT * FROM t_barr_pay_row "
            + "WHERE batch_no = #{bookNo} AND round_no = #{roundNo} AND del_flag = 0 ORDER BY row_no")
    List<TBarrPayRow> listRoundRows(@Param("bookNo") String bookNo, @Param("roundNo") Integer roundNo);

    /** 该轮某一去向的行，按原报行次排（回执上退回/空悬的行带纸面行次出门） */
    @Select("SELECT * FROM t_barr_pay_row "
            + "WHERE batch_no = #{bookNo} AND round_no = #{roundNo} AND del_flag = 0 AND status = #{status} "
            + "ORDER BY row_no")
    List<TBarrPayRow> listRoundStatusRows(@Param("bookNo") String bookNo, @Param("roundNo") Integer roundNo,
                                          @Param("status") Integer status);

    /** 现行空悬名单：未勾、在册、还没挪往期的行（撤册那一轮置过删的进不来） */
    @Select("SELECT * FROM t_barr_pay_row "
            + "WHERE del_flag = 0 AND status = 0 AND COALESCE(past_flag, 0) = 0 "
            + "ORDER BY batch_no, round_no, row_no")
    List<TBarrPayRow> listHangingCurrent();

    /** 往期层：已挪往期的行，照旧翻得着、一条不少；册码空着=全层都翻 */
    @Select("<script>SELECT * FROM t_barr_pay_row WHERE del_flag = 0 AND past_flag = 1 "
            + "<if test='bookNo != null and bookNo != \"\"'>AND batch_no = #{bookNo} </if>"
            + "ORDER BY batch_no, round_no, row_no</script>")
    List<TBarrPayRow> listPast(@Param("bookNo") String bookNo);

    /**
     * 挪往期：只动还在现行层（past_flag=0）的未勾在册行，update 真正改动一行（回 1）才算挪成。
     * 显式落 update_time、不走审计填充：年挪线程没有登录身份，填充会抛异常。
     */
    @Update("UPDATE t_barr_pay_row SET past_flag = 1, update_time = #{at} "
            + "WHERE id = #{id} AND del_flag = 0 AND status = 0 AND COALESCE(past_flag, 0) = 0")
    int markPast(@Param("id") Long id, @Param("at") Date at);

    /**
     * 撤册手续：把那一轮的在册行全部置删留底（不真抽走，撤了的轮照旧翻得着）。
     * 显式落 update_by/update_time，绕开登录态填充。
     */
    @Update("UPDATE t_barr_pay_row SET del_flag = 1, update_by = #{operator}, update_time = #{at} "
            + "WHERE batch_no = #{bookNo} AND round_no = #{roundNo} AND del_flag = 0")
    int withdrawRows(@Param("bookNo") String bookNo, @Param("roundNo") Integer roundNo,
                     @Param("operator") String operator, @Param("at") Date at);

    /** 当地那条封顶线：一街一行；没有行=这条街不卡 */
    @Select("SELECT * FROM t_barr_pay_cap WHERE street_code = #{streetCode} LIMIT 1")
    TBarrPayCap findCap(@Param("streetCode") String streetCode);
}
