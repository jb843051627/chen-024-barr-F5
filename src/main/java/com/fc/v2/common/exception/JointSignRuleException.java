package com.fc.v2.common.exception;

/**
 * 联户验收签认业务规则异常。
 * 被打回、越口落签、签讫后再动，话都要讲清落在哪一口、为什么这一笔不算。
 *
 * @author fuce
 * @date 2026-10-05
 */
public class JointSignRuleException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public JointSignRuleException(String message) {
        super(message);
    }
}
