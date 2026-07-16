package platform.zone01.letsplay.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class LoginRequestDTO {
    @NotBlank(message = "Please enter a valid email")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Please enter your password")
    @Size(min = 6, max = 20, message = "Please enter a valid password")
    private String password;
}
