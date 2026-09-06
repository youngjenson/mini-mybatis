package cn.jens.demo.mapper;

import cn.jens.demo.entity.User;
import cn.jens.mybatis.annotation.Param;

import java.util.List;

/** 使用 XML 定义 SQL 的用户 Mapper。 */
public interface UserXmlMapper {

    List<User> selectList();

    User selectById(@Param("id") Integer id);

    User selectFreshById(@Param("id") Integer id);

    User selectWithoutCacheById(@Param("id") Integer id);

    List<User> selectDynamic(
            @Param("name") String name,
            @Param("minAge") Integer minAge
    );

    List<User> selectByIds(@Param("ids") List<Integer> ids);

    Integer count();

    int insert(User user);

    boolean update(User user);

    int deleteById(@Param("id") Integer id);
}
