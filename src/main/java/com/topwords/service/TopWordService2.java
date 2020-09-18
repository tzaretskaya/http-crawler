package com.topwords.service;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.LongAdder;

@Service
@ParametersAreNonnullByDefault
public class TopWordService2 {
    private static final Logger LOGGER = LoggerFactory.getLogger(TopWordService2.class);

    public static final String WORDS_REGEX = "[^A-Za-zА-Яа-яÃƒâ€¦Ãƒâ€žÃƒâ€“a-zÃƒÂ¥ÃƒÂ¤ÃƒÂ¶]+";

    private final int topCount;
    private final CrawlerService crawlerService;
    private final TextParserService textParserService;
    private final NotifierService notifierService;
    private final ExecutorService executorService;

    public TopWordService2(
            @Value("${top-word-service.top-count:15}") int topCount,
            @Value("${top-word-service.thread-count:4}") int threadCount,
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

    public Map<String, Long> getTopWords(String urlString, int depth) {
        Object root = new Object();
        prepareSearch(root, urlString, depth);
        crawlerService.startCrawl(root, urlString);
        notifierService.waitCrawlFinish(root);
        Map<String, LongAdder> wordFrequencyMap = crawlerService.getCrawlResult(root);
        Map<String, Long> result = getTopWords(wordFrequencyMap);
        executorService.execute(() -> cleanForRoot(root));
        return result;
    }

    private void prepareSearch(Object root, String urlSource, int depth) {
        textParserService.prepareForRoot(root);
        notifierService.prepareForRoot(root);
        crawlerService.prepareForRoot(root, urlSource, depth);
    }

    private Map<String, Long> getTopWords(Map<String, LongAdder> wordFrequencyMap) {
        SortedMap<Long, List<String>> sortedMap = new TreeMap<>(Collections.reverseOrder());
        for (Map.Entry<String, LongAdder> entry : wordFrequencyMap.entrySet()) {
            sortedMap.computeIfAbsent(entry.getValue().longValue(), x -> new ArrayList<>()).add(entry.getKey());
        }
        int count = 0;
        Map<String, Long> result = new LinkedHashMap<>(topCount);
        for (Map.Entry<Long, List<String>> entry : sortedMap.entrySet()) {
            List<String> value = entry.getValue();
            value.sort(Comparator.naturalOrder()); //todo not necessary
            System.out.println(entry.getKey() + "-" + value);
            for (String str : value) {
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

    private void cleanForRoot(Object root) {
        crawlerService.cleanForRoot(root);
        notifierService.cleanForRoot(root);
        textParserService.cleanForRoot(root);
    }
}
