package it.smartcommunitylabdhub.runtime.hpcdl.specs;

import java.io.Serializable;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.smartcommunitylabdhub.commons.models.run.RunBaseStatus;
import it.smartcommunitylabdhub.runtime.hpcdl.framework.infrastructure.objects.HPCDLJob;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HPCDLRunStatus extends RunBaseStatus {

    private HPCDLJob job;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);
        HPCDLRunStatus spec = mapper.convertValue(data, HPCDLRunStatus.class);
        this.job = spec.getJob();
    }

    public static HPCDLRunStatus with(Map<String, Serializable> data) {
        HPCDLRunStatus spec = new HPCDLRunStatus();
        spec.configure(data);

        return spec;
    }

}
