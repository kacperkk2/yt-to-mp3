package kk.convert;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        final File file = new File("music/" + fileName);
        InputStreamResource resource = new InputStreamResource(new FileInputStream(file));
        ContentDisposition contentDisposition = ContentDisposition.builder("attachment")
                .filename(fileName)
                .build();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION);
        headers.setContentDisposition(contentDisposition);
        return ResponseEntity.ok()
                .contentLength(file.length())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .headers(headers)
                .body(resource);
    }

    @GetMapping("/status")
    public List<SongDto> status() throws IOException {
        String status = getCurrentProcessingStatus();
        return listFiles("music", 1).stream()
                .map(fileName ->
                        fileName.endsWith(".mp3")
                                ? SongDto.builder().name(fileName).status("ready").build()
                                : SongDto.builder().name(fileName).status(status).build()
                ).toList();
    }

    @PostMapping("/init")
    public String initDownload(@RequestParam("url") String url) throws IOException {
        if (stdInput != null && stdInput.ready()) {
            return "download ongoing!";
        }
        Runtime rt = Runtime.getRuntime();
        process = rt.exec("youtube-dl --ignore-errors --format bestaudio --extract-audio --audio-format mp3 --audio-quality 160K " +
                "--output music/%(title)s.%(ext)s " + url);
        stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
        if (stdInput.readLine() == null) {
            throw new RuntimeException("wrong url");
        }
        return "download init";
    }

    @DeleteMapping("/stop")
    public String stopDownload() throws IOException {
        if (stdInput == null || !stdInput.ready()) {
            return "no ongoing donwload";
        }
        process.destroy();
        listFiles("music", 1).stream()
                .filter(song -> song.endsWith(".part"))
                .map(song -> new File("music/" + song))
                .forEach(File::delete);
        return "download aborted";
    }

    @DeleteMapping("/songs")
    public ResponseEntity<String> removeWithExtensions(@RequestParam("ext") String ext) throws IOException {
        listFiles("music", 1).stream()
                .filter(song -> song.endsWith(ext))
                .map(song -> new File("music/" + song))
                .forEach(File::delete);
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
