package com.dileep.ailearning.invoice;

import jakarta.validation.constraints.NotBlank;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * HTTP surface for Modules 1 &amp; 2.
 *
 * <pre>
 * # Module 1 — extract from text:
 * curl -s localhost:8080/api/invoices/extract \
 *   -H 'Content-Type: application/json' \
 *   -d '{"text":"Invoice No: INV-9\nFrom: Foo Ltd\nWidget x2  100.00\nTotal 100.00"}'
 *
 * # ...or run a bundled text sample with zero setup:
 * curl -s -X POST localhost:8080/api/invoices/extract-sample/clean
 *
 * # Module 2 — extract from a photo/scan (needs `ollama pull llama3.2-vision`):
 * curl -s -F file=@/path/to/invoice.jpg localhost:8080/api/invoices/extract-image
 * </pre>
 */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    /** Bundled sample files under {@code src/main/resources/samples/invoice-*.txt}. */
    private static final List<String> SAMPLES = List.of("clean", "missing-fields");

    private final InvoiceExtractionService extractionService;

    public InvoiceController(InvoiceExtractionService extractionService) {
        this.extractionService = extractionService;
    }

    // --- Module 1: text ----------------------------------------------------

    @PostMapping("/extract")
    public ExtractionResult extract(@RequestBody ExtractRequest request) {
        return extractionService.extract(request.text());
    }

    @GetMapping("/samples")
    public List<String> samples() {
        return SAMPLES;
    }

    @PostMapping(path = "/extract-sample/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ExtractionResult extractSample(@PathVariable String name) {
        if (!SAMPLES.contains(name)) {
            throw new ResponseStatusException(NOT_FOUND,
                    "Unknown sample '" + name + "'. Try one of " + SAMPLES);
        }
        return extractionService.extract(loadSample(name));
    }

    // --- Module 2: image ---------------------------------------------------

    /**
     * Extract from an uploaded invoice image (multipart form field {@code file}).
     * Any common raster format the vision model understands works (JPEG/PNG/...).
     */
    @PostMapping(path = "/extract-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ExtractionResult extractImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded image file is empty.");
        }
        try {
            return extractionService.extractFromImage(file.getBytes());
        } catch (IOException e) {
            throw new IllegalStateException("Could not read uploaded image", e);
        }
    }

    private String loadSample(String name) {
        var resource = new ClassPathResource("samples/invoice-" + name + ".txt");
        try (var in = resource.getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read sample '" + name + "'", e);
        }
    }

    public record ExtractRequest(@NotBlank String text) {
    }
}
