package it.smartcommunitylabdhub.compliance.model;

/** Roles an actor may hold in a compliance context. OTHER requires actorRoleValue on the owner. */
public enum ActorRole {
    PROVIDER,
    DEPLOYER,
    DISTRIBUTOR,
    AUTHORIZED_REPRESENTATIVE,
    PRODUCT_MANUFACTURER,
    MODEL_DEVELOPER,
    SYSTEM_INTEGRATOR,
    SERVICE_OPERATOR,
    DATA_PROVIDER,
    DATA_CONTROLLER,
    DATA_PROCESSOR,
    HUMAN_OVERSIGHT_OPERATOR,
    END_USER,
    AFFECTED_PERSON,
    COMPLIANCE_OWNER,
    OTHER,
}
