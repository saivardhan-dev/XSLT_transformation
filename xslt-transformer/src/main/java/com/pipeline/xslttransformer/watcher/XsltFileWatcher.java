package com.pipeline.xslttransformer.watcher;

import com.pipeline.xslttransformer.cache.XsltCacheManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class XsltFileWatcher {

    private final XsltCacheManager xsltCacheManager;

    @Value("${app.xslt.external-path}")
    private String externalXsltPath;

    private WatchService watchService;
    private Thread watcherThread;

    @PostConstruct
    public void startWatcher() throws Exception {
        watchService = FileSystems.getDefault().newWatchService();
        Path xsltDir = Path.of(externalXsltPath);
        xsltDir.register(watchService,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE);

        log.info("👁 XSLT File Watcher started on: {}", externalXsltPath);

        watcherThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    WatchKey key = watchService.take();
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        String fileName = event.context().toString();

                        if (fileName.endsWith(".xslt")) {
                            String source = fileName.replace(".xslt", "");

                            if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                                log.info("📝 XSLT modified: {} — Evicting cache", fileName);
                                xsltCacheManager.evict(source);
                            } else if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                                log.info("➕ New XSLT added: {}", fileName);
                            } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                                log.warn("🗑 XSLT deleted: {} — Evicting cache", fileName);
                                xsltCacheManager.evict(source);
                            }
                        }
                    }
                    key.reset();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        watcherThread.setDaemon(true);
        watcherThread.setName("xslt-file-watcher");
        watcherThread.start();
    }

    @PreDestroy
    public void stopWatcher() throws Exception {
        if (watcherThread != null) watcherThread.interrupt();
        if (watchService != null) watchService.close();
        log.info("File watcher stopped");
    }
}