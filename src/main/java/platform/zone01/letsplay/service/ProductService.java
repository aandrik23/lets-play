package platform.zone01.letsplay.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import platform.zone01.letsplay.dto.ProductRequestDTO;
import platform.zone01.letsplay.dto.ProductResponseDTO;
import platform.zone01.letsplay.entity.Product;
import platform.zone01.letsplay.enums.Role;
import platform.zone01.letsplay.exception.ProductNotFoundException;
import platform.zone01.letsplay.repository.ProductRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<ProductResponseDTO> getAllProducts() {
        return  productRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public ProductResponseDTO getProductById(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product with id: " + id + " not found"));
        return toDTO(product);
    }

    public ProductResponseDTO saveProduct(ProductRequestDTO request) {
        String userId = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setUserId(userId);
        Product savedProduct = productRepository.save(product);

        return toDTO(savedProduct);
    }

    public ProductResponseDTO updateProduct(String id, ProductRequestDTO request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product with id: " + id + " not found"));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = auth.getName();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + Role.ADMIN.name()));

        if (product.getUserId().equals(userId) && !isAdmin) {
            throw new AccessDeniedException("Access denied");
        }

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());

        Product updatedProduct = productRepository.save(product);
        return toDTO(updatedProduct);
    }

    public void deleteProduct(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException("Product with id: " + id + " not found"));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = auth.getName();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + Role.ADMIN.name()));

        if (product.getUserId().equals(userId) && !isAdmin) {
            throw new AccessDeniedException("Access denied");
        }

        productRepository.delete(product);
    }

    private ProductResponseDTO toDTO(Product product) {
        return new ProductResponseDTO(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getUserId());
    }
}
