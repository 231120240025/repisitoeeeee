package searchengine.services;

import org.apache.lucene.morphology.russian.RussianMorphology;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.model.*;
import searchengine.repositories.*;
import java.util.concurrent.atomic.AtomicBoolean;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class IndexingService {

    private static final Logger logger = LoggerFactory.getLogger(IndexingService.class);

    private final AtomicBoolean isIndexing = new AtomicBoolean(false);
    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    private volatile boolean stopRequested = false;
    private final Object stopLock = new Object();

    @Autowired
    public IndexingService(SitesList sitesList, SiteRepository siteRepository,
                           PageRepository pageRepository, LemmaRepository lemmaRepository,
                           IndexRepository indexRepository) {
        this.sitesList = sitesList;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
    }

    public boolean isIndexingRunning() {
        return isIndexing.get();
    }

    public void startIndexing() {
        if (isIndexing.compareAndSet(false, true)) {
            stopRequested = false;
            new Thread(() -> {
                try {
                    logger.info("Запуск процесса индексации...");
                    performIndexing();
                    logger.info("Процесс индексации завершен.");
                } finally {
                    isIndexing.set(false);
                }
            }).start();
        } else {
            logger.warn("Попытка запуска индексации, когда она уже запущена.");
            throw new IllegalStateException("Индексация уже запущена");
        }
    }

    public void stopIndexing() {
        synchronized (stopLock) {
            if (!isIndexing.get()) {
                logger.warn("Попытка остановки индексации, когда она не запущена.");
                throw new IllegalStateException("Индексация не запущена");
            }
            stopRequested = true;
            logger.info("Процесс индексации был остановлен пользователем.");
            updateFailedStatusForIncompleteSites();
        }
    }

    @SuppressWarnings("resource")
    private void performIndexing() {
        ExecutorService executorService = Executors.newFixedThreadPool(sitesList.getSites().size());
        try {
            sitesList.getSites().forEach(siteConfig -> executorService.submit(() -> {
                long startTime = System.currentTimeMillis();
                logger.info("Начало обработки сайта: {} ({})", siteConfig.getName(), siteConfig.getUrl());
                deleteSiteData(siteConfig.getUrl());
                logger.info("Данные сайта удалены: {} ({})", siteConfig.getName(), siteConfig.getUrl());

                Site site = createNewSiteRecord(siteConfig.getName(), siteConfig.getUrl());

                try {
                    crawlSite(siteConfig.getUrl(), site);
                    if (!stopRequested) {
                        logger.info("Индексация завершена для сайта: {} ({})", siteConfig.getName(), siteConfig.getUrl());
                        updateSiteStatus(site, Status.INDEXED, null);
                    }
                } catch (Exception e) {
                    logger.error("Ошибка при индексации сайта: {} ({}): {}", siteConfig.getName(), siteConfig.getUrl(), e.getMessage());
                    updateSiteStatus(site, Status.FAILED, e.getMessage());
                } finally {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    logger.info("Индексация сайта: {} завершена за {} ms", siteConfig.getName(), elapsedTime);
                }
            }));
        } catch (Exception e) {
            logger.error("Ошибка выполнения ExecutorService", e);
        } finally {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    logger.warn("ExecutorService не завершился корректно за 60 секунд.");
                    executorService.shutdownNow(); // Принудительное завершение
                }
            } catch (InterruptedException e) {
                logger.error("Ошибка при ожидании завершения ExecutorService", e);
                Thread.currentThread().interrupt();
            }
        }
    }



    @SuppressWarnings("resource")
    private void crawlSite(String url, Site site) {
        if (stopRequested) {
            logger.warn("Обход сайта {} остановлен из-за остановки индексации.", site.getUrl());
            return;
        }
        Set<String> visitedUrls = new HashSet<>();
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        try {
            logger.info("Запуск обхода сайта: {}", site.getUrl());
            forkJoinPool.invoke(new PageCrawlerTask(url, site, visitedUrls));
            logger.info("Обход сайта завершен: {}", site.getUrl());
        } catch (Exception e) {
            logger.error("Ошибка при работе ForkJoinPool для сайта {}: {}", site.getUrl(), e.getMessage());
        } finally {
            forkJoinPool.shutdown();
            try {
                if (!forkJoinPool.awaitTermination(60, TimeUnit.SECONDS)) {
                    logger.warn("ForkJoinPool не завершился корректно за 60 секунд.");
                    forkJoinPool.shutdownNow(); // Принудительное завершение
                }
            } catch (InterruptedException e) {
                logger.error("Ошибка при ожидании завершения ForkJoinPool", e);
                Thread.currentThread().interrupt();
            }
        }
    }




    private Map<String, Integer> extractLemmas(String text) {
        Map<String, Integer> lemmaCounts = new HashMap<>();
        try {
            RussianMorphology morphology = new RussianMorphology();
            String[] words = text.toLowerCase().replaceAll("[^а-яёa-z\\s]", "").split("\\s+");
            for (String word : words) {
                if (!word.isEmpty()) {
                    List<String> lemmas = morphology.getNormalForms(word);
                    for (String lemma : lemmas) {
                        lemmaCounts.put(lemma, lemmaCounts.getOrDefault(lemma, 0) + 1);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Ошибка при лемматизации текста: {}", e.getMessage());
        }
        return lemmaCounts;
    }

    private void savePage(String url, int statusCode, String content, Site site) {
        if (content == null || content.isEmpty()) {
            content = "Содержимое страницы недоступно.";
        }
        Page page = new Page();
        page.setPath(url.replace(site.getUrl(), ""));
        page.setCode(statusCode);
        page.setContent(content);
        page.setSite(site);
        pageRepository.save(page);

        Map<String, Integer> lemmas = extractLemmas(content);
        saveLemmasAndIndexes(lemmas, page, site);

        logger.debug("Сохранена страница: {} с кодом: {}", url, statusCode);
    }

    private void saveLemmasAndIndexes(Map<String, Integer> lemmas, Page page, Site site) {
        // Получение существующих лемм с частотой
        Map<String, Lemma> existingLemmas = lemmaRepository.findBySite(site).stream()
                .collect(Collectors.toMap(Lemma::getLemma, lemma -> lemma));

        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            String lemmaText = entry.getKey();
            int count = entry.getValue();

            // Использовать существующую лемму или создать новую
            Lemma lemma = existingLemmas.getOrDefault(lemmaText, new Lemma());
            if (!existingLemmas.containsKey(lemmaText)) {
                lemma.setLemma(lemmaText);
                lemma.setSite(site);
            }

            // Увеличение частоты
            lemma.setFrequency(lemma.getFrequency() + count);
            lemmaRepository.save(lemma);

            // Добавить сохранённую лемму в карту для повторного использования
            existingLemmas.putIfAbsent(lemmaText, lemma);

            // Сохранение индекса
            Index index = new Index();
            index.setLemma(lemma);
            index.setPage(page);
            index.setRank(count);
            indexRepository.save(index);
        }
    }




    private void deleteSiteData(String siteUrl) {
        siteRepository.findByUrl(siteUrl).ifPresent(site -> {
            pageRepository.deleteBySite(site);
            siteRepository.deleteById((long) site.getId());
            logger.debug("Удалены данные для сайта: {}", siteUrl);
        });
    }

    private Site createNewSiteRecord(String name, String url) {
        Site site = new Site();
        site.setName(name);
        site.setUrl(url);
        site.setStatus(Status.INDEXING);
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);
        logger.debug("Создана запись для сайта: {} ({})", name, url);
        return site;
    }

    private void updateFailedStatusForIncompleteSites() {
        String errorMessage = "Индексация остановлена пользователем";
        siteRepository.findAll().forEach(site -> {
            if (site.getStatus() == Status.INDEXING) {
                site.setStatus(Status.FAILED);
                site.setStatusTime(LocalDateTime.now());
                site.setLastError(errorMessage);
                siteRepository.save(site);
                logger.info("Сайт {} ({}) помечен как FAILED: {}", site.getName(), site.getUrl(), errorMessage);
            }
        });
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
            if (stopRequested || visitedUrls.contains(url) || isPageAlreadyIndexed(url, site)) {
                return;
            }

            visitedUrls.add(url);

            try {
                logger.debug("Обработка страницы: {}", url);
                org.jsoup.Connection.Response response = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .referrer("http://www.google.com")
                        .execute();

                int statusCode = response.statusCode();
                if (statusCode >= 400) {
                    logger.warn("Пропуск страницы {} из-за ошибочного HTTP-кода: {}", url, statusCode);
                    return; // Не индексируем страницу с кодом ошибки
                }

                String content = response.parse().html();

                // Лемматизация и сохранение данных
                Map<String, Integer> lemmas = extractLemmas(content);
                IndexingService.this.savePageAndLemmas(url, statusCode, content, lemmas, site);

                Elements links = response.parse().select("a[href]");
                Set<PageCrawlerTask> tasks = new HashSet<>();
                for (Element link : links) {
                    String href = link.absUrl("href");
                    if (href.startsWith(site.getUrl())) {
                        tasks.add(new PageCrawlerTask(href, site, visitedUrls));
                    }
                }
                invokeAll(tasks);
            } catch (Exception e) {
                logger.error("Ошибка при обработке страницы {}: {}", url, e.getMessage());
            }
        }

        private boolean isPageAlreadyIndexed(String url, Site site) {
            String relativePath = url.replace(site.getUrl(), "");
            boolean exists = pageRepository.existsByPathAndSite(relativePath, site);
            if (exists) {
                logger.debug("Страница уже проиндексирована: {}", url);
            }
            return exists;
        }
    }



    private void updateSiteStatus(Site site, Status status, String error) {
        site.setStatus(status);
        site.setStatusTime(LocalDateTime.now());
        site.setLastError(error);
        siteRepository.save(site);
        logger.debug("Обновлен статус сайта {}: {}", site.getUrl(), status);
    }

    private void savePageAndLemmas(String url, int statusCode, String content, Map<String, Integer> lemmas, Site site) {
        // Сохранение страницы
        Page page = new Page();
        page.setPath(url.replace(site.getUrl(), ""));
        page.setCode(statusCode);
        page.setContent(content);
        page.setSite(site);
        pageRepository.save(page);

        // Сохранение лемм и индексов
        saveLemmasAndIndexes(lemmas, page, site);
    }


}