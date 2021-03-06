package com.httpcrawler.service;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.httpcrawler.data.Root;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.LongAdder;

@Service
@ParametersAreNonnullByDefault
public class TextParserService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TextParserService.class);

    public static final String WORDS_REGEX = "[^A-Za-zА-Яа-яÃƒâ€¦Ãƒâ€žÃƒâ€“a-zÃƒÂ¥ÃƒÂ¤ÃƒÂ¶]+";

    private final ConcurrentMap<Root, ConcurrentMap<String, LongAdder>> wordFrequencies;
    private final NotifierService notifierService;
    private final ExecutorService executorService;

    public TextParserService(
            @Value("${text-parser-service.thread-count}") int threadCount,
            NotifierService notifierService
    ) {
        this.wordFrequencies = new ConcurrentHashMap<>();
        this.notifierService = notifierService;
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

    public void prepareForRoot(Root root) {
        wordFrequencies.putIfAbsent(root, new ConcurrentHashMap<>());
    }

    public void parseText(Root root, Document doc, String url) {
        executorService.execute(() -> doParseText(root, doc, url));
    }

    public void cleanForRoot(Root obj) {
        wordFrequencies.remove(obj);
    }

    public Map<String, LongAdder> getResult(Root root) {
        return new HashMap<>(wordFrequencies.get(root));
    }

    @PreDestroy
    protected void stop() {
        executorService.shutdownNow();
    }

    private void doParseText(Root root, Document doc, String url) {
        String text = doc.body().text();
        try (
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)))
                )
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] words = line.split(WORDS_REGEX);
                for (String word : words) {
                    if ("".equals(word)) {
                        continue;
                    }
                    wordFrequencies.get(root).computeIfAbsent(word, x -> new LongAdder()).increment();
                }
            }
        } catch (IOException e) {
            LOGGER.debug("Text parsing exception for [{}]", url, e);
        } finally {
            notifierService.decrementPending(root);
        }
    }
}
