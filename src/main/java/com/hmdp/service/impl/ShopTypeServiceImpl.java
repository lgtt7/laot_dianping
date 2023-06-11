package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result queryTypeList() {
        //查询Redis中是否有数据
        String jsonShopType = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_TYPE);
        //如果有数据直接返回
        if(StrUtil.isNotBlank(jsonShopType)){
            List<ShopType> shopTypes = JSONUtil.toList(jsonShopType, ShopType.class);
            return Result.ok(shopTypes);
        }
        //没有数据则查询数据库
        QueryWrapper<ShopType> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByAsc("sort");
        List<ShopType> list = this.list(queryWrapper);
        //数据缓存至Redis
        String json = JSONUtil.toJsonStr(list);
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_TYPE,json);
        //返回数据

        return Result.ok(list);
    }
}
