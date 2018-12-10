package io.swagger.codegen.languages;

import io.swagger.codegen.*;
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
        apiTemplateFiles.put("request.mustache", ".go");
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
                "const", "fallthrough", "if", "range", "type",
                "continue", "for", "import", "return", "var", "error", "ApiResponse", "nil")
                // Added "error" as it's used so frequently that it may as well be a keyword
        );

        cliOptions.add(new CliOption(CodegenConstants.PACKAGE_VERSION, "Go package version.")
                .defaultValue("1.0.0"));
        cliOptions.add(CliOption.newBoolean(WITH_XML, "whether to include support for application/xml content type and include XML annotations in the model (works with libraries that provide support for JSON and XML)"));

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

    public void setPackageVersion(String packageVersion) {
        this.packageVersion = packageVersion;
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

                        Map<String, Object> templateParam = new HashMap<String, Object>();
                        templateParam.put("classVarName", tagName);
                        templateParam.put("allmodels", allTmpModels);
                        templateParam.put("alloperations", allTmpOperations);

                        String preName = templateName.split("\\.")[0];
                        // eg: /tmp/1_0_0/compute/v1/instance/request.go
                        String filename = (apiFileFolder() + version + File.separator + serviceType + File.separator
                                + apiVersion + File.separator + tag.getName() + File.separator + preName + suffix);
                        if (!super.shouldOverwrite(filename) && new File(filename).exists()) {
                            LOGGER.info("Skipped overwriting " + filename);
                            continue;
                        }
                        templateParam.put("templateName", templateName);
                        templateParam.put("filename", filename);
                        output.add(templateParam);
                    }
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
                String opVersion = getVersionByPath(op.path.toString());
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
            boolean isMatch = Pattern.matches(pattern, p);
            if (isMatch == true) {
                return p;
            }
        }
        return "";
    }

    private List<Object> getModelTmpDataByTagApiVersion(List<Object> allModels, String tagName, String apiVersion){
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
                    } else{
                        model.put("isReq", false);
                    }

                    if (tagInfo.containsKey("isResp") && (boolean) tagInfo.get("isResp")) {
                        model.put("isResp", tagInfo.get("isResp"));
                    } else{
                        model.put("isResp", false);
                    }

                    allTmpModels.add(model);
                    break;
                }
            }
        }
        return allTmpModels;
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

                String opVersion = getVersionByPath(op.path.toString());
                if (apiVersion.equals(opVersion)) {
                    tmpOperation.add(op);
                }
                if (op.queryParams.size() > 0) {
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
