package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;


    @Override
    public Result queryBlogsById(Long id) {
        //查询blog
        Blog blog = this.getById(id);

        //判断blog是否存在
        if(blog==null){
            return Result.fail("笔记不存在");
        }

        //获取用户信息
        Long userId = blog.getUserId();
        User user = userService.getById(userId);

        //补充blog用户信息
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());

        //查询blog是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        //判断登录用户是否已经点赞
        //获取登录用户ID
        UserDTO user = UserHolder.getUser();
        if(user==null){
            return;
        }
        Long userID = user.getId();
        //判断用户id是否存在redis
        String blogLikeKey = "blog:liked:"+blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(blogLikeKey, userID.toString());
        blog.setIsLike(score!=null);


    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
            this.isBlogLiked(blog);
        });


        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        //判断登录用户是否已经点赞
        //获取登录用户ID
        Long userID = UserHolder.getUser().getId();
        //判断用户id是否存在redis
        String blogLikeKey = "blog:liked:"+id;
        Double score = stringRedisTemplate.opsForZSet().score(blogLikeKey, userID.toString());

        if(score==null){
            //不存在则点赞
            //将指定id笔记点赞数+1
            boolean isSuccess = this.update().setSql("liked = liked+1").eq("id", id).update();
            if(isSuccess){
                //保存用户到redis的set集合
                stringRedisTemplate.opsForZSet().add(blogLikeKey,userID.toString(),System.currentTimeMillis());
            }
        }else{
            //存在则取消点赞
            //将指定id笔记点赞数-1
            boolean isSuccess = this.update().setSql("liked = liked-1").eq("id", id).update();
            if(isSuccess){
                //删除redis中的用户
                stringRedisTemplate.opsForZSet().remove(blogLikeKey,userID.toString());
            }

        }
        return Result.ok();
    }

    @Override
    public Result queryLikesById(Long id) {
        String key = RedisConstants.BLOG_LIKED_KEY+id;
        //查询top5的点赞用户
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(top5==null){
            return Result.ok();
        }
        //解析用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        if(ids.size()==0){
            return Result.ok();
        }
        //根据用户id查询用户
        String join = StrUtil.join(",", ids);
        List<UserDTO> userDTOS = userService.query()
                .in("id",ids)
                .last("order by field(id,"+join+")")
                .list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSave = save(blog);
        if(!isSave){
            return Result.fail("新增笔记失败");
        }
        //查询所有粉丝
        List<Follow> fans = followService.query().eq("follow_user_id", user.getId()).list();

        //将笔记推送给所有粉丝
        for(Follow fan : fans){
            //获取粉丝id
            Long fansId = fan.getUserId();
            //推送
            String key = "feed:"+fansId;
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();

        //查询用户收件箱
        String key = "feed:"+userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);

        //判断是否为空
        if(typedTuples == null || typedTuples.isEmpty()){
            return Result.ok();
        }
        //解析数据：blogId、score、offset
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int count = 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            //获取id 
            String idStr = typedTuple.getValue();
            ids.add(Long.valueOf(idStr));
            //获取时间戳
            long time = typedTuple.getScore().longValue();
            if(time==minTime){
                count++;
            }else {
                minTime = time;
                count = 1;
            }
        }

        String join = StrUtil.join(",", ids);
        //查询blog
        List<Blog> blogs = query().in("id", ids).last("order by field(id," + join + ")").list();
        //封装最终结果
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setMinTime(minTime);
        scrollResult.setOffset(count);
        return Result.ok(scrollResult);
    }
}
