package com.mars.linker.broker.netty;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * MQTT 主题过滤器工具与索引结构。
 */
final class TopicFilterSupport {

    private TopicFilterSupport() {
    }

    static boolean isExactTopic(String topicFilter) {
        return topicFilter != null
                && !topicFilter.isEmpty()
                && !topicFilter.contains("+")
                && !topicFilter.contains("#")
                && !topicFilter.startsWith("$share/");
    }

    static boolean isShareSubscription(String topicFilter) {
        return topicFilter != null && topicFilter.startsWith("$share/");
    }

    static ShareSubscription parseShareSubscription(String topicFilter) {
        if (!isShareSubscription(topicFilter)) {
            return null;
        }
        String s = topicFilter.substring("$share/".length());
        int idx = s.indexOf('/');
        if (idx <= 0 || idx == s.length() - 1) {
            return null;
        }
        String group = s.substring(0, idx);
        String filter = s.substring(idx + 1);
        if (group.isEmpty() || group.contains("/")) {
            return null;
        }
        if (!isValidTopicFilter(filter)) {
            return null;
        }
        return new ShareSubscription(group, filter);
    }

    static boolean isValidTopicFilter(String topicFilter) {
        if (topicFilter == null || topicFilter.isEmpty() || isShareSubscription(topicFilter)) {
            return false;
        }
        String[] parts = topicFilter.split("/", -1);
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) {
                return false;
            }
            if (p.equals("#")) {
                return i == parts.length - 1;
            }
            if (p.contains("#")) {
                return false;
            }
            if (p.equals("+")) {
                continue;
            }
            if (p.contains("+")) {
                return false;
            }
        }
        return true;
    }

    static boolean matchTopicFilter(String filter, String topic) {
        if (filter == null || filter.isEmpty() || topic == null || topic.isEmpty()) {
            return false;
        }
        if (isShareSubscription(filter)) {
            return false;
        }
        if (!filter.contains("+") && !filter.contains("#")) {
            return filter.equals(topic);
        }
        String[] f = filter.split("/", -1);
        String[] t = topic.split("/", -1);
        int i = 0;
        for (; i < f.length; i++) {
            String fp = f[i];
            if (fp.equals("#")) {
                return i == f.length - 1;
            }
            if (i >= t.length) {
                return false;
            }
            if (fp.equals("+")) {
                continue;
            }
            if (!fp.equals(t[i])) {
                return false;
            }
        }
        return i == t.length;
    }

    static final class ShareSubscription {
        final String group;
        final String filter;

        private ShareSubscription(String group, String filter) {
            this.group = group;
            this.filter = filter;
        }
    }

    static final class TopicFilterIndex {
        private final TopicNode root = new TopicNode();

        void add(String filter) {
            if (filter == null || filter.isEmpty()) {
                return;
            }
            String[] parts = filter.split("/", -1);
            TopicNode cur = root;
            for (String p : parts) {
                if ("#".equals(p)) {
                    cur.hashFilters.add(filter);
                    return;
                }
                if ("+".equals(p)) {
                    cur = cur.plusChild();
                    continue;
                }
                cur = cur.child(p);
            }
            cur.exactFilters.add(filter);
        }

        void remove(String filter) {
            if (filter == null || filter.isEmpty()) {
                return;
            }
            String[] parts = filter.split("/", -1);
            TopicNode cur = root;
            for (String p : parts) {
                if ("#".equals(p)) {
                    cur.hashFilters.remove(filter);
                    return;
                }
                if ("+".equals(p)) {
                    TopicNode next = cur.plus;
                    if (next == null) {
                        return;
                    }
                    cur = next;
                    continue;
                }
                TopicNode next = cur.children.get(p);
                if (next == null) {
                    return;
                }
                cur = next;
            }
            cur.exactFilters.remove(filter);
        }

        void clear() {
            root.children.clear();
            root.exactFilters.clear();
            root.hashFilters.clear();
            root.plus = null;
        }

        Set<String> match(String topic) {
            if (topic == null || topic.isEmpty()) {
                return Collections.emptySet();
            }
            String[] levels = topic.split("/", -1);
            Set<String> out = new CopyOnWriteArraySet<>();
            collect(root, levels, 0, out);
            return out;
        }

        private void collect(TopicNode node, String[] levels, int idx, Set<String> out) {
            if (!node.hashFilters.isEmpty()) {
                out.addAll(node.hashFilters);
            }
            if (idx >= levels.length) {
                if (!node.exactFilters.isEmpty()) {
                    out.addAll(node.exactFilters);
                }
                return;
            }
            TopicNode literal = node.children.get(levels[idx]);
            if (literal != null) {
                collect(literal, levels, idx + 1, out);
            }
            TopicNode plus = node.plus;
            if (plus != null) {
                collect(plus, levels, idx + 1, out);
            }
        }
    }

    private static final class TopicNode {
        final Map<String, TopicNode> children = new ConcurrentHashMap<>();
        final Set<String> exactFilters = new CopyOnWriteArraySet<>();
        final Set<String> hashFilters = new CopyOnWriteArraySet<>();
        volatile TopicNode plus;

        TopicNode child(String level) {
            return children.computeIfAbsent(level, k -> new TopicNode());
        }

        TopicNode plusChild() {
            if (plus == null) {
                synchronized (this) {
                    if (plus == null) {
                        plus = new TopicNode();
                    }
                }
            }
            return plus;
        }
    }
}
