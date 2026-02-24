package com.trae.admin.log.repository;

import com.trae.admin.log.entity.SysLogDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SysLogRepository extends ElasticsearchRepository<SysLogDocument, String> {
}
