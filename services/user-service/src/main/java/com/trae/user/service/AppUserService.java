package com.trae.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.trae.user.dto.AppUserQueryDTO;
import com.trae.user.dto.BatchTagDTO;
import com.trae.user.dto.UserStatusDTO;
import com.trae.user.entity.AppUser;
import com.trae.user.vo.AppUserVO;
import com.trae.user.vo.UserFieldValueVO;
import com.trae.user.vo.UserImportProgressVO;
import com.trae.user.vo.UserImportValidateResultVO;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface AppUserService extends IService<AppUser> {
    Page<AppUserVO> getUserPage(AppUserQueryDTO queryDTO);
    AppUserVO getUserDetail(Long id);
    void updateUserStatus(Long id, UserStatusDTO statusDTO);
    void assignUserTags(Long userId, List<Long> tagIds);
    void batchAddTags(BatchTagDTO batchTagDTO);
    void batchRemoveTags(BatchTagDTO batchTagDTO);
    List<UserFieldValueVO> getUserFieldValues(Long userId);
    void updateUserFieldValues(Long userId, List<UserFieldValueVO> fieldValues);
    void exportUsers(AppUserQueryDTO queryDTO, HttpServletResponse response);
    
    void downloadImportTemplate(HttpServletResponse response);
    UserImportValidateResultVO validateImportData(MultipartFile file);
    void downloadValidateResult(String taskId, HttpServletResponse response);
    String executeImport(String taskId);
    UserImportProgressVO getImportProgress(String importTaskId);
}
