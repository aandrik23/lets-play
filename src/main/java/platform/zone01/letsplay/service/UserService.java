package platform.zone01.letsplay.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import platform.zone01.letsplay.dto.UserResponseDTO;
import platform.zone01.letsplay.entity.User;
import platform.zone01.letsplay.exception.UserNotFoundException;
import platform.zone01.letsplay.repository.UserRepository;

import java.util.List;
import java.util.stream.Collectors;

@Service
@EnableMethodSecurity
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserResponseDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUserById(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User with id: " + id + " not found"));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (user.getId().equals(auth.getName())) {
            throw new AccessDeniedException("Admins cannot delete their own account");
        }

        userRepository.deleteById(id);
    }

    private UserResponseDTO toDTO(User user) {
        return new UserResponseDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole());
    }
}
