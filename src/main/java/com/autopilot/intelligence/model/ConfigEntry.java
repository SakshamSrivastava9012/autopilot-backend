package com.autopilot.intelligence.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A detected configuration entry — key/value pair with source file metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigEntry {
    private String key;
    private String value;
    private String sourceFile;
    private boolean secret;
    private String normalizedKey; // e.g. DB_PASSWORD
}
