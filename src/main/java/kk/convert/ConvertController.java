package kk.convert;

import lombok.RequiredArgsConstructor;
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
public class ConvertController {

    private BufferedReader stdInput;
    private Process process;

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(Paths.get("music"));
    }

    @ResponseBody
    @GetMapping("/songs/{file-name}")
    public ResponseEntity<Resource> getFile(@PathVariable("file-name") String fileName) throws FileNotFoundException {
        String fileToLoad = fileName + ".mp3";
        final File file = new File("music/" + fileToLoad);
        InputStreamResource resource = new InputStreamResource(new FileInputStream(file));
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION);
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileToLoad);
        return ResponseEntity.ok()
                .contentLength(file.length())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .headers(headers)
                .body(resource);
    }

    @ResponseBody
    @GetMapping(value = "/songs", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> getFiles() throws IOException {
        Set<String> filesReadyToDownload = listFiles("music", 1).stream()
                .filter(fileName -> fileName.endsWith(".mp3"))
                .collect(Collectors.toSet());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=youtube-to-mp3.zip")
                .body(outputStream -> {
                    try(ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {
                        zip(filesReadyToDownload, zipOut);
                    }
                });
    }

    private void zip(Set<String> files, ZipOutputStream zipOut) throws IOException {
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
        zipOut.close();
    }

    @GetMapping("/status")
    public List<SongDto> status() throws IOException {
        String status = getCurrentProcessingStatus();
        return listFiles("music", 1).stream()
                .map(fileName -> {
                    File file = new File("music/" + fileName);
                    float mb = (float) file.length() / 1000_000;
                    if (fileName.endsWith(".mp3")) {
                        return SongDto.builder()
                                .name(fileName.substring(0, fileName.indexOf(".mp3")))
                                .size(String.format("%.1f", mb) + " MB")
                                .status("ready").build();
                    }
                    else {
                        return SongDto.builder()
                                .name(fileName.substring(0, fileName.indexOf(".webm.part")))
                                .size(String.format("%.1f", mb) + " MB")
                                .status(status).build();
                    }
                }).toList();
    }

    @PostMapping("/download")
    public ResponseEntity<String> initDownload(@RequestParam("url") String url) throws IOException {
        if (stdInput != null && stdInput.ready()) {
            return ResponseEntity.badRequest().body(null);
        }
        Runtime rt = Runtime.getRuntime();
        process = rt.exec("youtube-dl --ignore-errors --format bestaudio --extract-audio " +
                "--audio-format mp3 " +
                "--audio-quality 160K " +
                "--output music/%(title)s.%(ext)s " + url);
        stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
        if (stdInput.readLine() == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(null);
    }

    @DeleteMapping("/download")
    public ResponseEntity<String> stopDownload() throws IOException {
        if (stdInput == null || !stdInput.ready()) {
            return ResponseEntity.badRequest().body(null);
        }
        process.destroy();
        listFiles("music", 1).stream()
                .filter(song -> song.endsWith(".part"))
                .map(song -> new File("music/" + song))
                .forEach(File::delete);
        return ResponseEntity.ok(null);
    }

    @DeleteMapping("/songs")
    public ResponseEntity<String> removeWithExtensions(@RequestParam("ext") String ext) throws IOException {
        listFiles("music", 1).stream()
                .filter(song -> song.endsWith(ext))
                .map(song -> new File("music/" + song))
                .forEach(File::delete);
        return ResponseEntity.ok(null);
    }

    @DeleteMapping("/songs/{song-name}")
    public ResponseEntity<String> removeSong(@PathVariable("song-name") String songName) {
        File file = new File("music/" + songName + ".mp3");
        file.delete();
        return ResponseEntity.ok(null);
    }

    private String getCurrentProcessingStatus() throws IOException {
        if (stdInput == null || !stdInput.ready()) {
            return "";
        }

        long start = System.currentTimeMillis();
        long end = start + 500;

        String status = null;
        do {
            status = stdInput.readLine();
            if (status == null) {
                break;
            }
            int percentIndex = status.indexOf('%');
            if (percentIndex > 0) {
                status = status.substring(status.indexOf("] ") + 3, percentIndex);
            }
        } while (System.currentTimeMillis() < end);

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
