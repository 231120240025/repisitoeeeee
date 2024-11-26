package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;
import searchengine.model.Page;
import searchengine.model.Lemma;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<Index, Integer> {

    // Находит все индексы, связанные с конкретной страницей
    List<Index> findAllByPage(Page page);

    // Удаляет все индексы, связанные с конкретной страницей
    void deleteAllByPage(Page page);

    // Удаляет все индексы, связанные с конкретной леммой
    void deleteAllByLemma(Lemma lemma);
}
