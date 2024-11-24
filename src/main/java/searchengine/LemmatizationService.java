package searchengine;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.util.*;

public class LemmatizationService {

    private static final Set<String> IGNORED_PARTS_OF_SPEECH = Set.of("МЕЖД", "СОЮЗ", "ПРЕДЛ", "ЧАСТ");
    private final LuceneMorphology luceneMorph;

    public LemmatizationService() throws Exception {
        this.luceneMorph = new RussianLuceneMorphology();
    }

    public Map<String, Integer> lemmatizeText(String text) {
        Map<String, Integer> lemmaFrequency = new HashMap<>();
        String[] words = text.toLowerCase().replaceAll("[^а-яё\\s]", "").split("\\s+");
        for (String word : words) {
            if (word.isBlank()) continue;
            try {
                List<String> morphInfos = luceneMorph.getMorphInfo(word);
                if (isIgnoredPartOfSpeech(morphInfos)) continue;
                List<String> normalForms = luceneMorph.getNormalForms(word);
                for (String lemma : normalForms) {
                    lemmaFrequency.put(lemma, lemmaFrequency.getOrDefault(lemma, 0) + 1);
                }
            } catch (Exception e) {
                System.err.println("Ошибка обработки слова: " + word + " - " + e.getMessage());
            }
        }
        return lemmaFrequency;
    }

    public String cleanHtml(String html) {
        return html.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
    }

    private boolean isIgnoredPartOfSpeech(List<String> morphInfos) {
        for (String info : morphInfos) {
            for (String ignored : IGNORED_PARTS_OF_SPEECH) {
                if (info.toUpperCase().contains(ignored)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void main(String[] args) {
        try {
            LemmatizationService lemmatizationService = new LemmatizationService();
            String text = "Повторное появление леопарда в Осетии позволяет предположить, что леопард постоянно обитает в некоторых районах Северного Кавказа.";
            System.out.println("Исходный текст: " + text);
            Map<String, Integer> lemmas = lemmatizationService.lemmatizeText(text);
            System.out.println("Результат лемматизации:");
            lemmas.forEach((lemma, count) -> System.out.println(lemma + " — " + count));
            String html = "<html><body><h1>Пример HTML</h1><p>Содержимое страницы</p></body></html>";
            String cleanText = lemmatizationService.cleanHtml(html);
            System.out.println("Очищенный текст из HTML: " + cleanText);
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
        }
    }
}
