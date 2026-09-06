package cn.jens.mybatis.config;

import cn.jens.mybatis.cache.PerpetualCache;
import cn.jens.mybatis.exception.PersistenceException;
import cn.jens.mybatis.mapping.MappedStatement;
import cn.jens.mybatis.mapping.ResultMap;
import cn.jens.mybatis.mapping.ResultMapping;
import cn.jens.mybatis.mapping.SqlCommandType;
import cn.jens.mybatis.scripting.SqlSource;
import cn.jens.mybatis.scripting.xmltags.XmlScriptBuilder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** 解析 XML Mapper 并注册映射元数据。 */
public class XmlMapperBuilder {

    private final Configuration configuration;

    public XmlMapperBuilder(Configuration configuration) {
        this.configuration = configuration;
    }

    public void parse(InputStream inputStream, String resource) {
        try {
            Document document = newDocumentBuilderFactory()
                    .newDocumentBuilder()
                    .parse(inputStream);
            Element mapper = document.getDocumentElement();
            if (!"mapper".equals(mapper.getTagName())) {
                throw new PersistenceException("Root element must be <mapper> in " + resource);
            }
            String namespace = requiredAttribute(mapper, "namespace", resource);
            registerMapperInterface(namespace);
            parseCache(mapper, namespace, resource);
            parseResultMaps(mapper, namespace, resource);
            parseStatements(mapper, namespace, resource);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new PersistenceException("Failed to parse XML mapper: " + resource, e);
        }
    }

    private void parseCache(Element mapper, String namespace, String resource) {
        List<Element> cacheElements = directChildren(mapper, "cache");
        if (cacheElements.size() > 1) {
            throw new PersistenceException("Mapper can declare only one <cache>: " + resource);
        }
        if (!cacheElements.isEmpty()) {
            configuration.addCache(new PerpetualCache(namespace));
        }
    }

    private void parseResultMaps(Element mapper, String namespace, String resource) {
        for (Element element : directChildren(mapper, "resultMap")) {
            String localId = requiredAttribute(element, "id", resource);
            String resultMapId = namespace + "." + localId;
            Class<?> type = resolveType(requiredAttribute(element, "type", resource));
            List<ResultMapping> mappings = new ArrayList<>();

            for (Element child : directChildren(element, null)) {
                if (!"id".equals(child.getTagName()) && !"result".equals(child.getTagName())) {
                    continue;
                }
                mappings.add(new ResultMapping(
                        requiredAttribute(child, "property", resource),
                        requiredAttribute(child, "column", resource),
                        "id".equals(child.getTagName())
                ));
            }
            configuration.addResultMap(resultMapId, new ResultMap(resultMapId, type, mappings));
        }
    }

    private void parseStatements(Element mapper, String namespace, String resource) {
        for (Element element : directChildren(mapper, null)) {
            SqlCommandType commandType = resolveCommandType(element.getTagName());
            if (commandType == null) {
                continue;
            }

            String localId = requiredAttribute(element, "id", resource);
            String statementId = namespace + "." + localId;
            String sql = element.getTextContent().strip();
            if (sql.isBlank()) {
                throw new PersistenceException("SQL must not be blank: " + statementId);
            }
            SqlSource sqlSource = new XmlScriptBuilder(resource).parse(element);
            ResultMap resultMap = resolveResultMap(element, namespace, commandType, resource);
            Class<?> resultType = resolveResultType(element, resultMap, commandType, resource);
            configuration.addMappedStatement(
                    statementId,
                    new MappedStatement(
                            statementId,
                            sql,
                            resultType,
                            commandType,
                            resultMap,
                            resolveFlushCache(element, commandType, resource),
                            resolveUseCache(element, commandType, resource),
                            sqlSource
                    )
            );
        }
    }

    private boolean resolveUseCache(
            Element statement,
            SqlCommandType commandType,
            String resource) {
        String value = statement.getAttribute("useCache");
        if (value.isBlank()) {
            return commandType == SqlCommandType.SELECT;
        }
        if (commandType != SqlCommandType.SELECT) {
            throw new PersistenceException("useCache is only valid for select in " + resource);
        }
        return parseBooleanAttribute("useCache", value, resource);
    }

    private boolean resolveFlushCache(
            Element statement,
            SqlCommandType commandType,
            String resource) {
        String value = statement.getAttribute("flushCache");
        if (value.isBlank()) {
            return commandType != SqlCommandType.SELECT;
        }
        return parseBooleanAttribute("flushCache", value, resource);
    }

    private boolean parseBooleanAttribute(String name, String value, String resource) {
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new PersistenceException(
                    "Invalid " + name + " value in " + resource + ": " + value
            );
        }
        return Boolean.parseBoolean(value);
    }

    private ResultMap resolveResultMap(
            Element statement,
            String namespace,
            SqlCommandType commandType,
            String resource) {
        String resultMapId = statement.getAttribute("resultMap");
        if (resultMapId.isBlank()) {
            return null;
        }
        if (commandType != SqlCommandType.SELECT) {
            throw new PersistenceException("resultMap is only valid for select in " + resource);
        }
        String qualifiedId = resultMapId.contains(".")
                ? resultMapId
                : namespace + "." + resultMapId;
        return configuration.getResultMap(qualifiedId);
    }

    private Class<?> resolveResultType(
            Element statement,
            ResultMap resultMap,
            SqlCommandType commandType,
            String resource) {
        if (commandType != SqlCommandType.SELECT) {
            return void.class;
        }
        String resultTypeName = statement.getAttribute("resultType");
        if (resultMap != null && !resultTypeName.isBlank()) {
            throw new PersistenceException(
                    "select cannot declare both resultType and resultMap in " + resource
            );
        }
        if (resultMap != null) {
            return resultMap.type();
        }
        if (resultTypeName.isBlank()) {
            throw new PersistenceException(
                    "select requires resultType or resultMap in " + resource
            );
        }
        return resolveType(resultTypeName);
    }

    private SqlCommandType resolveCommandType(String tagName) {
        return switch (tagName) {
            case "select" -> SqlCommandType.SELECT;
            case "insert" -> SqlCommandType.INSERT;
            case "update" -> SqlCommandType.UPDATE;
            case "delete" -> SqlCommandType.DELETE;
            default -> null;
        };
    }

    private Class<?> resolveType(String typeName) {
        return switch (typeName) {
            case "int", "integer" -> Integer.class;
            case "long" -> Long.class;
            case "string" -> String.class;
            case "boolean" -> Boolean.class;
            default -> loadClass(typeName, "Type class not found: ");
        };
    }

    private void registerMapperInterface(String namespace) {
        Class<?> mapperType = loadClass(namespace, "Mapper namespace class not found: ");
        configuration.addMapper(mapperType);
    }

    private Class<?> loadClass(String className, String messagePrefix) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new PersistenceException(messagePrefix + className, e);
        }
    }

    private String requiredAttribute(Element element, String name, String resource) {
        String value = element.getAttribute(name);
        if (value.isBlank()) {
            throw new PersistenceException(
                    "Missing attribute '" + name + "' on <" + element.getTagName()
                            + "> in " + resource
            );
        }
        return value;
    }

    private List<Element> directChildren(Element parent, String tagName) {
        List<Element> children = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            if (node instanceof Element element
                    && (tagName == null || tagName.equals(element.getTagName()))) {
                children.add(element);
            }
        }
        return children;
    }

    private DocumentBuilderFactory newDocumentBuilderFactory()
            throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setExpandEntityReferences(false);
        return factory;
    }
}
