package virtual.camera.app.net;

import android.content.Context;
import android.text.TextUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class ConnectionBlocker {
    public static final String KEY_ENABLED = "connection_blocker_enable";

    private static final String PREF_NAME = "app_prefs";

    private final Context context;
    private final List<BlockRule> rules = new ArrayList<>();
    private final List<NetworkLog> logs = new ArrayList<>();
    private ConnectionBlockerCallback callback;
    private boolean logAllowedConnections;

    public ConnectionBlocker(Context context) {
        this.context = context.getApplicationContext();
    }

    public static boolean isEnabled(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, enabled)
                .apply();
    }

    public void setCallback(ConnectionBlockerCallback callback) {
        this.callback = callback;
    }

    public void setLogAllowedConnections(boolean enabled) {
        this.logAllowedConnections = enabled;
    }

    public void addRule(String target, int port, String description) {
        rules.add(new BlockRule(target, port, description));
    }

    public void clearRules() {
        rules.clear();
    }

    public List<BlockRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    public List<NetworkLog> getLogs() {
        return Collections.unmodifiableList(logs);
    }

    public boolean shouldBlock(String target, int port) {
        if (!isEnabled(context)) {
            notifyAllowed(target, port);
            return false;
        }

        if (rules.isEmpty()) {
            notifyBlocked(target, port, "Network blocker enabled");
            return true;
        }

        for (BlockRule rule : rules) {
            if (rule.matches(target, port)) {
                rule.hitCount++;
                if (callback != null) {
                    callback.onRuleTriggered(rule, target);
                }
                notifyBlocked(target, port, rule.description);
                return true;
            }
        }

        notifyAllowed(target, port);
        return false;
    }

    private void notifyAllowed(String target, int port) {
        if (logAllowedConnections) {
            notifyLog(new NetworkLog(target, port, false));
        }
        if (callback != null) {
            callback.onConnectionAllowed(target, port);
        }
    }

    private void notifyBlocked(String target, int port, String reason) {
        notifyLog(new NetworkLog(target, port, true));
        if (callback != null) {
            callback.onConnectionBlocked(target, port, reason);
        }
    }

    private void notifyLog(NetworkLog log) {
        logs.add(log);
        if (callback != null) {
            callback.onNetworkLog(log);
        }
    }

    public interface ConnectionBlockerCallback {
        void onConnectionAllowed(String target, int port);

        void onConnectionBlocked(String target, int port, String reason);

        void onNetworkLog(NetworkLog log);

        void onRuleTriggered(BlockRule rule, String target);
    }

    public static class BlockRule implements Serializable {
        private static final long serialVersionUID = 1L;

        public String target;
        public int port;
        public String description;
        public boolean enabled = true;
        public int hitCount;

        private transient Pattern pattern;

        public BlockRule(String target, int port, String description) {
            this.target = target;
            this.port = port;
            this.description = description;
            initializePattern();
        }

        public boolean matches(String target, int port) {
            if (!enabled || TextUtils.isEmpty(target)) {
                return false;
            }
            if (this.port != -1 && this.port != port) {
                return false;
            }
            if (pattern != null) {
                return pattern.matcher(target).find();
            }
            return target.equals(this.target);
        }

        private void initializePattern() {
            if (TextUtils.isEmpty(target)) {
                pattern = null;
                return;
            }
            pattern = Pattern.compile(target);
        }
    }

    public static class NetworkLog implements Serializable {
        private static final long serialVersionUID = 1L;

        public String target;
        public int port;
        public boolean blocked;
        public long timestamp;

        public NetworkLog(String target, int port, boolean blocked) {
            this.target = target;
            this.port = port;
            this.blocked = blocked;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
