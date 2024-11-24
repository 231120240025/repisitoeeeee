package searchengine.services;

import org.springframework.stereotype.Service;
import searchengine.dto.statistics.SearchResult;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private static final List<SearchResult> indexData = new ArrayList<>();

    static {
        indexData.add(new SearchResult("http://www.site.com", "Example Site", "/path/to/page/1",
                "First Page Title", "Snippet with <b>highlight</b>", 0.95));
        // Дополнительные данные
    }

    public List<SearchResult> search(String query, String site, int offset, int limit) {
        if (indexData.isEmpty()) {
            throw new IllegalStateException("Индекс для поиска пока не готов");
        }

        // 1. Лемматизация запроса
        List<String> lemmas = getLemmas(query);

        // 2. Исключение слишком частых лемм
        lemmas = filterFrequentLemmas(lemmas);

        // 3. Сортировка лемм по возрастанию частоты
        lemmas = sortLemmasByFrequency(lemmas);

        // 4. Поиск страниц
        List<SearchResult> matchedPages = findPagesByLemmas(lemmas, site);

        // 5. Расчет релевантности
        List<SearchResult> rankedPages = calculateRelevance(matchedPages);

        // 6. Сортировка по релевантности
        rankedPages.sort(Comparator.comparingDouble(SearchResult::getRelevance).reversed());

        // 7. Постраничный вывод
        return rankedPages.stream()
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());
    }

    private List<String> getLemmas(String query) {
        // Метод для лемматизации запроса. Замените этот код вашим алгоритмом лемматизации.
        // Пример: разбиваем на слова и удаляем служебные слова.
        String[] words = query.toLowerCase().split("\\s+");
        Set<String> stopWords = Set.of("и", "в", "на", "с", "по"); // Пример стоп-слов
        return Arrays.stream(words)
                .filter(word -> !stopWords.contains(word))
                .distinct()
                .collect(Collectors.toList());
    }

    private List<String> filterFrequentLemmas(List<String> lemmas) {
        // Исключаем леммы, которые встречаются на слишком большом количестве страниц
        int threshold = (int) (indexData.size() * 0.8); // Пример: 80% страниц
        return lemmas.stream()
                .filter(lemma -> getLemmaFrequency(lemma) < threshold)
                .collect(Collectors.toList());
    }

    private int getLemmaFrequency(String lemma) {
        // Пример вычисления частоты леммы
        return (int) indexData.stream()
                .filter(page -> page.getSnippet().toLowerCase().contains(lemma) ||
                        page.getTitle().toLowerCase().contains(lemma))
                .count();
    }

    private List<String> sortLemmasByFrequency(List<String> lemmas) {
        return lemmas.stream()
                .sorted(Comparator.comparingInt(this::getLemmaFrequency))
                .collect(Collectors.toList());
    }

    private List<SearchResult> findPagesByLemmas(List<String> lemmas, String site) {
        List<SearchResult> result = new ArrayList<>(indexData);

        for (String lemma : lemmas) {
            result = result.stream()
                    .filter(page -> page.getSnippet().toLowerCase().contains(lemma) ||
                            page.getTitle().toLowerCase().contains(lemma))
                    .collect(Collectors.toList());
            if (result.isEmpty()) {
                return Collections.emptyList();
            }
        }

        if (site != null && !site.isEmpty()) {
            result = result.stream()
                    .filter(page -> page.getSite().equals(site))
                    .collect(Collectors.toList());
        }

        return result;
    }

    private List<SearchResult> calculateRelevance(List<SearchResult> pages) {
        double maxRelevance = pages.stream()
                .mapToDouble(this::calculatePageRank)
                .max()
                .orElse(1.0);

        return pages.stream()
                .map(page -> {
                    double absoluteRelevance = calculatePageRank(page);
                    double relativeRelevance = absoluteRelevance / maxRelevance;
                    return new SearchResult(
                            page.getSite(),
                            page.getSiteName(),
                            page.getUri(),
                            page.getTitle(),
                            generateSnippet(page.getSnippet(), absoluteRelevance), // Генерация сниппета
                            relativeRelevance
                    );
                })
                .collect(Collectors.toList());
    }

    private double calculatePageRank(SearchResult page) {
        // Пример подсчета ранга. Используйте свой источник данных.
        return Math.random() * 10; // Замените это на логику подсчета ранга из ваших данных.
    }

    private String generateSnippet(String text, double relevance) {
        // Генерация сниппета с выделением совпадений
        int maxLength = 100; // Пример длины сниппета
        if (text.length() > maxLength) {
            return text.substring(0, maxLength) + "...";
        }
        return text;
    }

    public int getSearchResultCount(String query, String site) {
        List<String> lemmas = getLemmas(query);
        return (int) findPagesByLemmas(lemmas, site).size();
    }
}
