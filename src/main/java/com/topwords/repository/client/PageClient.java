package com.topwords.repository.client;

import org.apache.http.HttpEntity;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.stereotype.Repository;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;

@Repository
@ParametersAreNonnullByDefault
public class PageClient {

    private final CloseableHttpClient httpClient;

    public PageClient() {
        this.httpClient = HttpClients.custom()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setCookieSpec(CookieSpecs.STANDARD).build())
                .build();
    }

    public HttpEntity getPage(String url) throws IOException {
        return httpClient.execute(new HttpGet(url)).getEntity();
    }
}
