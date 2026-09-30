package com.feesaas.customer.api;

import com.feesaas.customer.application.CustomerImportService;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/imports/customers")
public class CustomerImportController {

    private final CustomerImportService imports;

    public CustomerImportController(CustomerImportService imports) {
        this.imports = imports;
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        byte[] body = imports.template();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("duemate-members-template.xlsx")
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResponse importFile(@RequestPart("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new com.feesaas.shared.error.ApiException(
                    com.feesaas.shared.error.ErrorCode.VALIDATION_FAILED, "Choose an Excel or CSV file.");
        }
        var result = imports.importFile(file.getBytes());
        return new ImportResponse(result.created(), result.skipped(), result.errors());
    }

    public record ImportResponse(int created, int skipped, List<String> errors) {}
}
