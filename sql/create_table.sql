create database if not exists `ziwan_picture`;
use `ziwan_picture`;

-- 用户表
create table if not exists user
(
    id           bigint auto_increment comment 'id' primary key,
    userAccount  varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    UNIQUE KEY uk_userAccount (userAccount),
    INDEX idx_userName (userName)
) comment '用户' collate = utf8mb4_unicode_ci;


-- 图片表
create table if not exists picture
(
    id            bigint auto_increment comment 'id' primary key,
    url           varchar(512)                       not null comment '图片 user.idurl',
    thumbnailUrl  varchar(512)                       NULL COMMENT '缩略图 url',
    name          varchar(128)                       not null comment '图片名称',
    introduction  varchar(512)                       null comment '简介',
    category      varchar(64)                        null comment '分类',
    tags          varchar(512)                       null comment '标签（JSON 数组）',
    picSize       bigint                             null comment '图片体积',
    picWidth      int                                null comment '图片宽度',
    picHeight     int                                null comment '图片高度',
    picScale      double                             null comment '图片宽高比例',
    picFormat     varchar(32)                        null comment '图片格式',
    spaceId       bigint                             null comment '空间 id（为空表示公共空间）',
    reviewStatus  INT      DEFAULT 0                 NOT NULL COMMENT '审核状态：0-待审核; 1-通过; 2-拒绝',
    reviewMessage VARCHAR(512)                       NULL COMMENT '审核信息',
    reviewerId    BIGINT                             NULL COMMENT '审核人 ID',
    reviewTime    DATETIME                           NULL COMMENT '审核时间',
    userId        bigint                             not null comment '创建用户 id',
    createTime    datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    editTime      datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    updateTime    datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete      tinyint  default 0                 not null comment '是否删除',
    INDEX idx_name (name),                 -- 提升基于图片名称的查询性能
    INDEX idx_introduction (introduction), -- 用于模糊搜索图片简介
    INDEX idx_category (category),         -- 提升基于分类的查询性能
    INDEX idx_tags (tags),                 -- 提升基于标签的查询性能
    INDEX idx_userId (userId),             -- 提升基于用户 ID 的查询性能
    INDEX idx_reviewStatus (reviewStatus), -- 创建基于 reviewStatus 列的索引
    INDEX idx_spaceId (spaceId)            -- 创建基于 spaceId 列的索引
) comment '图片' collate = utf8mb4_unicode_ci;

-- 空间表
create table if not exists space
(
    id         bigint auto_increment comment 'id' primary key,
    spaceName  varchar(128)                       null comment '空间名称',
    spaceLevel int      default 0                 null comment '空间级别：0-普通版 1-专业版 2-旗舰版',
    maxSize    bigint   default 0                 null comment '空间图片的最大总大小',
    maxCount   bigint   default 0                 null comment '空间图片的最大数量',
    picColor   varchar(16)                        null comment '图片主色调',
    totalSize  bigint   default 0                 null comment '当前空间下图片的总大小',
    totalCount bigint   default 0                 null comment '当前空间下的图片数量',
    userId     bigint                             not null comment '创建用户 id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    editTime   datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete   tinyint  default 0                 not null comment '是否删除',
    spaceType  int      default 0                 not null comment '空间类型：0-私有 1-团队',
    -- 索引设计
    index idx_userId (userId),         -- 提升基于用户的查询效率
    index idx_spaceName (spaceName),   -- 提升基于空间名称的查询效率
    index idx_spaceLevel (spaceLevel), -- 提升按空间级别查询的效率
    INDEX idx_spaceType (spaceType)
) comment '空间' collate = utf8mb4_unicode_ci;


-- 空间成员表
create table if not exists space_user
(
    id         bigint auto_increment comment 'id' primary key,
    spaceId    bigint                                 not null comment '空间 id',
    userId     bigint                                 not null comment '用户 id',
    spaceRole  varchar(128) default 'viewer'          null comment '空间角色：viewer/editor/admin',
    createTime datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    -- 索引设计
    UNIQUE KEY uk_spaceId_userId (spaceId, userId), -- 唯一索引，用户在一个空间中只能有一个角色
    INDEX idx_spaceId (spaceId),                    -- 提升按空间查询的性能
    INDEX idx_userId (userId)                       -- 提升按用户查询的性能
) comment '空间用户关联' collate = utf8mb4_unicode_ci;

ALTER TABLE picture
    ADD COLUMN picColor VARCHAR(50) NULL COMMENT '图片颜色';


CREATE TABLE tb_voucher_order
(
    id          BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    userId     BIGINT UNSIGNED  NOT NULL COMMENT '下单的用户ID',
    voucherId  BIGINT UNSIGNED  NOT NULL COMMENT '购买的代金券ID',

    payType    TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '支付方式：1余额；2支付宝；3微信',

    status      TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '订单状态：1未支付；2已支付；3已核销；4已取消；5退款中；6已退款',

    createTime TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    payTime    TIMESTAMP        NULL     DEFAULT NULL COMMENT '支付时间',
    useTime    TIMESTAMP        NULL     DEFAULT NULL COMMENT '核销时间',
    refundTime TIMESTAMP        NULL     DEFAULT NULL COMMENT '退款时间',
    updateTime TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    KEY idx_userId (userId),
    KEY idx_voucherId (voucherId),
    KEY idx_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='代金券订单表';



CREATE TABLE `tb_voucher` (
                              `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
                              `spaceId` bigint(20) unsigned DEFAULT NULL COMMENT '空间id',
                              `title` varchar(255) NOT NULL COMMENT '代金券标题',
                              `subTitle` varchar(255) DEFAULT NULL COMMENT '副标题',
                              `rules` varchar(1024) DEFAULT NULL COMMENT '使用规则',
                              `payValue` bigint(10) unsigned NOT NULL COMMENT '支付金额，单位分。例如200代表2元',
                              `actualValue` bigint(10) unsigned NOT NULL COMMENT '抵扣金额，单位分。例如200代表2元',
                              `type` tinyint(1) unsigned NOT NULL DEFAULT '0' COMMENT '0-普通券；1-秒杀券',
                              `status` tinyint(1) unsigned NOT NULL DEFAULT '1' COMMENT '1-上架；2-下架；3-过期',
                              `createTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              `updateTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                              PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4;


CREATE TABLE `tb_seckill_voucher` (
                                      `voucherId` bigint(20) unsigned NOT NULL COMMENT '关联的优惠券id',
                                      `stock` int(8) unsigned NOT NULL COMMENT '库存',
                                      `createTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                      `beginTime` timestamp NOT NULL  default '0000-00-00 00:00:00' COMMENT '生效时间',
                                      `endTime` timestamp NOT NULL default '0000-00-00 00:00:00' COMMENT '失效时间',
                                      `updateTime` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                      PRIMARY KEY (`voucherId`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀优惠券表，与优惠券表一对一关系';
