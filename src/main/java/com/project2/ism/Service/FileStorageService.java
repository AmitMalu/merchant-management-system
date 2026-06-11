package com.project2.ism.Service;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path rootUploadPath = Paths.get("uploads");  // TODO: remember to path to appl.prop and also copy from uploads/document to new place

    public FileStorageService() {
        try {
            Files.createDirectories(rootUploadPath);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory!", e);
        }
    }

    public String store(MultipartFile file, String documentType) {
        if (file == null || file.isEmpty()) return null;

        try {
            // Create subfolder structure: uploads/documents
            Path folderPath = rootUploadPath.resolve("documents");
            Files.createDirectories(folderPath);

            // Create unique filename with document type prefix
            String timestamp = String.valueOf(System.currentTimeMillis());
            String originalName = file.getOriginalFilename();
            String extension = originalName.substring(originalName.lastIndexOf('.'));
            String uniqueId = UUID.randomUUID().toString().substring(0, 8);
            String filename = documentType + "_" + timestamp + "_" + uniqueId + extension;

            // Save file
            Path filePath = folderPath.resolve(filename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // Return relative path for storage in database
            return "documents/" + filename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + file.getOriginalFilename(), e);
        }
    }

    public Resource loadAsResource(String filePath) {
        try {
            Path file;
            if (filePath.startsWith("uploads")) {
                file = Paths.get(filePath).normalize(); // absolute within project
            } else {
                file = rootUploadPath.resolve(filePath).normalize();
            }

            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("Could not read file: " + filePath);
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not read file: " + filePath, e);
        }
    }


    public void deleteFile(String filePath) {
        try {
            if (filePath != null && !filePath.isEmpty()) {
                Files.deleteIfExists(rootUploadPath.resolve(filePath));
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not delete file: " + filePath, e);
        }
    }

    public boolean fileExists(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }
        return Files.exists(rootUploadPath.resolve(filePath));
    }
}