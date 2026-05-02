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
        StorageAPI.addServer("server-1","minecraft.techsvc.de:10111","9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08");
        long space = StorageAPI.getFreeSpace("server-1");
        Logger.info(String.valueOf(space));
    }
}
