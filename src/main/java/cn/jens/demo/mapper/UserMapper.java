package cn.jens.mybatis.mapper;

import cn.jens.mybatis.annotation.Select;
import cn.jens.mybatis.entity.User;

import java.util.List;

/**
 * 用户Mapper类
 * @author YumJens
 * @date 2026-09-05 15:27
 */
public interface UserMapper {

    @Select("select * from user")
    List<User> selectList();
}
