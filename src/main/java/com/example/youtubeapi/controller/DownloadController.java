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

    // Endpoint 1: Get video information (metadata only)
    @GetMapping("/info")
    public ResponseEntity<?> getVideoInfo(@RequestParam String url) {
        try {
            System.out.println("Fetching info for URL: " + url);
            
            // Build yt-dlp command with cookies
            ProcessBuilder pb = new ProcessBuilder(
                "yt-dlp",
                "--cookies", "/app/cookies.txt",      // Use cookies to avoid bot detection
                "--dump-json",                         // Output JSON format
                "--no-download",                       // Don't download the video
                "--no-playlist",                       // Don't process playlists
                "--extractor-args", "youtube:player-client=android", // Use android client (more reliable)
                url
            );
            
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // Read the output
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

    // Endpoint 2: Download video file
    @GetMapping("/download")
    public ResponseEntity<?> downloadVideo(@RequestParam String url) {
        try {
            System.out.println("Downloading video from URL: " + url);
            
            // Create downloads directory if it doesn't exist
            File downloadDirectory = new File(downloadDir);
            if (!downloadDirectory.exists()) {
                downloadDirectory.mkdirs();
            }

            // Get video title first (for filename)
            String title = getVideoTitle(url);
            String sanitizedTitle = sanitizeFileName(title);
            String outputFile = sanitizedTitle + ".mp4";
            String outputPath = downloadDir + File.separator + outputFile;

            // Build yt-dlp command with cookies
            ProcessBuilder pb = new ProcessBuilder(
                "yt-dlp",
                "--cookies", "/app/cookies.txt",      // Use cookies to avoid bot detection
                "-f", "best[ext=mp4]",                 // Best quality MP4 format
                "-o", outputPath,                      // Output path
                "--no-playlist",                       // Don't process playlists
                "--extractor-args", "youtube:player-client=android", // Use android client
                url
            );
            
            pb.redirectErrorStream(true);
            System.out.println("Executing: " + String.join(" ", pb.command()));
            
            Process process = pb.start();
            
            // Read and log output for debugging
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
            
            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            
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

    // Endpoint 3: Download audio only (MP3)
    @GetMapping("/download-audio")
    public ResponseEntity<?> downloadAudio(@RequestParam String url) {
        try {
            System.out.println("Downloading audio from URL: " + url);
            
            File downloadDirectory = new File(downloadDir);
            if (!downloadDirectory.exists()) {
                downloadDirectory.mkdirs();
            }

            String title = getVideoTitle(url);
            String sanitizedTitle = sanitizeFileName(title);
            String outputFile = sanitizedTitle + ".mp3";
            String outputPath = downloadDir + File.separator + outputFile;

            // Build yt-dlp command for audio extraction
            ProcessBuilder pb = new ProcessBuilder(
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
            );
            
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
            
            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            
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

    // Endpoint 4: Serve downloaded file
    @GetMapping("/file/{fileName}")
    public ResponseEntity<Resource> serveFile(@PathVariable String fileName) {
        try {
            Path filePath = Paths.get(downloadDir).resolve(fileName).normalize();
            File file = filePath.toFile();
            
            if (file.exists() && file.isFile()) {
                Resource resource = new FileSystemResource(file);
                
                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, "video/mp4")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(file.length()))
                    .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // Endpoint 5: Health check
    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "healthy");
        status.put("service", "YouTube Downloader API");
        status.put("downloadDir", downloadDir);
        
        // Check if cookies file exists
        File cookiesFile = new File("/app/cookies.txt");
        status.put("cookiesExists", String.valueOf(cookiesFile.exists()));
        
        return ResponseEntity.ok(status);
    }

    // Helper method: Get video title using yt-dlp
    private String getVideoTitle(String url) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            "yt-dlp",
            "--cookies", "/app/cookies.txt",
            "--get-title",
            "--no-download",
            "--no-playlist",
            url
        );
        
        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        
        String title = reader.readLine();
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        
        return (title != null && !title.isEmpty()) ? title : "video";
    }

    // Helper method: Sanitize filename (remove special characters)
    private String sanitizeFileName(String fileName) {
        if (fileName == null) return "video";
        // Remove special characters and spaces, keep only alphanumeric
        String sanitized = fileName.replaceAll("[^a-zA-Z0-9]", "");
        // Limit length to 50 characters
        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 50);
        }
        return sanitized;
    }

    // Helper method: Format file size (bytes to human readable)
    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        int z = (63 - Long.numberOfLeadingZeros(size)) / 10;
        return String.format("%.1f %sB", (double) size / (1L << (z * 10)), " KMGTPE".charAt(z));
    }
}
