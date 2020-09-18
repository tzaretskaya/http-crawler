package com.httpcrawler.service;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.httpcrawler.data.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;

@Service
@ParametersAreNonnullByDefault
public class TopWordFrequencyService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TopWordFrequencyService.class);

    private static final int RANDOM_MAX = 1_000_000;

    private final int topCount;
    private final CrawlerService crawlerService;
    private final TextParserService textParserService;
    private final NotifierService notifierService;
    private final ExecutorService executorService;

    public TopWordFrequencyService(
            @Value("${top-word-service-frequency.top-count:15}") int topCount,
            @Value("${top-word-service-frequency.thread-count:1}") int threadCount,
            CrawlerService crawlerService,
            TextParserService textParserService,
            NotifierService notifierService
    ) {
        this.topCount = topCount;
        this.crawlerService = crawlerService;
        this.textParserService = textParserService;
        this.notifierService = notifierService;
        this.executorService =
                Executors.newFixedThreadPool(
                        threadCount,
                        new ThreadFactoryBuilder()
                                .setNameFormat(getClass().getSimpleName() + "-TaskExecutor-%d")
                                .setPriority(Thread.NORM_PRIORITY)
                                .setUncaughtExceptionHandler((t, e) -> LOGGER.error(e.getMessage(), e))
                                .setDaemon(true)
                                .build()
                );
    }

    public Map<String, Long> getTopWordsFrequency(String urlString, int depth) {
        Root root = new Root(urlString, depth, ThreadLocalRandom.current().nextInt(RANDOM_MAX));
        prepareSearch(root, urlString, depth);
        crawlerService.startCrawl(root, urlString);
        notifierService.waitCrawlFinish(root);
        Map<String, LongAdder> wordFrequencyMap = crawlerService.getCrawlResult(root);
        Map<String, Long> result = getTopWordsFrequency(wordFrequencyMap);
        executorService.execute(() -> cleanForRoot(root));
        return result;
    }

    @PreDestroy
    protected void stop() {
        executorService.shutdownNow();
    }

    private void prepareSearch(Root root, String urlSource, int depth) {
        textParserService.prepareForRoot(root);
        notifierService.prepareForRoot(root);
        crawlerService.prepareForRoot(root, urlSource, depth);
    }

    private Map<String, Long> getTopWordsFrequency(Map<String, LongAdder> wordFrequencyMap) {
        SortedMap<Long, List<String>> sortedMap = new TreeMap<>(Collections.reverseOrder());
        for (Map.Entry<String, LongAdder> entry : wordFrequencyMap.entrySet()) {
            sortedMap.computeIfAbsent(entry.getValue().longValue(), x -> new ArrayList<>()).add(entry.getKey());
        }
        int count = 0;
        Map<String, Long> result = new LinkedHashMap<>(topCount);
        for (Map.Entry<Long, List<String>> entry : sortedMap.entrySet()) {
            for (String str : entry.getValue()) {
                result.put(str, entry.getKey());
                count++;
                if (count >= topCount) {
                    break;
                }
            }
            if (count >= topCount) {
                break;
            }
        }
        return result;
    }

    private void cleanForRoot(Root root) {
        crawlerService.cleanForRoot(root);
        notifierService.cleanForRoot(root);
        textParserService.cleanForRoot(root);
    }
}
