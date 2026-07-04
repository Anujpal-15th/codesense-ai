package com.codesense.analysis;

import java.util.List;

record OllamaChatRequest(String model, List<OllamaMessage> messages, boolean stream, String format) {
}
