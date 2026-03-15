package com.autopilot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AwsCredentialsDto {

    private String accessKeyId;
    private String secretAccessKey;
    private String sessionToken;

}
