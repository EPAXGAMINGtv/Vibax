package de.epax;


import de.epax.storageapi.StorageAPI;
import de.epax.storageapi.logging.Logger;
import de.epax.user.UserManager;

import java.io.IOException;


public class WebServer {
    public static void main(String[] args){
        try {
            StorageAPI.InitStorageAPI(true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Logger.info("Initializing UserManager");
        UserManager umgr = new UserManager();
        UserManager.createUser("epax","epax","12343");
    }
}
