package org.my_ration.file_uploader.service;


import lombok.Data;

@Data
public class User {


    private String userUIID;
    private boolean isAdmin=false;
    private String userName;

    private boolean isSystemRole=false;

    public boolean getIsSystemRole() {
        return isSystemRole;
    }


}
