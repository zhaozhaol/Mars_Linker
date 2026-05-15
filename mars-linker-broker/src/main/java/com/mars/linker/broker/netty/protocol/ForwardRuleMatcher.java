package com.mars.linker.broker.netty.protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 转发规则匹配引擎。
 * <p>
 * <b>核心特性</b>：
 * <ul>
 *   <li>正则表达式编译结果缓存复用，避免每次匹配重复编译</li>
 *   <li>多规则"或"逻辑 + 短路求值：任意一条匹配即返回 true</li>
 *   <li>规则为空时所有设备均匹配（全量转发，向后兼容）</li>
 *   <li>匹配超时保护：防止 ReDoS 攻击</li>
 * </ul>
 * </p>
 */
public final class ForwardRuleMatcher {

    private static final Logger log = LoggerFactory.getLogger(ForwardRuleMatcher.class);

    private static final long MATCH_TIMEOUT_MS = 1;

    private final List<Pattern> compiledPatterns;

    public ForwardRuleMatcher(List<ForwardRule> rules) {
        if (rules == null || rules.isEmpty()) {
            this.compiledPatterns = Collections.emptyList();
            return;
        }
        List<Pattern> patterns = new ArrayList<>(rules.size());
        for (int i = 0; i < rules.size(); i++) {
            ForwardRule rule = rules.get(i);
            if (rule.getExpression() == null || rule.getExpression().isEmpty()) {
                throw new PatternSyntaxException(
                        "lifecycle-forward-rules[" + i + "].expression 为空", "", -1);
            }
            Pattern pattern = Pattern.compile(rule.getExpression());
            patterns.add(pattern);
        }
        this.compiledPatterns = Collections.unmodifiableList(patterns);
    }

    /**
     * 对 clientId 执行规则匹配。
     *
     * @param clientId 设备客户端标识
     * @return true 表示匹配（应转发），false 表示不匹配（不转发）
     */
    public boolean matches(String clientId) {
        if (compiledPatterns.isEmpty()) {
            return true;
        }
        if (clientId == null) {
            return false;
        }
        long start = System.nanoTime();
        for (Pattern pattern : compiledPatterns) {
            try {
                if (pattern.matcher(clientId).find()) {
                    return true;
                }
            } catch (Exception e) {
                log.warn("规则匹配异常 pattern={} clientId={} error={}", pattern, clientId, e.getMessage());
            }
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            if (elapsed > MATCH_TIMEOUT_MS) {
                log.warn("规则匹配超时 elapsedMs={} clientId={}，终止匹配", elapsed, clientId);
                return false;
            }
        }
        return false;
    }

    public boolean hasRules() {
        return !compiledPatterns.isEmpty();
    }
}
