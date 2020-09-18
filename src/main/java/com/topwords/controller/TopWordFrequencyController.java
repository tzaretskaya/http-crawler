package com.topwords.controller;

import com.topwords.dto.TopWordsResponse;
import com.topwords.service.TopWordFrequencyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.validation.ValidationException;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

@RestController
@ParametersAreNonnullByDefault
public class TopWordFrequencyController {

    private final TopWordFrequencyService topWordFrequencyService;

    public TopWordFrequencyController(TopWordFrequencyService topWordFrequencyService) {
        this.topWordFrequencyService = topWordFrequencyService;
    }

    @GetMapping("/top-words")
    public TopWordsResponse getTopWords(
            @NotBlank(message = "urlString must be not blank")
            @RequestParam("urlString")
                    String urlString,
            @NotNull(message = "depth must be not null")
            @Positive(message = "depth must be positive")
            @RequestParam("depth")
                    Integer depth
    ) {
        try {
            URL url = new URL(urlString);
        } catch (MalformedURLException e) {
            throw new ValidationException("urlString must be url");
        }
        if (urlString.endsWith("/")) {
            urlString = urlString.substring(0, urlString.length() - 1);
        }
        long t2 = System.nanoTime();
        Map<String, Long> result2 = topWordFrequencyService.getTopWordsFrequency(urlString, depth);
        long t3 = System.nanoTime();
        System.out.println("time2 " + (t3 - t2));
        return new TopWordsResponse(result2);
    }
}
