package searchengine.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class IndexingService {

    private static final Logger logger = LoggerFactory.getLogger(IndexingService.class);

    private final AtomicBoolean isIndexing = new AtomicBoolean(false);
    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;

    private volatile boolean stopRequested = false;
    private final Object stopLock = new Object();

    @Autowired
    public IndexingService(SitesList sitesList, SiteRepository siteRepository, PageRepository pageRepository) {
        this.sitesList = sitesList;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
    }

    public boolean isIndexingRunning() {
        return isIndexing.get();
    }

    public void startIndexing() {
        if (isIndexing.compareAndSet(false, true)) {
            stopRequested = false; // Сбрасываем состояние остановки
            new Thread(() -> {
                try {
                    performIndexing();
                } finally {
                    isIndexing.set(false);
                }
            }).start();
        } else {
            throw new IllegalStateException("Индексация уже запущена");
        }
    }

    public void stopIndexing() {
        synchronized (stopLock) {
            if (!isIndexing.get()) {
                throw new IllegalStateException("Индексация не запущена");
            }
            stopRequested = true;
            logger.info("Индексация остановлена пользователем");
            updateFailedStatusForIncompleteSites();
        }
    }

    @SuppressWarnings("resource")
    private void performIndexing() {
        ExecutorService executorService = Executors.newFixedThreadPool(sitesList.getSites().size());
        try {
            sitesList.getSites().forEach(siteConfig ->
                    executorService.submit(() -> { // Запуск каждой индексации в отдельном потоке
                        logger.info("Начало обработки сайта: {} ({})", siteConfig.getName(), siteConfig.getUrl());
                        deleteSiteData(siteConfig.getUrl());
                        logger.info("Данные сайта удалены: {} ({})", siteConfig.getName(), siteConfig.getUrl());

                        Site site = createNewSiteRecord(siteConfig.getName(), siteConfig.getUrl());

                        try {
                            crawlSite(siteConfig.getUrl(), site); // Запуск обхода страниц
                            if (!stopRequested) {
                                logger.info("Индексация завершена успешно для сайта: {} ({})", siteConfig.getName(), siteConfig.getUrl());
                                updateSiteStatus(site, Status.INDEXED, null);
                            }
                        } catch (Exception e) {
                            logger.error("Ошибка при индексации сайта: {} ({})", siteConfig.getName(), siteConfig.getUrl(), e);
                            updateSiteStatus(site, Status.FAILED, e.getMessage());
                        }
                    })
            );
        } finally {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    logger.warn("ExecutorService не завершился корректно за 60 секунд");
                }
            } catch (InterruptedException e) {
                logger.error("Ошибка при ожидании завершения ExecutorService", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    @SuppressWarnings("resource") // Подавляем предупреждение о try-with-resources
    private void crawlSite(String url, Site site) {
        if (stopRequested) {
            return;
        }
        Set<String> visitedUrls = new HashSet<>();
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        try {
            forkJoinPool.invoke(new PageCrawlerTask(url, site, visitedUrls));
        } catch (Exception e) {
            logger.error("Ошибка при работе ForkJoinPool: {}", e.getMessage(), e);
        } finally {
            forkJoinPool.shutdown();
            try {
                if (!forkJoinPool.awaitTermination(60, TimeUnit.SECONDS)) {
                    logger.warn("ForkJoinPool не завершился корректно за 60 секунд");
                }
            } catch (InterruptedException e) {
                logger.error("Ошибка при ожидании завершения ForkJoinPool", e);
                Thread.currentThread().interrupt();
            }
        }
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
                org.jsoup.nodes.Document document = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows; U; WindowsNT 5.1; en-US; rv:1.8.1.6) Gecko/20070725 Firefox/2.0.0.6")
                        .referrer("http://www.google.com")
                        .get();
                String content = document.html();
                savePage(url, 200, content, site);

                Elements links = document.select("a[href]");
                Set<PageCrawlerTask> tasks = new HashSet<>();
                for (Element link : links) {
                    String href = link.absUrl("href");
                    if (href.startsWith(site.getUrl())) {
                        tasks.add(new PageCrawlerTask(href, site, visitedUrls));
                    }
                }
                invokeAll(tasks);
            } catch (Exception e) {
                savePage(url, 500, null, site);
            }
        }

        private boolean isPageAlreadyIndexed(String url, Site site) {
            String relativePath = url.replace(site.getUrl(), "");
            return pageRepository.existsByPathAndSite(relativePath, site);
        }
    }

    private void savePage(String url, int statusCode, String content, Site site) {
        Page page = new Page();
        page.setPath(url.replace(site.getUrl(), ""));
        page.setCode(statusCode);
        page.setContent(content);
        page.setSite(site);
        pageRepository.save(page);
    }

    private void deleteSiteData(String siteUrl) {
        siteRepository.findByUrl(siteUrl).ifPresent(site -> {
            pageRepository.deleteBySite(site);
            siteRepository.deleteById((long) site.getId());
        });
    }

    private Site createNewSiteRecord(String name, String url) {
        Site site = new Site();
        site.setName(name);
        site.setUrl(url);
        site.setStatus(Status.INDEXING);
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);
        return site;
    }

    private void updateSiteStatus(Site site, Status status, String error) {
        site.setStatus(status);
        site.setStatusTime(LocalDateTime.now());
        site.setLastError(error);
        siteRepository.save(site);
    }
}