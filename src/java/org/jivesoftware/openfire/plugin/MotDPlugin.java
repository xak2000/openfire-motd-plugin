package org.jivesoftware.openfire.plugin;

import org.jivesoftware.openfire.MessageRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.event.SessionEventDispatcher;
import org.jivesoftware.openfire.event.SessionEventListener;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.TaskEngine;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import java.io.File;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.TimerTask;

/**
 * MotD (Message of the Day) plugin.
 *
 * @author <a href="mailto:ryan@version2software.com">Ryan Graham</a>
 */
public class MotDPlugin implements Plugin {

    private static final String SUBJECT = "plugin.motd.subject";
    private static final String MESSAGE = "plugin.motd.message";
    private static final String ENABLED = "plugin.motd.enabled";
    private static final String MIN_DELAY = "plugin.motd.min_delay";

    private static final long DEFAULT_MIN_DELAY = Duration.ofHours(24).getSeconds();

    private JID serverAddress;
    private MessageRouter router;
    private MotdStorage motdStorage;

    private MotDSessionEventListener listener = new MotDSessionEventListener();

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        serverAddress = new JID(XMPPServer.getInstance().getServerInfo().getXMPPDomain());
        router = XMPPServer.getInstance().getMessageRouter();

        String tmpDir = System.getProperty("java.io.tmpdir");
        String dbFileName = Paths.get(tmpDir, "openfire_motd.db").toAbsolutePath().toString();
        motdStorage = new MotdStorage(dbFileName);

        SessionEventDispatcher.addListener(listener);
    }

    public void destroyPlugin() {
        SessionEventDispatcher.removeListener(listener);

        listener = null;
        serverAddress = null;
        router = null;
        try {
            motdStorage.close();
            motdStorage = null;
        } catch (Exception e) {
            // Ingore
        }
    }

    public void setSubject(String message) {
        JiveGlobals.setProperty(SUBJECT, message);
    }

    public String getSubject() {
        return JiveGlobals.getProperty(SUBJECT, "Message of the Day");
    }

    public void setMessage(String message) {
        JiveGlobals.setProperty(MESSAGE, message);
    }

    public String getMessage() {
        return JiveGlobals.getProperty(MESSAGE, "Big Brother is watching.");
    }

    public void setEnabled(boolean enable) {
        JiveGlobals.setProperty(ENABLED, Boolean.toString(enable));
    }

    public boolean isEnabled() {
        return JiveGlobals.getBooleanProperty(ENABLED, false);
    }

    public void setMinDelay(long seconds) {
        JiveGlobals.setProperty(MIN_DELAY, Long.toString(seconds));
    }

    public long getMinDelay() {
        return JiveGlobals.getLongProperty(MIN_DELAY, DEFAULT_MIN_DELAY);
    }

    private boolean shouldMotdBeSent(Session session) {
        if (!isEnabled()) {
            return false;
        }
        Instant lastSentDate = motdStorage.getLastSentDate(generateLastSentDateKey(session));
        if (lastSentDate == null) {
            return true;
        }
        long minDelay = getMinDelay();
        if (minDelay < 1) {
            return true;
        }
        return lastSentDate.isBefore(Instant.now().minus(minDelay, ChronoUnit.SECONDS));
    }

    private void sendMotdIfRequired(Session session) {
        if (shouldMotdBeSent(session)) {
            Message message = new Message();
            message.setFrom(serverAddress);
            message.setTo(session.getAddress());
            message.setSubject(getSubject());
            message.setBody(getMessage());

            TimerTask messageTask = new TimerTask() {
                @Override
                public void run() {
                    router.route(message);
                }
            };

            TaskEngine.getInstance().schedule(messageTask, 5000);
            motdStorage.setLastSentDate(generateLastSentDateKey(session), Instant.now());
        }
    }

    private String generateLastSentDateKey(Session session) {
        return session.getAddress().toBareJID();
    }

    private class MotDSessionEventListener implements SessionEventListener {

        public void sessionCreated(Session session) {
            sendMotdIfRequired(session);
        }

        public void sessionDestroyed(Session session) {
            //ignore
        }

        public void resourceBound(Session session) {
            // Do nothing.
        }

        public void anonymousSessionCreated(Session session) {
            //ignore
        }

        public void anonymousSessionDestroyed(Session session) {
            //ignore
        }
    }
}
