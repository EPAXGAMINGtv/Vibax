# StorageAPI - Dokumentation

## Übersicht

Die StorageAPI ist eine umfassende, verteilte Speicherlösung, die für moderne Anwendungen entwickelt wurde, die skalierbare und zuverlässige Dateispeicherung benötigen. Sie bietet erweiterte Funktionen für verteilte Speichersysteme mit Fokus auf Leistung, Sicherheit und Ausfallsicherheit.

## Kernfunktionen

### Dateioperationen
- **Lesen/Schreiben**: Dateien effizient auf Speicherservern speichern und abrufen
- **Verzeichnisverwaltung**: Dateien auflisten, Verzeichnisse erstellen und verwalten
- **Dateimanipulation**: Kopieren, verschieben, umbenennen und löschen von Dateien
- **Versionskontrolle**: Dateiversionen erstellen, auflisten und wiederherstellen
- **Metadatenverwaltung**: Benutzerdefinierte Metadaten für Dateien speichern und abrufen

### Serververwaltung
- **Dynamische Serververwaltung**: Hinzufügen/Entfernen von Speicherservern zur Laufzeit
- **Health Monitoring**: Automatische Überwachung der Serververfügbarkeit
- **Lastverteilung**: Intelligente Verteilung von Anfragen auf verfügbare Server
- **Failover**: Automatischer Wechsel bei Serverausfällen

### Sicherheit
- **Authentifizierung**: Token-basierte Authentifizierung mit Passwort-Hashes
- **IP-Filterung**: Zugriffsbeschränkung auf bestimmte IP-Adressen
- **Request Signing**: Signierte Anfragen zur Verhinderung von Manipulationen
- **TLS/SSL**: Konfigurierbare Verschlüsselung der Datenübertragung
- **Audit-Logging**: Detaillierte Protokollierung aller Zugriffe

### Leistung & Zuverlässigkeit
- **Caching**: In-Memory Cache mit konfigurierbarer TTL (Standard: 60 Sekunden)
- **Circuit Breaker**: Verhindert Kaskadenfehler (Schwellenwert: 50%, Timeout: 30s)
- **Retry-Mechanismus**: Automatische Wiederholungen mit exponentiellem Backoff (3 Versuche)
- **Connection Pooling**: Effiziente Verwaltung von Verbindungen
- **Batch-Operationen**: Mehrere Operationen in einer einzigen Anfrage ausführen

### Monitoring & Observability
- **Metriken**: Echtzeit-Überwachung von Anfragen, Fehlern, Latenz und Cache-Trefferraten
- **Ereignissystem**: Abonnieren von Speicherereignissen (Server-Status, Dateiänderungen)
- **Health Checks**: Echtzeit-Status der Serververfügbarkeit
- **Statistiken**: Umfassende Systemstatistiken

## Architektur

### Komponenten

1. **ServerPool**: Verwaltung der verfügbaren Speicherserver
2. **CacheLayer**: In-Memory Caching für häufig abgerufene Dateien
3. **HealthMonitor**: Überwachung und Prüfung der Serververfügbarkeit
4. **CircuitBreaker**: Schutz vor Kaskadenfehlern
5. **MetricsCollector**: Sammlung und Aggregation von Leistungsmetriken
6. **EventEmitter**: Ereignisverwaltung und Benachrichtigungen
7. **RetryPolicy**: Konfigurierbare Wiederholungsrichtlinien

### Fehlerbehandlung

Die StorageAPI verwendet eine strukturierte Fehlerbehandlung:
- **StorageAPIException**: Benutzerdefinierte Ausnahme mit Fehlercodes
- **ErrorCode Enum**: Standardisierte Fehlercodes (SERVER_NOT_FOUND, REQUEST_FAILED, etc.)
- **Kettenverfolgung**: Vollständige Ausnahmeverkettung für Root-Cause-Analyse

## Konfiguration

### Standardkonfiguration
- Cache aktiviert: `true`
- Cache TTL: `60.000ms` (1 Minute)
- Maximale Wiederholungen: `3`
- Initialer Wiederholungsverzögerung: `100ms`
- Circuit Breaker Schwellenwert: `0.5` (50% Fehlerrate)
- Circuit Breaker Minimum Calls: `5`
- Circuit Breaker Timeout: `30.000ms` (30 Sekunden)

### Serverkonfiguration
Server werden über IP-Adresse, Passwort-Hash und optionalen Namen konfiguriert:
```java
ServerConfig(ip, passwordHash, name)
```

## Verwendung

### Initialisierung
```java
StorageAPI.InitStorageAPI(true);
```

### Dateioperationen
```java
// Datei lesen
String content = StorageAPI.readFile("server1", "datei.txt");

// Datei schreiben
boolean success = StorageAPI.writeFile("server1", "datei.txt", "Inhalt");

// Datei löschen
boolean deleted = StorageAPI.deleteFile("server1", "datei.txt");
```

### Serververwaltung
```java
// Server hinzufügen
StorageAPI.addServer("server1", "192.168.1.100:8080", "passworthash");

// Server entfernen
StorageAPI.removeServer("server1");

// Serverstatus abrufen
Map<String, Object> health = StorageAPI.getServerHealth("server1");
```

### Batch-Operationen
```java
BatchRequest batch = new BatchRequest();
// Batch-Operationen hinzufügen
BatchResponse response = StorageAPI.executeBatch(batch);
```

## Anwendungsfälle

### Ideale Szenarien
- **Social Media Plattformen**: Speicherung von Benutzerinhalten, Medien und Profildaten
- **Content Management Systeme**: Zentrale Speicherung von Website-Inhalten
- **Dokumentenmanagementsysteme**: Versionierte Speicherung von Dokumenten
- **Datenanalyseplattformen**: Verteilte Speicherung großer Datensätze
- **E-Commerce**: Speicherung von Produktinformationen und Medien

### Vorteile
- **Skalierbarkeit**: Verteilte Architektur unterstützt horizontales Wachstum
- **Ausfallsicherheit**: Mehrere Server mit automatischem Failover
- **Leistung**: Caching und Connection Pooling für optimale Performance
- **Sicherheit**: Mehrere Sicherheitsschichten für den Datenzugriff
- **Verwaltung**: Einfache Überwachung und Verwaltung über integrierte Tools

## Metriken & Monitoring

Die StorageAPI bietet umfassende Metriken:
- **Gesamtanfragen**: Anzahl aller verarbeiteten Anfragen
- **Fehlgeschlagene Anfragen**: Anzahl der fehlgeschlagenen Operationen
- **Durchschnittliche Latenz**: Durchschnittliche Antwortzeiten
- **Cache-Trefferrate**: Prozentsatz der Cache-Treffer
- **Server-Status**: Verfügbarkeit und Gesundheit jedes Servers

## Ereignisse

Die StorageAPI emittiert Ereignisse für:
- Server online/offline Statusänderungen
- Dateioperationen (Erstellung, Änderung, Löschung)
- Systemwarnungen und Fehler
- Metriken-Schwellenwertüberschreitungen

## Best Practices

1. **Serververteilung**: Verteilen Sie Server geografisch für bessere Ausfallsicherheit
2. **Caching**: Passen Sie die Cache-TTL an Ihre Anwendungsanforderungen an
3. **Monitoring**: Implementieren Sie regelmäßige Health-Checks
4. **Sicherheit**: Verwenden Sie starke Passworthashes und IP-Filterung
5. **Fehlerbehandlung**: Implementieren Sie angemessene Retry-Strategien
6. **Versionskontrolle**: Nutzen Sie die Versionskontrolle für kritische Dateien

## Technische Details

### Unterstützte Operationen
- Datei-Upload/Download
- Verzeichnis-Operationen
- Datei-Metadaten
- Datei-Versionierung
- Datei-Locking
- Batch-Operationen
- Suchfunktionen
- Prüfsummen-Berechnung

### Unterstützte Protokolle
- HTTP/HTTPS für Serverkommunikation
- TLS für verschlüsselte Verbindungen
- Token-basierte Authentifizierung

### Performance-Optimierungen
- In-Memory Caching
- Connection Pooling
- Asynchrone Operationen
- Batch-Verarbeitung
- Intelligente Lastverteilung

## Fehlercodes

- `SERVER_NOT_FOUND`: Der angeforderte Server wurde nicht gefunden
- `REQUEST_FAILED`: Die Anfrage konnte nicht ausgeführt werden
- `CIRCUIT_OPEN`: Der Circuit Breaker ist offen (zu viele Fehler)
- `TIMEOUT`: Die Anfrage ist abgelaufen
- `AUTHENTICATION_FAILED`: Authentifizierung fehlgeschlagen
- `FILE_NOT_FOUND`: Die angeforderte Datei existiert nicht
- `INVALID_PARAMETERS`: Ungültige Parameter wurden übergeben
- `SERVER_ERROR`: Interner Serverfehler
- `UNKNOWN_ERROR`: Unbekannter Fehler ist aufgetreten

## Fazit

Die StorageAPI bietet eine robuste, skalierbare und sichere Lösung für verteilte Speicheranforderungen. Mit ihren erweiterten Funktionen für Dateiverwaltung, Serververwaltung, Sicherheit und Monitoring ist sie ideal für moderne Anwendungen, die eine zuverlässige und leistungsstarke Speicherlösung benötigen. Ob für Social Media Plattformen, Content Management Systeme oder Datenanalyseplattformen – die StorageAPI bietet die Flexibilität und Leistung, die für den Erfolg Ihrer Anwendung erforderlich sind.