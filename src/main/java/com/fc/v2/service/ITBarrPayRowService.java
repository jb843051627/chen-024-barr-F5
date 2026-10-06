package com.fc.v2.service;

import java.util.List;

import com.fc.v2.model.auto.TBarrPayBatch;
import com.fc.v2.model.auto.TBarrPayRow;
import com.fc.v2.model.custom.PayBookAudit;
import com.fc.v2.model.custom.PayBookForm;
import com.fc.v2.model.custom.PayBookReceipt;

/**
 * 补助核销册 Service接口（整册进门形状：一册一次递齐，无单行增删改）。
 *
 * 册子进门只此一道手续：{@link #intakeBook} 册头先点行数，随后逐行判收判退，
 * 末了把回执那三个数一并轧出来，中途不过第二处的手。「同一个册码再来一遍怎么办」
 * 这句话也只在这一处有答案（照面而不翻账）；单行既没有自己落册的路，
 * 也没有自己改去向的路，哪儿都不留这种暗门。
 *
 * @author fuce
 * @date 2026-10-06
 */
public interface ITBarrPayRowService {

    /** 按主键回查单行（回头任指一行） */
    TBarrPayRow selectTBarrPayRowById(Long id);

    /**
     * 进门一道手续：点行数 → 压过单册上限整册退回（库里不留半行） →
     * 同一册码已落过只照面（不添一行、不减一行，头一遍落成的去向盖不掉） →
     * 新册逐行判收判退落库 → 回库数出回执三数 → 册头落核讫／空册。
     * 区里自录与街道转递两条来路都进这一道门，第二遍不会在别处另起一册。
     */
    PayBookReceipt intakeBook(PayBookForm form, String operator);

    /**
     * 撤册：确实要动先把这道手续办了，办完再重新递。
     * 册头与全册行撤下留底（del_flag=1），活册活行占位让位；查无在册的册不许撤。
     */
    void withdrawBook(String batchNo, String operator);

    /** 回执：三个数回库一行一行数出来，没落成的行带纸面行次；查无此册不给回执 */
    PayBookReceipt receiptOf(String batchNo);

    /** 对账：任指一行，行上去向与回执三数彼此对得上；合不上的行点名报出来 */
    PayBookAudit auditBook(String batchNo);

    /** 册台账：在册的册（已撤留底的不列） */
    List<TBarrPayBatch> listBooks();

    /** 现行名单：在册且没挪往期的行，按原报行次摆开 */
    List<TBarrPayRow> listCurrentRows(String batchNo);

    /** 往期层：挪了往期的行照旧翻得着、一条不少 */
    List<TBarrPayRow> listPastRows(String batchNo);

    /**
     * 挪往期：去向一直空悬（未勾）的行不许长压——连着两个拨付年度还没着落，
     * 就挪去往期那一层，现行名单不再列它。这条路只朝旧册开，往后的新册走不到它跟前。
     * 回挪了几行。
     */
    int moveStaleToPast(int currentPayYear, String operator);
}
