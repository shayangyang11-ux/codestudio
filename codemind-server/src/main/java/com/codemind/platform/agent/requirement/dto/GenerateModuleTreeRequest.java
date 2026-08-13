package com.codemind.platform.agent.requirement.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public class GenerateModuleTreeRequest {

        @JsonProperty("requirementText")
        @JsonAlias({"requirement_text"})
        private String requirementText;

        @JsonProperty("modelingMode")
        @JsonAlias({"modeling_mode"})
        private String modelingMode;

        public String getRequirementText() {
                return requirementText;
        }

        public void setRequirementText(String requirementText) {
                this.requirementText = requirementText;
        }

        public String getModelingMode() {
                return modelingMode;
        }

        public void setModelingMode(String modelingMode) {
                this.modelingMode = modelingMode;
        }
}