package epax;

import de.epax.storageapi.ServerConfig;
import de.epax.storageapi.StorageAPI;
import epax.logging.Logger;
import epax.user.UserManager;

import java.io.IOException;
import java.util.Map;

public class WebServer {
    public static void main(String[] args){
        try {
            StorageAPI.InitStorageAPI(true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        UserManager umgr = new UserManager();
    }
}
