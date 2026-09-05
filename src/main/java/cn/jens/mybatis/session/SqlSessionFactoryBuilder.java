package cn.jens.mybatis.session;

import cn.jens.mybatis.config.Configuration;
import cn.jens.mybatis.config.XmlConfigBuilder;

import java.io.InputStream;

/**
 * 从 XML 配置构建 SqlSessionFactory。
 *
 * @author YumJens
 */
public class SqlSessionFactoryBuilder {

    public SqlSessionFactory build(InputStream inputStream) {
        Configuration configuration = new XmlConfigBuilder().parse(inputStream);
        return new DefaultSqlSessionFactory(configuration);
    }
}
