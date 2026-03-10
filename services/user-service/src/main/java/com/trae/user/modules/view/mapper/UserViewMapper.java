package com.trae.user.modules.view.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trae.user.modules.view.entity.UserView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserViewMapper extends BaseMapper<UserView> {
    @Select("SELECT * FROM user_views WHERE user_id = #{userId} ORDER BY is_default DESC, order_no ASC, created_at ASC")
    List<UserView> selectByUserId(Long userId);
}
