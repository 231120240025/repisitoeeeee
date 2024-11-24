package searchengine.dto.statistics;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class SuccessResponse {
    private final boolean result;
    private final int count;
    private final List<SearchResult> data;
}
