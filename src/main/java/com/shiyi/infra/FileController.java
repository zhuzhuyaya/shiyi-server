package com.shiyi.infra;

import com.shiyi.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final MinioService minioService;

    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(minioService.upload(file));
    }
}