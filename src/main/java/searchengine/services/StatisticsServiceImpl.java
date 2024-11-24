package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SitesList sitesList;
    private final Random random = new Random();

    @Override
    public StatisticsResponse getStatistics() {
        // Подготовка объекта для общей статистики
        TotalStatistics totalStatistics = new TotalStatistics();
        totalStatistics.setSites(sitesList.getSites().size());
        totalStatistics.setIndexing(true);

        List<DetailedStatisticsItem> detailedStatistics = new ArrayList<>();

        int totalPages = 0;
        int totalLemmas = 0;

        // Формируем детализированную статистику для каждого сайта
        for (Site site : sitesList.getSites()) {
            DetailedStatisticsItem item = new DetailedStatisticsItem();

            item.setName(site.getName());
            item.setUrl(site.getUrl());

            // Генерация случайных данных для страниц и лемм
            int pages = random.nextInt(10000) + 1; // От 1 до 10000
            int lemmas = pages * random.nextInt(1000); // Количество лемм зависит от страниц

            item.setPages(pages);
            item.setLemmas(lemmas);

            // Устанавливаем случайный статус и ошибку (если есть)
            String status = generateRandomStatus();
            item.setStatus(status);

            if ("FAILED".equals(status)) {
                item.setError("Ошибка индексации: главная страница сайта недоступна");
            }

            // Устанавливаем время обновления статуса
            item.setStatusTime(System.currentTimeMillis() / 1000L);

            // Суммируем общие показатели
            totalPages += pages;
            totalLemmas += lemmas;

            detailedStatistics.add(item);
        }

        // Устанавливаем суммарные значения
        totalStatistics.setPages(totalPages);
        totalStatistics.setLemmas(totalLemmas);

        // Формируем общий объект данных
        StatisticsData statisticsData = new StatisticsData();
        statisticsData.setTotal(totalStatistics);
        statisticsData.setDetailed(detailedStatistics);

        // Формируем итоговый ответ
        StatisticsResponse response = new StatisticsResponse();
        response.setResult(true);
        response.setStatistics(statisticsData);

        return response;
    }

    /**
     * Генерирует случайный статус для сайта.
     *
     * @return "INDEXED", "FAILED" или "INDEXING"
     */
    private String generateRandomStatus() {
        String[] statuses = { "INDEXED", "FAILED", "INDEXING" };
        return statuses[random.nextInt(statuses.length)];
    }
}
