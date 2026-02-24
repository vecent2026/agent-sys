package com.trae.admin.modules.log.repository;

import com.trae.admin.modules.log.entity.SysLogDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SysLogRepository extends ElasticsearchRepository<SysLogDocument, String> {
}
