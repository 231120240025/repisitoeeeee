package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;

import java.io.IOException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingPageService {

    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    @Transactional
    public void indexPage(String url, Site site) {
        try {
            log.info("Начало индексации страницы: {}", url);

            // Шаг 1: Загрузка HTML
            String htmlContent = fetchHtmlContent(url);

            // Шаг 2: Сохранение страницы
            Page page = savePage(url, site, htmlContent);

            // Шаг 3: Извлечение лемм
            Map<String, Integer> lemmas = extractLemmas(htmlContent);

            // Шаг 4: Сохранение лемм и индексов
            saveLemmasAndIndexes(page, lemmas);

            log.info("Индексация страницы завершена: {}", url);
        } catch (Exception e) {
            log.error("Ошибка индексации страницы: {}", url, e);
        }
    }

    private String fetchHtmlContent(String url) {
        try {
            log.info("Загрузка HTML для: {}", url);
            Document document = Jsoup.connect(url).get();
            return document.html();
        } catch (IOException e) {
            log.error("Ошибка загрузки HTML для: {}", url, e);
            throw new RuntimeException("Не удалось загрузить содержимое страницы: " + url, e);
        }
    }

    private Page savePage(String url, Site site, String htmlContent) {
        log.info("Сохранение страницы в базу данных: {}", url);
        Page page = new Page();
        page.setSite(site);
        page.setPath(url);
        page.setContent(htmlContent);
        page.setCode(200);
        return pageRepository.save(page);
    }

    private Map<String, Integer> extractLemmas(String htmlContent) {
        log.info("Извлечение текста и лемм из HTML.");
        String text = Jsoup.parse(htmlContent).text();
        Map<String, Integer> lemmas = new HashMap<>();

        String[] words = text.toLowerCase().split("\\W+");
        for (String word : words) {
            if (word.length() > 2) {
                lemmas.put(word, lemmas.getOrDefault(word, 0) + 1);
            }
        }

        log.info("Извлечено {} лемм.", lemmas.size());
        return lemmas;
    }

    @Transactional
    private void saveLemmasAndIndexes(Page page, Map<String, Integer> lemmas) {
        log.info("Сохранение лемм и индексов для страницы: {}", page.getPath());
        List<Index> indexes = new ArrayList<>();
        Map<String, Lemma> cachedLemmas = new HashMap<>();
        List<Lemma> newLemmas = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            String lemmaText = entry.getKey();
            int count = entry.getValue();

            Lemma lemma = cachedLemmas.computeIfAbsent(lemmaText, lt ->
                    lemmaRepository.findByLemmaAndSiteId(lt, page.getSite().getId())
                            .orElseGet(() -> {
                                Lemma newLemma = new Lemma();
                                newLemma.setLemma(lt);
                                newLemma.setFrequency(0);
                                newLemma.setSite(page.getSite());
                                newLemmas.add(newLemma);
                                return newLemma;
                            })
            );

            lemma.setFrequency(lemma.getFrequency() + 1);

            Index index = new Index();
            index.setPage(page);
            index.setLemma(lemma);
            index.setRank(count);
            indexes.add(index);
        }

        // Сохранение новых лемм в базе
        if (!newLemmas.isEmpty()) {
            lemmaRepository.saveAll(newLemmas);
            log.info("Сохранено новых лемм: {}", newLemmas.size());
        }

        // Батчевое сохранение индексов
        indexRepository.saveAll(indexes);
        log.info("Сохранено {} индексов для страницы: {}", indexes.size(), page.getPath());
    }
}
