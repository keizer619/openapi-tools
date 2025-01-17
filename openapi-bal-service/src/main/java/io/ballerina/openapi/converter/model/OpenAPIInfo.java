/*
 *  Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package io.ballerina.openapi.converter.model;

import java.util.Optional;

import static io.ballerina.openapi.converter.utils.ConverterCommonUtils.normalizeTitle;

/**
 * This {@code OpenAPIInfo} contains details related to openAPI info section.
 *
 * @since 2.0.0
 */
public class OpenAPIInfo {
    private final String title;
    private final String version;
    private final String contractPath;

    public OpenAPIInfo(OpenAPIInfoBuilder openAPIInfoBuilder) {
        this.title = openAPIInfoBuilder.title;
        this.version = openAPIInfoBuilder.version;
        this.contractPath = openAPIInfoBuilder.contractPath;
    }

    public Optional<String> getTitle() {
        return Optional.ofNullable(normalizeTitle(this.title));
    }

    public Optional<String> getVersion() {
        return Optional.ofNullable(this.version);
    }

    public Optional<String> getContractPath() {
        return Optional.ofNullable(this.contractPath);
    }

    /**
     * This is the builder class for the {@link OpenAPIInfo}.
     */
    public static class OpenAPIInfoBuilder {
        private String title;
        private String version;
        private String contractPath;

        public OpenAPIInfoBuilder title(String title) {
            this.title = title;
            return this;
        }

        public OpenAPIInfoBuilder version(String version) {
            this.version = version;
            return this;
        }

        public OpenAPIInfoBuilder contractPath(String contractPath) {
            this.contractPath = contractPath;
            return this;
        }

        public OpenAPIInfo build() {
            OpenAPIInfo openAPIInfo = new OpenAPIInfo(this);
            return openAPIInfo;
        }
    }
}
