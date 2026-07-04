package com.autopilot.analyzer.model;

public enum FrameworkType {
    // Frontend
    REACT_CRA,
    REACT_VITE,
    VUE_CLI,
    VUE_VITE,
    ANGULAR,
    NEXTJS,
    NUXT,
    ASTRO,
    REMIX,
    SVELTE,
    SVELTEKIT,
    SOLIDJS,
    SOLIDSTART,
    QWIK,
    PREACT,
    LIT,
    PARCEL,
    GATSBY,
    DOCUSAURUS,
    ELEVENTY,
    VITE_VANILLA,
    VANILLA_HTML_CSS_JS,

    // Backend - Java
    SPRING_BOOT,
    QUARKUS,
    MICRONAUT,

    // Backend - Node
    EXPRESS,
    NESTJS,
    FASTIFY,
    KOA,
    HONO,
    ADONIS,

    // Backend - Python
    DJANGO,
    FLASK,
    FASTAPI,

    // Backend - PHP
    LARAVEL,
    SYMFONY,

    // Backend - Go, Rust, .NET, Ruby
    GO,
    RUST,
    DOTNET,
    RUBY_ON_RAILS,

    // System
    DOCKER,
    GENERIC
}
