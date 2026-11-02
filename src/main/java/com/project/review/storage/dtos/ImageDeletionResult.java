package com.project.review.storage.dtos;

import java.util.Map;
import java.util.Set;

public record ImageDeletionResult(Set<String> succeeded, Map<String, String> failed) {
    public ImageDeletionResult {
        succeeded = Set.copyOf(succeeded);
        failed = Map.copyOf(failed);
    }
}
