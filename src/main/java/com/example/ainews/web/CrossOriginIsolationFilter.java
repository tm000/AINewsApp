package com.example.ainews.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * スマホ内翻訳（ブラウザで動く PLaMo）を複数スレッドで速く動かすためのヘッダー。
 * credentialless なので、他サイトのサムネイル画像はそのまま表示できる。
 * 対応していないブラウザ（Safari など）はヘッダーを無視し、1 スレッドで動く。
 */
@Component
public class CrossOriginIsolationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        response.setHeader("Cross-Origin-Embedder-Policy", "credentialless");
        chain.doFilter(request, response);
    }
}
