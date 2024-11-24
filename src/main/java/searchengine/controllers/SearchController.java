package searchengine.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.statistics.ErrorResponse;
import searchengine.dto.statistics.SearchResult;
import searchengine.dto.statistics.SuccessResponse;
import searchengine.services.SearchService;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SearchController {

    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);
    private static final String EMPTY_QUERY_ERROR = "Задан пустой поисковый запрос";
    private static final String INDEX_NOT_READY_ERROR = "Индекс для поиска пока не готов";
    private static final String INVALID_PARAMETERS_ERROR = "Параметры offset и limit должны быть положительными числами";

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public ResponseEntity<Object> search(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "site", required = false) String site,
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "limit", defaultValue = "20") int limit
    ) {
        try {
            validateParameters(query, offset, limit);

            List<SearchResult> results = searchService.search(query, site, offset, limit);
            int count = searchService.getSearchResultCount(query, site);

            return ResponseEntity.ok(new SuccessResponse(true, count, results));
        } catch (IllegalArgumentException ex) {
            logger.warn("Ошибка в параметрах запроса: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(ex.getMessage()));
        } catch (IllegalStateException ex) {
            logger.warn("Индекс не готов: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponse(INDEX_NOT_READY_ERROR));
        } catch (Exception ex) {
            logger.error("Внутренняя ошибка сервера: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Внутренняя ошибка сервера"));
        }
    }

    private void validateParameters(String query, int offset, int limit) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException(EMPTY_QUERY_ERROR);
        }

        if (offset < 0 || limit <= 0) {
            throw new IllegalArgumentException(INVALID_PARAMETERS_ERROR);
        }
    }
}
