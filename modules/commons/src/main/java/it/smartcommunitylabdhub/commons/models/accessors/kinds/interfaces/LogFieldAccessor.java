package it.smartcommunitylabdhub.commons.models.accessors.kinds.interfaces;

import java.util.Map;

public interface LogFieldAccessor extends CommonFieldAccessor {
  static LogFieldAccessor with(Map<String, Object> map) {
    return () -> map;
  }
}
