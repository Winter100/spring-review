package com.project.review.review.service;

import com.project.review.review.entities.Review;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 대표·서브 이미지와 본문이 참조하는 내부 스토리지 키를 같은 기준으로 수집합니다. */
@Component
public class ReviewImageKeys {
    private final String publicPrefix;

    public ReviewImageKeys(@Value("${storage.public-base-url}") String publicBaseUrl) {
        this.publicPrefix = publicBaseUrl.replaceAll("/+$", "") + "/";
    }

    public Set<String> collect(Review review) {
        Set<String> keys = fromContent(review.getContent());
        review.getImages().forEach(image -> keys.add(image.getImageKey()));
        return keys;
    }

    public Set<String> collect(String main, List<String> sub, JsonNode content) {
        Set<String> keys = fromContent(content);
        if (main != null) keys.add(main);
        if (sub != null) keys.addAll(sub);
        return keys;
    }

    public Set<String> fromContent(JsonNode content) {
        Set<String> keys = new HashSet<>();
        collectContent(content, keys);
        return keys;
    }

    private void collectContent(JsonNode node, Set<String> keys) {
        if (node == null || !node.isObject()) return;
        if ("image".equals(node.path("type").stringValue(""))) {
            String source = node.path("attrs").path("src").stringValue("");
            String key = source.startsWith(publicPrefix) ? source.substring(publicPrefix.length()) : source;
            if (key.startsWith("reviews/") && key.length() > "reviews/".length() && key.length() <= 1024) {
                keys.add(key);
            }
        }
        JsonNode children = node.path("content");
        if (children.isArray()) children.forEach(child -> collectContent(child, keys));
    }
}
