package com.rag.lecturelens.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class DownloadFile {
    String fileName;
    byte[] fileBytes;
}
