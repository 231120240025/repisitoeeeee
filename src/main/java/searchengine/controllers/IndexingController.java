package searchengine.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.services.IndexingService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class IndexingController {

    private static final Logger logger = LoggerFactory.getLogger(IndexingController.class);

    private final IndexingService indexingService;

    @Autowired
    public IndexingController(IndexingService indexingService) {
        this.indexingService = indexingService;
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<Map<String, Object>> startIndexing() {
        logger.info("Получен запрос на запуск индексации.");
        Map<String, Object> response = new HashMap<>();
        if (indexingService.isIndexingRunning()) {
            logger.warn("Индексация уже запущена.");
            response.put("result", false);
            response.put("error", "Индексация уже запущена");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
        try {
            indexingService.startIndexing();
            logger.info("Индексация успешно запущена.");
            response.put("result", true);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (IllegalStateException e) {
            logger.error("Ошибка при запуске индексации: {}", e.getMessage());
            response.put("result", false);
            response.put("error", e.getMessage());
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            logger.error("Непредвиденная ошибка при запуске индексации: {}", e.getMessage(), e);
            response.put("result", false);
            response.put("error", "Произошла ошибка при запуске индексации");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<Map<String, Object>> stopIndexing() {
        logger.info("Получен запрос на остановку индексации.");
        Map<String, Object> response = new HashMap<>();
        if (!indexingService.isIndexingRunning()) {
            logger.warn("Попытка остановки индексации, которая не запущена.");
            response.put("result", false);
            response.put("error", "Индексация не запущена");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
        try {
            indexingService.stopIndexing();
            logger.info("Индексация успешно остановлена.");
            response.put("result", true);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Непредвиденная ошибка при остановке индексации: {}", e.getMessage(), e);
            response.put("result", false);
            response.put("error", "Произошла ошибка при остановке индексации");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
