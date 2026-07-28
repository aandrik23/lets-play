package platform.zone01.letsplay.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import platform.zone01.letsplay.dto.AuthResponseDTO;
import platform.zone01.letsplay.dto.LoginRequestDTO;
import platform.zone01.letsplay.dto.RegisterRequestDTO;
import platform.zone01.letsplay.dto.UserResponseDTO;
import platform.zone01.letsplay.entity.User;
import platform.zone01.letsplay.enums.Role;
import platform.zone01.letsplay.exception.EmailAlreadyExistsException;
import platform.zone01.letsplay.exception.InvalidCredentialsException;
import platform.zone01.letsplay.repository.UserRepository;
import platform.zone01.letsplay.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;


    @Test
    void register_whenEmailAlreadyExists_shouldThrowEmailAlreadyExistsException() {

        RegisterRequestDTO request = new RegisterRequestDTO(
                "Alice",
                "alice@gmail.com",
                "password123"
        );

        when(userRepository.findByEmail("alice@gmail.com"))
                .thenReturn(Optional.of(new User()));

        EmailAlreadyExistsException exception = assertThrows(
                EmailAlreadyExistsException.class,
                () -> authService.register(request)
        );

        assertEquals("Email already in use: alice@gmail.com", exception.getMessage());
    }

    @Test
    void register_whenValidRequest_shouldReturnCorrectUserResponse() {
        RegisterRequestDTO request = new RegisterRequestDTO(
                "Alice",
                "alice@gmail.com",
                "password123"
        );

        when(userRepository.findByEmail("alice@gmail.com"))
                .thenReturn(Optional.empty());

        when(passwordEncoder.encode("password123"))
                .thenReturn("hashedpassword"); //fake hash

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setId("generatedId123");
                    return user;
                });

        UserResponseDTO response = authService.register(request);

        assertAll(
                () -> assertEquals("alice@gmail.com",  response.getEmail()),
                () -> assertEquals("generatedId123", response.getId()),
                () -> assertEquals(Role.USER, response.getRole())
        );

        verify(passwordEncoder).encode("password123");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void login_whenEmailDoesntExist_shouldThrowInvalidCredentialsException() {

        LoginRequestDTO request = new LoginRequestDTO(
                "thanos@gmail.com",
                "password123"
        );

        when(userRepository.findByEmail("thanos@gmail.com"))
                .thenReturn(Optional.empty());

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(request)
        );

        assertEquals("Invalid email or password", exception.getMessage());
    }

    @Test
    void login_whenPasswordIsWrong_shouldThrowInvalidCredentialsException() {

        LoginRequestDTO request = new LoginRequestDTO(
                "thanos@gmail.com",
                "password123"
        );

        User user = new User();
        user.setPassword("hashedpassword");

        when(userRepository.findByEmail("thanos@gmail.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches("password123", "hashedpassword"))
                .thenReturn(false);

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(request)
        );
    }

    @Test
    void login_whenValidCredentials_shouldReturnToken() {

        LoginRequestDTO request = new LoginRequestDTO(
                "thanos@gmail.com",
                "password123"
        );

        User user = new User();
        user.setPassword("hashedpassword");

        when(userRepository.findByEmail(request.getEmail()))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches("password123", "hashedpassword"))
                .thenReturn(true);

        when(jwtService.generateToken(user))
                .thenReturn("token");

        AuthResponseDTO response = authService.login(request);

        assertNotNull(response.getToken());
        assertEquals("token", response.getToken());
        verify(jwtService, times(1)).generateToken(user);

    }
}