package searchengine.services;

import org.springframework.stereotype.Service;
import searchengine.dto.statistics.SearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private static final List<SearchResult> indexData = new ArrayList<>();

    static {
        // Инициализация данных для примера
        indexData.add(new SearchResult("http://www.site.com", "Example Site", "/path/to/page/1",
                "First Page Title", "Snippet with <b>highlight</b>", 0.95));
    }

    public List<SearchResult> search(String query, String site, int offset, int limit) {
        if (indexData.isEmpty()) { // Проверка готовности индекса
            throw new IllegalStateException("Индекс для поиска пока не готов");
        }

        return indexData.stream()
                .filter(item -> {
                    boolean matchesQuery = item.getTitle().toLowerCase().contains(query.toLowerCase())
                            || item.getSnippet().toLowerCase().contains(query.toLowerCase());
                    boolean matchesSite = site == null || site.isEmpty() || item.getSite().equals(site);
                    return matchesQuery && matchesSite;
                })
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());
    }

    public int getSearchResultCount(String query, String site) {
        return (int) indexData.stream()
                .filter(item -> {
                    boolean matchesQuery = item.getTitle().toLowerCase().contains(query.toLowerCase())
                            || item.getSnippet().toLowerCase().contains(query.toLowerCase());
                    boolean matchesSite = site == null || site.isEmpty() || item.getSite().equals(site);
                    return matchesQuery && matchesSite;
                })
                .count();
    }
}
