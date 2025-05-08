package org.my_ration.file_uploader.db;

import org.my_ration.file_uploader.entities.CustomPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;


//TODO: Repository интерфейсы кладут в папку repository, как сервисы в папку service
@Repository
public interface CustomPhotoRepository extends JpaRepository<CustomPhoto, Long> {

    // Найти все записи по fileName
    List<CustomPhoto> findByUserUIID(String fileName);

    // Найти запись по path
    CustomPhoto findByPath(String path);

    // Кастомный запрос через @Query (пример)
    @Query("SELECT p FROM CustomPhoto p WHERE p.size > :size")
    List<CustomPhoto> findPhotosLargerThan(@Param("size") Long size);

    //Найти текущий объем файлов пользователя
    @Query("SELECT sum(size) FROM CustomPhoto p WHERE p.userUIID = :userUIID")
    Long getCurrentUserStorageSize(@Param("userUIID") String user);
}