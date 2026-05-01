package epax;

import de.epax.storageapi.StorageAPI;
import epax.logging.Logger;

import java.io.IOException;
import java.util.Map;

public class WebServer {
    public static void main(String[] args){
        try {
            StorageAPI.InitStorageAPI(true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Map<String, Object> health = StorageAPI.getServerHealth("server-2");
        Logger.info(health.toString());
    }
}
