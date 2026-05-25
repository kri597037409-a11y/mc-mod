package org.gradle.wrapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class GradleWrapperMain {
    private GradleWrapperMain() {
    }

    public static void main(String[] args) throws Exception {
        Path projectDir = Paths.get(System.getProperty("user.dir"));
        Path propertiesPath = projectDir.resolve("gradle/wrapper/gradle-wrapper.properties");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(propertiesPath)) {
            properties.load(input);
        }

        String distributionUrl = properties.getProperty("distributionUrl");
        if (distributionUrl == null || distributionUrl.isBlank()) {
            throw new IllegalStateException("distributionUrl is missing in " + propertiesPath);
        }

        Path gradleHome = installGradle(distributionUrl);
        Path executable = gradleHome.resolve(isWindows() ? "bin/gradle.bat" : "bin/gradle");
        if (!Files.exists(executable)) {
            throw new IllegalStateException("Gradle executable was not found: " + executable);
        }

        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        for (String arg : args) {
            command.add(arg);
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(projectDir.toFile());
        builder.inheritIO();
        Process process = builder.start();
        System.exit(process.waitFor());
    }

    private static Path installGradle(String distributionUrl) throws Exception {
        String fileName = distributionUrl.substring(distributionUrl.lastIndexOf('/') + 1);
        if (fileName.isBlank()) {
            fileName = "gradle-distribution.zip";
        }

        String key = Integer.toHexString(distributionUrl.hashCode());
        Path distRoot = Paths.get(System.getProperty("user.home"), ".gradle", "wrapper", "dists", "codex", key);
        Path marker = distRoot.resolve(".ok");
        Path zipPath = distRoot.resolve(fileName);
        Files.createDirectories(distRoot);

        if (!Files.exists(marker)) {
            if (!Files.exists(zipPath)) {
                System.out.println("Downloading Gradle distribution: " + distributionUrl);
                download(distributionUrl, zipPath);
            }
            unzip(zipPath, distRoot);
            Files.writeString(marker, "ok");
        }

        try (var stream = Files.list(distRoot)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("gradle-"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Could not find extracted Gradle distribution in " + distRoot));
        }
    }

    private static void download(String urlText, Path target) throws Exception {
        URL url = URI.create(urlText).toURL();
        for (int redirect = 0; redirect < 8; redirect++) {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", "Gradle Wrapper");
            int code = connection.getResponseCode();
            if (code >= 300 && code < 400) {
                String location = connection.getHeaderField("Location");
                if (location == null) {
                    throw new IOException("Redirect without Location from " + url);
                }
                url = URI.create(location).toURL();
                continue;
            }
            if (code != 200) {
                throw new IOException("Could not download " + url + ": HTTP " + code);
            }
            Path temp = target.resolveSibling(target.getFileName() + ".part");
            try (InputStream input = connection.getInputStream()) {
                Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        throw new IOException("Too many redirects while downloading " + urlText);
    }

    private static void unzip(Path zipPath, Path targetDir) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path target = targetDir.resolve(entry.getName()).normalize();
                if (!target.startsWith(targetDir)) {
                    throw new IOException("Blocked zip entry outside target directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    try (OutputStream output = Files.newOutputStream(target)) {
                        zip.transferTo(output);
                    }
                }
            }
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
