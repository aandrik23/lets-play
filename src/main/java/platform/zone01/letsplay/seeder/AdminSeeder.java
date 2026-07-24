package platform.zone01.letsplay.seeder;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import platform.zone01.letsplay.entity.User;
import platform.zone01.letsplay.enums.Role;
import platform.zone01.letsplay.repository.UserRepository;

@Component
public class AdminSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByEmail("admin@email.com").isEmpty()) {
            User admin = new User();
            admin.setName("admin");
            admin.setEmail("admin@email.com");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRole(Role.ADMIN);
            userRepository.save(admin);
            System.out.println(">>> Admin user created");
        } else {
            System.out.println(">>> Admin user already exists");
        }
    }
}