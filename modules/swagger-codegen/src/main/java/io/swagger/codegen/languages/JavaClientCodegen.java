package io.swagger.codegen.languages;

import static java.util.Collections.sort;

import com.google.common.collect.LinkedListMultimap;
import io.swagger.codegen.*;
import io.swagger.codegen.languages.features.BeanValidationFeatures;
import io.swagger.codegen.languages.features.GzipFeatures;
import io.swagger.codegen.languages.features.PerformBeanValidationFeatures;
import io.swagger.models.Swagger;
import io.swagger.models.Tag;
import io.swagger.util.Json;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

public class JavaClientCodegen extends AbstractJavaCodegen
        implements BeanValidationFeatures, PerformBeanValidationFeatures, GzipFeatures {
    static final String MEDIA_TYPE = "mediaType";

    @SuppressWarnings("hiding")
    private static final Logger LOGGER = LoggerFactory.getLogger(JavaClientCodegen.class);

    public static final String USE_RX_JAVA = "useRxJava";
    public static final String USE_RX_JAVA2 = "useRxJava2";
    public static final String DO_NOT_USE_RX = "doNotUseRx";
    public static final String USE_PLAY_WS = "usePlayWS";
    public static final String PLAY_VERSION = "playVersion";
    public static final String PARCELABLE_MODEL = "parcelableModel";
    public static final String USE_RUNTIME_EXCEPTION = "useRuntimeException";

    public static final String PLAY_24 = "play24";
    public static final String PLAY_25 = "play25";

    public static final String RETROFIT_1 = "retrofit";
    public static final String RETROFIT_2 = "retrofit2";
    public static final String REST_ASSURED = "rest-assured";

    protected String gradleWrapperPackage = "gradle.wrapper";
    protected boolean useRxJava = false;
    protected boolean useRxJava2 = false;
    protected boolean doNotUseRx = true; // backwards compatibility for swagger configs that specify neither rx1 nor rx2
                                         // (mustache does not allow for boolean operators so we need this extra field)
    protected boolean usePlayWS = false;
    protected String playVersion = PLAY_25;
    protected boolean parcelableModel = false;
    protected boolean useBeanValidation = false;
    protected boolean performBeanValidation = false;
    protected boolean useGzipFeature = false;
    protected boolean useRuntimeException = false;

    // extesion definitions
    protected String apiFixedFolderName = "internal";
    protected String modelFixedFolderName = "domain";
    protected String extensionModel = "extensionmodel.mustache";
    protected String extensionApiBase = "extensionapibase.mustache";
    protected String extensionApi = "extensionapi.mustache";
    protected String extensionApiImpl = "extensionapiimpl.mustache";
    protected String extensionOption = "extensionoption.mustache";
    protected Map<String, String> otherTemplateFiles = new HashMap<String, String>();
    protected String extensionserviceendpoint = "extensionserviceendpoint.mustache";
    protected String extensionservicetype = "extensionservicetype.mustache";
    protected String extensionosclient = "extensionosclient.mustache";
    protected String extensionosclientsession = "extensionosclientsession.mustache";
    protected String extensiondefaultapiprovider = "extensiondefaultapiprovider.mustache";

    public JavaClientCodegen() {
        super();
        outputFolder = "generated-code" + File.separator + "java";
        embeddedTemplateDir = templateDir = "Java";
        invokerPackage = "io.swagger.client";
        artifactId = "swagger-java-client";
        apiPackage = "io.swagger.client.api";
        modelPackage = "io.swagger.client.model";

        // override configurations
        projectFolder = "core" + File.separator + "src" + File.separator + "main";
        sourceFolder = projectFolder + File.separator + "java";
        modelPackage = "com.huawei.openstack4j.openstack";
        apiPackage = "com.huawei.openstack4j.openstack";
        modelTemplateFiles.clear();
        apiTemplateFiles.clear();
        otherTemplateFiles.clear();
        modelTemplateFiles.put(extensionModel, ".java");
        apiTemplateFiles.put(extensionApiBase, ".java");
        apiTemplateFiles.put(extensionApi, ".java");
        apiTemplateFiles.put(extensionApiImpl, ".java");
        otherTemplateFiles.put(extensionserviceendpoint, ".json");
        otherTemplateFiles.put(extensionservicetype, ".java");
        otherTemplateFiles.put(extensionosclient, ".java");
        otherTemplateFiles.put(extensionosclientsession, ".java");
        otherTemplateFiles.put(extensiondefaultapiprovider, ".java");

        cliOptions.add(
                CliOption.newBoolean(USE_RX_JAVA, "Whether to use the RxJava adapter with the retrofit2 library."));
        cliOptions.add(
                CliOption.newBoolean(USE_RX_JAVA2, "Whether to use the RxJava2 adapter with the retrofit2 library."));
        cliOptions.add(CliOption.newBoolean(PARCELABLE_MODEL,
                "Whether to generate models for Android that implement Parcelable with the okhttp-gson library."));
        cliOptions.add(CliOption.newBoolean(USE_PLAY_WS, "Use Play! Async HTTP client (Play WS API)"));
        cliOptions.add(CliOption.newString(PLAY_VERSION,
                "Version of Play! Framework (possible values \"play24\", \"play25\")"));
        cliOptions.add(CliOption.newBoolean(SUPPORT_JAVA6, "Whether to support Java6 with the Jersey1 library."));
        cliOptions.add(CliOption.newBoolean(USE_BEANVALIDATION, "Use BeanValidation API annotations"));
        cliOptions.add(CliOption.newBoolean(PERFORM_BEANVALIDATION, "Perform BeanValidation"));
        cliOptions.add(CliOption.newBoolean(USE_GZIP_FEATURE, "Send gzip-encoded requests"));
        cliOptions.add(CliOption.newBoolean(USE_RUNTIME_EXCEPTION, "Use RuntimeException instead of Exception"));

        supportedLibraries.put("jersey1",
                "HTTP client: Jersey client 1.19.4. JSON processing: Jackson 2.8.9. Enable Java6 support using '-DsupportJava6=true'. Enable gzip request encoding using '-DuseGzipFeature=true'.");
        supportedLibraries.put("feign", "HTTP client: OpenFeign 9.4.0. JSON processing: Jackson 2.8.9");
        supportedLibraries.put("jersey2", "HTTP client: Jersey client 2.25.1. JSON processing: Jackson 2.8.9");
        supportedLibraries.put("okhttp-gson",
                "HTTP client: OkHttp 2.7.5. JSON processing: Gson 2.8.1. Enable Parcelable models on Android using '-DparcelableModel=true'. Enable gzip request encoding using '-DuseGzipFeature=true'.");
        supportedLibraries.put(RETROFIT_1,
                "HTTP client: OkHttp 2.7.5. JSON processing: Gson 2.3.1 (Retrofit 1.9.0). IMPORTANT NOTE: retrofit1.x is no longer actively maintained so please upgrade to 'retrofit2' instead.");
        supportedLibraries.put(RETROFIT_2,
                "HTTP client: OkHttp 3.8.0. JSON processing: Gson 2.6.1 (Retrofit 2.3.0). Enable the RxJava adapter using '-DuseRxJava[2]=true'. (RxJava 1.x or 2.x)");
        supportedLibraries.put("resttemplate",
                "HTTP client: Spring RestTemplate 4.3.9-RELEASE. JSON processing: Jackson 2.8.9");
        supportedLibraries.put("resteasy", "HTTP client: Resteasy client 3.1.3.Final. JSON processing: Jackson 2.8.9");
        supportedLibraries.put("vertx", "HTTP client: VertX client 3.2.4. JSON processing: Jackson 2.8.9");
        supportedLibraries.put("google-api-client",
                "HTTP client: Google API client 1.23.0. JSON processing: Jackson 2.8.9");
        supportedLibraries.put("rest-assured",
                "HTTP client: rest-assured : 3.1.0. JSON processing: Gson 2.6.1. Only for Java8");

        CliOption libraryOption = new CliOption(CodegenConstants.LIBRARY, "library template (sub-template) to use");
        libraryOption.setEnum(supportedLibraries);
        // set okhttp-gson as the default
        libraryOption.setDefault("okhttp-gson");
        cliOptions.add(libraryOption);
        setLibrary("okhttp-gson");

    }

    @Override
    public CodegenType getTag() {
        return CodegenType.CLIENT;
    }

    @Override
    public String getName() {
        return "java";
    }

    @Override
    public String getHelp() {
        return "Generates a Java client library.";
    }

    @Override
    public void processOpts() {
        super.processOpts();

        if (additionalProperties.containsKey(USE_RX_JAVA) && additionalProperties.containsKey(USE_RX_JAVA2)) {
            LOGGER.warn(
                    "You specified both RxJava versions 1 and 2 but they are mutually exclusive. Defaulting to v2.");
        } else if (additionalProperties.containsKey(USE_RX_JAVA)) {
            this.setUseRxJava(Boolean.valueOf(additionalProperties.get(USE_RX_JAVA).toString()));
        }
        if (additionalProperties.containsKey(USE_RX_JAVA2)) {
            this.setUseRxJava2(Boolean.valueOf(additionalProperties.get(USE_RX_JAVA2).toString()));
        }
        if (!useRxJava && !useRxJava2) {
            additionalProperties.put(DO_NOT_USE_RX, true);
        }
        if (additionalProperties.containsKey(USE_PLAY_WS)) {
            this.setUsePlayWS(Boolean.valueOf(additionalProperties.get(USE_PLAY_WS).toString()));
        }
        additionalProperties.put(USE_PLAY_WS, usePlayWS);

        if (additionalProperties.containsKey(PLAY_VERSION)) {
            this.setPlayVersion(additionalProperties.get(PLAY_VERSION).toString());
        }
        additionalProperties.put(PLAY_VERSION, playVersion);

        if (additionalProperties.containsKey(PARCELABLE_MODEL)) {
            this.setParcelableModel(Boolean.valueOf(additionalProperties.get(PARCELABLE_MODEL).toString()));
        }
        // put the boolean value back to PARCELABLE_MODEL in additionalProperties
        additionalProperties.put(PARCELABLE_MODEL, parcelableModel);

        if (additionalProperties.containsKey(USE_BEANVALIDATION)) {
            this.setUseBeanValidation(convertPropertyToBooleanAndWriteBack(USE_BEANVALIDATION));
        }

        if (additionalProperties.containsKey(PERFORM_BEANVALIDATION)) {
            this.setPerformBeanValidation(convertPropertyToBooleanAndWriteBack(PERFORM_BEANVALIDATION));
        }

        if (additionalProperties.containsKey(USE_GZIP_FEATURE)) {
            this.setUseGzipFeature(convertPropertyToBooleanAndWriteBack(USE_GZIP_FEATURE));
        }

        if (additionalProperties.containsKey(USE_RUNTIME_EXCEPTION)) {
            this.setUseRuntimeException(convertPropertyToBooleanAndWriteBack(USE_RUNTIME_EXCEPTION));
        }

        final String invokerFolder = (sourceFolder + '/' + invokerPackage).replace(".", "/");
        final String authFolder = (sourceFolder + '/' + invokerPackage + ".auth").replace(".", "/");
        final String apiFolder = (sourceFolder + '/' + apiPackage).replace(".", "/");

        // Common files
        writeOptional(outputFolder, new SupportingFile("pom.mustache", "", "pom.xml"));
        writeOptional(outputFolder, new SupportingFile("README.mustache", "", "README.md"));
        writeOptional(outputFolder, new SupportingFile("build.gradle.mustache", "", "build.gradle"));
        writeOptional(outputFolder, new SupportingFile("build.sbt.mustache", "", "build.sbt"));
        writeOptional(outputFolder, new SupportingFile("settings.gradle.mustache", "", "settings.gradle"));
        writeOptional(outputFolder, new SupportingFile("gradle.properties.mustache", "", "gradle.properties"));
        writeOptional(outputFolder, new SupportingFile("manifest.mustache", projectFolder, "AndroidManifest.xml"));
        supportingFiles.add(new SupportingFile("travis.mustache", "", ".travis.yml"));
        supportingFiles.add(new SupportingFile("ApiClient.mustache", invokerFolder, "ApiClient.java"));
        if (!("resttemplate".equals(getLibrary()) || REST_ASSURED.equals(getLibrary()))) {
            supportingFiles.add(new SupportingFile("StringUtil.mustache", invokerFolder, "StringUtil.java"));
        }

        // google-api-client doesn't use the Swagger auth, because it uses Google
        // Credential directly (HttpRequestInitializer)
        if (!("google-api-client".equals(getLibrary()) || REST_ASSURED.equals(getLibrary()))) {
            supportingFiles.add(new SupportingFile("auth/HttpBasicAuth.mustache", authFolder, "HttpBasicAuth.java"));
            supportingFiles.add(new SupportingFile("auth/ApiKeyAuth.mustache", authFolder, "ApiKeyAuth.java"));
            supportingFiles.add(new SupportingFile("auth/OAuth.mustache", authFolder, "OAuth.java"));
            supportingFiles.add(new SupportingFile("auth/OAuthFlow.mustache", authFolder, "OAuthFlow.java"));
        }
        supportingFiles.add(new SupportingFile("gradlew.mustache", "", "gradlew"));
        supportingFiles.add(new SupportingFile("gradlew.bat.mustache", "", "gradlew.bat"));
        supportingFiles.add(new SupportingFile("gradle-wrapper.properties.mustache",
                gradleWrapperPackage.replace(".", File.separator), "gradle-wrapper.properties"));
        supportingFiles.add(new SupportingFile("gradle-wrapper.jar", gradleWrapperPackage.replace(".", File.separator),
                "gradle-wrapper.jar"));
        supportingFiles.add(new SupportingFile("git_push.sh.mustache", "", "git_push.sh"));
        supportingFiles.add(new SupportingFile("gitignore.mustache", "", ".gitignore"));

        if (performBeanValidation) {
            supportingFiles.add(new SupportingFile("BeanValidationException.mustache", invokerFolder,
                    "BeanValidationException.java"));
        }

        // TODO: add doc to retrofit1 and feign
        if ("feign".equals(getLibrary()) || "retrofit".equals(getLibrary())) {
            modelDocTemplateFiles.remove("model_doc.mustache");
            apiDocTemplateFiles.remove("api_doc.mustache");
        }

        if (!("feign".equals(getLibrary()) || "resttemplate".equals(getLibrary()) || usesAnyRetrofitLibrary()
                || "google-api-client".equals(getLibrary()) || REST_ASSURED.equals(getLibrary()))) {
            supportingFiles.add(new SupportingFile("apiException.mustache", invokerFolder, "ApiException.java"));
            supportingFiles.add(new SupportingFile("Configuration.mustache", invokerFolder, "Configuration.java"));
            supportingFiles.add(new SupportingFile("Pair.mustache", invokerFolder, "Pair.java"));
            supportingFiles.add(new SupportingFile("auth/Authentication.mustache", authFolder, "Authentication.java"));
        }

        if ("feign".equals(getLibrary())) {
            additionalProperties.put("jackson", "true");
            supportingFiles.add(new SupportingFile("ParamExpander.mustache", invokerFolder, "ParamExpander.java"));
            supportingFiles.add(new SupportingFile("EncodingUtils.mustache", invokerFolder, "EncodingUtils.java"));
        } else if ("okhttp-gson".equals(getLibrary()) || StringUtils.isEmpty(getLibrary())) {
            // the "okhttp-gson" library template requires "ApiCallback.mustache" for async
            // call
            supportingFiles.add(new SupportingFile("ApiCallback.mustache", invokerFolder, "ApiCallback.java"));
            supportingFiles.add(new SupportingFile("ApiResponse.mustache", invokerFolder, "ApiResponse.java"));
            supportingFiles.add(new SupportingFile("JSON.mustache", invokerFolder, "JSON.java"));
            supportingFiles
                    .add(new SupportingFile("ProgressRequestBody.mustache", invokerFolder, "ProgressRequestBody.java"));
            supportingFiles.add(
                    new SupportingFile("ProgressResponseBody.mustache", invokerFolder, "ProgressResponseBody.java"));
            supportingFiles.add(new SupportingFile("GzipRequestInterceptor.mustache", invokerFolder,
                    "GzipRequestInterceptor.java"));
            additionalProperties.put("gson", "true");
        } else if (usesAnyRetrofitLibrary()) {
            supportingFiles
                    .add(new SupportingFile("auth/OAuthOkHttpClient.mustache", authFolder, "OAuthOkHttpClient.java"));
            supportingFiles
                    .add(new SupportingFile("CollectionFormats.mustache", invokerFolder, "CollectionFormats.java"));
            additionalProperties.put("gson", "true");
            if ("retrofit2".equals(getLibrary()) && !usePlayWS) {
                supportingFiles.add(new SupportingFile("JSON.mustache", invokerFolder, "JSON.java"));
            }
        } else if ("jersey2".equals(getLibrary())) {
            supportingFiles.add(new SupportingFile("JSON.mustache", invokerFolder, "JSON.java"));
            supportingFiles.add(new SupportingFile("ApiResponse.mustache", invokerFolder, "ApiResponse.java"));
            additionalProperties.put("jackson", "true");
        } else if ("resteasy".equals(getLibrary())) {
            supportingFiles.add(new SupportingFile("JSON.mustache", invokerFolder, "JSON.java"));
            additionalProperties.put("jackson", "true");
        } else if ("jersey1".equals(getLibrary())) {
            additionalProperties.put("jackson", "true");
        } else if ("resttemplate".equals(getLibrary())) {
            additionalProperties.put("jackson", "true");
            supportingFiles.add(new SupportingFile("auth/Authentication.mustache", authFolder, "Authentication.java"));
        } else if ("vertx".equals(getLibrary())) {
            typeMapping.put("file", "AsyncFile");
            importMapping.put("AsyncFile", "io.vertx.core.file.AsyncFile");
            setJava8Mode(true);
            additionalProperties.put("java8", "true");
            additionalProperties.put("jackson", "true");
            apiTemplateFiles.put("apiImpl.mustache", "Impl.java");
            apiTemplateFiles.put("rxApiImpl.mustache", ".java");
            supportingFiles.remove(new SupportingFile("manifest.mustache", projectFolder, "AndroidManifest.xml"));
        } else if ("google-api-client".equals(getLibrary())) {
            additionalProperties.put("jackson", "true");

        } else if (REST_ASSURED.equals(getLibrary())) {
            additionalProperties.put("gson", "true");
            apiTemplateFiles.put("api.mustache", ".java");
            supportingFiles.add(
                    new SupportingFile("ResponseSpecBuilders.mustache", invokerFolder, "ResponseSpecBuilders.java"));
            supportingFiles.add(new SupportingFile("JSON.mustache", invokerFolder, "JSON.java"));
            supportingFiles
                    .add(new SupportingFile("GsonObjectMapper.mustache", invokerFolder, "GsonObjectMapper.java"));
        } else {
            LOGGER.error("Unknown library option (-l/--library): " + getLibrary());
        }

        if (usePlayWS) {
            // remove unsupported auth
            Iterator<SupportingFile> iter = supportingFiles.iterator();
            while (iter.hasNext()) {
                SupportingFile sf = iter.next();
                if (sf.templateFile.startsWith("auth/")) {
                    iter.remove();
                }
            }

            apiTemplateFiles.remove("api.mustache");

            if (PLAY_24.equals(playVersion)) {
                additionalProperties.put(PLAY_24, true);
                apiTemplateFiles.put("play24/api.mustache", ".java");

                supportingFiles.add(new SupportingFile("play24/ApiClient.mustache", invokerFolder, "ApiClient.java"));
                supportingFiles.add(new SupportingFile("play24/Play24CallFactory.mustache", invokerFolder,
                        "Play24CallFactory.java"));
                supportingFiles.add(new SupportingFile("play24/Play24CallAdapterFactory.mustache", invokerFolder,
                        "Play24CallAdapterFactory.java"));
            } else {
                additionalProperties.put(PLAY_25, true);
                apiTemplateFiles.put("play25/api.mustache", ".java");

                supportingFiles.add(new SupportingFile("play25/ApiClient.mustache", invokerFolder, "ApiClient.java"));
                supportingFiles.add(new SupportingFile("play25/Play25CallFactory.mustache", invokerFolder,
                        "Play25CallFactory.java"));
                supportingFiles.add(new SupportingFile("play25/Play25CallAdapterFactory.mustache", invokerFolder,
                        "Play25CallAdapterFactory.java"));
                additionalProperties.put("java8", "true");
            }

            supportingFiles
                    .add(new SupportingFile("play-common/auth/ApiKeyAuth.mustache", authFolder, "ApiKeyAuth.java"));
            supportingFiles.add(new SupportingFile("auth/Authentication.mustache", authFolder, "Authentication.java"));
            supportingFiles.add(new SupportingFile("Pair.mustache", invokerFolder, "Pair.java"));

            additionalProperties.put("jackson", "true");
            additionalProperties.remove("gson");
        }

        if (additionalProperties.containsKey("jackson")) {
            supportingFiles
                    .add(new SupportingFile("RFC3339DateFormat.mustache", invokerFolder, "RFC3339DateFormat.java"));
            if ("threetenbp".equals(dateLibrary) && !usePlayWS) {
                supportingFiles.add(new SupportingFile("CustomInstantDeserializer.mustache", invokerFolder,
                        "CustomInstantDeserializer.java"));
            }
        }
    }

    private boolean usesAnyRetrofitLibrary() {
        return getLibrary() != null && getLibrary().contains(RETROFIT_1);
    }

    private boolean usesRetrofit2Library() {
        return getLibrary() != null && getLibrary().contains(RETROFIT_2);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> postProcessOperations(Map<String, Object> objs) {
        super.postProcessOperations(objs);
        if (usesAnyRetrofitLibrary()) {
            Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
            if (operations != null) {
                List<CodegenOperation> ops = (List<CodegenOperation>) operations.get("operation");
                for (CodegenOperation operation : ops) {
                    if (operation.hasConsumes == Boolean.TRUE) {

                        if (isMultipartType(operation.consumes)) {
                            operation.isMultipart = Boolean.TRUE;
                        } else {
                            operation.prioritizedContentTypes = prioritizeContentTypes(operation.consumes);
                        }
                    }
                    if (usesRetrofit2Library() && StringUtils.isNotEmpty(operation.path)
                            && operation.path.startsWith("/")) {
                        operation.path = operation.path.substring(1);
                    }

                    // sorting operation parameters to make sure path params are parsed before query
                    // params
                    if (operation.allParams != null) {
                        sort(operation.allParams, new Comparator<CodegenParameter>() {
                            @Override
                            public int compare(CodegenParameter one, CodegenParameter another) {
                                if (one.isPathParam && another.isQueryParam) {
                                    return -1;
                                }
                                if (one.isQueryParam && another.isPathParam) {
                                    return 1;
                                }

                                return 0;
                            }
                        });
                        Iterator<CodegenParameter> iterator = operation.allParams.iterator();
                        while (iterator.hasNext()) {
                            CodegenParameter param = iterator.next();
                            param.hasMore = iterator.hasNext();
                        }
                    }
                }
            }

        }

        // camelize path variables for Feign client
        if ("feign".equals(getLibrary())) {
            Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
            List<CodegenOperation> operationList = (List<CodegenOperation>) operations.get("operation");
            for (CodegenOperation op : operationList) {
                String path = op.path;
                String[] items = path.split("/", -1);

                for (int i = 0; i < items.length; ++i) {
                    if (items[i].matches("^\\{(.*)\\}$")) { // wrap in {}
                        // camelize path variable
                        items[i] = "{" + camelize(items[i].substring(1, items[i].length() - 1), true) + "}";
                    }
                }
                op.path = StringUtils.join(items, "/");
            }
        }

        return objs;
    }

    @Override
    public String apiFilename(String templateName, String tag) {
        if ("vertx".equals(getLibrary())) {
            String suffix = apiTemplateFiles().get(templateName);
            String subFolder = "";
            if (templateName.startsWith("rx")) {
                subFolder = "/rxjava";
            }
            return apiFileFolder() + subFolder + '/' + toApiFilename(tag) + suffix;
        } else {
            return super.apiFilename(templateName, tag);
        }
    }

    /**
     * Prioritizes consumes mime-type list by moving json-vendor and json mime-types
     * up front, but otherwise preserves original consumes definition order.
     * [application/vnd...+json,... application/json, ..as is..]
     *
     * @param consumes consumes mime-type list
     * @return
     */
    static List<Map<String, String>> prioritizeContentTypes(List<Map<String, String>> consumes) {
        if (consumes.size() <= 1)
            return consumes;

        List<Map<String, String>> prioritizedContentTypes = new ArrayList<>(consumes.size());

        List<Map<String, String>> jsonVendorMimeTypes = new ArrayList<>(consumes.size());
        List<Map<String, String>> jsonMimeTypes = new ArrayList<>(consumes.size());

        for (Map<String, String> consume : consumes) {
            if (isJsonVendorMimeType(consume.get(MEDIA_TYPE))) {
                jsonVendorMimeTypes.add(consume);
            } else if (isJsonMimeType(consume.get(MEDIA_TYPE))) {
                jsonMimeTypes.add(consume);
            } else
                prioritizedContentTypes.add(consume);

            consume.put("hasMore", "true");
        }

        prioritizedContentTypes.addAll(0, jsonMimeTypes);
        prioritizedContentTypes.addAll(0, jsonVendorMimeTypes);

        prioritizedContentTypes.get(prioritizedContentTypes.size() - 1).put("hasMore", null);

        return prioritizedContentTypes;
    }

    private static boolean isMultipartType(List<Map<String, String>> consumes) {
        Map<String, String> firstType = consumes.get(0);
        if (firstType != null) {
            if ("multipart/form-data".equals(firstType.get(MEDIA_TYPE))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        super.postProcessModelProperty(model, property);
        if (!BooleanUtils.toBoolean(model.isEnum)) {
            // final String lib = getLibrary();
            // Needed imports for Jackson based libraries
            if (additionalProperties.containsKey("jackson")) {
                model.imports.add("JsonProperty");
                model.imports.add("JsonValue");
            }
            if (additionalProperties.containsKey("gson")) {
                model.imports.add("SerializedName");
                model.imports.add("TypeAdapter");
                model.imports.add("JsonAdapter");
                model.imports.add("JsonReader");
                model.imports.add("JsonWriter");
                model.imports.add("IOException");
            }
        } else { // enum class
            // Needed imports for Jackson's JsonCreator
            if (additionalProperties.containsKey("jackson")) {
                model.imports.add("JsonValue");
                model.imports.add("JsonCreator");
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> postProcessAllModels(Map<String, Object> objs) {
        Map<String, Object> allProcessedModels = super.postProcessAllModels(objs);
        if (!additionalProperties.containsKey("gsonFactoryMethod")) {
            List<Object> allModels = new ArrayList<Object>();
            for (String name : allProcessedModels.keySet()) {
                Map<String, Object> models = (Map<String, Object>) allProcessedModels.get(name);
                try {
                    allModels.add(((List<Object>) models.get("models")).get(0));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            additionalProperties.put("parent", modelInheritanceSupportInGson(allModels));
        }
        return allProcessedModels;
    }

    @Override
    public Map<String, Object> postProcessModelsEnum(Map<String, Object> objs) {
        objs = super.postProcessModelsEnum(objs);
        // Needed import for Gson based libraries
        if (additionalProperties.containsKey("gson")) {
            List<Map<String, String>> imports = (List<Map<String, String>>) objs.get("imports");
            List<Object> models = (List<Object>) objs.get("models");
            for (Object _mo : models) {
                Map<String, Object> mo = (Map<String, Object>) _mo;
                CodegenModel cm = (CodegenModel) mo.get("model");
                // for enum model
                if (Boolean.TRUE.equals(cm.isEnum) && cm.allowableValues != null) {
                    cm.imports.add(importMapping.get("SerializedName"));
                    Map<String, String> item = new HashMap<String, String>();
                    item.put("import", importMapping.get("SerializedName"));
                    imports.add(item);
                }
            }
        }
        return objs;
    }

    private List<Map<String, Object>> modelInheritanceSupportInGson(List<?> allModels) {
        LinkedListMultimap<CodegenModel, CodegenModel> byParent = LinkedListMultimap.create();
        for (Object m : allModels) {
            Map entry = (Map) m;
            CodegenModel parent = ((CodegenModel) entry.get("model")).parentModel;
            if (null != parent) {
                byParent.put(parent, ((CodegenModel) entry.get("model")));
            }
        }
        List<Map<String, Object>> parentsList = new ArrayList<>();
        for (CodegenModel parentModel : byParent.keySet()) {
            List<Map<String, Object>> childrenList = new ArrayList<>();
            Map<String, Object> parent = new HashMap<>();
            parent.put("classname", parentModel.classname);
            List<CodegenModel> childrenModels = byParent.get(parentModel);
            for (CodegenModel model : childrenModels) {
                Map<String, Object> child = new HashMap<>();
                child.put("name", model.name);
                child.put("classname", model.classname);
                childrenList.add(child);
            }
            parent.put("children", childrenList);
            parent.put("discriminator", parentModel.discriminator);
            parentsList.add(parent);
        }
        return parentsList;
    }

    public void setUseRxJava(boolean useRxJava) {
        this.useRxJava = useRxJava;
        doNotUseRx = false;
    }

    public void setUseRxJava2(boolean useRxJava2) {
        this.useRxJava2 = useRxJava2;
        doNotUseRx = false;
    }

    public void setDoNotUseRx(boolean doNotUseRx) {
        this.doNotUseRx = doNotUseRx;
    }

    public void setUsePlayWS(boolean usePlayWS) {
        this.usePlayWS = usePlayWS;
    }

    public void setPlayVersion(String playVersion) {
        this.playVersion = playVersion;
    }

    public void setParcelableModel(boolean parcelableModel) {
        this.parcelableModel = parcelableModel;
    }

    public void setUseBeanValidation(boolean useBeanValidation) {
        this.useBeanValidation = useBeanValidation;
    }

    public void setPerformBeanValidation(boolean performBeanValidation) {
        this.performBeanValidation = performBeanValidation;
    }

    public void setUseGzipFeature(boolean useGzipFeature) {
        this.useGzipFeature = useGzipFeature;
    }

    public void setUseRuntimeException(boolean useRuntimeException) {
        this.useRuntimeException = useRuntimeException;
    }

    final private static Pattern JSON_MIME_PATTERN = Pattern.compile("(?i)application\\/json(;.*)?");
    final private static Pattern JSON_VENDOR_MIME_PATTERN = Pattern.compile("(?i)application\\/vnd.(.*)+json(;.*)?");

    /**
     * Check if the given MIME is a JSON MIME. JSON MIME examples: application/json
     * application/json; charset=UTF8 APPLICATION/JSON
     */
    static boolean isJsonMimeType(String mime) {
        return mime != null && (JSON_MIME_PATTERN.matcher(mime).matches());
    }

    /**
     * Check if the given MIME is a JSON Vendor MIME. JSON MIME examples:
     * application/vnd.mycompany+json
     * application/vnd.mycompany.resourceA.version1+json
     */
    static boolean isJsonVendorMimeType(String mime) {
        return mime != null && JSON_VENDOR_MIME_PATTERN.matcher(mime).matches();
    }

    // override with any special text escaping logic
    @Override
    public String escapeText(String input) {
        if (input == null) {
            return input;
        }

        if (input == "") {
            return null;
        }

        // remove \t, \n, \r
        // replace \ with \\
        // replace " with \"
        // outter unescape to retain the original multi-byte characters
        // finally escalate characters avoiding code injection
        return escapeUnsafeCharacters(
                StringEscapeUtils.unescapeJava(StringEscapeUtils.escapeJava(input).replace("\\/", "/"))
                        .replaceAll("[\\t\\n\\r]", " ").replace("\\", "\\\\").replace("\"", "\\\""));
    }

    // override with model key
    @Override
    public String getModelKey(Object o) {
        String modelKey = null;
        if (o != null) {
            CodegenModel cm = (CodegenModel) o;
            if (cm != null) {
                modelKey = cm.classname;
            }
        }
        return modelKey;
    }

    // override with request key
    @Override
    public String getRequestKey(Object o) {
        String requestKey = null;
        if (o != null) {
            CodegenOperation co = (CodegenOperation) o;
            if (co != null) {
                if (co.bodyParam != null) {
                    requestKey = co.bodyParam.baseType;
                }
            }
        }
        return requestKey;
    }

    @Override
    public List<Map<String, Object>> writeApiModelToFile(List<File> files, List<Object> allOperations,
            List<Object> allModels, Swagger swagger) {
        String serviceType = swagger.getInfo().getTitle();
        List<Tag> swaggerTags = swagger.getTags();
        List<Map<String, Object>> output = new ArrayList<>();
        int year = Calendar.getInstance().get(Calendar.YEAR);
        List<Map<String, Object>> otherFiles = new ArrayList<>();

        if (System.getProperty("debugSwagger") != null) {
            LOGGER.info("############ Swagger info ############");
            Json.prettyPrint(swagger);

            LOGGER.info("############ Operation info ############");
            Json.prettyPrint(allOperations);

            LOGGER.info("############ Model info ############");
            Json.prettyPrint(allModels);
        }

        try {
            for (String templateName : modelTemplateFiles().keySet()) {
                String suffix = modelTemplateFiles().get(templateName);
                for (Tag tag : swaggerTags) {
                    String tagName = tag.getName().toLowerCase();
                    List<String> allApiVersions = getAllApiVersions(allOperations);
                    for (String apiVersion : allApiVersions) {
                        List<Object> allTmpOperations = getOpTmpDataByTagApiVersion(allOperations, tagName, apiVersion);
                        List<Object> allTmpModels = getModelTmpDataByTagApiVersion(allModels, tagName, apiVersion);
                        if (allTmpOperations.isEmpty()) {
                            continue;
                        }

                        // reset
                        List<Object> optionOps = resetOperationId(allTmpOperations, allTmpModels, tagName);

                        // get version
                        String version = getStandardVersion(apiVersion);

                        // generate files by options
                        for (Object tmpOption : optionOps) {
                            CodegenOperation co = (CodegenOperation) tmpOption;
                            if (co == null) {
                                continue;
                            }
                            // eg: KeyFilterOption
                            String classname = co.vendorExtensions.get("x-filteroption").toString();
                            // eg: com.huawei.openstack4j.openstack.csbs.v1.domain
                            String packagename = modelPackage + "." + serviceType.toLowerCase() + "." + version + "."
                                    + modelFixedFolderName;

                            Map<String, Object> templateParam = new HashMap<String, Object>();
                            templateParam.put("classVarName", tagName);
                            templateParam.put("year", year);
                            templateParam.put("classname", classname);
                            templateParam.put("packagename", packagename);
                            templateParam.put("option", co);

                            if (System.getProperty("debugSwagger") != null) {
                                LOGGER.info("############ Option Param info ############");
                                Json.prettyPrint(templateParam);
                            }

                            // eg:core/src/main/java/com/huawei/openstack4j/openstack/csbs/v1/domain/xxfilteroption
                            String filename = (apiFileFolder() + File.separator + serviceType.toLowerCase()
                                    + File.separator + version + File.separator + modelFixedFolderName + File.separator
                                    + classname + suffix);

                            templateParam.put("templateName", extensionOption);
                            templateParam.put("filename", filename);

                            // skip overwriting
                            if (!super.shouldOverwrite(filename) && new File(filename).exists()) {
                                LOGGER.info("Skipped overwriting " + filename);
                                continue;
                            }
                            output.add(templateParam);
                        }

                        // generate files by models
                        for (Object tmpModel : allTmpModels) {
                            Map<String, Object> singleModel = (Map<String, Object>) tmpModel;
                            if (!singleModel.containsKey("model")) {
                                continue;
                            }
                            CodegenModel cm = (CodegenModel) singleModel.get("model");
                            if (cm == null) {
                                continue;
                            }
                            // eg: ListKeyPairResp
                            String classname = cm.classname;
                            if (cm.vendorExtensions.containsKey("x-classname")) {
                                String xclassname = (String) cm.vendorExtensions.get("x-classname");
                                if (xclassname != null && xclassname != "") {
                                    classname = xclassname;
                                }
                            }
                            // eg: com.huawei.openstack4j.openstack.csbs.v1.domain
                            String packagename = modelPackage + "." + serviceType.toLowerCase() + "." + version + "."
                                    + modelFixedFolderName;

                            Map<String, Object> templateParam = new HashMap<String, Object>();
                            templateParam.put("classVarName", tagName);
                            templateParam.put("allmodels", allTmpModels);
                            templateParam.put("alloperations", allTmpOperations);
                            templateParam.put("year", year);
                            templateParam.put("singlemodel", tmpModel);
                            templateParam.put("classname", classname);
                            templateParam.put("packagename", packagename);

                            // eg: core/src/main/java/com/huawei/openstack4j/openstack/csbs/v1/domain/xx
                            String filename = (apiFileFolder() + File.separator + serviceType.toLowerCase()
                                    + File.separator + version + File.separator + modelFixedFolderName + File.separator
                                    + classname + suffix);

                            templateParam.put("templateName", templateName);
                            templateParam.put("filename", filename);

                            // skip overwriting
                            if (!super.shouldOverwrite(filename) && new File(filename).exists()) {
                                LOGGER.info("Skipped overwriting " + filename);
                                continue;
                            }

                            if (System.getProperty("debugSwagger") != null) {
                                LOGGER.info("############ Template Param info ############");
                                Json.prettyPrint(templateParam);
                            }
                            output.add(templateParam);
                        }
                    }
                }
            }
        } catch (

        Exception e) {
            throw new RuntimeException("Could not generate model file", e);
        }

        String apiBaseClassName = "Base" + serviceType + "Service";
        String apiClassName = serviceType + "Service";

        try {
            String suffix = apiTemplateFiles().get(extensionApiBase);
            List<String> allApiVersions = getAllApiVersions(allOperations);
            for (String apiVersion : allApiVersions) {
                List<Object> allTmpOperations = getOpTmpDataByApiVersion(allOperations, apiVersion);
                if (allTmpOperations.isEmpty()) {
                    continue;
                }

                // get version
                String version = getStandardVersion(apiVersion);

                // eg: com.huawei.openstack4j.openstack.csbs.v1.internal
                String packagename = apiPackage + "." + serviceType.toLowerCase() + "." + version + "."
                        + apiFixedFolderName;

                Map<String, Object> templateParam = new HashMap<String, Object>();
                templateParam.put("year", year);
                templateParam.put("packagename", packagename);
                templateParam.put("apiBaseClassName", apiBaseClassName);
                templateParam.put("serviceType", serviceType);

                // eg: core/src/main/java/com/huawei/openstack4j/openstack/csbs/v1/internal/
                String filename = (apiFileFolder() + File.separator + serviceType.toLowerCase() + File.separator
                        + version + File.separator + apiFixedFolderName + File.separator + apiBaseClassName + suffix);
                if (!super.shouldOverwrite(filename) && new File(filename).exists()) {
                    LOGGER.info("Skipped overwriting " + filename);
                    continue;
                }
                templateParam.put("templateName", extensionApiBase);
                templateParam.put("filename", filename);
                output.add(templateParam);
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not generate api base file", e);
        }

        try {
            List<String> allApiVersions = getAllApiVersions(allOperations);
            for (String apiVersion : allApiVersions) {
                List<Map<String, Object>> apiImplClassNames = new ArrayList<>();
                // get version
                String version = getStandardVersion(apiVersion);
                for (Tag tag : swaggerTags) {
                    String tagName = tag.getName().toLowerCase();
                    List<Object> allTmpOperations = getOpTmpDataByTagApiVersion(allOperations, tagName, apiVersion);
                    List<Object> allTmpModels = getModelTmpDataByTagApiVersion(allModels, tagName, apiVersion);
                    if (allTmpOperations.isEmpty()) {
                        continue;
                    }

                    // reset
                    resetOperationId(allTmpOperations, allTmpModels, tagName);

                    // validate
                    addRequestValidate(allTmpOperations, allTmpModels, tagName);

                    String suffix = apiTemplateFiles().get(extensionApiImpl);

                    // eg: com.huawei.openstack4j.openstack.csbs.v1.internal
                    String packagename = apiPackage + "." + serviceType.toLowerCase() + "." + version + "."
                            + apiFixedFolderName;
                    // eg: com.huawei.openstack4j.openstack.csbs.v1.domain
                    String importpackagename = apiPackage + "." + serviceType.toLowerCase() + "." + version + "."
                            + modelFixedFolderName;

                    String apiImplClassName = /*serviceType +*/camelize(tagName) + "Service";

                    // fill with HashMap
                    Map<String, Object> m = new HashMap<>();
                    m.put("tagName", tagName);
                    m.put("apiImplClassName", apiImplClassName);
                    apiImplClassNames.add(m);

                    Map<String, Object> templateParam = new HashMap<String, Object>();
                    templateParam.put("tagName", tagName);
                    templateParam.put("allmodels", allTmpModels);
                    templateParam.put("alloperations", allTmpOperations);
                    templateParam.put("year", year);
                    templateParam.put("packagename", packagename);
                    templateParam.put("importpackagename", importpackagename);
                    templateParam.put("apiBaseClassName", apiBaseClassName);
                    templateParam.put("apiClassName", apiClassName);
                    templateParam.put("apiImplClassName", apiImplClassName);
                    templateParam.put("serviceType", serviceType);

                    // eg: core/src/main/java/com/huawei/openstack4j/openstack/csbs/v1/internal/
                    String filename = (apiFileFolder() + File.separator + serviceType.toLowerCase() + File.separator
                            + version + File.separator + apiFixedFolderName + File.separator + apiImplClassName
                            + suffix);
                    if (!super.shouldOverwrite(filename) && new File(filename).exists()) {
                        LOGGER.info("Skipped overwriting " + filename);
                        continue;
                    }
                    templateParam.put("templateName", extensionApiImpl);
                    templateParam.put("filename", filename);
                    output.add(templateParam);
                }

                String importpackagename = null;
                if (apiImplClassNames.size() > 0) {
                    String suffix = apiTemplateFiles().get(extensionApi);

                    // eg: com.huawei.openstack4j.openstack.csbs.v1.internal
                    String packagename = apiPackage + "." + serviceType.toLowerCase() + "." + version + "."
                            + apiFixedFolderName;
                    importpackagename = packagename;

                    Map<String, Object> templateParam = new HashMap<String, Object>();
                    templateParam.put("year", year);
                    templateParam.put("packagename", packagename);
                    templateParam.put("apiBaseClassName", apiBaseClassName);
                    templateParam.put("apiClassName", apiClassName);
                    templateParam.put("apiImplClassNames", apiImplClassNames);
                    templateParam.put("serviceType", serviceType);

                    // eg: core/src/main/java/com/huawei/openstack4j/openstack/csbs/v1/internal/
                    String filename = (apiFileFolder() + File.separator + serviceType.toLowerCase() + File.separator
                            + version + File.separator + apiFixedFolderName + File.separator + apiClassName + suffix);
                    if (!super.shouldOverwrite(filename) && new File(filename).exists()) {
                        LOGGER.info("Skipped overwriting " + filename);
                        continue;
                    }
                    templateParam.put("templateName", extensionApi);
                    templateParam.put("filename", filename);
                    output.add(templateParam);
                }

                // Fill with other files
                Map<String, Object> ofs = new HashMap<String, Object>();
                ofs.put("apiVersion", apiVersion);
                ofs.put("version", version);
                ofs.put("importpackagename", importpackagename);
                ofs.put("apiBaseClassName", apiBaseClassName);
                ofs.put("apiClassName", apiClassName);
                ofs.put("apiImplClassNames", apiImplClassNames);
                ofs.put("serviceType", serviceType);
                ofs.put("serviceTypeUpperCase", serviceType.toUpperCase());
                ofs.put("serviceTypeLowerCase", serviceType.toLowerCase());
                otherFiles.add(ofs);
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not generate api file", e);
        }

        try {
            for (String templateName : otherTemplateFiles.keySet()) {
                String suffix = otherTemplateFiles.get(templateName);
                String filename = null;
                if (templateName.equals(extensionserviceendpoint)) {
                    // core/src/main/resources/service_endpoint.json
                    filename = outputFolder + File.separator + projectFolder + File.separator + "resources"
                            + File.separator + "service_endpoint.json";
                } else if (templateName.equals(extensionservicetype)) {
                    // core/src/main/java/com/huawei/openstack4j/api/types/ServiceType.java
                    filename = outputFolder + File.separator + projectFolder + File.separator + "java" + File.separator
                            + "com" + File.separator + "huawei" + File.separator + "openstack4j" + File.separator
                            + "api" + File.separator + "types" + File.separator + "ServiceType.java";
                } else if (templateName.equals(extensionosclient)) {
                    // core/src/main/java/com/huawei/openstack4j/api/OSClient.java
                    filename = outputFolder + File.separator + projectFolder + File.separator + "java" + File.separator
                            + "com" + File.separator + "huawei" + File.separator + "openstack4j" + File.separator
                            + "api" + File.separator + "OSClient.java";
                } else if (templateName.equals(extensionosclientsession)) {
                    // core/src/main/java/com/huawei/openstack4j/openstack/internal/OSClientSession.java
                    filename = apiFileFolder() + File.separator + "internal" + File.separator + "OSClientSession.java";
                } else if (templateName.equals(extensiondefaultapiprovider)) {
                    // core/src/main/java/com/huawei/openstack4j/openstack/provider/DefaultAPIProvider.java
                    filename = apiFileFolder() + File.separator + "provider" + File.separator
                            + "DefaultAPIProvider.java";
                }

                if (filename != null) {
                    if (!super.shouldOverwrite(filename) && new File(filename).exists()) {
                        LOGGER.info("Skipped overwriting " + filename);
                        continue;
                    }
                    Map<String, Object> templateParam = new HashMap<String, Object>();
                    templateParam.put("year", year);
                    templateParam.put("otherFiles", otherFiles);
                    templateParam.put("templateName", templateName);
                    templateParam.put("filename", filename);
                    output.add(templateParam);

                    if (System.getProperty("debugSwagger") != null) {
                        LOGGER.info("############ Other Param info ############");
                        Json.prettyPrint(templateParam);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not generate other files", e);
        }

        return output;
    }

    private List<String> getAllApiVersions(List<Object> allOperations) {
        List<String> allApiVersions = new ArrayList<String>();
        for (Object allItems : allOperations) {
            Map<String, Object> item = (Map<String, Object>) allItems;
            if (!item.containsKey("operations")) {
                continue;
            }
            Map<String, Object> operations = (Map<String, Object>) item.get("operations");
            if (!operations.containsKey("operation")) {
                continue;
            }
            List<CodegenOperation> operation = (List<CodegenOperation>) operations.get("operation");
            for (CodegenOperation op : operation) {
                if (op.path == null) {
                    continue;
                }
                String opVersion = getApiVersionByOp(op);
                if (!opVersion.isEmpty() && (!allApiVersions.contains(opVersion))) {
                    allApiVersions.add(opVersion);
                }
            }
        }
        return allApiVersions;
    }

    @Override
    public String getApiVersionByOp(CodegenOperation op) {
        return super.getVersionByPath(op.path.toString());
    }

    private String getStandardVersion(String version) {
        String apiVersion = version;
        String[] v = version.split("\\.");
        if (v.length > 1) {
            String decimal = v[1];
            if (decimal.equals("0")) {
                apiVersion = v[0];
            }
        }
        return apiVersion.toLowerCase();
    }

    private List<Object> getModelTmpDataByTagApiVersion(List<Object> allModels, String tagName, String apiVersion) {
        List<Object> allTmpModels = new ArrayList<Object>();
        for (int i = 0; i < allModels.size(); i++) {
            Map<String, Object> model = (Map<String, Object>) allModels.get(i);
            if (!model.containsKey("tagsInfo")) {
                continue;
            }
            List<Object> tagsInfo = (List<Object>) model.get("tagsInfo");
            for (int iInfo = 0; iInfo < tagsInfo.size(); iInfo++) {
                Map<String, Object> tagInfo = (Map<String, Object>) tagsInfo.get(iInfo);
                if (!tagInfo.containsKey("classVarName") || !tagInfo.containsKey("apiVersion")) {
                    continue;
                }
                String modelTagName = tagInfo.get("classVarName").toString();
                String modelapiVersion = tagInfo.get("apiVersion").toString();

                if (tagName.equals(modelTagName) && apiVersion.equals(modelapiVersion)) {
                    if (tagInfo.containsKey("isReq") && (boolean) tagInfo.get("isReq")) {
                        model.put("isReq", tagInfo.get("isReq"));
                    } else {
                        model.put("isReq", false);
                    }

                    if (tagInfo.containsKey("isResp") && (boolean) tagInfo.get("isResp")) {
                        model.put("isResp", tagInfo.get("isResp"));
                    } else {
                        model.put("isResp", false);
                    }

                    allTmpModels.add(model);
                    break;
                }
            }
        }
        return allTmpModels;
    }

    private List<Object> getOpTmpDataByTagApiVersion(List<Object> allOperations, String tagName, String apiVersion) {
        List<Object> allTmpOperations = new ArrayList<Object>();
        String opTagName = "";
        for (Object allItems : allOperations) {
            boolean hasQueryParams = false;
            Map<String, Object> item = (Map<String, Object>) allItems;
            Map<String, Object> tmpItem = new HashMap<String, Object>();
            Map<String, Object> tmpOperations = new HashMap<String, Object>();
            if (!item.containsKey("operations")) {
                continue;
            }

            Map<String, Object> operations = (Map<String, Object>) item.get("operations");
            if (!operations.containsKey("operation")) {
                continue;
            }
            List<CodegenOperation> operation = (List<CodegenOperation>) operations.get("operation");
            List<CodegenOperation> tmpOperation = new ArrayList<CodegenOperation>();
            for (CodegenOperation op : operation) {
                if (op.path == null) {
                    continue;
                }

                String opVersion = getApiVersionByOp(op);
                if (apiVersion.equals(opVersion)) {
                    tmpOperation.add(op);
                }
                if (op.vendorExtensions.containsKey("x-isPage")) {
                    hasQueryParams = true;
                }
            }
            tmpOperations.put("operation", tmpOperation);
            tmpItem.put("operations", tmpOperations);
            if (hasQueryParams == true) {
                tmpItem.put("hasQueryPagin", true);
            }

            opTagName = item.get("classVarName").toString();

            if (tagName.equals(opTagName) && !tmpOperation.isEmpty()) {
                allTmpOperations.add(tmpItem);
            }
        }

        return allTmpOperations;
    }

    private List<Object> getModelTmpDataByApiVersion(List<Object> allModels, String apiVersion) {
        List<Object> allTmpModels = new ArrayList<Object>();
        for (int i = 0; i < allModels.size(); i++) {
            Map<String, Object> model = (Map<String, Object>) allModels.get(i);
            if (!model.containsKey("tagsInfo")) {
                continue;
            }
            List<Object> tagsInfo = (List<Object>) model.get("tagsInfo");
            for (int iInfo = 0; iInfo < tagsInfo.size(); iInfo++) {
                Map<String, Object> tagInfo = (Map<String, Object>) tagsInfo.get(iInfo);
                if (!tagInfo.containsKey("apiVersion")) {
                    continue;
                }
                String modelapiVersion = tagInfo.get("apiVersion").toString();
                if (apiVersion.equals(modelapiVersion)) {
                    Map<String, Object> tmpModel = new HashMap<String, Object>();
                    // copyModel
                    for (String key : model.keySet()) {
                        tmpModel.put(key, model.get(key));
                    }

                    if (tagInfo.containsKey("isReq") && (boolean) tagInfo.get("isReq")) {
                        tmpModel.put("isReq", tagInfo.get("isReq"));
                    } else {
                        tmpModel.put("isReq", false);
                    }

                    if (tagInfo.containsKey("isResp") && (boolean) tagInfo.get("isResp")) {
                        tmpModel.put("isResp", tagInfo.get("isResp"));
                    } else {
                        tmpModel.put("isResp", false);
                    }

                    allTmpModels.add(tmpModel);
                    break;
                }
            }
        }
        return allTmpModels;
    }

    private List<Object> getOpTmpDataByApiVersion(List<Object> allOperations, String apiVersion) {
        List<Object> allTmpOperations = new ArrayList<Object>();
        String opTagName = "";
        for (Object allItems : allOperations) {
            boolean hasQueryParams = false;
            Map<String, Object> item = (Map<String, Object>) allItems;
            Map<String, Object> tmpItem = new HashMap<String, Object>();
            Map<String, Object> tmpOperations = new HashMap<String, Object>();
            if (!item.containsKey("operations")) {
                continue;
            }

            Map<String, Object> operations = (Map<String, Object>) item.get("operations");
            if (!operations.containsKey("operation")) {
                continue;
            }
            List<CodegenOperation> operation = (List<CodegenOperation>) operations.get("operation");
            List<CodegenOperation> tmpOperation = new ArrayList<CodegenOperation>();
            for (CodegenOperation op : operation) {
                if (op.path == null) {
                    continue;
                }

                String opVersion = getApiVersionByOp(op);
                if (apiVersion.equals(opVersion)) {
                    tmpOperation.add(op);
                }
                if (op.vendorExtensions.containsKey("x-isPage")) {
                    hasQueryParams = true;
                }
            }
            tmpOperations.put("operation", tmpOperation);
            tmpItem.put("operations", tmpOperations);
            if (hasQueryParams == true) {
                tmpItem.put("hasQueryPagin", true);
            }

            if (!tmpOperation.isEmpty()) {
                allTmpOperations.add(tmpItem);
            }
        }

        return allTmpOperations;
    }

    private boolean getOperationParamsRequired(CodegenOperation op) {
        boolean required = false;
        int length = 0;
        for (CodegenParameter p : op.pathParams) {
            if (p.vendorExtensions.containsKey("x-isProject")
                    && (boolean) p.vendorExtensions.get("x-isProject") == true) {
                continue;
            }
            length++;
            if (p.required) {
                required = true;
                return required;
            }
        }
        for (CodegenParameter p : op.queryParams) {
            length++;
            if (p.required) {
                required = true;
                return required;
            }
        }
        for (CodegenParameter p : op.bodyParams) {
            length++;
            if (p.required) {
                required = true;
                return required;
            }
        }
        if (length == 0) {
            required = true;
        }
        return required;
    }

    private List<Object> resetOperationId(List<Object> allTmpOperations, List<Object> allTmpModels, String tagName) {
        List<Object> optionOps = new ArrayList<Object>();
        for (Object allItems : allTmpOperations) {
            Map<String, Object> item = (Map<String, Object>) allItems;
            if (!item.containsKey("operations")) {
                continue;
            }

            Map<String, Object> operations = (Map<String, Object>) item.get("operations");
            if (!operations.containsKey("operation")) {
                continue;
            }
            List<CodegenOperation> operation = (List<CodegenOperation>) operations.get("operation");

            Map<String, String> newApiOpIds = getApiReqFuncName(operation);

            for (CodegenOperation op : operation) {
                String newOpId = newApiOpIds.get(op.operationId.toString());
                op.vendorExtensions.put("x-nickname", newOpId);

                boolean isExtractInfo = false;
                if (op.vendorExtensions.containsKey("isExtractInfo")
                        && (boolean) op.vendorExtensions.get("isExtractInfo") == true) {
                    isExtractInfo = true;
                }
                if (!isExtractInfo) {
                    Map<String, String> newClassNames = getNewClassNamesByTagName(tagName);
                    if (newClassNames.containsKey(newOpId)) {
                        op.vendorExtensions.put("x-classname", newClassNames.get(newOpId));
                    } else {
                        if (op.returnBaseType != null) {
                            String originalClassname = getIrregularClassName(toModelName(op.returnBaseType.toString()));
                            op.vendorExtensions.put("x-classname", originalClassname);
                        }
                    }
                }

                // get list
                if (newOpId.toLowerCase().startsWith("get") || newOpId.toLowerCase().startsWith("list")) {
                    boolean required = getOperationParamsRequired(op);
                    if (!required) {
                        op.vendorExtensions.put("x-notrequired", true);
                    }
                    // QueryParams size
                    if (op.queryParams.size() > 1) {
                        String prefix = op.returnBaseType;
                        if (op.vendorExtensions.containsKey("x-classname")) {
                            prefix = op.vendorExtensions.get("x-classname").toString();
                        }
                        op.vendorExtensions.put("x-filteroption", prefix + "FilterOption");
                        optionOps.add(op);
                    }
                }
            }
            resetModelNames(newApiOpIds, allTmpModels, tagName);
        }
        return optionOps;
    }

    private Map<String, String> getApiReqFuncName(List<CodegenOperation> operation) {
        String newApiFunName = "";
        Map<String, Object> apiIds = new HashMap<String, Object>();
        for (CodegenOperation op : operation) {
            String opId = op.operationId.toString().toLowerCase();
            newApiFunName = getApiFunNameByOpIdMethod(opId);

            if (newApiFunName == "") {
                newApiFunName = getApiFunNameByMethod(op);
            }

            if (newApiFunName == "") {
                newApiFunName = getApiFunNameByOpId(op, apiIds);
            }

            if (apiIds.containsKey(newApiFunName)) {
                reduceDupName(newApiFunName, op, apiIds);
            } else {
                apiIds.put(newApiFunName, op);
            }
        }

        Map<String, String> outPutApiIds = new HashMap<String, String>();
        for (String name : apiIds.keySet()) {
            CodegenOperation tmpOp = (CodegenOperation) apiIds.get(name);
            outPutApiIds.put(tmpOp.operationId.toString(), name);
        }

        return outPutApiIds;
    }

    private String getApiFunNameByOpIdMethod(String opId) {
        String newApiFunName = "";
        Map<String, String> apiOpIdContansMethods = new HashMap<String, String>();
        apiOpIdContansMethods.put("create", "create");
        apiOpIdContansMethods.put("update", "update");
        apiOpIdContansMethods.put("list", "list");
        apiOpIdContansMethods.put("get", "get");
        apiOpIdContansMethods.put("batch", "list");
        apiOpIdContansMethods.put("modify", "update");

        for (String method : apiOpIdContansMethods.keySet()) {
            if (opId.contains(method)) {
                newApiFunName = apiOpIdContansMethods.get(method);
                break;
            }
        }

        return newApiFunName;
    }

    private String getApiFunNameByMethod(CodegenOperation op) {
        String newApiFunName = "";
        Map<String, String> apiMethods = new HashMap<String, String>();
        apiMethods.put("x-is-delete-method", "delete");
        apiMethods.put("x-is-get-method", "get");

        for (String mt : apiMethods.keySet()) {
            if (op.vendorExtensions.containsKey(mt)) {
                newApiFunName = apiMethods.get(mt);
                break;
            }
        }

        return newApiFunName;
    }

    private String getApiFunNameByOpId(CodegenOperation op, Map<String, Object> apiIds) {
        String tmpOpId = op.operationId.toString();
        String baseName = op.baseName.toString();

        String newApiFunName = tmpOpId.replace(baseName, "");
        if (newApiFunName.length() < 2) {
            newApiFunName = tmpOpId;
        }

        if (apiIds.containsKey(newApiFunName)) {
            newApiFunName = tmpOpId;
        }

        return newApiFunName;
    }

    private void reduceDupName(String newApiFunName, CodegenOperation op, Map<String, Object> apiIds) {
        CodegenOperation preDupOp = (CodegenOperation) apiIds.get(newApiFunName);
        CodegenOperation originMethodNameOp = preDupOp;
        CodegenOperation resetOp = op;

        boolean changeOp = false;
        if (newApiFunName == "get") {
            String tagName = op.baseName.toString().toLowerCase();
            String preOpId = preDupOp.operationId.toString().toLowerCase();
            String opId = op.operationId.toString().toLowerCase();

            if ((!preOpId.contains(tagName)) && opId.contains(tagName)) {
                changeOp = true;
            } else if (preDupOp.queryParams.size() > op.queryParams.size()) {
                changeOp = true;
            }
        } else if (newApiFunName == "create" || newApiFunName == "update") {
            if (preDupOp.bodyParams.size() > op.bodyParams.size()) {
                changeOp = true;
            }
        } else if (preDupOp.path.length() > op.path.length()) {
            changeOp = true;
        }

        if (changeOp == true) {
            resetOp = preDupOp;
            originMethodNameOp = op;
        }

        String resetOpNewApiFunName = getApiFunNameByOpId(resetOp, apiIds);
        apiIds.put(resetOpNewApiFunName, resetOp);
        apiIds.put(newApiFunName, originMethodNameOp);
    }

    private Map<String, String> getNewClassNamesByTagName(String tagName) {
        Map<String, String> newClassNames = new HashMap<String, String>();
        newClassNames.put("get", camelize(tagName));
        newClassNames.put("list", camelize(tagName) + "s");
        newClassNames.put("create", camelize(tagName) + "Resp");

        return newClassNames;
    }

    private void resetModelNames(Map<String, String> newApiOpIds, List<Object> allTmpModels, String tagName) {
        Map<String, String> newClassNames = getNewClassNamesByTagName(tagName);
        for (int i = 0; i < allTmpModels.size(); i++) {
            Map<String, Object> model = (Map<String, Object>) allTmpModels.get(i);
            CodegenModel m = (CodegenModel) model.get("model");

            // reset operationId in model
            if (model.containsKey("nickname") && newApiOpIds.containsKey(model.get("nickname").toString())) {
                String newOpId = newApiOpIds.get(model.get("nickname").toString());
                m.vendorExtensions.put("x-nickname", newOpId);
            }

            if (model.containsKey("isExtractInfo") && (boolean) model.get("isExtractInfo") == true) {
                if (m.vendorExtensions.containsKey("x-nickname")) {
                    String newOpId = m.vendorExtensions.get("x-nickname").toString();
                    if (newClassNames.containsKey(newOpId)) {
                        m.vendorExtensions.put("x-classname", newClassNames.get(newOpId));
                    } else {
                        String originalClassname = getIrregularClassName(m.classname.toString());
                        m.vendorExtensions.put("x-classname", originalClassname);
                    }
                }
            }
        }
    }

    private String getIrregularClassName(String originalClassName) {
        List<String> methods = Arrays.asList("Create", "Delete", "Get", "List", "Update", "Resp");
        String newClassName = originalClassName;

        for (int i = 0; i < methods.size(); i++) {
            String method = methods.get(i);
            if (newClassName.contains(method)) {
                newClassName = newClassName.replace(method, "");
            }
        }

        if (newClassName == "") {
            newClassName = originalClassName;
        }

        return newClassName;
    }

    protected String strTemplate ="\n%scheckArgument(!Strings.isNullOrEmpty(%s), \"parameter `%s` should not be empty\");";
    protected String otherTemplate ="\n%scheckArgument(null != %s, \"parameter `%s` should not be null\");";
    protected String forBeginTemplate = "\n%sfor(int %s=0; %s<%s.size(); %s++) {";
    protected String forEndTemplate = "\n%s}";
    protected String[] indexCache = {"i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z", "a", "b", "c", "d", "e", "f", "g", "h"};
    private String lookupFromModel(List<Object> allTmpModels, String complexType, Boolean isContainer, String currentVar, String space, int index, String strValidate){
        // Complex Type
        if(complexType!=null && complexType!="") {
            LOGGER.info("############ complexType ############" + complexType);
            // Lookup from model
            for (int i = 0; i < allTmpModels.size(); i++) {
                Map<String, Object> model = (Map<String, Object>) allTmpModels.get(i);
                CodegenModel m = (CodegenModel) model.get("model");
                if(m != null) {
                    if (m.classname.equals(complexType)) {
                        LOGGER.info("############ classname ############", m.classname);
                        // base var
                        String baseVar = currentVar;
                        // base space
                        String baseSpace = space;
                        // Array
                        if(isContainer) {
                            // for begin
                            String baseIndex = getIndexFromCache(index);
                            baseVar = currentVar+".get("+baseIndex+")";
                            strValidate += String.format(forBeginTemplate, space, baseIndex, baseIndex, currentVar, baseIndex);
                            baseSpace += "    ";
                            strValidate += String.format(otherTemplate, baseSpace, baseVar, m.classname);
                            index++;
                        }
                        // Required Parameter
                        if (m.hasRequired) {
                            for (CodegenProperty cp : m.requiredVars) {
                                if (cp!=null) {
                                    // tmp var
                                    String tmpVar = baseVar+"."+cp.getter+"()";
                                    if (cp.isString) {
                                        // String
                                        strValidate += String.format(strTemplate, baseSpace, tmpVar, cp.name);
                                    } else {
                                        // Other
                                        strValidate += String.format(otherTemplate, baseSpace, tmpVar, cp.name);
                                    }
                                    // Recursion
                                    strValidate = lookupFromModel(allTmpModels, cp.complexType, cp.isContainer, tmpVar, baseSpace, index, strValidate);
                                }
                            }
                        }
                        // Array
                        if(isContainer) {
                            // for end
                            strValidate += String.format(forEndTemplate, space);
                        }
                        break;
                    }
                }
            }
        }
        return strValidate;
    }

    private String getIndexFromCache(int index) {
        if (index >= indexCache.length){
            return indexCache[0]+index;
        } else {
            return indexCache[index];
        }
    }

    private void addRequestValidate(List<Object> allTmpOperations, List<Object> allTmpModels, String tagName) {
        for (Object allItems : allTmpOperations) {
            Map<String, Object> item = (Map<String, Object>) allItems;
            if (!item.containsKey("operations")) {
                continue;
            }

            Map<String, Object> operations = (Map<String, Object>) item.get("operations");
            if (!operations.containsKey("operation")) {
                continue;
            }

            List<CodegenOperation> operation = (List<CodegenOperation>) operations.get("operation");
            for (CodegenOperation op : operation) {
                if (op.bodyParams!=null) {
                    // Init str
                    String strValidate = "";
                    for (CodegenParameter p : op.bodyParams) {
                        // Required Parameter
                        if (p.required) {
                            // init blank space
                            String space ="        ";
                            // init index
                            int index = 0;
                            if (p.isString) {
                                // String
                                strValidate += String.format(strTemplate, space, p.paramName, p.paramName);
                            } else {
                                // Other
                                strValidate += String.format(otherTemplate, space, p.paramName, p.paramName); 
                            }
                            // Complex Type
                            if (!p.isPrimitiveType) {
                                strValidate = lookupFromModel(allTmpModels, p.baseType, p.isContainer, p.paramName, space, index, strValidate);
                            }
                        }
                    }
                    // add x-request-validate
                    if(strValidate!=null && strValidate!="") {
                        op.vendorExtensions.put("x-request-validate", strValidate);
                    }
                }
            }
        }
    }
}
