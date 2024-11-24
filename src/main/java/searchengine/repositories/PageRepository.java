package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.Optional;

@Repository
public interface PageRepository extends JpaRepository<Page, Long> {

    // Удаляет все страницы, связанные с сайтом
    void deleteBySite(Site site);

    // Проверяет существование страницы по пути и сайту
    boolean existsByPathAndSite(String path, Site site);

    // Удаляет конкретную страницу по пути и сайту
    void deleteByPathAndSite(String path, Site site);

    // Находит страницу по пути и сайту
    Optional<Page> findByPathAndSite(String path, Site site);
}
