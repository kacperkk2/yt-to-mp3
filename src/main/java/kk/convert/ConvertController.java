package kk.convert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/converter")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:4200", "https://kacperkk2.github.io"})
@Slf4j
public class ConvertController {
    private static final String UNIT = "MB";
    private BufferedReader stdInput;
    private Process process;

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(Paths.get("music"));
    }

    @ResponseBody
    @GetMapping("/songs/{file-name}")
    public ResponseEntity<Resource> getFile(@PathVariable("file-name") String fileName) throws FileNotFoundException {
        log.info("Getting one song: {}", fileName);
        String fileToLoad = fileName + ".mp3";
        final File file = new File("music/" + fileToLoad);
        InputStreamResource resource = new InputStreamResource(new FileInputStream(file));
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION);
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileToLoad);
        log.info("Download of one song: {} initialized", fileName);
        return ResponseEntity.ok()
                .contentLength(file.length())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .headers(headers)
                .body(resource);
    }

    @ResponseBody
    @GetMapping(value = "/songs", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> getFiles() throws IOException {
        log.info("Getting all songs");
        Set<String> filesReadyToDownload = listFiles("music", 1).stream()
                .filter(fileName -> fileName.endsWith(".mp3"))
                .collect(Collectors.toSet());
        log.info("Download of all songs initialized");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=youtube-to-mp3.zip")
                .body(outputStream -> {
                    try(ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {
                        zip(filesReadyToDownload, zipOut);
                    }
                });
    }

    private void zip(Set<String> files, ZipOutputStream zipOut) throws IOException {
        log.info("Zipping {} files: {}}", files.size(), files);
        for (String srcFile : files) {
            File fileToZip = new File("music/" + srcFile);
            FileInputStream fis = new FileInputStream(fileToZip);
            ZipEntry zipEntry = new ZipEntry(fileToZip.getName());
            zipOut.putNextEntry(zipEntry);

            byte[] bytes = new byte[1024];
            int length;
            while((length = fis.read(bytes)) >= 0) {
                zipOut.write(bytes, 0, length);
            }
            fis.close();
        }
        log.info("Zipping finished");
        zipOut.close();
    }

    @GetMapping("/status")
    public StatusDto status() throws IOException {
        String status = getCurrentProcessingStatus();
        List<SongDto> songDtos = listFiles("music", 1).stream()
                .filter(fileName -> fileName.endsWith(".mp3") || fileName.endsWith(".webm.ytdl"))
                .map(fileName -> {
                    File file = new File("music/" + fileName);
                    float mb = (float) file.length() / 1000_000;
                    if (fileName.endsWith(".mp3")) {
                        return SongDto.builder()
                                .name(fileName.substring(0, fileName.indexOf(".mp3")))
                                .sizeNumber(mb)
                                .sizeUnit(UNIT)
                                .status("ready").build();
                    } else {
                        String withoutExt = fileName.substring(0, fileName.indexOf(".webm.ytdl"));
                        return SongDto.builder()
                                .name(withoutExt)
                                .sizeNumber(mb)
                                .sizeUnit(UNIT)
                                .status(status).build();
                    }
                }).toList();
        double totalSize = songDtos.stream()
                .map(SongDto::sizeNumber)
                .mapToDouble(Double::valueOf)
                .sum();
        return StatusDto.builder()
                .downloadOngoing(process != null && process.isAlive())
                .songs(songDtos)
                .totalSizeNumber(totalSize)
                .totalSizeUnit(UNIT)
                .build();
    }

    @PostMapping("/download")
    public ResponseEntity<String> initDownload(@RequestParam("url") String url) throws IOException {
        log.info("Initializing download for link: {}", url);
        if (stdInput != null && stdInput.ready()) {
            log.info("Download already ongoing, aborting init download");
            return ResponseEntity.badRequest().body(null);
        }
        Runtime rt = Runtime.getRuntime();
        process = rt.exec("yt-dlp --ignore-errors --format bestaudio --extract-audio " +
                "--audio-format mp3 " +
                "--audio-quality 160K " +
                "--output music/%(title)s.%(ext)s " + url);
        stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
        if (stdInput.readLine() == null) {
            log.info("Initializing download failed, process not returning anything");
            return ResponseEntity.notFound().build();
        }
        log.info("Initializing download successful for link: {}", url);
        return ResponseEntity.ok(null);
    }

    @DeleteMapping("/download")
    public ResponseEntity<String> stopDownload() throws IOException {
        log.info("Clearing download...");
        if (stdInput != null ) {
            process.destroy();
            log.info("Ongoing download aborted");
        }
        List<String> incompleteFiles = listFiles("music", 1).stream()
                .filter(song -> !song.endsWith(".mp3"))
                .toList();
        incompleteFiles.stream()
                .map(song -> new File("music/" + song))
                .forEach(File::delete);
        log.info("Removed {} incomplete files: {}", incompleteFiles.size(), incompleteFiles);
        return ResponseEntity.ok(null);
    }

    @DeleteMapping("/songs")
    public ResponseEntity<String> removeWithExtensions(
            @RequestParam(value = "ext", defaultValue = ".mp3", required = false) String ext) throws IOException {
        log.info("Removing files with extension: {}", ext);
        List<String> files = listFiles("music", 1).stream()
                .filter(song -> song.endsWith(ext))
                .toList();
        files.stream()
                .map(song -> new File("music/" + song))
                .forEach(File::delete);
        log.info("Removed {} files: {}", files.size(), files);
        return ResponseEntity.ok(null);
    }

    @DeleteMapping("/songs/{song-name}")
    public ResponseEntity<String> removeSong(@PathVariable("song-name") String songName) {
        log.info("Removing song: {}", songName);
        File file = new File("music/" + songName + ".mp3");
        if (file.delete()) {
            log.info("Song: {} removed", songName);
        } else {
            log.info("Song: {} not found", songName);
        }
        return ResponseEntity.ok(null);
    }

    private String getCurrentProcessingStatus() throws IOException {
        if (stdInput == null || !stdInput.ready()) {
            return "";
        }

        long start = System.currentTimeMillis();
        long end = start + 500;

        String status = "";
        do {
            status = stdInput.readLine();
            if (status == null) {
                break;
            }
            if (status.indexOf("error") > 0) {
                log.error("Error during processing: {}", status);
            }
            int percentIndex = status.indexOf('%');
            if (percentIndex > 0) {
                status = status.substring(status.indexOf("] ") + 3, percentIndex);
            }
        } while (System.currentTimeMillis() < end);

        log.info("Current processing status: {}", status);
        return status;
    }

    private Set<String> listFiles(String dir, int depth) throws IOException {
        try (Stream<Path> stream = Files.walk(Paths.get(dir), depth)) {
            return stream
                    .filter(file -> !Files.isDirectory(file))
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .collect(Collectors.toSet());
        }
    }
}
