package com.eventhub.api.auth;

import com.eventhub.api.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段 1 的验收测试：注册 → 登录 → 刷新 → 登出 全链路，
 * 跑在真实 MySQL / Redis 上。
 */
class AuthFlowIT extends AbstractIntegrationTest {

    private static final String REGISTER = "/api/v1/auth/register";
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String REFRESH = "/api/v1/auth/refresh";
    private static final String LOGOUT = "/api/v1/auth/logout";
    private static final String ME = "/api/v1/me";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    @DisplayName("注册签发令牌，凭令牌可读取自己的资料，且响应不含密码摘要")
    void register_then_read_own_profile() throws Exception {
        String email = "alice@example.com";
        TokenPair tokens = register(email, "s3cretPass1", "爱丽丝");

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();

        mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.nickname").value("爱丽丝"))
                .andExpect(jsonPath("$.data.roles[0]").value("USER"))
                // 密码摘要绝不能出现在任何响应里
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("同一邮箱重复注册返回 409，且不区分大小写")
    void duplicate_email_is_rejected_case_insensitively() throws Exception {
        register("bob@example.com", "s3cretPass1", "Bob");

        mvc.perform(post(REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("BOB@example.com", "s3cretPass1", "Bob2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    @DisplayName("弱密码被 Bean Validation 拦下，字段级错误回传在 data 里")
    void weak_password_fails_validation() throws Exception {
        mvc.perform(post(REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("carol@example.com", "onlyletters", "Carol")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data.password").exists());
    }

    @Test
    @DisplayName("不带令牌访问受保护接口返回 401，响应结构与其他接口一致")
    void protected_endpoint_requires_token() throws Exception {
        mvc.perform(get(ME))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("密码错误返回 401，且错误码与「邮箱不存在」相同以免账号枚举")
    void wrong_password_and_unknown_email_share_one_error_code() throws Exception {
        register("dave@example.com", "s3cretPass1", "Dave");

        mvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("dave@example.com", "wrongPass123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("nobody@example.com", "whatever123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("刷新令牌轮换：新令牌可用，旧令牌被重用则判定泄露并吊销全部会话")
    void refresh_rotates_and_detects_reuse() throws Exception {
        TokenPair first = register("erin@example.com", "s3cretPass1", "Erin");

        TokenPair second = refresh(first.refreshToken());
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());

        // 新访问令牌有效
        mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + second.accessToken()))
                .andExpect(status().isOk());

        // 旧刷新令牌已被吊销，再次使用必须被拒
        mvc.perform(post(REFRESH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(first.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));

        // 泄露处置：该用户全部刷新令牌被吊销，连最新的那个也不再可用
        mvc.perform(post(REFRESH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(second.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("登出后 access token 立即进入黑名单，refresh token 全部吊销")
    void logout_revokes_both_tokens() throws Exception {
        TokenPair tokens = register("frank@example.com", "s3cretPass1", "Frank");

        mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk());

        mvc.perform(post(LOGOUT).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk());

        // 同一个 access token 在剩余寿命内也必须失效
        mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                .andExpect(status().isUnauthorized());

        // refresh token 同样不再可用
        mvc.perform(post(REFRESH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(tokens.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 辅助方法 ----------

    private record TokenPair(String accessToken, String refreshToken) {
    }

    private TokenPair register(String email, String password, String nickname) throws Exception {
        MvcResult result = mvc.perform(post(REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, password, nickname)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        return tokensFrom(result);
    }

    private TokenPair refresh(String refreshToken) throws Exception {
        MvcResult result = mvc.perform(post(REFRESH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isOk())
                .andReturn();
        return tokensFrom(result);
    }

    private TokenPair tokensFrom(MvcResult result) throws Exception {
        JsonNode data = json.readTree(result.getResponse().getContentAsString()).path("data");
        return new TokenPair(data.path("accessToken").asText(), data.path("refreshToken").asText());
    }

    private static String registerBody(String email, String password, String nickname) {
        return """
                {"email":"%s","password":"%s","nickname":"%s"}
                """.formatted(email, password, nickname);
    }

    private static String loginBody(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    private static String refreshBody(String refreshToken) {
        return """
                {"refreshToken":"%s"}
                """.formatted(refreshToken);
    }
}
