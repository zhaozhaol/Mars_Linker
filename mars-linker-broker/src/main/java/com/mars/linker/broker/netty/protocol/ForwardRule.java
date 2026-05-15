package com.mars.linker.broker.netty.protocol;

/**
 * 转发规则数据模型，封装匹配类型与匹配表达式。
 * <p>
 * 当前仅支持 {@code regex_match} 匹配类型，expression 为 Java 正则表达式。
 * </p>
 */
public class ForwardRule {

    private String matchType;
    private String expression;

    public ForwardRule() {
    }

    public ForwardRule(String matchType, String expression) {
        this.matchType = matchType;
        this.expression = expression;
    }

    public String getMatchType() {
        return matchType;
    }

    public void setMatchType(String matchType) {
        this.matchType = matchType;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    @Override
    public String toString() {
        return "ForwardRule{matchType='" + matchType + "', expression='" + expression + "'}";
    }
}
