package com.educater.auth;

import com.educater.config.ConfigService;
import com.educater.db.MongoService;
import com.educater.model.User;
import java.util.Base64;

public class AuthService {
    private final MongoService mongo;

    public AuthService(MongoService mongo) {
        this.mongo = mongo;
    }

    public boolean isAdminLogin(String email, char[] password) {
        String adminEmail = ConfigService.getAdminEmail();
        String adminPass = ConfigService.getAdminPassword();
        boolean ok = adminEmail.equals(email) && new String(password).equals(adminPass);
        java.util.Arrays.fill(password, '\0');
        return ok;
    }

    public boolean signupTeacher(String email, char[] password) throws Exception {
        if (email == null || email.isBlank() || password == null || password.length == 0) return false;
        // Check if user exists
        if (mongo.findUserByEmail(email) != null) return false;

        byte[] salt = PasswordUtil.generateSalt();
        byte[] hash = PasswordUtil.hashPassword(password, salt);
        String saltB = Base64.getEncoder().encodeToString(salt);
        String hashB = Base64.getEncoder().encodeToString(hash);
        User u = new User();
        u.email = email;
        u.passwordHashBase64 = hashB;
        u.saltBase64 = saltB;
        return mongo.createUser(u);
    }

    public boolean resetTeacherPassword(String email, char[] newPassword) throws Exception {
        if (email == null || email.isBlank() || newPassword == null || newPassword.length == 0) return false;
        User u = mongo.findUserByEmail(email);
        if (u == null) return false;

        byte[] salt = PasswordUtil.generateSalt();
        byte[] hash = PasswordUtil.hashPassword(newPassword, salt);
        String saltB = Base64.getEncoder().encodeToString(salt);
        String hashB = Base64.getEncoder().encodeToString(hash);
        return mongo.updateUserPassword(email, hashB, saltB);
    }

    public boolean loginTeacher(String email, char[] password) throws Exception {
        User u = mongo.findUserByEmail(email);
        if (u == null) return false;
        return PasswordUtil.verifyPassword(password, u.passwordHashBase64, u.saltBase64);
    }

    public String loginTeacherSupabase(String email, char[] password) throws Exception {
        String url = ConfigService.getSupabaseUrl();
        String anonKey = ConfigService.getSupabaseAnonKey();
        if (url == null || url.isBlank() || anonKey == null || anonKey.isBlank()) {
            throw new Exception("Supabase URL or Anon Key is not configured.");
        }

        com.google.gson.JsonObject payload = new com.google.gson.JsonObject();
        payload.addProperty("email", email);
        payload.addProperty("password", new String(password));

        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder().build();
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url + "/auth/v1/token?grant_type=password"))
                .header("Content-Type", "application/json")
                .header("apikey", anonKey)
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        java.net.http.HttpResponse<String> res = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 200 && res.statusCode() < 300) {
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(res.body()).getAsJsonObject();
            if (json.has("access_token")) {
                return json.get("access_token").getAsString();
            }
        } else {
            throw new Exception("Supabase login failed. Status code: " + res.statusCode());
        }
        return null;
    }
}