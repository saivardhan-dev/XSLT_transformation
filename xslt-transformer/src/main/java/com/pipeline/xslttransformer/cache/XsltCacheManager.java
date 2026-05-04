package com.pipeline.xslttransformer.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class XsltCacheManager {

    private final ConcurrentHashMap<String, String> xsltCache
            = new ConcurrentHashMap<>();

    @Value("${app.xslt.external-path}")
    private String externalXsltPath;

    public String getXslt(String source) throws Exception {
        if (xsltCache.containsKey(source)) {
            log.info("Cache HIT for source: {}", source);
            return xsltCache.get(source);
        }

        log.info("Cache MISS for source: {} — Loading from disk", source);
        String fileName = source + ".xslt";
        Path path = Path.of(externalXsltPath + fileName);
        log.info("Looking for XSLT at: {}", path.toAbsolutePath());

        if (!Files.exists(path)) {
            throw new RuntimeException(
                    "XSLT file NOT FOUND for source: " + source
                            + " at: " + path.toAbsolutePath());
        }

        String content = Files.readString(path);
        xsltCache.put(source, content);
        log.info("✅ Loaded and cached XSLT for source: {}", source);
        return content;
    }

    public void evict(String source) {
        xsltCache.remove(source);
        log.info("✅ Evicted cache for source: {}", source);
    }

    public void evictAll() {
        xsltCache.clear();
        log.info("✅ Cleared entire XSLT cache");
    }

    public Set<String> getCachedSources() {
        return xsltCache.keySet();
    }

    public boolean isCached(String source) {
        return xsltCache.containsKey(source);
    }
}