package platform.zone01.letsplay.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import platform.zone01.letsplay.entity.Product;

import java.util.List;

public interface ProductRepository extends MongoRepository<Product, String> {
     List<Product> findByUserId(String userId);
}
