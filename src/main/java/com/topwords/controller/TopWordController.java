package com.topwords.controller;

import com.topwords.dto.TopWordsResponse;
import com.topwords.service.TopWordService;
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
public class TopWordController {

    private final TopWordService topWordService;

    public TopWordController(TopWordService topWordService) {
        this.topWordService = topWordService;
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
        long t1 = System.nanoTime();
        if (urlString.endsWith("/")) {
            urlString = urlString.substring(0, urlString.length() - 1);
        }
        Map<String, Long> result = topWordService.getTopWords(urlString, depth);
        long t2 = System.nanoTime();
        System.out.println("time " + (t2 - t1));
        return new TopWordsResponse(result);
    }
}
