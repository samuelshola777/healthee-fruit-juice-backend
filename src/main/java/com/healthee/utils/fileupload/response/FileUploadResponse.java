package com.healthee.utils.fileupload.response;


import com.healthee.utils.fileupload.FileUploadCategory;
import com.healthee.utils.fileupload.FileUsageType;

public record FileUploadResponse(
        String fileUrl,
        String publicId,
        String originalFileName,
        String format,
        String resourceType,
        Long fileSizeBytes,
        FileUploadCategory category,
        FileUsageType usageType
) {
}