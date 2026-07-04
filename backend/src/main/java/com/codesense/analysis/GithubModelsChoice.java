package com.codesense.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record GithubModelsChoice(GithubModelsMessage message) {
}
