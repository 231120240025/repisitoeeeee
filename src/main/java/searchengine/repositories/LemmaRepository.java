package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;
import searchengine.model.Site;

import java.util.List;
import java.util.Optional;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Integer> {
    Optional<Lemma> findByLemmaAndSiteId(String lemma, int siteId); // Находит лемму по тексту и ID сайта
    List<Lemma> findBySite(Site site);// Находит все леммы для сайта
    Optional<Lemma> findByLemmaAndSite(String lemma, Site site);

}
