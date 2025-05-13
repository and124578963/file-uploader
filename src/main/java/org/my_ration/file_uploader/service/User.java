package org.my_ration.file_uploader.service;


import lombok.Data;

@Data
public class User {


    private String userUIID;
    private boolean isAdmin;
    private String userName;

    private boolean isSystemRole;

    public User(){
        this.isSystemRole=false;
        this.isAdmin=false;
    }


    public boolean getIsSystemRole() {
        return isSystemRole;
    }
}
