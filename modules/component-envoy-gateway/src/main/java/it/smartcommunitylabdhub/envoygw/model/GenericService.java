package it.smartcommunitylabdhub.envoygw.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class GenericService {

    private String projectName;
    private String serviceId;
    private String serviceHost;
    private Integer servicePort;
}
