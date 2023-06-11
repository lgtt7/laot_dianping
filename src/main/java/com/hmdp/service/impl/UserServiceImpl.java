package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号是否正确
        boolean phoneInvalid = RegexUtils.isPhoneInvalid(phone);
        if(phoneInvalid){
            return Result.fail("手机格式错误");
        }
        //生成验证码
//        String code = RandomUtil.randomNumbers(4);
        //默认为1234
        String code = "1234";
        //session保存验证码
        //session.setAttribute(phone,code);
        //保存验证码到redis
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY +phone,code,1, TimeUnit.MINUTES);
        //发送验证码
        log.info(code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号格式是否正确
        if(RegexUtils.isPhoneInvalid(loginForm.getPhone())){
            return Result.fail("手机格式错误");
        }
        //校验验证码格式是否正确
        if(RegexUtils.isCodeInvalid(loginForm.getCode())){
            return Result.fail("验证码格式错误");
        }
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();

        //校验验证码是否正确
//        String sessionCode = (String)session.getAttribute(phone);
        String redisCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        if(redisCode==null || !code.equals(redisCode)){
            return Result.fail("验证码不匹配");
        }
        //查询数据库判断是否存在该手机号
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("phone",phone);
        User user = this.getOne(queryWrapper);
        if(user==null){
            //用户不存在，注册新用户
            user = createUserWithPhone(phone);
        }

        //生成token
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> map = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create().
                setIgnoreNullValue(true).
                setFieldValueEditor((fieldName,fieldValue) -> fieldValue.toString()));
        String tokenKey = RedisConstants.LOGIN_USER_KEY+token;
        //将用户信息保存到Redis
        stringRedisTemplate.opsForHash().putAll(tokenKey,map);
        //为Redis的用户信息设置有效时常
//        stringRedisTemplate.expire(tokenKey,100,TimeUnit.DAYS);

        //返回token
        return Result.ok(token);
    }

    @Override
    public Result logout(HttpServletRequest request) {
        //获取请求头的token
        String token = request.getHeader("authorization");

        //判断token是否存在
        if(StrUtil.isBlank(token)){
            return Result.ok();
        }
        String key = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.delete(key);
        return Result.ok();
    }

    private User createUserWithPhone(String phone){
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));

        this.save(user);

        return user;
    }
}
