package com.shiyi.infra;

import com.shiyi.common.BusinessException;
import com.shiyi.config.MinioProperties;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;
    private final MinioProperties properties;

    /** 上传图片，返回可公开访问的 URL */
    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择文件");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        if (dot < 0) {
            throw new BusinessException("文件缺少扩展名");
        }
        String ext = original.substring(dot + 1).toLowerCase();
        // 白名单制：只放行认识的类型，而不是拉黑认识的危险类型
        if (!List.of("jpg", "jpeg", "png", "webp").contains(ext)) {
            throw new BusinessException("仅支持 jpg/png/webp 图片");
        }

        // 对象名：按日期分目录 + UUID，防覆盖、防猜路径、原始文件名不落盘
        String objectName = LocalDate.now().toString().replace("-", "/")
                + "/" + UUID.randomUUID().toString().replace("-", "") + "." + ext;

        try (InputStream in = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectName)
                    .stream(in, file.getSize(), -1)   // 已知大小 → 单次 PUT；-1 表示分片大小自动
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            log.error("MinIO 上传失败", e);
            throw new BusinessException("上传失败，请稍后重试");
        }
        return properties.getEndpoint() + "/" + properties.getBucket() + "/" + objectName;
    }
}