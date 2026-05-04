package com.pipeline.xslttransformer.controller;

import com.pipeline.xslttransformer.cache.XsltCacheManager;
import com.pipeline.xslttransformer.route.DynamicRouteManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/cache")
@RequiredArgsConstructor
@Tag(name = "XSLT Cache Management",
        description = "Manage XSLT cache — evict, reload, inspect")
public class CacheController {

    private final XsltCacheManager xsltCacheManager;
    private final DynamicRouteManager dynamicRouteManager;

    @Operation(summary = "Evict specific source from cache")
    @DeleteMapping("/evict/{source}")
    public ResponseEntity<Map<String, String>> evict(
            @Parameter(description = "e.g. SourceA")
            @PathVariable String source) {
        if (!xsltCacheManager.isCached(source)) {
            return ResponseEntity.status(404).body(Map.of(
                    "status", "NOT_FOUND",
                    "message", source + " is not in cache"));
        }
        xsltCacheManager.evict(source);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", source + " evicted from cache"));
    }

    @Operation(summary = "Evict ALL sources from cache")
    @DeleteMapping("/evict/all")
    public ResponseEntity<Map<String, String>> evictAll() {
        xsltCacheManager.evictAll();
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Entire cache cleared"));
    }

    @Operation(summary = "Get all cached sources")
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getCacheStatus() {
        Set<String> cached = xsltCacheManager.getCachedSources();
        Set<String> routes = dynamicRouteManager.getActiveRoutes();
        return ResponseEntity.ok(Map.of(
                "cachedSources", cached,
                "totalCached", cached.size(),
                "activeRoutes", routes,
                "totalRoutes", routes.size()
        ));
    }

    @Operation(summary = "Check if specific source is cached")
    @GetMapping("/status/{source}")
    public ResponseEntity<Map<String, Object>> isSourceCached(
            @Parameter(description = "e.g. SourceA")
            @PathVariable String source) {
        boolean cached = xsltCacheManager.isCached(source);
        return ResponseEntity.ok(Map.of(
                "source", source,
                "cached", cached,
                "message", cached
                        ? source + " is in cache"
                        : source + " is NOT in cache"));
    }
}