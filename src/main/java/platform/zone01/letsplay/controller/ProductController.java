package platform.zone01.letsplay.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import platform.zone01.letsplay.dto.ProductRequestDTO;
import platform.zone01.letsplay.dto.ProductResponseDTO;
import platform.zone01.letsplay.service.ProductService;

import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService  productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("")
    public ResponseEntity<List<ProductResponseDTO>> getProducts() {
        List<ProductResponseDTO> response = productService.getAllProducts();
        return ResponseEntity.ok(response);

    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> getProduct(@PathVariable String id) {
        ProductResponseDTO product = productService.getProductById(id);
        return ResponseEntity.ok(product);
    }

    @PostMapping("")
    public ResponseEntity<ProductResponseDTO> saveProduct(@Valid @RequestBody ProductRequestDTO request) {
        ProductResponseDTO response = productService.saveProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> updateProduct(@PathVariable String id, @Valid @RequestBody ProductRequestDTO request) {
        ProductResponseDTO response = productService.updateProduct(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable String id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
