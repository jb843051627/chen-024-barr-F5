-- barr 无障碍环境建设与适老化改造补助监督 -- schema (chen-024)
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：chen_024

CREATE TABLE IF NOT EXISTS t_barr_arch (
  id bigint NOT NULL COMMENT '主键',
  arch_no varchar(64) DEFAULT NULL COMMENT '改造点位底册代号',
  arch_name varchar(128) DEFAULT NULL COMMENT '底册全称',
  arch_kind varchar(32) DEFAULT NULL COMMENT '底册类别',
  road_name varchar(128) DEFAULT NULL COMMENT '属地那一路(省—市—区—街道)',
  status int DEFAULT NULL COMMENT '底册情形 0在册 1已停用',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='改造点位底册';

CREATE TABLE IF NOT EXISTS t_barr_case_flow (
  id bigint NOT NULL COMMENT '主键',
  biz_no varchar(64) DEFAULT NULL COMMENT '一户一档改造卷宗号',
  stage int DEFAULT NULL COMMENT '当前格次 0..3（核卷/评估/验收/结清）',
  status int DEFAULT NULL COMMENT '卷宗走到哪一格 0未起 1在办 2已封住',
  content varchar(255) DEFAULT NULL COMMENT '一格一记',
  last_action varchar(64) DEFAULT NULL COMMENT '最近一次挪格动作',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='一户一档改造卷宗';

CREATE TABLE IF NOT EXISTS t_barr_pay_row (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '月度补助核销册码',
  row_no int DEFAULT NULL COMMENT '原报行次（进门落库时系统按落库先后排上，排定不再挪；送回的行次日再翻还是原行次）',
  item_code varchar(64) DEFAULT NULL COMMENT '被对上的改造进度卡代号（认户只认代号全等；拿户名对、代号念不全，一律按没对上办）',
  qty decimal(12,2) DEFAULT NULL COMMENT '本行请领补助额',
  quali_num decimal(12,2) DEFAULT NULL COMMENT '本行验收合格处数',
  voucher_img varchar(255) DEFAULT NULL COMMENT '验收凭证影像（没随册来的行按没备齐办：送回，缘故写缺影像）',
  return_reason varchar(255) DEFAULT NULL COMMENT '原路送回的缘故（写在行上，随回执带出给街道对号）',
  past_flag int DEFAULT '0' COMMENT '层级 0现行 1往期（连着两个拨付年度未勾的挪入；新册的行永不到这层）',
  pay_kind varchar(32) DEFAULT NULL COMMENT '拨付口径（区级／市级两条来路，随册落行）',
  status int DEFAULT NULL COMMENT '行落地情形 0未勾 1已收下 2已送回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除（撤册的行留底为1，不再占活位）',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  live_row varchar(160) GENERATED ALWAYS AS (CASE WHEN COALESCE(del_flag,0) = 0 THEN concat(batch_no, '#', row_no) ELSE NULL END) STORED COMMENT '活行占位：同册同次只容一行在册；撤册行让位，重递再排',
  PRIMARY KEY (id),
  UNIQUE KEY uk_live_row (live_row)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='补助核销行';

-- 月度补助核销册头：一册一行。册头与逐行说的是同一批行，两处联动；
-- 册头自报行数与实际递来不合时，以实际落库数为准（口径定死），自报数原样留底、对不上点名。
CREATE TABLE IF NOT EXISTS t_barr_pay_batch (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) NOT NULL COMMENT '月度补助核销册码（一册一码；同一册码第二遍只照面不翻账）',
  street_code varchar(40) DEFAULT NULL COMMENT '来册街道代号（街道转递带来的；区里自录可空）',
  street_name varchar(64) DEFAULT NULL COMMENT '街名（随册冗余）',
  pay_kind varchar(32) DEFAULT NULL COMMENT '拨付口径／来路（区级自录／市级转递，两条来路进同一道门）',
  pay_year int DEFAULT NULL COMMENT '拨付年度（挪往期的尺子；空着按进门那年落）',
  declared_rows int DEFAULT NULL COMMENT '册头自报行数（原样留底；与实际落库不合以实际为准，对不上点名）',
  status int DEFAULT NULL COMMENT '册情形 1已核讫 2空册 9已撤册（核讫只给有行落成的册；空册不出核讫）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除（撤册留底为1，不再占活位）',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  live_batch varchar(64) GENERATED ALWAYS AS (CASE WHEN COALESCE(del_flag,0) = 0 THEN batch_no ELSE NULL END) STORED COMMENT '活册占位：同一册码只容一册在册；撤册让位，重递再立',
  PRIMARY KEY (id),
  UNIQUE KEY uk_live_batch (live_batch)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='月度补助核销册头';

-- 当地那条封顶线：一条街一行。本行请领额高过这条线的，行原路送回；
-- 街上没划线=这条街还没划，进门不拿它卡行。
CREATE TABLE IF NOT EXISTS t_barr_cap_line (
  id bigint NOT NULL COMMENT '主键',
  street_code varchar(40) NOT NULL COMMENT '这条封顶线管哪条街',
  cap_amount decimal(12,2) NOT NULL COMMENT '单行请领额封顶线（高过线原路送回）',
  status int DEFAULT '0' COMMENT '线的情形 0在位 1已卸下',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_cap_street (street_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='单行请领封顶线';

CREATE TABLE IF NOT EXISTS t_barr_sign_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '联户验收签认单',
  node_no int DEFAULT NULL COMMENT '当前所在口 0..2（住户/到场两方/两级残联）',
  sign_mode int DEFAULT NULL COMMENT '本口算齐办法 0一名即签 1两名须同判',
  need_count int DEFAULT NULL COMMENT '本口应签人数',
  sign_count int DEFAULT NULL COMMENT '本口已签人数',
  status int DEFAULT NULL COMMENT '签认情形 0在签 1已点齐 2已压回',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='联户验收签认单';

CREATE TABLE IF NOT EXISTS t_barr_site_card (
  id bigint NOT NULL COMMENT '主键',
  card_no varchar(64) DEFAULT NULL COMMENT '改造进度卡代号（只由系统排号，本号不认）',
  arch_id bigint DEFAULT NULL COMMENT '挂在哪一处底册名下',
  arch_no varchar(64) DEFAULT NULL COMMENT '底册代号（以底册为准，随卡冗余）',
  arch_name varchar(128) DEFAULT NULL COMMENT '底册全称（以底册为准，随卡冗余；改卡不改底）',
  arch_kind varchar(32) DEFAULT NULL COMMENT '底册类别（随卡冗余，以底册为准）',
  street_code varchar(40) DEFAULT NULL COMMENT '卡所在街代号（由底册属地落到街道）',
  street_name varchar(64) DEFAULT NULL COMMENT '卡所在街名（随卡冗余）',
  season varchar(8) DEFAULT NULL COMMENT '本卡所属季（如 2026Q4，立卡时定死）',
  site_no varchar(64) DEFAULT NULL COMMENT '点位或住户代号',
  need_num int DEFAULT NULL COMMENT '应改处数',
  done_num int DEFAULT NULL COMMENT '已改处数',
  lack_num int DEFAULT NULL COMMENT '还差几处（轧出来，不手填：need-done）',
  content varchar(255) DEFAULT NULL COMMENT '随卡交来的改造要件',
  source int DEFAULT 0 COMMENT '立卡来路 0亲手立 1当月验收带出（两条入口同一套数法）',
  stage int DEFAULT 0 COMMENT '已走完的级数 0新录 1已改齐(缺项) 2收口（旧卡空值按0接住）',
  status int DEFAULT 0 COMMENT '进展档 0新录 1已改齐 2缺项（按级数与处数由系统数出，不许手填）',
  closed int DEFAULT 0 COMMENT '是否收口 0在走 1收口到头（旧卡空值按在走接住）',
  verify_code varchar(64) DEFAULT NULL COMMENT '核验串（系统落；旧卡头一回被流程接住时补）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '谁立的（递卡人登录身份自动落，不接手填）',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  active_site varchar(64) GENERATED ALWAYS AS (CASE WHEN COALESCE(closed,0) = 0 AND del_flag = 0 THEN site_no ELSE NULL END) STORED COMMENT '活卡占位：同点位同时只容一张在走卡',
  PRIMARY KEY (id),
  UNIQUE KEY uk_card_no (card_no),
  UNIQUE KEY uk_site_active (active_site),
  KEY idx_street_season (street_code, season),
  KEY idx_arch_kind (arch_kind)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='改造进度卡';

-- 卡代号系统取号（一季一排，本子自带号一概不认）
CREATE TABLE IF NOT EXISTS t_barr_card_seq (
  season varchar(8) NOT NULL COMMENT '季码 如 2026Q4',
  next_val int NOT NULL DEFAULT 0 COMMENT '下一发号值',
  PRIMARY KEY (season)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='改造进度卡取号表';

-- 卡流转留痕（推进/回退/收口一记一行；回退旧笔留底，不与后补之笔并排）
CREATE TABLE IF NOT EXISTS t_barr_card_log (
  id bigint NOT NULL COMMENT '主键',
  card_id bigint NOT NULL COMMENT '改造进度卡主键',
  card_no varchar(64) DEFAULT NULL COMMENT '当时卡代号',
  action varchar(32) NOT NULL COMMENT '动作 create/advance/revert/close/edit',
  from_stage int DEFAULT NULL COMMENT '动前级数',
  to_stage int DEFAULT NULL COMMENT '动后级数',
  step_note varchar(255) DEFAULT NULL COMMENT '这一步落下的一笔（一格一记）',
  operator varchar(64) DEFAULT NULL COMMENT '经办人（登录身份）',
  create_time datetime DEFAULT NULL COMMENT '动作时间',
  PRIMARY KEY (id),
  KEY idx_card_id (card_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='改造进度卡流转留痕';

-- 用户可见街道授权：街道岗一行；部岗可多行（只看得见划给它的那几条街）
CREATE TABLE IF NOT EXISTS t_barr_user_street (
  id bigint NOT NULL COMMENT '主键',
  user_id bigint NOT NULL COMMENT '登录用户',
  street_code varchar(40) NOT NULL COMMENT '授权可见街道代号',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_street (user_id, street_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户街道可见范围';

CREATE TABLE IF NOT EXISTS t_barr_tier_line (
  id bigint NOT NULL COMMENT '主键',
  rule_code varchar(64) DEFAULT NULL COMMENT '入户评估定档线代号',
  rule_name varchar(128) DEFAULT NULL COMMENT '线名',
  scene_kind varchar(32) DEFAULT NULL COMMENT '这条线管公共点位还是居家适老',
  th1_max decimal(12,3) DEFAULT NULL COMMENT '起算分',
  th2_max decimal(12,3) DEFAULT NULL COMMENT '够线分',
  th3_max decimal(12,3) DEFAULT NULL COMMENT '封顶分',
  eff_start datetime DEFAULT NULL COMMENT '起用那一日',
  eff_end datetime DEFAULT NULL COMMENT '交班那一日(不含)',
  priority int DEFAULT NULL COMMENT '交班次序(数值大的先说话)',
  status int DEFAULT NULL COMMENT '线的情形 0在位 1已卸下',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='入户评估定档线';

CREATE TABLE IF NOT EXISTS t_barr_visit_item (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '质保与养护复检条目代号（一行顶要紧的头一样；没落号当场挂递不动）',
  due_at datetime DEFAULT NULL COMMENT '该上门那一日的止点时刻（空着=行站不住）',
  amount decimal(12,2) DEFAULT NULL COMMENT '最多许提前几日（只按成事日数整日，零头丢弃；空/负/逾千日均属站不住）',
  content varchar(255) DEFAULT NULL COMMENT '事由与所对住户的记要',
  street_code varchar(40) DEFAULT NULL COMMENT '条目所在街代号（同街同楼组并成一段，一趟走完）',
  street_name varchar(64) DEFAULT NULL COMMENT '街名（随条冗余）',
  group_no varchar(64) DEFAULT NULL COMMENT '楼组片号（同街同号并一段）',
  arch_id bigint DEFAULT NULL COMMENT '所对住户的底册（t_barr_arch.id；底册已停用/撤下即住户撤走，本桩递不动）',
  owner_id bigint DEFAULT NULL COMMENT '摊到谁名下（经办用户id；定额按人按季卡）',
  owner_name varchar(64) DEFAULT NULL COMMENT '经办登录名（随条冗余，递出时照抄落印）',
  contact varchar(128) DEFAULT NULL COMMENT '联络栏（空着且不是本季新立的，天生办不成，直接挂递不动）',
  season varchar(8) DEFAULT NULL COMMENT '本条目所属季（如 2026Q4，录入时定死）',
  status int DEFAULT NULL COMMENT '条目情形 0搁着 1已递出 2递不动（只此三格，落不进第四种）',
  delivered_at datetime DEFAULT NULL COMMENT '递出那一刻（与状态同回；缺了不算走完）',
  delivered_by varchar(64) DEFAULT NULL COMMENT '递出经办那位（与状态同回；缺了不算走完）',
  stall_reason varchar(255) DEFAULT NULL COMMENT '卡壳缘故（挂递不动时连环节一起写回，日后照着捞出重排）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  KEY idx_visit_status_due (status, due_at),
  KEY idx_visit_owner_season (owner_id, season),
  KEY idx_visit_segment (street_code, group_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='质保与养护复检条目';

-- 法定节假日（只数真办得成事的日子：这张册上的一天都不数）
CREATE TABLE IF NOT EXISTS t_barr_holiday (
  day date NOT NULL COMMENT '放假日',
  day_name varchar(64) DEFAULT NULL COMMENT '假日名',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='法定节假日册';

-- 区里下文停办日（与节假日一样不数；停办下文一回录一行）
CREATE TABLE IF NOT EXISTS t_barr_closeday (
  day date NOT NULL COMMENT '停办那一日',
  reason varchar(255) DEFAULT NULL COMMENT '停办公告缘故',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='区下文停办日册';

-- 经办人季度定额（没有行=这季不卡；装满后摊到名下的桩当场挂递不动，缘故写回）
CREATE TABLE IF NOT EXISTS t_barr_owner_quota (
  id bigint NOT NULL COMMENT '主键',
  owner_id bigint NOT NULL COMMENT '经办用户id',
  season varchar(8) NOT NULL COMMENT '定额所属季 如 2026Q4',
  quota int NOT NULL COMMENT '本季最多递出几条',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_owner_season (owner_id, season)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='经办人季度定额';

-- 初始底册档案数据：在册一条（DC00·小区楼组类）、已停用一条（DC01·公共点位类）
INSERT IGNORE INTO t_barr_arch (id, arch_no, arch_name, arch_kind, road_name, status, del_flag, create_by, create_time)
VALUES (0, 'DC00', '浈县区白沙街道·祥和楼组片区（小区楼组类，在册）', '小区楼组', '岫岭省—柏州市—浈县区—白沙街道', 0, 0, 'seed', NOW()),
       (1, 'DC01', '梅坂区藕塘街道·沿河南岸公共通道（已并走停用）', '公共点位', '岫岭省—柏州市—梅坂区—藕塘街道', 1, 0, 'seed', NOW());

-- 两条街种子（底册属地最后一段与街名对齐，卡挂街由此落）
INSERT IGNORE INTO t_sys_street (id, street_code, area_code, street_name, short_name, sort, data_state, create_by, create_time)
VALUES (8024001001, 'BSJD', 'ZXQ', '白沙街道', '白沙', 1, 0, 'seed', NOW()),
       (8024001002, 'OTJD', 'MBQ', '藕塘街道', '藕塘', 2, 0, 'seed', NOW());

-- 菜单与按钮权限（挂在系统管理目录 411522822607867904 下）
INSERT IGNORE INTO t_sys_permission (id, name, descripion, url, is_blank, pid, perms, type, icon, order_num, visible, create_by, create_time)
VALUES (8024001101, '改造进度卡', '改造进度卡展示', '/BarrSiteCardController/view', 0, 411522822607867904, 'barr:barrSiteCard:view', 1, 'fa fa-clipboard', 90, 0, 'seed', NOW()),
       (8024001102, '改造进度卡集合', '改造进度卡集合', '/BarrSiteCardController/list', 0, 8024001101, 'barr:barrSiteCard:list', 2, NULL, NULL, 0, 'seed', NOW()),
       (8024001103, '改造进度卡立卡', '一处一张亲手立/验收带出', '/BarrSiteCardController/add', 0, 8024001101, 'barr:barrSiteCard:add', 2, NULL, NULL, 0, 'seed', NOW()),
       (8024001104, '改造进度卡改口', '改造进度卡改口编辑', '/BarrSiteCardController/edit', 0, 8024001101, 'barr:barrSiteCard:edit', 2, NULL, NULL, 0, 'seed', NOW()),
       (8024001105, '改造进度卡删除', '改造进度卡删除', '/BarrSiteCardController/remove', 0, 8024001101, 'barr:barrSiteCard:remove', 2, NULL, NULL, 0, 'seed', NOW()),
       (8024001106, '改造进度卡推进', '走完一级', '/BarrSiteCardController/advance', 0, 8024001101, 'barr:barrSiteCard:advance', 2, NULL, NULL, 0, 'seed', NOW()),
       (8024001107, '改造进度卡回退', '已改齐退回一步重来', '/BarrSiteCardController/revert', 0, 8024001101, 'barr:barrSiteCard:revert', 2, NULL, NULL, 0, 'seed', NOW()),
       (8024001108, '改造进度卡收口', '走到收口到头', '/BarrSiteCardController/close', 0, 8024001101, 'barr:barrSiteCard:close', 2, NULL, NULL, 0, 'seed', NOW());

-- 街道岗 / 部岗：两岗都能看卡立卡改口；删除、推进、回退、收口归部岗
INSERT IGNORE INTO t_sys_role (id, name) VALUES (8024001201, '无障碍街道岗'), (8024001202, '无障碍部岗');
INSERT IGNORE INTO t_sys_permission_role (id, role_id, permission_id)
VALUES (8024001301, 8024001201, 8024001101),
       (8024001302, 8024001201, 8024001102),
       (8024001303, 8024001201, 8024001103),
       (8024001304, 8024001201, 8024001104),
       (8024001401, 8024001202, 8024001101),
       (8024001402, 8024001202, 8024001102),
       (8024001403, 8024001202, 8024001103),
       (8024001404, 8024001202, 8024001104),
       (8024001405, 8024001202, 8024001105),
       (8024001406, 8024001202, 8024001106),
       (8024001407, 8024001202, 8024001107),
       (8024001408, 8024001202, 8024001108);

-- 管理员（id=1）给全部按钮权限，并兼部岗、两条街都看得见
INSERT IGNORE INTO t_sys_permission_role (id, role_id, permission_id)
SELECT 8024001500 + n, 488243256161730560, 8024001101 + n
FROM (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7) t;
INSERT IGNORE INTO t_sys_role_user (id, sys_user_id, sys_role_id) VALUES (8024001601, 1, 8024001202);
INSERT IGNORE INTO t_barr_user_street (id, user_id, street_code, create_by, create_time)
VALUES (8024001701, 1, 'BSJD', 'seed', NOW()),
       (8024001702, 1, 'OTJD', 'seed', NOW());

-- 复检条目：菜单与按钮权限（挂在系统管理目录 411522822607867904 下）
INSERT IGNORE INTO t_sys_permission (id, name, descripion, url, is_blank, pid, perms, type, icon, order_num, visible, create_by, create_time)
VALUES (8024001109, '质保养护复检', '质保与养护复检条目展示', '/BarrVisitItemController/view', 0, 411522822607867904, 'barr:barrVisitItem:view', 1, 'fa fa-history', 91, 0, 'seed', NOW()),
       (8024001110, '复检条目集合', '复检条目集合', '/BarrVisitItemController/list', 0, 8024001109, 'barr:barrVisitItem:list', 2, NULL, NULL, 0, 'seed', NOW()),
       (8024001111, '复检手动开跑', '窗口外只回话不动库', '/BarrVisitItemController/run', 0, 8024001109, 'barr:barrVisitItem:run', 2, NULL, NULL, 0, 'seed', NOW());

-- 街道岗 / 部岗都看得见复检册，都按得动手动开跑（窗口外按只听见回话）
INSERT IGNORE INTO t_sys_permission_role (id, role_id, permission_id)
VALUES (8024001311, 8024001201, 8024001109),
       (8024001312, 8024001201, 8024001110),
       (8024001313, 8024001201, 8024001111),
       (8024001411, 8024001202, 8024001109),
       (8024001412, 8024001202, 8024001110),
       (8024001413, 8024001202, 8024001111);

-- 管理员（id=1）给复检三项权限
INSERT IGNORE INTO t_sys_permission_role (id, role_id, permission_id)
SELECT 8024001500 + n, 488243256161730560, 8024001101 + n
FROM (SELECT 8 n UNION SELECT 9 UNION SELECT 10) t;

-- 每日一趟：凌晨 2:00 开跑（窗口 02:00 起到 05:00 前止；禁并发，错点不补跑）
INSERT IGNORE INTO t_sys_quartz_job (id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
VALUES (8024001801, '质保养护复检日跑', 'BARR', 'visitItemTask.runDaily()', '0 0 2 * * ?', '2', '1', 0, 'seed', NOW(), '窗口 02:00-05:00；窗口外只回话，库里一格不动');

-- ============================ 联户验收（会签版） ============================
-- 当场验收记录（当日受理簿）：一场验收一行。联户验收签认单只能由这里关联带出，
-- 别处另起手建的一张不算；记录上已有的字段，单上直接沿用（随单快照）。
CREATE TABLE IF NOT EXISTS t_barr_accept_record (
  id bigint NOT NULL COMMENT '主键',
  accept_no varchar(64) DEFAULT NULL COMMENT '当场验收记录号（受理簿上那一笔，一场一号）',
  group_no varchar(64) DEFAULT NULL COMMENT '楼组片号（一个楼组几户一道改、一场验收）',
  arch_id bigint DEFAULT NULL COMMENT '挂在哪一处底册名下',
  arch_no varchar(64) DEFAULT NULL COMMENT '底册代号（以底册为准，随记录冗余）',
  arch_name varchar(128) DEFAULT NULL COMMENT '底册全称（以底册为准，随记录冗余）',
  arch_kind varchar(32) DEFAULT NULL COMMENT '底册类别（随记录冗余）',
  street_code varchar(40) DEFAULT NULL COMMENT '验收所在街代号（由底册属地落到街道）',
  street_name varchar(64) DEFAULT NULL COMMENT '街名（随记录冗余）',
  site_count int DEFAULT NULL COMMENT '本场合验户数（楼组里一道改的几户）',
  accept_time datetime DEFAULT NULL COMMENT '当场验收那一刻',
  holder varchar(64) DEFAULT NULL COMMENT '当场受理簿落笔者（登录身份）',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_accept_no (accept_no),
  KEY idx_accept_street (street_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='当场验收记录（受理簿）';

-- 联户验收签认单（会签版）：只由验收记录关联带出，同一场验收只带得出一张（uk_accept_id）。
-- 单停口序/轮次只由系统在落签这一处按落笔实况挪，报文里的段号一概不作数；
-- 没有删除标记：签讫一笔进不来、一字改不了、整张也抽不走，在签的也不许另起一张顶替。
CREATE TABLE IF NOT EXISTS t_barr_joint_sign_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '联户验收签认单号（由验收记录号带出，一场一号）',
  accept_id bigint NOT NULL COMMENT '只由哪份当场验收记录带出（来路唯一，不许另起手建）',
  accept_no varchar(64) DEFAULT NULL COMMENT '验收记录号（记录上已有，单上直接沿用）',
  group_no varchar(64) DEFAULT NULL COMMENT '楼组片号（沿自验收记录）',
  arch_id bigint DEFAULT NULL COMMENT '底册id（沿自验收记录）',
  arch_no varchar(64) DEFAULT NULL COMMENT '底册代号（沿自验收记录）',
  arch_name varchar(128) DEFAULT NULL COMMENT '底册全称（沿自验收记录）',
  arch_kind varchar(32) DEFAULT NULL COMMENT '底册类别（沿自验收记录）',
  street_code varchar(40) DEFAULT NULL COMMENT '街代号（沿自验收记录）',
  street_name varchar(64) DEFAULT NULL COMMENT '街名（沿自验收记录）',
  site_count int DEFAULT NULL COMMENT '合验户数（沿自验收记录）',
  accept_time datetime DEFAULT NULL COMMENT '当场验收那一刻（沿自验收记录）',
  holder varchar(64) DEFAULT NULL COMMENT '当场受理簿落笔者（沿自验收记录）',
  node_no int DEFAULT 0 COMMENT '单停在第几段 0住户 1到场两方 2两级残联（系统按落笔挪，报文段号不作数）',
  round_no int DEFAULT 1 COMMENT '本单走到第几轮（打回一轮旧笔作废留底，新笔另起一轮，两轮分开放）',
  status int DEFAULT 0 COMMENT '单子情形 0在签 1已签讫（打回不是终局，退一格重走仍在签）',
  finished_time datetime DEFAULT NULL COMMENT '签讫那一刻（末口同判点齐才落）',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id),
  UNIQUE KEY uk_joint_bill_no (bill_no),
  UNIQUE KEY uk_joint_accept (accept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='联户验收签认单（会签版）';

-- 签认落笔留痕：一笔一行，只许追加，谁都不许改行删行。
-- 三口每笔落在哪一口由库（系统）钉死；已签数不入库上账，永远照这张表一口一口数出来回显。
-- 打回只把本口本轮已落的笔置作废（valid_flag=0/reject），更早各口旧签一笔不动；
-- 两级残联同席改口另落新笔，旧笔标 supersede，新旧各有凭据；并发票据各按 line_seq 记先后。
CREATE TABLE IF NOT EXISTS t_barr_joint_sign_line (
  id bigint NOT NULL COMMENT '主键',
  bill_id bigint NOT NULL COMMENT '落在哪张联户签认单上',
  accept_no varchar(64) DEFAULT NULL COMMENT '验收记录号（随笔冗余，受理簿对笔用）',
  node_no int NOT NULL COMMENT '落在哪一口 0..2（系统钉死，报文段号不作数）',
  round_no int NOT NULL COMMENT '第几轮落的（打回新旧两轮分开放）',
  seat_key varchar(32) NOT NULL COMMENT '席位 HOUSEHOLD/BUILDER/ASSESSOR/DISTRICT/CITY（规则书回的，报文不许自拟）',
  seat_name varchar(64) DEFAULT NULL COMMENT '席位名（规则书回的，不由报文定）',
  signer varchar(64) NOT NULL COMMENT '落笔人（当场签字那位）',
  verdict varchar(8) NOT NULL COMMENT '落笔结论 可/不可',
  line_seq int NOT NULL COMMENT '本口本轮落笔次第（并发各记各的先后，不许并成一句）',
  valid_flag int NOT NULL DEFAULT 1 COMMENT '还算不算数 1算数 0已作废（打回抹去/同席改口）',
  void_reason varchar(32) DEFAULT NULL COMMENT '作废缘故 reject本口打回 / supersede同席改口',
  operator varchar(64) DEFAULT NULL COMMENT '录笔经办（登录身份）',
  note varchar(255) DEFAULT NULL COMMENT '这一笔附言',
  create_time datetime DEFAULT NULL COMMENT '落笔时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_joint_line_seq (bill_id, node_no, round_no, line_seq),
  KEY idx_joint_line_bill (bill_id),
  KEY idx_joint_line_count (bill_id, node_no, round_no, valid_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='联户签认落笔留痕';

-- 受理簿记录号按日取号（YS-yyyyMMdd-xxxx）：本子自带号一概不认
CREATE TABLE IF NOT EXISTS t_barr_accept_seq (
  day varchar(8) NOT NULL COMMENT '日码 yyyyMMdd',
  next_val int NOT NULL DEFAULT 0 COMMENT '下一发号值',
  PRIMARY KEY (day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='当场验收记录取号表';

-- 当场验收记录：菜单与按钮权限（挂在系统管理目录 411522822607867904 下）
INSERT IGNORE INTO t_sys_permission (id, name, descripion, url, is_blank, pid, perms, type, icon, order_num, visible, create_by, create_time)
VALUES (8024001112, '联户验收记录', '当场验收记录（受理簿）展示', '/BarrAcceptRecordController/view', 0, 411522822607867904, 'barr:barrAccept:view', 1, 'fa fa-book', 92, 0, 'seed', NOW()),
       (8024001113, '联户验收记录集合', '当场验收记录集合', '/BarrAcceptRecordController/list', 0, 8024001112, 'barr:barrAccept:list', 2, NULL, NULL, 0, 'seed', NOW()),
       (8024001114, '当场验收落簿', '当场落一笔受理簿', '/BarrAcceptRecordController/register', 0, 8024001112, 'barr:barrAccept:register', 2, NULL, NULL, 0, 'seed', NOW());

-- 联户验收签认单（会签版）：菜单与按钮权限
INSERT IGNORE INTO t_sys_permission (id, name, descripion, url, is_blank, pid, perms, type, icon, order_num, visible, create_by, create_time)
VALUES (8024001115, '联户验收签认单', '联户验收签认单（会签版）展示', '/BarrJointSignController/view', 0, 411522822607867904, 'barr:barrJointSign:view', 1, 'fa fa-pencil-square-o', 93, 0, 'seed', NOW()),
       (8024001116, '联户签认单集合', '联户签认单集合', '/BarrJointSignController/list', 0, 8024001115, 'barr:barrJointSign:list', 2, NULL, NULL, 0, 'seed', NOW()),
       (8024001117, '由验收记录带单', '只许由当场验收记录关联带出一张', '/BarrJointSignController/open', 0, 8024001115, 'barr:barrJointSign:open', 2, NULL, NULL, 0, 'seed', NOW()),
       (8024001118, '三口落签', '全单只此一处走口（落可/不可）', '/BarrJointSignController/sign', 0, 8024001115, 'barr:barrJointSign:sign', 2, NULL, NULL, 0, 'seed', NOW()),
       (8024001119, '联户签认逐口核对', '事后从第三口数回第一口对受理簿', '/BarrJointSignController/audit', 0, 8024001115, 'barr:barrJointSign:audit', 2, NULL, NULL, 0, 'seed', NOW());

-- 街道岗 / 部岗：两岗都看得见受理簿与签认单；落簿、带单、落签、核对两岗都按得动
-- （到场三方里的外部签字人没有系统账号，由当场经办在同一会签口录笔）
INSERT IGNORE INTO t_sys_permission_role (id, role_id, permission_id)
VALUES (8024001321, 8024001201, 8024001112),
       (8024001322, 8024001201, 8024001113),
       (8024001323, 8024001201, 8024001114),
       (8024001324, 8024001201, 8024001115),
       (8024001325, 8024001201, 8024001116),
       (8024001326, 8024001201, 8024001117),
       (8024001327, 8024001201, 8024001118),
       (8024001328, 8024001201, 8024001119),
       (8024001421, 8024001202, 8024001112),
       (8024001422, 8024001202, 8024001113),
       (8024001423, 8024001202, 8024001114),
       (8024001424, 8024001202, 8024001115),
       (8024001425, 8024001202, 8024001116),
       (8024001426, 8024001202, 8024001117),
       (8024001427, 8024001202, 8024001118),
       (8024001428, 8024001202, 8024001119);

-- 管理员（id=1）给联户验收八项权限
INSERT IGNORE INTO t_sys_permission_role (id, role_id, permission_id)
SELECT 8024001520 + n, 488243256161730560, 8024001100 + n
FROM (SELECT 12 n UNION SELECT 13 UNION SELECT 14 UNION SELECT 15 UNION SELECT 16 UNION SELECT 17 UNION SELECT 18 UNION SELECT 19) t;

-- 种子：一场当场验收（祥和楼组片区，三户一道改，白沙街道）。签认单不预带，落簿后由当场带单。
INSERT IGNORE INTO t_barr_accept_record (id, accept_no, group_no, arch_id, arch_no, arch_name, arch_kind, street_code, street_name, site_count, accept_time, holder, create_by, create_time, remark)
SELECT 8024001901, 'YS-20261005-0001', '祥和楼组1栋', a.id, a.arch_no, a.arch_name, a.arch_kind, 'BSJD', '白沙街道', 3, NOW(), 'seed', 'seed', NOW(), '梅坂旧案之后，新规矩头一场：三口当面坐定，逐口点齐'
FROM t_barr_arch a
WHERE a.id = 0 AND a.del_flag = 0;


-- ============================ 补助核销册进门 ============================
-- 两条街的封顶线种子（当地那条线，一条街一行）
INSERT IGNORE INTO t_barr_cap_line (id, street_code, cap_amount, status, del_flag, create_by, create_time)
VALUES (8024002001, 'BSJD', 5000.00, 0, 0, 'seed', NOW()),
       (8024002002, 'OTJD', 3000.00, 0, 0, 'seed', NOW());

-- 补助核销册：菜单与按钮权限（挂在系统管理目录 411522822607867904 下）
INSERT IGNORE INTO t_sys_permission (id, name, descripion, url, is_blank, pid, perms, type, icon, order_num, visible, create_by, create_time)
VALUES (8024001120, '补助核销册', '月度补助核销册进门台账', '/BarrPayBookController/view', 0, 411522822607867904, 'barr:barrPayBook:view', 1, 'fa fa-book', 94, 0, 'seed', NOW()),
       (8024001121, '核销册集合', '在册核销册台账', '/BarrPayBookController/list', 0, 8024001120, 'barr:barrPayBook:list', 2, NULL, NULL, 0, 'seed', NOW()),
       (8024001122, '核销册进门', '一册一次递齐、整册进门', '/BarrPayBookController/intake', 0, 8024001120, 'barr:barrPayBook:intake', 2, NULL, NULL, 0, 'seed', NOW()),
       (8024001123, '核销册回执', '回执三数回库一行一行数出来', '/BarrPayBookController/receipt', 0, 8024001120, 'barr:barrPayBook:receipt', 2, NULL, NULL, 0, 'seed', NOW()),
       (8024001124, '核销册撤册', '要动先撤册，撤完再重新递', '/BarrPayBookController/withdraw', 0, 8024001120, 'barr:barrPayBook:withdraw', 2, NULL, NULL, 0, 'seed', NOW()),
       (8024001125, '核销册对账', '行去向与回执三数互相点名', '/BarrPayBookController/audit', 0, 8024001120, 'barr:barrPayBook:audit', 2, NULL, NULL, 0, 'seed', NOW()),
       (8024001126, '往期层翻看', '往期层照旧翻得着、一条不少', '/BarrPayBookController/pastRows', 0, 8024001120, 'barr:barrPayBook:past', 2, NULL, NULL, 0, 'seed', NOW()),
       (8024001127, '未勾挪往期', '连着两个拨付年度未勾的挪往期', '/BarrPayBookController/movePast', 0, 8024001120, 'barr:barrPayBook:movePast', 2, NULL, NULL, 0, 'seed', NOW());

-- 街道岗：看册、递册、拿回执、翻往期；撤册、对账、挪往期归部岗
INSERT IGNORE INTO t_sys_permission_role (id, role_id, permission_id)
VALUES (8024001331, 8024001201, 8024001120),
       (8024001332, 8024001201, 8024001121),
       (8024001333, 8024001201, 8024001122),
       (8024001334, 8024001201, 8024001123),
       (8024001335, 8024001201, 8024001126),
       (8024001431, 8024001202, 8024001120),
       (8024001432, 8024001202, 8024001121),
       (8024001433, 8024001202, 8024001122),
       (8024001434, 8024001202, 8024001123),
       (8024001435, 8024001202, 8024001124),
       (8024001436, 8024001202, 8024001125),
       (8024001437, 8024001202, 8024001126),
       (8024001438, 8024001202, 8024001127);

-- 管理员（id=1）给核销册八项权限
INSERT IGNORE INTO t_sys_permission_role (id, role_id, permission_id)
SELECT 8024001540 + n, 488243256161730560, 8024001120 + n
FROM (SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7) t;
