package com.topwords.service;

import com.topwords.repository.client.PageClient;

import java.util.Map;

public class TopWordService3 {

    private final PageClient pageClient;

    public TopWordService3(PageClient pageClient) {
        this.pageClient = pageClient;
    }

    public Map<String, Long> count(String url) {
        try {
            return new TopWordsCounter(pageClient).count(url);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return Map.of();
    }

}
