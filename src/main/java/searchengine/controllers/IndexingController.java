package searchengine.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.services.SiteIndexingService;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class IndexingController {

    private static final Logger logger = LoggerFactory.getLogger(IndexingController.class);
    private final SiteIndexingService siteIndexingService;

    @Autowired
    public IndexingController(SiteIndexingService siteIndexingService) {
        this.siteIndexingService = siteIndexingService;
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<Map<String, Object>> startIndexing() {
        logger.info("Received request to start indexing.");
        Map<String, Object> response = new HashMap<>();

        if (siteIndexingService.isIndexingRunning()) {
            response.put("result", false);
            response.put("error", "Indexing is already running");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        try {
            siteIndexingService.startIndexing();
            response.put("result", true);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error starting indexing: {}", e.getMessage(), e);
            response.put("result", false);
            response.put("error", "An error occurred while starting indexing");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<Map<String, Object>> stopIndexing() {
        logger.info("Received request to stop indexing.");
        Map<String, Object> response = new HashMap<>();

        if (!siteIndexingService.isIndexingRunning()) {
            response.put("result", false);
            response.put("error", "Indexing is not running");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        try {
            siteIndexingService.stopIndexing();
            response.put("result", true);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error stopping indexing: {}", e.getMessage(), e);
            response.put("result", false);
            response.put("error", "An error occurred while stopping indexing");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/reindexPage") // Changed from /api/indexPage to avoid conflict
    public ResponseEntity<Map<String, Object>> reindexPage(@RequestParam String url) {
        logger.info("Received request to reindex page: {}", url);
        Map<String, Object> response = new HashMap<>();

        if (siteIndexingService.isIndexingRunning()) {
            response.put("result", false);
            response.put("error", "Indexing is already running");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }

        try {
            siteIndexingService.indexPage(url);
            response.put("result", true);
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error reindexing page {}: {}", url, e.getMessage(), e);
            response.put("result", false);
            response.put("error", "An error occurred while reindexing the page");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
