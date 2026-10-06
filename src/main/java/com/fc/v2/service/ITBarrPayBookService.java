package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.model.auto.TBarrPayBook;
import com.fc.v2.model.auto.TBarrPayRow;
import com.fc.v2.model.custom.PayBookAudit;
import com.fc.v2.model.custom.PayBookForm;
import com.fc.v2.model.custom.PayBookReceipt;

/**
 * 补助核销册一道门 Service接口。
 *
 * 册子进门只此一道手续：册头先点行数，随后逐行判收判退，末了把回执那三个数
 * 一并轧出来，中途不过第二处的手。单行既没有自己落册的路，也没有自己改去向的路，
 * 哪儿都不留这种暗门；「同一个册码再来一遍怎么办」这句话只在这一处有答案。
 * 原先收册那一套（{@link ITBarrPayRowService}）原地不动，不往它身上多加一条规矩。
 *
 * @author fuce
 * @date 2026-10-06
 */
public interface ITBarrPayBookService {

    /**
     * 整册进门：一册一次递齐，不接逐行零着递。
     * 头一步点行数，压过上限整册退回、库里不许留半行；同一个册码第二遍递来只照面，
     * 不添一行、不减一行。逐行判收判退各走各的，末了回执三数回库一行一行点出来。
     */
    PayBookReceipt enterBook(PayBookForm form, String operator);

    /** 回执翻看：三个数只认回库现点出来的；没落成的行带纸面行次出门 */
    PayBookReceipt receiptOf(String bookNo);

    /** 对账：行上去向与回执三数彼此对上，合不上的那几行点名报出来 */
    PayBookAudit audit(String bookNo);

    /**
     * 撤册手续：确实要动先把这道手续办了，办完同册码才许重新递。
     * 撤的那一轮册头置已撤、行置删留底（不真抽走，照旧翻得着）。
     */
    String withdrawBook(String bookNo, String operator);

    /**
     * 挪往期：去向一直空悬的未勾行，连着两个拨付年度还没着落就挪去往期那一层。
     * 只朝旧册开，往后的新册走不到它跟前。yearNow/at 空着按当下接住（便于年挪与手动同一口子）。
     */
    int movePastDue(Integer yearNow, Date at);

    /** 翻册：在册那一轮的全部行，按原报行次排（行次排定不再挪） */
    List<TBarrPayRow> listBookRows(String bookNo);

    /** 现行空悬名单：未勾、在册、还没挪往期的行 */
    List<TBarrPayRow> listHangingCurrent();

    /** 往期层：照旧翻得着、一条不少；册码空着=全层都翻 */
    List<TBarrPayRow> listPast(String bookNo);

    /** 册头集合只读翻看（在册/已撤都在；写册只许走进门那一道） */
    List<TBarrPayBook> selectBookList(Wrapper<TBarrPayBook> wrapper);
}
