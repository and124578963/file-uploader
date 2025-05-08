package org.my_ration.file_uploader.db;

import lombok.RequiredArgsConstructor;
import org.my_ration.file_uploader.entities.CustomPhoto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


//TODO: db сервисов нет, есть сервисы от названий entity, значит это CustomPhotoService. Нужно переместить в папку сервисов.
@Service
@RequiredArgsConstructor // Lombok: инъекция зависимостей через конструктор
public class DBService {

    private final CustomPhotoRepository customPhotoRepository;

    // Получить все записи
    public List<CustomPhoto> getAllPhoto() {
        return customPhotoRepository.findAll();
    }

    // Найти по path
    public CustomPhoto getPhotoByPath(String path) {
        return customPhotoRepository.findByPath(path);
    }

    // Добавить новую запись
    @Transactional
    public CustomPhoto saveUserPhoto(CustomPhoto photo) {
        return customPhotoRepository.save(photo);
    }

    public Long getCurrentUserStorageSize(String userUUID) {
        return customPhotoRepository.getCurrentUserStorageSize(userUUID);
    }
}