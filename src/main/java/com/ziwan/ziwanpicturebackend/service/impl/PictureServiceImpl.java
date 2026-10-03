package com.ziwan.ziwanpicturebackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ziwan.ziwanpicturebackend.api.aliyunai.ALiYunAiApi;
import com.ziwan.ziwanpicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.ziwan.ziwanpicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.ziwan.ziwanpicturebackend.exception.BusinessException;
import com.ziwan.ziwanpicturebackend.exception.ErrorCode;
import com.ziwan.ziwanpicturebackend.exception.ThrowUtils;
import com.ziwan.ziwanpicturebackend.manager.CosManager;
import com.ziwan.ziwanpicturebackend.manager.LocalRedisCacheManager;
import com.ziwan.ziwanpicturebackend.manager.upload.FilePictureUpload;
import com.ziwan.ziwanpicturebackend.manager.upload.PictureUploadTemplate;
import com.ziwan.ziwanpicturebackend.manager.upload.UrlPictureUpload;
import com.ziwan.ziwanpicturebackend.model.dto.file.UploadPictureResult;
import com.ziwan.ziwanpicturebackend.model.dto.picture.*;
import com.ziwan.ziwanpicturebackend.model.entity.Picture;
import com.ziwan.ziwanpicturebackend.model.entity.Space;
import com.ziwan.ziwanpicturebackend.model.entity.SpaceUser;
import com.ziwan.ziwanpicturebackend.model.entity.User;
import com.ziwan.ziwanpicturebackend.model.enums.PictureReviewStatusEnum;
import com.ziwan.ziwanpicturebackend.model.enums.SpaceRoleEnum;
import com.ziwan.ziwanpicturebackend.model.vo.PictureVO;
import com.ziwan.ziwanpicturebackend.model.vo.UserVO;
import com.ziwan.ziwanpicturebackend.service.PictureService;
import com.ziwan.ziwanpicturebackend.mapper.PictureMapper;
import com.ziwan.ziwanpicturebackend.service.SpaceService;
import com.ziwan.ziwanpicturebackend.service.SpaceUserService;
import com.ziwan.ziwanpicturebackend.service.UserService;
import com.ziwan.ziwanpicturebackend.utils.ColorSimilarUtils;
import com.ziwan.ziwanpicturebackend.utils.ColorTransformUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author brave
 * &#064;description  针对表【picture(图片)】的数据库操作Service实现
 * &#064;createDate  2025-09-10 23:07:53
 */
@Service
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {


    @Resource
    private UserService userService;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Resource
    private FilePictureUpload filePictureUpload;
    @Resource
    private CosManager cosManager;

    @Resource
    private SpaceService spaceService;

    @Resource
    private LocalRedisCacheManager localRedisCacheManager;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private ALiYunAiApi aLiYunAiApi;

    @Resource
    private SpaceUserService spaceUserService;

    /**
     * 验证
     *
     * @param picture 图片
     */
    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAM_ERROR);
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAM_ERROR, "id不能为空");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAM_ERROR, "url过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 1024, ErrorCode.PARAM_ERROR, "简介过长");
        }

    }

    /**
     * 上传图片
     *
     * @param inputSource          输入源
     * @param pictureUploadRequest 上传图片请求
     * @param loginUser            登录用户
     * @return 图片信息
     */
    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTO_ERROR);

        //校验空间是否存在
        Long spaceId = pictureUploadRequest.getSpaceId();

        if (spaceId != null) {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            //校验空间是否有权限
            if (!space.getUserId().equals(loginUser.getId())) {
                throw new BusinessException(ErrorCode.NO_AUTO_ERROR, "无此空间权限");


            }
            //校验额度
            if (space.getTotalCount() >= space.getMaxCount()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间已满");
            }
            if (space.getTotalSize() >= space.getMaxSize()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间已满");
            }

        }


        //判断是否新增还是删除

        Long pictureId;
        if (pictureUploadRequest != null && pictureUploadRequest.getId() != null) {
            pictureId = pictureUploadRequest.getId();
        } else {
            pictureId = null;
        }
        //校验图片是否存在,更新时
        Picture oldPicture;
        if (pictureId != null) {

            oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");

            QueryWrapper<SpaceUser> spaceUserQueryWrapper = new QueryWrapper<>();
            spaceId = oldPicture.getSpaceId();
            if (spaceId != null){
                spaceUserQueryWrapper.eq("spaceId", spaceId);
                spaceUserQueryWrapper.eq("userId", loginUser.getId());
                SpaceUser spaceUser = spaceUserService.getOne(spaceUserQueryWrapper);
                if (spaceUser != null) {
                    SpaceRoleEnum value = SpaceRoleEnum.getEnumByValue(spaceUser.getSpaceRole());
                    if (ObjUtil.isNotEmpty(spaceUser) && value != null) {
                        if (SpaceRoleEnum.VIEWER.getValue().equals(value.getValue())) {
                            throw new BusinessException(ErrorCode.NO_AUTO_ERROR, "无此空间权限");
                        }
                    }

                }
            }
            else {
                if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                    throw new BusinessException(ErrorCode.NO_AUTO_ERROR);
                }

                //校验空间是否一致
                if (spaceId != null && oldPicture.getSpaceId() != null &&
                        !spaceId.equals(oldPicture.getSpaceId())) {
                    throw new BusinessException(ErrorCode.NO_AUTO_ERROR, "空间不一致");
                }

            }

//            boolean exists = this.lambdaQuery().eq(Picture::getId, pictureId).exists();
//            ThrowUtils.throwIf(!exists, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        } else {
            oldPicture = null;
        }
        //上传图片
        String uploadPathPrefix;
        if (spaceId == null) {
            //公共图片
            uploadPathPrefix = String.format("picture/%s", loginUser.getId());
        } else {
            //私有图片
            uploadPathPrefix = String.format("space/%s", spaceId);
        }

        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if (inputSource instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);


        Picture picture = Picture.builder()
                .url(uploadPictureResult.getUrl())
                .thumbnailUrl(uploadPictureResult.getThumbnailUrl())
                .name(uploadPictureResult.getPicName())
                .picSize(uploadPictureResult.getPicSize())
                .picWidth(uploadPictureResult.getPicWidth())
                .picHeight(uploadPictureResult.getPicHeight())
                .picScale(uploadPictureResult.getPicScale())
                .picFormat(uploadPictureResult.getPicFormat())
                .userId(loginUser.getId())
                .picColor(ColorTransformUtils.getStandardColor(uploadPictureResult.getPicColor()))
                .spaceId(spaceId)
                .build();
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getNamePrefix())) {
            picture.setName(pictureUploadRequest.getNamePrefix());

        }


        this.fillReviewParams(picture, loginUser);
        if (pictureId != null) {
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }

        Long finalSpaceId = spaceId;
        transactionTemplate.execute(status -> {
            try {
                // 保存或更新图片
                boolean save = this.saveOrUpdate(picture);
                ThrowUtils.throwIf(!save, ErrorCode.OPERATION_ERROR, "图片上传失败");

                if (finalSpaceId != null) {
                    // 新增或替换的空间额度更新
                    long newSize = picture.getPicSize() != null ? picture.getPicSize() : 0;
                    long oldSize = (oldPicture != null && oldPicture.getPicSize() != null) ? oldPicture.getPicSize() : 0;
                    long sizeDiff = newSize - oldSize;

                    LambdaUpdateChainWrapper<Space> update = spaceService.lambdaUpdate()
                            .eq(Space::getId, finalSpaceId)
                            .setSql("totalSize = totalSize +" + sizeDiff);
                    if (oldPicture != null) {
                        this.clearPicture(oldPicture);
                    }

                    if (oldPicture == null) {
                        // 新增图片才增加数量
                        update.setSql("totalCount = totalCount +1");
                    }

                    boolean updateResult = update.update();
                    ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新额度失败");
                }

                return picture;
            } catch (Exception e) {
                log.error("图片上传失败, userId={}, pictureId={}", loginUser.getId(), pictureId, e);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片上传失败");
            }

        });


        return PictureVO.objToVo(picture);
    }


    /**
     * 获取图片
     *
     * @param picture 图片
     * @param request 请求
     * @return 脱敏后的图片
     */
    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        PictureVO pictureVO = PictureVO.objToVo(picture);
        Long userId = pictureVO.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }

    /**
     * 获取图片分页
     *
     * @param picturePage 图片分页
     * @param request     请求
     * @return 图片分页
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(),
                picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        List<PictureVO> pictureVOList = pictureList.stream().map(PictureVO::objToVo).collect(Collectors.toList());

        //获取用户
        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        // 用户信息列表
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 填充用户信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));

        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }


    /**
     * 获取查询条件
     *
     * @param pictureQueryRequest 图片查询条件
     * @return 查询条件
     */
    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }


        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        Long spaceId = pictureQueryRequest.getSpaceId();
        Boolean nullSpaceId = pictureQueryRequest.getNullSpaceId();

        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();


        if (StrUtil.isNotBlank(searchText)) {
            queryWrapper.and(
                    qw -> qw.like("name", searchText)
                            .or()
                            .like("introduction", searchText)

            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(Boolean.TRUE.equals(nullSpaceId), "spaceId")
                .isNotNull(Boolean.FALSE.equals(nullSpaceId), "spaceId");
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);


        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        queryWrapper.orderBy(StrUtil.isNotBlank(sortField), sortOrder.equals("ascend"),
                sortField);


        return queryWrapper;
    }

    /**
     * 图片审核
     *
     * @param pictureReviewRequest 图片审核请求
     * @param loginUser            登录用户
     */
    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        //1. 参数校验
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.NOT_FOUND_ERROR);
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        String reviewMessage = pictureReviewRequest.getReviewMessage();
        ThrowUtils.throwIf(id == null ||
                        reviewStatusEnum == null ||
                        PictureReviewStatusEnum.REVIEW_WAITING.equals(reviewStatusEnum),
                ErrorCode.PARAM_ERROR);
        //2.判断图片是否存在
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        //3.校验审核状态是否重复
        if (oldPicture.getReviewStatus().equals(reviewStatus)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片已审核");
        }
        //4.数据库操作
        Picture updatePicture = Picture.builder().build();
        BeanUtil.copyProperties(pictureReviewRequest, updatePicture);
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());
        boolean result = this.updateById(updatePicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    /**
     * 填充审核参数
     *
     * @param picture   图片
     * @param loginUser 登录用户
     */
    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        if (userService.isAdmin(loginUser)) {
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEW_PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员审核通过");
            picture.setReviewTime(new Date());
        } else {
            //非管理员
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEW_WAITING.getValue());
        }

    }


    /**
     * 图片上传
     *
     * @param pictureUploadByBatchRequest 图片上传请求
     * @param loginUser                   登录用户
     * @return 图片信息
     */
    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        String searchText = pictureUploadByBatchRequest.getSearchText();
        Integer count = pictureUploadByBatchRequest.getCount();


        ThrowUtils.throwIf(count >= 40, ErrorCode.PARAM_ERROR, "上传数量不能大于40");
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }


        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);


        Document document;
        try {
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("图片获取失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片获取失败");
        }
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isNull(div)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "获取元素失败");
        }
        Elements imgElementList = div.select("img.mimg");
        int uploadCount = 0;
        for (Element imgElement : imgElementList) {
            String fileUrl = imgElement.attr("src");
            if (StrUtil.isBlank(fileUrl)) {
                log.info("图片地址{}为空,当前为第 {} 个", fileUrl, uploadCount);
                continue;
            }
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            pictureUploadRequest.setFileUrl(fileUrl);
            pictureUploadRequest.setNamePrefix(namePrefix + (uploadCount + 1));

            try {
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("图片上传成功,当前为第 {} 个,id = {}", uploadCount, pictureVO.getId());
                uploadCount++;

            } catch (Exception e) {
                log.error("图片上传失败,当前为第 {} 个,url = {}", uploadCount, fileUrl);
                continue;

            }
            if (uploadCount >= count) {
                break;
            }


        }

        return uploadCount;


    }

    /**
     * 删除图片
     *
     * @param oldPicture 旧图片
     */
    @Async
    @Override
    public void clearPicture(Picture oldPicture) {
        String oldPictureUrl = oldPicture.getUrl();
        long count = this.lambdaQuery()
                .eq(Picture::getUrl, oldPictureUrl)
                .count();
        if (count > 1) {
            return;
        }
        cosManager.deletePictureObject(oldPictureUrl);

        String thumbnailUrl = oldPicture.getThumbnailUrl();
        if (StrUtil.isNotBlank(thumbnailUrl)) {
            cosManager.deletePictureObject(thumbnailUrl);
        }

    }

    /**
     * 校验图片权限
     *
     * @param loginUser 登录用户
     * @param picture   图片
     */
    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        Long loginUserId = loginUser.getId();
        if (spaceId == null) {
            // 公共空间
            if (!userService.isAdmin(loginUser) && !loginUserId.equals(picture.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTO_ERROR);
            }
        } else {
            if (!loginUserId.equals(picture.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTO_ERROR);
            }
        }


    }

    /**
     * 删除图片
     *
     * @param id        图片id
     * @param loginUser 登录用户
     */
    @Override
    public void deletePicture(Long id, User loginUser) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAM_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        Picture oldPicture = this.getById(id);
        Long spaceId = oldPicture.getSpaceId();
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        //权限校验
        this.checkPictureAuth(loginUser, oldPicture);
        //开启事务
        transactionTemplate.execute(status -> {
            //插入数据库
            boolean remove = this.removeById(id);
            ThrowUtils.throwIf(!remove, ErrorCode.OPERATION_ERROR);

            if (ObjUtil.isNotNull(spaceId)) {
                //更新额度
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, oldPicture.getSpaceId())
                        .setSql("totalSize = totalSize -" + oldPicture.getPicSize())
                        .setSql("totalCount = totalCount -1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "更新额度失败");
            }
            return true;
        });
        //删除图片资源
        this.clearPicture(oldPicture);
    }

    /**
     * 编辑图片
     *
     * @param pictureEditRequest 图片编辑请求
     * @param loginUser          登录用户
     */
    @Override
    public void editPicture(PictureEditRequest pictureEditRequest, User loginUser) {
        Picture picture = Picture.builder().build();
        BeanUtil.copyProperties(pictureEditRequest, picture);
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        picture.setEditTime(new Date());
        this.validPicture(picture);
        this.fillReviewParams(picture, loginUser);

        Long id = pictureEditRequest.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        //校验权限
        this.checkPictureAuth(loginUser, oldPicture);
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }


    /**
     * 获取图片VO列表（仅展示审核通过的图片）
     *
     * @param pictureQueryRequest 图片查询请求
     * @param request             请求
     * @return 图片VO列表
     */
    @Override
    public Page<PictureVO> listPictureVOByPageWithCache(PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 最多展示20条, 防止前端传入的size过大
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAM_ERROR);
        // 只展示审核通过的图片
        Long spaceId = pictureQueryRequest.getSpaceId();
        if (spaceId == null) {
            // 公共空间
            // 只展示审核通过的图片
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.REVIEW_PASS.getValue());
            pictureQueryRequest.setNullSpaceId(true);

            // 查询缓存, 缓存没有，则查询数据库
            // 缓存key设置
            String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
            String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
            String redisKey = String.format("ziwanPicture:listPictureVOByPage:page:%s", hashKey);

            //1. 缓存逻辑先尝试从本地缓存中获取数据

            Page<PictureVO> cachePage = localRedisCacheManager.getCache(redisKey, Page.class);
            if (ObjUtil.isNotNull(cachePage)) {
                return cachePage;
            }
            // 分页查询(查询数据库)
            Page<Picture> picturePage = this.page(new Page<>(current, size),
                    this.getQueryWrapper(pictureQueryRequest));
            Page<PictureVO> pictureVOPage = this.getPictureVOPage(picturePage, request);

//        //4.更新缓存
//        // 缓存未命中
//        // 存入redis缓存
//        // 存入本地缓存
            localRedisCacheManager.setCache(redisKey, pictureVOPage);
            return pictureVOPage;

        } else {
            User loginUser = userService.getLoginUser(request);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            ThrowUtils.throwIf(!space.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTO_ERROR, "无此空间权限");

            // 分页查询(查询数据库)
            Page<Picture> picturePage = this.page(new Page<>(current, size),
                    this.getQueryWrapper(pictureQueryRequest));
            return this.getPictureVOPage(picturePage, request);

        }
    }

    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
        ThrowUtils.throwIf(spaceId == null || StrUtil.isBlank(picColor), ErrorCode.PARAM_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTO_ERROR, "无此空间权限");

        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        ThrowUtils.throwIf(!space.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTO_ERROR, "无此空间权限");
        List<Picture> pictureList = this.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .isNotNull(Picture::getPicColor)
                .list();

        if (CollUtil.isEmpty(pictureList)) {
            return new ArrayList<>();
        }
        Color targetColor = Color.decode(picColor);
        List<Picture> sortedPictureList = pictureList.stream()
                .sorted(Comparator.comparingDouble(picture -> {
                    String hexColor = picture.getPicColor();
                    if (StrUtil.isBlank(hexColor)) {
                        return Double.MAX_VALUE;
                    }
                    Color pictureColor = Color.decode(hexColor);
                    return -ColorSimilarUtils.calculateSimilarity(targetColor, pictureColor);
                }))
                .limit(10).toList();


        return sortedPictureList.stream().map(PictureVO::objToVo).toList();
    }


    @Override
    public void batchEditPictureMetadata(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        // 1. 获取和校验参数
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();
        ThrowUtils.throwIf(CollUtil.isEmpty(pictureIdList), ErrorCode.PARAM_ERROR);
        ThrowUtils.throwIf(spaceId == null, ErrorCode.PARAM_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        // 2. 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!space.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "没有空间访问权限");
        }
        // 3. 查询指定图片（仅选择需要的字段）
        List<Picture> pictureList = this.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId)
                .eq(Picture::getSpaceId, spaceId)
                .in(Picture::getId, pictureIdList)
                .list();
        if (pictureList.isEmpty()) {
            return;
        }
        // 4. 更新分类和标签
        pictureList.forEach(picture -> {
            if (StrUtil.isNotBlank(category)) {
                picture.setCategory(category);
            }
            if (CollUtil.isNotEmpty(tags)) {
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
        });
        // 5. 批量重命名
        String nameRule = pictureEditByBatchRequest.getNameRule();
        fillPictureWithNameRule(pictureList, nameRule);
        // 6. 操作数据库进行批量更新
        boolean result = this.updateBatchById(pictureList);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "批量编辑失败");
    }

    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();
        Picture picture = this.getById(pictureId);
        Optional.ofNullable(picture)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在"));

        // 1. 校验权限
        checkPictureAuth(loginUser, picture);
        CreateOutPaintingTaskRequest createOutPaintingTaskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl());
        createOutPaintingTaskRequest.setInput(input);
        createOutPaintingTaskRequest.setParameters(createPictureOutPaintingTaskRequest.getParameters());


        return aLiYunAiApi.createOutPaintingTask(createOutPaintingTaskRequest);

    }

    /**
     * nameRule 格式：图片{序号}
     *
     * @param pictureList 图片列表
     * @param nameRule    名称规则
     */
    private void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        if (CollUtil.isEmpty(pictureList) || StrUtil.isBlank(nameRule)) {
            return;
        }
        long count = 1;
        try {
            for (Picture picture : pictureList) {
                String pictureName = nameRule.replaceAll("\\{序号}", String.valueOf(count++));
                picture.setName(pictureName);
            }
        } catch (Exception e) {
            log.error("名称解析错误", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析错误");
        }
    }

}




