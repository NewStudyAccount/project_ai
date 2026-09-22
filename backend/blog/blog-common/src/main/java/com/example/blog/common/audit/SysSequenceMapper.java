package com.example.blog.common.audit;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SysSequenceMapper {

    @Update("UPDATE sys_sequence SET current_val = current_val + 1, update_time = NOW() "
            + "WHERE seq_name = #{seqName} AND seq_date = #{seqDate} AND deleted = 0")
    int increment(@Param("seqName") String seqName, @Param("seqDate") String seqDate);

    @Insert("INSERT INTO sys_sequence (id, seq_name, seq_date, current_val, create_time, update_time, "
            + "create_by, update_by, deleted) VALUES (#{id}, #{seqName}, #{seqDate}, #{currentVal}, "
            + "#{createTime}, #{updateTime}, #{createBy}, #{updateBy}, #{deleted})")
    int insert(SysSequence row);

    @Select("SELECT current_val FROM sys_sequence WHERE seq_name = #{seqName} AND seq_date = #{seqDate} AND deleted = 0")
    Long selectCurrentVal(@Param("seqName") String seqName, @Param("seqDate") String seqDate);
}
