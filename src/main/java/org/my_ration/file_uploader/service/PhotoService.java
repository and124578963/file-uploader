package org.my_ration.file_uploader.service;

import lombok.RequiredArgsConstructor;
import org.my_ration.file_uploader.entities.Photo;
import org.my_ration.file_uploader.repository.PhotoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor // Lombok: инъекция зависимостей через конструктор
public class PhotoService {

    private final PhotoRepository photoRepository;

    // Получить все записи
    public List<Photo> getAllPhoto() {
        return photoRepository.findAll();
    }

    // Найти по path
    public Photo getPhotoByPath(String path) {
        return photoRepository.findByPath(path);
    }

    // Добавить новую запись
    @Transactional
    public void saveUserPhoto(Photo photo) {
        photoRepository.save(photo);
    }

    public Long getUserCurrentStorageSizeByUIID(String userUUID) {
        Long storageSize= photoRepository.getUserCurrentStorageSizeByUIID(userUUID);
        if(storageSize==null){
            return 0L;
        }
        return storageSize;
    }

    @Transactional
    public void deletePhotoByPathDirect(String path) {
        photoRepository.deleteByPath(path);
    }

    @Transactional
    public void deletePhotoById(Long id) {
        photoRepository.deleteById(id);
    }
}