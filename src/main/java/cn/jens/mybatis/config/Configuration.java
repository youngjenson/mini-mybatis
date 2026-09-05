package cn.jens.config;

import cn.jens.mapping.MappedStatement;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 配置信息
 *
 * @author YumJens
 * @date 2026-09-04 23:36
 */
public class Configuration {

    @Setter
    @Getter
    private DataSource dataSource;

    private final Map<String, MappedStatement> mappedStatements
            = new HashMap<>();

    /**
     * 添加MappedStatement
     *
     * @param statementId     语句ID
     * @param mappedStatement 映射语句
     */
    public void addMappedStatement(
            String statementId,
            MappedStatement mappedStatement) {

        mappedStatements.put(statementId, mappedStatement);
    }

    /**
     * 获取MappedStatement
     *
     * @param statementId 语句ID
     * @return 映射语句
     */
    public MappedStatement getMappedStatement(String statementId) {
        return mappedStatements.get(statementId);
    }

}
