package com.autopilot.service.deployment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AssetPathPatchingTest {

    @Test
    public void testPatchAbsolutePaths(@TempDir Path tempDir) throws Exception {
        // Create standard public asset folder structure
        Path publicDir = tempDir.resolve("public");
        Files.createDirectories(publicDir);
        
        Path leadersDir = publicDir.resolve("leaders");
        Files.createDirectories(leadersDir);
        
        // Create dummy asset files
        Files.writeString(leadersDir.resolve("presdent.jpg"), "dummy image content");
        Files.writeString(leadersDir.resolve("vice_pres.jpg"), "dummy image content");
        Files.writeString(publicDir.resolve("logo.svg"), "dummy svg content");
        
        // Create source code file to be patched
        Path srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
        
        Path pageFile = srcDir.resolve("page.tsx");
        String originalCode = """
                'use client';
                import React from 'react';
                
                const leaders = [
                  { name: 'Ayush', img: '/leaders/presdent.jpg' },
                  { name: 'Mehek', img: '/leaders/vice_pres.jpg' }
                ];
                
                export default function Page() {
                  return (
                    <div>
                      <img src="/logo.svg" alt="logo" />
                      <img src={"/leaders/presdent.jpg"} />
                      <a href="/gallery">Gallery</a>
                      <div style={{ backgroundImage: 'url("/logo.svg")' }} />
                    </div>
                  );
                }
                """;
        Files.writeString(pageFile, originalCode);
        
        // Instantiate patcher and trigger patching
        FrontendPatcherService patcher = new FrontendPatcherService();
        patcher.patchFrontend(tempDir, "/app-f46aa078", null);
        
        // Read patched code
        String patchedCode = Files.readString(pageFile);
        
        // Verify HTML attributes are patched
        assertTrue(patchedCode.contains("src=\"/app-f46aa078/logo.svg\""));
        assertTrue(patchedCode.contains("href=\"/app-f46aa078/gallery\""));
        
        // Verify JavaScript object property paths are patched
        assertTrue(patchedCode.contains("img: '/app-f46aa078/leaders/presdent.jpg'"));
        assertTrue(patchedCode.contains("img: '/app-f46aa078/leaders/vice_pres.jpg'"));
        
        // Verify JSX curly-brace string paths are patched
        assertTrue(patchedCode.contains("src={\"/app-f46aa078/leaders/presdent.jpg\"}"));
        
        // Verify CSS background url paths are patched
        assertTrue(patchedCode.contains("url(\"/app-f46aa078/logo.svg\")"));
    }
}
