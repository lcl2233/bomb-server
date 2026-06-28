package com.bomb.module.vpn.service;

import com.bomb.common.exception.BusinessException;
import com.bomb.config.VpnWireGuardProperties;
import com.bomb.module.vpn.dto.SshCommandResult;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

@Slf4j
@Component
@RequiredArgsConstructor
public class WireGuardSshExecutor {

    private final VpnWireGuardProperties properties;

    public SshCommandResult runAddClientScript(String clientName) {
        validateClientName(clientName);
        String command = properties.getScriptPath() + " " + clientName;
        return execute(command);
    }

    public SshCommandResult execute(String command) {
        Session session = null;
        ChannelExec channel = null;
        try {
            JSch jsch = new JSch();
            Path privateKeyPath = Path.of(properties.getPrivateKeyPath());
            if (!Files.exists(privateKeyPath)) {
                throw new BusinessException("wireguard ssh private key not found: " + privateKeyPath);
            }
            jsch.addIdentity(privateKeyPath.toString());

            Path knownHostsPath = resolveKnownHostsPath();
            if (!Files.exists(knownHostsPath)) {
                throw new BusinessException("wireguard ssh known_hosts not found: " + knownHostsPath);
            }
            jsch.setKnownHosts(knownHostsPath.toString());

            session = jsch.getSession(properties.getUsername(), properties.getHost(), properties.getPort());
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "yes");
            session.setConfig(config);
            session.connect(properties.getConnectTimeoutMs());

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            channel.setOutputStream(stdout);
            channel.setErrStream(stderr);
            channel.connect(properties.getConnectTimeoutMs());

            waitForChannel(channel, Duration.ofMillis(properties.getCommandTimeoutMs()));

            Integer exitStatus = channel.getExitStatus();
            return SshCommandResult.builder()
                    .exitCode(exitStatus == null ? -1 : exitStatus)
                    .stdout(stdout.toString(StandardCharsets.UTF_8))
                    .stderr(stderr.toString(StandardCharsets.UTF_8))
                    .build();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("wireguard ssh command failed: {}", command, ex);
            throw new BusinessException("wireguard ssh command failed: " + ex.getMessage());
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
        }
    }

    private Path resolveKnownHostsPath() {
        if (StringUtils.hasText(properties.getKnownHostsPath())) {
            return Path.of(properties.getKnownHostsPath());
        }
        String userHome = System.getProperty("user.home");
        return Path.of(userHome, ".ssh", "known_hosts");
    }

    private void waitForChannel(ChannelExec channel, Duration timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (!channel.isClosed()) {
            if (System.currentTimeMillis() > deadline) {
                throw new BusinessException("wireguard ssh command timeout");
            }
            Thread.sleep(200);
        }
    }

    private void validateClientName(String clientName) {
        if (clientName == null || !clientName.matches("^[a-zA-Z0-9_-]+$")) {
            throw new BusinessException("invalid wireguard client name: " + clientName);
        }
    }
}
