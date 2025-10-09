package org.thingsboard.ai.mcp.server.tools.demo;

import jakarta.validation.constraints.NotBlank;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class MyTools {

    @Tool(description = "Echo back the provided text.")
    public String echo(
            @ToolParam(description = "Any text to echo back")
            @NotBlank String text
    ) {
        return text;
    }

    @Tool(description = "Reverse a string.")
    public String reverse(
            @ToolParam(description = "Text to reverse")
            @NotBlank String text
    ) {
        return new StringBuilder(text).reverse().toString();
    }

    @Tool(description = "Add two integers and return the sum.")
    public int add(
            @ToolParam(description = "First integer") int a,
            @ToolParam(description = "Second integer") int b
    ) {
        return a + b;
    }
}
