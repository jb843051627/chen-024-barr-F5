package com.fc.v2.mapper.custom;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 当场验收记录号按日取号（YS-yyyyMMdd-xxxx）。
 * 与进度卡季取号同一套数法：INSERT IGNORE 保底有行，UPDATE … LAST_INSERT_ID 本连接内取号，
 * 并发各拿各的号，受理簿上没有自带号这一说。
 *
 * @author fuce
 * @date 2026-10-05
 */
@Mapper
public interface BarrAcceptSeqDao {

    /** 该日取号行没有就先立一行（0 起），有则不动 */
    @Insert("INSERT IGNORE INTO t_barr_accept_seq(day, next_val) VALUES(#{day}, 0)")
    int ensureDay(@Param("day") String day);

    /** 本连接内把号往前挪一格，并把挪到的值记进 LAST_INSERT_ID */
    @Update("UPDATE t_barr_accept_seq SET next_val = LAST_INSERT_ID(next_val + 1) WHERE day = #{day}")
    int bump(@Param("day") String day);

    /** 取出本连接刚挪到的号 */
    @Select("SELECT LAST_INSERT_ID()")
    long currentValue();
}
