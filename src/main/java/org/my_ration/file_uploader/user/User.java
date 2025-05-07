package org.my_ration.file_uploader.user;


import lombok.Getter;
import org.springframework.security.core.*;
import org.springframework.security.core.context.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;


public class User {


    @Getter
    private String userUIID;
    @Getter
    private boolean isAdmin;
    @Getter
    private String userName;


    private boolean isSystemRole;

    public User(){
    }

    public void authorizeUser(){
        isSystemRole=false;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        userUIID = authentication.getName();
        isAdmin = authentication.getAuthorities()
                .stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            //System.out.println("Токен: " + jwt.getClaims());
            // Получаем preferred_username
            userName = jwt.getClaimAsString("preferred_username");

            System.out.println("Username: " + userName); // test

            List<String> realmRoles = jwt.getClaimAsStringList("realm_access.roles");
            if (realmRoles != null && realmRoles.contains("system")) {
                isSystemRole=true;
                System.out.println("Есть роль 'system'");
            }

        }

        System.out.println("userUIID: "+userUIID);
        System.out.println("isAdmin "+isAdmin);
    }

    public void addUser(){
    }


    public boolean getIsSystemRole() {
        return isSystemRole;
    }
}
