package com.codesense.analysis;

import java.util.List;

record GithubModelsChatRequest(String model, List<GithubModelsMessage> messages) {
}
