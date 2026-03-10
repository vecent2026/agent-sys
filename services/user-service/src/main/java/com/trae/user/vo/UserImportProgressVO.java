package com.trae.user.vo;

import lombok.Data;
import java.util.List;

@Data
public class UserImportProgressVO {
    private String status;
    private Integer total;
    private Integer processed;
    private Integer success;
    private Integer failed;
    private Integer progress;
    private List<ImportError> errors;

    @Data
    public static class ImportError {
        private Integer row;
        private String mobile;
        private String reason;
    }

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
}
