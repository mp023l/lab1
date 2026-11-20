package com.example.auction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={
 "spring.datasource.url=jdbc:h2:mem:jwtlab5;DB_CLOSE_DELAY=-1",
 "jwt.secret=K0Jm9CNQKffFjYI8wQCsUtO2f6MtM6t6P1ldSvrsfLs=",
 "auction.admin-password=AdminPassword12!"})
@AutoConfigureMockMvc
class JwtIntegrationTest {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Test void registerRotateAndRejectReplay() throws Exception {
  String username="test"+UUID.randomUUID().toString().replace("-","").substring(0,12);
  String body="{\"username\":\""+username+"\",\"name\":\"Tester\",\"email\":\""+username+"@example.org\",\"password\":\"SecurePassword12!\"}";
  String registered=mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
  JsonNode first=json.readTree(registered);
  assertFalse(first.path("accessToken").asText().isBlank());
  assertFalse(first.path("refreshToken").asText().isBlank());
  mvc.perform(get("/api/security/me").header("Authorization","Bearer "+first.path("accessToken").asText()))
    .andExpect(status().isOk());
  mvc.perform(get("/api/security/me").header("Authorization","Bearer "+first.path("refreshToken").asText()))
    .andExpect(status().isUnauthorized());
  String refresh="{\"refreshToken\":\""+first.path("refreshToken").asText()+"\"}";
  String updated=mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refresh))
    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertNotEquals(first.path("refreshToken").asText(),json.readTree(updated).path("refreshToken").asText());
  mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(refresh))
    .andExpect(status().isUnauthorized());
  mvc.perform(get("/api/security/me")).andExpect(status().isUnauthorized());
 }
}
