package cn.jens.mybatis.mapping;

import java.util.List;

/**
 * 一组命名的结果映射规则。
 *
 * @param id 完整 resultMap ID
 * @param type 目标 Java 类型
 * @param resultMappings 字段映射列表
 */
public record ResultMap(String id, Class<?> type, List<ResultMapping> resultMappings) {

    public ResultMap {
        resultMappings = List.copyOf(resultMappings);
    }
}
