package com.rag.lecturelens.util;

import org.springframework.stereotype.Component;

@Component
public class ValidateExtension {
    private static final String[] ALLOWED_DOC_EXT = {"pdf", "ppt", "pptx", "doc", "docx"};
    private static final String[] ALLOWED_AUDIO_EXT = {"mp4", "mp3", "avi"};
    public void validateDocumentExtension(String ext) {
        if (!isIn(ext, ALLOWED_DOC_EXT)) {
            throw new IllegalArgumentException("지원하지 않는 문서 확장자입니다: " + ext);
        }
    }

    public void validateAudioExtension(String ext) {
        if (!isIn(ext, ALLOWED_AUDIO_EXT)) {
            throw new IllegalArgumentException("지원하지 않는 음성/영상 확장자입니다: " + ext);
        }
    }

    private boolean isIn(String ext, String[] allowed) {
        for (String a : allowed) {
            if (a.equalsIgnoreCase(ext)) return true;
        }
        return false;
    }
}
