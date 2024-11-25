package searchengine.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SiteIndexingService {

    private static final Logger logger = LoggerFactory.getLogger(SiteIndexingService.class);

    private final AtomicBoolean isIndexing = new AtomicBoolean(false);
    private volatile boolean stopRequested = false;
    private final Object stopLock = new Object();

    private final SitesList sitesList;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final PageProcessingService pageProcessingService;

    @Autowired
    public SiteIndexingService(SitesList sitesList, SiteRepository siteRepository,
                               PageProcessingService pageProcessingService,
                               PageRepository pageRepository, LemmaRepository lemmaRepository,
                               IndexRepository indexRepository) {
        this.sitesList = sitesList;
        this.siteRepository = siteRepository;
        this.pageProcessingService = pageProcessingService;
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
            logger.info("Запуск процесса индексации...");
            new Thread(this::performIndexing).start();
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
            logger.info("Процесс индексации будет остановлен...");
            updateFailedStatusForIncompleteSites();
        }
    }

    private void performIndexing() {
        ExecutorService executorService = Executors.newFixedThreadPool(Math.min(10, sitesList.getSites().size()));
        List<Future<?>> futures = new CopyOnWriteArrayList<>();

        try {
            sitesList.getSites().forEach(siteConfig -> {
                futures.add(executorService.submit(() -> {
                    String url = siteConfig.getUrl();
                    String name = siteConfig.getName();
                    long startTime = System.currentTimeMillis();

                    if (stopRequested) {
                        logger.warn("Процесс индексации остановлен перед началом обработки сайта: {}", name);
                        return;
                    }

                    deleteSiteData(url);
                    Site site = createNewSiteRecord(name, url);

                    try {
                        logger.info("Начало индексации сайта: {} ({})", name, url);
                        pageProcessingService.crawlSite(url, site, stopRequested);

                        if (!stopRequested) {
                            updateSiteStatus(site, Status.INDEXED, null);
                            logger.info("Индексация завершена для сайта: {}", name);
                        } else {
                            logger.warn("Индексация сайта {} была остановлена.", name);
                        }
                    } catch (Exception e) {
                        logger.error("Ошибка при индексации сайта {}: {}", name, e.getMessage(), e);
                        updateSiteStatus(site, Status.FAILED, e.getMessage());
                    } finally {
                        logger.info("Индексация сайта {} завершена за {} ms", name, System.currentTimeMillis() - startTime);
                    }
                }));
            });

            for (Future<?> future : futures) {
                if (stopRequested) {
                    future.cancel(true);
                }
                future.get();
            }
        } catch (Exception e) {
            logger.error("Ошибка в процессе индексации: {}", e.getMessage(), e);
        } finally {
            shutdownExecutorService(executorService);
            isIndexing.set(false);
            logger.info("Процесс индексации завершен.");
        }
    }

    private void deleteSiteData(String siteUrl) {
        siteRepository.findByUrl(siteUrl).ifPresent(site -> {
            List<Page> pages = pageRepository.findAllBySite(site);
            pages.forEach(page -> {
                indexRepository.deleteAllByPage(page);
                pageRepository.delete(page);
            });

            List<Lemma> lemmas = lemmaRepository.findBySite(site);
            lemmas.forEach(lemma -> indexRepository.deleteAllByLemma(lemma));

            lemmaRepository.deleteAllBySite(site);
            siteRepository.delete(site);
            logger.info("Удалены данные сайта: {}", siteUrl);
        });
    }

    private Site createNewSiteRecord(String name, String url) {
        Site site = new Site();
        site.setName(name);
        site.setUrl(url);
        site.setStatus(Status.INDEXING);
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);
        logger.info("Создана запись о сайте: {} ({})", name, url);
        return site;
    }

    private void updateFailedStatusForIncompleteSites() {
        siteRepository.findAll().forEach(site -> {
            if (site.getStatus() == Status.INDEXING) {
                site.setStatus(Status.FAILED);
                site.setLastError("Индексация остановлена пользователем");
                site.setStatusTime(LocalDateTime.now());
                siteRepository.save(site);
                logger.info("Сайт {} помечен как FAILED", site.getName());
            }
        });
    }

    private void updateSiteStatus(Site site, Status status, String error) {
        site.setStatus(status);
        site.setStatusTime(LocalDateTime.now());
        site.setLastError(error);
        siteRepository.save(site);
        logger.info("Статус сайта {} обновлен на {}", site.getName(), status);
    }

    private void shutdownExecutorService(ExecutorService executorService) {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                logger.warn("ExecutorService не завершился корректно, принудительное завершение...");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.error("Ошибка при завершении ExecutorService: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
    }

    public void indexPage(String url) {
        if (isIndexingRunning()) {
            throw new IllegalStateException("Индексация уже запущена, невозможно запустить индексацию отдельной страницы");
        }

        stopRequested = false;
        logger.info("Запуск индексации отдельной страницы: {}", url);
        new Thread(() -> {
            Site site = siteRepository.findByUrl(url).orElseGet(() -> createNewSiteRecord(url, url));
            try {
                pageProcessingService.crawlSite(url, site, stopRequested);
                if (!stopRequested) {
                    updateSiteStatus(site, Status.INDEXED, null);
                    logger.info("Индексация завершена для страницы: {}", url);
                } else {
                    updateSiteStatus(site, Status.FAILED, "Индексация остановлена пользователем");
                    logger.warn("Индексация страницы {} была остановлена.", url);
                }
            } catch (Exception e) {
                logger.error("Ошибка при индексации страницы {}: {}", url, e.getMessage(), e);
                updateSiteStatus(site, Status.FAILED, e.getMessage());
            }
        }).start();
    }
}
