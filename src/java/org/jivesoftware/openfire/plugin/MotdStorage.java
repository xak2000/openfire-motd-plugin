package org.jivesoftware.openfire.plugin;

import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;

import java.time.Instant;

public class MotdStorage implements AutoCloseable {

    private final MVStore storage;
    private final MVMap<String, String> lastSentDates;

    public MotdStorage(String fileName) {
        this.storage = new MVStore.Builder().fileName(fileName).open();
        this.lastSentDates = storage.openMap("lastSentDates");
    }

    @Override
    public void close() throws Exception {
        this.storage.commit();
        this.storage.close();
    }

    public void setLastSentDate(String jid, Instant lastSentDate) {
        lastSentDates.put(jid, lastSentDate.toString());
    }

    public Instant getLastSentDate(String jid) {
        String lastSentDateStr = lastSentDates.get(jid);
        if (lastSentDateStr == null) {
            return null;
        }
        try {
            return Instant.parse(lastSentDateStr);
        } catch (Exception e) {
            return null;
        }
    }

}
