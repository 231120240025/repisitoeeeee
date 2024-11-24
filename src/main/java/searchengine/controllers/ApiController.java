package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;

    public ApiController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    /**
     * API для получения статистики поискового движка.
     * Формирует и возвращает общую информацию о состоянии индексации.
     *
     * @return ResponseEntity с результатами статистики
     */
    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> getStatistics() {
        // Вызов сервиса для расчета статистики
        StatisticsResponse response = statisticsService.getStatistics();
        return ResponseEntity.ok(response);
    }
}
