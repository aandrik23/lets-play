package platform.zone01.letsplay.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ErrorResponseDTO {
    private int status;
    private String error;
    private String message;
    private String path;
}
