package com.example.reservation.payment.api;

import com.example.reservation.payment.domain.exception.InvalidWebhookSignatureException;
import com.example.reservation.payment.service.StripeService;
import com.example.reservation.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StripeWebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class StripeWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StripeService stripeService;

    @Test
    @DisplayName("POST /api/webhooks/stripe - Returns 200 OK on valid signature and processed event")
    void handleWebhook_ValidSignature_Returns200() throws Exception {
        String payload = "{\"id\":\"evt_12345\",\"type\":\"payment_intent.succeeded\"}";
        String sigHeader = "t=1700000000,v1=valid_hmac_signature";

        doNothing().when(stripeService).processWebhookEvent(any(), eq(sigHeader));

        mockMvc.perform(post("/api/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", sigHeader)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(true));
    }

    @Test
    @DisplayName("POST /api/webhooks/stripe - Returns 400 Bad Request on bad or tampered signature")
    void handleWebhook_BadSignature_Returns400() throws Exception {
        String payload = "{\"id\":\"evt_tampered\",\"type\":\"payment_intent.succeeded\"}";
        String badSigHeader = "t=1700000000,v1=tampered_signature";

        doThrow(new InvalidWebhookSignatureException("Stripe signature verification failed"))
                .when(stripeService).processWebhookEvent(any(), eq(badSigHeader));

        mockMvc.perform(post("/api/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", badSigHeader)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("INVALID_WEBHOOK_SIGNATURE"));
    }

    @Test
    @DisplayName("POST /api/webhooks/stripe - Returns 400 Bad Request when Stripe-Signature header is missing")
    void handleWebhook_MissingSignature_Returns400() throws Exception {
        String payload = "{\"id\":\"evt_no_header\",\"type\":\"payment_intent.succeeded\"}";

        doThrow(new InvalidWebhookSignatureException("Missing Stripe-Signature header"))
                .when(stripeService).processWebhookEvent(any(), eq(null));

        mockMvc.perform(post("/api/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value("INVALID_WEBHOOK_SIGNATURE"));
    }
}
