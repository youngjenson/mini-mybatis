package cn.jens.demo.mapper;

import cn.jens.mybatis.annotation.Delete;
import cn.jens.mybatis.annotation.Insert;
import cn.jens.mybatis.annotation.Param;
import cn.jens.mybatis.annotation.Select;
import cn.jens.mybatis.annotation.Update;
import cn.jens.demo.entity.User;

import java.util.List;

/**
 * 用户Mapper类
 * @author YumJens
 * @date 2026-09-05 15:27
 */
public interface UserMapper {

    @Select("select id, name, age from user order by id")
    List<User> selectList();

    @Select("select id, name, age from user where id = #{id}")
    User selectById(@Param("id") Integer id);

    @Select("select id, name, age from user where name = #{name} and age = #{age}")
    User selectByNameAndAge(@Param("name") String name, @Param("age") Integer age);

    @Select("select count(*) from user")
    Integer count();

    @Insert("insert into user (id, name, age) values (#{id}, #{name}, #{age})")
    int insert(User user);

    @Update("update user set name = #{name}, age = #{age} where id = #{id}")
    boolean update(User user);

    @Delete("delete from user where id = #{id}")
    int deleteById(@Param("id") Integer id);
}
