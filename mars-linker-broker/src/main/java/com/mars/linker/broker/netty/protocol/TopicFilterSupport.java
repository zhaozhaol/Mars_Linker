package com.mars.linker.broker.netty.protocol;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * MQTT 主题过滤器工具与索引结构。
 */
public final class TopicFilterSupport {

    private static final ConcurrentHashMap<String, String[]> FILTER_PARTS_CACHE = new ConcurrentHashMap<>();
    private static final int FILTER_CACHE_MAX = 4096;

    private TopicFilterSupport() {
    }

    /**
     * 返回 filter 按 '/' split 的结果，优先从缓存获取。
     * MQTT 场景 filter 数量有限且相对稳定，缓存命中率极高。
     */
    private static String[] getFilterParts(String filter) {
        String[] parts = FILTER_PARTS_CACHE.get(filter);
        if (parts != null) {
            return parts;
        }
        parts = filter.split("/", -1);
        if (FILTER_PARTS_CACHE.size() < FILTER_CACHE_MAX) {
            String[] existing = FILTER_PARTS_CACHE.putIfAbsent(filter, parts);
            if (existing != null) {
                parts = existing;
            }
        }
        return parts;
    }

    /** 仅供测试清空缓存。 */
    static void clearFilterPartsCacheForTests() {
        FILTER_PARTS_CACHE.clear();
    }

    public static boolean isExactTopic(String topicFilter) {
        return topicFilter != null
                && !topicFilter.isEmpty()
                && !topicFilter.contains("+")
                && !topicFilter.contains("#")
                && !topicFilter.startsWith("$share/");
    }

    public static boolean isShareSubscription(String topicFilter) {
        return topicFilter != null && topicFilter.startsWith("$share/");
    }

    public static ShareSubscription parseShareSubscription(String topicFilter) {
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

    public static boolean isValidTopicFilter(String topicFilter) {
        if (topicFilter == null || topicFilter.isEmpty() || isShareSubscription(topicFilter)) {
            return false;
        }
        String[] parts = topicFilter.split("/", -1);
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            // MQTT 3.1.1 §4.7.1: 空层级合法（如 /sensor/data、sensor/、sensor//data）
            // 仅校验 +/# 必须占完整一级
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

    public static boolean matchTopicFilter(String filter, String topic) {
        if (filter == null || filter.isEmpty() || topic == null || topic.isEmpty()) {
            return false;
        }
        // MQTT-4.7.2-1: 通配符 # 和 +（作为第一层）不匹配 $ 前缀系统主题
        if (topic.startsWith("$") && (filter.startsWith("#") || filter.startsWith("+"))) {
            return false;
        }
        if (isShareSubscription(filter)) {
            return false;
        }
        if (!filter.contains("+") && !filter.contains("#")) {
            return filter.equals(topic);
        }
        String[] f = getFilterParts(filter);
        // 手动遍历 topic 层级，避免 topic.split("/", -1) 产生临时数组
        int tStart = 0;
        int tLen = topic.length();
        int i = 0;
        for (; i < f.length; i++) {
            String fp = f[i];
            if (fp.equals("#")) {
                return i == f.length - 1;
            }
            if (tStart > tLen) {
                // topic 层级已耗尽
                return false;
            }
            int slash = topic.indexOf('/', tStart);
            if (fp.equals("+")) {
                // 匹配任意单层（含空层级）
                tStart = slash < 0 ? tLen + 1 : slash + 1;
                continue;
            }
            // 精确层级匹配
            String tp = slash < 0 ? topic.substring(tStart) : topic.substring(tStart, slash);
            if (!fp.equals(tp)) {
                return false;
            }
            tStart = slash < 0 ? tLen + 1 : slash + 1;
        }
        // 所有 filter 层级已消费，topic 也必须全部消费
        return tStart > tLen;
    }

    public static final class ShareSubscription {
        public final String group;
        public final String filter;

        public ShareSubscription(String group, String filter) {
            this.group = group;
            this.filter = filter;
        }
    }

    public static final class TopicFilterIndex {
        private final TopicNode root = new TopicNode();

        public void add(String filter) {
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

        public void remove(String filter) {
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

        public void clear() {
            root.children.clear();
            root.exactFilters.clear();
            root.hashFilters.clear();
            root.plus = null;
        }

        public Set<String> match(String topic) {
            if (topic == null || topic.isEmpty()) {
                return Collections.emptySet();
            }
            String[] levels = topic.split("/", -1);
            Set<String> out = new HashSet<>();
            collect(root, levels, 0, out);
            return out;
        }

        private void collect(TopicNode node, String[] levels, int idx, Set<String> out) {
            // MQTT-4.7.2-1: 根层级的 # 和 + 不匹配 $ 前缀系统主题
            boolean skipRootWildcards = idx == 0 && levels.length > 0 && levels[0].startsWith("$");
            if (!skipRootWildcards && !node.hashFilters.isEmpty()) {
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
            if (!skipRootWildcards) {
                TopicNode plus = node.plus;
                if (plus != null) {
                    collect(plus, levels, idx + 1, out);
                }
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
