/*
 * Copyright (c) 2022 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.ballerina.openapi.validator;

import io.ballerina.compiler.syntax.tree.DefaultableParameterNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.ParameterNode;
import io.ballerina.compiler.syntax.tree.RequiredParameterNode;
import io.ballerina.compiler.syntax.tree.ResourcePathParameterNode;
import io.ballerina.openapi.validator.error.CompilationError;
import io.ballerina.openapi.validator.model.MetaData;
import io.ballerina.projects.plugins.SyntaxNodeAnalysisContext;
import io.ballerina.tools.diagnostics.DiagnosticSeverity;
import io.ballerina.tools.diagnostics.Location;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.ballerina.openapi.validator.Constants.ARRAY;
import static io.ballerina.openapi.validator.Constants.SQUARE_BRACKETS;
import static io.ballerina.openapi.validator.TypeValidatorUtils.convertBallerinaType;
import static io.ballerina.openapi.validator.TypeValidatorUtils.convertOpenAPITypeToBallerina;
import static io.ballerina.openapi.validator.ValidatorUtils.extractReferenceType;
import static io.ballerina.openapi.validator.ValidatorUtils.getNormalizedPath;
import static io.ballerina.openapi.validator.ValidatorUtils.getNumberFormatType;
import static io.ballerina.openapi.validator.ValidatorUtils.reportDiagnostic;
import static io.ballerina.openapi.validator.ValidatorUtils.unescapeIdentifier;

/**
 * This contains the all parameter validation.
 *
 * @since 1.1.0
 */
public class ParameterValidator implements Validator {
    private final String path;
    private final String method;
    private final SyntaxNodeAnalysisContext context;
    private final OpenAPI openAPI;
    private final DiagnosticSeverity severity;
    private final Map<String, Node> parameters;
    private final List<Parameter> oasParameters;
    // This default location is map to relevant resource function
    private final Location location;


    public ParameterValidator(MetaData metaData, Map<String, Node> parameters, List<Parameter> oasParameters) {
        this.openAPI = metaData.getOpenAPI();
        this.context = metaData.getContext();
        this.method = metaData.getMethod();
        this.path = metaData.getPath();
        this.severity = metaData.getSeverity();
        this.location = metaData.getLocation();
        this.parameters = parameters;
        this.oasParameters = oasParameters;
    }

    @Override
    public void validate() {
        //Ballerina to OAS parameter validation , here we validate query, path parameters.
        validateBallerinaParameters();

        //OAS->Ballerina parameter validate
        if (oasParameters != null) {
            validateOASParameters();
        }
    }

    /**
     * Validate OAS parameter against ballerina parameters.
     */
    private void validateOASParameters() {

        oasParameters.forEach(parameter -> {
            if (parameter.get$ref() != null) {
                Optional<String> parameterName = extractReferenceType(parameter.get$ref());
                if (parameterName.isEmpty()) {
                    return;
                }
                parameter = openAPI.getComponents().getParameters().get(parameterName.get());
            }
            if (!(parameter instanceof HeaderParameter) ||
                    parameter.getIn() != null && !parameter.getIn().equals("header")) {
                // headerValidation
                AtomicBoolean isImplemented = new AtomicBoolean(false);
                Parameter finalParameter1 = parameter;
                parameters.forEach((paramName, paramNode) -> {
                    // avoid headers
                    if (finalParameter1.getName().equals(paramName)) {
                        isImplemented.set(true);
                    }
                });
                if (!isImplemented.get()) {
                    // error message
                    reportDiagnostic(context, CompilationError.MISSING_PARAMETER,
                            location, severity, parameter.getName(), method, path);
                }
            }
        });
    }

    /**
     * This function is used to validate the ballerina resource parameter against to openapi parameters.
     *
     */
    private void validateBallerinaParameters() {

        for (Map.Entry<String, Node> parameter : parameters.entrySet()) {
            boolean isExist = false;
            String parameterName = unescapeIdentifier(parameter.getKey());
            String ballerinaType;
            //Todo: Nullable and default value assign scenarios
            if (parameter.getValue() instanceof ParameterNode) {
                ParameterNode paramNode = (ParameterNode) parameter.getValue();
                if (paramNode instanceof RequiredParameterNode) {
                    RequiredParameterNode requireParam = (RequiredParameterNode) paramNode;
                    ballerinaType = requireParam.typeName().toString().trim().replaceAll("\\?", "");
                } else {
                    DefaultableParameterNode defaultParam = (DefaultableParameterNode) paramNode;
                    ballerinaType = defaultParam.typeName().toString().trim().replaceAll("\\?", "");
                }
            } else {
                ResourcePathParameterNode pathParameterNode = (ResourcePathParameterNode) parameter.getValue();
                ballerinaType = convertBallerinaType(pathParameterNode.typeDescriptor().kind()).orElse(null);
            }
            if (oasParameters != null) {
                for (Parameter oasParameter : oasParameters) {
                    String oasParameterName = oasParameter.getName();
                    Schema<?> schema = oasParameter.getSchema();
                    if (oasParameter.get$ref() != null) {
                        Optional<String> name = extractReferenceType(oasParameter.get$ref());
                        if (name.isEmpty()) {
                            return;
                        }
                        oasParameterName = name.get();
                        schema = openAPI.getComponents().getParameters().get(oasParameterName).getSchema();
                        if (openAPI.getComponents().getParameters().get(oasParameterName).getName() != null) {
                            oasParameterName = openAPI.getComponents().getParameters().get(oasParameterName).getName();
                        }
                    }
                    //There are situation path parameter name change with its schema name, therefore we need to avoid
                    // name checking in path parameter
                    //ex:
                    // paths:
                    //  /applications/{obsId}/metrics:
                    //    get:
                    //      operationId: getMetrics
                    //      parameters:
                    //        - $ref: "#/components/parameters/obsIdPathParam"
                    if (!parameterName.equals(oasParameterName)) {
                        continue;
                    }
                    isExist = true;
                    String oasType = getNumberFormatType(schema);
                    if (oasType.equals(ARRAY) && schema instanceof ArraySchema) {
                        ArraySchema arraySchema = (ArraySchema) schema;
                        oasType = arraySchema.getItems().getType();
                    }
                    Optional<String> type = convertOpenAPITypeToBallerina(oasType);

                    //TODO: map<json> type matching
                    //TODO: Handle optional
                    //Array mapping
                    if (type.isEmpty() || Objects.requireNonNull(ballerinaType).contains(SQUARE_BRACKETS) &&
                            !ballerinaType.equals(type.get() + SQUARE_BRACKETS)) {
                        // This special concatenation is used to check the array query parameters
                        reportDiagnostic(context, CompilationError.TYPE_MISMATCH_PARAMETER,
                                parameter.getValue().location(), severity,  oasType +
                                        SQUARE_BRACKETS, ballerinaType,
                                parameterName, method, path);
                        break;
                    }
                    if (!Objects.equals(ballerinaType, type.get())) {
                        // This special concatenation is used to check the array query parameters
                        reportDiagnostic(context, CompilationError.TYPE_MISMATCH_PARAMETER,
                                parameter.getValue().location(), severity, oasType, ballerinaType,
                                parameterName, method, path);
                        break;
                    }
                }
            }
            if (!isExist) {
                // undocumented parameter
                reportDiagnostic(context, CompilationError.UNDEFINED_PARAMETER, parameter.getValue().location(),
                        severity, parameterName, method,
                        getNormalizedPath(path));
            }
        }
    }
}
