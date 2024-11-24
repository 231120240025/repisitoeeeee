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
        } finally {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    logger.warn("ExecutorService не завершился корректно за 60 секунд.");
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
                logger.debug("Обработка страницы: {}", url);
                org.jsoup.nodes.Document document = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
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
                logger.error("Ошибка при обработке страницы {}: {}", url, e.getMessage());
                savePage(url, 500, "Ошибка загрузки страницы.", site);
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
        logger.debug("Сохранена страница: {} с кодом: {}", url, statusCode);
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

    private void updateSiteStatus(Site site, Status status, String error) {
        site.setStatus(status);
        site.setStatusTime(LocalDateTime.now());
        site.setLastError(error);
        siteRepository.save(site);
        logger.debug("Обновлен статус сайта {}: {}", site.getUrl(), status);
    }
}
