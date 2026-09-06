package cn.jens.mybatis.config;

import cn.jens.mybatis.datasource.DataSourceFactory;
import cn.jens.mybatis.exception.PersistenceException;
import cn.jens.mybatis.plugin.Interceptor;
import cn.jens.mybatis.session.LocalCacheScope;
import cn.jens.mybatis.type.JdbcType;
import cn.jens.mybatis.type.TypeHandler;

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
import java.util.Locale;
import java.util.Properties;

/**
 * 解析 mini-mybatis-config.xml。
 *
 * @author YumJens
 */
public class XmlConfigBuilder {

    public Configuration parse(InputStream inputStream) {
        if (inputStream == null) {
            throw new PersistenceException("Configuration input stream must not be null");
        }

        try {
            Document document = newDocumentBuilderFactory()
                    .newDocumentBuilder()
                    .parse(inputStream);
            Element root = document.getDocumentElement();
            Configuration configuration = new Configuration();
            parseSettings(root, configuration);
            parseTypeHandlers(root, configuration);
            parsePlugins(root, configuration);
            parseDataSource(root, configuration);
            parseMappers(root, configuration);
            return configuration;
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new PersistenceException("Failed to parse mini-MyBatis configuration", e);
        }
    }

    private void parsePlugins(Element root, Configuration configuration) {
        NodeList pluginsNodes = root.getElementsByTagName("plugins");
        if (pluginsNodes.getLength() == 0) {
            return;
        }

        Element plugins = (Element) pluginsNodes.item(0);
        NodeList children = plugins.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element plugin && "plugin".equals(plugin.getTagName())) {
                parsePlugin(plugin, configuration);
            }
        }
    }

    private void parsePlugin(Element plugin, Configuration configuration) {
        String className = plugin.getAttribute("interceptor");
        if (className.isBlank()) {
            throw new PersistenceException("Plugin interceptor class must not be blank");
        }

        Interceptor interceptor = createInterceptor(className);
        Properties properties = new Properties();
        NodeList children = plugin.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element property && "property".equals(property.getTagName())) {
                String name = property.getAttribute("name");
                if (name.isBlank()) {
                    throw new PersistenceException("Plugin property name must not be blank");
                }
                properties.setProperty(
                        name,
                        property.getAttribute("value")
                );
            }
        }
        interceptor.setProperties(properties);
        configuration.addInterceptor(interceptor);
    }

    private Interceptor createInterceptor(String className) {
        try {
            Class<?> interceptorType = Class.forName(className);
            if (!Interceptor.class.isAssignableFrom(interceptorType)) {
                throw new PersistenceException(
                        "Plugin must implement Interceptor: " + className
                );
            }
            return (Interceptor) interceptorType.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new PersistenceException("Cannot create plugin: " + className, e);
        }
    }

    private void parseSettings(Element root, Configuration configuration) {
        NodeList settingsNodes = root.getElementsByTagName("settings");
        if (settingsNodes.getLength() == 0) {
            return;
        }

        Element settings = (Element) settingsNodes.item(0);
        NodeList settingNodes = settings.getElementsByTagName("setting");
        for (int index = 0; index < settingNodes.getLength(); index++) {
            Element setting = (Element) settingNodes.item(index);
            applySetting(
                    configuration,
                    setting.getAttribute("name"),
                    setting.getAttribute("value")
            );
        }
    }

    private void applySetting(Configuration configuration, String name, String value) {
        switch (name) {
            case "localCacheScope" -> setLocalCacheScope(configuration, value);
            case "cacheEnabled" -> configuration.setCacheEnabled(
                    parseBooleanSetting(name, value)
            );
            case "jdbcTypeForNull" -> configuration.setJdbcTypeForNull(
                    JdbcType.fromName(value)
            );
            default -> throw new PersistenceException("Unsupported setting: " + name);
        }
    }

    private void parseTypeHandlers(Element root, Configuration configuration) {
        NodeList typeHandlersNodes = root.getElementsByTagName("typeHandlers");
        if (typeHandlersNodes.getLength() == 0) {
            return;
        }

        Element typeHandlers = (Element) typeHandlersNodes.item(0);
        NodeList children = typeHandlers.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (!(child instanceof Element typeHandler)) {
                continue;
            }
            if (!"typeHandler".equals(typeHandler.getTagName())) {
                throw new PersistenceException(
                        "Unsupported element in <typeHandlers>: " + typeHandler.getTagName()
                );
            }
            registerTypeHandler(typeHandler, configuration);
        }
    }

    private void registerTypeHandler(Element element, Configuration configuration) {
        String handlerClassName = element.getAttribute("handler");
        if (handlerClassName.isBlank()) {
            throw new PersistenceException("Type handler class must not be blank");
        }
        TypeHandler<Object> typeHandler = configuration.getTypeHandlerRegistry()
                .resolveTypeHandler(handlerClassName);
        String javaTypeName = element.getAttribute("javaType");
        String jdbcTypeName = element.getAttribute("jdbcType");
        if (javaTypeName.isBlank()) {
            if (!jdbcTypeName.isBlank()) {
                throw new PersistenceException(
                        "typeHandler javaType is required when jdbcType is declared"
                );
            }
            configuration.getTypeHandlerRegistry().register(typeHandler);
            return;
        }

        Class<?> javaType = configuration.getTypeAliasRegistry().resolveAlias(javaTypeName);
        JdbcType jdbcType = jdbcTypeName.isBlank()
                ? null
                : JdbcType.fromName(jdbcTypeName);
        configuration.getTypeHandlerRegistry().register(javaType, jdbcType, typeHandler);
    }

    private void setLocalCacheScope(Configuration configuration, String value) {
        try {
            configuration.setLocalCacheScope(
                    LocalCacheScope.valueOf(value.toUpperCase(Locale.ROOT))
            );
        } catch (IllegalArgumentException e) {
            throw new PersistenceException("Invalid localCacheScope: " + value, e);
        }
    }

    private boolean parseBooleanSetting(String name, String value) {
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new PersistenceException("Invalid " + name + ": " + value);
        }
        return Boolean.parseBoolean(value);
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

    private void parseDataSource(Element root, Configuration configuration) {
        Element dataSource = requiredChild(root, "dataSource");
        NodeList propertyNodes = dataSource.getElementsByTagName("property");
        Properties properties = new Properties();
        for (int index = 0; index < propertyNodes.getLength(); index++) {
            Element property = (Element) propertyNodes.item(index);
            properties.setProperty(
                    property.getAttribute("name"),
                    property.getAttribute("value")
            );
        }
        configuration.setDataSource(new DataSourceFactory().create(properties));
    }

    private void parseMappers(Element root, Configuration configuration) {
        NodeList mappersNodes = root.getElementsByTagName("mappers");
        if (mappersNodes.getLength() == 0) {
            return;
        }

        Element mappers = (Element) mappersNodes.item(0);
        NodeList children = mappers.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (!(child instanceof Element mapper) || !"mapper".equals(mapper.getTagName())) {
                continue;
            }
            parseMapper(mapper, configuration);
        }
    }

    private void parseMapper(Element mapper, Configuration configuration) {
        String className = mapper.getAttribute("class");
        String resource = mapper.getAttribute("resource");
        if (!className.isBlank() && !resource.isBlank()) {
            throw new PersistenceException("Mapper must declare either class or resource, not both");
        }
        if (!className.isBlank()) {
            configuration.addMapper(loadMapperType(className));
            return;
        }
        if (resource.isBlank()) {
            throw new PersistenceException("Mapper must declare class or resource");
        }

        try (InputStream inputStream = getResourceAsStream(resource)) {
            new XmlMapperBuilder(configuration).parse(inputStream, resource);
        } catch (IOException e) {
            throw new PersistenceException("Failed to close mapper resource: " + resource, e);
        }
    }

    private InputStream getResourceAsStream(String resource) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(resource);
        if (inputStream == null) {
            throw new PersistenceException("Mapper resource not found: " + resource);
        }
        return inputStream;
    }

    private Class<?> loadMapperType(String className) {
        if (className == null || className.isBlank()) {
            throw new PersistenceException("Mapper class must not be blank");
        }
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new PersistenceException("Mapper class not found: " + className, e);
        }
    }

    private Element requiredChild(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            throw new PersistenceException("Missing configuration element: " + tagName);
        }
        return (Element) nodes.item(0);
    }
}
