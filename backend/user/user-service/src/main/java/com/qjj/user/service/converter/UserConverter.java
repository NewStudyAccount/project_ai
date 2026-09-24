package com.qjj.user.service.converter;

import com.qjj.user.api.dto.UserBasicVO;
import com.qjj.user.api.dto.UserProfileVO;
import com.qjj.user.service.entity.SysUser;
import com.qjj.user.service.entity.SysUserProfile;
import com.qjj.user.service.vo.UserVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * MapStruct 转换唯一落点。
 */
@Mapper(componentModel = "spring")
public interface UserConverter {

    @Mapping(target = "id", source = "id", qualifiedByName = "longToString")
    UserVO toUserVO(SysUser user);

    @Mapping(target = "id", source = "id", qualifiedByName = "longToString")
    UserBasicVO toBasicVO(SysUser user);

    @Mapping(target = "id", source = "user.id", qualifiedByName = "longToString")
    @Mapping(target = "gender", source = "profile.gender")
    @Mapping(target = "birthday", source = "profile.birthday", qualifiedByName = "dateToString")
    @Mapping(target = "address", source = "profile.address")
    @Mapping(target = "extraJson", source = "profile.extraJson")
    UserProfileVO toProfileVO(SysUser user, SysUserProfile profile);

    @Named("longToString")
    default String longToString(Long value) {
        return value == null ? "" : String.valueOf(value);
    }

    @Named("dateToString")
    default String dateToString(java.time.LocalDateTime value) {
        return value == null ? "" : value.toString();
    }
}
