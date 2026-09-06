package cn.jens.mybatis.scripting.xmltags;

import cn.jens.mybatis.exception.PersistenceException;
import cn.jens.mybatis.scripting.DynamicSqlSource;
import cn.jens.mybatis.scripting.SqlSource;
import cn.jens.mybatis.scripting.StaticSqlSource;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

/** 把 XML statement 的文本与动态标签解析为 SqlNode 树。 */
public class XmlScriptBuilder {

    private final String resource;

    public XmlScriptBuilder(String resource) {
        this.resource = resource;
    }

    public SqlSource parse(Element statement) {
        ParsedNodes parsedNodes = parseChildren(statement);
        if (!parsedNodes.dynamic()) {
            return new StaticSqlSource(statement.getTextContent().strip());
        }
        return new DynamicSqlSource(new MixedSqlNode(parsedNodes.nodes()));
    }

    private ParsedNodes parseChildren(Element parent) {
        List<SqlNode> nodes = new ArrayList<>();
        boolean dynamic = false;
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child.getNodeType() == Node.TEXT_NODE
                    || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                if (!child.getTextContent().isBlank()) {
                    nodes.add(new StaticTextSqlNode(child.getTextContent()));
                }
                continue;
            }
            if (child instanceof Element element) {
                nodes.add(parseDynamicElement(element));
                dynamic = true;
            }
        }
        return new ParsedNodes(nodes, dynamic);
    }

    private SqlNode parseDynamicElement(Element element) {
        return switch (element.getTagName()) {
            case "if" -> new IfSqlNode(
                    mixedContents(element),
                    requiredAttribute(element, "test")
            );
            case "where" -> new WhereSqlNode(mixedContents(element));
            case "foreach" -> parseForEach(element);
            default -> throw new PersistenceException(
                    "Unsupported dynamic SQL element <" + element.getTagName()
                            + "> in " + resource
            );
        };
    }

    private SqlNode parseForEach(Element element) {
        String item = requiredAttribute(element, "item");
        String index = optionalAttribute(element, "index");
        validateVariableName(item, "item");
        validateVariableName(index, "index");
        if (item.equals(index)) {
            throw new PersistenceException("foreach item and index must be different");
        }
        return new ForEachSqlNode(
                mixedContents(element),
                requiredAttribute(element, "collection"),
                item,
                index,
                optionalAttribute(element, "open"),
                optionalAttribute(element, "close"),
                optionalAttribute(element, "separator"),
                parseNullable(element)
        );
    }

    private SqlNode mixedContents(Element element) {
        return new MixedSqlNode(parseChildren(element).nodes());
    }

    private boolean parseNullable(Element element) {
        String value = element.getAttribute("nullable");
        if (value.isBlank()) {
            return false;
        }
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new PersistenceException(
                    "Invalid foreach nullable value in " + resource + ": " + value
            );
        }
        return Boolean.parseBoolean(value);
    }

    private void validateVariableName(String name, String attribute) {
        if (name == null) {
            return;
        }
        if (!name.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            throw new PersistenceException(
                    "Invalid foreach " + attribute + " name in " + resource + ": " + name
            );
        }
    }

    private String requiredAttribute(Element element, String name) {
        String value = element.getAttribute(name);
        if (value.isBlank()) {
            throw new PersistenceException(
                    "Missing attribute '" + name + "' on <" + element.getTagName()
                            + "> in " + resource
            );
        }
        return value;
    }

    private String optionalAttribute(Element element, String name) {
        String value = element.getAttribute(name);
        return value.isBlank() ? null : value;
    }

    private record ParsedNodes(List<SqlNode> nodes, boolean dynamic) {
    }
}
