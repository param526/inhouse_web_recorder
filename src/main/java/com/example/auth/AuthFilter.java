package com.example.auth;

import io.jsonwebtoken.Claims;
import spark.Filter;
import spark.Request;
import spark.Response;

import static spark.Spark.halt;

public class AuthFilter implements Filter {

    @Override
    public void handle(Request request, Response response) {
        String path = request.pathInfo();

        // Public routes that don't require authentication
        if (isPublicRoute(path)) {
            return;
        }

        String authHeader = request.headers("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            halt(401, "{\"error\":\"Authentication required\"}");
            return;
        }

        String token = authHeader.substring(7);
        Claims claims = JwtUtil.validateToken(token);
        if (claims == null) {
            halt(401, "{\"error\":\"Invalid or expired token\"}");
            return;
        }

        // Store user info in request attributes for downstream use
        request.attribute("userId", JwtUtil.getUserId(claims));
        request.attribute("username", JwtUtil.getUsername(claims));
        request.attribute("role", JwtUtil.getRole(claims));
    }

    private boolean isPublicRoute(String path) {
        return path.equals("/")
                || path.equals("/index.html")
                || path.equals("/app.html")
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/assets/")
                || path.equals("/api/auth/login")
                || path.equals("/api/auth/register")
                || path.equals("/api/health")
                || path.equals("/favicon.ico")
                // Run reports, screenshots & videos (accessed via browser directly)
                || path.matches("/api/runs/\\d+/report")
                || path.matches("/api/runs/\\d+/video")
                || path.startsWith("/api/screenshots/")
                // Static file extensions
                || path.endsWith(".html")
                || path.endsWith(".css")
                || path.endsWith(".js")
                || path.endsWith(".png")
                || path.endsWith(".jpg")
                || path.endsWith(".ico")
                || path.endsWith(".svg")
                || path.endsWith(".woff2")
                || path.endsWith(".woff");
    }

    public static Long getUserId(Request request) {
        return request.attribute("userId");
    }

    public static String getUsername(Request request) {
        return request.attribute("username");
    }

    public static String getRole(Request request) {
        return request.attribute("role");
    }

    public static boolean isAdmin(Request request) {
        return "ADMIN".equals(getRole(request));
    }
}
