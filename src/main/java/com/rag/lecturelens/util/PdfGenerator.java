package com.rag.lecturelens.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PdfGenerator {

    private static final float MARGIN = 50f;

    // 🔠 폰트 사이즈
    private static final float BODY_FONT_SIZE = 12f;
    private static final float H3_FONT_SIZE   = 14f;
    private static final float H2_FONT_SIZE   = 18f;
    private static final float H1_FONT_SIZE   = 22f;

    // 줄 간격
    private static final float BODY_LEADING  = 15f;
    private static final float H3_LEADING    = 18f;
    private static final float H2_LEADING    = 20f;
    private static final float H1_LEADING    = 22f;

    // 제목 아래에 최소 같이 있어야 하는 “다음 내용” 높이 (본문 2줄 정도 여유)
    private static final float MIN_FOLLOWING_HEIGHT = BODY_LEADING * 2;

    public byte[] generate(String markdownText) {
        PDDocument document = new PDDocument();
        PDPageContentStream cs = null;

        try {
            PDFont regularFont = PDType0Font.load(
                    document,
                    new ClassPathResource("fonts/NotoSansKR-Regular.ttf").getInputStream(),
                    true
            );
            PDFont boldFont = PDType0Font.load(
                    document,
                    new ClassPathResource("fonts/NotoSansKR-Bold.ttf").getInputStream(),
                    true
            );

            PDRectangle pageSize = PDRectangle.A4;
            float startY = pageSize.getHeight() - MARGIN;
            float usableWidth = pageSize.getWidth() - 2 * MARGIN;

            PDPage page = new PDPage(pageSize);
            document.addPage(page);

            cs = new PDPageContentStream(document, page);
            cs.beginText();
            cs.newLineAtOffset(MARGIN, startY);

            float y = startY;

            String[] lines = markdownText.split("\n");

            // ✅ 인덱스 기반 루프 (제목 다음 줄 미리 고려하기 위함)
            for (int i = 0; i < lines.length; i++) {

                String rawLine = lines[i];

                // 1) 이 줄에 ** 있는지 먼저 체크 (bold 여부 판단용)
                boolean hasBoldMarkup = rawLine.contains("**");

                // 2) inline 마크다운 정리
                String cleanedLine = cleanInlineMarkdown(rawLine);
                String trimmedForHr = cleanedLine.trim();

                // 2-1) --- 같은 구분선은 완전히 스킵
                if (trimmedForHr.matches("^-{3,}$")) {
                    continue;
                }

                // 3) 스타일 파싱 (H1/H2/H3/BULLET/BODY)
                LineStyle style = parseLineStyle(cleanedLine);

                // 4) bold 라인이면 Body/Bullet에 한해서 Bold 적용
                if (hasBoldMarkup &&
                        (style.type == LineType.BODY || style.type == LineType.BULLET)) {
                    style.bold = true;
                }

                String content = style.content;

                // 빈 줄이면 한 줄 띄우기
                if (content.isBlank()) {
                    y = newLine(cs, y, style.leading);
                    continue;
                }

                // 5) 단어 단위 줄바꿈
                List<String> wrappedLines = wrapText(
                        content,
                        regularFont,
                        style.fontSize,
                        usableWidth
                );

                // 6) ✨ 제목 widow/orphan 방지:
                //    제목(H1/H2/H3)인 경우,
                //    "제목 + 최소 다음 내용 높이"를 한 번에 고려해서
                //    현재 페이지에 공간이 부족하면 → 제목을 새 페이지로 보냄
                if (style.type == LineType.H1 ||
                        style.type == LineType.H2 ||
                        style.type == LineType.H3) {

                    float remainingHeight = y - MARGIN;
                    float headingHeight   = style.leading * wrappedLines.size();
                    float requiredHeight  = headingHeight + MIN_FOLLOWING_HEIGHT;

                    if (remainingHeight < requiredHeight) {
                        // 페이지 넘기기
                        cs.endText();
                        cs.close();

                        page = new PDPage(pageSize);
                        document.addPage(page);
                        cs = new PDPageContentStream(document, page);
                        cs.beginText();
                        y = startY;
                        cs.newLineAtOffset(MARGIN, y);
                    }
                }

                // 7) 실제 출력
                cs.setNonStrokingColor(style.r, style.g, style.b);
                cs.setLeading(style.leading);

                for (String line : wrappedLines) {

                    // 페이지 끝이면 새 페이지
                    if (y <= MARGIN) {
                        cs.endText();
                        cs.close();

                        page = new PDPage(pageSize);
                        document.addPage(page);
                        cs = new PDPageContentStream(document, page);
                        cs.beginText();
                        y = startY;
                        cs.newLineAtOffset(MARGIN, y);

                        cs.setNonStrokingColor(style.r, style.g, style.b);
                        cs.setLeading(style.leading);
                    }

                    PDFont fontToUse = style.bold ? boldFont : regularFont;
                    showText(cs, line, fontToUse, style.fontSize);
                    y = newLine(cs, y, style.leading);
                }
            }

            cs.endText();
            cs.close();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("PDF 생성 실패", e);
        } finally {
            try {
                if (cs != null) cs.close();
            } catch (IOException ignored) {}
            try {
                document.close();
            } catch (IOException ignored) {}
        }
    }

    // 줄바꿈 + y 좌표 갱신
    private float newLine(PDPageContentStream cs, float currentY, float leading) throws IOException {
        cs.newLine();
        return currentY - leading;
    }

    /**
     * 마크다운 스타일 파싱
     *  - #     → H1 (제목)
     *  - ##    → H2 (큰 소제목)
     *  - ###   → H3 (작은 소제목)
     *  - ####  → H3 으로 통합
     *  - -, *  → • bullet 로 변환
     */
    private LineStyle parseLineStyle(String raw) {
        String trimmed = raw.trim();

        // H1
        if (trimmed.startsWith("# ")) {
            return new LineStyle(
                    LineType.H1,
                    trimmed.substring(2).trim(),
                    H1_FONT_SIZE,
                    H1_LEADING,
                    51,  51,  153
            );
        }
        // H2
        else if (trimmed.startsWith("## ")) {
            return new LineStyle(
                    LineType.H2,
                    trimmed.substring(3).trim(),
                    H2_FONT_SIZE,
                    H2_LEADING,
                    0,   128, 128
            );
        }
        // H3 (###, #### 모두)
        else if (trimmed.startsWith("### ")) {
            return new LineStyle(
                    LineType.H3,
                    trimmed.substring(4).trim(),
                    H3_FONT_SIZE,
                    H3_LEADING,
                    255, 140, 0
            );
        } else if (trimmed.startsWith("#### ")) {
            return new LineStyle(
                    LineType.H3,
                    trimmed.substring(5).trim(),
                    H3_FONT_SIZE,
                    H3_LEADING,
                    255, 140, 0
            );
        }
        // Bullet
        else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            String text = trimmed.substring(2).trim();
            return new LineStyle(
                    LineType.BULLET,
                    "• " + text,
                    BODY_FONT_SIZE,
                    BODY_LEADING,
                    33, 33, 33
            );
        }
        // 일반 본문
        else {
            return new LineStyle(
                    LineType.BODY,
                    trimmed,
                    BODY_FONT_SIZE,
                    BODY_LEADING,
                    33, 33, 33
            );
        }
    }

    /**
     * inline 마크다운 제거 / 변환
     *  - **굵게**  → 굵게  (굵게 여부는 rawLine.contains("**") 로 별도 체크)
     *  - __굵게__  → 굵게
     *  - `코드`    → 코드
     *  - [텍스트](url) → 텍스트 (url)
     */
    private String cleanInlineMarkdown(String line) {
        if (line == null || line.isEmpty()) return "";

        String result = line;

        result = result.replace("**", "");
        result = result.replace("__", "");
        result = result.replace("`", "");

        Pattern linkPattern = Pattern.compile("\\[(.+?)]\\((.+?)\\)");
        Matcher m = linkPattern.matcher(result);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String label = m.group(1);
            String url = m.group(2);
            m.appendReplacement(sb, label + " (" + url + ")");
        }
        m.appendTail(sb);

        return sb.toString();
    }

    // 단어 단위 줄바꿈 (오른쪽 안 짤리게)
    private List<String> wrapText(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            result.add("");
            return result;
        }

        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder(words[0]);

        for (int i = 1; i < words.length; i++) {
            String candidate = line + " " + words[i];
            float size = font.getStringWidth(candidate) / 1000 * fontSize;

            if (size > maxWidth) {
                result.add(line.toString());
                line = new StringBuilder(words[i]);
            } else {
                line.append(" ").append(words[i]);
            }
        }
        result.add(line.toString());
        return result;
    }

    // 텍스트 한 줄 출력
    private void showText(PDPageContentStream cs,
                          String text,
                          PDFont font,
                          float fontSize) throws IOException {
        cs.setFont(font, fontSize);
        cs.showText(text);
    }

    // 라인 스타일 정보
    private static class LineStyle {
        LineType type;
        String content;
        float fontSize;
        float leading;
        int r, g, b;
        boolean bold = false;

        public LineStyle(LineType type, String content, float fontSize, float leading,
                         int r, int g, int b) {
            this.type = type;
            this.content = content;
            this.fontSize = fontSize;
            this.leading = leading;
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    private enum LineType {
        H1, H2, H3, BULLET, BODY
    }
}
