package com.emerald.member.lostfound;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import com.emerald.member.api.ApiFailure;

@Component
public class LostFoundPhotoStorage {
    private final Path directory;
    public LostFoundPhotoStorage(@Value("${lost-found.photo-directory:./var/lost-found}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }
    public String save(MultipartFile file) {
        if(file.isEmpty() || file.getSize()>5*1024*1024) throw new ApiFailure(400,"INVALID_PHOTO","Photo must be between 1 byte and 5 MB");
        String type=file.getContentType();
        String extension=switch(type==null?"":type) { case "image/jpeg" -> ".jpg"; case "image/png" -> ".png"; case "image/webp" -> ".webp"; default -> throw new ApiFailure(400,"INVALID_PHOTO_TYPE","Only JPEG, PNG, or WebP images are accepted"); };
        String key=UUID.randomUUID()+extension;
        try { Files.createDirectories(directory); Files.copy(file.getInputStream(),directory.resolve(key)); return key; }
        catch(IOException ex) { throw new ApiFailure(500,"PHOTO_STORAGE_FAILED","Could not store photo"); }
    }
    public Path resolve(String key) {
        if(key==null || !key.matches("[a-f0-9-]{36}\\.(jpg|png|webp)")) throw new ApiFailure(404,"PHOTO_NOT_FOUND","Photo not found");
        Path result=directory.resolve(key).normalize();
        if(!result.startsWith(directory) || !Files.isRegularFile(result)) throw new ApiFailure(404,"PHOTO_NOT_FOUND","Photo not found");
        return result;
    }
}
