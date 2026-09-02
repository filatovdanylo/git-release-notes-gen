package me.automatedgitdiffnotesgenerator.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class NoteGenerationService {
    private final ChatClient chatClient;
    public NoteGenerationService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String generate(String diffContext) {
        return chatClient.prompt()
                .user(diffContext)
                .call()
                .content();
    }
}
