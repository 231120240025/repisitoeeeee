package searchengine.controllers;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

@RestController
@RequestMapping("/api")
public class IndexPageController {

    private static final List<String> ALLOWED_DOMAINS = List.of("example.com", "another-example.com");

    @PostMapping("/indexPage")
    public ResponseEntity<IndexPageResponse> indexPage(@RequestBody IndexPageRequest request) {
        String url = request.getUrl();
        if (url == null || url.isBlank() || !isUrlAllowed(url)) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new IndexPageResponse(false, "Данная страница находится за пределами сайтов, указанных в конфигурационном файле"));
        }
        return ResponseEntity.ok(new IndexPageResponse(true, null));
    }

    private boolean isUrlAllowed(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            return ALLOWED_DOMAINS.stream().anyMatch(host::endsWith);
        } catch (URISyntaxException e) {
            return false;
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
