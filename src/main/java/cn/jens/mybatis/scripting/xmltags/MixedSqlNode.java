package cn.jens.mybatis.scripting.xmltags;

import java.util.List;

/** 按顺序执行一组 SQL 节点。 */
public class MixedSqlNode implements SqlNode {

    private final List<SqlNode> contents;

    public MixedSqlNode(List<SqlNode> contents) {
        this.contents = List.copyOf(contents);
    }

    @Override
    public void apply(DynamicContext context) {
        contents.forEach(sqlNode -> sqlNode.apply(context));
    }
}
