package platform.zone01.letsplay.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ProductResponseDTO {
    private String id;
    private String name;
    private String description;
    private Double price;
    private String userId;
}
