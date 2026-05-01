package de.epax.storageapi.manager;

import de.epax.storageapi.logging.Logger;

import java.io.*;
import java.util.*;

public class PropertiesManager {

    private final Map<String, Properties> propertiesMap = new HashMap<>();
    private File baseFile;

    public void load(String name, String filePath) throws IOException {

        Properties props = new Properties();
        baseFile = new File(filePath);

        File parent = baseFile.getParentFile();
        if (parent != null) parent.mkdirs();

        if (!baseFile.exists()) {
            baseFile.createNewFile();
            try (OutputStream out = new FileOutputStream(baseFile)) {
                props.store(out, "Auto-created properties file");
            }
            Logger.info("Created new properties file: " + filePath);
        }

        try (InputStream input = new FileInputStream(baseFile)) {
            props.load(input);
            propertiesMap.put(name, props);
            Logger.info("Loaded properties group: " + name + " from " + filePath);
        }
    }

    public void set(String group, String key, String value) {
        Properties props = propertiesMap.computeIfAbsent(group, k -> {
            Logger.info("Created new properties group: " + group);
            return new Properties();
        });

        props.setProperty(key, value);
        Logger.debug("Set property [" + group + "." + key + "] = " + value);
    }

    public String get(String group, String key) {
        Properties props = propertiesMap.get(group);

        if (props == null) {
            Logger.warn("Requested missing properties group: " + group);
            return null;
        }

        String value = props.getProperty(key);

        if (value == null) {
            Logger.warn("Missing key [" + key + "] in group [" + group + "]");
        }

        return value;
    }

    public String get(String group, String key, String defaultValue) {
        Properties props = propertiesMap.get(group);

        if (props == null) {
            Logger.warn("Missing group [" + group + "] -> returning default");
            return defaultValue;
        }

        return props.getProperty(key, defaultValue);
    }

    public int getInt(String group, String key, int defaultValue) {
        try {
            return Integer.parseInt(get(group, key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            Logger.warn("Invalid number for [" + group + "." + key + "] -> using default");
            return defaultValue;
        }
    }

    public boolean exists(String group, String key) {
        Properties props = propertiesMap.get(group);
        return props != null && props.containsKey(key);
    }

    public void save(String group, String filePath) throws IOException {

        Properties props = propertiesMap.get(group);

        if (props == null) {
            Logger.error("Cannot save missing properties group: " + group);
            throw new IllegalArgumentException("Group does not exist: " + group);
        }

        try (OutputStream out = new FileOutputStream(filePath)) {
            props.store(out, "Saved properties: " + group);
            Logger.info("Saved properties group: " + group + " to " + filePath);
        } catch (IOException e) {
            Logger.error("Failed to save group: " + group + " -> " + e.getMessage());
            throw e;
        }
    }

    public Properties getProperties(String group) {
        return propertiesMap.get(group);
    }

    public Set<String> getGroups() {
        return Collections.unmodifiableSet(propertiesMap.keySet());
    }
}