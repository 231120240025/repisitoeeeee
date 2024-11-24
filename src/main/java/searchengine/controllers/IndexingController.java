package searchengine.controllers;

import searchengine.services.IndexingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class IndexingController {

    private final IndexingService indexingService;

    @Autowired
    public IndexingController(IndexingService indexingService) {
        this.indexingService = indexingService;
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<Map<String, Object>> startIndexing() {
        Map<String, Object> response = new HashMap<>();
        if (indexingService.isIndexingRunning()) {
            response.put("result", false);
            response.put("error", "Индексация уже запущена");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
        try {
            indexingService.startIndexing();
            response.put("result", true);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (IllegalStateException e) {
            response.put("result", false);
            response.put("error", e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            response.put("result", false);
            response.put("error", "Произошла ошибка при запуске индексации");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<Map<String, Object>> stopIndexing() {
        Map<String, Object> response = new HashMap<>();
        if (!indexingService.isIndexingRunning()) {
            response.put("result", false);
            response.put("error", "Индексация не запущена");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
        try {
            indexingService.stopIndexing();
            response.put("result", true);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            response.put("result", false);
            response.put("error", "Произошла ошибка при остановке индексации");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
