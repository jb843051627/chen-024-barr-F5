package com.fc.v2.model.custom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 联户验收三口怎么算齐——全系统只认这一处给出的裁量。
 * 报文里写下的段号、席位、应签人数到了库里未必作数，一律以本书回的段位、席位与人数为凭；
 * 屏上那个已签数只是照落笔数出来后把本书的数复述一遍，谁都不许伸手填。
 *
 * 三口各管各的，谁也不许替谁：
 * 第一口 一名即过：只等一名，谁先落这一口的笔，结论随这一笔；
 * 第二口 两方先笔定调：等两名（施工方、第三方评估机构各一），先落笔那方怎么说就怎么定，
 *                       后一笔照样累入已签数，两方写了什么都留底；
 * 第三口 两名同判：区残联、市残联都写「可」才算数；一可一不可挂待定，不往下挪也不就此收。
 *
 * @author fuce
 * @date 2026-10-05
 */
public final class JointSignRuleBook {

    /** 第一口算齐办法：一名即过 */
    public static final String RULE_FIRST_PASS = "FIRST_PASS";
    /** 第二口算齐办法：两方先笔定调 */
    public static final String RULE_FIRST_DECIDE = "FIRST_DECIDE";
    /** 第三口算齐办法：两名同判 */
    public static final String RULE_UNANIMOUS = "RULE_UNANIMOUS";

    /** 席位：住户本人或看护人 */
    public static final String SEAT_HOUSEHOLD = "HOUSEHOLD";
    /** 席位：施工方 */
    public static final String SEAT_BUILDER = "BUILDER";
    /** 席位：第三方评估机构 */
    public static final String SEAT_ASSESSOR = "ASSESSOR";
    /** 席位：区残联 */
    public static final String SEAT_DISTRICT = "DISTRICT";
    /** 席位：市残联 */
    public static final String SEAT_CITY = "CITY";

    /** 一口里一个席位 */
    public static final class Seat {
        private final String key;
        private final String name;

        public Seat(String key, String name) {
            this.key = key;
            this.name = name;
        }

        public String getKey() {
            return key;
        }

        public String getName() {
            return name;
        }
    }

    /** 一口的裁量：段号、口名、算齐办法、该到的席位（应签人数即席位数） */
    public static final class Node {
        private final int no;
        private final String name;
        private final String rule;
        private final List<Seat> seats;

        public Node(int no, String name, String rule, List<Seat> seats) {
            this.no = no;
            this.name = name;
            this.rule = rule;
            this.seats = seats;
        }

        public int getNo() {
            return no;
        }

        public String getName() {
            return name;
        }

        public String getRule() {
            return rule;
        }

        public List<Seat> getSeats() {
            return seats;
        }

        /** 这一口该几个人签：书里定死，报文给的不作数 */
        public int getNeedCount() {
            return seats.size();
        }
    }

    private static final List<Node> NODES;

    static {
        List<Node> nodes = new ArrayList<>();
        nodes.add(new Node(0, "第一口·住户本人或看护人", RULE_FIRST_PASS,
                Collections.singletonList(new Seat(SEAT_HOUSEHOLD, "住户本人/看护人"))));
        nodes.add(new Node(1, "第二口·施工方与第三方评估机构", RULE_FIRST_DECIDE,
                java.util.Arrays.asList(
                        new Seat(SEAT_BUILDER, "施工方"),
                        new Seat(SEAT_ASSESSOR, "第三方评估机构"))));
        nodes.add(new Node(2, "第三口·区残联与市残联", RULE_UNANIMOUS,
                java.util.Arrays.asList(
                        new Seat(SEAT_DISTRICT, "区残联"),
                        new Seat(SEAT_CITY, "市残联"))));
        NODES = Collections.unmodifiableList(nodes);
    }

    private JointSignRuleBook() {
    }

    /** 三口，按段号 0..2 排好 */
    public static List<Node> nodes() {
        return NODES;
    }

    /** 头一段段号 */
    public static int firstNode() {
        return 0;
    }

    /** 末一段段号 */
    public static int lastNode() {
        return NODES.size() - 1;
    }

    /** 这一段在书里有没有；没有就是报文瞎指的段号 */
    public static boolean isKnownNode(int nodeNo) {
        return nodeNo >= 0 && nodeNo < NODES.size();
    }

    /** 照书取回这一段的裁量，段号越界抛规矩话（不悄悄当哪一段接住） */
    public static Node node(int nodeNo) {
        if (!isKnownNode(nodeNo)) {
            throw new IllegalArgumentException("段号 " + nodeNo + " 在三口裁量书里查无此口");
        }
        return NODES.get(nodeNo);
    }

    /** 这一口该几个人签 */
    public static int needCount(int nodeNo) {
        return node(nodeNo).getNeedCount();
    }

    /** 席位是不是这一口书里的席位；报文自拟的席位一概不认 */
    public static Seat seatOf(int nodeNo, String seatKey) {
        if (seatKey == null) {
            return null;
        }
        for (Seat seat : node(nodeNo).getSeats()) {
            if (seat.getKey().equals(seatKey.trim())) {
                return seat;
            }
        }
        return null;
    }
}
