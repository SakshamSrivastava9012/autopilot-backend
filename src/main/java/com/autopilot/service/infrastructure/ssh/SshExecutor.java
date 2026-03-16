package com.autopilot.service.infrastructure.ssh;

import com.jcraft.jsch.*;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class SshExecutor {

    public String execute(String host, String user, String privateKeyPath, String command) throws Exception {

        JSch jsch = new JSch();
        jsch.addIdentity(privateKeyPath);

        Session session = jsch.getSession(user, host, 22);
        session.setConfig("StrictHostKeyChecking", "no");

        session.connect();

        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);

        channel.setErrStream(System.err);

        InputStream in = channel.getInputStream();
        channel.connect();

        byte[] tmp = new byte[1024];
        StringBuilder output = new StringBuilder();

        while (true) {

            while (in.available() > 0) {
                int i = in.read(tmp, 0, 1024);
                if (i < 0) break;
                output.append(new String(tmp, 0, i));
            }

            if (channel.isClosed()) {
                break;
            }

            Thread.sleep(100);
        }

        channel.disconnect();
        session.disconnect();

        return output.toString();
    }
}
