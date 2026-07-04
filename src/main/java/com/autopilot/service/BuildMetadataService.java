package com.autopilot.service;

public interface BuildMetadataService {
    String getVersion();
    String getGitCommit();
    String getBuildTimestamp();
    long getStartTime();
    String getPid();
    String getRunningJarChecksum();
    String getRunningJarPath();
}
