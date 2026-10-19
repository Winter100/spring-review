package com.project.review.storage.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ImageContentType {
    public static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp");
}
