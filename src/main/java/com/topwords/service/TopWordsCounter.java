package com.topwords.service;

import com.topwords.repository.client.PageClient;
import org.jsoup.nodes.Document;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class TopWordsCounter {

    private final HttpEntityDownloader httpEntityDownloader;
    private final WordCounter wordCounter = new WordCounter();
    private final LinkSearcher linkSearcher = new LinkSearcher();

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    public final ConcurrentMap<String, Long> result = new ConcurrentHashMap<>();

    public TopWordsCounter(PageClient pageClient) {
        httpEntityDownloader = new HttpEntityDownloader(pageClient);
    }

    public Map<String, Long> count(String url) throws InterruptedException {
        Thread thread = new Thread(() -> countTopWords(url, 2));
        thread.start();
        thread.join();
        return result; // filter top 100
    }

    public void countTopWords(String url, int depth) {
        executorService.submit(() -> {
            if (depth == 0) {
                return;
            }


            Document doc = httpEntityDownloader.getUrlContent(url);
            Map<String, Long> pageResult = wordCounter.countWordsOnPage(doc);
            pageResult.forEach((k, v) ->
                result.compute(k, (key, oldValue) -> (oldValue == null ? v : oldValue + v))
            );
            int newDepth = depth - 1;
            linkSearcher.findAllLinks(doc).forEach(link -> executorService.submit(() -> countTopWords(link, newDepth)));
        });
    }
}
