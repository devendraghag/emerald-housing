package com.emerald.ai.chat;

import com.emerald.ai.security.SessionIdentityResponse;
import com.emerald.ai.chat.AssistantDtos.AskRequest;
import com.emerald.ai.chat.AssistantDtos.AskResponse;
import jakarta.validation.Valid;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {
    private final HousingFaqAssistant assistant;
    private final String sessionCookieName;

    public AssistantController(HousingFaqAssistant assistant,
                               @Value("${emerald.security.session-cookie-name:SESSION}") String sessionCookieName) {
        this.assistant = assistant;
        this.sessionCookieName = sessionCookieName;
    }

    @PostMapping("/questions")
    public AskResponse ask(@Valid @RequestBody AskRequest request,
                           @AuthenticationPrincipal SessionIdentityResponse actor,
                           HttpServletRequest httpRequest) {
        String sessionId = null;
        Cookie[] cookies = httpRequest.getCookies();
        if (cookies != null) for (Cookie cookie : cookies) if (sessionCookieName.equals(cookie.getName())) sessionId = cookie.getValue();
        return assistant.ask(actor.adminId().toString(), request, sessionId);
    }
}
