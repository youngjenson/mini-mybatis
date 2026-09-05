package cn.jens.mybatis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * 测试JDBC
 * @author YumJens
 * @date 2026-09-05 15:10
 */
public class TestJdbc {

    @Test
    @Disabled("手工 JDBC 对照实验，需要本地 MySQL；自动化链路由 MiniMyBatisIntegrationTest 覆盖")
    public void test() throws Exception {

        // 记载驱动
        Class.forName("com.mysql.cj.jdbc.Driver");

        // 获取连接
        Connection connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/test?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=UTC", "root", "root");
        // 创建PreparedStatement
        PreparedStatement ps = connection.prepareStatement("select * from user");
        ps.execute();

        // 获取结果集
        ResultSet resultSet = ps.getResultSet();
        while (resultSet.next()) {
            System.out.println(resultSet.getString("name"));
        }

        // 释放资源
        resultSet.close();
        ps.close();
        connection.close();
    }
}
