package io.kaoto.forage.maven.catalog;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.logging.Log;
import io.kaoto.forage.catalog.model.ConditionalBeanGroup;
import io.kaoto.forage.catalog.model.ConditionalBeanInfo;
import io.kaoto.forage.catalog.model.ConfigEntry;
import io.kaoto.forage.core.annotations.FactoryType;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.stmt.ReturnStmt;

public class CodeScanner {
    private static final Set<String> SKIP_DIRS = Set.of("target", ".git", "node_modules", ".idea", ".vscode");

    private final Log log;
    private final Map<String, Path> sourceDirectoryCache = new ConcurrentHashMap<>();
    private final ThreadLocal<JavaParser> parserLocal = ThreadLocal.withInitial(CodeScanner::createConfiguredParser);
    private final DocumentBuilderFactory documentBuilderFactory;

    public CodeScanner(Log log) {
        this.log = log;
        this.documentBuilderFactory = createDocumentBuilderFactory();
    }

    /**
     * Creates a JavaParser with optimized configuration for better performance.
     * Disables features not needed for annotation scanning.
     * Each thread gets its own instance via ThreadLocal since JavaParser is not thread-safe.
     */
    private static JavaParser createConfiguredParser() {
        ParserConfiguration config = new ParserConfiguration();
        // Use Java 17 language level for modern syntax support
        config.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        // Disable attribute comments processing for better performance
        config.setAttributeComments(false);
        // Disable storing tokens for better memory usage
        config.setStoreTokens(false);
        return new JavaParser(config);
    }

    /**
     * Creates a DocumentBuilderFactory with XXE protection, reused for all POM parsing.
     */
    private static DocumentBuilderFactory createDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception e) {
            // Should not happen with standard JDK XML implementations
            throw new IllegalStateException("Failed to configure XML parser security features", e);
        }
        return factory;
    }

    /**
     * Scans the artifact source directory in a single pass, extracting all information at once.
     * This is a performance optimization to avoid multiple directory walks and file parsing.
     * (Issue 2 optimization)
     */
    public ScanResult scanAllInOnePass(Artifact artifact, Path rootDir) {
        ScanResult result = new ScanResult();

        // Find the module directory (cached), then narrow to src/main/java
        Path moduleDir = findSourceDirectory(artifact, rootDir);
        if (moduleDir == null || !Files.exists(moduleDir)) {
            log.debug("No source directory found for artifact: " + artifact.getArtifactId());
            return result;
        }

        Path sourceDir = moduleDir.resolve("src").resolve("main").resolve("java");
        if (!Files.exists(sourceDir)) {
            log.debug("No src/main/java in module: " + artifact.getArtifactId());
            return result;
        }

        try {
            log.debug("Scanning source directory: " + sourceDir);

            // Walk through all Java files and extract all information in parallel.
            // ScanResult uses thread-safe collections and each thread gets its own
            // JavaParser instance via ThreadLocal, so parallel processing is safe.
            try (Stream<Path> paths = Files.walk(sourceDir)) {
                paths.filter(path -> path.toString().endsWith(".java"))
                        .parallel()
                        .forEach(javaFile -> {
                            try {
                                scanJavaFileForAllInfo(javaFile, result);
                            } catch (Exception e) {
                                log.warn("Failed to parse Java file: " + javaFile);
                                log.debug("Parse error details: " + e.getMessage(), e);
                            }
                        });
            }
        } catch (IOException e) {
            log.warn("Failed to scan source directory: " + sourceDir);
            log.debug("Scan error details: " + e.getMessage(), e);
        }

        if (!result.getBeans().isEmpty()
                || !result.getFactories().isEmpty()
                || !result.getConfigClasses().isEmpty()) {
            log.info("Scanned " + artifact.getArtifactId() + ": "
                    + result.getBeans().size()
                    + " beans, " + result.getFactories().size() + " factories, "
                    + result.getConfigProperties().size() + " config properties, "
                    + result.getConfigClasses().size() + " config classes");
        } else {
            log.debug("Scanned " + artifact.getArtifactId() + ": no annotations found");
        }

        return result;
    }

    /**
     * Scans a single Java file for ALL information types in one pass.
     * This replaces the need for separate scanJavaFileForForgeBeans, scanJavaFileForForageFactories, etc.
     */
    private void scanJavaFileForAllInfo(Path javaFile, ScanResult result) {
        try {
            Optional<CompilationUnit> parseResult =
                    parserLocal.get().parse(javaFile).getResult();

            if (parseResult.isEmpty()) {
                log.debug("Failed to parse Java file: " + javaFile);
                return;
            }

            CompilationUnit cu = parseResult.get();

            // Extract all information in a single pass through the AST
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
                // 1. Check for @ForageBean annotations
                classDecl.getAnnotations().forEach(annotation -> {
                    if (isForageBeanAnnotation(annotation)) {
                        ScannedBean bean = extractScannedBean(annotation, classDecl, cu);
                        if (bean != null) {
                            result.getBeans().add(bean);
                            log.debug("Found ForageBean: " + bean);
                        }
                    }
                    // 2. Check for @ForageFactory annotations
                    if (isForageFactoryAnnotation(annotation)) {
                        ScannedFactory factory = extractScannedFactory(annotation, classDecl, cu);
                        if (factory != null) {
                            result.getFactories().add(factory);
                            log.debug("Found ForageFactory: " + factory);
                        }
                    }
                });

                // 3. Check if class extends ConfigEntries
                if (extendsConfigEntries(classDecl)) {
                    log.debug("Found ConfigEntries class: " + classDecl.getNameAsString());
                    extractConfigModuleFields(classDecl, result.getConfigProperties());
                }

                // 4. Check if class implements Config
                if (implementsConfig(classDecl)) {
                    String className = getFullyQualifiedClassName(classDecl, cu);
                    String configName = extractConfigName(classDecl);

                    if (configName != null) {
                        result.getConfigClasses().put(className, configName);
                        log.info("Found Config: " + className + " -> " + configName);
                    }
                }
            });

        } catch (Exception e) {
            log.warn("Error parsing Java file: " + javaFile);
            log.debug("Parse error details: " + e.getMessage(), e);
        }
    }

    /**
     * Finds the source directory for the given artifact within the project structure.
     * Uses a pre-built directory map (populated on first call) to avoid repeated tree walks.
     */
    private Path findSourceDirectory(Artifact artifact, Path rootDir) {
        String artifactId = artifact.getArtifactId();

        // Build directory map on first call
        if (sourceDirectoryCache.isEmpty()) {
            buildDirectoryMap(rootDir);
        }

        Path result = sourceDirectoryCache.get(artifactId);
        if (result == null) {
            log.info("No source directory found for artifact: " + artifactId);
        }
        return result;
    }

    /**
     * Walks the project tree once and builds a map of artifactId -> module directory.
     * Skips .git, target, and other non-source directories for performance.
     * Uses POM-based matching: finds pom.xml files and extracts their artifactId.
     */
    private void buildDirectoryMap(Path rootDir) {
        log.info("Building source directory map from: " + rootDir);

        Map<String, Path> pomBasedMap = new HashMap<>();
        List<Path> directories = new ArrayList<>();

        try {
            Files.walkFileTree(rootDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String dirName = dir.getFileName().toString();
                    if (SKIP_DIRS.contains(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    directories.add(dir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.getFileName().toString().equals("pom.xml")) {
                        String aid = extractArtifactIdFromPom(file);
                        if (aid != null) {
                            pomBasedMap.put(aid, file.getParent());
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    log.debug("Failed to visit: " + file + " (" + exc.getMessage() + ")");
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("Error walking directory tree: " + rootDir, e);
        }

        // Build the cache: prefer POM-based match (most reliable), fall back to directory name match
        for (Path dir : directories) {
            String dirName = dir.getFileName().toString();
            if (!sourceDirectoryCache.containsKey(dirName) && !pomBasedMap.containsKey(dirName)) {
                // Store directory name match as fallback (only if no POM match exists)
                sourceDirectoryCache.put(dirName, dir);
            }
        }
        // POM-based matches override directory name matches
        sourceDirectoryCache.putAll(pomBasedMap);

        log.info("Source directory map built with " + sourceDirectoryCache.size() + " entries (from "
                + pomBasedMap.size() + " POM files and " + directories.size() + " directories)");
    }

    /**
     * Extracts the artifactId from a pom.xml file's direct child element.
     * Returns null if the POM cannot be parsed or has no artifactId.
     * Reuses the shared DocumentBuilderFactory for performance.
     */
    private String extractArtifactIdFromPom(Path pomPath) {
        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document doc = builder.parse(pomPath.toFile());

            Element root = doc.getDocumentElement();
            if (!"project".equals(root.getTagName())) {
                return null;
            }

            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element child && "artifactId".equals(child.getTagName())) {
                    return child.getTextContent().trim();
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse POM file: " + pomPath);
        }
        return null;
    }

    /**
     * Checks if the annotation is a @ForageFactory annotation.
     */
    private boolean isForageFactoryAnnotation(AnnotationExpr annotation) {
        String name = annotation.getNameAsString();
        return "ForageFactory".equals(name) || Constants.FORAGE_FACTORY_FQN.equals(name);
    }

    /**
     * Checks if the annotation is a @ForageBean annotation.
     */
    private boolean isForageBeanAnnotation(AnnotationExpr annotation) {
        String name = annotation.getNameAsString();
        return "ForageBean".equals(name) || Constants.FORAGE_BEAN_FQN.equals(name);
    }

    /**
     * Extracts ScannedFactory information from the annotation.
     */
    private ScannedFactory extractScannedFactory(
            AnnotationExpr annotation, ClassOrInterfaceDeclaration classDecl, CompilationUnit cu) {
        FactoryAnnotationData data = new FactoryAnnotationData();

        // Extract annotation values
        if (annotation instanceof SingleMemberAnnotationExpr singleMember) {
            if (singleMember.getMemberValue() instanceof StringLiteralExpr stringLiteral) {
                data.setName(stringLiteral.asString());
            }
        } else if (annotation instanceof NormalAnnotationExpr normalAnnotation) {
            extractFactoryAnnotationPairs(normalAnnotation, cu, data);
        }

        // Get the fully qualified class name
        String className = getFullyQualifiedClassName(classDecl, cu);

        ScannedFactory factory = new ScannedFactory(
                data.getName(),
                data.getComponents(),
                data.getDescription(),
                data.getFactoryType(),
                className,
                data.isAutowired());
        if (!data.getConditionalBeans().isEmpty()) {
            factory.setConditionalBeans(data.getConditionalBeans());
        }
        factory.setVariant(data.getVariant());
        factory.setConfigClassName(data.getConfigClassName());
        if (!data.getRuntimeDependencies().isEmpty()) {
            factory.setRuntimeDependencies(data.getRuntimeDependencies());
        }
        return factory;
    }

    /**
     * Extracts annotation member-value pairs from a NormalAnnotationExpr for ForageFactory.
     */
    private void extractFactoryAnnotationPairs(
            NormalAnnotationExpr normalAnnotation, CompilationUnit cu, FactoryAnnotationData data) {
        for (MemberValuePair pair : normalAnnotation.getPairs()) {
            String pairName = pair.getNameAsString();
            Expression value = pair.getValue();

            switch (pairName) {
                case "value" -> {
                    if (value instanceof StringLiteralExpr stringLiteral) {
                        data.setName(stringLiteral.asString());
                    }
                }
                case "components" -> data.setComponents(extractStringArrayValue(value));
                case "description" -> {
                    if (value instanceof StringLiteralExpr stringLiteral) {
                        data.setDescription(stringLiteral.asString());
                    }
                }
                case "type" -> data.setFactoryType(extractFactoryTypeValue(value));
                case "autowired" -> {
                    if (value instanceof BooleanLiteralExpr booleanLiteral) {
                        data.setAutowired(booleanLiteral.getValue());
                    }
                }
                case "conditionalBeans" -> data.setConditionalBeans(extractConditionalBeanGroupArray(value));
                case "variant" -> data.setVariant(extractVariantValue(value));
                case "configClass" -> data.setConfigClassName(extractClassValue(value, cu));
                case "runtimeDependencies" -> data.setRuntimeDependencies(extractStringArrayValue(value));
                default -> {}
            }
        }
    }

    /**
     * Extracts an array of ConditionalBeanGroup from an annotation value.
     */
    private List<ConditionalBeanGroup> extractConditionalBeanGroupArray(Expression value) {
        List<ConditionalBeanGroup> groups = new ArrayList<>();

        if (value instanceof ArrayInitializerExpr arrayExpr) {
            for (Expression element : arrayExpr.getValues()) {
                if (element instanceof NormalAnnotationExpr normalAnnotation) {
                    ConditionalBeanGroup group = extractConditionalBeanGroupInfo(normalAnnotation);
                    if (group != null) {
                        groups.add(group);
                    }
                }
            }
        }

        return groups;
    }

    /**
     * Extracts ConditionalBeanGroup from a @ConditionalBeanGroup annotation.
     */
    private ConditionalBeanGroup extractConditionalBeanGroupInfo(NormalAnnotationExpr annotation) {
        String id = "";
        String description = "";
        String configEntry = "";
        List<ConditionalBeanInfo> beans = new ArrayList<>();
        List<String> runtimeDeps = null;

        for (MemberValuePair pair : annotation.getPairs()) {
            String pairName = pair.getNameAsString();
            Expression value = pair.getValue();

            switch (pairName) {
                case "id" -> {
                    if (value instanceof StringLiteralExpr stringLiteral) {
                        id = stringLiteral.asString();
                    }
                }
                case "description" -> {
                    if (value instanceof StringLiteralExpr stringLiteral) {
                        description = stringLiteral.asString();
                    }
                }
                case "configEntry" -> {
                    if (value instanceof StringLiteralExpr stringLiteral) {
                        configEntry = stringLiteral.asString();
                    }
                }
                case "beans" -> beans = extractConditionalBeanArray(value);
                case "runtimeDependencies" -> runtimeDeps = extractStringArrayValue(value);
                default -> {}
            }
        }

        if (id.isEmpty() || configEntry.isEmpty()) {
            return null;
        }

        ConditionalBeanGroup group = new ConditionalBeanGroup(id, description, configEntry, beans);
        if (runtimeDeps != null && !runtimeDeps.isEmpty()) {
            group.setRuntimeDependencies(ScannedBean.parseVariantDependencies(runtimeDeps));
        }
        return group;
    }

    /**
     * Extracts an array of ConditionalBeanInfo from an annotation value.
     */
    private List<ConditionalBeanInfo> extractConditionalBeanArray(Expression value) {
        List<ConditionalBeanInfo> beans = new ArrayList<>();

        if (value instanceof ArrayInitializerExpr arrayExpr) {
            for (Expression element : arrayExpr.getValues()) {
                if (element instanceof NormalAnnotationExpr normalAnnotation) {
                    ConditionalBeanInfo beanInfo = extractConditionalBeanInfo(normalAnnotation);
                    if (beanInfo != null) {
                        beans.add(beanInfo);
                    }
                }
            }
        }

        return beans;
    }

    /**
     * Extracts ConditionalBeanInfo from a @ConditionalBean annotation.
     */
    private ConditionalBeanInfo extractConditionalBeanInfo(NormalAnnotationExpr annotation) {
        String name = null;
        String nameFromConfig = null;
        String javaType = "";
        String description = null;

        for (MemberValuePair pair : annotation.getPairs()) {
            String pairName = pair.getNameAsString();
            Expression pairValue = pair.getValue();

            switch (pairName) {
                case "name" -> {
                    if (pairValue instanceof StringLiteralExpr stringLiteral) {
                        String nameValue = stringLiteral.asString();
                        if (!nameValue.isEmpty()) {
                            name = nameValue;
                        }
                    }
                }
                case "nameFromConfig" -> {
                    if (pairValue instanceof StringLiteralExpr stringLiteral) {
                        String nameFromConfigValue = stringLiteral.asString();
                        if (!nameFromConfigValue.isEmpty()) {
                            nameFromConfig = nameFromConfigValue;
                        }
                    }
                }
                case "javaType" -> {
                    if (pairValue instanceof StringLiteralExpr stringLiteral) {
                        javaType = stringLiteral.asString();
                    }
                }
                case "description" -> {
                    if (pairValue instanceof StringLiteralExpr stringLiteral) {
                        String descValue = stringLiteral.asString();
                        if (!descValue.isEmpty()) {
                            description = descValue;
                        }
                    }
                }
                default -> {}
            }
        }

        if (javaType.isEmpty()) {
            return null;
        }

        return new ConditionalBeanInfo(name, nameFromConfig, javaType, description);
    }

    /**
     * Extracts ScannedBean information from the annotation.
     */
    private ScannedBean extractScannedBean(
            AnnotationExpr annotation, ClassOrInterfaceDeclaration classDecl, CompilationUnit cu) {
        String name = "";
        List<String> components = new ArrayList<>();
        String description = "";
        String feature = "";
        String configClassName = null;
        List<String> runtimeDeps = null;

        // Extract annotation values
        if (annotation instanceof SingleMemberAnnotationExpr singleMember) {
            if (singleMember.getMemberValue() instanceof StringLiteralExpr stringLiteral) {
                name = stringLiteral.asString();
            }
        } else if (annotation instanceof NormalAnnotationExpr normalAnnotation) {
            for (MemberValuePair pair : normalAnnotation.getPairs()) {
                String pairName = pair.getNameAsString();
                Expression value = pair.getValue();

                switch (pairName) {
                    case "value" -> {
                        if (value instanceof StringLiteralExpr stringLiteral) {
                            name = stringLiteral.asString();
                        }
                    }
                    case "components" -> components = extractStringArrayValue(value);
                    case "description" -> {
                        if (value instanceof StringLiteralExpr stringLiteral) {
                            description = stringLiteral.asString();
                        }
                    }
                    case "feature" -> {
                        if (value instanceof StringLiteralExpr stringLiteral) {
                            feature = stringLiteral.asString();
                        }
                    }
                    case "configClass" -> configClassName = extractClassValue(value, cu);
                    case "runtimeDependencies" -> runtimeDeps = extractStringArrayValue(value);
                    default -> {}
                }
            }
        }

        // Get the fully qualified class name
        String className = getFullyQualifiedClassName(classDecl, cu);

        ScannedBean bean = new ScannedBean(name, components, description, className, feature);
        bean.setConfigClassName(configClassName);
        if (runtimeDeps != null && !runtimeDeps.isEmpty()) {
            bean.setRuntimeDependencies(ScannedBean.parseVariantDependencies(runtimeDeps));
        }
        return bean;
    }

    /**
     * Checks if a class declaration extends ConfigEntries.
     */
    private boolean extendsConfigEntries(ClassOrInterfaceDeclaration classDecl) {
        return classDecl.getExtendedTypes().stream()
                .anyMatch(extendedType -> "ConfigEntries".equals(extendedType.getNameAsString()));
    }

    /**
     * Extracts ConfigModule fields from a ConfigEntries class.
     */
    private void extractConfigModuleFields(ClassOrInterfaceDeclaration classDecl, List<ConfigEntry> configProperties) {
        // Find all static final ConfigModule fields
        classDecl.findAll(FieldDeclaration.class).forEach(field -> {
            if (field.isStatic() && field.isFinal()) {
                field.getVariables().forEach(variable -> {
                    if (isConfigModuleField(field)) {
                        ConfigEntry configProp = extractConfigEntry(variable);
                        if (configProp != null) {
                            log.debug("Extracted configuration property: " + configProp.getName());
                            configProperties.add(configProp);
                        }
                    }
                });
            }
        });
    }

    /**
     * Checks if a field is a ConfigModule field.
     */
    private boolean isConfigModuleField(FieldDeclaration field) {
        // Check if the field type is ConfigModule
        return field.getElementType().asString().equals("ConfigModule")
                || field.getElementType().asString().endsWith(".ConfigModule");
    }

    /**
     * Extracts a ConfigEntry from a ConfigModule field initialization.
     * Supports two patterns:
     * 1. ConfigModule.of(SomeConfig.class, "config.key.name")
     * 2. ConfigModule.of(SomeConfig.class, "config.key.name", description, label, defaultValue, type, required, configTag)
     */
    private ConfigEntry extractConfigEntry(VariableDeclarator variable) {
        if (variable.getInitializer().isEmpty()) {
            return null;
        }

        var initializer = variable.getInitializer().get();
        if (!(initializer instanceof MethodCallExpr methodCall)) {
            return null;
        }

        if (!isConfigModuleOfCall(methodCall)) {
            return null;
        }

        return extractConfigEntryFromMethodCall(methodCall);
    }

    /**
     * Extracts configuration entry from a ConfigModule.of() or ConfigModule.ofBeanName() method call.
     */
    private ConfigEntry extractConfigEntryFromMethodCall(MethodCallExpr methodCall) {
        int argCount = methodCall.getArguments().size();

        if (argCount < 2) {
            return null;
        }

        // Extract the config key (second argument)
        var secondArg = methodCall.getArguments().get(1);
        if (!(secondArg instanceof StringLiteralExpr stringLiteral)) {
            return null;
        }

        String configKey = stringLiteral.asString();
        ConfigEntry configProp = new ConfigEntry();
        configProp.setName(configKey);

        String methodName = methodCall.getNameAsString();
        if ("ofBeanName".equals(methodName)) {
            extractBeanNameConfigEntry(methodCall, configProp);
        } else if (argCount >= 8) {
            extractFullConfigEntry(methodCall, configProp);
        } else {
            extractSimpleConfigEntry(configKey, configProp);
        }

        return configProp;
    }

    /**
     * Extracts full-form configuration entry with all 8 arguments.
     */
    private void extractFullConfigEntry(MethodCallExpr methodCall, ConfigEntry configProp) {
        // Description (3rd argument)
        extractStringArg(methodCall, 2, configProp::setDescription);

        // Label (4th argument)
        extractStringArg(methodCall, 3, configProp::setLabel);

        // Default value (5th argument)
        extractStringArg(methodCall, 4, configProp::setDefaultValue);

        // Type (6th argument)
        extractStringArg(methodCall, 5, configProp::setType);
        if (configProp.getType() == null) {
            configProp.setType("String");
        }

        // Required (7th argument)
        extractBooleanArg(methodCall, 6, configProp::setRequired);
        if (!configProp.isRequired()) {
            configProp.setRequired(false);
        }

        // ConfigTag (8th argument)
        var configTagArg = methodCall.getArguments().get(7);
        String configTagValue = extractConfigTagValue(configTagArg);
        if (configTagValue != null) {
            configProp.setConfigTag(configTagValue);
        }
    }

    /**
     * Extracts simple-form configuration entry with defaults.
     */
    private void extractSimpleConfigEntry(String configKey, ConfigEntry configProp) {
        configProp.setType("String");
        configProp.setDescription(configKey);
        configProp.setRequired(false);
    }

    /**
     * Extracts bean-name configuration entry from ConfigModule.ofBeanName() call.
     * Arguments: (config, name, description, label, required, configTag, selectsFrom)
     */
    private void extractBeanNameConfigEntry(MethodCallExpr methodCall, ConfigEntry configProp) {
        configProp.setType("bean-name");

        int argCount = methodCall.getArguments().size();

        // Description (3rd argument)
        if (argCount > 2) {
            extractStringArg(methodCall, 2, configProp::setDescription);
        }

        // Label (4th argument)
        if (argCount > 3) {
            extractStringArg(methodCall, 3, configProp::setLabel);
        }

        // Required (5th argument)
        if (argCount > 4) {
            extractBooleanArg(methodCall, 4, configProp::setRequired);
        }

        // ConfigTag (6th argument)
        if (methodCall.getArguments().size() > 5) {
            var configTagArg = methodCall.getArguments().get(5);
            String configTagValue = extractConfigTagValue(configTagArg);
            if (configTagValue != null) {
                configProp.setConfigTag(configTagValue);
            }
        }

        // SelectsFrom (7th argument)
        if (methodCall.getArguments().size() > 6) {
            extractStringArg(methodCall, 6, configProp::setSelectsFrom);
        }
    }

    /**
     * Extracts a string argument at the given index and applies it via the consumer.
     */
    private void extractStringArg(MethodCallExpr methodCall, int index, java.util.function.Consumer<String> consumer) {
        if (methodCall.getArguments().get(index) instanceof StringLiteralExpr stringLiteral) {
            consumer.accept(stringLiteral.asString());
        }
    }

    /**
     * Extracts a boolean argument at the given index and applies it via the consumer.
     */
    private void extractBooleanArg(
            MethodCallExpr methodCall, int index, java.util.function.Consumer<Boolean> consumer) {
        if (methodCall.getArguments().get(index) instanceof BooleanLiteralExpr booleanLiteral) {
            consumer.accept(booleanLiteral.getValue());
        }
    }

    /**
     * Extracts the ConfigTag enum value from an expression.
     * Handles patterns like ConfigTag.COMMON, ConfigTag.SECURITY, etc.
     */
    private String extractConfigTagValue(Expression expression) {
        String exprString = expression.toString();

        // Check if it's a field access expression (e.g., ConfigTag.COMMON)
        if (exprString.contains("ConfigTag.")) {
            String[] parts = exprString.split("\\.");
            if (parts.length >= 2) {
                return parts[parts.length - 1]; // Return the enum constant name (e.g., "COMMON")
            }
        }

        return null;
    }

    /**
     * Extracts the FactoryVariant enum value from an expression.
     * Handles patterns like FactoryVariant.BASE, FactoryVariant.SPRING_BOOT, FactoryVariant.QUARKUS.
     */
    private String extractVariantValue(Expression expression) {
        String exprString = expression.toString();

        // Check if it's a field access expression (e.g., FactoryVariant.BASE)
        if (exprString.contains("FactoryVariant.")) {
            String[] parts = exprString.split("\\.");
            if (parts.length >= 2) {
                return parts[
                        parts.length - 1]; // Return the enum constant name (e.g., "BASE", "SPRING_BOOT", "QUARKUS")
            }
        }

        return "BASE"; // Default fallback
    }

    /**
     * Extracts the Config class value from an annotation expression.
     * Handles patterns like DataSourceFactoryConfig.class, resolving to fully qualified name.
     * Returns null if it's the default Config.class or if resolution fails.
     */
    private String extractClassValue(Expression expression, CompilationUnit cu) {
        String exprString = expression.toString();

        // If it's just "Config.class", return null (it's the default)
        if ("Config.class".equals(exprString)) {
            return null;
        }

        // Extract the class name from "SomeConfig.class"
        if (exprString.endsWith(".class")) {
            String simpleClassName = exprString.substring(0, exprString.length() - 6);

            // Try to resolve to fully qualified name using imports
            String fullyQualifiedName = resolveClassNameFromImports(simpleClassName, cu);

            if (fullyQualifiedName != null) {
                return fullyQualifiedName;
            }

            // If not found in imports, it might be in the same package
            Optional<String> packageName = cu.getPackageDeclaration().map(NodeWithName::getNameAsString);
            return packageName.map(s -> s + "." + simpleClassName).orElse(simpleClassName);

            // Last resort: return as-is (might be fully qualified already)
        }

        return null;
    }

    /**
     * Resolves a simple class name to its fully qualified name using the compilation unit's imports.
     */
    private String resolveClassNameFromImports(String simpleClassName, CompilationUnit cu) {
        // Check imports for this class
        return cu.getImports().stream()
                .filter(importDecl -> !importDecl.isAsterisk())
                .map(NodeWithName::getNameAsString)
                .filter(importName -> importName.endsWith("." + simpleClassName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Extracts the FactoryType enum value from an expression.
     * Handles patterns like FactoryType.DATA_SOURCE, FactoryType.CONNECTION_FACTORY, FactoryType.AGENT.
     */
    private String extractFactoryTypeValue(Expression expression) {
        String exprString = expression.toString();

        // Check if it's a field access expression (e.g., FactoryType.DATA_SOURCE)
        if (exprString.contains("FactoryType.")) {
            String[] parts = exprString.split("\\.");
            if (parts.length >= 2) {
                return FactoryType.valueOf(parts[parts.length - 1]).getDisplayName();
            }
        }

        return ""; // Default fallback for unknown types
    }

    /**
     * Checks if a method call is ConfigModule.of() or ConfigModule.ofBeanName().
     */
    private boolean isConfigModuleOfCall(MethodCallExpr methodCall) {
        String methodName = methodCall.getNameAsString();
        return (methodName.equals("of") || methodName.equals("ofBeanName"))
                && methodCall.getScope().isPresent()
                && (methodCall.getScope().get().toString().equals("ConfigModule")
                        || methodCall.getScope().get().toString().endsWith(".ConfigModule")
                        || methodCall.getScope().get().toString().equals(Constants.CONFIG_MODULE_FQN));
    }

    /**
     * Extracts a list of strings from an annotation value that can be either a single string or an array of strings.
     */
    private List<String> extractStringArrayValue(Expression value) {
        List<String> result = new ArrayList<>();

        ArrayInitializerExpr arrayExpr = (ArrayInitializerExpr) value;
        for (Expression element : arrayExpr.getValues()) {
            if (element instanceof StringLiteralExpr) {
                result.add(((StringLiteralExpr) element).asString());
            }
        }

        return result;
    }

    /**
     * Gets the fully qualified class name from the compilation unit.
     */
    private String getFullyQualifiedClassName(ClassOrInterfaceDeclaration classDecl, CompilationUnit cu) {
        Optional<String> packageName = cu.getPackageDeclaration().map(NodeWithName::getNameAsString);
        String className = classDecl.getNameAsString();

        return packageName.map(s -> s + "." + className).orElse(className);
    }

    /**
     * Checks if a class declaration implements Config or extends AbstractConfig.
     */
    private boolean implementsConfig(ClassOrInterfaceDeclaration classDecl) {
        return classDecl.getImplementedTypes().stream().anyMatch(impl -> "Config".equals(impl.getNameAsString()))
                || classDecl.getExtendedTypes().stream()
                        .anyMatch(ext -> "AbstractConfig".equals(ext.getNameAsString()));
    }

    /**
     * Extracts the config name from a Config class's name() method.
     * Returns the string literal returned by the name() method, or null if not found.
     */
    private String extractConfigName(ClassOrInterfaceDeclaration classDecl) {
        // Find name() method
        Optional<MethodDeclaration> nameMethod = classDecl.getMethodsByName("name").stream()
                .filter(m -> m.getParameters().isEmpty())
                .findFirst();

        if (nameMethod.isEmpty()) {
            return null;
        }

        // Extract return statement
        Optional<ReturnStmt> returnStmt = nameMethod.get().findFirst(ReturnStmt.class);
        if (returnStmt.isEmpty()) {
            return null;
        }

        // Extract string literal
        Optional<Expression> expr = returnStmt.get().getExpression();
        if (expr.isPresent() && expr.get() instanceof StringLiteralExpr) {
            return ((StringLiteralExpr) expr.get()).asString();
        }

        return null;
    }
}
