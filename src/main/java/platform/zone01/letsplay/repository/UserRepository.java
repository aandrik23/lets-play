package platform.zone01.letsplay.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import platform.zone01.letsplay.entity.User;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);
}
