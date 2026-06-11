package com.project2.ism.Controller;

import com.project2.ism.Service.FileStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/file")
public class FileController {

    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    /**
     * Handle files with full path (supports nested directories)
     * This will handle paths like: /api/files/uploads/franchises/pan/filename.png
     * Note: /api is the context path, so the mapping is just /files/**
     */
    @GetMapping("/files/**")
    public ResponseEntity<Resource> serveFileByPath(HttpServletRequest request) {
        try {

            // Extract the path after /files/ (since /api is context path)
            String path = request.getRequestURI().substring(16);

            // URL decode to handle special characters and spaces
            path = URLDecoder.decode(path, StandardCharsets.UTF_8);

            // Load the resource using the full path
            Resource resource = fileStorageService.loadAsResource(path);

            // Determine content type
            String contentType = null;
            try {
                contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
            } catch (Exception e) {
                // Ignore and use fallback
            }

            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            // Check if download is requested
            String downloadParam = request.getParameter("download");
            String disposition = "true".equals(downloadParam) ? "attachment" : "inline";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + resource.getFilename() + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Keep the original documents endpoint for backward compatibility
     */
    @GetMapping("/documents/{filename:.+}")
    public ResponseEntity<Resource> serveDocumentFile(@PathVariable String filename, HttpServletRequest request) {
        try {
            Resource resource = fileStorageService.loadAsResource("documents/" + filename);

            String contentType = null;
            try {
                contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
            } catch (Exception e) {
                // Ignore
            }

            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            String downloadParam = request.getParameter("download");
            String disposition = "true".equals(downloadParam) ? "attachment" : "inline";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + resource.getFilename() + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Direct uploads folder access
     */
    @GetMapping("/uploads/**")
    public ResponseEntity<Resource> serveUploadFile(HttpServletRequest request) {
        try {
            // Extract the path after /uploads/
            String path = request.getRequestURI().substring("/uploads/".length());

            // URL decode to handle special characters and spaces
            path = URLDecoder.decode(path, StandardCharsets.UTF_8);

            // Prepend "uploads/" to the path since that's our root folder
            String fullPath = "uploads/" + path;

            Resource resource = fileStorageService.loadAsResource(fullPath);

            String contentType = null;
            try {
                contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
            } catch (Exception e) {
                // Ignore
            }

            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            String downloadParam = request.getParameter("download");
            String disposition = "true".equals(downloadParam) ? "attachment" : "inline";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + resource.getFilename() + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Check if a file exists at the given path
     */
    @GetMapping("/files/exists/**")
    public ResponseEntity<Boolean> checkFileExists(HttpServletRequest request) {
        try {
            String path = request.getRequestURI().substring("/files/exists/".length());
            path = URLDecoder.decode(path, StandardCharsets.UTF_8);
            boolean exists = fileStorageService.fileExists(path);
            return ResponseEntity.ok(exists);
        } catch (Exception e) {
            return ResponseEntity.ok(false);
        }
    }
}