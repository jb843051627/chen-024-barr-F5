package com.fc.v2.common.exception;

/**
 * 补助核销册业务规则异常。
 * 册码空着、查无此册、已撤又撤——话都要讲清卡在哪一道、为什么这一趟不算。
 *
 * @author fuce
 * @date 2026-10-06
 */
public class PayBookRuleException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PayBookRuleException(String message) {
        super(message);
    }
}
