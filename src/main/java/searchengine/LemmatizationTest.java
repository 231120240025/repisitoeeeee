package searchengine;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.util.List;

public class LemmatizationTest {

    public static void main(String[] args) {
        try {
            // Создание экземпляра лемматизатора
            LuceneMorphology luceneMorph = new RussianLuceneMorphology();

            // Слово для лемматизации
            String word = "леса";

            // Получение базовых форм слова
            List<String> wordBaseForms = luceneMorph.getNormalForms(word);

            // Вывод базовых форм
            System.out.println("Базовые формы для слова \"" + word + "\":");
            wordBaseForms.forEach(System.out::println);
        } catch (Exception e) {
            System.err.println("Ошибка при работе лемматизатора: " + e.getMessage());
        }
    }
}
