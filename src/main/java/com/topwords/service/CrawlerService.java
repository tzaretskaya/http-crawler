package com.topwords.service;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.topwords.repository.client.PageClient;
import org.apache.http.HttpEntity;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

@Service
@ParametersAreNonnullByDefault
public class CrawlerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CrawlerService.class);

    private final ConcurrentMap<Object, ConcurrentMap<String, Boolean>> seen;
    private final ConcurrentMap<Object, String> baseUrls;
    private final ConcurrentMap<Object, Integer> depths;
    private final PageClient pageClient;
    private final NotifierService notifierService;
    private final TextParserService textParserService;

    private final ExecutorService executorService;
    private AtomicInteger atomicInteger;

    public CrawlerService(
            @Value("${crawler-service.thread-count:4}") int threadCount,
            PageClient pageClient,
            NotifierService notifierService,
            TextParserService textParserService
    ) {
        this.seen = new ConcurrentHashMap<>();
        this.baseUrls = new ConcurrentHashMap<>();
        this.depths = new ConcurrentHashMap<>();
        this.pageClient = pageClient;
        this.notifierService = notifierService;
        this.textParserService = textParserService;
        this.executorService = Executors.newFixedThreadPool(
                threadCount,
                new ThreadFactoryBuilder()
                        .setNameFormat(getClass().getSimpleName() + "-TaskExecutor-%d")
                        .setPriority(Thread.NORM_PRIORITY)
                        .setUncaughtExceptionHandler((t, e) -> LOGGER.error(e.getMessage(), e))
                        .setDaemon(true)
                        .build()
        );
    }

    public void prepareForRoot(Object root, String urlSource, int depth) {
        seen.putIfAbsent(root, new ConcurrentHashMap<>());
        depths.putIfAbsent(root, depth);
        baseUrls.putIfAbsent(root, urlSource);
    }

    public Map<String, LongAdder> getCrawlResult(Object root) {
        return textParserService.getResult(root);
    }

    public void startCrawl(Object root, String link) {
        atomicInteger = new AtomicInteger(0);
        crawl(root, link, 1);
    }

    public void cleanForRoot(Object root) {
        System.out.println("CLEANING");
        baseUrls.remove(root);
        depths.remove(root);
        seen.remove(root);
        System.out.println("!!!!!-----!!!!!---- " + atomicInteger.get());
    }

    @PreDestroy
    protected void stop() {
        executorService.shutdownNow();
    }


    private void crawl(Object root, String link, int currentDepth) {
        ConcurrentMap<String, Boolean> objSeen = seen.get(root);
        Boolean wasSeen = objSeen.putIfAbsent(link, true);
        if (wasSeen != null) {
//            System.out.println("------ was seen "+link);
            return;
        } else if (currentDepth > depths.get(root)) {
            return;
        }
        atomicInteger.addAndGet(1);
        LOGGER.debug(">> Depth: [{}]  link: [{}]", currentDepth, link);
        notifierService.increment(root);

        executorService.execute(() -> {
            Document doc;
            try {
                String dataFromUrl = getDataFromUrl(link);
                doc = Jsoup.parse(dataFromUrl);
            } catch (Exception e) {
                LOGGER.error("Exception for url [{}]: ", link, e);
                notifierService.decrementPending(root);
                return;
            }
            extractLinks(root, doc, currentDepth);
            textParserService.parseText(root, doc, link);
        });
    }

    @Nullable
    private void extractLinks(Object root, Document doc, int currentDepth) {
        Elements elements = doc.select("a");
        for (Element element : elements) {
            String link = element.attributes().get("href");
            if (link != null && !link.equals("")) {
                link = prepareLink(link, root);
                if (link != null) {
//                    System.out.println("found "+ link);
                    crawl(root, link, currentDepth + 1);
                }
            }
        }
    }

    private String getDataFromUrl(String url) {
        LOGGER.debug("getData from url: [{}]  ", url);
        BufferedReader rd = null;
        try {
            HttpEntity httpEntity = pageClient.getPage(url);
            rd = new BufferedReader(new InputStreamReader(httpEntity.getContent()));
            String line;
            StringBuilder sb = new StringBuilder();
            while ((line = rd.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Throwable t) {
            throw new RuntimeException("Could not fetch data from " + url);
        } finally {
            if (rd != null) {
                try {
                    rd.close();
                } catch (IOException e) {
                    LOGGER.debug("", e);
                }
            }
        }
    }

    private String prepareLink(String link, Object root) {
        try {
            URI uri = new URI(link);
            String prefix = baseUrls.get(root);
            if (uri.isAbsolute()) {
                if (link.startsWith(prefix)) {
                    return link;
                }
            } else if (link.startsWith("//")) {
                if (link.startsWith("//" + prefix)) {
                    return link;
                }
            } else if (!link.startsWith("#")) {
                return baseUrls.get(root) + link;
            }
        } catch (Exception e) {
            LOGGER.debug("", e);
        }
        return null;
    }
}
