package epax.user;

import de.epax.storageapi.ServerConfig;
import de.epax.storageapi.StorageAPI;

public class UserManager {

    public UserManager(){
        for (ServerConfig s : StorageAPI.getServers().values()) {
            if (s.online) {
                StorageAPI.writeFile(s.name, "userData/.keep", "");
            }
        }
    }
}
