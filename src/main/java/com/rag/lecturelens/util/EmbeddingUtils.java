package com.rag.lecturelens.util;

public class EmbeddingUtils {

    // float[] → "[0.1,0.2,...]" 형태로
    public static String toPgVectorLiteral(float[] vec) {
        if (vec == null || vec.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            sb.append(vec[i]);
            if (i < vec.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}
