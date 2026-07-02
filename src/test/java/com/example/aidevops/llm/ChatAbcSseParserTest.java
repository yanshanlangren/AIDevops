package com.example.aidevops.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatAbcSseParserTest {

    private final ChatAbcSseParser parser = new ChatAbcSseParser(new ObjectMapper());

    @Test
    void concatenatesChunkContentWhenMessageEventIsAbsent() throws Exception {
        String stream = "event:chunk\n"
                + "data:{\"content\":\"{\\\"root_cause_hypothesis\\\":\\\"\"}\n\n"
                + "event:chunk\n"
                + "data:{\"content\":\"原因\\\"}\"}\n\n"
                + "event:done\n"
                + "data:{\"status\":\"success\",\"rescode\":\"FAIAG0000\"}\n\n";

        String content = parser.parse(input(stream), "FAIAG0000");

        assertEquals("{\"root_cause_hypothesis\":\"原因\"}", content);
    }

    @Test
    void prefersCompleteMessageContentOverChunks() throws Exception {
        String stream = "event:chunk\n"
                + "data:{\"content\":\"partial\"}\n\n"
                + "event:message\n"
                + "data:{\"content\":\"complete\"}\n\n"
                + "event:done\n"
                + "data:{\"status\":\"success\",\"resCode\":\"FAIAG0000\"}\n\n";

        assertEquals("complete", parser.parse(input(stream), "FAIAG0000"));
    }

    @Test
    void rejectsUnsuccessfulDoneEvent() {
        String stream = "event:done\n"
                + "data:{\"status\":\"failed\",\"rescode\":\"ERROR\"}\n\n";

        assertThrows(IllegalStateException.class,
                () -> parser.parse(input(stream), "FAIAG0000"));
    }

    @Test
    void ignoresChatStartedAndStopsReadingAfterDone() throws Exception {
        String stream = "event:chat_started\n"
                + "data:non-json-start-marker\n\n"
                + "event:message\n"
                + "data:{\"content\":\"complete\"}\n\n"
                + "event:done\n"
                + "data:{\"status\":\"success\",\"rescode\":\"FAIAG0000\"}\n\n"
                + "event:chunk\n"
                + "data:invalid-json-that-must-not-be-read\n\n";

        assertEquals("complete", parser.parse(input(stream), "FAIAG0000"));
    }

    @Test
    void ignoresReasoningWhenBuildingModelContent() throws Exception {
        String stream = "event:chunk\n"
                + "data:{\"content\":\"result\",\"additional_kwargs\":{\"reasoning\":\"first\\n\"}}\n\n"
                + "event:chunk\n"
                + "data:{\"content\":\" content\",\"additional_kwargs\":{\"reasoning\":\"second\"}}\n\n"
                + "event:done\n"
                + "data:{\"status\":\"success\",\"rescode\":\"FAIAG0000\"}\n\n";

        ByteArrayOutputStream chunkOutput = new ByteArrayOutputStream();
        ChatAbcSseParser streamingParser = new ChatAbcSseParser(
                new ObjectMapper(), true, "session-test", new PrintStream(chunkOutput, true, "UTF-8"));

        assertEquals("result content", streamingParser.parse(input(stream), "FAIAG0000"));
        assertEquals("first second" + System.lineSeparator(),
                new String(chunkOutput.toByteArray(), StandardCharsets.UTF_8));
    }

    private ByteArrayInputStream input(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
