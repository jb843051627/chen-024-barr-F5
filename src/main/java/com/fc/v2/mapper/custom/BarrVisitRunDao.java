package com.fc.v2.mapper.custom;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 复检日跑专用回库口：办成/挂死的回写全从这一处落，
 * 时刻读法也只认调用方传进来的同一个时刻，别处不许另开口子逐桩点账。
 *
 * @author fuce
 * @date 2026-10-05
 */
@Mapper
public interface BarrVisitRunDao {

    /**
     * 办成一桩的两个印子同句落：字样从「搁着」(0) 挪到「已递出」(1)，
     * 同一行补上递出那一刻与经办那位；缺一处这桩都不算走完。
     * 只动还搁着且在册的行；update 真正改动一行（回 1）才算递出。
     * 显式落 update_time、不走 MyBatis-Plus 审计填充：日跑线程没有登录身份，填充会抛异常。
     */
    @Update("UPDATE t_barr_visit_item SET status = 1, delivered_at = #{at}, delivered_by = #{by}, "
            + "stall_reason = NULL, update_time = #{at} "
            + "WHERE id = #{id} AND del_flag = 0 AND status = 0")
    int markDelivered(@Param("id") Long id, @Param("at") Date at, @Param("by") String by);

    /**
     * 挂「递不动」(2)：卡壳那一环连着缘故一起写回行上，日后照着缘故把这一批捞出来重排。
     * 只动还搁着且在册的行；显式落 update_time，绕开登录态填充。
     */
    @Update("UPDATE t_barr_visit_item SET status = 2, stall_reason = #{reason}, update_time = #{at} "
            + "WHERE id = #{id} AND del_flag = 0 AND status = 0")
    int markStalled(@Param("id") Long id, @Param("at") Date at, @Param("reason") String reason);

    /**
     * 法定节假日与区下文停办日并成一册取：数成事日时这两本册上的日子一律不数，
     * 余下的（含寻常周末）一天一天凑。
     */
    @Select("SELECT day FROM t_barr_holiday WHERE day BETWEEN #{from} AND #{to} "
            + "UNION "
            + "SELECT day FROM t_barr_closeday WHERE day BETWEEN #{from} AND #{to}")
    List<LocalDate> listNonWorkingDays(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 库内已递出按经办+季分组点一遍：定额占用只以库里现状为凭，
     * 不许在屏上现轧，也不在别处再起一轮点账。
     */
    @Select("SELECT owner_id AS ownerId, season AS season, COUNT(*) AS cnt "
            + "FROM t_barr_visit_item "
            + "WHERE del_flag = 0 AND status = 1 AND owner_id IS NOT NULL AND season IS NOT NULL "
            + "GROUP BY owner_id, season")
    List<java.util.Map<String, Object>> countDeliveredByOwnerSeason();
}
