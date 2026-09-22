package com.example.auth.framework;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SysSequenceMapper extends BaseMapper<SysSequence> {

    @Update("UPDATE sys_sequence SET current_val = current_val + 1, update_time = NOW() "
            + "WHERE seq_name = #{seqName} AND seq_date = #{seqDate} AND deleted = 0")
    int increment(@Param("seqName") String seqName, @Param("seqDate") String seqDate);
}
