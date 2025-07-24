package org.my_ration.file_uploader.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.my_ration.file_uploader.entities.Photo;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class UserService {
    private final PhotoService photoService;

    public boolean isPhotoExist(String path){
        Photo photo=photoService.getPhotoByPath(path);
        if(photo==null){
            return false;
        }else{
            return true;
        }
    }

    public User getUser() {
        User user = new User();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            // Обработка случая, когда аутентификация отсутствует
            return user;
        }

        user.setUserUIID(authentication.getName());

        user.setSystemRole(authentication.getAuthorities()
                .stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_system")));

        user.setAdmin(authentication.getAuthorities()
                .stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));

        if (authentication.getPrincipal() instanceof Jwt) {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            user.setUserName(jwt.getClaimAsString("preferred_username"));
            log.debug("Username: {}", user.getUserName());
        }

        if(user.isSystemRole()){
            log.debug("Есть роль 'system'");
        }

        log.debug("userUIID: {}", user.getUserUIID());
        return user;
    }

}