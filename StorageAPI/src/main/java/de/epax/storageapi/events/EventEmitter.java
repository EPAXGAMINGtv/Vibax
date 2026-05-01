package de.epax.storageapi.events;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class EventEmitter {
    private final ConcurrentHashMap<StorageEvent.EventType, List<Consumer<StorageEvent>>> listeners = new ConcurrentHashMap<>();

    public void on(StorageEvent.EventType type, Consumer<StorageEvent> listener) {
        listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void off(StorageEvent.EventType type, Consumer<StorageEvent> listener) {
        List<Consumer<StorageEvent>> list = listeners.get(type);
        if (list != null) {
            list.remove(listener);
        }
    }

    public void emit(StorageEvent event) {
        List<Consumer<StorageEvent>> list = listeners.get(event.getType());
        if (list != null) {
            for (Consumer<StorageEvent> listener : list) {
                try {
                    listener.accept(event);
                } catch (Exception e) {
                    // Log error but continue
                    System.err.println("Event listener error: " + e.getMessage());
                }
            }
        }
    }

    public void clearAll() {
        listeners.clear();
    }
}
