package com.example.ragdocqa.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UploadResult {

    private String docName;
    private int chunkCount;
    private int charCount;
}
