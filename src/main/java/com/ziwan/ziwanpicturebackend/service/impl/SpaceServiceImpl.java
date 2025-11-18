package com.ziwan.ziwanpicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ziwan.ziwanpicturebackend.exception.BusinessException;
import com.ziwan.ziwanpicturebackend.exception.ErrorCode;
import com.ziwan.ziwanpicturebackend.exception.ThrowUtils;
import com.ziwan.ziwanpicturebackend.manager.sharding.DynamicShardingManager;
import com.ziwan.ziwanpicturebackend.model.dto.space.SpaceAddRequest;
import com.ziwan.ziwanpicturebackend.model.dto.space.SpaceQueryRequest;
import com.ziwan.ziwanpicturebackend.model.entity.Space;
import com.ziwan.ziwanpicturebackend.model.entity.SpaceUser;
import com.ziwan.ziwanpicturebackend.model.entity.User;
import com.ziwan.ziwanpicturebackend.model.enums.SpaceLevelEnum;
import com.ziwan.ziwanpicturebackend.model.enums.SpaceRoleEnum;
import com.ziwan.ziwanpicturebackend.model.enums.SpaceTypeEnum;
import com.ziwan.ziwanpicturebackend.model.vo.SpaceVO;
import com.ziwan.ziwanpicturebackend.model.vo.UserVO;
import com.ziwan.ziwanpicturebackend.service.SpaceService;
import com.ziwan.ziwanpicturebackend.mapper.SpaceMapper;
import com.ziwan.ziwanpicturebackend.service.SpaceUserService;
import com.ziwan.ziwanpicturebackend.service.UserService;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author brave
 * &#064;description  针对表【space(空间)】的数据库操作Service实现
 * &#064;createDate  2025-10-15 18:40:52
 */
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
        implements SpaceService {

    @Resource
    private UserService userService;


    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    @Lazy
    private SpaceUserService spaceUserService;

    @Resource
    @Lazy
    private DynamicShardingManager dynamicShardingManager;

    // 用户锁映射表
    private static final ConcurrentHashMap<Long, Object> LOCK_MAP = new ConcurrentHashMap<>();


    /**
     * 校验
     *
     * @param space  空间
     * @param add   是否为创建时校验
     */
    @Override
    public void validSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAM_ERROR);
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        Integer spaceType = space.getSpaceType();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        SpaceTypeEnum enumByValue = SpaceTypeEnum.getEnumByValue(spaceType);


        // 创建时校验
        if (add) {
            ThrowUtils.throwIf(StrUtil.isBlank(spaceName) && spaceName.length() > 20,
                    ErrorCode.PARAM_ERROR, "空间名称过长");
            ThrowUtils.throwIf(spaceLevel == null, ErrorCode.PARAM_ERROR, "空间等级错误");

            if (spaceType != null && enumByValue == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "空间类型不存在");
            }
        } else {
            // 更新时校验
            if (StrUtil.isNotBlank(spaceName)) {
                ThrowUtils.throwIf(StrUtil.isBlank(spaceName) && spaceName.length() > 20,
                        ErrorCode.PARAM_ERROR, "空间名称过长");
            }
            // 更新时空间等级校验
            if (spaceLevel != null && spaceLevelEnum == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "空间等级不存在");
            }

            if (spaceType != null && enumByValue == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "空间类型不存在");
            }
        }


    }

    /**
     * 获取空间包装类（单条）
     *
     * @param space 空间
     * @param request 请求
     * @return 空的包装类
     */
    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {
        // 获取用户
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        Long userId = spaceVO.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            spaceVO.setUser(userVO);
        }
        return spaceVO;
    }

    /**
     * 获取空间包装类（分页）
     *
     * @param spacePage 分页信息
     * @param request   请求
     * @return 分页数据
     */
    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request) {
        List<Space> spaceList = spacePage.getRecords();
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(),
                spacePage.getSize(), spacePage.getTotal());
        if (CollUtil.isEmpty(spaceList)) {
            return spaceVOPage;
        }
        List<SpaceVO> spaceVOList = spaceList.stream().map(SpaceVO::objToVo).collect(Collectors.toList());

        //获取用户
        Set<Long> userIdSet = spaceList.stream().map(Space::getUserId).collect(Collectors.toSet());
        // 用户信息列表
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 填充用户信息
        spaceVOList.forEach(spaceVO -> {
            Long userId = spaceVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            spaceVO.setUser(userService.getUserVO(user));

        });
        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;
    }

    /**
     * 获取查询条件
     *
     * @param spaceQueryRequest 查询条件
     * @return 查询条件
     */
    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        if (spaceQueryRequest == null) {
            return queryWrapper;
        }


        Long id = spaceQueryRequest.getId();
        Long userId = spaceQueryRequest.getUserId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        String sortField = spaceQueryRequest.getSortField();
        String sortOrder = spaceQueryRequest.getSortOrder();
        Integer spaceType = spaceQueryRequest.getSpaceType();

        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(spaceName), "spaceName", spaceName);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceLevel), "spaceLevel", spaceLevel);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceType), "spaceType", spaceType);
        // 排序
        queryWrapper.orderBy(StrUtil.isNotBlank(sortField), sortOrder.equals("ascend"),
                sortField);


        return queryWrapper;
    }

    /**
     * 填充空间等级
     *
     * @param space
     */
    @Override
    public void fillSpaceBySpaceLevel(Space space) {

        SpaceLevelEnum enumByValue = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        if (enumByValue != null) {
            Long maxSize = space.getMaxSize();
            if (maxSize == null) {
                space.setMaxSize(enumByValue.getMaxSize());
            }
            Long maxCount = space.getMaxCount();
            if (maxCount == null) {
                space.setMaxCount(enumByValue.getMaxCount());
            }

        }


    }

    /**
     * 添加空间
     *
     * @param spaceAddRequest 添加空间请求
     * @param loginUser 登录用户
     * @return 新增空间id
     */
    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        Space space = new Space();
        BeanUtil.copyProperties(spaceAddRequest, space);
        // 空间名称
        if (StrUtil.isBlank(space.getSpaceName())) {
            space.setSpaceName("默认空间");
        }
        //空间等级
        if (space.getSpaceLevel() == null) {
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
            space.setMaxSize(SpaceLevelEnum.COMMON.getMaxSize());
            space.setMaxCount(SpaceLevelEnum.COMMON.getMaxCount());
        }

        if (space.getSpaceLevel() != null && space.getSpaceLevel() == SpaceLevelEnum.COMMON.getValue()) {
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
            space.setMaxSize(SpaceLevelEnum.COMMON.getMaxSize());
            space.setMaxCount(SpaceLevelEnum.COMMON.getMaxCount());
        }
        if (space.getSpaceLevel() != null &&
                space.getSpaceLevel() == SpaceLevelEnum.PROFESSIONAL.getValue() &&
                userService.isAdmin(loginUser)
        ) {
            space.setSpaceLevel(SpaceLevelEnum.PROFESSIONAL.getValue());
            space.setMaxSize(SpaceLevelEnum.PROFESSIONAL.getMaxSize());
            space.setMaxCount(SpaceLevelEnum.PROFESSIONAL.getMaxCount());
        } else if (space.getSpaceLevel() != null && space.getSpaceLevel() == SpaceLevelEnum.PROFESSIONAL.getValue()) {
            throw new BusinessException(ErrorCode.NO_AUTO_ERROR);
        }
        if (space.getSpaceLevel() != null &&
                space.getSpaceLevel() == SpaceLevelEnum.FLAGSHIP.getValue() &&
                userService.isAdmin(loginUser)
        ) {
            space.setSpaceLevel(SpaceLevelEnum.FLAGSHIP.getValue());
            space.setMaxSize(SpaceLevelEnum.FLAGSHIP.getMaxSize());
            space.setMaxCount(SpaceLevelEnum.FLAGSHIP.getMaxCount());
        } else if (space.getSpaceLevel() != null && space.getSpaceLevel() == SpaceLevelEnum.FLAGSHIP.getValue()) {
            throw new BusinessException(ErrorCode.NO_AUTO_ERROR, "无此空间权限");
        }

        if (space.getSpaceType() == null) {
            space.setSpaceType(SpaceTypeEnum.PRIVATE.getValue());
        }

        this.fillSpaceBySpaceLevel(space);
        this.validSpace(space, true);

        Long loginUserId = loginUser.getId();
        space.setUserId(loginUserId);
        if (SpaceLevelEnum.COMMON.getValue() != space.getSpaceLevel() && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTO_ERROR);
        }
        //创建锁，用户只能创建一个空间

        //String lockKey = String.valueOf(loginUserId).intern();
        Object lockKey = LOCK_MAP.computeIfAbsent(loginUserId, k -> new Object());
        synchronized (lockKey) {
            try {
                // 检查
                Long newSpaceId = transactionTemplate.execute(status -> {
                    // 检查是否用户空间存在
                    boolean exists = this.lambdaQuery()
                            .eq(Space::getUserId, loginUserId)
                            .eq(Space::getSpaceType, space.getSpaceType())
                            .exists();
                    ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "用户每类空间只能创建一个");
                    //不存在,保存空间
                    boolean result = this.save(space);
                    ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "创建空间失败");
                    //创建成功后，如果是团队空间，则创建团队空间成员
                    if (space.getSpaceType() == SpaceTypeEnum.TEAM.getValue()) {
                        SpaceUser spaceUser = new SpaceUser();
                        spaceUser.setSpaceId(space.getId());
                        spaceUser.setUserId(loginUserId);
                        spaceUser.setSpaceRole(SpaceRoleEnum.ADMIN.getValue());
                        result = spaceUserService.save(spaceUser);
                        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "创建团队成员失败");


                    }

//                    dynamicShardingManager.createSpacePictureTable(space);

                    return space.getId();
                });
                return Optional.ofNullable(newSpaceId).orElse(-1L);
            } finally {
                LOCK_MAP.remove(loginUserId);
            }

        }

    }

    @Override
    public void checkSpaceAuth(User loginUser, Space space) {
        ThrowUtils.throwIf(!space.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser),
                ErrorCode.NO_AUTO_ERROR);
    }
}




