package com.topwords.service;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.topwords.repository.client.PageClient;
import com.topwords.utils.AbstractLifecycle;
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
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.LongAdder;

@Service
@ParametersAreNonnullByDefault
public class TopWordService4 extends AbstractLifecycle {
    private static final Logger LOGGER = LoggerFactory.getLogger(TopWordService4.class);

    public static final String WORDS_REGEX = "[^A-Za-zА-Яа-яÃƒâ€¦Ãƒâ€žÃƒâ€“a-zÃƒÂ¥ÃƒÂ¤ÃƒÂ¶]+";

    private final int topCount;
    private final ConcurrentMap<Object, ConcurrentMap<String, LongAdder>> wordFrequencies;
    private final ConcurrentMap<Object, LongAdder> pending;
    private final ConcurrentMap<Object, ConcurrentMap<String, Boolean>> seen;
    private final ConcurrentMap<Object, String> baseUrls;
    private final ConcurrentMap<Object, Integer> depths;
    private final PageClient pageClient;
    private final ExecutorService executorService;

    public TopWordService4(
            @Value("${top-word-service.top-count:15}") int topCount,
            @Value("${top-word-service.thread-count:4}") int threadCount,
            PageClient pageClient
    ) {
        this.topCount = topCount;
        this.pageClient = pageClient;
        this.wordFrequencies = new ConcurrentHashMap<>();
        this.pending = new ConcurrentHashMap<>();
        this.seen = new ConcurrentHashMap<>();
        this.baseUrls = new ConcurrentHashMap<>();
        this.depths = new ConcurrentHashMap<>();
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

    @Override
    public void onStart() throws Exception {
    }

    @Override
    public void onStop() throws Exception {
        executorService.shutdownNow();
    }

    public Map<String, Long> getTopWords(String urlString, int depth) {
        Object root = new Object();
        prepareSearch(root, urlString, depth);
        startSearch(root, urlString);
        joinSearch(root);
        Map<String, LongAdder> wordFrequencyMap = wordFrequencies.get(root);

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
        executorService.execute(() -> cleanCollections(root));
        return result;
    }

    private void prepareSearch(Object root, String urlSource, int depth) {
        wordFrequencies.putIfAbsent(root, new ConcurrentHashMap<>());
        pending.putIfAbsent(root, new LongAdder());
        seen.putIfAbsent(root, new ConcurrentHashMap<>());
        baseUrls.putIfAbsent(root, urlSource);
        depths.putIfAbsent(root, depth);
    }

    private void startSearch(Object root, String urlSource) {
        handle(root, urlSource, 1);
    }

    private void joinSearch(Object root) {
        synchronized (root) {
            try {
                root.wait();
            } catch (InterruptedException e) {
                LOGGER.error("join was interrupted. Return not finished map");
            }
        }
    }

    private void handle(Object root, String link, int currentDepth) {
        ConcurrentMap<String, Boolean> objSeen = seen.get(root);
        Boolean wasSeen = objSeen.putIfAbsent(link, true);
        if (wasSeen != null || currentDepth > depths.get(root)) {
            return;
        }
        LOGGER.debug(">> Depth: [{}]  link: [{}]", currentDepth, link);
        pending.get(root).increment();
        executorService.execute(() -> {
            List<String> links = scanAndGetLinks(root, link);
            if (links != null) {
                for (String l : links) {
                    handle(root, l, currentDepth + 1);
                }
            }
        });
    }

    @Nullable
    private List<String> scanAndGetLinks(Object root, String url) {
        try {
            String dataFromUrl = getDataFromUrl(url);
            Document doc = Jsoup.parse(dataFromUrl);
            Elements elements = doc.select("a");
            ArrayList<String> list = new ArrayList<>(elements.size());
            for (Element element : elements) {
                String link = element.attributes().get("href");
                if (link != null && !link.equals("")) {
                    URI uri = new URI(link);
                    if (uri.isAbsolute()) {
                        if (link.startsWith(baseUrls.get(root))) {
                            list.add(link);
                        }
                    } else if (link.startsWith("//")) {
                        if (link.startsWith("//" + baseUrls.get(root))) {
                            list.add(link);
                        }
                    } else if (!link.startsWith("#")) {
                        list.add(baseUrls.get(root) + link);
                    }
                }
            }
            executorService.execute(() -> parseText(root, doc, url));
            return list;
        } catch (Throwable e) {
            LOGGER.debug("Exception for url [{}]: ", url, e);
            decrementPending(root);
        }
        return null;
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
            t.printStackTrace();
            throw new RuntimeException("Could not fetch data from " + url);
        } finally {
            if (rd != null) {
                try {
                    rd.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void parseText(Object root, Document doc, String url) {
        LOGGER.debug("parseText for url: [{}]  ", url);
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
            LOGGER.debug("Text parsing exception for [" + url + "]: " + e.getMessage(), e);
        } finally {
            decrementPending(root);
        }
    }

    private void decrementPending(Object root) {
        pending.get(root).decrement();
        if (pending.get(root).longValue() == 0L) {
            synchronized (root) {
                root.notify();
            }
        }
    }

    private void cleanCollections(Object obj) {
        wordFrequencies.remove(obj);
        pending.remove(obj);
        seen.remove(obj);
        baseUrls.remove(obj);
        depths.remove(obj);
    }
}
