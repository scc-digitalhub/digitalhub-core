package it.smartcommunitylabdhub.authorization.model;


import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Date;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class RefreshTokenEntity {

    @Id
    private String id;
    private String refreshToken;
    private String subject;
    private Date issuedTime;
    private Date expirationTime;
}
