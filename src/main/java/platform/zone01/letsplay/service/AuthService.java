package platform.zone01.letsplay.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import platform.zone01.letsplay.Security.JwtService;
import platform.zone01.letsplay.dto.AuthResponseDTO;
import platform.zone01.letsplay.dto.LoginRequestDTO;
import platform.zone01.letsplay.dto.RegisterRequestDTO;
import platform.zone01.letsplay.dto.UserResponseDTO;
import platform.zone01.letsplay.entity.User;
import platform.zone01.letsplay.enums.Role;
import platform.zone01.letsplay.exception.EmailAlreadyExistsException;
import platform.zone01.letsplay.exception.InvalidCredentialsException;
import platform.zone01.letsplay.repository.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public UserResponseDTO register(RegisterRequestDTO request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.USER);

        User saved = userRepository.save(user);
        return toResponseDTO(saved);
    }

    public AuthResponseDTO login(LoginRequestDTO request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateToken(user);
        return new AuthResponseDTO(token);
    }

    private UserResponseDTO toResponseDTO(User user) {
        return new UserResponseDTO(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
