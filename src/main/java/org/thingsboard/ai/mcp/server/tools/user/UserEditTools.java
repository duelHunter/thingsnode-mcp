package org.thingsboard.ai.mcp.server.tools.user;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.thingsboard.ai.mcp.server.rest.RestClientService;
import org.thingsboard.ai.mcp.server.tools.McpTools;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.security.Authority;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserEditTools implements McpTools {

    private final RestClientService clientService;

    /**
     * Edit an existing user (email, first/last name, phone, authority, additionalInfo).
     * Mirrors POST /api/user?sendActivationMail=false behavior from the UI.
     */
    @Tool(description = "Edit an existing user (email, firstName, lastName, phone, authority, additionalInfo JSON). "
            + "This mirrors the UI 'Apply changes' (POST /api/user?sendActivationMail=false). Returns the saved user JSON.")
    public String editUser(
            @ToolParam(description = "User UUID") @NotBlank String userId,
            @ToolParam(required = false, description = "Email / login") String email,
            @ToolParam(required = false, description = "First name") String firstName,
            @ToolParam(required = false, description = "Last name") String lastName,
            @ToolParam(required = false, description = "Phone") String phone,
            @ToolParam(required = false, description = "Authority: TENANT_ADMIN, CUSTOMER_USER, SYS_ADMIN, etc.") String authority,
            @ToolParam(required = false, description = "additionalInfo as JSON string (e.g. '{\"homeDashboardHideToolbar\":true}')") String additionalInfoJson,
            @ToolParam(required = false, description = "Send activation email (default false)") Boolean sendActivationMail
    ) {
        var client = clientService.getClient();
        boolean sendMail = (sendActivationMail != null) && sendActivationMail;

        try {
            // Load current user
            Optional<User> opt = client.getUserById(new UserId(UUID.fromString(userId)));
            if (opt.isEmpty()) {
                return JacksonUtil.toString(Map.of(
                        "ok", false,
                        "error", "UserNotFound",
                        "message", "No user for id " + userId
                ));
            }
            User u = opt.get();

            // Apply partial updates
            if (email != null && !email.isBlank()) {
                u.setEmail(email);
            }
            if (firstName != null) u.setFirstName(firstName);
            if (lastName  != null) u.setLastName(lastName);
            if (phone     != null) u.setPhone(phone);
            if (authority != null && !authority.isBlank()) {
                u.setAuthority(Authority.valueOf(authority));
            }
            if (additionalInfoJson != null && !additionalInfoJson.isBlank()) {
                u.setAdditionalInfo(JacksonUtil.toJsonNode(additionalInfoJson));
            }

            // Save (matches POST /api/user?sendActivationMail=false)
            // Your RestClient already supports saveUser(User, boolean) in most TB builds.
            User saved = client.saveUser(u, sendMail);

            return JacksonUtil.toString(Map.of("ok", true, "user", saved));
        } catch (Exception e) {
            return JacksonUtil.toString(Map.of(
                    "ok", false,
                    "error", e.getClass().getSimpleName(),
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Fetch the user (use this instead of /api/user/info/{id} if your RestClient doesn't expose that).
     */
    @Tool(description = "Get user by id (same object as POST /api/user returns).")
    public String getUser(
            @ToolParam(description = "User UUID") @NotBlank String userId
    ) {
        var client = clientService.getClient();
        try {
            var u = client.getUserById(new UserId(UUID.fromString(userId)));
            return JacksonUtil.toString(Map.of("ok", true, "user", u));
        } catch (Exception e) {
            return JacksonUtil.toString(Map.of(
                    "ok", false,
                    "error", e.getClass().getSimpleName(),
                    "message", e.getMessage()
            ));
        }
    }
}
