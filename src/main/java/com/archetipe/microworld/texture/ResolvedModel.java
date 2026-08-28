package com.archetipe.microworld.texture;

public record ResolvedModel(
        ResolvedFace top,
        ResolvedFace bottom,
        ResolvedFace north,
        ResolvedFace south,
        ResolvedFace east,
        ResolvedFace west
) {
}