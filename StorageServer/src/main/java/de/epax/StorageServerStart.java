package de.epax;

import at.favre.lib.crypto.bcrypt.BCrypt;
import de.epax.file.FileManager;
import de.epax.handler.*;
import de.epax.logging.Logger;
import de.epax.manager.PropertiesManager;
import de.epax.server.StorageServer;

import java.io.Console;
import java.io.IOException;
import java.util.Scanner;

/**
 * SECURITY FIX:
 *  - Password is now hashed with BCrypt (cost=12) instead of SHA-256
 *  - On first startup, prompts for a password and stores the BCrypt hash
 *  - Existing SHA-256 hashes in storageserver.properties are NOT compatible —
 *    delete the passwordHash line to trigger re-setup on next start
 */
public class StorageServerStart {

    private static int    port;
    private static int    maxConnections;
    private static String serverName;
    private static String storagePath;
    private static String passwordHash;

    public static void main(String[] args) {

        Logger.info("Starting StorageServer!");

        PropertiesManager properties = new PropertiesManager();

        try {
            properties.load("server", "storageserver.properties");
        } catch (IOException e) {
            Logger.error("Failed to load storageserver.properties: " + e.getMessage());
            return;
        }

        if (!properties.exists("server", "name"))           properties.set("server", "name",           "StorageServer");
        if (!properties.exists("server", "maxConnections")) properties.set("server", "maxConnections",  "100");
        if (!properties.exists("server", "port"))           properties.set("server", "port",            "8000");
        if (!properties.exists("server", "storagePath"))    properties.set("server", "storagePath",     "storage");

        // First startup: prompt for password and store BCrypt hash
        if (!properties.exists("server", "passwordHash")) {
            Logger.info("First startup — no API password set yet.");
            String password = promptPassword("Set API password: ");
            if (password == null || password.isBlank()) {
                Logger.error("Password cannot be empty!");
                return;
            }
            // SECURITY FIX: BCrypt with cost factor 12 (instead of SHA-256)
            String hash = BCrypt.withDefaults().hashToString(12, password.toCharArray());
            properties.set("server", "passwordHash", hash);
            Logger.info("Password hashed with BCrypt and saved.");
        }

        try {
            properties.save("server", "storageserver.properties");
        } catch (IOException e) {
            Logger.error("Failed to save properties: " + e.getMessage());
        }

        serverName     = properties.get("server", "name");
        maxConnections = properties.getInt("server", "maxConnections", 100);
        port           = properties.getInt("server", "port", 8000);
        storagePath    = properties.get("server", "storagePath");
        passwordHash   = properties.get("server", "passwordHash");

        new FileManager();
        StorageServer server = new StorageServer(port, maxConnections);

        server.addServerHandler(new SecureHandlerWrapper(new OnlineHandler(serverName),                                              "/online"),         "/online");
        server.addServerHandler(new SecureHandlerWrapper(new GetServerName(serverName, passwordHash),                                "/getname"),        "/getname");
        server.addServerHandler(new SecureHandlerWrapper(new ListFilesHandler(passwordHash),                                         "/listfiles"),      "/listfiles");
        server.addServerHandler(new SecureHandlerWrapper(new DeleteHandler(passwordHash),                                            "/delete"),         "/delete");
        server.addServerHandler(new SecureHandlerWrapper(new RecursiveDeleteHandler(passwordHash),                                   "/recursivedelete"),"/recursivedelete");
        server.addServerHandler(new SecureHandlerWrapper(new ExistsHandler(passwordHash),                                            "/exists"),         "/exists");
        server.addServerHandler(new SecureHandlerWrapper(new ReadFileHandler(passwordHash),                                          "/readfile"),       "/readfile");
        server.addServerHandler(new SecureHandlerWrapper(new PartialReadHandler(passwordHash),                                       "/partialread"),    "/partialread");
        server.addServerHandler(new SecureHandlerWrapper(new UploadHandler(passwordHash),                                            "/upload"),         "/upload");
        server.addServerHandler(new SecureHandlerWrapper(new DownloadHandler(passwordHash),                                          "/download"),       "/download");
        server.addServerHandler(new SecureHandlerWrapper(new MakeFileHandler(passwordHash),                                          "/makefile"),       "/makefile");
        server.addServerHandler(new SecureHandlerWrapper(new MakeConfigHandler(passwordHash),                                        "/makeconfig"),     "/makeconfig");
        server.addServerHandler(new SecureHandlerWrapper(new WriteFileHandler(passwordHash),                                         "/writefile"),      "/writefile");
        server.addServerHandler(new SecureHandlerWrapper(new PartialWriteHandler(passwordHash),                                      "/partialwrite"),   "/partialwrite");
        server.addServerHandler(new SecureHandlerWrapper(new AppendHandler(passwordHash),                                            "/append"),         "/append");
        server.addServerHandler(new SecureHandlerWrapper(new CreateDirectoryHandler(passwordHash),                                   "/createdirectory"),"/createdirectory");
        server.addServerHandler(new SecureHandlerWrapper(new CopyHandler(passwordHash),                                              "/copy"),           "/copy");
        server.addServerHandler(new SecureHandlerWrapper(new MoveHandler(passwordHash),                                              "/move"),           "/move");
        server.addServerHandler(new SecureHandlerWrapper(new RenameHandler(passwordHash),                                            "/rename"),         "/rename");
        server.addServerHandler(new SecureHandlerWrapper(new CopyDirectoryHandler(passwordHash),                                     "/copydir"),        "/copydir");
        server.addServerHandler(new SecureHandlerWrapper(new MoveDirectoryHandler(passwordHash),                                     "/movedir"),        "/movedir");
        server.addServerHandler(new SecureHandlerWrapper(new ReadConfigHandler(passwordHash),                                        "/readconfig"),     "/readconfig");
        server.addServerHandler(new SecureHandlerWrapper(new WriteConfigHandler(passwordHash),                                       "/writeconfig"),    "/writeconfig");
        server.addServerHandler(new SecureHandlerWrapper(new LockHandler(passwordHash),                                              "/lock"),           "/lock");
        server.addServerHandler(new SecureHandlerWrapper(new UnlockHandler(passwordHash),                                            "/unlock"),         "/unlock");
        server.addServerHandler(new SecureHandlerWrapper(new LockStatusHandler(passwordHash),                                        "/lockstatus"),     "/lockstatus");
        server.addServerHandler(new SecureHandlerWrapper(new VersionCreateHandler(passwordHash),                                     "/versioncreate"),  "/versioncreate");
        server.addServerHandler(new SecureHandlerWrapper(new VersionListHandler(passwordHash),                                       "/versionlist"),    "/versionlist");
        server.addServerHandler(new SecureHandlerWrapper(new VersionRestoreHandler(passwordHash),                                    "/versionrestore"), "/versionrestore");
        server.addServerHandler(new SecureHandlerWrapper(new SetMetadataHandler(passwordHash),                                       "/setmetadata"),    "/setmetadata");
        server.addServerHandler(new SecureHandlerWrapper(new GetMetadataHandler(passwordHash),                                       "/getmetadata"),    "/getmetadata");
        server.addServerHandler(new SecureHandlerWrapper(new GetAllMetadataHandler(passwordHash),                                    "/getallmetadata"), "/getallmetadata");
        server.addServerHandler(new SecureHandlerWrapper(new ChecksumHandler(passwordHash),                                          "/checksum"),       "/checksum");
        server.addServerHandler(new SecureHandlerWrapper(new DirectorySizeHandler(passwordHash),                                     "/dirsize"),        "/dirsize");
        server.addServerHandler(new SecureHandlerWrapper(new InfoHandler(passwordHash),                                              "/info"),           "/info");
        server.addServerHandler(new SecureHandlerWrapper(new SearchFileHandler(passwordHash),                                        "/searchfile"),     "/searchfile");
        server.addServerHandler(new SecureHandlerWrapper(new HealthHandler(passwordHash),                                            "/health"),         "/health");
        server.addServerHandler(new SecureHandlerWrapper(new MetricsHandler(passwordHash),                                           "/metrics"),        "/metrics");
        server.addServerHandler(new SecureHandlerWrapper(new AdminHandler(passwordHash, serverName, port, maxConnections, storagePath), "/admin"),       "/admin");
        server.addServerHandler(new SecureHandlerWrapper(new ChunkedUploadHandler(passwordHash),                                     "/uploadchunk"),    "/uploadchunk");
        server.addServerHandler(new SecureHandlerWrapper(new RangeRequestHandler(passwordHash),                                      "/rangedownload"),  "/rangedownload");
        server.addServerHandler(new isOnlineTextHandler(),                                                                           "/ion");

        server.startServer();
        Logger.info("StorageServer running on port " + port);
    }

    public static String getStoragePath()    { return storagePath;    }
    public static String getServerName()     { return serverName;     }
    public static int    getPort()           { return port;           }
    public static int    getMaxConnections() { return maxConnections; }

    private static String promptPassword(String prompt) {
        Console console = System.console();
        if (console != null) {
            char[] pw = console.readPassword(prompt);
            return pw != null ? new String(pw) : null;
        }
        System.out.print(prompt);
        Scanner scanner = new Scanner(System.in);
        return scanner.hasNextLine() ? scanner.nextLine() : null;
    }
}
