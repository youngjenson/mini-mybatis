package cn.jens;

import cn.jens.demo.mapper.UserMapper;
import cn.jens.mybatis.exception.PersistenceException;
import cn.jens.mybatis.session.SqlSession;
import cn.jens.mybatis.session.SqlSessionFactory;
import cn.jens.mybatis.session.SqlSessionFactoryBuilder;

import java.io.InputStream;

/**
 * mini-MyBatis 示例入口。
 *
 * @author YumJens
 */
public class MiniMybatisMain {

    static void main(String[] args) {
        try (InputStream inputStream = MiniMybatisMain.class.getClassLoader()
                .getResourceAsStream("mini-mybatis-config.xml")) {
            if (inputStream == null) {
                throw new PersistenceException("mini-mybatis-config.xml not found");
            }

            SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(inputStream);
            try (SqlSession session = factory.openSession()) {
                UserMapper userMapper = session.getMapper(UserMapper.class);
                userMapper.selectList().forEach(System.out::println);
                System.out.println(userMapper.selectById(2));
            }
        } catch (Exception e) {
            throw new PersistenceException("Failed to run mini-MyBatis demo", e);
        }
    }
}
