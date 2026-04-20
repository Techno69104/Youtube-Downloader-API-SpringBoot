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

    @GetMapping("/download")
    public ResponseEntity<?> downloadVideo(@RequestParam String url) {
        try {
            File downloadDirectory = new File(downloadDir);
            if (!downloadDirectory.exists()) {
                downloadDirectory.mkdirs();
            }

            String title = getVideoTitle(url);
            String sanitizedTitle = title.replaceAll("[^a-zA-Z0-9]", "");
            String outputFile = sanitizedTitle + ".mp4";
            String outputPath = downloadDir + File.separator + outputFile;

            ProcessBuilder pb = new ProcessBuilder(
                "yt-dlp",
                "-f", "best[ext=mp4]",
                "-o", outputPath,
                url
            );
            
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            
            if (finished && process.exitValue() == 0) {
                Map<String, String> response = new HashMap<>();
                response.put("message", "Video downloaded successfully");
                response.put("fileName", outputFile);
                response.put("downloadUrl", "/api/youtube/file/" + outputFile);
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Download failed"));
            }
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/file/{fileName}")
    public ResponseEntity<Resource> serveFile(@PathVariable String fileName) {
        try {
            Path filePath = Paths.get(downloadDir).resolve(fileName).normalize();
            Resource resource = new FileSystemResource(filePath.toFile());
            
            if (resource.exists()) {
                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/info")
    public ResponseEntity<?> getVideoInfo(@RequestParam String url) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "yt-dlp",
                "--dump-json",
                "--no-download",
                url
            );
            
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );
            
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
            
            process.waitFor(30, TimeUnit.SECONDS);
            return ResponseEntity.ok(output.toString());
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        return ResponseEntity.ok(Map.of("status", "healthy"));
    }

    private String getVideoTitle(String url) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            "yt-dlp",
            "--get-title",
            "--no-download",
            url
        );
        
        Process process = pb.start();
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream())
        );
        
        String title = reader.readLine();
        process.waitFor(10, TimeUnit.SECONDS);
        
        return title != null ? title : "video";
    }
}
