package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //获取登录用户id
        Long userId = UserHolder.getUser().getId();
        String key = "follows:"+userId;
        if(isFollow){
            //关注
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSave = this.save(follow);
            if(isSave){
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        }else{
            //取关
            boolean isRemove = this.remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            if(isRemove){
                stringRedisTemplate.opsForSet().remove(key,followUserId);
            }
        }
        return Result.ok();
    }

    @Override
    public Result followOrNot(Long followUserId) {
        //获取登录用户id
        Long userId = UserHolder.getUser().getId();

        //查询是否存在
        Integer count = query().eq("user_id", userId)
                .eq("follow_user_id", followUserId)
                .count();
        if(count>0){
            return Result.ok(true);
        }

        return Result.ok(false);
    }

    @Override
    public Result followCommon(Long id) {
        //获取当前用户id
        Long userId = UserHolder.getUser().getId();

        //Redis中俩用户关注的key
        String key1 = "follows:"+userId;
        String key2 = "follows:"+id;

        //取俩用户交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        //判断是否有交集
        if(intersect==null||intersect.isEmpty()){
            //无交集
            return Result.ok(Collections.emptyList());
        }
        //解析id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //查询用户
        List<UserDTO> userDTOList = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOList);
    }
}
