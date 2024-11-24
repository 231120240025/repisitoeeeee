package searchengine.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.Index;

@Entity
@Table(name = "page", indexes = {
        @Index(name = "idx_path_prefix", columnList = "pathPrefix")
})
@Data
@NoArgsConstructor
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private int id;

    @ManyToOne
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(name = "path", nullable = false, columnDefinition = "TEXT")
    private String path;

    @Column(name = "path_prefix", nullable = false, length = 255)
    private String pathPrefix;

    @Column(name = "code", nullable = false)
    private int code;

    @Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @PrePersist
    @PreUpdate
    private void updatePathPrefix() {
        this.pathPrefix = path.length() > 255 ? path.substring(0, 255) : path;
    }
}
