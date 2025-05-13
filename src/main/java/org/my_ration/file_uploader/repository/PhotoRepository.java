package org.my_ration.file_uploader.repository;

import org.my_ration.file_uploader.entities.Photo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PhotoRepository extends JpaRepository<Photo, Long> {

    // Найти все записи по category
    List<Photo> findByCategory(String category);

    // Найти все записи по userUIID
    List<Photo> findByUserUIID(String userUIID);

    // Найти запись по path
    Photo findByPath(String path);

    //Найти текущий объем файлов пользователя
    @Query("SELECT sum(size) FROM Photo p WHERE p.userUIID = :userUIID")
    Long getUserCurrentStorageSizeByUIID(@Param("userUIID") String user);
}