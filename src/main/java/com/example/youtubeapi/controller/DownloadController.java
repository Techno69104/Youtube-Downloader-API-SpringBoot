package com.example.youtubeapi.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/youtube")
@CrossOrigin(origins = "*")
public class DownloadController {

    @Value("${download.dir:/app/downloads}")
    private String downloadDir;

    // ============================================
    // PROXY CONFIGURATION - UPDATE THIS SECTION
    // ============================================
    // Option A: Use a free proxy (unstable, for testing only)
    // private static final String PROXY_URL = "http://45.87.247.26:80";
    
    // Option B: Use a paid proxy service like BrightData, ProxyMesh, or ScraperAPI
    // private static final String PROXY_URL = "http://username:password@proxy-provider.com:8000";
    
    // Option C: Use a SOCKS5 proxy (more secure)
    // private static final String PROXY_URL = "socks5://localhost:1080";
    
    // Currently disabled - set to null to use no proxy
    private static final String PROXY_URL = null; // Change this to your proxy URL when ready
    
    // Helper method to add proxy to commands if configured
    private String[] addProxyIfNeeded(String[] baseCommand) {
        if (PROXY_URL != null && !PROXY_URL.isEmpty()) {
            // Insert --proxy right after yt-dlp
            String[] newCommand = new String[baseCommand.length + 2];
            newCommand[0] = baseCommand[0]; // yt-dlp
            newCommand[1] = "--proxy";
            newCommand[2] = PROXY_URL;
            System.arraycopy(baseCommand, 1, newCommand, 3, baseCommand.length - 1);
            return newCommand;
        }
        return baseCommand;
    }

    // ============================================
    // ENDPOINT 1: Get video information
    // ============================================
    @GetMapping("/info")
    public ResponseEntity<?> getVideoInfo(@RequestParam String url) {
        try {
            System.out.println("Fetching info for URL: " + url);
            if (PROXY_URL != null) {
                System.out.println("Using proxy: " + PROXY_URL);
            }
            
            String[] baseCommand = {
                "yt-dlp",
                "--cookies", "/app/cookies.txt",
                "--dump-json",
                "--no-download",
                "--no-playlist",
                "--extractor-args", "youtube:player-client=android",
                url
            };
            
            String[] command = addProxyIfNeeded(baseCommand);
            ProcessBuilder pb = new ProcessBuilder(command);
            
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
            
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            
            if (finished && process.exitValue() == 0) {
                return ResponseEntity.ok(output.toString());
            } else {
                return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to fetch video info. Exit code: " + process.exitValue()));
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    // ============================================
    // ENDPOINT 2: Download video file
    // ============================================
    @GetMapping("/download")
    public ResponseEntity<?> downloadVideo(@RequestParam String url) {
        try {
            System.out.println("Downloading video from URL: " + url);
            if (PROXY_URL != null) {
                System.out.println("Using proxy: " + PROXY_URL);
            }
            
            File downloadDirectory = new File(downloadDir);
            if (!downloadDirectory.exists()) {
                downloadDirectory.mkdirs();
            }

            String title = getVideoTitle(url);
            String sanitizedTitle = sanitizeFileName(title);
            String outputFile = sanitizedTitle + ".mp4";
            String outputPath = downloadDir + File.separator + outputFile;

            String[] baseCommand = {
                "yt-dlp",
                "--cookies", "/app/cookies.txt",
                "-f", "best[ext=mp4]",
                "-o", outputPath,
                "--no-playlist",
                "--extractor-args", "youtube:player-client=android",
                "--socket-timeout", "30",
                url
            };
            
            String[] command = addProxyIfNeeded(baseCommand);
            ProcessBuilder pb = new ProcessBuilder(command);
            
            pb.redirectErrorStream(true);
            System.out.println("Executing: " + String.join(" ", command));
            
            Process process = pb.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
            
            boolean finished = process.waitFor(180, TimeUnit.SECONDS);
            
            if (finished && process.exitValue() == 0) {
                File downloadedFile = new File(outputPath);
                if (downloadedFile.exists()) {
                    Map<String, String> response = new HashMap<>();
                    response.put("message", "Video downloaded successfully");
                    response.put("fileName", outputFile);
                    response.put("downloadUrl", "/api/youtube/file/" + outputFile);
                    response.put("fileSize", formatFileSize(downloadedFile.length()));
                    return ResponseEntity.ok(response);
                } else {
                    return ResponseEntity.internalServerError()
                        .body(Map.of("error", "File was not found after download"));
                }
            } else {
                return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Download failed with exit code: " + process.exitValue()));
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    // ============================================
    // ENDPOINT 3: Download audio only (MP3)
    // ============================================
    @GetMapping("/download-audio")
    public ResponseEntity<?> downloadAudio(@RequestParam String url) {
        try {
            System.out.println("Downloading audio from URL: " + url);
            if (PROXY_URL != null) {
                System.out.println("Using proxy: " + PROXY_URL);
            }
            
            File downloadDirectory = new File(downloadDir);
            if (!downloadDirectory.exists()) {
                downloadDirectory.mkdirs();
            }

            String title = getVideoTitle(url);
            String sanitizedTitle = sanitizeFileName(title);
            String outputFile = sanitizedTitle + ".mp3";
            String outputPath = downloadDir + File.separator + outputFile;

            String[] baseCommand = {
                "yt-dlp",
                "--cookies", "/app/cookies.txt",
                "-f", "bestaudio",
                "--extract-audio",
                "--audio-format", "mp3",
                "--audio-quality", "0",
                "-o", outputPath,
                "--no-playlist",
                "--extractor-args", "youtube:player-client=android",
                url
            };
            
            String[] command = addProxyIfNeeded(baseCommand);
            ProcessBuilder pb = new ProcessBuilder(command);
            
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
            
            boolean finished = process.waitFor(180, TimeUnit.SECONDS);
            
            if (finished && process.exitValue() == 0) {
                File downloadedFile = new File(outputPath);
                if (downloadedFile.exists()) {
                    Map<String, String> response = new HashMap<>();
                    response.put("message", "Audio downloaded successfully");
                    response.put("fileName", outputFile);
                    response.put("downloadUrl", "/api/youtube/file/" + outputFile);
                    response.put("fileSize", formatFileSize(downloadedFile.length()));
                    return ResponseEntity.ok(response);
                } else {
                    return ResponseEntity.internalServerError()
                        .body(Map.of("error", "Audio file not found after download"));
                }
            } else {
                return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Audio download failed with exit code: " + process.exitValue()));
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    // ============================================
    // ENDPOINT 4: Serve downloaded file
    // ============================================
    @GetMapping("/file/{fileName}")
    public ResponseEntity<Resource> serveFile(@PathVariable String fileName) {
        try {
            Path filePath = Paths.get(downloadDir).resolve(fileName).normalize();
            File file = filePath.toFile();
            
            if (file.exists() && file.isFile()) {
                Resource resource = new FileSystemResource(file);
                
                String contentType = fileName.endsWith(".mp3") ? "audio/mpeg" : "video/mp4";
                
                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(file.length()))
                    .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ============================================
    // ENDPOINT 5: Health check
    // ============================================
    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "healthy");
        status.put("service", "YouTube Downloader API");
        status.put("downloadDir", downloadDir);
        status.put("proxyConfigured", PROXY_URL != null);
        if (PROXY_URL != null) {
            status.put("proxyUrl", PROXY_URL);
        }
        
        File cookiesFile = new File("/app/cookies.txt");
        status.put("cookiesExists", cookiesFile.exists());
        
        return ResponseEntity.ok(status);
    }

    // ============================================
    // ENDPOINT 6: Test proxy connectivity
    // ============================================
    @GetMapping("/test-proxy")
    public ResponseEntity<?> testProxy() {
        if (PROXY_URL == null) {
            return ResponseEntity.ok(Map.of("message", "No proxy configured. Set PROXY_URL in the code."));
        }
        
        try {
            String[] command = {"yt-dlp", "--proxy", PROXY_URL, "--version"};
            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String version = reader.readLine();
            process.waitFor(10, TimeUnit.SECONDS);
            
            return ResponseEntity.ok(Map.of(
                "proxyConfigured", true,
                "proxyUrl", PROXY_URL,
                "ytDlpVersion", version != null ? version : "unknown",
                "status", "Proxy test completed"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Proxy test failed: " + e.getMessage()));
        }
    }

    // ============================================
    // HELPER METHODS
    // ============================================
    
    private String getVideoTitle(String url) throws Exception {
        String[] baseCommand = {
            "yt-dlp",
            "--cookies", "/app/cookies.txt",
            "--get-title",
            "--no-download",
            "--no-playlist",
            url
        };
        
        String[] command = addProxyIfNeeded(baseCommand);
        ProcessBuilder pb = new ProcessBuilder(command);
        
        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        
        String title = reader.readLine();
        process.waitFor(10, TimeUnit.SECONDS);
        
        return (title != null && !title.isEmpty()) ? title : "video";
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null) return "video";
        String sanitized = fileName.replaceAll("[^a-zA-Z0-9]", "");
        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 50);
        }
        return sanitized.isEmpty() ? "video" : sanitized;
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        int z = (63 - Long.numberOfLeadingZeros(size)) / 10;
        return String.format("%.1f %sB", (double) size / (1L << (z * 10)), " KMGTPE".charAt(z));
    }
}
