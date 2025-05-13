package org.my_ration.file_uploader.entities;


import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "photo")
@Data
public class Photo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_UIID", nullable = false)
    private String userUIID;

    @Column(name = "path", nullable = false, unique = true)
    private String path;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "size")
    private Long size;

    @Column(name = "created_at", updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;
}
