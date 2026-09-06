package cn.jens.mybatis.scripting.xmltags;

/** 动态 SQL 语法树节点。 */
public interface SqlNode {

    void apply(DynamicContext context);
}
