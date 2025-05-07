package org.my_ration.file_uploader.entities;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "custom_photos")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomPhoto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_UIID", nullable = false)
    private String userUIID;

    @Column(name = "path", nullable = false, unique = true)
    private String path;

    @Column(name = "size")
    private Long size;
}
