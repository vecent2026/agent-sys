package com.trae.user.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.trae.user.dto.UserImportDTO;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
@Getter
public class HeaderValidationListener extends AnalysisEventListener<UserImportDTO> {

    private static final List<String> EXPECTED_HEADERS = Arrays.asList(
        "昵称", "手机号", "邮箱", "性别", "生日", "状态", "标签"
    );

    private final List<UserImportDTO> dataList = new ArrayList<>();
    private boolean headerValid = true;
    private String headerErrorMsg;
    private int rowIndex = 1;

    @Override
    public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
        List<String> actualHeaders = new ArrayList<>(headMap.values());
        
        if (actualHeaders.size() < EXPECTED_HEADERS.size()) {
            headerValid = false;
            headerErrorMsg = "表头列数不足，期望 " + EXPECTED_HEADERS.size() + " 列，实际 " + actualHeaders.size() + " 列，请使用正确的导入模板";
            log.warn("Header validation failed: {}", headerErrorMsg);
            return;
        }

        for (int i = 0; i < EXPECTED_HEADERS.size(); i++) {
            String expected = EXPECTED_HEADERS.get(i);
            String actual = actualHeaders.get(i);
            if (!expected.equals(actual)) {
                headerValid = false;
                headerErrorMsg = "表头错误，第 " + (i + 1) + " 列期望：" + expected + "，实际：" + actual + "，请使用正确的导入模板";
                log.warn("Header validation failed: {}", headerErrorMsg);
                return;
            }
        }

        log.info("Header validation passed");
    }

    @Override
    public void invoke(UserImportDTO data, AnalysisContext context) {
        if (!headerValid) {
            return;
        }
        data.setRowIndex(++rowIndex);
        dataList.add(data);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        log.info("Data reading completed. Total rows: {}", dataList.size());
    }
}
