package searchengine.controllers;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.model.Site;
import searchengine.repositories.SiteRepository;
import searchengine.services.IndexingPageService;
import lombok.extern.slf4j.Slf4j;
import java.net.URI;
import java.net.URISyntaxException;


@Slf4j
@RestController
@RequestMapping("/api")
@AllArgsConstructor
public class IndexPageController {

    private final IndexingPageService indexingPageService;
    private final SiteRepository siteRepository;

    @PostMapping("/indexPage")
    public ResponseEntity<IndexPageResponse> indexPage(@RequestBody IndexPageRequest request) {
        String url = request.getUrl();
        if (url == null || url.isBlank()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new IndexPageResponse(false, "URL страницы не указан"));
        }

        // Проверка, принадлежит ли URL одному из разрешённых доменов
        Site site = getMatchingSite(url);
        if (site == null) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new IndexPageResponse(false, "URL не соответствует разрешённым доменам"));
        }

        try {
            indexingPageService.indexPage(url, site);
            return ResponseEntity.ok(new IndexPageResponse(true, null));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new IndexPageResponse(false, "Ошибка индексации: " + e.getMessage()));
        }
    }

    private Site getMatchingSite(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null) {
                return null;
            }

            return siteRepository.findAll().stream()
                    .filter(site -> host.endsWith(URI.create(site.getUrl()).getHost()))
                    .findFirst()
                    .orElse(null);
        } catch (URISyntaxException e) {
            log.error("Некорректный URL: {}", url, e);
            return null;
        }
    }



    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndexPageRequest {
        private String url;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndexPageResponse {
        private boolean result;
        private String error;
    }
}
