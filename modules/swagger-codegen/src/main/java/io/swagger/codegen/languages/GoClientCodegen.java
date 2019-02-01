package io.swagger.codegen.languages;

import io.swagger.codegen.*;
import io.swagger.models.properties.*;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.MapProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.Swagger;
import io.swagger.models.Tag;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoClientCodegen extends AbstractGoCodegen {

    protected String packageVersion = "1.0.0";
    protected String apiDocPath = "docs/";
    protected String modelDocPath = "docs/";
    public static final String WITH_XML = "withXml";

    public GoClientCodegen() {
        super();

        outputFolder = "generated-code/go";
        modelTemplateFiles.put("results.mustache", ".go");
        apiTestTemplateFiles.put("requests_test.mustache", ".go");
        apiTemplateFiles.put("requests.mustache", ".go");
        apiTemplateFiles.put("urls.mustache", ".go");
        apiTemplateFiles.put("results.mustache", ".go");

        modelDocTemplateFiles.put("model_doc.mustache", ".md");
        apiDocTemplateFiles.put("api_doc.mustache", ".md");

        embeddedTemplateDir = templateDir = "go";

        // default HIDE_GENERATION_TIMESTAMP to true
        hideGenerationTimestamp = Boolean.TRUE;

        setReservedWordsLowerCase(
            Arrays.asList(
                // data type
                "string", "bool", "uint", "uint8", "uint16", "uint32", "uint64",
                "int", "int8", "int16", "int32", "int64", "float32", "float64",
                "complex64", "complex128", "rune", "byte", "uintptr",

                "break", "default", "func", "interface", "select",
                "case", "defer", "go", "map", "struct",
                "chan", "else", "goto", "package", "switch",
                "const", "fallthrough", "if", "range",
                "continue", "for", "import", "return", "var", "error", "ApiResponse", "nil")
                // Added "error" as it's used so frequently that it may as well be a keyword
        );

        cliOptions.add(new CliOption(CodegenConstants.PACKAGE_VERSION, "Go package version.")
                .defaultValue("1.0.0"));
        cliOptions.add(CliOption.newBoolean(WITH_XML, "whether to include support for application/xml content type and include XML annotations in the model (works with libraries that provide support for JSON and XML)"));

    }

    @Override
    public Map<String, String> typeMapping() {
        typeMapping.put("integer", "int");
        return typeMapping;
    }

    @Override
    public void processOpts() {
        super.processOpts();

        if (additionalProperties.containsKey(CodegenConstants.PACKAGE_NAME)) {
            setPackageName((String) additionalProperties.get(CodegenConstants.PACKAGE_NAME));
        }
        else {
            setPackageName("swagger");
        }

        if (additionalProperties.containsKey(CodegenConstants.PACKAGE_VERSION)) {
            setPackageVersion((String) additionalProperties.get(CodegenConstants.PACKAGE_VERSION));
        }
        else {
            setPackageVersion("1.0.0");
        }

        additionalProperties.put(CodegenConstants.PACKAGE_NAME, packageName);
        additionalProperties.put(CodegenConstants.PACKAGE_VERSION, packageVersion);

        additionalProperties.put("apiDocPath", apiDocPath);
        additionalProperties.put("modelDocPath", modelDocPath);

        modelPackage = packageName;
        apiPackage = packageName;

        supportingFiles.add(new SupportingFile("swagger.mustache", "api", "swagger.yaml"));
        supportingFiles.add(new SupportingFile("README.mustache", "", "README.md"));
        supportingFiles.add(new SupportingFile("git_push.sh.mustache", "", "git_push.sh"));
        supportingFiles.add(new SupportingFile("gitignore.mustache", "", ".gitignore"));
        supportingFiles.add(new SupportingFile("configuration.mustache", "", "configuration.go"));
        supportingFiles.add(new SupportingFile("client.mustache", "", "client.go"));
        supportingFiles.add(new SupportingFile("response.mustache", "", "response.go"));
        supportingFiles.add(new SupportingFile(".travis.yml", "", ".travis.yml"));

        if(additionalProperties.containsKey(WITH_XML)) {
            setWithXml(Boolean.parseBoolean(additionalProperties.get(WITH_XML).toString()));
            if ( withXml ) {
                additionalProperties.put(WITH_XML, "true");
            }
        }
    }

    /**
     * Configures the type of generator.
     *
     * @return the CodegenType for this generator
     * @see io.swagger.codegen.CodegenType
     */
    @Override
    public CodegenType getTag() {
        return CodegenType.CLIENT;
    }

    /**
     * Configures a friendly name for the generator.  This will be used by the generator
     * to select the library with the -l flag.
     *
     * @return the friendly name for the generator
     */
    @Override
    public String getName() {
        return "go";
    }

    /**
     * Returns human-friendly help for the generator.  Provide the consumer with help
     * tips, parameters here
     *
     * @return A string value for the help message
     */
    @Override
    public String getHelp() {
        return "Generates a Go client library (beta).";
    }

    /**
     * Location to write api files.  You can use the apiPackage() as defined when the class is
     * instantiated
     */
    @Override
    public String apiFileFolder() {
        return outputFolder + File.separator;
    }

    @Override
    public String modelFileFolder() {
        return outputFolder + File.separator;
    }

    @Override
    public String apiDocFileFolder() {
        return (outputFolder + "/" + apiDocPath).replace('/', File.separatorChar);
    }

    @Override
    public String modelDocFileFolder() {
        return (outputFolder + "/" + modelDocPath).replace('/', File.separatorChar);
    }

    @Override
    public String toModelDocFilename(String name) {
        return toModelName(name);
    }

    @Override
    public String toApiDocFilename(String name) {
        return toApiName(name);
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
                StringEscapeUtils.unescapeJava(
                        StringEscapeUtils.escapeJava(input)
                                .replace("\\/", "/"))
                        .replaceAll("[\\t\\n\\r]"," ")
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\""));
    }

    /**
     * Return the default value of the property
     *
     * @param p Swagger property object
     * @return string presentation of the default value of the property
     */
    @Override
    public String toDefaultValue(Property p) {
        if (p instanceof StringProperty) {
            StringProperty dp = (StringProperty) p;
            if (dp.getDefault() != null) {
                if (Pattern.compile("\r\n|\r|\n").matcher(dp.getDefault()).find())
                    return "'''" + dp.getDefault() + "'''";
                else
                    return "'" + dp.getDefault() + "'";
            }
        } else if (p instanceof BooleanProperty) {
            BooleanProperty dp = (BooleanProperty) p;
            if (dp.getDefault() != null) {
                if (dp.getDefault().toString().equalsIgnoreCase("false"))
                    return "False";
                else
                    return "True";
            }
        } else if (p instanceof DateProperty) {
            // TODO
        } else if (p instanceof DateTimeProperty) {
            // TODO
        } else if (p instanceof DoubleProperty) {
            DoubleProperty dp = (DoubleProperty) p;
            if (dp.getDefault() != null) {
                return dp.getDefault().toString();
            }
        } else if (p instanceof FloatProperty) {
            FloatProperty dp = (FloatProperty) p;
            if (dp.getDefault() != null) {
                return dp.getDefault().toString();
            }
        } else if (p instanceof IntegerProperty) {
            IntegerProperty dp = (IntegerProperty) p;
            if (dp.getDefault() != null) {
                return dp.getDefault().toString();
            }
        } else if (p instanceof LongProperty) {
            LongProperty dp = (LongProperty) p;
            if (dp.getDefault() != null) {
                return dp.getDefault().toString();
            }
        }

        return null;
    }

    public void setPackageVersion(String packageVersion) {
        this.packageVersion = packageVersion;
    }

    private void addApiTestTemplateParam(String tagName, String serviceType, String version, String apiVersion,
                                      List<Object> allTmpModels, List<Object> allTmpOperations,
                                      List<Map<String, Object>> output) {
        for (String templateName : apiTestTemplateFiles().keySet()) {
            String suffix = apiTestTemplateFiles().get(templateName);

            Map<String, Object> templateParam = new HashMap<String, Object>();
            templateParam.put("classVarName", tagName);
            templateParam.put("serviceType", serviceType);
            templateParam.put("version", version);
            templateParam.put("allmodels", allTmpModels);
            templateParam.put("alloperations", allTmpOperations);

            // eg: /tmp/1_0_0/compute/v1/instance/request.go
            String filename = (apiFileFolder() + version + File.separator + "acceptance" + File.separator +
                    serviceType + File.separator + apiVersion + File.separator + tagName + "_test" + suffix);
            if (!super.shouldOverwrite(filename) && new File(filename).exists()) {
                LOGGER.info("Skipped overwriting " + filename);
                continue;
            }
            templateParam.put("templateName", templateName);
            templateParam.put("filename", filename);
            output.add(templateParam);
        }
    }

    private void addApiTemplateParam(String tagName, String serviceType, String version, String apiVersion,
                                       List<Object> allTmpModels, List<Object> allTmpOperations,
                                       List<Map<String, Object>> output) {
        for (String templateName : apiTemplateFiles().keySet()) {
            Map<String, Object> templateParam = new HashMap<String, Object>();
            templateParam.put("classVarName", tagName);
            templateParam.put("serviceType", serviceType);
            templateParam.put("version", version);
            templateParam.put("allmodels", allTmpModels);
            templateParam.put("alloperations", allTmpOperations);
            String suffix = apiTemplateFiles().get(templateName);

            String folderName = getFolderNameByTagName(tagName);
            templateParam.put("folderName", folderName);
            String preName = templateName.split("\\.")[0];
            // eg: /tmp/1_0_0/compute/v1/instance/request.go
            String filename = (apiFileFolder() + version + File.separator + serviceType + File.separator
                    + apiVersion + File.separator + folderName + File.separator + preName + suffix);
            if (!super.shouldOverwrite(filename) && new File(filename).exists()) {
                LOGGER.info("Skipped overwriting " + filename);
                continue;
            }
            templateParam.put("templateName", templateName);
            templateParam.put("filename", filename);
            output.add(templateParam);
        }
    }

    private String getFolderNameByTagName(String tagName) {
        String folderName = "";
        if (tagName != null) {
            char c = tagName.charAt(tagName.length() - 1);
            String tmp = Character.toString(c);
            if (tmp.equals("s")) {
                folderName = tagName;
            } else {
                folderName = tagName + "s";
            }
        }

        return folderName;
    }

    private void resetOperationId(List<Object> allTmpOperations, List<Object> allTmpModels, String tagName) {
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
                boolean hasQueryPage = false;
                if (op.queryParams.size() > 0) {
                    hasQueryPage = true;
                }

                String newOpId = newApiOpIds.get(op.operationId.toString());
                op.vendorExtensions.put("x-nickname", newOpId);
                op.vendorExtensions.put("x-nicknameLowerCase", newOpId.substring(0, 1).toLowerCase() + newOpId.substring(1));

                if (hasQueryPage == false) {
                    continue;
                }
                Map<String, String> newClassNames = getNewClassNamesByTagName(tagName);
                if (newClassNames.containsKey(newOpId)) {
                    op.vendorExtensions.put("x-classname", newClassNames.get(newOpId));
                    op.vendorExtensions.put("x-classnameLowerCase", newClassNames.get(newOpId).toLowerCase());
                } else {
                    if (op.returnBaseType != null) {
                        LOGGER.info("::::::::::::::::::::op.returnBaseType.toString()=" + op);
                        String originalClassname = getIrregularClassName(toModelName(op.returnBaseType.toString()));
                        op.vendorExtensions.put("x-classname", originalClassname);
                        op.vendorExtensions.put("x-classnameLowerCase", originalClassname.toLowerCase());
                    }
                }
            }

            resetModelNames(newApiOpIds, allTmpModels, tagName);
        }
    }

    private Map<String, String> getNewClassNamesByTagName(String tagName) {
        Map<String, String> newClassNames = new HashMap<String, String>();
        newClassNames.put("Get", toModelName(tagName));
        newClassNames.put("List", toModelName(tagName) + "s");
        newClassNames.put("Create", "Create" + toModelName(tagName));

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
                m.vendorExtensions.put("x-nicknameLowerCase", newOpId.substring(0, 1).toLowerCase() + newOpId.substring(1));
            }

            if (model.containsKey("isExtractInfo") && (boolean) model.get("isExtractInfo") == true) {
                if (m.vendorExtensions.containsKey("x-nickname")) {
                    String newOpId = m.vendorExtensions.get("x-nickname").toString();
                    if (newClassNames.containsKey(newOpId)) {
                        m.vendorExtensions.put("x-classname", newClassNames.get(newOpId));
                        m.vendorExtensions.put("x-classnameLowerCase", newClassNames.get(newOpId).toLowerCase());
                    } else {
                        String originalClassname = getIrregularClassName(m.classname.toString());
                        m.vendorExtensions.put("x-classname", originalClassname);
                        m.vendorExtensions.put("x-classnameLowerCase", originalClassname.toLowerCase());
                    }
                }
            }
        }
    }

    private String getIrregularClassName(String originalClassName) {
        List<String> methods = Arrays.asList("Create", "Delete", "Get", "List", "Update", "Resp");
        String newClassName = originalClassName;

        for (int i = 0; i < methods.size(); i++){
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
    @Override
    public List<Map<String, Object>> writeApiModelToFile(List<File> files, List<Object> allOperations, List<Object> allModels, Swagger swagger)
    {

        String serviceType = swagger.getInfo().getTitle();
        String version = swagger.getInfo().getVersion();
        List<Tag> swaggerTags = swagger.getTags();
        List<Map<String, Object>> output = new ArrayList<>();
        try {
            version = version.replaceAll("[.]", "_");
            if (!version.startsWith("v")) {
                version = "v" + version;
            }

            serviceType = serviceType.toLowerCase();
            for (String templateName : apiTemplateFiles().keySet()) {
                String suffix = apiTemplateFiles().get(templateName);
                for (Tag tag : swaggerTags) {
                    String tagName = tag.getName().toLowerCase();
                    List<String> allApiVersions = getAllApiVersions(allOperations);
                    for (String apiVersion : allApiVersions) {
                        List<Object> allTmpOperations = getOpTmpDataByTagApiVersion(allOperations, tagName, apiVersion);
                        List<Object> allTmpModels = getModelTmpDataByTagApiVersion(allModels, tagName, apiVersion);

                    if (allTmpOperations.isEmpty()) {
                        continue;
                    }
                    resetOperationId(allTmpOperations, allTmpModels, tagName);

                    addApiTemplateParam(tagName, serviceType, version, apiVersion, allTmpModels, allTmpOperations, output);
//                    addApiTestTemplateParam(tagName, serviceType, version, apiVersion, allTmpModels, allTmpOperations, output);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not generate api file", e);
        }
        return output;
    }

    private List<String> getAllApiVersions(List<Object> allOperations){
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
                String opVersion = getVersionByOp(op);
                if (!opVersion.isEmpty() && (!allApiVersions.contains(opVersion))) {
                    allApiVersions.add(opVersion);
                }
            }
        }
        return  allApiVersions;
    }

    private String getVersionByPath(String path) {
        path = path.toLowerCase();
        String pattern = "v?[0-9]+\\.?[0-9]*";
        for (String p : path.split("/")) {
            if (isMatchVersionPattern(p) == true) {
                return p;
            }
        }
        return "";
    }

    private boolean isMatchVersionPattern(String version) {
        if (version == "") {
            return false;
        }

        version = version.toLowerCase();
        String pattern = "v?[0-9]+\\.?[0-9]*";
        boolean isMatch = Pattern.matches(pattern, version);

        return isMatch;
    }

    private String getVersionByOp(CodegenOperation op) {
        String apiVersion = "";
        if ((boolean) op.isDeprecated == true) {
            return apiVersion;
        }

        if (op.vendorExtensions.containsKey("x-version")) {
            apiVersion = op.vendorExtensions.get("x-version").toString();
        } else {
            apiVersion = getVersionByPath(op.path.toString());
        }

        apiVersion = getStandardVersion(apiVersion);
        return apiVersion;
    }

    private String getStandardVersion(String version) {
        String apiVersion = "";
        if (isMatchVersionPattern(version) == true) {
            apiVersion = version;
            String[] v = version.split(".");
            if (v.length > 1) {
                String decimal = v[1];
                if (decimal.equals("0")) {
                    apiVersion = v[0];
                }
            }
        }
        return apiVersion.toLowerCase();
    }

    private void reduceDupName(String newApiFunName, CodegenOperation op, Map<String, Object> apiIds) {
        CodegenOperation preDupOp = (CodegenOperation) apiIds.get(newApiFunName);
        CodegenOperation originMethodNameOp = preDupOp;
        CodegenOperation resetOp = op;

//        LOGGER.info("############ zhongjun reduceDupNamereduceDupName ############");

        boolean changeOp = false;
        if (newApiFunName == "Get") {
//            List<Tag> tags = op.tags
            String tagName = op.baseName.toString().toLowerCase();
            String preOpId = preDupOp.operationId.toString().toLowerCase();
            String opId = op.operationId.toString().toLowerCase();

//            LOGGER.info("############ zhongjun reduceDupNamereduceDupName ############tagName=" + tagName + ", preOpId=" + preOpId + ", opId=" + opId);

            if ((!preOpId.contains(tagName)) && opId.contains(tagName)) {
                changeOp = true;
            } else if (preDupOp.queryParams.size() > op.queryParams.size()) {
                changeOp = true;
            }
        } else if (newApiFunName == "Create" || newApiFunName == "Update") {
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

    private String getApiFunNameByOpIdMethod(String opId) {
        String newApiFunName = "";
        Map<String, String> apiOpIdContansMethods = new HashMap<String, String>();
        apiOpIdContansMethods.put("create", "Create");
        apiOpIdContansMethods.put("update", "Update");
        apiOpIdContansMethods.put("list", "List");
        apiOpIdContansMethods.put("get", "Get");
        apiOpIdContansMethods.put("batch", "List");
        apiOpIdContansMethods.put("modify", "Update");

        for (String method : apiOpIdContansMethods.keySet()) {
            if (opId.contains(method)) {
                newApiFunName = apiOpIdContansMethods.get(method);
                break;
            }
        }

        return newApiFunName;
    }

    private String getApiFunNameByOpId(CodegenOperation op, Map<String, Object> apiIds) {
//        LOGGER.info("############ zhongjun getApiFunNameByOpId enter enter ############");
//        String newApiFunName = "";
        String tmpOpId = op.operationId.toString();
        String baseName = op.baseName.toString();

//        LOGGER.info("############ zhongjun getApiFunNameByOpId ############basename=" + baseName + ", tmpOpId=" + tmpOpId);
        String newApiFunName = tmpOpId.replace(baseName, "");
        if (newApiFunName.length() < 2) {
            newApiFunName = tmpOpId;
        }

        if (apiIds.containsKey(newApiFunName)) {
            newApiFunName = tmpOpId;
        }

//        LOGGER.info("############ zhongjun getApiFunNameByOpId ############newApiFunName=" + newApiFunName + ", tmpOpId=" + tmpOpId);
        return newApiFunName;
    }

    private String getApiFunNameByMethod(CodegenOperation op) {
        String newApiFunName = "";
        Map<String, String> apiMethods = new HashMap<String, String>();
        apiMethods.put("x-is-delete-method", "Delete");
        apiMethods.put("x-is-get-method", "Get");

        for (String mt : apiMethods.keySet()) {
            if (op.vendorExtensions.containsKey(mt)) {
                newApiFunName = apiMethods.get(mt);
                break;
            }
        }

        return newApiFunName;
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

            if (apiIds.containsKey(newApiFunName)){
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

    private List<Object> getModelTmpDataByTagApiVersion(List<Object> allModels, String tagName, String apiVersion){
        List<Object> allTmpModels = new ArrayList<Object>();
        for (int i = 0; i < allModels.size(); i++) {
            Map<String, Object> model = (Map<String, Object>) allModels.get(i);

            if (model.containsKey("isDeprecated") && (boolean) model.get("isDeprecated") == true) {
                continue;
            }

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
                    Map<String, Object> tmpModel = new HashMap<String, Object>();
                    copyModel(model, tmpModel);

                    if (tagInfo.containsKey("isReq") && (boolean) tagInfo.get("isReq")) {
                        tmpModel.put("isReq", tagInfo.get("isReq"));
                    } else{
                        tmpModel.put("isReq", false);
                    }

                    if (tagInfo.containsKey("isResp") && (boolean) tagInfo.get("isResp")) {
                        tmpModel.put("isResp", tagInfo.get("isResp"));
                    } else{
                        tmpModel.put("isResp", false);
                    }

                    allTmpModels.add(tmpModel);
                    break;
                }
            }
        }
        return allTmpModels;
    }

    private void copyModel(Map<String, Object> oldModel, Map<String, Object> newModel) {
        for (String key : oldModel.keySet()) {
            newModel.put(key, oldModel.get(key));
        }
        return;
    }

    private List<Object> getOpTmpDataByTagApiVersion(List<Object> allOperations, String tagName, String apiVersion){
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

                if ((boolean) op.isDeprecated == true) {
                    continue;
                }

                String opVersion = getVersionByOp(op);
                if (apiVersion.equals(opVersion)) {
                    tmpOperation.add(op);
                }
                if (op.vendorExtensions.containsKey("x-isPage")) {
                    hasQueryParams = true;
                }
            }
            tmpOperations.put("operation", tmpOperation);
            tmpItem.put("operations", tmpOperations);
            if (hasQueryParams == true){
                tmpItem.put("hasQueryPagin", true);
            }

            opTagName = item.get("classVarName").toString();

            if (tagName.equals(opTagName) && !tmpOperation.isEmpty()) {
                allTmpOperations.add(tmpItem);
            }
        }

        return  allTmpOperations;
    }
}
