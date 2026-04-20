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
import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/youtube")
@CrossOrigin(origins = "*")
public class DownloadController {

    @Value("${download.dir:/app/downloads}")
    private String downloadDir;

    // ============================================
    // PROXY CONFIGURATION - PROXY ROTATION SYSTEM
    // ============================================
    // List of working proxies (USA, UK, Germany for YouTube access)
    // Format: "protocol://ip:port" or "protocol://username:password@ip:port"
    private static final List<String> PROXY_LIST = Arrays.asList(
        // Free proxies (for testing - replace with paid proxies for production)
        "http://192.252.214.128:80",      // USA
        "http://47.243.62.234:80",        // USA
        "http://162.223.89.34:80",        // USA
        "http://46.4.96.137:3128",        // Germany
        "http://51.89.14.70:80",          // UK
        "http://20.111.54.16:8123"        // USA
        
        // Add more proxies here for rotation
        // "http://username:password@proxy.proxymesh.com:31280",  // Paid proxy example
        // "http://username:pass@geo.iproyal.com:7000",          // Residential proxy example
    );
    
    private static int currentProxyIndex = 0;
    
    // Helper method to get next proxy in rotation (round-robin)
    private String getNextProxy() {
        if (PROXY_LIST == null || PROXY_LIST.isEmpty()) {
            return null;
        }
        String proxy = PROXY_LIST.get(currentProxyIndex);
        currentProxyIndex = (currentProxyIndex + 1) % PROXY_LIST.size();
        System.out.println("Using proxy: " + proxy);
        return proxy;
    }
    
    // Helper method to add proxy to command
    private String[] addProxyIfNeeded(String[] baseCommand, String proxy) {
        if (proxy != null && !proxy.isEmpty()) {
            String[] newCommand = new String[baseCommand.length + 2];
            newCommand[0] = baseCommand[0];
            newCommand[1] = "--proxy";
            newCommand[2] = proxy;
            System.arraycopy(baseCommand, 1, newCommand, 3, baseCommand.length - 1);
            return newCommand;
        }
        return baseCommand;
    }
    
    // Helper method to add concurrent fragment downloading for speed
    private String[] addConcurrentDownloads(String[] baseCommand, int concurrentFragments) {
        if (concurrentFragments > 1) {
            String[] newCommand = new String[baseCommand.length + 2];
            newCommand[0] = baseCommand[0];
            newCommand[1] = "-N";
            newCommand[2] = String.valueOf(concurrentFragments);
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
        // Try each proxy until one works
        for (int attempt = 0; attempt <= PROXY_LIST.size(); attempt++) {
            String proxy = (attempt == 0) ? null : getNextProxy();
            
            try {
                System.out.println("Fetching info for URL: " + url);
                if (proxy != null) {
                    System.out.println("Attempt " + attempt + " with proxy: " + proxy);
                } else {
                    System.out.println("Attempt " + attempt + " without proxy (direct connection)");
                }
                
                String[] baseCommand = {
                    "yt-dlp",
                    "--cookies", "/app/cookies.txt",
                    "--dump-json",
                    "--no-download",
                    "--no-playlist",
                    "--extractor-args", "youtube:player-client=android",
                    "--socket-timeout", "30",
                    url
                };
                
                String[] command = baseCommand;
                if (proxy != null) {
                    command = addProxyIfNeeded(baseCommand, proxy);
                }
                
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
                
                boolean finished = process.waitFor(45, TimeUnit.SECONDS);
                
                if (finished && process.exitValue() == 0) {
                    return ResponseEntity.ok(output.toString());
                }
                
            } catch (Exception e) {
                System.out.println("Attempt " + attempt + " failed: " + e.getMessage());
            }
        }
        
        return ResponseEntity.internalServerError()
            .body(Map.of("error", "Failed to fetch video info after trying all proxies"));
    }

    // ============================================
    // ENDPOINT 2: Download video file (with speed optimization)
    // ============================================
    @GetMapping("/download")
    public ResponseEntity<?> downloadVideo(@RequestParam String url,
                                            @RequestParam(required = false, defaultValue = "5") int concurrent,
                                            @RequestParam(required = false) String proxy) {
        try {
            System.out.println("Downloading video from URL: " + url);
            System.out.println("Concurrent fragments: " + concurrent);
            
            // Use specified proxy or rotate to next available
            String selectedProxy = proxy;
            if (selectedProxy == null && PROXY_LIST != null && !PROXY_LIST.isEmpty()) {
                selectedProxy = getNextProxy();
            }
            
            if (selectedProxy != null) {
                System.out.println("Using proxy: " + selectedProxy);
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
            
            // Add concurrent fragment downloading for speed
            String[] command = addConcurrentDownloads(baseCommand, concurrent);
            
            // Add proxy if configured
            if (selectedProxy != null) {
                command = addProxyIfNeeded(command, selectedProxy);
            }
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            System.out.println("Executing: " + String.join(" ", command));
            
            Process process = pb.start();
            
            // Read and log output for debugging
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
            
            boolean finished = process.waitFor(300, TimeUnit.SECONDS);
            
            if (finished && process.exitValue() == 0) {
                File downloadedFile = new File(outputPath);
                if (downloadedFile.exists()) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("message", "Video downloaded successfully");
                    response.put("fileName", outputFile);
                    response.put("downloadUrl", "/api/youtube/file/" + outputFile);
                    response.put("fileSize", formatFileSize(downloadedFile.length()));
                    response.put("proxyUsed", selectedProxy != null ? selectedProxy : "none");
                    response.put("concurrentFragments", concurrent);
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
    // ENDPOINT 3: Download audio only (MP3) with speed optimization
    // ============================================
    @GetMapping("/download-audio")
    public ResponseEntity<?> downloadAudio(@RequestParam String url,
                                            @RequestParam(required = false, defaultValue = "5") int concurrent) {
        try {
            System.out.println("Downloading audio from URL: " + url);
            System.out.println("Concurrent fragments: " + concurrent);
            
            String proxy = (PROXY_LIST != null && !PROXY_LIST.isEmpty()) ? getNextProxy() : null;
            if (proxy != null) {
                System.out.println("Using proxy: " + proxy);
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
            
            // Add concurrent fragment downloading
            String[] command = addConcurrentDownloads(baseCommand, concurrent);
            
            if (proxy != null) {
                command = addProxyIfNeeded(command, proxy);
            }
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            
            Process process = pb.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
            
            boolean finished = process.waitFor(300, TimeUnit.SECONDS);
            
            if (finished && process.exitValue() == 0) {
                File downloadedFile = new File(outputPath);
                if (downloadedFile.exists()) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("message", "Audio downloaded successfully");
                    response.put("fileName", outputFile);
                    response.put("downloadUrl", "/api/youtube/file/" + outputFile);
                    response.put("fileSize", formatFileSize(downloadedFile.length()));
                    response.put("proxyUsed", proxy != null ? proxy : "none");
                    response.put("concurrentFragments", concurrent);
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
    // ENDPOINT 5: Health check with proxy status
    // ============================================
    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "healthy");
        status.put("service", "YouTube Downloader API");
        status.put("downloadDir", downloadDir);
        status.put("proxyCount", PROXY_LIST.size());
        status.put("proxiesConfigured", PROXY_LIST);
        status.put("concurrentDownloadsEnabled", true);
        status.put("defaultConcurrentFragments", 5);
        
        File cookiesFile = new File("/app/cookies.txt");
        status.put("cookiesExists", cookiesFile.exists());
        
        return ResponseEntity.ok(status);
    }

    // ============================================
    // ENDPOINT 6: Test all proxies
    // ============================================
    @GetMapping("/test-proxies")
    public ResponseEntity<?> testProxies() {
        if (PROXY_LIST == null || PROXY_LIST.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "No proxies configured. Add proxies to PROXY_LIST."));
        }
        
        List<Map<String, Object>> results = new ArrayList<>();
        
        for (String proxy : PROXY_LIST) {
            try {
                String[] command = {"yt-dlp", "--proxy", proxy, "--version"};
                ProcessBuilder pb = new ProcessBuilder(command);
                Process process = pb.start();
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String version = reader.readLine();
                boolean worked = process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
                
                Map<String, Object> result = new HashMap<>();
                result.put("proxy", proxy);
                result.put("working", worked);
                if (worked) {
                    result.put("version", version);
                }
                results.add(result);
            } catch (Exception e) {
                Map<String, Object> result = new HashMap<>();
                result.put("proxy", proxy);
                result.put("working", false);
                result.put("error", e.getMessage());
                results.add(result);
            }
        }
        
        return ResponseEntity.ok(Map.of(
            "totalProxies", PROXY_LIST.size(),
            "results", results
        ));
    }

    // ============================================
    // ENDPOINT 7: Get current proxy rotation status
    // ============================================
    @GetMapping("/proxy-status")
    public ResponseEntity<?> proxyStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("currentProxyIndex", currentProxyIndex);
        status.put("totalProxies", PROXY_LIST.size());
        status.put("proxies", PROXY_LIST);
        status.put("nextProxy", PROXY_LIST.get(currentProxyIndex));
        return ResponseEntity.ok(status);
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
        
        ProcessBuilder pb = new ProcessBuilder(baseCommand);
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
