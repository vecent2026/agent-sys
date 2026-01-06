package com.trae.admin.modules.log.service;

import com.trae.admin.modules.log.dto.LogQueryDto;
import com.trae.admin.modules.log.entity.SysLogDocument;
import org.springframework.data.domain.Page;

/**
 * 日志服务接口
 */
public interface LogService {

    /**
     * 分页查询日志
     */
    Page<SysLogDocument> page(LogQueryDto queryDto);
}
