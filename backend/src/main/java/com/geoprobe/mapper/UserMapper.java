package com.geoprobe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.geoprobe.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Update("UPDATE users SET credits_remaining = credits_remaining - #{amount} " +
            "WHERE id = #{userId} AND credits_remaining >= #{amount}")
    int deductCreditsIfEnough(@Param("userId") String userId, @Param("amount") int amount);

    @Update("UPDATE users SET credits_remaining = credits_remaining + #{amount} WHERE id = #{userId}")
    int addCredits(@Param("userId") String userId, @Param("amount") int amount);

    @Update("UPDATE users SET default_organization_id = #{organizationId} WHERE id = #{userId}")
    int updateDefaultOrganizationId(@Param("userId") String userId, @Param("organizationId") String organizationId);
}
