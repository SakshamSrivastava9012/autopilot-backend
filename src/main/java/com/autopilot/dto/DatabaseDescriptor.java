package com.autopilot.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseDescriptor {
    private String engine; // e.g., POSTGRES, MONGO, MYSQL
    private String provider; // e.g., ATLAS, AWS_RDS, SUPABASE, INTERNAL
    private String connectionString;
    private String username;
    private String password;
    private boolean isExternal;
    private boolean requiresProvisioning;
}
