package com.project.review.review;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

final class ReviewContentFixtures {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private ReviewContentFixtures() {
    }

    static JsonNode textDocument(String text) {
        ObjectNode document = MAPPER.createObjectNode().put("type", "doc");
        document.putArray("content").addObject()
                .put("type", "paragraph")
                .putArray("content").addObject()
                .put("type", "text")
                .put("text", text);
        return document;
    }
}
