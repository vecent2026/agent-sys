package com.trae.admin.modules.log.service.impl;

import com.trae.admin.common.exception.BusinessException;
import com.trae.admin.modules.log.dto.LogQueryDto;
import com.trae.admin.modules.log.entity.SysLogDocument;
import com.trae.admin.modules.log.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LogServiceImpl implements LogService {

    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public Page<SysLogDocument> page(LogQueryDto queryDto) {
        Criteria criteria = new Criteria();
        
        if (queryDto.getUserId() != null) {
            criteria = criteria.and("userId").is(queryDto.getUserId());
        }
        if (queryDto.getTenantId() != null) {
            criteria = criteria.and("tenantId").is(queryDto.getTenantId());
        }
        if (queryDto.getIsPlatform() != null) {
            criteria = criteria.and("isPlatform").is(queryDto.getIsPlatform());
        }
        if (StringUtils.hasText(queryDto.getUsername())) {
            criteria = criteria.and("username").is(queryDto.getUsername());
        }
        if (StringUtils.hasText(queryDto.getModule())) {
            criteria = criteria.and("module").is(queryDto.getModule());
        }
        if (StringUtils.hasText(queryDto.getAction())) {
            criteria = criteria.and("action").is(queryDto.getAction());
        }
        if (StringUtils.hasText(queryDto.getStatus())) {
            criteria = criteria.and("status").is(queryDto.getStatus());
        }
        
        if (StringUtils.hasText(queryDto.getStartTime()) && StringUtils.hasText(queryDto.getEndTime())) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date start = sdf.parse(queryDto.getStartTime());
                Date end = sdf.parse(queryDto.getEndTime());
                // Use Date objects directly for between query
                criteria = criteria.and("createTime").between(start, end);
            } catch (ParseException e) {
                throw new BusinessException("时间格式错误，期望格式：yyyy-MM-dd HH:mm:ss");
            }
        }

        // Page index is 0-based in Spring Data, but usually 1-based in API DTOs.
        // LogQueryDto default page is 1.
        int pageIndex = queryDto.getPage() > 0 ? queryDto.getPage() - 1 : 0;
        PageRequest pageable = PageRequest.of(pageIndex, queryDto.getSize(), Sort.by(Sort.Direction.DESC, "createTime"));
        
        CriteriaQuery query = new CriteriaQuery(criteria);
        query.setPageable(pageable);

        SearchHits<SysLogDocument> searchHits = elasticsearchOperations.search(query, SysLogDocument.class);
        
        List<SysLogDocument> list = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());
                
        return new PageImpl<>(list, pageable, searchHits.getTotalHits());
    }
}
