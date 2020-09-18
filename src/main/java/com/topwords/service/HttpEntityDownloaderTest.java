package com.topwords.service;

import com.topwords.repository.client.PageClient;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

//import static org.junit.jupiter.api.Assertions.*;

class HttpEntityDownloaderTest {

    /*private PageClient pageClient = mock(PageClient.class); // Mockito
    private HttpEntityDownloader httpEntityDownloader = new HttpEntityDownloader(pageClient);

    // test 1: should return url content
    public void should_return_url_content__correct() {
        // given
        given(pageClient.getPage("123.ru")).thenReturn(new ByteArrayEntity("<a>link</a><span>text</span>".getBytes()));

        // when
        Document content = httpEntityDownloader.getUrlContent("123.ru");

        // then
        assertThat(content).equalsTo(Jsoup.parse("<a>link</a><span>text</span>")); // Mockito or Hamster
        verify(pageClient).getPage("123.ru");
    }
*/
    // test 2: should throw exception on null url

    // test 3: should throw exception when request failed
}