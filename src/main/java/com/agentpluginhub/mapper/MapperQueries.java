package com.agentpluginhub.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.Optional;

public final class MapperQueries {

    private MapperQueries() {
    }

    // 仅用于唯一条件查询;底层 selectOne 在多行匹配时会抛异常。
    public static <T> Optional<T> one(BaseMapper<T> mapper, LambdaQueryWrapper<T> query) {
        return Optional.ofNullable(mapper.selectOne(query));
    }

    public static <T> boolean exists(BaseMapper<T> mapper, LambdaQueryWrapper<T> query) {
        return mapper.selectCount(query) > 0;
    }

    public static <T> long count(BaseMapper<T> mapper, LambdaQueryWrapper<T> query) {
        return mapper.selectCount(query);
    }
}
