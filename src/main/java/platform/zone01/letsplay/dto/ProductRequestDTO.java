package platform.zone01.letsplay.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ProductRequestDTO {
    @NotBlank(message = "Please enter a product name")
    private String name;

    private String description;

    @NotNull(message = "Please enter a valid product price")
    @Positive(message = "Price must be greater than 0")
    private Double price;
}
