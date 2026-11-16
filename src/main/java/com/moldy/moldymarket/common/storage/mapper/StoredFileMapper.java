package com.moldy.moldymarket.common.storage.mapper;

import com.moldy.moldymarket.common.storage.dto.response.FileResponse;
import com.moldy.moldymarket.common.storage.entity.StoredFile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface StoredFileMapper {

    @Mapping(target = "fileId", source = "id")
    @Mapping(target = "fileSize", source = "actualFileSize")
    FileResponse toResponse(StoredFile storedFile);
}