package os.project.securitydemo2.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import os.project.securitydemo2.common.PageResponse;
import os.project.securitydemo2.product.dto.*;
import java.net.URI;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Validated
public class ProductController {
    private final ProductService products;

    @GetMapping
    @PreAuthorize("hasAuthority('product:read')")
    public PageResponse<ProductResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return products.list(page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('product:read')")
    public ProductResponse get(@PathVariable @Min(1) long id) {
        return products.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('product:write')")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse response = products.create(request);
        return ResponseEntity.created(URI.create("/api/products/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('product:write')")
    public ProductResponse update(@PathVariable @Min(1) long id,
                                  @Valid @RequestBody ProductRequest request) {
        return products.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('product:delete')")
    public ResponseEntity<Void> delete(@PathVariable @Min(1) long id) {
        products.delete(id);
        return ResponseEntity.noContent().build();
    }
}

