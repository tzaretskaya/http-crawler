package com.httpcrawler.dto;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collections;
import java.util.Map;

@ParametersAreNonnullByDefault
public class TopWordsResponse {

    public final Map<String, Long> topWordsFrequency;

    public TopWordsResponse(Map<String, Long> topWordsFrequency) {
        this.topWordsFrequency = Collections.unmodifiableMap(topWordsFrequency);
    }
}
