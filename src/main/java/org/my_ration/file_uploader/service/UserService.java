package org.my_ration.file_uploader.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.my_ration.file_uploader.db.DBService;
import org.my_ration.file_uploader.entities.CustomPhoto;
import org.my_ration.file_uploader.user.User;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final DBService dbService;
    @Getter
    private User user;

    public UserService(DBService dbService) {
        this.dbService = dbService;
    }

    public CustomPhoto saveUserPhoto(CustomPhoto customPhoto){
        return dbService.saveUserPhoto(customPhoto);
    }

    public boolean isPhotoExist(String path){
        CustomPhoto customPhoto=dbService.getPhotoByPath(path);

        if(customPhoto==null){
            return false;
        }else{
            return true;
        }

    }


    public void getUserInfo(){
        user=new User();
        user.authorizeUser();
    }

    public Long getUserStorage() {
        Long storageSize = dbService.getCurrentUserStorageSize(user.getUserUIID());
        if(storageSize==null){
            return 0L;
        }
        return storageSize;
    }
}