package com.fc.v2.service.impl;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.exception.JointSignRuleException;
import com.fc.v2.mapper.auto.TBarrAcceptRecordMapper;
import com.fc.v2.mapper.auto.TBarrArchMapper;
import com.fc.v2.mapper.auto.TSysStreetMapper;
import com.fc.v2.mapper.custom.BarrAcceptSeqDao;
import com.fc.v2.model.auto.TBarrAcceptRecord;
import com.fc.v2.model.auto.TBarrArch;
import com.fc.v2.model.auto.TSysStreet;
import com.fc.v2.model.custom.AcceptRecordForm;
import com.fc.v2.service.ITBarrAcceptRecordService;
import com.fc.v2.util.StringUtils;

/**
 * 当场验收记录（受理簿）Service业务层处理。
 * 一场验收落一行，记录号系统按日排（YS-yyyyMMdd-xxxx）；底册代号/全称/类别/街以底册为准，
 * 与改造进度卡同一套挂街数法。受理簿记的是当场实况，落簿之后不由手记改口。
 *
 * @author fuce
 * @date 2026-10-05
 */
@Service
public class TBarrAcceptRecordServiceImpl implements ITBarrAcceptRecordService {

    @Autowired
    private TBarrAcceptRecordMapper acceptRecordMapper;

    @Autowired
    private TBarrArchMapper barrArchMapper;

    @Autowired
    private BarrAcceptSeqDao barrAcceptSeqDao;

    @Autowired(required = false)
    private TSysStreetMapper sysStreetMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TBarrAcceptRecord register(AcceptRecordForm form, String holder) {
        if (form == null) {
            throw new JointSignRuleException("受理簿这一笔空着，落不下去");
        }
        String groupNo = trim(form.getGroupNo());
        if (StringUtils.isEmpty(groupNo)) {
            throw new JointSignRuleException("楼组片号：空着，不知道验的是哪一场");
        }
        TBarrArch arch = loadArch(form.getArchId());
        int siteCount = parseSiteCount(form.getSiteCount());
        Date acceptTime = parseAcceptTime(form.getAcceptTime());

        String day = new SimpleDateFormat("yyyyMMdd").format(acceptTime);
        String acceptNo = nextAcceptNo(day);

        TBarrAcceptRecord r = new TBarrAcceptRecord();
        r.setAcceptNo(acceptNo);
        r.setGroupNo(groupNo);
        r.setArchId(arch.getId());
        r.setArchNo(arch.getArchNo());
        r.setArchName(arch.getArchName());
        r.setArchKind(arch.getArchKind());
        TSysStreet street = resolveStreet(arch.getRoadName());
        r.setStreetCode(street.getStreetCode());
        r.setStreetName(street.getStreetName());
        r.setSiteCount(siteCount);
        r.setAcceptTime(acceptTime);
        r.setHolder(holder);
        r.setRemark(trim(form.getRemark()));
        this.acceptRecordMapper.insert(r);
        return r;
    }

    @Override
    public TBarrAcceptRecord selectById(Long id) {
        if (id == null) {
            return null;
        }
        return this.acceptRecordMapper.selectById(id);
    }

    @Override
    public TBarrAcceptRecord selectByAcceptNo(String acceptNo) {
        if (StringUtils.isEmpty(acceptNo)) {
            return null;
        }
        return this.acceptRecordMapper.selectOne(new QueryWrapper<TBarrAcceptRecord>()
                .eq("accept_no", acceptNo.trim())
                .last("limit 1"));
    }

    @Override
    public List<TBarrAcceptRecord> selectList(QueryWrapper<TBarrAcceptRecord> wrapper) {
        QueryWrapper<TBarrAcceptRecord> w = wrapper == null ? new QueryWrapper<>() : wrapper;
        w.orderByDesc("accept_time").orderByDesc("create_time");
        return this.acceptRecordMapper.selectList(w);
    }

    // ============================ 内部 ============================

    private TBarrArch loadArch(String archIdText) {
        if (StringUtils.isEmpty(archIdText)) {
            throw new JointSignRuleException("挂在哪一处底册名下：空着，这场验收落不了簿");
        }
        Long archId;
        try {
            archId = Long.valueOf(archIdText.trim());
        } catch (NumberFormatException e) {
            throw new JointSignRuleException("底册代号合不上底册，这场验收落不了簿");
        }
        TBarrArch arch = barrArchMapper.selectOne(new QueryWrapper<TBarrArch>()
                .eq("id", archId).eq("del_flag", 0));
        if (arch == null) {
            throw new JointSignRuleException("底册代号合不上在册底册，这场验收落不了簿");
        }
        if (arch.getStatus() != null && arch.getStatus() == 1) {
            throw new JointSignRuleException("底册「" + arch.getArchNo() + "」已停用，停用底册名下不再当场验收落簿");
        }
        return arch;
    }

    /** 与进度卡同一套挂街数法：属地最后一段是街名，去街道册对代号，对不上不许落簿 */
    private TSysStreet resolveStreet(String roadName) {
        if (StringUtils.isEmpty(roadName)) {
            throw new JointSignRuleException("底册属地没有写到街道，这场验收挂不上街");
        }
        String tail = roadName;
        int idx = Math.max(roadName.lastIndexOf('—'), roadName.lastIndexOf('-'));
        if (idx >= 0 && idx < roadName.length() - 1) {
            tail = roadName.substring(idx + 1);
        }
        tail = tail.trim();
        if (sysStreetMapper == null) {
            throw new JointSignRuleException("底册属地「" + tail + "」在街道册里对不上，这场验收挂不上街");
        }
        List<TSysStreet> list = sysStreetMapper.selectList(new QueryWrapper<TSysStreet>()
                .and(w -> w.isNull("data_state").or().ne("data_state", 1))
                .eq("street_name", tail)
                .last("limit 1"));
        if (list.isEmpty()) {
            throw new JointSignRuleException("底册属地「" + tail + "」在街道册里对不上，这场验收挂不上街");
        }
        return list.get(0);
    }

    private synchronized String nextAcceptNo(String day) {
        barrAcceptSeqDao.ensureDay(day);
        barrAcceptSeqDao.bump(day);
        long seq = barrAcceptSeqDao.currentValue();
        return String.format("YS-%s-%04d", day, seq);
    }

    private int parseSiteCount(String raw) {
        String v = trim(raw);
        if (StringUtils.isEmpty(v)) {
            throw new JointSignRuleException("本场合验户数：空着，一个楼组几户一道改要写清");
        }
        if (!v.matches("^\\d+$") || "0".equals(v)) {
            throw new JointSignRuleException("本场合验户数：得是正整数（" + raw.trim() + "），收不进簿");
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new JointSignRuleException("本场合验户数：数太大，收不进簿");
        }
    }

    private Date parseAcceptTime(String raw) {
        String v = trim(raw);
        if (StringUtils.isEmpty(v)) {
            return new Date();
        }
        try {
            String pattern = v.length() <= 10 ? "yyyy-MM-dd" : "yyyy-MM-dd HH:mm:ss";
            return new SimpleDateFormat(pattern).parse(v);
        } catch (java.text.ParseException e) {
            throw new JointSignRuleException("当场验收时刻：写得不合（" + raw.trim() + "），按 yyyy-MM-dd HH:mm:ss 写");
        }
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }
}
