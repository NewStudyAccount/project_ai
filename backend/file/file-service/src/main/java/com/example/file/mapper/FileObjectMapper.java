package com.example.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.file.entity.FileObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface FileObjectMapper extends BaseMapper<FileObject> {

    /** 含逻辑删行查询（覆盖上传需复活同 key 元数据）。 */
    @Select("SELECT * FROM file_object WHERE object_key = #{objectKey} LIMIT 1")
    FileObject selectByObjectKeyAny(@Param("objectKey") String objectKey);

    /** 按 id 更新元数据并复活（绕过逻辑删过滤，覆盖上传用）。 */
    @Update("UPDATE file_object SET bucket = #{bucket}, url = #{url}, content_type = #{contentType}, "
            + "size = #{size}, owner_id = #{ownerId}, biz_type = #{bizType}, biz_id = #{bizId}, "
            + "scene = #{scene}, update_time = #{updateTime}, update_by = #{updateBy}, deleted = 0 "
            + "WHERE id = #{id}")
    int updateMetaRevive(FileObject row);

    /** 逻辑删除。 */
    @Update("UPDATE file_object SET deleted = 1, update_time = #{updateTime}, update_by = #{updateBy} "
            + "WHERE id = #{id} AND deleted = 0")
    int logicDeleteById(@Param("id") Long id,
                        @Param("updateTime") java.time.LocalDateTime updateTime,
                        @Param("updateBy") Long updateBy);
}
