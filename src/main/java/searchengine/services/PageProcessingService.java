package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class PageProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(PageProcessingService.class);

    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    private final AtomicBoolean stopRequested = new AtomicBoolean(false);

    @Autowired
    public PageProcessingService(PageRepository pageRepository, LemmaRepository lemmaRepository,
                                 IndexRepository indexRepository) {
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
    }

    public void crawlSite(String url, Site site, boolean stopRequested) {
        if (stopRequested) {
            logger.warn("Обход сайта {} остановлен из-за остановки индексации.", site.getUrl());
            return;
        }

        this.stopRequested.set(stopRequested);
        Set<String> visitedUrls = Collections.synchronizedSet(new HashSet<>());
        ForkJoinPool forkJoinPool = new ForkJoinPool();

        try {
            logger.info("Запуск обхода сайта: {}", url);
            forkJoinPool.invoke(new PageCrawlerTask(url, site, visitedUrls));
        } finally {
            forkJoinPool.shutdown();
        }
    }

    public void stopCrawling() {
        stopRequested.set(true);
        logger.info("Запрос на остановку процесса краулинга получен.");
    }

    private class PageCrawlerTask extends RecursiveAction {
        private final String url;
        private final Site site;
        private final Set<String> visitedUrls;

        public PageCrawlerTask(String url, Site site, Set<String> visitedUrls) {
            this.url = url;
            this.site = site;
            this.visitedUrls = visitedUrls;
        }

        @Override
        protected void compute() {
            if (stopRequested.get() || visitedUrls.contains(url) || pageRepository.existsByPathAndSite(url.replace(site.getUrl(), ""), site)) {
                return;
            }

            visitedUrls.add(url);

            try {
                logger.debug("Обработка страницы: {}", url);
                Document document = fetchPageContent(url);
                if (document == null) {
                    logger.warn("Пропуск страницы {}: не удалось получить содержимое.", url);
                    return;
                }

                savePageAndLemmas(url, 200, document.html(), site);

                List<PageCrawlerTask> tasks = extractLinks(document, site.getUrl()).stream()
                        .map(link -> new PageCrawlerTask(link, site, visitedUrls))
                        .collect(Collectors.toList());
                invokeAll(tasks);
            } catch (Exception e) {
                logger.error("Ошибка при обработке страницы {}: {}", url, e.getMessage(), e);
            }
        }

        private Document fetchPageContent(String url) {
            try {
                Thread.sleep((long) (500 + Math.random() * 4500));
                return Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .referrer("http://www.google.com")
                        .timeout(10000)
                        .get();
            } catch (IOException | InterruptedException e) {
                logger.error("Ошибка при загрузке содержимого страницы {}: {}", url, e.getMessage());
                Thread.currentThread().interrupt();
                return null;
            }
        }

        private List<String> extractLinks(Document document, String baseUrl) {
            Elements links = document.select("a[href]");
            return links.stream()
                    .map(link -> link.absUrl("href"))
                    .filter(href -> href.startsWith(baseUrl) && !href.equals(baseUrl))
                    .distinct()
                    .collect(Collectors.toList());
        }
    }

    private void savePageAndLemmas(String url, int statusCode, String content, Site site) {
        try {
            Page page = new Page();
            page.setPath(url.replace(site.getUrl(), ""));
            page.setCode(statusCode);
            page.setContent(content);
            page.setSite(site);
            pageRepository.save(page);

            Map<String, Integer> lemmas = extractLemmas(content);
            saveLemmasAndIndexes(lemmas, page, site);
        } catch (Exception e) {
            logger.error("Ошибка при сохранении страницы и лемм для {}: {}", url, e.getMessage(), e);
        }
    }

    private Map<String, Integer> extractLemmas(String text) {
        Map<String, Integer> lemmaCounts = new HashMap<>();
        String[] words = text.toLowerCase().replaceAll("[^а-яёa-z\s]", "").split("\s+");
        for (String word : words) {
            if (!word.isEmpty()) {
                lemmaCounts.put(word, lemmaCounts.getOrDefault(word, 0) + 1);
            }
        }
        return lemmaCounts;
    }

    private void saveLemmasAndIndexes(Map<String, Integer> lemmas, Page page, Site site) {
        Map<String, Lemma> existingLemmas = lemmaRepository.findBySite(site).stream()
                .collect(Collectors.toMap(Lemma::getLemma, lemma -> lemma));

        lemmas.forEach((lemmaText, frequency) -> {
            Lemma lemma = existingLemmas.getOrDefault(lemmaText, new Lemma());
            if (!existingLemmas.containsKey(lemmaText)) {
                lemma.setLemma(lemmaText);
                lemma.setSite(site);
            }
            lemma.setFrequency(lemma.getFrequency() + frequency);
            lemmaRepository.save(lemma);

            Index index = new Index();
            index.setLemma(lemma);
            index.setPage(page);
            index.setRank(frequency);
            indexRepository.save(index);
        });
    }
}
